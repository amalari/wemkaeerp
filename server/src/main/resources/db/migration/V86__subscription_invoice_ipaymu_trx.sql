-- ==============================================================================
-- WeMade ERP (Jalur B) — V86: trx_id iPaymu pada invoice langganan (L1, FR-PAY-3)
-- ==============================================================================
-- Aditif dari V85: satu invoice boleh punya satu transaksi Hosted Checkout iPaymu.
-- trx_id adalah kunci masuk handler callback (POST /api/payment/ipaymu/notify), jadi:
--  - UNIQUE (partial, boleh NULL: invoice dibayar manual tidak punya trx), dan
--  - callback mencari invoice lewat indeks ini, bukan lewat reference_id yang bisa dipalsukan.
-- ==============================================================================

ALTER TABLE builder.subscription_invoices
    ADD COLUMN IF NOT EXISTS ipaymu_trx_id VARCHAR(100) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_invoices_ipaymu_trx
    ON builder.subscription_invoices (ipaymu_trx_id)
    WHERE ipaymu_trx_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_subscription_invoices_ipaymu_trx
    ON builder.subscription_invoices (ipaymu_trx_id)
    WHERE ipaymu_trx_id IS NOT NULL;
