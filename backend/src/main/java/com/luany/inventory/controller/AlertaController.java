package com.luany.inventory.controller;

import com.luany.inventory.model.AlertaStock;
import com.luany.inventory.service.AlertaStockNotificacionService;
import com.luany.inventory.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alertas")
@RequiredArgsConstructor
public class AlertaController {

    private final ProductoService productoService;
    private final AlertaStockNotificacionService alertaStockNotificacionService;

    @GetMapping("/stock")
    public List<AlertaStock> alertasDeStock() {
        return productoService.alertas();
    }

    @PostMapping("/notificar-stock")
    public ResponseEntity<NotificacionStockResponse> notificarStock() {
        int cantidadProductos = alertaStockNotificacionService.notificarAdministradores();
        return ResponseEntity.ok(new NotificacionStockResponse(
                "Se envió la alerta de inventario al correo del administrador.",
                cantidadProductos));
    }

    public record NotificacionStockResponse(String mensaje, int cantidadProductos) {
    }
}
