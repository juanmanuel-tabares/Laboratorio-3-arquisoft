package com.udea.chatbot_supermercado.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La confirmación y la cancelación las aprueba el cliente en la aplicación, no el modelo. */
class AprobacionesPendientesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ARGS_CONFIRMAR = "{\"pedidoId\":4,\"telefono\":\"3001234567\",\"metodoPago\":\"efectivo\"}";
    private static final String ARGS_CANCELAR = "{\"pedidoId\":4,\"telefono\":\"3001234567\"}";

    private final List<String> llamadasAlServidor = new ArrayList<>();
    private RelojMovible reloj;
    private Map<String, Object> pedido;
    private AprobacionesPendientes aprobaciones;
    private ToolCallback[] protegidas;

    /** Respuesta del servidor MCP tal como la entrega el cliente MCP de Spring AI. */
    private static String comoContenidoMcp(Object dto) {
        try {
            return JSON.writeValueAsString(List.of(Map.of("type", "text", "text", JSON.writeValueAsString(dto))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ToolCallback herramienta(String nombre, Function<String, String> respuesta) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(nombre).description(nombre).inputSchema("{}").build();
            }

            @Override
            public String call(String argumentos) {
                llamadasAlServidor.add(nombre);
                return respuesta.apply(argumentos);
            }
        };
    }

    @BeforeEach
    void setUp() {
        reloj = new RelojMovible(Instant.parse("2026-11-14T15:00:00Z"));
        pedido = new java.util.HashMap<>(Map.of(
            "id", 4, "estado", "BORRADOR", "direccionEntrega", "Calle 50 # 40-20, Medellín",
            "items", List.of(Map.of("producto", "Café molido", "presentacion", "500 g", "cantidad", 3, "subtotal", 55500)),
            "subtotal", 55500, "costoDomicilio", 6000, "total", 61500, "faltaParaPedidoMinimo", 0));
        ToolCallback[] servidor = {
            herramienta("buscarProductos", a -> "[]"),
            herramienta("verPedido", a -> comoContenidoMcp(pedido)),
            herramienta("confirmarPedido", a -> comoContenidoMcp(Map.of("id", 4, "estado", "CONFIRMADO", "total", 61500,
                "metodoPago", "EFECTIVO", "entregaEstimada", "2026-11-14 11:00 a. m."))),
            herramienta("cancelarPedido", a -> comoContenidoMcp(Map.of("id", 4, "estado", "CANCELADO")))
        };
        aprobaciones = new AprobacionesPendientes(servidor, reloj);
        protegidas = aprobaciones.proteger(servidor);
        llamadasAlServidor.clear();
    }

    private String propuestaDelModelo(String herramienta, String argumentos) {
        for (ToolCallback t : protegidas) {
            if (t.getToolDefinition().name().equals(herramienta)) {
                return t.call(argumentos);
            }
        }
        throw new IllegalArgumentException(herramienta);
    }

    @Test
    void confirmar_noLlegaAlServidorHastaQueElClienteAprueba() {
        aprobaciones.iniciarTurno("c1");
        String paraElModelo = propuestaDelModelo("confirmarPedido", ARGS_CONFIRMAR);
        AprobacionesPendientes.Aprobacion tarjeta = aprobaciones.terminarTurno().orElseThrow();

        assertThat(paraElModelo).contains("PENDIENTE_DE_APROBACION").contains("NO se ejecutó");
        assertThat(llamadasAlServidor).containsExactly("verPedido");           // solo leyó el resumen
        assertThat(tarjeta.titulo()).isEqualTo("Confirmar el Pedido N.º 4");
        assertThat(tarjeta.lineas()).containsExactly("3 × Café molido (500 g): $55.500", "Domicilio: $6.000");
        assertThat(tarjeta.total()).isEqualTo(61_500);
        assertThat(tarjeta.metodoPago()).isEqualTo("EFECTIVO");

        AprobacionesPendientes.Resultado r = aprobaciones.resolver("c1", tarjeta.id(), true);

        assertThat(llamadasAlServidor).containsExactly("verPedido", "confirmarPedido");
        assertThat(r.exitosa()).isTrue();
        assertThat(r.mensaje()).isEqualTo("Pedido N.º 4 confirmado. Total: $61.500, pago en EFECTIVO contra entrega. "
            + "Entrega estimada: 2026-11-14 11:00 a. m.");
    }

    @Test
    void laAprobacionSeUsaUnaSolaVez() {
        aprobaciones.iniciarTurno("c1");
        propuestaDelModelo("confirmarPedido", ARGS_CONFIRMAR);
        String id = aprobaciones.terminarTurno().orElseThrow().id();
        aprobaciones.resolver("c1", id, true);

        assertThatThrownBy(() -> aprobaciones.resolver("c1", id, true)).isInstanceOf(NoSuchElementException.class);
        assertThat(llamadasAlServidor).filteredOn("confirmarPedido"::equals).hasSize(1);
    }

    @Test
    void rechazar_noEjecutaNada() {
        aprobaciones.iniciarTurno("c1");
        propuestaDelModelo("cancelarPedido", ARGS_CANCELAR);
        String id = aprobaciones.terminarTurno().orElseThrow().id();

        AprobacionesPendientes.Resultado r = aprobaciones.resolver("c1", id, false);

        assertThat(r.aprobada()).isFalse();
        assertThat(r.mensaje()).contains("no cancelé el Pedido N.º 4");
        assertThat(llamadasAlServidor).doesNotContain("cancelarPedido");
    }

    @Test
    void otraConversacion_noPuedeAprobarla() {
        aprobaciones.iniciarTurno("c1");
        propuestaDelModelo("cancelarPedido", ARGS_CANCELAR);
        String id = aprobaciones.terminarTurno().orElseThrow().id();

        assertThatThrownBy(() -> aprobaciones.resolver("otra", id, true)).isInstanceOf(NoSuchElementException.class);
        assertThat(llamadasAlServidor).doesNotContain("cancelarPedido");
        assertThat(aprobaciones.resolver("c1", id, true).exitosa()).isTrue();   // la dueña sí puede
    }

    @Test
    void vencida_trasDiezMinutos() {
        aprobaciones.iniciarTurno("c1");
        propuestaDelModelo("confirmarPedido", ARGS_CONFIRMAR);
        String id = aprobaciones.terminarTurno().orElseThrow().id();
        reloj.avanzarMinutos(11);

        assertThatThrownBy(() -> aprobaciones.resolver("c1", id, true)).isInstanceOf(NoSuchElementException.class);
        assertThat(llamadasAlServidor).doesNotContain("confirmarPedido");
    }

    @Test
    void pedidoAjeno_devuelveElErrorDelServidorSinTarjeta() {
        ToolCallback[] servidor = {herramienta("verPedido", a -> {
            throw new ToolExecutionException(ToolDefinition.builder().name("verPedido").description("").inputSchema("{}").build(),
                new IllegalStateException("Error calling tool: [TextContent[annotations=null, "
                    + "text=El pedido 4 no pertenece al teléfono 3109998877, meta=null]]"));
        }), herramienta("cancelarPedido", a -> "[]")};
        AprobacionesPendientes conAjeno = new AprobacionesPendientes(servidor, reloj);
        ToolCallback cancelar = conAjeno.proteger(servidor)[1];

        conAjeno.iniciarTurno("c1");
        String paraElModelo = cancelar.call("{\"pedidoId\":4,\"telefono\":\"3109998877\"}");

        assertThat(paraElModelo).isEqualTo("{\"error\":\"El pedido 4 no pertenece al teléfono 3109998877\"}");
        assertThat(conAjeno.terminarTurno()).isEmpty();
    }

    @Test
    void confirmarBajoElMinimoOConPagoInvalido_noMuestraBotones() {
        pedido.put("faltaParaPedidoMinimo", 8000);
        aprobaciones.iniciarTurno("c1");
        assertThat(propuestaDelModelo("confirmarPedido", ARGS_CONFIRMAR)).contains("faltan $8.000");
        assertThat(aprobaciones.terminarTurno()).isEmpty();

        pedido.put("faltaParaPedidoMinimo", 0);
        aprobaciones.iniciarTurno("c1");
        String tarjeta = "{\"pedidoId\":4,\"telefono\":\"3001234567\",\"metodoPago\":\"tarjeta de crédito\"}";
        assertThat(propuestaDelModelo("confirmarPedido", tarjeta)).contains("Método de pago no válido");
        assertThat(aprobaciones.terminarTurno()).isEmpty();
    }

    @Test
    void soloSeInterceptanConfirmarYCancelar() {
        ToolCallback[] servidor = {herramienta("buscarProductos", a -> "[]"), herramienta("confirmarPedido", a -> "[]")};
        ToolCallback[] resultado = new AprobacionesPendientes(servidor, reloj).proteger(servidor);

        assertThat(resultado[0]).isSameAs(servidor[0]);
        assertThat(resultado[1]).isNotSameAs(servidor[1]);
        assertThat(resultado[1].getToolDefinition().name()).isEqualTo("confirmarPedido");
    }

    /** Reloj de prueba que se puede adelantar. */
    private static final class RelojMovible extends Clock {
        private Instant ahora;

        RelojMovible(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzarMinutos(long minutos) {
            ahora = ahora.plusSeconds(minutos * 60);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
