-- ==============================================================================
-- WeMade ERP (Jalur B) — V87: trx_id numerik iPaymu untuk rekonsiliasi (FR-PAY-3.3 butir 7)
-- ==============================================================================
-- `ipaymu_trx_id` (V86) menyimpan **SessionID** — kunci pencocokan callback. Tapi endpoint
-- cek status iPaymu (`POST /api/v2/transaction`) butuh **trx_id numerik**, yang hanya
-- dibawa callback. Kolom ini menyimpan angka itu supaya job rekonsiliasi bisa menanyakan
-- ulang status invoice ISSUED yang callback-nya hilang (tunnel mati saat pembayaran).
-- ==============================================================================

ALTER TABLE builder.subscription_invoices
    ADD COLUMN IF NOT EXISTS ipaymu_trx_numeric VARCHAR(30) NULL;

CREATE INDEX IF NOT EXISTS idx_subscription_invoices_ipaymu_trx_numeric
    ON builder.subscription_invoices (ipaymu_trx_numeric)
    WHERE ipaymu_trx_numeric IS NOT NULL;
