-- ==============================================================================
-- WeMade ERP — Contact & Deal: kualifikasi CRM menjadi transaksi (V34)
-- ==============================================================================
-- Menyediakan master data pelanggan (contacts), agregat deal penjualan,
-- dan purchase order klien (upload atau input manual) yang melekat pada deal.
-- ==============================================================================

-- 1. Contacts (master data pelanggan per tenant)
CREATE TABLE IF NOT EXISTS crm_contacts (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                   VARCHAR(150) NOT NULL DEFAULT '',
    brand_name             VARCHAR(150) NOT NULL DEFAULT '',
    phone                  VARCHAR(20) NOT NULL DEFAULT '',
    email                  VARCHAR(100) NOT NULL DEFAULT '',
    address                TEXT NOT NULL DEFAULT '',
    tax_id                 VARCHAR(50) NOT NULL DEFAULT '',
    source_lead_id         VARCHAR(64),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Find-or-create key kualifikasi: satu nomor telepon = satu pelanggan per tenant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_contacts_tenant_phone
    ON crm_contacts(tenant_id, phone) WHERE phone <> '';

CREATE INDEX IF NOT EXISTS idx_contacts_tenant_updated
    ON crm_contacts(tenant_id, updated_at DESC);

SELECT apply_tenant_rls('crm_contacts');

-- 2. Deals (agregat penjualan, lahir dari kualifikasi lead CRM)
CREATE TABLE IF NOT EXISTS deals (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    contact_id             VARCHAR(64) NOT NULL REFERENCES crm_contacts(id) ON DELETE CASCADE,
    source_lead_id         VARCHAR(64),
    title                  VARCHAR(150) NOT NULL,
    stage                  VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    estimated_value_idr    BIGINT,
    owner_employee_id      VARCHAR(64) REFERENCES employees(id),
    expected_close_date    DATE,
    notes                  TEXT NOT NULL DEFAULT '',
    created_by_user_id     VARCHAR(64) REFERENCES users(id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at            TIMESTAMPTZ,

    CONSTRAINT ck_deals_estimated_value_non_negative
        CHECK (estimated_value_idr IS NULL OR estimated_value_idr >= 0)
);

-- Idempotency kualifikasi: satu lead menghasilkan tepat satu deal.
CREATE UNIQUE INDEX IF NOT EXISTS uq_deals_tenant_source_lead
    ON deals(tenant_id, source_lead_id) WHERE source_lead_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_deals_tenant_stage
    ON deals(tenant_id, stage, updated_at DESC) WHERE archived_at IS NULL;

SELECT apply_tenant_rls('deals');

-- 3. Deal purchase orders (PO klien: upload berkas atau input manual)
CREATE TABLE IF NOT EXISTS deal_purchase_orders (
    id                     VARCHAR(64) PRIMARY KEY,
    tenant_id              VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    deal_id                VARCHAR(64) NOT NULL REFERENCES deals(id) ON DELETE CASCADE,
    po_number              VARCHAR(64) NOT NULL,
    po_date                DATE NOT NULL,
    origin                 VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    file_name              VARCHAR(255),
    mime_type              VARCHAR(100),
    file_size_bytes        BIGINT,
    storage_key            TEXT,
    manual_lines           JSONB NOT NULL DEFAULT '[]',
    notes                  TEXT NOT NULL DEFAULT '',
    recorded_by            VARCHAR(150) NOT NULL DEFAULT 'system',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_po_origin_valid CHECK (origin IN ('UPLOADED', 'MANUAL')),
    CONSTRAINT ck_po_uploaded_has_storage
        CHECK (origin <> 'UPLOADED' OR storage_key IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_deal_pos_tenant_deal
    ON deal_purchase_orders(tenant_id, deal_id, created_at DESC);

SELECT apply_tenant_rls('deal_purchase_orders');
