-- ==============================================================================
-- WeMade ERP — Telusur QR: Bundel & Karung (V52)
-- ==============================================================================
-- Memberi identitas fisik pada barang di lantai rajut, yang selama ini tidak ada:
-- progres produksi hanya berupa penjumlahan per SPK, dan QC memeriksa "pcs ke-N"
-- yang sebetulnya cuma nomor urut logis, bukan baju tertentu.
--
-- Dua tingkat wadah, satu ruang kode:
--   Bundel  -> hasil satu shift, dihitung per panel, menunggu di meja QC Rajut
--   Karung  -> setoran finishing; wajib satu SPK x satu size x satu warna
--
-- Silsilahnya disimpan sebagai tautan berkuantitas (bukan daftar id di dalam karung)
-- supaya bundel yang set-nya tidak lengkap bisa terpakai sebagian di kemudian hari
-- tanpa perlu migrasi lagi.
-- ==============================================================================

-- 1. Pemetaan SPK -> ordinal, sumber segmen `oooo` di dalam kode telusur.
--    Ordinal ini SENGAJA bukan nomor SPK yang tercetak: satu ruang kode melayani
--    dua penomoran SPK (sampling & massal) yang bisa bertabrakan angkanya.
CREATE TABLE IF NOT EXISTS trace_work_orders (
    id                  VARCHAR(64) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL,
    ordinal             INTEGER NOT NULL,
    work_order_kind     VARCHAR(10) NOT NULL,
    sampling_order_id   VARCHAR(64) REFERENCES sampling_orders(id) ON DELETE CASCADE,
    bulk_work_order_id  VARCHAR(64) REFERENCES bulk_work_orders(id) ON DELETE CASCADE,
    sizes_json          JSONB NOT NULL DEFAULT '[]',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Dua kolom nullable + CHECK, bukan satu kolom polimorfik tanpa FK: dengan cara ini
    -- integritas referensial tetap dijaga database, dan kedua agregat tetap tidak tercampur.
    CONSTRAINT ck_trace_one_work_order CHECK (
        (work_order_kind = 'SAMPLING' AND sampling_order_id IS NOT NULL AND bulk_work_order_id IS NULL)
        OR
        (work_order_kind = 'BULK' AND bulk_work_order_id IS NOT NULL AND sampling_order_id IS NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_trace_work_orders_ordinal
    ON trace_work_orders(tenant_id, ordinal);
CREATE UNIQUE INDEX IF NOT EXISTS uq_trace_work_orders_sampling
    ON trace_work_orders(tenant_id, sampling_order_id) WHERE sampling_order_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_trace_work_orders_bulk
    ON trace_work_orders(tenant_id, bulk_work_order_id) WHERE bulk_work_order_id IS NOT NULL;

-- 2. Ordinal tenant, ikut terkode supaya kartu milik pabrik lain ditolak alih-alih
--    diam-diam resolve ke SPK bernomor sama milik pabrik ini.
CREATE TABLE IF NOT EXISTS trace_tenant_ordinals (
    tenant_id  VARCHAR(64) PRIMARY KEY,
    ordinal    INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_trace_tenant_ordinal ON trace_tenant_ordinals(ordinal);

-- 3. Wadah fisik. UNIQUE(tenant_id, code) adalah tulang punggung idempotensi scan:
--    operator akan menekan dua kali, dan inilah yang membuat itu menghasilkan satu baris.
CREATE TABLE IF NOT EXISTS trace_containers (
    id               VARCHAR(64) PRIMARY KEY,
    tenant_id        VARCHAR(64) NOT NULL,
    code             VARCHAR(32) NOT NULL,
    work_order_kind  VARCHAR(10) NOT NULL,
    work_order_id    VARCHAR(64) NOT NULL,
    tier             VARCHAR(20) NOT NULL,
    size_label       VARCHAR(60) NOT NULL,
    colorway         VARCHAR(120) NOT NULL DEFAULT '',
    state            VARCHAR(20) NOT NULL DEFAULT 'OPENED',
    declared_pcs     INTEGER NOT NULL DEFAULT 0,
    weight_kg        DOUBLE PRECISION NOT NULL DEFAULT 0,
    operator_name    VARCHAR(150) NOT NULL DEFAULT '',
    shift_label      VARCHAR(40) NOT NULL DEFAULT '',
    -- Kapan hal ini terjadi di lantai; boleh lebih awal dari created_at saat jaringan mati.
    recorded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes            TEXT NOT NULL DEFAULT ''
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_trace_containers_code ON trace_containers(tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_trace_containers_work_order
    ON trace_containers(tenant_id, work_order_kind, work_order_id);
CREATE INDEX IF NOT EXISTS idx_trace_containers_queue
    ON trace_containers(tenant_id, tier, state);

-- 4. Hitungan lembar per panel di dalam satu bundel.
CREATE TABLE IF NOT EXISTS trace_container_panel_tallies (
    container_id VARCHAR(64) NOT NULL REFERENCES trace_containers(id) ON DELETE CASCADE,
    -- tenant_id didenormalisasi semata-mata supaya tabel ini bisa ikut RLS; tanpa kolom ini
    -- tidak ada yang bisa difilter dan isinya bocor lintas tenant.
    tenant_id    VARCHAR(64) NOT NULL,
    panel        VARCHAR(40) NOT NULL,
    pieces       INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (container_id, panel)
);
CREATE INDEX IF NOT EXISTS idx_trace_tallies_tenant ON trace_container_panel_tallies(tenant_id);

-- 5. Silsilah: sekian set dari sebuah bundel masuk ke sebuah karung.
CREATE TABLE IF NOT EXISTS trace_container_links (
    parent_id    VARCHAR(64) NOT NULL REFERENCES trace_containers(id) ON DELETE CASCADE,
    child_id     VARCHAR(64) NOT NULL REFERENCES trace_containers(id) ON DELETE CASCADE,
    tenant_id    VARCHAR(64) NOT NULL,
    consumed_pcs INTEGER NOT NULL,
    linked_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (parent_id, child_id),
    CONSTRAINT ck_trace_link_not_self CHECK (parent_id <> child_id),
    CONSTRAINT ck_trace_link_positive CHECK (consumed_pcs > 0)
);
CREATE INDEX IF NOT EXISTS idx_trace_links_child ON trace_container_links(tenant_id, child_id);

-- 6. Parameter pra-cetak per SPK.
CREATE TABLE IF NOT EXISTS trace_allocations (
    id               VARCHAR(64) PRIMARY KEY,
    tenant_id        VARCHAR(64) NOT NULL,
    work_order_kind  VARCHAR(10) NOT NULL,
    work_order_id    VARCHAR(64) NOT NULL,
    sets_per_bundle  INTEGER NOT NULL DEFAULT 20,
    pcs_per_sack     INTEGER NOT NULL DEFAULT 60,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_trace_allocations_work_order
    ON trace_allocations(tenant_id, work_order_kind, work_order_id);

-- 7. RLS untuk KELIMA tabel. Yang tanpa tenant_id sendiri sudah diberi kolom denormal di atas.
SELECT apply_tenant_rls('trace_work_orders');
SELECT apply_tenant_rls('trace_containers');
SELECT apply_tenant_rls('trace_container_panel_tallies');
SELECT apply_tenant_rls('trace_container_links');
SELECT apply_tenant_rls('trace_allocations');
