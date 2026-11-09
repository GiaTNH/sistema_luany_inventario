package com.luany.inventory.controller;

import com.luany.inventory.model.NotaVentaDetalleResponse;
import com.luany.inventory.service.NotaVentaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotaVentaController.class)
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class})
class NotaVentaControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotaVentaService notaVentaService;

    @Test
    void listarYConsultarNotaRequiereSesionYPermiteEmpleado() throws Exception {
        when(notaVentaService.listar("ana")).thenReturn(List.of());
        when(notaVentaService.obtener(10L)).thenReturn(respuesta());

        mockMvc.perform(get("/api/notas-venta?buscar=ana")
                        .with(user("empleada").roles("EMPLEADO")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notas-venta/10")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroNota").value("NV-TEST"));
        mockMvc.perform(get("/api/notas-venta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminPuedeCrearVentaConCsrfYNoSeAceptanCantidadesInvalidas() throws Exception {
        when(notaVentaService.crear(any(), eq("admin@luany.com"))).thenReturn(respuesta());

        mockMvc.perform(post("/api/notas-venta")
                        .with(user("admin@luany.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":3,"productos":[{"productoId":4,"cantidad":2}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(12.5))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(post("/api/notas-venta")
                        .with(user("empleada").roles("EMPLEADO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":3,"productos":[{"productoId":4,"cantidad":0}]}
                                """))
                .andExpect(status().isBadRequest());

        verify(notaVentaService).crear(any(), eq("admin@luany.com"));
    }

    @Test
    void crearVentaRechazaAnonimosOCsrfAusente() throws Exception {
        String body = """
                {"clienteId":3,"productos":[{"productoId":4,"cantidad":1}]}
                """;
        mockMvc.perform(post("/api/notas-venta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/notas-venta")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notas-venta")
                        .with(user("empleada").roles("EMPLEADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        verifyNoInteractions(notaVentaService);
    }

    private NotaVentaDetalleResponse respuesta() {
        return new NotaVentaDetalleResponse(
                10L, "NV-TEST", null, "Ana", "123", "Vendedora",
                new java.math.BigDecimal("12.50"), "EMITIDA", List.of());
    }
}
