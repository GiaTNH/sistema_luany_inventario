package com.luany.inventory.service;

public class CorreoRecuperacionException extends RuntimeException {

    public CorreoRecuperacionException() {
        super("No se pudo enviar el correo de recuperación.");
    }
}
