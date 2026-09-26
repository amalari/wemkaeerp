-- ==============================================================================
-- WeMade ERP — Link Sampling Orders to Deals (V35)
-- ==============================================================================
-- Mengikat SPK Sampling ke agregat Deal CRM (Golden Sample Lock):
-- satu deal bisa memuat beberapa order sampling (multi desain/warna), dan
-- status ACC seluruh sampling menjadi gerbang pembuka Tab Produksi Massal.
-- ==============================================================================

ALTER TABLE sampling_orders
    ADD COLUMN IF NOT EXISTS deal_id VARCHAR(64) REFERENCES deals(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS sample_quantity INT NOT NULL DEFAULT 2,
    ADD COLUMN IF NOT EXISTS courier_tracking VARCHAR(150),
    ADD COLUMN IF NOT EXISTS sampling_fee_idr BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_sampling_orders_deal
    ON sampling_orders(tenant_id, deal_id) WHERE deal_id IS NOT NULL;
