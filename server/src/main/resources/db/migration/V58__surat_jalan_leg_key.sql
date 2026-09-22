-- ==============================================================================
-- WeMade ERP — Menautkan Surat Jalan ke leg alur yang dilayaninya
-- ==============================================================================
-- Sebuah leg (perpindahan antar gedung, ke vendor makloon, atau ke pembeli) diturunkan dari
-- susunan alur dan konfigurasi lokasi tenant. Untuk menampilkan statusnya di panel alur, dan
-- untuk menegakkan gerbang "tidak maju tahap sebelum barang diterima", dokumen Surat Jalan
-- harus bisa ditemukan dari leg-nya.
--
-- Tanpa kolom ini, satu-satunya cara memasangkan keduanya adalah mencocokkan tuple
-- (jenis, asal, tujuan, vendor) — yang langsung ambigu begitu satu SPK melewati gedung yang
-- sama dua kali (rajut -> bordir -> balik), atau memakai vendor yang sama untuk dua proses.
--
-- NULL tetap sah dan bukan kesalahan data: dokumen yang diterbitkan manual dari workspace
-- Surat Jalan tidak melayani leg mana pun, begitu juga seluruh dokumen yang terbit sebelum
-- migrasi ini. Pencocokan untuk baris lama jatuh ke heuristik dan ditandai sebagai legacy.
--
-- Aditif dan idempoten.
-- ==============================================================================

ALTER TABLE surat_jalan_manifests
    ADD COLUMN IF NOT EXISTS leg_key VARCHAR(120);

-- Pencarian selalu dalam konteks satu SPK milik satu tenant, tidak pernah lintas subjek.
CREATE INDEX IF NOT EXISTS idx_surat_jalan_manifests_leg
    ON surat_jalan_manifests (tenant_id, subject_id, leg_key);
