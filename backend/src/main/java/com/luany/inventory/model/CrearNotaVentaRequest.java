package com.luany.inventory.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CrearNotaVentaRequest {

    @NotNull(message = "El cliente es obligatorio")
    private Long clienteId;

    @NotEmpty(message = "Agrega al menos un producto a la nota")
    @Valid
    private List<CrearDetalleVentaRequest> productos;
}
