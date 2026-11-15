package com.udea.supermercado_mcp;

import com.udea.supermercado_mcp.mcp.CatalogoTools;
import com.udea.supermercado_mcp.mcp.ClienteTools;
import com.udea.supermercado_mcp.mcp.PedidoTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class McpConfig {

    /** Publica como herramientas MCP todos los métodos @Tool de las tres clases. */
    @Bean
    public ToolCallbackProvider supermercadoTools(CatalogoTools catalogo, ClienteTools clientes, PedidoTools pedidos) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(catalogo, clientes, pedidos)
            .build();
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }
}
