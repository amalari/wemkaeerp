-- ==============================================================================
-- WeMade ERP (Jalur B) — V89: koreksi jam trial — mulai saat GO-LIVE, bukan registrasi
-- ==============================================================================
-- V88 memberi DEFAULT now()+14 hari, sehingga jam mulai berjalan saat tenant mendaftar —
-- padahal Builder gratis dan tenant boleh membangun berhari-hari. Koreksi:
--   1. Default dihapus; jam dipasang eksplisit oleh DeployTenantUseCase saat deploy pertama
--      sukses (app jadi), melalui PostgresTenantRepository.
--   2. Tenant yang keburur terisi default oleh V88 di-reset NULL bila belum pernah deploy —
--      deteksi: status masih TRIAL dan tidak punya deployment (query di bawah).
-- ==============================================================================

ALTER TABLE tenants
    ALTER COLUMN trial_ends_at DROP DEFAULT;

UPDATE tenants t
SET trial_ends_at = NULL
WHERE t.status = 'TRIAL'
  AND t.trial_ends_at IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM builder.deployments d
      WHERE d.tenant_id = t.id AND d.status = 'ACTIVE'
  );
