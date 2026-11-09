package com.luany.inventory.service;

import com.luany.inventory.model.ActualizarUsuarioRequest;
import com.luany.inventory.model.CambiarRolRequest;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.model.UsuarioGestionResponse;
import com.luany.inventory.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UsuarioGestionService {

    private final UsuarioRepository usuarioRepository;
    private final SessionRegistry sessionRegistry;

    @Transactional(readOnly = true)
    public List<UsuarioGestionResponse> listar() {
        return usuarioRepository.findAll(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.asc("apellido").ignoreCase(),
                        org.springframework.data.domain.Sort.Order.asc("nombre").ignoreCase()))
                .stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Transactional
    public UsuarioGestionResponse cambiarRol(Long id, CambiarRolRequest request, String emailAdministrador) {
        List<Usuario> administradores = usuarioRepository.buscarAdministradoresParaActualizar();
        Usuario usuario = usuarioRepository.buscarPorIdParaActualizar(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe"));

        String nuevoRol = request.getRol();
        if (usuario.getEmail().equalsIgnoreCase(emailAdministrador) && !"ADMIN".equals(nuevoRol)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No puedes quitarte tu propio rol de administrador");
        }

        long administradoresActivos = administradores.stream()
                .filter(admin -> "ACTIVO".equalsIgnoreCase(admin.getEstado()))
                .count();
        if ("ADMIN".equals(usuario.getRol())
                && "ACTIVO".equalsIgnoreCase(usuario.getEstado())
                && !"ADMIN".equals(nuevoRol)
                && administradoresActivos <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Debe permanecer al menos un administrador activo");
        }

        boolean rolCambio = !usuario.getRol().equals(nuevoRol);
        usuario.setRol(nuevoRol);
        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        if (rolCambio) {
            expirarSesiones(guardado.getEmail());
        }
        return aRespuesta(guardado);
    }

    @Transactional
    public UsuarioGestionResponse actualizar(Long id, ActualizarUsuarioRequest request, String emailAdministrador) {
        List<Usuario> administradores = usuarioRepository.buscarAdministradoresParaActualizar();
        Usuario usuario = usuarioRepository.buscarPorIdParaActualizar(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario no existe"));

        String nuevoRol = request.getRol();
        String nuevoEstado = request.getEstado();
        if (usuario.getEmail().equalsIgnoreCase(emailAdministrador)
                && !"ADMIN".equals(nuevoRol)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No puedes quitarte tu propio rol de administrador");
        }
        if (usuario.getEmail().equalsIgnoreCase(emailAdministrador)
                && "ACTIVO".equalsIgnoreCase(usuario.getEstado())
                && "INACTIVO".equals(nuevoEstado)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No puedes desactivar tu propia cuenta de administrador");
        }

        boolean dejaDeSerAdministradorActivo = "ADMIN".equals(usuario.getRol())
                && "ACTIVO".equalsIgnoreCase(usuario.getEstado())
                && (!"ADMIN".equals(nuevoRol) || !"ACTIVO".equals(nuevoEstado));
        long administradoresActivos = administradores.stream()
                .filter(admin -> "ACTIVO".equalsIgnoreCase(admin.getEstado()))
                .count();
        if (dejaDeSerAdministradorActivo && administradoresActivos <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Debe permanecer al menos un administrador activo");
        }

        String emailAnterior = usuario.getEmail();
        String nuevoEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        usuarioRepository.buscarPorEmail(nuevoEmail)
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Ya existe un usuario con ese correo");
                });

        boolean cambiaRol = !usuario.getRol().equals(nuevoRol);
        boolean seDesactiva = "ACTIVO".equalsIgnoreCase(usuario.getEstado())
                && "INACTIVO".equals(nuevoEstado);
        usuario.setNombre(request.getNombre().trim());
        usuario.setApellido(request.getApellido() == null || request.getApellido().isBlank()
                ? null : request.getApellido().trim());
        usuario.setEmail(nuevoEmail);
        usuario.setRol(nuevoRol);
        usuario.setEstado(nuevoEstado);

        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        if (cambiaRol || seDesactiva || !emailAnterior.equalsIgnoreCase(nuevoEmail)) {
            expirarSesiones(emailAnterior);
        }
        return aRespuesta(guardado);
    }

    private void expirarSesiones(String email) {
        sessionRegistry.getAllSessions(email, false).forEach(SessionInformation::expireNow);
    }

    private UsuarioGestionResponse aRespuesta(Usuario usuario) {
        return new UsuarioGestionResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getEmail(),
                usuario.getNombreUsuario(),
                usuario.getRol(),
                usuario.getEstado(),
                usuario.isDebeCambiarPassword());
    }
}
