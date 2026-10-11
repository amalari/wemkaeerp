# Discovery Note — M0 Builder (fondasi WeMake Builder console)

**Tanggal**: 2026-09-30 · **Penulis**: Achmad Jamaludin (dibantu Claude) · **Jalur**: B
**Rencana induk**: [`PLAN-builder-console.md`](PLAN-builder-console.md) — Discovery Note ini adalah syarat
sebelum kode M0, sebagai input `wemade-feature-workflow` Gerbang 1.
**Lingkup**: hanya M0 (permission `MANAGE_BUILDER`, `BuilderShell` + Overview + Pengaturan,
`DiscoveryDraft.tenant_id`, pin versi pack, migrasi impor `Deployment #1 IMPORTED`, cek JWT vs host,
penegakan 1-owner-per-email). M1 (chat/patch) dan M2 (deploy/rollback/wiring) punya discovery sendiri.

## 1. Kebutuhan

- **Siapa memakai**: owner tenant (TENANT_ADMIN) membuka Builder project-nya;
  kolaborator tenant yang diberi `MANAGE_BUILDER`; superadmin (mengundang & melihat semua project).
- **Data milik**: semuanya milik **tenant** (draf, kelak conversation/deployment). Draf lama
  (`ops.discovery_drafts` V78) milik pribadi pemanggil — dibiarkan apa adanya.
- **Berubah kapan**: draf berubah tiap sesi builder; pin versi pack berubah **hanya saat deploy** (M2);
  migrasi impor berjalan sekali per tenant (idempoten).

## 2. Fitur serupa

- Perintah: `scripts/find-similar-feature.sh builder deploy console`
- Temuan: tidak ada fitur builder/console yang sudah ada (0 hasil di domain core, `BusinessModule`,
  `AppNavScreen`, layar, route, migrasi). Yang paling dekat adalah pola, bukan fitur:
  - funnel discovery: `DiscoveryRoutes.kt`, `DiscoveryHandoffUseCases.kt`, teaching
    `teaching-discovery-a1-a7-a9-vertical-slice.md`;
  - pack per tenant: teaching `teaching-b7-tenant-domain-pack.md` (`LockDomainPackUseCase`,
    `AssignTenantDomainPackUseCase`);
  - navigasi layar baru: teaching `teaching-gcp-style-clay-nav-drawer.md` (pola sidebar).
- Keputusan: **Baru → tiru pola terdekat** (funnel discovery untuk use case & route; B7 untuk versi pack).

## 3. Jenis

**Governance-type platform screen — BUKAN `BusinessModule` baru.** Alasan:
- Builder tidak dijual per modul, tidak dihitung kuota paket, dan **tidak pernah muncul di kanvas
  Factory Flow** (§5.2 module-integration-rules).
- Tidak ada entri baru di `OperationalModuleCatalog`, tidak ada cabang `ModuleArchetype.forModule`,
  tidak ada slot `PortDataTypeRegistry` baru di M0.

## 4. Uji Variabilitas

| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---|---|---|---|---|
| `MANAGE_BUILDER` | tidak (sistem) | tidak | tidak (diberikan per user) | **kode** (`Permission` enum) | — |
| `Deployment.status` (`IMPORTED`, dst.) | tidak (status teknis) | tidak | tidak | **kode** | — |
| Pack & blueprint di draf | **ya** | **ya** | **ya** (lewat chat, M1) | **data** (`DiscoveryDraft`) | disalin → draf beku saat kunci/lock (Kontrak 5) |
| Pin versi pack per tenant | **ya** | ya | tidak (mesin yang menetapkan saat deploy) | data (`tenants.domain_pack_version`) | beku per deployment |
| Label layar Builder | tidak | tidak | tidak | kode (Clay string) | — |

## 5. Core & extend

- **Core baru**: `core/.../domain/builder/deploy/Deployment.kt` (agregat deployment + `Deployment #N`),
  paket netral industri. M0 hanya butuh status `IMPORTED`; enum penuh didefinisikan sekarang agar M2
  tidak mengubah tipe.
- **Titik extend**:
  - `Permission` (`AuthValueObjects.kt`) — tambah `MANAGE_BUILDER`;
  - `DiscoveryDraft` (`domain/discovery/DiscoveryDraft.kt`) — tambah `tenantId: TenantId?`;
  - `RegisterTenantUseCase` — gerbang satu-owner-per-email (F3);
  - `TenantResolutionPlugin.kt` — cek host-vs-JWT untuk user ber-tenant, carve-out superadmin (F2);
  - `DomainPackUseCases.kt` — `ResolveDomainPack(code, version)` sebagai use case **baru** di samping
    resolver lama `invoke(code)` (effective, tidak diubah);
  - `App.kt` — satu cabang delegasi ke `BuilderShell.kt` (568 → tetap ≤600);
  - `ModuleSchemaMap` + migrasi **V81** — schema `builder`, tabel `deployments`.
- **Contoh yang ditiru** (file:baris):
  - migrasi idempoten + backfill: `V75__tenant_domain_pack.sql` (`ADD COLUMN IF NOT EXISTS`);
  - schema + RLS: `V76__schema_per_module.sql` + `ModuleSchemaMap.kt`;
  - use case pack: `DomainPackUseCases.kt:42-76` (`LockDomainPackUseCase`, `AssignTenantDomainPackUseCase`);
  - cek tenant JWT: `TenantResolutionPlugin.kt:176-194` (pola `X-Tenant-Slug` + act-as).
- **Jangan disentuh**: `Application.kt` (698 baris, ratchet — wiring builder routes bukan M0),
  `DealDetailDialog.kt` & daftar utang file-size §6, file hasil generate (`Res`), resolver lama
  `invoke(code)` (perilaku effective tetap).

## 6. I/O & kanvas

- Port masuk: tidak ada di kanvas — Builder tidak berpartisipasi dalam Factory Flow.
- Port keluar: tidak ada. (M2: `Deployment` menghasilkan pack terkunci yang dikonsumsi
  `AssignTenantDomainPackUseCase` — kontrak use case, bukan port kanvas.)
- Kanvas: **tidak tampil** — Builder adalah layar tata kelola platform (`/builder/*`), bukan node
  level 1 maupun level 2.
- Telemetri: tidak ada (bukan node operasional).

## 7. Governance

| Operasi | Level minimum | Peran yang ditolak (dites 403) |
|---|---|---|
| Buka Builder (`/builder/*`) | `MANAGE_BUILDER` (TENANT_ADMIN default) | user tenant tanpa izin; user tenant lain (host ≠ tenant) |
| Ubah Pengaturan (identitas, kolaborator) | `MANAGE_BUILDER` | idem |
| Baca deployment | `MANAGE_BUILDER` | idem |
| Undang/aktifkan tenant | superadmin saja | TENANT_ADMIN tenant lain |

- **Gate**: permission `MANAGE_BUILDER` (bukan gate modul — Builder bukan `BusinessModule`); route tulis
  fail-closed (Kontrak 7): keputusan tidak bisa dihitung → 403.
- **ScopeCapability**: tidak berlaku (bukan modul katalog); datanya RLS per tenant di schema `builder`
  lewat `apply_tenant_rls_in('builder', 'deployments')`.
- **Entitlement**: tidak masuk kuota paket (setara governance — §5.2 module-integration-rules).
- **Peran yang tidak boleh**: user tenant tanpa `MANAGE_BUILDER`; user tenant A menyentuh tenant B
  via host maupun header.

## 8. Ukuran → TRD?

- Agregat baru: 1 (`Deployment`, M0 subset `IMPORTED`).
- Migrasi: **V81** — schema `builder`, tabel `builder.deployments`, kolom `ops.discovery_drafts.tenant_id`,
  kolom `tenants.domain_pack_version` + backfill `Deployment #1 IMPORTED` per tenant (idempoten).
- Perubahan file besar: `App.kt` 568 → wajib ≤600; `Application.kt` tidak disentuh di M0.
- → **TRD perlu**: [`TRD-PLAT-002-builder.md`](../trd/TRD-PLAT-002-builder.md), lingkup MVP M0–M2
  (M0 dipecah paling rinci; M1–M2 tingkat rencana).

- Aksesnya lewat permission platform baru `MANAGE_BUILDER` pada `Permission` enum yang sudah ada
  (`core/.../domain/auth/AuthValueObjects.kt`) — pola yang sama dengan `MANAGE_PLATFORM`.
