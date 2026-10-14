# MVP Tasks Summary - WeMade ERP

**Tanggal**: 2026-10-14

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
| A2 | Node aktif = entitlement + aktivasi tenant | ✅ | Aktivasi manual, badge/tombol dwibahasa, gerbang kuota, kanvas tak kosong untuk tenant baru; `PresetActivationStateTest`, `NodeActivationGateTest`, `FactoryFlowBypassVisibilityTest`, `PipelineModuleApiTest`; cek visual `wemade-demo` + starter CMT. Android ditunda (SDK) — [`PLAN`](PLAN-factory-flow-a2-entitlement-activation.md) |

### Belum ⏳
| # | Task | Priority | Plan |
|---|---|---|---|

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
| K1 | **Kriteria konvergensi §5** (butir 1) | ✅ | Paritas konveksi A=B: `AccessSnapshotB6Test` lulus 100% (690 snapshot identik); cek visual tenant rajut (`wemade-demo`) & bordir (`bordir-uji`) terverifikasi bersih di browser Wasm Compose |

### Belum ⏳ (sisa Jalur B)
| # | Task | Prioritas | Catatan |
|---|---|---|---|
| K2 | Migrasi data tenant A → B teruji di salinan DB produksi (entitlement, peran, SPK beku) | SEDANG | Syarat §5 butir 3; baru bisa diuji begitu ada salinan DB produksi |

---

## Task Baru: Data Flow + Prototype Simulation

| # | Task | Plan |
|---|---|---|
| BLD-03 | Builder Console v2 (Chat, Live, Handoff, Audit, Setting) — **T0 kontrak dibekukan** (`BuilderConsoleContract`, `BuilderAuditTrailCodec`, test kontrak hijau); T1–T4 belum | [`PLAN`](../features/PLAN-builder-console-v2-navigation.md) |
| BLD-02 | Ringkasan & follow-up per modul (BRIEF) — Fase 1: Track 0/A/B/C selesai, D (visual) belum | [`PLAN`](../features/PLAN-builder-module-briefs.md) |
| BLD-01 | Tab Alur Data per fitur + Prototype Simulation | [`PLAN`](../../features/PLAN-builder-data-flow-and-prototype-simulation.md) |
| K3 | Builder Wizard Self-Service (Template + Follow-up UI) | **TINGGI** | [`PLAN-K3-builder-wizard-self-service.md`](PLAN-K3-builder-wizard-self-service.md) |
| K3e | Halaman pemilihan metode (Template vs Scratch) | **TINGGI** | Screen pilih template garment atau from scratch |
| K3a | UI popup/render followUp dari API chat | **TINGGI** | Modal untuk tampilkan pertanyaan Clarification dari AI |
| K3b | Form input jawaban follow-up (per pertanyaan) | **TINGGI** | Input field sesuai tipe pertanyaan; submit ke chat |
| K3c | Integrasi follow-up answered ke chat flow | **TINGGI** | Kirim jawaban sebagai pesan chat; trigger next AI response |
| K3d | UI wizard shell (guided self-service) | ~~SEDANG~~ → DIBATALKAN | Chat flow yang ada sudah cukup fluid; stepperoptional nanti kalau dibutuhkan |

**TRD & Planning**: B0–B7 semuanya sudah tertutup dokumen —
discovery (`discovery-B0/B4/B7-*.md`), TRD (`TRD-PLAT-001_blueprint`, `TRD-PLAT-001-tenant-pack`,
TRD-PLAT-004/005/008/009/011/012), dan roadmap induk
[`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §4.

K3: Follow-up UI sudah ada backend (`AskModuleFollowUpsUseCase`, `Clarification`, `followUp` di API).Yang belum adalah UI layer — render popup dari response `followUp`, user isi di modal, lalu lanjut chat.

---

## Catatan

- **Inventory/QC/Fulfillment telemetry**: Inventory selesai 2026-10-12 (metrik katalog, bukan stok — lihat `PLAN-factory-flow-a8-debt.md` §1.1); QC/Fulfillment butuh modul/repository lebih dulu
- **A2**: satu-satunya MVP Jalur A yang belum dimulai
- **Jalur B**: implementasi enum-migration (B0–B7) selesai 2026-10-12; yang tersisa adalah
  kriteria konvergensi & generator AI (lihat bagian Jalur B)
