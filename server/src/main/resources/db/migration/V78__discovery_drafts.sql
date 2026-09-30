-- ==============================================================================
-- WeMade ERP (Jalur B) — V78: Discovery Drafts (plan PLAN-discovery-blueprint-prototype-studio §2 A5)
-- ==============================================================================
-- ops.discovery_drafts: keluaran tahap discovery — narasi prospek ber-login → draf yang membungkus
-- DomainPack (B7) + Blueprint (B4) + deskriptor layar (Fase C) sebagai satu dokumen JSON
-- (DiscoveryDraftCodec). Status DRAFT/LOCKED; LOCKED membeku dokumen (Kontrak 5).
--
-- owner_user_id (T12): funnel ber-login, dan gerbang routenya adalah *pemilik draf atau superadmin*.
-- prospect_lead_id opsional: draf bisa lahir tanpa baris funnel terlebih dahulu.
--
-- Tabel di schema `ops` (B8/batas V16): platform-global, TANPA RLS tenant, dan TANPA grant
-- wemade_app — schema `ops` sudah dibuat tak terlihat untuk role tenant-scoped sejak V16, dan
-- REVOKE eksplisit di bawah adalah pengaman kalau default privilege berubah di masa depan.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS ops.discovery_drafts (
    id               VARCHAR(64)  PRIMARY KEY,
    owner_user_id    VARCHAR(64)  NOT NULL REFERENCES public.users(id),
    prospect_lead_id VARCHAR(64)  NULL REFERENCES ops.prospect_leads(id),
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('DRAFT', 'LOCKED')),
    document         JSONB        NOT NULL,
    schema_version   INTEGER      NOT NULL DEFAULT 1 CHECK (schema_version > 0),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    locked_at        TIMESTAMPTZ  NULL
);

CREATE INDEX IF NOT EXISTS idx_discovery_drafts_owner ON ops.discovery_drafts (owner_user_id);

-- Owner-only: lihat komentar di atas. Tidak ada GRANT ke wemade_app untuk tabel ini.
REVOKE ALL ON ops.discovery_drafts FROM wemade_app;
