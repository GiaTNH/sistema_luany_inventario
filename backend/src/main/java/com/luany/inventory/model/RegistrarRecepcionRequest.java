package com.luany.inventory.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RegistrarRecepcionRequest {

    @NotBlank(message = "La clave de recepción es obligatoria")
    @Size(max = 36, message = "La clave de recepción no es válida")
    @Pattern(
            regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            message = "La clave de recepción no es válida")
    private String claveIdempotencia;

    @NotEmpty(message = "Indica al menos una cantidad recibida")
    @Valid
    private List<RegistrarRecepcionItemRequest> productos;
}
