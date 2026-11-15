package com.udea.supermercado_mcp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "pedido")
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "direccion_entrega", nullable = false, length = 200)
    private String direccionEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private EstadoPedido estado = EstadoPedido.BORRADOR;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", length = 20, columnDefinition = "varchar(20)")
    private MetodoPago metodoPago;

    @Column(nullable = false)
    private Long subtotal = 0L;

    @Column(name = "costo_domicilio", nullable = false)
    private Long costoDomicilio = 0L;

    @Column(nullable = false)
    private Long total = 0L;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_confirmacion")
    private LocalDateTime fechaConfirmacion;

    @Column(name = "entrega_estimada")
    private LocalDateTime entregaEstimada;

    @Version
    private Long version;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<DetallePedido> items = new ArrayList<>();

    protected Pedido() {}

    public Pedido(Cliente cliente, String direccionEntrega) {
        this.cliente = cliente;
        this.direccionEntrega = direccionEntrega;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Optional<DetallePedido> buscarItem(Long productoId) {
        return items.stream().filter(i -> i.getProducto().getId().equals(productoId)).findFirst();
    }

    /** Agrega unidades; si el producto ya está en el pedido, suma a la cantidad existente. */
    public DetallePedido agregar(Producto producto, int cantidad) {
        DetallePedido item = buscarItem(producto.getId()).orElse(null);
        if (item == null) {
            item = new DetallePedido(this, producto, cantidad);
            items.add(item);
        } else {
            item.setCantidad(item.getCantidad() + cantidad);
        }
        return item;
    }

    /** Fija la cantidad de un producto; 0 lo elimina del pedido. */
    public void fijarCantidad(Long productoId, int cantidad) {
        DetallePedido item = buscarItem(productoId)
            .orElseThrow(() -> new IllegalArgumentException("El producto " + productoId + " no está en el pedido " + id));
        if (cantidad == 0) {
            items.remove(item);
        } else {
            item.setCantidad(cantidad);
        }
    }

    public void recalcular(long subtotal, long costoDomicilio) {
        this.subtotal = subtotal;
        this.costoDomicilio = costoDomicilio;
        this.total = subtotal + costoDomicilio;
    }

    public void confirmar(MetodoPago metodoPago, LocalDateTime ahora, LocalDateTime entregaEstimada) {
        this.metodoPago = metodoPago;
        this.estado = EstadoPedido.CONFIRMADO;
        this.fechaConfirmacion = ahora;
        this.entregaEstimada = entregaEstimada;
    }

    public void cancelar() {
        this.estado = EstadoPedido.CANCELADO;
        this.entregaEstimada = null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Cliente getCliente() { return cliente; }
    public String getDireccionEntrega() { return direccionEntrega; }
    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public MetodoPago getMetodoPago() { return metodoPago; }
    public Long getSubtotal() { return subtotal; }
    public Long getCostoDomicilio() { return costoDomicilio; }
    public Long getTotal() { return total; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaConfirmacion() { return fechaConfirmacion; }
    public LocalDateTime getEntregaEstimada() { return entregaEstimada; }
    public List<DetallePedido> getItems() { return items; }
}
