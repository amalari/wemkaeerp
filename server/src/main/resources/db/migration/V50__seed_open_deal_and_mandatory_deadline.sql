-- ==============================================================================
-- WeMade ERP — V50: Seed Open Deal & SPK Sampling Baru Sebelum Terbit SPK
-- ==============================================================================
-- 1. Menambahkan kontak baru 'con-seed-005' (Morfeen Studio).
-- 2. Menambahkan deal baru 'deal-seed-005' berstatus 'OPEN' (tahap awal negosiasi /
--    sampling intake).
-- 3. Menambahkan lembar sampling 'smp-seed-0013' pada deal tersebut berstatus 'DRAFT'
--    dengan pipeline_stage 'NEW_INTAKE', spesifikasi ukuran lengkap, dan
--    mockup terpasang, tetapi deadline_delivery = NULL untuk menguji validasi
--    wajib isi deadline sebelum penerbitan SPK.
-- 4. Memastikan SPK yang sudah aktif di database memiliki deadline_delivery terisi.
-- ==============================================================================

-- 1. Kontak pelanggan baru
INSERT INTO crm_contacts (id, tenant_id, name, brand_name, phone, email, address, created_at, updated_at)
VALUES
    ('con-seed-005', 'ten-demo-001', 'Fajar Pratama', 'Morfeen Studio', '6281200005005',
     'fajar@morfeenstudio.com', 'Jl. Sukajadi No. 120, Bandung', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 2. Deal baru berstatus OPEN (belum terbit SPK)
INSERT INTO deals (id, tenant_id, contact_id, title, stage, estimated_value_idr,
                   expected_close_date, notes, created_by_user_id, created_at, updated_at)
VALUES
    ('deal-seed-005', 'ten-demo-001', 'con-seed-005',
     'PO Hoodie Rajut Vintage — 250 pcs Morfeen Studio', 'OPEN', 65000000,
     CURRENT_DATE + 30, 'Diskusi awal sampel; tentukan deadline pengiriman sampel sebelum rilis SPK ke sampling.',
     'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 3. Lembar sampling DRAFT / NEW_INTAKE terhubung ke deal-seed-005
--    deadline_delivery sengaja NULL agar user dapat menguji inputan wajib di UI
INSERT INTO sampling_orders (id, tenant_id, spk_number, client_name, style_name, status,
                             pipeline_stage, finishing_path, size_mode,
                             deadline_program, deadline_finishing, deadline_delivery,
                             deal_id, sample_quantity, sampling_fee_idr,
                             revision_count, revision_history, size_matrix,
                             acc_notes, notes, created_by_user_id, created_at, updated_at)
VALUES ('smp-seed-0050', 'ten-demo-001', 'SPK-SMP-0050', 'Morfeen Studio', 'Oversized Knit Hoodie Vintage',
        'DRAFT', 'NEW_INTAKE', 'INTERNAL', 'ALL_SIZE',
        NULL, NULL, NULL,
        'deal-seed-005', 2, 350000,
        0, '[]',
        '[
           {"id":"sampling_qty_row","pomName":"Jumlah Sampel (pcs)","values":{"ALL SIZE":"2","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_lebar_dada","pomName":"Lebar Dada","values":{"ALL SIZE":"60","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_panjang_baju","pomName":"Panjang Baju","values":{"ALL SIZE":"72","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_panjang_lengan","pomName":"Panjang Lengan","values":{"ALL SIZE":"58","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}}
         ]'::jsonb,
        '', 'Bahan katun rajut 7GG tebal; kantong kanguru depan dan tali rajut senada.',
        'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 3b. Spesifikasi rajut (Knit Spec) untuk smp-seed-0050 dengan mockup tampak depan
INSERT INTO sampling_knit_specs (id, tenant_id, sampling_order_id, yarn_type, knit_type,
                                 rib_spec, collar_spec, placket_spec, colorway_notes,
                                 mockup_image_urls, created_at, updated_at)
VALUES
    ('ks-smp-seed-0050', 'ten-demo-001', 'smp-seed-0050',
     'Cotton Blend 100% (2/20 NM)', 'Flat Knitting 7GG — Heavy Gauge Hoodie',
     'Rib 2x2 lebar 6 cm', 'Hoodie ganda dengan eyelet', 'Tanpa placket',
     'Solid Charcoal Vintage Washed.',
     '["front:mockup_hoodie_front.png"]'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 4. Pastikan SPK yang sudah aktif di database memiliki deadline_delivery terisi
UPDATE sampling_orders
SET deadline_delivery = CURRENT_DATE + 7,
    updated_at = CURRENT_TIMESTAMP
WHERE deadline_delivery IS NULL
  AND pipeline_stage != 'NEW_INTAKE'
  AND status != 'DRAFT';
