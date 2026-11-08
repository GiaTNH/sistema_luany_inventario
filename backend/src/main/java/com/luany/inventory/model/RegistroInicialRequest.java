package com.luany.inventory.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistroInicialRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String nombre;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato válido")
    @Size(max = 120, message = "El correo no puede superar 120 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 12, max = 72, message = "La contraseña debe tener entre 12 y 72 caracteres")
    private String password;

    @NotBlank(message = "La confirmación de contraseña es obligatoria")
    @Size(min = 12, max = 72, message = "La confirmación debe tener entre 12 y 72 caracteres")
    private String confirmarPassword;
}
