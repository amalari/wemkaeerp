-- ==============================================================================
-- WeMade ERP — Pemetaan simpul alur ke gedung, dan saklar multi-site per tenant
-- ==============================================================================
-- V55 sudah membuat `tenant_locations` beserta seed Gedung A / Gedung B untuk demo-tenant,
-- tapi sampai sekarang tidak ada satu query pun yang membacanya: tidak ada yang menyatakan
-- *proses mana dikerjakan di gedung mana*. Tanpa itu, sistem tidak bisa tahu bahwa perpindahan
-- Rajut -> Linking sebenarnya naik mobil pickup, dan pengiriman tidak pernah tercatat.
--
-- Satu tabel pemetaan untuk semua lapisan alur, bukan satu tabel per lapisan. Tahap sampling,
-- stasiun kerja, dan proses opsional sama-sama "tempat pekerjaan terjadi"; memisahkannya
-- menjadi beberapa tabel berarti beberapa sumber kebenaran yang wajib konsisten tanpa ada yang
-- menegakkan konsistensinya.
--
-- Simpul yang dikerjakan vendor makloon sengaja TIDAK didaftarkan di sini — tujuannya adalah
-- vendornya, yang sudah tercatat di `tenant_optional_processes.vendor_ref`. Tenant hanya
-- mengisi baris untuk simpul yang dikerjakan sendiri.
--
-- Aditif dan idempoten.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Saklar per tenant
-- ------------------------------------------------------------------------------
-- Dipisah dari `tenants` supaya penambahan saklar berikutnya tidak menyentuh tabel inti yang
-- dipakai hampir setiap query.
CREATE TABLE IF NOT EXISTS tenant_location_settings (
    tenant_id                     VARCHAR(64) PRIMARY KEY,
    -- FALSE berarti perpindahan antar gedung tidak dianggap ada, meski pemetaan di bawah
    -- terisi. Pemetaan sisa percobaan konfigurasi tidak boleh diam-diam menyalakan gerbang.
    is_multi_site_enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    -- Penyerahan ke pembeli tetap barang yang keluar pabrik, jadi berlaku juga untuk tenant
    -- satu atap. Tenant yang tidak menginginkannya mematikan saklar ini.
    require_customer_dispatch_sj  BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------------------------
-- 2. Pemetaan simpul alur -> lokasi fisik
-- ------------------------------------------------------------------------------
-- node_kind: 'STAGE' (tahap sampling) | 'PROC' (proses opsional tenant) | 'STATION' (stasiun
-- bawaan lini produksi). node_key: nama enum, kode proses, atau kode stasiun.
CREATE TABLE IF NOT EXISTS tenant_flow_node_locations (
    tenant_id     VARCHAR(64) NOT NULL,
    node_kind     VARCHAR(16) NOT NULL,
    node_key      VARCHAR(64) NOT NULL,
    location_id   VARCHAR(64) NOT NULL REFERENCES tenant_locations(id) ON DELETE CASCADE,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, node_kind, node_key)
);

CREATE INDEX IF NOT EXISTS idx_tenant_flow_node_locations_tenant
    ON tenant_flow_node_locations (tenant_id);

-- ------------------------------------------------------------------------------
-- 3. Seed demo — dipetakan, tapi saklarnya MATI
-- ------------------------------------------------------------------------------
-- Sengaja `is_multi_site_enabled = FALSE`: memasang migrasi ini tidak boleh mengubah tampilan
-- maupun memblokir SPK demo yang sedang berjalan di tengah alur. Multi-site dinyalakan lewat
-- API setelah layar penerbitan Surat Jalan benar-benar bisa dipakai.
INSERT INTO tenant_location_settings (tenant_id, is_multi_site_enabled, require_customer_dispatch_sj)
VALUES ('demo-tenant', FALSE, FALSE)
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO tenant_flow_node_locations (tenant_id, node_kind, node_key, location_id)
VALUES
    ('demo-tenant', 'STAGE', 'CAM_PROGRAMMING',  'loc-rajut-01'),
    ('demo-tenant', 'STAGE', 'MACHINE_KNITTING', 'loc-rajut-01'),
    ('demo-tenant', 'STAGE', 'LINKING_ASSEMBLY', 'loc-finishing-01'),
    ('demo-tenant', 'STAGE', 'FINISHING_QC',     'loc-finishing-01')
ON CONFLICT (tenant_id, node_kind, node_key) DO NOTHING;
