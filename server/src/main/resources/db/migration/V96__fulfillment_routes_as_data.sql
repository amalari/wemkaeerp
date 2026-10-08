-- ==============================================================================
-- WeMade ERP (Jalur B) — V96: Daftar rute serah terima sebagai data per tenant
-- (TRD-FLOW-003, B1)
-- ==============================================================================
-- Sampai V95, daftar rute serah terima karung adalah enum `SackRoute` dengan dua
-- nilai khas rajut. Tenant bordir atau sablon tidak bisa punya rute sendiri tanpa
-- mengubah kode. Tabel ini menyimpan daftar rute milik tenant.
--
-- Kode rajut lama identik dengan nama enum (TRD D3), jadi nilai yang sudah
-- tersimpan di `fulfillment_transfers.leg` dan `fulfillment_route_settings.route`
-- tetap sah TANPA backfill apa pun.
--
-- Aditif dan idempoten. TANPA SEED (TRD D4): tenant tanpa baris memakai template
-- pack secara efektif — menyemai baris berarti mengubah tenant yang sedang jalan.
--
-- Tidak ada constraint yang dilepas: `fulfillment_transfers.leg` tidak pernah
-- punya CHECK (verifikasi PR-0, 2026-10-08) dan CHECK `route` hanya ada pada
-- kolom `handover_mode` di V61. Rollback = DROP TABLE fulfillment_routes.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS fulfillment.fulfillment_routes (
    tenant_id    VARCHAR(64)  NOT NULL,
    code         VARCHAR(40)  NOT NULL,
    label        VARCHAR(120) NOT NULL,
    from_node    VARCHAR(120),                            -- FlowNodeRef.key, opsional
    to_node      VARCHAR(120),                            -- FlowNodeRef.key, opsional
    sort_order   INT          NOT NULL DEFAULT 0,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, code),
    CONSTRAINT ck_fulfillment_route_code CHECK (code ~ '^[A-Z][A-Z0-9_]{0,39}$')
);

CREATE INDEX IF NOT EXISTS idx_fulfillment_routes_tenant
    ON fulfillment.fulfillment_routes (tenant_id);

-- RLS pola V76/V77: baris hanya terbaca/tertulis untuk tenant yang sedang bekerja
-- (app.current_tenant_id di-set DatabaseFactory.dbQuery per transaksi).
SELECT apply_tenant_rls_in('fulfillment', 'fulfillment_routes');

-- Grant eksplisit: ALTER DEFAULT PRIVILEGES V76 untuk schema fulfillment terikat
-- pada role pembuat tabelnya, jadi grant langsung membuat tabel ini tidak
-- bergantung pada siapa yang menjalankan migrasi (dijaga ModuleSchemaOwnershipTest).
GRANT SELECT, INSERT, UPDATE, DELETE ON fulfillment.fulfillment_routes TO wemade_app;
