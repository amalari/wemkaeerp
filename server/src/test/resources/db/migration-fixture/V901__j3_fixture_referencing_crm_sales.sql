-- ==============================================================================
-- FIXTURE PELANGGAR (PLAN-module-ownership-lanes Track C3) — BUKAN migrasi sungguhan.
-- Berada di src/test/resources/db/migration-fixture/, di luar lokasi Flyway
-- (classpath:db/migration), jadi tidak pernah dijalankan. Dipakai
-- J3MigrationFenceTest untuk membuktikan pagar P4 benar-benar gagal ketika
-- migrasi J3 mengunci diri ke schema modul lain (crm_sales).
-- ==============================================================================

CREATE SCHEMA IF NOT EXISTS layanan_change_request;

CREATE TABLE IF NOT EXISTS layanan_change_request.change_request_notes (
    id          VARCHAR(64) PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL REFERENCES tenants(id),
    crm_lead_id VARCHAR(64) REFERENCES crm_sales.crm_leads (id),
    catatan     TEXT NOT NULL
);

-- JOIN lintas schema ke modul garment: dilarang bagi migrasi J3 (P4).
SELECT n.id, d.nama
FROM layanan_change_request.change_request_notes n
JOIN crm_sales.deals d ON d.id = n.crm_lead_id;

-- Menjungkir FROM tak-terkualifikasi lewat search_path: juga dilarang.
SET search_path = crm_sales, public;
