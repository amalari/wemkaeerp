-- ==============================================================================
-- WeMade ERP (Jalur B) — V103: Preferensi bahasa per pengguna & per tenant
-- (PLAN-i18n-phase1-ui-system Track B — B1)
-- ==============================================================================
-- Fase 1 multibahasa menyimpan bahasa antarmuka sebagai *data*, bukan kode:
--   - `users.preferred_language`   — pilihan pribadi pemakai (pemilih di login/profil),
--   - `tenants.default_language`   — bawaan pabrik yang dipakai saat pemakai belum memilih.
--
-- Keduanya aditif & idempoten. Nilai dibatasi CHECK ke kosakata yang didukung
-- (`id`, `en`) — parser `LanguageCode` di core menolak nilai lain, dan CHECK inilah
-- net terakhir di lapisan penyimpanan agar data rusak tak bisa masuk diam-diam.
--
-- Bawaan `'id'` menjaga paritas: tenant & pengguna lama tetap berperilaku persis
-- seperti sebelum kolom ini ada. Tidak ada RLS yang diubah oleh migrasi ini —
-- ia hanya menambah kolom pada tabel yang sudah ber-RLS (`users`/`tenants`).
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- users.preferred_language
-- ------------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(8) NOT NULL DEFAULT 'id';

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_preferred_language_check;
ALTER TABLE users
    ADD CONSTRAINT users_preferred_language_check
    CHECK (preferred_language IN ('id', 'en'));

-- ------------------------------------------------------------------------------
-- tenants.default_language
-- ------------------------------------------------------------------------------
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS default_language VARCHAR(8) NOT NULL DEFAULT 'id';

ALTER TABLE tenants DROP CONSTRAINT IF EXISTS tenants_default_language_check;
ALTER TABLE tenants
    ADD CONSTRAINT tenants_default_language_check
    CHECK (default_language IN ('id', 'en'));

COMMENT ON COLUMN users.preferred_language IS
    'Bahasa antarmuka pilihan pengguna (id/en). Lihat core/domain/i18n/LanguageCode.kt.';
COMMENT ON COLUMN tenants.default_language IS
    'Bahasa bawaan tenant (id/en); dipakai bila pengguna belum memilih.';
