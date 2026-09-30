# 🎓 Modul Pembelajaran: M0 WeMake Builder — Fondasi Konsol "ala Vercel"

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: DDD agregat baru, permission sistem vs data tenant, Flyway migrasi idempoten,
> Ktor plugin (host-vs-JWT), Ratchet utang file-size, fail-closed RBAC
> **Prasyarat**: Membaca [`PLAN-builder-console.md`](../plannings/PLAN-builder-console.md) §0–§6 dan
> teaching `teaching-b7-tenant-domain-pack.md`
> **Referensi Task**: TRD-PLAT-002 fase M0; discovery note M0

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: Funnel Discovery berakhir di handoff superadmin — klien tidak punya ruang kerja.
  Kita ingin tenant terasa seperti "project" ala Vercel: ada URL-nya, ada riwayat deployment-nya, dan
  "deploy" berarti *kunci versi + aktifkan*, bukan menyalin file ke server.
- **Analogi sederhana**: Tenant = sebuah *apartemen* yang sudah lama dihuni. M0 ini memasang
  *buku catatan renovasi* di pintu (tabel `builder.deployments`) dan menuliskan entri pertamanya
  ("kondisi saat serah terima" = `IMPORTED`) — tanpa menggeser satu pun furnitur (data operasional).
- **Hasil akhir M0**: `/builder` terbuka untuk pemilik project, menampilkan `Deployment #1 IMPORTED`,
  dan tiga pengaman baru terpasang: gerbang satu-owner-per-email, host-vs-JWT, dan kolom pin versi pack.

## 🧭 2. "Start dari Mana?" — Urutan Penulisan

1. **Langkah 0 — Discovery & audit draf ke kode** (`docs/plannings/discovery-M0-builder.md`).
   Rencana awal mengklaim `ServerRouteWiring` sudah ada — **ternyata tidak**. Audit §0 di plan
   menemukan 4 koreksi (F1–F4). *Pelajaran: jangan percaya klaim "sudah ada" sebelum `grep`.*
2. **Langkah 1 — Agregat domain** `core/domain/builder/BuilderDeployment.kt`.
   Enum `DeploymentStatus` didefinisikan **lengkap** sejak M0 (walau M0 hanya mengisi `IMPORTED`)
   supaya M2 tidak mengubah tipe yang sudah tersimpan.
3. **Langkah 2 — Permission sistem**: `MANAGE_BUILDER` di `AuthValueObjects.kt` + default
   `TENANT_ADMIN`. Enum ini lolos Uji Variabilitas karena ia *status sistem*, bukan kosakata vertikal.
4. **Langkah 3 — Repository interface + test domain** (fixture dua pack: `garment` & `bordir`).
5. **Langkah 4 — Migrasi V81** (aditif, idempoten), baru implementasi Postgres.
6. **Langkah 5 — Route + plugin** (gate fail-closed, host-vs-JWT).
7. **Langkah 6 — UI** (`BuilderShell` + pane, Clay), terakhir: `App.kt` satu cabang delegasi.

**Kenapa domain dulu?** Karena aturan (`IMPORTED wajib appBuild`, `ACTIVE wajib packVersion`) bisa
dites dalam milidetik tanpa DB. Kalau aturan ini ditulis di route, mengetesnya butuh server hidup —
dan aturan yang mahal dites akan jarang dites.

## 🧱 3. Bedah Kode & Mental Model

### 3.1 Invarian dua lapis: domain + database

`Deployment.init` menolak `IMPORTED` tanpa `appBuild`. V81 mengulanginya sebagai `CHECK` constraint:

```sql
CHECK (status <> 'IMPORTED' OR app_build IS NOT NULL)
```

**Mental model**: domain = penjaga saat *menulis lewat kode*; CHECK = penjaga saat *menulis lewat
jalan lain* (migrasi backfill, konsol DB). Dua lapis, bukan redundansi.

### 3.2 Migrasi idempoten dengan `ON CONFLICT DO NOTHING`

Backfill `Deployment #1` memakai `INSERT ... SELECT ... FROM tenants ON CONFLICT (tenant_id, number)
DO NOTHING`. Kalau `UNIQUE` tidak ada, menjalankan migrasi dua kali menduplikasi baris — dan test
`AC-M0-3` menuntut idempoten. **Jebakan umum**: lupa membuat id `deterministik` (`'dep-' || t.id || '-1'`).
Id acak membuat `ON CONFLICT` tidak pernah kena.


### 3.3 Pin versi: kolom baru, bukan resolver baru menggantikan lama

`tenants.domain_pack_version` **nullable, tanpa backfill**. Semua tenant lama tetap `NULL` =
perilaku B7 lama (pack *effective*). Ini adalah **Strangler Fig**: struktur baru dibangun di samping,
tenant lama identik (test paritas), dan M2 yang mengisi kolomnya saat deploy. `ResolveDomainPackVersionUseCase`
adalah use case **baru di samping** resolver effective — versi DRAFT ditolak keras, versi tak dikenal
`null` (Kontrak 4: tolak, jangan fallback senyap).

### 3.4 Host-vs-JWT dan carve-out superadmin (F2)

Aturan: user tenant yang membuka `<slug-lain>.wemakeerp.com` → 403. Kuncinya di
`TenantResolutionPlugin`, **di dalam** blok `if (principal.isTenantBound)` — jadi superadmin
(`tenantId = null`) otomatis lolos lewat jalur act-as yang sudah ada. *Jebakan yang nyaris terjadi*:
memasang cek ini di luar blok membuat superadmin terkunci dari semua subdomain — persis risiko yang
tercatat di plan §9.

### 3.5 Gate fail-closed + 403 yang teruji

`mayOpenBuilder(role)` — fungsi murni, teruji tanpa HTTP (pola `mayEditWithoutDecision`):

```kotlin
internal fun mayOpenBuilder(role: Role?): Boolean =
    role != null && (role == Role.PLATFORM_SUPERADMIN || role.defaultPermissions.contains(Permission.MANAGE_BUILDER))
```

`role != null` di depan = **fail-closed**: token rusak/peran tak bisa dihitung → 403, bukan 200.

### 3.6 Ratchet: `Application.kt` 698 → 664

`Application.kt` sudah di atas hard limit 500 (file-size-rules). Menambah satu baris registrasi route
tanpa kompensasi = pelanggaran Kontrak 2. Kompensasinya: blok onboarding (`/api/public/onboarding`)
diekstrak utuh ke `OnboardingRoutes.kt` (−39 baris) sebelum `builderRoutes(...)` ditambah (+1).
**Mental model**: file utang boleh disentuh asalkan *menyusut*, seperti membayar cicilan.

## 🗄️ 4. Keputusan Arsitektural & "Why"

| Keputusan | Alternatif | Kenapa ini |
|---|---|---|
| Tenant = project (tanpa agregat `Project`) | Agregat `Project` + `ProjectMember` | RBAC/RLS/entitlement tenant langsung terpakai; dua model kepemilikan = dua sumber kebenaran |
| Kolom pin versi | Tabel deployment sebagai sumber versi | Runtime butuh resolusi O(1) per request; `domain_packs` sudah ber-PK `(code, version)` |
| `IMPORTED` dengan `appBuild` | Paksa semua deployment punya pack version | Pack shipped (garment) bukan baris `domain_packs` — mengarang versi = data bohong |
| Permission enum `MANAGE_BUILDER` | Modul baru `BUILDER` | Builder bukan node kanvas, tidak dijual per modul, tidak masuk kuota (§5.2 rules) |
| Ekstraksi `OnboardingRoutes` sekarang | Tunda `ServerRouteWiring` ke M2 | Ratchet tidak menunggu; agregat onboarding memang berdiri sendiri |


## 🧪 5. Test — Apa yang Dibuktikan

| Test | Membuktikan |
|---|---|
| `BuilderDeploymentTest` | Invarian status + nomor per-tenant, dengan fixture **dua pack** (garment & bordir — Kontrak 6) |
| `RegisterTenantOwnerGateTest` | F3: email pemilik tenant lain ditolak; superadmin tidak dihitung pemilik; mode undangan tetap jalan |
| `ResolveDomainPackVersionTest` | Pin versi: persis, terkunci, tanpa fallback (fixture **non-default**: pack klinik) |
| `BuilderRouteGateTest` (7 test) | 200 owner · 403 tanpa izin · 401 tanpa token · **200 superadmin via subdomain (F2)** · 403 host & header tenant lain |
| `ModuleSchemaOwnershipTest` | `builder.deployments` terdaftar, ber-RLS, ber-grant `wemade_app` (pola `platformSchemas` baru) |
| Suite penuh server | 292 test: 1 gagal = `platformSuperadmin_shouldBeAbleToActAsAnyTenant` **pre-existing** (diverifikasi gagal juga di tree bersih sebelum perubahan) |

**Cek visual** (browser, server 8081 + web 3001): `/builder` sebagai superadmin demo menampilkan
Overview "wemade-demo.wemakeerp.com", badge `ACTIVE`, "Deployment aktif: IMPORTED",
"Pack: garment · versi bawaan platform"; menu M1/M2 tampil terkunci dengan badge fasenya.
Bukti: `tmp/.playwright-mcp/builder-m0-visual-check.png`.

## 🚧 6. Jebakan yang Nyata Terjadi (baca sebelum M1)

1. **`*/` di dalam KDoc** menutup komentar lebih awal (`/api/builder/*` di komentar → *unclosed
   comment*). Tulis "prefiks /api/builder" atau escape.
2. **`module()` default = repositori Postgres.** Test ktor yang lolos gerbang lalu menyentuh repo
   default akan *menggantung 60 dtk* menunggu koneksi. Selalu suntikkan set in-memory lengkap
   (lihat `installModule()` di `BuilderRouteGateTest`).
3. **ID fixture bentrok antar tenant** (`"dep-1"` dipakai dua tenant) membuat `removeAll { it.id == ... }`
   di fake repository menghapus baris tenant lain. Id fixture harus memuat identitas tenant.
4. **`pg_isready` menyesatkan** — Postgres Docker (`wemade-postgres`) ternyata sudah jalan 12 jam
   saat `pg_isready` melapor "no response". Verifikasi dengan `docker ps` sebelum menyimpulkan DB mati.
5. **Test pre-existing** (`platformSuperadmin_shouldBeAbleToActAsAnyTenant`) menggantung 60 dtk di
   environment ini sejak sebelum M0. Jangan "perbaiki" dengan mengubah assertion — verifikasi dulu
   dengan `git stash` lalu jalankan ulang di tree bersih.

## 🏆 7. Tantangan Mandiri

- [ ] **T1**: Tambahkan test: dua `save()` deployment aktif milik **tenant berbeda** bernomor sama
      harus sama-sama sukses (nomor itu per-tenant, bukan global).
- [ ] **T2**: Buat test yang menolak `blueprint_revision <= 0` lewat SQL langsung (bypass domain),
      lalu jelaskan mengapa lapis SQL-nya perlu meski domain sudah menolak.
- [ ] **T3**: Rancang (tanpa menulis kode) bentuk payload `propose_patch` untuk M1: apa yang membuat
      patch *dapat dibuang* tanpa mengubah draf? Bandingkan jawabanmu dengan Kontrak 5
      tenant-variability-rules (template disalin, dokumen membeku).
