-- Memisahkan dua meja inspeksi (QC Rajut vs QC Finishing) dan mencatat berapa pcs
-- yang dicakup satu lembar, sebagai dasar rekap "siapa memeriksa berapa" per SPK.

ALTER TABLE sampling_qc_inspections
    ADD COLUMN IF NOT EXISTS kind VARCHAR(30) NOT NULL DEFAULT 'FINISHING',
    ADD COLUMN IF NOT EXISTS inspected_qty INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS piece_no INTEGER NOT NULL DEFAULT 1;

-- Seluruh lembar yang sudah ada dibuat sebelum pemisahan ini, dan semuanya inspeksi
-- baju jadi — DEFAULT di atas sudah menempatkannya dengan benar, tidak ada backfill lain.

ALTER TABLE sampling_qc_inspections
    ADD CONSTRAINT sampling_qc_inspections_qty_positive CHECK (inspected_qty >= 1),
    ADD CONSTRAINT sampling_qc_inspections_piece_positive CHECK (piece_no >= 1);

CREATE INDEX IF NOT EXISTS idx_sampling_qc_inspections_order_kind
    ON sampling_qc_inspections (sampling_order_id, kind);
