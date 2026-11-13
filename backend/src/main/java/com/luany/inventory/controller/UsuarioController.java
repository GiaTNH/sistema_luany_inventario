package com.luany.inventory.controller;

import com.luany.inventory.model.CrearEmpleadoRequest;
import com.luany.inventory.model.ActualizarUsuarioRequest;
import com.luany.inventory.model.CambiarRolRequest;
import com.luany.inventory.model.UsuarioGestionResponse;
import com.luany.inventory.model.UsuarioCreadoResponse;
import com.luany.inventory.model.ResultadoNotificacion;
import com.luany.inventory.service.AuthService;
import com.luany.inventory.service.UsuarioGestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final AuthService authService;
    private final UsuarioGestionService usuarioGestionService;

    @GetMapping
    public List<UsuarioGestionResponse> listar() {
        return usuarioGestionService.listar();
    }

    @PostMapping
    public ResponseEntity<UsuarioCreadoResponse> crearEmpleado(
            @Valid @RequestBody CrearEmpleadoRequest request) {
        ResultadoNotificacion<UsuarioCreadoResponse> resultado = authService.crearEmpleado(request);
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(HttpStatus.CREATED);
        if (resultado.notificacionRequerida() && !resultado.notificacionAceptada()) {
            respuesta.header("X-Notification-Status", "not-sent");
        }
        return respuesta.body(resultado.resultado());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioGestionResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarUsuarioRequest request,
            Authentication authentication) {
        ResultadoNotificacion<UsuarioGestionResponse> resultado =
                usuarioGestionService.actualizar(id, request, authentication.getName());
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok();
        if (resultado.notificacionRequerida() && !resultado.notificacionAceptada()) {
            respuesta.header("X-Notification-Status", "not-sent");
        }
        return respuesta.body(resultado.resultado());
    }

    @PutMapping("/{id}/rol")
    public UsuarioGestionResponse cambiarRol(
            @PathVariable Long id,
            @Valid @RequestBody CambiarRolRequest request,
            Authentication authentication) {
        return usuarioGestionService.cambiarRol(id, request, authentication.getName());
    }
}
