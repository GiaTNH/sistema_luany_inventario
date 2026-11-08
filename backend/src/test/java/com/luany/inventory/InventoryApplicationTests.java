package com.luany.inventory;

import com.luany.inventory.model.CrearEmpleadoRequest;
import com.luany.inventory.repository.UsuarioRepository;
import com.luany.inventory.service.AuthService;
import com.luany.inventory.service.CorreoEnvioException;
import com.luany.inventory.service.CorreoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @BeforeEach
    void limpiarUsuarios() {
        usuarioRepository.deleteAll();
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
}