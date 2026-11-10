package com.luany.inventory.controller;

import com.luany.inventory.model.ReportesResponse.VentaDia;
import com.luany.inventory.model.ReportesResponse.VentasPeriodo;
import com.luany.inventory.model.TipoMovimiento;
import com.luany.inventory.service.ReportesService;
import com.luany.inventory.service.ReportesExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportesController.class)
@Import({ConfiguracionSeguridad.class, ManejadorErrores.class})
class ReportesControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ReportesService reportesService;
    @MockitoBean
    private ReportesExportService reportesExportService;

    @Test
    void administradorYEmpleadoPuedenConsultarYUnaSesionEsObligatoria() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 11, 1);
        when(reportesService.ventas(fecha, fecha)).thenReturn(
                new VentasPeriodo(fecha, fecha, new BigDecimal("25.00"), 2,
                        List.of(new VentaDia(fecha, new BigDecimal("25.00"), 2))));

        mockMvc.perform(get("/api/reportes/ventas?desde=2026-11-01&hasta=2026-11-01")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importeTotal").value(25.0))
                .andExpect(jsonPath("$.cantidadNotas").value(2));
        mockMvc.perform(get("/api/reportes/ventas?desde=2026-11-01&hasta=2026-11-01")
                        .with(user("empleado").roles("EMPLEADO")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reportes/ventas"))
                .andExpect(status().isUnauthorized());

        verify(reportesService, org.mockito.Mockito.times(2)).ventas(fecha, fecha);
    }

    @Test
    void exportacionesRequierenSesionYConservanRolesYFiltros() throws Exception {
        byte[] excel = new byte[]{'P', 'K', 3, 4};
        when(reportesExportService.exportarExcel(
                "productos-mas-vendidos", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 7), 5, null, null, null))
                .thenReturn(new ReportesExportService.ReporteArchivo(
                        "reporte_productos_mas_vendidos.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        excel));
        when(reportesExportService.exportarPdf(
                "movimientos", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 7), null, 8L, TipoMovimiento.VENTA, null))
                .thenReturn(new ReportesExportService.ReporteArchivo(
                        "reporte_movimientos.pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F'}));

        mockMvc.perform(get("/api/reportes/productos-mas-vendidos/excel"
                        + "?desde=2026-11-01&hasta=2026-11-07&limite=5")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("reporte_productos_mas_vendidos.xlsx")))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(excel));

        mockMvc.perform(get("/api/reportes/movimientos/pdf"
                        + "?desde=2026-11-01&hasta=2026-11-07&productoId=8&tipo=VENTA")
                        .with(user("empleado").roles("EMPLEADO")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));

        mockMvc.perform(get("/api/reportes/movimientos/pdf?desde=2026-11-01&hasta=2026-11-07"))
                .andExpect(status().isUnauthorized());

        verify(reportesExportService).exportarExcel(
                "productos-mas-vendidos", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 7), 5, null, null, null);
        verify(reportesExportService).exportarPdf(
                "movimientos", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 7), null, 8L, TipoMovimiento.VENTA, null);
    }
}
