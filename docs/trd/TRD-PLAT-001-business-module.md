# TRD-PLAT-001 (B6): `BusinessModule` sebagai Data Domain Pack

## 1. Document Context and Administration

- **Title & Unique ID**: `BusinessModule` sebagai data pack — `TRD-PLAT-001` bagian B6 (bagian Blueprint: [`TRD-PLAT-001-blueprint.md`](TRD-PLAT-001-blueprint.md))
- **Revision History**

| Versi | Tanggal | Penulis | Catatan |
|---|---|---|---|
| 0.1 | 2026-09-29 | Achmad Jamaludin (dibantu Claude) | Draf dari pemetaan kode & persistensi (2 agen riset). Prasyarat B5 terpenuhi (ledger gerbang kosong). |
| 0.2 | 2026-09-29 | Achmad Jamaludin (dibantu Claude) | **B6a–B6g selesai.** Snapshot akses 690 keputusan identik di setiap tahap; parser 14→1; enum dihapus; menu & rute generik dari pack; bukti e-learning. Sisa: `ModuleCategory` di layar RBAC, breadcrumb generik, B7. |

### Summary & Business Context

`enum class BusinessModule` (15 entri) adalah enum terakhir dan **paling berisiko** di Jalur B. Ia menjadi kunci
RBAC, entitlement, katalog modul, menu, dan gerbang route. Selama ia enum, pack lain (e-learning) tidak bisa punya
modul tanpa mengubah kode inti. B6 memindahkannya menjadi data pack dengan satu syarat keras:
**tidak ada pengguna yang kehilangan atau mendapat akses** karena refactor ini.

### Stakeholders & Approvers

Product & Tech Lead: Achmad Jamaludin · Implementasi: Claude (Jalur B) · QA: test paritas akses + cek visual menu per jabatan

### Goals (In-Scope)

- `ModuleId` value class + `ModuleDefinition` per pack; pack garment berisi 15 modul yang **identik** dengan enum.
- Satu parser kunci modul tersimpan (NAME & code) untuk seluruh persistensi & kabel; **nol migrasi data**.
- Semua `when` exhaustive & rantai `if (module == X)` menjadi data/registry.
- Menu & rute klien dari pack; modul tanpa layar khusus terbuka lewat rute generik.
- Bukti akhir: modul e-learning fiktif muncul di menu, tergerbang, tanpa menyentuh kode inti.

### Non-Goals (Out-of-Scope)

- Pemilihan pack per tenant (B7): `DomainPackRegistry.soleActivePack` tetap.
- Mengganti format kunci tersimpan (NAME di RBAC/entitlement tetap NAME).
- Layar khusus untuk modul pack baru. Modul baru memakai layar generik sampai dibangun.

## 2. Functional Requirements

### Kondisi sekarang (hasil pemetaan)

| Aspek | Temuan |
|---|---|
| Referensi | 405 referensi entri di kode utama (core 276, app 60, server 69) + 224 di test |
| Exhaustive `when` | 2: `GarmentSlots.forModule`, `ModuleWorkspaceScreen.sampleRowsFor`; plus `when (screen)` di `App.kt` atas `AppNavScreen` (1:1 dengan modul) |
| Rantai if | `ModuleWorkspaceScreen.kt:76-159` mengarahkan 10 modul ke layar khusus |
| Urutan | menu = urutan deklarasi enum (`NavMenu` iterasi `entries`) |
| "Semua modul" | sentinel `== entries.toSet()` di entitlement (5 tempat) |
| Kunci NAME (`CRM_SALES`) | `custom_roles.module_permissions` (kunci JSONB), `department_module_assignments.module` + **id baris** `dma-…-NAME-…`, `tenant_module_entitlements.granted_modules`, `/me/access`, telemetri, URL penugasan |
| Kunci code (`crm_sales`) | `module_catalog_entries.module_id`, `tenant_pipelines.graph_data.nodes[].moduleId`, custom field owner, prospek |
| Parser | 12 parser; **semuanya membuang nilai tak dikenal diam-diam** (fail-closed untuk akses, tapi tanpa jejak) |
| JWT | tidak membawa id modul |

- **FR-1 Identitas**: `ModuleId(value)` = code (`crm_sales`). NAME tersimpan = `code.uppercase()`. Untuk ke-15 modul
  garment hubungan ini berlaku persis (dibuktikan test), jadi parser tunggal `ModuleIdCodec` membaca **keduanya**
  tanpa tabel pemetaan, dan menulis format lama di tiap lokasi (NAME di RBAC/entitlement, code di katalog/pipeline).
- **FR-2 Definisi**: `ModuleDefinition(id, displayName, description, category, kind, iconKey, scopeCapability,
  supportedScopes, slot?)` di pack. Urutan list = urutan menu. `slot` menggantikan `GarmentSlots.forModule`.
- **FR-3 Nilai tak dikenal**: tetap dibuang (fail-closed) tapi **dicatat** (log peringatan berisi lokasi & nilai).
  Membuang tanpa jejak = akses hilang tanpa ada yang tahu sebabnya.
- **FR-4 "Semua modul"**: sentinel entitlement dibandingkan dengan `pack.modules`, bukan `entries`.
- **FR-5 Layar**: registry `ModuleScreenRegistry` (id → layar khusus). Modul tanpa entri → `ModuleWorkspaceScreen`
  generik. `AppNavScreen` tetap untuk layar bawaan; rute generik `/m/{code}` untuk modul data-only.
- **FR-6 Menu**: `NavMenu` dibangun dari `pack.modules` (kategori & urutan pack) ∩ keputusan `/me/access`.
- **FR-7 Gerbang**: modul baru tanpa route tidak butuh apa-apa; modul baru **dengan** route wajib punya aturan di
  `TenantRouteGatePolicy` atau `moduleGate`. `RouteGateTest` (ledger kosong) tetap penjaganya.

## 3. Non-Functional Requirements (NFRs)

| Category | Requirement & Target Metrics | Rationale / Mitigation |
| :--- | :--- | :--- |
| **Security** | **Nol perubahan akses**: matriks `callerDecisions` untuk setiap (pengguna nyata × modul) identik sebelum & sesudah setiap tahap | Snapshot dari DB B (`wemake_erp`) jadi test paritas; perubahan = merah |
| **Security** | Tidak ada modul yang lolos gerbang karena berubah jadi data | `RouteGateTest` (ledger kosong) + test "setiap modul ber-route punya aturan" |
| **Availability** | Nol migrasi data; format tersimpan identik per lokasi | Rollback = `git revert`; DB A & B tetap kompatibel untuk cutover |
| **Maintainability** | Satu parser kunci modul (hari ini 12) | Menghapus kelas bug "parser berbeda pendapat" |
| **Performance** | Lookup modul O(15) per keputusan | Setara enum |
| **Observability** | Log setiap nilai modul tak dikenal saat dibaca | FR-3 |

## 4. System Architecture & Technical Design

```mermaid
flowchart LR
    Pack[DomainPack.modules<br/>ModuleDefinition] --> Codec[ModuleIdCodec<br/>NAME ⇄ code]
    Codec --> DB[(custom_roles · assignments<br/>entitlements · catalog · pipeline)]
    Pack --> Engine[AccessDecisionEngine]
    Engine --> Gate[Gerbang route] & Me[/me/access/]
    Me --> Nav[NavMenu dari pack]
    Pack --> Screens[ModuleScreenRegistry<br/>+ /m/{code} generik]
```

- **Pola jembatan (B3)**: `typealias BusinessModule = ModuleId` sementara; `BusinessModule.CRM_SALES` → `GarmentModules.CRM_SALES`;
  anggota (`displayName`, `category`, `kind`, `iconKey`, `scopeCapability`, …) → extension yang membaca pack.
- **Id baris penugasan** (`dma-…-QUALITY_CONTROL-…`) tetap memakai NAME. Tidak ada migrasi id.

## 5. Testing, Deployment, and Operations

### Urutan kerja (tiap tahap satu commit, hijau, bisa di-revert)

| # | Tahap | Isi |
|---|---|---|
| B6a | Definisi | `ModuleDefinition` + `GarmentModules` dibangun **dari enum**; tabel emas 15 modul (semua properti, urutan); test `NAME == code.uppercase()` |
| B6b | Snapshot akses | Test paritas: `callerDecisions` untuk setiap pengguna & jabatan nyata di DB B × 15 modul dibekukan |
| B6c | Parser tunggal | `ModuleIdCodec` menggantikan 12 parser; log nilai tak dikenal; round-trip terhadap nilai nyata DB |
| B6d | Jembatan | `typealias` + `GarmentModules.X` + extension anggota; `GarmentModules` jadi data literal |
| B6e | `when` → data | `slot` di definisi; `sampleRowsFor` & rantai if → `ModuleScreenRegistry`; sentinel entitlement dari pack |
| B6f | Klien | `NavMenu` dari pack; rute `/m/{code}`; hapus enum |
| B6g | Bukti | Pack e-learning fiktif (test + fixture): modul data-only muncul di menu, layar generik, tergerbang |

### Technical Acceptance Criteria

- **AC-1**: Snapshot akses B6b identik di akhir setiap tahap B6c–B6f.
- **AC-2**: Round-trip semua nilai modul di DB B (role JSON, penugasan, entitlement, katalog, pipeline) identik byte-per-byte.
- **AC-3**: Menu setiap jabatan `wemade-demo` (6 jabatan) & owner `bordir-uji` identik secara visual sebelum/sesudah B6f.
- **AC-4**: `RouteGateTest` tetap dengan ledger kosong; core/app/server hijau; JVM/Wasm/JS terkompilasi.
- **AC-5**: B6g: modul `grading` e-learning terbuka di `/m/grading` untuk jabatan ber-`VIEW`, 403 tanpa wewenang.

### Rollback

Tanpa migrasi. Setiap tahap = satu commit yang bisa di-revert. Snapshot akses (B6b) adalah alarm: bila merah, tahap
itu tidak di-merge.
