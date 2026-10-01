-- ==============================================================================
-- WeMade ERP (Jalur B) — V88: jam trial tenant (trial_ends_at)
-- ==============================================================================
-- `TenantStatus.TRIAL` sudah ada sejak V1, tapi tanpa tenggat: tenant baru dapat PRO penuh
-- selamanya. Kolom ini memberi jam. Default di DB (bukan di kode onboarding) supaya tenant
-- baru otomatis punya tenggat 14 hari bahkan lewat jalur insert lama.
-- NULL = tenant legacy tanpa jam (status quo; tidak dipaksa).
-- ==============================================================================

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS trial_ends_at TIMESTAMPTZ NULL;

ALTER TABLE tenants
    ALTER COLUMN trial_ends_at SET DEFAULT now() + interval '14 days';

CREATE INDEX IF NOT EXISTS idx_tenants_trial_ends
    ON tenants (trial_ends_at)
    WHERE status = 'TRIAL' AND trial_ends_at IS NOT NULL;
