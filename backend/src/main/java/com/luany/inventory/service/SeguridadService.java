package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeguridadService {

    private final IntentoSeguridadRepository intentoRepository;
    private final AuditoriaSeguridadRepository auditoriaRepository;
    private final BloqueoHistorialRepository bloqueoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SessionRegistry sessionRegistry;
    private final CorreoService correoService;
    private final BloqueoIpService bloqueoIpService;
    private final Clock clock;

    @Value("${security.login.window-minutes:15}")
    private int ventanaMinutos;

    @Value("${security.login.alert-at:3}")
    private int alertaAt;

    @Value("${security.login.block-at:5}")
    private int blockAt;

    @Value("${security.login.alert-cooldown-minutes:15}")
    private int alertaCooldownMinutos;

    @Transactional
    public ResultadoLogin registrarFallido(String identificador, String ip) {
        LocalDateTime ahora = LocalDateTime.now(clock);
        Usuario usuario = usuarioRepository.buscarPorEmail(identificador).orElse(null);
        IntentoSeguridad intento = new IntentoSeguridad();
        intento.setIdentificador(normalizar(identificador));
        intento.setUsuario(usuario);
        intento.setIp(normalizarIp(ip));
        intento.setTipo("LOGIN");
        intento.setResultado("FALLIDO");
        intento.setFecha(ahora);
        intento.setDescripcion("Credenciales incorrectas o cuenta inexistente");
        intentoRepository.save(intento);

        registrarAuditoria(usuario == null ? normalizar(identificador) : usuario.getEmail(),
                "LOGIN_FALLIDO", "FALLIDO", ip, "Inicio de sesión rechazado");

        long cuenta = intentoRepository.countByIdentificadorIgnoreCaseAndResultadoAndFechaAfter(
                normalizar(identificador), "FALLIDO", ahora.minusMinutes(ventanaMinutos));
        long ipCount = ip == null ? 0 : intentoRepository.countByIpAndTipoAndResultadoAndFechaAfter(
                normalizarIp(ip), "LOGIN", "FALLIDO", ahora.minusMinutes(ventanaMinutos));

        boolean bloqueado = false;
        if (cuenta >= blockAt && usuario != null) {
            Usuario persistido = usuarioRepository.buscarPorIdParaActualizar(usuario.getId()).orElse(usuario);
            persistido.setBloqueadoHasta(ahora.plusMinutes(ventanaMinutos));
            persistido.setBloqueadoManual(false);
            usuarioRepository.save(persistido);
            expirarSesiones(persistido.getEmail());
            bloquearHistorial(persistido, "TEMPORAL", "BLOQUEO_AUTOMATICO",
                    ahora.plusMinutes(ventanaMinutos), "Límite automático de intentos fallidos",
                    null, ip);
            registrarAuditoria(persistido.getEmail(), "BLOQUEO_AUTOMATICO", "BLOQUEADO", ip,
                    "Bloqueo temporal por intentos fallidos");
            bloqueado = true;
        }

        if (cuenta >= alertaAt) {
            enviarAlertaAgrupada(usuario, normalizar(identificador), ip, cuenta, bloqueado, ahora);
        }
        if (ipCount >= blockAt) {
            registrarAuditoria(normalizar(identificador), "ACTIVIDAD_SOSPECHOSA_IP",
                    "OBSERVADO", ip, "Muchos intentos fallidos desde la misma dirección IP");
        }

        progresar(cuenta);
        bloqueoIpService.registrarFallido(normalizarIp(ip));
        return new ResultadoLogin(bloqueado, cuenta, ipCount);
    }

    @Transactional
    public void registrarExitoso(String identificador, String ip) {
        Usuario usuario = usuarioRepository.buscarPorEmail(identificador).orElse(null);
        registrarAuditoria(usuario == null ? normalizar(identificador) : usuario.getEmail(),
                "LOGIN_EXITOSO", "EXITOSO", ip, "Inicio de sesión válido");
        registrarIntento(identificador, usuario, ip, "EXITOSO", "LOGIN", "Credenciales válidas");
    }

    @Transactional
    public void registrarAuditoria(String usuario, String tipo, String resultado, String ip, String descripcion) {
        AuditoriaSeguridad registro = new AuditoriaSeguridad();
        registro.setFecha(LocalDateTime.now(clock));
        registro.setUsuarioRelacionado(normalizar(usuario));
        registro.setTipoEvento(tipo);
        registro.setResultado(resultado);
        registro.setIp(normalizarIp(ip));
        registro.setDescripcion(descripcion);
        auditoriaRepository.save(registro);
    }

    @Transactional
    public String verificarBloqueo(String identificador) {
        Usuario usuario = usuarioRepository.buscarPorEmail(normalizar(identificador)).orElse(null);
        if (usuario == null) return null;
        if ("INACTIVO".equalsIgnoreCase(usuario.getEstado())) return "La cuenta está deshabilitada.";
        if (usuario.isBloqueadoManual()) return "La cuenta está bloqueada por el administrador.";
        if (usuario.getBloqueadoHasta() != null) {
            if (LocalDateTime.now(clock).isBefore(usuario.getBloqueadoHasta())) {
                return "La cuenta está bloqueada temporalmente. Intenta nuevamente más tarde.";
            }
            usuario.setBloqueadoHasta(null);
        }
        return null;
    }

    @Transactional
    public void bloquearManual(Long id, String motivo, String administrador, String ip) {
        Usuario usuario = usuarioRepository.buscarPorIdParaActualizar(id)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no existe"));
        usuario.setBloqueadoManual(true);
        usuarioRepository.save(usuario);
        expirarSesiones(usuario.getEmail());
        bloquearHistorial(usuario, "MANUAL", "BLOQUEO_MANUAL", null,
                motivo == null || motivo.isBlank() ? "Bloqueo manual administrativo" : motivo,
                administrador, ip);
        registrarAuditoria(usuario.getEmail(), "BLOQUEO_MANUAL", "BLOQUEADO", ip,
                "Bloqueo manual aplicado por administración");
    }

    @Transactional
    public void desbloquear(Long id, String motivo, String administrador, String ip) {
        Usuario usuario = usuarioRepository.buscarPorIdParaActualizar(id)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no existe"));
        usuario.setBloqueadoManual(false);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);
        expirarSesiones(usuario.getEmail());
        bloquearHistorial(usuario, "MANUAL", "DESBLOQUEO", null,
                motivo == null || motivo.isBlank() ? "Desbloqueo administrativo" : motivo,
                administrador, ip);
        registrarAuditoria(usuario.getEmail(), "DESBLOQUEO", "DESBLOQUEADO", ip,
                "Acceso desbloqueado por administración");
    }

    @Transactional(readOnly = true)
    public List<IntentoSeguridad> intentosRecientes() {
        return intentoRepository.findTop100ByOrderByFechaDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditoriaSeguridad> auditoriaReciente() {
        return auditoriaRepository.findTop200ByOrderByFechaDesc();
    }

    @Transactional(readOnly = true)
    public List<BloqueoHistorial> bloqueosRecientes() {
        return bloqueoRepository.findTop200ByOrderByFechaDesc();
    }

    private void enviarAlertaAgrupada(
            Usuario usuario, String identificador, String ip, long cuenta,
            boolean bloqueado, LocalDateTime ahora) {
        LocalDateTime cooldown = ahora.minusMinutes(alertaCooldownMinutos);
        if (auditoriaRepository.existsByTipoEventoAndFechaAfter("ALERTA_SEGURIDAD", cooldown)) {
            return;
        }
        registrarAuditoria(usuario == null ? identificador : usuario.getEmail(),
                "ALERTA_SEGURIDAD", bloqueado ? "BLOQUEADO" : "OBSERVADO", ip,
                "Alerta agrupada por intentos fallidos");
        try {
            correoService.enviarAlertaSeguridad(
                    usuario == null ? identificador : usuario.getNombre() + " (" + usuario.getEmail() + ")",
                    ahora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                    ip,
                    cuenta,
                    usuario == null ? "Cuenta inexistente" :
                            ("INACTIVO".equalsIgnoreCase(usuario.getEstado()) ? "INACTIVO" : "ACTIVO"),
                    bloqueado);
        } catch (RuntimeException exception) {
            registrarAuditoria(usuario == null ? identificador : usuario.getEmail(),
                    "ALERTA_SEGURIDAD_CORREO_FALLIDO", "ERROR_NO_SENSIBLE", ip,
                    "No se pudo enviar la alerta de seguridad por correo");
        }
    }

    private void registrarIntento(
            String identificador, Usuario usuario, String ip,
            String resultado, String tipo, String descripcion) {
        IntentoSeguridad intento = new IntentoSeguridad();
        intento.setIdentificador(normalizar(identificador));
        intento.setUsuario(usuario);
        intento.setIp(normalizarIp(ip));
        intento.setTipo(tipo);
        intento.setResultado(resultado);
        intento.setFecha(LocalDateTime.now(clock));
        intento.setDescripcion(descripcion);
        intentoRepository.save(intento);
    }

    private void bloquearHistorial(
            Usuario usuario, String tipo, String accion, LocalDateTime vigencia,
            String motivo, String realizadoPor, String ip) {
        BloqueoHistorial historial = new BloqueoHistorial();
        historial.setUsuario(usuario);
        historial.setTipo(tipo);
        historial.setAccion(accion);
        historial.setFecha(LocalDateTime.now(clock));
        historial.setFechaVigenciaHasta(vigencia);
        historial.setMotivo(motivo);
        historial.setRealizadoPor(realizadoPor);
        historial.setIp(normalizarIp(ip));
        bloqueoRepository.save(historial);
    }

    private void expirarSesiones(String email) {
        for (SessionInformation sesion : sessionRegistry.getAllSessions(email, false)) {
            sesion.expireNow();
        }
    }

    private void progresar(long cuenta) {
        if (cuenta > 1) {
            int delayMillis = (int) Math.min(4000L, cuenta * 250L);
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String normalizar(String valor) {
        return valor == null ? null : valor.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizarIp(String ip) {
        return ip == null || ip.isBlank() ? null : ip.trim();
    }

    public record ResultadoLogin(boolean bloqueado, long cuenta, long cuentaIp) {
    }
}
