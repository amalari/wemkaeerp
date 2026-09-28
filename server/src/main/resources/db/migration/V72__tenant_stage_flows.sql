-- ==============================================================================
-- WeMade ERP — V72: Kerangka Tahap Alur per Tenant (TRD-FLOW-001 Tahap 1)
-- ==============================================================================
-- Kerangka tahap sampling berpindah dari enum Kotlin menjadi data per tenant, disalin dari
-- template industri saat pertama dibaca. Tahap 1 hanya menambah tabel: belum ada pembaca yang
-- memakainya, dan tidak ada baris lama yang diubah.
--
-- Kode tahap template KNIT_SWEATER identik dengan nama enum SamplingPipelineStage, sehingga
-- sampling_orders.current_stage dan key FlowNodeRef `STAGE:<code>` tetap valid tanpa migrasi.
--
-- Bentuk JSON `stages`: [{"code","displayName","kind","archetype","traits","origin","executionMode"}]
-- Lihat TenantStageFlowCodec.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS tenant_stage_flows (
    tenant_id     VARCHAR(64) PRIMARY KEY REFERENCES tenants(id),
    template_code VARCHAR(32) NOT NULL DEFAULT 'KNIT_SWEATER',
    stages        JSONB       NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE tenant_stage_flows ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'tenant_stage_flows' AND policyname = 'tenant_stage_flows_tenant_isolation') THEN
        CREATE POLICY tenant_stage_flows_tenant_isolation ON tenant_stage_flows
            USING (tenant_id = CURRENT_SETTING('app.current_tenant_id', true));
    END IF;
END $$;
