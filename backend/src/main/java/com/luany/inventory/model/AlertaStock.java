package com.luany.inventory.model;

public record AlertaStock(
        Long productoId,
        String nombre,
        Integer stockActual,
        Integer stockMinimo,
        String estado,
        Integer cantidadSugerida
) {
}