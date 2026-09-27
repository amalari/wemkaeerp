# 🎓 Modul Pembelajaran: Desain Tabel Matriks Ukuran (Size Chart & Qty) Dinamis, Adaptif & Anti-Truncation di Compose Multiplatform

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform Layout, `horizontalScroll`, Dynamic Column Width, SingleLine Constraints, File Decomposition (Rule §14)  
> **Prasyarat**: Pemahaman dasar Compose UI (`Row`, `Box`, `BasicTextField`, `Modifier`), state hoisting, serta konvensi Design System Clay repo WeMade ERP  
> **Referensi Task**: UI Fix — Matriks Ukuran Sampling (Max 1 baris per kolom, lebar adaptif mengikuti teks terpanjang, dan label "ALL SIZE" muncul utuh)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada form input spesifikasi pola (**Size Chart / POM**) dan **Alokasi Jumlah Sampel** di dialog pembuatan/pengeditan Deal sampling:
1. **Teks Terpotong (Truncation)**: Lebar kolom ukuran sebelumnya dikunci mati (`width(64.dp)`). Ketika user menamai kolom dengan `"ALL SIZE"`, kata tersebut terpotong menjadi `"ALL SIZ"` karena ruang yang tersisa setelah dikurangi padding dan tombol hapus silang `(X)` hanya sekitar 40dp.
2. **Placeholder Baris POM Menggembung (Line Wrapping)**: Kolom Bagian / Parameter Pola (POM) sebelumnya dikunci `140.dp`. Teks placeholder petunjuk `"cth: Lingkar Pinggang, Dada"` tidak muat sehingga membungkus menjadi 2 baris (`"cth: Lingkar Pinggang"` di atas dan `"Dada"` di bawah). Akibatnya, tinggi baris tabel menjadi tidak seragam dan membengkak secara vertikal.

### Mental Model & Solusi
Sebuah tabel spreadsheet yang fleksibel harus memiliki sifat:
- **Max 1 Row per Cell**: Setiap sel input maupun header wajib memiliki batas `singleLine = true`, `maxLines = 1`, dan `softWrap = false`. Tidak boleh ada elemen teks yang turun ke baris berikutnya.
- **Dynamic Adaptive Width (Mengikuti Teks Terpanjang)**:
  - Kolom **Bagian / POM** menghitung panjang karakter terpanjang antara label `"Bagian / POM"`, teks placeholder, dan data POM yang diinput user, lalu mengalikan perkiraan lebar karakter dengan batas bawah yang aman (`minOf 220.dp`).
  - Kolom **Ukuran (Size Columns)** menghitung panjang karakter terpanjang antara nama ukuran (misal `"ALL SIZE"`, `"CUSTOM SIZE"`), nilai ukuran di setiap baris fisik, dan kuantitas sampel, dengan tambahan ruang untuk tombol hapus `(X)` jika kolom bersifat dinamis (`minOf 80.dp`).
- **Sinkronisasi Kolom Antar-Tabel**: Kolom berukuran sama pada tabel *Size Chart* dan tabel *Alokasi Jumlah Sampel* saling menyelaraskan lebarnya sehingga tabel bawah dan atas sejajar rapi (*vertically aligned*).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun fitur tabel spreadsheet adaptif seperti ini dari nol:

1. **Langkah 0: Identifikasi Constraint Viewport & Scroll Container**
   - Karena jumlah kolom ukuran dan panjang teks parameter fisik tidak dapat dibatasi di muka (bisa bertambah kapan saja oleh user), kontainer luar **wajib** menggunakan `Modifier.fillMaxWidth().horizontalScroll(scrollState)`.
2. **Langkah 1: Rumuskan Logika Ukuran Kolom Murni (Pure Math Helpers)**
   - Buat fungsi penentu lebar kolom independen (`calculatePomColumnWidth` dan `calculateSizeColumnWidth`).
   - Jangan menyematkan angka literal ajaib (*magic numbers*) di dalam komponen Row; gunakan formula berbasis panjang karakter maksimal + padding.
3. **Langkah 2: Terapkan Kontrak Single-Line di Seluruh Text & TextField**
   - Pastikan setiap `BasicTextField` memiliki `singleLine = true` dan `maxLines = 1`.
   - Pastikan teks dekorasi / placeholder di `decorationBox` juga memiliki `maxLines = 1` dan `softWrap = false`.
4. **Langkah 3: Terapkan Ukuran Sinkron ke Header dan Data Row**
   - Sediakan Map `sizeColWidths = remember(columns, rows) { columns.associateWith { calculateSizeColumnWidth(...) } }`.
   - Gunakan `sizeColWidths[col]` pada header dan semua sel data di kolom tersebut.
5. **Langkah 4: Tinjau Batas Ukuran File (File Decomposition — Rule §14)**
   - Jika satu file Composable membengkak mendekati atau melebihi batas hard limit (600 baris untuk presentation), pisahkan komponen tabel kedua (misal `SamplingQuantityTable`) ke dalam file mandiri berbasis tanggung jawab tanpa memecahnya secara artifisial (`Part2`, `Helpers`, dll.).

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Kalkulasi Lebar Kolom Dinamis

Di [`DealSamplingSizeTables.kt`](file:///Volumes/amalari/Projects/wemade-erp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/DealSamplingSizeTables.kt):

```kotlin
internal const val POM_PLACEHOLDER_TEXT = "cth: Dada, Pinggang"

/**
 * Menghitung lebar kolom Bagian / POM secara adaptif (max 1 baris, default 110dp, pas mengikuti teks).
 */
internal fun calculatePomColumnWidth(pomRows: List<SizeChartRow>): Dp {
    val maxRowChars = pomRows
        .filter { !it.isQtyRow && it.pomName.isNotBlank() }
        .maxOfOrNull { it.pomName.length } ?: 0
    val neededWidth = (maxRowChars * 5.8).dp + 22.dp
    return maxOf(110.dp, neededWidth).coerceAtMost(320.dp)
}

/**
 * Menghitung lebar kolom ukuran secara adaptif (max 1 baris, mengikuti teks terpanjang).
 * Memastikan teks ukuran panjang seperti "ALL SIZE" atau nama custom muncul utuh tanpa terpotong.
 */
internal fun calculateSizeColumnWidth(
    col: String,
    pomRows: List<SizeChartRow> = emptyList(),
    qtyRow: SizeChartRow? = null,
    includeDeleteButtonSpace: Boolean = true
): Dp {
    val headerChars = if (col.isBlank()) 4 else col.length
    val maxRowChars = pomRows.maxOfOrNull { (it.values[col] ?: "").length } ?: 0
    val qtyChars = (qtyRow?.values?.get(col) ?: "").length
    val contentChars = maxOf(headerChars, maxRowChars, qtyChars)

    val buttonSpace = if (includeDeleteButtonSpace) 18.dp else 0.dp
    val neededWidth = (contentChars * 6.2).dp + buttonSpace + 16.dp
    return maxOf(64.dp, neededWidth)
}
```

**Mengapa blok ini dirumuskan begini?**
- **Menghindari Perluasan Prematur (*Premature Expansion*)**: Font 11sp di Compose memiliki rasio lebar rata-rata ~5.5–5.8dp per karakter. Menghitung `neededWidth = (maxChars * 5.8).dp + 22.dp` dengan dasar `110.dp` menjamin bahwa nama-nama POM standar (seperti *"Panjang Baju"*, *"Lebar Dada"*, *"Panjang Lengan"*) pas rapi di `110.dp` tanpa memicu pelebaran kolom sebelum teks benar-benar mendekati tepi kanan (*"belum sampai ujung sudah nambah width-nya"*).
- **Menghilangkan Jeda Padding Kosong Berlebih (*Excessive Right Padding*)**: Ketika teks melebihi 15 karakter (misal *"Lingkar Kerung Lengan"*), kolom melebar secara halus dan presisi mengikuti pertambahan karakter, dengan sisa padding kanan yang selalu konsisten ~10–13dp, bukan lompatan drastis puluhan dp.
- **Kolom Ukuran Rapi**: Untuk ukuran biasa seperti `"S"`, `"M"`, `"XL"`, lebar dasar `64.dp` memberikan tampilan yang padat dan estetik. Untuk ukuran panjang seperti `"ALL SIZE"` (8 huruf), kolom otomatis melebar ke `~84.dp` sehingga tombol hapus silang `(X)` dan teks label muat utuh tanpa terpotong (*zero truncation*).

### Blok B: Penguncian Max 1 Baris pada TextField & Placeholder

Di baris POM:

```kotlin
BasicTextField(
    value = row.pomName,
    readOnly = readOnly,
    onValueChange = { newPom ->
        onUpdateRow(row.copy(pomName = newPom))
    },
    textStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = WeMadeColors.OnSurface
    ),
    singleLine = true,
    maxLines = 1,
    decorationBox = { innerTextField ->
        if (row.pomName.isEmpty()) {
            Text(
                text = POM_PLACEHOLDER_TEXT,
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.5f),
                maxLines = 1,
                softWrap = false
            )
        }
        innerTextField()
    },
    modifier = Modifier.fillMaxWidth()
)
```

**Mengapa blok ini ditulis begini?**
- Sering kali developer hanya menambahkan `singleLine = true` pada `BasicTextField`, tetapi lupa bahwa `Text` di dalam `decorationBox` tetap memiliki default `softWrap = true`. Jika lebar kolom sempit, placeholder-lah yang memicu wrap 2 baris. Menambahkan `maxLines = 1` dan `softWrap = false` pada `Text` placeholder memastikan teks petunjuk tetap berupa 1 baris lurus.

### Blok C: Pemisahan Modul Bersih (`SamplingQuantityTable.kt`)

Sesuai aturan Rule §14 mengenai File Decomposition:
- File [`DealSamplingSizeTables.kt`](file:///Volumes/amalari/Projects/wemade-erp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/DealSamplingSizeTables.kt) mengelola `SamplingSizeChartTable` (396 baris, < 400 soft limit).
- File baru [`SamplingQuantityTable.kt`](file:///Volumes/amalari/Projects/wemade-erp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/SamplingQuantityTable.kt) mengelola `SamplingQuantityTable` (265 baris, < 400 soft limit).
- Keduanya berada pada package yang sama (`com.eventverse.app.presentation.deal.components`) sehingga tidak ada import yang rusak di [`DealDetailDialog.kt`](file:///Volumes/amalari/Projects/wemade-erp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/DealDetailDialog.kt).

### Blok D: Penambahan Kolom Kosong (Blank Placeholder) Tanpa Teks Bawaan "Size 1 / Size 2"

Ketika pengguna mengklik tombol `+ Kolom`, sistem sebelumnya menyisipkan teks `"Size ${currentColumns.size + 1}"` (seperti `"Size 2"`, `"Size 3"`). Pengguna harus repot-repot menghapus teks default tersebut sebelum dapat mengetikkan ukuran yang diinginkan (misal `"M"`, `"28"`, dll.).

```kotlin
// core/src/commonMain/kotlin/com/eventverse/app/domain/sampling/SamplingSizeMatrix.kt
fun addColumnToMatrix(matrix: List<SizeChartRow>, newColumn: String = ""): List<SizeChartRow> {
    val existing = extractSizeColumns(matrix)
    if (newColumn.isBlank()) {
        if (existing.any { it.isBlank() }) return matrix // Mencegah duplikasi kolom kosong tak bernilai
        val blankKey = ""
        return matrix.map { row ->
            val newMap = LinkedHashMap(row.values)
            newMap[blankKey] = ""
            row.copy(values = newMap)
        }
    }
    // ...
}
```

Di [`DealDetailDialog.kt`](file:///Volumes/amalari/Projects/wemade-erp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/DealDetailDialog.kt):
```kotlin
onAddColumn = {
    if (!isFormReadOnly) {
        sizeMatrixInput = addColumnToMatrix(sizeMatrixInput, "")
    }
}
```

Dengan perubahan ini:
- Kolom baru langsung terbuka bersih dengan teks kosong `""`.
- Placeholder `"Size"` yang berwarna samar (*muted*) otomatis muncul melalui `decorationBox`, memberi isyarat visual yang jelas bagi pengguna untuk segera mengetik nama ukuran yang diinginkan.
- Kolom kosong tidak dianggap aktif (`isSizeColumnActive` mengembalikan `false`) sampai pengguna memberi nama pada kolom tersebut.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa Memilih Pendekatan Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Kalkulasi Lebar Adaptif Berbasis Karakter** | Lebar Statis / Hardcoded (misal `64.dp`) | Nama ukuran di industri fashion sangat dinamis (`ALL SIZE`, `28`, `OVERSIZED`, `CUSTOM`). Kolom membesar otomatis sesuai kebutuhan. | Teks panjang terpotong menjadi `"ALL SIZ"`, `"OVERSI"` atau tombol hapus tertutup. |
| **`maxLines = 1` + `softWrap = false`** | Membiarkan text wrapping otomatis | Mencegah baris tabel memiliki tinggi yang berbeda-beda (*vertical stutter*), menjaga ritme visual tabular. | Satu sel dengan teks panjang membuat seluruh baris membengkak tinggi dan merusak estetika Clay. |
| **Dekomposisi File per Komponen Domain** | Membiarkan 1 file > 640 baris | Mematuhi batas arsitektur repo (Rule §14: maks 600 baris per file presentation). | File menjadi "God File", sulit direview, dan melanggar Continuous Integration check. |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Jebakan "Fixed Width di Dalam Scrollable Container"**:
   - *Gejala*: Kontainer sudah diberi `.horizontalScroll()`, tetapi lebar sel di dalamnya dipatok `64.dp`.
   - *Penyebab*: Mengira `horizontalScroll` otomatis melebarkan sel. Padahal `horizontalScroll` hanya memfasilitasi scrolling jika konten melebihi layar; sel yang di-hardcode `64.dp` tetap akan mengunci ukuran selnya di 64dp.
2. **Jebakan Placeholder DecorationBox Wrapping**:
   - *Gejala*: Sel tampak memiliki 2 baris meskipun `BasicTextField` diberi `singleLine = true`.
   - *Penyebab*: Teks di dalam `decorationBox` adalah Composable `Text` tersendiri. Jika tidak dipasang `maxLines = 1, softWrap = false`, ia akan membungkus ke baris baru secara independen.

---

## 🧪 6. Cara Membuktikan Kodingan Kita Bekerja

1. **Kompilasi Multi-Target**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm
   ./gradlew :app:shared:compileKotlinWasmJs
   ./gradlew :app:shared:compileKotlinJs
   ```
   Seluruh target harus menghasilkan status `BUILD SUCCESSFUL`.
2. **Verifikasi Visual**:
   - Buka halaman Deal Detail di browser (`http://localhost:3000/crm-sales/deals`).
   - Masuk ke tab Sampling pada kartu desain.
   - Perhatikan kolom pertama bertuliskan `"ALL SIZE"`; pastikan huruf `"E"` dan ikon silang `(X)` tampil utuh dengan spasi yang lega.
   - Ketik nama POM yang panjang pada baris input; pastikan tinggi baris tetap 1 baris datar (*single row*) dan kolom melebar secara proporsional.
3. **Audit Batas Ukuran File (Rule §14)**:
   ```bash
   wc -l app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/DealSamplingSizeTables.kt \
         app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/SamplingQuantityTable.kt
   ```
   Kedua file harus berada di bawah 400 baris (soft limit).

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: Uji coba menambahkan kolom ukuran baru bernama `"EXTRA LARGE"` dan `"CUSTOM SIZE 34"`. Amati apakah kolom secara otomatis memperlebar dirinya sendiri dan scroll horizontal muncul dengan mulus.
- [ ] **Tantangan 2**: Cobalah ubah resolusi browser ke ukuran sempit (misal 1024px atau tablet view) dan pastikan scrollbar horizontal tetap berjalan tanpa tombol hapus di baris data terpotong.
