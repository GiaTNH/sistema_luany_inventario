package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.ProductoRepository;
import com.luany.inventory.repository.ReportesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.luany.inventory.model.ReportesResponse.*;

@Service
@RequiredArgsConstructor
public class ReportesService {

    private static final int LIMITE_PRODUCTOS_MAXIMO = 100;
    private static final int TAMANO_PAGINA_MAXIMO = 100;
    private static final int PAGINA_MAXIMA = 100_000;

    private final ReportesRepository reportesRepository;
    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public VentasPeriodo ventas(LocalDate desde, LocalDate hasta) {
        Periodo periodo = validarPeriodo(desde, hasta);
        List<VentaDia> dias = reportesRepository.ventasPorDia(periodo.inicio(), periodo.finExclusivo())
                .stream()
                .map(fila -> new VentaDia(
                        convertirFecha(fila[0]),
                        convertirDecimal(fila[1]),
                        convertirLong(fila[2])))
                .toList();
        BigDecimal importe = dias.stream()
                .map(VentaDia::importe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long cantidadNotas = dias.stream().mapToLong(VentaDia::cantidadNotas).sum();
        return new VentasPeriodo(periodo.desde(), periodo.hasta(), importe, cantidadNotas, dias);
    }

    @Transactional(readOnly = true)
    public ProductosVendidos productosVendidos(LocalDate desde, LocalDate hasta, int limite) {
        if (limite < 1 || limite > LIMITE_PRODUCTOS_MAXIMO) {
            throw new IllegalArgumentException("El límite debe estar entre 1 y " + LIMITE_PRODUCTOS_MAXIMO);
        }
        Periodo periodo = validarPeriodo(desde, hasta);
        List<ProductoVendido> productos = reportesRepository
                .productosMasVendidos(periodo.inicio(), periodo.finExclusivo(), limite)
                .stream()
                .map(fila -> new ProductoVendido(
                        convertirLong(fila[0]), String.valueOf(fila[1]), convertirLong(fila[2])))
                .toList();
        return new ProductosVendidos(periodo.desde(), periodo.hasta(), limite, productos);
    }

    @Transactional(readOnly = true)
    public MovimientosPagina movimientos(
            LocalDate desde,
            LocalDate hasta,
            Long productoId,
            TipoMovimiento tipo,
            int pagina,
            int tamano) {
        if (productoId != null && productoId < 1) {
            throw new IllegalArgumentException("El producto debe ser válido");
        }
        if (pagina < 0 || pagina > PAGINA_MAXIMA) {
            throw new IllegalArgumentException("La página solicitada no es válida");
        }
        if (tamano < 1 || tamano > TAMANO_PAGINA_MAXIMO) {
            throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y " + TAMANO_PAGINA_MAXIMO);
        }
        Periodo periodo = validarPeriodo(desde, hasta);
        int offset = Math.multiplyExact(pagina, tamano);
        List<MovimientoItem> movimientos = reportesRepository
                .movimientos(periodo.inicio(), periodo.finExclusivo(), productoId, tipo, offset, tamano)
                .stream()
                .map(movimiento -> new MovimientoItem(
                        movimiento.getId(),
                        movimiento.getFecha(),
                        movimiento.getTipo().name(),
                        movimiento.getCantidad(),
                        movimiento.getObservacion(),
                        movimiento.getProducto().getId(),
                        movimiento.getProducto().getNombre(),
                        movimiento.getStockAnterior(),
                        movimiento.getStockNuevo()))
                .toList();
        long total = reportesRepository.contarMovimientos(
                periodo.inicio(), periodo.finExclusivo(), productoId, tipo);
        int totalPaginas = total == 0 ? 0 : (int) Math.ceil((double) total / tamano);
        return new MovimientosPagina(
                periodo.desde(), periodo.hasta(), pagina, tamano, total, totalPaginas, movimientos);
    }

    @Transactional(readOnly = true)
    public Inventario inventario(String estado) {
        String estadoFiltro = estado == null || estado.isBlank() ? null : estado.trim().toUpperCase();
        if (estadoFiltro != null
                && !List.of("AGOTADO", "BAJO", "NORMAL", "EXCESO").contains(estadoFiltro)) {
            throw new IllegalArgumentException("El estado debe ser AGOTADO, BAJO, NORMAL o EXCESO");
        }
        List<ProductoInventario> productos = productoRepository.findAll().stream()
                .map(producto -> new ProductoInventario(
                        producto.getId(),
                        producto.getNombre(),
                        producto.getStockActual(),
                        producto.getStockMinimo(),
                        producto.getStockMaximo(),
                        estadoInventario(producto)))
                .filter(producto -> estadoFiltro == null || estadoFiltro.equals(producto.estado()))
                .sorted((a, b) -> a.nombre().compareToIgnoreCase(b.nombre()))
                .toList();
        return new Inventario(productos);
    }

    @Transactional(readOnly = true)
    public PedidosRecepciones pedidosRecepciones(LocalDate desde, LocalDate hasta) {
        Periodo periodo = validarPeriodo(desde, hasta);
        List<EstadoPedido> estados = reportesRepository
                .resumenEstadosPedido(periodo.inicio(), periodo.finExclusivo())
                .stream()
                .map(fila -> new EstadoPedido(
                        String.valueOf(fila[0]),
                        convertirLong(fila[1]),
                        convertirFechaHora(fila[2]),
                        convertirFechaHora(fila[3])))
                .toList();
        List<PedidoRecepcion> pedidos = reportesRepository
                .pedidosConRecepciones(periodo.inicio(), periodo.finExclusivo())
                .stream()
                .map(fila -> {
                    long pedidas = convertirLong(fila[4]);
                    long recibidas = convertirLong(fila[5]);
                    return new PedidoRecepcion(
                            convertirLong(fila[0]),
                            convertirFechaHora(fila[1]),
                            String.valueOf(fila[2]),
                            String.valueOf(fila[3]),
                            pedidas,
                            recibidas,
                            Math.max(pedidas - recibidas, 0),
                            fila[6] == null ? null : convertirFechaHora(fila[6]));
                })
                .toList();
        List<RecepcionPorDia> recepciones = reportesRepository
                .recepcionesPorDia(periodo.inicio(), periodo.finExclusivo())
                .stream()
                .map(fila -> new RecepcionPorDia(
                        convertirFecha(fila[0]),
                        convertirLong(fila[1]),
                        convertirLong(fila[2])))
                .toList();
        return new PedidosRecepciones(periodo.desde(), periodo.hasta(), estados, pedidos, recepciones);
    }

    private Periodo validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde == null && hasta == null) {
            hasta = LocalDate.now();
            desde = hasta.minusDays(29);
        } else if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Debes indicar ambas fechas: desde y hasta");
        }
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a hasta");
        }
        return new Periodo(desde, hasta, desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
    }

    private String estadoInventario(Producto producto) {
        int actual = producto.getStockActual();
        if (actual <= 0) return "AGOTADO";
        if (actual <= producto.getStockMinimo()) return "BAJO";
        if (actual >= producto.getStockMaximo()) return "EXCESO";
        return "NORMAL";
    }

    private LocalDate convertirFecha(Object valor) {
        if (valor instanceof LocalDate fecha) return fecha;
        if (valor instanceof Date fecha) return fecha.toLocalDate();
        return LocalDate.parse(valor.toString());
    }

    private LocalDateTime convertirFechaHora(Object valor) {
        if (valor instanceof LocalDateTime fecha) return fecha;
        if (valor instanceof Timestamp fecha) return fecha.toLocalDateTime();
        return LocalDateTime.parse(valor.toString().replace(' ', 'T'));
    }

    private BigDecimal convertirDecimal(Object valor) {
        if (valor instanceof BigDecimal decimal) return decimal;
        return new BigDecimal(valor.toString());
    }

    private long convertirLong(Object valor) {
        if (valor instanceof Number numero) return numero.longValue();
        return Long.parseLong(valor.toString());
    }

    private record Periodo(LocalDate desde, LocalDate hasta, LocalDateTime inicio, LocalDateTime finExclusivo) {
    }
}
