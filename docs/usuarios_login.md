# Gestión de usuarios: creación administrativa e inicio de sesión

## 1. Descripción general

El acceso se realiza con cuentas creadas por un administrador. No hay registro público. El backend mantiene una sesión Spring Security después de validar las credenciales; la creación de empleados está restringida en el servidor al rol `ADMIN`.

## 2. Creación de cuentas de empleados

El administrador crea una cuenta desde **Usuarios** o mediante `POST /api/usuarios`, enviando únicamente nombre y correo electrónico. El servidor valida ambos campos, normaliza el correo y rechaza duplicados.

El servidor genera una contraseña temporal aleatoria, almacena únicamente su hash BCrypt, asigna el rol `EMPLEADO` y marca `debe_cambiar_password` como `true`. Envía las credenciales temporales al correo del empleado mediante la API HTTPS de Resend. La contraseña temporal no se incluye en la respuesta HTTP ni se registra en logs. Si Resend falla, se revierte la creación y la API responde con un error.

El endpoint no acepta contraseña ni rol en la solicitud. Los usuarios no autenticados y los empleados no pueden crear cuentas, aunque intenten llamar directamente a la API. Las operaciones de escritura usan un token CSRF.

## 3. Inicio de sesión

El inicio de sesión busca el usuario por correo y comprueba la contraseña mediante BCrypt. Si las credenciales son válidas, se establece una sesión del lado del servidor con la autoridad correspondiente al rol guardado en la base de datos. Los roles no se aceptan desde el cliente.

## 4. Protección de contraseñas mediante BCrypt

Las contraseñas se guardan como hashes BCrypt, no como texto legible. La contraseña temporal solo existe en memoria durante la operación y se transmite a Resend para el correo de bienvenida; no se devuelve al administrador vía HTTP ni se registra en logs.

## 5. Evidencia de la tabla usuarios

En esta sección se puede incorporar una captura de la tabla `usuarios` mostrando el rol y `debe_cambiar_password`, y únicamente el prefijo del hash BCrypt.

**Figura 1. Evidencia del almacenamiento de contraseñas en la tabla usuarios.**

[Insertar aquí la captura real de la tabla `usuarios` en MySQL Workbench.]

El resto del hash, las contraseñas temporales y los datos personales deben ocultarse.

**Fuente:** elaboración propia a partir de la base de datos del proyecto LUANY.

## 6. Pruebas de verificación

Las pruebas automatizadas verifican:

| Caso de prueba | Procedimiento | Resultado esperado |
|---|---|---|
| Creación de empleado | Crear una cuenta desde el servicio con un correo nuevo. | Guarda BCrypt, rol `EMPLEADO` y cambio de contraseña requerido. |
| Correo duplicado | Repetir la creación con el mismo correo. | El backend responde conflicto y no guarda otra cuenta. |
| Campos inválidos | Enviar nombre/correo vacíos o un correo inválido. | El backend rechaza la solicitud. |
| Acceso restringido | Intentar `POST /api/usuarios` sin sesión o con rol `EMPLEADO`. | El backend rechaza la creación. |
| Acceso ADMIN | Iniciar sesión con una cuenta ADMIN y crear un empleado. | El backend guarda la cuenta y solicita a Resend enviar el correo; la respuesta no incluye la contraseña temporal. |
| Fallo de correo | Simular un error o timeout del proveedor durante la creación. | El backend responde con error y revierte la creación del usuario. |

## 7. Conclusión

La creación de empleados está centralizada en el administrador y se valida en el backend. Las cuentas nuevas tienen contraseña temporal protegida con BCrypt, rol no elevado y marca de cambio inicial. El cambio de contraseña y el envío de correo quedan para etapas posteriores.