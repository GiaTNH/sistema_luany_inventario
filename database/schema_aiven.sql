-- =====================================================================
-- Sistema Luany - Inventario
-- Estructura de la base defaultdb (Aiven, MySQL 8)
-- Reconstruida a partir de information_schema. Solo estructura, sin datos.
-- Orden: primero las tablas sin dependencias, al final los detalles.
-- Usa IF NOT EXISTS: no borra ni modifica tablas que ya existan.
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS categorias (
  id     BIGINT      NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(80) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_categorias_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS proveedores (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  nombre    VARCHAR(120) NOT NULL,
  telefono  VARCHAR(20)  DEFAULT NULL,
  ruc       VARCHAR(20)  DEFAULT NULL,
  correo    VARCHAR(120) DEFAULT NULL,
  direccion VARCHAR(200) DEFAULT NULL,
  estado    VARCHAR(20)  DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_proveedores_ruc (ruc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS usuarios (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  email                 VARCHAR(120) NOT NULL,
  nombre                VARCHAR(100) NOT NULL,
  password              VARCHAR(100) NOT NULL,   -- hash BCrypt
  rol                   VARCHAR(20)  NOT NULL,
  apellido              VARCHAR(100) DEFAULT NULL,
  estado                VARCHAR(20)  DEFAULT 'ACTIVO',
  debe_cambiar_password TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_usuarios_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS clientes (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  nombre           VARCHAR(120) NOT NULL,
  tipo_documento   VARCHAR(20)  NOT NULL,
  numero_documento VARCHAR(20)  NOT NULL,
  telefono         VARCHAR(20)  DEFAULT NULL,
  direccion        VARCHAR(200) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_clientes_documento (numero_documento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS productos (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  nombre       VARCHAR(120) NOT NULL,
  precio       DOUBLE       NOT NULL,
  stock_actual INT          NOT NULL,
  stock_maximo INT          NOT NULL,
  stock_minimo INT          NOT NULL,
  categoria_id BIGINT       NOT NULL,
  proveedor_id BIGINT       NOT NULL,
  codigo       VARCHAR(50)  DEFAULT NULL,
  descripcion  VARCHAR(200) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_productos_codigo (codigo),
  KEY idx_productos_categoria (categoria_id),
  KEY idx_productos_proveedor (proveedor_id),
  CONSTRAINT fk_productos_categoria FOREIGN KEY (categoria_id) REFERENCES categorias (id),
  CONSTRAINT fk_productos_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores (id),
  CONSTRAINT chk_productos_stock  CHECK (stock_actual >= 0),
  CONSTRAINT chk_productos_precio CHECK (precio >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS movimientos (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  cantidad       INT          NOT NULL,
  fecha          DATETIME(6)  NOT NULL,
  observacion    VARCHAR(200) DEFAULT NULL,
  tipo           ENUM('ENTRADA','SALIDA','VENTA') NOT NULL,
  producto_id    BIGINT       NOT NULL,
  usuario_id     BIGINT       DEFAULT NULL,
  stock_anterior INT          DEFAULT NULL,
  stock_nuevo    INT          DEFAULT NULL,
  -- columna calculada: no se escribe desde el backend
  salida INT GENERATED ALWAYS AS (IF(tipo IN ('SALIDA','VENTA'), cantidad, 0)) VIRTUAL,
  PRIMARY KEY (id),
  KEY idx_mov_producto (producto_id),
  KEY idx_mov_usuario (usuario_id),
  CONSTRAINT fk_mov_producto FOREIGN KEY (producto_id) REFERENCES productos (id),
  CONSTRAINT fk_mov_usuario  FOREIGN KEY (usuario_id)  REFERENCES usuarios (id),
  CONSTRAINT chk_mov_cantidad CHECK (cantidad > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notas_venta (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  numero_nota   VARCHAR(30)   NOT NULL,
  fecha_emision DATETIME      NOT NULL,
  total         DECIMAL(12,2) NOT NULL,
  estado        VARCHAR(20)   NOT NULL,
  usuario_id    BIGINT        NOT NULL,
  cliente_id    BIGINT        NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notas_venta_numero (numero_nota),
  KEY idx_notas_usuario (usuario_id),
  KEY idx_notas_cliente (cliente_id),
  CONSTRAINT fk_notas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
  CONSTRAINT fk_notas_cliente FOREIGN KEY (cliente_id) REFERENCES clientes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS detalle_venta (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  nota_venta_id   BIGINT        NOT NULL,
  producto_id     BIGINT        NOT NULL,
  cantidad        INT           NOT NULL,
  precio_unitario DECIMAL(12,2) NOT NULL,
  subtotal        DECIMAL(12,2) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_dv_nota (nota_venta_id),
  KEY idx_dv_producto (producto_id),
  CONSTRAINT fk_dv_nota     FOREIGN KEY (nota_venta_id) REFERENCES notas_venta (id),
  CONSTRAINT fk_dv_producto FOREIGN KEY (producto_id)   REFERENCES productos (id),
  CONSTRAINT chk_detalle_venta_cantidad CHECK (cantidad > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pedidos (
  id                     BIGINT      NOT NULL AUTO_INCREMENT,
  fecha_pedido           DATETIME    NOT NULL,
  estado                 VARCHAR(30) NOT NULL,
  fecha_estimada_entrega DATE        DEFAULT NULL,
  proveedor_id           BIGINT      NOT NULL,
  usuario_id             BIGINT      NOT NULL,
  PRIMARY KEY (id),
  KEY idx_pedidos_proveedor (proveedor_id),
  KEY idx_pedidos_usuario (usuario_id),
  CONSTRAINT fk_pedidos_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedores (id),
  CONSTRAINT fk_pedidos_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuarios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS detalle_pedido (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  pedido_id   BIGINT        NOT NULL,
  producto_id BIGINT        NOT NULL,
  cantidad    INT           NOT NULL,
  precio      DECIMAL(12,2) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_dp_pedido (pedido_id),
  KEY idx_dp_producto (producto_id),
  CONSTRAINT fk_dp_pedido   FOREIGN KEY (pedido_id)   REFERENCES pedidos (id),
  CONSTRAINT fk_dp_producto FOREIGN KEY (producto_id) REFERENCES productos (id),
  CONSTRAINT chk_detalle_pedido_cantidad CHECK (cantidad > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
