package com.luany.inventory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GeminiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GeminiClient(@Value("${gemini.api.key:}") String apiKey,
                        @Value("${gemini.model}") String model,
                        @Value("${gemini.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(45));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @SuppressWarnings("unchecked")
    public String generar(String instruccionSistema, String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar gemini.api.key en application-local.properties");
        }

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", instruccionSistema))),
                "contents", List.of(Map.of("role", "user",
                        "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 2048)
        );

        try {
            Map<String, Object> respuesta = restClient.post()
                    .uri(baseUrl + "/models/" + model + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return extraerTexto(respuesta);

        } catch (RestClientResponseException e) {
            int codigo = e.getStatusCode().value();
            if (codigo == 429) {
                throw new IllegalStateException(
                        "El asistente alcanzó el límite de uso gratuito. Espera un minuto e intenta de nuevo.");
            }
            if (codigo == 404) {
                throw new IllegalStateException(
                        "El modelo configurado (" + model + ") no existe. Revisa gemini.model.");
            }
            if (codigo == 400 || codigo == 403) {
                throw new IllegalStateException(
                        "Gemini rechazó la solicitud (" + codigo + "). Revisa la clave y el modelo.");
            }
            throw new IllegalStateException("Gemini no está disponible por ahora (" + codigo + ")");

        } catch (ResourceAccessException e) {
            throw new IllegalStateException(
                    "No se pudo conectar con Gemini. Revisa la conexión a internet.");
        }
    }

    @SuppressWarnings("unchecked")
    private String extraerTexto(Map<String, Object> respuesta) {
        List<Map<String, Object>> candidatos =
                respuesta == null ? null : (List<Map<String, Object>>) respuesta.get("candidates");
        if (candidatos == null || candidatos.isEmpty()) {
            throw new IllegalStateException("Gemini no devolvió respuesta (puede haber sido bloqueada).");
        }
        Map<String, Object> contenido = (Map<String, Object>) candidatos.get(0).get("content");
        List<Map<String, Object>> partes =
                contenido == null ? null : (List<Map<String, Object>>) contenido.get("parts");
        if (partes == null || partes.isEmpty()) {
            throw new IllegalStateException("Gemini devolvió una respuesta vacía.");
        }
        StringBuilder texto = new StringBuilder();
        for (Map<String, Object> parte : partes) {
            Object t = parte.get("text");
            if (t != null) {
                texto.append(t);
            }
        }
        return texto.toString().trim();
    }
}