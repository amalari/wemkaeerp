-- ==============================================================================
-- WeMade ERP — V46: Lembar Input Dinamis per Tahap & Audit Perpindahan Tahap
-- ==============================================================================
-- 1. `stage_inputs` : kandungan jsonb List<StageWorkInput> — lembar kerja dinamis
--    (label + value bebas) per tahap pipeline sampling (CAM, Rajut Mesin, dst).
--    Preseden jsonb di tabel yang sama: revision_history (V39), size_matrix (V40).
-- 2. `stage_history`: jejak audit "siapa yang memindahkan dan kapan" — diisi server
--    dari JWT (CallerPrincipal), bukan dari body request, agar tidak bisa dipalsukan.
-- ==============================================================================

ALTER TABLE sampling_orders
    ADD COLUMN IF NOT EXISTS stage_inputs jsonb NOT NULL DEFAULT '{}';

ALTER TABLE sampling_orders
    ADD COLUMN IF NOT EXISTS stage_history jsonb NOT NULL DEFAULT '[]';
