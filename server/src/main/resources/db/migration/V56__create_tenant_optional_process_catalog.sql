-- ==============================================================================
-- WeMade ERP — KATALOG PROSES OPSIONAL PER TENANT (V56)
-- ==============================================================================
-- Mendukung flow dinamis per tenant: tahapan produksi opsional (Bordir, Sablon,
-- Laundry, dst.) yang bisa disisipkan divisi sampling di mana saja dalam alur —
-- di flow sampling (jangkar = SamplingPipelineStage) maupun di line workqueue
-- (jangkar = kode stasiun). Flow wajib tetap kerangka kode; tahapan opsional
-- 100% data per tenant (Kontrak 7 — isolasi pipeline multi-tenant).
-- ==============================================================================

CREATE TABLE IF NOT EXISTS tenant_optional_processes (
    id                          VARCHAR(64) PRIMARY KEY,            -- processId deterministik: proc-<kode>
    tenant_id                   VARCHAR(64) NOT NULL,
    process_code                VARCHAR(64) NOT NULL,
    display_name                VARCHAR(150) NOT NULL,
    archetype                   VARCHAR(40) NOT NULL,
    sampling_anchor_after       VARCHAR(40),                        -- nama enum SamplingPipelineStage; NULL = tidak masuk flow sampling
    station_anchor_after        VARCHAR(50),                        -- kode WorkStationCode; NULL = tidak masuk line workqueue
    execution_mode              VARCHAR(30) NOT NULL DEFAULT 'IN_HOUSE',
    vendor_ref                  VARCHAR(150),
    piecerate_tariff_idr        BIGINT NOT NULL DEFAULT 0,
    standard_minutes_per_piece  DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_optional_process_code UNIQUE (tenant_id, process_code),
    CONSTRAINT ck_tenant_optional_process_anchor
        CHECK (sampling_anchor_after IS NOT NULL OR station_anchor_after IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_tenant_optional_processes_tenant
    ON tenant_optional_processes(tenant_id);