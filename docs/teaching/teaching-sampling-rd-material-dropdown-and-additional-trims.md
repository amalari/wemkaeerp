# 🎓 Modul Pembelajaran: Master Bahan Baku & Aksesoris Non-Perbagian di Hasil R&D

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Dynamic Worksheets (JSONB), Compose Multiplatform Searchable Dropdown, Neo-Brutalism & Claymorphism  
> **Prasyarat**: Dasar Compose State (`remember`, `mutableStateOf`), JSONB persistence, dan pemisahan arsitektur domain/presentation  
> **Referensi Task**: Penambahan Kolom Master Bahan Baku (Searchable Dropdown) & Aksesoris Tambahan Non-Perbagian (Zipper, Kancing, dll.) di Hasil R&D

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada proses R&D garmen dan rajut konveksi:
1. **Bahan Baku Per Bagian (Panel)**: Setiap panel garmen (mis. Badan Depan, Badan Belakang, Lengan, Kerah) menggunakan spesifikasi benang atau kain tertentu. Jika operator R&D hanya mencatat angka gramasi (mis. 117 GR) dan waktu rajut (37 MENIT) tanpa menghubungkan ke Master Data Bahan Baku (`MaterialItem`), bagian gudang dan purchasing tidak mengetahui benang mana yang benar-benar dikonsumsi, berisiko salah potong stok atau salah beli benang saat masuk produksi massal.
2. **Bahan Baku Non-Perbagian (Aksesoris / Trims)**: Garmen tidak hanya terdiri dari rajutan panel. Ada bahan pelengkap yang bersifat global pada produk, seperti **Zipper/Ritsleting**, **Kancing**, **Label Brand/Washing**, **Tali Hoodie**, dan **Stopper**. Bahan-bahan ini tidak bisa dipaksakan masuk ke dalam tab bagian panel rajut karena bukan bagian panel yang dirajut mesin.

### Solusi Elegan Kita
1. **Kolom Master Bahan Baku di Tab Bagian Panel**: Menambahkan kolom searchable dropdown yang terintegrasi langsung dengan Master Data Bahan Baku (`MaterialItem`), memprioritaskan kategori Benang (`YARN`) dan Kain (`FABRIC`), dengan fallback input teks bebas jika bahan belum terdaftar di master data.
2. **Section Tambahan Bahan Baku (Non-Perbagian)**: Membuat section tabel dinamis baru khusus aksesoris dan trims (Zipper, Kancing, Label, dll.) lengkap dengan autocomplete Master Data, jumlah/kebutuhan, catatan spesifikasi, serta tombol tambah/hapus baris.
3. **Data Round-trip Aman Tanpa Migrasi DB**: Menyimpan seluruh data ke dalam kolom PostgreSQL `stage_inputs` bertipe `jsonb` menggunakan `StageSectionNames.PANEL_MATERIALS` dan `StageSectionNames.ADDITIONAL_MATERIALS`.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kita harus menulis fitur ini dari nol, berikut adalah urutan penulisan yang disiplin:

```
Langkah 1 (Domain Core)         → Tambah konstanta section di StageSectionNames
Langkah 2 (Domain Mapping)      → Tambah material ke CamPartTab & AdditionalMaterialItem ke CamProgramSheet
Langkah 3 (Unit Test Mapping)   → Tulis unit test roundtrip serialize & parse
Langkah 4 (Design System / UI)  → Buat MaterialSearchableDropdown & AdditionalMaterialsSection
Langkah 5 (Orchestrator Screen) → Pasang ke RdResultSection & hubungkan ke SamplingSpkDetailDialog
Langkah 6 (State Management)    → Muat availableMaterials di SamplingViewModel & SamplingUiState
```

Mengapa mulai dari **Domain & Mapping**?
Karena domain adalah *Single Source of Truth*. Jika kita mulai dari UI, kita akan tergoda membuat komponen visual tanpa tahu bagaimana bentuk data yang akan di-serialize ke backend. Dengan menyiapkan mapping murni terlebih dahulu, kita bisa memvalidasi kebenaran data lewat unit test sebelum satu baris UI pun digambar.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Domain Constants & Section Names (`StageWorkInput.kt`)
```kotlin
object StageSectionNames {
    const val PROGRAM = "PROGRAM"
    const val FEEDER_INSTRUCTIONS = "INSTRUKSI PANAH"
    const val PATTERN_FORMULAS = "RUMUS POLA"
    const val PANEL_WEIGHTS = "GRAMASI"
    const val PANEL_MINUTES = "WAKTU"
    const val PANEL_MATERIALS = "BAHAN BAKU PER BAGIAN"
    const val ADDITIONAL_MATERIALS = "BAHAN BAKU TAMBAHAN"
    const val SIZE_CHART = "DETAIL SIZE CHART"
    const val TENSELITY = "TENSELITY"
    const val FINISHED_MEASUREMENTS = "HASIL UKURAN JADI"
}
```
**Mengapa blok ini ditulis begini?**
- Section name bertindak sebagai *kontrak data* yang disimpan ke dalam dokumen `jsonb` di database.
- Menambahkan konstanta baku mencegah *typo* yang dapat merusak desentralisasi data antara tahap CAM dan R&D.

---

### Blok B: Model Data & Serialisasi Murni (`CamProgramSheetMapping.kt`)
```kotlin
data class CamPartTab(
    val id: String,
    val name: String,
    val program: String = "",
    val feederInstructions: List<String> = emptyList(),
    val tenselities: List<String> = emptyList(),
    val gramasi: String = "",
    val waktu: String = "",
    val material: String = "" // <-- Master Bahan Baku per panel
)

data class AdditionalMaterialItem(
    val id: String = "",
    val materialName: String = "",
    val quantity: String = "",
    val notes: String = ""
) {
    val isFilled: Boolean get() = materialName.isNotBlank() || quantity.isNotBlank()
}
```
**Mengapa blok ini ditulis begini?**
- `AdditionalMaterialItem` mengisolasi konsep bahan non-perbagian dengan 3 atribut inti: nama bahan, kuantitas, dan catatan teknis.
- Saat di-serialize ke `StageInputRow(label, value)`:
  - `label`: diisi `materialName` (mis. `TRM-001 — Zipper Metal 50cm`)
  - `value`: diisi format terstruktur `${quantity} | ${notes}` (mis. `1 PCS | Gigi besi no 5 warna hitam`)
- Parsing mendukung pemisah ` | ` maupun ` • `, sehingga data lama atau input sederhana tetap terbaca mulus.

---

### Blok C: Searchable Dropdown Berbasis Claymorphism (`MaterialSearchableDropdown.kt`)
```kotlin
@Composable
fun MaterialSearchableDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    availableMaterials: List<MaterialItem>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Cari / pilih bahan baku...",
    categoryPriority: List<MaterialCategory>? = null,
    enabled: Boolean = true
)
```
**Mengapa blok ini ditulis begini?**
- **Prioritas Kategori**: Di panel rajut, kita memprioritaskan kategori `YARN` (benang) dan `FABRIC` (kain). Sementara di aksesoris, kita memprioritaskan `TRIM` dan `ACCESSORY`. Opsi `categoryPriority` memastikan operator melihat pilihan paling relevan di urutan teratas tanpa harus mengetik panjang.
- **Neo-Brutalism & Claymorphism Compliance**: Menggunakan `claySurface`, outline tebal `ClayBorder`, token `WeMadeColors`, dan **nol Unicode emojis** (semua icon memakai Canvas vectors dari `ClayIcons.kt`) untuk menghindari tampilan *tofu* (`▯`) pada target Compose Web/Wasm.

---

### Blok D: Section Tambahan Bahan Baku (`AdditionalMaterialsSection.kt`)
```kotlin
@Composable
fun AdditionalMaterialsSection(
    items: List<AdditionalMaterialItem>,
    availableMaterials: List<MaterialItem>,
    onItemsChange: (List<AdditionalMaterialItem>) -> Unit,
    modifier: Modifier = Modifier
)
```
**Mengapa blok ini ditulis begini?**
- Memisahkan section ini ke file independen mematuhi **Rule §14 (Batas Ukuran File)**. `RdResultSection.kt` tetap ramping (337 baris, < 400 soft limit).
- Pengguna dapat menambah baris baru (`+ Tambah Bahan`) dan menghapus baris kapan saja dengan feedback visual reaktif.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan / Teknologi | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **JSONB Dynamic Worksheet** | Tabel Relasional Baru (`spk_materials`) | Fleksibilitas data dinamis per SPK tanpa perlu migrasi DDL baru untuk setiap variasi input garmen | Beban migrasi Flyway tinggi, skema kaku saat tim R&D butuh atribut tambahan |
| **MaterialSearchableDropdown dengan Free-Text Fallback** | Dropdown Enum Statis Kaku | Memudahkan pemilihan dari Master Data tapi tetap memberi kebebasan jika ada bahan sampel baru yang belum sempat didaftarkan di master data | Operator terblokir tidak bisa menyimpan R&D jika master data terlambat diinput admin |
| **Dekomposisi File Terpisah (`AdditionalMaterialsSection.kt`)** | Menulis semua Composable di dalam `RdResultSection.kt` | Mematuhi Rule §14 (file < 400 baris) dan SRP (Single Responsibility Principle) | Menghasilkan *God File* 700+ baris yang sulit direview dan mudah konflik git |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Unicode Glyph / Emojis di UI String**
   - *Kenapa bahaya*: Menulis `Text("🔍 Cari...")` atau `Text("🗑️ Hapus")` akan merender kotak kosong (`▯`) di browser karena Compose Wasm/Skiko tidak memuat emoji OS font secara otomatis.
   - *Solusi elegan*: Selalu gunakan Canvas vector icons dari `ClayIcons.kt` (`IconSearch`, `IconTrash`, `IconChevronDown`).

2. **Jebakan 2: Kehilangan Data Saat Rework / Autosave CAM**
   - *Kenapa bahaya*: Ketika lembar Program CAM di-serialize ulang, jika fungsi `serializeCamSections` lupa menyertakan `additionalMaterials` dan `material`, data yang sudah diisi R&D akan terhapus.
   - *Solusi elegan*: `serializeCamSections` selalu menerima default parameter dan mempertahankan `additionalMaterials` yang sudah ada di dalam `CamProgramSheet`.

3. **Jebakan 3: Melanggar Aturan Ratchet Ukuran File**
   - *Kenapa bahaya*: Menambah fitur ke `SamplingViewModel.kt` yang sudah panjang (> 400 baris) berisiko ditolak merge.
   - *Solusi elegan*: Ekstraksi helper method `updateOrder(...)` untuk menghilangkan duplikasi baris, sehingga file berkurang dari 482 baris menjadi 459 baris (-23 baris).

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian dilakukan melalui dua level verifikasi:
1. **Unit Test Mapping Murni (`CamProgramTabMappingTest.kt`)**:
   - Memastikan 9 section tersimpan lengkap.
   - Menguji bahwa `material` per bagian dan `additionalMaterials` non-perbagian dapat disimpan dan diparsing kembali (*roundtrip*) tanpa kehilangan data.
   - Menjalankan perintah:
     ```bash
     ./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.sampling.CamProgramTabMappingTest"
     ```
2. **Kompilasi Multi-Target**:
   - Memastikan kompilasi bersih di target JVM dan Web (WasmJS):
     ```bash
     ./gradlew :app:shared:compileKotlinJvm
     ./gradlew :app:shared:compileKotlinWasmJs
     ```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Tambahkan ringkasan total aksesoris yang terisi di header kartu `AdditionalMaterialsSection` (misalnya badge berubah hijau saat semua baris memiliki nama dan jumlah).
- [ ] **Tantangan 2**: Buat fitur autocomplete untuk satuan (UOM) pada field Jumlah (mis. suggestion cepat untuk "PCS", "KG", "YARD", "METER").
