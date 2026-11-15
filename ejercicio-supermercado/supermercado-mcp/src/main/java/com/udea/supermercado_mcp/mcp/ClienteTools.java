package com.udea.supermercado_mcp.mcp;

import com.udea.supermercado_mcp.dto.BusquedaCliente;
import com.udea.supermercado_mcp.dto.ClienteDto;
import com.udea.supermercado_mcp.service.ClienteService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Herramientas MCP para identificar y registrar clientes. */
@Component
public class ClienteTools {
    private final ClienteService clientes;

    public ClienteTools(ClienteService clientes) {
        this.clientes = clientes;
    }

    @Tool(description = "Busca un cliente por su número de celular. Úsala al inicio para identificar al cliente "
        + "antes de crear un pedido. Si no está registrado, la respuesta lo indica.")
    public BusquedaCliente buscarCliente(
        @ToolParam(description = "Celular colombiano de 10 dígitos, por ejemplo 3001234567") String telefono) {
        return clientes.buscar(telefono);
    }

    @Tool(description = "Registra un cliente nuevo con nombre, celular y dirección de entrega.")
    public ClienteDto registrarCliente(
        @ToolParam(description = "Nombre completo del cliente") String nombre,
        @ToolParam(description = "Celular colombiano de 10 dígitos") String telefono,
        @ToolParam(description = "Dirección completa con ciudad, por ejemplo 'Calle 50 # 40-20, Medellín'") String direccion) {
        return clientes.registrar(nombre, telefono, direccion);
    }
}
