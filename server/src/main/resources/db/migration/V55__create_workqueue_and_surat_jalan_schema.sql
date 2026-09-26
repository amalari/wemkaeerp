-- ==============================================================================
-- WeMade ERP — WORKQUEUE & SURAT JALAN MULTI-SITE / MAKLOON (V55)
-- ==============================================================================
-- Mendukung alur lantai produksi konveksi & rajut:
-- 1. work_cards: Antrean kerja per stasiun (potong, jahit, obras, steam, dll.)
-- 2. work_deposits: Setoran output piece-rate operator harian (anti-double claim)
-- 3. rework_tickets: Tiket perbaikan cacat bertingkat (Tier 1-3)
-- 4. tenant_locations: Multi-lokasi / multi-gedung pabrik fisik
-- 5. surat_jalan_manifests & surat_jalan_items:
--    - Mutasi Internal (mempertahankan tiket bundle individual)
--    - Makloon Vendor (melebur bundle menjadi lot masal ber-SLA & tarif)
--    - Pengiriman Pembeli (berbasis nomor kardus/karung & backlog bertahap)
-- ==============================================================================

-- 1. Lokasi Fisik Pabrik (Multi-Site)
CREATE TABLE IF NOT EXISTS tenant_locations (
    id                      VARCHAR(64) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    name                    VARCHAR(150) NOT NULL,
    code                    VARCHAR(50) NOT NULL,
    address                 TEXT NOT NULL DEFAULT '',
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_locations_tenant_code
    ON tenant_locations(tenant_id, code);

-- 2. Kartu Antrean Kerja Stasiun (Work Card)
CREATE TABLE IF NOT EXISTS work_cards (
    id                      VARCHAR(64) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    subject_kind            VARCHAR(30) NOT NULL,
    subject_id              VARCHAR(64) NOT NULL,
    order_number            VARCHAR(100) NOT NULL,
    article_name            VARCHAR(150) NOT NULL,
    station_code            VARCHAR(50) NOT NULL,
    size_label              VARCHAR(60) NOT NULL,
    bundle_no               INTEGER,
    queued_pcs              INTEGER NOT NULL,
    wip_pcs                 INTEGER NOT NULL,
    scrap_pcs               INTEGER NOT NULL DEFAULT 0,
    rework_pcs              INTEGER NOT NULL DEFAULT 0,
    tracking_unit           VARCHAR(30) NOT NULL DEFAULT 'BUNDLE',
    status                  VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    execution_mode          VARCHAR(30) NOT NULL DEFAULT 'IN_HOUSE',
    vendor_ref              VARCHAR(150),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_work_cards_tenant_station
    ON work_cards(tenant_id, station_code);
CREATE INDEX IF NOT EXISTS idx_work_cards_tenant_subject
    ON work_cards(tenant_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_work_cards_tenant_status
    ON work_cards(tenant_id, status);

-- 3. Setoran Borongan Operator (Work Deposit - Append Only)
CREATE TABLE IF NOT EXISTS work_deposits (
    id                      VARCHAR(64) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    work_card_id            VARCHAR(64) NOT NULL REFERENCES work_cards(id) ON DELETE CASCADE,
    operator_id             VARCHAR(64) NOT NULL,
    operator_name           VARCHAR(150) NOT NULL,
    qty_pcs                 INTEGER NOT NULL,
    tariff_snapshot_idr     BIGINT NOT NULL DEFAULT 0,
    is_rework_deposit       BOOLEAN NOT NULL DEFAULT FALSE,
    notes                   TEXT NOT NULL DEFAULT '',
    verified_photo_key      TEXT,
    submitted_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_work_deposits_tenant_op
    ON work_deposits(tenant_id, operator_id);
CREATE INDEX IF NOT EXISTS idx_work_deposits_card
    ON work_deposits(work_card_id);

-- 4. Tiket Perbaikan Cacat (Rework Ticket)
CREATE TABLE IF NOT EXISTS rework_tickets (
    id                          VARCHAR(64) PRIMARY KEY,
    tenant_id                   VARCHAR(64) NOT NULL,
    work_card_id                VARCHAR(64) NOT NULL REFERENCES work_cards(id) ON DELETE CASCADE,
    subject_kind                VARCHAR(30) NOT NULL,
    subject_id                  VARCHAR(64) NOT NULL,
    order_number                VARCHAR(100) NOT NULL,
    article_name                VARCHAR(150) NOT NULL,
    defect_code                 VARCHAR(64) NOT NULL,
    defect_display_name         VARCHAR(150) NOT NULL,
    liability                   VARCHAR(40) NOT NULL,
    qty_pcs                     INTEGER NOT NULL,
    size_label                  VARCHAR(60) NOT NULL,
    target_station_code         VARCHAR(50) NOT NULL,
    responsible_operator_id     VARCHAR(64),
    assigned_repair_operator_id VARCHAR(64),
    status                      VARCHAR(40) NOT NULL DEFAULT 'REWORK_ISSUED',
    qc_notes                    TEXT NOT NULL DEFAULT '',
    repair_notes                TEXT NOT NULL DEFAULT '',
    scrap_reason                TEXT,
    issued_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    in_repair_at                TIMESTAMPTZ,
    ready_for_recheck_at        TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_rework_tickets_tenant_station
    ON rework_tickets(tenant_id, target_station_code);
CREATE INDEX IF NOT EXISTS idx_rework_tickets_tenant_status
    ON rework_tickets(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_rework_tickets_card
    ON rework_tickets(work_card_id);

-- 5. Dokumen Surat Jalan (Manifest Mutasi / Makloon / Pengiriman)
CREATE TABLE IF NOT EXISTS surat_jalan_manifests (
    id                      VARCHAR(64) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    sj_number               VARCHAR(100) NOT NULL,
    transfer_type           VARCHAR(40) NOT NULL,
    subject_kind            VARCHAR(30) NOT NULL,
    subject_id              VARCHAR(64) NOT NULL,
    order_number            VARCHAR(100) NOT NULL,
    article_name            VARCHAR(150) NOT NULL,
    origin_location_id      VARCHAR(64),
    destination_location_id VARCHAR(64),
    vendor_ref              VARCHAR(150),
    customer_name           VARCHAR(150),
    customer_address        TEXT,
    carrier_name            VARCHAR(100),
    driver_name             VARCHAR(100),
    vehicle_plate           VARCHAR(50),
    status                  VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    unit_service_fee_idr    BIGINT NOT NULL DEFAULT 0,
    expected_return_date    DATE,
    dispatched_at           TIMESTAMPTZ,
    received_at             TIMESTAMPTZ,
    notes                   TEXT NOT NULL DEFAULT ''
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_surat_jalan_manifests_tenant_number
    ON surat_jalan_manifests(tenant_id, sj_number);
CREATE INDEX IF NOT EXISTS idx_surat_jalan_manifests_tenant_type
    ON surat_jalan_manifests(tenant_id, transfer_type);
CREATE INDEX IF NOT EXISTS idx_surat_jalan_manifests_tenant_status
    ON surat_jalan_manifests(tenant_id, status);

-- 6. Rincian Barang Surat Jalan (Items)
CREATE TABLE IF NOT EXISTS surat_jalan_items (
    id                      VARCHAR(64) PRIMARY KEY,
    manifest_id             VARCHAR(64) NOT NULL REFERENCES surat_jalan_manifests(id) ON DELETE CASCADE,
    work_card_id            VARCHAR(64),
    bundle_no               INTEGER,
    carton_id               VARCHAR(64),
    size_label              VARCHAR(60) NOT NULL,
    colorway                VARCHAR(120) NOT NULL DEFAULT '',
    qty_pcs                 INTEGER NOT NULL,
    notes                   TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_surat_jalan_items_manifest
    ON surat_jalan_items(manifest_id);

-- 7. Seed Lokasi Default untuk Demo Tenant
INSERT INTO tenant_locations (id, tenant_id, name, code, address, is_active)
VALUES
    ('loc-rajut-01', 'demo-tenant', 'Gedung Rajut & Bordir', 'GEDUNG_A', 'Komp. Industri Cimahi Blok A-2', TRUE),
    ('loc-finishing-01', 'demo-tenant', 'Gedung Jahit & Finishing', 'GEDUNG_B', 'Komp. Industri Cimahi Blok B-5', TRUE)
ON CONFLICT (id) DO NOTHING;
