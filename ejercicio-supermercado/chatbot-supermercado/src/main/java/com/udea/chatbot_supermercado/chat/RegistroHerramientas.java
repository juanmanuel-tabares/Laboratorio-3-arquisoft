package com.udea.chatbot_supermercado.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decorador de las herramientas MCP con dos responsabilidades:
 * <ol>
 *   <li>Anotar qué herramientas invoca el modelo en cada respuesta (la interfaz las muestra). Spring AI
 *       ejecuta las herramientas en el hilo de la petición, así que basta un ThreadLocal.</li>
 *   <li>Convertir los errores de herramienta en JSON {"error": "..."}. Cuando el servidor MCP responde con
 *       isError (p. ej. "No existe el pedido 101"), el cliente MCP lanza una excepción y el conector de Gemini
 *       intenta leer ese texto como JSON y aborta todo el turno. Devolviendo JSON, el modelo ve el error,
 *       lo explica al cliente y puede corregirse.</li>
 * </ol>
 */
@Component
public class RegistroHerramientas {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern TEXTO_MCP = Pattern.compile("text=(.*?), meta=", Pattern.DOTALL);

    private final ThreadLocal<List<String>> llamadas = ThreadLocal.withInitial(ArrayList::new);

    public void iniciar() {
        llamadas.get().clear();
    }

    public List<String> terminar() {
        List<String> resultado = List.copyOf(llamadas.get());
        llamadas.remove();
        return resultado;
    }

    public ToolCallback[] envolver(ToolCallback[] herramientas) {
        return Arrays.stream(herramientas).map(Rastreada::new).toArray(ToolCallback[]::new);
    }

    /** Extrae el mensaje legible de un error de herramienta MCP. */
    static String mensajeDeError(Throwable error) {
        String mensaje = error.getMessage();
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("text=")) {
                mensaje = t.getMessage();
            }
        }
        if (mensaje == null) return "Error al ejecutar la herramienta";
        Matcher m = TEXTO_MCP.matcher(mensaje);
        return m.find() ? m.group(1) : mensaje;
    }

    static String comoJsonDeError(Throwable error) {
        try {
            return JSON.writeValueAsString(Map.of("error", mensajeDeError(error)));
        } catch (Exception e) {
            return "{\"error\":\"Error al ejecutar la herramienta\"}";
        }
    }

    private final class Rastreada implements ToolCallback {
        private final ToolCallback delegada;

        private Rastreada(ToolCallback delegada) {
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
            llamadas.get().add(getToolDefinition().name());
            try {
                return contexto == null ? delegada.call(argumentos) : delegada.call(argumentos, contexto);
            } catch (RuntimeException e) {
                return comoJsonDeError(e);
            }
        }
    }
}
