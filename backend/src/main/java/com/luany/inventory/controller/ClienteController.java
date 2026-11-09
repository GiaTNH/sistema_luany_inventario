package com.luany.inventory.controller;

import com.luany.inventory.model.Cliente;
import com.luany.inventory.model.ClienteRequest;
import com.luany.inventory.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    public List<Cliente> listar(@RequestParam(required = false) String buscar) {
        return clienteService.listar(buscar);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Cliente crear(@Valid @RequestBody ClienteRequest request) {
        return clienteService.crear(request);
    }

    @PutMapping("/{id}")
    public Cliente actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return clienteService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> eliminar(@PathVariable Long id) {
        clienteService.eliminar(id);
        return Map.of("mensaje", "Cliente eliminado");
    }
}
