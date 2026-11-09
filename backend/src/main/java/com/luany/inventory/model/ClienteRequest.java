package com.luany.inventory.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClienteRequest {

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
    private String nombre;

    @NotBlank(message = "El tipo de documento es obligatorio")
    @Pattern(regexp = "(?i)DNI|RUC|OTRO", message = "El tipo de documento debe ser DNI, RUC u OTRO")
    @Size(max = 20, message = "El tipo de documento no puede superar 20 caracteres")
    private String tipoDocumento;

    @NotBlank(message = "El número de documento es obligatorio")
    @Size(max = 20, message = "El número de documento no puede superar 20 caracteres")
    private String numeroDocumento;

    @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
    private String telefono;

    @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
    private String direccion;
}
