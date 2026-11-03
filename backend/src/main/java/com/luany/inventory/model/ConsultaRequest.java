package com.luany.inventory.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ConsultaRequest {

    @NotBlank(message = "La pregunta es obligatoria")
    @Size(max = 500, message = "La pregunta no puede pasar de 500 caracteres")
    private String pregunta;
}