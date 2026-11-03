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
        proveedor.setNombre(datos.getNombre().trim());
        proveedor.setTelefono(datos.getTelefono());
        return proveedorRepository.save(proveedor);
    }

    public Proveedor actualizar(Long id, Proveedor datos) {
        validar(datos);
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no existe"));
        proveedor.setNombre(datos.getNombre().trim());
        proveedor.setTelefono(datos.getTelefono());
        return proveedorRepository.save(proveedor);
    }

    public void eliminar(Long id) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no existe"));
        proveedorRepository.delete(proveedor);
    }

    private void validar(Proveedor datos) {
        if (datos.getNombre() == null || datos.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del proveedor es obligatorio");
        }
    }
}