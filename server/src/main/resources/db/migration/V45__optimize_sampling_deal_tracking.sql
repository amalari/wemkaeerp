-- ==============================================================================
-- WeMade ERP — Optimize Sampling Deal Tracking & Audit Index (V45)
-- ==============================================================================
-- Menambahkan indeks performa untuk monitoring alur fisik garmen di modul Deals:
-- 1. Index pencarian cepat SPK sampling berdasarkan tenant dan deal pemilik.
-- 2. Index pelacakan setoran finishing dan inspeksi QC untuk live audit trail.
-- ==============================================================================

-- 1. Indeks komposit sampling_orders untuk querying deal tracking
CREATE INDEX IF NOT EXISTS idx_sampling_orders_deal_tracking
    ON sampling_orders(tenant_id, deal_id, pipeline_stage)
    WHERE archived_at IS NULL AND deal_id IS NOT NULL;

-- 2. Indeks komposit setoran finishing untuk lookup tanggal terbaru
CREATE INDEX IF NOT EXISTS idx_sampling_finishing_deposits_tracking
    ON sampling_finishing_deposits(tenant_id, sampling_order_id, created_at DESC);

-- 3. Indeks komposit inspeksi QC untuk lookup hasil inspeksi terbaru
CREATE INDEX IF NOT EXISTS idx_sampling_qc_inspections_tracking
    ON sampling_qc_inspections(tenant_id, sampling_order_id, inspected_at DESC);
