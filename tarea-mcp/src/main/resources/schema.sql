CREATE DATABASE IF NOT EXISTS tareas_mcp
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE tareas_mcp;

CREATE TABLE IF NOT EXISTS tarea (
  id BIGINT NOT NULL AUTO_INCREMENT,
  completado BIT(1) DEFAULT b'0',
  fecha VARCHAR(255) NULL,
  titulo VARCHAR(255) NOT NULL,
  version BIGINT DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tarea (titulo, fecha, completado, version) VALUES
('Preparar charla MCP', '2026-04-20', b'0', 0),
('Revisar slides', '2026-04-21', b'0', 0),
('Probar demo en vivo', '2026-04-22', b'1', 0);
