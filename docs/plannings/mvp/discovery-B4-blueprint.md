# Discovery Note — B4 Blueprint (menggantikan `GarmentBusinessPreset`)

**Tanggal**: 2026-09-29 · **Penulis**: Achmad Jamaludin (dibantu Claude) · **Jalur**: B
**Rencana induk**: [`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §4 B4

## 1. Kebutuhan

- **Siapa memakai**:
  - onboarding tenant (memilih starter);
  - AI agent (menulis Blueprint dari cerita bisnis — plan A6/A7);
  - kanvas & reconciler (modul aktif, sambungan);
  - modul (perilaku per model bisnis: HPP, kepemilikan stok, tanggung jawab cacat).
- **Data milik**:
  - Blueprint starter (FOB/CMT/D2C) = **platform/pack**, dikirim per rilis.
  - Salinan tenant = **tenant**, sudah ada sebagai `CustomTenantPipeline`.
- **Berubah kapan**: starter per rilis; salinan tenant disalin saat onboarding lalu disunting tenant (Kontrak 5).

## 2. Kondisi sekarang (hasil scan)

| Aspek | Tempat | Catatan |
|---|---|---|
| Identitas | `enum GarmentBusinessPreset` (3 entri) | 64 file, 119 referensi |
| Tersimpan | `tenants.business_preset`, `tenant_pipelines.base_preset`, JSON `baseStarterPreset` | nilai = **code** (`fob_full_package`) — tidak perlu migrasi |
| Modul aktif | `spec.supportedPresets` (di setiap modul) | **arah terbalik**: modul tahu preset |
| Perilaku | `spec.costingBehaviorFor(preset)`, `stockOwnershipFor`, `defectLiabilityFor`, `inputsFor` | ditulis per modul, bercabang per preset |
| Seed tampilan | `PresetNodeSeeds` (`when (preset)`) | FILE-SIZE-EXEMPT |
| Parser | `GarmentBusinessPreset.fromCode` | **fallback senyap ke FOB** (Kontrak 4) |
| Tampilan | `exampleCompanyName` | **sumber bug header** "PT WeMade Garmen Ekspor" di tenant bordir |

> **Koreksi (B4c, 2026-09-29):** `exampleCompanyName` ternyata **tidak dipakai di mana pun**. Badge header "PT WeMade Garmen Ekspor" berasal dari daftar perusahaan demo yang ditulis tangan di `CompanySwitcherDropdown` (`CompanyTenantProfile.ALL`), dan teks "FOB Full Package" jujur secara data: `bordir-uji` memang tersimpan `business_preset = fob_full_package`. Perbaikan tetap di Jalur A (A1).

## 3. Bentuk yang diusulkan

```kotlin
// mesin (netral) — core/domain/blueprint/
value class BlueprintCode(val value: String)            // "fob_full_package" — kode lama persis

data class BlueprintModule(
    val moduleCode: String,                             // "costing_hpp"
    val parameters: Map<String, String> = emptyMap()    // "costingBehavior" to "SERVICE_FEE_ONLY"
)

data class Blueprint(
    val code: BlueprintCode,
    val pack: DomainPackCode,
    val displayName: String, val shortBadge: String,
    val description: String, val targetClientProfile: String,
    val modules: List<BlueprintModule>                  // modul AKTIF; lainnya = bypass
)
```

- **Pack garment** menyediakan 3 Blueprint starter (FOB/CMT/D2C), **dibangun dari perilaku lama**
  (`supportedPresets`, `*For(preset)`) supaya identik, lalu dijaga tabel emas — pola yang sama dengan B1–B3.
- **Modul** membaca parameternya sendiri dari node (`costingBehavior`, `stockOwnership`, `defectLiability`,
  `includesStockInput`). Spec tidak lagi menyebut nama preset.
- Blueprint = **format yang sama** yang nanti ditulis AI agent (plan A6/A7) dan divalidasi (modul ada di katalog,
  port menyambung, parameter dikenal modul).

## 4. Uji Variabilitas

| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---|---|---|---|---|
| Daftar starter | tidak | **ya** | tidak | data per pack | dikirim per rilis |
| Modul aktif & parameter | **ya** | ya | **ya** | data | Blueprint → disalin ke `CustomTenantPipeline` saat onboarding |
| Arti parameter (`SERVICE_FEE_ONLY`) | tidak | ya | tidak | kode modul garment | — |

## 5. Keputusan yang perlu disetujui

| # | Pertanyaan | Rekomendasi |
|---|---|---|
| Q1 | Membalik arah: Blueprint menyatakan modul + parameter, spec modul tidak lagi tahu preset? | **Ya.** Tanpa ini modul pack lain harus "tahu FOB". Ini juga format keluaran AI agent. |
| Q2 | Parameter perilaku sebagai `Map<String,String>` per modul, atau tipe per modul? | **Map string** di mesin, **ditafsirkan modul garment** dengan parser ketat. Sama dengan `customFormulaParameters` yang sudah ada di node. |
| Q3 | Fallback senyap `fromCode` → FOB | **Dipertahankan di commit refactor (paritas)**, lalu **diperbaiki di commit terpisah** B4: kode tak dikenal ditolak dengan test. DB B sudah terpisah, jadi aman. |
| Q4 | Bug header (`exampleCompanyName`) | **Perbaiki di Jalur A dulu (A1)**, lalu cherry-pick ke B. Blueprint B tidak membawa nama perusahaan contoh. |
| Q5 | Kode tersimpan | **Kode lama persis** (`fob_full_package`, `cmt_makloon`, `brand_d2c`) — nol migrasi. |

## 6. Ukuran → TRD?

- Agregat baru: 1 (`Blueprint`) + perubahan kontrak spec modul (9 spec).
- Migrasi: tidak ada.
- Menyentuh kontrak yang dipakai AI agent di A6/A7 → **tulis `TRD-PLAT-001` bagian Blueprint** sebelum kode,
  supaya format Blueprint disepakati sekali untuk kedua jalur.

## 7. Urutan kerja (setelah disetujui)

1. **B4a** — tipe `Blueprint` + 3 starter garment dibangun dari perilaku lama; tabel emas (modul aktif & parameter per preset).
2. **B4b** — pembaca pindah:
   - builder, projector, reconciler, dan wiring membaca `blueprint.modules`;
   - spec membaca parameter node;
   - `supportedPresets` dan `*For(preset)` dihapus.
3. **B4c** — `GarmentBusinessPreset` dihapus; codec/repo memakai `BlueprintCode`.
4. **B4d** — fallback senyap diperbaiki (perubahan perilaku, test sendiri).
