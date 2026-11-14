package com.luany.inventory.service;

import com.luany.inventory.model.AlertaStock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
public class CorreoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorreoService.class);
    private static final String BREVO_EMAILS_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String URL_ACCESO = "https://sistema-luany-inventario.onrender.com/";

    private final RestClient restClient;
    private final String apiKey;
    private final String remitenteEmail;
    private final String remitenteNombre;

    @Autowired
    public CorreoService(
            @Value("${brevo.api-key:}") String apiKey,
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
        this.remitenteEmail = remitenteEmail;
        this.remitenteNombre = remitenteNombre;
    }

    public void enviarBienvenida(
            String destinatario,
            String nombre,
            String nombreUsuario,
            String passwordTemporal) {
        if (!configuracionCompleta()) {
            throw new CorreoEnvioException();
        }

        String contenidoTexto = """
                Hola %s:

                Tu cuenta de LUANY ya está lista.

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
                              <p style="margin:0; color:#625574; font-size:16px; line-height:25px;">Hola <strong style="color:#241442;">%s</strong>, tu cuenta ya está lista.</p>
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
                "sender", remitente(),
                "to", List.of(Map.of("email", destinatario)),
                "subject", "Bienvenido a LUANY",
                "textContent", contenidoTexto,
                "htmlContent", contenidoHtml);

        try {
            enviar(solicitud);
        } catch (RestClientException exception) {
            throw new CorreoEnvioException();
        }
    }

    public void enviarNotificacionCambioCorreo(String destinatario, String nombre) {
        if (!configuracionCompleta()) {
            LOGGER.error("No se pudo enviar la notificación de cambio de correo: falta configuración de Brevo.");
            throw new CorreoNotificacionException();
        }

        String contenidoTexto = """
                Hola %s:

                El correo electrónico asociado a tu cuenta de LUANY fue actualizado.
                El nuevo correo es: %s

                Si no reconoces este cambio, contacta con el administrador del sistema.
                Este es un mensaje automático de LUANY.
                """.formatted(nombre, destinatario);
        String contenidoHtml = """
                <!DOCTYPE html>
                <html lang="es"><head><meta charset="UTF-8"><title>Correo actualizado</title></head>
                <body style="font-family:Arial,Helvetica,sans-serif;color:#241442;">
                  <h1>LUANY</h1>
                  <p>Hola <strong>%s</strong>:</p>
                  <p>El correo asociado a tu cuenta fue actualizado. El nuevo correo es <strong>%s</strong>.</p>
                  <p>Si no reconoces este cambio, contacta con el administrador del sistema.</p>
                  <small>Este es un mensaje automático de LUANY.</small>
                </body></html>
                """.formatted(escaparHtml(nombre), escaparHtml(destinatario));
        Map<String, Object> solicitud = Map.of(
                "sender", remitente(),
                "to", List.of(Map.of("email", destinatario)),
                "subject", "Actualización del correo de tu cuenta LUANY",
                "textContent", contenidoTexto,
                "htmlContent", contenidoHtml);

        try {
            enviar(solicitud);
        } catch (RestClientException exception) {
            LOGGER.error("Falló el envío de la notificación de cambio de correo mediante Brevo.");
            throw new CorreoNotificacionException();
        }
    }

    public void enviarRecuperacion(String destinatario, String nombre, String enlace) {
        if (!configuracionCompleta()) {
            LOGGER.error("No se pudo enviar correo de recuperación: la configuración de Brevo está incompleta.");
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
                "sender", remitente(),
                "to", List.of(Map.of("email", destinatario)),
                "subject", "Restablece tu contraseña de LUANY",
                "textContent", contenidoTexto,
                "htmlContent", contenidoHtml);

        try {
            enviar(solicitud);
        } catch (RestClientException exception) {
            LOGGER.error("Falló el envío del correo de recuperación mediante Brevo.");
            throw new CorreoRecuperacionException();
        }
    }

    private Map<String, String> remitente() {
        return Map.of("name", remitenteNombre, "email", remitenteEmail);
    }

    private boolean configuracionCompleta() {
        return apiKey != null && !apiKey.isBlank()
                && remitenteEmail != null && !remitenteEmail.isBlank()
                && remitenteNombre != null && !remitenteNombre.isBlank();
    }

    private void enviar(Map<String, Object> solicitud) {
        restClient.post()
                .uri(BREVO_EMAILS_URL)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(solicitud)
                .retrieve()
                .toBodilessEntity();
    }

    private static String escaparHtml(String valor) {
        return valor
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public void enviarAlertaStock(List<AlertaStock> alertas, List<String> destinatarios) {
        if (!configuracionCompleta()) {
            throw new CorreoAlertaStockException();
        }
        if (alertas == null || alertas.isEmpty() || destinatarios == null || destinatarios.isEmpty()) {
            throw new IllegalArgumentException("No hay productos o administradores para enviar la alerta.");
        }

        StringBuilder contenidoTexto = new StringBuilder(
                "Se han identificado productos que necesitan revisión de inventario.\\n\\n");
        for (AlertaStock alerta : alertas) {
            contenidoTexto.append("- ").append(alerta.nombre())
                    .append(" | Stock: ").append(alerta.stockActual())
                    .append(" | Mínimo: ").append(alerta.stockMinimo())
                    .append(" | Estado: ").append(alerta.estado())
                    .append("\\n");
        }
        contenidoTexto.append("\\nIngresa al sistema LUANY para revisar el inventario.\\n")
                .append(URL_ACCESO)
                .append("\\n\\nEste es un mensaje automático de LUANY.");

        String filasHtml = alertas.stream()
                .map(alerta -> """
                        <tr>
                          <td style="padding:12px;border-bottom:1px solid #EEE8F8;">%s</td>
                          <td style="padding:12px;border-bottom:1px solid #EEE8F8;text-align:center;">%s</td>
                          <td style="padding:12px;border-bottom:1px solid #EEE8F8;text-align:center;">%s</td>
                          <td style="padding:12px;border-bottom:1px solid #EEE8F8;font-weight:700;color:%s;">%s</td>
                        </tr>
                        """.formatted(
                        escaparHtml(alerta.nombre()),
                        escaparHtml(String.valueOf(alerta.stockActual())),
                        escaparHtml(String.valueOf(alerta.stockMinimo())),
                        "AGOTADO".equalsIgnoreCase(alerta.estado()) ? "#D92D55" : "#B76B00",
                        escaparHtml(alerta.estado())))
                .collect(java.util.stream.Collectors.joining());

        String contenidoHtml = """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Alerta de inventario LUANY</title></head>
                <body style="margin:0;padding:28px 12px;background:#F7F3FF;font-family:Arial,Helvetica,sans-serif;color:#241442;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                        style="width:100%%;max-width:600px;background:#fff;border:1px solid #E9D8FF;border-radius:16px;overflow:hidden;">
                        <tr><td style="padding:24px;text-align:center;background:#873CF0;color:#fff;">
                          <div style="font-size:28px;font-weight:700;letter-spacing:1px;">LUANY</div>
                          <div style="font-size:12px;letter-spacing:2px;margin-top:4px;">ALERTA DE INVENTARIO</div>
                        </td></tr>
                        <tr><td style="padding:28px 24px 12px;">
                          <h2 style="margin:0 0 10px;color:#241442;">Productos que necesitan reposición</h2>
                          <p style="margin:0;color:#625574;line-height:1.6;">
                            Se encontraron <strong>%s productos</strong> con stock bajo o agotado.
                          </p>
                        </td></tr>
                        <tr><td style="padding:12px 24px 24px;">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                            style="border-collapse:collapse;font-size:14px;">
                            <thead><tr style="background:#F4F1FB;color:#5633A8;">
                              <th style="padding:12px;text-align:left;">Producto</th>
                              <th style="padding:12px;">Stock</th>
                              <th style="padding:12px;">Mínimo</th>
                              <th style="padding:12px;text-align:left;">Estado</th>
                            </tr></thead>
                            <tbody>%s</tbody>
                          </table>
                        </td></tr>
                        <tr><td style="padding:0 24px 24px;text-align:center;">
                          <a href="%s" style="display:inline-block;padding:13px 22px;background:#873CF0;color:#fff;border-radius:9px;text-decoration:none;font-weight:700;">
                            REVISAR INVENTARIO
                          </a>
                        </td></tr>
                        <tr><td style="padding:16px 24px;border-top:1px solid #F0EAF8;text-align:center;color:#81758F;font-size:12px;">
                          Este es un mensaje automático de LUANY.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(alertas.size(), filasHtml, URL_ACCESO);

        List<Map<String, String>> destinatariosCorreo = destinatarios.stream()
                .filter(email -> email != null && !email.isBlank())
                .distinct()
                .map(email -> Map.of("email", email))
                .toList();

        if (destinatariosCorreo.isEmpty()) {
            throw new IllegalArgumentException("No hay un correo de administrador válido.");
        }

        Map<String, Object> solicitud = Map.of(
                "sender", remitente(),
                "to", destinatariosCorreo,
                "subject", "LUANY - Alerta de stock: " + alertas.size() + " productos",
                "textContent", contenidoTexto.toString(),
                "htmlContent", contenidoHtml);

        try {
            enviar(solicitud);
        } catch (RestClientException exception) {
            LOGGER.error("Falló el envío de la alerta de stock mediante Brevo.");
            throw new CorreoAlertaStockException();
        }
    }

}
