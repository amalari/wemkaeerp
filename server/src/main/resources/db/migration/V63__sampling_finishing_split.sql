-- ==============================================================================
-- WeMade ERP — Tahap "Finishing & QC" dipecah empat (V63)
-- ==============================================================================
-- Sampai V62, seluruh penyelesaian akhir sampel berdesakan dalam satu tahap:
-- FINISHING_QC. Padahal di lantai sampel, sampel yang sudah selesai linking
-- berpindah tangan tiga kali sebelum siap kirim — pencuci, penyetrika, pemeriksa,
-- pengemas.
--
-- Itu bukan soal kerapian penamaan. `pipeline_stage` dipakai sebagai SIMPUL
-- KUSTODI: FlowNodeRef.Stage membungkusnya jadi simpul alur, dan
-- AdvanceSamplingStageUseCase menolak kenaikan tahap selama leg serah terima
-- menuju simpul itu belum DITERIMA. Selama empat tangan itu dianggap satu simpul,
-- sistem tidak bisa menjawab "sampelnya sekarang di siapa" — pertanyaan yang
-- justru paling sering datang dari buyer lewat sales.
--
-- Empat tahap ini BAWAAN, bukan kunci mati (CLAUDE.md §11). Pabrik dengan ruang
-- sampel lebih ramping menyesuaikan lewat tenant_optional_processes.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Baris lama dipetakan ke sub-tahap PALING AWAL
-- ------------------------------------------------------------------------------
-- Bukan ke QC_FINISHING, walau namanya lebih mirip. Basis data ini tidak pernah
-- menyimpan apakah sampel itu sudah dicuci dan disetrika; memetakannya ke QC
-- berarti menyatakan dua pekerjaan sudah beres tanpa dasar apa pun. Terlihat
-- mundur satu langkah bisa dikoreksi orang dalam sepuluh detik — kemajuan yang
-- diarang membuat orang berhenti mencari barang yang sebenarnya masih di bak cuci.
--
-- Kolomnya VARCHAR(50) polos tanpa CHECK (V41), jadi tidak ada constraint yang
-- perlu ikut diubah.
UPDATE sampling_orders
SET pipeline_stage = 'CUCI_SOFTENER'
WHERE pipeline_stage = 'FINISHING_QC';

-- Konfigurasi lokasi simpul alur ikut dipindahkan, kalau tidak leg serah terima
-- menuju tahap baru kehilangan gedung tujuannya dan gerbang kustodi macet total.
UPDATE tenant_flow_node_locations
SET node_key = 'CUCI_SOFTENER'
WHERE node_kind = 'STAGE'
  AND node_key = 'FINISHING_QC'
  AND NOT EXISTS (
      SELECT 1 FROM tenant_flow_node_locations x
      WHERE x.tenant_id = tenant_flow_node_locations.tenant_id
        AND x.node_kind = 'STAGE'
        AND x.node_key = 'CUCI_SOFTENER'
  );

-- Tiga tahap sisanya diberi pemetaan yang sama dengan tahap asalnya untuk tenant
-- yang sudah memetakan FINISHING_QC. Satu gedung untuk keempatnya berarti tidak
-- ada leg antar-gedung di antara mereka — dan itu memang benar: pemecahan ini soal
-- berpindah tangan, bukan soal berpindah gedung.
INSERT INTO tenant_flow_node_locations (tenant_id, node_kind, node_key, location_id)
SELECT src.tenant_id, 'STAGE', s.stage, src.location_id
FROM tenant_flow_node_locations src
CROSS JOIN (VALUES ('SETRIKA_UAP'), ('QC_FINISHING'), ('PENGEMASAN')) AS s(stage)
WHERE src.node_kind = 'STAGE' AND src.node_key = 'CUCI_SOFTENER'
ON CONFLICT (tenant_id, node_kind, node_key) DO NOTHING;

-- Proses opsional milik tenant yang berlabuh di tahap lama ikut dipindah, supaya
-- langkah tambahan pabrik tidak menghilang diam-diam dari papan alurnya.
UPDATE tenant_optional_processes
SET sampling_anchor_after = 'CUCI_SOFTENER'
WHERE sampling_anchor_after = 'FINISHING_QC';

-- Lembar kerja tahap & riwayat transisi (JSONB) SENGAJA tidak ditulis ulang:
-- stage_history mencatat apa yang dulu benar-benar terjadi, dan menulis ulang
-- arsip agar cocok dengan kosakata hari ini adalah memalsukan jejak audit.

-- ------------------------------------------------------------------------------
-- 2. Jaring pengaman
-- ------------------------------------------------------------------------------
-- Repository membaca nama tahap tak dikenal dengan fallback NEW_INTAKE. Satu baris
-- yang lolos UPDATE di atas akan muncul sebagai SPK yang baru masuk padahal hampir
-- jadi — regresi paling senyap yang mungkin terjadi di sini. Domain sudah memberi
-- alias legacy (SamplingPipelineStage.parseOrNull), tapi migrasinya tetap harus
-- bersih, jadi sisanya ditolak di sini selagi masih terlihat.
DO $$
DECLARE leftover INT;
BEGIN
    SELECT count(*) INTO leftover FROM sampling_orders WHERE pipeline_stage = 'FINISHING_QC';
    IF leftover > 0 THEN
        RAISE EXCEPTION 'Masih ada % baris sampling_orders bertahap FINISHING_QC', leftover;
    END IF;
END $$;
