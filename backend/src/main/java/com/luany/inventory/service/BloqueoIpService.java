package com.luany.inventory.service;

import com.luany.inventory.model.BloqueoIp;
import com.luany.inventory.repository.BloqueoIpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BloqueoIpService {

    static final int MAX_INTENTOS = 5;

    private final BloqueoIpRepository bloqueoIpRepository;
    private final Clock clock;

    @Value("${security.login.window-minutes:15}")
    private int ventanaMinutos;

    /**
     * Verifica si la IP está bloqueada. Lanza HTTP 429 si lo está.
     */
    @Transactional(readOnly = true)
    public void verificar(String ip) {
        if (ip == null || ip.isBlank()) return;
        LocalDateTime ahora = LocalDateTime.now(clock);
        bloqueoIpRepository.buscarBloqueoActivo(ip, ahora).ifPresent(b -> {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Se alcanzó el límite de intentos. Espera " + ventanaMinutos + " minutos e intenta nuevamente.");
        });
    }

    /**
     * Registra un intento fallido para la IP en dos fases dentro de la misma transacción:
     *
     * Fase 1 — UPSERT atómico: gestiona los cinco casos (IP nueva, bloqueo vigente,
     * bloqueo expirado, ventana expirada, ventana vigente) en una sola sentencia SQL.
     * MySQL serializa ON DUPLICATE KEY UPDATE a nivel de fila, por lo que dos solicitudes
     * concurrentes desde una IP nueva no pierden ni duplican intentos.
     *
     * Fase 2 — SELECT FOR UPDATE: lee el estado consolidado por el UPSERT y aplica
     * bloqueado_hasta exactamente cuando intentos alcanza MAX_INTENTOS. Ambas fases
     * ocurren en la misma transacción (REQUIRES_NEW) para que el bloqueo pesimista
     * se libere lo antes posible.
     *
     * @return true si la IP quedó bloqueada con este intento
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarFallido(String ip) {
        if (ip == null || ip.isBlank()) return false;

        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime ventanaInicio = ahora.minusMinutes(ventanaMinutos);

        // Fase 1: UPSERT atómico — actualiza el contador según el estado actual de la fila
        bloqueoIpRepository.upsertIntentoFallido(ip, ahora, ventanaInicio);

        // Fase 2: SELECT FOR UPDATE — leer estado consolidado y aplicar bloqueo si corresponde
        BloqueoIp bloqueo = bloqueoIpRepository.buscarParaBloqueo(ip).orElse(null);
        if (bloqueo == null) return false;

        // Bloqueo vigente: el UPSERT no modificó nada, la IP sigue bloqueada
        if (bloqueo.getBloqueadoHasta() != null && ahora.isBefore(bloqueo.getBloqueadoHasta())) {
            return true;
        }

        // Contador reiniciado (bloqueo expirado o ventana expirada): intentos vale 1
        if (bloqueo.getIntentos() < MAX_INTENTOS) {
            return false;
        }

        // Quinto intento (o más, por concurrencia): aplicar bloqueo
        bloqueo.setBloqueadoHasta(ahora.plusMinutes(ventanaMinutos));
        bloqueoIpRepository.save(bloqueo);
        return true;
    }

    /**
     * Conservado para compatibilidad. No se llama tras un login exitoso;
     * el historial de intentos por IP se preserva intencionalmente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void limpiar(String ip) {
        if (ip == null || ip.isBlank()) return;
        bloqueoIpRepository.findById(ip).ifPresent(bloqueoIpRepository::delete);
    }
}
