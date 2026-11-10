package com.luany.inventory.controller;

import com.luany.inventory.service.CorreoEnvioException;
import com.luany.inventory.service.CorreoRecuperacionException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> reglaDeNegocio(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> estadoDeRespuesta(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", e.getReason() == null ? "No se pudo completar la solicitud" : e.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> datosInvalidos(MethodArgumentNotValidException e) {
        String mensaje = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", mensaje));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> registroEnUso(DataIntegrityViolationException e) {
        String restriccion = nombreRestriccion(e);
        if ("uk_productos_codigo".equalsIgnoreCase(restriccion)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya existe un producto con ese código"));
        }
        if ("uk_proveedores_ruc".equalsIgnoreCase(restriccion)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya existe un proveedor con ese RUC"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "No se pudo completar: el registro ya existe o está en uso por otros datos"));
    }

    private String nombreRestriccion(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException violacion) {
                return violacion.getConstraintName();
            }
        }
        return null;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> servicioNoDisponible(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CorreoEnvioException.class)
    public ResponseEntity<Map<String, String>> correoNoEnviado(CorreoEnvioException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "No se pudo enviar el correo de bienvenida. No se creó la cuenta."));
    }

    @ExceptionHandler(CorreoRecuperacionException.class)
    public ResponseEntity<Map<String, String>> correoRecuperacionNoEnviado(CorreoRecuperacionException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "No se pudo procesar la solicitud. Intenta nuevamente más tarde."));
    }
}