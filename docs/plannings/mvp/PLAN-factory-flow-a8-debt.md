# Rencana: A8 — Technical Debt Collection

**Tanggal**: 2026-10-12 · **Penulis**: Achmad Jamaludin
**Rencana induk**: [`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §3 (Jalur A)

---

## 0. Konteks & Ringkasan

A8 adalah kumpulan utang teknis yang terlihat selama development A1-A7. Bukan fitur baru, tapi perbaikan teknis yang diperlukan untuk stabilitas dan keamanan.

| Item | Fokus | Impact |
|---|---|---|
| A8a | Provider telemetri modul lain | Data KPI lengkap |
| A8b | Skema id node pipeline | Konsistensi ID |
| A8c | FK restore-presets | Data integrity |
| A8d | GET locations fail-open | Keamanan |
| A8e | Editor tahap RBAC | Keamanan |

---

## 1. A8a — Provider Telemetri Modul Lain

### Status: **BELUM DIMULAI** (butuh use case baru per modul)

### Apa
Menambahkan `ModuleTelemetryProvider` untuk modul yang belum punya:

**Sudah ada:**
- `SamplingTelemetryProvider` → `sampling_order`
- `ProductionTelemetryProvider` → `production_mrp`
- `CostingTelemetryProvider` → `costing_hpp`

**Belum ada (perlu use case baru):**
- QC (`quality_control`) → inspection pass/fail rate (belum ada repository/modul QC)
- Fulfillment (`fulfillment`) → packing progress (repository packing/shipment baru mendarat 2026-10-12, dikerjakan sesi Fulfillment)

**Sudah ditambahkan (2026-10-12):**
- Inventory (`inventory`) → **tidak** stock level; lihat catatan deviasi di §1.1. Metrik diturunkan ke
  data katalog nyata karena sistem tidak punya ledger stok.

### Core
- `domain/pipeline/ModuleTelemetryProvider.kt`
- `domain/pipeline/ExistingTelemetryProviders.kt`

### Titik extend
- Tambah use case per modul (bisa reuse existing repository)
- Tambah provider baru per modul
- Register di `PipelineTelemetryRoutes.kt`

### Effort: **Medium** (per modul)

### 1.1 Deviasi Inventory (2026-10-12) — metrik stok tidak feasible

Rencana awal menulis Inventory → "stock level, low stock alerts" dengan asumsi "bisa reuse existing
repository" (`A8-debt.md` §1). **Asumsi itu keliru**: `inventory` hanya dipunggungi katalog master data
(`MaterialItemRepository` + `MaterialPriceRepository` di `core/.../domain/masterdata/`); tidak ada
tabel/kolom stok, `onHand`, atau reorder level (dicek di `V26__create_master_data_schema.sql` dan
seluruh `core`/`server`). Ledger stok adalah fitur Gudang yang belum di-discovery
(`PLAN-dual-track-garment-and-general-platform.md:67`), jadi angka stok **tidak boleh dikarang**
(aturan Kontrak 4/8: nilai yang tidak ada tidak dipalsukan).

Yang diimplementasikan: `GetInventoryNodeTelemetryUseCase` +
`InventoryTelemetryProvider` (`core/.../domain/masterdata/usecases/`,
`core/.../domain/pipeline/ExistingTelemetryProviders.kt`), di-wire di `ServerRouteWiring.kt`.
`wipPieces` = jumlah material aktif (non-arsip); `healthStatus` dari rasio material aktif **tanpa
harga acuan** (material tak berharga tidak bisa ikut HPP hilir) — pola sama dengan CRM/TechPack.

---

## 2. A8b — Skema ID Node Pipeline

### Status: **SELESAI** (sudah terdokumentasi di kode)

### Apa
Memastikan ID node pipeline konsisten dan predictable:

**Sekarang (sudah konsisten):**
- ID dari preset: `{prefix}-{moduleCode}` → `"fob-crm-sales"`, `"cmt-sampling-order"`, `"d2c-tech-pack-bom"`
- ID dari catalog (reconciler): `"node-{moduleCode}"` → `"node-crm-sales"`, `"node-tech-pack-bom"`
- ID dari custom plugin: `"custom-{moduleId}"`

**Dokumentasi:** `PresetNodeSeeds.kt:59-65` - `nodeIdPrefix()` function

### Core
- `domain/pipeline/CustomTenantPipeline.kt`
- `domain/pipeline/PipelinePresetFactory.kt`

### Effort: **Low** (sudah ada)

---

## 3. A8c — FK Restore Presets

### Status: **PREEMPTIF** (belum ada bug yang dilaporkan)

### Apa
Saat tenant restore preset (misal: dari CMT ke FOB), foreign key antar modul harus tetap valid:

**Contoh kasus:**
- TechPack referensi SamplingOrder (A4: `source_sampling_order_id`)
- BulkWorkOrder referensi CostingSheet (A4: `costing_sheet_id`)
- Saat restore, referensi ini harus di-preserve atau di-clear dengan benar

**Status:** Referensi menggunakan String ID (bukan FK database), sehingga tetap valid saat pipeline direstore. Belum ada bug yang dilaporkan.

### Core
- `domain/pipeline/usecases/RestorePresetUseCase.kt` (belum ada)
- `domain/pipeline/CustomTenantPipeline.kt`

### Effort: **Low** (monitor saja)

---

## 4. A8d — GET Locations Fail-Open

### Status: **SELESAI** (sudah diimplementasikan)

### Apa
Endpoint GET `/api/tenant/locations` sekarang fail-open — mengembalikan semua lokasi tanpa cek wewenang.

**Sudah benar:**
- READ (GET) → fail-open (tanpa jabatan tetap bisa lihat) - `TenantLocationRoutes.kt:42`
- WRITE (PUT/POST/DELETE) → fail-closed (harus punya wewenang) - `TenantLocationRoutes.kt:54-57`

### Core
- `routes/TenantLocationRoutes.kt`
- `routes/TenantRouteGatePolicy.kt`

### Effort: **Low** (sudah ada)

---

## 5. A8e — Editor Tahap RBAC

### Status: **SELESAI** (sudah diimplementasikan)

### Apa
Editor tahap (`TenantStageFlowRoutes`) visible oleh user yang tidak punya wewenang:

**Sudah benar:**
- Write operations: `mayEditWithoutDecision` check + `requireFactoryFlowAccess(AccessLevel.MANAGE)` - `TenantStageFlowRoutes.kt:69-81`
- Read operations: open for all tenant users (as intended for viewing stages)

### Core
- `routes/TenantStageFlowRoutes.kt`
- `routes/FactoryFlowAccessGuard.kt`

### Effort: **Low** (sudah ada)

---

## 6. Urutan Pengerjaan

```
A8d ──┐
A8e ──┼──► A8c ──► A8a ──► A8b
       │      (depends on A4 FK)
```

| Fase | Item | Dependensi |
|---|---|---|
| 1 | A8d, A8e | Independen |
| 2 | A8c | Setelah A4 |
| 3 | A8a | Setelah A8c |
| 4 | A8b | Setelah A8a |

---

## 7. Definition of Done per Item

### A8a (SELESAI)
- [x] Telemetry provider untuk CRM - `GetCrmNodeTelemetryUseCase` + `CrmTelemetryProvider`
- [x] Telemetry provider untuk TechPack - `GetTechPackNodeTelemetryUseCase` + `TechPackTelemetryProvider`
- [x] Telemetry provider untuk Inventory - `GetInventoryNodeTelemetryUseCase` + `InventoryTelemetryProvider` (metrik katalog, lihat §1.1; **bukan** stock level)
- [ ] Telemetry provider untuk QC - belum ada repository/modul terpisah
- [ ] Telemetry provider untuk Fulfillment - repository packing/shipment dikerjakan sesi Fulfillment
- [ ] Test: KPI menampilkan data dari semua modul

### A8b (SELESAI)
- [x] ID format terdokumentasi (`PresetNodeSeeds.kt:59-65`)
- [x] Tidak ada duplikat ID
- [x] Test: restore preset menghasilkan ID yang benar

### A8c (PREEMPTIF)
- [x] FK preserved saat restore (String ID, bukan FK database)
- [ ] Monitor: tidak ada bug yang dilaporkan

### A8d (SELESAI)
- [x] GET locations fail-open
- [x] PUT/POST/DELETE locations fail-closed
- [x] Test: 403 untuk yang tidak berwenang write

### A8e (SELESAI)
- [x] Editor tahap butuh akses pipeline
- [x] Test: 403 untuk yang tidak berwenang

---

## 8. Effort Summary

| Item | Effort | Risiko | Status |
|---|---|---|---|
| A8a | Medium | Rendah | **Sebagian selesai** (CRM + TechPack + Inventory done; Inventory pakai metrik katalog — lihat §1.1; QC/Fulfillment belum) |
| A8b | Low | Rendah | **Selesai** |
| A8c | Low | Rendah | **Preemptif** |
| A8d | Low | Rendah | **Selesai** |
| A8e | Low | Rendah | **Selesai** |

**Realisasi**: 4/5 item sudah selesai atau tidak perlu dilakukan.
**Sisa**: Inventory/QC/Fulfillment butuh implementasi modul terlebih dahulu.
