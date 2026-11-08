package com.luany.inventory.service;

import com.luany.inventory.model.RecuperacionPasswordToken;
import com.luany.inventory.model.Usuario;
import com.luany.inventory.repository.RecuperacionPasswordTokenRepository;
import com.luany.inventory.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecuperacionPasswordTokenServiceTests {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RecuperacionPasswordTokenRepository tokenRepository;

    private RecuperacionPasswordTokenService service;

    @BeforeEach
    void setUp() {
        service = new RecuperacionPasswordTokenService(usuarioRepository, tokenRepository);
    }

    @Test
    void crearSolicitudInvalidaTokensPreviosYPersisteSoloHashDelTokenNuevo() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("ana@luany.com");
        usuario.setNombre("Ana");
        usuario.setEstado("ACTIVO");
        when(usuarioRepository.buscarPorEmailParaActualizar("ana@luany.com"))
                .thenReturn(Optional.of(usuario));

        var solicitud = service.crearSolicitud("ana@luany.com").orElseThrow();

        ArgumentCaptor<RecuperacionPasswordToken> captor =
                ArgumentCaptor.forClass(RecuperacionPasswordToken.class);
        verify(tokenRepository).invalidarNoUtilizados(eq(1L), any(LocalDateTime.class));
        verify(tokenRepository).saveAndFlush(captor.capture());
        String tokenPlano = solicitud.enlace().substring(solicitud.enlace().indexOf("token=") + 6);
        RecuperacionPasswordToken guardado = captor.getValue();

        assertEquals(32, Base64.getUrlDecoder().decode(tokenPlano).length);
        assertEquals(hash(tokenPlano), guardado.getTokenHash());
        assertFalse(guardado.getTokenHash().contains(tokenPlano));
        assertEquals(30, java.time.Duration.between(
                guardado.getCreadoEn(), guardado.getExpiraEn()).toMinutes());
        assertEquals(guardado.getTokenHash(), solicitud.tokenHash());
    }

    @Test
    void invalidarTokenMarcaUnicamenteElTokenSolicitado() {
        service.invalidarToken("hash-especifico");

        verify(tokenRepository).invalidarNoUtilizado(
                eq("hash-especifico"), any(LocalDateTime.class));
    }

    private String hash(String valor) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(valor.getBytes(StandardCharsets.UTF_8)));
    }
}
