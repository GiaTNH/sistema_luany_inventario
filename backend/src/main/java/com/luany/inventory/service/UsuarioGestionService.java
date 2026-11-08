package com.luany.inventory.service;

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

@Service
@RequiredArgsConstructor
public class UsuarioGestionService {

    private final UsuarioRepository usuarioRepository;
    private final SessionRegistry sessionRegistry;

    @Transactional(readOnly = true)
    public List<UsuarioGestionResponse> listar() {
        return usuarioRepository.findAll(org.springframework.data.domain.Sort.by("id")).stream()
                .map(this::aRespuesta)
                .toList();
    }

    @Transactional
    public UsuarioGestionResponse cambiarRol(Long id, CambiarRolRequest request, String emailAdministrador) {
        List<Usuario> administradores = usuarioRepository.buscarAdministradoresParaActualizar();
        Usuario usuario = usuarioRepository.findById(id)
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
            sessionRegistry.getAllSessions(guardado.getEmail(), false)
                    .forEach(SessionInformation::expireNow);
        }
        return aRespuesta(guardado);
    }

    private UsuarioGestionResponse aRespuesta(Usuario usuario) {
        return new UsuarioGestionResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getEmail(),
                usuario.getNombreUsuario(),
                usuario.getRol(),
                usuario.getEstado());
    }
}
