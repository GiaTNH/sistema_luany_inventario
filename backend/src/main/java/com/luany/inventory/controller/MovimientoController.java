package com.luany.inventory.controller;

import com.luany.inventory.model.Movimiento;
import com.luany.inventory.model.MovimientoRequest;
import com.luany.inventory.model.TipoMovimiento;
import com.luany.inventory.service.MovimientoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final MovimientoService movimientoService;

    @PostMapping
    public Movimiento registrar(
            @Valid @RequestBody MovimientoRequest request,
            Authentication authentication) {
        return movimientoService.registrar(request, authentication.getName());
    }

    @GetMapping
    public List<Movimiento> listar() {
        return movimientoService.listarTodos();
    }

    @GetMapping("/pagina")
    public Page<Movimiento> listarPaginado(
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return movimientoService.listarPaginado(productoId, tipo, desde, hasta, pagina, tamano);
    }

    @GetMapping("/producto/{productoId}")
    public List<Movimiento> listarPorProducto(@PathVariable Long productoId) {
        return movimientoService.listarPorProducto(productoId);
    }
}
