-- ==============================================================================
-- WeMade ERP — ALUR PROSES KUSTOM PER DESAIN / SPK SAMPLING (V57)
-- ==============================================================================
-- Mendukung alur proses spesifik per desain/artikel:
-- - NULL / is_custom_flow = FALSE: SPK mewarisi alur default pabrik (tenant catalog)
-- - is_custom_flow = TRUE: SPK memiliki alur khusus yang tersimpan di custom_flow_processes
-- ==============================================================================

ALTER TABLE sampling_orders
    ADD COLUMN IF NOT EXISTS custom_flow_processes JSONB DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS is_custom_flow BOOLEAN NOT NULL DEFAULT FALSE;
