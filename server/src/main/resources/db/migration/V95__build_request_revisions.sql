-- Opsi 2 (revisi, bukan duplikat): deploy ulang pack kustom menggantikan permintaan yang belum selesai.
--   status SUPERSEDED : digantikan oleh deploy ulang (dikelola sistem; tak bisa diatur manual)
--   brief_version     : versi brief modul ini (1 = pertama)
--   supersedes        : permintaan yang digantikan oleh baris ini (revisi)
--   superseded_by     : pengganti bila baris ini SUPERSEDED (NULL = digugurkan tanpa pengganti, modul tak lagi aktif)
-- Aditif; baris lama tetap valid (brief_version 1, tanpa penaut).
ALTER TABLE builder.build_requests DROP CONSTRAINT IF EXISTS build_requests_status_check;
ALTER TABLE builder.build_requests ADD CONSTRAINT build_requests_status_check
    CHECK (status IN ('QUEUED','QUOTED','APPROVED','IN_PROGRESS','SHIPPED','REJECTED','SUPERSEDED'));
ALTER TABLE builder.build_requests ADD COLUMN IF NOT EXISTS brief_version INTEGER NOT NULL DEFAULT 1 CHECK (brief_version > 0);
ALTER TABLE builder.build_requests ADD COLUMN IF NOT EXISTS supersedes    VARCHAR(140) NULL;
ALTER TABLE builder.build_requests ADD COLUMN IF NOT EXISTS superseded_by VARCHAR(140) NULL;
