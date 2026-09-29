# 🎓 Modul Pembelajaran: Blueprint — Membalik Arah Pengetahuan Preset (Jalur B, B4a–B4c)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Dari "modul tahu preset" ke "Blueprint menyatakan modul"
> **Prasyarat**: [`teaching-b3-slots-from-pack.md`](teaching-b3-slots-from-pack.md) · TRD: [`TRD-PLAT-001-blueprint.md`](../trd/TRD-PLAT-001-blueprint.md)

---

## 💡 1. Masalah yang dipecahkan

Sebelum B4, setiap spec modul menyebut nama preset:

```kotlin
override val supportedPresets = setOf(FOB_FULL_PACKAGE, BRAND_D2C)
override fun costingBehaviorFor(preset) = when (preset) { CMT_MAKLOON -> SERVICE_FEE_ONLY; … }
```

Modul dari pack lain (e-learning) mustahil "tahu FOB", dan AI agent tidak punya satu objek pun yang bisa
ia tulis sebagai "alur pabrik ini". Sesudah B4:

```kotlin
Blueprint(code = "cmt_makloon", modules = listOf(
    m("inventory", false, STOCK to "CONSIGNED_CLIENT_MATERIAL", …),   // non-aktif tapi tetap berparameter
    m("costing_hpp", true, COSTING to "SERVICE_FEE_ONLY", …), …))
```

Spec modul hanya membaca parameternya sendiri: `inputsFor(parameters)`.

## 🧱 2. Bedah Keputusan

### Blok A — Dua langkah: dibangun dari perilaku lama, lalu dibekukan

- **B4a** membangun starter **dari** `supportedPresets` / `*For(preset)`, lalu membekukan hasilnya di tabel
  emas 27 baris.
- **B4b** mengganti isinya dengan **data literal** yang dibangkitkan dari tabel itu. Setelah itu API lama
  dihapus.

Tabel emas menjadi jembatan yang membuktikan data literal = perilaku lama.

### Blok B — Modul non-aktif tetap tercantum

Gudang di-bypass pada CMT, tapi stoknya tetap `CONSIGNED_CLIENT_MATERIAL`, karena skenario cacat kain dan
tenant bisa mengaktifkannya. Blueprint yang hanya berisi modul aktif akan kehilangan informasi itu.

### Blok C — Enum tetap di tepi, mesin sudah netral

Kolom tenant, codec, dan route masih memakai `GarmentBusinessPreset` sampai B4c; di batasnya dikonversi
lewat `GarmentBlueprints.of(preset)`. Mesin (builder, wiring, reconciler, katalog) sudah tidak tahu preset.

### Blok D — Hanya satu perilaku yang benar-benar hidup

`costingBehaviorFor`, `stockOwnershipFor`, dan `defectLiabilityFor` ternyata **tidak dipanggil di runtime**:
semuanya metadata deklaratif. Satu-satunya perilaku yang hidup: HPP membaca stok gudang hanya pada
`FULL_PACKAGE_COGS`. Parser parameternya ketat, jadi nilai tak dikenal menghasilkan error, bukan default.

## ⚠️ 3. Jebakan

1. **Test paritas tautologis.** Setelah builder memakai wiring, membandingkan builder dengan wiring selalu
   lulus. Pembanding paritas harus **seed tulis tangan** (`PresetNodeSeeds`), bukan hasil builder.
2. **Regex dan angka di nama enum.** `[A-Z_]` tidak cocok dengan `BRAND_D2C`; skrip pembangkit berhenti
   di assert. Assert jumlah hasil ekstraksi menyelamatkan dari data yang diam-diam kurang satu.

3. **Value class di string template (B4c).** `"${preset.code}"` dulu `fob_full_package`; setelah `code`
   menjadi `BlueprintCode`, hasilnya `BlueprintCode(value=fob_full_package)`. Kompilator **tidak** menangkap ini.
   Body `POST /pipeline/reset` sempat rusak begini, padahal semua test hijau. Penulisan DB (`it[kolom] = …`) justru
   tertangkap kompilator karena tipenya berbeda. Pelajarannya: setelah mengganti `String` dengan value class,
   sisir semua `${…code}` secara manual, lalu kunci format kabel dengan test yang **dibuktikan merah** saat bug
   dikembalikan (`ApiClientAuthHeaderTest.resetPipeline_shouldSendRawBlueprintCodeInBody`).
4. **Klaim tanpa grep.** Discovery B4 menyebut `exampleCompanyName` sebagai sumber bug header; grep saat B4c
   membuktikan field itu tidak dipakai sama sekali. Sumber sebenarnya ada di daftar demo `CompanySwitcherDropdown`.

## 🧪 4. Pembuktian

- `GarmentBlueprintParityTest`: tabel emas 27 baris (starter × modul × aktif × parameter), setiap modul
  katalog ada di setiap starter, dan tampilan starter sama tanpa nama perusahaan contoh.
- `CatalogPipelineBuilderTest.blueprintWithoutSeeds_shouldBuildFromBlueprintAlone`: Blueprint baru
  ("makloon + gudang titipan") menghasilkan node sintetis `makloon-titipan-gudang-*`. Gudang tersambung
  hanya ke MRP, karena HPP `SERVICE_FEE_ONLY` tidak membaca stok.
- core 950, app 158, server 231 hijau; JVM, Wasm, JS terkompilasi; visual `wemade-demo` & `bordir-uji` identik.

## 🧭 5. Berikutnya

- **B4c**: hapus `GarmentBusinessPreset`; `tenants.business_preset`, `baseStarterPreset`, codec, dan route
  memakai `BlueprintCode` (nilai tersimpan tetap).
- **B4d**: kode Blueprint tak dikenal ditolak (400 / gagal keras), bukan jatuh ke FOB.
