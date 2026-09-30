-- ==============================================================================
-- WeMade ERP (Jalur B) — V81: WeMake Builder fondasi (PLAN-builder-console M0)
-- ==============================================================================
-- Tiga perubahan aditif:
-- 1. builder.deployments — riwayat deployment milik tenant ("kunci versi + aktifkan").
--    M0 hanya mengisi status IMPORTED: snapshot keadaan tenant yang sudah berjalan
--    sebelum Builder ada. Pack shipped (garment) bukan baris domain_packs, jadi
--    pack_version NULL dan yang dicatat app_build. CHECK di bawah menegakkan
--    invarian domain: IMPORTED wajib app_build; status lain wajib pack_version.
-- 2. tenants.domain_pack_version — pin versi pack per tenant (rollback M2). NULL =
--    perilaku B7 lama (pack effective); tidak ada backfill — test paritas garment
--    menuntut tenant lama tidak berubah perilaku.
-- 3. ops.discovery_drafts.tenant_id — draf milik tenant (Builder), nullable; draf
--    funnel lama tetap milik pribadi pemanggilnya (V78), tidak di-backfill.
--
-- RLS + grant mengikuti pola V76/V77: builder.deployments ber-tenant_id, jadi
-- wajib apply_tenant_rls_in dan grant wemade_app. Daftar: ModuleSchemaMap.
-- ==============================================================================

CREATE SCHEMA IF NOT EXISTS builder;

CREATE TABLE IF NOT EXISTS builder.deployments (
    id                  VARCHAR(80)  PRIMARY KEY,
    tenant_id           VARCHAR(64)  NOT NULL REFERENCES public.tenants(id),
    number              INTEGER      NOT NULL CHECK (number > 0),
    pack_code           VARCHAR(64)  NOT NULL,
    pack_version        INTEGER      NULL CHECK (pack_version IS NULL OR pack_version > 0),
    app_build           VARCHAR(64)  NULL,
    blueprint_revision  INTEGER      NOT NULL DEFAULT 1 CHECK (blueprint_revision > 0),
    status              VARCHAR(32)  NOT NULL CHECK (status IN
                         ('VALIDATING','BLOCKED_ON_BUILD','ACTIVE','SUPERSEDED','FAILED','ROLLED_BACK','IMPORTED')),
    draft_id            VARCHAR(64)  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    activated_at        TIMESTAMPTZ  NULL,
    -- Invarian domain (BuilderDeployment.init), ditegakkan dua lapis:
    CHECK (status <> 'IMPORTED' OR app_build IS NOT NULL),
    CHECK (status IN ('IMPORTED','FAILED','VALIDATING') OR pack_version IS NOT NULL),
    UNIQUE (tenant_id, number)
);

CREATE INDEX IF NOT EXISTS idx_builder_deployments_tenant ON builder.deployments (tenant_id, number DESC);

-- Snapshot #1 untuk setiap tenant yang sudah ada: keadaan hari ini = deployment IMPORTED.
-- Idempoten: UNIQUE (tenant_id, number) + ON CONFLICT DO NOTHING.
INSERT INTO builder.deployments (id, tenant_id, number, pack_code, app_build, blueprint_revision, status, created_at)
SELECT
    'dep-' || t.id || '-1',
    t.id,
    1,
    t.domain_pack,
    COALESCE(current_setting('app.app_build', true), 'pre-builder'),
    1,
    'IMPORTED',
    NOW()
FROM public.tenants t
ON CONFLICT (tenant_id, number) DO NOTHING;

-- Pin versi pack per tenant (M2 yang mengisi; M0 hanya menyediakan kolomnya).
ALTER TABLE public.tenants ADD COLUMN IF NOT EXISTS domain_pack_version INTEGER;
ALTER TABLE public.tenants ADD CONSTRAINT chk_tenants_pack_version CHECK (domain_pack_version IS NULL OR domain_pack_version > 0);

-- Draf Builder milik tenant; draf funnel lama tetap NULL.
ALTER TABLE ops.discovery_drafts ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);

-- Hak akses & isolasi (pola V76).
GRANT USAGE ON SCHEMA builder TO wemade_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON builder.deployments TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA builder GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wemade_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA builder GRANT USAGE, SELECT ON SEQUENCES TO wemade_app;
SELECT apply_tenant_rls_in('builder', 'deployments');
