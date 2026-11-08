package com.luany.inventory.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambioPasswordRequest {

    @Size(max = 72, message = "La contraseña actual no puede superar 72 caracteres")
    private String passwordActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 12, max = 72, message = "La nueva contraseña debe tener entre 12 y 72 caracteres")
    private String nuevaPassword;

    @NotBlank(message = "La confirmación de contraseña es obligatoria")
    @Size(max = 72, message = "La confirmación no puede superar 72 caracteres")
    private String confirmarPassword;
}