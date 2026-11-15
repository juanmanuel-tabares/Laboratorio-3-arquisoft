package com.udea.tarea_mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TareaMcpApplicationTests {

    @Autowired
    private ToolCallbackProvider tareaTools;

    @Test
    void contextLoads() {
    }

    @Test
    void publicaLasNueveHerramientasMcp() {
        assertThat(Arrays.stream(tareaTools.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .map(d -> d.name()))
            .containsExactlyInAnyOrder(
                "listarTareas", "listarPendientes", "listarCompletadas", "contarTareas", "buscarTareas",
                "crearTarea", "marcarCompletada", "actualizarTarea", "eliminarTarea");
    }
}
