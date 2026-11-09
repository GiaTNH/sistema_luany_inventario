package com.luany.inventory.service;

import com.luany.inventory.model.Cliente;
import com.luany.inventory.model.ClienteRequest;
import com.luany.inventory.repository.ClienteRepository;
import com.luany.inventory.repository.NotaVentaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTests {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private NotaVentaRepository notaVentaRepository;

    @InjectMocks
    private ClienteService clienteService;

    @Test
    void listaOrdenadosPorNombreCuandoNoHayBusqueda() {
        List<Cliente> clientes = List.of(cliente(1L, "Ana"));
        when(clienteRepository.findAll(any(Sort.class))).thenReturn(clientes);

        assertEquals(clientes, clienteService.listar("  "));
        verify(clienteRepository).findAll(any(Sort.class));
    }

    @Test
    void buscaPorNombreODocumentoConTerminoNormalizado() {
        List<Cliente> clientes = List.of(cliente(1L, "Ana"));
        when(clienteRepository.findByNombreContainingIgnoreCaseOrNumeroDocumentoContainingIgnoreCase(
                any(), any(), any(Sort.class))).thenReturn(clientes);

        assertEquals(clientes, clienteService.listar("  ana  "));
        verify(clienteRepository).findByNombreContainingIgnoreCaseOrNumeroDocumentoContainingIgnoreCase(
                "ana", "ana", Sort.by(Sort.Order.asc("nombre").ignoreCase()));
    }

    @Test
    void creaClienteNormalizandoLosCamposYVerificandoDocumentoUnico() {
        ClienteRequest request = request("  Ana López  ", " dni ", " 12345 ");
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cliente creado = clienteService.crear(request);

        assertEquals("Ana López", creado.getNombre());
        assertEquals("DNI", creado.getTipoDocumento());
        assertEquals("12345", creado.getNumeroDocumento());
        assertEquals(null, creado.getTelefono());
        assertEquals(null, creado.getDireccion());
        verify(clienteRepository).findByNumeroDocumentoIgnoreCase("12345");
    }

    @Test
    void rechazaDocumentoDuplicadoAlCrear() {
        when(clienteRepository.findByNumeroDocumentoIgnoreCase("12345"))
                .thenReturn(Optional.of(cliente(1L, "Existente")));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> clienteService.crear(request("Nuevo", "DNI", "12345")));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    void actualizaUnClienteSinConfundirSuPropioDocumentoConDuplicado() {
        Cliente existente = cliente(1L, "Ana");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(clienteRepository.findByNumeroDocumentoIgnoreCase("12345")).thenReturn(Optional.of(existente));
        when(clienteRepository.save(existente)).thenReturn(existente);

        Cliente actualizado = clienteService.actualizar(1L, request("Ana López", "RUC", "12345"));

        assertEquals("Ana López", actualizado.getNombre());
        assertEquals("RUC", actualizado.getTipoDocumento());
        assertEquals("12345", actualizado.getNumeroDocumento());
        verify(clienteRepository).save(existente);
    }

    @Test
    void respondeNoEncontradoAlActualizarClienteInexistente() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> clienteService.actualizar(99L, request("Ana", "DNI", "12345")));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(notaVentaRepository);
    }

    @Test
    void impideEliminarClienteConNotasDeVenta() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente(1L, "Ana")));
        when(notaVentaRepository.existsByClienteId(1L)).thenReturn(true);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> clienteService.eliminar(1L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(clienteRepository, never()).delete(any(Cliente.class));
    }

    @Test
    void eliminaClienteSinNotasRelacionadas() {
        Cliente cliente = cliente(1L, "Ana");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(notaVentaRepository.existsByClienteId(1L)).thenReturn(false);

        clienteService.eliminar(1L);

        verify(clienteRepository).delete(cliente);
    }

    private ClienteRequest request(String nombre, String tipoDocumento, String numeroDocumento) {
        ClienteRequest request = new ClienteRequest();
        request.setNombre(nombre);
        request.setTipoDocumento(tipoDocumento);
        request.setNumeroDocumento(numeroDocumento);
        request.setTelefono(" ");
        request.setDireccion(null);
        return request;
    }

    private Cliente cliente(Long id, String nombre) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setNombre(nombre);
        cliente.setTipoDocumento("DNI");
        cliente.setNumeroDocumento("12345");
        return cliente;
    }
}
