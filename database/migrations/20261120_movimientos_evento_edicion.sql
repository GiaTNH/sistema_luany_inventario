-- Migration pending execution in the database.
-- Confirm that movimientos.tipo currently includes CREACION and retains the
-- production utf8mb4 configuration before proceeding.
-- Run this ALTER by itself, then verify the enum values and existing movement
-- rows before any other operation. If it fails, inspect the current column
-- definition and resume from the verified state; do not rerun blindly.
ALTER TABLE movimientos
  MODIFY COLUMN tipo ENUM('ENTRADA', 'SALIDA', 'VENTA', 'CREACION', 'EDICION')
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
