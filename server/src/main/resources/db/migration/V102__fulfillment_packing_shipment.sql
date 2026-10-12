-- ==============================================================================
-- WeMade ERP (Jalur B) — V102: Modul packing karton & pengiriman eksternal
-- (TRD-FUL-001 Track B)
-- ==============================================================================
-- Agregat PackingCarton (OPEN → SEALED → REOPENED → SEALED) dan
-- DispatchShipment (ISSUED → IN_TRANSIT → DELIVERED) untuk aliran keluar pabrik.
--
-- Schema `fulfillment` sudah dibuat di V76. Tabel baru:
-- - cartons: karton packing
-- - shipments: pengiriman eksternal (Surat Jalan)
-- - shipment_cartons: pivot many-to-many
--
-- Idempoten. Tidak ada seed — data awal dari operasi packing.
-- ==============================================================================

-- -----------------------------------------------------------------------------
-- Tabel cartons: karton packing
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fulfillment.cartons (
    id                VARCHAR(64) PRIMARY KEY,
    tenant_id         VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    production_ref    VARCHAR(64) NOT NULL,
    carton_number    VARCHAR(40) NOT NULL,
    item_breakdown   JSONB      NOT NULL DEFAULT '{}',
    quantity_pcs     INT        NOT NULL DEFAULT 0,
    gross_weight_kg  DECIMAL(10,2) NOT NULL DEFAULT 0,
    unit_ticket_ids  JSONB      NOT NULL DEFAULT '[]',
    seal_number      VARCHAR(40),
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes            TEXT        NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_fulfillment_cartons_tenant_status
    ON fulfillment.cartons (tenant_id, status, updated_at DESC);

-- -----------------------------------------------------------------------------
-- Tabel shipments: pengiriman eksternal (Surat Jalan)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fulfillment.shipments (
    id                VARCHAR(64) PRIMARY KEY,
    tenant_id         VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    shipment_number   VARCHAR(40) NOT NULL,
    stock_ownership   VARCHAR(30) NOT NULL,
    carrier           JSONB      NOT NULL DEFAULT '{}',
    destination       JSONB      NOT NULL DEFAULT '{}',
    status            VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    shipped_at        TIMESTAMPTZ,
    delivered_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes             TEXT        NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_fulfillment_shipments_tenant_status
    ON fulfillment.shipments (tenant_id, status, updated_at DESC);

-- -----------------------------------------------------------------------------
-- Tabel pivot shipment_cartons: many-to-many antara shipments dan cartons
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fulfillment.shipment_cartons (
    tenant_id     VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    shipment_id   VARCHAR(64) NOT NULL REFERENCES fulfillment.shipments(id) ON DELETE CASCADE,
    carton_id     VARCHAR(64) NOT NULL REFERENCES fulfillment.cartons(id) ON DELETE CASCADE,
    PRIMARY KEY (shipment_id, carton_id)
);

CREATE INDEX IF NOT EXISTS idx_fulfillment_shipment_cartons_carton
    ON fulfillment.shipment_cartons (carton_id);

-- -----------------------------------------------------------------------------
-- RLS: Row-Level Security per tenant (pola V76/V77)
-- -----------------------------------------------------------------------------
SELECT apply_tenant_rls_in('fulfillment', 'cartons');
SELECT apply_tenant_rls_in('fulfillment', 'shipments');
SELECT apply_tenant_rls_in('fulfillment', 'shipment_cartons');

-- Grant eksplisit (pola V76)
GRANT SELECT, INSERT, UPDATE, DELETE ON fulfillment.cartons TO wemade_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON fulfillment.shipments TO wemade_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON fulfillment.shipment_cartons TO wemade_app;
