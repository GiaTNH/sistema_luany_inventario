package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.MovimientoRepository;
import com.luany.inventory.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimientoService {

    private final MovimientoRepository movimientoRepository;
    private final ProductoRepository productoRepository;

    @Transactional
    public Movimiento registrar(MovimientoRequest request) {
        Producto producto = productoRepository.findById(request.getProductoId())
                .orElseThrow(() -> new IllegalArgumentException("El producto no existe"));

        int cantidad = request.getCantidad();

        if (request.getTipo() == TipoMovimiento.ENTRADA) {
            producto.setStockActual(producto.getStockActual() + cantidad);
        } else {
            if (producto.getStockActual() < cantidad) {
                throw new IllegalArgumentException(
                        "Stock insuficiente: hay " + producto.getStockActual()
                                + " unidades y se piden " + cantidad);
            }
            producto.setStockActual(producto.getStockActual() - cantidad);
        }
        productoRepository.save(producto);

        Movimiento movimiento = new Movimiento();
        movimiento.setProducto(producto);
        movimiento.setTipo(request.getTipo());
        movimiento.setCantidad(cantidad);
        movimiento.setObservacion(request.getObservacion());
        return movimientoRepository.save(movimiento);
    }

    public List<Movimiento> listarPorProducto(Long productoId) {
        return movimientoRepository.findPorProducto(productoId);
    }

    public List<Movimiento> listarTodos() {
        return movimientoRepository.findAll();
    }
}