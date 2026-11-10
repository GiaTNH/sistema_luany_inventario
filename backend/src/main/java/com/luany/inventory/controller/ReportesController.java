package com.luany.inventory.controller;

import com.luany.inventory.model.ReportesResponse.*;
import com.luany.inventory.model.TipoMovimiento;
import com.luany.inventory.service.ReportesService;
import com.luany.inventory.service.ReportesExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReportesController {

    private final ReportesService reportesService;
    private final ReportesExportService reportesExportService;

    @GetMapping("/ventas")
    public VentasPeriodo ventas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportesService.ventas(desde, hasta);
    }

    @GetMapping("/productos-mas-vendidos")
    public ProductosVendidos productosVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "10") int limite) {
        return reportesService.productosVendidos(desde, hasta, limite);
    }

    @GetMapping("/movimientos")
    public MovimientosPagina movimientos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return reportesService.movimientos(desde, hasta, productoId, tipo, pagina, tamano);
    }

    @GetMapping("/inventario")
    public Inventario inventario(@RequestParam(required = false) String estado) {
        return reportesService.inventario(estado);
    }

    @GetMapping("/pedidos-recepciones")
    public PedidosRecepciones pedidosRecepciones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportesService.pedidosRecepciones(desde, hasta);
    }

    @GetMapping("/{reporte}/excel")
    public ResponseEntity<byte[]> exportarExcel(
            @PathVariable String reporte,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) String estado) {
        return archivo(reportesExportService.exportarExcel(
                reporte, desde, hasta, limite, productoId, tipo, estado));
    }

    @GetMapping("/{reporte}/pdf")
    public ResponseEntity<byte[]> exportarPdf(
            @PathVariable String reporte,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) String estado) {
        return archivo(reportesExportService.exportarPdf(
                reporte, desde, hasta, limite, productoId, tipo, estado));
    }

    private ResponseEntity<byte[]> archivo(ReportesExportService.ReporteArchivo archivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .body(archivo.contenido());
    }
}
