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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotaVentaServiceTests {

    @Mock
    private NotaVentaRepository notaVentaRepository;
    @Mock
    private DetalleVentaRepository detalleVentaRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private VentaProductoLockRepository ventaProductoLockRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private MovimientoService movimientoService;

    @InjectMocks
    private NotaVentaService notaVentaService;

    private Cliente cliente;
    private Usuario usuario;
    private Producto producto;

    @BeforeEach
    void prepararDatos() {
        cliente = new Cliente();
        cliente.setId(5L);
        cliente.setNombre("Ana López");
        cliente.setNumeroDocumento("12345678");

        usuario = new Usuario();
        usuario.setId(9L);
        usuario.setNombre("Vendedora");
        usuario.setEmail("empleada@luany.com");
        usuario.setPassword("hash-no-debe-retornarse");

        producto = new Producto();
        producto.setId(3L);
        producto.setNombre("Vestido");
        producto.setPrecio(12.345);
        producto.setStockActual(10);
    }

    @Test
    void creaNotaCalculaImportesDesdePrecioPersistidoYRegistraVenta() {
        prepararReferencias();
        when(ventaProductoLockRepository.buscarPorIdBloqueando(3L)).thenReturn(Optional.of(producto));
        when(notaVentaRepository.saveAndFlush(any(NotaVenta.class))).thenAnswer(invocation -> {
            NotaVenta nota = invocation.getArgument(0);
            nota.setId(15L);
            return nota;
        });
        when(detalleVentaRepository.findByNotaVentaIdOrderById(15L)).thenReturn(List.of());
        when(movimientoService.registrar(any(MovimientoRequest.class), eq(usuario.getEmail())))
                .thenReturn(new Movimiento());

        ArgumentCaptor<NotaVenta> notaCaptor = ArgumentCaptor.forClass(NotaVenta.class);
        AtomicReference<DetalleVenta> detalleGuardado = new AtomicReference<>();
        when(detalleVentaRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            Iterable<DetalleVenta> detalles = invocation.getArgument(0);
            detalles.forEach(detalleGuardado::set);
            return List.of();
        });

        NotaVentaDetalleResponse respuesta = notaVentaService.crear(request(2), usuario.getEmail());

        verify(notaVentaRepository).saveAndFlush(notaCaptor.capture());
        NotaVenta nota = notaCaptor.getValue();
        assertEquals(30, nota.getNumeroNota().length());
        assertTrue(nota.getNumeroNota().startsWith("NV-"));
        assertEquals(new BigDecimal("24.70"), nota.getTotal());
        assertEquals(new BigDecimal("12.35"), detalleGuardado.get().getPrecioUnitario());
        assertEquals(new BigDecimal("24.70"), detalleGuardado.get().getSubtotal());
        assertEquals(15L, respuesta.id());
        assertNotEquals("hash-no-debe-retornarse", respuesta.usuarioNombre());
        verify(movimientoService).registrar(any(MovimientoRequest.class), eq(usuario.getEmail()));
    }

    @Test
    void rechazaStockInsuficienteAntesDeCrearNotaOMovimiento() {
        prepararReferencias();
        producto.setStockActual(1);
        when(ventaProductoLockRepository.buscarPorIdBloqueando(3L)).thenReturn(Optional.of(producto));

        assertThrows(IllegalArgumentException.class,
                () -> notaVentaService.crear(request(2), usuario.getEmail()));

        verify(notaVentaRepository, never()).saveAndFlush(any(NotaVenta.class));
        verifyNoInteractions(detalleVentaRepository, movimientoService);
    }

    @Test
    void clienteInexistenteNoRegistraLaVenta() {
        when(clienteRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> notaVentaService.crear(request(1), usuario.getEmail()));

        verifyNoInteractions(ventaProductoLockRepository, notaVentaRepository, movimientoService);
    }

    @Test
    void numeroDeNotaEnConflictoProvocaErrorSeguroYNoGeneraMovimientos() {
        prepararReferencias();
        when(ventaProductoLockRepository.buscarPorIdBloqueando(3L)).thenReturn(Optional.of(producto));
        when(notaVentaRepository.saveAndFlush(any(NotaVenta.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> notaVentaService.crear(request(1), usuario.getEmail()));

        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(detalleVentaRepository, movimientoService);
    }

    @Test
    void movimientoUsaTipoVentaYCantidadValidada() {
        prepararReferencias();
        when(ventaProductoLockRepository.buscarPorIdBloqueando(3L)).thenReturn(Optional.of(producto));
        when(notaVentaRepository.saveAndFlush(any(NotaVenta.class))).thenAnswer(invocation -> {
            NotaVenta nota = invocation.getArgument(0);
            nota.setId(15L);
            return nota;
        });
        when(detalleVentaRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(detalleVentaRepository.findByNotaVentaIdOrderById(15L)).thenReturn(List.of());
        when(movimientoService.registrar(any(MovimientoRequest.class), eq(usuario.getEmail())))
                .thenReturn(new Movimiento());

        notaVentaService.crear(request(3), usuario.getEmail());

        ArgumentCaptor<MovimientoRequest> movimiento = ArgumentCaptor.forClass(MovimientoRequest.class);
        verify(movimientoService).registrar(movimiento.capture(), eq(usuario.getEmail()));
        assertEquals(TipoMovimiento.VENTA, movimiento.getValue().getTipo());
        assertEquals(3, movimiento.getValue().getCantidad());
    }

    @Test
    void fallaMovimientoPropagaExcepcionYNoConfirmaLaOperacion() {
        prepararReferencias();
        when(ventaProductoLockRepository.buscarPorIdBloqueando(3L)).thenReturn(Optional.of(producto));
        when(notaVentaRepository.saveAndFlush(any(NotaVenta.class))).thenAnswer(invocation -> {
            NotaVenta nota = invocation.getArgument(0);
            nota.setId(15L);
            return nota;
        });
        when(detalleVentaRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(movimientoService.registrar(any(MovimientoRequest.class), eq(usuario.getEmail())))
                .thenThrow(new IllegalStateException("fallo de movimiento"));

        assertThrows(IllegalStateException.class,
                () -> notaVentaService.crear(request(1), usuario.getEmail()));

        verify(notaVentaRepository).saveAndFlush(any(NotaVenta.class));
        verify(detalleVentaRepository).saveAllAndFlush(any());
    }

    private void prepararReferencias() {
        when(clienteRepository.findById(5L)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.buscarPorEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
    }

    private CrearNotaVentaRequest request(int cantidad) {
        CrearDetalleVentaRequest linea = new CrearDetalleVentaRequest();
        linea.setProductoId(3L);
        linea.setCantidad(cantidad);
        CrearNotaVentaRequest request = new CrearNotaVentaRequest();
        request.setClienteId(5L);
        request.setProductos(List.of(linea));
        return request;
    }
}
