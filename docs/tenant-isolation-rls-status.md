# Status Isolasi Tenant & Row-Level Security

Catatan ini merekam temuan nyata tentang seberapa jauh isolasi antar-tenant benar-benar
ditegakkan hari ini, supaya tidak lagi diasumsikan lebih kuat daripada kenyataannya.

## Yang sudah berjalan (diperbarui 2026-09-30, pasca-B8)

- Kebijakan RLS terpasang di tabel ber-`tenant_id` `public` (`users`, `custom_roles`,
  `departments`, `employees`, `tenant_pipelines` — `apply_tenant_rls()` di
  `V1__create_multi_tenant_schema.sql`) dan, sejak `V77__rls_debt.sql`, di **sepuluh tabel
  yang sempat terlewat** lintas schema lewat `apply_tenant_rls_in('<schema>', '<tabel>')`.
- `DatabaseFactory.dbQuery(tenantId)` menerbitkan `SET LOCAL app.current_tenant_id`
  per transaksi, yaitu variabel yang dibaca seluruh kebijakan tersebut.
- Semua repository Postgres tetap memfilter eksplisit `WHERE tenant_id = ?` (lapis kedua).
- Role aplikasi `wemade_app` (tanpa SUPERUSER/BYPASSRLS) dibuat di V16 bersama schema `ops`.
  `DatabaseFactory` membuka **pool terpisah ber-scope tenant** bila `DB_APP_USER`/
  `DB_APP_PASSWORD` disetel; migrasi Flyway tetap berjalan di koneksi owner.
- Batas schema teruji: tabel platform di `ops` (V16), tabel modul di schema `<kode modul>`
  (V76) — dijaga `OpsSchemaBoundaryTest` dan `ModuleSchemaOwnershipTest`.
- `TenantRlsIsolationTest` membuktikan isolasi per tabel: transaksi ber-`SET LOCAL ROLE
  wemade_app` dengan konteks tenant T tidak boleh membaca satu baris pun milik tenant lain,
  untuk **semua** tabel ber-`tenant_id` di schema modul + `public`.

## Temuan: kebijakan sudah lengkap, penegakan bergantung satu variabel env

Kebijakan dan testnya sudah ada — yang belum, **koneksi server belum melewatinya**.
Bila `DB_APP_USER`/`DB_APP_PASSWORD` kosong, `DatabaseFactory` mencetak warning dan seluruh
kerja ber-tenant berjalan lewat koneksi owner (`postgres`: superuser, `BYPASSRLS`):

> **Selama `DB_APP_USER` belum disetel di suatu lingkungan, isolasi antar-tenant di
> lingkungan itu bergantung pada klausa `WHERE tenant_id = ?` di kode repository.**
> `TenantRlsIsolationTest` tetap sah tanpa env karena ia memakai `SET LOCAL ROLE wemade_app`
> di dalam transaksinya — tetapi pool yang benar-benar dipakai server belum terikat kebijakan.

`FORCE ROW LEVEL SECURITY` sengaja **tidak** dipakai: jalur owner (login, `/api/admin`,
platform, seluruh tabel `ops`) memang harus lewat dari RLS; memaksa owner terikat kebijakan
berisiko mengunci aplikasi dari datanya sendiri. Batasnya ditarik lewat pemisahan koneksi
(owner vs `wemade_app`), bukan lewat FORCE.

## Langkah penutup (sisa R0 → item A0 di `plannings/PLAN-discovery-blueprint-prototype-studio.md`)

1. Setel `DB_APP_USER=wemade_app` + `DB_APP_PASSWORD` di `.env` lokal, `.env.example`,
   `docker-compose.yml`, dan lingkungan deployment (role dibuat di V16).
2. Audit setiap `DatabaseFactory.dbQuery()` **tanpa** argumen `tenantId` — sah hanya bila
   menyentuh koneksi owner: tabel `tenants` (mis. `findBySlug` untuk *menentukan* tenant),
   `flyway_schema_history`, pencarian user lintas-tenant saat login (mis. Google), dan
   tabel `ops` (pekerjaan platform).
3. Tabel ber-`tenant_id` baru wajib `apply_tenant_rls_in('<schema>', '<tabel>')` (pola V77)
   sehingga otomatis masuk jangkauan `TenantRlsIsolationTest`; tabel `ops` baru justru
   **tidak** boleh diberi grant ke `wemade_app` (tetap owner-only, dijaga
   `OpsSchemaBoundaryTest`).

Sampai langkah 1 selesai di suatu lingkungan, perlakukan filter `tenant_id` di repository
sebagai satu-satunya kontrol isolasi di lingkungan itu, dan review setiap query baru
dengan asumsi tersebut.
