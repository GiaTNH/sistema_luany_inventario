package com.luany.inventory.service;

import com.luany.inventory.model.Proveedor;
import com.luany.inventory.repository.ProveedorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProveedorServiceTests {

    @Mock
    private ProveedorRepository proveedorRepository;

    @InjectMocks
    private ProveedorService proveedorService;

    @Test
    void crearAceptaCorreoValidoYNormalizaEstado() {
        Proveedor request = proveedor(" ventas@luany.com ", " activo ");
        when(proveedorRepository.save(any(Proveedor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Proveedor creado = proveedorService.crear(request);

        assertEquals("ventas@luany.com", creado.getCorreo());
        assertEquals("ACTIVO", creado.getEstado());
    }

    @Test
    void crearPermiteCorreoOpcionalVacio() {
        Proveedor request = proveedor("   ", null);
        when(proveedorRepository.save(any(Proveedor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Proveedor creado = proveedorService.crear(request);

        assertEquals(null, creado.getCorreo());
        assertEquals("ACTIVO", creado.getEstado());
    }

    @Test
    void crearRechazaCorreoMalFormadoSinGuardar() {
        Proveedor request = proveedor("no-es-un-correo", "ACTIVO");

        assertThrows(IllegalArgumentException.class, () -> proveedorService.crear(request));

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    void crearRechazaRucQueNoTieneOnceDigitos() {
        Proveedor request = proveedor(null, "ACTIVO");
        request.setRuc("12345A");

        assertThrows(IllegalArgumentException.class, () -> proveedorService.crear(request));

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    void crearNormalizaElEstadoPermitidoAunqueTengaEspacios() {
        Proveedor request = proveedor(null, "ACTIVO ");
        when(proveedorRepository.save(any(Proveedor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Proveedor creado = proveedorService.crear(request);

        assertEquals("ACTIVO", creado.getEstado());
    }

    @Test
    void crearRechazaEstadoDesconocidoSinGuardar() {
        Proveedor request = proveedor(null, "PAUSADO");

        assertThrows(IllegalArgumentException.class, () -> proveedorService.crear(request));

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    void crearRechazaRucDuplicadoAntesDeGuardar() {
        Proveedor request = proveedor(null, "ACTIVO");
        request.setRuc("20123456789");
        Proveedor existente = new Proveedor();
        existente.setId(1L);
        when(proveedorRepository.findByRuc("20123456789")).thenReturn(Optional.of(existente));

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> proveedorService.crear(request));

        assertEquals("Ya existe un proveedor con ese RUC", error.getMessage());
        verify(proveedorRepository, never()).save(any());
    }

    private Proveedor proveedor(String correo, String estado) {
        Proveedor proveedor = new Proveedor();
        proveedor.setNombre("Proveedor de prueba");
        proveedor.setCorreo(correo);
        proveedor.setEstado(estado);
        return proveedor;
    }
}
