package com.luany.inventory.controller;

import com.luany.inventory.service.SeguridadService;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seguridad")
@RequiredArgsConstructor
public class SeguridadController {

    private final SeguridadService seguridadService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/intentos")
    public List<IntentoSeguridadResponse> intentos() {
        return seguridadService.intentosRecientes().stream()
                .map(IntentoSeguridadResponse::desde)
                .toList();
    }

    @GetMapping("/auditoria")
    public List<AuditoriaResponse> auditoria() {
        return seguridadService.auditoriaReciente().stream()
                .map(AuditoriaResponse::desde)
                .toList();
    }

    @GetMapping("/bloqueos")
    public List<BloqueoResponse> bloqueos() {
        return seguridadService.bloqueosRecientes().stream()
                .map(BloqueoResponse::desde)
                .toList();
    }

    @GetMapping("/usuarios/{id}/estado")
    public EstadoSeguridadResponse estadoUsuario(@PathVariable Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no existe"));
        return new EstadoSeguridadResponse(
                usuario.isBloqueadoManual(),
                usuario.getBloqueadoHasta(),
                "INACTIVO".equalsIgnoreCase(usuario.getEstado()));
    }

    @PostMapping("/usuarios/{id}/bloquear")
    public EstadoSeguridadResponse bloquear(
            @PathVariable Long id,
            @Valid @RequestBody AccionSeguridadRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        seguridadService.bloquearManual(
                id, request.motivo(), authentication.getName(), httpRequest.getRemoteAddr());
        return estadoUsuario(id);
    }

    @PostMapping("/usuarios/{id}/desbloquear")
    public EstadoSeguridadResponse desbloquear(
            @PathVariable Long id,
            @Valid @RequestBody AccionSeguridadRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        seguridadService.desbloquear(
                id, request.motivo(), authentication.getName(), httpRequest.getRemoteAddr());
        return estadoUsuario(id);
    }

    public record AccionSeguridadRequest(
            @NotBlank(message = "El motivo es obligatorio")
            @Size(max = 200, message = "El motivo no puede superar 200 caracteres")
            String motivo) {
    }

    public record EstadoSeguridadResponse(
            boolean bloqueadoManual,
            java.time.LocalDateTime bloqueadoHasta,
            boolean deshabilitado) {
    }

    public record IntentoSeguridadResponse(
            Long id,
            String identificador,
            String ip,
            String resultado,
            String tipo,
            java.time.LocalDateTime fecha,
            String descripcion) {
        static IntentoSeguridadResponse desde(com.luany.inventory.model.IntentoSeguridad registro) {
            return new IntentoSeguridadResponse(
                    registro.getId(), registro.getIdentificador(), registro.getIp(),
                    registro.getResultado(), registro.getTipo(), registro.getFecha(),
                    registro.getDescripcion());
        }
    }

    public record AuditoriaResponse(
            Long id,
            java.time.LocalDateTime fecha,
            String usuarioRelacionado,
            String tipoEvento,
            String resultado,
            String ip,
            String descripcion) {
        static AuditoriaResponse desde(com.luany.inventory.model.AuditoriaSeguridad registro) {
            return new AuditoriaResponse(
                    registro.getId(), registro.getFecha(), registro.getUsuarioRelacionado(),
                    registro.getTipoEvento(), registro.getResultado(), registro.getIp(),
                    registro.getDescripcion());
        }
    }

    public record BloqueoResponse(
            Long id,
            String usuario,
            String tipo,
            String accion,
            java.time.LocalDateTime fecha,
            java.time.LocalDateTime fechaVigenciaHasta,
            String motivo,
            String realizadoPor,
            String ip) {
        static BloqueoResponse desde(com.luany.inventory.model.BloqueoHistorial registro) {
            Usuario usuario = registro.getUsuario();
            return new BloqueoResponse(
                    registro.getId(), usuario == null ? null : usuario.getEmail(),
                    registro.getTipo(), registro.getAccion(), registro.getFecha(),
                    registro.getFechaVigenciaHasta(), registro.getMotivo(),
                    registro.getRealizadoPor(), registro.getIp());
        }
    }
}
