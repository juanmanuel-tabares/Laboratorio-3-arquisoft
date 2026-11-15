package com.udea.supermercado_mcp.service;

import com.udea.supermercado_mcp.dto.PedidoDto;
import com.udea.supermercado_mcp.model.*;
import com.udea.supermercado_mcp.repository.ClienteRepository;
import com.udea.supermercado_mcp.repository.PedidoRepository;
import com.udea.supermercado_mcp.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Casos de uso del pedido. Todas las validaciones viven aquí: el modelo de lenguaje puede
 * equivocarse o ser manipulado, así que el servidor nunca confía en lo que "decidió" el chatbot.
 */
@Service
@Transactional
public class PedidoService {
    private final PedidoRepository pedidos;
    private final ProductoRepository productos;
    private final ClienteRepository clientes;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidos, ProductoRepository productos,
                         ClienteRepository clientes, Clock clock) {
        this.pedidos = pedidos;
        this.productos = productos;
        this.clientes = clientes;
        this.clock = clock;
    }

    public PedidoDto crearPedido(String telefono, String direccionEntrega) {
        String tel = ReglasNegocio.normalizarTelefono(telefono);
        Cliente cliente = clientes.findByTelefono(tel)
            .orElseThrow(() -> new IllegalArgumentException(
                "No hay un cliente con el teléfono " + tel + ". Regístralo antes de crear el pedido."));
        // Un cliente tiene como máximo un carrito abierto: evita pedidos duplicados en la conversación.
        Pedido pedido = pedidos.findFirstByClienteTelefonoAndEstado(tel, EstadoPedido.BORRADOR)
            .orElseGet(() -> pedidos.save(new Pedido(cliente,
                direccionEntrega == null || direccionEntrega.isBlank() ? cliente.getDireccion() : direccionEntrega.trim())));
        return PedidoDto.de(pedido);
    }

    public PedidoDto agregarProducto(Long pedidoId, String telefono, Long productoId, int cantidad) {
        ReglasNegocio.validarCantidad(cantidad, false);
        Pedido pedido = pedidoEditable(pedidoId, telefono);
        Producto producto = productoActivo(productoId);
        int yaEnPedido = pedido.buscarItem(productoId).map(DetallePedido::getCantidad).orElse(0);
        validarStock(producto, yaEnPedido + cantidad);
        ReglasNegocio.validarCantidad(yaEnPedido + cantidad, false);
        pedido.agregar(producto, cantidad);
        return guardarRecalculado(pedido);
    }

    public PedidoDto actualizarCantidad(Long pedidoId, String telefono, Long productoId, int cantidad) {
        ReglasNegocio.validarCantidad(cantidad, true);
        Pedido pedido = pedidoEditable(pedidoId, telefono);
        if (cantidad > 0) {
            validarStock(productoActivo(productoId), cantidad);
        }
        pedido.fijarCantidad(productoId, cantidad);
        return guardarRecalculado(pedido);
    }

    @Transactional(readOnly = true)
    public PedidoDto verPedido(Long pedidoId, String telefono) {
        return PedidoDto.de(pedidoDelCliente(pedidoId, telefono));
    }

    public PedidoDto confirmarPedido(Long pedidoId, String telefono, String metodoPago) {
        Pedido pedido = pedidoEditable(pedidoId, telefono);
        MetodoPago metodo = metodoPago(metodoPago);
        if (pedido.getItems().isEmpty()) {
            throw new IllegalStateException("El pedido " + pedidoId + " no tiene productos");
        }
        if (pedido.getSubtotal() < ReglasNegocio.PEDIDO_MINIMO) {
            throw new IllegalStateException("El pedido mínimo es de $" + ReglasNegocio.PEDIDO_MINIMO
                + " en productos; faltan $" + (ReglasNegocio.PEDIDO_MINIMO - pedido.getSubtotal()));
        }
        // Se revalida y descuenta el stock en la misma transacción (con @Version en Producto).
        pedido.getItems().forEach(i -> i.getProducto().descontarStock(i.getCantidad()));
        LocalDateTime ahora = LocalDateTime.now(clock);
        pedido.confirmar(metodo, ahora, ahora.plus(ReglasNegocio.TIEMPO_ENTREGA));
        return PedidoDto.de(pedidos.save(pedido));
    }

    public PedidoDto cancelarPedido(Long pedidoId, String telefono) {
        Pedido pedido = pedidoDelCliente(pedidoId, telefono);
        switch (pedido.getEstado()) {
            case BORRADOR -> { }
            case CONFIRMADO -> pedido.getItems().forEach(i -> i.getProducto().reponerStock(i.getCantidad()));
            default -> throw new IllegalStateException(
                "El pedido " + pedidoId + " está " + pedido.getEstado() + " y ya no se puede cancelar");
        }
        pedido.cancelar();
        return PedidoDto.de(pedidos.save(pedido));
    }

    @Transactional(readOnly = true)
    public List<PedidoDto> listarPorCliente(String telefono) {
        return pedidos.findByClienteTelefonoOrderByFechaCreacionDesc(ReglasNegocio.normalizarTelefono(telefono))
            .stream().map(PedidoDto::de).toList();
    }

    // ----------------------------------------------------------------- auxiliares

    private Pedido pedido(Long pedidoId) {
        if (pedidoId == null) throw new IllegalArgumentException("El id del pedido es obligatorio");
        return pedidos.findById(pedidoId)
            .orElseThrow(() -> new IllegalArgumentException("No existe el pedido " + pedidoId));
    }

    /**
     * Control de acceso a nivel de objeto: toda operación sobre un pedido exige el celular de su dueño.
     * Sin esto, cualquiera que adivinara un id podía ver o modificar pedidos ajenos (IDOR).
     */
    private Pedido pedidoDelCliente(Long pedidoId, String telefono) {
        Pedido pedido = pedido(pedidoId);
        String tel = ReglasNegocio.normalizarTelefono(telefono);
        if (!pedido.getCliente().getTelefono().equals(tel)) {
            throw new IllegalArgumentException("El pedido " + pedidoId + " no pertenece al teléfono " + tel);
        }
        return pedido;
    }

    private Pedido pedidoEditable(Long pedidoId, String telefono) {
        Pedido pedido = pedidoDelCliente(pedidoId, telefono);
        if (pedido.getEstado() != EstadoPedido.BORRADOR) {
            throw new IllegalStateException("El pedido " + pedidoId + " está " + pedido.getEstado()
                + " y ya no se puede modificar");
        }
        return pedido;
    }

    private Producto productoActivo(Long productoId) {
        if (productoId == null) throw new IllegalArgumentException("El id del producto es obligatorio");
        Producto producto = productos.findById(productoId)
            .orElseThrow(() -> new IllegalArgumentException("No existe el producto " + productoId));
        if (!Boolean.TRUE.equals(producto.getActivo())) {
            throw new IllegalArgumentException("El producto " + producto.getNombre() + " no está a la venta");
        }
        return producto;
    }

    private static void validarStock(Producto producto, int cantidadTotal) {
        if (producto.getStock() == 0) {
            throw new IllegalStateException(producto.getNombre() + " está agotado");
        }
        if (cantidadTotal > producto.getStock()) {
            throw new IllegalStateException("Solo quedan " + producto.getStock() + " unidades de " + producto.getNombre());
        }
    }

    private static MetodoPago metodoPago(String valor) {
        try {
            return MetodoPago.valueOf(valor == null ? "" : valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Método de pago no válido: '" + valor + "'. Opciones (contra entrega): "
                + Arrays.toString(MetodoPago.values()));
        }
    }

    private PedidoDto guardarRecalculado(Pedido pedido) {
        long subtotal = pedido.getItems().stream().mapToLong(DetallePedido::getSubtotal).sum();
        pedido.recalcular(subtotal, ReglasNegocio.costoDomicilio(subtotal));
        return PedidoDto.de(pedidos.save(pedido));
    }
}
