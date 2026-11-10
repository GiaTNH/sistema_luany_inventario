package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final Sort ORDEN_PEDIDOS = Sort.by(Sort.Direction.DESC, "fechaPedido");

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final RecepcionPedidoRepository recepcionPedidoRepository;
    private final DetalleRecepcionPedidoRepository detalleRecepcionPedidoRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final ProductoStockLockService productoStockLockService;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoService movimientoService;

    @Transactional(readOnly = true)
    public List<PedidoResumenResponse> listar(String busqueda, String estado) {
        return listar(busqueda, estado, null);
    }

    @Transactional(readOnly = true)
    public List<PedidoResumenResponse> listar(String busqueda, String estado, Long pedidoId) {
        String termino = busqueda == null ? "" : busqueda.trim().toLowerCase(Locale.ROOT);
        String filtroEstado = estado == null || estado.isBlank()
                ? null : estado.trim().toUpperCase(Locale.ROOT);
        if (filtroEstado != null && !Set.of("PENDIENTE", "PARCIAL", "RECIBIDO").contains(filtroEstado)) {
            throw new IllegalArgumentException("El estado debe ser PENDIENTE, PARCIAL o RECIBIDO");
        }
        if (pedidoId != null && pedidoId < 1) {
            throw new IllegalArgumentException("El identificador del pedido debe ser válido");
        }

        return pedidoRepository.listarConProveedorYUsuario(ORDEN_PEDIDOS).stream()
                .filter(pedido -> pedidoId == null || pedidoId.equals(pedido.getId()))
                .filter(pedido -> filtroEstado == null || filtroEstado.equals(pedido.getEstado()))
                .filter(pedido -> termino.isEmpty()
                        || String.valueOf(pedido.getId()).contains(termino)
                        || pedido.getProveedor().getNombre().toLowerCase(Locale.ROOT).contains(termino))
                .map(this::resumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PedidoKanbanResponse> listarTablero(
            String busqueda, String estado, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a hasta");
        }

        List<PedidoResumenResponse> resumenes = listar(busqueda, estado).stream()
                .filter(pedido -> desde == null || !pedido.fechaPedido().toLocalDate().isBefore(desde))
                .filter(pedido -> hasta == null || !pedido.fechaPedido().toLocalDate().isAfter(hasta))
                .toList();
        if (resumenes.isEmpty()) return List.of();

        List<Long> ids = resumenes.stream().map(PedidoResumenResponse::id).toList();
        Map<Long, Long> solicitadas = cantidadesPorPedido(
                detallePedidoRepository.cantidadesSolicitadasPorPedido(ids));
        Map<Long, Long> recibidas = cantidadesPorPedido(
                detalleRecepcionPedidoRepository.cantidadesRecibidasPorPedido(ids));

        return resumenes.stream()
                .map(pedido -> {
                    long cantidadSolicitada = solicitadas.getOrDefault(pedido.id(), 0L);
                    long cantidadRecibida = recibidas.getOrDefault(pedido.id(), 0L);
                    return new PedidoKanbanResponse(
                            pedido.id(),
                            pedido.fechaPedido(),
                            pedido.fechaEstimadaEntrega(),
                            pedido.estado(),
                            pedido.proveedorId(),
                            pedido.proveedorNombre(),
                            pedido.totalProductos(),
                            cantidadSolicitada,
                            cantidadRecibida,
                            Math.max(cantidadSolicitada - cantidadRecibida, 0));
                })
                .toList();
    }

    private Map<Long, Long> cantidadesPorPedido(List<Object[]> filas) {
        return filas.stream().collect(Collectors.toMap(
                fila -> ((Number) fila[0]).longValue(),
                fila -> ((Number) fila[1]).longValue()));
    }

    @Transactional(readOnly = true)
    public PedidoDetalleResponse obtener(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El pedido no existe"));
        return detalle(pedido);
    }

    @Transactional
    public PedidoDetalleResponse crear(CrearPedidoRequest request, String emailUsuario) {
        Proveedor proveedor = proveedorRepository.findById(request.getProveedorId())
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no existe"));
        if (proveedor.getEstado() == null || !"ACTIVO".equalsIgnoreCase(proveedor.getEstado().trim())) {
            throw new IllegalArgumentException("Solo se pueden registrar pedidos a proveedores activos");
        }
        validarLineasPedido(request.getProductos(), proveedor.getId());
        Usuario usuario = usuarioRepository.buscarPorEmail(emailUsuario)
                .orElseThrow(() -> new IllegalStateException("No se encontró el usuario de la sesión"));

        Pedido pedido = new Pedido();
        pedido.setProveedor(proveedor);
        pedido.setUsuario(usuario);
        pedido.setFechaEstimadaEntrega(request.getFechaEstimadaEntrega());
        pedido.setEstado("PENDIENTE");
        pedido = pedidoRepository.save(pedido);

        List<DetallePedido> detalles = new ArrayList<>();
        for (CrearPedidoItemRequest item : request.getProductos()) {
            Producto producto = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new IllegalArgumentException("Uno de los productos no existe"));
            DetallePedido detalle = new DetallePedido();
            detalle.setPedido(pedido);
            detalle.setProducto(producto);
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecio(item.getPrecio());
            detalles.add(detalle);
        }
        detallePedidoRepository.saveAll(detalles);
        return detalle(pedido);
    }

    @Transactional
    public PedidoDetalleResponse recibir(Long pedidoId, RegistrarRecepcionRequest request, String emailUsuario) {
        Pedido pedido = pedidoRepository.buscarPorIdBloqueando(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El pedido no existe"));
        var recepcionAnterior = recepcionPedidoRepository.findByClaveIdempotencia(
                request.getClaveIdempotencia());
        if (recepcionAnterior.isPresent()) {
            if (!recepcionAnterior.get().getPedido().getId().equals(pedidoId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "La clave de recepción ya fue utilizada en otro pedido");
            }
            return detalle(pedido);
        }
        if ("RECIBIDO".equals(pedido.getEstado())) {
            throw new IllegalArgumentException("Este pedido ya fue recibido completamente");
        }
        if (!"PENDIENTE".equals(pedido.getEstado()) && !"PARCIAL".equals(pedido.getEstado())) {
            throw new IllegalArgumentException("El estado actual no permite registrar una recepción");
        }
        validarLineasRecepcion(request.getProductos());

        Usuario usuario = usuarioRepository.buscarPorEmail(emailUsuario)
                .orElseThrow(() -> new IllegalStateException("No se encontró el usuario de la sesión"));
        List<DetallePedido> detallesPedido = detallePedidoRepository.findByPedidoIdOrderById(pedidoId);
        var detallePorId = detallesPedido.stream()
                .collect(java.util.stream.Collectors.toMap(DetallePedido::getId, detalle -> detalle));
        int totalPedido = 0;
        int totalRecibidoTrasOperacion = 0;
        var cantidadesAnteriores = new java.util.HashMap<Long, Integer>();
        for (DetallePedido detalle : detallesPedido) {
            int recibido = detalleRecepcionPedidoRepository.totalRecibido(detalle.getId()).intValue();
            cantidadesAnteriores.put(detalle.getId(), recibido);
            totalPedido += detalle.getCantidad();
            totalRecibidoTrasOperacion += recibido;
        }

        productoStockLockService.bloquear(request.getProductos().stream()
                .map(item -> {
                    DetallePedido detalle = detallePorId.get(item.getDetallePedidoId());
                    if (detalle == null) {
                        throw new IllegalArgumentException("Uno de los productos no pertenece a este pedido");
                    }
                    return detalle.getProducto().getId();
                })
                .toList());

        for (RegistrarRecepcionItemRequest item : request.getProductos()) {
            DetallePedido detalle = detallePorId.get(item.getDetallePedidoId());
            if (detalle == null) {
                throw new IllegalArgumentException("Uno de los productos no pertenece a este pedido");
            }
            int recibidoAntes = cantidadesAnteriores.get(detalle.getId());
            int pendiente = detalle.getCantidad() - recibidoAntes;
            if (item.getCantidad() > pendiente) {
                throw new IllegalArgumentException(
                        "La cantidad recibida supera las " + pendiente + " unidades pendientes de "
                                + detalle.getProducto().getNombre());
            }
            totalRecibidoTrasOperacion += item.getCantidad();
        }

        RecepcionPedido recepcion = new RecepcionPedido();
        recepcion.setPedido(pedido);
        recepcion.setUsuario(usuario);
        recepcion.setClaveIdempotencia(request.getClaveIdempotencia());
        recepcion = recepcionPedidoRepository.saveAndFlush(recepcion);

        for (RegistrarRecepcionItemRequest item : request.getProductos()) {
            DetallePedido detalle = detallePorId.get(item.getDetallePedidoId());
            MovimientoRequest movimientoRequest = new MovimientoRequest();
            movimientoRequest.setProductoId(detalle.getProducto().getId());
            movimientoRequest.setTipo(TipoMovimiento.ENTRADA);
            movimientoRequest.setCantidad(item.getCantidad());
            movimientoRequest.setObservacion(
                    "Recepción #" + recepcion.getId() + " del pedido #" + pedido.getId());
            Movimiento movimiento = movimientoService.registrar(movimientoRequest, emailUsuario);

            DetalleRecepcionPedido lineaRecibida = new DetalleRecepcionPedido();
            lineaRecibida.setRecepcion(recepcion);
            lineaRecibida.setDetallePedido(detalle);
            lineaRecibida.setCantidad(item.getCantidad());
            lineaRecibida.setMovimiento(movimiento);
            detalleRecepcionPedidoRepository.save(lineaRecibida);
        }

        pedido.setEstado(totalRecibidoTrasOperacion == totalPedido ? "RECIBIDO" : "PARCIAL");
        pedidoRepository.save(pedido);
        return detalle(pedido);
    }

    private void validarLineasPedido(List<CrearPedidoItemRequest> productos, Long proveedorId) {
        Set<Long> productosVistos = new HashSet<>();
        for (CrearPedidoItemRequest item : productos) {
            if (!productosVistos.add(item.getProductoId())) {
                throw new IllegalArgumentException("No repitas un producto en el mismo pedido");
            }
            Producto producto = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new IllegalArgumentException("Uno de los productos no existe"));
            if (!producto.getProveedor().getId().equals(proveedorId)) {
                throw new IllegalArgumentException(
                        "El producto " + producto.getNombre() + " no pertenece al proveedor seleccionado");
            }
        }
    }

    private void validarLineasRecepcion(List<RegistrarRecepcionItemRequest> productos) {
        Set<Long> detallesVistos = new HashSet<>();
        for (RegistrarRecepcionItemRequest item : productos) {
            if (!detallesVistos.add(item.getDetallePedidoId())) {
                throw new IllegalArgumentException("No repitas un producto en la misma recepción");
            }
        }
    }

    private PedidoResumenResponse resumen(Pedido pedido) {
        return new PedidoResumenResponse(
                pedido.getId(),
                pedido.getFechaPedido(),
                pedido.getFechaEstimadaEntrega(),
                pedido.getEstado(),
                pedido.getProveedor().getId(),
                pedido.getProveedor().getNombre(),
                pedido.getUsuario().getNombre(),
                detallePedidoRepository.findByPedidoIdOrderById(pedido.getId()).size());
    }

    private PedidoDetalleResponse detalle(Pedido pedido) {
        List<DetallePedido> detallesPedido = detallePedidoRepository.findByPedidoIdOrderById(pedido.getId());
        List<PedidoDetalleResponse.Item> productos = detallesPedido.stream()
                .map(detalle -> {
                    int recibido = detalleRecepcionPedidoRepository.totalRecibido(detalle.getId()).intValue();
                    return new PedidoDetalleResponse.Item(
                            detalle.getId(),
                            detalle.getProducto().getId(),
                            detalle.getProducto().getNombre(),
                            detalle.getCantidad(),
                            recibido,
                            detalle.getCantidad() - recibido,
                            detalle.getPrecio());
                })
                .toList();
        List<PedidoDetalleResponse.Recepcion> recepciones =
                recepcionPedidoRepository.findByPedidoIdOrderByFechaDescIdDesc(pedido.getId()).stream()
                        .map(recepcion -> new PedidoDetalleResponse.Recepcion(
                                recepcion.getId(),
                                recepcion.getFecha(),
                                recepcion.getUsuario().getNombre(),
                                detalleRecepcionPedidoRepository.findByRecepcionIdOrderById(recepcion.getId())
                                        .stream()
                                        .map(linea -> new PedidoDetalleResponse.RecepcionItem(
                                                linea.getDetallePedido().getId(),
                                                linea.getDetallePedido().getProducto().getNombre(),
                                                linea.getCantidad(),
                                                linea.getMovimiento().getId()))
                                        .toList()))
                        .toList();

        return new PedidoDetalleResponse(
                pedido.getId(),
                pedido.getFechaPedido(),
                pedido.getFechaEstimadaEntrega(),
                pedido.getEstado(),
                pedido.getProveedor().getId(),
                pedido.getProveedor().getNombre(),
                pedido.getUsuario().getNombre(),
                productos,
                recepciones);
    }
}
