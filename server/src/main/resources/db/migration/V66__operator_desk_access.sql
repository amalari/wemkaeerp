-- ==============================================================================
-- V66: Wewenang per meja lantai produksi (OPERATOR_EXEC)
--
-- Penugasan modul ke divisi kini bisa membatasi meja (Rajut, Linking, Cuci,
-- Setrika, QC, Kemas) yang boleh diakses. `[]` — nilai default semua baris —
-- berarti tanpa batasan, sehingga penugasan lama tidak berubah perilaku.
-- ==============================================================================

ALTER TABLE department_module_assignments
    ADD COLUMN IF NOT EXISTS allowed_desks JSONB NOT NULL DEFAULT '[]';
