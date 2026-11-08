# sistema_luany_inventario
Sistema de gestión de inventarios para optimizar el proceso de abastecimiento de la empresa Luany.

Proyecto: Desarrollo de un sistema de gestión de inventarios aplicando Scrum y Kanban para optimizar el proceso de abastecimiento en la empresa Luany.

Curso: Metodologías Ágiles - Universidad Privada San Juan Bautista (2026-II)

## Cómo ejecutar el sistema

El sistema usa Java 17, Spring Boot y MySQL. El código está en `backend/`.

1. Crear la base de datos con `database/schema_aiven.sql` y luego ejecutar `database/registro_inicial.sql` y `database/recuperacion_password.sql`; si se quiere, cargar también `database/datos_ejemplo.sql`. Ver `database/README.md`.
2. Definir las variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD` (y la clave de Gemini para el asistente, según `backend/src/main/resources/application.properties`).
3. Desde la carpeta `backend/`: `./mvnw spring-boot:run`
4. Abrir `http://localhost:8080`

Para desplegar en Render, usar `backend` como directorio raíz; ahí está el `Dockerfile`. La base de datos de producción está en Aiven (MySQL 8, SSL obligatorio) y el backend usa `spring.jpa.hibernate.ddl-auto=validate`: la estructura solo se cambia con scripts SQL.

Para enviar correos de bienvenida, configurar en el servicio de Render `MAIL_API_KEY` con la clave secreta de Resend. `MAIL_FROM_EMAIL` y `MAIL_FROM_NAME` son opcionales y usan por defecto `onboarding@resend.dev` y `LUANY`; en producción, el remitente debe estar verificado en Resend.

### Registro inicial del administrador

Antes del despliegue, ejecutar `database/registro_inicial.sql` en la base `defaultdb` de Aiven usando un cliente MySQL (por ejemplo, MySQL Workbench). El script crea la tabla de control de forma idempotente: la primera vez inicializa `completado=1` si ya hay usuarios o `completado=0` si no hay ninguno. Si la fila ya existe, volver a ejecutar el script conserva su valor actual.

Al terminar la validación del sistema, eliminar únicamente las cuentas de prueba que se hayan creado para esa validación; no eliminar cuentas reales. Confirmar que `usuarios` quedó vacía y habilitar el registro inicial con una actualización manual:

```sql
UPDATE registro_inicial
SET completado = 0
WHERE id = 1
  AND NOT EXISTS (SELECT 1 FROM usuarios);
```

Registrar inmediatamente al administrador definitivo desde la pantalla de acceso. No volver a habilitar el registro después de crear al administrador.
