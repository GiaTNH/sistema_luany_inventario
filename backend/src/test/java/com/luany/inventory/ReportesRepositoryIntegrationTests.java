package com.luany.inventory;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.*;
import com.luany.inventory.service.ReportesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:luanyreports;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "brevo.api-key=",
        "mail.from.email=",
        "mail.from.name=LUANY"
})
class ReportesRepositoryIntegrationTests {

    @Autowired private ReportesService reportesService;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private NotaVentaRepository notaVentaRepository;
    @Autowired private DetalleVentaRepository detalleVentaRepository;
    @Autowired private MovimientoRepository movimientoRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private DetallePedidoRepository detallePedidoRepository;
    @Autowired private RecepcionPedidoRepository recepcionPedidoRepository;
    @Autowired private DetalleRecepcionPedidoRepository detalleRecepcionPedidoRepository;

    @Test
    void ejecutaAgregacionesDeVentasMovimientosInventarioYPedidosSobreLaBaseDePrueba() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = hoy.atTime(12, 0);

        Categoria categoria = new Categoria();
        categoria.setNombre("Reportes");
        categoria = categoriaRepository.saveAndFlush(categoria);

        Proveedor proveedor = new Proveedor();
        proveedor.setNombre("Proveedor reporte");
        proveedor.setEstado("ACTIVO");
        proveedor = proveedorRepository.saveAndFlush(proveedor);

        Usuario usuario = new Usuario();
        usuario.setNombre("Usuario Reporte");
        usuario.setEmail("reportes@luany.test");
        usuario.setNombreUsuario("usuario-reporte");
        usuario.setPassword("hash");
        usuario.setRol("ADMIN");
        usuario.setEstado("ACTIVO");
        usuario = usuarioRepository.saveAndFlush(usuario);

        Cliente cliente = new Cliente();
        cliente.setNombre("Cliente Reporte");
        cliente.setTipoDocumento("DNI");
        cliente.setNumeroDocumento("99998888");
        cliente = clienteRepository.saveAndFlush(cliente);

        Producto producto = new Producto();
        producto.setNombre("Producto Reporte");
        producto.setCategoria(categoria);
        producto.setProveedor(proveedor);
        producto.setStockActual(5);
        producto.setStockMinimo(1);
        producto.setStockMaximo(20);
        producto.setPrecio(12.5);
        producto = productoRepository.saveAndFlush(producto);

        NotaVenta nota = new NotaVenta();
        nota.setNumeroNota("NV-REPORTES-TEST");
        nota.setFechaEmision(ahora);
        nota.setTotal(new BigDecimal("50.00"));
        nota.setEstado("EMITIDA");
        nota.setCliente(cliente);
        nota.setUsuario(usuario);
        nota = notaVentaRepository.saveAndFlush(nota);

        DetalleVenta detalleVenta = new DetalleVenta();
        detalleVenta.setNotaVenta(nota);
        detalleVenta.setProducto(producto);
        detalleVenta.setCantidad(4);
        detalleVenta.setPrecioUnitario(new BigDecimal("12.50"));
        detalleVenta.setSubtotal(new BigDecimal("50.00"));
        detalleVentaRepository.saveAndFlush(detalleVenta);

        Movimiento movimientoVenta = crearMovimiento(producto, TipoMovimiento.VENTA, 4, ahora);
        movimientoRepository.saveAndFlush(movimientoVenta);

        Pedido pedido = new Pedido();
        pedido.setFechaPedido(ahora);
        pedido.setEstado("PARCIAL");
        pedido.setProveedor(proveedor);
        pedido.setUsuario(usuario);
        pedido = pedidoRepository.saveAndFlush(pedido);

        DetallePedido detallePedido = new DetallePedido();
        detallePedido.setPedido(pedido);
        detallePedido.setProducto(producto);
        detallePedido.setCantidad(10);
        detallePedido.setPrecio(new BigDecimal("8.00"));
        detallePedido = detallePedidoRepository.saveAndFlush(detallePedido);

        RecepcionPedido recepcion = new RecepcionPedido();
        recepcion.setPedido(pedido);
        recepcion.setUsuario(usuario);
        recepcion.setClaveIdempotencia(UUID.randomUUID().toString());
        recepcion.setFecha(ahora.plusHours(1));
        recepcion = recepcionPedidoRepository.saveAndFlush(recepcion);

        Movimiento movimientoEntrada = crearMovimiento(producto, TipoMovimiento.ENTRADA, 4, ahora.plusHours(1));
        movimientoEntrada = movimientoRepository.saveAndFlush(movimientoEntrada);
        DetalleRecepcionPedido detalleRecepcion = new DetalleRecepcionPedido();
        detalleRecepcion.setRecepcion(recepcion);
        detalleRecepcion.setDetallePedido(detallePedido);
        detalleRecepcion.setCantidad(4);
        detalleRecepcion.setMovimiento(movimientoEntrada);
        detalleRecepcionPedidoRepository.saveAndFlush(detalleRecepcion);

        var ventas = reportesService.ventas(hoy, hoy);
        var ranking = reportesService.productosVendidos(hoy, hoy, 5);
        var movimientos = reportesService.movimientos(hoy, hoy, producto.getId(), TipoMovimiento.VENTA, 0, 20);
        var inventario = reportesService.inventario("NORMAL");
        var pedidos = reportesService.pedidosRecepciones(hoy, hoy);

        assertEquals(new BigDecimal("50.00"), ventas.importeTotal());
        assertEquals(1L, ventas.cantidadNotas());
        assertEquals(4L, ranking.productos().get(0).unidadesVendidas());
        assertEquals(1L, movimientos.totalElementos());
        assertEquals("NORMAL", inventario.productos().get(0).estado());
        assertEquals("PARCIAL", pedidos.resumenPorEstado().get(0).estado());
        assertEquals(4L, pedidos.pedidos().get(0).unidadesRecibidas());
        assertEquals(6L, pedidos.pedidos().get(0).unidadesPendientes());
        assertEquals(4L, pedidos.recepcionesPorDia().get(0).unidadesRecibidas());
    }

    private Movimiento crearMovimiento(Producto producto, TipoMovimiento tipo, int cantidad, LocalDateTime fecha) {
        Movimiento movimiento = new Movimiento();
        movimiento.setProducto(producto);
        movimiento.setTipo(tipo);
        movimiento.setCantidad(cantidad);
        movimiento.setFecha(fecha);
        movimiento.setObservacion("Prueba de reportes");
        return movimiento;
    }
}
