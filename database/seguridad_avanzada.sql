-- Migración segura para seguridad avanzada LUANY
-- Requiere MySQL 8 / Aiven. Es idempotente para columnas e índices compatibles.

SET @col_bloqueado_hasta = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'usuarios'
    AND column_name = 'bloqueado_hasta'
);
SET @sql_bloqueado_hasta = IF(
  @col_bloqueado_hasta = 0,
  'ALTER TABLE usuarios ADD COLUMN bloqueado_hasta DATETIME(6) DEFAULT NULL',
  'SELECT 1'
);
PREPARE stmt_bloqueado_hasta FROM @sql_bloqueado_hasta;
EXECUTE stmt_bloqueado_hasta;
DEALLOCATE PREPARE stmt_bloqueado_hasta;

SET @col_bloqueado_manual = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'usuarios'
    AND column_name = 'bloqueado_manual'
);
SET @sql_bloqueado_manual = IF(
  @col_bloqueado_manual = 0,
  'ALTER TABLE usuarios ADD COLUMN bloqueado_manual TINYINT(1) NOT NULL DEFAULT 0',
  'SELECT 1'
);
PREPARE stmt_bloqueado_manual FROM @sql_bloqueado_manual;
EXECUTE stmt_bloqueado_manual;
DEALLOCATE PREPARE stmt_bloqueado_manual;
  
CREATE TABLE IF NOT EXISTS intentos_seguridad (
  id BIGINT NOT NULL AUTO_INCREMENT,
  identificador VARCHAR(120) DEFAULT NULL,
  usuario_id BIGINT DEFAULT NULL,
  ip VARCHAR(45) DEFAULT NULL,
  resultado VARCHAR(30) NOT NULL,
  tipo VARCHAR(80) NOT NULL,
  fecha DATETIME(6) NOT NULL,
  descripcion VARCHAR(200) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_intentos_identificador_fecha (identificador, fecha),
  KEY idx_intentos_ip_fecha (ip, fecha),
  CONSTRAINT fk_intentos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bloqueos_historial (
  id BIGINT NOT NULL AUTO_INCREMENT,
  usuario_id BIGINT NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  accion VARCHAR(20) NOT NULL,
  fecha DATETIME(6) NOT NULL,
  fecha_vigencia_hasta DATETIME(6) DEFAULT NULL,
  motivo VARCHAR(200) DEFAULT NULL,
  realizado_por VARCHAR(120) DEFAULT NULL,
  ip VARCHAR(45) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_bloqueos_usuario_fecha (usuario_id, fecha),
  CONSTRAINT fk_bloqueos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auditoria_seguridad (
  id BIGINT NOT NULL AUTO_INCREMENT,
  fecha DATETIME(6) NOT NULL,
  usuario_relacionado VARCHAR(120) DEFAULT NULL,
  tipo_evento VARCHAR(80) NOT NULL,
  resultado VARCHAR(30) DEFAULT NULL,
  ip VARCHAR(45) DEFAULT NULL,
  descripcion VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_auditoria_tipo_fecha (tipo_evento, fecha),
  KEY idx_auditoria_usuario_fecha (usuario_relacionado, fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
