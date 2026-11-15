package com.udea.supermercado_mcp.service;

import com.udea.supermercado_mcp.dto.BusquedaCliente;
import com.udea.supermercado_mcp.dto.ClienteDto;
import com.udea.supermercado_mcp.model.Cliente;
import com.udea.supermercado_mcp.repository.ClienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {
    private final ClienteRepository clientes;

    public ClienteService(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    @Transactional(readOnly = true)
    public BusquedaCliente buscar(String telefono) {
        String tel = ReglasNegocio.normalizarTelefono(telefono);
        return clientes.findByTelefono(tel)
            .map(c -> new BusquedaCliente(true, ClienteDto.de(c), "Cliente registrado"))
            .orElseGet(() -> new BusquedaCliente(false, null,
                "No hay un cliente con el teléfono " + tel + ". Pide su nombre y dirección para registrarlo."));
    }

    @Transactional
    public ClienteDto registrar(String nombre, String telefono, String direccion) {
        String tel = ReglasNegocio.normalizarTelefono(telefono);
        if (nombre == null || nombre.trim().length() < 3) {
            throw new IllegalArgumentException("El nombre es obligatorio (mínimo 3 caracteres)");
        }
        if (direccion == null || direccion.trim().length() < 8) {
            throw new IllegalArgumentException("La dirección es obligatoria y debe ser completa (ej. Calle 50 # 40-20, Medellín)");
        }
        if (clientes.findByTelefono(tel).isPresent()) {
            throw new IllegalStateException("Ya existe un cliente registrado con el teléfono " + tel);
        }
        return ClienteDto.de(clientes.save(new Cliente(nombre.trim(), tel, direccion.trim())));
    }
}
