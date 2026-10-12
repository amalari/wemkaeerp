# TRD-A2: Node Aktif = Entitlement + Aktivasi Tenant

**Tanggal**: 2026-10-12 · **Penulis**: Achmad Jamaludin
**Rencana induk**: [`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §3 (Jalur A)
**Status**: Plan · Belum dimulai

---

## 1. Overview

**A2**: node aktif = entitlement + aktivasi tenant (preset hanya saran onboarding)

### Masalah
Saat ini, preset nodes (FOB/CMT/D2C) diaktifkan **semua** saat tenant dibuat. Ini menyebabkan:
- Tenant mendapat semua modul aktif tanpa выбор
- Preset bukan saran, tapi **aktivasi langsung**
- Tidak ada control atas modul yang dipakai

### Solusi
- Preset nodes di-create sebagai **bypassed** (= tidak aktif)
- Tenant harus **aktivasi manual** atau lewat **entitlement**
- Preset hanya **saran onboarding**, bukan aktivasi otomatis

---

## 2. Kondisi Sekarang (As-Is)

### 2.1 Dari mana `isBypassed`?

**File**: `core/.../domain/pipeline/CustomTenantPipeline.kt:125-140`

```kotlin
isBypassed = node.isBypassed  // diambil dari seed preset
```

**Seed preset** (`PresetNodeSeeds.kt`) menentukan mana yang bypassed:
- CMT: beberapa modul di-bypass (inventory, dll)
- FOB: semua aktif

### 2.2 Entitlement check?

**File**: `core/.../domain/pipeline/usecases/GetTenantModuleCatalogUseCase.kt:94-97`

```kotlin
isGrantedByPlan = entitlement.allowsCustomPlugins &&
    node.moduleId in entitlement.grantedCustomModuleIds
```

**Masalah**: Ini hanya untuk **custom plugins**, bukan preset nodes.

---

## 3. Desain (To-Be)

### 3.1 Core Logic

**File**: `core/.../domain/pipeline/CustomTenantPipeline.kt`

```kotlin
// SEBELUM (line ~136)
isBypassed = node.isBypassed,

// SESUDAH
isBypassed = true,  // semua preset nodes mulai sebagai bypassed
```

### 3.2 Entitlement Integration

Entitlement check harus affect **preset nodes** juga, bukan hanya custom plugins:

```kotlin
// Di GetTenantModuleCatalogUseCase
isGrantedByPlan = when {
    node.isCustomPlugin -> entitlement.allowsCustomPlugins &&
        node.moduleId in entitlement.grantedCustomModuleIds
    else -> node.moduleId in entitlement.grantedModuleIds  // baru: cek entitlement untuk preset
}
```

### 3.3 UI Flow

1. **Tenant baru dibuat** → semua nodes bypassed
2. **User lihat kanvas** → nodes bypassed tampil dengan badge "Nonaktif"
3. **User klik "Aktivasi"** → node jadi aktif
4. **Atau**: jika entitlement ada → node auto-aktif

---

## 4. Files yang Perlu Diedit

| Layer | File | Perubahan |
|---|---|---|
| Domain | `CustomTenantPipeline.kt` | `fromPreset()` → `isBypassed = true` |
| Domain | `GetTenantModuleCatalogUseCase.kt` | Cek entitlement untuk preset nodes |
| Domain | `PipelineCatalogReconciler.kt` | Respect existing `isBypassed` state |
| Presentation | `PipelineNodeCard.kt` | Tampilan node bypassed + tombol aktivasi |
| Presentation | `PipelineNodeViewModel.kt` | State + event untuk aktivasi |
| Test | `*PipelineTest.kt` | Test behavior baru |

---

## 5. Definition of Done

### Core Logic
- [ ] `CustomTenantPipeline.fromPreset()` set semua nodes ke `isBypassed = true`
- [ ] Entitlement check affects preset nodes (bukan hanya custom plugins)
- [ ] Reconciler respects existing bypassed state

### Tests
- [ ] Test: tenant baru tidak punya node aktif tanpa aktivasi
- [ ] Test: aktivasi node via UI works
- [ ] Test: bypassed node tidak muncul di kanvas aktif

### UI
- [ ] Node bypassed tampil dengan badge "Nonaktif" / warna muted
- [ ] Tombol "Aktivasi" di node bypassed
- [ ] Klik aktivasi → node jadi aktif

---

## 6. Effort Estimate

| Komponen | Effort | Risk |
|---|---|---|
| Core logic | Low | Rendah |
| UI changes | Medium | Medium |
| Tests | Medium | Rendah |

**Total**: 1-2 sprint

---

## 7. Referensi

- [`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §3 - Rencana induk
- [`CustomTenantPipeline.kt`](../../core/src/commonMain/kotlin/com/eventverse/app/domain/pipeline/CustomTenantPipeline.kt) - Implementasi pipeline
- [`PipelineNodeCard.kt`](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/pipeline/components/PipelineNodeCard.kt) - Komponen UI node
