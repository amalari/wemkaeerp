# 🎓 Modul Pembelajaran: Gramasi & Waktu per Bagian + Section Hasil Ukuran Jadi (Program CAM R&D)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Lembar kerja dinamis label/value, mapping UI ↔ domain, Compose state hoisting, Aturan Tiga Kali, batas ukuran file
> **Prasyarat**: Paham `StageInputSection` / `StageInputRow`, dasar `remember` + `mutableStateOf` di Compose
> **Referensi Task**: Permintaan langsung — halaman `/sampling-order`, tahap Program CAM (kolom R&D)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: Tim R&D menulis program CAM per bagian garmen (Depan, Lengan, …). Selain kode
  program dan instruksi panah, mereka juga perlu mencatat **gramasi** (berat panel, mis. `117 GR`) dan
  **waktu rajut** (mis. `37 MENIT`) per bagian. Mereka juga perlu mencatat **hasil ukuran jadi** sampel
  (P Badan, L Dada, …). Isi daftar ukuran ini berbeda di tiap desain, jadi tidak bisa dibuat sebagai
  kolom yang tetap.
- **Analogi**: Lembar CAM itu seperti map berkas per bagian baju. Gramasi dan waktu adalah dua kolom
  isian di **setiap** map. Hasil ukuran jadi adalah **lembar lampiran terpisah** yang barisnya bisa kamu
  tambah sendiri, seperti formulir "isi sesuai kebutuhan".
- **Hasil akhir**: Setiap tab bagian punya field Gramasi dan Waktu. Di bawah Catatan Rumus Pola ada
  kartu baru "HASIL UKURAN JADI" berisi baris label/value yang bisa ditambah dan dihapus.

---

## 🧭 2. "Start dari Mana?" — Urutan Penulisan

1. **Langkah 0 — Cek kontrak domain dulu (`core`)**. `StageInputSection(section, rows)` sudah generik
   (label + value), jadi **skema domain tidak perlu berubah**. Nama section `GRAMASI` dan `WAKTU` juga
   sudah ada di `StageSectionNames`. Yang kita tambahkan hanya satu nama baru:
   `FINISHED_MEASUREMENTS = "HASIL UKURAN JADI"`.
2. **Langkah 1 — Model UI + mapping murni**. Tambahkan `gramasi` dan `waktu` ke `CamPartTab`, buat
   `CamProgramSheet`, lalu perbarui `parseCamSections` / `serializeCamSections`. Kedua fungsi ini murni,
   jadi bisa diuji tanpa Compose.
3. **Langkah 2 — Test round-trip** (serialize → parse harus kembali ke data yang sama).
4. **Langkah 3 — UI**. Pasang field di tab aktif, lalu pakai ulang `DynamicSectionTable` untuk section
   baru.
5. **Langkah 4 — Cek ukuran file** dan kompilasi 5 target.

Kenapa mapping lebih dulu daripada UI? Karena bug di fitur seperti ini hampir selalu berupa **data
yang hilang saat round-trip**, bukan tampilan yang salah. Kalau mapping-nya sudah terbukti lewat test,
UI tinggal menyambungkan.

---

## 🧱 3. Bedah Blok per Blok

### Blok A — Model sheet ([CamProgramSheetMapping.kt](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/components/CamProgramSheetMapping.kt))

```kotlin
data class CamPartTab(
    val id: String, val name: String, val program: String = "",
    val feederInstructions: List<String> = emptyList(),
    val tenselities: List<String> = emptyList(),
    val gramasi: String = "",
    val waktu: String = ""
) {
    val isComplete: Boolean get() = program.isNotBlank() && feederInstructions.isNotEmpty()
}

data class CamProgramSheet(
    val tabs: List<CamPartTab>,
    val formulaNote: String,
    val finishedMeasurements: List<StageInputRow> = emptyList()
)
```

- Gramasi dan waktu **tidak** ikut `isComplete`. Keduanya informatif, bukan syarat untuk lolos gerbang
  menuju Mesin Rajut. Kalau dijadikan wajib, SPK lama yang belum punya datanya akan langsung terkunci.
- `Pair<List, String>` diganti `data class`. Pair tidak lagi cukup jelas begitu nilainya tiga. Data class
  tetap bisa di-destructure (`val (tabs, note) = …`), jadi pemanggil lama tidak rusak.

### Blok B — Serialisasi

```kotlin
fun singleRows(pick: (CamPartTab) -> String) = tabs.filter { pick(it).isNotBlank() }
    .map { StageInputRow(label = it.name, value = pick(it)) }
...
StageInputSection(StageSectionNames.PANEL_WEIGHTS, singleRows { it.gramasi }),
StageInputSection(StageSectionNames.PANEL_MINUTES, singleRows { it.waktu }),
StageInputSection(StageSectionNames.FINISHED_MEASUREMENTS, finishedMeasurements)
```

- Gramasi dan waktu memakai konvensi yang sama dengan PROGRAM: **label = nama bagian**. Parser
  mencocokkannya lewat `rowsForPart(name)`.
- Baris ukuran jadi disimpan **apa adanya, termasuk yang masih kosong**. Kenapa? Lihat Jebakan 1.

### Blok C — UI ([CamProgramTabbedSection.kt](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/components/CamProgramTabbedSection.kt))

```kotlin
ClayCard(modifier = Modifier.fillMaxWidth()) {
    DynamicSectionTable(
        sectionName = StageSectionNames.FINISHED_MEASUREMENTS,
        hint = "Ukuran hasil jadi sampel — tambah baris sesuai kebutuhan …",
        rows = finishedMeasurements,
        labelPlaceholder = "Label (mis. P BADAN)",
        valuePlaceholder = "Nilai (mis. 55 CM)",
        onRowsChange = { updateAndEmit(tabs, rumusPolaNote, it) }
    )
}
```

- Kita **tidak** membuat tabel baru. `DynamicSectionTable` sudah dipakai untuk semua lembar tahap. Ini
  sesuai Aturan Tiga Kali: satu komponen yang dipakai ulang, bukan blok yang disalin. Satu-satunya
  perubahan di komponen itu adalah placeholder yang kini berupa parameter. Default-nya tetap sama, jadi
  pemanggil lain tidak terpengaruh.
- `updateAndEmit` mendapat parameter ketiga dengan default `finishedMeasurements`, sehingga semua
  pemanggil lama (edit program, panah, tenselity) tidak perlu diubah.

---

## ⚖️ 4. The "Why"

| Pendekatan | Alternatif | Kenapa ini | Risiko alternatif |
|---|---|---|---|
| Section label/value generik | Field tetap `pBadan`, `lDada` di domain | Tenant dan desain bebas menamai ukuran | Setiap ukuran baru butuh migrasi DB dan perubahan domain |
| Pakai ulang `DynamicSectionTable` | Tulis tabel baru di file CAM | Satu tampilan, satu sumber perbaikan | Dua tabel yang lama-lama tidak sama lagi |
| Mapping dipindah ke file sendiri | Tambah langsung ke file section | File section tetap 417 baris, di bawah hard limit 600 | File section menuju >500 baris, campur logika data dan UI |

---

## ⚠️ 5. Jebakan Pemula

1. **Membuang baris kosong saat serialize.**
   - *Bahaya*: State Compose di-parse ulang dari `sections` (`remember(sections)`). Kalau baris kosong
     difilter, baris yang baru ditekan "+" langsung hilang dan tombolnya terlihat rusak.
   - *Solusi*: Simpan semua baris ukuran jadi. Gerbang domain hanya menghitung `isFilled`, jadi baris
     kosong tidak berbahaya.
2. **Menjadikan field baru sebagai syarat `isComplete`.** Ini diam-diam memblokir alur SPK yang sudah
   berjalan. Ubah aturan gerbang hanya kalau bisnis memintanya.
3. **Memecah file per baris** (`CamProgramTabbedSectionPart2.kt`). Kita memecahnya per tanggung
   jawab: *mapping data* dipisah dari *render*.

---

## 🧪 6. Pembuktian

`CamProgramTabMappingTest.serializeAndParse_withGramasiWaktuAndFinishedMeasurements_shouldRoundTrip`:

```kotlin
val measurements = listOf(StageInputRow("P BADAN", "55 CM"), StageInputRow("", ""))
val sheet = parseCamSections(serializeCamSections(tabs, "", measurements))
assertEquals("117 GR", sheet.tabs[0].gramasi)
assertEquals(measurements, sheet.finishedMeasurements) // baris kosong tetap ada
```

Test lama juga diperbarui: jumlah section sekarang 7 (sebelumnya 4). Kompilasi JVM, WasmJS, dan JS
lolos, dan `jvmTest` untuk kelas ini hijau.

---

## 🏆 7. Tantangan Mandiri

- [ ] Tampilkan gramasi dan waktu per bagian di kartu read-only operator (`OperatorDeskDetails.kt`)
      sebagai tabel, bukan satu baris gabungan.
- [ ] Beri tombol "isi dari preset size chart" yang mengisi baris ukuran jadi dari
      `order.finishedSizeCharts`.
- [ ] Tambahkan validasi ringan: nilai gramasi harus berupa angka + satuan (`GR`).
