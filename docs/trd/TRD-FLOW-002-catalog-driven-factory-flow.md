# TRD-FLOW-002: Kanvas Factory Flow dari Katalog Modul

## 1. Document Context and Administration

- **Title & Unique ID**: Kanvas Factory Flow dari Katalog Modul — `TRD-FLOW-002`
- **Revision History**

| Versi | Tanggal | Penulis | Catatan |
|---|---|---|---|
| 1.0 | 2026-09-29 | Achmad Jamaludin (dibantu Claude) | Ditulis pasca-implementasi Fase 1–5 (rencana `dengan-perubahan-sebanyak-ini`). Mencatat keputusan port, builder, level 2, dan telemetri. |

### Summary & Business Context

Factory Flow adalah fitur yang dijual ke klien: kanvas yang memperlihatkan alur pabrik mereka.
Sebelum TRD ini kanvas **ditulis tangan** (`PipelinePresetFactory`, 1224 baris, 3 preset): modul
baru tidak muncul, port katalog tidak menyambung, fitur dalam modul tidak terlihat, dan angka WIP
adalah dummy yang tampak nyata.

**Janji produk setelah TRD ini** — satu deklarasi di satu tempat, kanvas ikut berubah; lupa
mendeklarasikan = test gagal:

| Yang ditambah | Deklarasi | Muncul di kanvas | Penjaga |
|---|---|---|---|
| Modul operasional | spec di `OperationalModuleCatalog.all` | node level 1 + edge dari port, tenant lama lewat reconciler (badge "Baru — belum aktif") | `ModuleRegistrationConsistencyTest` |
| Fitur dalam modul | entri `ModuleFeatureRegistry` | level 2 di panel node induk | `RouteOwnershipTest` (route tanpa pemilik gagal) |
| Tahap/proses tenant | data tenant (editor) | level 2 | — |
| Angka WIP/cycle/health | `ModuleTelemetryProvider` | angka nyata; tanpa provider → "±N Pcs estimasi" | `PipelineTelemetryOverlayTest` |
| Governance/foundation | — | tidak pernah (disengaja) | test konsistensi |

Batas: fitur murni tampilan tanpa route tidak terdeteksi test — penjaganya gerbang 3 skill
`wemade-feature-workflow`.

## 2. Functional Requirements

- **FR-1 Port menyambung**: edge A→B bila `outputsFor(A) ∩ (inputsFor(B) ∪ referenceInputs(B))`
  tidak kosong; modul bypass meneruskan masukannya satu lompatan (`CatalogPortWiring`).
- **FR-2 Builder**: `CatalogPipelineBuilder.build(preset, scenario)` — urutan katalog = urutan kanvas;
  seed preset (`PresetNodeSeeds`, FILE-SIZE-EXEMPT) hanya memberi teks & telemetri contoh.
- **FR-3 Modul baru tampak**: reconciler memasang node + edge port; node `node-*` yang bypass
  tetap tampil walau `hideBypassedNodes`.
- **FR-4 Level 2**: panel node menampilkan fitur induknya — kerangka tahap tenant (warna data),
  stasiun lini, alat — beserta **jumlah SPK per tahap kerja**.
- **FR-5 Telemetri**: `GET /api/tenant/pipeline/telemetry` → `{modules:[{module, wipPieces,
  cycleTimeHours, healthStatus, stageWip}]}` (`ModuleTelemetryCodec`, satu format server/klien).
- **FR-6 Governance**: 5 route tulis pipeline fail-closed `FACTORY_FLOW MANAGE`; entitlement
  ditegakkan server (`grantedModules`); TRACEABILITY & SURAT_JALAN memeriksa `accessDecisions`.

## 3. Keputusan

| # | Keputusan | Alasan |
|---|---|---|
| D1 | QC menerima `AssembledGarmentBundle`; Tech Pack masuk QC sebagai **referensi** | tidak ada modul FINISHING; tech pack dibaca, bukan dialiri barang |
| D2 | Tech Pack mengeluarkan juga `MaterialRequisition`; Costing hanya membaca stok di `FULL_PACKAGE_COGS` | menyambung Gudang tanpa edge palsu di CMT |
| D3 | Telemetri nyata hanya untuk alur tersimpan pada skenario Normal | pratinjau preset & simulasi cacat adalah angka contoh — semua node bertag estimasi |
| D4 | Provider paralel, timeout 2 dtk; gagal/lambat = modul dihilangkan dari respons | satu modul lambat tidak menahan kanvas; klien jatuh ke "estimasi", bukan error |
| D5 | Telemetri mengikuti kebijakan **baca** kanvas (VIEW, fail-open bila keputusan tak terhitung) | konsisten dengan `GET /api/tenant/pipeline`; menulis tetap fail-closed |
| D6 | WIP Sampling = pcs SPK di tahap `WORK`; tahap masuk/keluar tidak dihitung | barangnya belum/tidak lagi di lantai |
| D7 | Health Sampling: ≥1 SPK macet ≥3 hari → BOTTLENECK, ≥3 → CRITICAL | memakai `RD_STALL_WARNING_DAYS` yang sudah ada |

## 4. Verifikasi (2026-09-29)

- Test segar: core 932, app:shared 158, server 231 — semua hijau.
- Kompilasi JVM, WasmJs, Js hijau. **Android tidak diverifikasi**: Android SDK tidak tersedia di mesin ini.
- API: `bordir-uji` → Sampling `wipPieces=4`, `stageWip={HOOPING:1, THREAD_TRIMMING:1}`; DB: 3 SPK
  (Hooping, Thread Trimming, Flow Review = tahap masuk, sengaja tak dihitung).
- Visual `bordir-uji` 1440px: node Sampling "4 Pcs WIP · 5.8h" nyata; node tanpa provider
  "±N Pcs estimasi"; level 2 "2. Hooping · 1 SPK", "4. Trimming · 1 SPK".

## 5. Sisa / Utang

- Provider telemetri belum ada untuk CRM, Tech Pack, Inventory, Operator, QC, Fulfillment (tampil estimasi).
- Header kanvas `bordir-uji` masih "PT WeMade Garmen Ekspor · FOB" — nama/preset header tidak dari tenant (sudah ada sebelum TRD ini).
- Resolusi node plugin kustom (`representativeModule` vs `module.code`), skema id node `fob-*` vs `node-*`,
  bug FK restore-presets, GET locations masih fail-open, editor tahap terlihat oleh yang tak berwenang.
- Uji test route telemetri di server butuh DB (repository Postgres); dibuktikan lewat curl, bukan test otomatis.
