package com.luany.inventory.controller;

import com.luany.inventory.model.ConsultaRequest;
import com.luany.inventory.model.RespuestaAsistente;
import com.luany.inventory.service.AsistenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/asistente")
@RequiredArgsConstructor
public class AsistenteController {

    private final AsistenteService asistenteService;

    @PostMapping("/consulta")
    public RespuestaAsistente consultar(@Valid @RequestBody ConsultaRequest request) {
        return asistenteService.consultar(request.getPregunta());
    }
}