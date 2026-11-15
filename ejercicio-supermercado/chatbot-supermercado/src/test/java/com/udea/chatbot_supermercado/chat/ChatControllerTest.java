package com.udea.chatbot_supermercado.chat;

import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
