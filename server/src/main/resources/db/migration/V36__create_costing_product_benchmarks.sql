-- ==============================================================================
-- WeMade ERP — Knowledge Base Produk Rajut Historis (V36)
-- ==============================================================================
-- Menyimpan arsip artikel yang PERNAH diproduksi beserta angka fisik riilnya
-- (gramasi netto, menit mesin rajut) sebagai acuan estimasi cepat HPP.
--
-- Mengapa tabel terpisah dari `costing_sheets`?
-- Lembar HPP adalah dokumen transaksional dengan siklus DRAFT → APPROVED. Arsip Excel lama
-- adalah fakta historis yang sudah selesai; memaksanya masuk ke siklus approval akan mengisi
-- antrean persetujuan dengan ratusan baris palsu dan merusak telemetri `pendingSheetCount`.
-- Kolom `source_sheet_id` tetap disediakan untuk kasus di mana arsip memang berasal dari
-- lembar HPP resmi di sistem ini.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS costing_product_benchmarks (
    id                             VARCHAR(64) PRIMARY KEY,
    tenant_id                      VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,

    -- Identitas artikel
    style_name                     VARCHAR(255) NOT NULL,
    client_name                    VARCHAR(255) NOT NULL DEFAULT '',
    category                       VARCHAR(32)  NOT NULL DEFAULT 'OTHER',

    -- Karakteristik teknis rajutan
    knit_type                      VARCHAR(120) NOT NULL DEFAULT '',
    yarn_type                      VARCHAR(120) NOT NULL DEFAULT '',
    gauge                          INT,

    -- Metrik fisik riil dari lantai produksi
    net_weight_grams               DECIMAL(10,2) NOT NULL,
    knitting_minutes               INT,
    button_count                   INT NOT NULL DEFAULT 0,

    -- Visual & atribut hasil ekstraksi AI
    mockup_image_url               TEXT,
    features_json                  JSONB NOT NULL DEFAULT '{}',
    cost_breakdown_json            JSONB NOT NULL DEFAULT '[]',

    -- Angka finansial historis
    hpp_per_unit_minor             BIGINT NOT NULL,
    selling_price_minor            BIGINT,

    -- Provenance
    source_sheet_id                VARCHAR(64) REFERENCES costing_sheets(id) ON DELETE SET NULL,
    source_file_name               VARCHAR(512) NOT NULL DEFAULT '',

    created_at                     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_benchmark_weight    CHECK (net_weight_grams > 0 AND net_weight_grams < 10000),
    CONSTRAINT chk_benchmark_gauge     CHECK (gauge IS NULL OR (gauge BETWEEN 1 AND 21)),
    CONSTRAINT chk_benchmark_buttons   CHECK (button_count >= 0)
);

-- Jalur baca utama estimator: sempitkan ke tenant + kategori, urutkan gauge terdekat.
CREATE INDEX IF NOT EXISTS idx_costing_benchmarks_similarity
    ON costing_product_benchmarks(tenant_id, category, gauge);

-- Katalog Knowledge Base di UI, terbaru di atas.
CREATE INDEX IF NOT EXISTS idx_costing_benchmarks_listing
    ON costing_product_benchmarks(tenant_id, created_at DESC);

-- Pencarian atribut hasil ekstraksi AI (mis. features @> '{"sablon":"ya"}').
CREATE INDEX IF NOT EXISTS idx_costing_benchmarks_features
    ON costing_product_benchmarks USING GIN (features_json);

-- Impor ulang folder yang sama tidak boleh menggandakan arsip.
CREATE UNIQUE INDEX IF NOT EXISTS uq_costing_benchmarks_source_file
    ON costing_product_benchmarks(tenant_id, source_file_name)
    WHERE source_file_name <> '';

SELECT apply_tenant_rls('costing_product_benchmarks');
