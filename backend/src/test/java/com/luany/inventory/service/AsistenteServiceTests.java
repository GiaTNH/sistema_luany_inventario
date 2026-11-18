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
        assertTrue(respuesta.tablas().size() > 1);
        assertEquals("Encontré información en " + respuesta.tablas().size()
                + " tablas. Te muestro los resultados.", respuesta.respuesta());
        assertFalse(respuesta.respuesta().contains("Vestido Luna"));
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
        when(productoService.listar()).thenReturn(List.of());
        when(reportesService.movimientos(desde, hasta, null, TipoMovimiento.ENTRADA, 0, 50))
                .thenReturn(new MovimientosPagina(desde, hasta, 0, 50, 1, 1, List.of(movimiento)));
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        RespuestaAsistente respuesta = asistenteService.consultar(
                "Últimos movimientos de entrada del 01/11/2026 al 07/11/2026", false);

        String prompt = promptEnviado();
        assertTrue(prompt.contains("ENTRADA | Blusa | cantidad: 5"));
        verify(reportesService).movimientos(desde, hasta, null, TipoMovimiento.ENTRADA, 0, 50);
        assertEquals(1, respuesta.tablas().size());
        assertEquals("MOVIMIENTOS", respuesta.tablas().get(0).tipo());
        assertEquals(List.of("Fecha y hora", "Tipo", "Producto", "Cantidad", "Stock anterior", "Stock nuevo"),
                respuesta.tablas().get(0).columnas());
        assertEquals(LocalDateTime.of(2026, 11, 3, 10, 0), respuesta.tablas().get(0).filas().get(0).get(0));
        assertEquals(1, respuesta.tablas().get(0).total());
    }

    @Test
    void filtraMovimientosPorNombreDeProductoIgnorandoMayusculasYTildes() {
        Producto ropon = producto(12L, "Ropón", 3, 1, 8);
        MovimientoItem movimiento = movimiento(1L, 12L, "Ropón", "ENTRADA");

        RespuestaAsistente respuesta = consultarMovimientos(
                "¿Qué movimientos ha tenido ropon?", List.of(ropon), 12L, null, List.of(movimiento));

        assertEquals(1, respuesta.tablas().get(0).total());
        assertEquals("Movimientos de Ropón", respuesta.tablas().get(0).titulo());
        assertEquals("Ropón", respuesta.tablas().get(0).filas().get(0).get(2));
    }

    @Test
    void filtraEntradasDelProductoSolicitado() {
        Producto ropon = producto(12L, "Ropon", 3, 1, 8);

        consultarMovimientos(
                "Muéstrame las entradas de ropon", List.of(ropon), 12L, TipoMovimiento.ENTRADA,
                List.of(movimiento(2L, 12L, "Ropon", "ENTRADA")));
    }

    @Test
    void filtraSalidasDelProductoSolicitado() {
        Producto ropon = producto(12L, "Ropon", 3, 1, 8);

        consultarMovimientos(
                "Muéstrame las salidas de ropon", List.of(ropon), 12L, TipoMovimiento.SALIDA,
                List.of(movimiento(3L, 12L, "Ropon", "SALIDA")));
    }

    @Test
    void identificaProductoConTildeEnLaConsulta() {
        Producto biberon = producto(21L, "Biberón", 3, 1, 8);

        consultarMovimientos(
                "¿Qué movimientos ha tenido el biberón?", List.of(biberon), 21L, null,
                List.of(movimiento(4L, 21L, "Biberón", "SALIDA")));
    }

    @Test
    void sugiereBiberonAnteUnErrorDeEscrituraSinConsultarMovimientos() {
        Producto biberon = producto(21L, "Biberón", 3, 1, 8);
        when(productoService.listar()).thenReturn(List.of(biberon));

        RespuestaAsistente respuesta = asistenteService.consultar(
                "¿Qué movimientos ha tenido bibern?", false);

        assertEquals("¿Te refieres a Biberón?", respuesta.respuesta());
        assertEquals(List.of(new RespuestaAsistente.OpcionProducto(21L, "Biberón")),
                respuesta.opcionesProducto());
        assertTrue(respuesta.tablas().isEmpty());
        verify(reportesService, never()).movimientos(
                any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void reconoceBiberonAlNormalizarTildesAntesDeFiltrarPorId() {
        Producto biberon = producto(21L, "Biberón", 3, 1, 8);

        consultarMovimientos(
                "¿Qué movimientos ha tenido bibéron?", List.of(biberon), 21L, null,
                List.of(movimiento(4L, 21L, "Biberón", "SALIDA")));
    }

    @Test
    void sugiereRoponAnteUnaOmisiónDeLetras() {
        Producto ropon = producto(12L, "Ropón", 3, 1, 8);
        when(productoService.listar()).thenReturn(List.of(ropon));

        RespuestaAsistente respuesta = asistenteService.consultar(
                "¿Qué movimientos ha tenido ropn?", false);

        assertEquals("¿Te refieres a Ropón?", respuesta.respuesta());
        assertEquals(12L, respuesta.opcionesProducto().get(0).id());
        verify(reportesService, never()).movimientos(
                any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void confirmaSugerenciaYConsultaConElIdRealDelProducto() {
        Producto biberon = producto(21L, "Biberón", 3, 1, 8);
        LocalDate hoy = LocalDate.now(clock);
        when(productoService.listar()).thenReturn(List.of(biberon));
        when(reportesService.movimientos(any(), any(), eq(21L), eq(null), eq(0), eq(50)))
                .thenReturn(new MovimientosPagina(hoy.minusDays(29), hoy, 0, 50, 0, 0, List.of()));
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        RespuestaAsistente respuesta = asistenteService.consultar(
                "¿Qué movimientos ha tenido bibern?", false, 21L, false);

        verify(reportesService).movimientos(any(), any(), eq(21L), eq(null), eq(0), eq(50));
        assertEquals("No encontré registros en la tabla de movimientos de biberón.",
                respuesta.respuesta());
        assertTrue(respuesta.opcionesProducto().isEmpty());
    }

    @Test
    void cancelarSugerenciaNoConsultaMovimientos() {
        RespuestaAsistente respuesta = asistenteService.consultar(
                "¿Qué movimientos ha tenido bibern?", false, null, true);

        assertEquals("Entendido. No consultaré movimientos de ningún producto.", respuesta.respuesta());
        verify(reportesService, never()).movimientos(
                any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void muestraOpcionesCuandoDosProductosTienenLaMismaSimilitud() {
        Producto ropon = producto(12L, "Ropon", 3, 1, 8);
        Producto ropin = producto(13L, "Ropin", 3, 1, 8);
        when(productoService.listar()).thenReturn(List.of(ropon, ropin));

        RespuestaAsistente respuesta = asistenteService.consultar(
                "¿Qué movimientos ha tenido ropn?", false);

        assertEquals(2, respuesta.opcionesProducto().size());
        assertTrue(respuesta.respuesta().contains("Selecciona"));
        verify(reportesService, never()).movimientos(
                any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void losMovimientosDeTodosLosProductosNoAplicanFiltroDeProducto() {
        List<MovimientoItem> movimientos = List.of(
                movimiento(5L, 12L, "Ropon", "ENTRADA"),
                movimiento(6L, 21L, "Biberón", "SALIDA"));
        LocalDate hoy = LocalDate.now(clock);
        when(reportesService.movimientos(any(LocalDate.class), any(LocalDate.class),
                eq(null), eq(null), eq(0), eq(50)))
                .thenReturn(new MovimientosPagina(hoy.minusDays(29), hoy, 0, 50, 2, 1, movimientos));
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        RespuestaAsistente respuesta = asistenteService.consultar(
                "Muéstrame los movimientos de todos los productos", false);

        verify(reportesService).movimientos(any(LocalDate.class), any(LocalDate.class),
                eq(null), eq(null), eq(0), eq(50));
        verify(productoService, never()).listar();
        assertEquals(2, respuesta.tablas().get(0).total());
        assertEquals(2, respuesta.tablas().get(0).filas().size());
    }

    @Test
    void noMezclaProductosDeNombreSimilarYEvitaCoincidenciasParciales() {
        Producto ropon = producto(12L, "Ropon", 3, 1, 8);
        Producto roponAzul = producto(13L, "Ropon Azul", 3, 1, 8);
        Producto roponera = producto(14L, "Roponera", 3, 1, 8);

        RespuestaAsistente respuesta = consultarMovimientos(
                "Muéstrame los movimientos de ropon azul",
                List.of(ropon, roponAzul, roponera), 13L, null,
                List.of(movimiento(7L, 13L, "Ropon Azul", "ENTRADA")));

        assertEquals("Ropon Azul", respuesta.tablas().get(0).filas().get(0).get(2));
    }

    @Test
    void productoNoEncontradoNoDevuelveMovimientosGlobales() {
        when(productoService.listar()).thenReturn(List.of(producto(12L, "Ropon", 3, 1, 8)));
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        RespuestaAsistente respuesta = asistenteService.consultar(
                "¿Qué movimientos ha tenido sombrero?", false);

        verify(reportesService, never()).movimientos(
                any(), any(), any(), any(), anyInt(), anyInt());
        assertEquals(0, respuesta.tablas().get(0).total());
        assertTrue(respuesta.respuesta().contains("No encontré un producto"));
        assertTrue(respuesta.tablas().get(0).filas().isEmpty());
    }

    @Test
    void nombresRegistradosAmbiguosMuestranOpcionesSinMezclarResultados() {
        Producto roponConTilde = producto(12L, "Ropón", 3, 1, 8);
        Producto roponSinTilde = producto(13L, "Ropon", 3, 1, 8);
        when(productoService.listar()).thenReturn(List.of(roponConTilde, roponSinTilde));

        RespuestaAsistente respuesta = asistenteService.consultar(
                "¿Qué movimientos ha tenido ropon?", false);

        verify(reportesService, never()).movimientos(
                any(), any(), any(), any(), anyInt(), anyInt());
        assertTrue(respuesta.respuesta().contains("varios productos"));
        assertEquals(2, respuesta.opcionesProducto().size());
        assertTrue(respuesta.tablas().isEmpty());
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

        RespuestaAsistente respuesta = asistenteService.consultar("¿Qué productos debo reponer?", false);

        assertTrue(promptEnviado().contains("unidades sugeridas: 12"));
        verify(productoService).productosConStockBajoParaAsistente(50);
        assertEquals("REPOSICION", respuesta.tablas().get(0).tipo());
        assertEquals("Falda", respuesta.tablas().get(0).filas().get(0).get(0));
        assertEquals(12, respuesta.tablas().get(0).filas().get(0).get(4));
    }

    @Test
    void filtraTablasParaStockBajoYSinStock() {
        Producto agotado = producto(7L, "Falda agotada", 0, 4, 12);
        Producto bajo = producto(8L, "Blusa baja", 2, 5, 15);
        when(productoService.productosConStockBajoParaAsistente(50))
                .thenReturn(List.of(agotado, bajo));
        when(productoService.estadoStock(agotado)).thenReturn("AGOTADO");
        when(productoService.estadoStock(bajo)).thenReturn("BAJO");
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        RespuestaAsistente stockBajo = asistenteService.consultar(
                "¿Qué productos tienen stock bajo?", false);
        RespuestaAsistente sinStock = asistenteService.consultar(
                "¿Qué productos están sin stock?", false);

        assertEquals("STOCK_BAJO", stockBajo.tablas().get(0).tipo());
        assertEquals(List.of("Blusa baja"), stockBajo.tablas().get(0).filas().stream()
                .map(fila -> fila.get(0)).toList());
        assertEquals("PRODUCTOS_SIN_STOCK", sinStock.tablas().get(0).tipo());
        assertEquals(List.of("Falda agotada"), sinStock.tablas().get(0).filas().stream()
                .map(fila -> fila.get(0)).toList());
    }

    @Test
    void respuestaConTablaNoRepiteLosRegistrosAunqueGeminiLosEnumere() {
        Producto producto = producto(8L, "Blusa Luna", 2, 5, 15);
        when(productoService.productosConStockBajoParaAsistente(50)).thenReturn(List.of(producto));
        when(productoService.estadoStock(producto)).thenReturn("BAJO");
        when(geminiClient.generar(any(), any()))
                .thenReturn("Encontré Blusa Luna con stock actual 2 y mínimo 5.");

        RespuestaAsistente respuesta = asistenteService.consultar(
                "¿Qué productos tienen stock bajo?", false);

        assertEquals(
                "Encontré 1 registro en la tabla de productos con stock bajo. Te muestro el resultado.",
                respuesta.respuesta());
        assertFalse(respuesta.respuesta().contains("Blusa Luna"));
        assertEquals("Blusa Luna", respuesta.tablas().get(0).filas().get(0).get(0));
    }

    @Test
    void saludoNoGeneraUnaTablaDeInventario() {
        when(productoService.listarParaAsistente(100)).thenReturn(List.of());
        when(geminiClient.generar(any(), any())).thenReturn("Puedo ayudarte a consultar el inventario.");

        RespuestaAsistente respuesta = asistenteService.consultar("Hola, ¿qué puedes hacer?", false);

        assertEquals("Puedo ayudarte a consultar el inventario.", respuesta.respuesta());
        assertTrue(respuesta.tablas().isEmpty());
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
        assertEquals("ROTACION", respuesta.tablas().get(0).tipo());
        assertEquals("Sombrero", respuesta.tablas().get(0).filas().get(0).get(0));
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

    private RespuestaAsistente consultarMovimientos(
            String pregunta, List<Producto> productos, Long productoId, TipoMovimiento tipo,
            List<MovimientoItem> movimientos) {
        when(productoService.listar()).thenReturn(productos);
        LocalDate hoy = LocalDate.now(clock);
        when(reportesService.movimientos(any(LocalDate.class), any(LocalDate.class),
                eq(productoId), eq(tipo), eq(0), eq(50)))
                .thenReturn(new MovimientosPagina(hoy.minusDays(29), hoy, 0, 50,
                        movimientos.size(), movimientos.isEmpty() ? 0 : 1, movimientos));
        when(geminiClient.generar(any(), any())).thenReturn("Respuesta");

        RespuestaAsistente respuesta = asistenteService.consultar(pregunta, false);

        verify(reportesService).movimientos(any(LocalDate.class), any(LocalDate.class),
                eq(productoId), eq(tipo), eq(0), eq(50));
        assertEquals(movimientos.size(), respuesta.tablas().get(0).total());
        assertEquals(movimientos.size(), respuesta.tablas().get(0).filas().size());
        return respuesta;
    }

    private MovimientoItem movimiento(Long id, Long productoId, String nombre, String tipo) {
        return new MovimientoItem(id, LocalDateTime.of(2026, 11, 3, 10, 0), tipo, 2,
                null, productoId, nombre, 4, 2);
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
