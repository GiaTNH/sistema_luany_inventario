package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.model.ReportesResponse.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenteServiceTests {

    @Mock private ProductoService productoService;
    @Mock private ProveedorService proveedorService;
    @Mock private MovimientoService movimientoService;
    @Mock private PedidoService pedidoService;
    @Mock private ClienteService clienteService;
    @Mock private NotaVentaService notaVentaService;
    @Mock private ReportesService reportesService;
    @Mock private UsuarioGestionService usuarioGestionService;
    @Mock private GeminiClient geminiClient;
    @Spy private Clock clock = Clock.system(ZoneId.of("America/Lima"));
    @InjectMocks private AsistenteService asistenteService;

    @Test
    void combinaProductosAlertasYRankingDelMesActual() {
        Producto producto = producto(4L, "Vestido Luna", 2, 5, 15);
        when(productoService.productosConStockBajoParaAsistente(50)).thenReturn(List.of(producto));
        when(productoService.estadoStock(producto)).thenReturn("BAJO");
        when(reportesService.ventas(any(), any())).thenAnswer(invocacion -> {
            LocalDate desde = invocacion.getArgument(0);
            LocalDate hasta = invocacion.getArgument(1);
            return new VentasPeriodo(desde, hasta, new BigDecimal("125.00"), 2, List.of());
        });
        when(reportesService.productosVendidos(any(), any(), eq(20))).thenAnswer(invocacion -> {
            LocalDate desde = invocacion.getArgument(0);
            LocalDate hasta = invocacion.getArgument(1);
            return new ProductosVendidos(desde, hasta, 20,
                    List.of(new ProductoVendido(4L, "Vestido Luna", 3)));
        });
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        RespuestaAsistente respuesta = asistenteService.consultar(
                "¿Qué productos tienen poco stock y cuáles fueron los más vendidos este mes?", false);
        String prompt = promptEnviado();

        assertTrue(respuesta.tipoConsulta().contains("ALERTAS"));
        assertTrue(respuesta.tipoConsulta().contains("REPORTES"));
        assertEquals(1, respuesta.productosEnContexto());
        assertTrue(prompt.contains("Vestido Luna"));
        assertTrue(prompt.contains("unidades sugeridas: 13"));
        assertTrue(prompt.contains("125.00"));
        assertTrue(prompt.contains("3 unidades"));
        verify(reportesService).ventas(LocalDate.now().withDayOfMonth(1), LocalDate.now());
    }

    @Test
    void consultaMovimientosFiltradosPorTipoYFechas() {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 7);
        MovimientoItem movimiento = new MovimientoItem(
                8L, LocalDateTime.of(2026, 11, 3, 10, 0), "ENTRADA", 5,
                null, 2L, "Blusa", 1, 6);
        when(reportesService.movimientos(desde, hasta, null, TipoMovimiento.ENTRADA, 0, 50))
                .thenReturn(new MovimientosPagina(desde, hasta, 0, 50, 1, 1, List.of(movimiento)));
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        asistenteService.consultar("Muéstrame los movimientos de entrada del 01/11/2026 al 07/11/2026", false);

        String prompt = promptEnviado();
        assertTrue(prompt.contains("ENTRADA | Blusa | cantidad: 5"));
        verify(reportesService).movimientos(desde, hasta, null, TipoMovimiento.ENTRADA, 0, 50);
    }

    @Test
    void consultaProveedoresConDatosDeContactoYProductosAsociados() {
        Proveedor proveedor = new Proveedor();
        proveedor.setId(3L);
        proveedor.setNombre("Textiles Norte");
        proveedor.setRuc("20123456789");
        proveedor.setCorreo("ventas@example.test");
        proveedor.setTelefono("999111222");
        proveedor.setEstado("ACTIVO");
        Producto producto = producto(1L, "Blusa", 4, 2, 10);
        producto.setProveedor(proveedor);
        when(proveedorService.listarParaAsistente(50)).thenReturn(List.of(proveedor));
        when(productoService.listarParaAsistente(100)).thenReturn(List.of(producto));
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        asistenteService.consultar("Dame los proveedores, su RUC, teléfono y productos", false);

        String prompt = promptEnviado();
        assertTrue(prompt.contains("20123456789"));
        assertTrue(prompt.contains("ventas@example.test"));
        assertTrue(prompt.contains("Blusa"));
    }

    @Test
    void consultaPedidosYRecepcionesEnElPeriodoSolicitado() {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 7);
        PedidoDetalleResponse pedido = new PedidoDetalleResponse(
                17L,
                LocalDateTime.of(2026, 11, 2, 9, 0),
                LocalDate.of(2026, 11, 10),
                "PARCIAL",
                3L,
                "Textiles Norte",
                "Operador",
                new BigDecimal("45.00"),
                List.of(new PedidoDetalleResponse.Item(
                        1L, 8L, "Blusa", 5, 2, 3, new BigDecimal("9.00"), new BigDecimal("45.00"))),
                List.of(new PedidoDetalleResponse.Recepcion(
                        22L,
                        LocalDateTime.of(2026, 11, 4, 10, 0),
                        "Operador",
                        List.of(new PedidoDetalleResponse.RecepcionItem(1L, "Blusa", 2, 30L)))));
        when(pedidoService.listarParaAsistente(desde, hasta, 20)).thenReturn(List.of(pedido));
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        asistenteService.consultar("¿Qué pedidos recibimos del 01/11/2026 al 07/11/2026?", false);

        String prompt = promptEnviado();
        assertTrue(prompt.contains("Pedido 17"));
        assertTrue(prompt.contains("Blusa (solicitadas: 5, recibidas: 2, pendientes: 3"));
        assertTrue(prompt.contains("costo unitario: 9.00"));
        verify(pedidoService).listarParaAsistente(desde, hasta, 20);
    }

    @Test
    void datosDeClientesOmitenIdentificadoresPrivadosYUsanRangoSolicitado() {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 7);
        Cliente cliente = new Cliente();
        cliente.setNombre("Ana Pérez");
        cliente.setNumeroDocumento("DOCUMENTO-PRIVADO");
        when(clienteService.listarParaAsistente("Ana", 30)).thenReturn(List.of(cliente));
        when(notaVentaService.listarParaAsistente("Ana", desde, hasta, 30)).thenReturn(List.of(
                new NotaVentaResumenResponse(
                        9L, "NV-009", LocalDateTime.of(2026, 11, 3, 11, 0),
                        "Ana Pérez", "Operador", new BigDecimal("65.00"), "EMITIDA", 1)));
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        asistenteService.consultar("¿Qué ventas hizo la cliente Ana del 01/11/2026 al 07/11/2026?", false);

        String prompt = promptEnviado();
        assertTrue(prompt.contains("Ana Pérez"));
        assertTrue(prompt.contains("65.00"));
        assertFalse(prompt.contains("DOCUMENTO-PRIVADO"));
        verify(clienteService).listarParaAsistente("Ana", 30);
        verify(notaVentaService).listarParaAsistente("Ana", desde, hasta, 30);
    }

    @Test
    void losUsuariosSeIncluyenSoloParaAdministradoresYSinDatosDeAcceso() {
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");
        when(usuarioGestionService.listarParaAsistente(50)).thenReturn(List.of(
                new UsuarioGestionResponse(1L, "Luisa", "Rojas", "privado@example.test",
                        "luisa.rojas", "EMPLEADO", "ACTIVO", false)));

        asistenteService.consultar("Lista los usuarios y sus roles", false);
        String restringido = promptEnviado();
        assertTrue(restringido.contains("solo para el rol administrador"));
        verify(usuarioGestionService, never()).listarParaAsistente(anyInt());

        asistenteService.consultar("Lista los usuarios y sus roles", true);
        String administrativo = promptEnviado();
        assertTrue(administrativo.contains("EMPLEADO"));
        assertFalse(administrativo.contains("privado@example.test"));
        assertFalse(administrativo.contains("luisa.rojas"));
    }

    @Test
    void mantieneConsultasDeAlertasYReposicionConDatosLimitados() {
        Producto producto = producto(7L, "Falda", 0, 4, 12);
        when(productoService.productosConStockBajoParaAsistente(50)).thenReturn(List.of(producto));
        when(productoService.estadoStock(producto)).thenReturn("AGOTADO");
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        asistenteService.consultar("¿Qué productos debo reponer?", false);

        assertTrue(promptEnviado().contains("unidades sugeridas: 12"));
        verify(productoService).productosConStockBajoParaAsistente(50);
    }

    @Test
    void conservaRotacionConVentasBajasYExcesoDeStock() {
        Producto masVendido = producto(1L, "Blusa", 8, 2, 5);
        Producto bajaRotacion = producto(2L, "Falda", 1, 2, 8);
        when(productoService.listarParaAsistente(100)).thenReturn(List.of(masVendido, bajaRotacion));
        when(movimientoService.ventasPorProducto(any(), any())).thenReturn(Map.of(1L, 12L, 2L, 0L));
        when(productoService.estadoStock(masVendido)).thenReturn("EXCESO");
        when(productoService.estadoStock(bajaRotacion)).thenReturn("BAJO");
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        asistenteService.consultar("¿Cómo está la rotación de productos?", false);

        String prompt = promptEnviado();
        assertTrue(prompt.contains("ROTACIÓN POR VENTAS"));
        assertTrue(prompt.contains("Blusa | ventas: 12"));
        assertTrue(prompt.contains("Falda | ventas: 0"));
        assertTrue(prompt.contains("Con exceso de stock y ventas bajas"));
        verify(reportesService, never()).ventas(any(), any());
    }

    @Test
    void identificaProductosSinVentasComoConsultaDeRotacion() {
        Producto producto = producto(5L, "Sombrero", 3, 1, 8);
        when(productoService.listarParaAsistente(100)).thenReturn(List.of(producto));
        when(movimientoService.ventasPorProducto(any(), any())).thenReturn(Map.of());
        when(productoService.estadoStock(producto)).thenReturn("NORMAL");
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        RespuestaAsistente respuesta = asistenteService.consultar(
                "¿Qué productos no se han vendido este mes?", false);

        assertTrue(respuesta.tipoConsulta().contains("ROTACION"));
        assertFalse(respuesta.tipoConsulta().contains("REPORTES"));
        assertTrue(promptEnviado().contains("Sombrero | ventas: 0"));
    }

    @Test
    void comparaIngresosConElPeriodoAnteriorDelMismoLargo() {
        when(reportesService.ventas(any(), any())).thenAnswer(invocacion -> {
            LocalDate desde = invocacion.getArgument(0);
            LocalDate hasta = invocacion.getArgument(1);
            return new VentasPeriodo(desde, hasta, new BigDecimal("10.00"), 1, List.of());
        });
        when(reportesService.productosVendidos(any(), any(), eq(20))).thenAnswer(invocacion -> {
            LocalDate desde = invocacion.getArgument(0);
            LocalDate hasta = invocacion.getArgument(1);
            return new ProductosVendidos(desde, hasta, 20, List.of());
        });
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        asistenteService.consultar("Compara las ventas de este mes con el mes pasado", false);

        assertTrue(promptEnviado().contains("COMPARACIÓN CON EL PERIODO ANTERIOR"));
        verify(reportesService, org.mockito.Mockito.times(2)).ventas(any(), any());
        verify(reportesService, org.mockito.Mockito.times(2))
                .productosVendidos(any(), any(), anyInt());
    }

    private String promptEnviado() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient, org.mockito.Mockito.atLeastOnce()).generar(any(), captor.capture());
        return captor.getValue();
    }

    private Producto producto(Long id, String nombre, int actual, int minimo, int maximo) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setNombre(nombre);
        producto.setCategoria(new Categoria(1L, "Ropa"));
        producto.setProveedor(new Proveedor(2L, "Proveedor", "999", "12345678901",
                "proveedor@example.test", null, "ACTIVO"));
        producto.setStockActual(actual);
        producto.setStockMinimo(minimo);
        producto.setStockMaximo(maximo);
        producto.setPrecio(12.5);
        return producto;
    }
}
