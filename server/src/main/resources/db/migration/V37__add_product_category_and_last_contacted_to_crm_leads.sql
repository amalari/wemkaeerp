-- ==============================================================================
-- WeMade ERP — CRM Leads: Add Product Category and Last Contacted At
-- ==============================================================================

-- Add product_category column (clothing archetype/order type, default empty string)
ALTER TABLE crm_leads ADD COLUMN IF NOT EXISTS product_category VARCHAR(100) NOT NULL DEFAULT '';

-- Add last_contacted_at column for SLA follow-up monitoring
ALTER TABLE crm_leads ADD COLUMN IF NOT EXISTS last_contacted_at TIMESTAMPTZ;

-- Index to optimize querying leads by last follow-up and staleness alerts
CREATE INDEX IF NOT EXISTS idx_crm_leads_tenant_last_contacted
    ON crm_leads(tenant_id, last_contacted_at DESC) WHERE archived_at IS NULL;
