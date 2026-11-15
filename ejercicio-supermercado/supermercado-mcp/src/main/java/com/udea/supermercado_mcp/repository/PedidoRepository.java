package com.udea.supermercado_mcp.repository;

import com.udea.supermercado_mcp.model.EstadoPedido;
import com.udea.supermercado_mcp.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByClienteTelefonoOrderByFechaCreacionDesc(String telefono);
    Optional<Pedido> findFirstByClienteTelefonoAndEstado(String telefono, EstadoPedido estado);
}
