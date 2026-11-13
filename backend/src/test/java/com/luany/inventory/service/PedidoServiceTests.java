package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTests {

    private static final String CLAVE_RECEPCION = "9fc78ecb-d852-4875-9715-5f96f03414b2";

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private DetallePedidoRepository detallePedidoRepository;
    @Mock
    private RecepcionPedidoRepository recepcionPedidoRepository;
    @Mock
    private DetalleRecepcionPedidoRepository detalleRecepcionPedidoRepository;
    @Mock
    private ProveedorRepository proveedorRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private ProductoStockLockService productoStockLockService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private MovimientoService movimientoService;
    @Mock
    private MovimientoRepository movimientoRepository;

    @InjectMocks
    private PedidoService pedidoService;

    private Proveedor proveedor;
    private Producto producto;
    private Usuario usuario;
    private DetallePedido detalle;

    @BeforeEach
    void prepararDatos() {
        proveedor = new Proveedor();
        proveedor.setId(3L);
        proveedor.setNombre("Proveedor");
        proveedor.setEstado("ACTIVO");

        producto = new Producto();
        producto.setId(7L);
        producto.setNombre("Vestido");
        producto.setProveedor(proveedor);
        producto.setStockActual(4);

        usuario = new Usuario();
        usuario.setId(2L);
        usuario.setNombre("Almacén");
        usuario.setEmail("almacen@luany.com");

        Pedido pedido = new Pedido();
        pedido.setId(11L);
        pedido.setProveedor(proveedor);
        pedido.setUsuario(usuario);
        pedido.setEstado("PENDIENTE");

        detalle = new DetallePedido();
        detalle.setId(13L);
        detalle.setPedido(pedido);
        detalle.setProducto(producto);
        detalle.setCantidad(5);
        detalle.setPrecio(new BigDecimal("18.50"));
    }

    @Test
    void crearPedidoNoActualizaStockNiRegistraMovimiento() {
        when(proveedorRepository.findById(3L)).thenReturn(Optional.of(proveedor));
        when(usuarioRepository.buscarPorEmail("almacen@luany.com")).thenReturn(Optional.of(usuario));
        when(productoRepository.findById(7L)).thenReturn(Optional.of(producto));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> {
            Pedido pedido = invocation.getArgument(0);
            pedido.setId(11L);
            return pedido;
        });
        when(detallePedidoRepository.findByPedidoIdOrderById(11L)).thenReturn(List.of());
        when(recepcionPedidoRepository.findByPedidoIdOrderByFechaDescIdDesc(11L)).thenReturn(List.of());

        PedidoDetalleResponse respuesta = pedidoService.crear(crearRequest(5), "almacen@luany.com");

        assertEquals("PENDIENTE", respuesta.estado());
        assertEquals(4, producto.getStockActual());
        verify(detallePedidoRepository).saveAll(any());
        verifyNoInteractions(movimientoService);
    }

    @Test
    void detallePedidoCalculaSubtotalYTotalDeCincoUnidades() {
        when(pedidoRepository.findById(11L)).thenReturn(Optional.of(detalle.getPedido()));
        when(detallePedidoRepository.findByPedidoIdOrderById(11L)).thenReturn(List.of(detalle));
        when(detalleRecepcionPedidoRepository.totalRecibido(13L)).thenReturn(0L);
        when(recepcionPedidoRepository.findByPedidoIdOrderByFechaDescIdDesc(11L)).thenReturn(List.of());

        PedidoDetalleResponse respuesta = pedidoService.obtener(11L);

        assertEquals(new BigDecimal("92.50"), respuesta.productos().get(0).subtotal());
        assertEquals(new BigDecimal("92.50"), respuesta.total());
    }

    @Test
    void tableroIncluyeLosIdsDeProductosParaRelacionarAlertasConPedidosActivos() {
        Pedido pedido = detalle.getPedido();
        pedido.setFechaPedido(LocalDateTime.of(2026, 11, 4, 10, 0));
        pedido.setEstado("PARCIAL");
        when(pedidoRepository.listarConProveedorYUsuario(any(Sort.class))).thenReturn(List.of(pedido));
        when(detallePedidoRepository.findByPedidoIdOrderById(11L)).thenReturn(List.of(detalle));
        when(detallePedidoRepository.cantidadesSolicitadasPorPedido(List.of(11L)))
                .thenReturn(List.<Object[]>of(new Object[]{11L, 5L}));
        when(detalleRecepcionPedidoRepository.cantidadesRecibidasPorPedido(List.of(11L)))
                .thenReturn(List.<Object[]>of(new Object[]{11L, 2L}));
        when(detallePedidoRepository.productosPorPedido(List.of(11L)))
                .thenReturn(List.<Object[]>of(new Object[]{11L, 7L}));

        List<PedidoKanbanResponse> resultado = pedidoService.listarTablero(null, null, null, null);

        assertEquals(1, resultado.size());
        assertEquals("PARCIAL", resultado.get(0).estado());
        assertEquals(5, resultado.get(0).cantidadTotalSolicitada());
        assertEquals(2, resultado.get(0).cantidadRecibida());
        assertEquals(3, resultado.get(0).cantidadPendiente());
        assertEquals(List.of(7L), resultado.get(0).productoIds());
        assertEquals(4, producto.getStockActual());
        verifyNoInteractions(movimientoService);
    }

    @Test
    void crearPedidoRechazaProveedorInactivoSinCrearPedido() {
        proveedor.setEstado("INACTIVO");
        when(proveedorRepository.findById(3L)).thenReturn(Optional.of(proveedor));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.crear(crearRequest(5), "almacen@luany.com"));

        verify(pedidoRepository, never()).save(any(Pedido.class));
        verifyNoInteractions(movimientoService);
    }

    @Test
    void crearPedidoRechazaProductosDeOtroProveedor() {
        Proveedor otroProveedor = new Proveedor();
        otroProveedor.setId(99L);
        Producto productoOtro = new Producto();
        productoOtro.setId(7L);
        productoOtro.setNombre("Ajeno");
        productoOtro.setProveedor(otroProveedor);
        when(proveedorRepository.findById(3L)).thenReturn(Optional.of(proveedor));
        when(productoRepository.findById(7L)).thenReturn(Optional.of(productoOtro));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.crear(crearRequest(5), "almacen@luany.com"));
        verify(pedidoRepository, never()).save(any(Pedido.class));
        verifyNoInteractions(movimientoService);
    }

    @Test
    void recibirParcialActualizaEstadoYRegistraEntradaUnaSolaVez() {
        pedidoConDetalle("PENDIENTE");
        when(usuarioRepository.buscarPorEmail("almacen@luany.com")).thenReturn(Optional.of(usuario));
        when(detallePedidoRepository.findByPedidoIdOrderById(11L)).thenReturn(List.of(detalle));
        when(detalleRecepcionPedidoRepository.totalRecibido(13L)).thenReturn(0L, 2L);
        when(recepcionPedidoRepository.saveAndFlush(any(RecepcionPedido.class))).thenAnswer(invocation -> {
            RecepcionPedido recepcion = invocation.getArgument(0);
            recepcion.setId(21L);
            return recepcion;
        });
        Movimiento movimiento = new Movimiento();
        movimiento.setId(31L);
        when(movimientoService.registrar(any(MovimientoRequest.class), any())).thenReturn(movimiento);
        when(recepcionPedidoRepository.findByPedidoIdOrderByFechaDescIdDesc(11L)).thenReturn(List.of());

        PedidoDetalleResponse respuesta = pedidoService.recibir(11L, recepcionRequest(2), "almacen@luany.com");

        assertEquals("PARCIAL", respuesta.estado());
        verify(movimientoService).registrar(any(MovimientoRequest.class), org.mockito.ArgumentMatchers.eq("almacen@luany.com"));
        verify(detalleRecepcionPedidoRepository).save(any(DetalleRecepcionPedido.class));
        verify(pedidoRepository).save(any(Pedido.class));
    }

    @Test
    void recibirCantidadRestanteCompletaElPedido() {
        pedidoConDetalle("PARCIAL");
        when(usuarioRepository.buscarPorEmail("almacen@luany.com")).thenReturn(Optional.of(usuario));
        when(detallePedidoRepository.findByPedidoIdOrderById(11L)).thenReturn(List.of(detalle));
        when(detalleRecepcionPedidoRepository.totalRecibido(13L)).thenReturn(2L, 5L);
        when(recepcionPedidoRepository.saveAndFlush(any(RecepcionPedido.class))).thenAnswer(invocation -> {
            RecepcionPedido recepcion = invocation.getArgument(0);
            recepcion.setId(22L);
            return recepcion;
        });
        when(movimientoService.registrar(any(MovimientoRequest.class), any())).thenReturn(new Movimiento());
        when(recepcionPedidoRepository.findByPedidoIdOrderByFechaDescIdDesc(11L)).thenReturn(List.of());

        PedidoDetalleResponse respuesta = pedidoService.recibir(11L, recepcionRequest(3), "almacen@luany.com");

        assertEquals("RECIBIDO", respuesta.estado());
        ArgumentCaptor<MovimientoRequest> movimiento = ArgumentCaptor.forClass(MovimientoRequest.class);
        verify(movimientoService).registrar(movimiento.capture(), org.mockito.ArgumentMatchers.eq("almacen@luany.com"));
        assertEquals(TipoMovimiento.ENTRADA, movimiento.getValue().getTipo());
        assertEquals(3, movimiento.getValue().getCantidad());
        assertEquals(4, producto.getStockActual());
    }

    @Test
    void recepcionExitosaActualizaStockMedianteElServicioDeMovimientos() {
        pedidoConDetalle("PENDIENTE");
        when(usuarioRepository.buscarPorEmail("almacen@luany.com")).thenReturn(Optional.of(usuario));
        when(detallePedidoRepository.findByPedidoIdOrderById(11L)).thenReturn(List.of(detalle));
        when(detalleRecepcionPedidoRepository.totalRecibido(13L)).thenReturn(0L, 2L);
        when(recepcionPedidoRepository.saveAndFlush(any(RecepcionPedido.class))).thenAnswer(invocation -> {
            RecepcionPedido recepcion = invocation.getArgument(0);
            recepcion.setId(23L);
            return recepcion;
        });
        when(productoStockLockService.bloquear(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(producto));
        when(productoStockLockService.bloquear(7L)).thenReturn(producto);
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(invocation -> {
            Movimiento movimiento = invocation.getArgument(0);
            movimiento.setId(33L);
            return movimiento;
        });
        when(recepcionPedidoRepository.findByPedidoIdOrderByFechaDescIdDesc(11L)).thenReturn(List.of());
        PedidoService service = new PedidoService(
                pedidoRepository,
                detallePedidoRepository,
                recepcionPedidoRepository,
                detalleRecepcionPedidoRepository,
                proveedorRepository,
                productoRepository,
                productoStockLockService,
                usuarioRepository,
                new MovimientoService(movimientoRepository, productoStockLockService, usuarioRepository));

        service.recibir(11L, recepcionRequest(2), "almacen@luany.com");

        assertEquals(6, producto.getStockActual());
        ArgumentCaptor<Movimiento> movimiento = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(movimiento.capture());
        assertEquals(TipoMovimiento.ENTRADA, movimiento.getValue().getTipo());
        assertEquals(2, movimiento.getValue().getCantidad());
    }

    @Test
    void rechazaRecepcionPorEncimaDeLoPendienteSinCrearMovimiento() {
        pedidoConDetalle("PENDIENTE");
        when(usuarioRepository.buscarPorEmail("almacen@luany.com")).thenReturn(Optional.of(usuario));
        when(detallePedidoRepository.findByPedidoIdOrderById(11L)).thenReturn(List.of(detalle));
        when(detalleRecepcionPedidoRepository.totalRecibido(13L)).thenReturn(3L);

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.recibir(11L, recepcionRequest(3), "almacen@luany.com"));

        verifyNoInteractions(movimientoService);
        verify(recepcionPedidoRepository, never()).saveAndFlush(any(RecepcionPedido.class));
    }

    @Test
    void rechazaPedidoYaRecibidoYNoDuplicaStock() {
        pedidoConDetalle("RECIBIDO");

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.recibir(11L, recepcionRequest(1), "almacen@luany.com"));

        verifyNoInteractions(movimientoService);
        verifyNoInteractions(detallePedidoRepository, detalleRecepcionPedidoRepository, usuarioRepository);
    }

    @Test
    void repetirSolicitudConLaMismaClaveNoVuelveASumarStock() {
        pedidoConDetalle("PARCIAL");
        RecepcionPedido recepcionPrevia = new RecepcionPedido();
        recepcionPrevia.setClaveIdempotencia(CLAVE_RECEPCION);
        recepcionPrevia.setPedido(detalle.getPedido());
        when(recepcionPedidoRepository.findByClaveIdempotencia(CLAVE_RECEPCION))
                .thenReturn(Optional.of(recepcionPrevia));
        when(detallePedidoRepository.findByPedidoIdOrderById(11L)).thenReturn(List.of(detalle));
        when(detalleRecepcionPedidoRepository.totalRecibido(13L)).thenReturn(2L);
        when(recepcionPedidoRepository.findByPedidoIdOrderByFechaDescIdDesc(11L)).thenReturn(List.of());

        pedidoService.recibir(11L, recepcionRequest(2), "almacen@luany.com");

        verifyNoInteractions(movimientoService);
        verify(recepcionPedidoRepository, never()).saveAndFlush(any(RecepcionPedido.class));
    }

    @Test
    void rechazaDosLineasDeRecepcionParaElMismoDetalle() {
        pedidoConDetalle("PENDIENTE");
        RegistrarRecepcionRequest request = recepcionRequest(1);
        request.getProductos().add(recepcionRequest(1).getProductos().get(0));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.recibir(11L, request, "almacen@luany.com"));

        verifyNoInteractions(movimientoService);
    }

    @Test
    void validaEstadoAntesDeListarPedidos() {
        assertThrows(IllegalArgumentException.class, () -> pedidoService.listar(null, "CANCELADO"));
        verify(pedidoRepository, never()).listarConProveedorYUsuario(any(Sort.class));
    }

    @Test
    void filtraPedidoPorIdentificadorExactoYNoPorCoincidenciaParcial() {
        Pedido pedido12 = pedido(12L);
        Pedido pedido112 = pedido(112L);
        when(pedidoRepository.listarConProveedorYUsuario(any(Sort.class)))
                .thenReturn(List.of(pedido12, pedido112));
        when(detallePedidoRepository.findByPedidoIdOrderById(12L)).thenReturn(List.of());

        List<PedidoResumenResponse> resultado = pedidoService.listar(null, null, 12L);

        assertEquals(List.of(12L), resultado.stream().map(PedidoResumenResponse::id).toList());
    }

    private Pedido pedido(Long id) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setProveedor(proveedor);
        pedido.setUsuario(usuario);
        pedido.setEstado("PENDIENTE");
        return pedido;
    }

    @Test
    void tableroResumeCantidadesRealesYAplicaFiltrosSinCambiarEstado() {
        detalle.getPedido().setFechaPedido(LocalDateTime.of(2026, 11, 5, 10, 0));
        when(pedidoRepository.listarConProveedorYUsuario(any(Sort.class)))
                .thenReturn(List.of(detalle.getPedido()));
        when(detallePedidoRepository.findByPedidoIdOrderById(11L)).thenReturn(List.of(detalle));
        when(detallePedidoRepository.cantidadesSolicitadasPorPedido(List.of(11L)))
                .thenReturn(java.util.Collections.singletonList(new Object[]{11L, 5L}));
        when(detalleRecepcionPedidoRepository.cantidadesRecibidasPorPedido(List.of(11L)))
                .thenReturn(java.util.Collections.singletonList(new Object[]{11L, 2L}));

        var tablero = pedidoService.listarTablero(
                "proveedor", "PENDIENTE", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertEquals(1, tablero.size());
        assertEquals(5L, tablero.get(0).cantidadTotalSolicitada());
        assertEquals(2L, tablero.get(0).cantidadRecibida());
        assertEquals(3L, tablero.get(0).cantidadPendiente());
        assertEquals("PENDIENTE", tablero.get(0).estado());
        assertThrows(IllegalArgumentException.class, () -> pedidoService.listarTablero(
                null, null, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1)));
    }

    @Test
    void tableroVacioNoEjecutaConsultasDeAgregacion() {
        when(pedidoRepository.listarConProveedorYUsuario(any(Sort.class))).thenReturn(List.of());

        assertEquals(List.of(), pedidoService.listarTablero(null, null, null, null));

        verify(detallePedidoRepository, never()).cantidadesSolicitadasPorPedido(any());
        verify(detalleRecepcionPedidoRepository, never()).cantidadesRecibidasPorPedido(any());
    }

    private void pedidoConDetalle(String estado) {
        detalle.getPedido().setEstado(estado);
        when(pedidoRepository.buscarPorIdBloqueando(11L)).thenReturn(Optional.of(detalle.getPedido()));
        org.mockito.Mockito.lenient()
                .when(productoStockLockService.bloquear(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(producto));
        org.mockito.Mockito.lenient()
                .when(productoStockLockService.bloquear(7L))
                .thenReturn(producto);
    }

    private CrearPedidoRequest crearRequest(int cantidad) {
        CrearPedidoItemRequest item = new CrearPedidoItemRequest();
        item.setProductoId(7L);
        item.setCantidad(cantidad);
        item.setPrecio(new BigDecimal("18.50"));
        CrearPedidoRequest request = new CrearPedidoRequest();
        request.setProveedorId(3L);
        request.setProductos(List.of(item));
        return request;
    }

    private RegistrarRecepcionRequest recepcionRequest(int cantidad) {
        RegistrarRecepcionItemRequest item = new RegistrarRecepcionItemRequest();
        item.setDetallePedidoId(13L);
        item.setCantidad(cantidad);
        RegistrarRecepcionRequest request = new RegistrarRecepcionRequest();
        request.setClaveIdempotencia(CLAVE_RECEPCION);
        request.setProductos(new java.util.ArrayList<>(List.of(item)));
        return request;
    }
}
