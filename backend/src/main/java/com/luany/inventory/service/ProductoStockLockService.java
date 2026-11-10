package com.luany.inventory.service;

import com.luany.inventory.model.Producto;
import com.luany.inventory.repository.ProductoStockLockRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ProductoStockLockService {

    private final ProductoStockLockRepository productoStockLockRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Producto> bloquear(Collection<Long> productoIds) {
        List<Producto> productos = new ArrayList<>();
        for (Long productoId : new TreeSet<>(productoIds)) {
            Producto producto = productoStockLockRepository.buscarPorIdBloqueando(productoId)
                    .orElseThrow(() -> new IllegalArgumentException("El producto no existe"));
            entityManager.refresh(producto, LockModeType.PESSIMISTIC_WRITE);
            productos.add(producto);
        }
        return productos;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Producto bloquear(Long productoId) {
        return bloquear(List.of(productoId)).get(0);
    }
}
