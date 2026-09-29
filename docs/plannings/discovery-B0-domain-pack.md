# Discovery Note — B0 Domain Pack

**Tanggal**: 2026-09-29 · **Penulis**: Achmad Jamaludin (dibantu Claude) · **Jalur**: B (platform general)
**Rencana induk**: [`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §4 B0

## 1. Kebutuhan

- **Siapa memakai**: developer platform (menambah vertikal), AI agent penyusun Blueprint (membaca kosakata
  vertikal), kanvas Factory Flow (fase & slot), superadmin (menetapkan vertikal tenant — mulai B7).
- **Data milik**: **platform**, bukan tenant. Pack dikirim bersama software karena setiap slot butuh modul
  yang benar-benar dibangun. Tenant memilih pack & template, tidak menyunting pack.
- **Berubah kapan**: saat rilis (developer menambah/mengubah pack). Sekali per tenant saat onboarding (pilih pack).

## 2. Fitur serupa

- Perintah: `scripts/find-similar-feature.sh pack vertical preset archetype industry` (hasil "pack" tercampur Tech Pack).
- Temuan:
  - `domain/stageflow/IndustryStageTemplates.kt` + `TenantStageFlow` — pola **template kode → salinan tenant**
    dengan kunci value object `StageCode` (TRD-FLOW-001). Pola paling dekat.
  - `domain/pipeline/OperationalModuleCatalog.kt` — katalog spec modul, sudah dijaga `ModuleRegistrationConsistencyTest`.
  - `domain/contracts/ModulePortPayload.kt` `PortDataTypeRegistry` — tipe port **sudah string**.
  - `domain/prospect/*` — penerjemah cerita → kebutuhan per archetype (konsumen pack di B7).
- Keputusan: **Mirip → tiru pola `IndustryStageTemplates` + `StageCode`** dan paritas Strangler Fig TRD-FLOW-001.

## 3. Jenis

**Bukan modul/fitur — fondasi platform di `core/domain/pack/`.** Tidak dijual, tidak di-RBAC, tidak di kanvas.
Ia adalah *kosakata* yang dibaca kanvas, katalog, dan (nanti) RBAC.

## 4. Uji Variabilitas

| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---|---|---|---|---|
| Fase kanvas (kolom) | tidak | **ya** | tidak | **data per pack** (`PhaseCode`) | ditetapkan pack; tidak disalin ke tenant |
| Slot modul (archetype) | tidak | **ya** | tidak | **data per pack** (`SlotCode`) | idem |
| Tipe port | tidak | **ya** | tidak | **data per pack** (`PortType`) | idem |
| Template industri (rajut/bordir) | pilih | ya | tahap disunting | data (sudah, TRD-FLOW-001) | disalin ke tenant, beku per SPK |
| Preset (FOB/CMT/D2C) | pilih | ya | ya | Blueprint per pack (B4) | disalin ke tenant saat onboarding |
| Modul (`BusinessModule`) | entitlement | **ya** | tidak | data per pack (B6) — **tidak di B0** | — |
| Jenis pack itu sendiri | — | — | — | **kode** (registry pack) | — |

Hierarki yang dihasilkan:
```
DomainPack (garment, elearning…)          ← platform, kode, dikirim per rilis
 └─ IndustryTemplate (KNIT_SWEATER…)      ← sudah ada; menjadi milik pack garment
     └─ TenantStageFlow                    ← salinan tenant (sudah ada)
         └─ frozenStageFlow per SPK        ← beku (sudah ada)
```

## 5. Core & extend

- **Core (baru)**: paket `core/.../domain/pack/`
  - `DomainPackCode`, `PhaseCode`, `SlotCode`, `PortType` — value object string (tolak kosong, tidak fallback).
  - `PhaseDefinition(code, order, displayName, subtitle, colorHex)`,
    `SlotDefinition(code, displayName, phase, defaultInput, defaultOutput)`,
    `DomainPack(code, displayName, phases, slots, portTypes)` dengan invariant: slot menunjuk fase yang ada,
    port default slot terdaftar di pack, kode unik, urutan fase unik.
  - `DomainPackRegistry` — `garment` sebagai satu-satunya pack.
  - `GarmentDomainPack` — **dibangun dari enum lama** (`PipelineStage.entries`, `ModuleArchetype.entries`,
    `PortDataTypeRegistry.KNOWN_TYPED_LABELS`), bukan disalin tangan, supaya identik secara konstruksi.
- **Titik extend**: menambah vertikal = satu objek pack baru + daftar di registry (B7).
- **Contoh yang ditiru**: `stageflow/IndustryStageTemplates.kt` (template), `stageflow/StageFlowValueObjects.kt:16`
  (`StageCode`), test paritas `stageflow/IndustryStageTemplatesTest.kt`.
- **Jangan disentuh di B0**: semua pembaca enum (kanvas, RBAC, persistence). B0 hanya **menambah** di samping.
  `PresetNodeSeeds.kt` (FILE-SIZE-EXEMPT), `Application.kt` (ratchet 698).

## 6. I/O & kanvas

- Tidak ada port/node. B0 tidak mengubah kanvas sama sekali (dibuktikan: semua test lama hijau tanpa diubah).
- Konsumen pertama: B1 (kolom kanvas dibaca dari `pack.phases`).

## 7. Governance

- Tidak ada route, tidak ada layar, tidak ada tulis → tidak ada matriks akses di B0.
- **Catatan untuk B6**: pack **tidak boleh** menjadi jalan pintas mendaftarkan modul tanpa gerbang
  (prasyarat B5).

## 8. Ukuran → TRD?

- Agregat baru: 1 (`DomainPack`), read-only, tanpa persistence. Migrasi: **tidak ada**.
- → **TRD tidak perlu untuk B0.** TRD (`TRD-PLAT-001`) ditulis di awal B6, karena B6 menyentuh keamanan & 20 migrasi.

## 9. Test (definisi selesai B0)

1. **Paritas enum** (mengiterasi enum, entri baru tanpa padanan = merah):
   - setiap `PipelineStage` ↔ tepat satu `PhaseDefinition` dengan order/nama/warna sama;
   - setiap `ModuleArchetype` ↔ tepat satu `SlotDefinition` dengan fase = `defaultStage`, port default sama;
   - `PortDataTypeRegistry.KNOWN_TYPED_LABELS` = `garment.portTypes`.
2. **Invariant pack** (pack buatan test, bukan garment): slot ke fase tak dikenal ditolak; port tak terdaftar ditolak; kode ganda ditolak.
3. **Tenant kedua / vertikal kedua**: fixture pack mini **e-learning** (3 fase, 3 slot) di test — membuktikan
   bentuknya tidak diam-diam mengasumsikan konveksi. Hanya fixture test; belum didaftarkan di registry.
4. Seluruh test lama hijau tanpa diubah (core 932, app 158, server 231).

## 10. Keputusan yang perlu disetujui

| # | Pertanyaan | Rekomendasi |
|---|---|---|
| Q1 | Pack didefinisikan di **kode** atau di **DB**? | **Kode.** Pack tanpa modul yang dibangun tidak berguna; DB menambah migrasi & celah tanpa manfaat. |
| Q2 | Kolom `tenants.domain_pack` sekarang atau nanti? | **Nanti (B7).** Semua tenant saat ini garment; menambahkannya sekarang = default senyap tanpa pembaca. |
| Q3 | `PhaseCode`/`SlotCode` memakai kode lama (`COMMERCIAL`, `order_ingestion`) atau nama baru? | **Kode lama persis**, karena archetype sudah tersimpan sebagai `order_ingestion` di JSON pipeline — nol migrasi. |
| Q4 | Sampling dipindah dari fase Komersial? | **Tidak di B0** (paritas harus identik). Diputuskan di pack garment setelah B1. |
