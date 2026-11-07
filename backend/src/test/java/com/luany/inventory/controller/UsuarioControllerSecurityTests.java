package com.luany.inventory.controller;

import com.luany.inventory.model.UsuarioCreadoResponse;
import com.luany.inventory.model.UsuarioResponse;
import com.luany.inventory.service.AuthService;
import com.luany.inventory.model.Producto;
import com.luany.inventory.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, UsuarioController.class, ProductoController.class})
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class})
class UsuarioControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ProductoService productoService;

    @Test
    void endpointCsrfEntregaTokenDeSesionAceptadoPorElLogin() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String cuerpoCsrf = resultado.getResponse().getContentAsString();
        String header = extraer(cuerpoCsrf, "headerName");
        String token = extraer(cuerpoCsrf, "token");
        MockHttpSession session = (MockHttpSession) resultado.getRequest().getSession(false);
        when(authService.iniciarSesion(any())).thenReturn(
                new UsuarioResponse(1L, "Usuario de prueba", "prueba@luany.com", "ADMIN"));

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(header, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"prueba@luany.com\",\"password\":\"secreta\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioAnonimoNoPuedeCrearEmpleado() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@luany.com\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void usuarioAnonimoNoPuedeAccederALasApiDeInventario() throws Exception {
        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productoService);
    }

    @Test
    void empleadoAutenticadoPuedeConsultarApiDeInventario() throws Exception {
        MockHttpSession session = iniciarSesion("EMPLEADO");
        when(productoService.listar()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/productos").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(productoService).listar();
    }

    @Test
    void administradorAutenticadoPuedeConsultarApiDeInventario() throws Exception {
        MockHttpSession session = iniciarSesion("ADMIN");
        when(productoService.listar()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/productos").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(productoService).listar();
    }

    @Test
    void empleadoAutenticadoNoPuedeCrearOtroUsuario() throws Exception {
        MockHttpSession session = iniciarSesion("EMPLEADO");

        mockMvc.perform(post("/api/usuarios")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@luany.com\"}"))
                .andExpect(status().isForbidden());

        verify(authService, never()).crearEmpleado(any());
    }

    @Test
    void administradorPuedeCrearEmpleadoYNoSeDevuelveElHash() throws Exception {
        MockHttpSession session = iniciarSesion("ADMIN");
        when(authService.crearEmpleado(any())).thenReturn(new UsuarioCreadoResponse(
                new UsuarioResponse(12L, "Ana", "ana@luany.com", "EMPLEADO"),
                "temporal-segura"));

        mockMvc.perform(post("/api/usuarios")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@luany.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.rol").value("EMPLEADO"))
                .andExpect(jsonPath("$.passwordTemporal").value("temporal-segura"))
                .andExpect(jsonPath("$.usuario.password").doesNotExist());

        verify(authService).crearEmpleado(any());
    }

    @Test
    void escrituraSinTokenCsrfSeRechazaInclusoParaAdmin() throws Exception {
        MockHttpSession session = iniciarSesion("ADMIN");

        mockMvc.perform(post("/api/usuarios")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@luany.com\"}"))
                .andExpect(status().isForbidden());

        verify(authService, never()).crearEmpleado(any());
    }

    @Test
    void camposObligatoriosSeValidanAntesDeLlamarAlServicio() throws Exception {
        MockHttpSession session = iniciarSesion("ADMIN");

        mockMvc.perform(post("/api/usuarios")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" \",\"email\":\"ana@luany.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(post("/api/usuarios")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(post("/api/usuarios")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"no-es-correo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(authService, never()).crearEmpleado(any());
    }

    @Test
    void correoDuplicadoSeRespondeComoConflicto() throws Exception {
        MockHttpSession session = iniciarSesion("ADMIN");
        when(authService.crearEmpleado(any())).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo"));

        mockMvc.perform(post("/api/usuarios")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@luany.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe un usuario con ese correo"));
    }

    private MockHttpSession iniciarSesion(String rol) throws Exception {
        when(authService.iniciarSesion(any())).thenReturn(
                new UsuarioResponse(1L, "Usuario de prueba", "prueba@luany.com", rol));

        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"prueba@luany.com\",\"password\":\"secreta\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    private String extraer(String json, String campo) {
        Matcher matcher = Pattern.compile("\"" + campo + "\":\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) throw new AssertionError("Falta el campo " + campo + " en la respuesta CSRF");
        return matcher.group(1);
    }
}
