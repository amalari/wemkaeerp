-- ==============================================================================
-- WeMade ERP — Sampling Multi-Division Workflow (V41)
-- ==============================================================================
-- Menghubungkan 4 pilar alur sampling:
-- 1. Divisi Sampling: Tahap pipeline & tenselity matrix
-- 2. Jalur Finishing & Makloon Vendor: Tracking vendor luar (Pak Asep) vs internal
-- 3. Divisi Finishing Internal: Log setoran parsial (pcs & kg timbangan + foto)
-- 4. Divisi Quality Control: Inspeksi fisik POM vs size chart buyer & defect checklist
-- ==============================================================================

-- 1. Pipeline Stage, Finishing Path, dan Makloon Vendor Info pada sampling_orders
ALTER TABLE sampling_orders
    ADD COLUMN IF NOT EXISTS pipeline_stage VARCHAR(50) NOT NULL DEFAULT 'NEW_INTAKE',
    ADD COLUMN IF NOT EXISTS finishing_path VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN IF NOT EXISTS vendor_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS vendor_phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS vendor_sent_at DATE,
    ADD COLUMN IF NOT EXISTS vendor_target_at DATE,
    ADD COLUMN IF NOT EXISTS vendor_returned_at DATE,
    ADD COLUMN IF NOT EXISTS vendor_cost_per_pcs BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS vendor_status VARCHAR(50) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS vendor_notes TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_sampling_orders_stage
    ON sampling_orders(tenant_id, pipeline_stage) WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_sampling_orders_vendor_status
    ON sampling_orders(tenant_id, vendor_status) WHERE archived_at IS NULL;

-- 2. Tenselity Setting Matrix pada sampling_machine_programs
ALTER TABLE sampling_machine_programs
    ADD COLUMN IF NOT EXISTS tenselity_entries JSONB NOT NULL DEFAULT '[]';

-- 3. Log Setoran Bertahap Tim Finishing Internal (Tanpa Batching Rumit)
CREATE TABLE IF NOT EXISTS sampling_finishing_deposits (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sampling_order_id      VARCHAR(64) NOT NULL REFERENCES sampling_orders(id) ON DELETE CASCADE,
    deposit_date           DATE NOT NULL DEFAULT CURRENT_DATE,
    qty_pcs                INT NOT NULL,
    weight_kg              NUMERIC(6, 2) NOT NULL DEFAULT 0.0,
    scale_photo_key        TEXT,
    garment_photo_key      TEXT,
    operator_name          VARCHAR(100) NOT NULL DEFAULT '',
    notes                  TEXT NOT NULL DEFAULT '',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sampling_finishing_deposits_order
    ON sampling_finishing_deposits(tenant_id, sampling_order_id, deposit_date DESC);

SELECT apply_tenant_rls('sampling_finishing_deposits');

-- 4. Lembar Verifikasi & Inspeksi Quality Control (QC Sample)
CREATE TABLE IF NOT EXISTS sampling_qc_inspections (
    id                       VARCHAR(64) PRIMARY KEY,
    tenant_id                VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sampling_order_id        VARCHAR(64) NOT NULL REFERENCES sampling_orders(id) ON DELETE CASCADE,
    inspector_name           VARCHAR(100) NOT NULL DEFAULT '',
    inspected_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    measured_pom_values      JSONB NOT NULL DEFAULT '[]',
    defects_found            JSONB NOT NULL DEFAULT '[]',
    qc_result                VARCHAR(30) NOT NULL DEFAULT 'PASSED', -- PASSED | REWORK | REJECT
    qc_notes                 TEXT NOT NULL DEFAULT '',
    verified_photo_front_key TEXT,
    verified_photo_back_key  TEXT
);

CREATE INDEX IF NOT EXISTS idx_sampling_qc_inspections_order
    ON sampling_qc_inspections(tenant_id, sampling_order_id, inspected_at DESC);

SELECT apply_tenant_rls('sampling_qc_inspections');
