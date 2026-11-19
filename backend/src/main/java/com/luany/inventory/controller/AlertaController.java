package com.luany.inventory.controller;

import com.luany.inventory.model.AlertaStock;
import com.luany.inventory.model.NotificarStockRequest;
import com.luany.inventory.service.AlertaStockNotificacionService;
import com.luany.inventory.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

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
    public ResponseEntity<NotificacionStockResponse> notificarStock(
            @Valid @RequestBody NotificarStockRequest request) {
        int cantidadProductos =
                alertaStockNotificacionService.notificarAdministradores(request.destinatarioIds());
        return ResponseEntity.ok(new NotificacionStockResponse(
                "Se envió la alerta de inventario a los destinatarios seleccionados.",
                cantidadProductos));
    }

    @GetMapping("/destinatarios")
    public List<AlertaStockNotificacionService.Destinatario> destinatarios() {
        return alertaStockNotificacionService.destinatariosDisponibles();
    }

    public record NotificacionStockResponse(String mensaje, int cantidadProductos) {
    }
}
