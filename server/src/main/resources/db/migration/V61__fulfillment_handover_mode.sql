-- ==============================================================================
-- WeMade ERP — Dua pola serah terima antar divisi (V61)
-- ==============================================================================
-- Sampai V60, modul transfer internal memaksa satu pola: bundel wajib dituang ke
-- karung, karung ditutup, lalu di-ACC admin sebelum boleh jalan. Pola itu benar
-- untuk konveksi menengah ke bawah — operator membawa bundel selesai ke meja
-- admin, admin menyortir dan mengirimkannya ke divisi lain.
--
-- Tapi ada pabrik yang operatornya mengantar sendiri dari meja A ke meja B. Di
-- sana tidak ada kustodi perantara yang perlu dibatasi, jadi menuntut timbang +
-- foto + ACC tiap serah terima hanya menambah friksi tanpa menambah informasi.
--
-- Mode dipilih PER RUTE, bukan per tenant: satu pabrik bisa memakai dua pola
-- sekaligus (Rajut->Finishing lewat meja admin karena volumenya besar dan perlu
-- disortir per size; Finishing->QC langsung karena mejanya bersebelahan).
--
-- Aditif dan idempoten. Rute yang tidak terdaftar jatuh ke ADMIN_HUB — bukan
-- karena itu "lebih aman" secara umum, tapi karena itulah satu-satunya pola yang
-- ada sebelum migrasi ini. Tenant yang tidak menyentuh apa pun harus melihat
-- perilaku yang persis sama seperti kemarin.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Mode yang berlaku saat sebuah perjalanan dibuat (SNAPSHOT)
-- ------------------------------------------------------------------------------
-- Disimpan di barisnya sendiri, bukan dibaca ulang dari tabel konfigurasi, dengan
-- alasan yang sama seperti work_deposits.tariff_snapshot_idr: pabrik boleh
-- mengubah mode sebuah rute kapan saja, dan perjalanan yang telanjur berjalan
-- harus tetap sah dengan aturan yang berlaku saat ia berangkat.
--
-- DEFAULT 'ADMIN_HUB' inilah yang membuat seluruh baris lama tetap valid.
ALTER TABLE fulfillment_transfers
    ADD COLUMN IF NOT EXISTS handover_mode VARCHAR(16) NOT NULL DEFAULT 'ADMIN_HUB';

ALTER TABLE fulfillment_transfers
    DROP CONSTRAINT IF EXISTS ck_fulfillment_handover_mode;
ALTER TABLE fulfillment_transfers
    ADD CONSTRAINT ck_fulfillment_handover_mode
        CHECK (handover_mode IN ('DIRECT', 'ADMIN_HUB'));

-- ------------------------------------------------------------------------------
-- 2. Bukti timbang jadi opsional — tapi hanya untuk DIRECT
-- ------------------------------------------------------------------------------
-- Melonggarkan NOT NULL saja akan membuat baris ADMIN_HUB tanpa timbangan bisa
-- masuk diam-diam, dan itu persis disiplin bukti yang tidak boleh melemah. Maka
-- NOT NULL diganti CHECK bersyarat, bukan dihapus.
ALTER TABLE fulfillment_transfers ALTER COLUMN dispatch_weight_kg DROP NOT NULL;
ALTER TABLE fulfillment_transfers ALTER COLUMN dispatch_scale_photo_key DROP NOT NULL;

ALTER TABLE fulfillment_transfers
    DROP CONSTRAINT IF EXISTS ck_fulfillment_dispatch_evidence;
ALTER TABLE fulfillment_transfers
    ADD CONSTRAINT ck_fulfillment_dispatch_evidence
        CHECK (
            handover_mode <> 'ADMIN_HUB'
            OR (dispatch_weight_kg IS NOT NULL AND dispatch_scale_photo_key IS NOT NULL)
        );

-- TTD penerima ikut jadi opsional: pada DIRECT, foto dan nama penerima sudah
-- cukup menunjuk siapa yang memegang. Pada ADMIN_HUB ia tetap wajib, dan itu
-- ditegakkan di domain (InternalTransfer.receive) karena syaratnya bergantung
-- pada status, bukan hanya pada mode.
ALTER TABLE fulfillment_transfers ALTER COLUMN receiver_signature_key DROP NOT NULL;

-- ------------------------------------------------------------------------------
-- 3. Konfigurasi mode per rute, per tenant
-- ------------------------------------------------------------------------------
-- Barisnya hanya ada untuk rute yang benar-benar disetel. Ketiadaan baris adalah
-- pernyataan yang sah ("belum disentuh"), bukan data yang hilang — itulah yang
-- membedakannya dari menyemai seluruh rute dengan nilai default.
CREATE TABLE IF NOT EXISTS fulfillment_route_settings (
    tenant_id      VARCHAR(64) NOT NULL,
    route          VARCHAR(40) NOT NULL,
    handover_mode  VARCHAR(16) NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, route),
    CONSTRAINT ck_fulfillment_route_mode CHECK (handover_mode IN ('DIRECT', 'ADMIN_HUB'))
);

CREATE INDEX IF NOT EXISTS idx_fulfillment_route_settings_tenant
    ON fulfillment_route_settings (tenant_id);

SELECT apply_tenant_rls('fulfillment_route_settings');

-- ------------------------------------------------------------------------------
-- 4. Tanpa seed
-- ------------------------------------------------------------------------------
-- Sengaja tidak ada INSERT. Menyemai satu rute demo ke DIRECT berarti mengubah
-- disiplin bukti sebuah tenant yang sedang berjalan hanya karena migrasi dipasang
-- — persis perilaku diam-diam yang dihindari seluruh migrasi ini. Mode dinyalakan
-- lewat PUT /api/tenant/fulfillment/route-settings, sebagai keputusan sadar.
