-- ==============================================================================
-- WeMade ERP — V69: Kustodi Penyimpanan Sampel (Pengemasan -> Penyimpanan -> Kirim)
-- ==============================================================================
-- Barang selesai kemas tidak pernah langsung dikirim: ia disimpan dulu (rak packing atau
-- gudang), lalu dilepas ke buyer oleh PIC. Tabel ini mencatat siapa penerima simpan,
-- di mana barangnya, dan siapa yang melepasnya — termasuk alasan bila kirim parsial.
--
-- Tahap `STORAGE_HOLDING` sendiri tidak butuh migrasi: `pipeline_stage` disimpan per nama.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS sample_storage_records (
    id                VARCHAR(64)  PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL REFERENCES tenants(id),
    sampling_order_id VARCHAR(64)  NOT NULL REFERENCES sampling_orders(id) ON DELETE CASCADE,
    deal_id           VARCHAR(64),
    location_label    VARCHAR(80)  NOT NULL,
    qty_pcs           INTEGER      NOT NULL CHECK (qty_pcs > 0),
    stored_by_email   VARCHAR(255) NOT NULL DEFAULT '',
    stored_by_name    VARCHAR(255) NOT NULL DEFAULT '',
    stored_at         TIMESTAMPTZ  NOT NULL,
    released_by_email VARCHAR(255),
    released_by_name  VARCHAR(255),
    released_at       TIMESTAMPTZ,
    partial_reason    TEXT
);

CREATE INDEX IF NOT EXISTS idx_sample_storage_order
    ON sample_storage_records (tenant_id, sampling_order_id, stored_at);

CREATE INDEX IF NOT EXISTS idx_sample_storage_deal
    ON sample_storage_records (tenant_id, deal_id);
