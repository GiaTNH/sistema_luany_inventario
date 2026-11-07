package com.luany.inventory.service;

import com.luany.inventory.model.Proveedor;
import com.luany.inventory.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    public List<Proveedor> listar() {
        return proveedorRepository.findAll();
    }

    public Proveedor crear(Proveedor datos) {
        validar(datos);
        Proveedor proveedor = new Proveedor();
        aplicarDatos(proveedor, datos);
        return proveedorRepository.save(proveedor);
    }

    public Proveedor actualizar(Long id, Proveedor datos) {
        validar(datos);
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no existe"));
        aplicarDatos(proveedor, datos);
        return proveedorRepository.save(proveedor);
    }

    public void eliminar(Long id) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no existe"));
        proveedorRepository.delete(proveedor);
    }

    // Los campos opcionales (ruc, correo, direccion, estado) solo se cambian
    // si vienen en la peticion; asi el formulario actual no los borra.
    private void aplicarDatos(Proveedor proveedor, Proveedor datos) {
        proveedor.setNombre(datos.getNombre().trim());
        proveedor.setTelefono(datos.getTelefono());
        if (datos.getRuc() != null) {
            String ruc = datos.getRuc().trim();
            if (ruc.isEmpty()) {
                proveedor.setRuc(null);
            } else {
                proveedorRepository.findByRuc(ruc)
                        .filter(otro -> !otro.getId().equals(proveedor.getId()))
                        .ifPresent(otro -> {
                            throw new IllegalArgumentException("Ya existe un proveedor con ese RUC");
                        });
                proveedor.setRuc(ruc);
            }
        }
        if (datos.getCorreo() != null) {
            proveedor.setCorreo(vacioANulo(datos.getCorreo()));
        }
        if (datos.getDireccion() != null) {
            proveedor.setDireccion(vacioANulo(datos.getDireccion()));
        }
        if (datos.getEstado() != null && !datos.getEstado().isBlank()) {
            proveedor.setEstado(datos.getEstado().trim());
        }
    }

    private String vacioANulo(String valor) {
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private void validar(Proveedor datos) {
        if (datos.getNombre() == null || datos.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del proveedor es obligatorio");
        }
    }
}
