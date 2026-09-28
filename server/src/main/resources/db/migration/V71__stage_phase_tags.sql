-- ==============================================================================
-- WeMade ERP — V71: Tag Fase Sampling / Produksi pada Cuci & Setrika
-- ==============================================================================
-- Di Penentuan Alur, tahap Cuci Softener dan Setrika Uap punya tag [Sampling ×] [Produksi ×].
-- Men-× tag Sampling membuat kartu sampling desain itu melompati meja tersebut; tag Produksi
-- disimpan untuk diwarisi bulk order.
--
-- Bentuk JSON: {"WASHING": ["PRODUCTION"]} — hanya tahap yang menyimpang dari default (kedua
-- fase) yang ditulis. Lihat StagePhaseTagsCodec.
-- ==============================================================================

-- Template pabrik: default yang diwarisi desain baru. Tidak ada baris = kedua fase.
CREATE TABLE IF NOT EXISTS tenant_stage_phase_tags (
    tenant_id  VARCHAR(64) PRIMARY KEY REFERENCES tenants(id),
    tags       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE tenant_stage_phase_tags ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'tenant_stage_phase_tags' AND policyname = 'tenant_stage_phase_tags_tenant_isolation') THEN
        CREATE POLICY tenant_stage_phase_tags_tenant_isolation ON tenant_stage_phase_tags
            USING (tenant_id = CURRENT_SETTING('app.current_tenant_id', true));
    END IF;
END $$;

-- Per desain. NULL = mewarisi template pabrik; dibekukan saat SPK masuk Program CAM agar
-- perubahan template tidak me-rute ulang kartu yang sudah berjalan di lantai.
ALTER TABLE sampling_orders ADD COLUMN IF NOT EXISTS stage_phase_tags JSONB;
