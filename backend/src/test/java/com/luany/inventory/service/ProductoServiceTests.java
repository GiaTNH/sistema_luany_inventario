package com.luany.inventory.service;

import com.luany.inventory.model.AlertaStock;
import com.luany.inventory.model.Categoria;
import com.luany.inventory.model.Producto;
import com.luany.inventory.repository.CategoriaRepository;
import com.luany.inventory.repository.ProductoRepository;
import com.luany.inventory.repository.ProveedorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTests {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private ProveedorRepository proveedorRepository;

    @InjectMocks
    private ProductoService productoService;

    @Test
    void alertasIncluyeCategoriaYClasificaStockCriticoYSinDuplicados() {
        Producto critico = producto(1L, "Blusa", "Ropa", 0, 3, 10);
        Producto bajo = producto(2L, "Pantalón", "Ropa", 2, 2, 10);
        when(productoRepository.findStockBajo()).thenReturn(List.of(critico, bajo));

        List<AlertaStock> alertas = productoService.alertas();

        assertEquals(2, alertas.size());
        assertEquals(new AlertaStock(1L, "Blusa", "Ropa", 0, 3, "AGOTADO", 10), alertas.get(0));
        assertEquals(new AlertaStock(2L, "Pantalón", "Ropa", 2, 2, "BAJO", 8), alertas.get(1));
        verify(productoRepository).findStockBajo();
    }

    private Producto producto(
            Long id,
            String nombre,
            String nombreCategoria,
            int stockActual,
            int stockMinimo,
            int stockMaximo) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setCategoria(new Categoria(1L, nombreCategoria));
        producto.setStockActual(stockActual);
        producto.setStockMinimo(stockMinimo);
        producto.setStockMaximo(stockMaximo);
        return producto;
    }
}
