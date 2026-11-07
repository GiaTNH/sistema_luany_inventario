-- =====================================================================
-- Datos de ejemplo - Sistema Luany (base defaultdb en Aiven)
-- Se puede ejecutar varias veces: INSERT IGNORE evita duplicados
-- (categorias.nombre, proveedores.ruc, productos.codigo y
-- clientes.numero_documento son UNIQUE).
-- No crea usuarios: el administrador y los empleados se crean desde
-- la aplicacion para que la contrasena se guarde con hash BCrypt.
-- Ejecutar antes: database/schema_aiven.sql (solo si la base esta vacia).
-- =====================================================================

INSERT IGNORE INTO categorias (nombre) VALUES
  ('Categoria A'),
  ('Categoria B');

INSERT IGNORE INTO proveedores (nombre, telefono, ruc, correo, direccion, estado) VALUES
  ('Proveedor Uno', '999111222', '20100000001', 'uno@proveedor.com', 'Av. Principal 123', 'ACTIVO'),
  ('Proveedor Dos', '999333444', '20100000002', 'dos@proveedor.com', 'Jr. Comercio 456', 'ACTIVO');

INSERT IGNORE INTO productos
  (nombre, codigo, descripcion, categoria_id, proveedor_id, stock_actual, stock_minimo, stock_maximo, precio)
VALUES
  ('Producto 1', 'P-0001', 'Producto de ejemplo 1',
     (SELECT id FROM categorias  WHERE nombre = 'Categoria A'),
     (SELECT id FROM proveedores WHERE ruc = '20100000001'), 50, 10, 100, 12.50),
  ('Producto 2', 'P-0002', 'Producto de ejemplo 2',
     (SELECT id FROM categorias  WHERE nombre = 'Categoria A'),
     (SELECT id FROM proveedores WHERE ruc = '20100000001'),  8, 10, 100,  7.00),
  ('Producto 3', 'P-0003', 'Producto de ejemplo 3',
     (SELECT id FROM categorias  WHERE nombre = 'Categoria B'),
     (SELECT id FROM proveedores WHERE ruc = '20100000002'),  0,  5,  60, 25.00),
  ('Producto 4', 'P-0004', 'Producto de ejemplo 4',
     (SELECT id FROM categorias  WHERE nombre = 'Categoria B'),
     (SELECT id FROM proveedores WHERE ruc = '20100000002'), 120, 20, 100,  3.80),
  ('Producto 5', 'P-0005', 'Producto de ejemplo 5',
     (SELECT id FROM categorias  WHERE nombre = 'Categoria A'),
     (SELECT id FROM proveedores WHERE ruc = '20100000002'),  4, 15,  80,  9.90);

INSERT IGNORE INTO clientes (nombre, tipo_documento, numero_documento, telefono, direccion) VALUES
  ('Cliente General', 'DNI', '00000000', NULL, NULL);
