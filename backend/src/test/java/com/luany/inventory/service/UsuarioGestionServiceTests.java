package com.luany.inventory.service;

import com.luany.inventory.model.CambiarRolRequest;
import com.luany.inventory.model.ActualizarUsuarioRequest;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.model.UsuarioGestionResponse;
import com.luany.inventory.model.ResultadoNotificacion;
import com.luany.inventory.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class UsuarioGestionServiceTests {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private CorreoService correoService;

    private UsuarioGestionService usuarioGestionService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        usuarioGestionService = new UsuarioGestionService(
                usuarioRepository, sessionRegistry, correoService, new NotificacionTrasCommit());
    }

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void noPermiteQueElAdministradorSeQuiteSuPropioRol() {
        Usuario admin = usuario(1L, "admin@luany.com", "ADMIN", "ACTIVO");
        when(usuarioRepository.buscarAdministradoresParaActualizar()).thenReturn(List.of(admin));
        when(usuarioRepository.buscarPorIdParaActualizar(1L)).thenReturn(Optional.of(admin));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> usuarioGestionService.cambiarRol(1L, cambioRol("EMPLEADO"), "admin@luany.com"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void noPermiteQuitarElUltimoRolAdministrador() {
        Usuario admin = usuario(1L, "admin@luany.com", "ADMIN", "ACTIVO");
        when(usuarioRepository.buscarAdministradoresParaActualizar()).thenReturn(List.of(admin));
        when(usuarioRepository.buscarPorIdParaActualizar(1L)).thenReturn(Optional.of(admin));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> usuarioGestionService.cambiarRol(1L, cambioRol("EMPLEADO"), "otro-admin@luany.com"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRolInvalidaLasSesionesExistentesDelUsuario() {
        Usuario admin = usuario(1L, "admin@luany.com", "ADMIN", "ACTIVO");
        Usuario empleado = usuario(2L, "empleado@luany.com", "EMPLEADO", "ACTIVO");
        when(usuarioRepository.buscarAdministradoresParaActualizar()).thenReturn(List.of(admin));
        when(usuarioRepository.buscarPorIdParaActualizar(2L)).thenReturn(Optional.of(empleado));
        when(usuarioRepository.saveAndFlush(empleado)).thenReturn(empleado);
        when(sessionRegistry.getAllSessions("empleado@luany.com", false)).thenReturn(List.of());

        usuarioGestionService.cambiarRol(2L, cambioRol("ADMIN"), "admin@luany.com");

        assertEquals("ADMIN", empleado.getRol());
        verify(sessionRegistry).getAllSessions("empleado@luany.com", false);
    }

    @Test
    void actualizarDatosYDesactivarCuentaExpiraLasSesiones() {
        Usuario admin = usuario(1L, "admin@luany.com", "ADMIN", "ACTIVO");
        Usuario empleado = usuario(2L, "empleado@luany.com", "EMPLEADO", "ACTIVO");
        String passwordHashOriginal = empleado.getPassword();
        when(usuarioRepository.buscarAdministradoresParaActualizar()).thenReturn(List.of(admin));
        when(usuarioRepository.buscarPorIdParaActualizar(2L)).thenReturn(Optional.of(empleado));
        when(usuarioRepository.buscarPorEmail("nuevo@luany.com")).thenReturn(Optional.empty());
        when(usuarioRepository.saveAndFlush(empleado)).thenReturn(empleado);
        when(sessionRegistry.getAllSessions("empleado@luany.com", false)).thenReturn(List.of());

        var resultado = usuarioGestionService.actualizar(
                2L, actualizacion("Ana María", "Pérez", "nuevo@luany.com", "EMPLEADO", "INACTIVO"),
                "admin@luany.com");

        assertEquals("Ana María", empleado.getNombre());
        assertEquals("Pérez", empleado.getApellido());
        assertEquals("nuevo@luany.com", empleado.getEmail());
        assertEquals("INACTIVO", empleado.getEstado());
        assertEquals(passwordHashOriginal, empleado.getPassword());
        assertEquals("INACTIVO", resultado.resultado().estado());
        assertFalse(resultado.resultado().debeCambiarPassword());
        assertTrue(resultado.notificacionRequerida());
        assertFalse(resultado.notificacionAceptada());
        verifyNoInteractions(correoService);
        verify(sessionRegistry).getAllSessions("empleado@luany.com", false);
        ejecutarDespuesDelCommit();
        verify(correoService).enviarNotificacionCambioCorreo("nuevo@luany.com", "Ana María");
        assertTrue(resultado.notificacionAceptada());
    }

    @Test
    void falloDeNotificacionNoRevierteElCambioDeCorreo() {
        Usuario admin = usuario(1L, "admin@luany.com", "ADMIN", "ACTIVO");
        Usuario empleado = usuario(2L, "empleado@luany.com", "EMPLEADO", "ACTIVO");
        when(usuarioRepository.buscarAdministradoresParaActualizar()).thenReturn(List.of(admin));
        when(usuarioRepository.buscarPorIdParaActualizar(2L)).thenReturn(Optional.of(empleado));
        when(usuarioRepository.buscarPorEmail("nuevo@luany.com")).thenReturn(Optional.empty());
        when(usuarioRepository.saveAndFlush(empleado)).thenReturn(empleado);
        when(sessionRegistry.getAllSessions("empleado@luany.com", false)).thenReturn(List.of());
        doThrow(new CorreoNotificacionException()).when(correoService)
                .enviarNotificacionCambioCorreo("nuevo@luany.com", "Ana");

        var resultado = usuarioGestionService.actualizar(
                2L, actualizacion("Ana", null, "nuevo@luany.com", "EMPLEADO", "ACTIVO"),
                "admin@luany.com");
        ejecutarDespuesDelCommit();

        assertEquals("nuevo@luany.com", empleado.getEmail());
        assertTrue(resultado.notificacionRequerida());
        assertFalse(resultado.notificacionAceptada());
    }

    @Test
    void listarIncluyeElIndicadorDeCambioObligatorioDePassword() {
        Usuario pendiente = usuario(2L, "pendiente@luany.com", "EMPLEADO", "ACTIVO");
        pendiente.setDebeCambiarPassword(true);
        when(usuarioRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(pendiente));

        List<UsuarioGestionResponse> resultado = usuarioGestionService.listar();

        assertEquals(1, resultado.size());
        assertTrue(resultado.get(0).debeCambiarPassword());
        assertEquals("ACTIVO", resultado.get(0).estado());
    }

    @Test
    void noPermiteDesactivarAlUltimoAdministradorActivo() {
        Usuario admin = usuario(1L, "admin@luany.com", "ADMIN", "ACTIVO");
        when(usuarioRepository.buscarAdministradoresParaActualizar()).thenReturn(List.of(admin));
        when(usuarioRepository.buscarPorIdParaActualizar(1L)).thenReturn(Optional.of(admin));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> usuarioGestionService.actualizar(
                        1L, actualizacion("Admin", null, "admin@luany.com", "ADMIN", "INACTIVO"),
                        "admin@luany.com"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(usuarioRepository, never()).saveAndFlush(any());
        assertTrue(admin.getEstado().equals("ACTIVO"));
    }

    @Test
    void noPermiteAlAdministradorDesactivarseAunqueExistaOtroAdministradorActivo() {
        Usuario adminActual = usuario(1L, "admin@luany.com", "ADMIN", "ACTIVO");
        Usuario otroAdmin = usuario(2L, "otro-admin@luany.com", "ADMIN", "ACTIVO");
        when(usuarioRepository.buscarAdministradoresParaActualizar())
                .thenReturn(List.of(adminActual, otroAdmin));
        when(usuarioRepository.buscarPorIdParaActualizar(1L)).thenReturn(Optional.of(adminActual));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> usuarioGestionService.actualizar(
                        1L, actualizacion("Admin", null, "admin@luany.com", "ADMIN", "INACTIVO"),
                        "admin@luany.com"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(usuarioRepository, never()).saveAndFlush(any());
        assertEquals("ACTIVO", adminActual.getEstado());
    }

    @Test
    void noPermiteCambiarElRolPropioDuranteLaEdicion() {
        Usuario admin = usuario(1L, "admin@luany.com", "ADMIN", "ACTIVO");
        when(usuarioRepository.buscarAdministradoresParaActualizar()).thenReturn(List.of(admin));
        when(usuarioRepository.buscarPorIdParaActualizar(1L)).thenReturn(Optional.of(admin));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> usuarioGestionService.actualizar(
                        1L, actualizacion("Admin", null, "admin@luany.com", "EMPLEADO", "ACTIVO"),
                        "admin@luany.com"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    private CambiarRolRequest cambioRol(String rol) {
        CambiarRolRequest request = new CambiarRolRequest();
        request.setRol(rol);
        return request;
    }

    private ActualizarUsuarioRequest actualizacion(
            String nombre, String apellido, String email, String rol, String estado) {
        ActualizarUsuarioRequest request = new ActualizarUsuarioRequest();
        request.setNombre(nombre);
        request.setApellido(apellido);
        request.setEmail(email);
        request.setRol(rol);
        request.setEstado(estado);
        return request;
    }

    private Usuario usuario(Long id, String email, String rol, String estado) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Usuario");
        usuario.setEmail(email);
        usuario.setNombreUsuario("usuario" + id);
        usuario.setPassword("hash");
        usuario.setRol(rol);
        usuario.setEstado(estado);
        return usuario;
    }

    private void ejecutarDespuesDelCommit() {
        for (TransactionSynchronization sincronizacion
                : TransactionSynchronizationManager.getSynchronizations()) {
            sincronizacion.afterCommit();
        }
    }
}
