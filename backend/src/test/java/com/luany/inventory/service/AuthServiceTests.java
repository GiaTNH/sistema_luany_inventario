
package com.luany.inventory.service;

import com.luany.inventory.model.CrearEmpleadoRequest;
import com.luany.inventory.model.CambioPasswordRequest;
import com.luany.inventory.model.LoginRequest;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.model.UsuarioResponse;
import com.luany.inventory.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CorreoService correoService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        TransactionSynchronizationManager.initSynchronization();
        authService = new AuthService(
                usuarioRepository, passwordEncoder, correoService, jdbcTemplate, new NotificacionTrasCommit());
    }

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void crearEmpleadoGeneraPasswordHashYRolEsperados() {
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.empty());
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(10L);
            return usuario;
        });

        CrearEmpleadoRequest request = new CrearEmpleadoRequest();
        request.setNombre("  Ana López  ");
        request.setEmail(" Empleado@Luany.com ");

        var resultado = authService.crearEmpleado(request);
        assertFalse(resultado.notificacionAceptada());
        verifyNoInteractions(correoService);
        ejecutarDespuesDelCommit();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(captor.capture());
        Usuario guardado = captor.getValue();
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        verify(correoService).enviarBienvenida(
                eq("empleado@luany.com"), eq("Ana López"),
                usernameCaptor.capture(), passwordCaptor.capture());

        assertEquals("Ana López", guardado.getNombre());
        assertEquals("empleado@luany.com", guardado.getEmail());
        assertEquals("ana.lopez", guardado.getNombreUsuario());
        assertEquals(guardado.getNombreUsuario(), resultado.resultado().nombreUsuario());
        assertEquals(guardado.getNombreUsuario(), usernameCaptor.getValue());
        assertEquals("EMPLEADO", guardado.getRol());
        assertEquals(16, passwordCaptor.getValue().length());
        assertTrue(guardado.isDebeCambiarPassword());
        assertNotEquals(passwordCaptor.getValue(), guardado.getPassword());
        assertTrue(passwordEncoder.matches(passwordCaptor.getValue(), guardado.getPassword()));
        assertEquals(10L, resultado.resultado().usuario().id());
        assertEquals("EMPLEADO", resultado.resultado().usuario().rol());
        assertTrue(resultado.resultado().usuario().debeCambiarPassword());
        assertTrue(resultado.notificacionAceptada());
        assertTrue(resultado.notificacionRequerida());
    }

    @Test
    void crearEmpleadoAceptaRolAdministradorSinCambiarLaReglaDeCambioInicial() {
        when(usuarioRepository.buscarPorEmail("admin-nuevo@luany.com")).thenReturn(Optional.empty());
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CrearEmpleadoRequest request = new CrearEmpleadoRequest();
        request.setNombre("Admin nuevo");
        request.setEmail("admin-nuevo@luany.com");
        request.setRol("ADMIN");

        var resultado = authService.crearEmpleado(request);
        ejecutarDespuesDelCommit();

        assertEquals("ADMIN", resultado.resultado().usuario().rol());
        assertTrue(resultado.resultado().usuario().debeCambiarPassword());
    }

    @Test
    void crearEmpleadoConservaLaCuentaSiFallaLaNotificacionDespuesDelCommit() {
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.empty());
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new CorreoEnvioException()).when(correoService)
                .enviarBienvenida(eq("empleado@luany.com"), eq("Ana"), anyString(), anyString());

        CrearEmpleadoRequest request = new CrearEmpleadoRequest();
        request.setNombre("Ana");
        request.setEmail("empleado@luany.com");

        var resultado = authService.crearEmpleado(request);
        verify(usuarioRepository).saveAndFlush(any(Usuario.class));
        verifyNoInteractions(correoService);
        ejecutarDespuesDelCommit();

        assertTrue(resultado.notificacionRequerida());
        assertFalse(resultado.notificacionAceptada());
        assertEquals("empleado@luany.com", resultado.resultado().usuario().email());
        assertFalse(resultado.resultado().toString().contains("passwordTemporal"));
    }

    @Test
    void crearEmpleadoRechazaCorreoExistente() {
        when(usuarioRepository.buscarPorEmail("empleado@luany.com"))
                .thenReturn(Optional.of(new Usuario()));

        CrearEmpleadoRequest request = new CrearEmpleadoRequest();
        request.setNombre("Ana López");
        request.setEmail("empleado@luany.com");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.crearEmpleado(request));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambioPasswordGuardaHashYActualizaLaBandera() {
        Usuario usuario = usuarioConPasswordTemporal("temporal-segura");
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CambioPasswordRequest request = new CambioPasswordRequest();
        request.setNuevaPassword("NuevaClaveSegura2026!");
        request.setConfirmarPassword("NuevaClaveSegura2026!");

        UsuarioResponse respuesta = authService.cambiarPassword("empleado@luany.com", request);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();

        assertTrue(passwordEncoder.matches(request.getNuevaPassword(), guardado.getPassword()));
        assertNotEquals(request.getNuevaPassword(), guardado.getPassword());
        assertFalse(guardado.isDebeCambiarPassword());
        assertFalse(respuesta.debeCambiarPassword());
        assertEquals("EMPLEADO", respuesta.rol());
    }

    @Test
    void cambioPasswordNoPermiteReutilizarLaPasswordActual() {
        String passwordActual = "UnaTemporalSegura2026!";
        Usuario usuario = usuarioConPasswordTemporal(passwordActual);
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.of(usuario));

        CambioPasswordRequest request = new CambioPasswordRequest();
        request.setNuevaPassword(passwordActual);
        request.setConfirmarPassword(passwordActual);

        assertThrows(IllegalArgumentException.class,
                () -> authService.cambiarPassword("empleado@luany.com", request));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void usuarioAutenticadoPuedeActualizarPasswordConSuPasswordActual() {
        String passwordActual = "ContrasenaActual2026!";
        Usuario usuario = usuarioConPasswordTemporal(passwordActual);
        usuario.setDebeCambiarPassword(false);
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CambioPasswordRequest request = new CambioPasswordRequest();
        request.setPasswordActual(passwordActual);
        request.setNuevaPassword("NuevaClaveSegura2026!");
        request.setConfirmarPassword("NuevaClaveSegura2026!");

        UsuarioResponse respuesta = authService.cambiarPassword("empleado@luany.com", request);

        assertTrue(passwordEncoder.matches("NuevaClaveSegura2026!", usuario.getPassword()));
        assertFalse(respuesta.debeCambiarPassword());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void usuarioAutenticadoNoPuedeActualizarPasswordConPasswordActualIncorrecta() {
        Usuario usuario = usuarioConPasswordTemporal("ContrasenaActual2026!");
        usuario.setDebeCambiarPassword(false);
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.of(usuario));

        CambioPasswordRequest request = new CambioPasswordRequest();
        request.setPasswordActual("ClaveIncorrecta2026!");
        request.setNuevaPassword("NuevaClaveSegura2026!");
        request.setConfirmarPassword("NuevaClaveSegura2026!");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> authService.cambiarPassword("empleado@luany.com", request));

        assertEquals("La contraseña actual es incorrecta", error.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void usuarioAutenticadoNoPuedeReutilizarSuPasswordActual() {
        String passwordActual = "ContrasenaActual2026!";
        Usuario usuario = usuarioConPasswordTemporal(passwordActual);
        usuario.setDebeCambiarPassword(false);
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.of(usuario));

        CambioPasswordRequest request = new CambioPasswordRequest();
        request.setPasswordActual(passwordActual);
        request.setNuevaPassword(passwordActual);
        request.setConfirmarPassword(passwordActual);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> authService.cambiarPassword("empleado@luany.com", request));

        assertEquals("La nueva contraseña debe ser diferente a la actual", error.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambioPasswordRechazaConfirmacionDistinta() {
        Usuario usuario = usuarioConPasswordTemporal("temporal-segura");
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.of(usuario));

        CambioPasswordRequest request = new CambioPasswordRequest();
        request.setNuevaPassword("NuevaClaveSegura2026!");
        request.setConfirmarPassword("OtraClaveSegura2026!");

        assertThrows(IllegalArgumentException.class,
                () -> authService.cambiarPassword("empleado@luany.com", request));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambioPasswordRechazaNuevaPasswordSobreLimiteUtf8DeBcrypt() {
        Usuario usuario = usuarioConPasswordTemporal("temporal-segura");
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.of(usuario));

        CambioPasswordRequest request = new CambioPasswordRequest();
        request.setNuevaPassword("á".repeat(37));
        request.setConfirmarPassword("á".repeat(37));

        assertThrows(IllegalArgumentException.class,
                () -> authService.cambiarPassword("empleado@luany.com", request));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void loginYConsultaDeSesionDevuelvenEstadoPersistido() {
        Usuario usuario = usuarioConPasswordTemporal("temporal-segura");
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.of(usuario));

        LoginRequest login = new LoginRequest();
        login.setEmail("empleado@luany.com");
        login.setPassword("temporal-segura");

        assertTrue(authService.iniciarSesion(login).debeCambiarPassword());
        assertTrue(authService.obtenerUsuarioAutenticado("empleado@luany.com").debeCambiarPassword());
    }

    @Test
    void usuarioInactivoNoPuedeIniciarSesionAunqueLaPasswordSeaCorrecta() {
        Usuario usuario = usuarioConPasswordTemporal("temporal-segura");
        usuario.setEstado("INACTIVO");
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.of(usuario));

        LoginRequest login = new LoginRequest();
        login.setEmail("empleado@luany.com");
        login.setPassword("temporal-segura");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> authService.iniciarSesion(login));

        assertEquals("Tu usuario está inactivo. Contacta al administrador", error.getMessage());
    }

    private Usuario usuarioConPasswordTemporal(String passwordTemporal) {
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        usuario.setNombre("Empleado");
        usuario.setEmail("empleado@luany.com");
        usuario.setNombreUsuario("empleado");
        usuario.setRol("EMPLEADO");
        usuario.setPassword(passwordEncoder.encode(passwordTemporal));
        usuario.setDebeCambiarPassword(true);
        return usuario;
    }

    private void ejecutarDespuesDelCommit() {
        for (TransactionSynchronization sincronizacion
                : TransactionSynchronizationManager.getSynchronizations()) {
            sincronizacion.afterCommit();
        }
    }
}
