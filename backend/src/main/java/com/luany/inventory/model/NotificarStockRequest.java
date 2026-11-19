package com.luany.inventory.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record NotificarStockRequest(
        @NotEmpty(message = "Selecciona al menos un destinatario")
        List<@NotNull(message = "El destinatario es obligatorio") Long> destinatarioIds) {
}
