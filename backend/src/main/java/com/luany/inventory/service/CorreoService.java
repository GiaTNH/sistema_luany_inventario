package com.luany.inventory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
public class CorreoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorreoService.class);
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

    public void enviarBienvenida(
            String destinatario,
            String nombre,
            String nombreUsuario,
            String passwordTemporal) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new CorreoEnvioException();
        }

        String contenidoTexto = """
                Hola %s:

                Tu cuenta de empleado de LUANY ya está lista.

                Nombre de usuario: %s
                Correo electrónico: %s
                Contraseña temporal: %s

                Ingresa en %s. Por seguridad, deberás cambiar la contraseña en tu primer inicio de sesión.
                Este es un mensaje automático de LUANY.
                """.formatted(nombre, nombreUsuario, destinatario, passwordTemporal, URL_ACCESO);

        String contenidoHtml = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>¡Bienvenido a LUANY!</title>
                </head>
                <body style="margin:0; padding:0; background-color:#F7F3FF; font-family:Arial, Helvetica, sans-serif; color:#241442;">
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%" style="background-color:#F7F3FF; padding:32px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="600" style="width:100%%; max-width:600px; background-color:#ffffff; border:1px solid #E9D8FF; border-radius:18px; overflow:hidden; box-shadow:0 8px 28px rgba(135,60,240,0.12);">
                          <tr>
                            <td align="center" style="padding:28px 24px; background-color:#873CF0; color:#ffffff;">
                              <div style="font-size:30px; line-height:36px; font-weight:700; letter-spacing:1px;">LUANY</div>
                              <div style="padding-top:5px; font-size:12px; line-height:18px; font-weight:600; letter-spacing:2px;">SISTEMA DE INVENTARIO</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:34px 32px 12px; text-align:center;">
                              <h1 style="margin:0 0 16px; color:#210D49; font-size:27px; line-height:34px;">¡Bienvenido a LUANY!</h1>
                              <p style="margin:0; color:#625574; font-size:16px; line-height:25px;">Hola <strong style="color:#241442;">%s</strong>, tu cuenta de empleado ya está lista.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:22px 32px 10px;">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%" style="background-color:#F7F3FF; border:1px solid #E9D8FF; border-radius:12px;">
                                <tr>
                                  <td style="padding:20px 22px 8px; color:#81758F; font-size:12px; line-height:18px; font-weight:700; letter-spacing:1px; text-transform:uppercase;">Nombre de usuario</td>
                                </tr>
                                <tr>
                                  <td style="padding:0 22px 18px; color:#241442; font-size:16px; line-height:23px; word-break:break-word;">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:20px 22px 8px; color:#81758F; font-size:12px; line-height:18px; font-weight:700; letter-spacing:1px; text-transform:uppercase;">Correo electrónico</td>
                                </tr>
                                <tr>
                                  <td style="padding:0 22px 18px; color:#241442; font-size:16px; line-height:23px; word-break:break-word;">%s</td>
                                </tr>
                                <tr>
                                  <td style="padding:0 22px 8px; color:#81758F; font-size:12px; line-height:18px; font-weight:700; letter-spacing:1px; text-transform:uppercase;">Contraseña temporal</td>
                                </tr>
                                <tr>
                                  <td style="padding:0 22px 20px; color:#241442; font-family:Consolas, 'Courier New', monospace; font-size:16px; line-height:24px; font-weight:700; word-break:break-all;">%s</td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td align="center" style="padding:22px 32px 26px;">
                              <a href="%s" target="_blank" style="display:inline-block; padding:15px 28px; border-radius:10px; background-color:#873CF0; color:#ffffff; font-size:14px; line-height:20px; font-weight:700; letter-spacing:0.5px; text-decoration:none;">INGRESAR A LUANY</a>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 32px 28px;">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%" style="background-color:#FFF7ED; border:1px solid #FED7AA; border-radius:10px;">
                                <tr>
                                  <td style="padding:15px 17px; color:#7C4215; font-size:14px; line-height:22px;">
                                    <strong>Importante:</strong> por seguridad, deberás cambiar tu contraseña en tu primer inicio de sesión.
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td align="center" style="padding:18px 24px; border-top:1px solid #F0EAF8; color:#81758F; font-size:12px; line-height:19px;">
                              Este es un mensaje automático de LUANY. Por favor, no respondas a este correo.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(escaparHtml(nombre), escaparHtml(nombreUsuario), escaparHtml(destinatario),
                escaparHtml(passwordTemporal), URL_ACCESO);

        Map<String, Object> solicitud = Map.of(
                "from", remitente,
                "to", List.of(destinatario),
                "subject", "Bienvenido a LUANY",
                "text", contenidoTexto,
                "html", contenidoHtml);

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

    public void enviarRecuperacion(String destinatario, String nombre, String enlace) {
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.error("No se pudo enviar correo de recuperación: MAIL_API_KEY no está configurada.");
            throw new CorreoRecuperacionException();
        }

        String contenidoTexto = """
                Hola %s:

                Recibimos una solicitud para restablecer la contraseña de tu cuenta de LUANY.
                Usa este enlace dentro de los próximos 30 minutos:

                %s

                Si no solicitaste este cambio, puedes ignorar este mensaje.
                Este es un mensaje automático de LUANY.
                """.formatted(nombre, enlace);
        String contenidoHtml = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Recupera tu contraseña de LUANY</title>
                </head>
                <body style="margin:0; padding:0; background-color:#F7F3FF; font-family:Arial, Helvetica, sans-serif; color:#241442;">
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%%" style="background-color:#F7F3FF; padding:32px 12px;">
                    <tr><td align="center">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="600" style="width:100%%; max-width:600px; background-color:#ffffff; border:1px solid #E9D8FF; border-radius:18px;">
                        <tr><td align="center" style="padding:28px 24px; background-color:#873CF0; color:#ffffff;">
                          <div style="font-size:30px; line-height:36px; font-weight:700; letter-spacing:1px;">LUANY</div>
                          <div style="padding-top:5px; font-size:12px; line-height:18px; font-weight:600; letter-spacing:2px;">SISTEMA DE INVENTARIO</div>
                        </td></tr>
                        <tr><td style="padding:34px 32px 12px; text-align:center;">
                          <h1 style="margin:0 0 16px; color:#210D49; font-size:26px; line-height:34px;">Restablece tu contraseña</h1>
                          <p style="margin:0; color:#625574; font-size:16px; line-height:25px;">Hola <strong style="color:#241442;">%s</strong>, recibimos una solicitud para cambiar la contraseña de tu cuenta.</p>
                        </td></tr>
                        <tr><td align="center" style="padding:22px 32px 24px;">
                          <a href="%s" target="_blank" style="display:inline-block; padding:15px 28px; border-radius:10px; background-color:#873CF0; color:#ffffff; font-size:14px; line-height:20px; font-weight:700; text-decoration:none;">RESTABLECER CONTRASEÑA</a>
                        </td></tr>
                        <tr><td style="padding:0 32px 28px; color:#625574; font-size:14px; line-height:22px; text-align:center;">
                          Este enlace vence en 30 minutos y solo puede utilizarse una vez. Si no solicitaste este cambio, ignora este correo.
                        </td></tr>
                        <tr><td align="center" style="padding:18px 24px; border-top:1px solid #F0EAF8; color:#81758F; font-size:12px; line-height:19px;">
                          Este es un mensaje automático de LUANY. Por favor, no respondas a este correo.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escaparHtml(nombre), escaparHtml(enlace));

        Map<String, Object> solicitud = Map.of(
                "from", remitente,
                "to", List.of(destinatario),
                "subject", "Restablece tu contraseña de LUANY",
                "text", contenidoTexto,
                "html", contenidoHtml);

        try {
            restClient.post()
                    .uri(RESEND_EMAILS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(solicitud)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            LOGGER.error("Falló el envío del correo de recuperación mediante Resend.", exception);
            throw new CorreoRecuperacionException();
        }
    }

    private static String escaparHtml(String valor) {
        return valor
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
