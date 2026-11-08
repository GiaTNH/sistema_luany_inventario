package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final CorreoService correoService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public boolean registroInicialDisponible() {
        Boolean controlDisponible = jdbcTemplate.query(
                "SELECT completado FROM registro_inicial WHERE id = 1",
                resultSet -> resultSet.next() && !resultSet.getBoolean("completado"));
        return Boolean.TRUE.equals(controlDisponible) && usuarioRepository.count() == 0;
    }

    @Transactional
    public UsuarioResponse registrarPrimerAdministrador(RegistroInicialRequest request) {
        if (!Objects.equals(request.getPassword(), request.getConfirmarPassword())) {
            throw new IllegalArgumentException("La contraseña y su confirmación no coinciden");
        }

        String password = request.getPassword();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("La contraseña no puede superar 72 bytes UTF-8");
        }

        int controlActualizado = jdbcTemplate.update(
                "UPDATE registro_inicial SET completado = TRUE " +
                        "WHERE id = 1 AND completado = FALSE");
        if (controlActualizado != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El registro inicial no está disponible");
        }

        if (usuarioRepository.count() != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El registro inicial no está disponible");
        }

        Usuario administrador = new Usuario();
        administrador.setNombre(request.getNombre().trim());
        administrador.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        administrador.setPassword(passwordEncoder.encode(password));
        administrador.setRol("ADMIN");
        administrador.setDebeCambiarPassword(false);

        return aRespuesta(usuarioRepository.saveAndFlush(administrador));
    }

    @Transactional
    public UsuarioCreadoResponse crearEmpleado(CrearEmpleadoRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (usuarioRepository.buscarPorEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo");
        }

        String passwordTemporal = generarPasswordTemporal();
        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre().trim());
        if (request.getApellido() != null && !request.getApellido().isBlank()) {
            usuario.setApellido(request.getApellido().trim());
        }
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(passwordTemporal));
        usuario.setRol("EMPLEADO");
        usuario.setDebeCambiarPassword(true);
        usuarioRepository.saveAndFlush(usuario);
        correoService.enviarBienvenida(email, usuario.getNombre(), passwordTemporal);

        return new UsuarioCreadoResponse(aRespuesta(usuario));
    }

    public UsuarioResponse iniciarSesion(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        Usuario usuario = usuarioRepository.buscarPorEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Correo o contraseña incorrectos"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new IllegalArgumentException("Correo o contraseña incorrectos");
        }

        if ("INACTIVO".equalsIgnoreCase(usuario.getEstado())) {
            throw new IllegalArgumentException("Tu usuario está inactivo. Contacta al administrador");
        }
        return aRespuesta(usuario);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtenerUsuarioAutenticado(String email) {
        Usuario usuario = usuarioRepository.buscarPorEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));
        return aRespuesta(usuario);
    }
    @Transactional
    public UsuarioResponse cambiarPassword(String email, CambioPasswordRequest request) {
        Usuario usuario = usuarioRepository.buscarPorEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));

        if (!usuario.isDebeCambiarPassword()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El usuario no tiene un cambio pendiente");
        }

        String nuevaPassword = request.getNuevaPassword();

        if (nuevaPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "La nueva contraseña no puede superar 72 bytes UTF-8");
        }

        if (passwordEncoder.matches(nuevaPassword, usuario.getPassword())) {
            throw new IllegalArgumentException(
                    "La nueva contraseña debe ser diferente a la actual");
        }

        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuario.setDebeCambiarPassword(false);

        usuarioRepository.save(usuario);

        return aRespuesta(usuario);
    }

    private UsuarioResponse aRespuesta(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRol(), u.isDebeCambiarPassword());
    }

    private String generarPasswordTemporal() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
