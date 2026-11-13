package com.luany.inventory.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
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
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
            encabezadoStyle.setFillForegroundColor(IndexedColors.VIOLET.getIndex());
            encabezadoStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font encabezadoFont = workbook.createFont();
            encabezadoFont.setBold(true);
            encabezadoFont.setColor(IndexedColors.WHITE.getIndex());
            encabezadoStyle.setFont(encabezadoFont);
            encabezadoStyle.setAlignment(HorizontalAlignment.CENTER);
            encabezadoStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
            encabezadoStyle.setWrapText(true);
            encabezadoStyle.setBorderBottom(BorderStyle.THIN);
            encabezadoStyle.setBorderTop(BorderStyle.THIN);
            encabezadoStyle.setBorderLeft(BorderStyle.THIN);
            encabezadoStyle.setBorderRight(BorderStyle.THIN);

            CellStyle filaStyle = workbook.createCellStyle();
            filaStyle.setBorderBottom(BorderStyle.THIN);
            filaStyle.setBorderTop(BorderStyle.THIN);
            filaStyle.setBorderLeft(BorderStyle.THIN);
            filaStyle.setBorderRight(BorderStyle.THIN);
            filaStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
            filaStyle.setWrapText(true);

            CellStyle monedaStyle = workbook.createCellStyle();
            monedaStyle.cloneStyleFrom(filaStyle);
            monedaStyle.setDataFormat(workbook.createDataFormat().getFormat("\"S/ \"#,##0.00"));
            monedaStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle numeroStyle = workbook.createCellStyle();
            numeroStyle.cloneStyleFrom(filaStyle);
            numeroStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            numeroStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle totalesStyle = workbook.createCellStyle();
            totalesStyle.cloneStyleFrom(filaStyle);
            totalesStyle.setFillForegroundColor(IndexedColors.LAVENDER.getIndex());
            totalesStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font totalesFont = workbook.createFont();
            totalesFont.setBold(true);
            totalesFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            totalesStyle.setFont(totalesFont);

            CellStyle totalesMonedaStyle = workbook.createCellStyle();
            totalesMonedaStyle.cloneStyleFrom(totalesStyle);
            totalesMonedaStyle.setDataFormat(workbook.createDataFormat().getFormat("\"S/ \"#,##0.00"));
            totalesMonedaStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle totalesNumeroStyle = workbook.createCellStyle();
            totalesNumeroStyle.cloneStyleFrom(totalesStyle);
            totalesNumeroStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            totalesNumeroStyle.setAlignment(HorizontalAlignment.RIGHT);

            int rowIndex = 0;
            Row brand = sheet.createRow(rowIndex++);
            brand.createCell(0).setCellValue("LUANY");
            brand.getCell(0).setCellStyle(tituloStyle);
            brand.createCell(1).setCellValue("Juega · Viste · Crece");
            brand.getCell(1).setCellStyle(seccionStyle);
            brand.setHeightInPoints(30);
            agregarLogoExcel(workbook, sheet);
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
                encabezado.setHeightInPoints(30);
                for (int columna = 0; columna < seccion.encabezados().size(); columna++) {
                    Cell celda = encabezado.createCell(columna);
                    celda.setCellValue(seccion.encabezados().get(columna));
                    celda.setCellStyle(encabezadoStyle);
                }
                for (List<Object> valores : seccion.filas()) {
                    Row fila = sheet.createRow(rowIndex++);
                    escribirFilaExcel(fila, valores, seccion.encabezados(), filaStyle, monedaStyle, numeroStyle);
                }
                if (!seccion.totales().isEmpty()) {
                    Row totales = sheet.createRow(rowIndex++);
                    totales.setHeightInPoints(25);
                    for (int columna = 0; columna < seccion.totales().size(); columna++) {
                        Cell celda = totales.createCell(columna);
                        Object valor = seccion.totales().get(columna);
                        escribirCelda(celda, valor);
                        celda.setCellStyle(esCeldaMonetaria(seccion.encabezados(), columna)
                                ? totalesMonedaStyle : valor instanceof Number ? totalesNumeroStyle : totalesStyle);
                    }
                }
            }

            int columnas = exportacion.secciones().stream()
                    .mapToInt(seccion -> seccion.encabezados().size()).max().orElse(1);
            for (int columna = 0; columna < columnas; columna++) {
                sheet.autoSizeColumn(columna);
                sheet.setColumnWidth(columna, Math.min(Math.max(sheet.getColumnWidth(columna) + 1000, 3200), 22000));
            }
            sheet.createFreezePane(0, 4);
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.setFitToPage(true);
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
            com.lowagie.text.Font encabezadoMarca =
                    FontFactory.getFont(FontFactory.HELVETICA, 8, new java.awt.Color(117, 107, 145));
            com.lowagie.text.Font metadata =
                    FontFactory.getFont(FontFactory.HELVETICA, 9, java.awt.Color.DARK_GRAY);

            byte[] logo = cargarLogo();
            if (logo != null) {
                Image imagen = Image.getInstance(logo);
                imagen.scaleToFit(160, 68);
                PdfPTable marca = new PdfPTable(new float[]{1, 8});
                marca.setWidthPercentage(100);
                PdfPCell celdaLogo = new PdfPCell(imagen, true);
                celdaLogo.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
                Paragraph textoMarca = new Paragraph();
                textoMarca.add(new Chunk("LUANY\n", titulo));
                textoMarca.add(new Chunk("Juega · Viste · Crece", encabezadoMarca));
                PdfPCell celdaMarca = new PdfPCell(textoMarca);
                celdaMarca.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
                celdaMarca.setVerticalAlignment(com.lowagie.text.Element.ALIGN_MIDDLE);
                marca.addCell(celdaLogo);
                marca.addCell(celdaMarca);
                document.add(marca);
            } else {
                document.add(new Paragraph("LUANY", titulo));
            }
            Paragraph tituloReporte = new Paragraph(exportacion.titulo(), titulo);
            tituloReporte.setSpacingBefore(8);
            tituloReporte.setSpacingAfter(5);
            document.add(tituloReporte);
            Paragraph generado = new Paragraph(
                    "Fecha de generación: " + exportacion.generadoEn().format(FECHA_HORA), metadata);
            generado.setSpacingAfter(3);
            document.add(generado);
            Paragraph filtros = new Paragraph("Filtros: " + exportacion.filtros(), metadata);
            filtros.setSpacingAfter(7);
            document.add(filtros);
            document.add(Chunk.NEWLINE);

            for (Seccion seccion : exportacion.secciones()) {
                document.add(new Paragraph(seccion.titulo(), FontFactory.getFont(
                        FontFactory.HELVETICA_BOLD, 11, new java.awt.Color(49, 39, 114))));
                PdfPTable table = new PdfPTable(seccion.encabezados().size());
                table.setWidthPercentage(100);
                table.setHeaderRows(1);
                table.setWidths(anchosPdf(seccion.encabezados()));
                table.setSpacingBefore(6);
                table.setSpacingAfter(12);
                table.setSplitRows(true);
                for (String columna : seccion.encabezados()) {
                    PdfPCell cell = new PdfPCell(new Phrase(columna, encabezado));
                    cell.setBackgroundColor(new java.awt.Color(104, 75, 181));
                    cell.setPadding(7);
                    cell.setBorderColor(new java.awt.Color(220, 214, 233));
                    table.addCell(cell);
                }
                for (int indiceFila = 0; indiceFila < seccion.filas().size(); indiceFila++) {
                    List<Object> fila = seccion.filas().get(indiceFila);
                    for (int columna = 0; columna < fila.size(); columna++) {
                        Object valor = fila.get(columna);
                        PdfPCell cell = new PdfPCell(new Phrase(
                                textoCelda(valor, seccion.encabezados(), columna), cuerpo));
                        cell.setPadding(6);
                        cell.setBorderColor(new java.awt.Color(220, 214, 233));
                        if (indiceFila % 2 == 1) {
                            cell.setBackgroundColor(new java.awt.Color(248, 246, 252));
                        }
                        if (valor instanceof Number) {
                            cell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
                        }
                        table.addCell(cell);
                    }
                }
                if (!seccion.totales().isEmpty()) {
                    for (int columna = 0; columna < seccion.totales().size(); columna++) {
                        Object valor = seccion.totales().get(columna);
                        PdfPCell cell = new PdfPCell(new Phrase(
                                textoCelda(valor, seccion.encabezados(), columna), encabezado));
                        cell.setBackgroundColor(new java.awt.Color(104, 75, 181));
                        cell.setPadding(6);
                        cell.setBorderColor(new java.awt.Color(220, 214, 233));
                        if (valor instanceof Number) {
                            cell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
                        }
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

    private void escribirFilaExcel(
            Row fila,
            List<Object> valores,
            List<String> encabezados,
            CellStyle filaStyle,
            CellStyle monedaStyle,
            CellStyle numeroStyle) {
        for (int columna = 0; columna < valores.size(); columna++) {
            Cell celda = fila.createCell(columna);
            Object valor = valores.get(columna);
            escribirCelda(celda, valor);
            if (valor instanceof Number) {
                celda.setCellStyle(esCeldaMonetaria(encabezados, columna) ? monedaStyle : numeroStyle);
            } else {
                celda.setCellStyle(filaStyle);
            }
        }
    }

    private boolean esCeldaMonetaria(List<String> encabezados, int columna) {
        return columna < encabezados.size()
                && encabezados.get(columna).toLowerCase(Locale.ROOT)
                        .matches(".*(importe|precio|subtotal|total).*");
    }

    private float[] anchosPdf(List<String> encabezados) {
        float[] anchos = new float[encabezados.size()];
        for (int indice = 0; indice < encabezados.size(); indice++) {
            String encabezado = encabezados.get(indice).toLowerCase(Locale.ROOT);
            anchos[indice] = encabezado.contains("producto") || encabezado.contains("observación")
                    ? 2.8f
                    : encabezado.contains("fecha") || encabezado.contains("proveedor") ? 1.6f : 1f;
        }
        return anchos;
    }

    private String textoCelda(Object valor, List<String> encabezados, int columna) {
        if (valor instanceof Number numero && esCeldaMonetaria(encabezados, columna)) {
            return String.format(Locale.ROOT, "S/ %.2f", numero.doubleValue());
        }
        return textoCelda(valor);
    }

    private void agregarLogoExcel(XSSFWorkbook workbook, Sheet sheet) throws IOException {
        byte[] logo = cargarLogo();
        if (logo == null) return;
        int indiceImagen = workbook.addPicture(logo, XSSFWorkbook.PICTURE_TYPE_PNG);
        var ancla = workbook.getCreationHelper().createClientAnchor();
        ancla.setCol1(3);
        ancla.setRow1(0);
        ancla.setCol2(4);
        ancla.setRow2(2);
        sheet.createDrawingPatriarch().createPicture(ancla, indiceImagen).resize(0.2);
    }

    private byte[] cargarLogo() throws IOException {
        ClassPathResource recurso = new ClassPathResource("static/img/logo_luany.png");
        if (!recurso.exists()) return null;
        try (InputStream entrada = recurso.getInputStream()) {
            return entrada.readAllBytes();
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
