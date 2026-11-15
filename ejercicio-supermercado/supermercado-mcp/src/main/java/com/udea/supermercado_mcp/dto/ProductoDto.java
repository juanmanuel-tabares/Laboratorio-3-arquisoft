package com.udea.supermercado_mcp.dto;

import com.udea.supermercado_mcp.model.Producto;

public record ProductoDto(Long id, String nombre, String categoria, String presentacion,
                          long precio, int stock, boolean disponible) {
    public static ProductoDto de(Producto p) {
        return new ProductoDto(p.getId(), p.getNombre(), p.getCategoria(), p.getPresentacion(),
            p.getPrecio(), p.getStock(), p.getStock() > 0);
    }
}
