package com.udea.chatbot_supermercado.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aprobación humana de las acciones sensibles ({@code confirmarPedido} y {@code cancelarPedido}), exigida por el
 * host y no por el modelo.
 * <p>
 * Cuando el modelo llama una de estas herramientas, la llamada no llega al servidor MCP. Se guarda como acción
 * pendiente, con los argumentos exactos que propuso el modelo, y el modelo recibe un resultado que le dice que la
 * acción espera la aprobación del cliente. La interfaz muestra una tarjeta con el resumen real del pedido (leído con
 * {@code verPedido}) y los botones Aprobar y Rechazar. Solo el clic del cliente
 * ({@code POST /api/chat/{conversacionId}/aprobaciones/{accionId}}) ejecuta la herramienta, una sola vez, con esos
 * mismos argumentos y dentro de los 10 minutos siguientes. El modelo no tiene ninguna herramienta para aprobar.
 * <p>
 * Es el mismo patrón que usa Claude Desktop al pedir permiso antes de ejecutar una herramienta MCP.
 */
@Component
public class AprobacionesPendientes {

    static final Set<String> ACCIONES_SENSIBLES = Set.of("confirmarPedido", "cancelarPedido");
    static final Duration VIGENCIA = Duration.ofMinutes(10);
    private static final Set<String> METODOS_DE_PAGO = Set.of("EFECTIVO", "DATAFONO", "NEQUI");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Lo que ve el cliente en la tarjeta de aprobación. */
    public record Aprobacion(String id, String accion, long pedidoId, String titulo, List<String> lineas,
                             long total, String metodoPago, String direccion) {}

    /** Resultado de aprobar o rechazar: el texto que se le muestra al cliente. */
    public record Resultado(String accion, boolean aprobada, boolean exitosa, String mensaje) {}

    private record Pendiente(Aprobacion aprobacion, String conversacionId, String argumentos, Instant creada) {}

    private final Map<String, ToolCallback> originales = new HashMap<>();
    private final Map<String, Pendiente> pendientes = new ConcurrentHashMap<>();
    private final ThreadLocal<String> conversacion = new ThreadLocal<>();
    private final ThreadLocal<Aprobacion> propuesta = new ThreadLocal<>();
    private final Clock reloj;

    @Autowired
    public AprobacionesPendientes(ToolCallbackProvider mcpTools) {
        this(mcpTools.getToolCallbacks(), Clock.systemUTC());
    }

    AprobacionesPendientes(ToolCallback[] herramientasMcp, Clock reloj) {
        for (ToolCallback h : herramientasMcp) {
            originales.put(nombreBase(h), h);
        }
        this.reloj = reloj;
    }

    /** Reemplaza las herramientas sensibles por su versión que espera aprobación; las demás quedan igual. */
    public ToolCallback[] proteger(ToolCallback[] herramientas) {
        return Arrays.stream(herramientas)
            .map(h -> ACCIONES_SENSIBLES.contains(nombreBase(h)) ? new Interceptada(h) : h)
            .toArray(ToolCallback[]::new);
    }

    public void iniciarTurno(String conversacionId) {
        conversacion.set(conversacionId);
        propuesta.remove();
    }

    /** La acción que el modelo propuso en este turno, si propuso alguna. */
    public Optional<Aprobacion> terminarTurno() {
        Optional<Aprobacion> resultado = Optional.ofNullable(propuesta.get());
        propuesta.remove();
        conversacion.remove();
        return resultado;
    }

    /**
     * Aprueba (ejecuta la herramienta en el servidor MCP) o rechaza (la descarta) una acción pendiente.
     *
     * @throws NoSuchElementException si la acción no existe, ya se usó, venció o es de otra conversación
     */
    public Resultado resolver(String conversacionId, String accionId, boolean aprobar) {
        purgarVencidas();
        Pendiente p = pendientes.get(accionId);
        if (p == null || !p.conversacionId().equals(conversacionId) || !pendientes.remove(accionId, p)) {
            throw new NoSuchElementException("Esta acción ya no está disponible: se usó, venció o es de otra conversación.");
        }
        Aprobacion a = p.aprobacion();
        boolean confirmar = a.accion().equals("confirmarPedido");
        String pedido = "el Pedido N.º " + a.pedidoId();
        if (!aprobar) {
            return new Resultado(a.accion(), false, true,
                "Listo, no " + (confirmar ? "confirmé " : "cancelé ") + pedido + ". ¿Quieres cambiar algo?");
        }
        try {
            JsonNode r = llamar(a.accion(), p.argumentos());
            String mensaje = confirmar
                ? "Pedido N.º " + a.pedidoId() + " confirmado. Total: " + pesos(r.path("total").asLong())
                  + ", pago en " + r.path("metodoPago").asText() + " contra entrega. Entrega estimada: "
                  + r.path("entregaEstimada").asText()   // ya termina en "a. m." o "p. m."
                : "Pedido N.º " + a.pedidoId() + " cancelado.";
            return new Resultado(a.accion(), true, true, mensaje);
        } catch (Exception e) {
            return new Resultado(a.accion(), true, false, "No se pudo " + (confirmar ? "confirmar " : "cancelar ")
                + pedido + ": " + RegistroHerramientas.mensajeDeError(e));
        }
    }

    /** Descarta las acciones pendientes de una conversación (al reiniciarla). */
    public void descartar(String conversacionId) {
        pendientes.values().removeIf(p -> p.conversacionId().equals(conversacionId));
    }

    private void purgarVencidas() {
        Instant limite = reloj.instant().minus(VIGENCIA);
        pendientes.values().removeIf(p -> p.creada().isBefore(limite));
    }

    /** Llama una herramienta MCP original y devuelve el JSON que viene dentro del primer contenido de texto. */
    private JsonNode llamar(String herramienta, String argumentos) throws Exception {
        ToolCallback original = originales.get(herramienta);
        if (original == null) {
            throw new IllegalStateException("El servidor MCP no publica la herramienta " + herramienta);
        }
        return contenido(original.call(argumentos));
    }

    /** El cliente MCP de Spring AI devuelve [{"type":"text","text":"<json del servidor>"}]. */
    static JsonNode contenido(String respuesta) {
        try {
            JsonNode nodo = JSON.readTree(respuesta);
            if (nodo.isArray() && !nodo.isEmpty() && nodo.get(0).has("text")) {
                String texto = nodo.get(0).get("text").asText();
                try {
                    return JSON.readTree(texto);
                } catch (Exception noEsJson) {
                    return TextNode.valueOf(texto);
                }
            }
            return nodo;
        } catch (Exception e) {
            return TextNode.valueOf(respuesta);
        }
    }

    /** Nombre de la herramienta sin el prefijo que el cliente MCP podría anteponer ("cliente_servidor_nombre"). */
    static String nombreBase(ToolCallback herramienta) {
        String nombre = herramienta.getToolDefinition().name();
        return nombre.substring(nombre.lastIndexOf('_') + 1);
    }

    static String pesos(long valor) {
        return "$" + String.format(Locale.ROOT, "%,d", valor).replace(',', '.');
    }

    /**
     * Revisión de cortesía antes de mostrar el botón, para no ofrecer una acción que el servidor va a rechazar.
     * No reemplaza las reglas del servidor: al ejecutar, {@code PedidoService} vuelve a validar todo.
     */
    private static String motivoDeRechazo(String accion, JsonNode pedido, String metodoPago) {
        String estado = pedido.path("estado").asText();
        long id = pedido.path("id").asLong();
        if (accion.equals("confirmarPedido")) {
            if (!estado.equals("BORRADOR")) {
                return "El Pedido N.º " + id + " está en estado " + estado + "; solo se puede confirmar un pedido en borrador.";
            }
            if (pedido.path("items").isEmpty()) {
                return "El Pedido N.º " + id + " no tiene productos.";
            }
            long falta = pedido.path("faltaParaPedidoMinimo").asLong();
            if (falta > 0) {
                return "El Pedido N.º " + id + " no alcanza el pedido mínimo: faltan " + pesos(falta) + " en productos.";
            }
            if (!METODOS_DE_PAGO.contains(metodoPago)) {
                return "Método de pago no válido: \"" + metodoPago + "\". Opciones: EFECTIVO, DATAFONO o NEQUI (contra entrega).";
            }
        } else if (!estado.equals("BORRADOR") && !estado.equals("CONFIRMADO")) {
            return "El Pedido N.º " + id + " está en estado " + estado + " y ya no se puede cancelar.";
        }
        return null;
    }

    private static String error(String mensaje) {
        try {
            return JSON.writeValueAsString(Map.of("error", mensaje));
        } catch (Exception e) {
            return "{\"error\":\"Acción no disponible\"}";
        }
    }

    /** Herramienta sensible: en vez de ejecutarse, crea la acción pendiente de aprobación. */
    private final class Interceptada implements ToolCallback {
        private final ToolCallback delegada;

        private Interceptada(ToolCallback delegada) {
            this.delegada = delegada;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegada.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegada.getToolMetadata();
        }

        @Override
        public String call(String argumentos) {
            return call(argumentos, null);
        }

        @Override
        public String call(String argumentos, ToolContext contexto) {
            String accion = nombreBase(delegada);
            String conversacionId = conversacion.get();
            if (conversacionId == null) {
                return error("Esta acción requiere la aprobación del cliente desde la aplicación.");
            }
            try {
                JsonNode args = JSON.readTree(argumentos == null || argumentos.isBlank() ? "{}" : argumentos);
                long pedidoId = args.path("pedidoId").asLong(-1);
                String telefono = args.path("telefono").asText("");
                if (pedidoId <= 0 || telefono.isBlank()) {
                    return error("Faltan el número de pedido o el celular del cliente.");
                }
                // El resumen sale del servidor, no del modelo; verPedido además comprueba que el pedido sea del cliente.
                JsonNode pedido = llamar("verPedido", JSON.writeValueAsString(Map.of("pedidoId", pedidoId, "telefono", telefono)));
                String metodoPago = args.path("metodoPago").asText("").trim().toUpperCase(Locale.ROOT);
                String motivo = motivoDeRechazo(accion, pedido, metodoPago);
                if (motivo != null) {
                    return error(motivo);
                }
                boolean confirmar = accion.equals("confirmarPedido");
                List<String> lineas = new ArrayList<>();
                for (JsonNode item : pedido.path("items")) {
                    lineas.add(item.path("cantidad").asInt() + " × " + item.path("producto").asText()
                        + " (" + item.path("presentacion").asText() + "): " + pesos(item.path("subtotal").asLong()));
                }
                if (confirmar) {
                    long domicilio = pedido.path("costoDomicilio").asLong();
                    lineas.add("Domicilio: " + (domicilio == 0 ? "gratis" : pesos(domicilio)));
                }
                Aprobacion aprobacion = new Aprobacion(UUID.randomUUID().toString(), accion, pedidoId,
                    (confirmar ? "Confirmar" : "Cancelar") + " el Pedido N.º " + pedidoId, List.copyOf(lineas),
                    pedido.path("total").asLong(), confirmar ? metodoPago : null, pedido.path("direccionEntrega").asText());

                purgarVencidas();
                descartar(conversacionId);          // a lo sumo una acción pendiente por conversación
                pendientes.put(aprobacion.id(), new Pendiente(aprobacion, conversacionId, argumentos, reloj.instant()));
                propuesta.set(aprobacion);

                return JSON.writeValueAsString(Map.of(
                    "estado", "PENDIENTE_DE_APROBACION",
                    "accion", accion,
                    "pedidoId", pedidoId,
                    "instrucciones", "La acción todavía NO se ejecutó. La aplicación le está mostrando al cliente el "
                        + "resumen del pedido con los botones Aprobar y Rechazar. Dile en una o dos frases que revise el "
                        + "resumen y pulse Aprobar si está de acuerdo. No digas que el pedido ya quedó "
                        + (confirmar ? "confirmado" : "cancelado") + " y no vuelvas a llamar esta herramienta."));
            } catch (Exception e) {
                return RegistroHerramientas.comoJsonDeError(e);
            }
        }
    }
}
