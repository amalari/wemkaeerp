-- Opsi B (serah-terima developer): brief yang DIBEKUKAN saat permintaan pembuatan lahir (deploy pack kustom).
--   brief_markdown : untuk dibaca developer (Antrian Pembuatan, "Lihat brief")
--   brief_json     : untuk mesin (portal developer kelak); format = BriefCodec
--   brief_at       : saat dibekukan
-- Aditif dan nullable: permintaan lama tetap terbaca persis seperti sebelumnya (tanpa brief).
ALTER TABLE builder.build_requests ADD COLUMN IF NOT EXISTS brief_markdown TEXT NULL;
ALTER TABLE builder.build_requests ADD COLUMN IF NOT EXISTS brief_json     TEXT NULL;
ALTER TABLE builder.build_requests ADD COLUMN IF NOT EXISTS brief_at       TIMESTAMPTZ NULL;
