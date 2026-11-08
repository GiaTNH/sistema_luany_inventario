package com.luany.inventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
    void enviaCredencialesALaApiDeResendPorHttps() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        CorreoService correoService = new CorreoService(
                builder.build(), "test-api-key", "onboarding@resend.dev", "LUANY");

        servidor.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ana@luany.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("clave-temporal-segura")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "https://sistema-luany-inventario.onrender.com/")))
                .andRespond(withSuccess());

        assertDoesNotThrow(() ->
                correoService.enviarBienvenida("ana@luany.com", "Ana", "clave-temporal-segura"));
        servidor.verify();
    }

    @Test
    void enviaCorreoDeRecuperacionConVersionHtmlYTexto() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        CorreoService correoService = new CorreoService(
                builder.build(), "test-api-key", "onboarding@resend.dev", "LUANY");
        String enlace = "https://sistema-luany-inventario.onrender.com/#restablecer?token=secreto";

        servidor.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"html\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"text\"")))
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
                builder.build(), "test-api-key", "onboarding@resend.dev", "LUANY");
        servidor.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"provider failure secret detail\"}"));

        CorreoEnvioException error = assertThrows(CorreoEnvioException.class,
                () -> correoService.enviarBienvenida(
                        "ana@luany.com", "Ana", "clave-temporal-segura"));

        assertFalse(error.getMessage().contains("provider failure"));
        assertFalse(error.getMessage().contains("test-api-key"));
        assertFalse(error.getMessage().contains("clave-temporal-segura"));
        servidor.verify();
    }

    @Test
    void faltaApiKeyLanzaExcepcionSinIntentarEnviar() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        CorreoService correoService = new CorreoService(
                builder.build(), "", "onboarding@resend.dev", "LUANY");

        CorreoEnvioException error = assertThrows(CorreoEnvioException.class,
                () -> correoService.enviarBienvenida(
                        "ana@luany.com", "Ana", "clave-temporal-segura"));

        assertEquals("No se pudo enviar el correo de bienvenida.", error.getMessage());
        servidor.verify();
    }

    @Test
    void apiKeyNulaLanzaExcepcion() {
        CorreoService correoService = new CorreoService(
                RestClient.create(), null, "onboarding@resend.dev", "LUANY");

        assertThrows(CorreoEnvioException.class,
                () -> correoService.enviarBienvenida(
                        "ana@luany.com", "Ana", "clave-temporal-segura"));
    }
}
