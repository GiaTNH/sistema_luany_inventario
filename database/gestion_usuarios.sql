-- Migración manual exclusiva para bases LUANY antiguas que todavía no tienen
-- usuarios.nombre_usuario ni uk_usuarios_nombre_usuario.
-- NO ejecutar como script de instalación sobre database/schema_aiven.sql ni
-- sobre una base que ya incluya esas definiciones.
-- Para una base antigua, ejecutar una sola vez antes de desplegar esta versión.
ALTER TABLE usuarios
  ADD COLUMN nombre_usuario VARCHAR(100) NULL AFTER email;

UPDATE usuarios
SET nombre_usuario = CONCAT('usuario', id)
WHERE nombre_usuario IS NULL OR TRIM(nombre_usuario) = '';

ALTER TABLE usuarios
  MODIFY COLUMN nombre_usuario VARCHAR(100) NOT NULL,
  ADD UNIQUE KEY uk_usuarios_nombre_usuario (nombre_usuario);
