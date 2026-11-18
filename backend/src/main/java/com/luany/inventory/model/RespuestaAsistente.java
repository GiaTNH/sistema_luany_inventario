package com.luany.inventory.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public record RespuestaAsistente(
        String tipoConsulta,
        String respuesta,
        int productosEnContexto,
        long tiempoMs,
        List<TablaAsistente> tablas,
        List<OpcionProducto> opcionesProducto
) {
    public RespuestaAsistente(String tipoConsulta, String respuesta, int productosEnContexto, long tiempoMs) {
        this(tipoConsulta, respuesta, productosEnContexto, tiempoMs, List.of(), List.of());
    }

    public RespuestaAsistente(
            String tipoConsulta, String respuesta, int productosEnContexto, long tiempoMs,
            List<TablaAsistente> tablas) {
        this(tipoConsulta, respuesta, productosEnContexto, tiempoMs, tablas, List.of());
    }

    public RespuestaAsistente {
        tablas = tablas == null ? List.of() : List.copyOf(tablas);
        opcionesProducto = opcionesProducto == null ? List.of() : List.copyOf(opcionesProducto);
    }

    public record OpcionProducto(long id, String nombre) {
    }

    public record TablaAsistente(
            String tipo,
            String titulo,
            long total,
            int mostrados,
            List<String> columnas,
            List<List<Object>> filas
    ) {
        public TablaAsistente {
            columnas = List.copyOf(columnas);
            filas = filas.stream()
                    .map(fila -> Collections.unmodifiableList(new ArrayList<>(fila)))
                    .toList();
        }
    }
}