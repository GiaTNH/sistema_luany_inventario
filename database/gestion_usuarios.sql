-- Migración manual para habilitar la gestión de usuarios.
-- Ejecutar una sola vez en la base LUANY antes de desplegar esta versión.
ALTER TABLE usuarios
  ADD COLUMN nombre_usuario VARCHAR(100) NULL AFTER email;

UPDATE usuarios
SET nombre_usuario = CONCAT('usuario', id)
WHERE nombre_usuario IS NULL OR TRIM(nombre_usuario) = '';

ALTER TABLE usuarios
  MODIFY COLUMN nombre_usuario VARCHAR(100) NOT NULL,
  ADD UNIQUE KEY uk_usuarios_nombre_usuario (nombre_usuario);
