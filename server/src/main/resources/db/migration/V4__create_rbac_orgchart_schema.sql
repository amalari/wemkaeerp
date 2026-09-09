-- ==============================================================================
-- WeMade ERP — Multi-Tenant Schema for RBAC, Departments, and Employees
-- Phase 1: Core RBAC & Organizational Hierarchy (Issue #1, Issue #11)
-- ==============================================================================

-- 1. Custom Roles Table with Module Permissions Matrix (JSONB)
CREATE TABLE IF NOT EXISTS custom_roles (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    is_system_default BOOLEAN NOT NULL DEFAULT FALSE,
    module_permissions JSONB NOT NULL DEFAULT '{}',
    user_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_role_name UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_custom_roles_tenant ON custom_roles(tenant_id);

-- 2. Departments Table with Custom Preset Support
CREATE TABLE IF NOT EXISTS departments (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) REFERENCES tenants(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    short_name VARCHAR(50) NOT NULL,
    color_hex BIGINT NOT NULL,
    is_custom BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_dept_code UNIQUE (tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_departments_tenant ON departments(tenant_id);

-- 3. Employees Table for Factory Hierarchy & Org Chart
CREATE TABLE IF NOT EXISTS employees (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    department_id VARCHAR(64) REFERENCES departments(id) ON DELETE SET NULL,
    level VARCHAR(50) NOT NULL,
    role_title VARCHAR(100) NOT NULL,
    reports_to_id VARCHAR(64) REFERENCES employees(id) ON DELETE SET NULL,
    phone VARCHAR(50) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_employee_email UNIQUE (tenant_id, email)
);

CREATE INDEX IF NOT EXISTS idx_employees_tenant ON employees(tenant_id);
CREATE INDEX IF NOT EXISTS idx_employees_dept ON employees(department_id);
CREATE INDEX IF NOT EXISTS idx_employees_reports_to ON employees(reports_to_id);

-- 4. Apply Multi-Tenant Row-Level Security (RLS)
SELECT apply_tenant_rls('custom_roles');
SELECT apply_tenant_rls('departments');
SELECT apply_tenant_rls('employees');

-- 5. Seed initial data for demo tenant 'ten-demo-001'
-- A. Default Departments
INSERT INTO departments (id, tenant_id, code, display_name, short_name, color_hex, is_custom)
VALUES 
    ('dept-sales', 'ten-demo-001', 'sales', 'Penjualan & CRM', 'Sales', 4280624107, FALSE),
    ('dept-ppic', 'ten-demo-001', 'production_ppic', 'Produksi & PPIC', 'Produksi', 4293552140, FALSE),
    ('dept-warehouse', 'ten-demo-001', 'warehouse', 'Gudang & Logistik', 'Gudang', 4279145608, FALSE),
    ('dept-qc', 'ten-demo-001', 'qc', 'Quality Control (QC)', 'QC', 4279583562, FALSE),
    ('dept-exec', 'ten-demo-001', 'finance_executive', 'Keuangan & Direksi', 'Direksi', 4286339821, FALSE)
ON CONFLICT (id) DO NOTHING;

-- B. Default Factory Presets Roles
INSERT INTO custom_roles (id, tenant_id, name, description, is_system_default, module_permissions, user_count)
VALUES 
    ('role-owner', 'ten-demo-001', 'Owner / Direktur Pabrik', 'Pemilik usaha dengan akses penuh ke seluruh modul, keuangan rahasia, dan manajemen staf.', TRUE, '{"CRM_SALES":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"SAMPLING_ORDER":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"INVENTORY":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"TECH_PACK_BOM":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"COSTING_HPP":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"PRODUCTION_MRP":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"OPERATOR_EXEC":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"QUALITY_CONTROL":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"FULFILLMENT":{"level":"MANAGE","scope":"ALL_TENANT_DATA"}}'::jsonb, 1),
    ('role-ppic', 'ten-demo-001', 'Kepala Produksi (PPIC)', 'Merencanakan alokasi mesin jahit, SPK potong/jahit, memantau bahan baku, dan kontrol kualitas.', TRUE, '{"CRM_SALES":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"SAMPLING_ORDER":{"level":"OPERATE","scope":"SUBORDINATE_DATA"},"INVENTORY":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"TECH_PACK_BOM":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"COSTING_HPP":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"PRODUCTION_MRP":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"OPERATOR_EXEC":{"level":"MANAGE","scope":"SUBORDINATE_DATA"},"QUALITY_CONTROL":{"level":"MANAGE","scope":"ALL_TENANT_DATA"},"FULFILLMENT":{"level":"OPERATE","scope":"ALL_TENANT_DATA"}}'::jsonb, 2),
    ('role-sales-head', 'ten-demo-001', 'Kepala Penjualan (Head of Sales)', 'Memantau target prospek seluruh sales bawahan, menyetujui sampling order, dan evaluasi komisi.', TRUE, '{"CRM_SALES":{"level":"MANAGE","scope":"SUBORDINATE_DATA"},"SAMPLING_ORDER":{"level":"MANAGE","scope":"SUBORDINATE_DATA"},"INVENTORY":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"TECH_PACK_BOM":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"COSTING_HPP":{"level":"VIEW","scope":"SUBORDINATE_DATA"},"PRODUCTION_MRP":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"OPERATOR_EXEC":{"level":"NONE","scope":"ALL_TENANT_DATA"},"QUALITY_CONTROL":{"level":"NONE","scope":"ALL_TENANT_DATA"},"FULFILLMENT":{"level":"VIEW","scope":"ALL_TENANT_DATA"}}'::jsonb, 1),
    ('role-sales', 'ten-demo-001', 'Sales Eksekutif', 'Mencatat prospek pelanggan, mengajukan sampling baju, dan memantau progres pesanan.', TRUE, '{"CRM_SALES":{"level":"OPERATE","scope":"OWN_DATA_ONLY"},"SAMPLING_ORDER":{"level":"OPERATE","scope":"OWN_DATA_ONLY"},"INVENTORY":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"TECH_PACK_BOM":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"COSTING_HPP":{"level":"VIEW","scope":"OWN_DATA_ONLY"},"PRODUCTION_MRP":{"level":"NONE","scope":"ALL_TENANT_DATA"},"OPERATOR_EXEC":{"level":"NONE","scope":"ALL_TENANT_DATA"},"QUALITY_CONTROL":{"level":"NONE","scope":"ALL_TENANT_DATA"},"FULFILLMENT":{"level":"VIEW","scope":"ALL_TENANT_DATA"}}'::jsonb, 4),
    ('role-warehouse', 'ten-demo-001', 'Staff Gudang & Logistik', 'Menerima bahan baku kain, mengelola pengeluaran aksesoris, dan mencetak surat jalan packing.', TRUE, '{"CRM_SALES":{"level":"NONE","scope":"ALL_TENANT_DATA"},"SAMPLING_ORDER":{"level":"NONE","scope":"ALL_TENANT_DATA"},"INVENTORY":{"level":"OPERATE","scope":"ALL_TENANT_DATA"},"TECH_PACK_BOM":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"COSTING_HPP":{"level":"NONE","scope":"ALL_TENANT_DATA"},"PRODUCTION_MRP":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"OPERATOR_EXEC":{"level":"NONE","scope":"ALL_TENANT_DATA"},"QUALITY_CONTROL":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"FULFILLMENT":{"level":"MANAGE","scope":"ALL_TENANT_DATA"}}'::jsonb, 3),
    ('role-operator', 'ten-demo-001', 'Operator Mesin Jahit', 'Input pencapaian hasil jahitan harian pada antarmuka tablet tanpa akses dokumen lainnya.', TRUE, '{"CRM_SALES":{"level":"NONE","scope":"ALL_TENANT_DATA"},"SAMPLING_ORDER":{"level":"NONE","scope":"ALL_TENANT_DATA"},"INVENTORY":{"level":"NONE","scope":"ALL_TENANT_DATA"},"TECH_PACK_BOM":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"COSTING_HPP":{"level":"NONE","scope":"ALL_TENANT_DATA"},"PRODUCTION_MRP":{"level":"VIEW","scope":"ALL_TENANT_DATA"},"OPERATOR_EXEC":{"level":"OPERATE","scope":"OWN_DATA_ONLY"},"QUALITY_CONTROL":{"level":"NONE","scope":"ALL_TENANT_DATA"},"FULFILLMENT":{"level":"NONE","scope":"ALL_TENANT_DATA"}}'::jsonb, 14)
ON CONFLICT (id) DO NOTHING;

-- C. Default Employees
INSERT INTO employees (id, tenant_id, name, email, department_id, level, role_title, reports_to_id, phone)
VALUES 
    ('emp-hendra', 'ten-demo-001', 'Bpk. Hendra Kusuma', 'hendra.owner@wemade.id', 'dept-exec', 'EXECUTIVE', 'Direktur Utama / Owner', NULL, '081122334455'),
    ('emp-budi', 'ten-demo-001', 'Budi Santoso', 'budi.sales@wemade.id', 'dept-sales', 'HEAD_OF_DEPARTMENT', 'Head of Sales & Marketing', 'emp-hendra', '081234567890'),
    ('emp-joko', 'ten-demo-001', 'Joko Susilo', 'joko.ppic@wemade.id', 'dept-ppic', 'HEAD_OF_DEPARTMENT', 'Kepala Produksi & PPIC', 'emp-hendra', '081398765432'),
    ('emp-siti', 'ten-demo-001', 'Siti Rahma', 'siti.gudang@wemade.id', 'dept-warehouse', 'HEAD_OF_DEPARTMENT', 'Kepala Gudang & Logistik', 'emp-hendra', '081711223344'),
    ('emp-anton', 'ten-demo-001', 'Anton Prasetyo', 'anton.qc@wemade.id', 'dept-qc', 'HEAD_OF_DEPARTMENT', 'Kepala Quality Control (QC)', 'emp-hendra', '081855667788'),
    ('emp-rian', 'ten-demo-001', 'Rian Firmansyah', 'rian.sales@wemade.id', 'dept-sales', 'STAFF_OPERATOR', 'Sales Eksekutif Lapangan', 'emp-budi', '082111223344'),
    ('emp-dedi', 'ten-demo-001', 'Dedi Kurniawan', 'dedi.tender@wemade.id', 'dept-sales', 'STAFF_OPERATOR', 'Sales Tender & Korporat', 'emp-budi', '082199887766'),
    ('emp-maya', 'ten-demo-001', 'Maya Anggraini', 'maya.sample@wemade.id', 'dept-sales', 'STAFF_OPERATOR', 'Admin Sampling & CS', 'emp-budi', '082133445566'),
    ('emp-agus', 'ten-demo-001', 'Agus Setiawan', 'agus.cutting@wemade.id', 'dept-ppic', 'STAFF_OPERATOR', 'Mandor Meja Potong', 'emp-joko', '085211223344')
ON CONFLICT (id) DO NOTHING;
