# sistema_luany_inventario
Sistema de gestión de inventarios para optimizar el proceso de abastecimiento de la empresa Luany.

Proyecto: Desarrollo de un sistema de gestión de inventarios aplicando Scrum y Kanban para optimizar el proceso de abastecimiento en la empresa Luany.

Curso: Metodologías Ágiles - Universidad Privada San Juan Bautista (2026-II)

## Diagrama de arquitectura

Archivo: `Arquitectura de inventarios Luany.png` (en esta misma carpeta)

Arquitectura web cliente-servidor en tres capas con API REST:

| Capa | Descripción |
|---|---|
| Presentación (Frontend) | Pantallas web: Login, Productos, Movimientos, Pedidos, Proveedores y Alertas |
| Lógica de negocio (Backend / API) | Módulos de Seguridad, Productos, Proveedores, Movimientos, Pedidos y Alertas |
| Datos (Base de datos) | Tablas Usuarios, Productos, Proveedores, Pedidos, Detalle_pedido y Movimientos |

Comunicación: HTTPS / JSON entre frontend y backend, y SQL entre backend y base de datos.
