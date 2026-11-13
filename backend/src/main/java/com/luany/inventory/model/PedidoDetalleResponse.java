package com.luany.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoDetalleResponse(
        Long id,
        LocalDateTime fechaPedido,
        LocalDate fechaEstimadaEntrega,
        String estado,
        Long proveedorId,
        String proveedorNombre,
        String usuarioNombre,
        BigDecimal total,
        List<Item> productos,
        List<Recepcion> recepciones) {

    public record Item(
            Long detallePedidoId,
            Long productoId,
            String productoNombre,
            int cantidadPedida,
            int cantidadRecibida,
            int cantidadPendiente,
            BigDecimal precio,
            BigDecimal subtotal) {
    }

    public record Recepcion(
            Long id,
            LocalDateTime fecha,
            String usuarioNombre,
            List<RecepcionItem> productos) {
    }

    public record RecepcionItem(
            Long detallePedidoId,
            String productoNombre,
            int cantidad,
            Long movimientoId) {
    }
}
