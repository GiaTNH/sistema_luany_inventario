
package com.luany.inventory.service;

import com.luany.inventory.model.CrearEmpleadoRequest;
import com.luany.inventory.model.CambioPasswordRequest;
import com.luany.inventory.model.LoginRequest;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.model.UsuarioCreadoResponse;
import com.luany.inventory.model.UsuarioResponse;
import com.luany.inventory.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
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
        authService = new AuthService(usuarioRepository, passwordEncoder, correoService, jdbcTemplate);
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

        UsuarioCreadoResponse respuesta = authService.crearEmpleado(request);

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
        assertEquals(guardado.getNombreUsuario(), respuesta.nombreUsuario());
        assertEquals(guardado.getNombreUsuario(), usernameCaptor.getValue());
        assertEquals("EMPLEADO", guardado.getRol());
        assertTrue(guardado.isDebeCambiarPassword());
        assertNotEquals(passwordCaptor.getValue(), guardado.getPassword());
        assertTrue(passwordEncoder.matches(passwordCaptor.getValue(), guardado.getPassword()));
        assertEquals(10L, respuesta.usuario().id());
        assertEquals("EMPLEADO", respuesta.usuario().rol());
        assertTrue(respuesta.usuario().debeCambiarPassword());
        assertEquals(2, respuesta.getClass().getRecordComponents().length);
    }

    @Test
    void crearEmpleadoFallaSiNoPuedeEnviarCorreo() {
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.empty());
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new CorreoEnvioException()).when(correoService)
                .enviarBienvenida(eq("empleado@luany.com"), eq("Ana"), anyString(), anyString());

        CrearEmpleadoRequest request = new CrearEmpleadoRequest();
        request.setNombre("Ana");
        request.setEmail("empleado@luany.com");

        assertThrows(CorreoEnvioException.class, () -> authService.crearEmpleado(request));
        verify(usuarioRepository).saveAndFlush(any(Usuario.class));
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
}
