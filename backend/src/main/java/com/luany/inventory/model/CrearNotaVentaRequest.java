package com.luany.inventory.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CrearNotaVentaRequest {

    private Long clienteId;

    @NotEmpty(message = "Agrega al menos un producto a la nota")
    @Valid
    private List<CrearDetalleVentaRequest> productos;
}
