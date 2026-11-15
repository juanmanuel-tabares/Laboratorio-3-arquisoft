package com.udea.supermercado_mcp.model;

import jakarta.persistence.*;

@Entity
@Table(name = "detalle_pedido")
public class DetallePedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;

    /** Precio congelado al agregar el producto: un cambio de precio posterior no altera el pedido. */
    @Column(name = "precio_unitario", nullable = false)
    private Long precioUnitario;

    @Column(nullable = false)
    private Long subtotal;

    protected DetallePedido() {}

    DetallePedido(Pedido pedido, Producto producto, int cantidad) {
        this.pedido = pedido;
        this.producto = producto;
        this.precioUnitario = producto.getPrecio();
        setCantidad(cantidad);
    }

    void setCantidad(int cantidad) {
        this.cantidad = cantidad;
        this.subtotal = precioUnitario * cantidad;
    }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public Producto getProducto() { return producto; }
    public Integer getCantidad() { return cantidad; }
    public Long getPrecioUnitario() { return precioUnitario; }
    public Long getSubtotal() { return subtotal; }
}
