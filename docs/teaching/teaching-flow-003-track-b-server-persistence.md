# 🎓 Modul Pembelajaran: Track B — Rute Serah Terima sebagai Data (Server & Persistensi)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Strangler Fig migration, PostgreSQL RLS, Flyway additive migration, Ktor route gating, Exposed ORM, fail-closed API design
> **Prasyarat**: Paham dasar DDD (port–adapter), pernah menyentuh migrasi Flyway, tahu apa itu enum dan kenapa ia liku
> **Referensi Task**: `docs/trd/TRD-FLOW-003-handover-routes-as-data.md`, `docs/plannings/PLAN-handover-routes-as-data.md` (Track B)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah Nyata**: Daftar rute serah terima karung (QC Rajut → Finishing, dst.) tadinya adalah **enum** `SackRoute`. Tenant bordir yang alurnya *Digitizing → Hooping* tidak bisa punya rute sendiri tanpa mengubah kode dan merilis ulang aplikasi. Ini pelanggaran **Uji Variabilitas** (`tenant-variability-rules.md` Kontrak 1): sesuatu yang boleh berbeda antar tenant/industri/admin **wajib data, bukan kode**.
- **Analogi Sederhana**: Enum itu seperti daftar ruang rapat yang dicat permanen di dinding gedung. Mau tambah ruang? Harus renovasi gedung (deploy). Data per tenant itu seperti buku reservasi: tiap perusahaan punya bukunya sendiri, tambah/hapus tinggal tulis.
- **Hasil Akhir**: Tabel `fulfillment.fulfillment_routes` (milik tenant), API `GET/PUT /api/tenant/fulfillment/routes` + `/route-settings` yang ber-kode (bukan enum), dan mesin submit karung yang memvalidasi rute **dari database** — bukan dari isi enum.

---

## 🧭 2. "Start dari Mana?" — Alur Penulisan Track B

1. **Langkah 0: Pastikan gerbang kontrak sudah merge (PR-0)**. Track B memakai port `HandoverRouteRepository` + codec `HandoverRouteSettingsCodec` dari `core` — dibekukan PR-0. **Pelajaran**: kerja paralel aman hanya kalau kontrak (port + parser + codec) disatukan lebih dulu, serial, oleh satu orang.
2. **Langkah 1: Migrasi aditif dulu (`V96`)** — tabel baru, tanpa seed, tanpa mengubah kolom lama. Kenapa dulu? Karena repositori dan route butuh tempat menyimpan; dan migrasi aditif bisa di-rollback dengan satu `DROP TABLE`.
3. **Langkah 2: `FulfillmentRoutesTable` (Exposed mirror)** — cermin SQL 1:1. Jangan pernah biarkan Exposed jadi sumber skema; Flyway yang punya.
4. **Langkah 3: `PostgresHandoverRouteRepository`** — implementasi port PR-0: `findByTenantId` (null = belum pernah menyimpan), `save` (menolak penghapusan rute yang pernah dipakai), `codesInUse`.
5. **Langkah 4: `FulfillmentRouteRoutes.kt` (file baru)** — GET/PUT `/routes` dan `/route-settings` **dipindah keluar** dari `FulfillmentTransferRoutes.kt` (tadinya 365 baris, di atas soft limit server 300). Ratchet: file lama wajib **mengecil** — hasilnya 356 baris pasca-rebase (file juga menerima perubahan submit dari A, tapi tetap lebih pendek dari sebelum disentuh).
6. **Langkah 5: Pasang ke mesin submit** — parameter `knownRoutes` milik `SubmitTransferUseCase` (disiapkan Track A) diisi lambda yang membaca database.
7. **Langkah 6: Test gerbang (B4)** — 403/200/400/409 + tenant non-garment, mengikuti pola `RouteGateTest`.

---

## 🧱 3. Bedah Kode Blok per Blok

### 3.1 V96 — migrasi yang jujur pada dirinya sendiri

```sql
CREATE TABLE IF NOT EXISTS fulfillment.fulfillment_routes (
    ...
    CONSTRAINT ck_fulfillment_route_code CHECK (code ~ '^[A-Z][A-Z0-9_]{0,39}$')
);
SELECT apply_tenant_rls_in('fulfillment', 'fulfillment_routes');
GRANT SELECT, INSERT, UPDATE, DELETE ON fulfillment.fulfillment_routes TO wemade_app;
```

- **CHECK kode di database** menduplikasi regex `HandoverRouteCode` di core. Pertahanan berlapis: domain menolak sebelum menulis, database menolak kalau ada jalan lain.
- **RLS via `apply_tenant_rls_in`**: baris hanya terlihat untuk `app.current_tenant_id` yang di-set `DatabaseFactory.dbQuery(tenantId)` per transaksi. **Jebakan umum**: koneksi owner (postgres) **melewati** RLS — test yang menyemai data via `transaction {}` polos harus `SET LOCAL app.current_tenant_id` dulu (lihat 3.5).
- **Tanpa seed (D4)**: tenant tanpa baris memakai template pack secara efektif. Menyemai baris berarti mengubah perilaku tenant yang sedang jalan — dosa paritas.
- **Uji migrasi dalam `BEGIN … ROLLBACK`**: seluruh SQL dijalankan dalam transaksi lalu `ROLLBACK` — sintaks & constraint tervalidasi tanpa jejak: `cat V96*.sql | docker exec -i wemade-postgres psql … -c 'BEGIN;' -f - -c 'ROLLBACK;'`.

### 3.2 `PostgresHandoverRouteRepository` — aturan di tempat datanya

Kunci `save()`: sebelum menulis, bandingkan kode lama vs baru; kode yang **hilang** dan **pernah dipakai** (`SELECT DISTINCT leg …`) menolak dengan pesan berpenanda `ROUTE_IN_USE`. Handler memetakannya ke **409**, sisanya 400. **Kenapa di repositori, bukan di handler?** Supaya pemanggil mana pun (handler sekarang, skrip besok) tidak bisa memakan riwayat.

Baca pun ketat: `HandoverRouteCode(row[leg])` — nilai tak sah **melempar**, tidak pernah diam menjadi rute lain (Kontrak 4: fallback senyap = data berubah tanpa jejak).

### 3.3 `FulfillmentRouteRoutes.kt` — kontrak §4.4, gerbang dua lapis

- **Gerbang lapis 1 (terpusat)**: `TenantRouteGatePolicy` — semua `/api/tenant/fulfillment/*` butuh VIEW (baca) / OPERATE (tulis) atas modul FULFILLMENT, **sebelum** body dibaca. Route baru otomatis terjaga karena kebijakannya per-prefix, bukan per-handler. Inilah kenapa `RouteGateTest` tetap hijau tanpa kita menyentuh ledger.
- **Gerbang lapis 2 (handler)**: PUT menuntut **MANAGE** (`canManageRoutes`) — paritas dengan PUT lama: topologi ACC ditentukan di sini, dan siapa pun yang bisa menulis konfigurasi bisa mematikannya.
- **Penolakan ketat**: `HandoverRouteSettingsCodec.decodeModes/decodeRoutes` melempar pada kode tak sah; handler menolak kode yang tidak ada di daftar tenant dengan 400. **Tidak ada `mapNotNull` diam-diam.**
- **Baris mode yatim** (mode untuk kode yang sudah tak ada di daftar): dibaca sebagai "tidak disetel" + `log.warn` — layar kerja tetap terbuka, kejadiannya tercatat (TRD Monitoring).
- `effectiveRoutes` dibuat `internal` dan dipakai bersama file transfer: **satu daftar rute, satu kebenaran**.

### 3.4 Menyambungkan mesin submit (`knownRoutes`)

`SubmitTransferUseCase` (Track A) menerima `knownRoutes: suspend (TenantId) -> TenantHandoverRoutes` dengan default `legacySackRoutes` — jembatan sementara. Track B memasang yang sungguhan di handler:

```kotlin
val submit = SubmitTransferUseCase(
    transfers, containers, routeConfigRepository,
    knownRoutes = { tenantId ->
        TenantHandoverRoutes.resolve(tenantId, handoverRoutes.findByTenantId(tenantId), tenant.pack.handoverRouteTemplate)
    }
)
```

Mental model: **resolve = salinan menang, template mengisi**. Tenant yang pernah menyimpan memakai salinannya; yang belum memakai template pack — tanpa menyimpan apa pun. Saat rebase ke `main` pasca-merge A1–A5, jembatan `SackRoute` di sisi server dihapus di PR B ini (template pack garment kini terisi) — persis catatan plan §Track B.

### 3.5 Test gerbang (B4) — belajar dari dua kegagalan pertamanya

- `putRoutes_rbacUncounted_returns403`: token membawa `customRoleId` yang tidak ada di repositori jabatan → keputusan tidak bisa dihitung → **403 fail-closed** (Kontrak 7). Test ini menjaga "jangan pernah jawab 200 karena ragu".
- Seed perjalanan karung untuk test 409 awalnya kena `row-level security policy` — `transaction {}` default tersambung pool `wemade_app` (RLS aktif!). Solusinya dua baris: `SET LOCAL app.current_tenant_id` sebelum `INSERT`. **Pelajaran**: tahu koneksi mana yang owner dan mana yang app itu setengah ilmu RLS; setengahnya lagi tahu kapan masing-masing dipakai.
- Test paritas `getRoutes_tenantWithoutStoredRows_...`: tenant segar tanpa baris tetap melihat dua rute warisan dari template pack — bukti "perilaku tenant lama identik" (AC-2) di sisi server.

---

## ⚖️ 4. Keputusan & Trade-off

| Keputusan | Kenapa | Alternatif yang ditolak |
|---|---|---|
| `POST /transfers` membangun `SubmitTransferUseCase` per permintaan | butuh `TenantContext` (pack → template) yang hanya ada di handler; konstruksinya murah | menyuntik `TenantRepository` menembus tiga lapis wiring — ribet untuk hasil sama |
| Mode disimpan lewat port `FulfillmentRouteConfigRepository` (ber-kode hasil A3) | satu penulis untuk `fulfillment_route_settings`, tanpa duplikasi | repositori mode terpisah buatan B — **dihapus saat rebase** begitu A3 merge |
| Baca mode diperketat (melempar, tidak skip) | A menitipkan komentar "Track B2 menggantinya"; baris tak sah = kerusakan data | tetap `mapNotNull` — fallback senyap yang justru jadi utang |
| GET tanpa cek wewenang di handler | gerbang terpusat sudah menuntut VIEW; paritas perilaku | VIEW eksplisit per handler = gerbang ganda yang menyesatkan pembaca |

---

## 🧪 5. Verifikasi yang Dilakukan (bukti)

- `:server:compileKotlin` + `:server:compileTestKotlin` hijau.
- `FulfillmentRouteRoutesTest` (8) + `RouteGateTest` + `RouteOwnershipTest` + `ModuleSchemaOwnershipTest` — **12/12 hijau segar**.
- Migrasi: dry-run `BEGIN…ROLLBACK` bersih, diterapkan Flyway, dijaga `ModuleSchemaOwnershipTest` (tabel terdaftar di `ModuleSchemaMap`, RLS + grant `wemade_app` terverifikasi).
- Regresi: 5 kelas yang gagal di suite penuh dijalankan di `main` dan di cabang B — hasil gagalnya **identik** (`AccessSnapshotB6Test`, pre-existing); sisanya flaky per-urutan, lolos saat terisolasi di kedua sisi.

---

## 🏆 6. Tantangan Mandiri

- [ ] Tambahkan test: PUT `/routes` mengirim `from`/`to` yang bukan `FlowNodeRef` sah — pastikan 400 dan pesannya berpath (`$.routes[0].from`).
- [ ] Bukti AC-6 penuh: setelah rute dinonaktifkan, perjalanan lama pada rute itu masih terbaca via `GET /transfers` (riwayat tidak tertelan).
- [ ] Riset: dua admin menekan PUT `/routes` bersamaan — transaksi mana yang menang, dan apakah hasilnya konsisten dengan `codesInUse`?

