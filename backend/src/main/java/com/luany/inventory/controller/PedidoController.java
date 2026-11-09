package com.luany.inventory.controller;

import com.luany.inventory.model.CrearPedidoRequest;
import com.luany.inventory.model.PedidoDetalleResponse;
import com.luany.inventory.model.PedidoResumenResponse;
import com.luany.inventory.model.RegistrarRecepcionRequest;
import com.luany.inventory.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @GetMapping
    public List<PedidoResumenResponse> listar(
            @RequestParam(required = false) String buscar,
            @RequestParam(required = false) String estado) {
        return pedidoService.listar(buscar, estado);
    }

    @GetMapping("/{id}")
    public PedidoDetalleResponse obtener(@PathVariable Long id) {
        return pedidoService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoDetalleResponse crear(
            @Valid @RequestBody CrearPedidoRequest request,
            Authentication authentication) {
        return pedidoService.crear(request, authentication.getName());
    }

    @PostMapping("/{id}/recepciones")
    public PedidoDetalleResponse recibir(
            @PathVariable Long id,
            @Valid @RequestBody RegistrarRecepcionRequest request,
            Authentication authentication) {
        return pedidoService.recibir(id, request, authentication.getName());
    }
}
