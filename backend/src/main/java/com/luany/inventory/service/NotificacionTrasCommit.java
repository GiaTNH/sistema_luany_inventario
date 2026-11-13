package com.luany.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class NotificacionTrasCommit {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificacionTrasCommit.class);

    public void registrar(String tipo, Runnable envio, Runnable alAceptar) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "No se puede programar una notificación sin una transacción activa");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    envio.run();
                    alAceptar.run();
                } catch (RuntimeException error) {
                    LOGGER.error(
                            "No se pudo enviar la notificación de {} después de confirmar la transacción.",
                            tipo);
                }
            }
        });
    }
}
