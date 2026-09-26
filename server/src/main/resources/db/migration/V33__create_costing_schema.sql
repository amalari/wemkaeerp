-- ==============================================================================
-- WeMade ERP — Modul COSTING_HPP (V33)
-- ==============================================================================
-- Menyediakan tabel rate card per tenant ber-tanggal-berlaku [effectiveFrom, effectiveTo),
-- lembar kalkulasi HPP dengan snapshot komersial JSONB, dan rincian bucket biaya.
-- ==============================================================================

-- 1. Rate Card per tenant & per behavior
CREATE TABLE IF NOT EXISTS costing_rate_cards (
    id                             VARCHAR(64) PRIMARY KEY,
    tenant_id                      VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    behavior                       VARCHAR(64) NOT NULL,
    version                        INT NOT NULL DEFAULT 1,
    effective_from                 TIMESTAMPTZ NOT NULL,
    effective_to                   TIMESTAMPTZ,
    description                    TEXT NOT NULL DEFAULT '',
    labor_rate_minor_units         BIGINT,
    subcontract_rate_minor_units   BIGINT,
    service_fee_minor_units        BIGINT,
    overhead_minor_units           BIGINT,
    packing_unit_minor_units       BIGINT,
    packing_order_minor_units      BIGINT,
    margin_ratio_micros            BIGINT,
    retail_markup_micros           BIGINT,
    marketplace_fee_micros         BIGINT,
    fabric_waste_micros            BIGINT,
    include_fabric_cost            BOOLEAN,
    seeded_from_node_id            VARCHAR(64),
    created_by_user_id             VARCHAR(64),
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_costing_rate_cards_lookup
    ON costing_rate_cards(tenant_id, behavior, effective_from DESC);

SELECT apply_tenant_rls('costing_rate_cards');

-- 2. Lembar Kalkulasi HPP (Costing Sheets)
CREATE TABLE IF NOT EXISTS costing_sheets (
    id                             VARCHAR(64) PRIMARY KEY,
    tenant_id                      VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    number                         VARCHAR(64) NOT NULL,
    tech_pack_id                   VARCHAR(64) NOT NULL,
    order_quantity                 BIGINT NOT NULL,
    behavior                       VARCHAR(64) NOT NULL,
    status                         VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    pricing_as_of                  TIMESTAMPTZ NOT NULL,
    parameter_overrides            JSONB NOT NULL DEFAULT '{}',
    latest_result                  JSONB,
    approved_snapshot              JSONB,
    rejection_reason               TEXT,
    notes                          TEXT NOT NULL DEFAULT '',
    linked_spk_number              VARCHAR(64),
    created_by_user_id             VARCHAR(64),
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_costing_sheets_order_qty CHECK (order_quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_costing_sheets_tenant
    ON costing_sheets(tenant_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_costing_sheets_techpack
    ON costing_sheets(tenant_id, tech_pack_id);

SELECT apply_tenant_rls('costing_sheets');

-- 3. Rincian Bucket Biaya HPP (Costing Sheet Buckets)
CREATE TABLE IF NOT EXISTS costing_sheet_buckets (
    id                             VARCHAR(64) PRIMARY KEY,
    tenant_id                      VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sheet_id                       VARCHAR(64) NOT NULL REFERENCES costing_sheets(id) ON DELETE CASCADE,
    kind                           VARCHAR(32) NOT NULL,
    label                          VARCHAR(150) NOT NULL,
    amount_per_unit_minor_units    BIGINT NOT NULL,
    ownership                      VARCHAR(64) NOT NULL,
    is_billable                    BOOLEAN NOT NULL DEFAULT TRUE,
    source_refs                    JSONB NOT NULL DEFAULT '[]',
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_costing_sheet_buckets_sheet
    ON costing_sheet_buckets(tenant_id, sheet_id);

SELECT apply_tenant_rls('costing_sheet_buckets');
