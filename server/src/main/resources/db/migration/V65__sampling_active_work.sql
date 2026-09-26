-- ==============================================================================
-- WeMade ERP — V65: Klaim "Sedang Dikerjakan" di Meja Operator Sampling
-- ==============================================================================
-- `active_work`: JSON StageWorkClaim (stage, operatorName, actorEmail, startedAt) —
-- operator yang sedang memegang SPK di mejanya. String kosong = SPK di antrian.
-- Disimpan sebagai text, bukan jsonb, karena "tidak ada klaim" adalah keadaan yang
-- paling sering dan string kosong bukan JSON yang sah.
--
-- Kiriman balik rework TIDAK butuh kolom baru: dicatat di `stage_history` yang sudah
-- ada, ditandai field `liability` + `reason` pada entrinya.
-- ==============================================================================

ALTER TABLE sampling_orders
    ADD COLUMN IF NOT EXISTS active_work text NOT NULL DEFAULT '';
