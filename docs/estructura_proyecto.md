# Estructura del Proyecto

El proyecto está organizado bajo una arquitectura en capas limpia utilizando Spring Boot:

```text
src/
├── main/
│   ├── java/com/luany/inventory/
│   │   ├── controller/   # Gestiona las peticiones HTTP y endpoints REST del sistema.
│   │   ├── model/        # Define las entidades JPA que representan las tablas de la BD.
│   │   ├── repository/   # Interfaces de Spring Data JPA para la persistencia de datos.
│   │   ├── service/      # Contiene la lógica de negocio y la integración con la IA.
│   │   └── InventoryApplication.java # Clase principal de arranque de Spring Boot.
│   └── resources/
│       └── application.properties # Configuración de conexión y base de datos.
