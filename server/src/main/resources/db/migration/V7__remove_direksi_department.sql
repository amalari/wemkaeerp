-- ==============================================================================
-- WeMade ERP — Migration V7: Hapus Divisi 'Direksi' (dept-exec) & Tegaskan Direksi Non-Divisi
-- 
-- Sesuai prinsip Domain-Driven Design (DDD) WeMade ERP:
-- 1. Direksi (Executive / Owner) berdiri di pucuk struktur organisasi dan membawahi
--    seluruh perusahaan, BUKAN kepala dari suatu departemen tertentu.
--    Oleh karena itu, karyawan level EXECUTIVE memiliki department_id = NULL.
-- 2. Divisi 'dept-exec' ('Keuangan & Direksi' / 'Direksi') adalah relik lama dari
--    sebelum Direksi dipisahkan menjadi Non-Divisi. Divisi ini dihapus agar tidak
--    terjadi kerancuan antara opsi 'Direksi (Non-Divisi)' dan divisi 'Direksi'.
-- 3. Memastikan divisi operasional 'Keuangan' (dept-finance) terdaftar pada tenant demo.
-- ==============================================================================

-- 1. Lepaskan keterikatan department_id untuk semua karyawan level EXECUTIVE (Direksi)
UPDATE employees
SET department_id = NULL
WHERE level = 'EXECUTIVE' AND department_id = 'dept-exec';

-- 2. Alihkan karyawan non-executive jika ada yang terikat pada dept-exec (fallback to NULL)
UPDATE employees
SET department_id = NULL
WHERE department_id = 'dept-exec';

-- 3. Hapus divisi 'dept-exec' dari tabel departments
DELETE FROM departments
WHERE id = 'dept-exec';

-- 4. Pastikan divisi operasional 'Keuangan & Akuntansi' (dept-finance) terdaftar
INSERT INTO departments (id, tenant_id, code, display_name, short_name, color_hex, is_custom)
VALUES ('dept-finance', 'ten-demo-001', 'finance', 'Keuangan & Akuntansi', 'Keuangan', 4286339821, FALSE)
ON CONFLICT (id) DO NOTHING;
