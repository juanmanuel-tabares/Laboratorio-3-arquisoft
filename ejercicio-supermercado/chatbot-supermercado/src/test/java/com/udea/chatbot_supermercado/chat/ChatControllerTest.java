package com.udea.chatbot_supermercado.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ChatControllerTest {

    @Test
    void errorDesconocido_mensajeGenerico() {
        assertThat(ChatController.mensajeDeError(new IllegalStateException("x"))).contains("no está disponible");
    }

    @Test
    void fallaTrasConfirmar_avisaQueLaOperacionYaSeEjecuto() {
        String mensaje = ChatController.conAccionesEjecutadas("Límite alcanzado.",
            List.of("verPedido", "confirmarPedido"));

        assertThat(mensaje).contains("alcancé a ejecutar confirmarPedido").doesNotContain("verPedido");
    }

    @Test
    void fallaSoloConLecturas_noAgregaAviso() {
        assertThat(ChatController.conAccionesEjecutadas("Límite alcanzado.", List.of("buscarProductos")))
            .isEqualTo("Límite alcanzado.");
    }

    @Test
    void timeoutDelModelo_mensajeDeDemora() {
        RuntimeException envuelto = new RuntimeException(new SocketTimeoutException("timeout"));
        assertThat(ChatController.mensajeDeError(envuelto)).contains("tardó demasiado");
    }

    private static ToolCallback herramienta(String nombre, String respuesta) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(nombre).description(nombre).inputSchema("{}").build();
            }

            @Override
            public String call(String argumentos) {
                return respuesta;
            }
        };
    }

    @Test
    void aprobarDesdeLaInterfaz_ejecutaUnaVezYQuedaEnLaMemoria() {
        String verPedido = "[{\"type\":\"text\",\"text\":\"{\\\"id\\\":6,\\\"estado\\\":\\\"BORRADOR\\\",\\\"items\\\":[],\\\"total\\\":28000}\"}]";
        ToolCallback[] servidor = {herramienta("verPedido", verPedido), herramienta("cancelarPedido", "[]")};
        AprobacionesPendientes aprobaciones = new AprobacionesPendientes(servidor, Clock.systemUTC());
        ChatMemory memoria = MessageWindowChatMemory.builder().build();
        ChatController controlador = new ChatController(mock(ChatClient.class), memoria, new RegistroHerramientas(), aprobaciones);

        aprobaciones.iniciarTurno("c1");
        aprobaciones.proteger(servidor)[1].call("{\"pedidoId\":6,\"telefono\":\"3001234567\"}");
        String id = aprobaciones.terminarTurno().orElseThrow().id();

        ResponseEntity<?> primera = controlador.resolver("c1", id, new ChatController.Decision(true));
        ResponseEntity<?> segunda = controlador.resolver("c1", id, new ChatController.Decision(true));

        assertThat(primera.getStatusCode()).isEqualTo(HttpStatus.OK);
        ChatController.Respuesta r = (ChatController.Respuesta) primera.getBody();
        assertThat(r.respuesta()).isEqualTo("Pedido N.º 6 cancelado.");
        assertThat(r.herramientas()).containsExactly("cancelarPedido");
        assertThat(memoria.get("c1")).hasSize(2);
        assertThat(memoria.get("c1").get(0).getText()).contains("pulsó «Aprobar»");
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
