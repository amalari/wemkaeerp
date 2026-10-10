# TRD-PLAT-012: Gerbang Factory Flow Fail-Closed (token tanpa identitas pabrik)

## 1. Document Context and Administration

- **Title & ID**: TRD-PLAT-012 — Gerbang rute Factory Flow / pipeline / locations fail-closed
- **Status**: **DRAFT — menunggu persetujuan.** Dokumen saja; tidak ada kode produksi/tes yang diubah.
- **Basis**: `main` @ `96b8d130`. Pola acuan: TRD-PLAT-011 (Org Chart), yang sudah diterapkan di `OrgChartAccessGuard.kt`.

### Revision History

| Versi | Tanggal | Penulis | Catatan |
| :--- | :--- | :--- | :--- |
| 0.1 | 2026-10-10 | Claude Sonnet 5.5 (atas permintaan Achmad) | Draft awal dari audit kode HEAD `96b8d130` |

### Summary & Business Context

`factoryFlowDecision` (`routes/FactoryFlowAccessGuard.kt:75-109`) memakai pola null-permisif yang sama dengan Org
Chart sebelum TRD-PLAT-011: `null` bila principal tak ada (`:80`), repositori wewenang tak dipasang (`:81`), atau token
tanpa `customRoleId` **dan** tanpa `departmentId` (`:82`); `requireFactoryFlowAccess` (`:121-135`) meloloskan `null`
(`:125`). Persoalan "keputusan tak terhitung dibaca lolos" hanya penting bila ada rute yang bergantung padanya.

**Temuan audit yang mengoreksi dugaan awal**: celahnya jauh lebih sempit dari Org Chart, karena Factory Flow sudah
dilindungi lapis kedua yang fail-closed (lihat §2 tabel audit):

1. `tenantRouteGate` (`ServerRouteWiring.kt:76`, kebijakan `TenantRouteGatePolicy.kt:98,81,89`) memasang gerbang
   *terpusat* berbasis `moduleDecision` (tanpa identitas = NONE) pada semua **GET** `/api/tenant/pipeline*`,
   `/pipeline/telemetry`, dan `/stage-flow*`.
2. Semua **tulis** Factory Flow (pipeline PUT/POST, stage-flow POST/PATCH/DELETE, locations PUT) lewat
   `manageTenant()`/`mayEditWithoutDecision` (`TenantStageFlowRoutes.kt:187`): tanpa keputusan, hanya peran
   ber-`MANAGE_TENANT` yang lolos — fail-closed untuk SALES/OPERATOR tanpa identitas.
3. **Satu-satunya rute yang benar-benar bocor: `GET /api/tenant/locations`** — tak punya entri `TenantRouteGatePolicy`,
   dan satu-satunya penjaganya adalah `requireFactoryFlowAccess(decision=null)` yang lolos. Token `SALES`/`OPERATOR`
   tanpa jabatan dan divisi menerima konfigurasi lokasi/pemetaan simpul-ke-gedung (200). Dugaan ini berasal dari
   pembacaan kode, **belum diprobe secara dinamis** (probe pertama adalah langkah 1 implementasi; lihat §5).

Selain itu `null` permisif di `factoryFlowDecision` adalah **sabuk lapis dalam yang rapuh**: handler baru yang hanya
memanggil `requireFactoryFlowAccess` (seperti `/locations` GET) mewarisi celah tanpa gerbang terpusat.

### Stakeholders & Approvers

Product/pemilik platform (keputusan arah), Tech Lead (RBAC/guard), QA (probe 403), Developer pelaksana.

### Goals (In-Scope)

- G1: `factoryFlowDecision` fail-closed bagi token tanpa identitas bila repositori wewenang terpasang (samakan TRD-PLAT-011).
- G2: `GET /api/tenant/locations` mendapat gerbang terpusat (`TenantRouteGatePolicy`) sehingga tak bergantung null-permisif.
- G3: Jaring pengaman tes: probe token tanpa identitas pada **semua** rute Factory Flow (baca + tulis), tenant non-default.
- G4: Daftar temuan pola null-permisif di rute tenant lain (tanpa mengubahnya).

### Non-Goals (Out-of-Scope)

- Mengubah `AccessDecisionEngine`, `ModuleGate`, `moduleDecision` (kecuali penyatuan guard — lihat Q3).
- Memigrasikan tiga guard salinan (`CrmAccessGuard`, `OrgChartAccessGuard`, `FactoryFlowAccessGuard`) ke `moduleDecision`
  (refactor tersendiri, sudah dicatat di KDoc `ModuleAccessGuard.kt`).
- Perubahan klien (kecuali bila Q2 diputuskan).
- Menyentuh `presentation/orgchart` dan `presentation/rbac`.

## 2. Functional Requirements

### Audit: peta rute Factory Flow → gate

Legenda gate: **T** = `tenantRouteGate`/`TenantRouteGatePolicy` (terpusat, `moduleDecision`, fail-closed);
**H** = handler `requireFactoryFlowAccess(factoryFlowDecision)` (null-permisif); **M** = `manageTenant()` =
`mayEditWithoutDecision` + H (tulis, fail-closed untuk non-Owner tanpa keputusan).

| Rute | Handler (file:baris) | Gate | Level | Status untuk token tanpa identitas non-Owner |
| :--- | :--- | :--- | :--- | :--- |
| `GET /api/tenant/pipeline` | `PipelineRoutes.kt:103` | T (`TenantRouteGatePolicy.kt:98`) | VIEW `FACTORY_FLOW` | Tertutup (403) oleh T |
| `GET /api/tenant/pipeline/modules` | `PipelineRoutes.kt:148` | T (sama) | VIEW | Tertutup |
| `PUT /api/tenant/pipeline` | `PipelineRoutes.kt:115` | M (`:50-58`); T = `null` (`:98`) | MANAGE | Tertutup oleh `mayEditWithoutDecision` (hanya `MANAGE_TENANT`); **bergantung H** |
| `POST /pipeline/reset`, `/modules/activation`, `PUT /modules/{id}`, `POST /modules/custom` | `PipelineRoutes.kt:133,163,187,214` | M | MANAGE | Tertutup (sama); bergantung H |
| `GET /api/tenant/pipeline/telemetry` | `PipelineTelemetryRoutes.kt:29-33` | T (`:89`, `PRODUCTION_MRP` atau `FACTORY_FLOW`) + H (VIEW `FACTORY_FLOW`) | VIEW | Tertutup oleh T. Catatan: T lebih longgar dari H (pemilik `PRODUCTION_MRP` saja lolos T, ditolak H) — lihat Q4 |
| `GET /api/tenant/stage-flow*` | `TenantStageFlowRoutes.kt:98-` | T (`:81`) | VIEW (`SPK_READERS`+`FACTORY_FLOW`) | Tertutup |
| `POST/PATCH/DELETE /stage-flow/stages…`, `/reset` | `TenantStageFlowRoutes.kt:104-145` | M (`:69-81`); T = `null` | MANAGE | Tertutup oleh `mayEditWithoutDecision`; bergantung H |
| `GET /api/tenant/stage-templates` | `TenantStageFlowRoutes.kt:83` | tak ada (sengaja) | — | `RouteGateLedger.openByDesign` (katalog bawaan platform, bukan data tenant) — **bukan celah** |
| `GET /api/tenant/entitlement` | `PipelineRoutes.kt:75` | tak ada (sengaja) | — | `openByDesign` (menu setiap anggota) — **bukan celah** |
| **`GET /api/tenant/locations`** | `TenantLocationRoutes.kt:39-47` | **H saja** (T tidak ada: `ruleFor` → `null` karena tak ada prefix) | VIEW | **TERBUKA (200)** untuk token tanpa identitas non-Owner |
| `PUT /api/tenant/locations` | `TenantLocationRoutes.kt:49-75` | M-setara (`mayEditWithoutDecision` + H) | MANAGE | Tertutup oleh `mayEditWithoutDecision`; bergantung H |
| `/api/tenant/process-catalog*` (termasuk `/phase-tags`) | `TenantProcessRoutes.kt:47`, `TenantPhaseTagRoutes.kt:28` | T (`:78`) | VIEW (baca), MANAGE `SAMPLING_ORDER` (tulis) | Tertutup; bukan Factory Flow murni |

Kepemilikan: `RouteOwnership.kt:55-56` memetakan `/pipeline` dan `/locations` ke `FACTORY_FLOW`.

Ringkasan: **1 rute baca terbuka (`GET /locations`)**; 0 rute tanpa gerbang sama sekali (`RouteGateLedger.ungated` kosong);
11 rute tulis dan 1 rute baca (`/locations`) bergantung pada guard null-permisif di handler sebagai satu-satunya/lapis
tambahan; sisanya dijaga T.

Perbedaan halus: untuk token **berjabatan/berdivisi**, `factoryFlowDecision` hanya memberi bypass bila
`isPlatformSuperadmin && role == null` (`:97-98`), sedangkan `callerDecisions` (jalur T) juga memberi bypass kepada
`Role.TENANT_ADMIN` tanpa jabatan (`ModuleAccessGuard.kt:101-102`). Owner yang hanya punya `departmentId` (tanpa
jabatan) karenanya bisa dinilai berbeda oleh T dan H. Perlu diseragamkan (Q3).

### Audit: pola null-permisif di rute tenant lain (hanya daftar; tidak diubah)

Pencarian `AccessDecision?`, `decision ?: return true`, `repository == null → lolos`, `principal == null` di `server/src/main/.../routes`:

| # | Temuan | Bukti | Penilaian |
| :-: | :--- | :--- | :--- |
| 1 | `requireFactoryFlowAccess` meloloskan `null` | `FactoryFlowAccessGuard.kt:125` | **Subjek TRD ini** |
| 2 | `requireOrgChartAccess` meloloskan `null` | `OrgChartAccessGuard.kt:129` | Sudah dipersempit TRD-PLAT-011: `null` hanya bila repositori tak dipasang (`:80`). Aman |
| 3 | `CostingRoutes` approve: `roleRepository != null && customRoleId != null` | `CostingRoutes.kt:325-335` | Fail-closed (tanpa keputusan → ditolak); bukan permisif |
| 4 | `hasFulfillmentAccess`: `principal ?: return false`, `roleRepository?... ?: return false` | `FulfillmentTransferRoutes.kt:348-355`, `FulfillmentRouteRoutes.kt:170-175` | Fail-closed; tetapi rute ini juga dijaga T (`:95`) |
| 5 | `CrmAccessGuard.crmDecision` | `CrmAccessGuard.kt:77-84` | Fail-closed (KDoc menyatakan sengaja menyimpang dari pola permisif) |
| 6 | Tiga salinan perhitungan persona (Crm/OrgChart/FactoryFlow) + `moduleDecision` | KDoc `ModuleAccessGuard.kt` | Utang desain: risiko drift (bukti: perbedaan bypass `TENANT_ADMIN`, §2) |
| 7 | `route("/api/tenant/…") { tenantRouteGate }` hanya mencakup prefix di `TenantRouteGatePolicy`; prefix lain bergantung `RouteGateLedger`+probe `RouteGateTest` | `TenantRouteGatePolicy.kt:70-102`, `RouteGateTest.kt:53` | Probe memakai token berjabatan tak dikenal (selalu NONE) — **tidak menangkap** kelas celah "tanpa identitas" di luar Org Chart (probe PLAT-011 dibatasi ke employees/departments, `RouteGateTest.kt:113-118`) |

Pencarian `?: true` mengembalikan hanya default non-keamanan (`SamplingRoutes.kt:90,156,180`, `TenantStageFlowRoutes.kt:115`,
`TechPackRoutes.kt:93`). **Tidak ditemukan rute tenant lain dengan pola decision-null-lolos** selain #1 (dan #2 yang sudah
diperbaiki). Keterbatasan: pencarian berbasis pola teks, bukan analisis aliran data.

### Persyaratan

- **FR-1**: Bila repositori wewenang terpasang, pemanggil tanpa jabatan dan tanpa divisi, atau tanpa principal, diputuskan
  lewat `moduleDecision(FACTORY_FLOW, …)`: Owner (`TENANT_ADMIN`) dan superadmin MANAGE, peran lain NONE → 403.
  `null` tersisa **hanya** bila repositori wewenang tidak dipasang.
- **FR-2**: `GET /api/tenant/locations` memiliki gerbang terpusat: `TenantRouteGatePolicy` memuat
  `path.startsWith("/api/tenant/locations") -> if (read) GateRule(VIEW, listOf(FACTORY_FLOW)) else null`
  (tulis tetap di handler, sama pola pipeline).
- **FR-3**: Tabel rute→gate→level (target) di bawah menjadi acuan tes.
- **FR-4**: Tulis tetap fail-closed dengan dua lapis (`mayEditWithoutDecision` dipertahankan sebagai sabuk).
- **FR-5**: Probe jaring pengaman: semua rute `/api/tenant/{pipeline,locations,stage-flow}` untuk token tanpa identitas non-Owner → 403.

### Tabel target rute → gate → level

| Rute | Gate terpusat (T) | Handler (H) | Level |
| :--- | :--- | :--- | :--- |
| `GET /pipeline`, `/pipeline/modules`, `/stage-flow*` | T (tetap) | — | VIEW |
| `GET /pipeline/telemetry` | T (tetap) | H VIEW | VIEW |
| **`GET /locations`** | **T baru (FR-2)** | H VIEW (sekarang fail-closed via FR-1) | VIEW |
| `PUT/POST/PATCH/DELETE` pipeline, stage-flow, `PUT /locations` | `null` (handler) | M: `mayEditWithoutDecision` + H MANAGE | MANAGE |
| `GET /stage-templates`, `GET /entitlement` | tidak ada (`openByDesign`) | — | — |

Catatan: sesuai instruksi, level mengikuti kode yang ada (baca VIEW, tulis MANAGE). Berbeda dari Org Chart (OPERATE
untuk POST/PUT), Factory Flow sengaja MANAGE karena topologi menggerakkan gerbang perpindahan tahap dan Surat Jalan
(KDoc `FactoryFlowAccessGuard.kt:69-74`).

## 3. Non-Functional Requirements (NFRs)

| Category | Requirement & Target Metrics | Rationale / Mitigation |
| :--- | :--- | :--- |
| **Performance** | Maks. +1 perhitungan keputusan per request tanpa identitas; Owner/superadmin melewati query penugasan (`ModuleAccessGuard.kt:107-108`). Telemetri 2 dtk/provider tak berubah | Jalur `moduleDecision` sudah dioptimalkan |
| **Scalability** | Tak ada tabel/skema baru; tanpa migrasi | Perubahan hanya di guard + kebijakan |
| **Security** | Nol rute Factory Flow yang meloloskan token non-Owner tanpa identitas (baca & tulis); pesan 403 tetap `Butuh wewenang …` | Kontrak 7 `tenant-variability-rules.md`; menutup bocor topologi lokasi |
| **Availability & Reliability** | Mode tanpa repositori wewenang (tes lama/in-memory) tetap `null`→lolos agar tak pecah; Owner/superadmin/demo tak berubah | Opsi "tolak `null` tanpa syarat" ditolak (mematikan pemasangan lama) |
| **Maintainability & Observability** | Logika baru di `FactoryFlowAccessGuard.kt` (135 baris); `TenantRouteGatePolicy.kt` (104) +1 baris; tes baru di berkas bertema; ratchet server: soft 300 / hard 500 | Lihat estimasi ukuran §4 |

## 4. System Architecture & Technical Design

### High-Level Architecture

```mermaid
flowchart LR
    C[Klien Factory Flow] -->|Bearer JWT| G[tenantRouteGate\nTenantRouteGatePolicy]
    G -->|moduleDecision\nfail-closed| H[Handler Pipeline/Stage-flow/Locations]
    H -->|factoryFlowDecision\n[FR-1: fail-closed bila repo terpasang]| R[requireFactoryFlowAccess]
    H -->|tulis| M[mayEditWithoutDecision\nsabuk MANAGE_TENANT]
    R --> D{{AccessDecisionEngine}}
```

Dua lapis tetap; yang berubah: lapis dalam (H) menjadi fail-closed dan `GET /locations` memperoleh lapis luar (T).

### Detailed Component Design

**K1 — `factoryFlowDecision` (FR-1).** Ganti baris `:80-82` dengan pola `orgChartDecision` (`OrgChartAccessGuard.kt:80-86`):

```kotlin
if (roleRepository == null || moduleAssignmentRepository == null) return null
val principal = callerPrincipalOrNull
if (principal == null || (principal.customRoleId == null && principal.departmentId == null)) {
    return moduleDecision(GarmentModules.FACTORY_FLOW, tenant, roleRepository, moduleAssignmentRepository)
}
```

Urutan pemeriksaan dibalik dari kode lama (`:80` mengembalikan `null` untuk principal null sebelum memeriksa
repositori): pemanggil tanpa principal pada pemasangan dengan repositori kini NONE → 403. Cabang berjabatan tidak berubah.

**K2 — `GET /locations` masuk `TenantRouteGatePolicy` (FR-2).** Satu baris `when`; `RouteOwnership` sudah memetakan prefix.
Menutup rute lewat lapis luar sehingga tak lagi bergantung guard handler.

**K3 — `mayEditWithoutDecision` dipertahankan** sebagai sabuk tulis (tidak dihapus); kini hampir tak terjangkau bagi
non-Owner tanpa identitas (keputusan terhitung NONE → H menolak), tapi tetap menutup kasus repositori tak terpasang.

**K4 — Pesan 403**: tidak berubah (`requireFactoryFlowAccess` `:128-132`).

**Opsi ditolak**: (1) hanya menambah entri kebijakan locations — handler lain tetap bergantung `null` permisif;
(2) menolak `null` tanpa syarat — mematikan pemasangan tanpa RBAC; (3) memigrasikan ketiga guard ke `moduleDecision` sekarang
— lingkup melebar, ditunda ke tindak lanjut (Q3); (4) menyalin guard keempat — Aturan Tiga Kali.

### Data Model & Schema

Tidak ada perubahan skema atau migrasi. Tabel terkait (`ModuleSchemaMap.kt:45`): `tenant_pipelines`, `tenant_locations`,
`tenant_location_settings`, `tenant_flow_node_locations`.

### API Specifications & External Contracts

Tak ada endpoint baru. Perubahan kontrak: `GET /api/tenant/locations` dan (bila ada pemasangan dengan repositori wewenang)
rute Factory Flow lain kini 403 untuk token layanan non-Owner tanpa jabatan/divisi. Galat: `403 "Butuh wewenang … atas modul "Alur Pabrik"; wewenang Anda saat ini … (…)"`.

**Dampak klien** (`app/shared`): `PipelineApiClient.kt:132-133` mengubah status non-2xx menjadi `error("Gagal … (HTTP 403)")`;
`FactoryFlowViewModel.loadTenantPipeline` (`:127-143`) memanggil `fallBackToPreset(cause)` pada kegagalan `getPipeline`
— yaitu **menampilkan preset, bukan state "akses ditolak"** (perlu diverifikasi visual, Q2). Pemanggilan sekunder
(`getStageFlow`, `getTelemetry`, `getModuleCatalog`) hanya mengabaikan kegagalan. **Tidak ada klien** untuk `/api/tenant/locations`
di `app/shared/commonMain` (pencarian string tidak menemukannya) — jadi perubahan K2 tak berdampak pada UI saat ini. Karena
menu dan gerbang memakai jalur perhitungan yang sama (`callerDecisions`), pengguna non-berwenang umumnya tak membuka layar
Factory Flow; token layanan tanpa identitas bukan pemakai UI.

### Technology Usage & Tradeoff Justification

Memakai ulang `moduleDecision` (jalur tunggal menu+gerbang) ketimbang hitung-ulang persona: satu sumber kebenaran,
menghindari drift (temuan #6). Biaya: ketergantungan guard Factory Flow pada `ModuleAccessGuard.kt`.

### Assumptions, Constraints, & Dependencies

- Asumsi (belum diverifikasi): tidak ada akun produksi bertipe peran lemah tanpa jabatan/divisi yang memakai `/locations`
  atau Factory Flow (DB dev: hanya `TENANT_ADMIN` dan superadmin tanpa identitas, dari TRD-PLAT-011 — keterbatasan: hanya DB dev).
- TRD-PLAT-011 sudah di basis (`OrgChartAccessGuard.kt:80-86`).
- Ratchet ukuran server; `FactoryFlowAccessGuard.kt` 135 → ±140 (di bawah soft 300). Berkas ber-import tak terpakai di
  kepalanya (±30 baris) — bukan lingkup, tetapi tidak boleh dibesarkan.

**Estimasi ukuran berkas (server soft 300 / hard 500):**

| Berkas | Sekarang | Estimasi | Catatan |
| :--- | :-: | :-: | :--- |
| `FactoryFlowAccessGuard.kt` | 135 | ≈140 | K1 + KDoc |
| `TenantRouteGatePolicy.kt` | 104 | ≈106 | K2 |
| `TenantLocationRoutes.kt` | 89 | 89 | tak berubah |
| `PipelineRoutes.kt` | 302 | ≤302 | **di atas soft**: tidak boleh membesar; tak disentuh |
| Tes baru `FactoryFlowAccessApiTest.kt` | — | ≈250 | soft tes 500 |
| `RouteGateTest.kt` | 174 | ≈210 | probe tanpa identitas diperluas |

## 5. Testing, Deployment, and Operations

### Technical Acceptance Criteria

- AC1: Token `SALES` dan `OPERATOR` tanpa `customRoleId`/`departmentId` → 403 pada `GET /locations`, `GET /pipeline`, `/pipeline/modules`,
  `/pipeline/telemetry`, `GET /stage-flow`; dan 403 pada semua rute tulis (PUT/POST/PATCH/DELETE).
- AC2: `TENANT_ADMIN` tanpa jabatan → 200 baca & tulis; superadmin act-as → 200; token tanpa principal (bila repositori terpasang) → 403.
- AC3: Jabatan VIEW → baca 200, tulis 403; jabatan NONE → 403; jabatan MANAGE → 200 (perilaku tak berubah).
- AC4: `RouteGateTest` baru gagal terhadap kode lama (membuktikan celah `GET /locations`) dan hijau setelah perbaikan.
- AC5: `TenantRouteGatePolicyTest` memuat kasus `GET /api/tenant/locations` = `GateRule(VIEW, [FACTORY_FLOW])`, tulis = `null`.
- AC6: `RouteOwnershipTest` tetap hijau.

### Testing Strategy

1. **Probe dulu terhadap kode lama** (lebih dulu menulis tes, lihat gagal): rute `/locations` GET diharapkan 200 → tes merah.
2. `FactoryFlowAccessApiTest` (baru, mengikuti `OrgChartAccessApiTest`): matriks peran × rute (baca + tulis) pada tenant garmen
   **dan** non-garmen (klinik/`bordir-uji`), template non-default (Kontrak 6/7 `tenant-variability-rules.md`); isolasi tenant
   (token tenant A ke tenant B → 403/404).
3. Perluas `RouteGateTest.orgChartRoutes_deny…` menjadi probe generik berparameter prefix (`employees`, `departments`,
   `pipeline`, `locations`, `stage-flow`) dengan token tanpa identitas (`TestAuth.tenantToken(slug, Role.SALES)`), agar kelas
   celah ini terjaga untuk modul lain kelak. Kecualikan `openByDesign` (`/entitlement`, `/stage-templates`).
4. Kasus "repositori wewenang tidak dipasang" (`null`) tetap lolos — dikunci sebagai tes karakterisasi agar perubahan
   itu disengaja.
5. Regresi: `StageFlowEditGuardTest`, tes pipeline/stage-flow yang memakai `asTenant(slug, Role.SALES)` tanpa jabatan akan
   berubah — pindai dulu (`grep`), perbarui hanya yang memang mengunci perilaku permisif.
6. Verifikasi visual (CLAUDE.md §13, bila UI disentuh; di sini tidak): login superadmin demo, buka Factory Flow, pastikan
   Owner tak terdampak.

### Monitoring & Error Handling

Tanpa log baru; 403 sudah terlihat di log akses. Disarankan hitungan 403 per rute `/locations` selama sepekan pasca-rilis
untuk mendeteksi token layanan yang terdampak.

### Deployment & Rollback Plan

- **Urutan implementasi** (satu PR kecil; Track dipakai agar jelas):
  - **Track A (server, penutup celah):** (A1) tulis probe+tes merah; (A2) K2 entri kebijakan `/locations`; (A3) K1 `factoryFlowDecision`.
    A2 saja sudah menutup satu-satunya kebocoran nyata; A3 menutup sabuk dalam.
  - **Track B (jaring pengaman):** probe generik `RouteGateTest` (bagian 3), tes karakterisasi `null`.
  - **Track C (tindak lanjut, terpisah):** seragamkan perhitungan persona/bypass `TENANT_ADMIN` (Q3); lembutkan UX 403 Factory Flow (Q2).
- Rollback: revert PR (tanpa migrasi, tanpa perubahan data). Kompilasi/uji: `./gradlew :server:test` untuk kelas terkait,
  lalu `scripts/audit-variability.sh` (melapor).
- Pasca-implementasi: teaching doc (CLAUDE.md §12) dan `graphify update .`.

### Risiko

- Token layanan tanpa identitas yang membaca `/locations` mendapat 403 (diterima; tak ada klien di `app/shared`).
- Kemungkinan tes yang mengunci perilaku permisif (lihat pengujian #5).
- Kegagalan 403 di Factory Flow tampil sebagai preset (Q2), bukan galat jelas.

### Pertanyaan Terbuka (dengan rekomendasi)

- **Q1 — Apakah GET `/locations` boleh terbuka bagi pembaca non-FACTORY_FLOW (mis. Fulfillment yang membaca pemetaan gedung untuk Surat Jalan)?** Rekomendasi: gate `VIEW` atas `FACTORY_FLOW` saja dulu (konsisten dengan kode, `FactoryFlowAccessGuard.kt:69-74`; Gudang NONE); bila Fulfillment butuh, tambahkan `FULFILLMENT` ke `GateRule` setelah ada klien nyata.
- **Q2 — Klien Factory Flow pada 403?** `fallBackToPreset(cause)` pada `getPipeline` gagal menampilkan preset seolah data. Rekomendasi: bukan bagian TRD ini; ajukan tiket terpisah agar 403 memunculkan state "akses ditolak" (hanya menyentuh `presentation/pipeline`).
- **Q3 — Bypass `TENANT_ADMIN`** berbeda antara `factoryFlowDecision` (hanya superadmin, `:97-98`) dan `callerDecisions` (`ModuleAccessGuard.kt:101-102`) untuk token berdivisi tanpa jabatan. Rekomendasi: seragamkan di Track C dengan mengganti `factoryFlowDecision` memakai `callerDecisions`, setelah tes paritas.
- **Q4 — Telemetri**: T meloloskan pemilik `PRODUCTION_MRP` (`TenantRouteGatePolicy.kt:89`) tetapi H menolaknya (VIEW `FACTORY_FLOW`). Rekomendasi: pertahankan (H lebih ketat, data kanvas), catat; putuskan bersama pemilik produk bila kanvas harus terbaca staf MRP.
- **Q5 — Seberapa luas probe generik?** Rekomendasi: probe seluruh `/api/tenant/**` token tanpa identitas, dengan daftar `openByDesign` sebagai satu-satunya pengecualian; bila terlalu berisik, mulai dari prefix Factory Flow.
