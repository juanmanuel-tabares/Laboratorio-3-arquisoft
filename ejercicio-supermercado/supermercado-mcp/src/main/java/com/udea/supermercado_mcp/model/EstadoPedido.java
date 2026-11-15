package com.udea.supermercado_mcp.model;

/** Ciclo de vida de un pedido. Solo un pedido en BORRADOR (el "carrito") se puede modificar. */
public enum EstadoPedido {
    BORRADOR, CONFIRMADO, EN_CAMINO, ENTREGADO, CANCELADO
}
