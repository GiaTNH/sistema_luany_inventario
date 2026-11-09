package com.luany.inventory.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PedidoResumenResponse(
        Long id,
        LocalDateTime fechaPedido,
        LocalDate fechaEstimadaEntrega,
        String estado,
        Long proveedorId,
        String proveedorNombre,
        String usuarioNombre,
        int totalProductos) {
}
