-- ==============================================================================
-- WeMade ERP — Spek Panel per Ukuran (V53)
-- ==============================================================================
-- Sebelum ini gramasi, waktu rajut, dan program mesin hanya ada SATU set per SPK,
-- padahal badan depan size XL jelas lebih berat dan lebih lama dirajut daripada
-- size S. Operator yang memegang lembar kerja XL menerima angka milik size acuan
-- lalu menyesuaikannya dari ingatan.
--
-- Kolom lama TIDAK dihapus: ia tetap jadi angka acuan yang dibaca modul costing,
-- sekaligus fallback untuk ukuran yang belum pernah ditimbang. SPK lama karenanya
-- menghasilkan angka yang sama persis seperti sebelumnya.
-- ==============================================================================

ALTER TABLE sampling_yield_timings
    ADD COLUMN IF NOT EXISTS panel_size_specs JSONB NOT NULL DEFAULT '[]';
