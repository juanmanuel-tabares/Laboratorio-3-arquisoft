package com.udea.agente_mcp;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.mcp.McpToolset;
import com.google.adk.tools.mcp.StreamableHttpServerParameters;
import java.time.Duration;
import java.util.List;

public class TareaAgent {
    public static BaseAgent ROOT_AGENT = buildAgent();

    private static BaseAgent buildAgent() {
        StreamableHttpServerParameters params =
            StreamableHttpServerParameters.builder()
                .url("http://localhost:8088/mcp")
                .timeout(Duration.ofSeconds(30))
                .build();

        McpToolset mcpToolset = new McpToolset(params);

        try {
            List<BaseTool> mcpTools = mcpToolset.getTools(null)
                .toList()
                .blockingGet();

            System.out.println("Herramientas MCP descubiertas: " + mcpTools.size());

            return LlmAgent.builder()
                .name("TareaAgent")
                // gemini-2.5-flash (el de la guía) ya no está disponible para claves nuevas (HTTP 404).
                // Se puede cambiar sin recompilar con la variable de entorno GEMINI_MODEL.
                .model(System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.6-flash"))
                .description("Agente para administrar tareas")
                .instruction("Usa las herramientas MCP de tareas para consultar, crear, actualizar y administrar "
                    + "pendientes. Antes de eliminar, confirma el identificador con el usuario.")
                .tools(mcpTools)
                .build();
        } catch (Exception e) {
            throw new IllegalStateException(
                "No fue posible conectar con el servidor MCP en http://localhost:8088/mcp", e);
        }
    }
}
