package com.luany.inventory.service;

import com.luany.inventory.model.Producto;
import com.luany.inventory.repository.ProductoStockLockRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoStockLockServiceTests {

    @Mock
    private ProductoStockLockRepository productoStockLockRepository;
    @Mock
    private EntityManager entityManager;

    @Test
    void bloqueaProductosEnOrdenAscendenteYRefrescaElStockBajoBloqueo() {
        Producto productoDos = producto(2L);
        Producto productoSiete = producto(7L);
        when(productoStockLockRepository.buscarPorIdBloqueando(2L)).thenReturn(Optional.of(productoDos));
        when(productoStockLockRepository.buscarPorIdBloqueando(7L)).thenReturn(Optional.of(productoSiete));
        ProductoStockLockService service =
                new ProductoStockLockService(productoStockLockRepository, entityManager);

        List<Producto> bloqueados = service.bloquear(List.of(7L, 2L, 7L));

        assertEquals(List.of(productoDos, productoSiete), bloqueados);
        InOrder orden = inOrder(productoStockLockRepository, entityManager);
        orden.verify(productoStockLockRepository).buscarPorIdBloqueando(2L);
        orden.verify(entityManager).refresh(productoDos, LockModeType.PESSIMISTIC_WRITE);
        orden.verify(productoStockLockRepository).buscarPorIdBloqueando(7L);
        orden.verify(entityManager).refresh(productoSiete, LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager).refresh(productoDos, LockModeType.PESSIMISTIC_WRITE);
    }

    private Producto producto(Long id) {
        Producto producto = new Producto();
        producto.setId(id);
        return producto;
    }
}
