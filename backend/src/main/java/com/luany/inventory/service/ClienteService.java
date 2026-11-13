package com.luany.inventory.service;

import com.luany.inventory.model.Cliente;
import com.luany.inventory.model.ClienteRequest;
import com.luany.inventory.repository.ClienteRepository;
import com.luany.inventory.repository.NotaVentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private static final String DOCUMENTO_CLIENTE_GENERAL = "CLIENTE-GENERAL";
    private static final String NOMBRE_CLIENTE_GENERAL = "Cliente general";
    private static final Sort ORDEN_NOMBRE = Sort.by(Sort.Order.asc("nombre").ignoreCase());

    private final ClienteRepository clienteRepository;
    private final NotaVentaRepository notaVentaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<Cliente> listar(String busqueda) {
        if (busqueda == null || busqueda.isBlank()) {
            return clienteRepository.findAll(ORDEN_NOMBRE);
        }
        String termino = busqueda.trim();
        return clienteRepository.findByNombreContainingIgnoreCaseOrNumeroDocumentoContainingIgnoreCase(
                termino, termino, ORDEN_NOMBRE);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cliente obtenerOCrearGeneral() {
        jdbcTemplate.update("""
                INSERT INTO clientes (nombre, tipo_documento, numero_documento)
                VALUES (?, 'OTRO', ?)
                ON DUPLICATE KEY UPDATE numero_documento = VALUES(numero_documento)
                """, NOMBRE_CLIENTE_GENERAL, DOCUMENTO_CLIENTE_GENERAL);
        return clienteRepository.findByNumeroDocumentoIgnoreCase(DOCUMENTO_CLIENTE_GENERAL)
                .map(this::validarClienteGeneral)
                .orElseThrow(() -> new IllegalStateException("No se pudo resolver el cliente general"));
    }

    @Transactional
    public Cliente crear(ClienteRequest request) {
        String numeroDocumento = limpiar(request.getNumeroDocumento());
        validarDocumentoDisponible(numeroDocumento, null);
        Cliente cliente = new Cliente();
        aplicarDatos(cliente, request, numeroDocumento);
        return clienteRepository.save(cliente);
    }

    @Transactional
    public Cliente actualizar(Long id, ClienteRequest request) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El cliente no existe"));
        String numeroDocumento = limpiar(request.getNumeroDocumento());
        validarDocumentoDisponible(numeroDocumento, id);
        aplicarDatos(cliente, request, numeroDocumento);
        return clienteRepository.save(cliente);
    }

    @Transactional
    public void eliminar(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El cliente no existe"));
        if (notaVentaRepository.existsByClienteId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "No se puede eliminar el cliente porque tiene notas de venta relacionadas");
        }
        clienteRepository.delete(cliente);
    }

    private void validarDocumentoDisponible(String numeroDocumento, Long idActual) {
        clienteRepository.findByNumeroDocumentoIgnoreCase(numeroDocumento)
                .filter(cliente -> idActual == null || !cliente.getId().equals(idActual))
                .ifPresent(cliente -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Ya existe un cliente con ese número de documento");
                });
    }

    private void aplicarDatos(Cliente cliente, ClienteRequest request, String numeroDocumento) {
        cliente.setNombre(limpiar(request.getNombre()));
        cliente.setTipoDocumento(limpiar(request.getTipoDocumento()).toUpperCase(Locale.ROOT));
        cliente.setNumeroDocumento(numeroDocumento);
        cliente.setTelefono(limpiarOpcional(request.getTelefono()));
        cliente.setDireccion(limpiarOpcional(request.getDireccion()));
    }

    private Cliente validarClienteGeneral(Cliente cliente) {
        if (!NOMBRE_CLIENTE_GENERAL.equals(cliente.getNombre()) || !"OTRO".equals(cliente.getTipoDocumento())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "El identificador reservado para Cliente general ya está en uso");
        }
        return cliente;
    }

    private String limpiar(String valor) {
        return valor.trim();
    }

    private String limpiarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}
