package com.luany.inventory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
public class CorreoService {

    private static final String RESEND_EMAILS_URL = "https://api.resend.com/emails";
    private static final String URL_ACCESO = "https://sistema-luany-inventario.onrender.com/";

    private final RestClient restClient;
    private final String apiKey;
    private final String remitente;

    @Autowired
    public CorreoService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${mail.from.email}") String remitenteEmail,
            @Value("${mail.from.name}") String remitenteNombre) {
        this(RestClient.create(), apiKey, remitenteEmail, remitenteNombre);
    }

    CorreoService(
            RestClient restClient,
            String apiKey,
            String remitenteEmail,
            String remitenteNombre) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.remitente = remitenteNombre + " <" + remitenteEmail + ">";
    }

    public void enviarBienvenida(String destinatario, String nombre, String passwordTemporal) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new CorreoEnvioException();
        }

        String contenido = """
                Hola %s:

                Tu cuenta de empleado de LUANY ya está lista.

                Correo: %s
                Contraseña temporal: %s

                Ingresa en %s. Por seguridad, deberás cambiar la contraseña en tu primer inicio de sesión.
                """.formatted(nombre, destinatario, passwordTemporal, URL_ACCESO);

        Map<String, Object> solicitud = Map.of(
                "from", remitente,
                "to", List.of(destinatario),
                "subject", "Bienvenido a LUANY",
                "text", contenido);

        try {
            restClient.post()
                    .uri(RESEND_EMAILS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(solicitud)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new CorreoEnvioException();
        }
    }
}
