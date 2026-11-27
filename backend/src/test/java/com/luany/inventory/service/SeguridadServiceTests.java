
package com.luany.inventory.service;

import com.luany.inventory.model.Usuario;
import com.luany.inventory.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeguridadServiceTests {

    @Mock private IntentoSeguridadRepository intentoRepository;
    @Mock private AuditoriaSeguridadRepository auditoriaRepository;
    @Mock private BloqueoHistorialRepository bloqueoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private SessionRegistry sessionRegistry;
    @Mock private CorreoService correoService;
    @Mock private BloqueoIpService bloqueoIpService;

    private SeguridadService seguridadService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        seguridadService = new SeguridadService(
                intentoRepository,
                auditoriaRepository,
                bloqueoRepository,
                usuarioRepository,
                sessionRegistry,
                correoService,
                bloqueoIpService,
                Clock.fixed(
                        Instant.parse("2026-11-17T17:00:00Z"),
                        ZoneId.of("America/Lima")
                )
        );

        ReflectionTestUtils.setField(seguridadService, "ventanaMinutos", 15);
        ReflectionTestUtils.setField(seguridadService, "alertaAt", 3);
        ReflectionTestUtils.setField(seguridadService, "blockAt", 5);
        ReflectionTestUtils.setField(seguridadService, "alertaCooldownMinutos", 15);

        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNombre("Ana");
        usuario.setEmail("ana@luany.com");
        usuario.setEstado("ACTIVO");
    }

    @Test
    void alTercerIntentoAgrupaUnaAlertaSinBloquear() {
        when(usuarioRepository.buscarPorEmail("ana@luany.com"))
                .thenReturn(Optional.of(usuario));

        when(intentoRepository.countByIdentificadorIgnoreCaseAndResultadoAndFechaAfter(
                eq("ana@luany.com"), eq("FALLIDO"), any()))
                .thenReturn(3L);

        when(intentoRepository.countByIpAndTipoAndResultadoAndFechaAfter(
                eq("192.0.2.20"), eq("LOGIN"), eq("FALLIDO"), any()))
                .thenReturn(3L);

        when(auditoriaRepository.existsByTipoEventoAndFechaAfter(
                eq("ALERTA_SEGURIDAD"), any()))
                .thenReturn(false);

        SeguridadService.ResultadoLogin resultado =
                seguridadService.registrarFallido("ana@luany.com", "192.0.2.20");

        assertFalse(resultado.bloqueado());

        verify(correoService).enviarAlertaSeguridad(
                eq("Ana (ana@luany.com)"),
                anyString(),
                eq("192.0.2.20"),
                eq(3L),
                eq("ACTIVO"),
                eq(false)
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
        verify(bloqueoIpService).registrarFallido("192.0.2.20");
    }

    @Test
    void alQuintoIntentoBloqueaTemporalmenteLaCuenta() {
        when(usuarioRepository.buscarPorEmail("ana@luany.com"))
                .thenReturn(Optional.of(usuario));

        when(usuarioRepository.buscarPorIdParaActualizar(1L))
                .thenReturn(Optional.of(usuario));

        when(intentoRepository.countByIdentificadorIgnoreCaseAndResultadoAndFechaAfter(
                eq("ana@luany.com"), eq("FALLIDO"), any()))
                .thenReturn(5L);

        when(intentoRepository.countByIpAndTipoAndResultadoAndFechaAfter(
                eq("192.0.2.20"), eq("LOGIN"), eq("FALLIDO"), any()))
                .thenReturn(5L);

        when(auditoriaRepository.existsByTipoEventoAndFechaAfter(
                eq("ALERTA_SEGURIDAD"), any()))
                .thenReturn(false);

        SeguridadService.ResultadoLogin resultado =
                seguridadService.registrarFallido("ana@luany.com", "192.0.2.20");

        assertTrue(resultado.bloqueado());
        assertNotNull(usuario.getBloqueadoHasta());
        assertFalse(usuario.isBloqueadoManual());

        verify(usuarioRepository).save(usuario);
        verify(bloqueoRepository).save(any());
        verify(bloqueoIpService).registrarFallido("192.0.2.20");
    }

    @Test
    void bloqueoTemporalExpiraCuandoPasaSuFecha() {
        usuario.setBloqueadoHasta(java.time.LocalDateTime.now(Clock.fixed(
                Instant.parse("2026-11-17T16:00:00Z"),
                ZoneId.of("America/Lima")
        )).minusMinutes(1));

        when(usuarioRepository.buscarPorEmail("ana@luany.com"))
                .thenReturn(Optional.of(usuario));

        assertNull(seguridadService.verificarBloqueo("ana@luany.com"));
    }

    @Test
    void bloqueoManualInvalidaSesiones() {
        when(usuarioRepository.buscarPorIdParaActualizar(1L))
                .thenReturn(Optional.of(usuario));

        SessionInformation sesion = mock(SessionInformation.class);

        when(sessionRegistry.getAllSessions("ana@luany.com", false))
                .thenReturn(java.util.List.of(sesion));

        seguridadService.bloquearManual(
                1L,
                "Actividad sospechosa confirmada",
                "admin@luany.com",
                "192.0.2.30"
        );

        assertTrue(usuario.isBloqueadoManual());

        verify(usuarioRepository).save(usuario);
        verify(bloqueoRepository).save(any());
        verify(auditoriaRepository).save(any());
        verify(sesion).expireNow();
    }

    @Test
    void desbloqueoManualLimpiaElBloqueoYTambienInvalidaSesiones() {
        usuario.setBloqueadoManual(true);
        usuario.setBloqueadoHasta(java.time.LocalDateTime.now().plusMinutes(10));

        when(usuarioRepository.buscarPorIdParaActualizar(1L))
                .thenReturn(Optional.of(usuario));

        SessionInformation sesion = mock(SessionInformation.class);

        when(sessionRegistry.getAllSessions("ana@luany.com", false))
                .thenReturn(java.util.List.of(sesion));

        seguridadService.desbloquear(
                1L,
                "Revisión concluida",
                "admin@luany.com",
                "192.0.2.30"
        );

        assertFalse(usuario.isBloqueadoManual());
        assertNull(usuario.getBloqueadoHasta());

        verify(usuarioRepository).save(usuario);
        verify(bloqueoRepository).save(any());
        verify(auditoriaRepository).save(any());
        verify(sesion).expireNow();
    }
}
