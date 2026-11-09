package com.luany.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record NotaVentaDetalleResponse(
        Long id,
        String numeroNota,
        LocalDateTime fechaEmision,
        String clienteNombre,
        String clienteDocumento,
        String usuarioNombre,
        BigDecimal total,
        String estado,
        List<Item> productos) {

    public record Item(
            Long productoId,
            String productoNombre,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal) {
    }
}
