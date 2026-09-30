-- ==============================================================================
-- WeMade ERP (Jalur B) — V79: Prototype Patterns (plan PLAN-discovery-blueprint-prototype-studio §4)
-- ==============================================================================
-- ops.prototype_patterns: pola layout Studio internal — resep menyusun widget prototype
-- (kosakata tertutup WidgetKind: FORM/TABLE/KANBAN/...) yang dipanen dari layar produksi dan
-- dipakai ulang antar draf. pattern_json = objek JSON bebas milik Studio (posisi/konfigurasi);
-- yang divalidasi domain hanyalah widget & pack yang dirujuk.
--
-- Tabel di schema `ops` (B8/batas V16): platform-global, TANPA RLS tenant, TANPA grant
-- wemade_app — sama seperti ops.discovery_drafts (V78). REVOKE eksplisit di bawah adalah
-- pengaman kalau default privilege berubah.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS ops.prototype_patterns (
    id                 VARCHAR(64)  PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    widget             VARCHAR(30)  NOT NULL,
    pack_code          VARCHAR(64)  NULL,
    pattern_json       JSONB        NOT NULL,
    created_by_user_id VARCHAR(64)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_prototype_pattern_name UNIQUE (name)
);

-- Owner-only: lihat komentar di atas. Tidak ada GRANT ke wemade_app untuk tabel ini.
REVOKE ALL ON ops.prototype_patterns FROM wemade_app;
--
-- created_by_user_id TANPA FK ke users: pembuat pola adalah identitas token (superadmin platform
-- tidak selalu punya baris users), dan pola adalah metadata Studio yang hidup lebih lama dari
-- sesi pembuatnya. Integritasnya adalah tanggung jawab route (login wajib), bukan database.
REVOKE ALL ON ops.prototype_patterns FROM wemade_app;
