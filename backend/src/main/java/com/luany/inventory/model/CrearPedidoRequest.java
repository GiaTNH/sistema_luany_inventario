package com.luany.inventory.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.FutureOrPresent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CrearPedidoRequest {

    @NotNull(message = "El proveedor es obligatorio")
    private Long proveedorId;

    @FutureOrPresent(message = "La fecha estimada no puede estar en el pasado")
    private LocalDate fechaEstimadaEntrega;

    @NotEmpty(message = "Agrega al menos un producto al pedido")
    @Valid
    private List<CrearPedidoItemRequest> productos;
}
