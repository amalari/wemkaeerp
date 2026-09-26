-- ==============================================================================
-- WeMade ERP — CRM Leads: Add Email and Make Brand Name Optional
-- ==============================================================================

-- Add email column (default empty string)
ALTER TABLE crm_leads ADD COLUMN IF NOT EXISTS email VARCHAR(150) NOT NULL DEFAULT '';

-- Make brand_name optional with default empty string
ALTER TABLE crm_leads ALTER COLUMN brand_name SET DEFAULT '';
