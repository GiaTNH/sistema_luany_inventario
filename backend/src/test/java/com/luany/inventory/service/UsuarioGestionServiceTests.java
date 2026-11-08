package com.luany.inventory.service;

import com.luany.inventory.model.CambiarRolRequest;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioGestionServiceTests {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SessionRegistry sessionRegistry;

    private UsuarioGestionService usuarioGestionService;

    @BeforeEach
    void setUp() {
        usuarioGestionService = new UsuarioGestionService(usuarioRepository, sessionRegistry);
    }

    @Test
    void noPermiteQueElAdministradorSeQuiteSuPropioRol() {
        Usuario admin = usuario(1L, "admin@luany.com", "ADMIN", "ACTIVO");
        when(usuarioRepository.buscarAdministradoresParaActualizar()).thenReturn(List.of(admin));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

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
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

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
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(empleado));
        when(usuarioRepository.saveAndFlush(empleado)).thenReturn(empleado);
        when(sessionRegistry.getAllSessions("empleado@luany.com", false)).thenReturn(List.of());

        usuarioGestionService.cambiarRol(2L, cambioRol("ADMIN"), "admin@luany.com");

        assertEquals("ADMIN", empleado.getRol());
        verify(sessionRegistry).getAllSessions("empleado@luany.com", false);
    }

    private CambiarRolRequest cambioRol(String rol) {
        CambiarRolRequest request = new CambiarRolRequest();
        request.setRol(rol);
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
}
