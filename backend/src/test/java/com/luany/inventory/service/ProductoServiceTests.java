package com.luany.inventory.service;

import com.luany.inventory.model.AlertaStock;
import com.luany.inventory.model.Categoria;
import com.luany.inventory.model.Producto;
import com.luany.inventory.model.ProductoActualizacionRequest;
import com.luany.inventory.model.ProductoRequest;
import com.luany.inventory.model.Proveedor;
import com.luany.inventory.repository.CategoriaRepository;
import com.luany.inventory.repository.ProductoRepository;
import com.luany.inventory.repository.ProveedorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    @Mock
    private ProductoStockLockService productoStockLockService;

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

    @Test
    void crearProductoConservaElStockInicialSolicitado() {
        Categoria categoria = new Categoria(2L, "Ropa");
        Proveedor proveedor = new Proveedor();
        proveedor.setId(3L);
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoria));
        when(proveedorRepository.findById(3L)).thenReturn(Optional.of(proveedor));
        when(productoRepository.save(org.mockito.ArgumentMatchers.any(Producto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductoRequest request = new ProductoRequest();
        request.setNombre("Vestido");
        request.setCategoriaId(2L);
        request.setProveedorId(3L);
        request.setStockActual(6);
        request.setStockMinimo(1);
        request.setStockMaximo(20);
        request.setPrecio(12.5);

        Producto creado = productoService.crear(request);

        assertEquals(6, creado.getStockActual());
    }

    @Test
    void actualizarProductoNoPermiteCambiarStockActual() {
        Producto existente = producto(10L, "Vestido", "Ropa", 8, 1, 20);
        Categoria categoria = new Categoria(2L, "Ropa");
        Proveedor proveedor = new Proveedor();
        proveedor.setId(3L);
        when(productoStockLockService.bloquear(10L)).thenReturn(existente);
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoria));
        when(proveedorRepository.findById(3L)).thenReturn(Optional.of(proveedor));
        when(productoRepository.save(existente)).thenReturn(existente);

        ProductoActualizacionRequest request = new ProductoActualizacionRequest();
        request.setNombre("Vestido actualizado");
        request.setCategoriaId(2L);
        request.setProveedorId(3L);
        request.setStockMinimo(1);
        request.setStockMaximo(20);
        request.setPrecio(12.5);

        Producto actualizado = productoService.actualizar(10L, request);

        assertEquals(8, actualizado.getStockActual());
        assertEquals("Vestido actualizado", actualizado.getNombre());
        verify(productoStockLockService).bloquear(10L);
    }

    @Test
    void crearProductoRechazaCodigoDuplicadoAntesDeGuardar() {
        Categoria categoria = new Categoria(2L, "Ropa");
        Proveedor proveedor = new Proveedor();
        proveedor.setId(3L);
        Producto existente = producto(10L, "Otro producto", "Ropa", 0, 0, 20);
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoria));
        when(proveedorRepository.findById(3L)).thenReturn(Optional.of(proveedor));
        when(productoRepository.findByCodigo("LUANY-01")).thenReturn(Optional.of(existente));

        ProductoRequest request = new ProductoRequest();
        request.setNombre("Vestido");
        request.setCategoriaId(2L);
        request.setProveedorId(3L);
        request.setStockActual(0);
        request.setStockMinimo(0);
        request.setStockMaximo(20);
        request.setPrecio(12.5);
        request.setCodigo(" LUANY-01 ");

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> productoService.crear(request));

        assertEquals("Ya existe un producto con ese código", error.getMessage());
        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void eliminarProductoReferenciadoMuestraErrorLegible() {
        Producto producto = producto(10L, "Vestido", "Ropa", 8, 1, 20);
        when(productoRepository.findById(10L)).thenReturn(Optional.of(producto));
        doThrow(new DataIntegrityViolationException("detalle técnico"))
                .when(productoRepository).flush();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> productoService.eliminar(10L));

        assertEquals(
                "No se puede eliminar el producto porque está asociado a movimientos, pedidos, ventas u otros registros.",
                error.getMessage());
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
