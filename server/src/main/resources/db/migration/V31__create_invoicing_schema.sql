-- ==============================================================================
-- WeMade ERP — Modul Invoicing & Penagihan Berkanvas (V31)
-- ==============================================================================
-- Menyediakan tabel nomor sekuens atomik, profil penerbit tagihan,
-- template kanvas milimeter (Mm10), faktur, baris faktur, dan riwayat pembayaran.
-- ==============================================================================

-- 1. Sequence table untuk alokasi nomor faktur atomik per tenant, prefix, tahun, bulan
CREATE TABLE IF NOT EXISTS invoice_number_sequences (
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    prefix                 VARCHAR(32) NOT NULL DEFAULT 'INV',
    year                   INT NOT NULL,
    month                  INT NOT NULL,
    current_seq            BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, prefix, year, month)
);

SELECT apply_tenant_rls('invoice_number_sequences');

-- 2. Profil Penerbit Faktur (Issuer Profile per tenant)
CREATE TABLE IF NOT EXISTS invoice_issuer_profiles (
    tenant_id              VARCHAR(64) PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    company_name           VARCHAR(150) NOT NULL,
    tagline                VARCHAR(200) NOT NULL DEFAULT '',
    address                TEXT NOT NULL DEFAULT '',
    phone                  VARCHAR(50) NOT NULL DEFAULT '',
    email                  VARCHAR(100) NOT NULL DEFAULT '',
    tax_id                 VARCHAR(50) NOT NULL DEFAULT '',
    bank_name              VARCHAR(100) NOT NULL DEFAULT '',
    bank_account_number    VARCHAR(100) NOT NULL DEFAULT '',
    bank_account_holder    VARCHAR(150) NOT NULL DEFAULT '',
    logo_url               TEXT,
    signature_name         VARCHAR(150) NOT NULL DEFAULT '',
    signature_title        VARCHAR(100) NOT NULL DEFAULT '',
    signature_image_url    TEXT,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT apply_tenant_rls('invoice_issuer_profiles');

-- 3. Template Faktur Berkanvas A4 (Invoice Templates)
CREATE TABLE IF NOT EXISTS invoice_templates (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                   VARCHAR(150) NOT NULL,
    description            TEXT NOT NULL DEFAULT '',
    paper_size             VARCHAR(30) NOT NULL DEFAULT 'A4_PORTRAIT',
    margin_mm10            INT NOT NULL DEFAULT 150,
    applicable_kinds       JSONB NOT NULL DEFAULT '["SAMPLE","DOWN_PAYMENT","SETTLEMENT","FULL"]',
    elements               JSONB NOT NULL DEFAULT '[]',
    is_default             BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_invoice_templates_tenant
    ON invoice_templates(tenant_id, updated_at DESC) WHERE archived_at IS NULL;

SELECT apply_tenant_rls('invoice_templates');

-- 4. Invoices (Agregat Dokumen Faktur Tagihan)
CREATE TABLE IF NOT EXISTS invoices (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_number         VARCHAR(64) NOT NULL,
    kind                   VARCHAR(30) NOT NULL,
    status                 VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    template_id            VARCHAR(64) NOT NULL REFERENCES invoice_templates(id),
    source_kind            VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    source_reference_id    VARCHAR(64),
    parent_invoice_id      VARCHAR(64) REFERENCES invoices(id) ON DELETE SET NULL,
    bill_to                JSONB NOT NULL,
    issuer_profile         JSONB NOT NULL,
    rendered_template      JSONB,
    subtotal_minor         BIGINT NOT NULL,
    currency               VARCHAR(10) NOT NULL DEFAULT 'IDR',
    contract_value_minor   BIGINT,
    discount_numerator     BIGINT NOT NULL DEFAULT 0,
    discount_denominator   BIGINT NOT NULL DEFAULT 100,
    tax_rate_numerator     BIGINT NOT NULL DEFAULT 0,
    tax_rate_denominator   BIGINT NOT NULL DEFAULT 100,
    tax_amount_minor       BIGINT NOT NULL DEFAULT 0,
    total_minor            BIGINT NOT NULL,
    paid_amount_minor      BIGINT NOT NULL DEFAULT 0,
    payment_terms_days     INT NOT NULL DEFAULT 14,
    issue_date             DATE NOT NULL,
    due_date               DATE,
    issued_at              TIMESTAMPTZ,
    paid_at                TIMESTAMPTZ,
    voided_at              TIMESTAMPTZ,
    void_reason            TEXT,
    notes                  TEXT NOT NULL DEFAULT '',
    terms_and_conditions   TEXT NOT NULL DEFAULT '',
    custom_attributes      JSONB NOT NULL DEFAULT '{}',
    created_by             VARCHAR(150) NOT NULL DEFAULT 'system',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_invoices_subtotal_non_negative CHECK (subtotal_minor >= 0),
    CONSTRAINT ck_invoices_total_non_negative CHECK (total_minor >= 0),
    CONSTRAINT ck_invoices_paid_non_negative CHECK (paid_amount_minor >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_invoices_tenant_number
    ON invoices(tenant_id, invoice_number);

CREATE INDEX IF NOT EXISTS idx_invoices_tenant_status
    ON invoices(tenant_id, status, due_date);

CREATE INDEX IF NOT EXISTS idx_invoices_tenant_updated
    ON invoices(tenant_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_invoices_source
    ON invoices(tenant_id, source_kind, source_reference_id);

SELECT apply_tenant_rls('invoices');

-- 5. Invoice Lines (Rincian Item Tagihan)
CREATE TABLE IF NOT EXISTS invoice_lines (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_id             VARCHAR(64) NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description            VARCHAR(250) NOT NULL,
    quantity_micros        BIGINT NOT NULL,
    uom                    VARCHAR(20) NOT NULL,
    unit_price_minor       BIGINT NOT NULL,
    discount_numerator     BIGINT NOT NULL DEFAULT 0,
    discount_denominator   BIGINT NOT NULL DEFAULT 100,
    amount_minor           BIGINT NOT NULL,
    sort_order             INT NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_lines_quantity_positive CHECK (quantity_micros > 0),
    CONSTRAINT ck_lines_unit_price_non_negative CHECK (unit_price_minor >= 0),
    CONSTRAINT ck_lines_amount_non_negative CHECK (amount_minor >= 0)
);

CREATE INDEX IF NOT EXISTS idx_invoice_lines_invoice
    ON invoice_lines(tenant_id, invoice_id, sort_order ASC);

SELECT apply_tenant_rls('invoice_lines');

-- 6. Invoice Payments (Riwayat Pembayaran Masuk Append-Only)
CREATE TABLE IF NOT EXISTS invoice_payments (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_id             VARCHAR(64) NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    amount_minor           BIGINT NOT NULL,
    currency               VARCHAR(10) NOT NULL DEFAULT 'IDR',
    paid_at                TIMESTAMPTZ NOT NULL,
    method                 VARCHAR(50) NOT NULL,
    reference              VARCHAR(100) NOT NULL DEFAULT '',
    note                   TEXT NOT NULL DEFAULT '',
    recorded_by            VARCHAR(150) NOT NULL DEFAULT 'system',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_payments_amount_positive CHECK (amount_minor > 0)
);

CREATE INDEX IF NOT EXISTS idx_invoice_payments_invoice
    ON invoice_payments(tenant_id, invoice_id, paid_at DESC);

SELECT apply_tenant_rls('invoice_payments');
