package com.luany.inventory.controller;

import com.luany.inventory.model.LoginRequest;
import com.luany.inventory.model.CambioPasswordRequest;
import com.luany.inventory.model.RegistroInicialRequest;
import com.luany.inventory.model.RestablecerPasswordRequest;
import com.luany.inventory.model.SolicitudRecuperacionPasswordRequest;
import com.luany.inventory.model.UsuarioResponse;
import com.luany.inventory.service.AuthService;
import com.luany.inventory.service.RecuperacionPasswordService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RecuperacionPasswordService recuperacionPasswordService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionRegistry sessionRegistry;

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrfToken(CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
    }

    @GetMapping("/registro-inicial/disponible")
    public ResponseEntity<RegistroInicialDisponibilidadResponse> registroInicialDisponible() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new RegistroInicialDisponibilidadResponse(authService.registroInicialDisponible()));
    }

    @PostMapping("/registro-inicial")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public UsuarioResponse registrarPrimerAdministrador(
            @Valid @RequestBody RegistroInicialRequest request) {
        return authService.registrarPrimerAdministrador(request);
    }

    @PostMapping("/solicitar-recuperacion")
    public RecuperacionPasswordResponse solicitarRecuperacion(
            @Valid @RequestBody SolicitudRecuperacionPasswordRequest request) {
        return new RecuperacionPasswordResponse(
                recuperacionPasswordService.solicitarRecuperacion(request));
    }

    @PostMapping("/restablecer-password")
    public RecuperacionPasswordResponse restablecerPassword(
            @Valid @RequestBody RestablecerPasswordRequest request) {
        return new RecuperacionPasswordResponse(
                recuperacionPasswordService.restablecerPassword(request));
    }

    @PostMapping("/login")
    public UsuarioResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        UsuarioResponse usuario = authService.iniciarSesion(request);
        httpRequest.getSession(true);
        httpRequest.changeSessionId();

        actualizarAutenticacion(usuario, httpRequest, httpResponse);

        return usuario;
    }

    @GetMapping("/me")
    public UsuarioResponse me(Authentication authentication) {
        return authService.obtenerUsuarioAutenticado(authentication.getName());
    }

    @PostMapping("/cambiar-password")
    public UsuarioResponse cambiarPassword(
            @Valid @RequestBody CambioPasswordRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        boolean cambioObligatorio = authentication.getAuthorities().stream()
                .anyMatch(authority -> "PASSWORD_CHANGE_REQUIRED".equals(authority.getAuthority()));
        UsuarioResponse usuario = authService.cambiarPassword(authentication.getName(), request);
        if (cambioObligatorio) {
            actualizarAutenticacion(usuario, httpRequest, httpResponse);
        }
        return usuario;
    }

    private void actualizarAutenticacion(
            UsuarioResponse usuario,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String authority = usuario.debeCambiarPassword()
                ? "PASSWORD_CHANGE_REQUIRED"
                : "ROLE_" + usuario.rol();
        var authentication = new UsernamePasswordAuthenticationToken(
                usuario.email(),
                null,
                List.of(new SimpleGrantedAuthority(authority)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        sessionRegistry.registerNewSession(httpRequest.getSession(true).getId(), usuario.email());
    }

    public record CsrfTokenResponse(String token, String headerName) {
    }

    public record RegistroInicialDisponibilidadResponse(boolean disponible) {
    }

    public record RecuperacionPasswordResponse(String mensaje) {
    }
}