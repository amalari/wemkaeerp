-- ==============================================================================
-- WeMade ERP — Bagan Organisasi, Hak Akses (RBAC) & Alur Pabrik menjadi modul
-- ==============================================================================
-- Ketiga layar ini sebelumnya berupa layar administrasi yang di-hardcode di menu dan hanya
-- dijaga sesi login. Akibatnya setiap orang yang bisa masuk melihat ketiganya dengan hak
-- penuh, dan platform tidak punya cara membedakan tenant yang berlangganan Alur Pabrik dari
-- yang tidak.
--
-- Setelah ketiganya menjadi `BusinessModule` berkategori GOVERNANCE, keduanya jadi mungkin:
-- wewenangnya diatur admin pabrik lewat matriks RBAC, dan penyambungannya diatur superadmin
-- lewat `tenant_module_entitlements`.
--
-- Seluruh isi migrasi ini aditif dan idempoten.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Jangan sampai tenant yang sudah ada kehilangan layarnya
-- ------------------------------------------------------------------------------
-- `granted_modules` punya dua arti yang berlawanan tergantung nilainya:
--
--   NULL          -> "apa pun yang diberikan paket" — ini kasus normal, dan otomatis ikut
--                    memuat ketiga modul baru tanpa disentuh sama sekali.
--   array eksplisit -> daftar yang MEMPERSEMPIT. Tenant seperti ini tidak akan pernah
--                    menerima modul yang dirilis kemudian.
--
-- Baris jenis kedua itulah yang berbahaya di sini: tanpa backfill, pabrik yang entitlement-nya
-- pernah disetel tangan akan kehilangan Bagan Organisasi-nya pada deploy pertama — dan
-- kehilangan itu tidak akan terlihat sebagai error apa pun, hanya sebagai menu yang mendadak
-- hilang.
--
-- Hak yang sudah ada tidak boleh menyusut karena sebuah rilis. Yang tadinya terbuka untuk
-- semua orang tetap terbuka; superadmin yang kemudian memutuskannya adalah keputusan sadar,
-- bukan efek samping migrasi.
UPDATE tenant_module_entitlements
SET granted_modules = (
        SELECT jsonb_agg(DISTINCT combined.m)
        FROM (
            SELECT jsonb_array_elements_text(granted_modules) AS m
            UNION
            SELECT unnest(ARRAY['ORG_CHART', 'DYNAMIC_RBAC', 'FACTORY_FLOW'])
        ) AS combined
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE granted_modules IS NOT NULL;

-- ------------------------------------------------------------------------------
-- 2. Jaga katalog modul tetap sejalan dengan enum
-- ------------------------------------------------------------------------------
-- Header V14 menyatakan aturannya: setiap modul yang ditambahkan ke `BusinessModule` harus
-- ditambahkan juga di sini, sampai katalog ini menjadi sumber kebenaran dan enum dipensiunkan.
--
-- `archetype_code` sengaja 'governance', bukan salah satu slot kapabilitas produksi. Modul
-- tata kelola tidak berdiri di lini produksi: tidak ada yang menyerahkan pekerjaan kepadanya
-- dan ia tidak menyerahkan pekerjaan ke siapa pun. Memaksakan slot seperti 'order_ingestion'
-- akan membuatnya muncul sebagai kandidat yang dapat ditukar di kanvas Alur Pabrik — persis
-- yang tidak boleh terjadi.
--
-- Harga 0: ketiganya bagian dari platform, bukan modul yang dijual terpisah. Nilainya tetap
-- dicatat agar pratinjau tagihan menjumlahkan katalog yang lengkap, bukan katalog berlubang.
INSERT INTO module_catalog_entries (
    id, module_id, archetype_code, display_name, description, category_code,
    scope_capability, stock_ownership, costing_behavior,
    accepted_input_types, produced_output_type,
    is_custom_plugin, origin_tenant_id, lifecycle_status, complexity_tier,
    base_monthly_price_idr, released_at
) VALUES
    ('mce-org-chart', 'org_chart', 'governance',
     'Bagan Organisasi & Karyawan',
     'Struktur divisi, jenjang jabatan, dan data karyawan pabrik.',
     'GOVERNANCE', 'GLOBAL_ONLY', 'non_stock_service', 'indirect_overhead',
     '[]', 'OrganizationStructure',
     FALSE, NULL, 'RELEASED', 'M', 0, CURRENT_TIMESTAMP),

    ('mce-dynamic-rbac', 'dynamic_rbac', 'governance',
     'Hak Akses & Jabatan (RBAC)',
     'Matriks wewenang per jabatan, penugasan modul ke divisi, dan pengujian persona.',
     'GOVERNANCE', 'GLOBAL_ONLY', 'non_stock_service', 'indirect_overhead',
     '[]', 'AccessPolicyMatrix',
     FALSE, NULL, 'RELEASED', 'L', 0, CURRENT_TIMESTAMP),

    ('mce-factory-flow', 'factory_flow', 'governance',
     'Alur Pabrik (Pipeline)',
     'Kanvas alur operasional tenant: urutan modul, penggantian nama, dan bypass.',
     'GOVERNANCE', 'GLOBAL_ONLY', 'non_stock_service', 'indirect_overhead',
     '[]', 'CustomTenantPipeline',
     FALSE, NULL, 'RELEASED', 'L', 0, CURRENT_TIMESTAMP)
-- Konflik dinilai pada `module_id`, bukan `id`: module_id-lah kunci alaminya — nilai yang sama
-- dipakai `CustomPipelineNode.moduleId` dan `BusinessModule.code` — sedangkan `id` hanya label
-- baris. Memakai `id` akan membuat migrasi ini gagal, bukan diam, bila katalognya sudah memuat
-- modul yang sama di bawah id berbeda.
ON CONFLICT (module_id) DO NOTHING;
