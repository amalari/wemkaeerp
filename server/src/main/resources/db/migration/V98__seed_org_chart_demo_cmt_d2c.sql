-- ==============================================================================
-- WeMade ERP (Jalur B) — V98: Seed bagan organisasi untuk tenant demo CMT & D2C
-- (TRD-PLAT-010 T2, Q2)
-- ==============================================================================
-- V4 hanya menyemai divisi/karyawan untuk `ten-demo-001`; V9 membuat `ten-demo-cmt` dan `ten-demo-d2c` TANPA data
-- org. Kedua tenant demo itu tampak berisi hanya karena sampel yang dirakit klien Org Chart — dan sampel klien itu
-- dihapus (TRD-PLAT-010 T1). Migrasi ini memberi mereka data NYATA di database, setara keadaan akhir `ten-demo-001`
-- (V4 + V7): lima divisi (Penjualan, Produksi, Gudang, QC, Keuangan) dan sembilan karyawan, Direksi tanpa divisi.
--
-- Idempoten: `ON CONFLICT DO NOTHING` (id unik per tenant lewat sufiks `-cmt` / `-d2c`; email unik per tenant lewat
-- domain tenant). Menjalankan ulang tidak mengubah baris yang ada, dan tenant yang tidak ada (DB tanpa V9) dilewati
-- oleh JOIN ke `public.tenants`. `ten-demo-001` TIDAK disentuh.
-- Tabel berschema `org_chart` (V76); RLS sudah terpasang pada tabelnya, migrasi berjalan sebagai pemilik seperti V4.
-- ==============================================================================

WITH demo(tenant_id, suffix, domain) AS (
    VALUES ('ten-demo-cmt', 'cmt', 'berkah-makloon.id'),
           ('ten-demo-d2c', 'd2c', 'urbanwear.id')
),
target AS (
    SELECT d.* FROM demo d JOIN public.tenants t ON t.id = d.tenant_id
)
INSERT INTO org_chart.departments (id, tenant_id, code, display_name, short_name, color_hex, is_custom)
SELECT 'dept-' || s.slug || '-' || g.suffix, g.tenant_id, s.code, s.display_name, s.short_name, s.color_hex, FALSE
FROM target g
CROSS JOIN (VALUES
    ('sales',     'sales',            'Penjualan & CRM',      'Sales',    4280624107),
    ('ppic',      'production_ppic',  'Produksi & PPIC',      'Produksi', 4293552140),
    ('warehouse', 'warehouse',        'Gudang & Logistik',    'Gudang',   4279145608),
    ('qc',        'qc',               'Quality Control (QC)', 'QC',       4279583562),
    ('finance',   'finance',          'Keuangan & Akuntansi', 'Keuangan', 4286339821)
) AS s(slug, code, display_name, short_name, color_hex)
ON CONFLICT DO NOTHING;

WITH demo(tenant_id, suffix, domain) AS (
    VALUES ('ten-demo-cmt', 'cmt', 'berkah-makloon.id'),
           ('ten-demo-d2c', 'd2c', 'urbanwear.id')
),
target AS (
    SELECT d.* FROM demo d JOIN public.tenants t ON t.id = d.tenant_id
)
INSERT INTO org_chart.employees (id, tenant_id, name, email, department_id, level, role_title, reports_to_id, phone)
SELECT 'emp-' || e.slug || '-' || g.suffix,
       g.tenant_id,
       e.name,
       e.mail_user || '@' || g.domain,
       CASE WHEN e.dept_slug IS NULL THEN NULL ELSE 'dept-' || e.dept_slug || '-' || g.suffix END,
       e.level,
       e.role_title,
       CASE WHEN e.boss_slug IS NULL THEN NULL ELSE 'emp-' || e.boss_slug || '-' || g.suffix END,
       e.phone
FROM target g
CROSS JOIN (VALUES
    ('hendra', 'Bpk. Hendra Kusuma',  'hendra.owner',  NULL,        'EXECUTIVE',          'Direktur Utama / Owner',       NULL,     '081122334455'),
    ('budi',   'Budi Santoso',        'budi.sales',    'sales',     'HEAD_OF_DEPARTMENT', 'Head of Sales & Marketing',    'hendra', '081234567890'),
    ('joko',   'Joko Susilo',         'joko.ppic',     'ppic',      'HEAD_OF_DEPARTMENT', 'Kepala Produksi & PPIC',       'hendra', '081398765432'),
    ('siti',   'Siti Rahma',          'siti.gudang',   'warehouse', 'HEAD_OF_DEPARTMENT', 'Kepala Gudang & Logistik',     'hendra', '081711223344'),
    ('anton',  'Anton Prasetyo',      'anton.qc',      'qc',        'HEAD_OF_DEPARTMENT', 'Kepala Quality Control (QC)',  'hendra', '081855667788'),
    ('rian',   'Rian Firmansyah',     'rian.sales',    'sales',     'STAFF_OPERATOR',     'Sales Eksekutif Lapangan',     'budi',   '082111223344'),
    ('dedi',   'Dedi Kurniawan',      'dedi.tender',   'sales',     'STAFF_OPERATOR',     'Sales Tender & Korporat',      'budi',   '082199887766'),
    ('maya',   'Maya Anggraini',      'maya.sample',   'sales',     'STAFF_OPERATOR',     'Admin Sampling & CS',          'budi',   '082133445566'),
    ('agus',   'Agus Setiawan',       'agus.cutting',  'ppic',      'STAFF_OPERATOR',     'Mandor Meja Potong',           'joko',   '085211223344')
) AS e(slug, name, mail_user, dept_slug, level, role_title, boss_slug, phone)
ON CONFLICT DO NOTHING;
