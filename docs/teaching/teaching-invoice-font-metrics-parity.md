# 🎓 Modul Pembelajaran: Menyamakan Metrik Font Kanvas & PDF pada Desainer Faktur

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Tipografi digital (advance width, em, unitsPerEm), Kotlin Multiplatform `commonMain`, Compose `CompositionLocal`, Apache PDFBox, desain tabel data yang di-*check in*
> **Prasyarat**: Paham dasar Compose (`Text`, modifier), tahu bahwa `core` adalah modul KMP yang jalan di JVM/JS/Wasm/iOS/Android
> **Referensi Task**: Perbaikan paritas kanvas↔PDF pada `presentation/invoicing/template`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Desainer template faktur punya **dua mesin gambar** untuk satu dokumen yang sama:

| | Mesin gambar | Berjalan di |
|---|---|---|
| Saat mendesain | Compose (Skia) | Browser / desktop / HP |
| Saat mencetak | Apache PDFBox | Server |

Kalau keduanya tidak sepakat, hasilnya adalah kelas bug paling menyebalkan yang ada: **tidak ada yang error, tidak ada test yang merah, dan salahnya baru ketahuan setelah faktur sampai di tangan klien.**

Sebelum perbaikan ini, ketidaksepakatannya ada di dua tempat sekaligus.

**Ketidaksepakatan 1 — fontnya memang beda.**
Renderer PDF punya fungsi privat `pickFont` yang bilang "teks ≥14pt pakai Fredoka, sisanya Nunito". Kanvas tidak tahu apa-apa soal aturan itu. Lebih parah, kanvas menggambar dengan:

```kotlin
Text(text = …, fontSize = …, fontWeight = …)   // tidak ada fontFamily!
```

Tanpa `fontFamily`, Compose jatuh ke `LocalTextStyle.current`. Di Material 3 nilai bawaannya `TextStyle.Default`, dan tidak ada satu pun `ProvideTextStyle` di seluruh `app/shared`. Artinya kanvas menggambar dengan **font bawaan sistem operasi** — Roboto di Android, SF di macOS, sans-serif di web. Jadi judul faktur 20pt: Fredoka di kertas, SF di layar.

**Ketidaksepakatan 2 — lebar hurufnya ditebak.**
`InvoiceTextLayout` memutuskan di mana baris dipotong. Untuk itu ia perlu tahu lebar tiap huruf, dan dulu ia **menaksir** lewat tabel per kelas karakter:

```kotlin
char.isDigit()        -> 0.56
char in "MWmw@%&"     -> 0.90
char.isUpperCase()    -> 0.68
else                  -> 0.52
```

Saya ukur taksiran itu terhadap berkas Nunito yang sebenarnya dipaketkan:

| Karakter | Nunito Regular (nyata) | Taksiran | Selisih |
|---|---|---|---|
| digit `0`–`9` | **0.600** | 0.560 | **−6.7%** |
| `W` | **1.101** | 0.900 | **−18.3%** |
| `i`, `l` | 0.232 | 0.340 | +46.6% |
| titik/koma | 0.234 | 0.280 | +19.6% |

### Kenapa arah kesalahannya penting

Ini bagian yang paling layak diingat seumur hidup sebagai engineer: **dua arah kesalahan tidak sama bahayanya.**

- **Taksiran kelebaran** (`i` ditaksir 46% lebih lebar) → baris dipotong lebih awal dari perlunya. Jelek sedikit, tapi dokumennya tetap benar.
- **Taksiran kesempitan** (digit ditaksir 6.7% lebih sempit) → pemecah baris bilang "muat", padahal nyatanya tidak.

Lalu apa yang terjadi di PDF ketika baris ternyata lebih lebar dari kotaknya? Lihat kode perataannya:

```kotlin
val drawX = when (style.align) {
    TextAlign.LEFT   -> xPt
    TextAlign.CENTER -> xPt + (widthPt - textWidth).coerceAtLeast(0f) / 2f
    TextAlign.RIGHT  -> xPt + (widthPt - textWidth).coerceAtLeast(0f)
}
```

Kalau `textWidth > widthPt`, maka `(widthPt - textWidth)` negatif, `coerceAtLeast(0f)` menjepitnya ke nol, dan `drawX` menjadi `xPt` — **angka yang seharusnya rata kanan diam-diam berubah jadi rata kiri**, lalu menjulur keluar kotaknya.

Dan kelas karakter yang paling salah taksir adalah digit. Isi utama kolom uang adalah digit. Jadi taksiran yang paling meleset mengenai bagian faktur yang paling tidak boleh salah.

### Analogi Sederhana

Bayangkan dua tukang jahit membuat baju dari satu pola yang sama, tapi:
- tukang A pakai meteran sungguhan,
- tukang B pakai jengkal tangannya sendiri.

Selama polanya longgar, bajunya "kelihatan sama". Begitu polanya pas-pasan, baju tukang B kekecilan — dan baru ketahuan waktu dipakai pelanggan, bukan waktu dijahit.

Perbaikan ini: **buang jengkal, kasih kedua tukang meteran yang sama persis.**

### Hasil Akhir yang Diharapkan

Satu sumber kebenaran untuk dua pertanyaan tipografi:
1. *Font mana yang berlaku?* → `InvoiceFontResolver`
2. *Selebar apa huruf itu?* → `InvoiceFontMetrics`

Dipakai bersama oleh pemecah baris, kanvas Compose, dan renderer PDFBox. Tanpa pengecualian.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Ini urutannya, dan **urutannya tidak boleh ditukar**. Kalau kamu kerjakan langkah 2 dulu, kamu akan mengkalibrasi lebar Nunito untuk teks yang di layar digambar SF — hasilnya tetap beda, cuma dengan angka yang lebih rapi. Sia-sia.

### Langkah 0: Ukur dulu, jangan langsung ngoding

Sebelum menulis satu baris pun, saya bikin program 30 baris yang membaca keempat TTF dan membandingkan taksiran lama vs lebar asli. Tabel selisih di bagian 1 itu hasilnya.

**Kenapa ini langkah nol?** Karena tanpa angka, "taksirannya kurang akurat" cuma perasaan. Dengan angka, kamu tahu **kelas mana** yang salah, **ke arah mana**, dan **seberapa**. Itu yang menentukan apakah perbaikannya perlu tabel penuh atau cukup kalibrasi ulang konstanta.

> **Kebiasaan yang layak dibawa**: kalau kamu mau mengganti sebuah heuristik, ukur dulu seberapa buruk heuristik itu. Sering kali hasilnya "ternyata cukup bagus, jangan disentuh" — dan itu juga kemenangan.

### Langkah 1: Angkat identitas font ke domain (`core`)

Buat `InvoiceFont` + `InvoiceFontResolver`. **Pindahkan aturan `pickFont` apa adanya**, jangan sekalian diperbaiki.

Kenapa ini duluan? Karena langkah 2 butuh jawaban atas pertanyaan "font mana?" sebelum bisa menjawab "selebar apa?".

### Langkah 2: Sambungkan kedua mesin gambar ke resolver itu

- PDF: `pickFont` privat → `fonts.forStyle(style)`
- Kanvas: tambahkan `fontFamily` + `fontWeight` dari resolver

Setelah langkah ini **berhenti dan lihat aplikasinya**. Ini perubahan visual terbesar dari seluruh task — setiap teks di kanvas berubah bentuk.

### Langkah 3: Bangkitkan tabel metrik dari TTF

Pakai `PDFont.getStringWidth` — **jalur yang sama persis dengan yang dipakai renderer**, bukan pustaka metrik lain.

### Langkah 4: Ganti taksiran dengan tabel

`InvoiceTextLayout` sekarang bertanya ke `InvoiceFontMetrics`.

### Langkah 5: Pasang jaring pengaman (test drift)

Tabel yang di-*check in* bisa membusuk diam-diam. Test yang membandingkannya ulang dengan PDFBox setiap build adalah satu-satunya alasan tabel itu boleh ada.

### Langkah 6: Tampilkan sisa kasusnya di kanvas

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Identitas font sebagai tipe domain

`core/…/domain/invoicing/template/InvoiceFont.kt`

```kotlin
enum class InvoiceFont(val resourceName: String) {
    NUNITO_REGULAR("nunito_regular"),
    NUNITO_BOLD("nunito_bold"),
    FREDOKA_MEDIUM("fredoka_medium"),
    FREDOKA_BOLD("fredoka_bold")
}

object InvoiceFontResolver {
    const val HEADING_THRESHOLD_PT: Int = 14

    fun resolve(style: TextStyleSpec): InvoiceFont = when {
        style.fontSizePt >= HEADING_THRESHOLD_PT && style.isBold -> InvoiceFont.FREDOKA_BOLD
        style.fontSizePt >= HEADING_THRESHOLD_PT -> InvoiceFont.FREDOKA_MEDIUM
        style.isBold -> InvoiceFont.NUNITO_BOLD
        else -> InvoiceFont.NUNITO_REGULAR
    }
}
```

**Mengapa blok ini ditulis begini?**

- **Enum menamai BERKAS, bukan keluarga font.** Kenapa bukan `enum class FontFamily { NUNITO, FREDOKA }` plus parameter bobot? Karena hanya empat berkas inilah yang benar-benar dipaketkan. Menamai keluarga akan menyembunyikan kenyataan bahwa Nunito Italic tidak ada, dan membuat `TextStyleSpec.isItalic` **terlihat seperti berfungsi** padahal tidak. Tipe yang jujur soal apa yang tersedia lebih berharga daripada tipe yang rapi.

- **`resourceName` hidup di enum.** Nama berkas dulu tersebar sebagai string literal `"/fonts/nunito_regular.ttf"` di renderer. Sekarang enum-nya jadi daftar resmi, dan test bisa memeriksa *setiap* berkas yang dideklarasikan memang terpaket.

- **Aturan 14pt dipindah apa adanya, walau kasar.** `TextStyleSpec` tidak punya medan keluarga font, jadi ambang ukuran adalah proksi untuk "ini judul". Itu tidak ideal. Tapi memperbaikinya **di saat yang sama** dengan memindahkannya berarti kalau ada faktur yang berubah rupa, kamu tidak tahu itu gara-gara pemindahannya atau perbaikannya.

> 💡 **Mental model: pindahkan, lalu perbaiki — jangan sekaligus.** Satu commit harus bisa dijawab dengan satu kalimat. "Memindahkan aturan X ke domain **tanpa mengubah perilakunya**" adalah kalimat yang bisa diverifikasi. "Memindahkan sambil memperbaiki" tidak.

### Blok B: Menjembatani domain ke Compose lewat `CompositionLocal`

`app/shared/…/presentation/invoicing/template/TemplateCanvas.kt`

```kotlin
@Immutable
private class InvoiceFontFaces(val nunito: FontFamily, val fredoka: FontFamily) {

    fun familyFor(font: InvoiceFont): FontFamily = when (font) {
        InvoiceFont.NUNITO_REGULAR, InvoiceFont.NUNITO_BOLD -> nunito
        InvoiceFont.FREDOKA_MEDIUM, InvoiceFont.FREDOKA_BOLD -> fredoka
    }

    fun weightFor(font: InvoiceFont): FontWeight = when (font) {
        InvoiceFont.NUNITO_REGULAR -> FontWeight.Normal
        InvoiceFont.NUNITO_BOLD -> FontWeight.Bold
        InvoiceFont.FREDOKA_MEDIUM -> FontWeight.Medium
        InvoiceFont.FREDOKA_BOLD -> FontWeight.Bold
    }
}

private val LocalInvoiceFontFaces = staticCompositionLocalOf<InvoiceFontFaces> {
    error("LocalInvoiceFontFaces belum dipasang — bungkus kanvas dengan ProvideInvoiceFontFaces.")
}
```

**Mengapa blok ini ditulis begini?**

- **Kenapa `CompositionLocal`, bukan parameter biasa?** Rantai pemanggilnya `TemplateCanvas → PaperContent → ElementLayer → RenderElementContent`, empat tingkat. Menambah dua parameter di setiap tingkat berarti mengotori tiga fungsi yang tidak peduli soal font hanya untuk mengantarkannya. `CompositionLocal` persis untuk kasus ini: konteks ambien yang dibaca di daun, bukan data yang mengalir.

- **Kenapa `staticCompositionLocalOf`, bukan `compositionLocalOf`?** Yang `static` tidak melacak pembacanya untuk recomposition. Itu lebih murah, dan cocok karena nilainya tidak pernah berubah selama kanvas hidup. Kalau nilainya sering berubah, `compositionLocalOf` yang benar.

- **Kenapa nilai bawaannya `error(...)`?** Ini keputusan yang paling saya pikirkan. Alternatifnya memberi bawaan "aman" seperti `FontFamily.Default`. Tapi jatuh ke font bawaan platform secara diam-diam **adalah persis bug yang sedang kita perbaiki**. Kalau ada yang lupa memasang penyedianya, saya ingin aplikasinya crash saat dibuka developer — bukan mencetak faktur yang salah untuk klien. Gagal berisik di waktu murah > gagal senyap di waktu mahal.

- **Kenapa objeknya di-`remember`, bukan dipanggil per elemen?** Perhatikan:
  ```kotlin
  @Composable fun rememberNunitoFamily(): FontFamily = FontFamily(Font(...), Font(...), Font(...))
  ```
  Namanya `remember…`, tapi **tidak ada `remember` di dalamnya** — ia merakit `FontFamily` baru setiap panggilan. Memanggilnya di dalam perulangan elemen berarti merakit dua keluarga font untuk setiap elemen di setiap recomposition. Jadi ia dipanggil sekali di `ProvideInvoiceFontFaces`, hasilnya di-`remember`, lalu dibagikan.

> ⚠️ **Pelajaran**: awalan `remember` pada nama fungsi adalah **konvensi**, bukan jaminan. Selalu buka isinya sebelum memanggil sesuatu di dalam loop.

### Blok C: Tabel metrik yang di-*check in*

`core/…/domain/invoicing/template/InvoiceFontMetrics.kt`

```kotlin
object InvoiceFontMetrics {
    const val UNITS_PER_EM: Int = 1000

    private val NUNITO_REGULAR_ASCII = intArrayOf(
        258, 228, 392, 600, 600, 930, 693, 221, 317, 317, 450, 600,  //  !"#$%&'()*+
        228, 424, 228, 283, 600, 600, 600, 600, 600, 600, 600, 600,  // ,-./01234567
        …
    )

    fun advanceUnits(font: InvoiceFont, char: Char): Int {
        val code = char.code
        if (code in ASCII_LOW..ASCII_HIGH) return asciiTable(font)[code - ASCII_LOW]
        EXTRA[font]?.get(char)?.let { return it }
        return asciiTable(font)['M'.code - ASCII_LOW]
    }
}
```

**Mengapa blok ini ditulis begini?**

- **Kenapa di-*check in*, bukan dibangkitkan saat build?** Karena `core` adalah `commonMain` — kode ini juga jalan di JS dan Wasm, yang **tidak bisa membaca berkas TTF**. Membangkitkan saat build berarti menambah plugin Gradle lintas lima target untuk 380 angka yang hanya berubah kalau fontnya diganti. Ongkosnya tidak sepadan.

- **Kenapa `IntArray`, bukan `Map<Char, Int>`?** ASCII itu rentang berurutan, jadi `code - 32` adalah indeks langsung — O(1) tanpa hashing, tanpa boxing 380 `Integer`. Pemecah baris memanggil ini sekali per karakter per pengukuran; di dokumen panjang itu puluhan ribu panggilan.

- **Kenapa komentar `//  !"#$%&'()*+` di ujung tiap baris?** Tanpa itu, ini blok 95 angka telanjang yang tidak bisa direview manusia. Dengan itu, kamu bisa menghitung ke kolom ke-5 dan memverifikasi "`$` = 600". Tabel data yang tidak bisa dibaca manusia adalah tabel data yang akan salah diedit suatu hari.

- **Kenapa karakter tak dikenal memakai lebar `M`?** Ini penerapan langsung pelajaran "arah kesalahan" dari bagian 1. Lebar `M` jelas kelewat lebar untuk huruf beraksen seperti `é`. Itu **disengaja**: kelebaran cuma memecah baris lebih awal; kesempitan meluberkan teks keluar kotak tanpa ada yang tahu. Kalau harus salah, salahlah ke arah yang bisa dilihat.

- **Kenapa ada `EXTRA` untuk segelintir karakter non-ASCII?** Tanda kutip melengkung (`'` `"`), en/em dash (`–` `—`), dan elipsis (`…`) sering ikut masuk lewat tempelan dari Word/Chat. Semuanya **sempit**. Kalau mereka jatuh ke lebar `M`, baris akan pecah jauh lebih awal dari seharusnya dan pengguna melihat teksnya "meloncat" tanpa sebab. Empat belas entri eksplisit menutup 99% kasus nyata.

### Blok D: Test drift — alasan tabel itu boleh ada

`server/src/test/…/InvoiceFontMetricsDriftTest.kt`

```kotlin
@Test
fun `tabel ASCII cocok dengan metrik PDFBox untuk seluruh font`() {
    InvoiceFont.entries.forEach { font ->
        withFont(font) { pdFont ->
            (32..126).forEach { code ->
                val char = code.toChar()
                assertEquals(
                    pdFont.getStringWidth(char.toString()).roundToInt(),
                    InvoiceFontMetrics.advanceUnits(font, char),
                    "Lebar '$char' (${hex(code)}) pada $font meleset. " +
                        "Berkas font berubah? Bangkitkan ulang tabel di InvoiceFontMetrics."
                )
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**

- **Kenapa test ini WAJIB ada?** Tabel angka yang di-*check in* punya satu kelemahan fatal: ia bisa membusuk tanpa suara. Siapa pun yang memutakhirkan versi Nunito atau menukar subsetnya membuat seluruh perhitungan meleset, dan gejalanya baru muncul sebagai teks meluber di PDF yang **sudah dikirim ke klien**. Test ini mengubah kegagalan senyap di produksi menjadi build merah di laptop.

- **Kenapa dibandingkan dengan PDFBox, bukan pustaka tipografi lain?** Yang harus cocok bukanlah "kebenaran tipografi universal", melainkan **angka yang dipakai mesin gambarnya sendiri**. Membandingkan dengan FreeType atau HarfBuzz akan memberi jawaban yang *lebih benar* secara tipografi dan tetap salah untuk tujuan kita.

- **Kenapa test ini di `server`, bukan di `core`?** `core` tidak punya PDFBox maupun akses ke berkas TTF. `server` punya keduanya, dan di situlah PDF benar-benar dirender.

- **Detail kecil yang menggigit**: pesan errornya awalnya saya tulis pakai `String.format("… (U+%04X) …")`. Test-nya langsung gagal dengan `UnknownFormatConversionException` — karena `$char` diinterpolasi **sebelum** `format` jalan, dan salah satu karakter yang diuji adalah `%`. Saya ganti dengan perakitan hex manual.

  > 💡 Kalau string format-mu memuat data yang dikendalikan orang lain, kamu punya bug. Di sini "orang lain" cuma test case, tapi polanya sama dengan format-string injection.

### Blok E: Menampilkan sisa kasusnya, bukan menyembunyikannya

`core/…/template/InvoiceDocumentLayout.kt`

```kotlin
val hasOverflow: Boolean
    get() {
        val style = element.textStyleOrNull ?: return false
        return textLines.any { line ->
            InvoiceTextLayout.measureWidthMm10(line, style) > rect.width.value
        }
    }
```

dan di kanvas:

```kotlin
when {
    isSelected       -> Modifier.border(ClayBorder.Thick, WeMadeColors.Primary, ClayShapes.Element)
    laid.hasOverflow -> Modifier.border(ClayBorder.Thick, WeMadeColors.Warning, ClayShapes.Element)
    else             -> Modifier.border(ClayBorder.Hairline, …)
}
```

**Mengapa blok ini ditulis begini?**

- Dengan metrik nyata, sisa kasus luapan tinggal satu: satu karakter yang lebih lebar dari elemennya, yang memang tidak bisa dipecah lagi. `coerceAtLeast(0f)` di PDF **benar** sebagai jaring pengaman — lebih baik teks tergambar miring daripada hilang.
- Tapi ia **salah sebagai satu-satunya reaksi**, karena ia membuat masalahnya tak terlihat sampai dicetak. Penanda di kanvas memindahkan penemuannya ke saat elemen masih bisa diperbaiki.
- **Ketebalan outline tetap sama, warnanya yang beda** — ini Kontrak 8 di [`design-system-rules.md`](../../.claude/rules/design-system-rules.md). Kalau state dibedakan lewat ketebalan, kartu yang meluber jadi "menggemuk" dan tata letaknya bergeser.
- `ItemTable` sengaja mengembalikan `null` dari `textStyleOrNull` walau ia menggambar teks: ia punya **dua** gaya dan ratusan sel dengan lebar berbeda. Tidak ada satu gaya yang mewakilinya.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan Kita | Alternatif | Mengapa Kita Memilih Ini | Risiko Alternatif |
|---|---|---|---|
| **Tabel advance width di-*check in* + test drift** | Codegen Gradle saat build | `core` jalan di JS/Wasm yang tak bisa baca TTF; codegen lintas 5 target untuk 380 angka statis tidak sepadan | Plugin build baru yang harus dipelihara, waktu build naik, dan tetap tidak bisa dipakai di target non-JVM |
| | Kalibrasi ulang konstanta taksiran + margin 3% | Cuma menunda: tetap ada selisih, dan tiap ganti font harus dikalibrasi ulang manual tanpa ada yang tahu kapan perlunya | Meleset lagi diam-diam pada font berikutnya |
| **Keputusan wrap dilakukan sekali di domain** | Biarkan tiap mesin wrap dengan metriknya sendiri | Titik potong identik **secara konstruksi**, bukan karena dua pengukur kebetulan sepakat | 3 baris di layar, 4 baris di kertas — dan tidak ada test yang bisa menangkapnya |
| **Aturan pemilihan font di domain** | Biarkan di renderer PDF | Pemecah baris harus tahu font mana yang diukur; kanvas harus tahu font mana yang digambar | Aturan yang sama ditulis dua kali lalu menyimpang pelan-pelan |
| **`staticCompositionLocalOf` dengan bawaan `error()`** | Parameter diteruskan 4 tingkat | Tidak mengotori fungsi perantara yang tak peduli soal font | Bising, dan tiap fungsi baru harus ikut meneruskannya |
| | `CompositionLocal` dengan bawaan `FontFamily.Default` | Bawaan senyap = bug yang sedang kita perbaiki, dalam bentuk baru | Lupa pasang penyedia → faktur salah cetak, bukan crash |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

### Jebakan 1: Mengira `Text()` tanpa `fontFamily` akan memakai font tema

*Kenapa bahaya*: Di Material 3, `MaterialTheme` **tidak** menyediakan `LocalTextStyle` secara global. Yang menyediakannya adalah komponen tertentu (`Button`, `ListItem`) lewat `ProvideTextStyle`. `Text` telanjang di dalam `Box` memakai `TextStyle.Default` → font sistem. Kamu memasang Nunito dengan benar, tema Material-mu benar, dan teksnya tetap keluar Roboto — tanpa satu pun peringatan.

*Solusi elegan kita*: `fontFamily` eksplisit di setiap teks yang paritasnya penting, dibagikan lewat `CompositionLocal` agar tidak terlupa.

### Jebakan 2: Mengira selisih beberapa persen itu "cuma kosmetik"

*Kenapa bahaya*: Selisih 6.7% memang kecil — **sampai** sebuah string mengisi ≥94% lebar kotaknya. Di titik itu, selisih kecil berubah jadi perubahan perilaku: rata kanan hilang total. Kesalahan kecil yang melewati sebuah ambang akan berhenti berperilaku kecil.

*Solusi elegan kita*: Pakai angka aslinya. Kalau tidak bisa, **pastikan salahnya ke arah yang aman** dan tulis alasannya di komentar.

### Jebakan 3: Menulis test yang cuma memeriksa dirinya sendiri

*Kenapa bahaya*: Versi pertama test regresi saya begini:

```kotlin
lines.forEach { line ->
    assertTrue(InvoiceTextLayout.measureWidthMm10(line, body) <= boxMm10)
}
```

Kelihatan meyakinkan. Tapi pengukur dan pemecah baris memakai **tabel yang sama**, jadi mereka akan selalu sepakat — termasuk sepakat saat sama-sama salah. Test ini akan hijau dengan taksiran lama maupun tabel baru. Ia tidak membuktikan apa pun.

*Solusi elegan kita*: Tegaskan **hasil yang bisa diamati dari luar**, bukan konsistensi internal:

```kotlin
assertEquals(listOf("Rp", "12.500.000"), lines)   // dulu 1 baris, sekarang 2
```

> 💡 Sebelum menulis assertion, tanya: *"apakah test ini akan MERAH pada kode yang lama?"* Kalau tidak, itu bukan test regresi.

### Jebakan 4: Membenahi terlalu banyak hal sekaligus

*Kenapa bahaya*: Ambang 14pt itu kasar. `isItalic` tidak berfungsi. Sel tabel tidak di-wrap. Semuanya menggoda untuk dibereskan sekalian. Tapi kalau tampilan faktur berubah, kamu tidak akan tahu perubahan mana penyebabnya.

*Solusi elegan kita*: Pindahkan dulu tanpa mengubah perilaku, verifikasi, baru perbaiki. Sisa temuan dicatat, bukan dikerjakan diam-diam.

### Jebakan 5: Menganggap "kompilasi hijau" = "selesai"

*Kenapa bahaya*: Semua 490 test `core` hijau **sebelum** saya pernah melihat kanvasnya. Perubahan font adalah perubahan visual murni; tidak ada assertion yang bisa menangkap "fontnya jelek" atau "teksnya terpotong kotak".

*Solusi elegan kita*: Jalankan aplikasinya. Render PDF-nya ke gambar dan lihat. Kalau layarnya sulit dicapai, buat perancah sementara yang menampilkan komponennya langsung — **lalu hapus perancahnya**.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Empat lapis, masing-masing menangkap kelas kesalahan yang berbeda.

### Lapis 1 — Unit test domain (`core:jvmTest`), murni tanpa I/O

```kotlin
@Test
fun `digit width comes from the font, not from a character class`() {
    val expected = (6.0 * 10 * InvoiceTextLayout.MM10_PER_PT).toInt()  // 10 digit × 0.600 em
    val actual = InvoiceTextLayout.measureWidthMm10("0123456789", body)
    assertTrue(actual in (expected - 1)..(expected + 1))
}
```

Angka `6.0` dihitung tangan dari spesifikasi Nunito (digit tabular 600/1000), bukan disalin dari keluaran kode. **Test yang mengambil nilai harapannya dari kode yang diuji tidak menguji apa pun.**

### Lapis 2 — Test drift terhadap mesin gambar sungguhan (`server:test`)

Menutup celah "tabel yang di-*check in* bisa membusuk". Ini satu-satunya alasan tabel itu boleh ada.

### Lapis 3 — Kompilasi lima target

```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
          :core:compileKotlinIosArm64
```

`core` adalah `commonMain`. Kode yang jalan di JVM belum tentu jalan di Wasm.

### Lapis 4 — Mata

Test tidak bisa bilang "fontnya jelek". Yang saya lakukan:

1. **PDF**: perancah sementara yang menulis PDF sampel ke berkas, dikonversi ke PNG, dilihat. Terbukti Fredoka di judul, Nunito di isi, kolom uang tetap rata kanan.
2. **Kanvas**: perancah sementara di `desktopApp/main.kt` yang menampilkan `TemplateCanvas` langsung dengan template standar — melewati login dan navigasi yang butuh backend hidup. Dijalankan di zoom 200% supaya jelas teksnya tidak terpotong kotak elemen.
3. **Kedua perancah dihapus**, `main.kt` dikembalikan. Ini bagian yang paling sering dilupakan.

**Catatan jujur soal hasil test**: 47 test integrasi `server` gagal dengan `HikariPool$PoolInitializationException` — tidak ada Postgres berjalan di mesin ini. Itu kegagalan lingkungan yang sudah ada sebelum perubahan ini, bukan regresi. Membedakan "gagal karena kodeku" dan "gagal karena mesinku" adalah keahlian tersendiri: **kelompokkan kegagalan berdasarkan exception-nya** sebelum panik.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1 — Sel tabel belum di-wrap.**
  `drawItemTable` memanggil `drawText(lines = listOf(cellText))` untuk setiap sel: satu baris, tanpa `InvoiceTextLayout.wrap()`. Nama produk panjang akan meluber melewati batas kolomnya. Kanvas memakai `maxLines = 1` sehingga terpotong elipsis — **jadi kanvas dan PDF berbeda perilaku di sini.** Perbaiki, lalu pikirkan: kalau sel bisa jadi 2 baris, bagaimana `rowHeight` menyesuaikan? Bagaimana `requiredTableHeightMm10` ikut berubah?

- [ ] **Tantangan 2 — Apa yang terjadi kalau barisnya banyak sekali?**
  Renderer memanggil `doc.addPage(page)` satu kali. Bikin faktur dengan 60 baris dan render PDF-nya. Ke mana perginya baris ke-40? Apa yang terjadi pada elemen ber-`anchorBelowTable`? Rancang paginasinya — perhatikan bahwa "header diulang di tiap halaman" mengubah `requiredTableHeightMm10` secara mendasar.

- [ ] **Tantangan 3 — `isItalic` berbohong.**
  `TextStyleSpec.isItalic` ada di model, bisa disimpan, tapi tak ada satu pun berkas TTF miring yang dipaketkan; kedua renderer mengabaikannya. Pilih satu: bundel Nunito Italic dan dukung beneran, atau buang medannya dari model. Argumentasikan pilihanmu — **medan yang berbohong lebih buruk daripada medan yang tidak ada.**

- [ ] **Tantangan 4 — Kerning.**
  `PDFont.getStringWidth` menjumlahkan advance tanpa kerning; Skia menerapkan kerning GPOS. Ukur seberapa besar selisihnya untuk string faktur tipikal. Apakah cukup besar untuk diurus? Kalau iya, sisi mana yang harus menyesuaikan — dan kenapa jawabannya **bukan** "bikin kedua sisi pakai kerning"?

---

## 📎 Berkas yang Disentuh

| Berkas | Perubahan |
|---|---|
| `core/…/template/InvoiceFont.kt` | **Baru** — `InvoiceFont` + `InvoiceFontResolver` |
| `core/…/template/InvoiceFontMetrics.kt` | **Baru** — tabel advance width 4 font × 95 ASCII + 14 ekstra |
| `core/…/template/InvoiceTextLayout.kt` | `advanceEm` per kelas karakter → `InvoiceFontMetrics`; `estimateWidthMm10` → `measureWidthMm10` |
| `core/…/template/InvoiceDocumentLayout.kt` | `LaidOutElement.hasOverflow` |
| `core/…/template/TemplateElement.kt` | `textStyleOrNull` |
| `server/…/pdf/InvoicePdfRenderer.kt` | `pickFont` privat → `LoadedFonts.forStyle` lewat resolver domain |
| `app/shared/…/template/TemplateCanvas.kt` | `fontFamily`/`fontWeight` di semua teks; penanda luapan |
| `core/…/InvoiceTextLayoutTest.kt` | +5 test (regresi luapan, lebar digit, ambang Fredoka, fallback) |
| `server/…/InvoiceFontMetricsDriftTest.kt` | **Baru** — 4 test drift terhadap PDFBox |
