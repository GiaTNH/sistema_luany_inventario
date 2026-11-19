package com.luany.inventory.service;

import com.luany.inventory.model.Movimiento;
import com.luany.inventory.model.MovimientoRequest;
import com.luany.inventory.model.Producto;
import com.luany.inventory.model.TipoMovimiento;
import com.luany.inventory.repository.MovimientoRepository;
import com.luany.inventory.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovimientoServiceTests {

    @Mock
    private MovimientoRepository movimientoRepository;
    @Mock
    private ProductoStockLockService productoStockLockService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Spy
    private Clock clock = Clock.system(ZoneId.of("America/Lima"));

    @InjectMocks
    private MovimientoService movimientoService;

    @Test
    void entradaQueAlcanzaElMaximoDelTipoSeRegistra() {
        Producto producto = producto(Integer.MAX_VALUE - 4);
        when(productoStockLockService.bloquear(1L)).thenReturn(producto);
        when(movimientoRepository.save(any(Movimiento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime antes = LocalDateTime.now(clock);

        Movimiento movimiento = movimientoService.registrar(request(4));

        assertEquals(Integer.MAX_VALUE, producto.getStockActual());
        assertEquals(Integer.MAX_VALUE, movimiento.getStockNuevo());
        LocalDateTime despues = LocalDateTime.now(clock);
        assertFalse(movimiento.getFecha().isBefore(antes));
        assertFalse(movimiento.getFecha().isAfter(despues));
        verify(movimientoRepository).save(movimiento);
    }

    @Test
    void entradaQueDesbordaRechazaSinCambiarStockNiGuardarMovimiento() {
        Producto producto = producto(Integer.MAX_VALUE - 4);
        when(productoStockLockService.bloquear(1L)).thenReturn(producto);

        assertThrows(IllegalArgumentException.class,
                () -> movimientoService.registrar(request(5)));

        assertEquals(Integer.MAX_VALUE - 4, producto.getStockActual());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void motivoEsObligatorioParaRegistrarUnMovimiento() {
        MovimientoRequest request = request(1);
        request.setObservacion(" ");

        assertThrows(IllegalArgumentException.class, () -> movimientoService.registrar(request));

        verify(productoStockLockService, never()).bloquear(1L);
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void movimientosOperativosRechazanCantidadCeroYNegativa() {
        for (TipoMovimiento tipo : List.of(
                TipoMovimiento.ENTRADA, TipoMovimiento.SALIDA, TipoMovimiento.VENTA)) {
            for (int cantidad : List.of(0, -1)) {
                MovimientoRequest request = request(cantidad);
                request.setTipo(tipo);
                assertThrows(IllegalArgumentException.class, () -> movimientoService.registrar(request));
            }
        }

        verify(productoStockLockService, never()).bloquear(1L);
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void creacionConCantidadCeroSeRechazaEnElFlujoNormalSinCambiarStock() {
        MovimientoRequest request = request(0);
        request.setTipo(TipoMovimiento.CREACION);

        assertThrows(IllegalArgumentException.class, () -> movimientoService.registrar(request));

        verify(productoStockLockService, never()).bloquear(1L);
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void salidaYVentaRechazanCantidadMayorAlStock() {
        Producto producto = producto(2);
        when(productoStockLockService.bloquear(1L)).thenReturn(producto);
        for (TipoMovimiento tipo : List.of(TipoMovimiento.SALIDA, TipoMovimiento.VENTA)) {
            MovimientoRequest request = request(3);
            request.setTipo(tipo);

            assertThrows(IllegalArgumentException.class, () -> movimientoService.registrar(request));

            assertEquals(2, producto.getStockActual());
        }

        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void listarPaginadoAplicaRangoCompletoYOrdenRecientePrimero() {
        when(movimientoRepository.buscarPaginado(
                eq(4L), eq(TipoMovimiento.VENTA),
                eq(LocalDate.of(2026, 11, 1).atStartOfDay()),
                eq(LocalDate.of(2026, 11, 4).plusDays(1).atStartOfDay()),
                any(Pageable.class))).thenReturn(Page.empty());

        movimientoService.listarPaginado(
                4L, TipoMovimiento.VENTA,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4), 2, 20);

        ArgumentCaptor<Pageable> solicitud = ArgumentCaptor.forClass(Pageable.class);
        verify(movimientoRepository).buscarPaginado(
                eq(4L), eq(TipoMovimiento.VENTA),
                eq(LocalDate.of(2026, 11, 1).atStartOfDay()),
                eq(LocalDate.of(2026, 11, 5).atStartOfDay()),
                solicitud.capture());
        assertEquals(2, solicitud.getValue().getPageNumber());
        assertEquals(20, solicitud.getValue().getPageSize());
        assertEquals("DESC", solicitud.getValue().getSort().getOrderFor("fecha").getDirection().name());
        assertEquals("DESC", solicitud.getValue().getSort().getOrderFor("id").getDirection().name());
    }

    @Test
    void rangoDeFechasInvalidoSeRechazaAntesDeConsultar() {
        assertThrows(IllegalArgumentException.class, () -> movimientoService.listarPaginado(
                null, null, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 4), 0, 20));
        verify(movimientoRepository, never()).buscarPaginado(
                any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void tamanosDePaginaMayoresAlLimiteSeRechazan() {
        assertThrows(IllegalArgumentException.class, () -> movimientoService.listarPaginado(
                null, null, null, null, 0, 101));
    }

    private MovimientoRequest request(int cantidad) {
        MovimientoRequest request = new MovimientoRequest();
        request.setProductoId(1L);
        request.setTipo(TipoMovimiento.ENTRADA);
        request.setCantidad(cantidad);
        request.setObservacion("Reposición de inventario");
        return request;
    }

    private Producto producto(int stock) {
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setStockActual(stock);
        return producto;
    }
}
