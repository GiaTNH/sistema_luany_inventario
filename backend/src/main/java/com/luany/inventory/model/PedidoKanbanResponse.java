package com.luany.inventory.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoKanbanResponse(
        Long id,
        LocalDateTime fechaPedido,
        LocalDate fechaEstimadaEntrega,
        String estado,
        Long proveedorId,
        String proveedorNombre,
        int totalProductos,
        long cantidadTotalSolicitada,
        long cantidadRecibida,
        long cantidadPendiente,
        List<Long> productoIds) {
}
