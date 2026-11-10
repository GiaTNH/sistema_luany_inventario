package com.luany.inventory.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.luany.inventory.model.ReportesResponse.*;
import com.luany.inventory.model.TipoMovimiento;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ReportesExportService {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReportesService reportesService;

    public ReporteArchivo exportarExcel(
            String reporte,
            LocalDate desde,
            LocalDate hasta,
            Integer limite,
            Long productoId,
            TipoMovimiento tipo,
            String estado) {
        Exportacion exportacion = preparar(reporte, desde, hasta, limite, productoId, tipo, estado);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Reporte");
            sheet.setDisplayGridlines(false);

            CellStyle tituloStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font tituloFont = workbook.createFont();
            tituloFont.setBold(true);
            tituloFont.setFontHeightInPoints((short) 16);
            tituloFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            tituloStyle.setFont(tituloFont);

            CellStyle seccionStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font seccionFont = workbook.createFont();
            seccionFont.setBold(true);
            seccionFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            seccionStyle.setFont(seccionFont);

            CellStyle encabezadoStyle = workbook.createCellStyle();
            encabezadoStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            encabezadoStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font encabezadoFont = workbook.createFont();
            encabezadoFont.setBold(true);
            encabezadoStyle.setFont(encabezadoFont);
            encabezadoStyle.setBorderBottom(BorderStyle.THIN);

            int rowIndex = 0;
            Row brand = sheet.createRow(rowIndex++);
            brand.createCell(0).setCellValue("LUANY");
            brand.getCell(0).setCellStyle(tituloStyle);
            Row title = sheet.createRow(rowIndex++);
            title.createCell(0).setCellValue(exportacion.titulo());
            title.getCell(0).setCellStyle(tituloStyle);
            rowIndex = escribirMetadata(sheet, rowIndex, exportacion);

            for (Seccion seccion : exportacion.secciones()) {
                rowIndex++;
                Row tituloSeccion = sheet.createRow(rowIndex++);
                tituloSeccion.createCell(0).setCellValue(seccion.titulo());
                tituloSeccion.getCell(0).setCellStyle(seccionStyle);

                Row encabezado = sheet.createRow(rowIndex++);
                for (int columna = 0; columna < seccion.encabezados().size(); columna++) {
                    Cell celda = encabezado.createCell(columna);
                    celda.setCellValue(seccion.encabezados().get(columna));
                    celda.setCellStyle(encabezadoStyle);
                }
                for (List<Object> valores : seccion.filas()) {
                    Row fila = sheet.createRow(rowIndex++);
                    escribirFilaExcel(fila, valores);
                }
                if (!seccion.totales().isEmpty()) {
                    Row totales = sheet.createRow(rowIndex++);
                    for (int columna = 0; columna < seccion.totales().size(); columna++) {
                        Cell celda = totales.createCell(columna);
                        escribirCelda(celda, seccion.totales().get(columna));
                        celda.setCellStyle(seccionStyle);
                    }
                }
            }

            int columnas = exportacion.secciones().stream()
                    .mapToInt(seccion -> seccion.encabezados().size()).max().orElse(1);
            for (int columna = 0; columna < columnas; columna++) {
                sheet.autoSizeColumn(columna);
                sheet.setColumnWidth(columna, Math.min(sheet.getColumnWidth(columna) + 600, 15000));
            }
            workbook.write(output);
            return new ReporteArchivo(
                    exportacion.nombreArchivo() + ".xlsx", XLSX_MEDIA_TYPE, output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el archivo Excel.", exception);
        }
    }

    public ReporteArchivo exportarPdf(
            String reporte,
            LocalDate desde,
            LocalDate hasta,
            Integer limite,
            Long productoId,
            TipoMovimiento tipo,
            String estado) {
        Exportacion exportacion = preparar(reporte, desde, hasta, limite, productoId, tipo, estado);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 32, 32, 34, 34);
            PdfWriter.getInstance(document, output);
            document.open();
            com.lowagie.text.Font titulo =
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new java.awt.Color(49, 39, 114));
            com.lowagie.text.Font encabezado =
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, java.awt.Color.WHITE);
            com.lowagie.text.Font cuerpo =
                    FontFactory.getFont(FontFactory.HELVETICA, 8, java.awt.Color.BLACK);
            com.lowagie.text.Font metadata =
                    FontFactory.getFont(FontFactory.HELVETICA, 9, java.awt.Color.DARK_GRAY);

            document.add(new Paragraph("LUANY", titulo));
            document.add(new Paragraph(exportacion.titulo(), titulo));
            document.add(new Paragraph("Generado: " + exportacion.generadoEn().format(FECHA_HORA), metadata));
            document.add(new Paragraph("Filtros: " + exportacion.filtros(), metadata));
            document.add(Chunk.NEWLINE);

            for (Seccion seccion : exportacion.secciones()) {
                document.add(new Paragraph(seccion.titulo(), FontFactory.getFont(
                        FontFactory.HELVETICA_BOLD, 11, new java.awt.Color(49, 39, 114))));
                PdfPTable table = new PdfPTable(seccion.encabezados().size());
                table.setWidthPercentage(100);
                table.setHeaderRows(1);
                for (String columna : seccion.encabezados()) {
                    PdfPCell cell = new PdfPCell(new Phrase(columna, encabezado));
                    cell.setBackgroundColor(new java.awt.Color(104, 75, 181));
                    cell.setPadding(5);
                    table.addCell(cell);
                }
                for (List<Object> fila : seccion.filas()) {
                    for (Object valor : fila) {
                        PdfPCell cell = new PdfPCell(new Phrase(textoCelda(valor), cuerpo));
                        cell.setPadding(4);
                        table.addCell(cell);
                    }
                }
                if (!seccion.totales().isEmpty()) {
                    for (Object valor : seccion.totales()) {
                        PdfPCell cell = new PdfPCell(new Phrase(textoCelda(valor), encabezado));
                        cell.setBackgroundColor(new java.awt.Color(104, 75, 181));
                        cell.setPadding(5);
                        table.addCell(cell);
                    }
                }
                document.add(table);
                document.add(Chunk.NEWLINE);
            }
            document.close();
            return new ReporteArchivo(
                    exportacion.nombreArchivo() + ".pdf", "application/pdf", output.toByteArray());
        } catch (DocumentException | IOException exception) {
            throw new IllegalStateException("No se pudo generar el archivo PDF.", exception);
        }
    }

    private Exportacion preparar(
            String reporte,
            LocalDate desde,
            LocalDate hasta,
            Integer limite,
            Long productoId,
            TipoMovimiento tipo,
            String estado) {
        LocalDateTime generadoEn = LocalDateTime.now();
        return switch (reporte) {
            case "ventas" -> exportarVentas(desde, hasta, generadoEn);
            case "productos-mas-vendidos" -> exportarProductos(desde, hasta, limite, generadoEn);
            case "movimientos" -> exportarMovimientos(desde, hasta, productoId, tipo, generadoEn);
            case "inventario" -> exportarInventario(estado, generadoEn);
            case "pedidos-recepciones" -> exportarPedidos(desde, hasta, generadoEn);
            default -> throw new IllegalArgumentException("El reporte solicitado no es válido.");
        };
    }

    private Exportacion exportarVentas(LocalDate desde, LocalDate hasta, LocalDateTime generadoEn) {
        VentasPeriodo datos = reportesService.ventas(desde, hasta);
        List<List<Object>> filas = datos.ventasPorDia().stream()
                .map(dia -> fila(dia.fecha(), dia.cantidadNotas(), dia.importe()))
                .toList();
        Seccion seccion = new Seccion(
                "Ventas por día",
                List.of("Fecha", "Cantidad de notas", "Importe"),
                filas,
                fila("TOTAL", datos.cantidadNotas(), datos.importeTotal()));
        return new Exportacion("reporte_ventas", "Ventas por período", generadoEn,
                "Desde " + datos.desde() + " hasta " + datos.hasta(), List.of(seccion));
    }

    private Exportacion exportarProductos(
            LocalDate desde, LocalDate hasta, Integer limite, LocalDateTime generadoEn) {
        ProductosVendidos datos = reportesService.productosVendidos(desde, hasta, limite == null ? 10 : limite);
        List<List<Object>> filas = new ArrayList<>();
        long totalUnidades = 0;
        int posicion = 1;
        for (ProductoVendido producto : datos.productos()) {
            filas.add(fila(posicion++, producto.nombre(), producto.unidadesVendidas()));
            totalUnidades += producto.unidadesVendidas();
        }
        Seccion seccion = new Seccion(
                "Productos más vendidos",
                List.of("Posición", "Producto", "Unidades vendidas"),
                filas,
                fila("TOTAL DEL RANKING", "", totalUnidades));
        return new Exportacion("reporte_productos_mas_vendidos", "Productos más vendidos", generadoEn,
                "Desde " + datos.desde() + " hasta " + datos.hasta() + "; límite " + datos.limite(),
                List.of(seccion));
    }

    private Exportacion exportarMovimientos(
            LocalDate desde, LocalDate hasta, Long productoId, TipoMovimiento tipo, LocalDateTime generadoEn) {
        int pagina = 0;
        long totalEsperado = -1;
        LocalDate desdeAplicado = desde;
        LocalDate hastaAplicado = hasta;
        List<MovimientoItem> movimientos = new ArrayList<>();
        while (totalEsperado < 0 || movimientos.size() < totalEsperado) {
            MovimientosPagina respuesta = reportesService.movimientos(
                    desde, hasta, productoId, tipo, pagina++, 100);
            totalEsperado = respuesta.totalElementos();
            desdeAplicado = respuesta.desde();
            hastaAplicado = respuesta.hasta();
            if (respuesta.movimientos().isEmpty() && movimientos.size() < totalEsperado) {
                throw new IllegalStateException("No se pudieron recuperar todos los movimientos del reporte.");
            }
            movimientos.addAll(respuesta.movimientos());
        }
        List<List<Object>> filas = movimientos.stream()
                .map(movimiento -> fila(
                        movimiento.id(),
                        movimiento.fecha(),
                        movimiento.productoNombre(),
                        movimiento.tipo(),
                        movimiento.cantidad(),
                        movimiento.stockAnterior(),
                        movimiento.stockNuevo(),
                        movimiento.observacion()))
                .toList();
        String filtros = "Desde " + desdeAplicado + " hasta " + hastaAplicado
                + "; producto: " + (productoId == null ? "todos" : productoId)
                + "; tipo: " + (tipo == null ? "todos" : tipo);
        Seccion seccion = new Seccion(
                "Historial de movimientos (" + movimientos.size() + ")",
                List.of("ID", "Fecha", "Producto", "Tipo", "Cantidad", "Stock anterior", "Stock nuevo", "Observación"),
                filas,
                List.of());
        return new Exportacion("reporte_movimientos", "Historial de movimientos", generadoEn,
                filtros, List.of(seccion));
    }

    private Exportacion exportarInventario(String estado, LocalDateTime generadoEn) {
        Inventario datos = reportesService.inventario(estado);
        List<List<Object>> filas = new ArrayList<>();
        long totalUnidades = 0;
        for (ProductoInventario producto : datos.productos()) {
            filas.add(fila(producto.productoId(), producto.nombre(), producto.stockActual(),
                    producto.stockMinimo(), producto.stockMaximo(), producto.estado()));
            totalUnidades += producto.stockActual();
        }
        Seccion seccion = new Seccion(
                "Inventario (" + datos.productos().size() + " productos)",
                List.of("ID", "Producto", "Stock actual", "Stock mínimo", "Stock máximo", "Estado"),
                filas,
                fila("TOTAL", "", totalUnidades, "", "", ""));
        return new Exportacion("reporte_inventario", "Inventario", generadoEn,
                "Estado: " + (estado == null || estado.isBlank() ? "todos" : estado.toUpperCase(Locale.ROOT)),
                List.of(seccion));
    }

    private Exportacion exportarPedidos(LocalDate desde, LocalDate hasta, LocalDateTime generadoEn) {
        PedidosRecepciones datos = reportesService.pedidosRecepciones(desde, hasta);
        List<List<Object>> resumen = datos.resumenPorEstado().stream()
                .map(estado -> fila(estado.estado(), estado.cantidad(), estado.primero(), estado.ultimo()))
                .toList();
        long totalPedidos = datos.resumenPorEstado().stream().mapToLong(EstadoPedido::cantidad).sum();
        List<Object> totalResumen = fila("TOTAL", totalPedidos, "", "");
        List<List<Object>> pedidos = datos.pedidos().stream()
                .map(pedido -> fila(
                        pedido.pedidoId(), pedido.fechaPedido(), pedido.proveedor(), pedido.estado(),
                        pedido.unidadesPedidas(), pedido.unidadesRecibidas(), pedido.unidadesPendientes(),
                        pedido.ultimaRecepcion()))
                .toList();
        long unidadesPedidas = datos.pedidos().stream().mapToLong(PedidoRecepcion::unidadesPedidas).sum();
        long unidadesRecibidas = datos.pedidos().stream().mapToLong(PedidoRecepcion::unidadesRecibidas).sum();
        long unidadesPendientes = datos.pedidos().stream().mapToLong(PedidoRecepcion::unidadesPendientes).sum();
        List<List<Object>> recepciones = datos.recepcionesPorDia().stream()
                .map(dia -> fila(dia.fecha(), dia.recepciones(), dia.unidadesRecibidas()))
                .toList();
        long totalRecepciones = datos.recepcionesPorDia().stream().mapToLong(RecepcionPorDia::recepciones).sum();
        long totalUnidadesRecibidas = datos.recepcionesPorDia().stream()
                .mapToLong(RecepcionPorDia::unidadesRecibidas).sum();
        return new Exportacion("reporte_pedidos_recepciones", "Pedidos y recepciones", generadoEn,
                "Desde " + datos.desde() + " hasta " + datos.hasta(),
                List.of(
                        new Seccion("Resumen de pedidos por estado",
                                List.of("Estado", "Cantidad", "Pedido más antiguo", "Pedido más reciente"),
                                resumen, totalResumen),
                        new Seccion("Detalle de pedidos",
                                List.of("Pedido", "Fecha", "Proveedor", "Estado", "Unidades pedidas",
                                        "Unidades recibidas", "Unidades pendientes", "Última recepción"),
                                pedidos, fila("TOTAL", "", "", "", unidadesPedidas, unidadesRecibidas,
                                        unidadesPendientes, "")),
                        new Seccion("Recepciones por día",
                                List.of("Fecha", "Recepciones", "Unidades recibidas"),
                                recepciones, fila("TOTAL", totalRecepciones, totalUnidadesRecibidas))));
    }

    private int escribirMetadata(Sheet sheet, int rowIndex, Exportacion exportacion) {
        Row generado = sheet.createRow(rowIndex++);
        generado.createCell(0).setCellValue("Fecha de generación");
        generado.createCell(1).setCellValue(exportacion.generadoEn().format(FECHA_HORA));
        Row filtros = sheet.createRow(rowIndex++);
        filtros.createCell(0).setCellValue("Período / filtros");
        filtros.createCell(1).setCellValue(exportacion.filtros());
        return rowIndex;
    }

    private void escribirFilaExcel(Row fila, List<Object> valores) {
        for (int columna = 0; columna < valores.size(); columna++) {
            escribirCelda(fila.createCell(columna), valores.get(columna));
        }
    }

    private void escribirCelda(Cell celda, Object valor) {
        if (valor == null) return;
        if (valor instanceof Number numero) {
            celda.setCellValue(numero.doubleValue());
        } else {
            celda.setCellValue(textoCelda(valor));
        }
    }

    private String textoCelda(Object valor) {
        if (valor == null) return "";
        if (valor instanceof LocalDate fecha) return fecha.format(FECHA);
        if (valor instanceof LocalDateTime fechaHora) return fechaHora.format(FECHA_HORA);
        return valor.toString();
    }

    private List<Object> fila(Object... valores) {
        return new ArrayList<>(Arrays.asList(valores));
    }

    public record ReporteArchivo(String nombre, String mediaType, byte[] contenido) {
    }

    private record Seccion(String titulo, List<String> encabezados, List<List<Object>> filas, List<Object> totales) {
    }

    private record Exportacion(
            String nombreArchivo,
            String titulo,
            LocalDateTime generadoEn,
            String filtros,
            List<Seccion> secciones) {
    }
}
