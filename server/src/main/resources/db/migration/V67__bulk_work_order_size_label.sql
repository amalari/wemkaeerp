-- V67 — Aturan "1 SPK = 1 ukuran": SPK massal kini lahir per ukuran dari PO multi-size.
--
-- `size_label` nullable karena hanya SPK kelahiran baru yang diisi; SPK lama multi-size
-- dibiarkan apa adanya sebagai legacy (lihat docs/plannings/planning-spk-per-size-split.md §8).

ALTER TABLE bulk_work_orders ADD COLUMN IF NOT EXISTS size_label VARCHAR(60);

-- Backfill: SPK lama yang kebetulan single-line dianggap SPK ukuran itu juga.
UPDATE bulk_work_orders
   SET size_label = substring(
       size_breakdown::jsonb->0->>'sizeLabel' FROM 1 FOR 60
   )
 WHERE size_label IS NULL
   AND jsonb_array_length(size_breakdown::jsonb) = 1;

CREATE INDEX IF NOT EXISTS idx_bulk_work_orders_deal_size
    ON bulk_work_orders (tenant_id, deal_id, size_label);
