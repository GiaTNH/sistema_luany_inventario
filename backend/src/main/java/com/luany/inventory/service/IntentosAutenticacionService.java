package com.luany.inventory.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IntentosAutenticacionService {

    static final int MAX_INTENTOS = 5;
    static final Duration VENTANA = Duration.ofMinutes(15);
    private static final int MAX_CLAVES = 10_000;

    private final ConcurrentHashMap<String, VentanaIntentos> intentos = new ConcurrentHashMap<>();
    private final Clock clock;

    public IntentosAutenticacionService(Clock luanyClock) {
        this.clock = luanyClock;
    }

    public synchronized void verificarLogin(String email, String ip) {
        Instant ahora = clock.instant();
        verificar("login-email:" + normalizar(email), ahora);
        // El bloqueo por IP se verifica mediante BloqueoIpService (persistente)
    }

    public synchronized void registrarLoginFallido(String email, String ip) {
        Instant ahora = clock.instant();
        registrar("login-email:" + normalizar(email), ahora);
        // El registro de fallos por IP se delega a BloqueoIpService (persistente)
    }

    public synchronized void registrarLoginExitoso(String email) {
        intentos.remove("login-email:" + normalizar(email));
    }

    public synchronized void registrarSolicitudRecuperacion(String email, String ip) {
        Instant ahora = clock.instant();
        String claveEmail = "recuperacion-email:" + normalizar(email);
        String claveIp = "recuperacion-ip:" + normalizarIp(ip);
        verificar(claveEmail, ahora);
        verificar(claveIp, ahora);
        registrar(claveEmail, ahora);
        registrar(claveIp, ahora);
    }

    private void verificar(String clave, Instant ahora) {
        VentanaIntentos ventana = intentos.get(clave);
        if (ventana == null) return;
        if (ventana.bloqueadoHasta() != null) {
            if (ahora.isBefore(ventana.bloqueadoHasta())) {
                throw bloqueado();
            }
            intentos.remove(clave, ventana);
            return;
        }
        if (!ahora.isBefore(ventana.inicio().plus(VENTANA))) {
            intentos.remove(clave, ventana);
            return;
        }
    }

    private void registrar(String clave, Instant ahora) {
        VentanaIntentos ventana = intentos.get(clave);
        if (ventana == null || !ahora.isBefore(ventana.inicio().plus(VENTANA))) {
            limpiarSiHaceFalta(ahora);
            if (!intentos.containsKey(clave) && intentos.size() >= MAX_CLAVES) {
                throw bloqueado();
            }
            ventana = new VentanaIntentos(ahora, 0, null);
        }
        int cantidad = ventana.cantidad() + 1;
        Instant bloqueadoHasta = cantidad >= MAX_INTENTOS ? ahora.plus(VENTANA) : null;
        intentos.put(clave, new VentanaIntentos(ventana.inicio(), cantidad, bloqueadoHasta));
    }

    private void limpiarSiHaceFalta(Instant ahora) {
        if (intentos.size() < MAX_CLAVES) return;
        intentos.entrySet().removeIf(entrada ->
                entrada.getValue().bloqueadoHasta() == null
                        ? !ahora.isBefore(entrada.getValue().inicio().plus(VENTANA))
                        : !ahora.isBefore(entrada.getValue().bloqueadoHasta()));
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizarIp(String ip) {
        return ip == null || ip.isBlank() ? "desconocida" : ip.trim();
    }

    private ResponseStatusException bloqueado() {
        return new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Se alcanzó el límite de intentos. Espera 15 minutos e intenta nuevamente.");
    }

    private record VentanaIntentos(Instant inicio, int cantidad, Instant bloqueadoHasta) {
    }
}
