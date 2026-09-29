# 🎓 Modul Pembelajaran: Satu PostgreSQL Schema per Modul (Jalur B, B8)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Batas modul yang terlihat di database, tanpa kehilangan FK, transaksi, JOIN, maupun RLS
> **Prasyarat**: `V16__separate_ops_schema.sql`, `teaching-b7-tenant-domain-pack.md`

---

## 💡 1. Kenapa schema, bukan database terpisah

ERP hidup dari rantai transaksi: deal → SPK → stok → invoice. Kalau tiap modul punya **database sendiri**, rantai itu
kehilangan foreign key, transaksi atomik (butuh saga/2PC), dan JOIN laporan. **Schema** memberi pemisahan namespace
yang sama rapinya, tapi semuanya tetap di satu database:

```sql
SELECT s.spk_number, d.title, i.invoice_number
FROM sampling_order.sampling_orders s
JOIN crm_sales.deals d ON s.deal_id = d.id
LEFT JOIN invoicing.invoices i ON i.deal_id = d.id;
```

## 🧱 2. Aturannya

| Aturan | Alasan |
|---|---|
| Nama schema = **kode modul** (`crm_sales`) | Tidak perlu tabel pemetaan kedua; modul hasil AI otomatis dapat `klinik_antrean` |
| Fitur tinggal di schema **modul induk** (washing → `operator_exec`) | Sama dengan kepemilikan RBAC & route (`ModuleFeatureRegistry`) |
| Tabel platform tetap di `public` | tenants/users dirujuk hampir semua modul |
| `search_path` **tidak** diubah | Semua referensi eksplisit; tidak ada resolusi nama yang bergantung konfigurasi koneksi |

Sumber kebenaran: `ModuleSchemaMap.kt`. `ModuleSchemaOwnershipTest` gagal bila ada tabel yang tidak terdaftar, atau
tabel modul yang tertinggal di `public`.

## 🛠️ 3. Migrasi V76

- `ALTER TABLE public.x SET SCHEMA <modul>` untuk 64 tabel. Index, constraint, sequence milik kolom, dan **policy RLS
  ikut pindah**; data dan id tidak disentuh.
- Grant `wemade_app` per schema, **termasuk** `ALTER DEFAULT PRIVILEGES`. V16 hanya mengaturnya untuk `public`, sehingga
  tabel baru di schema modul tidak otomatis ter-grant.
- `apply_tenant_rls_in(schema, tabel)`: `apply_tenant_rls` versi V1 memakai `format('%I', nama)` dan tidak bisa
  menerima `schema.tabel`. Kebijakannya identik.
- Diuji dulu dengan `BEGIN; … ROLLBACK;` di DB B sebelum Flyway menjalankannya.

## ⚠️ 4. Jebakan yang ditemukan

1. **SQL mentah**: enam repository punya nama tabel di string (`INSERT INTO trace_work_orders …`), plus satu subquery
   `FROM tech_packs` yang tidak ada di pemetaan awal. Diganti `${XTable.tableName}` (bernilai `schema.nama`), bukan
   literal. Referensi kolom `tech_packs.version` tetap sah selama FROM-nya berschema.
2. **Snapshot akses membaca DB lain.** `AccessSnapshotB6Test` mengambil daftar prinsipal dari `DB_NAME` dengan default
   `wemade_erp`, yaitu DB **repo A**, bukan DB B. Query-nya kini mencari tabel di schema mana pun
   (`to_regclass('dynamic_rbac.x')` lalu `public.x`), jadi set 690 prinsipal tetap sama dan keputusannya identik.
   Mengganti default DB-nya sengaja **tidak** dilakukan diam-diam, karena itu mengganti isi snapshot.
3. **Utang RLS lama ketahuan.** Sepuluh tabel ber-`tenant_id` sudah tanpa RLS **sebelum** B8. Mereka dicatat di
   ledger `RLS_DEBT` (ratchet: hanya boleh berkurang), tidak ditambal diam-diam di migrasi pemindahan.
4. **Test flaky yang tampak seperti regresi**: `PostgresCostingBenchmarkRepositoryIntegrationTest` membuat id tenant
   dari `nanoTime % 1e6`. Karena ada 310 tenant sisa run lama di DB dev, id sempat bertabrakan. Dibuktikan dengan run
   ulang (hijau 2×), lalu diganti suffix UUID.

## 🧪 5. Pembuktian

- `ModuleSchemaOwnershipTest` merah di HEAD, hijau setelah V76. Hasil `\dn`: 13 schema modul + `public` (10 tabel
  platform) + `ops`.
- Server 246 hijau; snapshot akses 690 keputusan identik.
- Server nyata (8081): 13 endpoint wemade-demo 200. Di browser :3001, CRM leads, Order Sampling (11 SPK), dan Invoice
  tampil dengan 0 error console.

## 🔒 6. Lanjutan: utang RLS dibayar (V77)

- `apply_tenant_rls_in` menyalakan RLS di 10 tabel `RLS_DEBT`; ledger kini kosong.
- **Isolasi diuji sebagai `wemade_app`**, bukan dengan melihat katalog policy saja. `TenantRlsIsolationTest` menjalankan
  `SET LOCAL ROLE wemade_app` untuk setiap tabel ber-`tenant_id`. Syaratnya: tenant tidak melihat satu pun baris tenant
  lain, tetap melihat **seluruh** barisnya sendiri, dan tanpa konteks tenant melihat 0 baris (fail-closed). Kemampuan
  test mendeteksi kebocoran dibuktikan dengan mematikan RLS satu tabel di transaksi yang di-rollback: satu baris tenant
  lain langsung terbaca.
- **Suite penuh dijalankan dengan `DB_APP_USER`** (role dev `wemade_app_dev LOGIN IN ROLE wemade_app`, hanya di DB dev):
  250 hijau, sama seperti mode owner. Test `whenAppUserConfigured_tenantQueriesReallyRunAsIt` memastikan
  `current_user` benar-benar role itu. Gradle harus `--no-daemon`, karena JVM test mewarisi env daemon, bukan shell.

**Bug nyata yang ditemukan**: `PostgresWashingBatchRepository` membuka `newSuspendedTransaction` sendiri. Di bawah
`DB_APP_USER`, transaksi itu memakai pool tenant **tanpa** `app.current_tenant_id`, sehingga daftar batch washing kosong
diam-diam dan penyimpanan ditolak. Selama ini tidak terlihat karena dev berjalan sebagai superuser. Diperbaiki lewat
`DatabaseFactory.dbQuery(tenantId)` dan dijaga test `noRepository_opensItsOwnTransaction_bypassingDatabaseFactory`.

**Superadmin di bawah RLS** (server 8081 dengan `DB_APP_USER`):

| Jalur | Hasil |
|---|---|
| act-as `wemade-demo` | 1 lead, 11 SPK |
| act-as `klinik-uji` | pack klinik, 0 lead (tidak ada kebocoran dari wemade-demo) |
| login persona + jabatan `role-sales-head` | token untuk tenant persona; menu sesuai jabatan; lintas tenant 403 |
| `/api/admin/tenants/{slug}` | 200 (pool owner, lintas tenant memang tugasnya) |

## 🧭 7. Sisa

- Produksi: `DB_APP_USER` **wajib** diisi saat cutover (role LOGIN turunan `wemade_app`); tanpanya RLS tidak berlaku.
- Default DB snapshot (`wemade_erp` → `wemake_erp`) → tulis ulang snapshot dengan sadar.
