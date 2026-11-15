package com.luany.inventory.controller;

import com.luany.inventory.model.RespuestaAsistente;
import com.luany.inventory.service.AsistenteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AsistenteController.class)
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class})
class AsistenteControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AsistenteService asistenteService;

    @Test
    void transmiteElRolAutenticadoAlServicioYExigeSesion() throws Exception {
        when(asistenteService.consultar("Lista los usuarios", false))
                .thenReturn(new RespuestaAsistente("USUARIOS", "Sin permiso", 0, 1));
        when(asistenteService.consultar("Lista los usuarios", true))
                .thenReturn(new RespuestaAsistente("USUARIOS", "Usuarios", 0, 1));

        mockMvc.perform(post("/api/asistente/consulta")
                        .with(user("empleado").roles("EMPLEADO"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"pregunta\":\"Lista los usuarios\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/asistente/consulta")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"pregunta\":\"Lista los usuarios\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/asistente/consulta")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"pregunta\":\"Lista los usuarios\"}"))
                .andExpect(status().isUnauthorized());

        verify(asistenteService).consultar("Lista los usuarios", false);
        verify(asistenteService).consultar("Lista los usuarios", true);
    }
}
