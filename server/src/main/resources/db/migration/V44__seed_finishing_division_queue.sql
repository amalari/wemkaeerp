-- ==============================================================================
-- WeMade ERP — Seed: Antrean Divisi Finishing Internal (V44)
-- ==============================================================================
-- Melanjutkan V43. Tujuannya satu: modul OPERATOR_EXEC (Catatan Kerja Operator
-- Finishing) punya antrean yang representatif, bukan layar kosong.
--
-- FinishingOperatorWorkspaceScreen menyaring SPK dengan syarat:
--   pipeline_stage IN ('LINKING_ASSEMBLY','FINISHING_QC')  DAN  finishing_path='INTERNAL'
-- Sebelum seed ini hanya SPK-SMP-0008 yang lolos saringan itu (SPK-SMP-0007 ada di
-- tahap linking tapi jalurnya MAKLOON_VENDOR, jadi dipantau admin di tab vendor).
--
-- 4 SPK baru dengan variasi progres setoran, supaya seluruh state kartu terlihat:
--   SPK-SMP-0009  LINKING_ASSEMBLY  4 pcs   0/4  — baru masuk antrean, belum disetor
--   SPK-SMP-0010  FINISHING_QC      6 pcs   4/6  — setoran bertahap (2 setoran, multi-size)
--   SPK-SMP-0011  FINISHING_QC      3 pcs   3/3  — tuntas, siap naik ke antrean QC
--   SPK-SMP-0012  LINKING_ASSEMBLY  2 pcs   1/2  — sisa 1 pcs, ada bukti foto timbangan
--
-- PENOMORAN: PostgresSamplingOrderRepository.nextSpkNumber() = COUNT(*) + 1.
-- DB dev memuat 0001..0008, jadi seed ini mengisi 0009..0012 dan nomor berikutnya
-- yang diterbitkan aplikasi (0013) tetap bebas dari constraint UNIQUE.
--
-- Target pcs finishing dihitung finishingTargetPcs() dari baris `sampling_qty_row`
-- di size_matrix, dan sebuah kolom ukuran hanya dianggap AKTIF bila SELURUH baris
-- POM di matriks terisi untuk kolom itu (isSizeColumnActive). Karena itu kolom yang
-- dipakai di bawah selalu diisi penuh di semua baris POM — kalau ada satu sel POM
-- kosong, kuantitasnya diam-diam tidak ikut terhitung dan target jadi meleset.
-- ==============================================================================

-- 1. Kontak & deal baru (dua SPK finishing menempel ke sini)
INSERT INTO crm_contacts (id, tenant_id, name, brand_name, phone, email, address, created_at, updated_at)
VALUES
    ('con-seed-004', 'ten-demo-001', 'Dimas Prasetyo', 'Kanva Knit', '6281200004004',
     'dimas@kanvaknit.id', 'Jl. Soekarno Hatta No. 145, Bandung', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO deals (id, tenant_id, contact_id, title, stage, estimated_value_idr,
                   expected_close_date, notes, created_by_user_id, created_at, updated_at)
VALUES
    ('deal-seed-004', 'ten-demo-001', 'con-seed-004',
     'PO Vest Rajut Kanva — 400 pcs Dua Colorway', 'WON', 61000000,
     CURRENT_DATE - 3, 'Dua sampel colorway berjalan paralel di meja finishing.',
     'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '3 days')
ON CONFLICT (id) DO NOTHING;


-- 2. SPK sampling di tahap finishing (semua jalur INTERNAL)

-- 2a. SPK-SMP-0009 — baru turun ke meja linking, belum ada setoran
INSERT INTO sampling_orders (id, tenant_id, spk_number, client_name, style_name, status,
                             pipeline_stage, finishing_path, size_mode,
                             deadline_program, deadline_finishing, deadline_delivery,
                             deal_id, sample_quantity, sampling_fee_idr,
                             revision_count, revision_history, size_matrix,
                             acc_notes, notes, created_by_user_id, created_at, updated_at)
VALUES ('smp-seed-0009', 'ten-demo-001', 'SPK-SMP-0009', 'Kanva Knit', 'Vest Rajut Rib Colorway Navy',
        'IN_PROGRESS', 'LINKING_ASSEMBLY', 'INTERNAL', 'ALL_SIZE',
        CURRENT_DATE - 6, CURRENT_DATE + 4, CURRENT_DATE + 7,
        'deal-seed-004', 4, 850000,
        0, '[]',
        '[
           {"id":"sampling_qty_row","pomName":"Jumlah Sampel (pcs)","values":{"ALL SIZE":"4","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_lebar_dada","pomName":"Lebar Dada","values":{"ALL SIZE":"54","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_panjang_baju","pomName":"Panjang Baju","values":{"ALL SIZE":"58","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}}
         ]'::jsonb,
        '', 'Panel sudah lengkap di rak B2; linking bahu dulu baru rib pinggang.',
        'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '11 days', CURRENT_TIMESTAMP - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;

-- 2b. SPK-SMP-0010 — setoran bertahap multi-size, baru 4 dari 6 pcs
INSERT INTO sampling_orders (id, tenant_id, spk_number, client_name, style_name, status,
                             pipeline_stage, finishing_path, size_mode,
                             deadline_program, deadline_finishing, deadline_delivery,
                             deal_id, sample_quantity, sampling_fee_idr,
                             revision_count, revision_history, size_matrix,
                             acc_notes, notes, created_by_user_id, created_at, updated_at)
VALUES ('smp-seed-0010', 'ten-demo-001', 'SPK-SMP-0010', 'Kanva Knit', 'Vest Rajut Rib Colorway Cream',
        'IN_PROGRESS', 'FINISHING_QC', 'INTERNAL', 'MULTI_SIZE',
        CURRENT_DATE - 8, CURRENT_DATE + 2, CURRENT_DATE + 5,
        'deal-seed-004', 6, 1150000,
        0, '[]',
        '[
           {"id":"sampling_qty_row","pomName":"Jumlah Sampel (pcs)","values":{"ALL SIZE":"","S":"2","M":"2","L":"2","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_lebar_dada","pomName":"Lebar Dada","values":{"ALL SIZE":"","S":"48","M":"51","L":"54","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_panjang_baju","pomName":"Panjang Baju","values":{"ALL SIZE":"","S":"56","M":"58","L":"60","XL":"","XXL":"","XXXL":""}}
         ]'::jsonb,
        '', 'Setoran dicicil per ukuran; sisa L menunggu obras rib pinggang.',
        'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '13 days', CURRENT_TIMESTAMP - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;

-- 2c. SPK-SMP-0011 — finishing tuntas 3/3, siap diambil QC
INSERT INTO sampling_orders (id, tenant_id, spk_number, client_name, style_name, status,
                             pipeline_stage, finishing_path, size_mode,
                             deadline_program, deadline_finishing, deadline_delivery,
                             deal_id, sample_quantity, sampling_fee_idr,
                             revision_count, revision_history, size_matrix,
                             acc_notes, notes, created_by_user_id, created_at, updated_at)
VALUES ('smp-seed-0011', 'ten-demo-001', 'SPK-SMP-0011', 'Sejahtera Style', 'Sweater Rajut Crewneck Rib Tebal',
        'IN_PROGRESS', 'FINISHING_QC', 'INTERNAL', 'ALL_SIZE',
        CURRENT_DATE - 9, CURRENT_DATE + 1, CURRENT_DATE + 4,
        'deal-seed-002', 3, 900000,
        0, '[]',
        '[
           {"id":"sampling_qty_row","pomName":"Jumlah Sampel (pcs)","values":{"ALL SIZE":"3","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_lebar_dada","pomName":"Lebar Dada","values":{"ALL SIZE":"57","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_panjang_baju","pomName":"Panjang Baju","values":{"ALL SIZE":"65","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}}
         ]'::jsonb,
        '', 'Sudah disetrika uap dan digantung; tinggal verifikasi POM oleh QC.',
        'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '6 hours')
ON CONFLICT (id) DO NOTHING;

-- 2d. SPK-SMP-0012 — sisa 1 pcs, setoran pertama berbukti foto timbangan
INSERT INTO sampling_orders (id, tenant_id, spk_number, client_name, style_name, status,
                             pipeline_stage, finishing_path, size_mode,
                             deadline_program, deadline_finishing, deadline_delivery,
                             deal_id, sample_quantity, sampling_fee_idr,
                             revision_count, revision_history, size_matrix,
                             acc_notes, notes, created_by_user_id, created_at, updated_at)
VALUES ('smp-seed-0012', 'ten-demo-001', 'SPK-SMP-0012', 'Nuansa Wear', 'Cardigan Rajut Pocket Cable',
        'IN_PROGRESS', 'LINKING_ASSEMBLY', 'INTERNAL', 'ALL_SIZE',
        CURRENT_DATE - 4, CURRENT_DATE + 6, CURRENT_DATE + 9,
        'deal-seed-003', 2, 800000,
        0, '[]',
        '[
           {"id":"sampling_qty_row","pomName":"Jumlah Sampel (pcs)","values":{"ALL SIZE":"2","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_lebar_dada","pomName":"Lebar Dada","values":{"ALL SIZE":"53","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_panjang_baju","pomName":"Panjang Baju","values":{"ALL SIZE":"64","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}}
         ]'::jsonb,
        '', 'Kabel depan rawan melar — linking saku dikerjakan terakhir.',
        'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '2 days')
ON CONFLICT (id) DO NOTHING;


-- 3. Spesifikasi rajut per SPK
INSERT INTO sampling_knit_specs (id, tenant_id, sampling_order_id, yarn_type, knit_type,
                                 rib_spec, collar_spec, placket_spec, colorway_notes,
                                 mockup_image_urls, created_at, updated_at)
VALUES
    ('ks-smp-seed-0009', 'ten-demo-001', 'smp-seed-0009',
     'Akilik 100% (2/32 NM)', 'Flat Knitting 12GG — Vest Rib 2x2',
     'Rib 2x2 lebar 4 cm', 'V-neck rib 3 cm', 'Placket kancing 4 butir',
     'Solid Navy; rib senada.', '[]'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ks-smp-seed-0010', 'ten-demo-001', 'smp-seed-0010',
     'Akilik 100% (2/32 NM)', 'Flat Knitting 12GG — Vest Rib 2x2',
     'Rib 2x2 lebar 4 cm', 'V-neck rib 3 cm', 'Placket kancing 4 butir',
     'Solid Cream; grading S/M/L satu PO dengan colorway Navy.',
     '[]'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ks-smp-seed-0011', 'ten-demo-001', 'smp-seed-0011',
     'Wool Blend 20% + Akilik 80% (2/28 NM)', 'Flat Knitting 7GG — Crewneck Rib Tebal',
     'Rib 2x1 lebar 5 cm', 'Crewneck rib 3 cm', 'Tanpa placket (pullover)',
     'Solid Mocca; benang sama dengan SPK-SMP-0007.',
     '[]'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ks-smp-seed-0012', 'ten-demo-001', 'smp-seed-0012',
     'Akilik Premium (2/30 NM)', 'Flat Knitting 12GG — Cable Knit + Saku Tempel',
     'Rib inggris lebar 4 cm', 'Round neck rib 2.5 cm', 'Placket kancing 6 butir',
     'Solid Cream; motif cable 4 jalur di panel depan.',
     '[]'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;


-- 4. Milestone tracker — PROGRAM & RAJUT beres, LINKING/WASHING mengikuti tahap masing-masing
INSERT INTO sampling_milestones (id, tenant_id, sampling_order_id, step_name,
                                 is_completed, completed_at, step_order, notes)
VALUES
    -- SPK-SMP-0009: panel turun mesin, linking baru mulai
    ('sm-0009-1', 'ten-demo-001', 'smp-seed-0009', 'PROGRAM',  TRUE,  CURRENT_DATE - 10, 1, 'Program vest 12GG OK'),
    ('sm-0009-2', 'ten-demo-001', 'smp-seed-0009', 'RAJUT',    TRUE,  CURRENT_DATE - 6,  2, '4 set panel selesai'),
    ('sm-0009-3', 'ten-demo-001', 'smp-seed-0009', 'PROSES_TAMBAHAN', FALSE, NULL,       3, ''),
    ('sm-0009-4', 'ten-demo-001', 'smp-seed-0009', 'LINKING',  FALSE, NULL,              4, 'Antrean meja linking hari ini'),
    ('sm-0009-5', 'ten-demo-001', 'smp-seed-0009', 'WASHING',  FALSE, NULL,              5, ''),
    ('sm-0009-6', 'ten-demo-001', 'smp-seed-0009', 'KIRIM',    FALSE, NULL,              6, ''),
    ('sm-0009-7', 'ten-demo-001', 'smp-seed-0009', 'HPP',      FALSE, NULL,              7, ''),
    -- SPK-SMP-0010: linking beres, finishing dicicil
    ('sm-0010-1', 'ten-demo-001', 'smp-seed-0010', 'PROGRAM',  TRUE,  CURRENT_DATE - 12, 1, 'Program grading S/M/L'),
    ('sm-0010-2', 'ten-demo-001', 'smp-seed-0010', 'RAJUT',    TRUE,  CURRENT_DATE - 8,  2, '6 set panel selesai'),
    ('sm-0010-3', 'ten-demo-001', 'smp-seed-0010', 'PROSES_TAMBAHAN', TRUE, CURRENT_DATE - 6, 3, 'Kancing terpasang'),
    ('sm-0010-4', 'ten-demo-001', 'smp-seed-0010', 'LINKING',  TRUE,  CURRENT_DATE - 4,  4, 'Linking internal'),
    ('sm-0010-5', 'ten-demo-001', 'smp-seed-0010', 'WASHING',  FALSE, NULL,              5, 'Steam menyusul setelah sisa 2 pcs beres'),
    ('sm-0010-6', 'ten-demo-001', 'smp-seed-0010', 'KIRIM',    FALSE, NULL,              6, ''),
    ('sm-0010-7', 'ten-demo-001', 'smp-seed-0010', 'HPP',      FALSE, NULL,              7, ''),
    -- SPK-SMP-0011: finishing tuntas, tinggal QC
    ('sm-0011-1', 'ten-demo-001', 'smp-seed-0011', 'PROGRAM',  TRUE,  CURRENT_DATE - 13, 1, 'Program 7GG selesai'),
    ('sm-0011-2', 'ten-demo-001', 'smp-seed-0011', 'RAJUT',    TRUE,  CURRENT_DATE - 9,  2, '3 set panel selesai'),
    ('sm-0011-3', 'ten-demo-001', 'smp-seed-0011', 'PROSES_TAMBAHAN', TRUE, CURRENT_DATE - 7, 3, 'Obras rapi'),
    ('sm-0011-4', 'ten-demo-001', 'smp-seed-0011', 'LINKING',  TRUE,  CURRENT_DATE - 3,  4, 'Linking internal'),
    ('sm-0011-5', 'ten-demo-001', 'smp-seed-0011', 'WASHING',  TRUE,  CURRENT_DATE - 1,  5, 'Steam + gantung'),
    ('sm-0011-6', 'ten-demo-001', 'smp-seed-0011', 'KIRIM',    FALSE, NULL,              6, 'Menunggu QC'),
    ('sm-0011-7', 'ten-demo-001', 'smp-seed-0011', 'HPP',      FALSE, NULL,              7, ''),
    -- SPK-SMP-0012: linking berjalan, sisa 1 pcs
    ('sm-0012-1', 'ten-demo-001', 'smp-seed-0012', 'PROGRAM',  TRUE,  CURRENT_DATE - 6,  1, 'Program cable 12GG'),
    ('sm-0012-2', 'ten-demo-001', 'smp-seed-0012', 'RAJUT',    TRUE,  CURRENT_DATE - 3,  2, '2 set panel selesai'),
    ('sm-0012-3', 'ten-demo-001', 'smp-seed-0012', 'PROSES_TAMBAHAN', FALSE, NULL,       3, 'Saku tempel menyusul'),
    ('sm-0012-4', 'ten-demo-001', 'smp-seed-0012', 'LINKING',  FALSE, NULL,              4, '1 pcs beres, 1 pcs berjalan'),
    ('sm-0012-5', 'ten-demo-001', 'smp-seed-0012', 'WASHING',  FALSE, NULL,              5, ''),
    ('sm-0012-6', 'ten-demo-001', 'smp-seed-0012', 'KIRIM',    FALSE, NULL,              6, ''),
    ('sm-0012-7', 'ten-demo-001', 'smp-seed-0012', 'HPP',      FALSE, NULL,              7, '')
ON CONFLICT (id) DO NOTHING;


-- 5. Setoran finishing internal (inti antrean divisi finishing)
--    Sengaja tidak ada setoran untuk SPK-SMP-0009: kartu "0 dari 4 pcs" adalah
--    state kosong yang juga perlu terlihat di layar operator.
INSERT INTO sampling_finishing_deposits (id, tenant_id, sampling_order_id, deposit_date,
                                         qty_pcs, weight_kg, scale_photo_key, garment_photo_key,
                                         operator_name, notes, created_at)
VALUES
    -- SPK-SMP-0010 → 2 + 2 = 4 dari 6 pcs
    ('fd-seed-0010-1', 'ten-demo-001', 'smp-seed-0010', CURRENT_DATE - 3,
     2, 0.86, 'finishing/smp-seed-0010/timbangan-01.jpg', 'finishing/smp-seed-0010/baju-01.jpg',
     'Siti Rohmah', 'Ukuran S selesai linking + pasang kancing.',
     CURRENT_TIMESTAMP - INTERVAL '3 days'),
    ('fd-seed-0010-2', 'ten-demo-001', 'smp-seed-0010', CURRENT_DATE - 1,
     2, 0.92, 'finishing/smp-seed-0010/timbangan-02.jpg', NULL,
     'Siti Rohmah', 'Ukuran M selesai; sisa L menunggu obras rib pinggang.',
     CURRENT_TIMESTAMP - INTERVAL '1 day'),
    -- SPK-SMP-0011 → 1 + 2 = 3 dari 3 pcs (tuntas)
    ('fd-seed-0011-1', 'ten-demo-001', 'smp-seed-0011', CURRENT_DATE - 2,
     1, 0.63, 'finishing/smp-seed-0011/timbangan-01.jpg', 'finishing/smp-seed-0011/baju-01.jpg',
     'Yanto Suryana', 'Sampel pertama untuk cek berat vs gramasi panel.',
     CURRENT_TIMESTAMP - INTERVAL '2 days'),
    ('fd-seed-0011-2', 'ten-demo-001', 'smp-seed-0011', CURRENT_DATE,
     2, 1.28, 'finishing/smp-seed-0011/timbangan-02.jpg', 'finishing/smp-seed-0011/baju-02.jpg',
     'Yanto Suryana', 'Dua sisanya beres; sudah steam dan digantung untuk QC.',
     CURRENT_TIMESTAMP - INTERVAL '5 hours'),
    -- SPK-SMP-0012 → 1 dari 2 pcs
    ('fd-seed-0012-1', 'ten-demo-001', 'smp-seed-0012', CURRENT_DATE - 1,
     1, 0.71, 'finishing/smp-seed-0012/timbangan-01.jpg', 'finishing/smp-seed-0012/baju-01.jpg',
     'Siti Rohmah', 'Cable depan agak melar 0.5 cm — sudah disetel ulang saat linking.',
     CURRENT_TIMESTAMP - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;
