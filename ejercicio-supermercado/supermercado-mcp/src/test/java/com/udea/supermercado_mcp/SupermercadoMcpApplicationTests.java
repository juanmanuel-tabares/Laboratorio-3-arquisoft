package com.udea.supermercado_mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Levanta el contexto completo contra MySQL: con ddl-auto=validate, si las entidades no
 * coinciden con schema.sql la prueba falla. También verifica las herramientas MCP publicadas.
 */
@SpringBootTest
class SupermercadoMcpApplicationTests {

    @Autowired
    private ToolCallbackProvider supermercadoTools;

    @Test
    void publicaLasOnceHerramientasMcp() {
        assertThat(Arrays.stream(supermercadoTools.getToolCallbacks())
                .map(ToolCallback::getToolDefinition).map(d -> d.name()))
            .containsExactlyInAnyOrder(
                "listarCategorias", "buscarProductos",
                "buscarCliente", "registrarCliente",
                "crearPedido", "agregarProducto", "actualizarCantidad", "verPedido",
                "confirmarPedido", "cancelarPedido", "listarPedidosCliente");
    }
}
