# Tecnologías utilizadas en el proyecto LUANY

## 1. Descripción general

El Sistema Inteligente Integrador para la Gestión de Inventario de la empresa LUANY es una aplicación backend desarrollada para administrar productos, categorías, proveedores, movimientos de inventario y alertas de stock. También contempla un asistente inteligente para apoyar las consultas relacionadas con la gestión del inventario.

El sistema utiliza una arquitectura por capas, que permite organizar el código, separar las responsabilidades y facilitar el mantenimiento y la ampliación de sus funcionalidades.

## 2. Tecnologías principales

| Tecnología | Versión o componente | Función en el proyecto |
|---|---|---|
| Java | 17 | Lenguaje utilizado para desarrollar la aplicación. |
| Spring Boot | 4.1.1 | Facilita la configuración y ejecución del backend. |
| Spring Web MVC | Dependencia de Spring Boot | Permite crear los servicios y endpoints de la API REST. |
| Spring Data JPA | Dependencia de Spring Boot | Facilita las operaciones de acceso y persistencia de datos. |
| Hibernate | ORM | Relaciona las entidades Java con las tablas de MySQL. |
| MySQL | Servidor 8.4.13 en el entorno local | Almacena la información del inventario. |
| MySQL Connector/J | Controlador JDBC | Permite la conexión entre Java y MySQL. |
| Maven | Maven Wrapper | Administra las dependencias y permite compilar, probar y ejecutar el proyecto. |
| Jakarta Validation | Dependencia de Spring Boot | Permite validar los datos recibidos por la API. |
| Lombok | Dependencia del proyecto | Reduce código repetitivo mediante anotaciones. |
| Spring Security Crypto | Módulo de seguridad criptográfica | Proporciona herramientas para proteger contraseñas mediante funciones de hash. |
| Google Gemini API | Modelo configurado: `gemini-3.5-flash-lite` | Permite integrar capacidades de inteligencia artificial en el asistente del sistema. |
| JUnit y herramientas de prueba de Spring | Dependencias de prueba | Permiten verificar el funcionamiento de los componentes de la aplicación. |

## 3. Herramientas de desarrollo

- **Visual Studio Code:** editor utilizado para modificar, organizar y ejecutar el código fuente.
- **MySQL Workbench:** herramienta gráfica utilizada para conectarse a MySQL, ejecutar consultas y revisar las bases de datos.
- **Git:** sistema de control de versiones que permite registrar los cambios realizados en el proyecto.
- **GitHub:** plataforma utilizada para almacenar y compartir el repositorio del proyecto.
- **PowerShell:** terminal utilizada para ejecutar comandos de Maven, Java y Git.

## 4. Arquitectura del sistema

El backend está organizado en capas para separar las responsabilidades de cada componente.

### 4.1. Capa de controladores (Controller)

Recibe las solicitudes HTTP, identifica la operación solicitada y devuelve las respuestas de la API.

Incluye controladores relacionados con productos, categorías, proveedores, movimientos, alertas, autenticación y asistencia inteligente.

### 4.2. Capa de servicios (Service)

Contiene la lógica de negocio del sistema. Coordina las operaciones necesarias para registrar, consultar, actualizar y eliminar información, según las funcionalidades implementadas.

### 4.3. Capa de repositorios (Repository)

Gestiona el acceso a los datos mediante las interfaces de persistencia y las operaciones proporcionadas por Spring Data JPA.

### 4.4. Capa de modelos (Model)

Contiene las entidades y los objetos utilizados para representar los datos del sistema, como productos, categorías, proveedores, movimientos y usuarios.

### 4.5. Capa de configuración

Contiene los archivos y componentes necesarios para configurar la aplicación, la conexión con la base de datos y otros servicios.

## 5. Base de datos

El sistema utiliza MySQL como gestor de base de datos relacional. La base de datos local se denomina `luany_inventario`.

Entre las principales entidades del sistema se encuentran:

- Categorías.
- Productos.
- Proveedores.
- Movimientos de inventario.
- Usuarios.
- Alertas de stock.

Las relaciones entre las entidades permiten organizar la información y mantener la integridad de los datos.

Hibernate administra la correspondencia entre las entidades Java y las tablas de la base de datos, de acuerdo con la configuración de JPA.

## 6. API REST

La aplicación expone servicios HTTP para realizar operaciones sobre los recursos del inventario.

Por ejemplo, el controlador de productos utiliza la ruta base `/api/productos` y contempla las siguientes operaciones:

| Método HTTP | Endpoint | Descripción |
|---|---|---|
| GET | `/api/productos` | Lista los productos registrados. |
| GET | `/api/productos/{id}` | Consulta un producto por su identificador. |
| GET | `/api/productos/buscar?nombre=texto` | Busca productos por nombre. |
| GET | `/api/productos/stock-bajo` | Consulta productos con stock bajo. |
| POST | `/api/productos` | Registra un producto. |
| PUT | `/api/productos/{id}` | Actualiza un producto. |
| DELETE | `/api/productos/{id}` | Elimina un producto. |

Las operaciones se ejecutan mediante solicitudes HTTP y utilizan datos JSON cuando corresponde.

## 7. Validación y seguridad

El proyecto incorpora mecanismos de validación de datos y herramientas criptográficas para contribuir a la protección de la información.

Las credenciales de conexión a MySQL y las claves de servicios externos deben mantenerse en la configuración local o en variables de entorno. No deben publicarse contraseñas, claves API ni otros datos sensibles en el repositorio.

La protección efectiva de cada endpoint debe verificarse de acuerdo con la configuración de seguridad implementada en el código.

## 8. Inteligencia artificial

El proyecto contempla la integración con Google Gemini API para incorporar un asistente inteligente contextual.

La configuración identifica el endpoint base de la API y el modelo `gemini-3.5-flash-lite`. Las funciones disponibles dependen de la implementación del servicio y del controlador correspondiente.

La clave de acceso a Gemini debe mantenerse fuera de los archivos públicos del repositorio.

## 9. Configuración y ejecución

Para trabajar con el proyecto se requiere:

1. Instalar Java JDK 17.
2. Instalar y ejecutar MySQL Server.
3. Crear la base de datos `luany_inventario` si todavía no existe.
4. Configurar el usuario y la contraseña de MySQL en el entorno local.
5. Configurar la clave de Gemini si se utilizará el asistente inteligente.
6. Abrir la carpeta raíz del proyecto en Visual Studio Code.
7. Ejecutar las pruebas y después iniciar la aplicación con Maven.

Los comandos para Windows son:

```powershell
.\mvnw.cmd test
```

Para iniciar la aplicación:

```powershell
.\mvnw.cmd spring-boot:run
```

La aplicación utiliza el puerto `8080` en la configuración actual. Una vez iniciada, se pueden probar los endpoints disponibles desde el navegador o una herramienta de solicitudes HTTP.

## 10. Conclusión

Las tecnologías utilizadas permiten desarrollar un backend organizado, conectado a una base de datos relacional y preparado para gestionar las operaciones del inventario mediante una API REST.

La combinación de Java, Spring Boot, Spring Data JPA, Hibernate y MySQL facilita la persistencia de datos y la separación de responsabilidades. Asimismo, Maven permite administrar las dependencias y ejecutar las pruebas, mientras que Git y GitHub facilitan el control de versiones y la colaboración durante el desarrollo.

La integración con Google Gemini API constituye un componente adicional para incorporar funciones de asistencia inteligente al sistema LUANY.