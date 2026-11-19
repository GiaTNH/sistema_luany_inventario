package com.luany.inventory.service;

import com.luany.inventory.model.*;
import com.luany.inventory.repository.CategoriaRepository;
import com.luany.inventory.repository.ProductoRepository;
import com.luany.inventory.repository.ProveedorRepository;
import com.luany.inventory.repository.MovimientoRepository;
import com.luany.inventory.repository.UsuarioRepository;
import com.luany.inventory.repository.DetallePedidoRepository;
import com.luany.inventory.repository.DetalleRecepcionPedidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private static final int MAX_INTENTOS_CODIGO = 5;

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoStockLockService productoStockLockService;
    private final MovimientoRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final DetalleRecepcionPedidoRepository detalleRecepcionPedidoRepository;

    public List<Producto> listar() {
        return productoRepository.findAllByActivoTrue(Sort.by(
                Sort.Order.desc("fechaCreacion"), Sort.Order.desc("id")));
    }

    public List<Producto> listarParaAsistente(int limite) {
        return productoRepository.findAllByActivoTrue(PageRequest.of(
                0, limite, Sort.by(Sort.Order.desc("fechaCreacion"), Sort.Order.desc("id"))))
                .getContent();
    }

    public Producto obtener(Long id) {
        return productoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new IllegalArgumentException("El producto no existe"));
    }

    public List<Producto> buscarPorNombre(String nombre) {
        return productoRepository.buscarPorNombre(nombre).stream().filter(Producto::isActivo).toList();
    }

    public List<Producto> productosConStockBajo() {
        return productoRepository.findStockBajo();
    }

    public List<Producto> productosConStockBajoParaAsistente(int limite) {
        return productoRepository.findStockBajo(PageRequest.of(0, limite));
    }

    @Transactional
    public Producto crear(ProductoRequest request) {
        return crear(request, null);
    }

    @Transactional
    public Producto crear(ProductoRequest request, String emailUsuario) {
        Producto producto = new Producto();
        producto.setActivo(true);
        aplicarDatos(producto, request.getNombre(), request.getCategoriaId(), request.getProveedorId(),
                request.getStockMinimo(), request.getStockMaximo(), request.getPrecio(),
                generarCodigoDisponible(), request.getDescripcion());
        producto.setStockActual(request.getStockActual());
        Producto creado;
        try {
            creado = productoRepository.saveAndFlush(producto);
        } catch (DataIntegrityViolationException exception) {
            if (esColisionCodigo(exception)) {
                throw new IllegalArgumentException(
                        "No se pudo asignar un código único al producto. Intenta nuevamente");
            }
            throw exception;
        }
        Movimiento movimiento = new Movimiento();
        movimiento.setProducto(creado);
        movimiento.setTipo(TipoMovimiento.CREACION);
        movimiento.setCantidad(0);
        movimiento.setFecha(creado.getFechaCreacion());
        movimiento.setObservacion("Producto creado y registrado en el inventario. Código: "
                + (creado.getCodigo() == null ? "—" : creado.getCodigo()));
        movimiento.setStockAnterior(creado.getStockActual());
        movimiento.setStockNuevo(creado.getStockActual());
        if (emailUsuario != null) {
            Usuario usuario = usuarioRepository.buscarPorEmail(emailUsuario)
                    .orElseThrow(() -> new IllegalStateException(
                            "No se encontró el usuario de la sesión"));
            movimiento.setUsuario(usuario);
        }
        movimientoRepository.save(movimiento);
        return creado;
    }

    @Transactional
    public Producto actualizar(Long id, ProductoActualizacionRequest request) {
        validarActivoParaModificar(id);
        Producto producto = productoStockLockService.bloquear(id);
        aplicarDatos(producto, request.getNombre(), request.getCategoriaId(), request.getProveedorId(),
                request.getStockMinimo(), request.getStockMaximo(), request.getPrecio(),
                request.getCodigo(), request.getDescripcion());
        return productoRepository.save(producto);
    }

    @Transactional
    public void eliminar(Long id) {
        validarActivoParaModificar(id);
        Producto producto = productoStockLockService.bloquear(id);
        if (producto.getStockActual() != null && producto.getStockActual() > 0) {
            throw new IllegalArgumentException("No se puede retirar un producto con stock disponible");
        }
        boolean tienePendientes = detallePedidoRepository.findByProductoId(id).stream()
                .filter(detalle -> "PENDIENTE".equalsIgnoreCase(detalle.getPedido().getEstado())
                        || "PARCIAL".equalsIgnoreCase(detalle.getPedido().getEstado()))
                .anyMatch(detalle -> {
                    long recibido = detalleRecepcionPedidoRepository
                            .totalRecibido(detalle.getId());
                    return detalle.getCantidad() - recibido > 0;
                });
        if (tienePendientes) {
            throw new IllegalArgumentException(
                    "No se puede retirar el producto porque existen recepciones pendientes en uno o más pedidos");
        }
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    private void validarActivoParaModificar(Long id) {
        if (productoRepository.findByIdAndActivoTrue(id).isPresent()) {
            return;
        }
        if (productoRepository.findById(id).isPresent()) {
            throw new IllegalArgumentException("El producto está inactivo y no se puede modificar");
        }
        throw new IllegalArgumentException("El producto no existe");
    }

    private String generarCodigoDisponible() {
        for (int intento = 0; intento < MAX_INTENTOS_CODIGO; intento++) {
            String codigo = "LU-" + UUID.randomUUID().toString().replace("-", "").substring(0, 32)
                    .toUpperCase(Locale.ROOT);
            if (!productoRepository.existsByCodigo(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException(
                "No se pudo generar un código único para el producto después de varios intentos");
    }

    private boolean esColisionCodigo(DataIntegrityViolationException exception) {
        for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uk_productos_codigo".equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    public String estadoStock(Producto p) {
        if (p.getStockActual() <= 0) {
            return "AGOTADO";
        }
        if (p.getStockActual() <= p.getStockMinimo()) {
            return "BAJO";
        }
        if (p.getStockActual() >= p.getStockMaximo()) {
            return "EXCESO";
        }
        return "NORMAL";
    }

    public List<AlertaStock> alertas() {
        return productoRepository.findStockBajo().stream()
                .filter(Producto::isActivo)
                .map(p -> new AlertaStock(
                        p.getId(),
                        p.getNombre(),
                        p.getCategoria() == null ? null : p.getCategoria().getNombre(),
                        p.getStockActual(),
                        p.getStockMinimo(),
                        p.getStockActual() == 0 ? "AGOTADO" : "BAJO",
                        Math.max(p.getStockMaximo() - p.getStockActual(), 0)))
                .toList();
    }

    private void aplicarDatos(
            Producto producto,
            String nombre,
            Long categoriaId,
            Long proveedorId,
            Integer stockMinimo,
            Integer stockMaximo,
            Double precio,
            String codigo,
            String descripcion) {
        if (stockMaximo < stockMinimo) {
            throw new IllegalArgumentException("El stock máximo no puede ser menor que el mínimo");
        }
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new IllegalArgumentException("La categoría no existe"));
        Proveedor proveedor = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no existe"));

        producto.setNombre(nombre.trim());
        producto.setCategoria(categoria);
        producto.setProveedor(proveedor);
        producto.setStockMinimo(stockMinimo);
        producto.setStockMaximo(stockMaximo);
        producto.setPrecio(precio);
        // codigo y descripcion son opcionales: si el formulario no los envia,
        // se conservan los valores que ya tenia el producto.
        if (codigo != null) {
            String codigoLimpio = codigo.trim();
            if (codigoLimpio.isEmpty()) {
                producto.setCodigo(null);
            } else {
                productoRepository.findByCodigo(codigoLimpio)
                        .filter(otro -> !otro.getId().equals(producto.getId()))
                        .ifPresent(otro -> {
                            throw new IllegalArgumentException("Ya existe un producto con ese código");
                        });
                producto.setCodigo(codigoLimpio);
            }
        }
        if (descripcion != null) {
            String descripcionLimpia = descripcion.trim();
            producto.setDescripcion(descripcionLimpia.isEmpty() ? null : descripcionLimpia);
        }
    }
}
