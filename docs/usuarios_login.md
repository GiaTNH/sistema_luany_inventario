# Gestión de usuarios: registro e inicio de sesión

## 1. Descripción general

El sistema de gestión de inventario de la empresa LUANY contempla la gestión de usuarios para controlar el acceso a sus funcionalidades. El registro permite incorporar usuarios al sistema, mientras que el inicio de sesión permite comprobar sus credenciales antes de autorizar el acceso.

La gestión de usuarios también considera la validación de los datos ingresados y la protección de las contraseñas almacenadas en la base de datos.

## 2. Registro de usuarios

El registro permite guardar los datos de un nuevo usuario en la base de datos. Durante este proceso, el sistema debe comprobar que los campos obligatorios estén completos y que la información cumpla las validaciones establecidas.

Cuando se registra una contraseña, esta debe procesarse mediante un algoritmo de hash seguro antes de almacenarse. De esta manera, la base de datos no conserva la contraseña original como texto legible.

Las validaciones aplicadas y los campos solicitados deben corresponder al formulario y al código implementados en el proyecto.

## 3. Inicio de sesión

El inicio de sesión permite verificar las credenciales de un usuario registrado. El proceso comprende la recepción de los datos ingresados, la búsqueda del usuario correspondiente y la comprobación de la contraseña.

Si las credenciales son válidas, el sistema permite el acceso de acuerdo con el mecanismo de autenticación implementado. Si las credenciales no son correctas, el acceso debe rechazarse y mostrarse un mensaje de error que no exponga información confidencial.

## 4. Protección de contraseñas mediante BCrypt

Las contraseñas deben almacenarse mediante un mecanismo de hash y no como texto plano. El formato BCrypt utiliza cadenas que pueden comenzar con el prefijo `$2a$`.

Este prefijo es compatible con el formato de hash de BCrypt. Sin embargo, para confirmar que la autenticación funciona correctamente, también debe comprobarse que el código utiliza el mecanismo correspondiente para verificar la contraseña introducida durante el inicio de sesión.

La contraseña original no debe mostrarse ni guardarse directamente en la base de datos. Asimismo, las credenciales de conexión y las claves de servicios externos deben mantenerse fuera del repositorio público.

## 5. Evidencia de la tabla usuarios

En esta sección se incorpora una captura obtenida de la base de datos del proyecto LUANY, donde se visualiza la tabla `usuarios` y el formato de hash utilizado para almacenar las contraseñas.

**Figura 1. Evidencia del almacenamiento de contraseñas en la tabla usuarios.**

[Insertar aquí la captura real de la tabla `usuarios` en MySQL Workbench.]

La captura debe permitir identificar el campo correspondiente a la contraseña y mostrar únicamente el prefijo `$2a$` del hash. El resto del hash y los datos personales de los usuarios deben ocultarse para proteger la información.

**Fuente:** elaboración propia a partir de la base de datos del proyecto LUANY.

## 6. Pruebas de verificación

Para documentar el funcionamiento de la gestión de usuarios, se deben ejecutar las siguientes pruebas:

| Caso de prueba | Procedimiento | Resultado esperado |
|---|---|---|
| Registro de usuario | Ingresar los datos requeridos para crear una cuenta. | El sistema registra al usuario si los datos cumplen las validaciones. |
| Validación de campos | Enviar el formulario con campos obligatorios vacíos. | El sistema rechaza los datos que incumplen las reglas establecidas. |
| Inicio de sesión correcto | Ingresar las credenciales de un usuario registrado. | El sistema permite el acceso. |
| Contraseña incorrecta | Ingresar una contraseña que no corresponde al usuario. | El sistema rechaza el acceso. |
| Verificación del hash | Consultar el campo de contraseña en la base de datos. | Se comprueba que la contraseña está almacenada en un formato de hash compatible con el mecanismo utilizado. |

Los resultados de estas pruebas deben registrarse después de ejecutarlas en el sistema. La definición de un resultado esperado no constituye, por sí sola, evidencia de que la prueba haya sido superada.

## 7. Conclusión

La gestión de usuarios es importante para controlar el acceso al sistema de inventario LUANY y proteger las credenciales almacenadas. La validación de los datos y el uso de un mecanismo de hash para las contraseñas contribuyen a mejorar la seguridad de la aplicación.

La documentación debe mantenerse actualizada de acuerdo con el código fuente, la estructura de la base de datos y los resultados obtenidos durante las pruebas.