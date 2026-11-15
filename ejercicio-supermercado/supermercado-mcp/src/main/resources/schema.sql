-- =====================================================================
-- Base de datos del supermercado a domicilio (ejercicio propuesto)
-- Recrea la base desde cero: ejecútelo solo sobre datos de laboratorio.
--   mysql -h 127.0.0.1 -P 3307 -u root -p < schema.sql
-- Precios en pesos colombianos (COP), sin decimales.
-- =====================================================================
-- Fuerza UTF-8 en la conexión: sin esto, un cliente con locale "C" envía los
-- acentos como latin1 y quedan corruptos ("LÃ¡cteos" en vez de "Lácteos").
SET NAMES utf8mb4;

DROP DATABASE IF EXISTS supermercado;
CREATE DATABASE supermercado CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE supermercado;

CREATE TABLE producto (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  nombre        VARCHAR(120) NOT NULL,
  categoria     VARCHAR(60)  NOT NULL,
  presentacion  VARCHAR(60)  NOT NULL,
  precio        BIGINT       NOT NULL,
  stock         INT          NOT NULL,
  activo        BIT(1)       NOT NULL DEFAULT b'1',
  version       BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT chk_producto_precio CHECK (precio > 0),
  CONSTRAINT chk_producto_stock  CHECK (stock >= 0)
) ENGINE=InnoDB;

CREATE TABLE cliente (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  nombre          VARCHAR(120) NOT NULL,
  telefono        VARCHAR(10)  NOT NULL,
  direccion       VARCHAR(200) NOT NULL,
  fecha_registro  DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_cliente_telefono UNIQUE (telefono)
) ENGINE=InnoDB;

CREATE TABLE pedido (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  cliente_id          BIGINT       NOT NULL,
  direccion_entrega   VARCHAR(200) NOT NULL,
  estado              VARCHAR(20)  NOT NULL,   -- BORRADOR, CONFIRMADO, EN_CAMINO, ENTREGADO, CANCELADO
  metodo_pago         VARCHAR(20)  NULL,       -- EFECTIVO, DATAFONO, NEQUI (contra entrega)
  subtotal            BIGINT       NOT NULL DEFAULT 0,
  costo_domicilio     BIGINT       NOT NULL DEFAULT 0,
  total               BIGINT       NOT NULL DEFAULT 0,
  fecha_creacion      DATETIME(6)  NOT NULL,
  fecha_confirmacion  DATETIME(6)  NULL,
  entrega_estimada    DATETIME(6)  NULL,
  version             BIGINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_pedido_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id)
) ENGINE=InnoDB;

CREATE TABLE detalle_pedido (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  pedido_id        BIGINT NOT NULL,
  producto_id      BIGINT NOT NULL,
  cantidad         INT    NOT NULL,
  precio_unitario  BIGINT NOT NULL,   -- precio congelado al momento de agregar
  subtotal         BIGINT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_detalle_pedido   FOREIGN KEY (pedido_id)   REFERENCES pedido (id) ON DELETE CASCADE,
  CONSTRAINT fk_detalle_producto FOREIGN KEY (producto_id) REFERENCES producto (id),
  CONSTRAINT uk_detalle_producto UNIQUE (pedido_id, producto_id),
  CONSTRAINT chk_detalle_cantidad CHECK (cantidad > 0)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Catálogo (productos genéricos, datos sintéticos)
-- ---------------------------------------------------------------------
INSERT INTO producto (nombre, categoria, presentacion, precio, stock) VALUES
('Banano',               'Frutas y verduras', '1 kg',              4200,  80),
('Manzana roja',         'Frutas y verduras', '1 kg',              9800,  50),
('Limón Tahití',         'Frutas y verduras', '1 kg',              5200,  60),
('Tomate chonto',        'Frutas y verduras', '1 kg',              5600,  70),
('Papa pastusa',         'Frutas y verduras', 'Bolsa 2,5 kg',      9900,  40),
('Cebolla cabezona',     'Frutas y verduras', '1 kg',              4800,  55),
('Aguacate Hass',        'Frutas y verduras', 'Unidad',            3500,  45),
('Zanahoria',            'Frutas y verduras', '1 kg',              3900,  50),
('Leche entera',         'Lácteos y huevos',  'Bolsa 1,1 L',       4600, 120),
('Huevos AA',            'Lácteos y huevos',  'Cubeta x 30',      19900,  30),
('Queso campesino',      'Lácteos y huevos',  '500 g',            13500,  25),
('Yogur de fresa',       'Lácteos y huevos',  'Botella 1 L',       8900,  35),
('Mantequilla',          'Lácteos y huevos',  '250 g',             9200,  20),
('Pechuga de pollo',     'Carnes y pollo',    '1 kg',             18900,  30),
('Carne molida de res',  'Carnes y pollo',    '500 g',            14500,  25),
('Lomo de cerdo',        'Carnes y pollo',    '1 kg',             26900,  15),
('Pan tajado blanco',    'Panadería',         'Paquete 600 g',     7800,  40),
('Arepas de maíz',       'Panadería',         'Paquete x 10',      6500,  50),
('Croissant',            'Panadería',         'Unidad',            2900,  30),
('Arroz blanco',         'Despensa',          '1 kg',              5400, 100),
('Fríjol cargamanto',    'Despensa',          '500 g',             8900,  40),
('Lenteja',              'Despensa',          '500 g',             4300,  50),
('Aceite de girasol',    'Despensa',          'Botella 1 L',      12900,  35),
('Pasta spaghetti',      'Despensa',          '500 g',             3900,  60),
('Azúcar',               'Despensa',          '1 kg',              4900,  70),
('Café molido',          'Despensa',          '500 g',            18500,  30),
('Sal refinada',         'Despensa',          '1 kg',              2400,  50),
('Chocolate de mesa',    'Despensa',          '250 g',             6900,  30),
('Agua sin gas',         'Bebidas',           'Botella 600 ml',    2200, 100),
('Gaseosa cola',         'Bebidas',           'Botella 1,5 L',     6800,  60),
('Jugo de naranja',      'Bebidas',           'Botella 1 L',       7900,  30),
('Papel higiénico',      'Aseo y hogar',      'Paquete x 12',     24900,  25),
('Detergente en polvo',  'Aseo y hogar',      '1 kg',             13900,  30),
('Jabón lavaloza',       'Aseo y hogar',      '450 g',             5900,  40),
('Crema dental',         'Aseo y hogar',      'Tubo 100 ml',       7500,   0);  -- agotado (para probar la regla de stock)

-- ---------------------------------------------------------------------
-- Cliente de ejemplo con un pedido histórico ya entregado
-- ---------------------------------------------------------------------
INSERT INTO cliente (nombre, telefono, direccion, fecha_registro) VALUES
('Ana Gómez', '3001234567', 'Calle 50 # 40-20, Medellín', NOW(6));

INSERT INTO pedido (cliente_id, direccion_entrega, estado, metodo_pago, subtotal, costo_domicilio, total,
                    fecha_creacion, fecha_confirmacion, entrega_estimada)
VALUES (1, 'Calle 50 # 40-20, Medellín', 'ENTREGADO', 'EFECTIVO', 29100, 6000, 35100,
        NOW(6) - INTERVAL 3 DAY, NOW(6) - INTERVAL 3 DAY, NOW(6) - INTERVAL 3 DAY + INTERVAL 1 HOUR);

INSERT INTO detalle_pedido (pedido_id, producto_id, cantidad, precio_unitario, subtotal) VALUES
(1, 9,  2,  4600,  9200),   -- 2 x Leche entera
(1, 10, 1, 19900, 19900);   -- 1 x Huevos AA   -> subtotal 29.100 + domicilio 6.000 = 35.100
