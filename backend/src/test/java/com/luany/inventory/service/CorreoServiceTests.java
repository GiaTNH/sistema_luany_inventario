package com.luany.inventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CorreoServiceTests {

    @Test
    void enviaCredencialesALaApiDeBrevoPorHttps() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        CorreoService correoService = new CorreoService(
                builder.build(), "test-api-key", "noreply@luany.com", "LUANY");

        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-api-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"sender\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"email\":\"noreply@luany.com\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"name\":\"LUANY\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ana@luany.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ana.usuario")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("clave-temporal-segura")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"textContent\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"htmlContent\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "https://sistema-luany-inventario.onrender.com/")))
                .andRespond(withSuccess());

        assertDoesNotThrow(() ->
                correoService.enviarBienvenida(
                        "ana@luany.com", "Ana", "ana.usuario", "clave-temporal-segura"));
        servidor.verify();
    }

    @Test
    void enviaCorreoDeRecuperacionConVersionHtmlYTexto() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        CorreoService correoService = new CorreoService(
                builder.build(), "test-api-key", "noreply@luany.com", "LUANY");
        String enlace = "https://sistema-luany-inventario.onrender.com/#restablecer?token=secreto";

        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-api-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"htmlContent\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"textContent\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ana &amp; Luis")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(enlace)))
                .andRespond(withSuccess());

        correoService.enviarRecuperacion("ana@luany.com", "Ana & Luis", enlace);
        servidor.verify();
    }

    @Test
    void fallaSinExponerLaRespuestaDelProveedorNiLasCredenciales() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        CorreoService correoService = new CorreoService(
                builder.build(), "test-api-key", "noreply@luany.com", "LUANY");
        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"provider failure secret detail\"}"));

        CorreoEnvioException error = assertThrows(CorreoEnvioException.class,
                () -> correoService.enviarBienvenida(
                        "ana@luany.com", "Ana", "ana.usuario", "clave-temporal-segura"));

        assertFalse(error.getMessage().contains("provider failure"));
        assertFalse(error.getMessage().contains("test-api-key"));
        assertFalse(error.getMessage().contains("clave-temporal-segura"));
        servidor.verify();
    }

    @Test
    void falloDeBrevoEnRecuperacionNoExponeRespuestaNiDatosDelCorreo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        CorreoService correoService = new CorreoService(
                builder.build(), "test-api-key", "noreply@luany.com", "LUANY");
        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(header("api-key", "test-api-key"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"provider failure secret detail\"}"));

        CorreoRecuperacionException error = assertThrows(CorreoRecuperacionException.class,
                () -> correoService.enviarRecuperacion(
                        "ana@luany.com", "Ana", "https://example.com/token-secreto"));

        assertFalse(error.getMessage().contains("provider failure"));
        assertFalse(error.getMessage().contains("test-api-key"));
        assertFalse(error.getMessage().contains("token-secreto"));
        servidor.verify();
    }

    @Test
    void faltaApiKeyLanzaExcepcionSinIntentarEnviar() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        CorreoService correoService = new CorreoService(
                builder.build(), "", "noreply@luany.com", "LUANY");

        CorreoEnvioException error = assertThrows(CorreoEnvioException.class,
                () -> correoService.enviarBienvenida(
                        "ana@luany.com", "Ana", "ana.usuario", "clave-temporal-segura"));

        assertEquals("No se pudo enviar el correo de bienvenida.", error.getMessage());
        servidor.verify();
    }

    @Test
    void apiKeyNulaLanzaExcepcion() {
        CorreoService correoService = new CorreoService(
                RestClient.create(), null, "noreply@luany.com", "LUANY");

        assertThrows(CorreoEnvioException.class,
                () -> correoService.enviarBienvenida(
                        "ana@luany.com", "Ana", "ana.usuario", "clave-temporal-segura"));
    }

    @Test
    void remitenteNoConfiguradoFallaSinIntentarEnviar() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        CorreoService correoService = new CorreoService(
                builder.build(), "test-api-key", "", "LUANY");

        assertThrows(CorreoEnvioException.class,
                () -> correoService.enviarBienvenida(
                        "ana@luany.com", "Ana", "ana.usuario", "clave-temporal-segura"));
        servidor.verify();
    }
}
