-- ==============================================================================
-- WeMade ERP — Modul Jadwal Mesin & SPK Massal / PRODUCTION_MRP (V42)
-- ==============================================================================
-- SPK Produksi Massal: dokumen kerja potong-jahit-finishing untuk satu kontrak buyer,
-- diterbitkan dari Deal yang sampelnya sudah di-ACC (Golden Sample Lock).
--
-- Catatan desain:
-- - size_breakdown, line_allocations, dan stage_progress disimpan sebagai jsonb, bukan tabel
--   anak. Ketiganya selalu dibaca dan ditulis utuh bersama SPK-nya (tidak pernah di-query
--   lepas), sehingga tabel anak hanya menambah join tanpa menambah kemampuan apa pun.
-- - golden_sample_order_id sengaja ON DELETE RESTRICT: SPK massal yang kehilangan acuan
--   sampelnya adalah dokumen yang menyuruh lantai produksi bekerja tanpa spesifikasi.
-- - PRODUCTION_MRP ber-ScopeCapability.GLOBAL_ONLY, jadi tidak ada kolom pemilik dokumen —
--   jadwal mesin adalah data kolektif pabrik.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS bulk_work_orders (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    spk_number VARCHAR(50) NOT NULL,

    client_name VARCHAR(150) NOT NULL DEFAULT '',
    style_name VARCHAR(150) NOT NULL DEFAULT '',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

    deal_id VARCHAR(64) REFERENCES deals(id) ON DELETE SET NULL,
    golden_sample_order_id VARCHAR(64) REFERENCES sampling_orders(id) ON DELETE RESTRICT,

    stock_ownership VARCHAR(40) NOT NULL DEFAULT 'OWNED_RAW_MATERIAL',

    size_breakdown JSONB NOT NULL DEFAULT '[]',
    line_allocations JSONB NOT NULL DEFAULT '[]',
    stage_progress JSONB NOT NULL DEFAULT '[]',

    target_output_per_day INT NOT NULL DEFAULT 0,
    planned_start_date DATE,
    planned_finish_date DATE,
    notes TEXT NOT NULL DEFAULT '',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ
);

-- Nomor SPK unik per pabrik, bukan unik global: dua pabrik boleh sama-sama punya SPK-MSL-0001.
CREATE UNIQUE INDEX IF NOT EXISTS idx_bulk_work_orders_spk_per_tenant
    ON bulk_work_orders(tenant_id, spk_number) WHERE archived_at IS NULL;

-- Satu deal hanya boleh punya satu SPK massal hidup — gerbang anti-penerbitan ganda yang
-- membuat kain dipotong dua kali. Yang dibatalkan dikecualikan agar deal bisa diterbitkan ulang.
CREATE UNIQUE INDEX IF NOT EXISTS idx_bulk_work_orders_one_active_per_deal
    ON bulk_work_orders(tenant_id, deal_id)
    WHERE deal_id IS NOT NULL AND archived_at IS NULL AND status <> 'CANCELLED';

CREATE INDEX IF NOT EXISTS idx_bulk_work_orders_status
    ON bulk_work_orders(tenant_id, status) WHERE archived_at IS NULL;

SELECT apply_tenant_rls('bulk_work_orders');
