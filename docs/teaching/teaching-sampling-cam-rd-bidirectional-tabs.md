# 🎓 Modul Pembelajaran: Sinkronisasi Tab Dua Arah Program CAM & Hasil R&D

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform, State Synchronization, Technical Sheet Mapping, Claymorphism Design System  
> **Prasyarat**: Dasar Compose State (`remember`, `mutableStateOf`, `LaunchedEffect`), EventVerse Domain Architecture  
> **Referensi Task**: Sinkronisasi Tab Bagian Garmen Antara Program CAM & Hasil R&D (Gramasi & Waktu)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada industri garmen rajut (*knitwear manufacturing*), satu artikel busana terdiri dari beberapa bagian panel garmen yang dirajut secara terpisah (misalnya: Badan Depan, Badan Belakang, Lengan, Kerah, Rib, Manset).

Alur pengerjaannya terbagi dalam dua tahap krusial:
1. **Program CAM (Computer-Aided Manufacturing)**: Programmer CAM menentukan kode program rajut, instruksi panah (*feeder* benang), dan setelan tarikan (*tensility*) per bagian panel garmen.
2. **Lantai R&D (Mesin Rajut Turun)**: Operator rajut di lantai R&D menjalankan program tersebut dan menimbang gramasi aktual serta mencatat waktu rajut riil per bagian panel.

Sebelumnya, bagian Hasil R&D hanya berupa daftar datar tanpa tab (*flat list*), tidak memiliki navigasi tab, dan penambahan bagian di satu tempat tidak langsung sinkron ke tempat lainnya. Jika tim R&D menemukan bagian baru (seperti "Kerah" tambahan) atau ingin menginput gramasi per bagian secara terorganisir, data menjadi tidak selaras dengan lembar CAM.

### Solusi & Mental Model
Lembar kerja teknis SPK (*Technical Sheet*) adalah **satu sumber kebenaran tunggal (Single Source of Truth)**. Seluruh bagian garmen (*parts*) direpresentasikan dalam model data bersama `CamPartTab` yang memuat:
- Program, Feeder, Tensility (diisi di tahap CAM)
- Gramasi & Waktu (diisi di tahap R&D)

UI di tahap CAM (`CamProgramTabbedSection`) dan UI di tahap R&D (`RdResultSection`) keduanya memiliki **tab bar dinamis** yang identik. Perubahan pada daftar tab (tambah bagian, hapus bagian, ganti gramasi) otomatis diserialisasi dan langsung terefleksi dua arah secara reaktif.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun fitur sinkronisasi dua arah ini dari nol, ikuti urutan berikut:

1. **Langkah 1: Parsing & Mapping Foundation (`CamProgramSheetMapping.kt`)**
   - Pastikan parser mengenali bagian garmen dari seluruh section teknis yang ada: tidak hanya `PROGRAM`, `FEEDER_INSTRUCTIONS`, dan `TENSELITY`, namun juga `PANEL_WEIGHTS` dan `PANEL_MINUTES`.
   - Mengapa? Jika sebuah bagian garmen baru ditambahkan di R&D atau data lama hanya memuat gramasi, bagian tersebut tidak boleh hilang saat diparsing kembali.

2. **Langkah 2: Perancangan UI Tabbing di Hasil R&D (`RdResultSection.kt`)**
   - Bangun baris tab horizontal menggunakan token Claymorphism (`ClayShapes.Pill`, `WeMadeColors`, `ClayBadge`).
   - Setiap tab menampilkan status pengisian: tanda centang hijau jika gramasi & waktu sudah terisi, atau tanda peringatan jika belum lengkap.
   - Sediakan tombol `+ Bagian` yang membuka `AddCamPartDialog` dengan fitur *autocomplete* nama bagian garmen.
   - Sediakan tombol hapus (`x`) pada tiap tab untuk menghapus bagian panel.

3. **Langkah 3: Sinkronisasi Seleksi & Reaktivitas State**
   - Gunakan `selectedTabId` dengan *fallback* nama bagian (`selectedTabName`) agar ketika komposisi ulang terjadi atau index tab bergeser, kursor operator tetap berada pada bagian yang sedang dikerjakan.
   - Sediakan *container* kartu untuk bagian yang aktif, menampilkan input Gramasi dan Waktu secara proporsional.

4. **Langkah 4: Auto-Save Draft di Dialog SPK (`SamplingSpkDetailDialog.kt`)**
   - Hubungkan *callback* `onSectionsChange` dari `RdResultSection` ke mekanisme `onDraftChange(it)` agar setiap ketikan operator R&D langsung tersimpan sebagai draft dan tidak hilang jika dialog ditutup.

5. **Langkah 5: Pengujian Otomatis (`CamProgramTabMappingTest.kt`)**
   - Tulis *unit test* untuk memvalidasi:
     - Bagian yang ditambah dari R&D masuk ke serialisasi dan terbaca oleh lembar CAM.
     - Bagian yang dihapus bersih dari seluruh section data.
     - Section gramasi/waktu mandiri dapat diparsing menjadi tab secara utuh.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pengenalan Bagian dari Semua Section (`CamProgramSheetMapping.kt`)
```kotlin
val tabNames = linkedSetOf<String>()
progSec?.rows?.forEach { if (it.label.isNotBlank()) tabNames.add(it.label.trim()) }
feederSec?.rows?.forEach { partName(it.label).takeIf { n -> n.isNotBlank() }?.let(tabNames::add) }
tenselitySec?.rows?.forEach { partName(it.label).takeIf { n -> n.isNotBlank() }?.let(tabNames::add) }
weightSec?.rows?.forEach { partName(it.label).takeIf { n -> n.isNotBlank() }?.let(tabNames::add) }
minuteSec?.rows?.forEach { partName(it.label).takeIf { n -> n.isNotBlank() }?.let(tabNames::add) }
```
**Mengapa ditulis begini?**
- `linkedSetOf<String>` menjaga urutan kemunculan tab unik tanpa duplikasi.
- Menambahkan pemeriksaan ke `weightSec` dan `minuteSec` menjamin bahwa bagian yang dibuat pertama kali di R&D atau bagian lama yang hanya memiliki gramasi/menit tetap diakui sebagai tab resmi.

---

### Blok B: Tab Bar Interaktif & Status Pengisian (`RdResultSection.kt`)
```kotlin
tabs.forEach { tab ->
    val isSelected = tab.id == activeTab?.id
    val isFilled = tab.gramasi.isNotBlank() && tab.waktu.isNotBlank()

    Row(
        modifier = Modifier
            .clickable {
                selectedTabId = tab.id
                selectedTabName = tab.name
            }
            .claySurface(
                shape = ClayShapes.Pill,
                background = if (isSelected) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                ...
            )
    ) {
        if (isFilled) {
            IconCheck(modifier = Modifier.size(12.dp), color = if (isSelected) WeMadeColors.Surface else WeMadeColors.Success)
        } else {
            IconWarning(modifier = Modifier.size(12.dp), color = if (isSelected) WeMadeColors.Surface else WeMadeColors.Warning)
        }
        Text(text = tab.name, ...)
        Text(text = if (isFilled) "Terisi" else "Belum", ...)
        // Tombol hapus tab
        Box(modifier = Modifier.clickable { ... updateAndEmit(newTabs) }) {
            IconClose(...)
        }
    }
}
```
**Mengapa ditulis begini?**
- **Mental Model Status**: Operator R&D tidak perlu membuka setiap tab satu per satu untuk mengecek kelengkapan data. Tab pill memberikan sinyal visual instan ("Terisi" warna hijau vs "Belum" warna amber).
- **Claymorphism**: Menggunakan `claySurface` dengan `ClayShapes.Pill` dan palet brand WeMade tanpa hardcode hex literal.

---

### Blok C: Sinkronisasi Reaktif ke Program CAM & Draft SPK
```kotlin
fun updateAndEmit(
    newTabs: List<CamPartTab> = tabs,
    newMeasurements: List<StageInputRow> = sheet.finishedMeasurements
) {
    tabs = newTabs
    onSectionsChange(serializeCamSections(newTabs, sheet.formulaNote, newMeasurements))
}
```
Dan pada `SamplingSpkDetailDialog.kt`:
```kotlin
if (isRdStage) {
    RdResultSection(
        sections = camSections,
        onSectionsChange = {
            camSections = it
            if (isCamStage || isRdStage) onDraftChange(it)
        }
    )
}
```
**Mengapa ditulis begini?**
- Ketika `newTabs` dipancarkan (*emit*), `camSections` di level parent diperbarui.
- Karena `CamProgramTabbedSection` juga mengamati `camSections`, tab baru yang ditambahkan di R&D langsung muncul di bagian Program CAM di atasnya.
- Memanggil `onDraftChange(it)` memastikan draft SPK tersimpan secara otomatis (*debounced draft autosave*).

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Jebakan ID Berbasis Index (`tab-$idx-$name`)**:
   - Jika ID tab hanya mengandalkan index array (0, 1, 2), menghapus tab ke-0 akan menyebabkan semua tab berikutnya bergeser index. State `selectedTabId` yang lama tidak akan cocok lagi, mengakibatkan loncatan kursor yang membingungkan.
   - **Solusi**: Selalu sediakan fallback pencocokan berbasis nama bagian (`selectedTabName`) saat mencari `activeTab`.
2. **Lupa Sinkronisasi Draft**:
   - Hanya mengupdate state lokal `camSections` tanpa memicu `onDraftChange` menyebabkan data yang diketik di R&D hilang saat pengguna me-refresh halaman atau berpindah menu.
3. **Menggunakan Unicode Emoji untuk Icon Status**:
   - Menulis `Text("✓")` atau `Text("⚠")` akan menyebabkan masalah *tofu* (kotak kosong `▯`) di browser WebAssembly/Skiko. Selalu gunakan icon berbasis canvas seperti `IconCheck` dan `IconWarning` dari `ClayIcons.kt`.

---

## ✅ 5. Verifikasi & Tantangan Mandiri

### Verifikasi yang Telah Dilakukan
1. **Unit Testing**:
   - `CamProgramTabMappingTest.kt` memvalidasi parsing, round-trip serialisasi, penambahan tab dari R&D, dan penghapusan tab secara bersih.
2. **Kompilasi Multiplatform**:
   - Sukses dikompilasi pada target JVM (`./gradlew :app:shared:jvmTest`) dan WebAssembly (`./gradlew :app:webApp:compileKotlinWasmJs`).
3. **Standar Design System**:
   - Mematuhi limit ukuran file (< 400 baris), nol literal `Color(0xFF...)`, dan mematuhi Neo-Brutalism/Claymorphism.

### Tantangan Mandiri untuk Junior Developer
- Coba buat komponen rekapitulasi ringkas (*quick summary chip*) yang menghitung total gramasi kotor dari seluruh panel yang sudah terisi di header kartu Hasil R&D.
