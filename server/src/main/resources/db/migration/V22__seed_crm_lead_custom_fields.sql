-- ==============================================================================
-- WeMade ERP — Seed convection-specific custom fields for crm_leads
-- ==============================================================================
-- Split from V21 deliberately, following the V2/V3/V9 precedent: re-seeding demo tenants
-- must not require editing an already-shipped migration.
--
-- ClothingCategory is intentionally a SEEDED custom field (SINGLE_SELECT), not a hardcoded
-- core enum. Every convection factory has its own taxonomy; locking it into an enum would
-- contradict the entire reason the custom field engine exists. Tenants are free to add,
-- rename or archive options — no deploy required.
--
-- Idempotent via WHERE NOT EXISTS so this migration is safe to run against a tenant that
-- already has these fields (e.g. re-running seeds in development).
-- ==============================================================================

DO $$
DECLARE
    demo_tenant RECORD;
BEGIN
    FOR demo_tenant IN SELECT id FROM tenants WHERE id IN ('ten-demo-001', 'ten-demo-cmt', 'ten-demo-d2c')
    LOOP
        -- 1. Kategori Pakaian (SINGLE_SELECT) — replaces what would otherwise be a hardcoded
        --    ClothingCategory enum (Kaos, Kemeja PDL, Polo, Jaket/Hoodie, Jersey, Celana).
        INSERT INTO custom_field_definitions
            (id, tenant_id, owner_resource, field_key, label, field_type, config, position, is_system)
        SELECT
            'cf-' || demo_tenant.id || '-kategori-pakaian', demo_tenant.id, 'crm_sales',
            'kategori_pakaian', 'Kategori Pakaian', 'SINGLE_SELECT',
            '{"options":[
                {"id":"opt_kaos","label":"Kaos","colorHex":"#2563EB"},
                {"id":"opt_kemeja_pdl","label":"Kemeja PDL","colorHex":"#0D9488"},
                {"id":"opt_polo","label":"Polo","colorHex":"#7C3AED"},
                {"id":"opt_jaket_hoodie","label":"Jaket/Hoodie","colorHex":"#D97706"},
                {"id":"opt_jersey","label":"Jersey","colorHex":"#DC2626"},
                {"id":"opt_celana","label":"Celana","colorHex":"#475569"}
            ]}'::jsonb,
            1000, TRUE
        WHERE NOT EXISTS (
            SELECT 1 FROM custom_field_definitions
            WHERE tenant_id = demo_tenant.id AND owner_resource = 'crm_sales' AND field_key = 'kategori_pakaian'
        );

        -- 2. Jenis Sablon (SINGLE_SELECT)
        INSERT INTO custom_field_definitions
            (id, tenant_id, owner_resource, field_key, label, field_type, config, position, is_system)
        SELECT
            'cf-' || demo_tenant.id || '-jenis-sablon', demo_tenant.id, 'crm_sales',
            'jenis_sablon', 'Jenis Sablon', 'SINGLE_SELECT',
            '{"options":[
                {"id":"opt_rubber","label":"Rubber","colorHex":"#2563EB"},
                {"id":"opt_plastisol","label":"Plastisol","colorHex":"#0D9488"},
                {"id":"opt_dtf","label":"DTF","colorHex":"#7C3AED"},
                {"id":"opt_bordir","label":"Bordir","colorHex":"#D97706"},
                {"id":"opt_tanpa_sablon","label":"Tanpa Sablon","colorHex":"#475569"}
            ]}'::jsonb,
            2000, FALSE
        WHERE NOT EXISTS (
            SELECT 1 FROM custom_field_definitions
            WHERE tenant_id = demo_tenant.id AND owner_resource = 'crm_sales' AND field_key = 'jenis_sablon'
        );

        -- 3. Detail Kain (TEXT)
        INSERT INTO custom_field_definitions
            (id, tenant_id, owner_resource, field_key, label, field_type, config, position, is_system)
        SELECT
            'cf-' || demo_tenant.id || '-detail-kain', demo_tenant.id, 'crm_sales',
            'detail_kain', 'Detail Kain', 'TEXT', '{}'::jsonb, 3000, FALSE
        WHERE NOT EXISTS (
            SELECT 1 FROM custom_field_definitions
            WHERE tenant_id = demo_tenant.id AND owner_resource = 'crm_sales' AND field_key = 'detail_kain'
        );

        -- 4. Ukuran Screen (TEXT)
        INSERT INTO custom_field_definitions
            (id, tenant_id, owner_resource, field_key, label, field_type, config, position, is_system)
        SELECT
            'cf-' || demo_tenant.id || '-ukuran-screen', demo_tenant.id, 'crm_sales',
            'ukuran_screen', 'Ukuran Screen', 'TEXT', '{}'::jsonb, 4000, FALSE
        WHERE NOT EXISTS (
            SELECT 1 FROM custom_field_definitions
            WHERE tenant_id = demo_tenant.id AND owner_resource = 'crm_sales' AND field_key = 'ukuran_screen'
        );

        -- 5. Warna Bahan (SINGLE_SELECT)
        INSERT INTO custom_field_definitions
            (id, tenant_id, owner_resource, field_key, label, field_type, config, position, is_system)
        SELECT
            'cf-' || demo_tenant.id || '-warna-bahan', demo_tenant.id, 'crm_sales',
            'warna_bahan', 'Warna Bahan', 'SINGLE_SELECT',
            '{"options":[
                {"id":"opt_hitam","label":"Hitam","colorHex":"#1E293B"},
                {"id":"opt_putih","label":"Putih","colorHex":"#94A3B8"},
                {"id":"opt_navy","label":"Navy","colorHex":"#1D4ED8"}
            ]}'::jsonb,
            5000, FALSE
        WHERE NOT EXISTS (
            SELECT 1 FROM custom_field_definitions
            WHERE tenant_id = demo_tenant.id AND owner_resource = 'crm_sales' AND field_key = 'warna_bahan'
        );

        -- 6. Sample Approved (CHECKBOX)
        INSERT INTO custom_field_definitions
            (id, tenant_id, owner_resource, field_key, label, field_type, config, position, is_system)
        SELECT
            'cf-' || demo_tenant.id || '-sample-approved', demo_tenant.id, 'crm_sales',
            'sample_approved', 'Sample Approved', 'CHECKBOX', '{}'::jsonb, 6000, FALSE
        WHERE NOT EXISTS (
            SELECT 1 FROM custom_field_definitions
            WHERE tenant_id = demo_tenant.id AND owner_resource = 'crm_sales' AND field_key = 'sample_approved'
        );
    END LOOP;
END $$;
