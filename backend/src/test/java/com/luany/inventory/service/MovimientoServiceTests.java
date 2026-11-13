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
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @InjectMocks
    private MovimientoService movimientoService;

    @Test
    void entradaQueAlcanzaElMaximoDelTipoSeRegistra() {
        Producto producto = producto(Integer.MAX_VALUE - 4);
        when(productoStockLockService.bloquear(1L)).thenReturn(producto);
        when(movimientoRepository.save(any(Movimiento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Movimiento movimiento = movimientoService.registrar(request(4));

        assertEquals(Integer.MAX_VALUE, producto.getStockActual());
        assertEquals(Integer.MAX_VALUE, movimiento.getStockNuevo());
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
        return request;
    }

    private Producto producto(int stock) {
        Producto producto = new Producto();
        producto.setId(1L);
        producto.setStockActual(stock);
        return producto;
    }
}
