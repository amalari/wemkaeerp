# MVP Tasks Summary - WeMade ERP

**Tanggal**: 2026-10-12

## Jalur A - ERP Konveksi

### Selesai ✅
| # | Task | Status | Commit |
|---|---|---|---|
| A1 | Header kanvas dari data tenant | ✅ | 3987c73b |
| A3 | EdgeFlowProvider + 6 panah ready | ✅ | 3987c73b |
| A4 | Kolom rujukan (source_sampling_order_id, dll) | ✅ | 3987c73b |
| A5 | Kolom kanvas phase data + Sampling di ENGINEERING | ✅ | 3987c73b |
| A6 | PipelineBlueprint + validator | ✅ | 3987c73b |
| A7 | AiFlowTranslator (LLM-based) | ✅ | 3987c73b |
| A8a | CRM + TechPack telemetry | ✅ | cd39608d |
| A8b | Skema ID node pipeline | ✅ | dokumentasi |
| A8c | FK restore-presets | ✅ | preemptif |
| A8d | GET locations fail-open | ✅ | sudah ada |
| A8e | Editor tahap RBAC | ✅ | sudah ada |

### Belum ⏳
| # | Task | Priority | Plan |
|---|---|---|---|
| **A2** | Node aktif = entitlement + aktivasi tenant | **TINGGI** | [`PLAN-factory-flow-a2-entitlement-activation.md`](PLAN-factory-flow-a2-entitlement-activation.md) (TRD format) |

---

## Jalur B - Platform General

> **Diverifikasi ulang ke kode & TRD pada 2026-10-12.** Status sebelumnya ("Discovery Done,
> Implementation Pending" untuk B0/B4/B7) ternyata **basi** — implementasi B0–B7 sudah selesai.

### Selesai ✅
| # | Task | Status | Bukti |
|---|---|---|---|
| B0 | Domain Pack concept | ✅ | `core/.../domain/pack/` (`DomainPack`, `PhaseCode`, `SlotCode`, `PortType`); `GarmentDomainPackParityTest` |
| B1 | `PipelineStage` → `PhaseCode` dari pack | ✅ | Enum lama sudah tidak ada di `core/`/`server`/`app` |
| B2 | Tipe port → `PortType` dari pack | ✅ | `PortDataTypeRegistry` dihapus; dijaga `CatalogPortVocabularyTest` |
| B3 | `ModuleArchetype` → `SlotCode` dari pack | ✅ | Enum lama sudah tidak ada; slot di `GarmentSlots.kt` |
| B4 | Blueprint (menggantikan `GarmentBusinessPreset`) | ✅ | [`TRD-PLAT-001-blueprint`](../../trd/mvp/TRD-PLAT-001-blueprint.md) v0.3 "**B4 selesai**" |
| B5 | Test gerbang keamanan modul | ✅ | `RouteOwnershipTest` (route tanpa pemilik gagal) + gerbang fail-closed [`TRD-PLAT-011`](../../trd/mvp/TRD-PLAT-011-orgchart-gates-fail-closed.md)/[`TRD-PLAT-012`](../../trd/mvp/TRD-PLAT-012-factory-flow-gates-fail-closed.md) + `AccessSnapshotB6Test` opt-in |
| B6 | `BusinessModule` → `ModuleId` dari pack | ✅ | B6a–B6g: `BusinessModule` kini `typealias` = `ModuleId` (`BusinessModule.kt`); parser tunggal `ModuleIdCodec`; menu dari pack + `/m/{code}`; snapshot 690 keputusan akses |
| B7 | Tenant Pack Dinamis | ✅ | [`TRD-PLAT-001-tenant-pack`](../../trd/mvp/TRD-PLAT-001-tenant-pack.md) v0.2 "**B7a–f selesai**"; bukti tenant `klinik-uji` di server nyata; `tenant/layanan/LayananPilotPack.kt` |

### Belum ⏳ (sisa Jalur B)
| # | Task | Prioritas | Catatan |
|---|---|---|---|
| K1 | **Kriteria konvergensi §5** PLAN-dual-track: paritas penuh A=B + cek visual rajut & bordir | TINGGI | Syarat A pindah ke B; jalankan `AccessSnapshotB6Test` + cek visual 2 tenant |
| K2 | Migrasi data tenant A → B teruji di salinan DB produksi (entitlement, peran, SPK beku) | SEDANG | Baru bisa diuji begitu ada salinan DB produksi |
| K3 | Generator AI pack + wizard discovery self-service (non-goal B7 yang sengaja ditunda) | RENDAH | Kontrak sudah disiapkan B7; tunggu kebutuhan klien |

**TRD & Planning**: B0–B7 semuanya sudah tertutup dokumen —
discovery (`discovery-B0/B4/B7-*.md`), TRD (`TRD-PLAT-001-blueprint`, `TRD-PLAT-001-tenant-pack`,
TRD-PLAT-004/005/008/009/011/012), dan roadmap induk
[`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §4.
Tidak perlu dokumen baru sampai K1–K3 dimulai.

---

## Catatan

- **Inventory/QC/Fulfillment telemetry**: butuh modul/repository lebih dulu
- **A2**: satu-satunya MVP Jalur A yang belum dimulai
- **Jalur B**: implementasi enum-migration (B0–B7) selesai 2026-10-12; yang tersisa adalah
  kriteria konvergensi & generator AI (lihat bagian Jalur B)
