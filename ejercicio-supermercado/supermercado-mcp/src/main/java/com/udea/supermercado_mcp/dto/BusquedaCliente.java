package com.udea.supermercado_mcp.dto;

/** Resultado de buscar un cliente: no lanzar error cuando no existe, sino indicar el siguiente paso. */
public record BusquedaCliente(boolean registrado, ClienteDto cliente, String mensaje) {}
