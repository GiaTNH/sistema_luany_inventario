package com.luany.inventory.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrarRecepcionItemRequest {

    @NotNull(message = "El detalle del pedido es obligatorio")
    private Long detallePedidoId;

    @NotNull(message = "La cantidad recibida es obligatoria")
    @Min(value = 1, message = "La cantidad recibida debe ser mayor a cero")
    private Integer cantidad;
}
