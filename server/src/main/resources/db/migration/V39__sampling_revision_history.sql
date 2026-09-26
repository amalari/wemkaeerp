-- =============================================================================
-- V10: Riwayat feedback revisi sampling per nomor revisi.
--
-- Sebelumnya hanya `acc_notes` (teks tunggal) yang menampung feedback — setiap revisi
-- baru MENIMPA catatan lama, sehingga admin tidak bisa menelusuri feedback revisi 1
-- setelah revisi 2 diajukan. Kolom jsonb ini mengarsipkan [{revision, notes, at}].
-- =============================================================================
ALTER TABLE sampling_orders
    ADD COLUMN IF NOT EXISTS revision_history jsonb NOT NULL DEFAULT '[]';