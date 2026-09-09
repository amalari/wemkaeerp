-- ==============================================================================
-- WeMade ERP — Soft Delete / Archive Pattern (Issue: Archive instead of Delete)
-- Meniru pola Odoo: active=false / Odoo Archive, data tidak pernah benar-benar dihapus
-- Menyimpan waktu arsip sebagai ISO-8601 VARCHAR (tidak memerlukan module timezone tambahan)
-- ==============================================================================

-- 1. Tambah kolom archived_at pada tabel employees
--    NULL  = karyawan aktif (default)
--    NOT NULL = karyawan sudah diarsipkan (seperti Odoo's active=false)
ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS archived_at VARCHAR(50) DEFAULT NULL;

-- 2. Tambah kolom archived_at pada tabel departments
ALTER TABLE departments
    ADD COLUMN IF NOT EXISTS archived_at VARCHAR(50) DEFAULT NULL;

-- 3. Index untuk performa query WHERE archived_at IS NULL (hot path — semua list query)
CREATE INDEX IF NOT EXISTS idx_employees_active ON employees(tenant_id) WHERE archived_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_departments_active ON departments(tenant_id) WHERE archived_at IS NULL;

-- 4. Index untuk query arsip (cold path — hanya panel arsip)
CREATE INDEX IF NOT EXISTS idx_employees_archived ON employees(tenant_id, archived_at) WHERE archived_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_departments_archived ON departments(tenant_id, archived_at) WHERE archived_at IS NOT NULL;

