package com.udea.chatbot_supermercado.chat;

import com.google.genai.errors.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InterruptedIOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final int MAX_CARACTERES = 1000;

    public record Peticion(String conversacionId, String mensaje) {}
    public record Decision(boolean aprobar) {}

    /** {@code aprobacion} viene cuando el modelo propuso confirmar o cancelar: la interfaz muestra los botones. */
    public record Respuesta(String conversacionId, String respuesta, List<String> herramientas,
                            AprobacionesPendientes.Aprobacion aprobacion) {}

    public record Error(String error, List<String> herramientas, AprobacionesPendientes.Aprobacion aprobacion) {
        public Error(String error) {
            this(error, List.of(), null);
        }
    }

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final RegistroHerramientas registro;
    private final AprobacionesPendientes aprobaciones;

    public ChatController(ChatClient chatClient, ChatMemory chatMemory, RegistroHerramientas registro,
                          AprobacionesPendientes aprobaciones) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.registro = registro;
        this.aprobaciones = aprobaciones;
    }

    @PostMapping
    public ResponseEntity<?> chatear(@RequestBody Peticion peticion) {
        String mensaje = peticion.mensaje() == null ? "" : peticion.mensaje().trim();
        if (mensaje.isEmpty() || mensaje.length() > MAX_CARACTERES) {
            return ResponseEntity.badRequest().body(new Error("El mensaje debe tener entre 1 y " + MAX_CARACTERES + " caracteres"));
        }
        String conversacionId = peticion.conversacionId() == null || peticion.conversacionId().isBlank()
            ? UUID.randomUUID().toString() : peticion.conversacionId();

        registro.iniciar();
        aprobaciones.iniciarTurno(conversacionId);
        try {
            String texto = chatClient.prompt()
                .user(mensaje)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversacionId))
                .call()
                .content();
            List<String> herramientas = registro.terminar();
            var aprobacion = aprobaciones.terminarTurno().orElse(null);
            log.debug("[{}] herramientas usadas: {}; acción por aprobar: {}", conversacionId, herramientas, aprobacion);
            return ResponseEntity.ok(new Respuesta(conversacionId, texto, herramientas, aprobacion));
        } catch (RuntimeException e) {
            List<String> herramientas = registro.terminar();
            // Si el modelo alcanzó a proponer la acción antes de fallar, los botones se muestran igual.
            var aprobacion = aprobaciones.terminarTurno().orElse(null);
            log.error("[{}] error al conversar con el modelo (herramientas ya ejecutadas: {})", conversacionId, herramientas, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new Error(conAccionesEjecutadas(mensajeDeError(e), herramientas), herramientas, aprobacion));
        }
    }

    /**
     * El cliente pulsó Aprobar o Rechazar. Aquí, y solo aquí, se ejecuta confirmarPedido o cancelarPedido en el
     * servidor MCP. El resultado se agrega a la memoria de la conversación para que el modelo lo conozca en el
     * siguiente turno; no se gasta una llamada al modelo.
     */
    @PostMapping("/{conversacionId}/aprobaciones/{accionId}")
    public ResponseEntity<?> resolver(@PathVariable String conversacionId, @PathVariable String accionId,
                                      @RequestBody Decision decision) {
        AprobacionesPendientes.Resultado r;
        try {
            r = aprobaciones.resolver(conversacionId, accionId, decision.aprobar());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new Error(e.getMessage()));
        }
        log.info("[{}] {} {} por el cliente: {}", conversacionId, r.accion(), r.aprobada() ? "aprobada" : "rechazada", r.mensaje());
        chatMemory.add(conversacionId, List.of(
            new UserMessage("(El cliente pulsó «" + (r.aprobada() ? "Aprobar" : "Rechazar") + "» en la acción " + r.accion() + ")"),
            new AssistantMessage(r.mensaje())));
        return ResponseEntity.ok(new Respuesta(conversacionId, r.mensaje(),
            r.aprobada() ? List.of(r.accion()) : List.of(), null));
    }

    /** Herramientas que modifican datos: si alguna alcanzó a ejecutarse antes del fallo, hay que avisarlo. */
    private static final List<String> HERRAMIENTAS_DE_ESCRITURA = List.of(
        "registrarCliente", "crearPedido", "agregarProducto", "actualizarCantidad", "confirmarPedido", "cancelarPedido");

    /**
     * Si el modelo falla DESPUÉS de ejecutar una herramienta de escritura (p. ej. confirmarPedido), la operación
     * ya quedó hecha en el servidor aunque el modelo no alcanzara a redactar la respuesta. Se le dice al cliente
     * para que no repita la solicitud a ciegas (y, por ejemplo, agregue dos veces el mismo producto).
     */
    static String conAccionesEjecutadas(String mensaje, List<String> herramientas) {
        List<String> escrituras = herramientas.stream().filter(HERRAMIENTAS_DE_ESCRITURA::contains).distinct().toList();
        if (escrituras.isEmpty()) {
            return mensaje;
        }
        return mensaje + " Importante: antes del error alcancé a ejecutar " + String.join(", ", escrituras)
            + ". Pregúntame por el estado de tu pedido antes de repetir la solicitud.";
    }

    /** Traduce los fallos del proveedor del modelo a un mensaje útil para el cliente. */
    static String mensajeDeError(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ApiException api) {
                if (api.code() == 429) {
                    return "Se alcanzó el límite de uso del modelo de IA (cuota de la API de Gemini por minuto o por día). Espera un momento e intenta de nuevo.";
                }
                if (api.code() >= 500) {
                    return "El modelo de IA está saturado en este momento. Intenta de nuevo en unos segundos.";
                }
            }
            if (t instanceof InterruptedIOException) {
                return "El modelo de IA tardó demasiado en responder. Intenta de nuevo.";
            }
        }
        return "El asistente no está disponible en este momento. Intenta de nuevo en unos segundos.";
    }

    @DeleteMapping("/{conversacionId}")
    public ResponseEntity<Void> reiniciar(@PathVariable String conversacionId) {
        chatMemory.clear(conversacionId);
        aprobaciones.descartar(conversacionId);
        return ResponseEntity.noContent().build();
    }
}
