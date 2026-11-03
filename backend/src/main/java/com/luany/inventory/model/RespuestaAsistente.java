package com.luany.inventory.model;

public record RespuestaAsistente(
        String tipoConsulta,
        String respuesta,
        int productosEnContexto,
        long tiempoMs
) {
}