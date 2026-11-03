# sistema_luany_inventario
Sistema de gestión de inventarios para optimizar el proceso de abastecimiento de la empresa Luany.

Proyecto: Desarrollo de un sistema de gestión de inventarios aplicando Scrum y Kanban para optimizar el proceso de abastecimiento en la empresa Luany.

Curso: Metodologías Ágiles - Universidad Privada San Juan Bautista (2026-II)

## Cómo ejecutar el sistema

El sistema usa Java 17, Spring Boot y MySQL. El código está en `backend/`.

1. Crear la base de datos con los scripts de `database/`.
2. Definir las variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD` (y la clave de Gemini para el asistente, según `backend/src/main/resources/application.properties`).
3. Desde la carpeta `backend/`: `./mvnw spring-boot:run`
4. Abrir `http://localhost:8080`

Para desplegar en Render, usar `backend` como directorio raíz; ahí está el `Dockerfile`.
