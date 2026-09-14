-- ==============================================================================
-- WeMade ERP — Sampling Orders (BusinessModule.SAMPLING_ORDER)
-- ==============================================================================
-- Modul operasional kedua: Pola & Sampling Order (SPK Sample Rajut / Garment Tech Sheet).
-- Menyimpan lembar kerja teknis SPK rajut (Flat Knitting & Garment):
-- - Spesifikasi benang, jenis rajut, dan feeder bar
-- - Size chart ganda (Ukuran Jadi vs Ukuran Rajut Mentah Mesin)
-- - Setelan tenselity & kode file program CAM (BIAN-D, BIAN-B, dll)
-- - Timbangan gramasi panel & waktu rajut per panel
-- - Status milestone tracker pengerjaan (Program -> Rajut -> Linking -> Washing -> Kirim -> HPP)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS sampling_orders (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    spk_number             VARCHAR(50) NOT NULL,

    client_name            VARCHAR(150) NOT NULL,
    style_name             VARCHAR(150) NOT NULL,
    status                 VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- DRAFT | IN_PROGRESS | REVISION | ACC_APPROVED | CANCELLED
    size_mode              VARCHAR(20) NOT NULL DEFAULT 'ALL_SIZE', -- ALL_SIZE | MULTI_SIZE

    deadline_program       DATE,
    deadline_finishing     DATE,
    deadline_delivery      DATE,

    -- Loose relation to CRM lead (nullable for loose coupling / standalone sampling)
    lead_id                VARCHAR(64) REFERENCES crm_leads(id) ON DELETE SET NULL,

    acc_notes              TEXT NOT NULL DEFAULT '',
    notes                  TEXT NOT NULL DEFAULT '',

    created_by_user_id     VARCHAR(64) REFERENCES users(id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at            TIMESTAMPTZ,

    CONSTRAINT uq_sampling_order_spk UNIQUE (tenant_id, spk_number)
);

CREATE INDEX IF NOT EXISTS idx_sampling_orders_tenant_active
    ON sampling_orders(tenant_id, updated_at DESC) WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_sampling_orders_status
    ON sampling_orders(tenant_id, status) WHERE archived_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_sampling_orders_lead
    ON sampling_orders(tenant_id, lead_id) WHERE lead_id IS NOT NULL;

SELECT apply_tenant_rls('sampling_orders');

-- 2. Knit Specifications Table (Spesifikasi Benang & Konstruksi Rajut)
CREATE TABLE IF NOT EXISTS sampling_knit_specs (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sampling_order_id      VARCHAR(64) NOT NULL REFERENCES sampling_orders(id) ON DELETE CASCADE,

    yarn_type              VARCHAR(100) NOT NULL DEFAULT '',
    knit_type              VARCHAR(100) NOT NULL DEFAULT '',
    rib_spec               VARCHAR(100) NOT NULL DEFAULT '',
    collar_spec            VARCHAR(100) NOT NULL DEFAULT '',
    placket_spec           VARCHAR(100) NOT NULL DEFAULT '',
    colorway_notes         TEXT NOT NULL DEFAULT '',
    mockup_image_urls      JSONB NOT NULL DEFAULT '[]',

    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_sampling_knit_specs_order UNIQUE (sampling_order_id)
);

CREATE INDEX IF NOT EXISTS idx_sampling_knit_specs_tenant
    ON sampling_knit_specs(tenant_id);

SELECT apply_tenant_rls('sampling_knit_specs');

-- 3. Size Charts Table (Ukuran Jadi vs Ukuran Rajut Mentah Mesin)
CREATE TABLE IF NOT EXISTS sampling_size_charts (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sampling_order_id      VARCHAR(64) NOT NULL REFERENCES sampling_orders(id) ON DELETE CASCADE,

    category               VARCHAR(30) NOT NULL, -- FINISHED_SIZE | KNIT_RAW_SIZE
    size_label             VARCHAR(30) NOT NULL DEFAULT 'ALL SIZE', -- 'ALL SIZE', 'S', 'M', 'L', 'XL'

    body_length            NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    body_width             NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    sleeve_length          NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    arm_hole               NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    neck_drop              NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    neck_width             NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    shoulder_width         NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    rib_height             NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    collar_height          NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    placket_width          NUMERIC(6, 1) NOT NULL DEFAULT 0.0,
    sleeve_opening         NUMERIC(6, 1) NOT NULL DEFAULT 0.0,

    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_sampling_size_charts_unique UNIQUE (sampling_order_id, category, size_label)
);

CREATE INDEX IF NOT EXISTS idx_sampling_size_charts_order
    ON sampling_size_charts(sampling_order_id, category);

SELECT apply_tenant_rls('sampling_size_charts');

-- 4. Machine Programs Table (Program CAM, Feeder 1-7, Rumus Jarum & Tenselity)
CREATE TABLE IF NOT EXISTS sampling_machine_programs (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sampling_order_id      VARCHAR(64) NOT NULL REFERENCES sampling_orders(id) ON DELETE CASCADE,

    program_front          VARCHAR(100) NOT NULL DEFAULT '',
    program_back           VARCHAR(100) NOT NULL DEFAULT '',
    program_sleeve         VARCHAR(100) NOT NULL DEFAULT '',
    program_collar         VARCHAR(100) NOT NULL DEFAULT '',
    program_placket        VARCHAR(100) NOT NULL DEFAULT '',

    feeder_instructions    JSONB NOT NULL DEFAULT '[]',
    pattern_formulas       JSONB NOT NULL DEFAULT '{}',
    tension_settings       JSONB NOT NULL DEFAULT '{}',

    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_sampling_machine_programs_order UNIQUE (sampling_order_id)
);

CREATE INDEX IF NOT EXISTS idx_sampling_programs_tenant
    ON sampling_machine_programs(tenant_id);

SELECT apply_tenant_rls('sampling_machine_programs');

-- 5. Yield & Timings Table (Gramasi & Cycle Time per Panel)
CREATE TABLE IF NOT EXISTS sampling_yield_timings (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sampling_order_id      VARCHAR(64) NOT NULL REFERENCES sampling_orders(id) ON DELETE CASCADE,

    panel_weights_grams    JSONB NOT NULL DEFAULT '{}',
    panel_knitting_minutes JSONB NOT NULL DEFAULT '{}',
    linking_notes          TEXT NOT NULL DEFAULT '',
    additional_process     TEXT NOT NULL DEFAULT '',
    is_washed              BOOLEAN NOT NULL DEFAULT FALSE,
    estimated_hpp_idr      BIGINT NOT NULL DEFAULT 0,

    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_sampling_yield_timings_order UNIQUE (sampling_order_id)
);

CREATE INDEX IF NOT EXISTS idx_sampling_yield_tenant
    ON sampling_yield_timings(tenant_id);

SELECT apply_tenant_rls('sampling_yield_timings');

-- 6. Milestones Table (Tahapan Alur Pengerjaan Sample)
CREATE TABLE IF NOT EXISTS sampling_milestones (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sampling_order_id      VARCHAR(64) NOT NULL REFERENCES sampling_orders(id) ON DELETE CASCADE,

    step_name              VARCHAR(30) NOT NULL, -- PROGRAM | RAJUT | PROSES_TAMBAHAN | LINKING | WASHING | KIRIM | HPP
    is_completed           BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at           DATE,
    step_order             INT NOT NULL DEFAULT 0,
    notes                  TEXT NOT NULL DEFAULT '',

    CONSTRAINT uq_sampling_milestone_step UNIQUE (sampling_order_id, step_name)
);

CREATE INDEX IF NOT EXISTS idx_sampling_milestones_order
    ON sampling_milestones(sampling_order_id, step_order);

SELECT apply_tenant_rls('sampling_milestones');
