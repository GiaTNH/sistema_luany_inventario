package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.time.LocalDate;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotaVentaService {

    private static final BigDecimal MAX_TOTAL = new BigDecimal("9999999999.99");
    private static final int MAX_LIMITE_ASISTENTE = 30;

    private final NotaVentaRepository notaVentaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteService clienteService;
    private final ProductoStockLockService productoStockLockService;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoService movimientoService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<NotaVentaResumenResponse> listar(String busqueda) {
        String termino = busqueda == null ? "" : busqueda.trim();
        List<NotaVenta> notas = termino.isEmpty()
                ? notaVentaRepository.findAllByOrderByFechaEmisionDesc()
                : notaVentaRepository.buscar(termino);
        return notas.stream().map(this::resumen).toList();
    }

    @Transactional(readOnly = true)
    public List<NotaVentaResumenResponse> listarParaAsistente(
            LocalDate desde, LocalDate hasta, int limite) {
        return listarParaAsistente(null, desde, hasta, limite);
    }

    @Transactional(readOnly = true)
    public List<NotaVentaResumenResponse> listarParaAsistente(
            String cliente, LocalDate desde, LocalDate hasta, int limite) {
        if (limite < 1) {
            throw new IllegalArgumentException("El límite debe ser al menos 1");
        }
        if (limite > MAX_LIMITE_ASISTENTE) {
            throw new IllegalArgumentException("El límite no puede superar " + MAX_LIMITE_ASISTENTE);
        }
        return notaVentaRepository.buscarParaAsistente(
                        cliente == null || cliente.isBlank() ? null : cliente.trim(),
                        desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay(),
                        PageRequest.of(0, limite, Sort.by(Sort.Order.desc("fechaEmision"))))
                .stream()
                .map(this::resumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotaVentaDetalleResponse obtener(Long id) {
        NotaVenta nota = notaVentaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La nota de venta no existe"));
        return detalle(nota);
    }

    @Transactional
    public NotaVentaDetalleResponse crear(CrearNotaVentaRequest request, String emailUsuario) {
        validarLineas(request == null ? null : request.getProductos());
        Cliente cliente = request.getClienteId() == null
                ? clienteService.obtenerOCrearGeneral()
                : clienteRepository.findById(request.getClienteId())
                        .orElseThrow(() -> new IllegalArgumentException("El cliente no existe"));
        Usuario usuario = usuarioRepository.buscarPorEmail(emailUsuario)
                .orElseThrow(() -> new IllegalStateException("No se encontró el usuario de la sesión"));

        List<Long> idsOrdenados = request.getProductos().stream()
                .map(CrearDetalleVentaRequest::getProductoId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, Producto> productosBloqueados = new HashMap<>();
        for (Producto producto : productoStockLockService.bloquear(idsOrdenados)) {
            Long productoId = producto.getId();
            productosBloqueados.put(productoId, producto);
        }

        BigDecimal total = BigDecimal.ZERO.setScale(2);
        List<DetalleVenta> detalles = new ArrayList<>();
        for (CrearDetalleVentaRequest item : request.getProductos()) {
            Producto producto = productosBloqueados.get(item.getProductoId());
            if (producto == null) {
                throw new IllegalArgumentException("El producto no existe o no está activo");
            }
            if (!producto.isActivo()) {
                throw new IllegalArgumentException(
                        "El producto " + producto.getNombre() + " está inactivo y no puede venderse");
            }
            if (producto.getStockActual() < item.getCantidad()) {
                throw new IllegalArgumentException(
                        "Stock insuficiente para " + producto.getNombre() + ": disponibles "
                                + producto.getStockActual() + ", solicitadas " + item.getCantidad());
            }
            BigDecimal precioUnitario = precioActual(producto);
            BigDecimal subtotal = precioUnitario
                    .multiply(BigDecimal.valueOf(item.getCantidad()))
                    .setScale(2, RoundingMode.HALF_UP);
            total = total.add(subtotal);

            DetalleVenta detalle = new DetalleVenta();
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(precioUnitario);
            detalle.setSubtotal(subtotal);
            detalles.add(detalle);
        }
        if (total.compareTo(MAX_TOTAL) > 0) {
            throw new IllegalArgumentException("El total de la venta supera el máximo permitido");
        }

        NotaVenta nota = new NotaVenta();
        nota.setNumeroNota(generarNumeroNota());
        nota.setFechaEmision(LocalDateTime.now(clock));
        nota.setCliente(cliente);
        nota.setUsuario(usuario);
        nota.setTotal(total);
        nota.setEstado("EMITIDA");
        try {
            nota = notaVentaRepository.saveAndFlush(nota);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No se pudo asignar un número único a la nota. Intenta nuevamente");
        }
        nota.setNumeroNota("Nota-" + String.format(Locale.ROOT, "%03d", nota.getId()));

        for (DetalleVenta detalle : detalles) {
            detalle.setNotaVenta(nota);
        }
        detalleVentaRepository.saveAllAndFlush(detalles);

        for (DetalleVenta detalle : detalles) {
            MovimientoRequest movimiento = new MovimientoRequest();
            movimiento.setProductoId(detalle.getProducto().getId());
            movimiento.setTipo(TipoMovimiento.VENTA);
            movimiento.setCantidad(detalle.getCantidad());
            movimiento.setObservacion("Venta " + nota.getNumeroNota());
            movimientoService.registrar(movimiento, emailUsuario);
        }

        return detalle(nota);
    }

    private void validarLineas(List<CrearDetalleVentaRequest> productos) {
        if (productos == null || productos.isEmpty()) {
            throw new IllegalArgumentException("Agrega al menos un producto a la nota");
        }
        Set<Long> productosVistos = new HashSet<>();
        for (CrearDetalleVentaRequest item : productos) {
            if (item == null) {
                throw new IllegalArgumentException("Cada línea debe incluir un producto válido");
            }
            if (item.getProductoId() == null || item.getProductoId() <= 0) {
                throw new IllegalArgumentException("El producto es obligatorio y debe ser válido");
            }
            if (item.getCantidad() == null || item.getCantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor a cero");
            }
            if (!productosVistos.add(item.getProductoId())) {
                throw new IllegalArgumentException("No repitas un producto en la misma nota de venta");
            }
        }
    }

    private BigDecimal precioActual(Producto producto) {
        Double precio = producto.getPrecio();
        if (precio == null || !Double.isFinite(precio) || precio < 0) {
            throw new IllegalStateException("El producto " + producto.getNombre() + " tiene un precio inválido");
        }
        BigDecimal normalizado = BigDecimal.valueOf(precio).setScale(2, RoundingMode.HALF_UP);
        if (normalizado.compareTo(MAX_TOTAL) > 0) {
            throw new IllegalStateException("El precio del producto " + producto.getNombre() + " es inválido");
        }
        return normalizado;
    }

    private String generarNumeroNota() {
        return "NV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 27).toUpperCase(Locale.ROOT);
    }

    private NotaVentaResumenResponse resumen(NotaVenta nota) {
        return new NotaVentaResumenResponse(
                nota.getId(),
                nota.getNumeroNota(),
                nota.getFechaEmision(),
                nota.getCliente().getNombre(),
                nota.getUsuario().getNombre(),
                nota.getTotal(),
                nota.getEstado(),
                detalleVentaRepository.countByNotaVentaId(nota.getId()));
    }

    private NotaVentaDetalleResponse detalle(NotaVenta nota) {
        List<NotaVentaDetalleResponse.Item> productos =
                detalleVentaRepository.findByNotaVentaIdOrderById(nota.getId()).stream()
                        .map(detalle -> new NotaVentaDetalleResponse.Item(
                                detalle.getProducto().getId(),
                                detalle.getProducto().getNombre(),
                                detalle.getCantidad(),
                                detalle.getPrecioUnitario(),
                                detalle.getSubtotal()))
                        .toList();
        return new NotaVentaDetalleResponse(
                nota.getId(),
                nota.getNumeroNota(),
                nota.getFechaEmision(),
                nota.getCliente().getNombre(),
                nota.getCliente().getNumeroDocumento(),
                nota.getUsuario().getNombre(),
                nota.getTotal(),
                nota.getEstado(),
                productos);
    }
}
