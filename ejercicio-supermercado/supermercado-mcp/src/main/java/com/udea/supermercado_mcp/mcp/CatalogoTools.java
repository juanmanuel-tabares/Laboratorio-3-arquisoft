package com.udea.supermercado_mcp.mcp;

import com.udea.supermercado_mcp.dto.ProductoDto;
import com.udea.supermercado_mcp.repository.ProductoRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Herramientas MCP de consulta del catálogo (solo lectura). */
@Component
@Transactional(readOnly = true)
public class CatalogoTools {
    private final ProductoRepository productos;

    public CatalogoTools(ProductoRepository productos) {
        this.productos = productos;
    }

    @Tool(description = "Lista las categorías de productos que vende el supermercado.")
    public List<String> listarCategorias() {
        return productos.categorias();
    }

    @Tool(description = "Busca productos del catálogo por nombre y/o categoría. Devuelve id, precio en pesos "
        + "colombianos (COP), presentación, stock y si está disponible. Usa el id devuelto para agregar productos "
        + "a un pedido. Sin filtros devuelve los primeros 20 productos.")
    public List<ProductoDto> buscarProductos(
        @ToolParam(description = "Texto a buscar en el nombre, por ejemplo 'leche' o 'arroz'", required = false)
        String texto,
        @ToolParam(description = "Categoría exacta, tal como la devuelve listarCategorias", required = false)
        String categoria) {
        return productos.buscar(vacioANull(texto), vacioANull(categoria), Limit.of(20))
            .stream().map(ProductoDto::de).toList();
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
