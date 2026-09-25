# 🎓 Modul Pembelajaran: Section Program CAM Tabbed (Addable Part Tabs) & Catatan Rumus Pola Terpisah

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform UI, Domain-Driven Design (DDD), Claymorphism Design System, State Serialization & Round-trip  
> **Prasyarat**: Compose State Management, Kotlin Sealed Types, DDD Value Objects, Stage Work Input Codec  
> **Referensi**: SPK Sampling Detail Dialog — Program CAM Section Upgrade

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada industri garmen rajut (knitwear), seorang programmer mesin rajut CAM (seperti mesin Shima Seiki / Stoll) tidak bekerja dengan satu tabel datar generik. Sebuah sweater atau kardigan terdiri atas beberapa **bagian/komponen fisik** (misalnya *Badan Depan*, *Badan Belakang*, *Lengan*, *Kerah*, *Rib Bawah*, dsb.).

Setiap bagian fisik memiliki 3 parameter teknis esensial:
1. **Kode Program CAM**: File program biner mesin rajut yang dieksekusi (mis. `BIAN-D`, `BIAN-B`, `BIAN-L`).
2. **Instruksi Panah (Feeder)**: Posisi dan jenis benang pada feeder panah rajut (mis. `Feeder 1: 1 RIB STRIPE 1 PLAY (HITAM)`).
3. **Tenselity**: Nilai kerapatan tarikan jarum rajut (tension settings, mis. `1 BS POLY: 14`, `BS TARIK: 12`).

Sebelumnya, UI hanya menampilkan 3 tabel flat bertumpuk (`PROGRAM`, `INSTRUKSI PANAH`, dan `RUMUS POLA`). Operator terpaksa mencampur aduk baris depan, belakang, dan lengan dalam satu tabel panjang yang membingungkan. Selain itu, **Rumus Pola** bukanlah parameter per bagian, melainkan catatan/kalkulasi rajut keseluruhan (*measurement formula*) yang seharusnya berada di section terpisah.

### Analogi Sederhana
Bayangkan Anda sedang merakit lemari dengan 4 laci berbeda.
- **Cara Lama (Flat Table)**: Semua sekrup laci 1, laci 2, laci 3, dan kunci digabung dalam satu kantong acak tanpa label jelas.
- **Cara Tabbed (Berdasarkan Bagian)**: Ada sekat laci (`[ Depan ]`, `[ Belakang ]`, `[ Lengan ]`, `[ Kerah ]`). Saat membuka sekat "Depan", Anda langsung melihat kode program, feeder benang, dan tension rajut untuk bagian Depan saja. Catatan rumus ukuran ditaruh di papan memo terpisah di luar laci.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun fitur ini dari nol, ikuti urutan berikut:

1. **Langkah 0: Kontrak Domain (`core`)**
   - Periksa `StageSectionNames`: tambahkan `TENSELITY` ke dalam `CAM_REQUIRED` sebagai gerbang validasi bisnis bahwa sebelum mesin rajut beroperasi, operator wajib melengkapi program, instruksi feeder, dan tension rajut.
2. **Langkah 1: Unit Test Domain**
   - Perbarui data fixture pengujian (`SamplingOrderTest.kt`) untuk memvalidasi bahwa transisi tahap dari `CAM_PROGRAMMING` ke `MACHINE_KNITTING` memerlukan `TENSELITY` selain `PROGRAM` dan `FEEDER_INSTRUCTIONS`.
3. **Langkah 2: Spesifikasi Lembar Tahap (`StageWorksheetSpecs.kt`)**
   - Daftarkan `TENSELITY` ke dalam `CAM_SECTION_SPECS` agar terdefinisi di single source of truth lembar tahapan.
4. **Langkah 3: Pemodelan Data State Tab & Serializer (`CamProgramTabbedSection.kt`)**
   - Buat model representasi internal `CamPartTab(id, name, program, feederInstructions: List<String>, tenselities: List<String>)`.
   - Tulis pure function `parseCamSections(...)` dan `serializeCamSections(...)` untuk menjamin data round-trip 100% kompatibel dengan schema JSONB `stage_inputs` di backend tanpa perlu migrasi DB baru.
5. **Langkah 4: Komponen Presentasi Tabbed & Taggable Mobile-First (`ClayTagInput.kt`)**
   - Rancang baris tab dinamis dengan pills `ClayShapes.Pill` dan tombol `+ Bagian`.
   - Hilangkan tombol fisik `+ Tambah` yang kaku: dukung trigger otomatis tombol keyboard **`Enter / Done`** dan **koma (`,`)** untuk langsung mengonversi ketikan menjadi tag.
   - Integrasikan gestur Drag & Drop Reorder pada badan chip dengan handle 6-titik dan penomoran dinamis.
   - Rancang section terpisah (`ClayCard`) untuk Catatan Rumus Pola (`minLines = 3`).
   - Buat dialog tambah bagian kustom lengkap dengan chip saran bagian garmen umum.
6. **Langkah 5: Integrasi Dialog Utama (`SamplingSpkDetailDialog.kt`)**
   - Gantikan implementasi lama dengan `CamProgramTabbedSection` dan hubungkan gerbang tombol *"Simpan Program -> Masuk Mesin Rajut"*.
7. **Langkah 6: Automated Testing**
   - Tulis unit test untuk verifikasi round-trip parsing dan validasi gerbang (`CamProgramTabMappingTest.kt`).

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Domain Gating di Pure Kotlin (`core/StageWorkInput.kt`)

```kotlin
object StageSectionNames {
    const val PROGRAM = "PROGRAM"
    const val FEEDER_INSTRUCTIONS = "INSTRUKSI PANAH"
    const val PATTERN_FORMULAS = "RUMUS POLA"
    const val PANEL_WEIGHTS = "GRAMASI"
    const val PANEL_MINUTES = "WAKTU"
    const val SIZE_CHART = "DETAIL SIZE CHART"
    const val TENSELITY = "TENSELITY"

    /** Section wajib sebelum boleh masuk Mesin Rajut (gerbang CAM). */
    val CAM_REQUIRED: List<String> = listOf(PROGRAM, FEEDER_INSTRUCTIONS, TENSELITY)
}
```

**Mengapa ditulis begini?**
- `CAM_REQUIRED` mendefinisikan *business contract* domain murni. Operator mesin rajut tidak bisa merajut jika parameter kerapatan rajut (`TENSELITY`) tidak ada.
- Rumus Pola (`PATTERN_FORMULAS`) diposisikan sebagai catatan teknis yang fleksibel sehingga tidak menghambat operator jika rumus pola sudah terdokumentasi di tempat lain.

---

### Blok B: Parser & Serializer Round-trip State

```kotlin
fun parseCamSections(sections: List<StageInputSection>): Pair<List<CamPartTab>, String> {
    // 1. Ekstraksi section dari format domain
    val progSec = sections.firstOrNull { it.section == StageSectionNames.PROGRAM }
    val feederSec = sections.firstOrNull { it.section == StageSectionNames.FEEDER_INSTRUCTIONS }
    val tenselitySec = sections.firstOrNull { it.section == StageSectionNames.TENSELITY }
    val formulaSec = sections.firstOrNull { it.section == StageSectionNames.PATTERN_FORMULAS }

    // 2. Kumpulkan tab part unik
    val tabNames = linkedSetOf<String>()
    progSec?.rows?.forEach { if (it.label.isNotBlank()) tabNames.add(it.label.trim()) }
    // ...
    if (tabNames.isEmpty()) tabNames.addAll(DEFAULT_CAM_PARTS)

    // 3. Mapping data per tab
    val tabs = tabNames.mapIndexed { idx, name ->
        // ...
        CamPartTab("tab-$idx-$name", name, progRow?.value.orEmpty(), feeders, tenselities)
    }

    return Pair(tabs, formulaNote)
}
```

**Mental Model:**
- Database PostgreSQL menyimpan data lembar sebagai `List<StageInputSection>` (skema generik `section` + `rows: [{label, value}]`).
- Dengan menyandikan tab ke dalam label (mis. `Depan • Panah 1`), kita **tidak perlu mengubah skema DDL database sama sekali**!
- Saat dimuat, parser otomatis merekonstruksi tab dan barisnya. Saat disimpan, serializer mengonversi kembali ke format flat yang dimengerti Ktor backend dan PostgreSQL JSONB.

---

### Blok C: UI Komposisi Tab Neo-Brutalist & Note Box Terpisah

```kotlin
// Card 1: Tabbed Technical Inputs
ClayCard(modifier = Modifier.fillMaxWidth()) {
    // Tab Pills Row (Scrollable)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        tabs.forEach { tab ->
            // Pill Button dengan selected state dan tombol delete
        }
        ClayButton(text = "+ Bagian", style = ClayButtonStyle.Ghost, ...)
    }

    // Active Tab Content (Program + Draggable Taggable Inputs)
    ClayTextField(value = activeTab.program, label = "KODE PROGRAM CAM", ...)
    ClayTagInput(
        label = "INSTRUKSI PANAH",
        tags = activeTab.feederInstructions,
        numbered = true, // Nomor 1, 2, 3 berurutan dinamis saat di-drag
        onTagsChange = { ... }
    )
    ClayTagInput(
        label = "TENSELITY / SETTING TENSION",
        tags = activeTab.tenselities,
        numbered = false, // Draggable reorder
        onTagsChange = { ... }
    )
}

// Card 2: Rumus Pola (Beda Section!)
ClayCard(modifier = Modifier.fillMaxWidth()) {
    Row {
        IconNote(...)
        Text("CATATAN RUMUS POLA", fontWeight = FontWeight.Bold)
    }
    ClayTextField(
        value = rumusPolaNote,
        placeholder = "mis. PB & LD : 25 X 8\nP BADAN : 2.94 K...",
        singleLine = false,
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
}
```

**Poin Arsitektur & Styling:**
- **Zero Literal Color**: Semua warna menggunakan `WeMadeColors.*`.
- **Zero Emoji Glyphs**: Menggunakan vektor Canvas murni dari `ClayIcons.kt` (`IconClose`, `IconNote`, `IconPlus`).
- **Unified Tag Input Surface**: Tag yang sudah dipilih diletakkan **di dalam kotak input yang sama** berdampingan dengan cursor `BasicTextField` (menggunakan `ClayFlowRow`), bukan melayang di atas field secara terpisah. Ketika pengguna mengklik area kotak manapun, fokus otomatis diarahkan ke `BasicTextField`.
- **File Decomposition**: Memisahkan `CamProgramTabbedSection.kt` (384 baris) dan `ClayTagInput.kt` (294 baris), keduanya patuh pada batas lunak 400 baris.

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

| Jebakan | Mengapa Berbahaya | Solusi yang Diterapkan |
|---|---|---|
| **Menyimpan State Tab secara Terpisah di DB** | Membutuhkan migrasi DDL baru, API endpoint baru, dan merusak kompatibilitas historis order lama. | Enkode hierarki tab ke dalam label baris `StageInputRow` (`"$tabName • $itemLabel"`). Skema DB tetap bersih. |
| **Mutasi List di dalam `onDrag` (Recomposition Trap)** | Memanggil `onTagsChange` saat drag masih aktif membuat parent merekomposisi. Key `pointerInput(tags)` berubah dan coroutine dibatalkan oleh Compose sebelum `onDragEnd`, menyebabkan chip macet di posisi dragged (menjadi biru gelap permanen). | Pindahkan mutasi ke `onDragEnd`, gunakan `try ... finally { reset() }` agar state selalu ter-reset, dan pisahkan tombol hapus `✕` dari pointer drag. |
| **Hardcode Ukuran Tombol & Border** | Menggunakan `ClayButtonSize.Small` (yang tidak ada) atau `ClayBorder.Thin`. | Gunakan token `ClaySpacing`, `ClayBorder.Hairline`, dan `PaddingValues` standar. |
| **Menghapus Semua Tab Sampai Kosong** | Jika user menghapus semua tab, UI akan crash atau menjadi blank tanpa input. | Tombol `×` hanya ditampilkan jika `tabs.size > 1`. Minimal 1 tab selalu terjaga. |
| **Memaksa Rumus Pola di dalam Tab** | Operator harus mengulang mengetik rumus pola yang sama di setiap tab (Depan, Belakang, Lengan). | Rumus Pola diangkat ke section tersendiri di bawah tabbed card sebagai note multi-baris. |

---

## 🧪 5. Verifikasi & Tantangan Mandiri

### Cara Menguji Kebenaran Kode
1. **Jalankan Unit Test Shared**:
   ```bash
   ./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.sampling.CamProgramTabMappingTest"
   ```
2. **Jalankan Unit Test Core Domain**:
   ```bash
   ./gradlew :core:jvmTest --tests "com.eventverse.app.domain.sampling.SamplingOrderTest"
   ```
3. **Uji Alur Visual**:
   - Buka SPK di tahap **Program CAM**.
   - Cek apakah tab default (`Depan`, `Belakang`, `Lengan`, `Kerah`) tampil rapi.
   - Klik `+ Bagian`, pilih atau ketik bagian kustom (mis. `Rib Bawah`).
   - Isi kode program, instruksi panah, dan tenselity untuk tiap bagian.
   - Isi Catatan Rumus Pola di kotak terpisah di bawah.
   - Periksa tombol *"Simpan Program -> Masuk Mesin Rajut"* aktif saat semua parameter wajib terisi.
