package com.luany.inventory;

import com.luany.inventory.model.CrearEmpleadoRequest;
import com.luany.inventory.repository.UsuarioRepository;
import com.luany.inventory.repository.RecuperacionPasswordTokenRepository;
import com.luany.inventory.service.AuthService;
import com.luany.inventory.service.CorreoEnvioException;
import com.luany.inventory.service.CorreoService;
import com.luany.inventory.service.RecuperacionPasswordService;
import com.luany.inventory.model.RegistroInicialRequest;
import com.luany.inventory.model.RecuperacionPasswordToken;
import com.luany.inventory.model.RestablecerPasswordRequest;
import com.luany.inventory.model.SolicitudRecuperacionPasswordRequest;
import com.luany.inventory.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataAccessException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:luanytest;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "MAIL_API_KEY=",
        "MAIL_FROM_EMAIL=onboarding@resend.dev",
        "MAIL_FROM_NAME=LUANY",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class InventoryApplicationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private RecuperacionPasswordService recuperacionPasswordService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RecuperacionPasswordTokenRepository tokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRegistry sessionRegistry;

    @BeforeEach
    void limpiarUsuarios() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS registro_inicial (
                    id TINYINT NOT NULL PRIMARY KEY,
                    completado BOOLEAN NOT NULL DEFAULT FALSE,
                    CONSTRAINT chk_registro_inicial_id CHECK (id = 1)
                )
                """);
        tokenRepository.deleteAll();
        usuarioRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM registro_inicial");
        jdbcTemplate.update("INSERT INTO registro_inicial (id, completado) VALUES (1, FALSE)");
    }

    @Test
    void contextLoads() {
    }

    @Test
    void faltaDeApiKeyRevierteLaCreacionDelUsuarioEnBaseDeDatos() {
        CrearEmpleadoRequest request = new CrearEmpleadoRequest();
        request.setNombre("Usuario rollback");
        request.setEmail("rollback@luany.com");

        assertThrows(CorreoEnvioException.class, () -> authService.crearEmpleado(request));
        assertTrue(usuarioRepository.buscarPorEmail("rollback@luany.com").isEmpty());
    }

    @Test
    void registroInicialCreaAdminConBCryptSinCambioObligatorioNiCorreo() {
        RegistroInicialRequest request = registroInicial("Admin", "admin@luany.com");

        var respuesta = authService.registrarPrimerAdministrador(request);

        Usuario guardado = usuarioRepository.buscarPorEmail("admin@luany.com").orElseThrow();
        assertEquals("ADMIN", guardado.getRol());
        assertFalse(guardado.isDebeCambiarPassword());
        assertTrue(passwordEncoder.matches(request.getPassword(), guardado.getPassword()));
        assertNotEquals(request.getPassword(), guardado.getPassword());
        assertEquals("ADMIN", respuesta.rol());
        assertFalse(respuesta.debeCambiarPassword());
        assertFalse(java.util.Arrays.stream(respuesta.getClass().getRecordComponents())
                .anyMatch(component -> component.getName().equals("password")));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT completado FROM registro_inicial WHERE id = 1", Boolean.class));
        assertFalse(authService.registroInicialDisponible());
    }

    @Test
    void segundoRegistroInicialEsRechazado() {
        authService.registrarPrimerAdministrador(registroInicial("Admin", "admin@luany.com"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authService.registrarPrimerAdministrador(
                        registroInicial("Otro", "otro@luany.com")));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(1, usuarioRepository.count());
        assertEquals(1, usuarioRepository.countByRol("ADMIN"));
    }

    @Test
    void contrasenasQueNoCoincidenRechazanElRegistro() {
        RegistroInicialRequest request = registroInicial("Admin", "admin@luany.com");
        request.setConfirmarPassword("OtraClaveSegura2026!");

        assertThrows(IllegalArgumentException.class,
                () -> authService.registrarPrimerAdministrador(request));
        assertFalse(jdbcTemplate.queryForObject(
                "SELECT completado FROM registro_inicial WHERE id = 1", Boolean.class));
        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void indicadorSinInicializarImpideElRegistro() {
        jdbcTemplate.update("DELETE FROM registro_inicial");

        assertFalse(authService.registroInicialDisponible());
        assertThrows(ResponseStatusException.class,
                () -> authService.registrarPrimerAdministrador(
                        registroInicial("Admin", "admin@luany.com")));
        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void tablaDeControlAusenteImpideElRegistroSinCrearUsuarios() {
        jdbcTemplate.execute("DROP TABLE registro_inicial");

        assertThrows(DataAccessException.class,
                () -> authService.registrarPrimerAdministrador(
                        registroInicial("Admin", "admin@luany.com")));
        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void usuarioExistenteImpideRegistroAunqueElIndicadorEsteDisponible() {
        Usuario existente = new Usuario();
        existente.setNombre("Empleado");
        existente.setEmail("empleado@luany.com");
        existente.setNombreUsuario("empleado");
        existente.setPassword(passwordEncoder.encode("ClaveSegura2026!"));
        existente.setRol("EMPLEADO");
        usuarioRepository.saveAndFlush(existente);

        assertFalse(authService.registroInicialDisponible());
        assertThrows(ResponseStatusException.class,
                () -> authService.registrarPrimerAdministrador(
                        registroInicial("Admin", "admin@luany.com")));
        assertFalse(jdbcTemplate.queryForObject(
                "SELECT completado FROM registro_inicial WHERE id = 1", Boolean.class));
        assertEquals(1, usuarioRepository.count());
    }

    @Test
    void falloAlGuardarRevierteElIndicadorDeRegistro() {
        RegistroInicialRequest request = registroInicial("N".repeat(101), "admin@luany.com");

        assertThrows(RuntimeException.class,
                () -> authService.registrarPrimerAdministrador(request));

        assertFalse(jdbcTemplate.queryForObject(
                "SELECT completado FROM registro_inicial WHERE id = 1", Boolean.class));
        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void registrosInicialesConcurrentesCreanUnSoloAdministradorEnH2() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var inicio = new CountDownLatch(1);
        var listo = new CountDownLatch(2);
        try {
            var primero = executor.submit(() -> intentarRegistroConcurrently(
                    registroInicial("Admin Uno", "admin1@luany.com"), inicio, listo));
            var segundo = executor.submit(() -> intentarRegistroConcurrently(
                    registroInicial("Admin Dos", "admin2@luany.com"), inicio, listo));

            assertTrue(listo.await(5, TimeUnit.SECONDS));
            inicio.countDown();

            int exitosos = (primero.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (segundo.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, exitosos);
            assertEquals(1, usuarioRepository.count());
            assertEquals(1, usuarioRepository.countByRol("ADMIN"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void fallaCorreoDeRecuperacionInvalidaElTokenGenerado() {
        Usuario usuario = usuarioActivo("recuperacion@luany.com");
        usuarioRepository.saveAndFlush(usuario);
        SolicitudRecuperacionPasswordRequest request = new SolicitudRecuperacionPasswordRequest();
        request.setEmail(usuario.getEmail());

        assertEquals("No pudimos enviar el enlace de recuperación. Inténtalo más tarde.",
                recuperacionPasswordService.solicitarRecuperacion(request));
        assertEquals(1, tokenRepository.count());
        assertNotNull(tokenRepository.findAll().get(0).getUtilizadoEn());
        assertTrue(tokenRepository.findByTokenHashAndUtilizadoEnIsNull(
                tokenRepository.findAll().get(0).getTokenHash()).isEmpty());
    }

    @Test
    void respuestaHttpDistingueCuentaInexistenteDeFalloDeEnvioSinDetallesTecnicos() throws Exception {
        String cuerpo = """
                {"email":"desconocido@luany.com"}
                """;
        var correoInexistente = mockMvc.perform(post("/api/auth/solicitar-recuperacion")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje")
                        .value("No existe una cuenta activa asociada a ese correo."))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        usuarioRepository.saveAndFlush(usuarioActivo("recuperacion@luany.com"));
        var correoRegistradoConFalloResend = mockMvc.perform(post("/api/auth/solicitar-recuperacion")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"recuperacion@luany.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje")
                        .value("No pudimos enviar el enlace de recuperación. Inténtalo más tarde."))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertNotEquals(correoInexistente, correoRegistradoConFalloResend);
        assertEquals(1, tokenRepository.count());
        assertNotNull(tokenRepository.findAll().get(0).getUtilizadoEn());
    }

    @Test
    void restablecimientoGuardaBCryptYConsumeTokenEnH2() throws Exception {
        String tokenPlano = "token-valido-para-prueba-de-integracion";
        Usuario usuario = usuarioRepository.saveAndFlush(usuarioActivo("reset@luany.com"));
        usuario.setPassword(passwordEncoder.encode("ContrasenaAnterior2026!"));
        usuario.setDebeCambiarPassword(true);
        usuarioRepository.saveAndFlush(usuario);
        guardarToken(tokenPlano, usuario, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10), null);

        String mensaje = recuperacionPasswordService.restablecerPassword(
                restablecer(tokenPlano, "NuevaClaveSegura2026!"));

        Usuario actualizado = usuarioRepository.buscarPorEmail("reset@luany.com").orElseThrow();
        assertTrue(mensaje.contains("actualizó correctamente"));
        assertTrue(passwordEncoder.matches("NuevaClaveSegura2026!", actualizado.getPassword()));
        assertNotEquals("NuevaClaveSegura2026!", actualizado.getPassword());
        assertFalse(actualizado.isDebeCambiarPassword());
        assertNotNull(tokenRepository.findAll().get(0).getUtilizadoEn());
        assertThrows(ResponseStatusException.class,
                () -> recuperacionPasswordService.restablecerPassword(
                        restablecer(tokenPlano, "OtraClaveSegura2026!")));
    }

    @Test
    void restablecerPasswordExpiraSesionYBloqueaSuAccesoProtegido() throws Exception {
        String email = "sesion-revocada@luany.com";
        String passwordActual = "ContrasenaAnterior2026!";
        String tokenPlano = "token-para-revocar-sesion-integracion";
        Usuario usuario = usuarioActivo(email);
        usuario.setPassword(passwordEncoder.encode(passwordActual));
        usuario.setDebeCambiarPassword(false);
        usuario = usuarioRepository.saveAndFlush(usuario);
        guardarToken(tokenPlano, usuario, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10), null);

        var loginResultado = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, passwordActual)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession sesionAnterior =
                (MockHttpSession) loginResultado.getRequest().getSession(false);
        assertNotNull(sesionAnterior);

        mockMvc.perform(get("/api/auth/me").session(sesionAnterior))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        assertNotNull(sessionRegistry.getSessionInformation(sesionAnterior.getId()));

        mockMvc.perform(post("/api/auth/restablecer-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","nuevaPassword":"NuevaClaveSegura2026!",
                                 "confirmarPassword":"NuevaClaveSegura2026!"}
                                """.formatted(tokenPlano)))
                .andExpect(status().isOk());

        var informacionSesion = sessionRegistry.getSessionInformation(sesionAnterior.getId());
        assertNotNull(informacionSesion);
        assertTrue(informacionSesion.isExpired());

        mockMvc.perform(get("/api/auth/me").session(sesionAnterior))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mensaje")
                        .value("Tu sesión ha expirado. Inicia sesión nuevamente."))
                .andExpect(result -> assertNull(result.getHandler()));
    }

    @Test
    void usuarioAutenticadoActualizaPasswordConservaSesionYPuedeCerrarSesion() throws Exception {
        String email = "actualizacion-password@luany.com";
        String passwordActual = "ContrasenaActual2026!";
        String nuevaPassword = "NuevaClaveSegura2026!";
        Usuario usuario = usuarioActivo(email);
        usuario.setPassword(passwordEncoder.encode(passwordActual));
        usuario.setDebeCambiarPassword(false);
        usuarioRepository.saveAndFlush(usuario);

        var loginResultado = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, passwordActual)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) loginResultado.getRequest().getSession(false);
        assertNotNull(sesion);

        mockMvc.perform(post("/api/auth/cambiar-password")
                        .session(sesion)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passwordActual":"%s","nuevaPassword":"%s",
                                 "confirmarPassword":"%s"}
                                """.formatted(passwordActual, nuevaPassword, nuevaPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarPassword").value(false));

        Usuario actualizado = usuarioRepository.buscarPorEmail(email).orElseThrow();
        assertTrue(passwordEncoder.matches(nuevaPassword, actualizado.getPassword()));
        assertNotEquals(nuevaPassword, actualizado.getPassword());
        mockMvc.perform(get("/api/auth/me").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/api/auth/logout")
                        .session(sesion)
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaTokensExpiradosYUtilizadosEnH2() throws Exception {
        Usuario usuario = usuarioRepository.saveAndFlush(usuarioActivo("reset@luany.com"));
        String expirado = "token-expirado-integracion";
        String utilizado = "token-utilizado-integracion";
        guardarToken(expirado, usuario, LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), null);
        guardarToken(utilizado, usuario, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10),
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));

        assertThrows(ResponseStatusException.class,
                () -> recuperacionPasswordService.restablecerPassword(
                        restablecer(expirado, "NuevaClaveSegura2026!")));
        assertThrows(ResponseStatusException.class,
                () -> recuperacionPasswordService.restablecerPassword(
                        restablecer(utilizado, "NuevaClaveSegura2026!")));
        assertEquals(1, usuarioRepository.count());
    }

    @Test
    void dosRestablecimientosConcurrentesSoloConsumenUnaVezEnH2() throws Exception {
        String tokenPlano = "token-concurrente-integracion";
        Usuario usuario = usuarioRepository.saveAndFlush(usuarioActivo("concurrente@luany.com"));
        guardarToken(tokenPlano, usuario, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10), null);
        var executor = Executors.newFixedThreadPool(2);
        var inicio = new CountDownLatch(1);
        var listo = new CountDownLatch(2);
        try {
            var primero = executor.submit(() ->
                    intentarRestablecimientoConcurrente(tokenPlano, inicio, listo));
            var segundo = executor.submit(() ->
                    intentarRestablecimientoConcurrente(tokenPlano, inicio, listo));
            assertTrue(listo.await(5, TimeUnit.SECONDS));
            inicio.countDown();

            int exitosos = (primero.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (segundo.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, exitosos);
            Usuario actualizado = usuarioRepository.buscarPorEmail("concurrente@luany.com").orElseThrow();
            assertTrue(passwordEncoder.matches("NuevaClaveSegura2026!", actualizado.getPassword()));
            assertNotNull(tokenRepository.findAll().get(0).getUtilizadoEn());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean intentarRegistroConcurrently(
            RegistroInicialRequest request, CountDownLatch inicio, CountDownLatch listo) throws Exception {
        listo.countDown();
        inicio.await();
        try {
            authService.registrarPrimerAdministrador(request);
            return true;
        } catch (ResponseStatusException error) {
            if (error.getStatusCode() == org.springframework.http.HttpStatus.CONFLICT) {
                return false;
            }
            throw error;
        }
    }

    private RegistroInicialRequest registroInicial(String nombre, String email) {
        RegistroInicialRequest request = new RegistroInicialRequest();
        request.setNombre(nombre);
        request.setEmail(email);
        request.setPassword("ClaveSegura2026!");
        request.setConfirmarPassword("ClaveSegura2026!");
        return request;
    }

    private boolean intentarRestablecimientoConcurrente(
            String token, CountDownLatch inicio, CountDownLatch listo) throws Exception {
        listo.countDown();
        inicio.await();
        try {
            recuperacionPasswordService.restablecerPassword(
                    restablecer(token, "NuevaClaveSegura2026!"));
            return true;
        } catch (ResponseStatusException error) {
            if (error.getStatusCode() == org.springframework.http.HttpStatus.BAD_REQUEST) {
                return false;
            }
            throw error;
        }
    }

    private Usuario usuarioActivo(String email) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Usuario de prueba");
        usuario.setEmail(email);
        usuario.setNombreUsuario("usuario." + email.substring(0, email.indexOf('@')));
        usuario.setPassword(passwordEncoder.encode("ContrasenaAnterior2026!"));
        usuario.setRol("EMPLEADO");
        usuario.setEstado("ACTIVO");
        usuario.setDebeCambiarPassword(true);
        return usuario;
    }

    private void guardarToken(
            String tokenPlano, Usuario usuario, LocalDateTime expiraEn, LocalDateTime utilizadoEn)
            throws Exception {
        RecuperacionPasswordToken token = new RecuperacionPasswordToken();
        token.setUsuario(usuario);
        token.setTokenHash(hashToken(tokenPlano));
        token.setCreadoEn(LocalDateTime.now(ZoneOffset.UTC));
        token.setExpiraEn(expiraEn);
        token.setUtilizadoEn(utilizadoEn);
        tokenRepository.saveAndFlush(token);
    }

    private RestablecerPasswordRequest restablecer(String token, String password) {
        RestablecerPasswordRequest request = new RestablecerPasswordRequest();
        request.setToken(token);
        request.setNuevaPassword(password);
        request.setConfirmarPassword(password);
        return request;
    }

    private String hashToken(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

}