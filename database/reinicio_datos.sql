-- =====================================================================
-- REINICIO DE DATOS - Sistema Luany
-- ATENCION: BORRA todos los datos de categorias, proveedores, productos,
-- clientes, movimientos, notas de venta, pedidos y recepciones, y reinicia sus ids.
-- NO borra la tabla usuarios (se conservan las cuentas).
-- Usar solo en pruebas. Despues, ejecutar database/datos_ejemplo.sql.
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE detalle_recepcion_pedido;
TRUNCATE TABLE recepciones_pedido;
TRUNCATE TABLE detalle_pedido;
TRUNCATE TABLE detalle_venta;
TRUNCATE TABLE pedidos;
TRUNCATE TABLE notas_venta;
TRUNCATE TABLE movimientos;
TRUNCATE TABLE productos;
TRUNCATE TABLE clientes;
TRUNCATE TABLE proveedores;
TRUNCATE TABLE categorias;

SET FOREIGN_KEY_CHECKS = 1;
