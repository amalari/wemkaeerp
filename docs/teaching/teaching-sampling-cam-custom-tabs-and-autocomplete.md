# 🎓 Modul Pembelajaran: Program CAM — Tab Bagian Garmen Dinamis (Non-Default) & Autocomplete

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform UI, Domain-Driven Design (DDD), UX Autocomplete vs Static Choices, Clean Component Decomposition  
> **Prasyarat**: Compose State Management, DDD Pure Function Serialization, Claymorphism Design System  
> **Referensi**: SPK Sampling Detail Dialog — Program CAM Tabbed Section & Add Part Dialog

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada industri manufaktur rajut (knitwear), jenis garmen yang dibuat sangat beragam:
- **Sweater / Kardigan**: Memiliki panel Badan Depan, Badan Belakang, Lengan Kiri/Kanan, Kerah, dan Manset.
- **Rompi / Vest**: Tidak memiliki bagian lengan sama sekali.
- **Syal / Selimut**: Hanya berupa 1 panel tunggal persegi panjang.
- **Kupluk / Beanie**: Hanya terdiri dari panel topi dan lipatan rib.

Jika sistem memaksakan tab default (*Depan*, *Belakang*, *Lengan*, *Kerah*) untuk semua SPK baru:
1. Operator garmen non-sweater (seperti rompi atau syal) harus menghapus tab yang tidak relevan satu per satu.
2. Jika operator lupa menghapus tab kosong tersebut, gerbang validasi domain `CAM_REQUIRED` akan menolak pergerakan tahap karena mengira ada bagian garmen yang belum selesai diprogram.
3. Deretan tombol pilihan (*choice chips*) statis yang memakan ruang dialog memberi kesan kaku dan terbatas, seolah-olah operator hanya boleh memilih dari opsi yang disediakan.

### Analogi Sederhana
Bayangkan sebuah formulir pendaftaran barang bagasi bandara. Jika sistem secara otomatis mengasumsikan setiap penumpang membawa *Koper Besar*, *Ransel*, *Kardus*, dan *Tas Tangan*, penumpang yang hanya membawa satu tas ransel kecil terpaksa harus menghapus 3 barang bawaan palsu dari tiketnya. Pendekatan yang benar adalah: **mulai dari daftar kosong**, lalu biarkan penumpang mengetik barangnya dengan bantuan **autocomplete** yang menyarankan istilah umum saat mengetik.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus mengimplementasikan fitur ini dari nol, ikuti urutan berikut:

1. **Langkah 1: Hilangkan Hardcoded Default di Parser (`CamProgramTabbedSection.kt`)**
   - Hapus inisialisasi paksa `tabNames.addAll(DEFAULT_CAM_PARTS)`.
   - Pastikan bila data SPK baru kosong, `tabs` mengembalikan `emptyList()`.
2. **Langkah 2: Sediakan UX Empty State & Izinkan Penghapusan Semua Tab**
   - Saat `tabs.isEmpty()`, tampilkan kartu *Empty State* yang menjelaskan bahwa belum ada bagian garmen dan arahkan operator untuk mengklik `+ Bagian`.
   - Hilangkan batasan `if (tabs.size > 1)` pada tombol hapus tab `(X)` agar operator bisa menghapus tab sampai habis jika salah memasukkan data.
3. **Langkah 3: Dekomposisi Komponen Dialog (`AddCamPartDialog.kt`)**
   - Pisahkan dialog penambahan bagian ke file terpisah agar mematuhi batas ukuran file (*File Size Rules* di bawah 400 baris) dan *Single Responsibility Principle*.
   - Gantikan deretan chip statis dengan input teks standar (`ClayTextField`).
   - Implementasikan *live autocomplete suggestions panel* di bawah field input yang menyaring saran dari `SUGGESTED_CAM_PARTS` secara real-time berdasarkan teks yang diketik.
   - Tambahkan aksi keyboard `ImeAction.Done` sehingga pengguna bisa langsung menekan tombol **Enter** di keyboard untuk menambahkan bagian.
4. **Langkah 4: Validasi Kelengkapan Tab & Auto-Switching (`CamProgramTabbedSection.kt`)**
   - Tambahkan properti `val isComplete: Boolean get() = program.isNotBlank() && feederInstructions.isNotEmpty()` pada model `CamPartTab`.
   - Pasang parameter `validationTrigger: Int = 0` yang dipicu saat operator mengklik tombol *"Simpan Program -> Masuk Mesin Rajut"*.
   - Pasang `LaunchedEffect(validationTrigger)`: jika ada tab yang belum lengkap (`!it.isComplete`), otomatis alihkan tab aktif (`selectedTabId = firstIncomplete.id`) agar tab tersebut langsung terbuka di layar.
   - Tampilkan indikator status pada masing-masing pill tab: `IconCheck` + label *"Lengkap"* warna hijau bila sudah lengkap, dan `IconWarning` + label *"Belum"* bila masih ada isian yang kosong (berubah merah saat validasi gagal).
   - Tampilkan pesan error banner merah dan beri highlight error (`isError = true`) dengan teks helper merah pada field yang belum diisi.
5. **Langkah 5: Automated Unit Test (`CamProgramTabMappingTest.kt`)**
   - Perbarui assertion tes agar memverifikasi bahwa parser dengan input kosong menghasilkan `emptyList()` dan uji fungsi `isComplete` pada model tab.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Parser Tanpa Default (`CamProgramTabbedSection.kt`)

```kotlin
fun parseCamSections(sections: List<StageInputSection>): Pair<List<CamPartTab>, String> {
    val progSec = sections.firstOrNull { it.section == StageSectionNames.PROGRAM }
    val feederSec = sections.firstOrNull { it.section == StageSectionNames.FEEDER_INSTRUCTIONS }
    val tenselitySec = sections.firstOrNull { it.section == StageSectionNames.TENSELITY }
    val formulaSec = sections.firstOrNull { it.section == StageSectionNames.PATTERN_FORMULAS }

    val tabNames = linkedSetOf<String>()
    progSec?.rows?.forEach { if (it.label.isNotBlank()) tabNames.add(it.label.trim()) }
    feederSec?.rows?.forEach { row ->
        val name = if (row.label.contains(" • ")) row.label.substringBefore(" • ").trim() else row.label.trim()
        if (name.isNotBlank()) tabNames.add(name)
    }
    tenselitySec?.rows?.forEach { row ->
        val name = if (row.label.contains(" • ")) row.label.substringBefore(" • ").trim() else row.label.trim()
        if (name.isNotBlank()) tabNames.add(name)
    }
    // DULU: if (tabNames.isEmpty()) tabNames.addAll(DEFAULT_CAM_PARTS)
    // SEKARANG: Tidak ada pemaksaan default. Jika belum ada input, tabNames tetap kosong.
    ...
}
```

**Mental Model**:
Data domain tidak boleh "mengarang" data jika pengguna belum mengisinya. Jika belum ada bagian garmen yang disimpan di database, UI harus jujur menampilkan status kosong (*empty state*), bukan berasumsi bahwa pakaian tersebut pasti punya depan, belakang, lengan, dan kerah.

---

### Blok B: Dialog Input dengan Autocomplete Dinamis (`AddCamPartDialog.kt`)

```kotlin
@Composable
fun AddCamPartDialog(
    existingPartNames: List<String>,
    onDismiss: () -> Unit,
    onAddPart: (String) -> Unit
) {
    var partNameInput by remember { mutableStateOf("") }
    
    // 1. Filter saran autocomplete saat pengguna mulai mengetik
    val matchingSuggestions = remember(partNameInput, existingPartNames) {
        val trimmed = partNameInput.trim()
        if (trimmed.isBlank()) {
            emptyList()
        } else {
            SUGGESTED_CAM_PARTS.filter { suggestion ->
                suggestion.contains(trimmed, ignoreCase = true) &&
                    !suggestion.equals(trimmed, ignoreCase = true) &&
                    existingPartNames.none { it.equals(suggestion, ignoreCase = true) }
            }
        }
    }
    val canAdd = partNameInput.isNotBlank() && existingPartNames.none { it.equals(partNameInput.trim(), ignoreCase = true) }

    fun submit() {
        if (canAdd) {
            onAddPart(partNameInput.trim())
        }
    }
    ...
```

**Mengapa Autocomplete lebih unggul daripada Choice Chips?**
1. **Tidak Membatasi Mental Pengguna**: Deretan tombol pilihan statis membuat pengguna merasa opsinya hanya yang tertera di layar. Dengan field input bebas + autocomplete, pengguna tahu mereka bebas mengetik nama apa pun (mis. *"Kerah Shanghai"*, *"Saku Dada Kiri"*), namun tetap mendapatkan kemudahan memilih rekomendasi umum.
2. **Bersih & Hemat Ruang**: Panel autocomplete hanya muncul jika pengguna mulai mengetik dan ada kata yang cocok (`matchingSuggestions.isNotEmpty()`). Saat input kosong atau setelah memilih saran, panel menghilang secara halus.
3. **Ergonomi Keyboard**: Operator yang bekerja cepat di laptop/desktop pabrik cukup mengetik `"dep"` -> tekan Enter atau klik saran -> tab langsung terbentuk tanpa memindahkan tangan ke mouse berkali-kali.

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Jebakan Default yang Menjebak Validasi**:
   Membuat default value di UI tampak seperti "kemudahan", tetapi dalam sistem ERP berbasis gerbang (*Stage Gating*), default yang tidak relevan justru menjadi blokade yang memaksa pengguna melakukan pekerjaan pembersihan ekstra.
2. **Jebakan Popup di Dalam Dialog pada Compose Multiplatform**:
   Menggunakan `DropdownMenu` Material 3 mentah di dalam `Dialog` sering kali menyebabkan masalah z-index, event stealing (fokus input keyboard hilang), atau koordinat offset meleset di target Skiko Wasm/Desktop. Menampilkan panel saran autocomplete yang terintegrasi langsung di layout kartu dialog (`claySurface`) jauh lebih stabil, responsif, dan bebas efek samping.
3. **Melanggar Batas Baris File**:
   Menggabungkan semua dialog dan komponen tambahan ke dalam satu file screen besar membuat file membengkak melewati ambang 400 baris. Selalu pisahkan dialog ke file mandiri seperti `AddCamPartDialog.kt`.

---

## ✅ 5. Verifikasi & Pengujian Mandiri

1. **Unit Test**:
   Jalankan perintah berikut:
   ```bash
   ./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.sampling.CamProgramTabMappingTest"
   ```
   Pastikan seluruh test lolos (`BUILD SUCCESSFUL`), khususnya test `parseCamSections_withEmptyList_shouldReturnEmptyTabs`.
2. **Verifikasi Visual**:
   - Buka SPK di tahap Program CAM:
     - Pastikan tab tidak otomatis terisi *Depan*, *Belakang*, *Lengan*, *Kerah*.
     - Muncul kartu empty state: *"Belum Ada Bagian Garmen"*.
     - Klik `+ Bagian` -> muncul dialog dengan input bersih tanpa deretan pilihan chip statis.
     - Ketik nama (mis. `"ba"`) -> saran *"Badan Depan"* dan *"Badan Belakang"* muncul dengan tombol *"Gunakan"*.
     - Tekan Enter atau klik *"Tambah"* -> tab bagian langsung aktif.
