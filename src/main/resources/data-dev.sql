-- Datos de relleno para perfil DEV (H2)
--
-- Este script se carga sólo en el perfil `dev` vía:
--   spring.sql.init.data-locations=classpath:data-dev.sql

-- Categorías
MERGE INTO categories (code, name, parent_id) KEY (code) VALUES ('FOOD','Alimentos',NULL);
MERGE INTO categories (code, name, parent_id) KEY (code) VALUES ('HOME','Hogar',NULL);

MERGE INTO categories (code, name, parent_id) KEY (code)
VALUES (
  'DAIRY',
  'Lácteos',
  (SELECT id FROM categories WHERE code='FOOD')
);

MERGE INTO categories (code, name, parent_id) KEY (code)
VALUES (
  'BEV',
  'Bebidas',
  (SELECT id FROM categories WHERE code='FOOD')
);

MERGE INTO categories (code, name, parent_id) KEY (code)
VALUES (
  'CLEAN',
  'Limpieza',
  (SELECT id FROM categories WHERE code='HOME')
);

-- Productos (clave por SKU)
MERGE INTO products (
  sku, name, brand, status,
  unit_type, unit_amount, unit_code,
  estimated_shelf_life_days,
  created_at, updated_at, version
) KEY (sku)
VALUES
  ('MILK-1L', 'Leche entera 1L', 'Cheapp', 'ACTIVE', 'VOLUME', 1.0, 'L', 10, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 0);

MERGE INTO products (
  sku, name, brand, status,
  unit_type, unit_amount, unit_code,
  estimated_shelf_life_days,
  created_at, updated_at, version
) KEY (sku)
VALUES
  ('YOG-200G', 'Yogurt natural 200g', 'Cheapp', 'ACTIVE', NULL, 200, NULL, 15, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 0);

MERGE INTO products (
  sku, name, brand, status,
  unit_type, unit_amount, unit_code,
  estimated_shelf_life_days,
  created_at, updated_at, version
) KEY (sku)
VALUES
  ('WATER-600ML', 'Agua 600ml', 'Cheapp', 'ACTIVE', 'VOLUME', 600, 'ML', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 0);

MERGE INTO products (
  sku, name, brand, status,
  unit_type, unit_amount, unit_code,
  estimated_shelf_life_days,
  created_at, updated_at, version
) KEY (sku)
VALUES
  ('SODA-355ML', 'Refresco 355ml', 'Fizz', 'ACTIVE', 'VOLUME', 355, 'ML', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 0);

MERGE INTO products (
  sku, name, brand, status,
  unit_type, unit_amount, unit_code,
  estimated_shelf_life_days,
  created_at, updated_at, version
) KEY (sku)
VALUES
  ('SOAP-500ML', 'Jabón líquido 500ml', 'CleanCo', 'ACTIVE', 'VOLUME', 500, 'ML', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 0);

-- Pivot product_categories (evitar duplicados)
MERGE INTO product_categories (product_id, category_id) KEY (product_id, category_id)
SELECT p.id, c.id
FROM products p, categories c
WHERE p.sku IN ('MILK-1L','YOG-200G') AND c.code='DAIRY';

MERGE INTO product_categories (product_id, category_id) KEY (product_id, category_id)
SELECT p.id, c.id
FROM products p, categories c
WHERE p.sku IN ('WATER-600ML','SODA-355ML') AND c.code='BEV';

MERGE INTO product_categories (product_id, category_id) KEY (product_id, category_id)
SELECT p.id, c.id
FROM products p, categories c
WHERE p.sku IN ('SOAP-500ML') AND c.code='CLEAN';

-- Stock por defecto (warehouse MAIN)
MERGE INTO stock (product_id, warehouse_id, quantity, updated_at, version)
KEY (product_id, warehouse_id)
SELECT p.id,
       'MAIN',
       CASE p.sku
         WHEN 'MILK-1L' THEN 50
         WHEN 'YOG-200G' THEN 80
         WHEN 'WATER-600ML' THEN 200
         WHEN 'SODA-355ML' THEN 120
         WHEN 'SOAP-500ML' THEN 40
         ELSE 10
       END,
       CURRENT_TIMESTAMP(),
       0
FROM products p;

-- Movimientos (uno por producto, si no existe)
INSERT INTO inventory_movements (
  product_id, warehouse_id, type, quantity, reason,
  external_event_id, correlation_id, created_at
)
SELECT p.id,
       'MAIN',
       'CREDIT',
       s.quantity,
       'seed',
       NULL,
       'seed-setup',
       DATEADD('DAY', -1, CURRENT_TIMESTAMP())
FROM products p
JOIN stock s ON s.product_id = p.id AND s.warehouse_id = 'MAIN'
WHERE NOT EXISTS (
  SELECT 1 FROM inventory_movements m
  WHERE m.product_id = p.id AND m.reason = 'seed'
);
