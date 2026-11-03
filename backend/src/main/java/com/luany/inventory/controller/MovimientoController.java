package com.luany.inventory.controller;

import com.luany.inventory.model.Movimiento;
import com.luany.inventory.model.MovimientoRequest;
import com.luany.inventory.service.MovimientoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final MovimientoService movimientoService;

    @PostMapping
    public Movimiento registrar(@Valid @RequestBody MovimientoRequest request) {
        return movimientoService.registrar(request);
    }

    @GetMapping
    public List<Movimiento> listar() {
        return movimientoService.listarTodos();
    }

    @GetMapping("/producto/{productoId}")
    public List<Movimiento> listarPorProducto(@PathVariable Long productoId) {
        return movimientoService.listarPorProducto(productoId);
    }
}