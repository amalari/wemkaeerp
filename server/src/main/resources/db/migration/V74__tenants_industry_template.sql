-- ==============================================================================
-- WeMade ERP — V74: Template Industri per Tenant (TRD-FLOW-001 Tahap 3)
-- ==============================================================================
-- Sumbu terpisah dari business_preset (FOB/CMT/D2C): model bisnis menjawab "siapa pemilik
-- bahan & bagaimana menagih", template industri menjawab "tahap apa yang dijalani barangnya".
-- Menentukan kerangka yang di-provision ke tenant_stage_flows saat tenant belum punya kerangka.
--
-- Default KNIT_SWEATER: seluruh tenant yang ada hari ini berjalan di atas kerangka rajut.
-- Tenant yang sudah punya baris tenant_stage_flows tidak terpengaruh kolom ini.
-- ==============================================================================

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS industry_template VARCHAR(32) NOT NULL DEFAULT 'KNIT_SWEATER';
