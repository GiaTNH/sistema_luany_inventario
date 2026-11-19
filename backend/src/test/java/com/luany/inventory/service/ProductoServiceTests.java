package com.luany.inventory.service;

import com.luany.inventory.model.AlertaStock;
import com.luany.inventory.model.Categoria;
import com.luany.inventory.model.Producto;
import com.luany.inventory.model.ProductoActualizacionRequest;
import com.luany.inventory.model.ProductoRequest;
import com.luany.inventory.model.Pedido;
import com.luany.inventory.model.Proveedor;
import com.luany.inventory.model.DetallePedido;
import com.luany.inventory.repository.CategoriaRepository;
import com.luany.inventory.repository.DetallePedidoRepository;
import com.luany.inventory.repository.DetalleRecepcionPedidoRepository;
import com.luany.inventory.repository.ProductoRepository;
import com.luany.inventory.repository.ProveedorRepository;
import com.luany.inventory.repository.MovimientoRepository;
import com.luany.inventory.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock
    private MovimientoRepository movimientoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private DetallePedidoRepository detallePedidoRepository;
    @Mock
    private DetalleRecepcionPedidoRepository detalleRecepcionPedidoRepository;

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
        when(productoRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(Producto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(movimientoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

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
        when(productoRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(existente));
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
    void actualizarProductoInactivoRechazaAntesDeBloquearStock() {
        Producto inactivo = producto(10L, "Vestido", "Ropa", 0, 1, 20);
        inactivo.setActivo(false);
        when(productoRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.empty());
        when(productoRepository.findById(10L)).thenReturn(Optional.of(inactivo));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> productoService.actualizar(10L, new ProductoActualizacionRequest()));

        assertEquals("El producto está inactivo y no se puede modificar", error.getMessage());
        verify(productoStockLockService, never()).bloquear(10L);
        verify(productoRepository, never()).save(any());
    }

    @Test
    void crearProductoGeneraCodigoYRegistraEventoDeCreacion() {
        Categoria categoria = new Categoria(2L, "Ropa");
        Proveedor proveedor = new Proveedor();
        proveedor.setId(3L);
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoria));
        when(proveedorRepository.findById(3L)).thenReturn(Optional.of(proveedor));
        when(productoRepository.saveAndFlush(any(Producto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(movimientoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProductoRequest request = new ProductoRequest();
        request.setNombre("Vestido");
        request.setCategoriaId(2L);
        request.setProveedorId(3L);
        request.setStockActual(7);
        request.setStockMinimo(0);
        request.setStockMaximo(20);
        request.setPrecio(12.5);
        request.setCodigo(" LUANY-01 ");

        Producto creado = productoService.crear(request);

        assertTrue(creado.getCodigo().matches("LU-[A-F0-9]{32}"));
        org.mockito.ArgumentCaptor<com.luany.inventory.model.Movimiento> movimiento =
                org.mockito.ArgumentCaptor.forClass(com.luany.inventory.model.Movimiento.class);
        verify(movimientoRepository).save(movimiento.capture());
        assertEquals(com.luany.inventory.model.TipoMovimiento.CREACION, movimiento.getValue().getTipo());
        assertEquals(0, movimiento.getValue().getCantidad());
        assertEquals(7, movimiento.getValue().getStockAnterior());
        assertEquals(7, movimiento.getValue().getStockNuevo());
    }

    @Test
    void codigoAutomaticoReintentaSiElCodigoPropuestoYaExiste() {
        Categoria categoria = new Categoria(2L, "Ropa");
        Proveedor proveedor = new Proveedor();
        proveedor.setId(3L);
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoria));
        when(proveedorRepository.findById(3L)).thenReturn(Optional.of(proveedor));
        when(productoRepository.existsByCodigo(any())).thenReturn(true, false);
        when(productoRepository.saveAndFlush(any(Producto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(movimientoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Producto creado = productoService.crear(requestProducto(0));

        assertTrue(creado.getCodigo().matches("LU-[A-F0-9]{32}"));
        verify(productoRepository, org.mockito.Mockito.times(2)).existsByCodigo(any());
        verify(productoRepository).saveAndFlush(creado);
    }

    @Test
    void codigoAutomaticoFallaTrasAgotarIntentosDeColision() {
        when(productoRepository.existsByCodigo(any())).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> productoService.crear(requestProducto(0)));

        verify(productoRepository, org.mockito.Mockito.times(5)).existsByCodigo(any());
        verify(productoRepository, never()).saveAndFlush(any());
    }

    @Test
    void codigoUnicoDeBaseDeDatosImpideGuardarUnaColisionConcurrente() {
        Categoria categoria = new Categoria(2L, "Ropa");
        Proveedor proveedor = new Proveedor();
        proveedor.setId(3L);
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoria));
        when(proveedorRepository.findById(3L)).thenReturn(Optional.of(proveedor));
        when(productoRepository.saveAndFlush(any(Producto.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate code",
                        new ConstraintViolationException("duplicate code", null, "uk_productos_codigo")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> productoService.crear(requestProducto(0)));

        assertEquals(
                "No se pudo asignar un código único al producto. Intenta nuevamente",
                error.getMessage());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void productoConRecepcionPendienteNoPuedeRetirarse() {
        Producto producto = producto(10L, "Vestido", "Ropa", 0, 1, 20);
        DetallePedido detalle = detallePedido(20L, 5, "PENDIENTE");
        when(productoRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(producto));
        when(productoStockLockService.bloquear(10L)).thenReturn(producto);
        when(detallePedidoRepository.findByProductoId(10L)).thenReturn(List.of(detalle));
        when(detalleRecepcionPedidoRepository.totalRecibido(20L)).thenReturn(2L);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> productoService.eliminar(10L));

        assertEquals(
                "No se puede retirar el producto porque existen recepciones pendientes en uno o más pedidos",
                error.getMessage());
        verify(productoRepository, never()).save(any());
    }

    @Test
    void productoConPedidoParcialmenteRegistradoPeroSinCantidadPendientePuedeRetirarse() {
        Producto producto = producto(10L, "Vestido", "Ropa", 0, 1, 20);
        DetallePedido detalle = detallePedido(20L, 5, "PARCIAL");
        when(productoRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(producto));
        when(productoStockLockService.bloquear(10L)).thenReturn(producto);
        when(detallePedidoRepository.findByProductoId(10L)).thenReturn(List.of(detalle));
        when(detalleRecepcionPedidoRepository.totalRecibido(20L)).thenReturn(5L);
        when(productoRepository.save(producto)).thenReturn(producto);

        productoService.eliminar(10L);

        assertEquals(false, producto.isActivo());
        verify(productoRepository).save(producto);
    }

    @Test
    void pedidosRecibidosOCanceladosNoBloqueanLaBajaAunqueConservenCantidadSinRecibir() {
        Producto producto = producto(10L, "Vestido", "Ropa", 0, 1, 20);
        DetallePedido recibido = detallePedido(20L, 5, "RECIBIDO");
        DetallePedido cancelado = detallePedido(21L, 5, "CANCELADO");
        when(productoRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(producto));
        when(productoStockLockService.bloquear(10L)).thenReturn(producto);
        when(detallePedidoRepository.findByProductoId(10L)).thenReturn(List.of(recibido, cancelado));
        when(productoRepository.save(producto)).thenReturn(producto);

        productoService.eliminar(10L);

        assertEquals(false, producto.isActivo());
        verify(detalleRecepcionPedidoRepository, never()).totalRecibido(any());
        verify(productoRepository).save(producto);
    }

    @Test
    void stockDisponibleImpideBajaAntesDeConsultarPedidos() {
        Producto producto = producto(10L, "Vestido", "Ropa", 1, 1, 20);
        when(productoRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(producto));
        when(productoStockLockService.bloquear(10L)).thenReturn(producto);

        assertThrows(IllegalArgumentException.class, () -> productoService.eliminar(10L));

        verify(detallePedidoRepository, never()).findByProductoId(any());
        verify(productoRepository, never()).save(any());
    }

    @Test
    void eliminarProductoReferenciadoMuestraErrorLegible() {
        Producto producto = producto(10L, "Vestido", "Ropa", 8, 1, 20);
        when(productoRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(producto));
        when(productoStockLockService.bloquear(10L)).thenReturn(producto);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> productoService.eliminar(10L));

        assertEquals("No se puede retirar un producto con stock disponible", error.getMessage());
        verify(productoRepository, never()).delete(any());
    }

    @Test
    void eliminarProductoSinStockLoDesactivaSinBorrarSuHistorial() {
        Producto producto = producto(10L, "Vestido", "Ropa", 0, 1, 20);
        when(productoRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.of(producto));
        when(productoStockLockService.bloquear(10L)).thenReturn(producto);
        when(productoRepository.save(producto)).thenReturn(producto);

        productoService.eliminar(10L);

        assertEquals(false, producto.isActivo());
        verify(productoRepository, never()).delete(any());
        verify(productoRepository).save(producto);
    }

    @Test
    void eliminarProductoInactivoRechazaAntesDeConsultarStockOPedidos() {
        Producto inactivo = producto(10L, "Vestido", "Ropa", 0, 1, 20);
        inactivo.setActivo(false);
        when(productoRepository.findByIdAndActivoTrue(10L)).thenReturn(Optional.empty());
        when(productoRepository.findById(10L)).thenReturn(Optional.of(inactivo));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> productoService.eliminar(10L));

        assertEquals("El producto está inactivo y no se puede modificar", error.getMessage());
        verify(productoStockLockService, never()).bloquear(10L);
        verify(detallePedidoRepository, never()).findByProductoId(any());
        verify(productoRepository, never()).save(any());
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

    private ProductoRequest requestProducto(int stock) {
        ProductoRequest request = new ProductoRequest();
        request.setNombre("Vestido");
        request.setCategoriaId(2L);
        request.setProveedorId(3L);
        request.setStockActual(stock);
        request.setStockMinimo(0);
        request.setStockMaximo(20);
        request.setPrecio(12.5);
        return request;
    }

    private DetallePedido detallePedido(Long id, int cantidad, String estado) {
        Pedido pedido = new Pedido();
        pedido.setEstado(estado);
        DetallePedido detalle = new DetallePedido();
        detalle.setId(id);
        detalle.setPedido(pedido);
        detalle.setCantidad(cantidad);
        return detalle;
    }
}
