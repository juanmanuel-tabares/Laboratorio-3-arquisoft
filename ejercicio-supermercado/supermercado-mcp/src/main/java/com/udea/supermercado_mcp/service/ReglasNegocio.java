package com.udea.supermercado_mcp.service;

import java.time.Duration;
import java.util.regex.Pattern;

/** Reglas de negocio del domicilio, centralizadas para que el servidor (y no el modelo) las haga cumplir. */
public final class ReglasNegocio {
    public static final long COSTO_DOMICILIO = 6_000;
    public static final long DOMICILIO_GRATIS_DESDE = 100_000;
    public static final long PEDIDO_MINIMO = 20_000;
    public static final int MAX_UNIDADES_POR_PRODUCTO = 30;
    public static final Duration TIEMPO_ENTREGA = Duration.ofMinutes(60);

    private static final Pattern CELULAR_CO = Pattern.compile("^3\\d{9}$");

    private ReglasNegocio() {}

    public static long costoDomicilio(long subtotal) {
        if (subtotal == 0) return 0;
        return subtotal >= DOMICILIO_GRATIS_DESDE ? 0 : COSTO_DOMICILIO;
    }

    /** Normaliza ("300 123 4567", "+57 3001234567") y valida un celular colombiano de 10 dígitos. */
    public static String normalizarTelefono(String telefono) {
        if (telefono == null) {
            throw new IllegalArgumentException("El teléfono es obligatorio");
        }
        String digitos = telefono.replaceAll("[\\s\\-()]", "");
        if (digitos.startsWith("+57")) digitos = digitos.substring(3);
        if (!CELULAR_CO.matcher(digitos).matches()) {
            throw new IllegalArgumentException(
                "El teléfono '" + telefono + "' no es válido: debe ser un celular colombiano de 10 dígitos que empiece por 3");
        }
        return digitos;
    }

    public static void validarCantidad(int cantidad, boolean permiteCero) {
        int minimo = permiteCero ? 0 : 1;
        if (cantidad < minimo || cantidad > MAX_UNIDADES_POR_PRODUCTO) {
            throw new IllegalArgumentException(
                "La cantidad debe estar entre " + minimo + " y " + MAX_UNIDADES_POR_PRODUCTO + " unidades");
        }
    }
}
