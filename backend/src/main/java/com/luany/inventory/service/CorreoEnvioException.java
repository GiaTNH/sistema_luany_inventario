package com.luany.inventory.service;

public class CorreoEnvioException extends RuntimeException {

    public CorreoEnvioException() {
        super("No se pudo enviar el correo de bienvenida.");
    }
}
