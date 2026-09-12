-- ==============================================================================
-- WeMade ERP — Persona Testing & Department Module Assignments
-- ==============================================================================
-- Two gaps this closes, both of which made the RBAC matrix look configurable while
-- being, underneath, not persisted at all:
--
--   1. A signed-in user carried only a coarse `role` (the fixed `Role` enum: TENANT_ADMIN,
--      STAFF, …). The factory-floor role the tenant actually configures (`custom_roles.id`,
--      e.g. `role-sales-head`) and the division the person belongs to had nowhere to live.
--      Without them a testing persona could not survive a page reload: `GET /me` re-derived
--      the identity from the coarse claim and silently widened it.
--
--   2. `DepartmentModuleAssignment` existed as a domain type and as a screen, but only ever
--      as in-memory seed data in the client view model. Changing a division's access to a
--      module looked like it worked and was gone on refresh.
--
-- Everything here is additive. No column is dropped and no existing row is rewritten, so
-- the migration is safe to apply to a populated database.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Carry the tenant-configured identity on the user
-- ------------------------------------------------------------------------------
-- Both nullable: existing users (and the platform superadmin, who has no tenant at all)
-- legitimately have neither. ON DELETE SET NULL rather than CASCADE — deleting a division
-- or a role must not delete the people in it.
ALTER TABLE users ADD COLUMN IF NOT EXISTS department_id VARCHAR(64)
    REFERENCES departments(id) ON DELETE SET NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS custom_role_id VARCHAR(64)
    REFERENCES custom_roles(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_users_department ON users(department_id);
CREATE INDEX IF NOT EXISTS idx_users_custom_role ON users(custom_role_id);

-- ------------------------------------------------------------------------------
-- 2. Let a role state which division it belongs to
-- ------------------------------------------------------------------------------
-- `CustomRole.departmentId` has been in the domain model all along and never reached the
-- database. The access decision engine needs it: a role's permissions and its division's
-- assignment are unioned, and without this link only one half of that union is reachable.
ALTER TABLE custom_roles ADD COLUMN IF NOT EXISTS department_id VARCHAR(64)
    REFERENCES departments(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_custom_roles_department ON custom_roles(department_id);

-- Backfill lewat **kode divisi**, bukan id divisi.
--
-- Versi pertama migrasi ini menulis id mati (`dept-exec`, `dept-sales`, …) dan langsung gagal:
-- `dept-exec` sudah dihapus oleh V7. Id divisi adalah data milik tenant — bisa ditambah, diganti
-- nama, dihapus — sehingga menyebutnya langsung di migrasi berarti mengunci skema pada satu
-- potret data di satu saat.
--
-- JOIN ke `departments` membuat pasangan yang divisinya tidak ada **tersaring dengan sendirinya**,
-- bukan meledak. Dan karena join-nya per `tenant_id`, tenant yang punya id divisi berbeda tetap
-- terisi benar.
--
-- Yang tidak cocok di sini sengaja dibiarkan NULL. Jabatan tanpa divisi tetap sah: wewenangnya
-- datang dari matriks jabatan saja, tanpa jalur divisi. Owner/direksi justru memang begitu.
UPDATE custom_roles cr
SET department_id = d.id
FROM (VALUES
    ('sales-head',  'sales'),
    ('sales',       'sales'),
    ('ppic',        'production_ppic'),
    ('warehouse',   'warehouse'),
    ('operator',    'production_ppic'),
    ('qc',          'qc')
) AS m(role_suffix, dept_code)
JOIN departments d ON d.code = m.dept_code
WHERE cr.department_id IS NULL
  AND cr.tenant_id = d.tenant_id
  AND cr.id LIKE 'role-%' || m.role_suffix;

-- ------------------------------------------------------------------------------
-- 3. Department → module access assignments
-- ------------------------------------------------------------------------------
-- Lives in `public`, not `ops`: this is tenant-owned configuration, which is the line
-- V16 drew. RLS therefore applies to it like every other tenant table.
--
-- `specific_role_ids` empty means "every position in this division" — the domain type's
-- `appliesToAllRoles`. It is JSONB rather than a junction table because it is read as a
-- whole set on every permission evaluation and never queried by individual member.
CREATE TABLE IF NOT EXISTS department_module_assignments (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    department_id VARCHAR(64) NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    module VARCHAR(50) NOT NULL,
    access_level VARCHAR(20) NOT NULL DEFAULT 'OPERATE',
    data_scope VARCHAR(30) NOT NULL DEFAULT 'ALL_TENANT_DATA',
    specific_role_ids JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- One division may hold several assignments for the same module — one for all positions and
-- narrower ones for specific positions — so the uniqueness that matters is per role-set, and
-- that is enforced by the deterministic id the domain builds (`assignmentKey`). The index
-- below is the read path: every evaluation loads one tenant's assignments for one module.
CREATE INDEX IF NOT EXISTS idx_dept_module_assign_tenant ON department_module_assignments(tenant_id);
CREATE INDEX IF NOT EXISTS idx_dept_module_assign_lookup ON department_module_assignments(tenant_id, module);

-- `apply_tenant_rls` melakukan CREATE POLICY tanpa IF NOT EXISTS, jadi pemanggilan kedua akan
-- gagal. Dibuang dulu agar migrasi ini aman dijalankan ulang — sifat yang wajib dimiliki setiap
-- migrasi, bukan kemewahan.
DROP POLICY IF EXISTS department_module_assignments_tenant_isolation ON department_module_assignments;
SELECT apply_tenant_rls('department_module_assignments');

-- ------------------------------------------------------------------------------
-- 4. Seed the demo tenant's assignments
-- ------------------------------------------------------------------------------
-- These mirror `createDefaultModuleAssignments()` in DynamicRbacViewModel, which is what the
-- screen showed before the table existed. Moving them here makes the screen's starting state
-- the database's state rather than a client-side coincidence.
-- Sama seperti backfill di atas: dipilih lewat **kode divisi** dan dijoin, sehingga divisi yang
-- tidak ada di sebuah tenant hanya membuat barisnya tidak tercipta. Id barisnya pun diturunkan
-- dari data (`'dma-' || d.tenant_id || '-' || d.id || '-' || modul`), bukan ditulis tangan, jadi
-- migrasi ini ikut mengisi tenant lain yang id divisinya berbeda tanpa perlu diubah.
--
-- Ini **titik awal**, bukan kebenaran permanen. Divisi yang ditambahkan admin setelah ini tidak
-- muncul di sini dan memang tidak perlu: penugasannya dibuat lewat layar RBAC, dan persona dapat
-- langsung diracik ke divisi baru itu lewat tab "Racik Kustom".
INSERT INTO department_module_assignments (id, tenant_id, department_id, module, access_level, data_scope, specific_role_ids)
SELECT
    'dma-' || d.tenant_id || '-' || d.id || '-' || m.module,
    d.tenant_id,
    d.id,
    m.module,
    m.access_level,
    m.data_scope,
    '[]'::jsonb
FROM (VALUES
    ('sales',           'CRM_SALES',       'OPERATE', 'SUBORDINATE_DATA'),
    ('sales',           'SAMPLING_ORDER',  'OPERATE', 'OWN_DATA_ONLY'),
    ('production_ppic', 'COSTING_HPP',     'VIEW',    'ALL_TENANT_DATA'),
    ('production_ppic', 'TECH_PACK_BOM',   'MANAGE',  'ALL_TENANT_DATA'),
    ('production_ppic', 'PRODUCTION_MRP',  'MANAGE',  'ALL_TENANT_DATA'),
    ('production_ppic', 'OPERATOR_EXEC',   'OPERATE', 'OWN_DATA_ONLY'),
    ('warehouse',       'INVENTORY',       'OPERATE', 'ALL_TENANT_DATA'),
    ('warehouse',       'FULFILLMENT',     'MANAGE',  'ALL_TENANT_DATA'),
    ('qc',              'QUALITY_CONTROL', 'MANAGE',  'ALL_TENANT_DATA'),
    ('qc',              'INVENTORY',       'VIEW',    'ALL_TENANT_DATA'),
    ('finance',         'COSTING_HPP',     'MANAGE',  'ALL_TENANT_DATA')
) AS m(dept_code, module, access_level, data_scope)
JOIN departments d ON d.code = m.dept_code
ON CONFLICT (id) DO NOTHING;
