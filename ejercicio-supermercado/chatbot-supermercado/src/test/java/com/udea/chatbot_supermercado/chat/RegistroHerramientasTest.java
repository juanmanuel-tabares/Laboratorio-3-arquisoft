package com.udea.chatbot_supermercado.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class RegistroHerramientasTest {

    private static ToolDefinition definicion(String nombre) {
        return ToolDefinition.builder().name(nombre).description(nombre).inputSchema("{}").build();
    }

    private static ToolCallback herramienta(String nombre) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definicion(nombre);
            }

            @Override
            public String call(String argumentos) {
                return "{\"ok\":\"" + nombre + "\"}";
            }
        };
    }

    /** Simula lo que hace el cliente MCP de Spring AI cuando el servidor responde con isError. */
    private static ToolCallback herramientaConError(String nombre, String mensajeServidor) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definicion(nombre);
            }

            @Override
            public String call(String argumentos) {
                throw new ToolExecutionException(definicion(nombre), new IllegalStateException(
                    "Error calling tool: [TextContent[annotations=null, text=" + mensajeServidor + ", meta=null]]"));
            }
        };
    }

    @Test
    void registraLasHerramientasInvocadasYDelegaLaLlamada() {
        RegistroHerramientas registro = new RegistroHerramientas();
        ToolCallback[] envueltas = registro.envolver(new ToolCallback[]{herramienta("buscarProductos"), herramienta("agregarProducto")});

        registro.iniciar();
        String resultado = envueltas[0].call("{}");
        envueltas[1].call("{}");
        envueltas[0].call("{}");

        assertThat(resultado).isEqualTo("{\"ok\":\"buscarProductos\"}");
        assertThat(envueltas[0].getToolDefinition().name()).isEqualTo("buscarProductos");
        assertThat(registro.terminar()).containsExactly("buscarProductos", "agregarProducto", "buscarProductos");
        assertThat(registro.terminar()).isEmpty();
    }

    @Test
    void errorDeHerramienta_seDevuelveComoJsonLegibleParaElModelo() {
        RegistroHerramientas registro = new RegistroHerramientas();
        ToolCallback envuelta = registro.envolver(new ToolCallback[]{
            herramientaConError("agregarProducto", "No existe el pedido 101")})[0];

        registro.iniciar();
        String resultado = envuelta.call("{\"pedidoId\":101}");

        assertThat(resultado).isEqualTo("{\"error\":\"No existe el pedido 101\"}");
        assertThat(registro.terminar()).containsExactly("agregarProducto");
    }
}
