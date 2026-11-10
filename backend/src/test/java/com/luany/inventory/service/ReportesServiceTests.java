package com.luany.inventory.service;

import com.luany.inventory.model.Movimiento;
import com.luany.inventory.model.Producto;
import com.luany.inventory.model.TipoMovimiento;
import com.luany.inventory.repository.ProductoRepository;
import com.luany.inventory.repository.ReportesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportesServiceTests {

    @Mock
    private ReportesRepository reportesRepository;
    @Mock
    private ProductoRepository productoRepository;
    @InjectMocks
    private ReportesService reportesService;

    @Test
    void ventasSumaImportesYNotasPorDiaEnElPeriodoSolicitado() {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 2);
        when(reportesRepository.ventasPorDia(any(), any())).thenReturn(List.of(
                new Object[]{Date.valueOf(desde), new BigDecimal("12.50"), 2L},
                new Object[]{Date.valueOf(hasta), new BigDecimal("7.50"), 1L}));

        var resultado = reportesService.ventas(desde, hasta);

        assertEquals(new BigDecimal("20.00"), resultado.importeTotal());
        assertEquals(3L, resultado.cantidadNotas());
        assertEquals(2, resultado.ventasPorDia().size());
        verify(reportesRepository).ventasPorDia(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
    }

    @Test
    void rechazaLimiteDeRankingFueraDelRangoPermitido() {
        assertThrows(IllegalArgumentException.class,
                () -> reportesService.productosVendidos(null, null, 101));
        verify(reportesRepository, never()).productosMasVendidos(any(), any(), anyInt());
    }

    @Test
    void paginaMovimientosYDevuelveSoloCamposDelDto() {
        Producto producto = new Producto();
        producto.setId(8L);
        producto.setNombre("Vestido");
        Movimiento movimiento = new Movimiento();
        movimiento.setId(21L);
        movimiento.setProducto(producto);
        movimiento.setTipo(TipoMovimiento.VENTA);
        movimiento.setCantidad(3);
        movimiento.setFecha(LocalDateTime.of(2026, 11, 2, 12, 0));
        when(reportesRepository.movimientos(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(movimiento));
        when(reportesRepository.contarMovimientos(any(), any(), any(), any())).thenReturn(41L);

        var resultado = reportesService.movimientos(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), 8L, TipoMovimiento.VENTA, 1, 20);

        assertEquals(41L, resultado.totalElementos());
        assertEquals(3, resultado.totalPaginas());
        assertEquals("Vestido", resultado.movimientos().get(0).productoNombre());
        assertEquals("VENTA", resultado.movimientos().get(0).tipo());
        verify(reportesRepository).movimientos(
                LocalDate.of(2026, 11, 1).atStartOfDay(),
                LocalDate.of(2026, 11, 4).atStartOfDay(),
                8L, TipoMovimiento.VENTA, 20, 20);
    }

    @Test
    void calculaEstadosDeInventarioYPermiteFiltrar() {
        Producto agotado = producto(1L, "A", 0, 3, 10);
        Producto bajo = producto(2L, "B", 2, 3, 10);
        Producto normal = producto(3L, "C", 5, 3, 10);
        Producto exceso = producto(4L, "D", 10, 3, 10);
        when(productoRepository.findAll()).thenReturn(List.of(normal, exceso, agotado, bajo));

        var resultado = reportesService.inventario("BAJO");

        assertEquals(1, resultado.productos().size());
        assertEquals("B", resultado.productos().get(0).nombre());
        assertEquals("BAJO", resultado.productos().get(0).estado());
    }

    @Test
    void agregaPedidosYCantidadesRecibidasEnDtos() {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDateTime fechaPedido = LocalDateTime.of(2026, 11, 2, 10, 0);
        LocalDateTime fechaRecepcion = fechaPedido.plusDays(1);
        when(reportesRepository.resumenEstadosPedido(any(), any())).thenReturn(java.util.Collections.singletonList(
                new Object[]{"PARCIAL", 1L, fechaPedido, fechaPedido}));
        when(reportesRepository.pedidosConRecepciones(any(), any())).thenReturn(java.util.Collections.singletonList(
                new Object[]{4L, Timestamp.valueOf(fechaPedido), "Proveedor", "PARCIAL", 8L, 3L,
                        Timestamp.valueOf(fechaRecepcion)}));
        when(reportesRepository.recepcionesPorDia(any(), any())).thenReturn(java.util.Collections.singletonList(
                new Object[]{Date.valueOf(fechaRecepcion.toLocalDate()), 1L, 3L}));

        var resultado = reportesService.pedidosRecepciones(desde, LocalDate.of(2026, 11, 3));

        assertEquals(1, resultado.resumenPorEstado().size());
        assertEquals(8L, resultado.pedidos().get(0).unidadesPedidas());
        assertEquals(3L, resultado.pedidos().get(0).unidadesRecibidas());
        assertEquals(5L, resultado.pedidos().get(0).unidadesPendientes());
        assertEquals(fechaRecepcion.toLocalDate(), resultado.recepcionesPorDia().get(0).fecha());
    }

    @Test
    void rechazaPeriodoInvertido() {
        assertThrows(IllegalArgumentException.class,
                () -> reportesService.ventas(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 1)));
        verify(reportesRepository, never()).ventasPorDia(any(), any());
    }

    private Producto producto(Long id, String nombre, int actual, int minimo, int maximo) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setStockActual(actual);
        producto.setStockMinimo(minimo);
        producto.setStockMaximo(maximo);
        return producto;
    }
}
