-- ==============================================================================
-- KANDIDAT PR — HASIL GENERATOR (HandoffScaffoldGenerator.generateFromSpec) — WAJIB REVIEW MANUSIA
-- ==============================================================================
-- Modul `layanan_change_request`: Permintaan Perubahan
-- Sumber pola: V76 (schema per modul + grant), V64 (registrasi katalog), V77 (RLS).
-- Setelah diterapkan file ini milik tim; perubahan berikutnya = migrasi baru, bukan menjalankan ulang generator.
-- ==============================================================================

CREATE SCHEMA IF NOT EXISTS layanan_change_request;

CREATE TABLE IF NOT EXISTS layanan_change_request.change_requests (
    id          VARCHAR(64) PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL REFERENCES tenants(id),
    judul       TEXT NOT NULL CHECK (btrim(judul) <> ''),
    peminta     TEXT,
    prioritas   VARCHAR(120) CHECK (prioritas IN ('Rendah', 'Sedang', 'Tinggi')),
    status      VARCHAR(120) NOT NULL CHECK (status IN ('Baru', 'Ditinjau', 'Disetujui', 'Selesai')),
    perkiraan_jam NUMERIC(18,4),
    target_selesai DATE,
    mendesak    BOOLEAN NOT NULL DEFAULT FALSE,
    catatan     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_change_requests_tenant ON layanan_change_request.change_requests(tenant_id, created_at);

SELECT apply_tenant_rls_in('layanan_change_request', 'change_requests');

GRANT USAGE ON SCHEMA layanan_change_request TO wemade_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA layanan_change_request TO wemade_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA layanan_change_request TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA layanan_change_request GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA layanan_change_request GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;

-- Registrasi katalog (pola V64): PLANNED & tanpa harga — modul belum dijual.
INSERT INTO module_catalog_entries (
    id, module_id, archetype_code, display_name, description, category_code,
    scope_capability, lifecycle_status
) VALUES (
    'mce-layanan_change_request', 'layanan_change_request', 'layanan_change_request',
    'Permintaan Perubahan', 'Melacak permintaan customisasi dari klien: dari masuk, ditinjau, disetujui, sampai selesai.',
    'UTAMA', 'GLOBAL_ONLY', 'PLANNED'
)
ON CONFLICT (module_id) DO NOTHING;

-- TODO(review): backfill wewenang per jabatan (pola V64) adalah keputusan bisnis, bukan keputusan generator.
-- Kunci modul di `custom_roles.module_permissions` = NAME (LAYANAN_CHANGE_REQUEST); katalog & node pipeline = code (layanan_change_request).
-- Owner tenant (TENANT_ADMIN tanpa jabatan) otomatis MANAGE; jabatan lain TIDAK punya akses sampai diberi.
