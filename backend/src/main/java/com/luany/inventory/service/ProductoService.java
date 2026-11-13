package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.CategoriaRepository;
import com.luany.inventory.repository.ProductoRepository;
import com.luany.inventory.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoStockLockService productoStockLockService;

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
        aplicarDatos(producto, request.getNombre(), request.getCategoriaId(), request.getProveedorId(),
                request.getStockMinimo(), request.getStockMaximo(), request.getPrecio(),
                request.getCodigo(), request.getDescripcion());
        producto.setStockActual(request.getStockActual());
        return productoRepository.save(producto);
    }

    @Transactional
    public Producto actualizar(Long id, ProductoActualizacionRequest request) {
        Producto producto = productoStockLockService.bloquear(id);
        aplicarDatos(producto, request.getNombre(), request.getCategoriaId(), request.getProveedorId(),
                request.getStockMinimo(), request.getStockMaximo(), request.getPrecio(),
                request.getCodigo(), request.getDescripcion());
        return productoRepository.save(producto);
    }

    @Transactional
    public void eliminar(Long id) {
        Producto producto = obtener(id);
        try {
            productoRepository.delete(producto);
            productoRepository.flush();
        } catch (DataIntegrityViolationException error) {
            throw new IllegalArgumentException(
                    "No se puede eliminar el producto porque está asociado a movimientos, pedidos, ventas u otros registros.");
        }
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
                        p.getCategoria() == null ? null : p.getCategoria().getNombre(),
                        p.getStockActual(),
                        p.getStockMinimo(),
                        p.getStockActual() == 0 ? "AGOTADO" : "BAJO",
                        Math.max(p.getStockMaximo() - p.getStockActual(), 0)))
                .toList();
    }

    private void aplicarDatos(
            Producto producto,
            String nombre,
            Long categoriaId,
            Long proveedorId,
            Integer stockMinimo,
            Integer stockMaximo,
            Double precio,
            String codigo,
            String descripcion) {
        if (stockMaximo < stockMinimo) {
            throw new IllegalArgumentException("El stock máximo no puede ser menor que el mínimo");
        }
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("La categoría no existe"));
        Proveedor proveedor = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no existe"));

        producto.setNombre(nombre.trim());
        producto.setCategoria(categoria);
        producto.setProveedor(proveedor);
        producto.setStockMinimo(stockMinimo);
        producto.setStockMaximo(stockMaximo);
        producto.setPrecio(precio);
        // codigo y descripcion son opcionales: si el formulario no los envia,
        // se conservan los valores que ya tenia el producto.
        if (codigo != null) {
            String codigoLimpio = codigo.trim();
            if (codigoLimpio.isEmpty()) {
                producto.setCodigo(null);
            } else {
                productoRepository.findByCodigo(codigoLimpio)
                        .filter(otro -> !otro.getId().equals(producto.getId()))
                        .ifPresent(otro -> {
                            throw new IllegalArgumentException("Ya existe un producto con ese código");
                        });
                producto.setCodigo(codigoLimpio);
            }
        }
        if (descripcion != null) {
            String descripcionLimpia = descripcion.trim();
            producto.setDescripcion(descripcionLimpia.isEmpty() ? null : descripcionLimpia);
        }
    }
}
