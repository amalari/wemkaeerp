-- ==============================================================================
-- WeMade ERP — Partial Unique Indexes for Soft-Delete / Archive Pattern
-- Issue: Allow reuse/re-registration & graceful email conflict handling
-- ==============================================================================

-- 1. Employees: Ganti unique constraint global dengan partial unique index (hanya aktif)
ALTER TABLE employees DROP CONSTRAINT IF EXISTS uq_tenant_employee_email;
DROP INDEX IF EXISTS uq_tenant_employee_email;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_employee_email_active 
    ON employees(tenant_id, email) 
    WHERE archived_at IS NULL;

-- 2. Departments: Ganti unique constraint global dengan partial unique index (hanya aktif)
ALTER TABLE departments DROP CONSTRAINT IF EXISTS uq_tenant_dept_code;
DROP INDEX IF EXISTS uq_tenant_dept_code;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_dept_code_active 
    ON departments(tenant_id, code) 
    WHERE archived_at IS NULL;
