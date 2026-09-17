-- ==============================================================================
-- WeMade ERP — Seed: Won Deals & SPK Sampling Terhubung (V43)
-- ==============================================================================
-- Data demo realistis untuk tenant ten-demo-001 (PT WeMade Garmen Ekspor):
--   1. 3 kontak pelanggan (crm_contacts) — prasyarat FK tabel deals.
--   2. 3 deal penjualan yang SUDAH MELEWATI deal (stage = 'WON').
--   3. 4 SPK sampling (sampling_orders) yang menempel pada deal-deal tersebut
--      via deal_id, dengan variasi status & tahap pipeline agar kanban modul
--      sampling terisi representatif:
--        SPK-SMP-0005  IN_PROGRESS  @ MACHINE_KNITTING   (deal-seed-001)
--        SPK-SMP-0006  ACC_APPROVED @ ACC_APPROVED       (deal-seed-001, golden sample)
--        SPK-SMP-0007  IN_PROGRESS  @ LINKING_ASSEMBLY   (deal-seed-002, jalur makloon vendor)
--        SPK-SMP-0008  REVISION     @ FINISHING_QC       (deal-seed-003, multi-size)
--
-- PENOMORAN: PostgresSamplingOrderRepository.nextSpkNumber() = COUNT(*) + 1.
-- Database dev sudah memuat SPK-SMP-0001..0004, sehingga seed mulai dari 0005
-- agar nomor berikutnya yang diterbitkan aplikasi (0009) tidak bentrok dengan
-- constraint UNIQUE (tenant_id, spk_number).
--
-- Semua format JSONB (size_matrix, feeder_instructions, tenselity_entries,
-- pattern_formulas, panel_weights_grams, panel_knitting_minutes) mengikuti
-- persis encoder/decoder PostgresSamplingOrderRepository & SamplingOrderCodec
-- agar tidak ada data yang gagal di-decode diam-diam oleh server maupun klien.
-- ==============================================================================

-- 1. Kontak pelanggan (satu nomor telepon = satu pelanggan per tenant)
INSERT INTO crm_contacts (id, tenant_id, name, brand_name, phone, email, address, created_at, updated_at)
VALUES
    ('con-seed-001', 'ten-demo-001', 'Rina Kusumawati', 'BKG Apparel', '6281200001001',
     'rina@bkgapparel.id', 'Jl. Riau No. 21, Bandung', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('con-seed-002', 'ten-demo-001', 'Budi Santoso', 'Sejahtera Style', '6281200002002',
     'budi@sejahterastyle.co.id', 'Jl. Merdeka No. 88, Semarang', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('con-seed-003', 'ten-demo-001', 'Ayu Lestari', 'Nuansa Wear', '6281200003003',
     'ayu@nuansawear.id', 'Jl. Kaliurang KM 5, Yogyakarta', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 2. Deal penjualan yang sudah melewati deal (WON)
INSERT INTO deals (id, tenant_id, contact_id, title, stage, estimated_value_idr,
                   expected_close_date, notes, created_by_user_id, created_at, updated_at)
VALUES
    ('deal-seed-001', 'ten-demo-001', 'con-seed-001',
     'PO Cardigan Rajut BKG — 500 pcs Kombinasi Abu', 'WON', 87500000,
     CURRENT_DATE - 5, 'DP 50% diterima; golden sample ACC menjadi acuan produksi massal.',
     'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '5 days'),
    ('deal-seed-002', 'ten-demo-001', 'con-seed-002',
     'PO Sweater Rajut Sejahtera — 300 pcs Highneck Polos', 'WON', 52000000,
     CURRENT_DATE - 2, 'Sampling tahap linking di vendor makloon; jadwal kirim produksi bulan depan.',
     'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '25 days', CURRENT_TIMESTAMP - INTERVAL '2 days'),
    ('deal-seed-003', 'ten-demo-001', 'con-seed-003',
     'PO Outer Rajut Nuansa — 150 pcs Multi-Size', 'WON', 45500000,
     CURRENT_DATE - 1, 'Sample revisi 1: peregangan bahu dikurangi 1 cm sesuai feedback buyer.',
     'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;


-- 3. SPK Sampling terhubung ke deal (kolom anak mengikuti skema V23/V35/V38/V40/V41)

-- 3a. SPK-SMP-0005 — sedang rajut turun mesin
INSERT INTO sampling_orders (id, tenant_id, spk_number, client_name, style_name, status,
                             pipeline_stage, finishing_path, size_mode,
                             deadline_program, deadline_finishing, deadline_delivery,
                             deal_id, sample_quantity, sampling_fee_idr,
                             revision_count, revision_history, size_matrix,
                             acc_notes, notes, created_by_user_id, created_at, updated_at)
VALUES ('smp-seed-0005', 'ten-demo-001', 'SPK-SMP-0005', 'BKG Apparel', 'Cardigan Rajut Kombinasi Abu',
        'IN_PROGRESS', 'MACHINE_KNITTING', 'INTERNAL', 'ALL_SIZE',
        CURRENT_DATE + 3, CURRENT_DATE + 12, CURRENT_DATE + 15,
        'deal-seed-001', 2, 750000,
        0, '[]',
        '[
           {"id":"sampling_qty_row","pomName":"Jumlah Sampel (pcs)","values":{"ALL SIZE":"2","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_lebar_dada","pomName":"Lebar Dada","values":{"ALL SIZE":"56","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_panjang_baju","pomName":"Panjang Baju","values":{"ALL SIZE":"62","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}}
         ]'::jsonb,
        '', 'Prioritas mesin 3 feeder stripe; program CAM sudah validasi kepala divisi sampling.',
        'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;

-- 3b. SPK-SMP-0006 — ACC produksi (golden sample deal 001, gerbang Tab Produksi Massal)
INSERT INTO sampling_orders (id, tenant_id, spk_number, client_name, style_name, status,
                             pipeline_stage, finishing_path, size_mode,
                             deadline_program, deadline_finishing, deadline_delivery,
                             deal_id, sample_quantity, sampling_fee_idr, courier_tracking,
                             revision_count, revision_history, size_matrix,
                             acc_notes, notes, created_by_user_id, created_at, updated_at)
VALUES ('smp-seed-0006', 'ten-demo-001', 'SPK-SMP-0006', 'BKG Apparel', 'Cardigan Rajut Polos Abu',
        'ACC_APPROVED', 'ACC_APPROVED', 'INTERNAL', 'ALL_SIZE',
        CURRENT_DATE - 10, CURRENT_DATE - 2, CURRENT_DATE - 1,
        'deal-seed-001', 2, 750000, 'JNE-8811223344',
        0, '[]',
        '[
           {"id":"sampling_qty_row","pomName":"Jumlah Sampel (pcs)","values":{"ALL SIZE":"2","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_lebar_dada","pomName":"Lebar Dada","values":{"ALL SIZE":"55","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_panjang_baju","pomName":"Panjang Baju","values":{"ALL SIZE":"60","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}}
         ]'::jsonb,
        'ACC oleh buyer; sampel arsip disimpan di rak golden sample.',
        'Sudah diterima buyer di Bandung; produksi massal boleh diluncurkan dari Tab Produksi.',
        'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '6 days')
ON CONFLICT (id) DO NOTHING;


-- 3c. SPK-SMP-0007 — linking di jalur makloon vendor (Pak Asep)
INSERT INTO sampling_orders (id, tenant_id, spk_number, client_name, style_name, status,
                             pipeline_stage, finishing_path, size_mode,
                             deadline_program, deadline_finishing, deadline_delivery,
                             deal_id, sample_quantity, sampling_fee_idr,
                             vendor_name, vendor_phone, vendor_sent_at, vendor_target_at,
                             vendor_cost_per_pcs, vendor_status, vendor_notes,
                             revision_count, revision_history, size_matrix,
                             acc_notes, notes, created_by_user_id, created_at, updated_at)
VALUES ('smp-seed-0007', 'ten-demo-001', 'SPK-SMP-0007', 'Sejahtera Style', 'Sweater Rajut Highneck Polos',
        'IN_PROGRESS', 'LINKING_ASSEMBLY', 'MAKLOON_VENDOR', 'ALL_SIZE',
        CURRENT_DATE + 5, CURRENT_DATE + 14, CURRENT_DATE + 18,
        'deal-seed-002', 3, 900000,
        'Pak Asep — Makloon Linking Cimahi', '6281312345678', CURRENT_DATE - 3, CURRENT_DATE + 2,
        15000, 'WITH_VENDOR', 'Hubungi Pak Asep H-1 sebelum ambil; bawa benang cadangan warna BW.',
        0, '[]',
        '[
           {"id":"sampling_qty_row","pomName":"Jumlah Sampel (pcs)","values":{"ALL SIZE":"3","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_lebar_dada","pomName":"Lebar Dada","values":{"ALL SIZE":"58","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_panjang_baju","pomName":"Panjang Baju","values":{"ALL SIZE":"66","S":"","M":"","L":"","XL":"","XXL":"","XXXL":""}}
         ]'::jsonb,
        '', 'Kerah highneck dirajut terpisah; kalau vendor telat, linking kerah diambil alih internal.',
        'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '3 days')
ON CONFLICT (id) DO NOTHING;

-- 3d. SPK-SMP-0008 — revisi sample multi-size (grading S-XL)
INSERT INTO sampling_orders (id, tenant_id, spk_number, client_name, style_name, status,
                             pipeline_stage, finishing_path, size_mode,
                             deadline_program, deadline_finishing, deadline_delivery,
                             deal_id, sample_quantity, sampling_fee_idr,
                             revision_count, revision_history, size_matrix,
                             acc_notes, notes, created_by_user_id, created_at, updated_at)
VALUES ('smp-seed-0008', 'ten-demo-001', 'SPK-SMP-0008', 'Nuansa Wear', 'Outer Rajut Panjang Belted',
        'REVISION', 'FINISHING_QC', 'INTERNAL', 'MULTI_SIZE',
        CURRENT_DATE + 2, CURRENT_DATE + 9, CURRENT_DATE + 11,
        'deal-seed-003', 3, 1200000,
        1,
        jsonb_build_array(jsonb_build_object(
            'revision', 1,
            'notes', 'Peregangan bahu dikurangi 1 cm; panjang badan +2 cm untuk ukuran L & XL.',
            'at', TO_CHAR(CURRENT_TIMESTAMP - INTERVAL '4 days', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'))),
        '[
           {"id":"sampling_qty_row","pomName":"Jumlah Sampel (pcs)","values":{"ALL SIZE":"","S":"1","M":"1","L":"1","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_lebar_dada","pomName":"Lebar Dada","values":{"ALL SIZE":"","S":"48","M":"50","L":"52","XL":"","XXL":"","XXXL":""}},
           {"id":"pom_panjang_baju","pomName":"Panjang Baju","values":{"ALL SIZE":"","S":"70","M":"72","L":"75","XL":"","XXL":"","XXXL":""}}
         ]'::jsonb,
        'Menunggu verifikasi QC setelah rework bahu; jangan ACC sebelum buyer approve Rev 1.',
        'Buyer minta foto setrika uap sebelum kirim; gunakan hanger kayu untuk foto produk.',
        'usr-owner-001', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '4 days')
ON CONFLICT (id) DO NOTHING;


-- 4. Spesifikasi rajut (Knit Spec) per SPK
INSERT INTO sampling_knit_specs (id, tenant_id, sampling_order_id, yarn_type, knit_type,
                                 rib_spec, collar_spec, placket_spec, colorway_notes,
                                 mockup_image_urls, created_at, updated_at)
VALUES
    ('ks-smp-seed-0005', 'ten-demo-001', 'smp-seed-0005',
     'Viscose 30% + Akilik 70% (2/32 NM)', 'Flat Knitting 12GG — Half Cardigan Stripe',
     'Rib 2x1 lebar 3 cm', 'Round neck rib 2.5 cm', 'Placket kancing 5 butir, lebar 2.8 cm',
     'Body kombinasi Abu Tua + Abu Muda; rib & kerah Hitam BW.',
     '[]'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ks-smp-seed-0006', 'ten-demo-001', 'smp-seed-0006',
     'Viscose 30% + Akilik 70% (2/32 NM)', 'Flat Knitting 12GG — Plain Polos',
     'Rib 2x1 lebar 3 cm', 'Round neck rib 2.5 cm', 'Placket kancing 5 butir, lebar 2.8 cm',
     'Polos Abu Muda full body; benang sama dengan SPK-SMP-0005 (satu PO).',
     '[]'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ks-smp-seed-0007', 'ten-demo-001', 'smp-seed-0007',
     'Wool Blend 20% + Akilik 80% (2/28 NM)', 'Flat Knitting 7GG — Highneck Solid',
     'Rib 2x1 lebar 4 cm', 'Highneck rib ganda 8 cm', 'Tanpa placket (pullover)',
     'Solid Mocca; feeder 7 BS POLY untuk stabilisasi bahu.',
     '[]'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ks-smp-seed-0008', 'ten-demo-001', 'smp-seed-0008',
     'Akilik Premium (2/30 NM)', 'Flat Knitting 12GG — Outer Long Cardigan Belted',
     'Rib inggris lebar 4 cm', 'Shawl collar', 'Belt rajut panjang 180 cm',
     'Solid Cream; grading S/M/L mengikuti matriks ukuran Rev 1.',
     '[]'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 5. Size chart ukuran jadi vs ukuran rajut mentah mesin
INSERT INTO sampling_size_charts (id, tenant_id, sampling_order_id, category, size_label,
                                  body_length, body_width, sleeve_length, arm_hole, neck_drop,
                                  neck_width, shoulder_width, rib_height, collar_height,
                                  placket_width, sleeve_opening, created_at)
VALUES
    ('sc-0005-fin', 'ten-demo-001', 'smp-seed-0005', 'FINISHED_SIZE', 'ALL SIZE',
     62.0, 56.0, 60.0, 25.0, 6.0, 19.0, 43.0, 10.0, 3.0, 2.8, 10.0, CURRENT_TIMESTAMP),
    ('sc-0005-raw', 'ten-demo-001', 'smp-seed-0005', 'KNIT_RAW_SIZE', 'ALL SIZE',
     57.0, 57.0, 55.0, 25.0, 6.0, 19.0, 43.0, 10.0, 6.0, 2.8, 10.0, CURRENT_TIMESTAMP),
    ('sc-0006-fin', 'ten-demo-001', 'smp-seed-0006', 'FINISHED_SIZE', 'ALL SIZE',
     60.0, 55.0, 59.0, 25.0, 6.0, 19.0, 43.0, 10.0, 3.0, 2.8, 10.0, CURRENT_TIMESTAMP),
    ('sc-0006-raw', 'ten-demo-001', 'smp-seed-0006', 'KNIT_RAW_SIZE', 'ALL SIZE',
     55.0, 56.0, 54.0, 25.0, 6.0, 19.0, 43.0, 10.0, 6.0, 2.8, 10.0, CURRENT_TIMESTAMP),
    ('sc-0007-fin', 'ten-demo-001', 'smp-seed-0007', 'FINISHED_SIZE', 'ALL SIZE',
     66.0, 58.0, 62.0, 27.0, 4.0, 20.0, 45.0, 11.0, 8.0, 0.0, 11.0, CURRENT_TIMESTAMP),
    ('sc-0007-raw', 'ten-demo-001', 'smp-seed-0007', 'KNIT_RAW_SIZE', 'ALL SIZE',
     61.0, 59.0, 57.0, 27.0, 4.0, 20.0, 45.0, 11.0, 10.0, 0.0, 11.0, CURRENT_TIMESTAMP),
    ('sc-0008-fin-s', 'ten-demo-001', 'smp-seed-0008', 'FINISHED_SIZE', 'S',
     70.0, 48.0, 58.0, 23.0, 5.0, 18.0, 40.0, 10.0, 0.0, 0.0, 9.0, CURRENT_TIMESTAMP),
    ('sc-0008-fin-m', 'ten-demo-001', 'smp-seed-0008', 'FINISHED_SIZE', 'M',
     72.0, 50.0, 59.0, 24.0, 5.0, 18.5, 41.5, 10.0, 0.0, 0.0, 9.5, CURRENT_TIMESTAMP),
    ('sc-0008-fin-l', 'ten-demo-001', 'smp-seed-0008', 'FINISHED_SIZE', 'L',
     75.0, 52.0, 60.0, 25.0, 5.0, 19.0, 43.0, 10.0, 0.0, 0.0, 10.0, CURRENT_TIMESTAMP),
    ('sc-0008-raw-m', 'ten-demo-001', 'smp-seed-0008', 'KNIT_RAW_SIZE', 'M',
     67.0, 51.0, 54.0, 24.0, 5.0, 18.5, 41.5, 10.0, 0.0, 0.0, 9.5, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 6. Program mesin CAM + feeder + tenselity (mengikuti FactorySizePresets)
INSERT INTO sampling_machine_programs (id, tenant_id, sampling_order_id,
                                       program_front, program_back, program_sleeve,
                                       program_collar, program_placket,
                                       feeder_instructions, pattern_formulas,
                                       tension_settings, tenselity_entries,
                                       created_at, updated_at)
VALUES
    ('mp-smp-seed-0005', 'ten-demo-001', 'smp-seed-0005',
     'BIAN-D5-KOMBINASI', 'BIAN-B5-KOMBINASI', 'BIAN-D5-LGN-KMB', 'RIB-BULAT-25', 'PLACKET-KMB',
     '[{"feederNumber":1,"name":"RIB STRIPE","ply":"1 PLAY","color":"HITAM"},
       {"feederNumber":2,"name":"RIB","ply":"1 PLAY","color":"BW"},
       {"feederNumber":3,"name":"DASAR","ply":"1 PLAY","color":"BW"},
       {"feederNumber":4,"name":"STRIPE","ply":"1 PLAY","color":"M71"},
       {"feederNumber":5,"name":"STRIPE","ply":"1 PLAY","color":"DARK GREY"},
       {"feederNumber":6,"name":"RIB","ply":"1 PLAY","color":"HITAM"},
       {"feederNumber":7,"name":"BS POLY","ply":"-","color":"-"}]'::jsonb,
     '{"bodyLengthK":2.94,"bodyWidthN":6.6,"ribK":4.7}'::jsonb,
     '{"rib":"Sedang","body":"Lembut","stripes":"Kencang"}'::jsonb,
     '[{"parameter":"1 BS POLY","body":"","sleeve":"","collar":""},
       {"parameter":"2 BS TARIK","body":"","sleeve":"","collar":""},
       {"parameter":"3 BS TARIK","body":"","sleeve":"","collar":""},
       {"parameter":"4 SILANG","body":"","sleeve":"","collar":""},
       {"parameter":"5 SILANG","body":"","sleeve":"","collar":""},
       {"parameter":"6 RIB","body":"","sleeve":"","collar":""},
       {"parameter":"7 TIF RIB","body":"","sleeve":"","collar":""},
       {"parameter":"8 PRODUKSI","body":"","sleeve":"","collar":""}]'::jsonb,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('mp-smp-seed-0006', 'ten-demo-001', 'smp-seed-0006',
     'BIAN-D5-POLOS', 'BIAN-B5-POLOS', 'BIAN-D5-LGN-PLS', 'RIB-BULAT-25', 'PLACKET-PLS',
     '[{"feederNumber":1,"name":"RIB","ply":"1 PLAY","color":"BW"},
       {"feederNumber":2,"name":"DASAR","ply":"1 PLAY","color":"BW"},
       {"feederNumber":3,"name":"DASAR","ply":"1 PLAY","color":"BW"},
       {"feederNumber":4,"name":"DASAR","ply":"1 PLAY","color":"BW"},
       {"feederNumber":5,"name":"BS POLY","ply":"-","color":"-"},
       {"feederNumber":6,"name":"RIB","ply":"1 PLAY","color":"BW"},
       {"feederNumber":7,"name":"BS POLY","ply":"-","color":"-"}]'::jsonb,
     '{"bodyLengthK":2.94,"bodyWidthN":6.6,"ribK":4.7}'::jsonb,
     '{"rib":"Sedang","body":"Lembut"}'::jsonb,
     '[{"parameter":"1 BS POLY","body":"","sleeve":"","collar":""},
       {"parameter":"2 BS TARIK","body":"","sleeve":"","collar":""},
       {"parameter":"3 BS TARIK","body":"","sleeve":"","collar":""},
       {"parameter":"4 SILANG","body":"","sleeve":"","collar":""},
       {"parameter":"5 SILANG","body":"","sleeve":"","collar":""},
       {"parameter":"6 RIB","body":"","sleeve":"","collar":""},
       {"parameter":"8 PRODUKSI","body":"","sleeve":"","collar":""}]'::jsonb,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('mp-smp-seed-0007', 'ten-demo-001', 'smp-seed-0007',
     'BIAN-D7-HIGHNECK', 'BIAN-B7-HIGHNECK', 'BIAN-D7-LGN-HN', 'HIGHNECK-RIB8', '-',
     '[{"feederNumber":1,"name":"RIB","ply":"1 PLAY","color":"MOCCA"},
       {"feederNumber":2,"name":"DASAR","ply":"1 PLAY","color":"MOCCA"},
       {"feederNumber":3,"name":"DASAR","ply":"1 PLAY","color":"MOCCA"},
       {"feederNumber":4,"name":"DASAR","ply":"1 PLAY","color":"MOCCA"},
       {"feederNumber":5,"name":"BS POLY","ply":"-","color":"-"},
       {"feederNumber":6,"name":"RIB","ply":"1 PLAY","color":"MOCCA"},
       {"feederNumber":7,"name":"BS POLY","ply":"-","color":"-"}]'::jsonb,
     '{"bodyLengthK":3.1,"bodyWidthN":7.0,"ribK":5.0}'::jsonb,
     '{"body":"Lembut","highneck":"Kencang"}'::jsonb,
     '[{"parameter":"1 BS POLY","body":"","sleeve":"","collar":""},
       {"parameter":"2 BS TARIK","body":"","sleeve":"","collar":""},
       {"parameter":"4 SILANG","body":"","sleeve":"","collar":""},
       {"parameter":"6 RIB","body":"","sleeve":"","collar":""},
       {"parameter":"8 PRODUKSI","body":"","sleeve":"","collar":""}]'::jsonb,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('mp-smp-seed-0008', 'ten-demo-001', 'smp-seed-0008',
     'BIAN-D12-OUTER-M', 'BIAN-B12-OUTER-M', 'BIAN-D12-LGN-OT', 'SHAWL-RIB', 'BELT-RIBUT',
     '[{"feederNumber":1,"name":"RIB","ply":"1 PLAY","color":"CREAM"},
       {"feederNumber":2,"name":"DASAR","ply":"1 PLAY","color":"CREAM"},
       {"feederNumber":3,"name":"DASAR","ply":"1 PLAY","color":"CREAM"},
       {"feederNumber":4,"name":"DASAR","ply":"1 PLAY","color":"CREAM"},
       {"feederNumber":5,"name":"BS POLY","ply":"-","color":"-"},
       {"feederNumber":6,"name":"RIB INGGRIS","ply":"1 PLAY","color":"CREAM"},
       {"feederNumber":7,"name":"BS POLY","ply":"-","color":"-"}]'::jsonb,
     '{"bodyLengthK":3.0,"bodyWidthN":6.8,"ribK":4.9}'::jsonb,
     '{"body":"Sedang","ribInggris":"Kencang"}'::jsonb,
     '[{"parameter":"1 BS POLY","body":"","sleeve":"","collar":""},
       {"parameter":"2 BS TARIK","body":"","sleeve":"","collar":""},
       {"parameter":"4 SILANG","body":"","sleeve":"","collar":""},
       {"parameter":"6 RIB","body":"","sleeve":"","collar":""},
       {"parameter":"8 PRODUKSI","body":"","sleeve":"","collar":""},
       {"parameter":"16 BS WARNA","body":"","sleeve":"","collar":""}]'::jsonb,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;


-- 7. Yield & timing (gramasi panel + menit rajut)
INSERT INTO sampling_yield_timings (id, tenant_id, sampling_order_id,
                                    panel_weights_grams, panel_knitting_minutes,
                                    linking_notes, additional_process, is_washed,
                                    estimated_hpp_idr, created_at, updated_at)
VALUES
    ('yt-smp-seed-0005', 'ten-demo-001', 'smp-seed-0005',
     '{"front":215.0,"back":205.0,"sleeve":90.0,"collar":35.0,"placket":25.0}'::jsonb,
     '{"front":28,"back":26,"sleeve":14,"collar":6,"placket":5}'::jsonb,
     'Linking kombinasi warna perlu marker stripe agar garis sejajar.',
     'Pasang Kancing', FALSE, 152000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('yt-smp-seed-0006', 'ten-demo-001', 'smp-seed-0006',
     '{"front":210.0,"back":200.0,"sleeve":88.0,"collar":34.0,"placket":24.0}'::jsonb,
     '{"front":26,"back":24,"sleeve":13,"collar":6,"placket":5}'::jsonb,
     'Linking polos standar; tidak ada marker warna.',
     'Pasang Kancing', TRUE, 148000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('yt-smp-seed-0007', 'ten-demo-001', 'smp-seed-0007',
     '{"front":320.0,"back":310.0,"sleeve":150.0,"collar":60.0,"placket":0.0}'::jsonb,
     '{"front":42,"back":40,"sleeve":24,"collar":12,"placket":0}'::jsonb,
     'Linking 7GG oleh vendor; beri instruksi kekuatan jahit mata rantai.',
     'Steam Highneck', TRUE, 245000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('yt-smp-seed-0008', 'ten-demo-001', 'smp-seed-0008',
     '{"front":290.0,"back":280.0,"sleeve":130.0,"collar":70.0,"placket":45.0}'::jsonb,
     '{"front":38,"back":36,"sleeve":22,"collar":14,"placket":10}'::jsonb,
     'Shawl collar dirajut menyatu; belt dipisah lalu dijepit jahit mati.',
     'Pasang Belt + Steam', TRUE, 268000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 8. Milestone tracker per SPK (progress selaras dengan pipeline_stage)
INSERT INTO sampling_milestones (id, tenant_id, sampling_order_id, step_name,
                                 is_completed, completed_at, step_order, notes)
VALUES
    -- SPK-SMP-0005: rajut turun mesin (PROGRAM selesai, RAJUT berjalan)
    ('sm-0005-1', 'ten-demo-001', 'smp-seed-0005', 'PROGRAM',  TRUE,  CURRENT_DATE - 8, 1, 'Program CAM validasi OK'),
    ('sm-0005-2', 'ten-demo-001', 'smp-seed-0005', 'RAJUT',    FALSE, NULL,             2, 'Panel depan selesai; belakang & lengan berjalan'),
    ('sm-0005-3', 'ten-demo-001', 'smp-seed-0005', 'PROSES_TAMBAHAN', FALSE, NULL, 3, ''),
    ('sm-0005-4', 'ten-demo-001', 'smp-seed-0005', 'LINKING',  FALSE, NULL,             4, ''),
    ('sm-0005-5', 'ten-demo-001', 'smp-seed-0005', 'WASHING',  FALSE, NULL,             5, ''),
    ('sm-0005-6', 'ten-demo-001', 'smp-seed-0005', 'KIRIM',    FALSE, NULL,             6, ''),
    ('sm-0005-7', 'ten-demo-001', 'smp-seed-0005', 'HPP',      FALSE, NULL,             7, ''),
    -- SPK-SMP-0006: ACC produksi (semua tahap selesai)
    ('sm-0006-1', 'ten-demo-001', 'smp-seed-0006', 'PROGRAM',  TRUE,  CURRENT_DATE - 16, 1, 'Program CAM validasi OK'),
    ('sm-0006-2', 'ten-demo-001', 'smp-seed-0006', 'RAJUT',    TRUE,  CURRENT_DATE - 13, 2, '4 panel selesai'),
    ('sm-0006-3', 'ten-demo-001', 'smp-seed-0006', 'PROSES_TAMBAHAN', TRUE, CURRENT_DATE - 11, 3, 'Kancing dijahit mati'),
    ('sm-0006-4', 'ten-demo-001', 'smp-seed-0006', 'LINKING',  TRUE,  CURRENT_DATE - 9,  4, 'Linking internal'),
    ('sm-0006-5', 'ten-demo-001', 'smp-seed-0006', 'WASHING',  TRUE,  CURRENT_DATE - 8,  5, 'Steam finishing'),
    ('sm-0006-6', 'ten-demo-001', 'smp-seed-0006', 'KIRIM',    TRUE,  CURRENT_DATE - 6,  6, 'Dikirim JNE ke buyer Bandung'),
    ('sm-0006-7', 'ten-demo-001', 'smp-seed-0006', 'HPP',      TRUE,  CURRENT_DATE - 5,  7, 'HPP final terkunci'),
    -- SPK-SMP-0007: linking di vendor (PROGRAM..RAJUT selesai)
    ('sm-0007-1', 'ten-demo-001', 'smp-seed-0007', 'PROGRAM',  TRUE,  CURRENT_DATE - 7, 1, 'Program 7GG selesai'),
    ('sm-0007-2', 'ten-demo-001', 'smp-seed-0007', 'RAJUT',    TRUE,  CURRENT_DATE - 4, 2, 'Panel selesai semua'),
    ('sm-0007-3', 'ten-demo-001', 'smp-seed-0007', 'PROSES_TAMBAHAN', FALSE, NULL, 3, 'Menunggu linking vendor dulu'),
    ('sm-0007-4', 'ten-demo-001', 'smp-seed-0007', 'LINKING',  FALSE, NULL,             4, 'Sedang di Pak Asep — target kembali H+2'),
    ('sm-0007-5', 'ten-demo-001', 'smp-seed-0007', 'WASHING',  FALSE, NULL,             5, ''),
    ('sm-0007-6', 'ten-demo-001', 'smp-seed-0007', 'KIRIM',    FALSE, NULL,             6, ''),
    ('sm-0007-7', 'ten-demo-001', 'smp-seed-0007', 'HPP',      FALSE, NULL,             7, ''),
    -- SPK-SMP-0008: QC pasca rework (PROGRAM..WASHING selesai, KIRIM/HPP menunggu)
    ('sm-0008-1', 'ten-demo-001', 'smp-seed-0008', 'PROGRAM',  TRUE,  CURRENT_DATE - 12, 1, 'Program grading S/M/L'),
    ('sm-0008-2', 'ten-demo-001', 'smp-seed-0008', 'RAJUT',    TRUE,  CURRENT_DATE - 9,  2, '3 pcs multi-size selesai'),
    ('sm-0008-3', 'ten-demo-001', 'smp-seed-0008', 'PROSES_TAMBAHAN', TRUE, CURRENT_DATE - 7, 3, 'Belt terpasang'),
    ('sm-0008-4', 'ten-demo-001', 'smp-seed-0008', 'LINKING',  TRUE,  CURRENT_DATE - 6,  4, 'Linking internal'),
    ('sm-0008-5', 'ten-demo-001', 'smp-seed-0008', 'WASHING',  TRUE,  CURRENT_DATE - 5,  5, 'Steam + foto produk'),
    ('sm-0008-6', 'ten-demo-001', 'smp-seed-0008', 'KIRIM',    FALSE, NULL,             6, 'Menunggu QC rework bahu'),
    ('sm-0008-7', 'ten-demo-001', 'smp-seed-0008', 'HPP',      FALSE, NULL,             7, '')
ON CONFLICT (id) DO NOTHING;

