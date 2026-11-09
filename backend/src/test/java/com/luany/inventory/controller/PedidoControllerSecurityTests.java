package com.luany.inventory.controller;

import com.luany.inventory.model.PedidoDetalleResponse;
import com.luany.inventory.service.PedidoService;
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

@WebMvcTest(controllers = PedidoController.class)
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class})
class PedidoControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService pedidoService;

    @Test
    void lecturaRequiereSesionYPermiteEmpleadoDeAlmacen() throws Exception {
        when(pedidoService.listar("ana", "PENDIENTE")).thenReturn(List.of());

        mockMvc.perform(get("/api/pedidos?buscar=ana&estado=PENDIENTE")
                        .with(user("almacen").roles("EMPLEADO")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/pedidos"))
                .andExpect(status().isUnauthorized());

        verify(pedidoService).listar("ana", "PENDIENTE");
    }

    @Test
    void administradorPuedeCrearPedidoConCsrf() throws Exception {
        when(pedidoService.crear(any(), eq("admin@luany.com"))).thenReturn(respuesta());

        mockMvc.perform(post("/api/pedidos")
                        .with(user("admin@luany.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proveedorId":4,"productos":[
                                  {"productoId":8,"cantidad":3,"precio":12.50}
                                ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void empleadoPuedeConfirmarRecepcionPeroSeRequiereCsrfYValidacion() throws Exception {
        when(pedidoService.recibir(eq(5L), any(), eq("almacen@luany.com"))).thenReturn(respuesta());

        mockMvc.perform(post("/api/pedidos/5/recepciones")
                        .with(user("almacen@luany.com").roles("EMPLEADO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productos":[{"detallePedidoId":12,"cantidad":1}]}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/pedidos/5/recepciones")
                        .with(user("almacen@luany.com").roles("EMPLEADO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"claveIdempotencia":"9fc78ecb-d852-4875-9715-5f96f03414b2",
                                 "productos":[{"detallePedidoId":12,"cantidad":1}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));

        mockMvc.perform(post("/api/pedidos/5/recepciones")
                        .with(user("almacen@luany.com").roles("EMPLEADO"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"claveIdempotencia":"9fc78ecb-d852-4875-9715-5f96f03414b2",
                                 "productos":[{"detallePedidoId":12,"cantidad":0}]}
                                """))
                .andExpect(status().isBadRequest());

        verify(pedidoService).recibir(eq(5L), any(), eq("almacen@luany.com"));
    }

    @Test
    void empleadoPuedeConsultarDetalle() throws Exception {
        when(pedidoService.obtener(5L)).thenReturn(respuesta());

        mockMvc.perform(get("/api/pedidos/5").with(user("almacen").roles("EMPLEADO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void pedidoConCantidadInvalidaNoLlegaAlServicio() throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proveedorId":4,"productos":[
                                  {"productoId":8,"cantidad":0,"precio":12.50}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verifyNoInteractions(pedidoService);
    }

    private PedidoDetalleResponse respuesta() {
        return new PedidoDetalleResponse(
                5L, null, null, "PENDIENTE", 4L, "Proveedor", "Almacén", List.of(), List.of());
    }
}
