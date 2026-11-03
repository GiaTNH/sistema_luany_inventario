package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.CategoriaRepository;
import com.luany.inventory.repository.ProductoRepository;
import com.luany.inventory.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProveedorRepository proveedorRepository;

    public List<Producto> listar() {
        return productoRepository.findAll();
    }

    public Producto obtener(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El producto no existe"));
    }

    public List<Producto> buscarPorNombre(String nombre) {
        return productoRepository.buscarPorNombre(nombre);
    }

    public List<Producto> productosConStockBajo() {
        return productoRepository.findStockBajo();
    }

    @Transactional
    public Producto crear(ProductoRequest request) {
        Producto producto = new Producto();
        aplicarDatos(producto, request);
        return productoRepository.save(producto);
    }

    @Transactional
    public Producto actualizar(Long id, ProductoRequest request) {
        Producto producto = obtener(id);
        aplicarDatos(producto, request);
        return productoRepository.save(producto);
    }

    @Transactional
    public void eliminar(Long id) {
        Producto producto = obtener(id);
        productoRepository.delete(producto);
    }

    public String estadoStock(Producto p) {
        if (p.getStockActual() <= 0) {
            return "AGOTADO";
        }
        if (p.getStockActual() <= p.getStockMinimo()) {
            return "BAJO";
        }
        if (p.getStockActual() >= p.getStockMaximo()) {
            return "EXCESO";
        }
        return "NORMAL";
    }

    public List<AlertaStock> alertas() {
        return productoRepository.findStockBajo().stream()
                .map(p -> new AlertaStock(
                        p.getId(),
                        p.getNombre(),
                        p.getStockActual(),
                        p.getStockMinimo(),
                        estadoStock(p),
                        Math.max(p.getStockMaximo() - p.getStockActual(), 0)))
                .toList();
    }

    private void aplicarDatos(Producto producto, ProductoRequest r) {
        if (r.getStockMaximo() < r.getStockMinimo()) {
            throw new IllegalArgumentException("El stock máximo no puede ser menor que el mínimo");
        }
        Categoria categoria = categoriaRepository.findById(r.getCategoriaId())
                .orElseThrow(() -> new IllegalArgumentException("La categoría no existe"));
        Proveedor proveedor = proveedorRepository.findById(r.getProveedorId())
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no existe"));

        producto.setNombre(r.getNombre().trim());
        producto.setCategoria(categoria);
        producto.setProveedor(proveedor);
        producto.setStockActual(r.getStockActual());
        producto.setStockMinimo(r.getStockMinimo());
        producto.setStockMaximo(r.getStockMaximo());
        producto.setPrecio(r.getPrecio());
    }
}