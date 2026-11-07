package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioCreadoResponse crearEmpleado(CrearEmpleadoRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (usuarioRepository.buscarPorEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo");
        }

        String passwordTemporal = generarPasswordTemporal();
        Usuario usuario = new Usuario();
        usuario.setNombre(request.getNombre().trim());
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(passwordTemporal));
        usuario.setRol("EMPLEADO");
        usuario.setDebeCambiarPassword(true);
        usuarioRepository.save(usuario);

        return new UsuarioCreadoResponse(aRespuesta(usuario), passwordTemporal);
    }

    public UsuarioResponse iniciarSesion(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        Usuario usuario = usuarioRepository.buscarPorEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Correo o contraseña incorrectos"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new IllegalArgumentException("Correo o contraseña incorrectos");
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));

        if (!usuario.isDebeCambiarPassword()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El usuario no tiene un cambio pendiente");
        }
        if (!passwordEncoder.matches(request.getPasswordTemporal(), usuario.getPassword())) {
            throw new IllegalArgumentException("La contraseña temporal es incorrecta");
        }
        if (passwordEncoder.matches(request.getNuevaPassword(), usuario.getPassword())) {
            throw new IllegalArgumentException("La nueva contraseña debe ser diferente a la temporal");
        }
        if (request.getNuevaPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("La nueva contraseña no puede superar 72 bytes UTF-8");
        }

        usuario.setPassword(passwordEncoder.encode(request.getNuevaPassword()));
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