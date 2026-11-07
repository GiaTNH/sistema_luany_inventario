package com.luany.inventory.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambioPasswordRequest {

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 12, max = 72, message = "La nueva contraseña debe tener entre 12 y 72 caracteres")
    private String nuevaPassword;
}