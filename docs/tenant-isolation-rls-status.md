# Status Isolasi Tenant & Row-Level Security

Catatan ini merekam temuan nyata tentang seberapa jauh isolasi antar-tenant benar-benar
ditegakkan hari ini, supaya tidak lagi diasumsikan lebih kuat daripada kenyataannya.

## Yang sudah berjalan

- Kebijakan RLS **ada** dan terpasang di `users`, `custom_roles`, `departments`,
  `employees`, dan `tenant_pipelines` (lihat `apply_tenant_rls()` di
  `V1__create_multi_tenant_schema.sql`).
- `DatabaseFactory.dbQuery(tenantId)` menerbitkan `SET LOCAL app.current_tenant_id`
  per transaksi, yaitu variabel yang dibaca kebijakan RLS.
- Semua repository Postgres juga memfilter secara eksplisit dengan
  `WHERE tenant_id = ?`.

## Temuan: RLS saat ini TIDAK aktif secara efektif

Aplikasi terhubung sebagai role `postgres`:

```
 rolname  | rolsuper | rolbypassrls
----------+----------+--------------
 postgres | t        | t
```

PostgreSQL **selalu melewati** kebijakan RLS untuk role `SUPERUSER` atau `BYPASSRLS`.
Tabel-tabel juga masih `relforcerowsecurity = false`. Artinya:

> **Isolasi antar-tenant hari ini sepenuhnya bergantung pada klausa `WHERE tenant_id = ?`
> di kode repository, bukan pada RLS.** Kebijakan RLS berperan sebagai dokumentasi niat,
> belum sebagai jaring pengaman database.

Konsekuensinya: satu query yang lupa memfilter `tenant_id` akan membocorkan data
antar-tenant tanpa dihalangi database.

## Kenapa belum diaktifkan di perubahan ini

Mengaktifkan penegakan RLS bukan sekadar satu perintah `ALTER TABLE`, dan mengaktifkannya
setengah jalan berisiko membuat aplikasi terkunci dari datanya sendiri. Ada jalur query
yang secara sah harus berjalan **tanpa** konteks tenant, misalnya:

- `PostgresTenantRepository.findBySlug()` — dipakai untuk *menentukan* tenant mana yang
  sedang meminta, jadi jelas belum punya `app.current_tenant_id`. (Tabel `tenants`
  memang sengaja tidak diberi RLS.)
- Pencarian user lintas-tenant pada alur login Google (berdasarkan email).

Jika RLS ditegakkan sebelum setiap jalur ini diaudit, query tanpa konteks tenant akan
mengembalikan nol baris, bukan error — kegagalan senyap yang jauh lebih sulit didiagnosis.

## Langkah untuk benar-benar menegakkan RLS

Ini keputusan deployment (menyangkut kredensial), karena itu belum dieksekusi:

1. Buat role aplikasi khusus **tanpa** `SUPERUSER` dan **tanpa** `BYPASSRLS`, mis.
   `wemade_app`, lalu beri hanya privilese DML yang diperlukan.
2. Arahkan aplikasi ke role tersebut lewat env `DB_USER` / `DB_PASSWORD`
   (`DatabaseFactory` sudah membacanya; migrasi Flyway sebaiknya tetap dijalankan
   dengan role pemilik skema yang lebih tinggi).
3. Tambahkan `ALTER TABLE <tabel> FORCE ROW LEVEL SECURITY;` agar pemilik tabel pun
   ikut terikat kebijakan.
4. Audit setiap pemanggilan `DatabaseFactory.dbQuery()` **tanpa** argumen `tenantId`.
   Setiap pemanggilan seperti itu harus dipastikan menyentuh tabel tanpa RLS
   (`tenants`, `flyway_schema_history`) atau dipindahkan ke koneksi khusus.
5. Tambahkan test integrasi yang berjalan sebagai role aplikasi dan membuktikan bahwa
   query **tanpa** filter `tenant_id` mengembalikan nol baris milik tenant lain.

Sampai langkah-langkah itu selesai, perlakukan filter `tenant_id` di repository sebagai
satu-satunya kontrol isolasi, dan review setiap query baru dengan asumsi tersebut.
