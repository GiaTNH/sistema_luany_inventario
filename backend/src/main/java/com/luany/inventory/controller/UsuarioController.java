package com.luany.inventory.controller;

import com.luany.inventory.model.CrearEmpleadoRequest;
import com.luany.inventory.model.UsuarioCreadoResponse;
import com.luany.inventory.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final AuthService authService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioCreadoResponse crearEmpleado(@Valid @RequestBody CrearEmpleadoRequest request) {
        return authService.crearEmpleado(request);
    }
}
