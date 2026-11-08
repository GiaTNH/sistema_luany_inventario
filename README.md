# sistema_luany_inventario
Sistema de gestión de inventarios para optimizar el proceso de abastecimiento de la empresa Luany.

Proyecto: Desarrollo de un sistema de gestión de inventarios aplicando Scrum y Kanban para optimizar el proceso de abastecimiento en la empresa Luany.

Curso: Metodologías Ágiles - Universidad Privada San Juan Bautista (2026-II)

## Cómo ejecutar el sistema

El sistema usa Java 17, Spring Boot y MySQL. El código está en `backend/`.

1. Crear la base de datos con `database/schema_aiven.sql` (y, si se quiere, `database/datos_ejemplo.sql`). Ver `database/README.md`.
2. Definir las variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD` (y la clave de Gemini para el asistente, según `backend/src/main/resources/application.properties`).
3. Desde la carpeta `backend/`: `./mvnw spring-boot:run`
4. Abrir `http://localhost:8080`

Para desplegar en Render, usar `backend` como directorio raíz; ahí está el `Dockerfile`. La base de datos de producción está en Aiven (MySQL 8, SSL obligatorio) y el backend usa `spring.jpa.hibernate.ddl-auto=validate`: la estructura solo se cambia con scripts SQL.

Para enviar correos de bienvenida, configurar en el servicio de Render `MAIL_API_KEY` con la clave secreta de Resend. `MAIL_FROM_EMAIL` y `MAIL_FROM_NAME` son opcionales y usan por defecto `onboarding@resend.dev` y `LUANY`; en producción, el remitente debe estar verificado en Resend.
