package com.luany.inventory.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestablecerPasswordRequest {

    @NotBlank(message = "El enlace de recuperación no es válido")
    @Size(max = 64, message = "El enlace de recuperación no es válido")
    private String token;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 12, max = 72, message = "La contraseña debe tener entre 12 y 72 caracteres")
    private String nuevaPassword;

    @NotBlank(message = "La confirmación de contraseña es obligatoria")
    @Size(min = 12, max = 72, message = "La confirmación debe tener entre 12 y 72 caracteres")
    private String confirmarPassword;
}
