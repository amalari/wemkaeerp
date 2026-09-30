-- ==============================================================================
-- WeMade ERP (Jalur B) — V85: Invoice langganan platform (PLAN-builder-console M2, FR-M2-5)
-- ==============================================================================
-- builder.subscription_invoices: tagihan bulanan tenant, diterbitkan superadmin, dibayar manual.
--
-- lines_json menyimpan SALINAN BEKU baris tagihan (harga saat terbit). Harga tidak dinormalisasi ke
-- tabel katalog: invoice adalah dokumen historis, dan dokumen historis yang menunjuk ke harga hari
-- ini akan berubah sendiri ketika katalog dinaikkan — angka di PDF yang sudah dikirim pun ikut
-- berubah. total_idr = jumlah baris saat terbit, bukan hasil hitung ulang.
--
-- Milik tenant (RLS pola V76) meski hanya superadmin yang menulis: tenant berhak membaca tagihannya.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS builder.subscription_invoices (
    id          VARCHAR(140) PRIMARY KEY,
    tenant_id   VARCHAR(64)  NOT NULL REFERENCES public.tenants(id) ON DELETE CASCADE,
    number      VARCHAR(40)  NOT NULL,
    period      VARCHAR(7)   NOT NULL CHECK (period ~ '^[0-9]{4}-[0-9]{2}$'),
    lines_json  TEXT         NOT NULL,
    total_idr   BIGINT       NOT NULL CHECK (total_idr >= 0),
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('DRAFT','ISSUED','PAID','VOID')),
    issued_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    paid_at     TIMESTAMPTZ  NULL,
    paid_note   TEXT         NULL,
    -- PAID tanpa waktu bayar = data rusak; invarian yang sama dijaga di domain.
    CONSTRAINT chk_subscription_invoices_paid_at CHECK (status <> 'PAID' OR paid_at IS NOT NULL)
);

-- Satu invoice hidup per tenant per periode (yang VOID boleh berulang: itu jejak pembatalan).
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_invoices_tenant_period
    ON builder.subscription_invoices (tenant_id, period)
    WHERE status <> 'VOID';

CREATE INDEX IF NOT EXISTS idx_subscription_invoices_tenant
    ON builder.subscription_invoices (tenant_id, issued_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON builder.subscription_invoices TO wemade_app;
SELECT apply_tenant_rls_in('builder', 'subscription_invoices');
