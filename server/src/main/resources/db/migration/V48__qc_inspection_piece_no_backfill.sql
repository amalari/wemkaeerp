-- ==============================================================================
-- WeMade ERP — V48: Tambah kolom piece_no yang tertinggal dari V47
-- ==============================================================================
-- V47 terlanjur diaplikasikan (15:40) pada versi file yang belum memuat
-- `piece_no`; revisi file V47 sesudahnya tidak pernah dijalankan Flyway karena
-- versi 47 sudah tercatat di schema history. Migrasi ini melengkapi kolom yang
-- hilang agar cocok dengan SamplingQcInspectionsTable.pieceNo.
-- Idempoten: aman walau kolom/constraint sudah ada.
-- ==============================================================================

ALTER TABLE sampling_qc_inspections
    ADD COLUMN IF NOT EXISTS piece_no INTEGER NOT NULL DEFAULT 1;

ALTER TABLE sampling_qc_inspections
    DROP CONSTRAINT IF EXISTS sampling_qc_inspections_piece_positive;

ALTER TABLE sampling_qc_inspections
    ADD CONSTRAINT sampling_qc_inspections_piece_positive CHECK (piece_no >= 1);
