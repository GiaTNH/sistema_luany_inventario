package com.luany.inventory.service;

import com.luany.inventory.model.AlertaStock;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertaStockNotificacionServiceTests {

    @Mock
    private ProductoService productoService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private CorreoService correoService;
    @InjectMocks
    private AlertaStockNotificacionService service;

    @Test
    void notificaSoloLosAdministradoresSeleccionados() {
        Usuario administrador = new Usuario();
        administrador.setId(5L);
        administrador.setNombre("Administradora");
        administrador.setEmail("admin@luany.com");
        when(productoService.alertas()).thenReturn(List.of(
                new AlertaStock(8L, "Biberón", "Bebés", 0, 3, "AGOTADO", 10)));
        when(usuarioRepository.findAllByRolAndEstadoIgnoreCaseOrderByNombreAsc("ADMIN", "ACTIVO"))
                .thenReturn(List.of(administrador));

        int cantidad = service.notificarAdministradores(List.of(5L));

        assertEquals(1, cantidad);
        verify(correoService).enviarAlertaStock(
                List.of(new AlertaStock(8L, "Biberón", "Bebés", 0, 3, "AGOTADO", 10)),
                List.of("admin@luany.com"));
    }

    @Test
    void rechazaUnDestinatarioQueYaNoEsAdministradorActivo() {
        when(productoService.alertas()).thenReturn(List.of(
                new AlertaStock(8L, "Biberón", "Bebés", 0, 3, "AGOTADO", 10)));
        when(usuarioRepository.findAllByRolAndEstadoIgnoreCaseOrderByNombreAsc("ADMIN", "ACTIVO"))
                .thenReturn(List.of());

        assertThrows(ResponseStatusException.class,
                () -> service.notificarAdministradores(List.of(5L)));

        verify(correoService, never()).enviarAlertaStock(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void requiereDestinatariosSeleccionados() {
        assertThrows(ResponseStatusException.class,
                () -> service.notificarAdministradores(List.of()));

        verify(productoService, never()).alertas();
    }
}
