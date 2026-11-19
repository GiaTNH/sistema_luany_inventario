package com.luany.inventory.service;

import com.luany.inventory.model.AlertaStock;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AlertaStockNotificacionService {

    private final ProductoService productoService;
    private final UsuarioRepository usuarioRepository;
    private final CorreoService correoService;

    @Transactional(readOnly = true)
    public List<Destinatario> destinatariosDisponibles() {
        return usuarioRepository.findAllByRolAndEstadoIgnoreCaseOrderByNombreAsc("ADMIN", "ACTIVO")
                .stream()
                .filter(usuario -> usuario.getEmail() != null && !usuario.getEmail().isBlank())
                .map(usuario -> new Destinatario(usuario.getId(), usuario.getNombre(), usuario.getEmail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public int notificarAdministradores(List<Long> destinatarioIds) {
        if (destinatarioIds == null || destinatarioIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona al menos un destinatario.");
        }
        Set<Long> seleccionados = Set.copyOf(destinatarioIds);
        if (seleccionados.size() != destinatarioIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No repitas destinatarios.");
        }
        List<AlertaStock> alertas = productoService.alertas();
        if (alertas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No hay productos con stock bajo o agotado para notificar.");
        }

        List<Usuario> usuarios = usuarioRepository.findAllByRolAndEstadoIgnoreCaseOrderByNombreAsc(
                "ADMIN", "ACTIVO").stream()
                .filter(usuario -> seleccionados.contains(usuario.getId()))
                .filter(usuario -> usuario.getEmail() != null && !usuario.getEmail().isBlank())
                .toList();
        if (usuarios.size() != seleccionados.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Uno o más destinatarios ya no están disponibles.");
        }
        List<String> destinatarios = usuarios.stream()
                .map(Usuario::getEmail)
                .distinct()
                .toList();

        if (destinatarios.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No hay un administrador activo con correo registrado.");
        }

        correoService.enviarAlertaStock(alertas, destinatarios);
        return alertas.size();
    }

    public record Destinatario(Long id, String nombre, String correo) {
    }
}
