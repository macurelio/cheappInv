-- cheappInv - Script de inicialización PostgreSQL
-- Compatible con DBeaver
--
-- Crea:
--  - Base de datos y usuario (opcional)
--  - Tablas usadas por el microservicio
--  - Índices/constraints según anotaciones JPA
--
-- NOTA: el proyecto actualmente usa H2 en memoria (application.yaml)
-- pero en producción normalmente se usaría PostgreSQL.

-- ==========================================================
-- 0) (OPCIONAL) Crear DB y usuario
-- ==========================================================
-- Ejecuta esta sección conectado a la base 'postgres' como superuser.

-- CREATE USER cheappinv_user WITH PASSWORD 'cheappinv_pass';
-- CREATE DATABASE cheappinv OWNER cheappinv_user;
-- GRANT ALL PRIVILEGES ON DATABASE cheappinv TO cheappinv_user;

-- ==========================================================
-- 1) Esquema (opcional)
-- ==========================================================
-- Si quieres usar un schema dedicado:
-- CREATE SCHEMA IF NOT EXISTS cheappinv AUTHORIZATION cheappinv_user;
-- SET search_path TO cheappinv;

-- ==========================================================
-- 2) Tablas
-- ==========================================================

-- categories
CREATE TABLE IF NOT EXISTS categories (
    id         BIGSERIAL PRIMARY KEY,
    code       TEXT NOT NULL,
    name       TEXT NOT NULL,
    parent_id  BIGINT NULL,
    CONSTRAINT uk_categories_code UNIQUE (code),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id)
);

-- products
CREATE TABLE IF NOT EXISTS products (
    id           BIGSERIAL PRIMARY KEY,
    sku          TEXT NOT NULL,
    name         TEXT NULL,
    brand        TEXT NULL,
    status       TEXT NOT NULL,

    -- Unidad (UnitType/UnitCode) + cantidad
    unit_type    TEXT NULL,
    unit_amount  NUMERIC NULL,
    unit_code    TEXT NULL,

    estimated_shelf_life_days INTEGER NULL,

    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_products_sku UNIQUE (sku)
);

-- join table: product_categories
CREATE TABLE IF NOT EXISTS product_categories (
    product_id  BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_product_categories_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_categories_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);

-- stock
CREATE TABLE IF NOT EXISTS stock (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT NOT NULL,
    warehouse_id  TEXT NOT NULL,
    quantity      BIGINT NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uk_stock_product_wh UNIQUE (product_id, warehouse_id)
);

-- inventory_movements
CREATE TABLE IF NOT EXISTS inventory_movements (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT NOT NULL,
    warehouse_id      TEXT NOT NULL,
    type              TEXT NOT NULL,
    quantity          BIGINT NOT NULL,
    reason            TEXT NOT NULL,
    external_event_id TEXT NULL,
    correlation_id    TEXT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_movements_product FOREIGN KEY (product_id) REFERENCES products(id)
);

-- inbox_events (idempotencia)
CREATE TABLE IF NOT EXISTS inbox_events (
    id           BIGSERIAL PRIMARY KEY,
    event_id     TEXT NOT NULL,
    event_type   TEXT NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_inbox_event_id UNIQUE (event_id)
);

-- outbox_events (publicación)
CREATE TABLE IF NOT EXISTS outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    event_id       TEXT NOT NULL,
    event_type     TEXT NOT NULL,
    schema_version INTEGER NOT NULL,
    correlation_id TEXT NULL,
    payload_json   TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    published      BOOLEAN NOT NULL DEFAULT FALSE,
    published_at   TIMESTAMPTZ NULL,
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
);

-- recipes
CREATE TABLE IF NOT EXISTS recipes (
    id             BIGSERIAL PRIMARY KEY,
    recipe_id      TEXT NOT NULL,
    dish_sku       TEXT NOT NULL,
    version_number INTEGER NOT NULL,
    status         TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_recipes_recipe_id UNIQUE (recipe_id)
);

CREATE TABLE IF NOT EXISTS recipe_ingredients (
    id             BIGSERIAL PRIMARY KEY,
    recipe_id      BIGINT NOT NULL,
    ingredient_sku TEXT NOT NULL,
    quantity       BIGINT NOT NULL,
    unit_code      TEXT NULL,
    CONSTRAINT fk_recipe_ingredients_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
    CONSTRAINT uk_recipe_ingredient UNIQUE (recipe_id, ingredient_sku)
);

-- processed_comandas (idempotencia de negocio)
CREATE TABLE IF NOT EXISTS processed_comandas (
    id           BIGSERIAL PRIMARY KEY,
    comanda_id   TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_processed_comanda_id UNIQUE (comanda_id)
);

-- historical_consumption
CREATE TABLE IF NOT EXISTS historical_consumption (
    id          BIGSERIAL PRIMARY KEY,
    product_sku TEXT NOT NULL,
    quantity    BIGINT NOT NULL,
    comanda_id  TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

-- ==========================================================
-- 3) Índices
-- ==========================================================

-- categories
CREATE INDEX IF NOT EXISTS idx_categories_code ON categories (code);

-- products
-- Nota: idx_products_sku cubierto por UNIQUE
CREATE INDEX IF NOT EXISTS idx_products_name ON products (name);

-- inventory_movements
CREATE INDEX IF NOT EXISTS idx_movements_product_ts ON inventory_movements (product_id, created_at);
CREATE INDEX IF NOT EXISTS idx_movements_product_wh_type_ts ON inventory_movements (product_id, warehouse_id, type, created_at);

-- inbox_events
CREATE INDEX IF NOT EXISTS idx_inbox_received_at ON inbox_events (received_at);

-- outbox_events
CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events (published, created_at);

-- recipes
CREATE INDEX IF NOT EXISTS idx_recipes_dish_sku ON recipes (dish_sku);
CREATE INDEX IF NOT EXISTS idx_recipes_dish_status ON recipes (dish_sku, status);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipe ON recipe_ingredients (recipe_id);

-- ==========================================================
-- 4) Checks (opcionales)
-- ==========================================================


DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_products_status'
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE','BLOCKED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_movements_type'
    ) THEN
        ALTER TABLE inventory_movements
            ADD CONSTRAINT chk_movements_type CHECK (type IN ('DEBIT','CREDIT'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_recipes_status'
    ) THEN
        ALTER TABLE recipes
            ADD CONSTRAINT chk_recipes_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'));
    END IF;
END $$;
