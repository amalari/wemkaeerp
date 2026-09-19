-- ==============================================================================
-- WeMade ERP — V49: Sinkronisasi Stage Deal dengan Progres Nyata Sampling
-- ==============================================================================
-- Memperbaiki inkonsistensi data seed V43 & V44 di mana deal-deal bernilai transaksi
-- (deal-seed-001 s/d 004) langsung dilabeli 'WON' (Dimenangkan) secara prematur,
-- padahal SPK sampling-nya masih aktif berjalan di lantai pabrik:
--   1. deal-seed-001 (BKG Apparel): DSG-02 (SPK-SMP-0005) masih di meja rajut.
--   2. deal-seed-002 (Sejahtera Style): SPK-SMP-0007 masih di vendor makloon linking.
--   3. deal-seed-003 (Nuansa Wear): SPK-SMP-0008 masih revisi di finishing QC.
--   4. deal-seed-004 (Kanva Knit): SPK-SMP-0009 & 0010 masih di linking & steaming.
--
-- Perubahan:
-- Mengubah stage deal tersebut menjadi 'PO_RECEIVED' (Label UI: 'Sampling' / Oranye),
-- sehingga status kanban deals, mini-stepper, dan metrik KPI pipeline di CRM Sales
-- mencerminkan alur bisnis garmen yang akurat dan selaras dengan modul Sampling.
-- ==============================================================================

-- 1. Sinkronisasi deal-seed-001 (BKG Apparel)
UPDATE deals
SET stage = 'PO_RECEIVED',
    notes = 'Sampling berjalan: DSG-01 sudah ACC (Golden Sample), DSG-02 masih proses rajut mesin.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'deal-seed-001';

-- 2. Sinkronisasi deal-seed-002 (Sejahtera Style)
UPDATE deals
SET stage = 'PO_RECEIVED',
    notes = 'Sampling berjalan: tahap linking di vendor makloon Cimahi; jadwal kirim sampel ke buyer minggu ini.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'deal-seed-002';

-- 3. Sinkronisasi deal-seed-003 (Nuansa Wear)
UPDATE deals
SET stage = 'PO_RECEIVED',
    notes = 'Sampling revisi 1: peregangan bahu dikurangi 1 cm sesuai feedback buyer; sedang QC finishing.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'deal-seed-003';

-- 4. Sinkronisasi deal-seed-004 (Kanva Knit)
UPDATE deals
SET stage = 'PO_RECEIVED',
    notes = 'Sampling berjalan: dua sampel colorway berjalan paralel di meja finishing linking & steaming.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'deal-seed-004';
