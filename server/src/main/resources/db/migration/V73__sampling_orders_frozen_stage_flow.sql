-- ==============================================================================
-- WeMade ERP — V73: Kerangka Tahap Beku per SPK (TRD-FLOW-001 Tahap 2, R1)
-- ==============================================================================
-- Kerangka tahap pabrik (tenant_stage_flows, V72) disalin ke SPK saat kartu masuk lantai —
-- bersamaan dengan pembekuan stage_phase_tags (V71) — supaya admin yang mengubah kerangka
-- tidak me-rute ulang kartu yang sedang dikerjakan.
--
-- NULL = belum beku; pembaca memakai kerangka rajut default. Tidak ada backfill: baris lama
-- berjalan di atas kerangka rajut, identik dengan perilaku sebelum kolom ini ada.
-- ==============================================================================

ALTER TABLE sampling_orders ADD COLUMN IF NOT EXISTS frozen_stage_flow JSONB;
