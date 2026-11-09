package com.luany.inventory.controller;

import com.luany.inventory.model.CrearNotaVentaRequest;
import com.luany.inventory.model.NotaVentaDetalleResponse;
import com.luany.inventory.model.NotaVentaResumenResponse;
import com.luany.inventory.service.NotaVentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notas-venta")
@RequiredArgsConstructor
public class NotaVentaController {

    private final NotaVentaService notaVentaService;

    @GetMapping
    public List<NotaVentaResumenResponse> listar(@RequestParam(required = false) String buscar) {
        return notaVentaService.listar(buscar);
    }

    @GetMapping("/{id}")
    public NotaVentaDetalleResponse obtener(@PathVariable Long id) {
        return notaVentaService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotaVentaDetalleResponse crear(
            @Valid @RequestBody CrearNotaVentaRequest request,
            Authentication authentication) {
        return notaVentaService.crear(request, authentication.getName());
    }
}
