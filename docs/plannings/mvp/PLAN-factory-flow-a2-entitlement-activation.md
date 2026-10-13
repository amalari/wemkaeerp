# TRD-A2: Node Aktif = Entitlement + Aktivasi Tenant

**Tanggal**: 2026-10-12 · **Penulis**: Achmad Jamaludin
**Rencana induk**: [`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §3 (Jalur A)
**Status**: **Selesai** (2026-10-14; kompilasi Android ditunda atas keputusan pemilik — SDK tak ada di mesin dev) — core, badge, tombol, label dwibahasa, gerbang entitlement, tes core & server, cek visual `wemade-demo` selesai; sisa: kompilasi Android (butuh SDK)

---

## 1. Konteks dan Administrasi

- **ID**: TRD-A2 — Node Aktif = Entitlement + Aktivasi Tenant
- **Status**: **Diusulkan; siap implementasi**
- **Rujukan**:
  - [`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §3 - Rencana induk
  - [`CustomTenantPipeline.kt`](../../core/src/commonMain/kotlin/com/eventverse/app/domain/pipeline/CustomTenantPipeline.kt) - Implementasi pipeline
  - [`GetTenantModuleCatalogUseCase.kt`](../../core/src/commonMain/kotlin/com/eventverse/app/domain/pipeline/usecases/GetTenantModuleCatalogUseCase.kt) - Katalog modul
  - [`PipelineNodeCard.kt`](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/pipeline/components/PipelineNodeCard.kt) - Komponen UI node
  - TRD-PLAT-012 (gerbang fail-closed)

### Masalah

Saat ini, preset nodes (FOB/CMT/D2C) diaktifkan **semua** saat tenant dibuat. Ini menyebabkan:
- Tenant mendapat semua modul aktif tanpa pilihan
- Preset bukan saran, tapi **aktivasi langsung**
- Tidak ada control atas modul yang dipakai

### Solusi MVP

- Preset nodes di-create sebagai **bypassed** (= tidak aktif)
- Tenant harus **aktivasi manual** atau lewat **entitlement**
- Preset hanya **saran onboarding**, bukan aktivasi otomatis

---

## 2. Track 0 — Contract (serial)

> **Temuan kode (2026-10-13)**: Inventory apa yang sudah ada vs apa yang perlu ditambah.

### T0.1 — Inventory: Di mana `isBypassed` diset?

**File**: `core/.../domain/pipeline/CustomTenantPipeline.kt:125-140`

```kotlin
isBypassed = node.isBypassed  // diambil dari seed preset
```

**Seed preset** (`PresetNodeSeeds.kt`) menentukan mana yang bypassed:
- CMT: beberapa modul di-bypass (inventory, dll)
- FOB: semua aktif

### T0.2 — Inventory: Entitlement check untuk preset nodes?

**File**: `core/.../domain/pipeline/usecases/GetTenantModuleCatalogUseCase.kt:94-97`

```kotlin
isGrantedByPlan = entitlement.allowsCustomPlugins &&
    node.moduleId in entitlement.grantedCustomModuleIds
```

**Masalah**: Ini hanya untuk **custom plugins**, bukan preset nodes.

### T0.3 — Keputusan Kontrak

| Keputusan | Nilai |
|---|---|
| Semua preset nodes dibuat sebagai `isBypassed = true` | WAJIB |
| Entitlement check harus mencakup preset nodes (bukan hanya custom plugins) | WAJIB |
| Reconciler harus respect existing `isBypassed` state | WAJIB |
| UI: nodes bypassed wajib punya badge "Nonaktif" | WAJIB |
| UI: tombol aktivasi di node bypassed | WAJIB |

### T0.4 — Inventory: Apa yang sudah jalan?

| Komponen | Status | Bukti |
|---|---|---|
| Entitlement table (`tenant_module_entitlements`) | ✅ Sudah ada | `TenantModuleEntitlementsTable` |
| GET `/api/tenant/entitlement` | ✅ Sudah ada | `PipelineRoutes.kt:75-97` |
| GET `/pipeline/modules` dengan entitlement annotation | ✅ Sudah ada | `PipelineRoutes.kt:147-158` |
| `isGrantedByPlan` untuk custom plugins | ✅ Sudah ada | `GetTenantModuleCatalogUseCase.kt:94-97` |
| UI: module activation panel | ✅ Sudah ada | `TenantModulePanel.kt:82` - `canActivate = module.isGrantedByPlan` |
| UI: bypassed node di kanvas (filter) | ✅ Sudah ada | `FactoryFlowUiState.kt:68` - `!node.isBypassed \|\| node.isNewFromCatalog` |

### T0.5 — Inventory: Apa yang belum?

| Komponen | Status | Gap |
|---|---|---|
| `CustomTenantPipeline.fromPreset()` → semua nodes bypassed | ❌ Belum | Seed preset menentukan bypassed, bukan hardcoded true |
| Entitlement check untuk preset nodes | ❌ Belum | `isGrantedByPlan` hanya untuk custom plugins |
| UI: badge "Nonaktif" di node bypassed | ❌ Belum | Nodes bypassed tidak punya penanda visual |
| UI: tombol aktivasi langsung di kanvas | ❌ Belum | Aktivasi hanya lewat module panel |

---

## 3. Track A — Core (paralel)

> Implementasi domain logic.

### A1 — Pipeline Creation: Semua nodes bypassed

**File**: `core/.../domain/pipeline/CustomTenantPipeline.kt`

```kotlin
// SEBELUM (line ~136)
isBypassed = node.isBypassed,

// SESUDAH
isBypassed = true,  // semua preset nodes mulai sebagai bypassed
```

### A2 — Entitlement Integration untuk Preset Nodes

**File**: `core/.../domain/pipeline/usecases/GetTenantModuleCatalogUseCase.kt`

```kotlin
// Sebelum: hanya custom plugins
isGrantedByPlan = entitlement.allowsCustomPlugins &&
    node.moduleId in entitlement.grantedCustomModuleIds

// Sesudah: preset nodes juga dicek
isGrantedByPlan = when {
    node.isCustomPlugin -> entitlement.allowsCustomPlugins &&
        node.moduleId in entitlement.grantedCustomModuleIds
    else -> node.moduleId in entitlement.grantedModuleIds  // BARU
}
```

### A3 — Reconciler respects bypassed state

**File**: `core/.../domain/pipeline/PipelineCatalogReconciler.kt`

确保 existing `isBypassed` state tidak di-overwrite saat reconciler berjalan.

### A4 — Tests: Core Logic

- [ ] Test: tenant baru tidak punya node aktif tanpa aktivasi manual
- [ ] Test: entitlement auto-aktifkan node jika ada di grants
- [ ] Test: reconciler tidak menimpa bypassed state

---

## 4. Track B — Presentation (paralel)

> Implementasi UI.

### B1 — Badge "Nonaktif" di Node Bypassed

**File**: `app/shared/.../presentation/pipeline/components/PipelineNodeCard.kt`

Tambahkan badge visual untuk nodes yang bypassed:
- Warna muted/abu-abu
- Label "Nonaktif" atau ikon lock

### B2 — Tombol Aktivasi di Kanvas

**File**: `app/shared/.../presentation/pipeline/components/PipelineNodeCard.kt`

Tombol "Aktivasi" yang muncul pada node bypassed saat di-hover atau diklik.

### B3 — State Management

**File**: `app/shared/.../presentation/pipeline/FactoryFlowUiState.kt`

Tambahkan state untuk aktivasi:
```kotlin
data class FactoryFlowUiState(
    // ... existing
    val activatingNode: String? = null,  // nodeId yang sedang diaktifkan
)
```

### B4 — Tests: UI

- [ ] Test: bypassed node tampil dengan badge "Nonaktif"
- [ ] Test: tombol "Aktivasi" muncul di node bypassed
- [ ] Test: klik aktivasi → node jadi aktif

---

## 5. Track C — Integration (paralel)

> End-to-end flow.

### C1 — Activation Flow

1. User lihat kanvas → nodes bypassed tampil dengan badge "Nonaktif"
2. User klik "Aktivasi" pada node tertentu
3. UI emit event ke `POST /api/tenant/pipeline/modules`
4. Server update `isActive = true`
5. UI refresh → node jadi aktif

### C2 — Visual Check

- [ ] Cek visual di tenant `wemade-demo` - nodes bypassed terlihat
- [ ] Cek visual di tenant `bordir-uji` - bypassed nodes ada badge

---

## 6. Definition of Done

### Track A — Core
- [x] `CustomTenantPipeline.fromPreset()` set semua nodes ke `isBypassed = true` (tes: `PresetActivationStateTest`, semua preset)
- [x] Entitlement berlaku untuk preset nodes (`GetTenantModuleCatalogUseCase` + `TenantModuleEntitlement.validate`; server menolak 400 — `PipelineModuleApiTest.postActivation_beyondPlanQuota_…`)
- [x] Reconciler tidak menimpa aktivasi manual (`PresetActivationStateTest.reconcile_keepsManualActivationOfExistingNodes`)
- [x] Tes: tenant baru tanpa modul aktif (3 preset), `withPresetActivation` pada preset non-default (CMT)
- [ ] Tes "entitlement auto-aktifkan node" — dibatalkan: aktivasi tetap manual (keputusan A2); entitlement hanya mengizinkan

### Track B — Presentation
- [x] Badge "Nonaktif" di node bypassed
- [x] Tombol "Aktivasi" hanya untuk node yang boleh (paket + kuota); ditahan saat `isSaving` (guard klik ganda)
- [x] Klik aktivasi → `SetModuleActive` → `POST /modules/activation`
- [x] Tes presentasi tingkat logika: `NodeActivationGateTest` (4 kasus: paket/kuota/tak dikenal) + `FactoryFlowViewModelTenantDataTest` (SetModuleActive → aktif). Tes komposabel (badge/tombol tergambar) **tidak ada**: app/shared belum punya infrastruktur Compose UI test — ditutup lewat cek visual 2026-10-13
- [x] Label "Nonaktif"/"Aktivasi" dwibahasa (`NodeActivationLabels.kt`, resource `factory_flow_node_*_id/_en`)

### Track C — Integration
- [x] Full flow bypassed → aktivasi → aktif dicek di browser (2026-10-13, tenant `wemade-demo`, Chromium headless, port 3101/8181): nonaktifkan lewat panel → node bypassed tampil dengan badge "Nonaktif" + tombol "Aktivasi" → klik → "Modul diaktifkan", header "9 modul aktif"
- [~] Kompilasi 5 target: JVM, Wasm, JS bersih; **Android ditunda** (keputusan pemilik 2026-10-14, SDK tak terpasang) — jalankan di CI/mesin ber-SDK
- [x] Cek visual `wemade-demo`
- [x] Cek visual tenant non-default (2026-10-14): `bordir-uji` tak ada di seed, diganti reset ke starter **CMT Jasa Jahit** (nama/peran makloon) — 9 node Nonaktif + tombol Aktivasi. **Temuan & perbaikan**: tenant baru (0 aktif) sebelumnya melihat kanvas KOSONG ("Tidak ada modul yang cocok") karena node bypassed disembunyikan default; kini `FactoryFlowUiState.hidesBypassedNodes` menampilkannya bila tak ada node aktif (`FactoryFlowBypassVisibilityTest`)
- [x] Temuan visual badge ganda ("Nonaktif" + "Di-bypass") diperbaiki: node bypassed hanya satu badge "Nonaktif"; header kartu tidak lagi terpotong (dilihat ulang 2026-10-13)
- [x] Cek visual kuota penuh (tenant STARTER, 8 aktif/batas 5: badge ada, tombol "Aktivasi" tersembunyi) dan bahasa EN ("Inactive"/"Activate")

## 7. Effort Estimate

| Track | Komponen | Effort | Risk |
|---|---|---|---|
| Track A | Core logic | Low | Rendah |
| Track B | UI changes | Medium | Medium |
| Track C | Integration + tests | Medium | Rendah |

**Total**: 1-2 sprint

---

## 8. Referensi

- [`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §3 - Rencana induk
- [`CustomTenantPipeline.kt`](../../core/src/commonMain/kotlin/com/eventverse/app/domain/pipeline/CustomTenantPipeline.kt) - Implementasi pipeline
- [`GetTenantModuleCatalogUseCase.kt`](../../core/src/commonMain/kotlin/com/eventverse/app/domain/pipeline/usecases/GetTenantModuleCatalogUseCase.kt) - Katalog modul
- [`PipelineNodeCard.kt`](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/pipeline/components/PipelineNodeCard.kt) - Komponen UI node
- TRD-PLAT-012 (gerbang fail-closed)
