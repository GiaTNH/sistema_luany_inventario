# Arquitectura del sistema

Aplicación web cliente-servidor en tres capas, con API REST, para el sistema de gestión de inventarios de la empresa Luany.

## Capas

| Capa | Tecnología | Función |
|---|---|---|
| Presentación (frontend) | HTML, CSS y JavaScript | Pantallas de Login, Productos, Proveedores, Movimientos, Notas de venta, Pedidos, Alertas, Tablero Kanban, Reportes y Usuarios |
| Lógica de negocio (backend / API) | Node.js | Módulos de Seguridad, Productos, Proveedores, Movimientos, Notas de venta, Pedidos, Alertas y Reportes |
| Datos | MySQL | 8 tablas: Usuarios, Productos, Proveedores, Movimientos, Notas_venta, Detalle_venta, Pedidos y Detalle_pedido |

La comunicación entre el frontend y el backend usa HTTPS / JSON. Entre el backend y la base de datos se usa SQL.

## Decisiones de arquitectura

| Decisión | Requisito |
|---|---|
| Dos roles: administrador y almacén (ver la hoja "Roles y Permisos" del Product Backlog) | PB-10, RNF-04 |
| Contraseñas almacenadas con hash, nunca en texto plano | RNF-03 |
| Cierre automático de sesión tras 15 minutos de inactividad | RNF-05 |
| Copia de respaldo diaria de la base de datos | RNF-11 |
| El stock nunca queda negativo y cada movimiento guarda fecha, hora y usuario | RNF-12 |
| Las notas de venta son de control interno y no son comprobante de pago | PB-18 |

## Módulos y su relación con el backlog

| Módulo | Historias |
|---|---|
| Seguridad y usuarios | PB-10 |
| Productos | PB-01, PB-04, PB-05, PB-16 |
| Proveedores | PB-11 |
| Movimientos | PB-02, PB-03, PB-12 |
| Notas de venta | PB-18 |
| Pedidos | PB-07, PB-08, PB-09, PB-13 |
| Alertas y tablero Kanban | PB-06, PB-14 |
| Reportes | PB-15, PB-17 |

## Estructura del repositorio

| Carpeta | Contenido |
|---|---|
| `frontend/` | Interfaz y prototipo de pantallas |
| `backend/` | API y reglas de negocio |
| `database/` | Modelo de datos y scripts |
| `producto/` | Product Backlog, requisitos, roles y diccionario de datos |
| `scrum/` | Organización del equipo |
| `arquitectura/` | Este documento y el diagrama |
