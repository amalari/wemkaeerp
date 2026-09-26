-- ==============================================================================
-- WeMade ERP — Kontak Vendor & Penugasan Vendor ke Proses Subkon (V64)
-- ==============================================================================
-- Sampai V63 vendor hanyalah teks bebas (`vendor_ref`) yang diketik di alur. Akibatnya
-- tidak ada daftar harga per vendor, tidak ada riwayat harga saat deal, dan siapa pun yang
-- menyusun alur bisa menulis nama vendor apa saja.
--
-- Mulai V64:
--   * `vendors`             — buku kontak vendor + daftar harga layanan (JSON, berversi tanggal)
--   * `vendor_assignments`  — keputusan admin produksi: proses X order Y dikerjakan vendor Z
--                             dengan harga ini (snapshot — tidak ikut berubah bila harga naik)
--   * BusinessModule.VENDOR_CONTACTS — wewenangnya terpisah dari CRM dan Master Data Bahan.
--
-- `vendor_ref` di alur TETAP ada: ia diisi otomatis dari nama vendor saat penugasan, dan
-- dari situlah leg Surat Jalan ke vendor diturunkan.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS vendors (
    id          VARCHAR(64) PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL REFERENCES tenants(id),
    name        VARCHAR(150) NOT NULL,
    phone       VARCHAR(40) NOT NULL DEFAULT '',
    address     TEXT NOT NULL DEFAULT '',
    notes       TEXT NOT NULL DEFAULT '',
    rates       TEXT NOT NULL DEFAULT '[]',       -- VendorCodec.encodeRates
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Nama vendor disalin ke Surat Jalan; dua vendor bernama sama tidak bisa dibedakan kurir.
CREATE UNIQUE INDEX IF NOT EXISTS uq_vendors_tenant_name ON vendors(tenant_id, LOWER(name));

CREATE TABLE IF NOT EXISTS vendor_assignments (
    id                  VARCHAR(64) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL REFERENCES tenants(id),
    subject_id          VARCHAR(64) NOT NULL,          -- id order (SPK sampling hari ini, PO massal kelak)
    subject_label       VARCHAR(64) NOT NULL DEFAULT '',
    process_code        VARCHAR(64) NOT NULL,
    process_name        VARCHAR(150) NOT NULL,
    vendor_id           VARCHAR(64) NOT NULL REFERENCES vendors(id),
    vendor_name         VARCHAR(150) NOT NULL,         -- snapshot
    vendor_phone        VARCHAR(40) NOT NULL DEFAULT '',
    price_per_unit_idr  BIGINT NOT NULL,
    price_unit          VARCHAR(30) NOT NULL,
    quantity_pcs        INTEGER NOT NULL,
    units_per_piece     INTEGER NOT NULL DEFAULT 1,
    price_source        VARCHAR(30) NOT NULL,
    expected_return_at  DATE,
    notes               TEXT NOT NULL DEFAULT '',
    status              VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    assigned_by_user_id VARCHAR(64) NOT NULL DEFAULT '',
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cancelled_at        TIMESTAMPTZ,
    CONSTRAINT ck_vendor_assignment_qty CHECK (quantity_pcs > 0 AND units_per_piece > 0),
    CONSTRAINT ck_vendor_assignment_price CHECK (price_per_unit_idr >= 0)
);

-- Satu proses pada satu order hanya boleh punya SATU penugasan aktif.
CREATE UNIQUE INDEX IF NOT EXISTS uq_vendor_assignment_active
    ON vendor_assignments(tenant_id, subject_id, UPPER(process_code))
    WHERE status = 'ASSIGNED';
CREATE INDEX IF NOT EXISTS idx_vendor_assignments_vendor ON vendor_assignments(tenant_id, vendor_id);

ALTER TABLE vendors ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendor_assignments ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'vendors' AND policyname = 'vendors_tenant_isolation') THEN
        CREATE POLICY vendors_tenant_isolation ON vendors
            USING (tenant_id = CURRENT_SETTING('app.current_tenant_id', true));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = 'vendor_assignments' AND policyname = 'vendor_assignments_tenant_isolation') THEN
        CREATE POLICY vendor_assignments_tenant_isolation ON vendor_assignments
            USING (tenant_id = CURRENT_SETTING('app.current_tenant_id', true));
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- Registrasi modul — tenant dengan granted_modules eksplisit tidak boleh kehilangan modul baru
-- ------------------------------------------------------------------------------
UPDATE tenant_module_entitlements
SET granted_modules = (
        SELECT jsonb_agg(DISTINCT combined.m)
        FROM (
            SELECT jsonb_array_elements_text(granted_modules) AS m
            UNION
            SELECT unnest(ARRAY['VENDOR_CONTACTS'])
        ) AS combined
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE granted_modules IS NOT NULL;

INSERT INTO module_catalog_entries (
    id, module_id, archetype_code, display_name, description, category_code,
    scope_capability, stock_ownership, costing_behavior,
    accepted_input_types, produced_output_type,
    is_custom_plugin, origin_tenant_id, lifecycle_status, complexity_tier,
    base_monthly_price_idr, released_at
) VALUES (
    'mce-vendor-contacts', 'vendor_contacts', 'foundation',
    'Kontak Vendor & Makloon',
    'Buku kontak vendor subkon, daftar harga layanan per vendor, dan penunjukan vendor ke proses Vendor Luar.',
    'FOUNDATION', 'GLOBAL_ONLY', 'non_stock_service', 'indirect_overhead',
    '[]', 'VendorAssignment',
    FALSE, NULL, 'RELEASED', 'S', 0, CURRENT_TIMESTAMP
)
ON CONFLICT (module_id) DO NOTHING;

-- ------------------------------------------------------------------------------
-- Backfill wewenang jabatan bawaan (cermin CustomRole.createFactoryPresets)
--   PPIC / admin produksi : MANAGE — menambah vendor, harga, dan menunjuk vendor
--   Sales, Gudang         : VIEW   — melihat vendor & status order di vendor
--   Operator              : NONE
-- ------------------------------------------------------------------------------
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'VENDOR_CONTACTS', jsonb_build_object('level', 'MANAGE', 'scope', 'ALL_TENANT_DATA')),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE AND (id LIKE '%owner' OR id LIKE '%ppic')
  AND NOT (module_permissions ? 'VENDOR_CONTACTS');

UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'VENDOR_CONTACTS', jsonb_build_object('level', 'VIEW', 'scope', 'ALL_TENANT_DATA')),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE AND (id LIKE '%sales' OR id LIKE '%sales-head' OR id LIKE '%warehouse')
  AND NOT (module_permissions ? 'VENDOR_CONTACTS');

UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'VENDOR_CONTACTS', jsonb_build_object('level', 'NONE', 'scope', 'ALL_TENANT_DATA')),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE AND id LIKE '%operator'
  AND NOT (module_permissions ? 'VENDOR_CONTACTS');

-- ------------------------------------------------------------------------------
-- Seed demo: dua vendor rekanan untuk tenant demo
-- ------------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tenants WHERE id = 'ten-demo-001') THEN
        INSERT INTO vendors (id, tenant_id, name, phone, address, notes, rates) VALUES
        ('vnd-demo-001', 'ten-demo-001', 'CV Sablon Jaya', '081234567801', 'Jl. Soekarno Hatta 12, Bandung',
         'Spesialis plastisol & DTF. Min order 50 pcs untuk plastisol.',
         '[{"serviceCode":"SABLON","serviceName":"Sablon Plastisol","priceIdr":3500,"unit":"PER_PRINT_POINT","minQuantity":50,"effectiveFrom":"2026-09-01","effectiveTo":null}]'),
        ('vnd-demo-002', 'ten-demo-001', 'Bordir Mandiri', '081234567802', 'Jl. Cigondewah 7, Bandung',
         'Bordir komputer 12 kepala.',
         '[{"serviceCode":"BORDIR","serviceName":"Bordir Logo Kecil","priceIdr":6000,"unit":"PER_PIECE","minQuantity":24,"effectiveFrom":"2026-08-01","effectiveTo":null}]')
        ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;
