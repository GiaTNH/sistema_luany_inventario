package com.luany.inventory.service;

import com.luany.inventory.model.RestablecerPasswordRequest;
import com.luany.inventory.model.SolicitudRecuperacionPasswordRequest;
import com.luany.inventory.model.RecuperacionPasswordToken;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.repository.RecuperacionPasswordTokenRepository;
import com.luany.inventory.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

@Service
public class RecuperacionPasswordService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecuperacionPasswordService.class);
    static final String MENSAJE_RECUPERACION =
            "Si existe una cuenta activa asociada a ese correo, recibirás instrucciones para restablecer tu contraseña.";
    private final UsuarioRepository usuarioRepository;
    private final RecuperacionPasswordTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecuperacionPasswordSolicitudProcessor solicitudProcessor;
    private final SessionRegistry sessionRegistry;
    private final IntentosAutenticacionService intentosAutenticacionService;
    private final SeguridadService seguridadService;

    @Autowired
    public RecuperacionPasswordService(
            UsuarioRepository usuarioRepository,
            RecuperacionPasswordTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            RecuperacionPasswordSolicitudProcessor solicitudProcessor,
            SessionRegistry sessionRegistry,
            IntentosAutenticacionService intentosAutenticacionService,
            SeguridadService seguridadService) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.solicitudProcessor = solicitudProcessor;
        this.sessionRegistry = sessionRegistry;
        this.intentosAutenticacionService = intentosAutenticacionService;
        this.seguridadService = seguridadService;
    }

    public RecuperacionPasswordService(
            UsuarioRepository usuarioRepository,
            RecuperacionPasswordTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            RecuperacionPasswordSolicitudProcessor solicitudProcessor,
            SessionRegistry sessionRegistry) {
        this(usuarioRepository, tokenRepository, passwordEncoder, solicitudProcessor,
                sessionRegistry, null, null);
    }

    public RecuperacionPasswordService(
            UsuarioRepository usuarioRepository,
            RecuperacionPasswordTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            RecuperacionPasswordSolicitudProcessor solicitudProcessor,
            SessionRegistry sessionRegistry,
            IntentosAutenticacionService intentosAutenticacionService) {
        this(usuarioRepository, tokenRepository, passwordEncoder, solicitudProcessor,
                sessionRegistry, intentosAutenticacionService, null);
    }

    public String solicitarRecuperacion(SolicitudRecuperacionPasswordRequest request) {
        return solicitarRecuperacion(request, null);
    }

    public String solicitarRecuperacion(SolicitudRecuperacionPasswordRequest request, String ip) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (intentosAutenticacionService != null) {
            intentosAutenticacionService.registrarSolicitudRecuperacion(email, ip);
        }
        if (seguridadService != null) {
            seguridadService.registrarAuditoria(email, "RECUPERACION_SOLICITADA",
                    "SOLICITADO", ip, "Solicitud de restablecimiento de contraseña");
        }
        try {
            solicitudProcessor.procesar(email).get();
        } catch (TaskRejectedException exception) {
            LOGGER.warn("No se pudo encolar una solicitud de recuperación.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Se interrumpió el procesamiento de una solicitud de recuperación.");
        } catch (ExecutionException exception) {
            LOGGER.error("No se pudo completar una solicitud de recuperación.", exception.getCause());
        }
        return MENSAJE_RECUPERACION;
    }

    @Transactional
    public String restablecerPassword(RestablecerPasswordRequest request) {
        if (!request.getNuevaPassword().equals(request.getConfirmarPassword())) {
            throw new IllegalArgumentException("La contraseña y su confirmación no coinciden");
        }

        String nuevaPassword = request.getNuevaPassword();
        PoliticaPassword.validar(nuevaPassword);

        String hash = hashToken(request.getToken());
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        RecuperacionPasswordToken token = tokenRepository
                .findByTokenHashAndUtilizadoEnIsNull(hash)
                .filter(registro -> registro.getExpiraEn().isAfter(ahora))
                .orElseThrow(RecuperacionPasswordService::tokenInvalido);

        if (tokenRepository.consumirSiVigente(hash, ahora) != 1) {
            throw tokenInvalido();
        }

        Usuario usuario = token.getUsuario();
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuario.setDebeCambiarPassword(false);
        usuarioRepository.save(usuario);
        sessionRegistry.getAllSessions(usuario.getEmail(), false)
                .forEach(session -> session.expireNow());
        if (seguridadService != null) {
            seguridadService.registrarAuditoria(usuario.getEmail(), "PASSWORD_RESTABLECIDA",
                    "EXITOSO", null, "Contraseña restablecida mediante enlace de recuperación");
        }
        return "Tu contraseña se actualizó correctamente. Ya puedes iniciar sesión.";
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    private static ResponseStatusException tokenInvalido() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "El enlace de recuperación no es válido o expiró.");
    }
}
