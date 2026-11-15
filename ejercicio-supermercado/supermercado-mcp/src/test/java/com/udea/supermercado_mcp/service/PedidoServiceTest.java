package com.udea.supermercado_mcp.service;

import com.udea.supermercado_mcp.dto.PedidoDto;
import com.udea.supermercado_mcp.model.*;
import com.udea.supermercado_mcp.repository.ClienteRepository;
import com.udea.supermercado_mcp.repository.PedidoRepository;
import com.udea.supermercado_mcp.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Reglas de negocio del pedido, sin base de datos (repositorios simulados y reloj fijo). */
class PedidoServiceTest {

    private static final String ANA = "3001234567";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-13T15:00:00Z"), ZoneId.of("America/Bogota"));

    private PedidoRepository pedidos;
    private ProductoRepository productos;
    private ClienteRepository clientes;
    private PedidoService service;

    private Cliente ana;
    private Producto leche;      // 4.600, stock 10
    private Producto cafe;       // 18.500, stock 30
    private Producto agotado;    // stock 0
    private Pedido carrito;

    @BeforeEach
    void setUp() {
        pedidos = mock(PedidoRepository.class);
        productos = mock(ProductoRepository.class);
        clientes = mock(ClienteRepository.class);
        service = new PedidoService(pedidos, productos, clientes, RELOJ);

        ana = new Cliente("Ana Gómez", "3001234567", "Calle 50 # 40-20, Medellín");
        ana.setId(1L);
        leche = producto(9L, "Leche entera", 4_600, 10);
        cafe = producto(26L, "Café molido", 18_500, 30);
        agotado = producto(35L, "Crema dental", 7_500, 0);
        carrito = new Pedido(ana, ana.getDireccion());
        carrito.setId(100L);

        when(pedidos.findById(100L)).thenReturn(Optional.of(carrito));
        when(pedidos.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
        when(clientes.findByTelefono("3001234567")).thenReturn(Optional.of(ana));
    }

    private Producto producto(long id, String nombre, long precio, int stock) {
        Producto p = new Producto(nombre, "Despensa", "Unidad", precio, stock);
        p.setId(id);
        when(productos.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void agregarProducto_sumaUnidadesYCobraDomicilioBajoElUmbral() {
        service.agregarProducto(100L, ANA, 9L, 2);
        PedidoDto dto = service.agregarProducto(100L, ANA, 9L, 1);

        assertThat(dto.items()).singleElement().satisfies(i -> {
            assertThat(i.cantidad()).isEqualTo(3);
            assertThat(i.subtotal()).isEqualTo(13_800);
        });
        assertThat(dto.subtotal()).isEqualTo(13_800);
        assertThat(dto.costoDomicilio()).isEqualTo(ReglasNegocio.COSTO_DOMICILIO);
        assertThat(dto.total()).isEqualTo(19_800);
        assertThat(dto.faltaParaPedidoMinimo()).isEqualTo(6_200);
    }

    @Test
    void domicilioGratisDesdeCienMil() {
        PedidoDto dto = service.agregarProducto(100L, ANA, 26L, 6);   // 6 x 18.500 = 111.000

        assertThat(dto.costoDomicilio()).isZero();
        assertThat(dto.total()).isEqualTo(111_000);
        assertThat(dto.faltaParaDomicilioGratis()).isZero();
    }

    @Test
    void agregarProducto_masQueElStock_lanzaError() {
        service.agregarProducto(100L, ANA, 9L, 8);

        assertThatThrownBy(() -> service.agregarProducto(100L, ANA, 9L, 3))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Solo quedan 10");
    }

    @Test
    void agregarProducto_agotado_lanzaError() {
        assertThatThrownBy(() -> service.agregarProducto(100L, ANA, 35L, 1))
            .hasMessageContaining("agotado");
    }

    @Test
    void agregarProducto_cantidadFueraDeRango_lanzaError() {
        assertThatThrownBy(() -> service.agregarProducto(100L, ANA, 9L, 0)).hasMessageContaining("entre 1 y 30");
        assertThatThrownBy(() -> service.agregarProducto(100L, ANA, 26L, 31)).hasMessageContaining("entre 1 y 30");
    }

    @Test
    void pedidoConfirmado_noSePuedeModificar() {
        carrito.setEstado(EstadoPedido.CONFIRMADO);

        assertThatThrownBy(() -> service.agregarProducto(100L, ANA, 9L, 1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ya no se puede modificar");
    }

    @Test
    void actualizarCantidadEnCero_quitaElProducto() {
        service.agregarProducto(100L, ANA, 9L, 2);
        service.agregarProducto(100L, ANA, 26L, 1);

        PedidoDto dto = service.actualizarCantidad(100L, ANA, 9L, 0);

        assertThat(dto.items()).extracting("producto").containsExactly("Café molido");
        assertThat(dto.subtotal()).isEqualTo(18_500);
    }

    @Test
    void confirmar_bajoElPedidoMinimo_lanzaErrorConLoQueFalta() {
        service.agregarProducto(100L, ANA, 9L, 2);   // 9.200

        assertThatThrownBy(() -> service.confirmarPedido(100L, ANA, "EFECTIVO"))
            .hasMessageContaining("pedido mínimo")
            .hasMessageContaining("faltan $10800");
        assertThat(leche.getStock()).isEqualTo(10);
    }

    @Test
    void confirmar_descuentaStockYCalculaEntrega() {
        service.agregarProducto(100L, ANA, 26L, 2);  // 37.000
        service.agregarProducto(100L, ANA, 9L, 1);

        PedidoDto dto = service.confirmarPedido(100L, ANA, "nequi");

        assertThat(dto.estado()).isEqualTo("CONFIRMADO");
        assertThat(dto.metodoPago()).isEqualTo("NEQUI");
        assertThat(cafe.getStock()).isEqualTo(28);
        assertThat(leche.getStock()).isEqualTo(9);
        assertThat(dto.entregaEstimada()).isEqualTo("2026-11-13 11:00 a. m."); // 10:00 Bogotá + 60 min
    }

    @Test
    void confirmar_metodoDePagoInvalido_listaLasOpciones() {
        service.agregarProducto(100L, ANA, 26L, 2);

        assertThatThrownBy(() -> service.confirmarPedido(100L, ANA, "tarjeta de crédito"))
            .hasMessageContaining("EFECTIVO, DATAFONO, NEQUI");
    }

    @Test
    void cancelarConfirmado_reponeElInventario() {
        service.agregarProducto(100L, ANA, 26L, 2);
        service.confirmarPedido(100L, ANA, "EFECTIVO");

        PedidoDto dto = service.cancelarPedido(100L, "300 123 4567");

        assertThat(dto.estado()).isEqualTo("CANCELADO");
        assertThat(cafe.getStock()).isEqualTo(30);
    }

    @Test
    void cancelar_conTelefonoDeOtroCliente_lanzaError() {
        assertThatThrownBy(() -> service.cancelarPedido(100L, "3109998877"))
            .hasMessageContaining("no pertenece");
    }

    @Test
    void cancelar_pedidoEntregado_lanzaError() {
        carrito.setEstado(EstadoPedido.ENTREGADO);

        assertThatThrownBy(() -> service.cancelarPedido(100L, "3001234567"))
            .hasMessageContaining("ya no se puede cancelar");
    }

    @Test
    void crearPedido_reutilizaElCarritoAbierto() {
        when(pedidos.findFirstByClienteTelefonoAndEstado("3001234567", EstadoPedido.BORRADOR))
            .thenReturn(Optional.of(carrito));

        PedidoDto dto = service.crearPedido("+57 3001234567", null);

        assertThat(dto.id()).isEqualTo(100L);
        verify(pedidos, never()).save(any());
    }

    @Test
    void crearPedido_clienteNoRegistrado_lanzaError() {
        when(clientes.findByTelefono("3150000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crearPedido("3150000000", null))
            .hasMessageContaining("Regístralo");
    }

    @Test
    void operarPedidoAjeno_lanzaErrorAunqueElIdExista() {
        assertThatThrownBy(() -> service.verPedido(100L, "3109998877")).hasMessageContaining("no pertenece");
        assertThatThrownBy(() -> service.agregarProducto(100L, "3109998877", 9L, 1)).hasMessageContaining("no pertenece");
        assertThatThrownBy(() -> service.confirmarPedido(100L, "3109998877", "EFECTIVO")).hasMessageContaining("no pertenece");
        assertThat(carrito.getItems()).isEmpty();
    }

    @Test
    void verPedido_delDuenio_aceptaTelefonoConFormato() {
        assertThat(service.verPedido(100L, "+57 300 123 4567").id()).isEqualTo(100L);
    }
}
