package com.udea.supermercado_mcp.dto;

import com.udea.supermercado_mcp.model.Pedido;
import com.udea.supermercado_mcp.service.ReglasNegocio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Vista del pedido que recibe el modelo. Incluye campos "de ayuda" (faltaParaDomicilioGratis,
 * faltaParaPedidoMinimo) para que el chatbot pueda orientar al cliente sin hacer cálculos.
 */
public record PedidoDto(Long id, String estado, String cliente, String telefono, String direccionEntrega,
                        List<ItemPedidoDto> items, long subtotal, long costoDomicilio, long total,
                        long faltaParaDomicilioGratis, long faltaParaPedidoMinimo,
                        String metodoPago, String fechaCreacion, String entregaEstimada) {

    /**
     * Fechas como texto legible con a. m./p. m. explícito. Primero se enviaban como arreglos [2026, 11, 12, ...];
     * luego en 24 h ("04:09"), y el modelo le agregó "PM" por su cuenta a una entrega de madrugada.
     * Se arma a mano porque el locale es-CO de Java usa un espacio especial (U+202F) dentro de "a. m.".
     */
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd h:mm");

    public static PedidoDto de(Pedido p) {
        return new PedidoDto(p.getId(), p.getEstado().name(), p.getCliente().getNombre(),
            p.getCliente().getTelefono(), p.getDireccionEntrega(),
            p.getItems().stream().map(ItemPedidoDto::de).toList(),
            p.getSubtotal(), p.getCostoDomicilio(), p.getTotal(),
            Math.max(0, ReglasNegocio.DOMICILIO_GRATIS_DESDE - p.getSubtotal()),
            Math.max(0, ReglasNegocio.PEDIDO_MINIMO - p.getSubtotal()),
            p.getMetodoPago() == null ? null : p.getMetodoPago().name(),
            formatear(p.getFechaCreacion()), formatear(p.getEntregaEstimada()));
    }

    private static String formatear(LocalDateTime fecha) {
        if (fecha == null) return null;
        return fecha.format(FORMATO) + (fecha.getHour() < 12 ? " a. m." : " p. m.");
    }
}
