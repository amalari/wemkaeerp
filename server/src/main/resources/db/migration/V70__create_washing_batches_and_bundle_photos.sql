-- ==============================================================================
-- WeMade ERP — V70: Washing Batches, Bundle Photo Audit & Lot Splitting
-- ==============================================================================
-- Mendukung kontrol fisik gerbang pencucian (Washing Gate):
-- 1. washing_batches: Sesi drum cuci gabungan beberapa bundle (multi-bundle/multi-PO)
-- 2. washing_batch_items: Rincian bundle masuk + BUKTI FOTO FISIK PER BUNDLE
-- 3. washing_batch_sort_outputs: Hasil meja sortir pasca-dryer per PO & Ukuran
-- ==============================================================================

-- 1. Sesi Cuci Drum / Mesin Cuci Masal
CREATE TABLE IF NOT EXISTS washing_batches (
    id                      VARCHAR(64) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    batch_code              VARCHAR(50) NOT NULL,
    machine_drum_no         VARCHAR(50) NOT NULL DEFAULT '',
    wash_recipe             VARCHAR(100) NOT NULL DEFAULT '',
    operator_name           VARCHAR(150) NOT NULL DEFAULT '',
    total_bundles           INTEGER NOT NULL DEFAULT 0,
    total_input_pcs         INTEGER NOT NULL DEFAULT 0,
    total_output_pcs        INTEGER NOT NULL DEFAULT 0,
    missing_pcs             INTEGER NOT NULL DEFAULT 0,
    status                  VARCHAR(30) NOT NULL DEFAULT 'IN_WASHER',
    notes                   TEXT NOT NULL DEFAULT '',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_washing_batches_tenant_code
    ON washing_batches(tenant_id, batch_code);
CREATE INDEX IF NOT EXISTS idx_washing_batches_tenant_status
    ON washing_batches(tenant_id, status);

SELECT apply_tenant_rls('washing_batches');

-- 2. Rincian Bundle Masuk Drum (dengan Bukti Foto Per Bundle)
CREATE TABLE IF NOT EXISTS washing_batch_items (
    id                      VARCHAR(64) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    batch_id                VARCHAR(64) NOT NULL REFERENCES washing_batches(id) ON DELETE CASCADE,
    work_card_id            VARCHAR(64) NOT NULL REFERENCES work_cards(id) ON DELETE CASCADE,
    subject_id              VARCHAR(64) NOT NULL,
    order_number            VARCHAR(100) NOT NULL,
    article_name            VARCHAR(150) NOT NULL DEFAULT '',
    bundle_no               INTEGER NOT NULL,
    size_label              VARCHAR(60) NOT NULL,
    input_pcs               INTEGER NOT NULL,
    bundle_photo_key        TEXT NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_washing_batch_items_batch
    ON washing_batch_items(batch_id);
CREATE INDEX IF NOT EXISTS idx_washing_batch_items_card
    ON washing_batch_items(work_card_id);

SELECT apply_tenant_rls('washing_batch_items');

-- 3. Hasil Sortir Meja Pasca-Dryer (Pemisahan per PO & Size menuju Setrika)
CREATE TABLE IF NOT EXISTS washing_batch_sort_outputs (
    id                      VARCHAR(64) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    batch_id                VARCHAR(64) NOT NULL REFERENCES washing_batches(id) ON DELETE CASCADE,
    subject_id              VARCHAR(64) NOT NULL,
    order_number            VARCHAR(100) NOT NULL,
    size_label              VARCHAR(60) NOT NULL,
    output_pcs              INTEGER NOT NULL,
    scrap_pcs               INTEGER NOT NULL DEFAULT 0,
    defect_pcs              INTEGER NOT NULL DEFAULT 0,
    notes                   TEXT NOT NULL DEFAULT '',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_washing_batch_sort_outputs_batch
    ON washing_batch_sort_outputs(batch_id);

SELECT apply_tenant_rls('washing_batch_sort_outputs');
