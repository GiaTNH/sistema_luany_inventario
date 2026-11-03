package com.luany.inventory.controller;

import com.luany.inventory.model.AlertaStock;
import com.luany.inventory.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alertas")
@RequiredArgsConstructor
public class AlertaController {

    private final ProductoService productoService;

    @GetMapping("/stock")
    public List<AlertaStock> alertasDeStock() {
        return productoService.alertas();
    }
}