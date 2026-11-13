package com.luany.inventory.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.luany.inventory.model.ReportesResponse.*;
import com.luany.inventory.model.TipoMovimiento;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportesExportServiceTests {

    @Mock
    private ReportesService reportesService;
    @InjectMocks
    private ReportesExportService exportService;

    @Test
    void excelDeVentasEsUnXlsxValidoIncluyeTotalesYFiltros() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 2);
        when(reportesService.ventas(desde, hasta)).thenReturn(new VentasPeriodo(
                desde, hasta, new BigDecimal("32.50"), 3,
                List.of(new VentaDia(desde, new BigDecimal("20.00"), 2),
                        new VentaDia(hasta, new BigDecimal("12.50"), 1))));

        var archivo = exportService.exportarExcel("ventas", desde, hasta, null, null, null, null);

        assertEquals("reporte_ventas.xlsx", archivo.nombre());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", archivo.mediaType());
        assertTrue(archivo.contenido().length > 100);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(archivo.contenido()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("LUANY", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Ventas por período", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Desde 2026-11-01 hasta 2026-11-02", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("TOTAL", sheet.getRow(9).getCell(0).getStringCellValue());
            assertEquals(32.5, sheet.getRow(9).getCell(2).getNumericCellValue());
            assertTrue(sheet.getRow(7).getCell(2).getCellStyle().getDataFormatString().contains("0.00"));
            assertEquals(1,
                    ((org.apache.poi.xssf.usermodel.XSSFWorkbook) workbook).getAllPictures().size());
        }
        verify(reportesService).ventas(desde, hasta);
    }

    @Test
    void pdfDeVentasEsValidoNoVacioYContieneEncabezadoYPeriodo() throws Exception {
        LocalDate desde = LocalDate.of(2026, 10, 5);
        LocalDate hasta = LocalDate.of(2026, 10, 6);
        when(reportesService.ventas(desde, hasta)).thenReturn(new VentasPeriodo(
                desde, hasta, new BigDecimal("18.00"), 1,
                List.of(new VentaDia(desde, new BigDecimal("18.00"), 1))));

        var archivo = exportService.exportarPdf("ventas", desde, hasta, null, null, null, null);

        assertEquals("reporte_ventas.pdf", archivo.nombre());
        assertEquals("application/pdf", archivo.mediaType());
        assertTrue(archivo.contenido().length > 100);
        try (PdfReader reader = new PdfReader(archivo.contenido())) {
            String texto = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(texto.contains("LUANY"));
            assertTrue(texto.contains("Ventas por período"));
            assertTrue(texto.contains("2026-10-05"));
            assertTrue(texto.contains("TOTAL"));
            assertTrue(texto.contains("S/ 18.00"));
            var recursos = reader.getPageN(1)
                    .getAsDict(com.lowagie.text.pdf.PdfName.RESOURCES)
                    .getAsDict(com.lowagie.text.pdf.PdfName.XOBJECT);
            assertTrue(recursos != null && !recursos.getKeys().isEmpty());
        }
        verify(reportesService).ventas(desde, hasta);
    }

    @Test
    void rankingRespetaLimiteYExportaProductosDevueltosPorReportes() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 9);
        when(reportesService.productosVendidos(desde, hasta, 5)).thenReturn(new ProductosVendidos(
                desde, hasta, 5, List.of(new ProductoVendido(7L, "Vestido", 12))));

        var archivo = exportService.exportarExcel(
                "productos-mas-vendidos", desde, hasta, 5, null, null, null);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(archivo.contenido()))) {
            assertEquals("Productos más vendidos", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("Vestido", workbook.getSheetAt(0).getRow(7).getCell(1).getStringCellValue());
            assertEquals(12, workbook.getSheetAt(0).getRow(7).getCell(2).getNumericCellValue());
        }
        verify(reportesService).productosVendidos(desde, hasta, 5);
    }

    @Test
    void movimientoExportaTodasLasPaginasYConservaFechasProductoYTipo() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 9);
        List<MovimientoItem> primeraPagina = new ArrayList<>();
        for (int indice = 0; indice < 100; indice++) {
            primeraPagina.add(new MovimientoItem(
                    (long) indice + 1, LocalDateTime.of(2026, 11, 2, 10, 0), "VENTA",
                    1, "Venta", 8L, "Vestido", 20, 19));
        }
        when(reportesService.movimientos(desde, hasta, 8L, TipoMovimiento.VENTA, 0, 100))
                .thenReturn(new MovimientosPagina(desde, hasta, 0, 100, 101, 2, primeraPagina));
        when(reportesService.movimientos(desde, hasta, 8L, TipoMovimiento.VENTA, 1, 100))
                .thenReturn(new MovimientosPagina(desde, hasta, 1, 100, 101, 2, List.of(
                        new MovimientoItem(101L, LocalDateTime.of(2026, 11, 3, 10, 0), "VENTA",
                                2, "Último movimiento", 8L, "Vestido", 19, 17))));

        var archivo = exportService.exportarPdf(
                "movimientos", desde, hasta, null, 8L, TipoMovimiento.VENTA, null);

        try (PdfReader reader = new PdfReader(archivo.contenido())) {
            assertTrue(reader.getNumberOfPages() >= 2);
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                texto.append(new PdfTextExtractor(reader).getTextFromPage(pagina));
            }
            assertTrue(texto.toString().contains("Historial de movimientos (101)"));
            assertTrue(texto.toString().contains("Último movimiento"));
        }
        verify(reportesService).movimientos(desde, hasta, 8L, TipoMovimiento.VENTA, 0, 100);
        verify(reportesService).movimientos(desde, hasta, 8L, TipoMovimiento.VENTA, 1, 100);
    }

    @Test
    void inventarioYPedidosUsanLosDtosYFiltrosExistentes() throws Exception {
        when(reportesService.inventario("BAJO")).thenReturn(new Inventario(List.of(
                new ProductoInventario(3L, "Body", 2, 4, 20, "BAJO"))));
        when(reportesService.pedidosRecepciones(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9)))
                .thenReturn(new PedidosRecepciones(
                        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9),
                        List.of(new EstadoPedido("PARCIAL", 1, null, null)),
                        List.of(new PedidoRecepcion(9L, LocalDateTime.of(2026, 11, 2, 9, 0),
                                "Proveedor", "PARCIAL", 8, 3, 5, null)),
                        List.of(new RecepcionPorDia(LocalDate.of(2026, 11, 3), 1, 3))));

        var inventario = exportService.exportarPdf("inventario", null, null, null, null, null, "BAJO");
        var pedidos = exportService.exportarExcel("pedidos-recepciones",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9), null, null, null, null);

        try (PdfReader reader = new PdfReader(inventario.contenido())) {
            String texto = new PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(texto.contains("Estado: BAJO"));
            assertTrue(texto.contains("Body"));
        }
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(pedidos.contenido()))) {
            var sheet = workbook.getSheetAt(0);
            assertTrue(sheet.getRow(5).getCell(0).getStringCellValue().contains("Resumen de pedidos"));
            assertTrue(sheet.getRow(10).getCell(0).getStringCellValue().contains("Detalle de pedidos"));
        }
        verify(reportesService).inventario("BAJO");
        verify(reportesService).pedidosRecepciones(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9));
    }
}
