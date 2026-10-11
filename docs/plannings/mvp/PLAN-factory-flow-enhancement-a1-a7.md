# Rencana: Factory Flow Enhancement — A1 sampai A7

**Tanggal**: 2026-10-11 · **Penulis**: Achmad Jamaludin
**Rencana induk**: [`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §3 (Jalur A)

---

## 0. Konteks & Ringkasan

A1–A7 adalah peningkatan **Factory Flow** (kanvas visual alur produksi) di ERP Konveksi. Ini bukan migrasi ke platform general (Jalur B), melainkan perbaikan di produk utama.

| Item | Fokus | Impact |
|---|---|---|
| A1 | Header & ringkasan kanvas | UX |
| A2 | Node aktif = entitlement | RBAC/Feature toggle |
| A3 | EdgeFlowProvider (panah antar node) | Visibilitas alur |
| A4 | Kolom rujukan antar modul | Data integrity |
| A5 | Kolom kanvas dari data | Fleksibilitas |
| A6 | Blueprint + validator | AI-ready |
| A7 | AI Agent konveksi | Otisзация |

---

## 1. A1 — Header Kanvas dari Data Tenant

### Apa
- Header kanvas menampilkan **nama pabrik nyata** dari data tenant, bukan hardcoded "PT WeMade Garmen Ekspor · FOB"
- Ringkasan atas **tidak menjumlah angka estimasi** (bug sekarang: menjumlah)
- Badge health dari estimasi **diredupkan** (tidak terlalu mencolok)

### Core
- `domain/pipeline/TenantPipeline.kt` atau aggregate terkait
- `domain/tenant/Tenant.kt`

### Titik extend
- UI: `presentation/pipeline/*Canvas*.kt` — header component

### Teaching serupa
- Cek `teaching-catalog-driven-factory-flow.md` untuk pola kanvas

### Effort: **Low**

---

## 2. A2 — Node Aktif = Entitlement + Aktivasi Tenant

### Apa
- Node di kanvas aktif bila:
  1. **Entitlement**: tenant punya hak akses ke modul tersebut
  2. **Aktivasi**: modul diaktifkan oleh tenant (bukan preset)
- Preset (FOB/CMT/D2C) hanya **saran onboarding**, bukan强制

### Core
- `domain/pipeline/TenantPipelineProjector.kt` — proyeksi node aktif
- `domain/pipeline/TenantModuleEntitlement.kt` — cek entitlement

### Titik extend
- `OperationalModuleCatalog` — membaca entitlement
- UI kanvas — conditionally render node

### Teaching serupa
- `teaching-b5-route-gates.md` untuk pola entitlement check

### Effort: **Medium**
- Butuh perubahan di projector dan UI

---

## 3. A3 — EdgeFlowProvider (Panah Antar Modul)

### Apa
Kontrak `EdgeFlowProvider` untuk menelusuri aliran antar node:

**6 panah yang siap di-track:**
1. CRM → Sampling (Commercial Inquiry → SPK Sample)
2. Tech Pack → HPP (Engineering → Costing)
3. MRP → Operator (Planning → Production)
4. Operator → QC (Production → Quality)
5. QC → Fulfillment (QC → Packing)
6. Sampling → Tech Pack (via nomor SPK — temporary)

**Panah lain**: tampilkan "belum terlacak"

### Approach
- **Prototipe 1 panah dulu**, dicek visual, baru all 6
- Butuh **ruang antar kolom** untuk label panah

### Core
- `domain/contracts/EdgeFlow.kt` (new)
- `domain/pipeline/PipelineEdge.kt` atau serupa

### Titik extend
- UI: perlu space antar swimlane untuk label

### Effort: **Medium**
- Desain UI + data model

---

## 4. A4 — Kolom Rujukan Antar Modul

### Apa
3 kolom baru di DB untuk referensi silang:

| Kolom | Tabel | Untuk |
|---|---|---|
| `source_sampling_order_id` | `tech_packs` | SPK Tech Pack ← Sampling Order |
| `costing_sheet_id` | `bulk_work_orders` | Bulk SPK ← Costing Sheet |
| `sampling_order_id` | `costing_sheets` | Costing Sheet ← Sampling (CMT) |

### Migrasi DB
- 3 kolom aditif (tidak merusak data existing)

### Core
- Entity masing-masing: `TechPack`, `BulkWorkOrder`, `CostingSheet`

### Effort: **Low**
- Schema change + FK

---

## 5. A5 — Kolom Kanvas Jadi Data

### Apa
- Kolom/phase pada spec modul sekarang dari **data** (bukan hardcoded)
- Default dari `archetype` — bisa override per tenant
- **Sampling dipindahkan ke fase pengembangan produk** (bukan produksi)

### Core
- `domain/pipeline/OperationalModuleCatalog.kt` — spec dengan `phase`
- `domain/stageflow/TenantStageFlow.kt`

### Prasyarat
- Modul kustom buatan AI (A6/A7)

### Effort: **Medium**

---

## 6. A6 — Blueprint Konveksi

### Apa
`PipelineBlueprint` dengan:
- Modul katalog (dari `OperationalModuleCatalog`)
- Modul kustom + port wajib
- Template industri (rajut, bordir, dll.)
- Parameter per modul
- **Validator port** — cek kompatibilitas input/output
- **Pratinjau kanvas** — lihat hasil sebelum simpan

### Komponen
```
PipelineBlueprint(
    id: BlueprintId,
    tenantId: TenantId,
    modules: List<ModuleSpec>,      // katalog + custom
    connections: List<Connection>,   // port wiring
    industryTemplate: IndustryCode,
    parameters: Map<ModuleId, JsonObject>,
    createdAt: Instant,
    status: DRAFT | PUBLISHED
)
```

### Titik extend
- `ProposedFlowValidator` — perlu diupdate (KDoc basi)
- UI: blueprint editor + preview

### Effort: **High**
- Data model + validator + UI

---

## 7. A7 — AI Agent Konveksi

### Apa
- LLM mengisi `FlowTranslationDraft` / **Blueprint**
- Mengganti `KeywordFlowTranslator` (rule-based)
- Output: Blueprint + **daftar slot kosong** (yang perlu diisi tenant)
- Tenant harus **menyetujuin** sebelum disimpan

### Keamanan
- Output LLM = **input tak tepercaya**
- Validasi ketat sebelum save
- Audit trail

### Core
- `infrastructure/ai/DiscoveryAgent.kt` atau baru
- `domain/pipeline/BlueprintValidator.kt`

### Effort: **High**
- Integrasi LLM + validasi + UI approval

---

## 8. Urutan Pengerjaan

```
A1 ──┐
A2 ──┼──► A5 ──► A6 ──► A7
A3 ──┤         ▲
A4 ──┘         │
       (A3 & A4 bisa paralel)
```

| Fase | Item | Dependensi |
|---|---|---|
| 1 | A1, A2 | Independen |
| 2 | A4 | Independen |
| 3 | A3 | Setelah A1 (butuh space) |
| 4 | A5 | Setelah A2 (butuh entitlement) |
| 5 | A6 | Setelah A5 (phase dari data) |
| 6 | A7 | Setelah A6 (Blueprint ada) |

---

## 9. Gudang (Luar Scope)

> Gudang (permintaan & alokasi bahan, panah #4–6) = **fitur baru**, butuh discovery sendiri.

---

## 10. Definition of Done per Item

### A1
- [ ] Header 显示真实工厂名
- [ ] Ringkasan 不计算估算
- [ ] Health badge diredupkan
- [ ] Visual check

### A2
- [ ] Node aktif dari entitlement check
- [ ] Preset hanya saran
- [ ] Test 403 untuk modul tidak berhak

### A3
- [ ] EdgeFlowProvider kontrak ada
- [ ] 1 panah prototype works
- [ ] Visual check
- [ ] 6 panah lengkap

### A4
- [ ] 3 kolom added ke DB
- [ ] FK constraint
- [ ] Read/write works

### A5
- [ ] Phase dari data
- [ ] Sampling di fase pengembangan produk
- [ ] Test deploy di tenant baru

### A6
- [ ] PipelineBlueprint data model
- [ ] Validator port kompatibilitas
- [ ] Preview kanvas
- [ ] ProposedFlowValidator diupdate

### A7
- [ ] LLM integration
- [ ] Blueprint generation
- [ ] Tenant approval workflow
- [ ] Audit trail
- [ ] Test: output divalidasi sebelum save

---

## 11. Effort Summary

| Item | Effort | Risiko |
|---|---|---|
| A1 | Low | Rendah |
| A2 | Medium | Rendah |
| A3 | Medium | Medium (UI space) |
| A4 | Low | Rendah |
| A5 | Medium | Medium |
| A6 | High | Tinggi |
| A7 | High | Tinggi (LLM) |

**Total estimate**: 6–8 sprint
