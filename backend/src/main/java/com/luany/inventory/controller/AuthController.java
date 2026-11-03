package com.luany.inventory.controller;

import com.luany.inventory.model.LoginRequest;
import com.luany.inventory.model.RegistroRequest;
import com.luany.inventory.model.UsuarioResponse;
import com.luany.inventory.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registro")
    public UsuarioResponse registrar(@Valid @RequestBody RegistroRequest request) {
        return authService.registrar(request);
    }

    @PostMapping("/login")
    public UsuarioResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.iniciarSesion(request);
    }
}