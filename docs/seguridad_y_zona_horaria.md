# Seguridad y zona horaria

## Sesiones y autenticación

- Los endpoints `/api/**` requieren una sesión autenticada y un rol `ADMIN` o `EMPLEADO`; `/api/usuarios/**` y el envío de alertas por correo requieren `ADMIN`.
- Las solicitudes que modifican datos conservan CSRF. Las cookies de sesión son `HttpOnly`, `SameSite=Lax` y `Secure` por defecto.
- Se permiten hasta tres sesiones por usuario y la sesión vence tras 30 minutos de inactividad. El cierre de sesión invalida la sesión del servidor.
- El login limita a cinco fallos por correo y dirección IP en 15 minutos. La recuperación tiene un límite equivalente y siempre devuelve el mismo mensaje, sin confirmar si existe el correo.
- Las contraseñas nuevas requieren de 12 a 72 caracteres con mayúsculas, minúsculas, números y símbolos; se almacenan con BCrypt. Los tokens de recuperación son aleatorios, se almacenan como hash y vencen o se consumen una sola vez.
- El contador anti-fuerza-bruta vive en memoria del proceso. En una instancia protege el período completo de bloqueo; si Render se escala a varias instancias, los contadores no se comparten ni sobreviven reinicios. Para ese despliegue se necesita un almacén compartido (por ejemplo, Redis o una tabla dedicada) antes de escalar.

## Configuración en Render

Conservar las variables existentes de conexión a la base de datos y de correo. Agregar o confirmar:

| Variable | Valor recomendado | Uso |
|---|---|---|
| `SESSION_COOKIE_SECURE` | `true` | Cookies seguras en HTTPS. |
| `DB_CONNECTION_INIT_SQL` | `SET time_zone = '-05:00'` | Fija la zona horaria de sesión MySQL en UTC-05:00. |

`America/Lima` también está fijada por el backend, Hibernate, Jackson y el `Clock` compartido; no depende del huso horario por defecto del contenedor. Para desarrollo HTTP local, puede usarse `SESSION_COOKIE_SECURE=false`. La aplicación confía en los encabezados de proxy de Render para reconocer HTTPS.

## Aiven y fechas históricas

No se requiere cambiar el esquema de Aiven: las fechas operativas existentes usan `DATETIME`/`DATETIME(6)`, que no contienen zona horaria. La aplicación escribe y presenta las nuevas fechas como hora civil de `America/Lima`; el ajuste JDBC no transforma automáticamente esos valores históricos.

La causa probable de las horas adelantadas era que `LocalDateTime.now()` dependía de la zona de la JVM (Render suele usar UTC) y las columnas no guardan esa zona. El frontend recibía una fecha sin sufijo de zona y podía mostrar la hora almacenada como si ya fuera hora peruana, sin convertir esos cinco horas de diferencia.

No ejecutar una conversión masiva. El esquema y los datos almacenados no permiten determinar por sí solos si cada fecha histórica se escribió en UTC o en hora peruana. Antes de una eventual migración, comparar registros con operaciones de fecha y hora conocidas y respaldar la base. Si se confirma que algunos valores se guardaron en UTC, diseñar una migración específica para esos registros; si ya eran hora peruana, no convertirlos.

Los tokens de recuperación son datos técnicos y siguen usando UTC, independientemente de las fechas operativas.

## Verificación

Ejecutar la suite completa con:

```powershell
backend\mvnw.cmd -f backend\pom.xml clean test
```

## Seguridad avanzada

Se añadieron controles persistentes para:

- intentos de login por cuenta e IP;
- bloqueo temporal al alcanzar el quinto intento;
- alerta agrupada y con enfriamiento al tercer intento;
- bloqueo/desbloqueo manual administrativo;
- deshabilitación y reactivación de cuentas;
- auditoría de login, logout, cambios de contraseña, roles, creación/desactivación y accesos denegados;
- invalidación de sesiones al bloquear o deshabilitar una cuenta.

Variables adicionales en Render:

| Variable | Valor recomendado |
|---|---|
| `SECURITY_ADMIN_EMAIL` | Correo real del administrador que recibirá alertas. |
| `SECURITY_LOGIN_WINDOW_MINUTES` | `15` |
| `SECURITY_LOGIN_ALERT_AT` | `3` |
| `SECURITY_LOGIN_BLOCK_AT` | `5` |
| `SECURITY_LOGIN_ALERT_COOLDOWN_MINUTES` | `15` |
| `SECURITY_CORS_ALLOWED_ORIGINS` | Origen HTTPS de Render, por ejemplo `https://sistema-luany-inventario.onrender.com` |

Antes de desplegar, ejecutar `database/seguridad_avanzada.sql` en MySQL/Aiven. También están documentados los nuevos modelos e índices en `database/schema_aiven.sql`.

## Despliegue seguro

1. Ejecutar primero `database/seguridad_avanzada.sql` en Aiven, usando una copia o ventana mantenida.
2. Confirmar que las columnas y tablas nuevas existen con `SHOW COLUMNS FROM usuarios` y `SHOW TABLES LIKE '%seguridad%'`.
3. Configurar todas las variables `DB_*`, `BREVO_*`, `SESSION_COOKIE_SECURE`, `SECURITY_*` y `PORT` en Render.
4. Mantener `SERVER.forward` administrado por Render; no agregar un proxy inverso propio que permita cabeceras X-Forwarded-For manipulables.
5. Desplegar una sola instancia durante la validación inicial y comprobar login, logout, usuarios, alertas y panel de seguridad.
6. Verificar manualmente la alerta real con un usuario de prueba que falle cinco veces; confirmar que Brevo recibe el correo y que no existan contraseñas ni tokens en el contenido.
7. Hacer respaldo antes de desplegar y no ejecutar scripts de reinicio de datos ni migraciones históricas de fechas.

MFA TOTP no se activó en esta entrega. La arquitectura actual de sesión puede incorporarlo, pero requiere preparación de secreto cifrado, alta de códigos de recuperación, pruebas de recuperación y un período de migración. No se activó para evitar bloquear el login actual sin un flujo completo de activación.

Riesgos todavía no cubiertos:

- el bloqueo por IP debe configurarse detrás de un proxy con información de cliente validada;
- CORS y CSP deben revisarse nuevamente si se incorporan recursos externos;
- MFA TOTP continúa pendiente;
- un despliegue con varias instancias ya comparte los intentos y bloqueos porque se persisten en MySQL;
- las pruebas de correo siguen dependiendo de la configuración real de Brevo.
