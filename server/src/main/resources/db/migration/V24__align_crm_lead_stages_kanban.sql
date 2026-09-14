-- ==============================================================================
-- WeMade ERP — CRM Leads Kanban Stages Alignment
-- ==============================================================================
-- Aligning CRM lead stages to the 3-stage Kanban board workflow:
-- 1. NEW_LEAD (Inquiry awal, belum ada value / screening)
-- 2. QUALIFIED (Prospek lolos kualifikasi / kuantiti dan budget cocok)
-- 3. UNQUALIFIED (Prospek tidak memenuhi syarat / batal)
-- ==============================================================================

-- Migrate existing legacy stages if any exist
UPDATE crm_leads SET stage = 'NEW_LEAD' WHERE stage = 'INQUIRY';
UPDATE crm_leads SET stage = 'QUALIFIED' WHERE stage IN ('TECHPACK_SPEC', 'QUOTATION_SENT', 'SAMPLE_APPROVAL', 'DEAL_DP_CONFIRMED');
UPDATE crm_leads SET stage = 'UNQUALIFIED' WHERE stage = 'LOST';

-- Update column default value to NEW_LEAD
ALTER TABLE crm_leads ALTER COLUMN stage SET DEFAULT 'NEW_LEAD';

-- Add index on tenant_id and stage for ultra-fast Kanban grouping
CREATE INDEX IF NOT EXISTS idx_crm_leads_tenant_stage ON crm_leads (tenant_id, stage) WHERE archived_at IS NULL;
