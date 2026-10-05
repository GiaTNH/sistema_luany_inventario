# sistema_luany_inventario
Sistema de gestión de inventarios para optimizar el proceso de abastecimiento de la empresa Luany.

# Jared Acosta Huaman - Developer (Pruebas y Evidencias)

Proyecto: Desarrollo de un sistema de gestión de inventarios aplicando Scrum y Kanban para optimizar el proceso de abastecimiento en la empresa Luany.

Curso: Metodologías Ágiles - Universidad Privada San Juan Bautista (2026-II)

## Mi rol

Soy el Developer de pruebas y evidencias. Me encargo de la arquitectura del sistema y de comprobar que lo que se construye funcione.

## Responsabilidades

- Elaborar el diagrama de arquitectura del sistema.
- Diseñar y ejecutar las pruebas de cada funcionalidad.
- Verificar que cada historia cumpla su criterio de aceptación antes de pasar a "Hecho".
- Reunir las evidencias del avance de cada sprint.

## Diagrama de arquitectura

Archivo: `Arquitectura de inventarios Luany.png` (en esta misma carpeta)

Arquitectura web cliente-servidor en tres capas con API REST:

| Capa | Descripción |
|---|---|
| Presentación (Frontend) | Pantallas web: Login, Productos, Movimientos, Pedidos, Proveedores y Alertas |
| Lógica de negocio (Backend / API) | Módulos de Seguridad, Productos, Proveedores, Movimientos, Pedidos y Alertas |
| Datos (Base de datos) | Tablas Usuarios, Productos, Proveedores, Pedidos, Detalle_pedido y Movimientos |

Comunicación: HTTPS / JSON entre frontend y backend, y SQL entre backend y base de datos.

## Sprint 1

- Tarea en Trello: Diagrama de arquitectura , junto con Kyara.

## Pendiente

- Plan de pruebas para los módulos del Sprint 2.
