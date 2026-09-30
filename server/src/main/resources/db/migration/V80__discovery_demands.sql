-- ==============================================================================
-- WeMade ERP (Jalur B) — V80: Buku Demand Discovery (plan PLAN-discovery-blueprint-prototype-studio §6, E2)
-- ==============================================================================
-- ops.discovery_demands: narasi prospek VERBATIM per draf — sebelum V80 narasi tidak
-- tersimpan di mana pun (dokumen draf hanya pack + blueprint + screens), sehingga sinyal
-- produk ("3 prospek beda memakai kata yang sama") hilang begitu sesi berakhir.
--
-- unmatched_terms = istilah narasi yang belum terwakili modul mana pun (DemandLedger,
-- fungsi murni di core); candidates Rule of Three dihitung saat dibaca, bukan disimpan,
-- supaya ambangnya bisa berubah tanpa migrasi.
--
-- Tabel di schema `ops` (pola V78): platform-global, TANPA RLS tenant, TANPA grant
-- wemade_app. ON DELETE CASCADE mengikuti draf: menghapus draf uji tidak boleh
-- meninggalkan demand yatim.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS ops.discovery_demands (
    id               VARCHAR(80)  PRIMARY KEY,
    draft_id         VARCHAR(64)  NOT NULL REFERENCES ops.discovery_drafts(id) ON DELETE CASCADE,
    owner_user_id    VARCHAR(64)  NOT NULL,
    narrative        TEXT         NOT NULL,
    industry_hint    VARCHAR(80)  NULL,
    agent_ref        VARCHAR(80)  NOT NULL,
    matched_modules  JSONB        NOT NULL DEFAULT '[]',
    unmatched_terms  JSONB        NOT NULL DEFAULT '[]',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_discovery_demands_created ON ops.discovery_demands (created_at DESC);

-- Owner-only: lihat komentar di atas. Tidak ada GRANT ke wemade_app untuk tabel ini.
REVOKE ALL ON ops.discovery_demands FROM wemade_app;
