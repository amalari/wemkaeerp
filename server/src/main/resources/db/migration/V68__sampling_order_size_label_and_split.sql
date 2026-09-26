-- ==============================================================================
-- WeMade ERP — V68: Sampling Split per Ukuran (1 Desain Multi-Size -> N SPK Sampling)
-- ==============================================================================
-- 1. Tambah kolom size_label dan parent_sampling_order_id pada sampling_orders.
-- 2. Index pencarian SPK per deal dan per parent order.
-- 3. Backfill data seed smp-seed-0050: split ALL SIZE dan S menjadi 2 SPK terpisah.
-- ==============================================================================

ALTER TABLE sampling_orders ADD COLUMN IF NOT EXISTS size_label VARCHAR(60);
ALTER TABLE sampling_orders ADD COLUMN IF NOT EXISTS parent_sampling_order_id VARCHAR(64) REFERENCES sampling_orders(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_sampling_orders_deal_parent
    ON sampling_orders (tenant_id, deal_id, parent_sampling_order_id);

CREATE INDEX IF NOT EXISTS idx_sampling_orders_deal_size
    ON sampling_orders (tenant_id, deal_id, size_label);

-- Backfill data seed smp-seed-0050 jika ada: set size_label = 'ALL SIZE' dan sample_quantity = 2
UPDATE sampling_orders
   SET size_label = 'ALL SIZE',
       sample_quantity = 2
 WHERE id = 'smp-seed-0050' AND (size_label IS NULL OR size_label = '');

-- Buat SPK kedua untuk ukuran 'S' (SPK-SMP-0051) menempel ke smp-seed-0050
INSERT INTO sampling_orders (
    id, tenant_id, spk_number, client_name, style_name, status,
    pipeline_stage, finishing_path, size_mode, size_label, parent_sampling_order_id,
    deadline_program, deadline_finishing, deadline_delivery,
    deal_id, sample_quantity, sampling_fee_idr,
    revision_count, revision_history, size_matrix,
    acc_notes, notes, created_by_user_id, created_at, updated_at
)
SELECT
    'smp-seed-0050-s', tenant_id, 'SPK-SMP-0051', client_name, style_name, status,
    pipeline_stage, finishing_path, size_mode, 'S', id,
    deadline_program, deadline_finishing, deadline_delivery,
    deal_id, 2, sampling_fee_idr,
    revision_count, revision_history, size_matrix,
    acc_notes, notes, created_by_user_id, created_at, updated_at
FROM sampling_orders
WHERE id = 'smp-seed-0050'
ON CONFLICT (id) DO NOTHING;

-- Knit spec untuk smp-seed-0050-s menyalin dari parent
INSERT INTO sampling_knit_specs (
    id, tenant_id, sampling_order_id, yarn_type, knit_type,
    rib_spec, collar_spec, placket_spec, colorway_notes,
    mockup_image_urls, created_at, updated_at
)
SELECT
    'ks-smp-seed-0050-s', tenant_id, 'smp-seed-0050-s', yarn_type, knit_type,
    rib_spec, collar_spec, placket_spec, colorway_notes,
    mockup_image_urls, created_at, updated_at
FROM sampling_knit_specs
WHERE sampling_order_id = 'smp-seed-0050'
ON CONFLICT (id) DO NOTHING;
