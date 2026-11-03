package com.luany.inventory.service;

import com.luany.inventory.model.Categoria;
import com.luany.inventory.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public List<Categoria> listar() {
        return categoriaRepository.findAll();
    }

    public Categoria crear(Categoria datos) {
        validar(datos);
        Categoria categoria = new Categoria();
        categoria.setNombre(datos.getNombre().trim());
        return categoriaRepository.save(categoria);
    }

    public Categoria actualizar(Long id, Categoria datos) {
        validar(datos);
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La categoría no existe"));
        categoria.setNombre(datos.getNombre().trim());
        return categoriaRepository.save(categoria);
    }

    public void eliminar(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La categoría no existe"));
        categoriaRepository.delete(categoria);
    }

    private void validar(Categoria datos) {
        if (datos.getNombre() == null || datos.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría es obligatorio");
        }
    }
}