package com.udea.supermercado_mcp.repository;

import com.udea.supermercado_mcp.model.Producto;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Búsqueda por texto y/o categoría. La colación utf8mb4_unicode_ci de MySQL hace que
     * el LIKE ignore mayúsculas y tildes ("limon" encuentra "Limón Tahití").
     */
    @Query("""
        select p from Producto p
        where p.activo = true
          and (:texto is null or p.nombre like concat('%', :texto, '%'))
          and (:categoria is null or p.categoria = :categoria)
        order by p.categoria, p.nombre
        """)
    List<Producto> buscar(@Param("texto") String texto, @Param("categoria") String categoria, Limit limit);

    @Query("select distinct p.categoria from Producto p where p.activo = true order by p.categoria")
    List<String> categorias();
}
