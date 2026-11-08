package com.luany.inventory.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecuperacionPasswordSolicitudProcessor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RecuperacionPasswordSolicitudProcessor.class);
    private final RecuperacionPasswordTokenService tokenService;
    private final CorreoService correoService;

    @Async("recoveryTaskExecutor")
    public void procesar(String email) {
        tokenService.crearSolicitud(email).ifPresent(solicitud -> enviarCorreo(solicitud));
    }

    private void enviarCorreo(RecuperacionPasswordTokenService.SolicitudCorreo solicitud) {
        try {
            correoService.enviarRecuperacion(
                    solicitud.destinatario(), solicitud.nombre(), solicitud.enlace());
        } catch (CorreoRecuperacionException exception) {
            tokenService.invalidarToken(solicitud.tokenHash());
            LOGGER.warn("No se pudo completar el envío de recuperación; se invalidó el token generado.");
        }
    }
}
