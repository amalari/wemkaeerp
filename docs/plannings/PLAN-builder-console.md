# Rencana: WeMake Builder (ERP ala Vercel, 1 akun = 1 project)

> Status: revisi 1 · 2026-09-30 · **Pemilik: Achmad Jamaludin**
> Asal: draf rencana sesi perencanaan 2026-09-30, diaudit ke kode aktual sebelum dibekukan (§0).
> Keluaran turunan: [`TRD-PLAT-002-builder.md`](../trd/TRD-PLAT-002-builder.md) (lingkup MVP M0–M2 saja)
> dan [`discovery-M0-builder.md`](discovery-M0-builder.md) (syarat sebelum kode M0).
>
> Prasyarat baca: `docs/teaching/teaching-b7-tenant-domain-pack.md`,
> `docs/teaching/teaching-discovery-a1-a7-a9-vertical-slice.md`, `TRD-PLAT-001-*.md`.

## 0. Hasil audit draf ke kode aktual

Semua klaim "sudah ada" di draf telah ditelusuri ke file. Yang **terbukti benar** tidak diulang di
sini; tabel di bawah hanya mencatat **koreksi** yang masuk ke revisi ini:

| # | Klaim draf | Hasil audit | Revisi |
|---|---|---|---|
| F1 | Route builder "didaftarkan di `ServerRouteWiring`, bukan `Application.kt`" | **`ServerRouteWiring` tidak ada.** Semua route didaftarkan langsung di blok `routing {}` milik `Application.kt:258` (pemanggilan `*Routes(...)` di baris 522–572). `Application.kt` = 698 baris, di atas hard limit 500 (file-size-rules §2) | §6 ditulis ulang: `ServerRouteWiring.kt` adalah **pemecahan baru** dari `Application.kt` — cicilan Ratchet, bukan file yang sudah ada. Pengurangan baris `Application.kt` dihitung sebagai bagian deliverable M2 |
| F2 | "Tenant di JWT wajib sama dengan tenant dari host (beda → 403)" | `PLATFORM_SUPERADMIN` boleh punya `tenantId = null` (`User.kt:33` hanya mewajibkan `tenantId` untuk role tenant). Aturan mentah akan mengunci superadmin dari semua subdomain — padahal §3 menyuruh superadmin "Masuk ke tenant mana pun" | §2: aturan host-vs-JWT **hanya berlaku untuk user ber-tenant**; superadmin tetap lewat jalur act-as yang sudah ada (`TenantResolutionPlugin.kt:188-194`). Test superadmin-masuk-subdomain-tetap-200 ditambahkan ke §10 |
| F3 | "1 akun = 1 project" tanpa titik penegakan | Tidak ada yang mencegah satu email menjadi owner dua tenant begitu daftar publik dinyalakan (flag) | §1: penegakan di **`RegisterTenantUseCase`** — satu email hanya boleh menjadi owner (`TENANT_ADMIN`) satu tenant; kolaborator tetap boleh diundang ke banyak tenant. Dites sejak M0, wajib sebelum flag daftar publik (M2) |
| F4 | §2 tabel URL: "Daftar = narasi singkat + pilih slug → redirect" vs M0 "diundang superadmin" | Bukan kontradiksi, tapi mudah disalahbaca | §2 diberi catatan: **M0–M1 alur undangan**; baris "Daftar" di tabel URL baru aktif saat flag publik dinyalakan di M2 |

Klaim audit yang memperkuat draf (sampel): `extractSubdomain` benar di `TenantResolutionPlugin.kt:266`;
`App.kt` tepat 568 baris; `Application.kt` tepat 698; migrasi terakhir V80 (rencana mulai **V81** benar);
`tenants.domain_pack` (V75) memang **tanpa kolom versi** — pin versi adalah celah nyata;
`ResolveDomainPack(code, version)` memang belum ada (resolver hari ini hanya `invoke(code)` → effective,
`DomainPackUseCases.kt:60`); tool agent `platform_modules` & `validate_draft` sudah ada,
`propose_patch` memang baru; `markDue`/`markPastDue`/`suspend` terdefinisi di `Tenant.kt:40-44`
dan **nol pemanggil** — klaim "tak terpakai" benar.

## Context

Sekarang WeMade punya funnel Discovery satu arah: wizard 4 langkah → draf → kunci → handoff oleh superadmin.
Target: pengalaman **seperti Vercel** tapi sederhana. **Satu akun = satu project = satu tenant**, dengan URL
`wmc.wemakeerp.com`. Menu sampingnya adalah **Builder** (Chat, Modules, Data Flow, Prototype, Antrian
Pembuatan, Deployments, Billing, …). "Deploy" = **kunci versi + aktifkan**, dengan rollback.

Keputusan user:
- pengguna = klien + superadmin;
- deploy = kunci versi + aktifkan;
- gateway = iPaymu (ditunda, lihat §8);
- garment WeMade = project pertama;
- MVP ramping;
- vertikal kedua = sablon/bordir;
- **1 akun 1 project**.

### Positioning bisnis

- **Assisted-serve.** Klien bercerita lewat chat dan melihat preview sendiri; konsultan WeMade menyempurnakan dan melakukan go-live.
- **Pembeda = kedalaman vertikal manufaktur pesanan Indonesia**, bukan "chat membuat ERP". Vertikal kedua sablon/bordir memakai ulang mesin tahap, stasiun, defect, dan costing. Tenant uji `bordir-uji` sudah ada.
- **Jangan jadi software house.** Antrian Pembuatan wajib berbayar (quote), diprioritaskan lewat Rule of Three.
- **Infrastruktur platform ditunda** sampai ada 3–5 design partner yang membayar.

## 1. Keputusan kunci: Project = Tenant

Dengan 1 akun = 1 project, **tidak ada agregat `Project` baru**. Tenant yang sudah ada adalah project-nya:

| Konsep Vercel | Di WeMake | Sudah ada |
|---|---|---|
| Akun + Project | **Tenant** + user TENANT_ADMIN-nya (pemilik) | `domain/tenant/Tenant.kt`, `RegisterTenantUseCase` (buat tenant TRIAL + slug) |
| Nama project / URL | `TenantSlug` → `<slug>.wemakeerp.com` | `CheckSubdomainAvailabilityUseCase`, slug terlarang (`TenantValueObjects.FORBIDDEN_SLUGS`) |
| Source | `DiscoveryDraft` (pack + blueprint + screens) | `domain/discovery/DiscoveryDraft.kt` → **tambah `tenant_id`** |
| Commit | Revisi draf | draf DRAFT→LOCKED |
| Chat (v0) | Chat Agent yang mengusulkan patch | `KoogDiscoveryAgent` + `DiscoveryDraftValidator` |
| Preview | tenant sandbox | `DiscoveryPreviewUseCases` |
| Deployment | versi pack terkunci + blueprint aktif di tenant | `LockDomainPackUseCase`, `AssignTenantDomainPackUseCase`, `HandoffDiscoveryDraftUseCase` |
| Kolaborator | user lain di tenant yang sama dengan izin builder | RBAC tenant yang ada |
| Superadmin | melihat semua tenant = "semua project" | `PLATFORM_SUPERADMIN`, header `X-Tenant-Slug` |

Yang **tidak perlu** lagi (dibanding rancangan multi-project):
- `Project`, `ProjectMember`, dan role `PLATFORM_BUILDER`. `User` tetap wajib `tenantId` untuk role
  tenant (`User.kt:34`), aturan `domain/auth/User.kt` tidak berubah.
- daftar project dan level "Team";
- `RootSwitch` berbasis host yang kompleks.

Akses Builder memakai permission baru `MANAGE_BUILDER` di `Permission` (enum sistem, lolos Uji Variabilitas):
- dimiliki TENANT_ADMIN dan superadmin;
- bisa diberikan ke user lain di tenant sebagai kolaborator.

**Penegakan 1 akun = 1 project (F3)**: `RegisterTenantUseCase` menolak pendaftaran bila email owner
sudah menjadi `TENANT_ADMIN` pada tenant lain (409). Kolaborator tidak terkena batasan ini — satu manusia
boleh diundang ke banyak tenant sebagai kolaborator, yang dibatasi adalah **kepemilikan**. Dites sejak M0.

## 2. URL

| URL | Isi |
|---|---|
| `wemakeerp.com` / `app.wemakeerp.com` | Login + halaman marketing. **Daftar (M2, via flag)** = narasi singkat + pilih slug → `RegisterTenantUseCase` (TRIAL) + user owner → redirect |
| `<slug>.wemakeerp.com/builder/...` | **Builder** (sidebar di bawah) |
| `<slug>.wemakeerp.com/` | ERP runtime. Sebelum deploy pertama: halaman "Belum di-deploy → buka Builder" |

> Catatan (F4): **M0–M1 alur undangan** — tenant dibuat/diaktifkan oleh superadmin; baris "Daftar" di
> tabel URL baru aktif saat flag daftar publik dinyalakan di M2 (setelah penegakan F3 teruji).

Detail teknis:
- Base domain lewat env `PLATFORM_BASE_DOMAIN`, menggantikan `wemade.id` default di
  `TenantApiEndpointResolver` (default tetap `wemade.id` untuk dev/test — banyak fixture dan teaching doc
  memakainya; override hanya di produksi).
- Tenant dari host sudah didukung oleh `extractSubdomain` (`TenantResolutionPlugin.kt:266`). Tambahan:
  tenant di JWT wajib sama dengan tenant dari host (beda → 403), meniru aturan `X-Tenant-Slug`.
  **Carve-out (F2)**: aturan ini hanya berlaku untuk user ber-tenant. `PLATFORM_SUPERADMIN`
  (`tenantId = null`) tetap bisa masuk ke subdomain tenant mana pun lewat jalur act-as yang sudah ada
  (`TenantResolutionPlugin.kt:188-194`). Test superadmin-masuk-subdomain-tetap-200 ada di §10.
- Login di subdomain tidak perlu kolom "Kode Pabrik".
- Builder = seksi route `/builder/*` di bundle yang sama. Shell-nya file baru
  `presentation/builder/BuilderShell.kt`, dan `App.kt` hanya mendapat **satu** cabang delegasi
  (568 baris, hard limit 600 — catat `wc -l` sebelum/sesudah, Kontrak 1 file-size-rules).

## 3. Menu Builder (sidebar)

| Kelompok | Menu | Isi | Reuse | MVP |
|---|---|---|---|---|
| Bangun | **Overview** | URL, status (TRIAL/LIVE), deployment aktif, tombol Preview/Deploy | — | ✅ |
| | **Chat** | percakapan dengan agent; tiap balasan = patch usulan (diff) → Terapkan / Buang | Koog + validator | ✅ |
| | **Modules** | modul pack: aktif/nonaktif, parameter Blueprint, status "tersedia / perlu dibangun" | `ModuleMapPane` | ✅ |
| | **Data Flow** | graf port antar modul, port yatim ditandai | `DataFlowPane`, kanvas Factory Flow | ✅ |
| | **Prototype** | layar per modul + live preview | `PrototypeRenderer` | ✅ |
| Kirim | **Antrian Pembuatan** | modul yang butuh kode: status, quote, ETA | scaffold + ledger `moduledev` | ✅ tipis |
| | **Deployments** | riwayat v1…vN, diff, Rollback | pack versioning | ✅ |
| Operasi | **Billing** | paket, estimasi bulanan, invoice | `GetTenantBillingPreviewUseCase` | ✅ manual |
| | **Pengaturan** | identitas bisnis (logo, NPWP, prefiks nomor dokumen), kolaborator | — | ✅ |
| Nanti | Domains (custom), Usage, Aktivitas, Roles & Akses, Data Awal, Integrations, Backup | | | L1–L4 |

**Superadmin**: halaman `app.wemakeerp.com/admin` berisi daftar semua tenant beserta status builder-nya,
papan Antrian Pembuatan lintas tenant, Buku Demand, dan Studio Pola (keduanya sudah ada). "Masuk" ke tenant
mana pun memakai mekanisme `X-Tenant-Slug` yang sudah ada.

## 4. Model domain

Paket baru `core/.../domain/builder/`, netral industri. Enum-nya semua status sistem (lolos Uji Variabilitas).

- `builder/chat/`: `BuilderConversation(tenantId)` dan `ChatMessage(role {USER, AGENT, SYSTEM}, text, proposedPatch?, appliedDraftId?)`.
  Agent **hanya mengusulkan**; menerapkan dan deploy selalu aksi manusia.
- `builder/deploy/Deployment.kt`: `(tenantId, number, draftId, packCode, packVersion|appBuild, blueprintRevision, status {VALIDATING, BLOCKED_ON_BUILD, ACTIVE, SUPERSEDED, FAILED, ROLLED_BACK, IMPORTED})`.
  Event domain: `DeploymentActivated`, `DeploymentRolledBack`.
- `builder/build/BuildRequest.kt`: `(tenantId, moduleId, reason, status {QUEUED, QUOTED, APPROVED, IN_PROGRESS, SHIPPED, REJECTED}, quoteId?)`.
  Menaut ke `ModulePricingQuote` / ledger `moduledev` (`core/domain/moduledev/`).

Perubahan pada yang sudah ada:
- `DiscoveryDraft` + `tenant_id` (nullable untuk draf lama; backfill dibiarkan NULL — draf lama milik
  pribadi pemanggil per V78, bukan milik tenant). Satu tenant punya satu *working draft*; setelah deploy,
  draf dikloning untuk revisi berikutnya.
- **Pin versi pack per tenant** (`tenants.domain_pack_version`), supaya rollback mungkin. Perilaku baru
  `ResolveDomainPack(code, version)`; resolver lama `invoke(code)` (effective) tidak diubah. Ini perubahan
  perilaku B7, jadi wajib test paritas garment.
- Handoff saat ini membuat tenant baru. Sekarang tenant **sudah ada sejak daftar**, jadi logikanya pindah
  ke `DeployTenantUseCase`: kunci, lalu assign ke tenant sendiri. `AssignTenantDomainPackUseCase`,
  yang menolak ganti pack bila sudah ada data, tetap aman: pack hanya berganti *versi*, bukan *kode*.

## 5. Alur kunci

**Daftar (M2, flag publik)**: narasi + slug → tenant TRIAL + owner (dengan gerbang F3) → agent membuat
draf pertama → masuk `/builder/chat`. **M0–M1**: tenant & owner dibuat/diundang superadmin.

**Chat → revisi**:
1. `ChatWithBuilderAgentUseCase` memanggil Koog dengan tool `propose_patch` (baru), `platform_modules`, dan `validate_draft`.
2. Hasilnya lewat validator berpath, dengan koreksi diri maks. 3×.
3. Agent membalas dengan patch + diff.
4. User menekan "Terapkan" → `ApplyDraftPatchUseCase`.

Fallback deterministik tetap berlaku. Istilah yang tak cocok dengan modul mana pun masuk `DemandLedger`.

**Deploy** (`DeployTenantUseCase`):
1. Validasi draf.
2. Bila ada modul yang butuh kode, `BuildRequest` dibuat dan status jadi `BLOCKED_ON_BUILD`.
3. Kunci draf dan kunci pack sebagai version+1.
4. Pin versi, salin blueprint.
5. Deployment baru `ACTIVE`; tenant TRIAL→ACTIVE saat deploy pertama (setelah pembayaran/konfirmasi).


## 6. Persistensi & API

- Data builder sekarang **milik tenant**, jadi tabelnya di schema `builder` dengan RLS:
  `apply_tenant_rls_in('builder', …)`, grant `wemade_app`, dan terdaftar di `ModuleSchemaMap`
  (pola V76, B8) + `OpsSchemaBoundaryTest` diperbarui. Tabelnya: `conversations`, `chat_messages`,
  `deployments`, `build_requests`.
- `ops.discovery_drafts` + kolom `tenant_id` (nullable). Migrasi mulai **V81**.
- **Route wiring (F1 — koreksi)**: `ServerRouteWiring.kt` **belum ada** dan dibuat sebagai pemecahan baru.
  Hari ini semua route dipasang di blok `routing {}` milik `Application.kt:258` (pemanggilan `*Routes(...)`
  di baris 522–572). Deliverable M2: pindahkan pemanggilan `*Routes(...)` yang ada ke
  `server/routes/ServerRouteWiring.kt` (satu fungsi ekstensi `Routing.serverRouteWiring(deps)`), tambah
  seksi `builderRoutes(...)`, lalu `Application.kt` memanggil satu fungsi itu. Target ratchet:
  `Application.kt` 698 → ≤600 (cicilan file-size-rules Kontrak 2); tidak ada route baru yang ditambahkan
  ke `Application.kt`.
- Route builder dipecah per agregat: `BuilderChatRoutes`, `DeploymentRoutes`, `BuildQueueRoutes`,
  `BuilderBillingRoutes`.
- Gerbang `MANAGE_BUILDER` bersifat fail-closed. Setiap endpoint tulis punya test 403 untuk user tenant
  tanpa izin dan untuk tenant lain (Kontrak 7 tenant-variability-rules).
- Klien: `infrastructure/api/BuilderApiClient.kt`, termasuk membungkus endpoint preview dan handoff yang belum dipanggil klien.
- UI: `presentation/builder/`, satu file per pane, memakai Clay (design-system-rules); sidebar = komponen `designsystem/` yang buta domain.

## 7. Infrastruktur (belum ada sama sekali)

- Dockerfile server + build web statis.
- Caddy dengan wildcard `*.wemakeerp.com` (DNS-01), lalu `/api` → Ktor dan selain itu → bundle Wasm dengan fallback SPA.
- `DB_APP_USER` **wajib** di produksi, karena tanpanya RLS terlewati (DatabaseFactory sudah mendukung, ada warning bila kosong).

## 8. Fase

### MVP: dibangun sekarang

| Fase | Isi | Selesai bila |
|---|---|---|
| **M0 Fondasi** | permission `MANAGE_BUILDER`, `BuilderShell` + Overview + Pengaturan, `DiscoveryDraft.tenant_id`, pin versi pack, migrasi impor `Deployment #1`, cek JWT vs host (dengan carve-out superadmin F2), **penegakan 1-owner-per-email (F3)**, undangan tenant + login di subdomain (daftar publik via flag, dinyalakan di M2) | `wemade-demo/builder` tampil dengan Deployment #1 IMPORTED; test paritas garment hijau |
| **M1 Builder** | Chat tersimpan + `propose_patch`, Modules, Data Flow, Prototype, preview dalam builder | `bordir-uji` dibangun dari chat sampai preview yang bisa diklik |
| **M2 Deploy** | `DeployTenantUseCase`, Deployments + rollback, Antrian Pembuatan tipis (dikelola superadmin), billing manual (invoice PDF dari harga terkunci + konfirmasi bayar oleh superadmin), **`ServerRouteWiring` (pemecahan F1)**, flag daftar publik, Docker + Caddy wildcard | `bordir.wemakeerp.com` hidup dari tombol Deploy; rollback teruji; invoice pertama terkirim |

### Gerbang sebelum ekspansi

Diukur pada 3–5 design partner yang membayar:
- waktu dari narasi ke preview pertama;
- persentase modul terlayani tanpa kode;
- konversi preview → bayar;
- jumlah item Antrian Pembuatan per klien.

### Setelah gerbang lolos

| Fase | Isi |
|---|---|
| **L1 Billing iPaymu** | port `PaymentGateway` + `IpaymuPaymentGateway`, callback (signature + cek ulang status + idempoten per `trx_id`), dunning ke `TenantStatus` (`markDue`/`markPastDue`/`suspend` yang selama ini tak terpakai — terverifikasi nol pemanggil), Usage |
| **L2 Custom Domain** | verifikasi CNAME/TXT, Caddy on-demand TLS (`ask` hanya untuk host terverifikasi), tabel `tenant_hostnames` menggantikan `extractSubdomain` |
| **L3 Lanjutan** | Roles & Akses, Data Awal (impor Excel), Integrations, Backup/Ekspor, Aktivitas, fork pack shipped → pack data |

Setiap fase menjalankan `wemade-feature-discovery` → `wemade-feature-workflow` dan diakhiri teaching doc.

## 9. Risiko

- **Pin versi pack** mengubah perilaku B7 → test paritas garment.
- **Agent tidak tepercaya** → validator yang sama; agent tidak punya tool deploy.
- **Data tenant hidup** → gerbang penghapusan modul, tanpa drop schema.
- **Pintu belakang garment** → Antrian Pembuatan untuk pack shipped hanya menaut ke `wemade-erp`.
- **Keterbatasan 1 akun 1 project**: grup usaha dengan beberapa pabrik butuh beberapa akun terpisah. Diterima demi kesederhanaan; penegakannya di `RegisterTenantUseCase` (F3). Bila nanti perlu, "Organisasi" di atas tenant bisa ditambahkan tanpa mengubah model tenant.
- **Superadmin terkunci secara tak sengaja (F2)**: aturan host-vs-JWT yang mentah akan memblokir superadmin dari semua subdomain — carve-out act-as wajib dites sejak M0.

## 10. Verifikasi

- Rencana ini hasil audit ke kode (§0); klaim "sudah ada" ditelusuri ulang saat TRD fase berikutnya ditulis.
- TRD memuat kriteria uji per fase:
  - test paritas garment (impor + deploy ulang = no-op);
  - `bordir-uji` end-to-end;
  - test 403 per endpoint tulis (tanpa izin dan tenant lain);
  - test rollback dengan gerbang data;
  - **test superadmin masuk subdomain tenant tetap 200 dengan aturan host-vs-JWT aktif (F2)**;
  - **test daftar kedua dengan email owner yang sama → 409 (F3)**.
- Saat implementasi: kompilasi 5 target, `scripts/audit-variability.sh`, dan cek visual `/builder` setelah login superadmin demo **dan** login owner `bordir-uji`.

**Gerbang data**: versi yang menghapus modul yang sudah berisi data ditolak, kecuali "Arsipkan modul". Schema tidak pernah di-drop.

**Rollback**: pin kembali ke versi N−1 dengan gerbang yang sama, tercatat di audit.

### 5b. Garment WeMade = project pertama (dogfooding)

| | Garment WeMade |
|---|---|
| Project | tenant `wemade-demo` → `wemade-demo.wemakeerp.com/builder`. Seed `cv-berkah-makloon` (`ten-demo-cmt`) & `urbanwear-d2c` (`ten-demo-d2c`) tetap tenant biasa (masing-masing 1 project) |
| Pack | `garment` = **pack shipped (kode)**, struktur read-only dengan badge "Pack bawaan platform" |
| Yang bisa diubah di Builder | lapisan **Blueprint** (modul aktif, parameter) + konfigurasi tenant |
| Deployment #1 | `IMPORTED`: snapshot `tenant.businessPreset` + versi build aplikasi |
| Rollback | ke Blueprint revisi sebelumnya; versi kode tidak di-rollback per tenant |
| Antrian Pembuatan | menaut ke pekerjaan di repo `wemade-erp` (AGENTS.md: fitur konveksi tidak ditulis pertama di sini) |

Aturan ini berlaku untuk semua pack shipped, dan kode mesin tidak menyebut "garment".

**Migrasi impor** (M0, idempoten): untuk setiap tenant yang ada, buat `Deployment #1 IMPORTED`. Data operasional tidak disentuh.

**Test paritas utama**: tenant garment setelah impor berperilaku identik; deploy ulang Blueprint yang sama = no-op.

**Tenant kedua di test**: `bordir-uji` dengan pack data, dari chat sampai deploy.

