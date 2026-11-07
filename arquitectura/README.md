# Arquitectura del sistema de gestión de inventarios - LUANY

Aplicación web cliente-servidor de tres capas, con API REST, desarrollada para gestionar los productos, el inventario y las operaciones del negocio LUANY.

## 1. Arquitectura general

El sistema está organizado en tres capas:

| Capa | Tecnología | Función |
|---|---|---|
| Presentación (Frontend) | HTML, CSS y JavaScript | Interfaz web para el inicio de sesión, la gestión de productos, usuarios y demás módulos del sistema. |
| Lógica de negocio (Backend / API) | Java 17 y Spring Boot | Procesa las solicitudes, aplica las reglas del negocio, gestiona la autenticación y comunica el frontend con la base de datos mediante API REST. |
| Datos (Base de datos) | MySQL 8 | Almacena la información de usuarios, productos, proveedores y las operaciones del inventario. |

El frontend se comunica con el backend mediante solicitudes HTTP/HTTPS e intercambio de datos en formato JSON. El backend utiliza SQL para consultar y actualizar la base de datos.

## 2. Tecnologías utilizadas

| Componente | Tecnología |
|---|---|
| Lenguajes del frontend | HTML, CSS y JavaScript |
| Lenguaje del backend | Java 17 |
| Framework del backend | Spring Boot |
| Arquitectura de comunicación | API REST |
| Seguridad | Spring Security |
| Cifrado de contraseñas | BCrypt |
| Base de datos | MySQL 8 |
| Gestión de dependencias | Maven |
| Repositorio de código | Git y GitHub |
| Despliegue de la aplicación | Render |
| Alojamiento de la base de datos | Aiven |

## 3. Capas del sistema

### 3.1. Capa de presentación (Frontend)

Es la parte del sistema con la que interactúan los usuarios desde el navegador web. Está desarrollada con HTML, CSS y JavaScript.

Contiene las interfaces correspondientes a los siguientes módulos:

- Login.
- Productos.
- Proveedores.
- Movimientos.
- Notas de venta.
- Pedidos.
- Alertas.
- Tablero Kanban.
- Reportes.
- Gestión de usuarios.

Las pantallas se organizan en archivos HTML, mientras que los estilos y las funciones de interacción se encuentran en sus respectivos archivos CSS y JavaScript.

### 3.2. Capa de lógica de negocio (Backend / API)

Está desarrollada con Java 17 y Spring Boot. Se encarga de procesar las solicitudes del frontend, aplicar las reglas del sistema y comunicarse con la base de datos.

La organización del backend sigue una estructura por responsabilidades:

| Paquete | Responsabilidad |
|---|---|
| `controller/` | Recibe las solicitudes HTTP y expone los endpoints de la API REST. |
| `service/` | Contiene la lógica de negocio y el procesamiento de las operaciones. |
| `repository/` | Gestiona las consultas y el acceso a la base de datos. |
| `model/` | Define las entidades que representan la información almacenada. |
| `config/` | Contiene la configuración de seguridad y otros componentes del sistema. |

### 3.3. Capa de datos (Base de datos)

Utiliza MySQL para almacenar y administrar la información del sistema.

La base de datos se encuentra alojada en Aiven, lo que permite que la aplicación desplegada en Render pueda acceder a ella mediante una conexión remota.

Las entidades y tablas corresponden a los módulos del sistema, entre ellos:

- Usuarios.
- Categorías.
- Productos.
- Proveedores.
- Clientes.
- Movimientos.
- Notas de venta.
- Detalle de venta.
- Pedidos.
- Detalle de pedido.

Son 10 tablas. La estructura vigente está en `database/schema_aiven.sql` y el diagrama actualizado en `database/modelo-er-actualizado.png`.

## 4. Seguridad y control de acceso

La seguridad se gestiona desde el backend mediante Spring Security.

| Característica | Descripción |
|---|---|
| Autenticación | Los usuarios ingresan al sistema mediante sus credenciales. |
| Roles | Se contempla el rol ADMIN y el rol EMPLEADO. |
| Creación de empleados | El administrador puede registrar empleados desde la gestión de usuarios. |
| Contraseñas | Se almacenan mediante hash BCrypt, nunca en texto plano. |
| Contraseña temporal | El sistema genera una contraseña temporal al crear un empleado. |
| Cambio de contraseña | Los nuevos empleados quedan marcados para realizar un cambio obligatorio de contraseña en su primer acceso. |
| Protección de solicitudes | Se utiliza Spring Security y protección CSRF para las operaciones que modifican información. |
| Sesiones | La autenticación utiliza sesiones administradas por el servidor. |

## 5. Despliegue del sistema

La aplicación utiliza los siguientes servicios:

| Servicio | Plataforma | Función |
|---|---|---|
| Aplicación web | Render | Aloja y ejecuta el backend Spring Boot y sirve los archivos del frontend. |
| Base de datos | Aiven | Aloja la base de datos MySQL. |
| Control de versiones | GitHub | Almacena el código fuente y permite organizar el trabajo del equipo. |

La conexión entre Render y Aiven se configura mediante variables de entorno, evitando almacenar las credenciales de acceso directamente en el código fuente.

## 6. Módulos y relación con el Product Backlog

| Módulo | Historias relacionadas |
|---|---|
| Seguridad y usuarios | PB-10 |
| Productos | PB-01, PB-04, PB-05, PB-16 |
| Proveedores | PB-11 |
| Movimientos | PB-02, PB-03, PB-12 |
| Notas de venta | PB-18 |
| Pedidos | PB-07, PB-08, PB-09, PB-13 |
| Alertas y tablero Kanban | PB-06, PB-14 |
| Reportes | PB-15, PB-17 |

Los módulos representan el alcance funcional del sistema. Su implementación se realiza de manera progresiva, de acuerdo con las tareas y prioridades establecidas en el Product Backlog.

## 7. Estructura del repositorio

La aplicación concentra el frontend y el backend dentro del proyecto Spring Boot.

```text
sistema_luany_inventario/
│
├── backend/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/luany/inventory/
│   │       │       ├── controller/
│   │       │       ├── model/
│   │       │       ├── repository/
│   │       │       ├── service/
│   │       │       └── config/
│   │       │
│   │       └── resources/
│   │           ├── static/
│   │           │   ├── css/
│   │           │   ├── js/
│   │           │   ├── img/
│   │           │   └── archivos HTML
│   │           │
│   │           └── application.properties
│   │
│   ├── pom.xml
│   └── mvnw
│
└── README.md
```

## 8. Consideraciones del sistema

- Las notas de venta son de control interno y no constituyen comprobantes de pago.
- El stock no debe quedar en valores negativos.
- Los movimientos deben registrar información que permita identificar la operación, su fecha y el usuario responsable.
- Las copias de respaldo de la base de datos deben formar parte de las medidas de mantenimiento y protección de la información.
- Las funcionalidades se desarrollan progresivamente y deben validarse antes de considerarse terminadas.

