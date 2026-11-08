package com.luany.inventory.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class RecuperacionPasswordSolicitudProcessor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RecuperacionPasswordSolicitudProcessor.class);
    private final RecuperacionPasswordTokenService tokenService;
    private final CorreoService correoService;

    @Async("recoveryTaskExecutor")
    public CompletableFuture<Resultado> procesar(String email) {
        try {
            var solicitud = tokenService.crearSolicitud(email);
            if (solicitud.isEmpty()) {
                return CompletableFuture.completedFuture(Resultado.CUENTA_NO_ENCONTRADA);
            }
            return CompletableFuture.completedFuture(enviarCorreo(solicitud.get()));
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo procesar la solicitud de recuperación.", exception);
            return CompletableFuture.completedFuture(Resultado.ERROR_ENVIO);
        }
    }

    private Resultado enviarCorreo(RecuperacionPasswordTokenService.SolicitudCorreo solicitud) {
        try {
            correoService.enviarRecuperacion(
                    solicitud.destinatario(), solicitud.nombre(), solicitud.enlace());
            return Resultado.CORREO_ENVIADO;
        } catch (CorreoRecuperacionException exception) {
            boolean tokenInvalidado = true;
            try {
                tokenService.invalidarToken(solicitud.tokenHash());
            } catch (RuntimeException invalidationException) {
                tokenInvalidado = false;
                LOGGER.error("No se pudo invalidar el token tras fallar el envío.", invalidationException);
            }
            if (tokenInvalidado) {
                LOGGER.warn("No se pudo completar el envío de recuperación; se invalidó el token generado.");
            }
            return Resultado.ERROR_ENVIO;
        }
    }

    public enum Resultado {
        CORREO_ENVIADO,
        CUENTA_NO_ENCONTRADA,
        ERROR_ENVIO
    }
}
