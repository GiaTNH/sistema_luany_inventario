package com.luany.inventory.controller;

import com.luany.inventory.model.Cliente;
import com.luany.inventory.service.ClienteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClienteController.class)
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class})
class ClienteControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteService clienteService;

    @Test
    void listadoPermiteAdminYEmpleadoPeroRechazaAnonimos() throws Exception {
        when(clienteService.listar("ana")).thenReturn(List.of());

        mockMvc.perform(get("/api/clientes?buscar=ana").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/clientes").with(user("empleado").roles("EMPLEADO")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isUnauthorized());

        verify(clienteService).listar("ana");
        verify(clienteService).listar(null);
    }

    @Test
    void empleadoPuedeCrearClienteConCsrfYSeValidaElCuerpo() throws Exception {
        Cliente creado = new Cliente(1L, "Ana López", "DNI", "12345", null, null);
        when(clienteService.crear(any())).thenReturn(creado);

        mockMvc.perform(post("/api/clientes")
                        .with(user("empleado").roles("EMPLEADO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana López","tipoDocumento":"DNI","numeroDocumento":"12345"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.numeroDocumento").value("12345"));

        mockMvc.perform(post("/api/clientes")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":" ","tipoDocumento":"PASAPORTE","numeroDocumento":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void operacionesDeEdicionYEliminacionRequierenAutenticacionYCsrf() throws Exception {
        mockMvc.perform(put("/api/clientes/1")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana","tipoDocumento":"DNI","numeroDocumento":"12345"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/clientes/1")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana","tipoDocumento":"DNI","numeroDocumento":"12345"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/clientes/1")
                        .with(user("empleado").roles("EMPLEADO"))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(clienteService).actualizar(any(Long.class), any());
        verify(clienteService).eliminar(1L);
    }

    @Test
    void cuerpoInvalidoNoLlegaAlServicio() throws Exception {
        mockMvc.perform(post("/api/clientes")
                        .with(user("empleado").roles("EMPLEADO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","tipoDocumento":"DNI","numeroDocumento":"1"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clienteService);
    }
}
