-- ==============================================================================
-- WeMade ERP — Backfill wewenang modul tata kelola ke jabatan yang sudah ada
-- ==============================================================================
-- V18 menambahkan ORG_CHART, DYNAMIC_RBAC, dan FACTORY_FLOW sebagai modul, lalu memastikan
-- ketiganya tersambung ke setiap tenant. Yang belum ditangani: `custom_roles.module_permissions`
-- di database masih memuat sembilan modul operasional saja.
--
-- Akibatnya fatal dan senyap. `CustomRole.getAccess()` mengembalikan NONE untuk kunci yang tidak
-- ada, jadi setiap jabatan — termasuk Owner — kehilangan ketiga layar itu begitu rilis ini
-- mendarat. Dan bypass Owner tidak menyelamatkannya: `TestingPersona.matchRole()` memasangkan
-- direksi ke `role-owner`, dan invarian TestingPersona MELARANG persona berjabatan memakai bypass
-- (supaya menguji sebuah jabatan benar-benar menguji jabatan itu). Jadi Owner pun berjalan lewat
-- matriks, menemukan NONE, dan tidak punya satu pun layar untuk memperbaikinya.
--
-- Penguncian `CustomRole.enforce()` tidak menutup lubang ini karena ia hanya berlaku saat jabatan
-- DITULIS. Baris yang sudah lama tersimpan tidak pernah melewatinya. Invarian di kode hanya
-- menjaga data yang lahir setelah invarian itu ada — sisanya urusan migrasi.
--
-- Nilai yang diisi mengikuti `CustomRole.createFactoryPresets()` persis, sehingga tenant lama dan
-- tenant baru berangkat dari konfigurasi yang sama.
--
-- Aditif dan idempoten: `||` pada JSONB menimpa kunci yang sama, jadi dijalankan ulang tidak
-- menggandakan apa pun. Kunci yang sudah diatur admin sengaja IKUT ditimpa hanya bila belum ada
-- (lihat klausa NOT ? di tiap pernyataan) — pengaturan manual tidak boleh dibatalkan migrasi.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Owner: akses penuh, dan DYNAMIC_RBAC wajib MANAGE
-- ------------------------------------------------------------------------------
-- Ini bukan kemurahan hati melainkan syarat anti-lockout. Jabatan Owner adalah satu-satunya yang
-- dijamin punya jalan kembali ke layar Hak Akses; tanpa baris ini, tenant yang direksinya memakai
-- `role-owner` tidak bisa membuka RBAC-nya sendiri lagi.
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'ORG_CHART',    jsonb_build_object('level', 'MANAGE', 'scope', 'ALL_TENANT_DATA'),
        'DYNAMIC_RBAC', jsonb_build_object('level', 'MANAGE', 'scope', 'ALL_TENANT_DATA'),
        'FACTORY_FLOW', jsonb_build_object('level', 'MANAGE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%owner'
  AND NOT (module_permissions ? 'DYNAMIC_RBAC');

-- ------------------------------------------------------------------------------
-- 2. Kepala Produksi (PPIC): pemilik kanvas alur
-- ------------------------------------------------------------------------------
-- PPIC-lah yang menyusun urutan modul produksi sehari-hari, jadi ia MANAGE atas Alur Pabrik.
-- Matriks wewenang tetap tertutup: mengatur siapa boleh apa bukan pekerjaan kepala produksi.
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'ORG_CHART',    jsonb_build_object('level', 'VIEW',   'scope', 'SUBORDINATE_DATA'),
        'DYNAMIC_RBAC', jsonb_build_object('level', 'NONE',   'scope', 'ALL_TENANT_DATA'),
        'FACTORY_FLOW', jsonb_build_object('level', 'MANAGE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%ppic'
  AND NOT (module_permissions ? 'DYNAMIC_RBAC');

-- ------------------------------------------------------------------------------
-- 3. Sales, Kepala Sales, Gudang: hanya melihat bagan divisinya
-- ------------------------------------------------------------------------------
-- SUBORDINATE_DATA, bukan ALL_TENANT_DATA. Inilah yang membuat ScopeCapability.HIERARCHICAL pada
-- ORG_CHART punya arti sejak hari pertama: kepala divisi melihat timnya, bukan seluruh daftar
-- karyawan pabrik beserta email dan nomor teleponnya.
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'ORG_CHART',    jsonb_build_object('level', 'VIEW', 'scope', 'SUBORDINATE_DATA'),
        'DYNAMIC_RBAC', jsonb_build_object('level', 'NONE', 'scope', 'ALL_TENANT_DATA'),
        'FACTORY_FLOW', jsonb_build_object('level', 'NONE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND (id LIKE '%sales' OR id LIKE '%sales-head' OR id LIKE '%warehouse')
  AND NOT (module_permissions ? 'DYNAMIC_RBAC');

-- ------------------------------------------------------------------------------
-- 4. Operator mesin jahit: seluruh layar tata kelola tertutup
-- ------------------------------------------------------------------------------
-- Operator bekerja di satu layar input di tablet. Memberinya akses ke daftar karyawan tidak
-- menambah apa pun selain permukaan yang bisa bocor.
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'ORG_CHART',    jsonb_build_object('level', 'NONE', 'scope', 'ALL_TENANT_DATA'),
        'DYNAMIC_RBAC', jsonb_build_object('level', 'NONE', 'scope', 'ALL_TENANT_DATA'),
        'FACTORY_FLOW', jsonb_build_object('level', 'NONE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = TRUE
  AND id LIKE '%operator'
  AND NOT (module_permissions ? 'DYNAMIC_RBAC');

-- ------------------------------------------------------------------------------
-- 5. Jabatan rakitan tenant: ditutup, bukan ditebak
-- ------------------------------------------------------------------------------
-- Jabatan yang dibuat sendiri oleh sebuah pabrik ("Kepala Sablon", "Admin Ekspor") tidak punya
-- padanan di preset, dan menebak wewenangnya berarti memberi akses yang tidak pernah diminta
-- siapa pun. Ditutup secara eksplisit supaya kuncinya ADA di matriks — tanpa ini, layar RBAC
-- menampilkan kartu kosong dan admin tidak tahu apakah modulnya memang tertutup atau belum
-- pernah dikonfigurasi.
UPDATE custom_roles
SET module_permissions = module_permissions || jsonb_build_object(
        'ORG_CHART',    jsonb_build_object('level', 'NONE', 'scope', 'ALL_TENANT_DATA'),
        'DYNAMIC_RBAC', jsonb_build_object('level', 'NONE', 'scope', 'ALL_TENANT_DATA'),
        'FACTORY_FLOW', jsonb_build_object('level', 'NONE', 'scope', 'ALL_TENANT_DATA')
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE is_system_default = FALSE
  AND NOT (module_permissions ? 'DYNAMIC_RBAC');
