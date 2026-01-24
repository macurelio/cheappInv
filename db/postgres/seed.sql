-- cheappInv - Datos de relleno (seed) para PostgreSQL
--
-- Uso:
--   psql -d cheappinv -f db/postgres/init.sql
--   psql -d cheappinv -f db/postgres/seed.sql
--
-- Nota: este seed es idempotente (si lo corres 2 veces no duplica por SKU/CODE)

BEGIN;

-- ==========================================================
-- 0) Normalización / saneo de datos existentes (evita romper la app)
-- ==========================================================
-- En el código actual:
--   UnitType = { VOLUME }
--   UnitCode = { ML, L }
-- Si existen valores anteriores/inválidos (por ejemplo 'G', 'KG', 'WEIGHT', '0001'), los dejamos en NULL
-- para que no falle Hibernate al mapear enums.
UPDATE products
SET unit_code = NULL
WHERE unit_code IS NOT NULL AND unit_code NOT IN ('ML','L');

UPDATE products
SET unit_type = NULL
WHERE unit_type IS NOT NULL AND unit_type NOT IN ('VOLUME');

-- ==========================================================
-- 1) Categorías
-- ==========================================================
INSERT INTO categories (code, name, parent_id)
VALUES
  ('FOOD', 'Alimentos', NULL)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO categories (code, name, parent_id)
SELECT 'DAIRY', 'Lácteos', c.id
FROM categories c
WHERE c.code = 'FOOD'
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO categories (code, name, parent_id)
SELECT 'BEV', 'Bebidas', c.id
FROM categories c
WHERE c.code = 'FOOD'
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO categories (code, name, parent_id)
VALUES
  ('HOME', 'Hogar', NULL)
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO categories (code, name, parent_id)
SELECT 'CLEAN', 'Limpieza', c.id
FROM categories c
WHERE c.code = 'HOME'
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;

-- ==========================================================
-- 2) Productos (alineados a enums del código)
-- ==========================================================
-- Nota importante: el proyecto actualmente solo soporta UnitType=VOLUME y UnitCode=ML/L.
-- Por eso los productos como "YOG-200G" se guardan SIN unit_code/unit_type por ahora.
INSERT INTO products (
  sku, name, brand, status,
  unit_type, unit_amount, unit_code,
  estimated_shelf_life_days,
  created_at, updated_at, version
)
VALUES
  ('MILK-1L', 'Leche entera 1L', 'Cheapp', 'ACTIVE', 'VOLUME', 1.0, 'L', 10, now(), now(), 0),
  ('YOG-200G', 'Yogurt natural 200g', 'Cheapp', 'ACTIVE', NULL, 200, NULL, 15, now(), now(), 0),
  ('WATER-600ML', 'Agua 600ml', 'Cheapp', 'ACTIVE', 'VOLUME', 600, 'ML', NULL, now(), now(), 0),
  ('SODA-355ML', 'Refresco 355ml', 'Fizz', 'ACTIVE', 'VOLUME', 355, 'ML', NULL, now(), now(), 0),
  ('SOAP-500ML', 'Jabón líquido 500ml', 'CleanCo', 'ACTIVE', 'VOLUME', 500, 'ML', NULL, now(), now(), 0)
ON CONFLICT (sku) DO UPDATE
SET
  name = EXCLUDED.name,
  brand = EXCLUDED.brand,
  status = EXCLUDED.status,
  unit_type = EXCLUDED.unit_type,
  unit_amount = EXCLUDED.unit_amount,
  unit_code = EXCLUDED.unit_code,
  estimated_shelf_life_days = EXCLUDED.estimated_shelf_life_days,
  updated_at = EXCLUDED.updated_at;

-- ==========================================================
-- 3) Relación producto <-> categoría
-- ==========================================================
INSERT INTO product_categories (product_id, category_id)
SELECT p.id, c.id
FROM products p
JOIN categories c ON c.code = 'DAIRY'
WHERE p.sku IN ('MILK-1L','YOG-200G')
ON CONFLICT DO NOTHING;

INSERT INTO product_categories (product_id, category_id)
SELECT p.id, c.id
FROM products p
JOIN categories c ON c.code = 'BEV'
WHERE p.sku IN ('WATER-600ML','SODA-355ML')
ON CONFLICT DO NOTHING;

INSERT INTO product_categories (product_id, category_id)
SELECT p.id, c.id
FROM products p
JOIN categories c ON c.code = 'CLEAN'
WHERE p.sku IN ('SOAP-500ML')
ON CONFLICT DO NOTHING;

-- ==========================================================
-- 4) Stock inicial (warehouse MAIN)
-- ==========================================================
INSERT INTO stock (product_id, warehouse_id, quantity, updated_at, version)
SELECT p.id, 'MAIN',
       CASE p.sku
         WHEN 'MILK-1L' THEN 50
         WHEN 'YOG-200G' THEN 80
         WHEN 'WATER-600ML' THEN 200
         WHEN 'SODA-355ML' THEN 120
         WHEN 'SOAP-500ML' THEN 40
         ELSE 10
       END,
       now(), 0
FROM products p
ON CONFLICT (product_id, warehouse_id) DO UPDATE
  SET quantity = EXCLUDED.quantity,
      updated_at = EXCLUDED.updated_at;

-- ==========================================================
-- 5) Movimientos (para historial)
-- ==========================================================
INSERT INTO inventory_movements (
  product_id, warehouse_id, type, quantity, reason,
  external_event_id, correlation_id, created_at
)
SELECT p.id, 'MAIN', 'CREDIT',
       CASE p.sku
         WHEN 'MILK-1L' THEN 50
         WHEN 'YOG-200G' THEN 80
         WHEN 'WATER-600ML' THEN 200
         WHEN 'SODA-355ML' THEN 120
         WHEN 'SOAP-500ML' THEN 40
         ELSE 10
       END,
       'seed',
       NULL,
       'seed-setup',
       now() - interval '1 day'
FROM products p
WHERE NOT EXISTS (
  SELECT 1 FROM inventory_movements m
  WHERE m.product_id = p.id AND m.reason = 'seed'
);

COMMIT;
