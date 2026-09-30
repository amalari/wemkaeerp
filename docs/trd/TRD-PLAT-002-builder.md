# TRD-PLAT-002: WeMake Builder — Console ala Vercel (1 Akun = 1 Project)

## 1. Document Context and Administration

- **Title & Unique ID**: WeMake Builder Console — `TRD-PLAT-002` (lingkup **MVP M0–M2 saja**; L1–L3
  keluar lingkup dan hanya disinggung sebagai non-goal)
- **Revision History**

| Versi | Tanggal | Penulis | Catatan |
|---|---|---|---|
| 0.1 | 2026-09-30 | Achmad Jamaludin (dibantu Claude) | Dari [`PLAN-builder-console.md`](../plannings/PLAN-builder-console.md) revisi 1 (sudah diaudit ke kode, §0 F1–F4) dan [`discovery-M0-builder.md`](../plannings/discovery-M0-builder.md) |

### Summary & Business Context

Funnel Discovery hari ini satu arah dan berakhir di handoff superadmin: wizard 4 langkah → draf → kunci →
tenant baru. Klien tidak punya ruang kerja mandiri. WeMake Builder mengubah tenant menjadi **project**
gaya Vercel: satu akun = satu project = satu tenant, URL `<slug>.wemakeerp.com`, sidebar Builder
(Overview, Chat, Modules, Data Flow, Prototype, Antrian Pembuatan, Deployments, Billing, Pengaturan).
"Deploy" = kunci versi pack + aktifkan di tenant, dengan rollback.

Positioning bisnis (detail di plan): **assisted-serve** — klien bercerita via chat, konsultan menyempurnakan
dan go-live; pembeda = kedalaman vertikal manufaktur pesanan Indonesia; Antrian Pembuatan wajib berbayar.

Rujukan kode aktual: klaim "sudah ada" di plan §0 sudah diaudit per file (contoh: `extractSubdomain` di
`TenantResolutionPlugin.kt:266`, `App.kt` = 568 baris, migrasi terakhir V80, `tenants.domain_pack` tanpa
kolom versi).

### Stakeholders & Approvers

Product & Tech Lead: Achmad Jamaludin · Implementasi: Claude (Jalur B) · QA: test paritas garment,
test 403 per endpoint, cek visual `bordir-uji`

### Goals (In-Scope)

- **M0 Fondasi**: permission `MANAGE_BUILDER`; `BuilderShell` + Overview + Pengaturan (Clay);
  `DiscoveryDraft.tenantId`; pin versi pack (`tenants.domain_pack_version` + `ResolveDomainPack(code, version)`);
  migrasi V81 (schema `builder`, `deployments`, backfill `Deployment #1 IMPORTED` idempoten);
  cek JWT-vs-host dengan carve-out superadmin; penegakan satu-owner-per-email; undangan + login di subdomain.
- **M1 Builder**: `BuilderConversation`/`ChatMessage` tersimpan; tool `propose_patch` + `ApplyDraftPatchUseCase`;
  pane Modules, Data Flow, Prototype (reuse `ModuleMapPane`/`DataFlowPane`/`PrototypeRenderer`);
  preview dalam builder.
- **M2 Deploy**: `DeployTenantUseCase` (validasi → BuildRequest/blocked → kunci → pin → ACTIVE);
  Deployments + rollback dengan gerbang data; Antrian Pembuatan tipis (di atas `moduledev`);
  billing manual (invoice PDF + konfirmasi superadmin); `ServerRouteWiring.kt` (pemecahan dari
  `Application.kt`, ratchet 698 → ≤600); flag daftar publik; Dockerfile + Caddy wildcard.

### Non-Goals (Out-of-Scope)


## 2. Functional Requirements

### M0 Fondasi

- **FR-M0-1 Permission**: `MANAGE_BUILDER` ditambahkan ke `Permission` (`AuthValueObjects.kt`).
  Default: `TENANT_ADMIN` dan `PLATFORM_SUPERADMIN` memilikinya; dapat diberikan ke user tenant lain
  sebagai kolaborator melalui RBAC tenant yang ada.
- **FR-M0-2 Gerbang kepemilikan (F3)**: `RegisterTenantUseCase` menolak (409) bila email owner sudah
  menjadi `TENANT_ADMIN` pada tenant lain. Kolaborator tidak terkena. Test: daftar kedua email sama → 409.
- **FR-M0-3 Cek host-vs-JWT (F2)**: untuk user ber-tenant (`tenantId != null`), tenant dari subdomain host
  wajib sama dengan tenant di JWT; beda → 403. `PLATFORM_SUPERADMIN` (`tenantId = null`) dikecualikan dan
  tetap lewat jalur act-as `TenantResolutionPlugin.kt:188-194`.
- **FR-M0-4 Draf milik tenant**: `DiscoveryDraft` + `tenantId: TenantId?` (nullable; draf lama tetap NULL
  dan tetap milik pribadi pemanggil per V78). Satu tenant punya satu *working draft*.
- **FR-M0-5 Pin versi pack**: kolom `tenants.domain_pack_version INTEGER`; use case baru
  `ResolveDomainPack(code, version)`; resolver lama `invoke(code)` (effective) tidak berubah perilaku.
- **FR-M0-6 Migrasi impor (V81, idempoten)**: setiap tenant yang ada mendapat `Deployment #1` dengan
  status `IMPORTED`, `packVersion = NULL`, `appBuild = versi build aplikasi`. Data operasional tidak disentuh.
- **FR-M0-7 Shell Builder**: `presentation/builder/BuilderShell.kt` + pane Overview (URL, status
  TRIAL/LIVE, deployment aktif, tombol Preview — Deploy tampil tapi dinonaktifkan sampai M2) dan
  Pengaturan (identitas bisnis + kolaborator). Sidebar = komponen `designsystem/` yang buta domain
  (menerima `String`/`Color`/lambda — Kontrak 6 design-system-rules). `App.kt` hanya mendapat **satu**
  cabang delegasi (568 → wajib ≤600).
- **FR-M0-8 Undangan**: superadmin membuat/mengaktifkan tenant + user owner; owner login di
  `<slug>.wemakeerp.com` tanpa kolom "Kode Pabrik". Daftar publik **tidak** tersedia sampai M2 (F4).

### M1 Builder

- **FR-M1-1 Chat tersimpan**: `BuilderConversation(tenantId)`, `ChatMessage(role {USER, AGENT, SYSTEM},
  text, proposedPatch?, appliedDraftId?)` di schema `builder` (RLS).
- **FR-M1-2 Tool `propose_patch`**: Koog agent mengusulkan patch `DiscoveryDraft` (bukan menulis langsung);
  loop koreksi diri maks. 3× lewat `DiscoveryDraftValidator` berpath (pola A8); fallback deterministik
  tetap; agent tidak punya tool selain `platform_modules`, `validate_draft`, `propose_patch`.
- **FR-M1-3 Terapkan/Buang**: user menerima patch → `ApplyDraftPatchUseCase` menulis draf; agent tidak
  pernah menulis sendiri.
- **FR-M1-4 Pane reuse**: Modules (`ModuleMapPane`), Data Flow (`DataFlowPane`), Prototype
  (`PrototypeRenderer`) di-host dalam Builder; modul yang belum ada kodenya bertanda "perlu dibangun"
  (menuju Antrian Pembuatan).
- **FR-M1-5 Istilah tak dikenal** dari chat masuk `DemandLedger` (pola `discovery_demands` V80).

### M2 Deploy

- **FR-M2-1 `DeployTenantUseCase`**: (1) validasi draf; (2) modul yang butuh kode → `BuildRequest`
  (status `QUEUED`) dan deployment `BLOCKED_ON_BUILD`; (3) kunci draf + kunci pack sebagai version+1;
  (4) pin versi + salin blueprint; (5) deployment baru `ACTIVE`; tenant TRIAL→ACTIVE pada deploy pertama.
- **FR-M2-2 Status deployment**: `VALIDATING, BLOCKED_ON_BUILD, ACTIVE, SUPERSEDED, FAILED, ROLLED_BACK,
  IMPORTED`; event domain `DeploymentActivated`, `DeploymentRolledBack`.
- **FR-M2-3 Gerbang data**: versi yang menghapus modul yang sudah berisi data ditolak (kecuali aksi
  eksplisit "Arsipkan modul"); schema tidak pernah di-drop. Rollback = pin ke versi N−1 dengan gerbang
  yang sama, tercatat di audit.
- **FR-M2-4 Antrian Pembuatan**: `BuildRequest(tenantId, moduleId, reason, status {QUEUED, QUOTED,
  APPROVED, IN_PROGRESS, SHIPPED, REJECTED}, quoteId?)` menaut ke `ModulePricingQuote`/ledger `moduledev`;
  dikelola superadmin (MVP tanpa self-service).
- **FR-M2-5 Billing manual**: invoice PDF dari harga terkunci (`GetTenantBillingPreviewUseCase`) +

## 3. Non-Functional Requirements (NFRs)

| Category | Requirement & Target Metrics | Rationale / Mitigation |
| :--- | :--- | :--- |
| **Performance** | Chat respons < 3 dtk (fallback deterministik < 200 ms); pane Builder lazy-load; preview memakai renderer yang sudah ada | Keluaran LLM lambat — UI jujur menampilkan status |
| **Scalability** | Tabel `builder.*` terindeks per `tenant_id`; satu working draft per tenant; deployment append-only | Milik tenant, volume kecil per tenant |
| **Security** | Fail-closed di semua endpoint tulis (`MANAGE_BUILDER`); RLS schema `builder` via `apply_tenant_rls_in`; host-vs-JWT untuk user ber-tenant; agent tanpa tool tulis kecuali `propose_patch`; `DB_APP_USER` wajib produksi | Keluaran agent dan host header = input tak tepercaya |
| **Availability & Reliability** | Agent mati / API key kosong → fallback deterministik (server tidak gagal start, pola A8); deploy gagal → status `FAILED`, tenant tetap pada versi pin sebelumnya | Build/LLM bukan jalur kritikal runtime ERP |
| **Maintainability & Observability** | Route per agregat di `ServerRouteWiring`; file-size: `App.kt` ≤600, `Application.kt` ≤600 setelah M2, pane Builder satu file per pane ≤600; audit log untuk activate/rollback; kompilasi 5 target per fase | Ratchet file-size-rules; deploy = aksi berisiko, wajib tercatat |

## 4. System Architecture & Technical Design

### High-Level Architecture

```mermaid
flowchart LR
    Owner[Owner tenant<br/>MANAGE_BUILDER] -->|/builder/*| Shell[BuilderShell<br/>presentation/builder/]
    Super[PLATFORM_SUPERADMIN<br/>act-as X-Tenant-Slug] --> Shell
    Shell --> API[BuilderApiClient]
    API -->|/api/builder/*| Routes[BuilderChatRoutes · DeploymentRoutes<br/>BuildQueueRoutes · BuilderBillingRoutes]
    Routes --> Gate[MANAGE_BUILDER + host-vs-JWT<br/>fail-closed]
    Gate --> ChatUC[ChatWithBuilderAgentUseCase]
    ChatUC --> Agent[Koog + propose_patch<br/>fallback deterministik]

### Detailed Component Design

- **Domain baru** (`core/.../domain/builder/`, netral industri; enum = status sistem, lolos Uji Variabilitas):
  - `deploy/Deployment.kt`: `(tenantId, number, draftId, packCode, packVersion?, appBuild?, blueprintRevision, status, timestamps)` + event `DeploymentActivated`, `DeploymentRolledBack`.
  - `chat/`: `BuilderConversation`, `ChatMessage`.
  - `build/BuildRequest.kt`: menaut `quoteId` ke `ModulePricingQuote` (`core/domain/moduledev/`).
- **Perubahan pada yang ada**:
  - `DiscoveryDraft` + `tenantId: TenantId?`;
  - `Permission.MANAGE_BUILDER`;
  - `RegisterTenantUseCase`: gerbang satu-owner-per-email (409);
  - `TenantResolutionPlugin`: host-vs-JWT untuk user ber-tenant; carve-out superadmin lewat jalur act-as yang ada;
  - `DomainPackUseCases`: `ResolveDomainPack(code, version)` (baru) — resolver effective `invoke(code)` tidak berubah;
  - `App.kt`: satu cabang → `BuilderShell.kt`.
- **Klien**: `infrastructure/api/BuilderApiClient.kt` (termasuk membungkus endpoint preview/handoff yang belum dipanggil klien); ViewModel MVI per pane di `presentation/builder/`.

### Data Model & Schema (V81)

```sql
CREATE SCHEMA IF NOT EXISTS builder;

CREATE TABLE IF NOT EXISTS builder.deployments (
    id                  VARCHAR(64) PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL REFERENCES tenants(id),
    number              INTEGER     NOT NULL CHECK (number > 0),
    draft_id            VARCHAR(64),
    pack_code           VARCHAR(64) NOT NULL,
    pack_version        INTEGER     CHECK (pack_version IS NULL OR pack_version > 0),
    app_build           VARCHAR(64),
    blueprint_revision  INTEGER     NOT NULL DEFAULT 1,
    status              VARCHAR(32) NOT NULL,          -- IMPORTED (M0), lainnya M2
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at        TIMESTAMPTZ,
    UNIQUE (tenant_id, number)
);
-- M1: builder.conversations, builder.chat_messages
-- M2: builder.build_requests
-- grant wemade_app + ALTER DEFAULT PRIVILEGES + apply_tenant_rls_in('builder', ...) (pola V76)
-- idempoten: INSERT ... ON CONFLICT (tenant_id, number) DO NOTHING untuk Deployment #1 IMPORTED

ALTER TABLE ops.discovery_drafts ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS domain_pack_version INTEGER;
```

Daftarkan tabel di `ModuleSchemaMap` dan perbarui `OpsSchemaBoundaryTest` (pola B8).

### API Specifications

| Method & Path | Gate | Catatan |
|---|---|---|
| `GET /api/builder/overview` | `MANAGE_BUILDER` | status, deployment aktif |
| `GET/POST /api/builder/chat` | `MANAGE_BUILDER` | M1; POST = balasan agent + patch usulan |
| `POST /api/builder/chat/apply` | `MANAGE_BUILDER` | M1; terapkan patch |
| `GET /api/builder/deployments` | `MANAGE_BUILDER` | riwayat v1…vN |
| `POST /api/builder/deployments` | `MANAGE_BUILDER` + `TENANT_ADMIN` | M2; deploy |
| `POST /api/builder/deployments/{n}/rollback` | `MANAGE_BUILDER` + `TENANT_ADMIN` | M2; gerbang data |
| `GET/POST /api/builder/build-requests` | `MANAGE_BUILDER` (tulis) / superadmin (kelola) | M2; quote via moduledev |
| `GET /api/builder/billing` | `MANAGE_BUILDER` | M2; preview harga terkunci |

Semua tulis fail-closed: keputusan RBAC tidak bisa dihitung → 403. Error shape mengikuti pola route yang ada.

### Technology Usage & Tradeoff Justification

- **Tenant = project** alih-alih agregat `Project` baru: menghindari dual-model kepemilikan; RBAC, RLS,
  entitlement, dan reconciler pipeline yang sudah ada langsung dipakai.
- **Pin versi (kolom) alih-alih tabel deployment-only**: rollback butuh resolusi O(1) per request runtime;
  `domain_packs` sudah ber-PK `(code, version)` sehingga versi historis tersimpan sendiri.
- **Koog in-process + fallback deterministik**: pola terbukti A8; konsol tidak boleh mati karena LLM.
- **`ServerRouteWiring` baru**: `Application.kt` 698 baris > hard limit 500 — wiring adalah pemisahan
  komposisi yang aman (bukan logika), sekaligus cicilan ratchet (F1).

### Assumptions, Constraints, & Dependencies

- Migrasi mulai **V81** (terakhir V80). DB B terpisah, jadi perubahan perilaku (pin versi) aman diuji.
- Pack garment adalah pack **shipped** — tidak berbaris di `domain_packs` data; Deployment #1 `IMPORTED`
  menyimpan `appBuild`, bukan `packVersion`.
- `bordir-uji` (pack data) tersedia sebagai tenant uji kedua (Kontrak 6 tenant-variability-rules).
- 1 akun = 1 project ditegakkan di `RegisterTenantUseCase` sejak M0; daftar publik baru menyala di M2.
- Caddy + DNS wildcard butuh akses DNS `wemakeerp.com` — prasyarat operasional M2.

## 5. Testing, Deployment, and Operations

### Technical Acceptance Criteria (AC)

- **AC-M0-1**: `wemade-demo` login subdomain → `/builder` menampilkan Overview dengan
  `Deployment #1 IMPORTED`; badge "Pack bawaan platform" pada struktur pack garment (read-only).
- **AC-M0-2**: test paritas garment — perilaku tenant garment setelah impor identik; resolver effective
  `invoke(code)` tidak berubah hasil untuk semua tenant lama.
- **AC-M0-3**: migrasi V81 dijalankan dua kali → tidak ada baris duplikat (idempoten).
- **AC-M0-4**: user tenant dengan JWT tenant A mengakses host tenant B → 403;
  **superadmin (`tenantId = null`) mengakses subdomain tenant mana pun → 200** (F2).
- **AC-M0-5**: daftar/registrasi kedua dengan email owner yang sama → 409 (F3).
- **AC-M1-1**: `bordir-uji` dari chat: narasi → patch usulan (diff) → Terapkan → draf berubah; Buang →
  draf tetap. Agent tanpa API key → fallback deterministik tetap menghasilkan draf sah.
- **AC-M1-2**: preview dalam Builder menampilkan layar per modul via `PrototypeRenderer`.
- **AC-M2-1**: `bordir.wemakeerp.com` hidup dari tombol Deploy (validasi → kunci → pin → `ACTIVE`).
- **AC-M2-2**: rollback N→N−1 teruji; percobaan rollback yang menghapus modul berisi data → ditolak
  gerbang data; keduanya tercatat di audit log.
- **AC-M2-3**: endpoint tulis builder → 403 untuk user tenant tanpa `MANAGE_BUILDER` **dan** untuk user
  tenant lain; tanpa keputusan RBAC yang bisa dihitung → 403 (fail-closed).
- **AC-M2-4**: daftar publik (flag nyala) menghasilkan tenant TRIAL + owner; flag mati → undangan saja.
- **AC-M2-5**: `Application.kt` ≤600 baris; tidak ada route builder di `Application.kt`.
- **AC-Umum**: kompilasi 5 target hijau (`compileKotlinJvm`, `compileKotlinWasmJs`, `compileKotlinJs`,
  `assembleAndroidMain`, `jvmTest`); `scripts/audit-variability.sh` tanpa temuan baru.

### Testing Strategy

- **Unit (core)**: `Deployment` state machine (status legal transisi, event), gerbang F3
  (`RegisterTenantUseCase`), `ResolveDomainPack(code, version)` (versi tak dikenal **ditolak**, tanpa
  fallback senyap — Kontrak 4 tenant-variability-rules).
- **Paritas**: test yang mengiterasi tenant garment lama membandingkan perilaku pra/pasca pin versi
  (pola tabel emas B1–B7).
- **Server integration**: route gate test untuk semua endpoint builder (pola `RouteGateTest` /
  `RbacWriteGateTest`), termasuk test 403 peran tak berwenang dan tenant lain; test host-vs-JWT;
  test superadmin act-as; test idempoten migrasi.
- **Klien**: ViewModel per pane dengan fake use case; jvmTest untuk `BuilderApiClient` (pola
  `ApiClientAuthHeaderTest`).
- **E2E / visual**: cek mata `/builder` setelah login superadmin demo **dan** login owner `bordir-uji`;
  lebar sempit ~1280dp; `bordir-uji` dari chat sampai deploy (M2). Login dulu — "belum login" bukan
  alasan sah melaporkan UI (design-system-rules DoD).

### Monitoring & Error Handling

- Audit log untuk `DeploymentActivated`, `DeploymentRolledBack`, konfirmasi billing manual.
- Log terstruktur di route builder: `tenantId`, endpoint, keputusan gate (tanpa payload chat — privasi).
- Deploy gagal → status `FAILED` + pesan validator berpath; tenant tetap pada versi pin sebelumnya
  (deploy tidak pernah meninggalkan tenant tanpa versi aktif).
- Chat: galat agent/timeout → fallback deterministik + banner status di UI.

### Deployment & Rollback Plan

- Per fase: satu atau beberapa PR kecil, masing-masing lolos kompilasi 5 target + test di atas.
- M2 rilis infra: (1) Dockerfile server + build Wasm statis; (2) Caddy wildcard DNS-01;
  (3) set `PLATFORM_BASE_DOMAIN=wemakeerp.com` + `DB_APP_USER` di produksi; (4) flag daftar publik
  mati secara default.
- Rollback aplikasi: revert rilis (DB B terpisah; migrasi V81 additif/idempoten, aman dibiarkan).
- Rollback tenant (fitur produk): pin versi N−1 dengan gerbang data — bukan rollback kode.
- Teaching doc per fase di `docs/teaching/` setelah fase selesai (wajib, AGENTS.md §12).


    Agent --> Patch[DiscoveryDraft patch<br/>validator berpath]
    Patch --> Draft[(ops.discovery_drafts<br/>+ tenant_id)]
    Routes --> DeployUC[DeployTenantUseCase]
    DeployUC --> Lock[LockDomainPackUseCase<br/>version+1]
    Lock --> Pin[tenants.domain_pack_version]
    DeployUC --> Dep[(builder.deployments<br/>Deployment #N)]
    DeployUC --> BR[BuildRequest] --> MD[(moduledev: quote/ledger)]
    Pin --> Runtime[ERP runtime tenant<br/>/m/{code} via pack pin]
```

  konfirmasi bayar oleh superadmin. Gateway iPaymu = L1.
- **FR-M2-6 Wiring (F1)**: `server/routes/ServerRouteWiring.kt` baru; `Application.kt` memanggil satu
  fungsi `Routing.serverRouteWiring(deps)`; route builder (`BuilderChatRoutes`, `DeploymentRoutes`,
  `BuildQueueRoutes`, `BuilderBillingRoutes`) hanya lewat file ini. Ratchet `Application.kt` ≤600.
- **FR-M2-7 Daftar publik (flag)**: narasi + slug → `RegisterTenantUseCase` (TRIAL) + owner (gerbang F3
  wajib hijau dulu); dialihkan ke `/builder/chat` setelah agent membuat draf pertama.
- **FR-M2-8 Infra**: Dockerfile server + bundle Wasm statis; Caddy wildcard `*.wemakeerp.com` (DNS-01),
  `/api` → Ktor, sisanya → bundle + fallback SPA; `DB_APP_USER` wajib di produksi.

- **L1 Billing iPaymu** (port `PaymentGateway`, callback, dunning) — setelah gerbang design partner.
- **L2 Custom Domain** (CNAME/TXT, on-demand TLS, `tenant_hostnames`).
- **L3**: Roles & Akses lanjutan, Data Awal (impor Excel), Integrations, Backup/Ekspor, Aktivitas,
  fork pack shipped → pack data.
- Agregat `Project`/`ProjectMember`, role `PLATFORM_BUILDER`, level "Team" — sengaja tidak dibuat (plan §1).
- Menulis fitur konveksi baru di repo ini (Antrian Pembuatan untuk pack shipped menaut ke `wemade-erp`).
- Chat agent dengan tool deploy — agent **hanya** mengusulkan patch, selamanya.
