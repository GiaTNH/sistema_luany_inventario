package com.luany.inventory.controller;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManejadorErroresTests {

    private final ManejadorErrores manejadorErrores = new ManejadorErrores();

    @Test
    void restriccionDeCodigoDuplicadoDevuelveMensajeEspecifico() {
        DataIntegrityViolationException error = errorDeRestriccion("uk_productos_codigo");

        ResponseEntity<Map<String, String>> respuesta = manejadorErrores.registroEnUso(error);

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertEquals("Ya existe un producto con ese código", respuesta.getBody().get("error"));
    }

    @Test
    void restriccionDeRucDuplicadoDevuelveMensajeEspecifico() {
        DataIntegrityViolationException error = errorDeRestriccion("uk_proveedores_ruc");

        ResponseEntity<Map<String, String>> respuesta = manejadorErrores.registroEnUso(error);

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertEquals("Ya existe un proveedor con ese RUC", respuesta.getBody().get("error"));
    }

    private DataIntegrityViolationException errorDeRestriccion(String nombre) {
        ConstraintViolationException causa =
                new ConstraintViolationException("duplicate value", null, nombre);
        return new DataIntegrityViolationException("constraint violation", causa);
    }
}
