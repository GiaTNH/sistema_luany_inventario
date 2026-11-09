-- Migration: persist partial and complete order receipts and link each receipt
-- line to the stock movement that it creates. Do not execute against production
-- without the normal database change/release approval.
CREATE TABLE IF NOT EXISTS recepciones_pedido (
  id                 BIGINT      NOT NULL AUTO_INCREMENT,
  pedido_id          BIGINT      NOT NULL,
  usuario_id         BIGINT      NOT NULL,
  clave_idempotencia VARCHAR(36) NOT NULL,
  fecha              DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recepciones_pedido_idempotencia (clave_idempotencia),
  KEY idx_recepciones_pedido_pedido (pedido_id),
  KEY idx_recepciones_pedido_usuario (usuario_id),
  CONSTRAINT fk_recepciones_pedido_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos (id),
  CONSTRAINT fk_recepciones_pedido_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS detalle_recepcion_pedido (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  recepcion_id      BIGINT NOT NULL,
  detalle_pedido_id BIGINT NOT NULL,
  cantidad          INT    NOT NULL,
  movimiento_id     BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_detalle_recepcion_movimiento (movimiento_id),
  KEY idx_detalle_recepcion_recepcion (recepcion_id),
  KEY idx_detalle_recepcion_pedido (detalle_pedido_id),
  CONSTRAINT fk_detalle_recepcion_recepcion FOREIGN KEY (recepcion_id)
    REFERENCES recepciones_pedido (id),
  CONSTRAINT fk_detalle_recepcion_detalle FOREIGN KEY (detalle_pedido_id)
    REFERENCES detalle_pedido (id),
  CONSTRAINT fk_detalle_recepcion_movimiento FOREIGN KEY (movimiento_id)
    REFERENCES movimientos (id),
  CONSTRAINT chk_detalle_recepcion_cantidad CHECK (cantidad > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
