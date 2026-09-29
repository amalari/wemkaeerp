-- ==============================================================================
-- WeMade ERP (Jalur B) — V75: Domain Pack per Tenant (TRD-PLAT-001 B7)
-- ==============================================================================
-- tenants.domain_pack: vertikal tenant. DEFAULT 'garment' adalah nilai **benar** untuk setiap
-- baris yang ada (semua tenant sebelum B7 berjalan di pack garment), bukan fallback baca.
--
-- domain_packs: pack **data** (JSON DomainPackCodec) — kelak keluaran generator AI. Pack
-- bawaan (garment) tidak disimpan di sini. Tabel platform, tanpa RLS: dibaca saat server
-- mulai (tanpa konteks tenant) dan ditulis hanya lewat endpoint superadmin.
--
-- Versi: pack LOCKED tidak diubah di tempat; revisi = baris versi baru (Kontrak 5).
-- Tenant memakai versi LOCKED tertinggi, atau DRAFT tertinggi bila belum ada yang dikunci.
-- ==============================================================================

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS domain_pack VARCHAR(64) NOT NULL DEFAULT 'garment';

CREATE TABLE IF NOT EXISTS domain_packs (
    code            VARCHAR(64)  NOT NULL,
    version         INTEGER      NOT NULL CHECK (version > 0),
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('DRAFT', 'LOCKED')),
    owner_tenant_id VARCHAR(64)  NULL REFERENCES tenants(id),
    definition      JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (code, version)
);
