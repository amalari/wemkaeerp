-- ==============================================================================
-- WeMade ERP — Multi-Tenant Seed: 3 Tenant Companies (Issue #21)
-- 1. PT WeMade Garmen Ekspor (Pabrik Utama)
-- 2. CV Berkah Makloon Jahit (Unit Makloon Jahit)
-- 3. UrbanWear Studio Apparel (Workshop Studio)
-- ==============================================================================

-- 1. Update existing primary demo tenant
UPDATE tenants
SET name = 'PT WeMade Garmen Ekspor',
    status = 'ACTIVE',
    tier = 'PRO',
    active_machine_count = 12
WHERE slug = 'wemade-demo';

-- 2. Seed Tenant (cv-berkah-makloon)
INSERT INTO tenants (id, slug, name, status, tier, active_machine_count)
VALUES (
    'ten-demo-cmt',
    'cv-berkah-makloon',
    'CV Berkah Makloon Jahit',
    'ACTIVE',
    'PRO',
    8
)
ON CONFLICT (id) DO UPDATE SET
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    tier = EXCLUDED.tier,
    active_machine_count = EXCLUDED.active_machine_count;

-- 3. Seed Tenant (urbanwear-d2c)
INSERT INTO tenants (id, slug, name, status, tier, active_machine_count)
VALUES (
    'ten-demo-d2c',
    'urbanwear-d2c',
    'UrbanWear Studio Apparel',
    'ACTIVE',
    'PRO',
    15
)
ON CONFLICT (id) DO UPDATE SET
    slug = EXCLUDED.slug,
    name = EXCLUDED.name,
    status = EXCLUDED.status,
    tier = EXCLUDED.tier,
    active_machine_count = EXCLUDED.active_machine_count;

-- 4. Seed initial operational pipeline for each tenant
INSERT INTO tenant_pipelines (id, tenant_id, pipeline_name, base_preset, graph_data)
VALUES
    (
        'pipe-ten-demo-001',
        'ten-demo-001',
        'Alur Operasional PT WeMade Garmen Ekspor',
        'fob_full_package',
        '{"tenantId":"ten-demo-001","company":"PT WeMade Garmen Ekspor"}'
    ),
    (
        'pipe-ten-demo-cmt',
        'ten-demo-cmt',
        'Alur Operasional CV Berkah Makloon Jahit',
        'cmt_makloon',
        '{"tenantId":"ten-demo-cmt","company":"CV Berkah Makloon Jahit"}'
    ),
    (
        'pipe-ten-demo-d2c',
        'ten-demo-d2c',
        'Alur Operasional UrbanWear Studio Apparel',
        'brand_d2c',
        '{"tenantId":"ten-demo-d2c","company":"UrbanWear Studio Apparel"}'
    )
ON CONFLICT (tenant_id) DO UPDATE SET
    pipeline_name = EXCLUDED.pipeline_name,
    base_preset = EXCLUDED.base_preset;
