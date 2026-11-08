package com.luany.inventory.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarRolRequest {

    @NotBlank(message = "El rol es obligatorio")
    @Pattern(regexp = "ADMIN|EMPLEADO", message = "El rol debe ser ADMIN o EMPLEADO")
    private String rol;
}
