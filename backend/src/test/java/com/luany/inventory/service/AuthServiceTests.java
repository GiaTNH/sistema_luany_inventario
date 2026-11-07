
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

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(usuarioRepository, passwordEncoder);
    }

    @Test
    void crearEmpleadoGeneraPasswordHashYRolEsperados() {
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario usuario = invocation.getArgument(0);
            usuario.setId(10L);
            return usuario;
        });

        CrearEmpleadoRequest request = new CrearEmpleadoRequest();
        request.setNombre("  Ana López  ");
        request.setEmail(" Empleado@Luany.com ");

        UsuarioCreadoResponse respuesta = authService.crearEmpleado(request);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();

        assertEquals("Ana López", guardado.getNombre());
        assertEquals("empleado@luany.com", guardado.getEmail());
        assertEquals("EMPLEADO", guardado.getRol());
        assertTrue(guardado.isDebeCambiarPassword());
        assertNotEquals(respuesta.passwordTemporal(), guardado.getPassword());
        assertTrue(passwordEncoder.matches(respuesta.passwordTemporal(), guardado.getPassword()));
        assertEquals(10L, respuesta.usuario().id());
        assertEquals("EMPLEADO", respuesta.usuario().rol());
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

        assertThrows(IllegalArgumentException.class,
                () -> authService.cambiarPassword("empleado@luany.com", request));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambioPasswordRechazaUsuarioSinCambioPendiente() {
        Usuario usuario = usuarioConPasswordTemporal("temporal-segura");
        usuario.setDebeCambiarPassword(false);
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.of(usuario));

        CambioPasswordRequest request = new CambioPasswordRequest();
        request.setNuevaPassword("NuevaClaveSegura2026!");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.cambiarPassword("empleado@luany.com", request));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambioPasswordRechazaNuevaPasswordSobreLimiteUtf8DeBcrypt() {
        Usuario usuario = usuarioConPasswordTemporal("temporal-segura");
        when(usuarioRepository.buscarPorEmail("empleado@luany.com")).thenReturn(Optional.of(usuario));

        CambioPasswordRequest request = new CambioPasswordRequest();
        request.setNuevaPassword("á".repeat(37));

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

    private Usuario usuarioConPasswordTemporal(String passwordTemporal) {
        Usuario usuario = new Usuario();
        usuario.setId(5L);
        usuario.setNombre("Empleado");
        usuario.setEmail("empleado@luany.com");
        usuario.setRol("EMPLEADO");
        usuario.setPassword(passwordEncoder.encode(passwordTemporal));
        usuario.setDebeCambiarPassword(true);
        return usuario;
    }
}
