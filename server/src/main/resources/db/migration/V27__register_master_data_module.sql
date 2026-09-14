-- ==============================================================================
-- WeMade ERP — Registrasi BusinessModule.MASTER_DATA & Seed Bahan Demo (V27)
-- ==============================================================================

-- 1. Jangan sampai tenant dengan granted_modules eksplisit kehilangan modul baru
UPDATE tenant_module_entitlements
SET granted_modules = (
        SELECT jsonb_agg(DISTINCT combined.m)
        FROM (
            SELECT jsonb_array_elements_text(granted_modules) AS m
            UNION
            SELECT unnest(ARRAY['MASTER_DATA'])
        ) AS combined
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE granted_modules IS NOT NULL;

-- 2. Daftarkan ke katalog modul
INSERT INTO module_catalog_entries (
    id, module_id, archetype_code, display_name, description, category_code,
    scope_capability, stock_ownership, costing_behavior,
    accepted_input_types, produced_output_type,
    is_custom_plugin, origin_tenant_id, lifecycle_status, complexity_tier,
    base_monthly_price_idr, released_at
) VALUES (
    'mce-master-data', 'master_data', 'foundation',
    'Master Data Bahan & Harga',
    'Katalog benang, kain, aksesoris, satuan kemasan, dan tarif acuan HPP point-in-time.',
    'FOUNDATION', 'GLOBAL_ONLY', 'non_stock_service', 'indirect_overhead',
    '[]', 'MaterialCatalogSnapshot',
    FALSE, NULL, 'RELEASED', 'M', 0, CURRENT_TIMESTAMP
)
ON CONFLICT (module_id) DO NOTHING;

-- 3. Backfill wewenang role RBAC yang sudah ada
-- Owner
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'MASTER_DATA', jsonb_build_object('level', 'MANAGE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%owner'
  AND NOT (module_permissions ? 'MASTER_DATA');

-- PPIC
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'MASTER_DATA', jsonb_build_object('level', 'MANAGE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%ppic'
  AND NOT (module_permissions ? 'MASTER_DATA');

-- Head of Sales
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'MASTER_DATA', jsonb_build_object('level', 'VIEW', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%sales-head'
  AND NOT (module_permissions ? 'MASTER_DATA');

-- Sales Eksekutif
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'MASTER_DATA', jsonb_build_object('level', 'NONE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%sales'
  AND id NOT LIKE '%sales-head'
  AND NOT (module_permissions ? 'MASTER_DATA');

-- Staff Gudang
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'MASTER_DATA', jsonb_build_object('level', 'OPERATE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%warehouse'
  AND NOT (module_permissions ? 'MASTER_DATA');

-- Operator
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'MASTER_DATA', jsonb_build_object('level', 'NONE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%operator'
  AND NOT (module_permissions ? 'MASTER_DATA');

-- 4. Seed demo materials untuk tenant demo ten-demo-001 (bila ada)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tenants WHERE id = 'ten-demo-001') THEN
        -- Seed initial sequences
        INSERT INTO material_code_sequences (tenant_id, category_code, current_seq)
        VALUES
            ('ten-demo-001', 'YRN', 2),
            ('ten-demo-001', 'FAB', 1),
            ('ten-demo-001', 'TRM', 2),
            ('ten-demo-001', 'PKG', 1)
        ON CONFLICT (tenant_id, category_code) DO NOTHING;

        -- Material 1: Benang Katun Combed 30s
        INSERT INTO material_items (
            id, tenant_id, code, name, category, base_uom, alternate_uoms, default_ownership, description
        ) VALUES (
            'mat-demo-001', 'ten-demo-001', 'YRN-0001', 'Benang Katun Combed 30s Reaktif', 'yarn', 'kg',
            '[{"from":"cone","equivalent":{"micros":1200000,"uom":"kg"}}]',
            'owned_raw_material', 'Benang rajut katun combed 30s warna hitam dan varian reaktif'
        ) ON CONFLICT (id) DO NOTHING;

        INSERT INTO material_prices (
            id, tenant_id, material_id, amount_minor, currency, per_quantity_micros, per_uom, source, effective_from, note
        ) VALUES (
            'prc-demo-001', 'ten-demo-001', 'mat-demo-001', 14500000, 'IDR', 1000000, 'kg', 'STANDARD',
            '2026-01-01 00:00:00+00', 'Tarif acuan kuartal 1'
        ) ON CONFLICT (id) DO NOTHING;

        -- Material 2: Benang Viscose 2/30 (Bahan Sampling Cardigan)
        INSERT INTO material_items (
            id, tenant_id, code, name, category, base_uom, alternate_uoms, default_ownership, description
        ) VALUES (
            'mat-demo-002', 'ten-demo-001', 'YRN-0002', 'Benang Viscose 2/30 Raw White', 'yarn', 'kg',
            '[{"from":"cone","equivalent":{"micros":1000000,"uom":"kg"}}]',
            'owned_raw_material', 'Benang viscose halus untuk cardigan dan sweater rajut'
        ) ON CONFLICT (id) DO NOTHING;

        INSERT INTO material_prices (
            id, tenant_id, material_id, amount_minor, currency, per_quantity_micros, per_uom, source, effective_from, note
        ) VALUES (
            'prc-demo-002', 'ten-demo-001', 'mat-demo-002', 12800000, 'IDR', 1000000, 'kg', 'STANDARD',
            '2026-01-01 00:00:00+00', 'Tarif acuan reguler supplier PT. Indo Raya'
        ) ON CONFLICT (id) DO NOTHING;

        -- Material 3: Kain Baby Terry (Konsinyasi Klien)
        INSERT INTO material_items (
            id, tenant_id, code, name, category, base_uom, alternate_uoms, default_ownership, description
        ) VALUES (
            'mat-demo-003', 'ten-demo-001', 'FAB-0001', 'Kain Baby Terry Navy (Titipan Buyer)', 'fabric', 'kg',
            '[{"from":"roll","equivalent":{"micros":25000000,"uom":"kg"}}]',
            'consigned_client_material', 'Kain titipan klien brand untuk produksi hoodie makloon'
        ) ON CONFLICT (id) DO NOTHING;

        INSERT INTO material_prices (
            id, tenant_id, material_id, amount_minor, currency, per_quantity_micros, per_uom, source, effective_from, note
        ) VALUES (
            'prc-demo-003', 'ten-demo-001', 'mat-demo-003', 0, 'IDR', 1000000, 'kg', 'CLIENT_SUPPLIED_ZERO',
            '2026-01-01 00:00:00+00', 'Kain titipan konsinyasi klien (Rp 0)'
        ) ON CONFLICT (id) DO NOTHING;

        -- Material 4: Kancing Batok Kelapa 18L
        INSERT INTO material_items (
            id, tenant_id, code, name, category, base_uom, alternate_uoms, default_ownership, description
        ) VALUES (
            'mat-demo-004', 'ten-demo-001', 'TRM-0001', 'Kancing Batok Kelapa Natural 18L', 'trim', 'pcs',
            '[{"from":"grs","equivalent":{"micros":144000000,"uom":"pcs"}}]',
            'owned_raw_material', 'Kancing 2-hole aksen kayu/kelapa untuk cardigan rajut'
        ) ON CONFLICT (id) DO NOTHING;

        INSERT INTO material_prices (
            id, tenant_id, material_id, amount_minor, currency, per_quantity_micros, per_uom, source, effective_from, note
        ) VALUES (
            'prc-demo-004', 'ten-demo-001', 'mat-demo-004', 35000, 'IDR', 1000000, 'pcs', 'STANDARD',
            '2026-01-01 00:00:00+00', 'Rp 350 / pcs (Rp 50.400 / gross)'
        ) ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;
