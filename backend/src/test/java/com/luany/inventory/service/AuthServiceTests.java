package com.luany.inventory.service;

import com.luany.inventory.model.CrearEmpleadoRequest;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.model.UsuarioCreadoResponse;
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
}
