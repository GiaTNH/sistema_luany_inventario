package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.MovimientoRepository;
import com.luany.inventory.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimientoService {

    private final MovimientoRepository movimientoRepository;
    private final ProductoStockLockService productoStockLockService;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public Movimiento registrar(MovimientoRequest request) {
        return registrar(request, null);
    }

    @Transactional
    public Movimiento registrar(MovimientoRequest request, String emailUsuario) {
        Producto producto = productoStockLockService.bloquear(request.getProductoId());

        int cantidad = request.getCantidad();
        int stockAnterior = producto.getStockActual();

        if (request.getTipo() == TipoMovimiento.ENTRADA) {
            if (stockAnterior < 0 || cantidad > Integer.MAX_VALUE - stockAnterior) {
                throw new IllegalArgumentException(
                        "La entrada supera el máximo permitido para el stock");
            }
            producto.setStockActual(stockAnterior + cantidad);
        } else {
            if (producto.getStockActual() < cantidad) {
                throw new IllegalArgumentException(
                        "Stock insuficiente: hay " + producto.getStockActual()
                                + " unidades y se piden " + cantidad);
            }
            producto.setStockActual(producto.getStockActual() - cantidad);
        }
        Movimiento movimiento = new Movimiento();
        movimiento.setProducto(producto);
        movimiento.setTipo(request.getTipo());
        movimiento.setCantidad(cantidad);
        movimiento.setObservacion(request.getObservacion());
        movimiento.setStockAnterior(stockAnterior);
        movimiento.setStockNuevo(producto.getStockActual());
        if (emailUsuario != null) {
            usuarioRepository.buscarPorEmail(emailUsuario).ifPresent(movimiento::setUsuario);
        }
        return movimientoRepository.save(movimiento);
    }

    public List<Movimiento> listarPorProducto(Long productoId) {
        return movimientoRepository.findPorProducto(productoId);
    }

    public List<Movimiento> listarTodos() {
        return movimientoRepository.findAll();
    }
}
