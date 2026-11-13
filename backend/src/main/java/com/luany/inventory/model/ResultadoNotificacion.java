package com.luany.inventory.model;

public final class ResultadoNotificacion<T> {

    private final T resultado;
    private final boolean notificacionRequerida;
    private boolean notificacionAceptada;

    public ResultadoNotificacion(T resultado, boolean notificacionRequerida) {
        this.resultado = resultado;
        this.notificacionRequerida = notificacionRequerida;
    }

    public T resultado() {
        return resultado;
    }

    public boolean notificacionRequerida() {
        return notificacionRequerida;
    }

    public boolean notificacionAceptada() {
        return notificacionAceptada;
    }

    public void marcarNotificacionAceptada() {
        notificacionAceptada = true;
    }
}
