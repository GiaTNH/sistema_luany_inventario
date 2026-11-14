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

@Service
@RequiredArgsConstructor
public class AlertaStockNotificacionService {

    private final ProductoService productoService;
    private final UsuarioRepository usuarioRepository;
    private final CorreoService correoService;

    @Transactional(readOnly = true)
    public int notificarAdministradores() {
        List<AlertaStock> alertas = productoService.alertas();
        if (alertas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No hay productos con stock bajo o agotado para notificar.");
        }

        List<String> destinatarios = usuarioRepository.findAll().stream()
                .filter(usuario -> "ADMIN".equalsIgnoreCase(usuario.getRol()))
                .filter(usuario -> "ACTIVO".equalsIgnoreCase(usuario.getEstado()))
                .map(Usuario::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .distinct()
                .toList();

        if (destinatarios.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No hay un administrador activo con correo registrado.");
        }

        correoService.enviarAlertaStock(alertas, destinatarios);
        return alertas.size();
    }
}
