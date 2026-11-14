package com.luany.inventory.service;

public class CorreoAlertaStockException extends RuntimeException {

    public CorreoAlertaStockException() {
        super("No se pudo enviar la alerta de inventario por correo. Intenta nuevamente.");
    }
}
