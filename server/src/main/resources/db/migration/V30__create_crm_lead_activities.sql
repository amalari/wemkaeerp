-- ==============================================================================
-- WeMade ERP — CRM Lead Activities (Sales comments & follow-up log)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS crm_lead_activities (
    id                  VARCHAR(64) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    lead_id             VARCHAR(64) NOT NULL REFERENCES crm_leads(id) ON DELETE CASCADE,
    author_employee_id  VARCHAR(64) REFERENCES employees(id) ON DELETE SET NULL,
    author_name         VARCHAR(150) NOT NULL DEFAULT 'Sales',
    content             TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_crm_lead_activities_lead ON crm_lead_activities(tenant_id, lead_id, created_at DESC);

-- Enable RLS for tenant isolation
ALTER TABLE crm_lead_activities ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'crm_lead_activities' AND policyname = 'crm_lead_activities_tenant_isolation'
    ) THEN
        CREATE POLICY crm_lead_activities_tenant_isolation ON crm_lead_activities
            USING (tenant_id = CURRENT_SETTING('app.current_tenant_id', true));
    END IF;
END $$;
