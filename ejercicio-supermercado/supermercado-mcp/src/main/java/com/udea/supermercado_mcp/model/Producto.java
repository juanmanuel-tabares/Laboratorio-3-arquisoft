package com.udea.supermercado_mcp.model;

import jakarta.persistence.*;

@Entity
@Table(name = "producto")
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 60)
    private String categoria;

    @Column(nullable = false, length = 60)
    private String presentacion;

    /** Precio en pesos colombianos (COP). */
    @Column(nullable = false)
    private Long precio;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Boolean activo = true;

    /** Bloqueo optimista: evita vender dos veces la última unidad en confirmaciones simultáneas. */
    @Version
    private Long version;

    protected Producto() {}

    public Producto(String nombre, String categoria, String presentacion, long precio, int stock) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.presentacion = presentacion;
        this.precio = precio;
        this.stock = stock;
    }

    public void descontarStock(int cantidad) {
        if (cantidad > stock) {
            throw new IllegalStateException("Stock insuficiente de " + nombre + ": quedan " + stock + " unidades");
        }
        stock -= cantidad;
    }

    public void reponerStock(int cantidad) {
        stock += cantidad;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public String getCategoria() { return categoria; }
    public String getPresentacion() { return presentacion; }
    public Long getPrecio() { return precio; }
    public Integer getStock() { return stock; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
}
