-- ==============================================================================
-- WeMade ERP (Jalur B) — V84: Draf Lead AI (TRD-HELP-002)
-- ==============================================================================
-- 1. crm_leads.created_via (K2): jalur pembuatan lead. AI_DRAFT = field diisi draf AI lalu
--    DISIMPAN OLEH MANUSIA (created_by_user_id). Aditif, default MANUAL untuk baris lama.
-- 2. crm_sales.crm_ai_settings (K1): opt-in per tenant. Tidak ada baris = fitur mati, karena
--    fitur ini mengirim nama pelanggan ke penyedia LLM. Milik tenant (RLS pola V76).
-- ==============================================================================

ALTER TABLE crm_sales.crm_leads
    ADD COLUMN IF NOT EXISTS created_via VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'crm_leads_created_via_check') THEN
        ALTER TABLE crm_sales.crm_leads
            ADD CONSTRAINT crm_leads_created_via_check CHECK (created_via IN ('MANUAL', 'AI_DRAFT'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS crm_sales.crm_ai_settings (
    tenant_id           VARCHAR(64) PRIMARY KEY REFERENCES public.tenants(id),
    lead_draft_enabled  BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_by_user_id  VARCHAR(64) NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

GRANT SELECT, INSERT, UPDATE, DELETE ON crm_sales.crm_ai_settings TO wemade_app;
SELECT apply_tenant_rls_in('crm_sales', 'crm_ai_settings');
