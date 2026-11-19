-- MySQL 8.4.11 (Aiven).
-- Run once, after verifying the production schema matches the documented
-- starting state. MySQL does not support ADD COLUMN IF NOT EXISTS; the first
-- ALTER intentionally fails on duplicate columns, protecting against reruns.
-- Do not run with a client configured to continue after SQL errors.
--
-- Existing product rows are retained. The new columns receive their defaults:
-- activo = 1 and fecha_creacion = the migration time.
-- Execute this ALTER TABLE by itself, then verify both columns and existing
-- product rows before continuing. If it fails, inspect the table schema and
-- resume only after determining whether either column was already added.
ALTER TABLE productos
  ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN fecha_creacion DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- Existing movements have cantidad > 0, so they satisfy the replacement check.
-- Only the movement type definition and its quantity check are changed.
-- Execute this ALTER TABLE separately, only after the productos change is
-- verified. Then verify the ENUM, CHECK constraint, and existing movement
-- rows. If it fails, inspect the current type and constraint before recovery;
-- do not rerun the whole migration blindly.
ALTER TABLE movimientos
  MODIFY COLUMN tipo ENUM('ENTRADA', 'SALIDA', 'VENTA', 'CREACION')
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  DROP CHECK chk_mov_cantidad,
  ADD CONSTRAINT chk_mov_cantidad CHECK (cantidad >= 0);
