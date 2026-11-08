package com.luany.inventory.service;

import com.luany.inventory.model.RecuperacionPasswordToken;
import com.luany.inventory.model.RestablecerPasswordRequest;
import com.luany.inventory.model.SolicitudRecuperacionPasswordRequest;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.repository.RecuperacionPasswordTokenRepository;
import com.luany.inventory.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecuperacionPasswordServiceTests {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RecuperacionPasswordTokenRepository tokenRepository;

    @Mock
    private CorreoService correoService;

    @Mock
    private RecuperacionPasswordSolicitudProcessor solicitudProcessor;

    @Mock
    private RecuperacionPasswordTokenService tokenService;

    @Mock
    private SessionRegistry sessionRegistry;

    private PasswordEncoder passwordEncoder;
    private RecuperacionPasswordService service;
    private RecuperacionPasswordSolicitudProcessor processor;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        lenient().when(sessionRegistry.getAllSessions(any(), anyBoolean()))
                .thenReturn(java.util.List.of());
        service = new RecuperacionPasswordService(
                usuarioRepository, tokenRepository, passwordEncoder, solicitudProcessor, sessionRegistry);
        processor = new RecuperacionPasswordSolicitudProcessor(tokenService, correoService);
    }

    @Test
    void procesadorEnvíaElCorreoUnaVezCreadaLaSolicitudTransaccional() {
        var solicitudCorreo = new RecuperacionPasswordTokenService.SolicitudCorreo(
                "ana@luany.com", "Ana",
                "https://sistema-luany-inventario.onrender.com/#restablecer?token=secreto",
                "hash-del-token");
        when(tokenService.crearSolicitud("ana@luany.com"))
                .thenReturn(Optional.of(solicitudCorreo));

        assertEquals(RecuperacionPasswordSolicitudProcessor.Resultado.CORREO_ENVIADO,
                processor.procesar("ana@luany.com").join());

        verify(correoService).enviarRecuperacion(
                "ana@luany.com", "Ana", solicitudCorreo.enlace());
    }

    @Test
    void solicitudesMuestranElResultadoDeLaRecuperacionSinDatosAdicionales() {
        SolicitudRecuperacionPasswordRequest existente = solicitud("ana@luany.com");
        SolicitudRecuperacionPasswordRequest inexistente = solicitud("nadie@luany.com");
        SolicitudRecuperacionPasswordRequest falloEnvio = solicitud("fallo@luany.com");
        when(solicitudProcessor.procesar("ana@luany.com"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        RecuperacionPasswordSolicitudProcessor.Resultado.CORREO_ENVIADO));
        when(solicitudProcessor.procesar("nadie@luany.com"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        RecuperacionPasswordSolicitudProcessor.Resultado.CUENTA_NO_ENCONTRADA));
        when(solicitudProcessor.procesar("fallo@luany.com"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        RecuperacionPasswordSolicitudProcessor.Resultado.ERROR_ENVIO));

        String respuestaExistente = service.solicitarRecuperacion(existente);
        String respuestaInexistente = service.solicitarRecuperacion(inexistente);
        String respuestaFalloEnvio = service.solicitarRecuperacion(falloEnvio);

        assertEquals(RecuperacionPasswordService.MENSAJE_CORREO_ENVIADO, respuestaExistente);
        assertEquals(RecuperacionPasswordService.MENSAJE_CUENTA_NO_ENCONTRADA, respuestaInexistente);
        assertEquals(RecuperacionPasswordService.MENSAJE_ERROR_ENVIO, respuestaFalloEnvio);
        verify(solicitudProcessor).procesar("ana@luany.com");
        verify(solicitudProcessor).procesar("nadie@luany.com");
        verify(solicitudProcessor).procesar("fallo@luany.com");
    }

    @Test
    void cuentaInactivaNoRecibeCorreoNiToken() {
        when(tokenService.crearSolicitud("ana@luany.com")).thenReturn(Optional.empty());

        assertEquals(RecuperacionPasswordSolicitudProcessor.Resultado.CUENTA_NO_ENCONTRADA,
                processor.procesar("ana@luany.com").join());

        verifyNoInteractions(tokenRepository, correoService);
    }

    @Test
    void falloDeCorreoInvalidaSoloElTokenGeneradoEnOtraOperacion() {
        var solicitudCorreo = new RecuperacionPasswordTokenService.SolicitudCorreo(
                "ana@luany.com", "Ana", "https://example.test/recuperar", "hash-generado");
        when(tokenService.crearSolicitud("ana@luany.com"))
                .thenReturn(Optional.of(solicitudCorreo));
        doThrow(new CorreoRecuperacionException())
                .when(correoService).enviarRecuperacion(eq("ana@luany.com"), eq("Ana"), anyString());

        assertEquals(RecuperacionPasswordSolicitudProcessor.Resultado.ERROR_ENVIO,
                processor.procesar("ana@luany.com").join());

        verify(tokenService).invalidarToken("hash-generado");
    }

    @Test
    void restableceConTokenValidoGuardaBCryptYDesactivaCambioObligatorio() throws Exception {
        String tokenPlano = "token-valido-de-prueba-256-bits";
        Usuario usuario = usuarioActivo();
        usuario.setPassword(passwordEncoder.encode("ContrasenaAnterior2026!"));
        usuario.setDebeCambiarPassword(true);
        RecuperacionPasswordToken token = token(tokenPlano, usuario, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        when(tokenRepository.findByTokenHashAndUtilizadoEnIsNull(hash(tokenPlano)))
                .thenReturn(Optional.of(token));
        when(tokenRepository.consumirSiVigente(eq(hash(tokenPlano)), any(LocalDateTime.class)))
                .thenReturn(1);

        RestablecerPasswordRequest request = restablecer(tokenPlano, "NuevaClaveSegura2026!");
        String respuesta = service.restablecerPassword(request);

        assertTrue(respuesta.contains("actualizó correctamente"));
        assertTrue(passwordEncoder.matches(request.getNuevaPassword(), usuario.getPassword()));
        assertNotEquals(request.getNuevaPassword(), usuario.getPassword());
        assertFalse(usuario.isDebeCambiarPassword());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void restablecimientoExpiraLasSesionesRegistradasDelUsuario() throws Exception {
        String tokenPlano = "token-para-invalidar-sesiones";
        Usuario usuario = usuarioActivo();
        RecuperacionPasswordToken token = token(
                tokenPlano, usuario, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        SessionInformation session = mock(SessionInformation.class);
        when(sessionRegistry.getAllSessions("ana@luany.com", false))
                .thenReturn(java.util.List.of(session));
        when(tokenRepository.findByTokenHashAndUtilizadoEnIsNull(hash(tokenPlano)))
                .thenReturn(Optional.of(token));
        when(tokenRepository.consumirSiVigente(eq(hash(tokenPlano)), any(LocalDateTime.class)))
                .thenReturn(1);

        service.restablecerPassword(restablecer(tokenPlano, "NuevaClaveSegura2026!"));

        verify(session).expireNow();
    }

    @Test
    void rechazaTokenExpiradoOYaUtilizado() throws Exception {
        String tokenPlano = "token-expirado-de-prueba";
        when(tokenRepository.findByTokenHashAndUtilizadoEnIsNull(hash(tokenPlano)))
                .thenReturn(Optional.of(token(tokenPlano, usuarioActivo(),
                        LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1))));

        assertThrows(ResponseStatusException.class,
                () -> service.restablecerPassword(restablecer(tokenPlano, "NuevaClaveSegura2026!")));
        verify(tokenRepository, never()).consumirSiVigente(any(), any());
        verify(usuarioRepository, never()).save(any());

        when(tokenRepository.findByTokenHashAndUtilizadoEnIsNull(hash("usado")))
                .thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> service.restablecerPassword(restablecer("usado", "NuevaClaveSegura2026!")));
    }

    @Test
    void tokenNoPuedeConsumirseSiLaActualizacionAtomicaNoAfectaUnaFila() throws Exception {
        String tokenPlano = "token-consumido-en-carrera";
        when(tokenRepository.findByTokenHashAndUtilizadoEnIsNull(hash(tokenPlano)))
                .thenReturn(Optional.of(token(tokenPlano, usuarioActivo(),
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5))));
        when(tokenRepository.consumirSiVigente(eq(hash(tokenPlano)), any(LocalDateTime.class)))
                .thenReturn(0);

        assertThrows(ResponseStatusException.class,
                () -> service.restablecerPassword(restablecer(tokenPlano, "NuevaClaveSegura2026!")));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void rechazaContrasenaInvalidaYConfirmacionDiferente() {
        RestablecerPasswordRequest noCoincide =
                restablecer("token", "NuevaClaveSegura2026!");
        noCoincide.setConfirmarPassword("OtraClaveSegura2026!");
        assertThrows(IllegalArgumentException.class, () -> service.restablecerPassword(noCoincide));

        String passwordSobreLimiteUtf8 = "á".repeat(37);
        assertThrows(IllegalArgumentException.class,
                () -> service.restablecerPassword(restablecer("token", passwordSobreLimiteUtf8)));
        verifyNoInteractions(tokenRepository);
    }

    private Usuario usuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNombre("Ana");
        usuario.setEmail("ana@luany.com");
        usuario.setEstado("ACTIVO");
        return usuario;
    }

    private SolicitudRecuperacionPasswordRequest solicitud(String email) {
        SolicitudRecuperacionPasswordRequest request = new SolicitudRecuperacionPasswordRequest();
        request.setEmail(email);
        return request;
    }

    private RestablecerPasswordRequest restablecer(String token, String password) {
        RestablecerPasswordRequest request = new RestablecerPasswordRequest();
        request.setToken(token);
        request.setNuevaPassword(password);
        request.setConfirmarPassword(password);
        return request;
    }

    private RecuperacionPasswordToken token(
            String tokenPlano, Usuario usuario, LocalDateTime expiraEn) {
        RecuperacionPasswordToken token = new RecuperacionPasswordToken();
        token.setUsuario(usuario);
        token.setTokenHash(hash(tokenPlano));
        token.setExpiraEn(expiraEn);
        token.setCreadoEn(LocalDateTime.now(ZoneOffset.UTC));
        return token;
    }

    private String hash(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
