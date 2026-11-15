package com.udea.supermercado_mcp.dto;

import com.udea.supermercado_mcp.model.DetallePedido;

public record ItemPedidoDto(Long productoId, String producto, String presentacion,
                            int cantidad, long precioUnitario, long subtotal) {
    public static ItemPedidoDto de(DetallePedido d) {
        return new ItemPedidoDto(d.getProducto().getId(), d.getProducto().getNombre(),
            d.getProducto().getPresentacion(), d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal());
    }
}
