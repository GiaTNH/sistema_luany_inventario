package com.luany.inventory.service;

import com.luany.inventory.model.RecuperacionPasswordToken;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.repository.RecuperacionPasswordTokenRepository;
import com.luany.inventory.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecuperacionPasswordTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final RecuperacionPasswordTokenRepository tokenRepository;

    @Transactional
    public Optional<SolicitudCorreo> crearSolicitud(String email) {
        return usuarioRepository.buscarPorEmailParaActualizar(email)
                .filter(usuario -> "ACTIVO".equalsIgnoreCase(usuario.getEstado()))
                .map(this::crearToken);
    }

    @Transactional
    public void invalidarToken(String tokenHash) {
        tokenRepository.invalidarNoUtilizado(tokenHash, LocalDateTime.now(ZoneOffset.UTC));
    }

    private SolicitudCorreo crearToken(Usuario usuario) {
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        tokenRepository.invalidarNoUtilizados(usuario.getId(), ahora);

        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        String tokenPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = hashToken(tokenPlano);

        RecuperacionPasswordToken registro = new RecuperacionPasswordToken();
        registro.setUsuario(usuario);
        registro.setTokenHash(tokenHash);
        registro.setCreadoEn(ahora);
        registro.setExpiraEn(ahora.plusMinutes(30));
        tokenRepository.saveAndFlush(registro);

        String enlace = "https://sistema-luany-inventario.onrender.com/#restablecer?token=" + tokenPlano;
        return new SolicitudCorreo(usuario.getEmail(), usuario.getNombre(), enlace, tokenHash);
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    public record SolicitudCorreo(String destinatario, String nombre, String enlace, String tokenHash) {
    }
}
