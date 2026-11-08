package com.luany.inventory.service;

import com.luany.inventory.model.RestablecerPasswordRequest;
import com.luany.inventory.model.SolicitudRecuperacionPasswordRequest;
import com.luany.inventory.model.RecuperacionPasswordToken;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.repository.RecuperacionPasswordTokenRepository;
import com.luany.inventory.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class RecuperacionPasswordService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecuperacionPasswordService.class);
    static final String MENSAJE_CORREO_ENVIADO =
            "Enviamos un enlace para restablecer tu contraseña al correo registrado.";
    static final String MENSAJE_CUENTA_NO_ENCONTRADA =
            "No existe una cuenta activa asociada a ese correo.";
    static final String MENSAJE_ERROR_ENVIO =
            "No pudimos enviar el enlace de recuperación. Inténtalo más tarde.";
    private final UsuarioRepository usuarioRepository;
    private final RecuperacionPasswordTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecuperacionPasswordSolicitudProcessor solicitudProcessor;
    private final SessionRegistry sessionRegistry;

    public String solicitarRecuperacion(SolicitudRecuperacionPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        try {
            RecuperacionPasswordSolicitudProcessor.Resultado resultado =
                    solicitudProcessor.procesar(email).get();
            return switch (resultado) {
                case CORREO_ENVIADO -> MENSAJE_CORREO_ENVIADO;
                case CUENTA_NO_ENCONTRADA -> MENSAJE_CUENTA_NO_ENCONTRADA;
                case ERROR_ENVIO -> MENSAJE_ERROR_ENVIO;
            };
        } catch (TaskRejectedException exception) {
            LOGGER.warn("No se pudo encolar una solicitud de recuperación.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Se interrumpió el procesamiento de una solicitud de recuperación.");
        } catch (ExecutionException exception) {
            LOGGER.error("No se pudo completar una solicitud de recuperación.", exception.getCause());
        }
        return MENSAJE_ERROR_ENVIO;
    }

    @Transactional
    public String restablecerPassword(RestablecerPasswordRequest request) {
        if (!request.getNuevaPassword().equals(request.getConfirmarPassword())) {
            throw new IllegalArgumentException("La contraseña y su confirmación no coinciden");
        }

        String nuevaPassword = request.getNuevaPassword();
        if (nuevaPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("La contraseña no puede superar 72 bytes UTF-8");
        }

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
