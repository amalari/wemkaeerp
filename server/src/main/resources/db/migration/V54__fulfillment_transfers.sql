-- ==============================================================================
-- WeMade ERP — FULFILLMENT: Transfer Karung Antar Divisi (V54)
-- ==============================================================================
-- Modul pengiriman internal bekerja seperti kurir paket: karung yang sudah
-- ditutup di modul telusur (V52) diajukan berangkat, di-ACC admin produksi,
-- lalu diterima di tujuan dengan bukti timbang + foto.
--
-- Tabel ini MENJALANI perjalanan karung, bukan MENYIMPAN isinya — isi karung
-- tetap milik trace_containers. Satu karung cuma boleh punya satu perjalanan
-- aktif; ditegakkan di use case dan didukung indeks parsial di bawah.
--
-- Bukti serah terima selalu dua jalur:
--   RECEIVER -> TTD penerima (digambar di perangkat internal) + foto timbangan
--   COURIER  -> resi + berat tertagih EKSAK sampai koma + foto resi
-- ==============================================================================

CREATE TABLE IF NOT EXISTS fulfillment_transfers (
    id                      VARCHAR(64) PRIMARY KEY,
    tenant_id               VARCHAR(64) NOT NULL,
    sack_code               VARCHAR(32) NOT NULL,
    work_order_kind         VARCHAR(10),
    work_order_id           VARCHAR(64),
    size_label              VARCHAR(60) NOT NULL,
    colorway                VARCHAR(120) NOT NULL DEFAULT '',
    declared_pcs            INTEGER NOT NULL,
    leg                     VARCHAR(40) NOT NULL,
    status                  VARCHAR(20) NOT NULL DEFAULT 'MENUNGGU_ACC',

    -- Bukti dispatch: timbang -> foto -> baru ajukan. Wajib sejak pengajuan.
    dispatch_weight_kg      NUMERIC(6, 2) NOT NULL,
    dispatch_scale_photo_key TEXT NOT NULL,
    requested_by            VARCHAR(150) NOT NULL,
    requested_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- ACC admin produksi (TTD wajib) / penolakan (alasan wajib).
    approved_by             VARCHAR(150),
    approved_at             TIMESTAMPTZ,
    approval_signature_key  TEXT,
    rejected_by             VARCHAR(150),
    reject_reason           TEXT,

    -- Bukti serah terima: RECEIVER atau COURIER (lihat header).
    handover_type           VARCHAR(20),
    receiver_name           VARCHAR(150),
    receiver_signature_key  TEXT,
    carrier                 VARCHAR(80),
    tracking_number         VARCHAR(80),
    chargeable_weight_kg    NUMERIC(6, 2),
    handover_photo_key      TEXT,
    received_weight_kg      NUMERIC(6, 2),
    received_pcs            INTEGER,
    received_at             TIMESTAMPTZ,

    notes                   TEXT NOT NULL DEFAULT '',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_fulfillment_handover_type
        CHECK (handover_type IS NULL OR handover_type IN ('RECEIVER', 'COURIER'))
);

CREATE INDEX IF NOT EXISTS idx_fulfillment_transfers_queue
    ON fulfillment_transfers(tenant_id, status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_fulfillment_transfers_sack
    ON fulfillment_transfers(tenant_id, sack_code);

-- Satu karung, satu perjalanan aktif. Status final (DITERIMA*) boleh berulang —
-- karung boleh dikirim lagi keesokan harinya — tapi MENUNGGU_ACC/DIANTAR/DITOLAK
-- cuma boleh satu baris pada satu waktu.
CREATE UNIQUE INDEX IF NOT EXISTS uq_fulfillment_active_transfer
    ON fulfillment_transfers(tenant_id, sack_code)
    WHERE status IN ('MENUNGGU_ACC', 'DIANTAR', 'DITOLAK', 'DIPERIKSA');

-- Jejak audit per perubahan status — siapa melakukan apa dan kapan.
CREATE TABLE IF NOT EXISTS fulfillment_transfer_events (
    id           VARCHAR(64) PRIMARY KEY,
    tenant_id    VARCHAR(64) NOT NULL,
    transfer_id  VARCHAR(64) NOT NULL REFERENCES fulfillment_transfers(id) ON DELETE CASCADE,
    event_type   VARCHAR(30) NOT NULL,
    actor        VARCHAR(150) NOT NULL DEFAULT '',
    detail       TEXT NOT NULL DEFAULT '',
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_fulfillment_events_transfer
    ON fulfillment_transfer_events(tenant_id, transfer_id, occurred_at);

SELECT apply_tenant_rls('fulfillment_transfers');
SELECT apply_tenant_rls('fulfillment_transfer_events');
