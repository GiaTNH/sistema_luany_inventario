package com.luany.inventory;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.*;
import com.luany.inventory.service.MovimientoService;
import com.luany.inventory.service.NotaVentaService;
import com.luany.inventory.service.PedidoService;
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
    private PedidoService pedidoService;
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
    private PedidoRepository pedidoRepository;
    @Autowired
    private DetallePedidoRepository detallePedidoRepository;
    @Autowired
    private RecepcionPedidoRepository recepcionPedidoRepository;
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
        jdbcTemplate.update("DELETE FROM detalle_recepcion_pedido");
        jdbcTemplate.update("DELETE FROM recepciones_pedido");
        jdbcTemplate.update("DELETE FROM detalle_pedido");
        jdbcTemplate.update("DELETE FROM pedidos");
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
    void ventaYMovimientoManualConcurrentesNoPuedenDescontarElMismoStock() throws Exception {
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch listos = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var venta = executor.submit(() -> venderConcurrentemente(inicio, listos));
            var movimiento = executor.submit(() -> salidaManualConcurrente(inicio, listos));
            assertTrue(listos.await(5, TimeUnit.SECONDS));
            inicio.countDown();

            int exitosas = (venta.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (movimiento.get(15, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, exitosas);
            assertEquals(0, productoRepository.findById(producto.getId()).orElseThrow().getStockActual());
            assertEquals(1, movimientoRepository.count());
            assertEquals(venta.get() ? 1 : 0, notaVentaRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recepcionesConcurrentesDePedidosDistintosCompartenBloqueoDeProducto() throws Exception {
        Pedido primerPedido = crearPedidoConDetalle("PENDIENTE");
        Pedido segundoPedido = crearPedidoConDetalle("PENDIENTE");
        DetallePedido primerDetalle = detallePedidoRepository.findByPedidoIdOrderById(primerPedido.getId()).get(0);
        DetallePedido segundoDetalle = detallePedidoRepository.findByPedidoIdOrderById(segundoPedido.getId()).get(0);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch listos = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var primera = executor.submit(() ->
                    recibirConcurrentemente(primerPedido.getId(), primerDetalle.getId(), inicio, listos));
            var segunda = executor.submit(() ->
                    recibirConcurrentemente(segundoPedido.getId(), segundoDetalle.getId(), inicio, listos));
            assertTrue(listos.await(5, TimeUnit.SECONDS));
            inicio.countDown();

            assertEquals("RECIBIDO", primera.get(15, TimeUnit.SECONDS));
            assertEquals("RECIBIDO", segunda.get(15, TimeUnit.SECONDS));
            assertEquals(3, productoRepository.findById(producto.getId()).orElseThrow().getStockActual());
            assertEquals(2, recepcionPedidoRepository.count());
            assertEquals(2, movimientoRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recepcionReviertePedidoStockYRegistrosSiFallaElMovimiento() {
        Pedido pedido = crearPedidoConDetalle("PENDIENTE");
        DetallePedido detalle = detallePedidoRepository.findByPedidoIdOrderById(pedido.getId()).get(0);
        doThrow(new IllegalStateException("Fallo de movimiento"))
                .when(movimientoService)
                .registrar(any(MovimientoRequest.class), eq(usuario.getEmail()));
        RegistrarRecepcionItemRequest linea = new RegistrarRecepcionItemRequest();
        linea.setDetallePedidoId(detalle.getId());
        linea.setCantidad(1);
        RegistrarRecepcionRequest request = new RegistrarRecepcionRequest();
        request.setClaveIdempotencia(java.util.UUID.randomUUID().toString());
        request.setProductos(List.of(linea));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> pedidoService.recibir(pedido.getId(), request, usuario.getEmail()));

        assertEquals(1, productoRepository.findById(producto.getId()).orElseThrow().getStockActual());
        assertEquals("PENDIENTE", pedidoRepository.findById(pedido.getId()).orElseThrow().getEstado());
        assertEquals(0, recepcionPedidoRepository.count());
        assertEquals(0, movimientoRepository.count());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detalle_recepcion_pedido", Integer.class));
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

    private boolean salidaManualConcurrente(CountDownLatch inicio, CountDownLatch listos) throws Exception {
        listos.countDown();
        inicio.await();
        MovimientoRequest request = new MovimientoRequest();
        request.setProductoId(producto.getId());
        request.setTipo(TipoMovimiento.SALIDA);
        request.setCantidad(1);
        request.setObservacion("Salida manual de prueba");
        try {
            movimientoService.registrar(request, usuario.getEmail());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String recibirConcurrentemente(
            Long pedidoId, Long detallePedidoId, CountDownLatch inicio, CountDownLatch listos) throws Exception {
        listos.countDown();
        inicio.await();
        RegistrarRecepcionItemRequest linea = new RegistrarRecepcionItemRequest();
        linea.setDetallePedidoId(detallePedidoId);
        linea.setCantidad(1);
        RegistrarRecepcionRequest request = new RegistrarRecepcionRequest();
        request.setClaveIdempotencia(java.util.UUID.randomUUID().toString());
        request.setProductos(List.of(linea));
        return pedidoService.recibir(pedidoId, request, usuario.getEmail()).estado();
    }

    private Pedido crearPedidoConDetalle(String estado) {
        Pedido pedido = new Pedido();
        pedido.setProveedor(producto.getProveedor());
        pedido.setUsuario(usuario);
        pedido.setEstado(estado);
        pedido = pedidoRepository.saveAndFlush(pedido);

        DetallePedido detalle = new DetallePedido();
        detalle.setPedido(pedido);
        detalle.setProducto(producto);
        detalle.setCantidad(1);
        detalle.setPrecio(java.math.BigDecimal.valueOf(10));
        detallePedidoRepository.saveAndFlush(detalle);
        return pedido;
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
