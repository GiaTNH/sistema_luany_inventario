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
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String MAYUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITOS = "23456789";
    private static final String CARACTERES_PASSWORD = MAYUSCULAS + MINUSCULAS + DIGITOS;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final CorreoService correoService;
    private final JdbcTemplate jdbcTemplate;
    private final NotificacionTrasCommit notificacionTrasCommit;

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
        administrador.setNombreUsuario(generarNombreUsuario(administrador.getNombre()));
        administrador.setPassword(passwordEncoder.encode(password));
        administrador.setRol("ADMIN");
        administrador.setDebeCambiarPassword(false);

        return aRespuesta(usuarioRepository.saveAndFlush(administrador));
    }

    @Transactional
    public ResultadoNotificacion<UsuarioCreadoResponse> crearEmpleado(CrearEmpleadoRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String rol = request.getRol() == null ? "EMPLEADO" : request.getRol().trim();
        if (!Set.of("ADMIN", "EMPLEADO").contains(rol)) {
            throw new IllegalArgumentException("El rol debe ser ADMIN o EMPLEADO");
        }

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
        usuario.setNombreUsuario(generarNombreUsuario(usuario.getNombre()));
        usuario.setPassword(passwordEncoder.encode(passwordTemporal));
        usuario.setRol(rol);
        usuario.setDebeCambiarPassword(true);
        usuarioRepository.saveAndFlush(usuario);
        ResultadoNotificacion<UsuarioCreadoResponse> resultado = new ResultadoNotificacion<>(
                new UsuarioCreadoResponse(aRespuesta(usuario), usuario.getNombreUsuario()), true);
        notificacionTrasCommit.registrar(
                "bienvenida",
                () -> correoService.enviarBienvenida(
                        email, usuario.getNombre(), usuario.getNombreUsuario(), passwordTemporal),
                resultado::marcarNotificacionAceptada);
        return resultado;
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
    public UsuarioResponse actualizarPerfil(String email, ActualizarPerfilRequest request) {
        Usuario usuario = usuarioRepository.buscarPorEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        usuario.setNombre(request.getNombre().trim());
        return aRespuesta(usuarioRepository.save(usuario));
    }
    @Transactional
    public UsuarioResponse cambiarPassword(String email, CambioPasswordRequest request) {
        Usuario usuario = usuarioRepository.buscarPorEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "La sesión ya no es válida"));

        if (!Objects.equals(request.getNuevaPassword(), request.getConfirmarPassword())) {
            throw new IllegalArgumentException("La contraseña y su confirmación no coinciden");
        }

        String nuevaPassword = request.getNuevaPassword();
        if (nuevaPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException(
                    "La nueva contraseña no puede superar 72 bytes UTF-8");
        }

        if (!usuario.isDebeCambiarPassword()) {
            String passwordActual = request.getPasswordActual();
            if (passwordActual == null || passwordActual.isBlank()) {
                throw new IllegalArgumentException("Ingresa tu contraseña actual");
            }
            if (passwordActual.getBytes(StandardCharsets.UTF_8).length > 72
                    || !passwordEncoder.matches(passwordActual, usuario.getPassword())) {
                throw new IllegalArgumentException("La contraseña actual es incorrecta");
            }
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
        char[] password = new char[16];
        password[0] = MAYUSCULAS.charAt(SECURE_RANDOM.nextInt(MAYUSCULAS.length()));
        password[1] = MINUSCULAS.charAt(SECURE_RANDOM.nextInt(MINUSCULAS.length()));
        password[2] = DIGITOS.charAt(SECURE_RANDOM.nextInt(DIGITOS.length()));
        for (int indice = 3; indice < password.length; indice++) {
            password[indice] = CARACTERES_PASSWORD.charAt(SECURE_RANDOM.nextInt(CARACTERES_PASSWORD.length()));
        }
        for (int indice = password.length - 1; indice > 0; indice--) {
            int otroIndice = SECURE_RANDOM.nextInt(indice + 1);
            char temporal = password[indice];
            password[indice] = password[otroIndice];
            password[otroIndice] = temporal;
        }
        return new String(password);
    }

    private String generarNombreUsuario(String nombre) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
        if (base.isBlank()) base = "empleado";
        base = base.substring(0, Math.min(base.length(), 80));

        String candidato = base;
        int sufijo = 2;
        while (usuarioRepository.existsByNombreUsuario(candidato)) {
            String finalSufijo = "." + sufijo++;
            candidato = base.substring(0, Math.min(base.length(), 100 - finalSufijo.length()))
                    + finalSufijo;
        }
        return candidato;
    }
}
