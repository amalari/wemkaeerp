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

---

# Lampiran M1 (2026-09-30) — Chat tersimpan + `propose_patch` + panes

Implementasi M1 dilampirkan di dokumen yang sama karena mesin dan kontraknya sambungan langsung.
Discovery: [`docs/plannings/discovery-M1-builder-chat.md`](../plannings/discovery-M1-builder-chat.md).

## Arsitektur yang terbentuk
- **Domain** (`core/domain/builder/`): `BuilderChat.kt` (entitas + port `BuilderAgent.proposePatch` +
  `BuilderChatRepository`), `BuilderChatUseCases.kt` (`SendBuilderMessageUseCase` — pesan USER dicatat
  *dulu* agar kegagalan agent tidak memakan cerita user; `ApplyDraftPatchUseCase` — validator menilai
  ulang, draf terkunci ditolak dengan `DraftLockedException`).
- **Agent**: `DiscoveryBackedBuilderAgent` membungkus `DiscoveryAgent` yang sudah teruji (Koog/fallback)
  — patch = draf penuh, **tidak pernah** ditulis agent; "Terapkan" = aksi manusia (plan §4).
- **Persistensi**: V82 (`builder.conversations` satu-per-tenant `UNIQUE(tenant_id)`, `builder.chat_messages`
  CHECK `role='AGENT' OR proposed_draft IS NULL`) + `PostgresBuilderChatRepository` + RLS V76.
- **API**: `GET/POST /api/builder/chat`, `POST /api/builder/chat/apply`, `GET /api/builder/draft`
  (envelope `summaryObj` Fase D) — semua di belakang `mayOpenBuilder` fail-closed; `module()` menerima
  `builderChatRepository`/`builderAgent` untuk test in-memory.
- **UI**: `BuilderChatPane` + `BuilderDesignPanes` (Modules/Data Flow/Prototype reuse penuh
  `ModuleMapPane`/`DataFlowPane`/`PrototypeRenderer` via `DiscoveryDraftUi.fromJson`).

## Pitfall baru (lanjutan daftar M0)
6. **`/*` di dalam KDoc membuka komentar bersarang** — Kotlin mendukung komentar bersarang, jadi
   `` `GET /api/builder/*` `` di KDoc membuat komentar tak pernah tertutup ("Unclosed comment" di
   baris paling akhir file, bukan di baris yang bermasalah). Ganti dengan `...`.
7. **Ratchet dikompensasi dengan pemadatan, bukan penghapusan logika**: `Application.kt` 664 → 663
   dengan memadatkan argumen `crmRoutes`/`dealRoutes` (gaya `prospectRoutes`) — nol baris bersih.
8. **Regex pengambil `messageId` di test HTTP** harus berankor pada prefiks id (`"id":"msg-…"`) —
   dokumen draf di patch juga punya kunci `"id"` (kode modul) dan `.last()` akan menangkap id modul,
   bukan id pesan.
9. **`jsonb` Exposed butuh serializer** (plugin serialisasi yang sengaja tidak dipakai repo) — simpan
   daftar ringkasan sebagai `TEXT` berisi JSON array + konversi manual di repository.
10. **Demo mode web mem-mint token offline** (`jwt-offline-token-…`) — server sungguhan menolaknya,
    sehingga cek visual UI Builder hanya sampai lapis layout (shell 9 menu ✓, pane Chat ✓). End-to-end
    chat live menunggu login asli (OAuth) / tenant uji — item belum terverifikasi bersama item M0.

## Verifikasi M1
- Kompilasi: JVM ✓, WasmJs ✓, JS ✓ (Android: SDK tak tersedia di mesin ini — same as M0).
- Test: `core` hijau penuh (termasuk 6 test baru `BuilderChatUseCaseTest`, fixture garment +
  `bordir-uji`), `server` 296 test hijau penuh (termasuk 5 test baru gate chat: 200 send/apply,
  409 double-apply, 403 tanpa izin), `app jvmTest` hijau.
- Live: V82 ter-apply Flyway di DB dev (`builder.conversations`/`chat_messages` terbentuk);
  `curl` tanpa token → 401 fail-closed; visual shell + pane Chat dicek dengan mata (lihat pitfall 10).

---

# Lampiran M2 (2026-09-30) — Deploy, rollback + gerbang data, Antrian Pembuatan (domain)

Discovery: [`docs/plannings/discovery-M2-builder-deploy.md`](../plannings/discovery-M2-builder-deploy.md).

## Arsitektur yang terbentuk
- **Domain**: `BuildRequest.kt` (FR-M2-4: status QUEUED→SHIPPED, taut quoteId/deployment) +
  `DeploymentUseCases.kt` — `DeployTenantUseCase` (validasi → pack kustom = BLOCKED_ON_BUILD +
  BuildRequest QUEUED per modul aktif; pack shipped = ACTIVE + pin versi + kunci draf + TRIAL→ACTIVE)
  dan `RollbackDeploymentUseCase` (append-only: aktif → ROLLED_BACK, versi N−1 diaktifkan ulang;
  gerbang data v1 = rollback penurunan `blueprintRevision` ditolak bila tenant punya data, `force`
  = arsip eksplisit yang tetap ter-audit).
- **Persistensi**: V83 `builder.build_requests` (RLS + grant) + `Postgres/InMemory` repo; terdaftar
  di `ModuleSchemaMap.platformSchemas` (uji kepemilikan schema menuntutnya).
- **API**: `BuilderDeploymentRoutes.kt` — agregat terpisah dari chat (plan §6); activate/rollback
  ter-audit (`AuditAction.BUILDER_DEPLOYMENT_*`). Dipasang **di dalam** `builderRoutes(...)` dengan
  default Postgres — `Application.kt` tetap 663 baris (ratchet, nol baris baru).
- **UI**: `BuilderDeploymentsPane` + menu Deployments terbuka; Deploy/Rollback dengan badge status.

## Pitfall baru (lanjutan daftar M0–M1)
11. **`copy()` data class menjalankan `init` pada nilai antara.** `imported.supersede().copy(packVersion = v)`
    meledak: `supersede()` membuat objek SUPERSEDED-tanpa-versi dulu, dan `init` invarian berjalan di
    situ. Gabungkan semua perubahan dalam **satu** `copy(status = …, packVersion = …)`.
12. **`server/build.gradle.kts` menautkan `core-jvm.jar` lama via `compileOnly(files(...))`** — jar
    membayangi source `:core` terbaru di classpath test. Saat domain `:core` berubah dan test server
    berperilaku "masih kode lama", jalankan `./gradlew :core:jvmJar` dulu sebelum menyimpulkan apa pun.
13. **Snapshot IMPORTED pra-Builder tidak punya versi** — saat di-supersede ia mewarisi nomor versi
    yang baru dikunci (keadaan yang sama, kini resmi terkunci); invarian "SUPERSEDED wajib bawa versi"
    tetap terpenuhi dua lapis (domain + SQL CHECK).
14. **Route agregat baru = komposisi, bukan baris baru di `Application.kt`** — pasang route anak di
    dalam fungsi agregat induk (`builderRoutes` memasang `builderDeploymentRoutes`) dengan default
    Postgres agar produksi aman dan test menyuntik in-memory.

## Verifikasi M2 (lingkup turn ini)
- Kompilasi JVM/WasmJs/JS ✓ (Android: SDK tetap tidak tersedia di mesin ini).
- Test: core hijau penuh (7 test deploy/rollback termasuk skenario atas snapshot IMPORTED), server
  310 test — satu-satunya kegagalan adalah route `/api/tenant/help/ask` milik **pekerjaan paralel**
  (fitur help/tutorial yang belum selesai di tree, bukan lingkup Builder).
- Live: V83 ter-apply (`builder.build_requests` terbentuk); `POST /deployments` & `/rollback` tanpa
  token → 401 fail-closed.
- **Belum selesai dari M2** (turn berikutnya): FR-M2-5 billing manual, FR-M2-6 penuntasan F1
  (`Application.kt` ≤600), FR-M2-7 flag daftar publik, route Antrian superadmin + pane-nya,
  Docker/Caddy (prasyarat DNS wildcard `*.wemakeerp.com`).

---

# Lampiran M2b (2026-09-30) — F1 selesai, daftar publik bergerbang, tagihan harga-terkunci

Menutup tiga sisa M2 yang bisa dikerjakan tanpa prasyarat non-kode.

## FR-M2-6 — F1 tuntas: `Application.kt` 663 → 379 baris
- Blok auth publik (`/api/public/auth` — Google, demo/persona, `/me`) **dipindah utuh** ke
  `routes/PublicAuthRoutes.kt` (321 baris), bersama tiga helper privatnya (`authSessionJson`,
  `resolvePersonaUser`, `platformRoleFor`). Pemindahan sengaja baris-per-baris: perilaku route tidak
  berubah, hanya tempatnya.
- Hasilnya jauh di bawah target plan (≤600), tetapi masih di atas soft limit `server/**` (300) — jadi
  langkah berikutnya, bila file itu disentuh lagi, adalah memindahkan blok route tersisa ke
  `ServerRouteWiring`.
- Pelajaran import: `call.request.header(...)` butuh `io.ktor.server.request.*`, dan `TenantId`/
  `TenantSlug` ada di `domain.tenant.*` — file hasil pemindahan wajib memuat keduanya.

## FR-M2-7 — daftar publik bergerbang, bawaan tertutup
- `WEMADE_PUBLIC_SIGNUP` (kosong = tertutup) menggerbangi `POST /api/public/onboarding/register`.
- `GET /api/public/onboarding/config` mengabarkan keadaan gerbang supaya klien tidak merender form
  yang akan ditolak server. Pemeriksaan slug (`/check-subdomain`) tetap hidup: flag hanya menggerbangi
  **pendaftaran**, bukan seluruh alur onboarding.
- 4 test (`OnboardingSignupFlagTest`): tertutup → config `false`, register 403, slug check tetap 200;
  terbuka → 201. Arah gagal diuji lebih dulu, karena yang berbahaya bukan form yang tidak muncul,
  melainkan tenant anonim yang tercipta diam-diam.

## FR-M2-5 — tagihan langganan dari harga yang **dibekukan** (v1, tanpa PDF)
KDoc `GetTenantBillingPreviewUseCase` sudah menyatakan syaratnya sendiri: *"This is a preview, not an
invoice… Real billing requires the price to be locked per subscription"*. Karena itu yang dibangun
lebih dulu adalah **pembekuannya**, bukan renderernya:

- `SubscriptionInvoice` menolak ada tanpa baris, tanpa periode `YYYY-MM`, atau berstatus `PAID` tanpa
  `paidAt`. `IssueSubscriptionInvoiceUseCase` **menyalin** baris preview ke invoice (snapshot),
  menolak periode ganda yang belum VOID, dan menolak invoice kosong — tagihan Rp 0 yang tidak
  dijelaskan lebih membingungkan daripada kegagalan yang terlihat.
- `ConfirmSubscriptionPaymentUseCase` idempoten untuk invoice yang sudah PAID (klik dua kali bukan
  error), tetapi menolak invoice VOID (keputusan yang sudah dibatalkan).
- Port `TenantBillingPreviewSource` berupa **fun interface**, bukan tipe konkret preview use case:
  yang harus dibuktikan adalah "harga naik setelah terbit tidak mengubah invoice", dan itu hanya bisa
  diuji bila sumber harga dapat berubah di tengah test — kelas preview bersifat `final`.
- Persistensi V85 `builder.subscription_invoices` (RLS + grant, `lines_json` TEXT berisi snapshot,
  CHECK `PAID ⇒ paid_at`). Repo Postgres **hanya** meng-update kolom status/bayar saat `save()`:
  menimpa baris & total akan mengubah arti dokumen yang sudah dikirim ke tenant.
- Route `BuilderBillingRoutes`: superadmin menerbitkan & mengonfirmasi (ter-audit
  `builder_invoice_issued` / `builder_invoice_paid`), tenant membaca miliknya sendiri dari
  `tenantContext` — bukan dari parameter query yang bisa dipalsukan.
- **Ditunda sadar**: PDF (FR-M2-5b). Renderer invoice yang ada terikat dokumen penjualan tenant,
  sedangkan ini dokumen platform; memaksakannya berarti mengarang pemetaan domain. JSON lengkap sudah
  keluar dari endpoint, jadi UI dapat mencetak/mengunduh tanpa menunggu keputusan itu.

## FR-M2-4 (UI) — pane Antrian Pembuatan
`BuilderBuildQueuePane` membuka antrean lintas tenant dan **menampilkan penolakan server apa adanya**:
tenant yang membukanya menerima `403 Hanya superadmin platform`, bukan daftar kosong yang menyiratkan
"tidak ada pekerjaan".

## Pitfall baru (lanjutan)
15. **`LazyColumn` ber-`Modifier.weight(1f)` di dalam induk `verticalScroll` berukuran nol.** Shell
    Builder membungkus pane dengan `verticalScroll`, sehingga tinggi maksimum tak terbatas dan bobot
    `weight` jatuh ke 0 — kartu ada di pohon komposisi, tidak pernah terlihat, dan **kompilasi hijau**.
    Tertangkap hanya karena diperiksa dengan mata: pane Deployments tampak "kosong tanpa error".
    Perbaikan: `Column` biasa (induknya toh sudah bisa di-scroll), bukan daftar bersarang.
16. **`copy()` data class menjalankan `init` pada nilai antara** — kembali menggigit saat menyusun
    `SubscriptionInvoiceLine`.
17. **`call.request.header()` butuh `io.ktor.server.request.*`**, bukan `io.ktor.server.application.*`.
    Gejalanya "Unresolved reference 'header'" yang tampak seperti salah versi Ktor.
18. **Perancah verifikasi statis harus threading + mengirim isi berkas.** `TCPServer` single-thread
    tersangkut keep-alive browser (halaman menggantung tanpa error), dan `send_head()` tanpa menulis
    body membuat browser menunggu selamanya. Keduanya menghabiskan waktu lebih lama daripada bug
    aplikasinya — periksa perancah dulu sebelum menuduh aplikasi.
19. **Server dev lama memegang port = instance baru gagal bind sambil tetap mencetak "started".**
    `curl` lalu dilayani kode lama (route baru tampak 404). Sebelum menyimpulkan "route saya tidak
    terpasang", pastikan dulu proses mana yang memegang port (`lsof -nP -iTCP:8081 -sTCP:LISTEN`).

## Bukti verifikasi M2b
- Test: `SubscriptionBillingUseCaseTest` (7), `SubscriptionInvoiceLinesCodecTest` (2),
  `OnboardingSignupFlagTest` (4), `BuilderRouteGateTest` 21 (deploy 3 + antrean 3 + tagihan 4).
- Live (server lokal, JWT asli dari `POST /api/public/auth/demo`):
  - `GET /api/public/onboarding/config` → `{"publicSignupEnabled":false}`; `register` → **403**.
  - `POST /api/builder/billing/invoices?tenantId=ten-bordir-uji&period=2026-09` → **200**,
    `INV-2026-09-001`, 9 baris beku, total **Rp 3.400.000**; periode yang sama diulang → **409**.
  - `…/confirm?note=transfer BCA 30 Sep` → **200 PAID**; baris V85 menunjukkan `paid_at` + `paid_note`;
    `audit_logs` mencatat kedua aksi sebagai `PLATFORM_SUPERADMIN`.
  - Deploy & rollback tanpa token → **401**.
- Visual (Playwright; sesi JWT asli disuntik ke `localStorage`, bundle Wasm segar): pane **Billing**
  menampilkan `INV-2026-09-001 · PAID · 9 modul · Rp 3.400.000`; pane **Deployments** menampilkan
  `#1 IMPORTED · build pre-builder` dengan Deploy aktif dan Rollback nonaktif (memang belum ada
  deployment ACTIVE); pane **Antrian** kosong untuk superadmin dan menampilkan pesan 403 untuk tenant.

---

# Lampiran M2c (2026-10-01) — FR-M2-5b: PDF tagihan platform

Ini penutup sisa M2 yang bisa dikerjakan tanpa prasyarat luar. Yang tersisa setelah lampiran ini
hanya **Docker + Caddy wildcard** (menunggu akses DNS `*.wemakeerp.com`).

## Keputusan pertama: dokumen ini **bukan** dokumen tenant

`InvoicePdfRenderer` yang sudah ada mencetak penjualan **tenant** — kop profil tenant, NPWP tenant,
prefiks nomor dokumen tenant. Tagihan langganan arahnya terbalik: platform menagih tenant. Memakai
renderer itu berarti mencetak dokumen dengan pemilik yang salah, dan itu bukan bug kosmetik: dokumen
uang yang salah kopnya akan dipertanyakan keuangan klien.

Karena itu dokumen ini dibuat sebagai **dokumen platform**, meniru pola yang sudah terbukti di Fase D
(lembar blueprint) — bukan pola invoice tenant:

```
core/.../domain/builder/print/
├── SubscriptionInvoicePdfDocument.kt   # ISI: nomor, periode, baris, total (rupiah sudah berlabel)
├── SubscriptionInvoiceSheet.kt         # BENTUK: peran baris + perataan, baris yang sudah ditempatkan
└── SubscriptionInvoiceSheetLayout.kt   # TATA LETAK: aliran, kolom kanan, pemenggalan halaman

server/.../infrastructure/pdf/
├── PdfSheetPainter.kt                  # PRIMITIF: teks, kotak, garis, watermark, encodeSafe
├── SubscriptionInvoicePdfRenderer.kt   # RUPA: peran baris → ukuran, bobot, keabu-abuan
└── PrintLabels.kt                      # label waktu cetak (satu format untuk semua dokumen)
```

Empat lapis itu bukan pembagian administratif; masing-masing punya test yang berbeda:

| Lapis | Diuji oleh | Pertanyaan yang dijawab |
|---|---|---|
| Isi | `SubscriptionInvoicePdfDocumentTest` (6) | Apakah angkanya yang **beku**? Apakah watermark sesuai status? |
| Tata letak | `SubscriptionInvoiceSheetLayoutTest` (7) | Apakah angkanya lurus ke margin kanan? Apakah halaman lanjutan berjudul? |
| Rupa | `SubscriptionInvoicePdfRendererTest` (4) | Apakah isinya benar-benar sampai ke kertas (ekstraksi teks PDFBox)? |
| Gerbang | `BuilderRouteGateTest` +7 | 401 tanpa sesi, 403 tanpa izin, **404** untuk tagihan tenant lain, 200 lewat tiket |

## Watermark menyebut status, dan kepekatannya berbeda

`BELUM DIBAYAR` (0,80) dan `DIBATALKAN` (0,72) dicetak lebih gelap daripada `LUNAS` (0,88) — bukan
soal selera: dua yang pertama yang mencegah orang mentransfer ke tagihan yang salah. PDF ini beredar
lewat WhatsApp/email dan difoto; invoice lunas yang masih terlihat "belum dibayar" akan ditagih dua
kali.

## Kolom angka: satu-satunya alasan tata letaknya berbeda dari blueprint

Blueprint hanya satu kolom. Tagihan butuh kolom harga yang **berakhir** di margin kanan, dan itu
dihitung dari lebar teks yang sudah diukur (`InvoiceTextLayout.measureWidthMm10`) — bukan ditempel
dengan jarak tetap. Test-nya membandingkan bidang, bukan tampilan:

```kotlin
amounts.forEach { line -> assertTrue(line.rect.right.value >= rightLimit - 2) }   // lurus ke margin
labelLines.zip(amountLines).forEach { (label, amount) ->
    assertTrue(label.rect.right.value <= amount.rect.x.value)                       // tidak bersinggungan
}
```

Nama modul terpanjang pun tidak menabrak kolom angka: lebar sisi kanan direservasi lebih dulu, sisa
lebar jadi milik label.

## Ratchet yang ikut terkumpul

| Berkas | Sebelum | Sesudah | Kenapa |
|---|---|---|---|
| `BlueprintPdfRenderer.kt` | 194 | **117** | primitif pindah ke `PdfSheetPainter` |
| `DiscoveryBlueprintPdfRoutes.kt` | 146 | **141** | label waktu pindah ke `PrintLabels` |
| `BuilderBillingRoutes.kt` | 141 | 272 | + 2 rute cetak & gerbangnya (masih di bawah soft 300) |

`encodeSafe` (pemetaan glyph yang absen dari Nunito) sengaja **tidak** disalin ke renderer kedua:
daftar itu pengetahuan hasil penyelidikan font, dan dua salinan berarti satu perbaikan akan tertinggal
di salah satunya.

`SubscriptionInvoiceSheetLayout.kt` (337 baris) melewati **soft 250** untuk `core/**`. Sudah ditinjau:
satu tanggung jawab (bagaimana satu lembar tagihan ditata di kertas), satu nama yang jujur, dan ~90
barisnya KDoc keputusan. Ambang **hard 400** belum terlewati; kalau nanti terlewati, yang dipecah
adalah mesin penempat barisnya (`PageBuilder`) menjadi `SheetFlowPageBuilder`, bukan file ini dibelah
sembarang.

## Pitfall baru (lanjutan daftar M0–M2b)

20. **Tiket cetak hanya berlaku untuk path berakhiran `.pdf`.** `PrintTicketService.decode` menolak
    tiket bila `requestPath` tidak berakhiran `.pdf`. Rute pertama di sini bernama `/{id}/pdf`; tiketnya
    terbit dengan benar, lalu **seluruh** permintaan ditolak plugin sebagai "tiket tidak sah" (401) —
    dari luar tampak seperti tiket kedaluwarsa, padahal salah nama rute. Rute kini `/{id}/invoice.pdf`,
    mengikuti `blueprint.pdf` / `labels.pdf` / `spk-card.pdf`. Yang menangkapnya: test rute, bukan
    kompilasi dan bukan test renderer.

21. **Proxy verifikasi: `Host: 127.0.0.1` diperlakukan sebagai subdomain tenant.** Aturan host-vs-JWT
    mengecualikan `localhost`, tidak mengecualikan alamat loopback IP, sehingga setiap permintaan lewat
    proxy dijawab **403** sementara `curl` langsung ke API dijawab 200. Proxy harus menulis ulang
    `Host: localhost:8081`, bukan membuang header Host. Produksi tidak terdampak (Host selalu nama
    domain), tapi ini jebakan nyata untuk alat verifikasi lokal.

22. **Menyuntik sesi ke `localStorage` harus lengkap.** Sesi tanpa larik `permissions` **dihapus sendiri**
    oleh aplikasi: permintaan tetap dikirim dengan `X-Tenant-Slug`, tetapi **tanpa** `Authorization`,
    lalu aplikasi mendarat di `/login`. Yang benar: suntikkan utuh keluaran
    `POST /api/public/auth/demo` (token + user + permissions + tenantSlug), bukan rakitan setengah.

23. **Aplikasi memanggil API di origin yang sama.** Perancah verifikasi statis harus mem-proxy `/api`
    ke server Ktor. Tanpa itu permintaan `/api/…` dilayani `index.html` (200 HTML), aplikasi membaca
    sesinya sebagai tidak sah, dan pembersihan sesi membuat diagnosis berikutnya menyesatkan
    ("kok langsung logout?").

24. **Invarian domain tetap berlaku di fixture test.** `SubscriptionInvoice` menolak `status = PAID`
    tanpa `paidAt`; fixture uji pun harus mengisi keduanya. Fixture yang lebih permisif dari domainnya
    akan menyembunyikan bug yang justru sedang diuji.

## Bukti verifikasi M2c

- Test: `SubscriptionInvoicePdfDocumentTest` (6), `SubscriptionInvoiceSheetLayoutTest` (7),
  `SubscriptionInvoicePdfRendererTest` (4), `BuilderRouteGateTest` 28 (7 baru untuk PDF). Renderer
  blueprint lama tetap hijau **tanpa diubah test-nya** setelah primitifnya dipindah — itu bukti
  refaktor tidak mengubah perilaku.
- Live (server lokal + Postgres, JWT asli):
  ```
  POST /api/public/auth/demo?tenantSlug=wemade-demo&role=PLATFORM_SUPERADMIN   → token
  POST /api/builder/billing/invoices?tenantId=ten-bordir-uji&period=2026-10   → 200 INV-2026-10-001
  POST /api/builder/billing/invoices/inv-…-2026-10-1/print-ticket             → 200 ticket
  GET  …/invoice.pdf?ticket=…                                                 → 200 application/pdf 12.964 B
  POST …/confirm?note=Transfer%20BCA%201-Okt                                  → 200 PAID
  GET  …/invoice.pdf?ticket=… (tiket baru)                                    → 200, watermark LUNAS
  ```
  Teksnya diperiksa dengan `pdftotext`: 9 baris modul, kolom harga rata kanan, `Total per bulan
  Rp 3.400.000`, footer `Halaman 1 dari 1 · INV-2026-10-001 · Dicetak 01 Okt 2026 00:21 +07:00`.
- **Dilihat dengan mata**: kedua PDF dirender ke PNG (`pdftoppm`) dan diperiksa — watermark diagonal
  `BELUM DIBAYAR` / `LUNAS`, kolom angka lurus, tiga garis pemisah, catatan kaki tercetak.
- Visual sisi tenant (Playwright, Wasm): pane **Billing** menampilkan dua invoice dengan tombol
  **Unduh PDF**; menekan tombolnya menerbitkan tiket (`iss: wemade-erp-print`, `scope` = invoice itu,
  `platform_superadmin: false`) dan membuka tab baru dengan `document.contentType == "application/pdf"`
  — jalur yang sama dengan yang dipakai pengguna sungguhan.

## Yang belum (jujur)

- **Kirim** PDF masih manual (unduh → lampirkan). Belum ada email/WhatsApp gateway, jadi kriteria
  "invoice pertama terkirim" baru terbukti sebagai "dokumen pertama diunduh".
- Belum ada UI VOID; penomoran ulang setelah VOID sudah diizinkan domain tapi belum ber-UI.
- Docker + Caddy wildcard tetap menunggu akses DNS — tanpa itu, "`bordir.wemakeerp.com` hidup dari
  tombol Deploy" tidak bisa diuji, dan Deploy masih hanya mengubah catatan deployment.
