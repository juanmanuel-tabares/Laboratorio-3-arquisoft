package com.udea.chatbot_supermercado.config;

import com.udea.chatbot_supermercado.chat.AprobacionesPendientes;
import com.udea.chatbot_supermercado.chat.RegistroHerramientas;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class ChatConfig {

    /** Memoria por conversación: conserva los últimos 40 mensajes (incluye las respuestas de las herramientas). */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(40).build();
    }

    /**
     * ChatClient = modelo (Gemini) + instrucciones del asistente + herramientas MCP + memoria.
     * mcpTools lo crea el starter del cliente MCP a partir del tools/list del servidor del supermercado.
     * confirmarPedido y cancelarPedido pasan por AprobacionesPendientes: el modelo solo puede proponerlas.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 ToolCallbackProvider mcpTools,
                                 RegistroHerramientas registro,
                                 AprobacionesPendientes aprobaciones,
                                 ChatMemory chatMemory,
                                 @Value("classpath:prompts/sistema.st") Resource instrucciones) {
        return builder
            .defaultSystem(instrucciones)
            .defaultToolCallbacks(aprobaciones.proteger(registro.envolver(mcpTools.getToolCallbacks())))
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
    }
}
