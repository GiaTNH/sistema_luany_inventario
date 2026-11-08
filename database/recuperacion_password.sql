CREATE TABLE IF NOT EXISTS tokens_recuperacion_password (
  id BIGINT NOT NULL AUTO_INCREMENT,
  usuario_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expira_en DATETIME(6) NOT NULL,
  utilizado_en DATETIME(6) DEFAULT NULL,
  creado_en DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_token_recuperacion_hash (token_hash),
  KEY idx_token_recuperacion_usuario (usuario_id),
  CONSTRAINT fk_token_recuperacion_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
