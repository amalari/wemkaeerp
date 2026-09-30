-- ==============================================================================
-- WeMade ERP (Jalur B) — V83: Antrian Pembuatan (PLAN-builder-console M2, FR-M2-4)
-- ==============================================================================
-- builder.build_requests: permintaan pembuatan kode modul. Lahir QUEUED dari
-- DeployTenantUseCase bila draf memakai pack kustom (belum diimplementasi platform);
-- dikelola superadmin (MVP tanpa self-service). Milik tenant (RLS pola V76).
-- ==============================================================================

CREATE TABLE IF NOT EXISTS builder.build_requests (
    id             VARCHAR(140) PRIMARY KEY,
    tenant_id      VARCHAR(64) NOT NULL REFERENCES public.tenants(id),
    module_id      VARCHAR(80) NOT NULL,
    reason         TEXT        NOT NULL,
    status         VARCHAR(20) NOT NULL CHECK (status IN
                    ('QUEUED','QUOTED','APPROVED','IN_PROGRESS','SHIPPED','REJECTED')),
    quote_id       VARCHAR(64) NULL,
    deployment_id  VARCHAR(80) NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_builder_build_requests_tenant
    ON builder.build_requests (tenant_id, created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON builder.build_requests TO wemade_app;
SELECT apply_tenant_rls_in('builder', 'build_requests');
