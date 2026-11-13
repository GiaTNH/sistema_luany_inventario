package com.luany.inventory;

import com.luany.inventory.model.Categoria;
import com.luany.inventory.model.Movimiento;
import com.luany.inventory.model.Producto;
import com.luany.inventory.model.Proveedor;
import com.luany.inventory.model.TipoMovimiento;
import com.luany.inventory.repository.CategoriaRepository;
import com.luany.inventory.repository.MovimientoRepository;
import com.luany.inventory.repository.ProductoRepository;
import com.luany.inventory.repository.ProveedorRepository;
import com.luany.inventory.service.MovimientoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:luanymovimientos;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "brevo.api-key=",
        "mail.from.email=",
        "mail.from.name=LUANY"
})
class MovimientoPaginacionIntegrationTests {

    @Autowired private MovimientoService movimientoService;
    @Autowired private MovimientoRepository movimientoRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ProveedorRepository proveedorRepository;

    private Producto producto;

    @BeforeEach
    void prepararDatos() {
        movimientoRepository.deleteAll();
        productoRepository.deleteAll();
        proveedorRepository.deleteAll();
        categoriaRepository.deleteAll();

        Categoria categoria = new Categoria();
        categoria.setNombre("Prueba paginación");
        categoria = categoriaRepository.saveAndFlush(categoria);

        Proveedor proveedor = new Proveedor();
        proveedor.setNombre("Proveedor paginación");
        proveedor = proveedorRepository.saveAndFlush(proveedor);

        producto = new Producto();
        producto.setNombre("Producto paginación");
        producto.setCategoria(categoria);
        producto.setProveedor(proveedor);
        producto.setStockActual(10);
        producto.setStockMinimo(1);
        producto.setStockMaximo(20);
        producto.setPrecio(8.5);
        producto = productoRepository.saveAndFlush(producto);

        guardarMovimiento(TipoMovimiento.ENTRADA, LocalDateTime.of(2026, 11, 4, 23, 59, 59));
        guardarMovimiento(TipoMovimiento.SALIDA, LocalDateTime.of(2026, 11, 4, 20, 0));
        guardarMovimiento(TipoMovimiento.ENTRADA, LocalDateTime.of(2026, 11, 5, 0, 0));
    }

    @Test
    void paginaMovimientosOrdenadaYFiltradaIncluyeElDiaFinalCompleto() {
        var pagina = movimientoService.listarPaginado(
                null, null, null, null, 0, 2);

        assertEquals(3, pagina.getTotalElements());
        assertEquals(2, pagina.getContent().size());
        assertEquals(LocalDateTime.of(2026, 11, 5, 0, 0), pagina.getContent().get(0).getFecha());
        assertEquals(LocalDateTime.of(2026, 11, 4, 23, 59, 59), pagina.getContent().get(1).getFecha());

        var filtrada = movimientoService.listarPaginado(
                producto.getId(), TipoMovimiento.ENTRADA,
                LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4), 0, 20);

        assertEquals(1, filtrada.getTotalElements());
        assertEquals(LocalDateTime.of(2026, 11, 4, 23, 59, 59), filtrada.getContent().get(0).getFecha());
    }

    private void guardarMovimiento(TipoMovimiento tipo, LocalDateTime fecha) {
        Movimiento movimiento = new Movimiento();
        movimiento.setProducto(producto);
        movimiento.setTipo(tipo);
        movimiento.setCantidad(1);
        movimiento.setFecha(fecha);
        movimientoRepository.saveAndFlush(movimiento);
    }
}
