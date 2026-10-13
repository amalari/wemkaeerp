-- =============================================================================
-- V104: Jejak audit packing & shipment (TRD-FUL-001)
-- carton_events / shipment_events ditulis oleh PostgresFulfillmentPacking*Repository.recordEvent
-- (segel, buka-ulang, kirim, batal) tetapi tidak ada di V102. Tabel di schema modul induk (fulfillment).
-- carton_id/shipment_id sengaja TANPA FK: jejak audit harus bertahan walau dokumen dihapus.
-- =============================================================================

CREATE TABLE IF NOT EXISTS fulfillment.carton_events (
    id           VARCHAR(64) PRIMARY KEY,
    tenant_id    VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    carton_id    VARCHAR(64) NOT NULL,
    event_type   VARCHAR(40) NOT NULL,
    actor        VARCHAR(120) NOT NULL,
    detail       TEXT        NOT NULL DEFAULT '',
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fulfillment_carton_events_carton
    ON fulfillment.carton_events (tenant_id, carton_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS fulfillment.shipment_events (
    id           VARCHAR(64) PRIMARY KEY,
    tenant_id    VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    shipment_id  VARCHAR(64) NOT NULL,
    event_type   VARCHAR(40) NOT NULL,
    actor        VARCHAR(120) NOT NULL,
    detail       TEXT        NOT NULL DEFAULT '',
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fulfillment_shipment_events_shipment
    ON fulfillment.shipment_events (tenant_id, shipment_id, occurred_at DESC);

SELECT apply_tenant_rls_in('fulfillment', 'carton_events');
SELECT apply_tenant_rls_in('fulfillment', 'shipment_events');

GRANT SELECT, INSERT, UPDATE, DELETE ON fulfillment.carton_events TO wemade_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON fulfillment.shipment_events TO wemade_app;
