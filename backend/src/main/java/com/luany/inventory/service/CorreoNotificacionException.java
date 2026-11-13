package com.luany.inventory.service;

public class CorreoNotificacionException extends RuntimeException {

    public CorreoNotificacionException() {
        super("No se pudo enviar la notificación de cambio de correo.");
    }
}
