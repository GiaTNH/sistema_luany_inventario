package com.luany.inventory.controller;

import com.luany.inventory.model.ReportesResponse.*;
import com.luany.inventory.model.TipoMovimiento;
import com.luany.inventory.service.ReportesService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReportesController {

    private final ReportesService reportesService;

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
}
