USE luany_inventario;

INSERT INTO categorias (nombre) VALUES ('Categoria A'), ('Categoria B');

INSERT INTO proveedores (nombre, telefono) VALUES
('Proveedor Uno', '999111222'),
('Proveedor Dos', '999333444');

INSERT INTO productos (nombre, categoria_id, proveedor_id, stock_actual, stock_minimo, stock_maximo, precio) VALUES
('Producto 1', 1, 1, 50, 10, 100, 12.50),
('Producto 2', 1, 1, 8, 10, 100, 7.00),
('Producto 3', 2, 2, 0, 5, 60, 25.00),
('Producto 4', 2, 2, 120, 20, 100, 3.80),
('Producto 5', 1, 2, 4, 15, 80, 9.90);