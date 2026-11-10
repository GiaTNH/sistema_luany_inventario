package com.luany.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ReportesResponse {

    private ReportesResponse() {
    }

    public record VentasPeriodo(
            LocalDate desde,
            LocalDate hasta,
            BigDecimal importeTotal,
            long cantidadNotas,
            List<VentaDia> ventasPorDia) {
    }

    public record VentaDia(LocalDate fecha, BigDecimal importe, long cantidadNotas) {
    }

    public record ProductosVendidos(
            LocalDate desde,
            LocalDate hasta,
            int limite,
            List<ProductoVendido> productos) {
    }

    public record ProductoVendido(Long productoId, String nombre, long unidadesVendidas) {
    }

    public record MovimientosPagina(
            LocalDate desde,
            LocalDate hasta,
            int pagina,
            int tamano,
            long totalElementos,
            int totalPaginas,
            List<MovimientoItem> movimientos) {
    }

    public record MovimientoItem(
            Long id,
            LocalDateTime fecha,
            String tipo,
            Integer cantidad,
            String observacion,
            Long productoId,
            String productoNombre,
            Integer stockAnterior,
            Integer stockNuevo) {
    }

    public record Inventario(List<ProductoInventario> productos) {
    }

    public record ProductoInventario(
            Long productoId,
            String nombre,
            Integer stockActual,
            Integer stockMinimo,
            Integer stockMaximo,
            String estado) {
    }

    public record PedidosRecepciones(
            LocalDate desde,
            LocalDate hasta,
            List<EstadoPedido> resumenPorEstado,
            List<PedidoRecepcion> pedidos,
            List<RecepcionPorDia> recepcionesPorDia) {
    }

    public record EstadoPedido(String estado, long cantidad, LocalDateTime primero, LocalDateTime ultimo) {
    }

    public record PedidoRecepcion(
            Long pedidoId,
            LocalDateTime fechaPedido,
            String proveedor,
            String estado,
            long unidadesPedidas,
            long unidadesRecibidas,
            long unidadesPendientes,
            LocalDateTime ultimaRecepcion) {
    }

    public record RecepcionPorDia(LocalDate fecha, long recepciones, long unidadesRecibidas) {
    }
}
