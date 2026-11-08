-- Ejecutar una vez en la base LUANY de Aiven antes de desplegar esta version.
-- El script es idempotente y no modifica ni elimina usuarios existentes.
CREATE TABLE IF NOT EXISTS registro_inicial (
  id          TINYINT    NOT NULL,
  completado  TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT chk_registro_inicial_id CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO registro_inicial (id, completado)
SELECT 1, IF(EXISTS (SELECT 1 FROM usuarios), 1, 0)
FROM DUAL
ON DUPLICATE KEY UPDATE completado = registro_inicial.completado;
