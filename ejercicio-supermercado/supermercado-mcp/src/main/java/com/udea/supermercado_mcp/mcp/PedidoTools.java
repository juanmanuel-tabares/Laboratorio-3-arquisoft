package com.udea.supermercado_mcp.mcp;

import com.udea.supermercado_mcp.dto.PedidoDto;
import com.udea.supermercado_mcp.service.PedidoService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Herramientas MCP del ciclo de vida del pedido. Delegan toda la lógica en PedidoService.
 * Todas reciben el celular del cliente: el servidor verifica que el pedido le pertenezca.
 */
@Component
public class PedidoTools {
    private static final String TELEFONO = "Celular del cliente dueño del pedido (10 dígitos)";

    private final PedidoService pedidos;

    public PedidoTools(PedidoService pedidos) {
        this.pedidos = pedidos;
    }

    @Tool(description = "Crea un pedido (carrito en estado BORRADOR) para un cliente registrado. Si el cliente ya "
        + "tiene un pedido en BORRADOR, devuelve ese mismo pedido. Si no se indica dirección, usa la registrada.")
    public PedidoDto crearPedido(
        @ToolParam(description = "Celular del cliente registrado") String telefono,
        @ToolParam(description = "Dirección de entrega si es distinta a la registrada", required = false)
        String direccionEntrega) {
        return pedidos.crearPedido(telefono, direccionEntrega);
    }

    @Tool(description = "Agrega unidades de un producto a un pedido en BORRADOR; si el producto ya estaba, suma las "
        + "unidades. Valida que haya stock. Devuelve el pedido actualizado con subtotal, domicilio y total.")
    public PedidoDto agregarProducto(
        @ToolParam(description = "Id del pedido") Long pedidoId,
        @ToolParam(description = TELEFONO) String telefono,
        @ToolParam(description = "Id del producto, obtenido con buscarProductos") Long productoId,
        @ToolParam(description = "Unidades a agregar (1 a 30)") Integer cantidad) {
        return pedidos.agregarProducto(pedidoId, telefono, productoId, cantidad == null ? 0 : cantidad);
    }

    @Tool(description = "Cambia la cantidad de un producto que ya está en un pedido en BORRADOR. Usa cantidad 0 "
        + "para quitar el producto del pedido.")
    public PedidoDto actualizarCantidad(
        @ToolParam(description = "Id del pedido") Long pedidoId,
        @ToolParam(description = TELEFONO) String telefono,
        @ToolParam(description = "Id del producto") Long productoId,
        @ToolParam(description = "Nueva cantidad total (0 para quitarlo)") Integer cantidad) {
        return pedidos.actualizarCantidad(pedidoId, telefono, productoId, cantidad == null ? -1 : cantidad);
    }

    @Tool(description = "Muestra el detalle de un pedido del cliente: productos, subtotal, costo de domicilio, total, "
        + "estado, cuánto falta para el pedido mínimo y para el domicilio gratis.")
    public PedidoDto verPedido(
        @ToolParam(description = "Id del pedido") Long pedidoId,
        @ToolParam(description = TELEFONO) String telefono) {
        return pedidos.verPedido(pedidoId, telefono);
    }

    @Tool(description = "Confirma el pedido, descuenta el inventario y lo envía a despacho con hora estimada de "
        + "entrega. Llámala SOLO después de mostrar el resumen con el total y recibir la confirmación explícita "
        + "del cliente. El pago es contra entrega.")
    public PedidoDto confirmarPedido(
        @ToolParam(description = "Id del pedido") Long pedidoId,
        @ToolParam(description = TELEFONO) String telefono,
        @ToolParam(description = "Método de pago contra entrega: EFECTIVO, DATAFONO o NEQUI") String metodoPago) {
        return pedidos.confirmarPedido(pedidoId, telefono, metodoPago);
    }

    @Tool(description = "Cancela un pedido en BORRADOR o CONFIRMADO del cliente indicado y repone el inventario. "
        + "Pedidos EN_CAMINO o ENTREGADO no se pueden cancelar. Confirma con el cliente antes de usarla.")
    public PedidoDto cancelarPedido(
        @ToolParam(description = "Id del pedido") Long pedidoId,
        @ToolParam(description = TELEFONO) String telefono) {
        return pedidos.cancelarPedido(pedidoId, telefono);
    }

    @Tool(description = "Lista los pedidos de un cliente, del más reciente al más antiguo, con su estado y total.")
    public List<PedidoDto> listarPedidosCliente(
        @ToolParam(description = "Celular del cliente") String telefono) {
        return pedidos.listarPorCliente(telefono);
    }
}
