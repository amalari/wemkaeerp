-- ==============================================================================
-- WeMade ERP — Spesifikasi Teknis & Bill of Material (Tech Pack BOM) (V28)
-- ==============================================================================
-- Menyediakan tabel untuk tech pack, baris BOM, operasi kerja (SAM),
-- penskalaan yield ukuran, dan sekuens style code atomik per tenant.
-- ==============================================================================

-- 1. Sequence table untuk alokasi nomor kode style atomik (e.g. STY-0001)
CREATE TABLE IF NOT EXISTS tech_pack_style_sequences (
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    prefix                 VARCHAR(32) NOT NULL DEFAULT 'STY',
    current_seq            BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, prefix)
);

SELECT apply_tenant_rls('tech_pack_style_sequences');

-- 2. Tech Packs (Agregat Induk)
CREATE TABLE IF NOT EXISTS tech_packs (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    style_code             VARCHAR(32) NOT NULL,
    style_name             VARCHAR(150) NOT NULL,
    client_name            VARCHAR(150) NOT NULL DEFAULT '',
    status                 VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    version                INT NOT NULL DEFAULT 1,
    source_sample_spec_id  VARCHAR(64) REFERENCES sampling_orders(id) ON DELETE SET NULL,
    source_spk_number      VARCHAR(50) NOT NULL DEFAULT '',
    custom_attributes      JSONB NOT NULL DEFAULT '{}',
    notes                  TEXT NOT NULL DEFAULT '',
    created_by_user_id     VARCHAR(64) REFERENCES users(id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at            TIMESTAMPTZ,
    archived_at            TIMESTAMPTZ,
    CONSTRAINT uq_tech_packs_tenant_style_version UNIQUE (tenant_id, style_code, version),
    CONSTRAINT chk_tech_packs_released CHECK (status <> 'RELEASED' OR released_at IS NOT NULL)
);

-- Satu draf aktif per style code
CREATE UNIQUE INDEX IF NOT EXISTS uq_tech_packs_single_draft
    ON tech_packs(tenant_id, style_code) WHERE status = 'DRAFT' AND archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_tech_packs_tenant_updated
    ON tech_packs(tenant_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_tech_packs_style_search
    ON tech_packs(tenant_id, lower(style_name)) WHERE archived_at IS NULL;

SELECT apply_tenant_rls('tech_packs');

-- 3. Tech Pack BOM Lines
CREATE TABLE IF NOT EXISTS tech_pack_bom_lines (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    tech_pack_id           VARCHAR(64) NOT NULL REFERENCES tech_packs(id) ON DELETE CASCADE,
    line_id                VARCHAR(64) NOT NULL,
    material_free_text     TEXT NOT NULL,
    material_id            VARCHAR(64) REFERENCES material_items(id) ON DELETE SET NULL,
    material_code          VARCHAR(50),
    material_name          VARCHAR(150),
    category               VARCHAR(40) NOT NULL,
    net_quantity_micros    BIGINT NOT NULL,
    net_uom                VARCHAR(20) NOT NULL,
    waste_numerator        BIGINT NOT NULL DEFAULT 0,
    waste_denominator      BIGINT NOT NULL DEFAULT 1,
    ownership              VARCHAR(40) NOT NULL DEFAULT 'owned_raw_material',
    notes                  TEXT NOT NULL DEFAULT '',
    sort_order             INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tech_pack_bom_line UNIQUE (tech_pack_id, line_id),
    CONSTRAINT chk_bom_net_positive CHECK (net_quantity_micros >= 0),
    CONSTRAINT chk_bom_waste_denom CHECK (waste_denominator <> 0)
);

CREATE INDEX IF NOT EXISTS idx_tech_pack_bom_lines_tp
    ON tech_pack_bom_lines(tenant_id, tech_pack_id);

CREATE INDEX IF NOT EXISTS idx_tech_pack_bom_lines_material
    ON tech_pack_bom_lines(tenant_id, material_id) WHERE material_id IS NOT NULL;

SELECT apply_tenant_rls('tech_pack_bom_lines');

-- 4. Tech Pack Labor Operations (SAM & Stasiun Kerja)
CREATE TABLE IF NOT EXISTS tech_pack_labor_operations (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    tech_pack_id           VARCHAR(64) NOT NULL REFERENCES tech_packs(id) ON DELETE CASCADE,
    operation_id           VARCHAR(64) NOT NULL,
    name                   VARCHAR(150) NOT NULL,
    sam_numerator          BIGINT NOT NULL DEFAULT 0,
    sam_denominator        BIGINT NOT NULL DEFAULT 1,
    workstation            VARCHAR(100) NOT NULL DEFAULT '',
    is_subcontracted       BOOLEAN NOT NULL DEFAULT false,
    sort_order             INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tech_pack_labor_op UNIQUE (tech_pack_id, operation_id),
    CONSTRAINT chk_labor_sam_denom CHECK (sam_denominator <> 0)
);

CREATE INDEX IF NOT EXISTS idx_tech_pack_labor_ops_tp
    ON tech_pack_labor_operations(tenant_id, tech_pack_id);

SELECT apply_tenant_rls('tech_pack_labor_operations');

-- 5. Tech Pack Size Yields (Faktor Penskalaan Ukuran)
CREATE TABLE IF NOT EXISTS tech_pack_size_yields (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    tech_pack_id           VARCHAR(64) NOT NULL REFERENCES tech_packs(id) ON DELETE CASCADE,
    size_label             VARCHAR(30) NOT NULL,
    scale_numerator        BIGINT NOT NULL DEFAULT 1,
    scale_denominator      BIGINT NOT NULL DEFAULT 1,
    ordered_quantity       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tech_pack_size_yield UNIQUE (tech_pack_id, size_label),
    CONSTRAINT chk_size_scale_denom CHECK (scale_denominator <> 0)
);

CREATE INDEX IF NOT EXISTS idx_tech_pack_size_yields_tp
    ON tech_pack_size_yields(tenant_id, tech_pack_id);

SELECT apply_tenant_rls('tech_pack_size_yields');
