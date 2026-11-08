package com.luany.inventory;

import com.luany.inventory.model.CrearEmpleadoRequest;
import com.luany.inventory.repository.UsuarioRepository;
import com.luany.inventory.service.AuthService;
import com.luany.inventory.service.CorreoEnvioException;
import com.luany.inventory.service.CorreoService;
import com.luany.inventory.model.RegistroInicialRequest;
import com.luany.inventory.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataAccessException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

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
class InventoryApplicationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void limpiarUsuarios() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS registro_inicial (
                    id TINYINT NOT NULL PRIMARY KEY,
                    completado BOOLEAN NOT NULL DEFAULT FALSE,
                    CONSTRAINT chk_registro_inicial_id CHECK (id = 1)
                )
                """);
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
}