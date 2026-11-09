package com.luany.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record NotaVentaResumenResponse(
        Long id,
        String numeroNota,
        LocalDateTime fechaEmision,
        String clienteNombre,
        String usuarioNombre,
        BigDecimal total,
        String estado,
        int cantidadProductos) {
}
