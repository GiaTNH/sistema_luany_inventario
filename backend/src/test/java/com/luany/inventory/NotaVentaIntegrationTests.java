package com.luany.inventory;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.*;
import com.luany.inventory.service.MovimientoService;
import com.luany.inventory.service.NotaVentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:luanysales;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "brevo.api-key=",
        "mail.from.email=",
        "mail.from.name=LUANY"
})
class NotaVentaIntegrationTests {

    @Autowired
    private NotaVentaService notaVentaService;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private ProveedorRepository proveedorRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private NotaVentaRepository notaVentaRepository;
    @Autowired
    private DetalleVentaRepository detalleVentaRepository;
    @Autowired
    private MovimientoRepository movimientoRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    private MovimientoService movimientoService;

    private Cliente cliente;
    private Usuario usuario;
    private Producto producto;

    @BeforeEach
    void limpiarYPreparar() {
        jdbcTemplate.update("DELETE FROM detalle_venta");
        jdbcTemplate.update("DELETE FROM notas_venta");
        jdbcTemplate.update("DELETE FROM movimientos");
        jdbcTemplate.update("DELETE FROM tokens_recuperacion_password");
        jdbcTemplate.update("DELETE FROM productos");
        jdbcTemplate.update("DELETE FROM clientes");
        jdbcTemplate.update("DELETE FROM proveedores");
        jdbcTemplate.update("DELETE FROM categorias");
        jdbcTemplate.update("DELETE FROM usuarios");

        Categoria categoria = new Categoria();
        categoria.setNombre("Ropa");
        categoria = categoriaRepository.saveAndFlush(categoria);

        Proveedor proveedor = new Proveedor();
        proveedor.setNombre("Proveedor ventas");
        proveedor.setEstado("ACTIVO");
        proveedor = proveedorRepository.saveAndFlush(proveedor);

        cliente = new Cliente();
        cliente.setNombre("Cliente prueba");
        cliente.setTipoDocumento("DNI");
        cliente.setNumeroDocumento("87654321");
        cliente = clienteRepository.saveAndFlush(cliente);

        usuario = new Usuario();
        usuario.setNombre("Vendedora");
        usuario.setEmail("ventas@luany.com");
        usuario.setNombreUsuario("ventas");
        usuario.setPassword("hash");
        usuario.setRol("EMPLEADO");
        usuario.setEstado("ACTIVO");
        usuario = usuarioRepository.saveAndFlush(usuario);

        producto = new Producto();
        producto.setNombre("Blusa");
        producto.setCategoria(categoria);
        producto.setProveedor(proveedor);
        producto.setStockActual(1);
        producto.setStockMinimo(0);
        producto.setStockMaximo(100);
        producto.setPrecio(10.50);
        producto = productoRepository.saveAndFlush(producto);
    }

    @Test
    void transaccionRevierteNotaDetalleStockYMovimientoSiFallaUnMovimiento() {
        doThrow(new IllegalStateException("Fallo de movimiento"))
                .when(movimientoService)
                .registrar(any(MovimientoRequest.class), eq(usuario.getEmail()));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> notaVentaService.crear(request(1), usuario.getEmail()));

        assertEquals(0, notaVentaRepository.count());
        assertEquals(0, detalleVentaRepository.count());
        assertEquals(0, movimientoRepository.count());
        assertEquals(1, productoRepository.findById(producto.getId()).orElseThrow().getStockActual());
    }

    @Test
    void ventasConcurrentesConUnaUnidadDeStockSoloPermitenUnaVenta() throws Exception {
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch listos = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var primera = executor.submit(() -> venderConcurrentemente(inicio, listos));
            var segunda = executor.submit(() -> venderConcurrentemente(inicio, listos));
            assertTrue(listos.await(5, TimeUnit.SECONDS));
            inicio.countDown();

            int exitosas = (primera.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (segunda.get(15, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, exitosas);
            assertEquals(1, notaVentaRepository.count());
            assertEquals(1, detalleVentaRepository.count());
            assertEquals(1, movimientoRepository.count());
            assertEquals(0, productoRepository.findById(producto.getId()).orElseThrow().getStockActual());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void numerosDeNotasSimultaneasSonDistintosYElStockQuedaConsistente() throws Exception {
        producto.setStockActual(2);
        productoRepository.saveAndFlush(producto);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch listos = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var primera = executor.submit(() -> crearConcurrentemente(inicio, listos));
            var segunda = executor.submit(() -> crearConcurrentemente(inicio, listos));
            assertTrue(listos.await(5, TimeUnit.SECONDS));
            inicio.countDown();

            String numeroPrimero = primera.get(15, TimeUnit.SECONDS);
            String numeroSegundo = segunda.get(15, TimeUnit.SECONDS);

            assertNotEquals(numeroPrimero, numeroSegundo);
            assertEquals(2, notaVentaRepository.count());
            assertEquals(2, movimientoRepository.count());
            assertEquals(0, productoRepository.findById(producto.getId()).orElseThrow().getStockActual());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean venderConcurrentemente(CountDownLatch inicio, CountDownLatch listos) throws Exception {
        listos.countDown();
        inicio.await();
        try {
            notaVentaService.crear(request(1), usuario.getEmail());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String crearConcurrentemente(CountDownLatch inicio, CountDownLatch listos) throws Exception {
        listos.countDown();
        inicio.await();
        return notaVentaService.crear(request(1), usuario.getEmail()).numeroNota();
    }

    private CrearNotaVentaRequest request(int cantidad) {
        CrearDetalleVentaRequest linea = new CrearDetalleVentaRequest();
        linea.setProductoId(producto.getId());
        linea.setCantidad(cantidad);
        CrearNotaVentaRequest request = new CrearNotaVentaRequest();
        request.setClienteId(cliente.getId());
        request.setProductos(List.of(linea));
        return request;
    }
}
