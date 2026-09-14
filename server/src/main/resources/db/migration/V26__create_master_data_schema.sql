-- ==============================================================================
-- WeMade ERP — Master Data Bahan Baku, Satuan & Tarif Acuan HPP (V26)
-- ==============================================================================
-- Modul fondasi non-bypassable yang menyediakan katalog benang, kain, aksesoris,
-- satuan kemasan, dan riwayat harga acuan point-in-time untuk Tech Pack dan Costing HPP.
-- ==============================================================================

-- 1. Sequence table untuk alokasi kode material atomik per kategori (e.g. YRN-0001, FAB-0001)
CREATE TABLE IF NOT EXISTS material_code_sequences (
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    category_code          VARCHAR(32) NOT NULL,
    current_seq            BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, category_code)
);

SELECT apply_tenant_rls('material_code_sequences');

-- 2. Material Items (Katalog Bahan & Layanan)
CREATE TABLE IF NOT EXISTS material_items (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code                   VARCHAR(32) NOT NULL,
    name                   VARCHAR(200) NOT NULL,
    category               VARCHAR(50) NOT NULL,
    base_uom               VARCHAR(20) NOT NULL,
    alternate_uoms         JSONB NOT NULL DEFAULT '[]',
    default_ownership      VARCHAR(50) NOT NULL DEFAULT 'owned_raw_material',
    description            TEXT NOT NULL DEFAULT '',
    custom_attributes      JSONB NOT NULL DEFAULT '{}',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at            TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_material_items_tenant_code_active
    ON material_items(tenant_id, code) WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_material_items_tenant_active
    ON material_items(tenant_id, updated_at DESC) WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_material_items_name
    ON material_items(tenant_id, lower(name)) WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_material_items_category
    ON material_items(tenant_id, category) WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_material_items_custom_attributes_gin
    ON material_items USING GIN (custom_attributes jsonb_path_ops);

SELECT apply_tenant_rls('material_items');

-- 3. Material Prices (Append-only point-in-time pricing history)
CREATE TABLE IF NOT EXISTS material_prices (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    material_id            VARCHAR(64) NOT NULL REFERENCES material_items(id) ON DELETE CASCADE,
    amount_minor           BIGINT NOT NULL,
    currency               VARCHAR(10) NOT NULL DEFAULT 'IDR',
    per_quantity_micros    BIGINT NOT NULL,
    per_uom                VARCHAR(20) NOT NULL,
    source                 VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    effective_from         TIMESTAMPTZ NOT NULL,
    note                   TEXT NOT NULL DEFAULT '',
    recorded_by_user_id    VARCHAR(64) REFERENCES users(id) ON DELETE SET NULL,
    recorded_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_amount_minor_non_negative CHECK (amount_minor >= 0),
    CONSTRAINT ck_consigned_is_zero CHECK (source <> 'CLIENT_SUPPLIED_ZERO' OR amount_minor = 0),
    CONSTRAINT uq_material_price_point_in_time UNIQUE (material_id, source, effective_from)
);

CREATE INDEX IF NOT EXISTS idx_material_prices_query
    ON material_prices(tenant_id, material_id, source, effective_from DESC);

SELECT apply_tenant_rls('material_prices');

-- 4. Material Price Policies (Tenant-level pricing preference strategy)
CREATE TABLE IF NOT EXISTS material_price_policies (
    tenant_id              VARCHAR(64) PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    preference_order       JSONB NOT NULL DEFAULT '["STANDARD"]',
    fallback_to_standard   BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT apply_tenant_rls('material_price_policies');
