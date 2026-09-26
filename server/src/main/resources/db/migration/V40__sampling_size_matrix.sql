-- ==============================================================================
-- WeMade ERP — Sampling Size Matrix Table (V40)
-- ==============================================================================
-- Menyimpan tabel size chart sampling yang barisnya customable (default: Lebar Dada,
-- Panjang Baju, plus baris tambahan dari admin/tim sampling) dan kolom ALL SIZE, S, M, L, XL, XXL, XXXL.
-- ==============================================================================

ALTER TABLE sampling_orders
    ADD COLUMN IF NOT EXISTS size_matrix jsonb NOT NULL DEFAULT '[]';
