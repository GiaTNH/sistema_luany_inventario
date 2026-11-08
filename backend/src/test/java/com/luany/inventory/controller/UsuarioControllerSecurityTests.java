package com.luany.inventory.controller;

import com.luany.inventory.model.UsuarioCreadoResponse;
import com.luany.inventory.model.RegistroInicialRequest;
import com.luany.inventory.model.UsuarioResponse;
import com.luany.inventory.model.Producto;
import com.luany.inventory.service.ProductoService;
import com.luany.inventory.service.AuthService;
import com.luany.inventory.service.CorreoEnvioException;
import com.luany.inventory.service.RecuperacionPasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, UsuarioController.class, ProductoController.class})
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class})
class UsuarioControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRegistry sessionRegistry;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RecuperacionPasswordService recuperacionPasswordService;

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
                usuario("ADMIN", false));

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(header, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"prueba@luany.com\",\"password\":\"secreta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarPassword").value(false));
        org.junit.jupiter.api.Assertions.assertNotNull(
                sessionRegistry.getSessionInformation(session.getId()));
    }

    @Test
    void estadoDeRegistroInicialEsPublico() throws Exception {
        when(authService.registroInicialDisponible()).thenReturn(true);

        mockMvc.perform(get("/api/auth/registro-inicial/disponible"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true));

        verify(authService).registroInicialDisponible();
    }

    @Test
    void solicitudesDeRecuperacionSonPublicasPeroRequierenCsrf() throws Exception {
        when(recuperacionPasswordService.solicitarRecuperacion(any()))
                .thenReturn("Enviamos un enlace para restablecer tu contraseña al correo registrado.");
        when(recuperacionPasswordService.restablecerPassword(any()))
                .thenReturn("Tu contraseña se actualizó correctamente. Ya puedes iniciar sesión.");

        mockMvc.perform(post("/api/auth/solicitar-recuperacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@luany.com\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(recuperacionPasswordService);

        mockMvc.perform(post("/api/auth/solicitar-recuperacion")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@luany.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(
                        "Enviamos un enlace para restablecer tu contraseña al correo registrado."))
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(post("/api/auth/restablecer-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-secreto","nuevaPassword":"NuevaClaveSegura2026!",
                                 "confirmarPassword":"NuevaClaveSegura2026!"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/restablecer-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-secreto","nuevaPassword":"NuevaClaveSegura2026!",
                                 "confirmarPassword":"NuevaClaveSegura2026!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(recuperacionPasswordService).solicitarRecuperacion(any());
        verify(recuperacionPasswordService).restablecerPassword(any());
    }

    @Test
    void registroInicialEsPublicoPeroRequiereCsrfYNoAceptaRolDelCliente() throws Exception {
        when(authService.registrarPrimerAdministrador(any())).thenReturn(
                new UsuarioResponse(1L, "Admin", "admin@luany.com", "ADMIN", false));

        mockMvc.perform(post("/api/auth/registro-inicial")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Admin","email":"admin@luany.com",
                                 "password":"ClaveSegura2026!","confirmarPassword":"ClaveSegura2026!",
                                 "rol":"EMPLEADO"}
                                """))
                .andExpect(status().isForbidden());

        verify(authService, never()).registrarPrimerAdministrador(any());

        mockMvc.perform(post("/api/auth/registro-inicial")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Admin","email":"admin@luany.com",
                                 "password":"ClaveSegura2026!","confirmarPassword":"ClaveSegura2026!",
                                 "rol":"EMPLEADO"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.debeCambiarPassword").value(false))
                .andExpect(jsonPath("$.password").doesNotExist());

        ArgumentCaptor<RegistroInicialRequest> requestCaptor =
                ArgumentCaptor.forClass(RegistroInicialRequest.class);
        verify(authService).registrarPrimerAdministrador(requestCaptor.capture());
        assertEquals("admin@luany.com", requestCaptor.getValue().getEmail());
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
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@luany.com\",\"rol\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        verify(authService, never()).crearEmpleado(any());
    }

    @Test
    void empleadoConCambioPendienteNoPuedeConsultarProductos() throws Exception {
        MockHttpSession session = iniciarSesion("EMPLEADO", true);

        mockMvc.perform(get("/api/productos").session(session))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productoService);
    }

    @Test
    void empleadoConCambioPendientePuedeConsultarSuSesion() throws Exception {
        MockHttpSession session = iniciarSesion("EMPLEADO", true);
        when(authService.obtenerUsuarioAutenticado("prueba@luany.com"))
                .thenReturn(usuario("EMPLEADO", true));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("prueba@luany.com"))
                .andExpect(jsonPath("$.debeCambiarPassword").value(true));

        verify(authService).obtenerUsuarioAutenticado("prueba@luany.com");
    }

    @Test
    void usuarioAnonimoNoPuedeConsultarSuSesion() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void empleadoConCambioPendientePuedeCambiarPasswordYUsarLaMismaSesion() throws Exception {
        MockHttpSession session = iniciarSesion("EMPLEADO", true);
        when(authService.cambiarPassword(eq("prueba@luany.com"), any()))
                .thenReturn(usuario("EMPLEADO", false));
        when(productoService.listar()).thenReturn(java.util.List.of());

        mockMvc.perform(post("/api/auth/cambiar-password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaPassword\":\"NuevaClaveSegura2026!\",\"confirmarPassword\":\"NuevaClaveSegura2026!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarPassword").value(false))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/productos").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(authService).cambiarPassword(eq("prueba@luany.com"), any());
        verify(productoService).listar();
    }

    @Test
    void empleadoConCambioPendienteNoPuedeCambiarPasswordSinCsrf() throws Exception {
        MockHttpSession session = iniciarSesion("EMPLEADO", true);

        mockMvc.perform(post("/api/auth/cambiar-password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaPassword\":\"NuevaClaveSegura2026!\",\"confirmarPassword\":\"NuevaClaveSegura2026!\"}"))
                .andExpect(status().isForbidden());

        verify(authService, never()).cambiarPassword(any(), any());
    }

    @Test
    void empleadoSinCambioPendientePuedeUsarEndpointAutenticadoDeCambio() throws Exception {
        MockHttpSession session = iniciarSesion("EMPLEADO");
        when(authService.cambiarPassword(eq("prueba@luany.com"), any()))
                .thenReturn(usuario("EMPLEADO", false));

        mockMvc.perform(post("/api/auth/cambiar-password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"ContrasenaActual2026!\",\"nuevaPassword\":\"NuevaClaveSegura2026!\",\"confirmarPassword\":\"NuevaClaveSegura2026!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarPassword").value(false));

        verify(authService).cambiarPassword(eq("prueba@luany.com"), any());
    }

    @Test
    void nuevaPasswordDebeCumplirLaLongitudMinima() throws Exception {
        MockHttpSession session = iniciarSesion("EMPLEADO", true);

        mockMvc.perform(post("/api/auth/cambiar-password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaPassword\":\"corta\",\"confirmarPassword\":\"corta\"}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).cambiarPassword(any(), any());
    }

    @Test
    void empleadoConCambioPendientePuedeCerrarSesion() throws Exception {
        MockHttpSession session = iniciarSesion("EMPLEADO", true);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void administradorPuedeCrearEmpleadoYNoSeDevuelveElHash() throws Exception {
        MockHttpSession session = iniciarSesion("ADMIN");
        when(authService.crearEmpleado(any())).thenReturn(new UsuarioCreadoResponse(
                new UsuarioResponse(12L, "Ana", "ana@luany.com", "EMPLEADO", true)));

        mockMvc.perform(post("/api/usuarios")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@luany.com\",\"rol\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.rol").value("EMPLEADO"))
                .andExpect(jsonPath("$.passwordTemporal").doesNotExist())
                .andExpect(jsonPath("$.usuario.password").doesNotExist());

        verify(authService).crearEmpleado(any());
    }

    @Test
    void falloDeCorreoSeRespondeComoServicioNoDisponible() throws Exception {
        MockHttpSession session = iniciarSesion("ADMIN");
        when(authService.crearEmpleado(any())).thenThrow(
                new CorreoEnvioException());

        mockMvc.perform(post("/api/usuarios")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@luany.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(
                        "No se pudo enviar el correo de bienvenida. No se creó la cuenta."));
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
        return iniciarSesion(rol, false);
    }

    private MockHttpSession iniciarSesion(String rol, boolean debeCambiarPassword) throws Exception {
        when(authService.iniciarSesion(any())).thenReturn(usuario(rol, debeCambiarPassword));

        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"prueba@luany.com\",\"password\":\"secreta\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    private UsuarioResponse usuario(String rol, boolean debeCambiarPassword) {
        return new UsuarioResponse(
                1L, "Usuario de prueba", "prueba@luany.com", rol, debeCambiarPassword);
    }

    private String extraer(String json, String campo) {
        Matcher matcher = Pattern.compile("\"" + campo + "\":\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) throw new AssertionError("Falta el campo " + campo + " en la respuesta CSRF");
        return matcher.group(1);
    }
}
