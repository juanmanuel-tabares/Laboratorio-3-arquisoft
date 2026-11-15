package com.udea.supermercado_mcp.dto;

import com.udea.supermercado_mcp.model.Cliente;

public record ClienteDto(Long id, String nombre, String telefono, String direccion) {
    public static ClienteDto de(Cliente c) {
        return new ClienteDto(c.getId(), c.getNombre(), c.getTelefono(), c.getDireccion());
    }
}
