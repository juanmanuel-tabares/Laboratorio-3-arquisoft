package com.udea.tarea_mcp;

import com.udea.tarea_mcp.service.TareaToolsService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TareaMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(TareaMcpApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider tareaTools(TareaToolsService tools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(tools)
            .build();
    }
}
