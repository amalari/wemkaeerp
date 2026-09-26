# 🎓 Modul Pembelajaran: Merombak Desainer Template Faktur — Satu Mesin Tata Letak, Dua Kanvas

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Pure Domain Layer (KMP), Deterministic Text Layout, WYSIWYG
> Canvas ⇄ PDF, Compose Multiplatform State Management, Derived State vs Editable State,
> Cache/Build Staleness, Verifikasi UI di Compose Wasm
> **Prasyarat**: Dasar Kotlin, dasar Compose (`@Composable`, `mutableStateOf`),
> pemahaman dasar DDD (Entity/Value Object/Use Case), dasar PDFBox
> **Referensi Task**: Revamp desainer `/invoicing` (palet kiri + inspektur tunggal +
> mesin tata letak bersama)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata: dua "otak" yang menggambar dokumen yang sama

Bayangkan sebuah perusahaan percetakan. Ada **dua orang** yang bekerja dari satu berkas desain:
satu duduk di depan layar (menyusun pratinjau), satu lagi mengoperasikan mesin cetak. Kalau
keduanya menafsirkan "desain" itu dengan **selera masing-masing** — yang satu memutus baris
kalimat di huruf ke-40, yang lain di huruf ke-38 — maka hasil cetak tidak akan pernah sama
dengan pratinjaunya. Pelanggan melihat satu hal di layar, lalu menerima hal lain di kertas.

Persis itu yang terjadi pada desainer template faktur versi sebelumnya:

- **Compose** (kanvas web) menyusun baris teks dengan mesin teksnya sendiri (Skia).
- **PDFBox** (server, saat mencetak PDF) menyusun baris dengan mesinnya sendiri.

Keduanya benar secara teknis. Tapi begitu teksnya panjang dan harus dibungkus ke beberapa baris,
keduanya **memutus di tempat yang berbeda**. Akibatnya kotak-kotak elemen di bawahnya bergeser —
dan elemen yang paling menderita adalah yang **posisinya ditambatkan di bawah tabel**
(`anchorBelowTable`), karena posisi mereka bergantung pada tinggi tabel, yang bergantung pada
jumlah baris teks.

### Analogi Sederhana

Pikirkan **notasi musik**. Kalau setiap pemain biola menafsirkan "tempo sedang" dengan
kecepatannya sendiri, orkestranya berantakan. Solusinya bukan menyuruh mereka "bermain lebih
kompak", tapi **satu lembar partitur** yang dibaca semua orang. Di task ini, partitur itu bernama
`InvoiceDocumentLayout.solve()` — modul murni di `core/` yang menyelesaikan seluruh tata letak
**sekali**, lalu hasilnya dibaca apa adanya oleh Compose **dan** PDFBox.

### Hasil Akhir yang Diharapkan

```
                    ┌─────────────────────────────┐
                    │  core/ (PURE KOTLIN)        │
                    │  InvoiceTextLayout          │  ← memutus baris + mengukur
                    │  InvoiceDocumentLayout      │  ← menyelesaikan posisi & tinggi
                    │    solve(template, invoice) │
                    └──────────┬──────────────────┘
                               │  List<LaidOutElement> (teks sudah terpotong per baris)
                 ┌─────────────┴─────────────┐
                 ▼                           ▼
        ┌──────────────────┐        ┌────────────────────┐
        │ Compose Canvas   │        │ PDFBox Renderer    │
        │ menggambar       │        │ menggambar         │
        │ apa adanya       │        │ apa adanya         │
        └──────────────────┘        └────────────────────┘
```

**Aturan emasnya**: kanvas dan PDF **tidak boleh** punya logika pembungkusan baris sendiri.
Mereka hanya menggambar `LaidOutElement.textLines` kata per kata.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Ini bagian yang paling sering salah. Junior developer biasanya mulai dari **layar** (karena
itu yang terlihat), lalu berjuang memaksa logika masuk ke dalam composable. Urutan yang benar
justru dari **dalam ke luar**:

### Step 0 — Tentukan "kontrak data" lebih dulu

Sebelum menulis satu baris kode, jawab: *bentuk data seperti apa yang membuat kedua kanvas
bahagia?* Jawabannya adalah `LaidOutElement`: setiap elemen, ditambah **daftar baris teks yang
sudah final** + tinggi hasil hitungan. Kalau kontraknya belum jelas, seluruh lapisan di atasnya
akan terus berubah.

### Step 1 — `core/domain/invoicing/template/InvoiceTextLayout.kt`

Mesin pengukur teks. Input: sebuah kalimat + lebar kotak + ukuran font. Output: `List<String>`
(baris-baris final). **Ini harus selesai dan teruji sebelum apa pun yang lain.**

### Step 2 — `InvoiceDocumentLayout.kt`

Pemanggil mesin di atas: untuk setiap elemen, hitung tinggi → lalu susun posisi (khususnya
elemen `anchorBelowTable` yang menunggu tinggi tabel).

### Step 3 — Tes Core (`InvoiceTextLayoutTest`, `InvoiceDocumentLayoutTest`)

Di lapisan murni, tes berjalan dalam milidetik dan **tidak butuh browser maupun server**. Di
sinilah Anda mengunci perilaku: "kalimat panjang → 3 baris", "tabel membesar → elemen di
bawahnya turun".

### Step 4 — Konsumen **kedua** dulu: `InvoicePdfRenderer.kt` (server)

Dengan sengaja mengerjakan **konsumen yang lebih kaku lebih dulu** (PDFBox, karena ia tidak
punya API teks kaya), Anda memaksa kontrak `LaidOutElement` menjadi sederhana. Kalau PDFBox
bisa, Compose pasti bisa. Urutan sebaliknya biasanya melahirkan kontrak yang "manja".

### Step 5 — Konsumen pertama: `TemplateCanvas.kt` (Compose)

Baru sekarang menggambar di layar. Bagian ini menjadi **tipis**, karena semua keputusan sulit
sudah diambil di Step 1–2.

### Step 6 — Baru lapisan presentasi di sekelilingnya

`ElementPalette.kt` (kiri), `DesignerPropertyInspector.kt` (kanan), `DesignerToolbar.kt`,
`TemplateDesignerUiState.kt` + `TemplateDesignerViewModel.kt` (state), dan terakhir
`InvoiceTemplateDesignerScreen.kt` (perekat).

### Step 7 — Verifikasi berlapis

Tes Core → tes ViewModel → tes server → **jalankan di browser dan lihat dengan mata**.

> **Mental model yang perlu dipegang**: perubahan pada Step 1 akan memaksa seluruh lapisan
> di atasnya ikut berubah. Itulah kenapa Step 1 harus benar **dulu**, bukan "nanti diperbaiki
> saat sudah kelihatan bagus di layar".

---
## 🔬 3. Bedah Kode Blok per Blok

### 3.1 `InvoiceTextLayout.wrap()` — satu-satunya pemutus baris

```kotlin
fun wrap(text: String, widthMm10: Int, style: TextStyleSpec): List<String> {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    if (normalized.isEmpty()) return listOf("")

    val maxEm = maxEmFor(widthMm10, style)
    val lines = mutableListOf<String>()
    normalized.split('\n').forEach { segment -> lines += wrapSegment(segment, maxEm) }
    return lines.ifEmpty { listOf("") }
}
```

**Mental model**: `wrap` sengaja **tidak** tahu apa-apa soal Compose, PDFBox, atau piksel. Ia
hanya tahu dua hal: teks, dan **berapa banyak `em` yang muat** pada lebar itu.

- **Baris 1 — normalisasi.** `\r\n` (Windows) dan `\r` (Mac klasik) diseragamkan jadi `\n`. Kalau
  ini dilewat, teks yang ditempel dari Excel/Notepad akan menghasilkan baris hantu. Normalisasi di
  **satu tempat** seperti ini adalah alasan utama mesin ini harus ada di domain, bukan di UI — UI
  punya tiga target platform dengan tiga kebiasaan newline.
- **Baris 2 — teks kosong tetap satu baris.** Ini bukan detail sepele. Kalau `wrap("")`
  mengembalikan `listOf()` (nol baris), maka tinggi elemen = 0, dan elemen berukuran nol **tidak
  bisa diklik** di kanvas. Pengguna akan melaporkan "elemen saya hilang" padahal elemennya ada.
- **`maxEmFor` — mengubah milimeter menjadi satuan relatif.** Inilah inti daya tahan mesin ini.
  Lebar teks diukur dalam **`em`** (satuan yang bergantung pada ukuran font), bukan milimeter
  absolut:

  ```
  em_maksimum = lebar_tersedia_mm10 / (fontSizePt × MM10_PER_PT)
  ```

  Dengan membagi lebar dengan ukuran font, seluruh kalkulasi menjadi **bebas font**. Ganti `10pt`
  jadi `18pt` dan `maxEm` otomatis menyusut — tidak ada satu pun angka yang perlu diubah.

### 3.2 `wrapSegment()` — inti algoritma, dan jaminan kemajuan

```kotlin
segment.trim().split(' ').filter { it.isNotEmpty() }.forEach { word ->
    var remaining = word
    while (remaining.isNotEmpty()) {
        val spaceEm = if (current.isEmpty()) 0.0 else SPACE_EM
        val remainingEm = remaining.sumOf { advanceEm(it) }

        if (currentEm + spaceEm + remainingEm <= maxEm) {
            if (spaceEm > 0.0) current.append(' ')
            current.append(remaining)
            currentEm += spaceEm + remainingEm
            remaining = ""
            continue
        }
        ...
    }
}
```

**Mental model**: ini algoritma greedy word-wrap klasik (mirip yang dipakai browser): *masukkan
kata sebanyak mungkin ke baris sekarang; kalau tidak muat, tutup baris dan mulai baris baru.*

Yang membedakannya dari implementasi naif ada di blok `if (current.isEmpty())`:

```kotlin
if (current.isEmpty()) {
    val head = StringBuilder()
    var headEm = 0.0
    for (ch in remaining) {
        val chEm = advanceEm(ch)
        if (headEm + chEm > maxEm) break
        head.append(ch)
        headEm += chEm
    }
    // Jaminan kemajuan: minimal satu karakter per baris, walau tipenya sangat besar.
    if (head.isEmpty()) head.append(remaining.first())
    lines += head.toString()
    remaining = remaining.substring(head.length)
}
```

Dua hal penting di sini:

1. **Kata yang lebih panjang dari satu baris dipotong per karakter.** Kasus nyatanya ada di
   template ini sendiri: nomor faktur `INV/2026/03/0001` dan nomor rekening `8420-123-999`.
   Tanpa cabang ini, kata tersebut akan **meluber keluar kotak** dan menimpa elemen sebelahnya.
2. **`if (head.isEmpty()) head.append(remaining.first())` — jaminan kemajuan.** Kalau fontnya
   sangat besar sehingga bahkan **satu karakter** lebih lebar dari kotaknya, `head` akan kosong
   dan `remaining.substring(0)` tidak memotong apa pun → **loop tak terbatas**, dan aplikasi hang.
   Satu baris kecil ini mencegahnya.

> **Jebakan umum**: setiap `while` yang memotong string **wajib** punya jaminan kemajuan seperti
> ini. Tanpa itu, ada satu kombinasi input yang membuat aplikasi hang — dan kombinasi itu selalu
> ketemu di tangan pengguna, bukan di test.


### 3.3 Tabel `advanceEm()` — mengapa perkiraan justru pilihan yang benar

```kotlin
private fun advanceEm(char: Char): Double = when {
    char == ' ' -> SPACE_EM
    char == '\t' -> SPACE_EM * 4
    char in ".,:;'’`!|" -> 0.28
    char in "ijltfrI()[]{}/\\-" -> 0.34
    char.isDigit() -> 0.56
    char in "MWmw@%&" -> 0.90
    char.isUpperCase() -> 0.68
    else -> 0.52
}
```

Ini **bukan** metrik font. Ini perkiraan lebar dalam `em`, dikelompokkan berdasarkan **kelas
karakter**. Terlihat "kurang teliti", dan memang begitu — tetapi trade-off-nya sangat
menguntungkan:

| Pendekatan | Konsekuensi |
|---|---|
| Tabel per-glyph (metrik asli Nunito) | Ratusan angka harus dipelihara; **setiap kali font diganti, seluruh tabel dihitung ulang**; dan tetap tidak menyelesaikan masalah sebenarnya |
| Perkiraan per kelas (`advanceEm`) | 8 baris, stabil, tidak bergantung font |

Tapi tunggu — bukankah ini memperburuk WYSIWYG? **Tidak**, dan ini poin yang paling perlu
dipahami:

> Pemecah baris **tidak butuh lebar absolut yang presisi**. Ia hanya butuh **urutan relatif
> lebar** yang benar: `m` harus lebih lebar daripada `i`, dan digit berada di antaranya. Selama
> urutan itu benar, urutan kata yang muat dalam satu baris akan benar.

Yang benar-benar menjamin WYSIWYG bukan tabel ini — melainkan **kenyataan bahwa kanvas dan PDF
memakai daftar baris ini apa adanya**. Keduanya tidak diberi kesempatan untuk wrap sendiri.
Perkiraan yang "kurang presisi" jadi tidak relevan, karena **tidak ada pembanding kedua**. Sisa
kesalahannya hanya kosmetik: jarak di kanan baris bisa berbeda beberapa persen dari batas kotak.

### 3.4 `InvoiceDocumentLayout.solve()` — menegakkan aturan dinamis

```kotlin
fun solve(
    template: InvoiceTemplate,
    invoice: Invoice,
    paidAmount: Money = Money.zero(invoice.currency)
): List<LaidOutElement> {
    val delta = tableDeltaMm10(template, invoice)
    return template.elements
        .sortedBy { it.zOrder }
        .map { element -> layOut(element, template, invoice, paidAmount, delta) }
}
```

Empat **aturan** yang ditegakkan `solve()` — bacalah `KDoc`-nya, karena inilah kontrak yang
paling mudah dilanggar tanpa sadar:

**Aturan 1 — tinggi teks diturunkan, bukan diketik.**
Pengguna hanya boleh mengubah **lebar**. Tinggi dihitung dari jumlah baris, sehingga isian
"Tinggi" di inspektur bersifat *read-only* ("Tinggi (otomatis)").

**Aturan 2 — tabel item tidak pernah menyusut, hanya tumbuh.**

```kotlin
fun tableDeltaMm10(template: InvoiceTemplate, invoice: Invoice): Int {
    val table = template.itemTable ?: return 0
    val required = requiredTableHeightMm10(table, invoice.lines.size)
    return (required - table.rect.height.value).coerceAtLeast(0)
}
```

Perhatikan `.coerceAtLeast(0)`. Kalau tanda ini dihilangkan, faktur dengan **sedikit** baris akan
membuat `required < table.rect.height` → `delta` negatif → tabel menyusut, dan seluruh elemen di
bawahnya **terangkat naik**. Template yang dirancang untuk 5 baris akan tampak "rusak" saat
faktur kebetulan hanya berisi 1 baris.

**Aturan 3 — `anchorBelowTable` digeser dari posisi tersimpan, bukan dari posisi hasil geser.**
Ini jebakan paling halus. Bayangkan penulisan yang keliru: hasil `solve()` (yang sudah digeser)
ditulis **kembali** ke model. Setiap kali teks diubah → hitung ulang → geser lagi → tulis lagi.
Elemen ber-anchor akan **merayap turun terus** pada setiap penekanan tombol. Karena itu
`measureHeights()` hanya menulis `height`, tidak pernah `y`.

**Aturan 4 — elemen tidak pernah keluar kertas**, termasuk setelah pergeseran dinamis
(`TemplateElementPlacementTest`).

### 3.5 Sisi konsumen: `InvoicePdfRenderer`

```kotlin
private fun drawText(lines: List<String>, ...) {
    lines.forEachIndexed { index, line ->
        beginText()
        setFont(...)          // PDFBox 3: setFont WAJIB di dalam beginText
        showText(line)
        endText()
        // perataan dihitung per baris, bukan sekali untuk seluruh blok
    }
}
```

Tiga perubahan penting dari versi lama:

1. **Parameter `String` → `List<String>`.** Renderer berhenti mengambil keputusan wrap; ia
   **menerima hasil keputusan itu**. Kalau Anda tergoda menambahkan `text.chunked(60)` di sini —
   jangan. Itu menghidupkan kembali bug yang baru saja dihapus.
2. **`setFont` dipindah ke dalam `beginText`.** Ini persyaratan API PDFBox 3: font harus
   ditetapkan setelah text object dibuka, kalau tidak barisnya keluar dengan font default.
3. **Perataan per baris**, bukan per blok. Teks rata-tengah 3 baris dengan perataan per blok akan
   membuat baris pendek menempel ke kiri.

---

## 🧠 4. Technology & Approach — "The Why"

### 4.1 Mengapa mesin tata letak ditaruh di `core/` (Domain), bukan di `shared/`

Menurut aturan DDD proyek ini (`AGENTS.md` §2), **Domain tidak boleh bergantung pada layer mana
pun**. Mesin ini sama sekali tidak menyebut Compose, Ktor, atau PDFBox. Itu bukan kemewahan
arsitektural — itu **prasyarat teknis**:

| Kalau di `shared/` (Compose) | Kalau di `core/` (Domain) |
|---|---|
| Server tidak bisa memakainya → PDFBox wrap sendiri → WYSIWYG hilang | Server & klien memakai fungsi yang sama |
| Tes butuh Compose runtime | Tes murni, jalan di JVM dalam milidetik |
| Tidak bisa diuji di `:core:jvmTest` | Bisa |

**Risiko kalau mengambil jalan pintas**: menaruh `wrap()` di `TemplateCanvas.kt` akan
"kelihatan benar" hari ini, karena faktur contoh punya teks pendek. Bugnya baru muncul bulan
depan, saat pelanggan menulis catatan panjang — dan saat itu terjadi, tidak ada satu pun tes yang
bisa menangkapnya, karena bugnya **definisi** adalah "dua keluaran yang berbeda".

### 4.2 Mengapa elemen disisipkan dari **palet kiri**, bukan dari tombol di toolbar

Versi lama punya tombol `+ Teks`, `+ Garis`, `+ Kotak`, `+ Tabel`, `AI Auto-Map` di toolbar.
Masalahnya bukan "terlalu banyak tombol", tapi **dua hal yang berbeda berdesakan di satu tempat**:

- *Menambah* elemen = mengubah isi kertas.
- *Mengatur* elemen (zoom, alat, simpan) = mengubah cara Anda melihat/menyimpan kertas.

Menggabungkannya membuat toolbar bertambah panjang **setiap kali ada jenis elemen baru** — dan
jenis elemen akan terus bertambah. Setelah dipisah:

- **Kiri** = perpustakaan bahan (*apa yang bisa saya tambahkan*), digrup per modul asal data.
- **Tengah** = kanvas.
- **Kanan** = properti elemen terpilih (*bagaimana bentuknya*).
- **Atas** = alat tampilan & simpan. Ukurannya sekarang **konstan**.

### 4.3 Mengapa default elemen diambil dari `InvoiceBindingRegistry`, bukan ditulis di palet

Setiap tombol di palet dan setiap isian di inspektur membaca **deskriptor yang sama**:
`BindingDescriptor` (`defaultFontSizePt`, `defaultWidthMm10`, `defaultPrefix`, `defaultSuffix`,
`moduleSource`). Konsekuensinya penting: **palet dan inspektur tidak bisa berbeda pendapat**.

Kalau angka default ditulis ulang di UI, cepat atau lambat palet akan menyisipkan teks 12pt
sementara inspektur menampilkan 10pt — dan setiap kali registry berubah, ada dua tempat yang
harus diingat. Ini penerapan aturan "satu sumber kebenaran" pada level data, bukan level kode.

Perhatikan juga `standaloneTokens` / `standaloneModules()`: token berlingkup baris (`line.*`)
hanya bermakna **di dalam** tabel item, jadi ia **dikecualikan dari palet**. Menampilkannya akan
mengundang pengguna menyisipkan "Harga Satuan" sebagai teks lepas di luar tabel — dan nilainya
akan kosong, karena tidak ada `line` untuk dibaca.

### 4.4 Mengapa **hanya lebar** yang bisa diubah, dan tinggi diturunkan

Bayangkan tinggi bisa diketik manual. Pengguna mengetik "40 mm" pada kotak yang isinya 3 baris
(butuh 18 mm). Apa yang harus terjadi?

- Teks dipotong (data faktur hilang di cetakan)? **Tidak boleh.**
- Teks meluber keluar kotak menimpa elemen lain? **Tidak boleh.**

Tidak ada jawaban yang benar, karena pertanyaannya salah. Solusinya: **hilangkan pertanyaannya**.
Tinggi adalah **derived state** — turunan dari isi dan lebar. Karena itu:

1. Kanvas hanya menyediakan gagang ubah **lebar** (bukan sudut kanan-bawah).
2. `TemplateRect.resizedWidth()` menjepit lebar ke dalam kertas di lapisan domain.
3. Isian "Tinggi" di inspektur menjadi *read-only*.

**Keuntungan kedua**: menggeser sudut bawah untuk mengubah tinggi hanya akan "dilawan" oleh
perhitungan ulang tinggi di frame berikutnya. Elemen akan tampak memantul balik — terasa seperti
kanvas rusak, padahal logikanya benar.

### 4.5 Mengapa `viewportSize` di-hoist ke atas `BoxWithConstraints` yang bergulir

```kotlin
BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
    val viewport = DpSize(maxWidth, maxHeight)   // ← di LUAR container bergulir
    ...
    TemplateCanvas(..., viewportSize = viewport)
}
```

```kotlin
// Di dalam TemplateCanvas:
val contentWidth = maxOf(paperWidthDp + ClayOffset.Rest, viewportSize.width)
val contentHeight = maxOf(paperHeightDp + ClayOffset.Rest, viewportSize.height)
```

**Mental model**: `Modifier.verticalScroll`/`horizontalScroll` memberi anaknya constraint
**tak hingga** (`Constraints.Infinity`) pada sumbu gulirnya. Kalau `BoxWithConstraints` diletakkan
**di dalam** area bergulir, `maxWidth`/`maxHeight`-nya menjadi `Infinity` — dan
`Modifier.size(Infinity)` bukan cara memusatkan kertas, itu cara membuat crash atau layout aneh.

Solusinya: ukur **wadah bergulirnya** (dari luar), lalu kirim ukurannya ke dalam sebagai data
biasa. Tekniknya sederhana: *kalau sesuatu perlu tahu "seberapa besar ruang yang tersedia",
ukurlah di tempat yang masih punya batas.*

### 4.6 Mengapa zoom "Muat Layar" dihitung di dalam area kanvas

```kotlin
LaunchedEffect(viewport, state.template.paperSize) {
    fitZoomPercent = fitZoomFor(viewport.width, viewport.height, state.template.paperSize)
}
```

Zoom yang tepat bergantung pada **lebar sisa setelah panel kiri masuk** — informasi yang hanya
dimiliki area kanvas. Menghitungnya dari luar (misalnya dari lebar layar dikurangi konstanta)
akan salah begitu ukuran panel berubah.

Kompromi yang diterima: pada frame pertama nilai ini masih `null`, jadi tombolnya **belum
muncul** (`if (fitZoomPercent != null)`). Itu disengaja: lebih baik tombol muncul 1 frame lebih
lambat daripada memunculkan tombol yang memakai angka tebakan.

### 4.7 Mengapa tombol alat dipisah jadi **Kursor** dan **Geser Kanvas**

Kanvas A4 selalu penuh elemen. Pada satu mode "pintar", setiap tarikan di atas elemen berarti
"pindahkan elemen", sehingga **tidak ada lagi titik di kanvas yang bisa dipakai menggeser
tampilan**. Mode `PAN` mematikan seluruh handler elemen sehingga tarikan di titik mana pun
menggeser viewport.

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

### Jebakan 1 — `NoClassDefFoundError: com/eventverse/app/shared/common/DateTimeCodec`

**Gejala.** Menekan "Simpan Template" memunculkan banner merah:
`Gagal menyimpan template (HTTP 500): com/eventverse/app/shared/common/DateTimeCodec`.
Yang membuat bingung: kelas itu **ada** di kode, **ada** di jar, dan tesnya hijau.

**Cara berpikir yang salah.** Langsung mencari bug di `InvoiceTemplateCodec` atau di
`DateTimeCodec`. Tidak ada bug di sana. Uji cepat yang membuktikannya:

```bash
# Kelasnya memang ada di dalam jar:
unzip -l core/build/libs/core-jvm.jar | grep -c DateTimeCodec     # → 1
```

**Cara berpikir yang benar: tanyakan "proses mana yang sedang melayani saya, dan sejak kapan?"**

```bash
ps -p <PID> -o pid,lstart,command      # kapan proses server MULAI
ls -la core/build/libs/core-jvm.jar    # kapan jar-nya TERAKHIR ditulis
```

Hasil nyatanya di lingkungan ini:

| Bukti | Nilai |
|---|---|
| Proses Ktor mulai | `Tue Sep 15 23:15:36 2026` |
| `core-jvm.jar` terakhir dibangun | `Sep 16 01:03` (≈ 2 jam **setelah** server hidup) |
| Classpath proses | `... -cp server/build/classes/...:core/build/libs/core-jvm.jar ...` |

Jadi: **server sudah hidup sebelum jar-nya diganti.** JVM memegang handle ke jar itu dan sudah
memuat sebagian isinya; kelas yang baru ditambahkan (`DateTimeCodec`) tidak pernah ada di versi
yang sedang dipegang proses. Hasilnya `NoClassDefFoundError` — pesan errornya berupa **nama
internal berslash**, bukan nama bertitik, dan itu tanda khas error **saat linking**, bukan saat
`Class.forName`.

**Pelajaran:**

- `./gradlew :server:run` **tidak** memuat ulang kelas yang sudah berjalan. Ini bukan
  hot-reload. Setiap kali Anda mengubah `core/`, server harus dijalankan ulang.
- Perbedaan waktu antara "proses mulai" dan "artefak dibangun" adalah **petunjuk pertama** yang
  harus dicek saat melihat error janggal yang "mustahil".
- Karena `NoClassDefFoundError` untuk kelas yang benar-benar ada, curigai **classpath basi**
  sebelum curigai kode.

```bash
# Perbaikan: hentikan proses lama, lalu jalankan ulang
kill <PID_LAMA>
set -a && . ./.env && set +a && nohup ./gradlew :server:run > /tmp/server.log 2>&1 &
```

**Verifikasi**: setelah restart, banner hijau *"Template 'Template Faktur Standar Indonesia'
berhasil disimpan!"* muncul, dan log server bersih dari `NoClassDefFoundError`.

### Jebakan 2 — Banner pesan lama tidak dibersihkan (menampilkan "gagal" **dan** "berhasil" sekaligus)

**Gejala.** Screenshot verifikasi menunjukkan dua banner sekaligus: banner merah kuno di atas,
banner hijau "*berhasil disimpan!*" di bawahnya. Pengguna tidak punya cara tahu mana yang masih
berlaku.

**Akar masalahnya bukan di UI, tapi di urutan penulisan state:**

```kotlin
// SEBELUM
private fun saveTemplate() {
    scope.launch {
        _uiState.update { it.copy(isSaving = true) }        // ← `error` lama TIDAK dibersihkan
        ...
        .onSuccess { saved ->
            _uiState.update { it.copy(isSaving = false, successMessage = "...") }
        }
    }
}
```

Banner hanya punya dua jalan keluar: ditimpa oleh pesan jenis yang sama, atau ditutup manual.
Karena itu kesalahan lama bertahan melewati operasi baru.

**Perbaikannya adalah satu kebiasaan yang berlaku umum:**

> **Bersihkan pesan hasil di AWAL operasi, bukan hanya menimpanya di AKHIR.**

```kotlin
// SESUDAH
_uiState.update { it.copy(isSaving = true, error = null, successMessage = null) }
```

Terapkan juga di jalur gagal (`successMessage = null`) supaya dua jenis pesan tidak pernah hidup
bersamaan. Tes regresinya ada di
`TemplateDesignerViewModelTest.saveTemplate_afterAPreviousFailure_clearsTheStaleErrorMessage` —
ia **sengaja gagal dulu, lalu berhasil**, karena tanpa langkah pertama tesnya tidak akan
menangkap apa pun.

**Pelajaran**: bug jenis ini lolos dari semua tes "happy path". Yang menangkapnya adalah
**melihat layarnya dengan mata** setelah serangkaian tindakan — bukan setelah satu tindakan.

### Jebakan 3 — Mengubah tinggi elemen secara manual

Sudah dibahas di §4.4. Ringkasnya: kalau tinggi bisa diketik dan juga dihitung, keduanya akan
saling menimpa setiap recomposition. Pilih satu: **tinggi adalah turunan**.

### Jebakan 4 — Menulis posisi hasil `solve()` kembali ke model

Lihat §3.4 Aturan 3. Gejalanya halus: elemen ber-anchor **merayap turun** sedikit pada setiap
perubahan teks. Jangan menulis `y` hasil layout ke `TemplateElement`. Yang disimpan hanya
**niat** perancang (`anchorBelowTable = true`); pergeserannya adalah **hasil render**.

### Jebakan 5 — `compose.resources` dan font statis

Font proyek ini dibundel sebagai **instance statis per bobot**, bukan variable font, karena
dukungan variable font belum seragam di 5 target KMP — dan gejalanya menyesatkan (semua bobot
ter-render sebagai Regular di sebagian platform). Terkait erat dengan §12 `AGENTS.md`.

---

## ✅ 6. Verifikasi & Pengujian

### 6.1 Piramida tes untuk fitur ini

```
        ┌──────────────────────────────┐
        │ 5. Verifikasi di browser     │  ← paling lambat, paling dipercaya
        ├──────────────────────────────┤
        │ 4. :server:test (renderer)   │  ← PDFTextStripper mengecek teks multi-baris
        ├──────────────────────────────┤
        │ 3. :app:shared:jvmTest       │  ← ViewModel + fake data source
        ├──────────────────────────────┤
        │ 2. :core:jvmTest (layout)    │  ← geometri & pemotongan baris
        └──────────────────────────────┘
```

Jumlah tes berbanding terbalik dengan kecepatannya. Tes di lapisan 2 berjalan dalam milidetik dan
**tidak butuh** PostgreSQL, browser, atau Compose runtime. Inilah imbalan nyata dari keputusan
di §4.1.

### 6.2 Perintah verifikasi (jalankan dari akar proyek)

```bash
# .env WAJIB dimuat: :server:test menembak PostgreSQL di 5435, sedangkan default Gradle 5432.
set -a && . ./.env && set +a

./gradlew \
  :core:cleanJvmTest :core:jvmTest \
  :server:cleanTest :server:test \
  :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs \
  :app:shared:assembleAndroidMain :app:shared:jvmTest \
  --console=plain
```

`cleanJvmTest` / `cleanTest` dipakai karena tanpa itu Gradle berkata **`UP-TO-DATE`** dan Anda
tidak benar-benar melihat tesnya berjalan. `UP-TO-DATE` bukan "hijau" — artinya "tidak
dijalankan".

### 6.3 Hasil nyata pada task ini

| Suite | Jumlah | Hasil |
|---|---|---|
| `:core:jvmTest` — `InvoiceTextLayoutTest` | 10 | 0 gagal |
| `:core:jvmTest` — `InvoiceDocumentLayoutTest` | 7 | 0 gagal |
| `:core:jvmTest` — `TemplateElementPlacementTest` | 7 | 0 gagal |
| `:core:jvmTest` — `TemplateRectResizeTest` | 4 | 0 gagal |
| `:core:jvmTest` — `InvoiceTemplateCodecTest` | 3 | 0 gagal |
| `:server:test` — seluruh suite (31 kelas) | — | 0 gagal |
| ↳ `InvoicePdfRendererTest` | 2 | 0 gagal (multi-baris lewat `PDFTextStripper`) |
| ↳ `InvoicingApiTest` | 5 | 0 gagal (jalur simpan template) |
| ↳ `InvoiceTemplateSeedDecodeTest` | 1 | 0 gagal (23 elemen seed tidak dibuang) |
| `:app:shared:jvmTest` — `TemplateDesignerViewModelTest` | 22 | 0 gagal |
| Kompilasi 5 target | jvm, wasmJs, js, android, jvmTest | `BUILD SUCCESSFUL` |

**Cara membaca hasilnya tanpa tertipu**, karena keluaran Gradle tidak mencetak nama tiap tes:

```bash
python3 - <<'PY'
import xml.etree.ElementTree as ET
f = ('app/shared/build/test-results/jvmTest/'
     'TEST-com.eventverse.app.presentation.invoicing.template.TemplateDesignerViewModelTest.xml')
t = ET.parse(f).getroot()
print('tests=%s failures=%s errors=%s' % (t.get('tests'), t.get('failures'), t.get('errors')))
for tc in t.findall('testcase'):
    print(' -', tc.get('name'))
PY
```


### 6.4 Verifikasi di browser — dan mengapa ini bukan pekerjaan sepele

**Jebakan pertama**: aplikasi Compose Wasm **tidak bisa** ditemukan dengan
`document.querySelectorAll('canvas')`. Hasilnya nol. Bukan karena tidak ada kanvas, tapi karena
kanvasnya hidup di dalam **ShadowRoot**:

```
body
└── div
    └── div (shadowRoot = true)          ← batas yang tidak ditembus querySelectorAll
        ├── style
        └── div
            ├── canvas  (1600×1000)      ← di sini Compose menggambar
            └── div#cmp_a11y_root        ← pohon aksesibilitas Compose
```

**Jebakan kedua**: `document.elementFromPoint(x, y)` **tidak menembus shadow root**. Ia
mengembalikan **host**-nya (div luar), bukan kanvas. Konsekuensinya menipu: Anda mengirim
`pointerdown`/`pointerup` ke elemen yang salah, **tidak ada error apa pun**, dan UI tidak
bereaksi. Anda akan menyimpulkan "aplikasinya rusak" padahal Anda hanya mengetuk pintu yang salah.

**Jebakan ketiga**: Playwright CSS selector **memang** menembus shadow DOM (ia berhasil
menemukan `<div role="button">Muat Layar</div>`), tetapi kliknya ditolak:

```
<canvas tabindex="0" width="1600" height="1000" role="generic" draggable="true"> intercepts pointer events
```

Kanvas menutupi seluruh permukaan, sementara node aksesibilitas memakai `pointer-events: none`.

**Jurus yang bekerja**: ambil kanvasnya langsung (tembus shadow root sendiri), netralkan
`setPointerCapture` (tanpa pointer asli, pemanggilannya membuat gesture gagal), lalu kirim
urutan `pointerover → pointermove → pointerdown → pointerup` **ke kanvas itu**.

```js
const sr  = document.body.firstElementChild.firstElementChild.shadowRoot;
const cvs = sr.querySelector('canvas');           // ← tembus shadow root secara manual

const oSet = cvs.setPointerCapture;
cvs.setPointerCapture = () => {};                 // ← netralkan capture

const mk = (x, y, b) => ({
  bubbles: true, cancelable: true, composed: true,
  clientX: x, clientY: y, button: 0, buttons: b,
  pointerId: 1, pointerType: 'mouse', isPrimary: true
});
cvs.dispatchEvent(new PointerEvent('pointerover', mk(x, y, 0)));
cvs.dispatchEvent(new PointerEvent('pointermove',  mk(x, y, 0)));
cvs.dispatchEvent(new PointerEvent('pointerdown',  mk(x, y, 1)));
cvs.dispatchEvent(new PointerEvent('pointerup',    mk(x, y, 0)));

cvs.setPointerCapture = oSet;                      // ← kembalikan
```

**Cara mendapatkan koordinat yang benar**: jangan menebak dari screenshot, karena
`elementFromPoint` tidak bisa dipakai. Bacalah **pohon aksesibilitas** untuk memperoleh hit-box
sebenarnya — dan ambil ulang setiap kali, karena tata letak bergeser beberapa piksel ketika label
zoom berubah lebar:

```js
[...sr.querySelectorAll('div')]
  .filter(e => e.getAttribute('role') === 'button')
  .map(e => ({ label: e.textContent.trim(), ...e.getBoundingClientRect() }))
```

Contoh hasil nyatanya: `Muat Layar → {x:1332, y:102, w:92, h:40}`. Meleset 10–20 piksel sudah
cukup untuk membuat klik "tidak terjadi apa-apa" — dan Anda akan salah menyimpulkan bahwa
fiturnya rusak.

**Hasil verifikasi yang benar-benar terlihat:**

| Tindakan | Bukti |
|---|---|
| Ketuk `+` | zoom `100%` → `115%`, kertas membesar mengikuti |
| Ketuk `Muat Layar` | zoom `115%` → `80%`, **seluruh A4 utuh terlihat** |
| Ukur kertas di screenshot | 506 × 708 px = 210 × 297 mm × 3dp/mm × 0,80 ✔ |
| Ketuk `Simpan Template` | banner hijau *"berhasil disimpan!"*; log server bersih dari error |
| Jumlah elemen di inspektur | `24` = 23 elemen seed + 1 yang disisipkan dari palet ✔ |

> **Pelajaran paling berharga dari sesi ini**: seluruh tes otomatis hijau, kompilasi 5 target
> hijau, **dan tetap ada dua bug nyata** — banner basi (§5 Jebakan 2) dan HTTP 500 (§5 Jebakan 1)
> — yang hanya ketahuan setelah aplikasi **dijalankan dan dilihat dengan mata**. Tes membuktikan
> *kode melakukan apa yang Anda pikirkan*; ia tidak membuktikan *yang Anda pikirkan itu benar*.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

### Tantangan 1 — Buktikan pemotongan baris benar-benar bebas font

Tambahkan tes di `core/src/commonTest/.../InvoiceTextLayoutTest.kt` yang memverifikasi bahwa
**lebar baris hasil `wrap()` tidak pernah melebihi lebar kotaknya**, untuk ukuran font 8pt, 10pt,
14pt, dan 20pt pada kotak 500 Mm10:

```kotlin
@Test
fun wrappedLines_neverExceedTheBoxWidth_atAnyFontSize() {
    val text = "Kawasan Industri Rancaekek Kav. 12 Bandung Jawa Barat"
    for (pt in listOf(8, 10, 14, 20)) {
        val style = TextStyleSpec(fontSizePt = pt)
        val lines = InvoiceTextLayout.wrap(text, widthMm10 = 500, style = style)
        lines.forEach { line ->
            assertTrue(
                InvoiceTextLayout.estimateWidthMm10(line, style) <= 500,
                "Baris '$line' (${pt}pt) melebar melewati kotak 500 Mm10"
            )
        }
    }
}
```

*Pertanyaan lanjutan*: apakah batas `<` atau `<=` yang benar? Cari tahu di mana perbandingan itu
dilakukan di `wrapSegment`, dan jelaskan mengapa keputusannya begitu.

### Tantangan 2 — Perbaiki bug nyata: gagal simpan tidak memulihkan `isSaving`

Buka `TemplateDesignerViewModel.saveTemplate()`. Apa yang terjadi pada `isSaving` kalau
`remoteDataSource.saveTemplate` **melempar exception** (bukan mengembalikan `Result.failure`)?
Bandingkan dengan `saveAndCreateInvoice()` yang memakai `isFailure`. Tulis tes yang gagal lebih
dulu (merah), baru perbaiki. Inilah cara kerja *test-driven bug fix*.

### Tantangan 3 — Cari pemakaian `anchorBelowTable` di seluruh repo

```bash
grep -rn "anchorBelowTable" core/src server/src app/shared/src
```

Untuk setiap tempat, jawab: apakah ia membaca dari **model** (niat perancang, benar) atau dari
**hasil `solve()`** (hasil render, berbahaya kalau ditulis kembali)? Tulis kesimpulanmu — ini
latihan membedakan **source of truth** dari **derived value**.

### Tantangan 4 — Tambahkan "Muat Lebar" di samping "Muat Layar"

Saat ini `fitZoomFor()` memakai `min(rasioLebar, rasioTinggi)`. Tambahkan tombol kedua yang
memakai **hanya rasio lebar** (kertas memenuhi lebar, pengguna menggulir ke bawah). Ini melatih
kamu memisahkan **kebijakan** (tombol apa yang ada) dari **mekanisme** (fungsi matematika) —
perhatikan bahwa perubahannya seharusnya hanya di `DesignerToolbar` + satu fungsi baru, dan
`TemplateCanvas` **tidak perlu disentuh sama sekali**.

### Tantangan 5 — Tirukan teknik verifikasi browser ini di layar lain

Pilih satu layar Compose Wasm lain (mis. `/crm`). Tanpa membaca dokumen ini lagi, tulis ulang
skrip pengetuk kanvas dari ingatan, lalu verifikasi satu interaksi. Kalau kamu lupa salah satu
dari tiga bagian (`shadowRoot` → `querySelector('canvas')` → netralkan `setPointerCapture`),
gejalanya akan selalu sama: **tidak ada error, dan tidak terjadi apa-apa.**

---

## 📚 Rujukan Berkas di Repo Ini

| Berkas | Perannya |
|---|---|
| `core/.../domain/invoicing/template/InvoiceTextLayout.kt` | Pemecah baris + pengukur (deterministik) |
| `core/.../domain/invoicing/template/InvoiceDocumentLayout.kt` | `solve()`, `measureHeights()`, aturan dinamis |
| `core/.../domain/invoicing/template/TemplateElementPresets.kt` | Preset elemen & penempatan otomatis |
| `core/.../domain/invoicing/template/InvoiceBindingRegistry.kt` | Sumber default palet & inspektur |
| `core/.../domain/invoicing/template/TemplateGeometry.kt` | `TemplateRect.resizedWidth()` (penjepitan lebar) |
| `app/shared/.../presentation/invoicing/template/ElementPalette.kt` | Perpustakaan elemen (kiri) |
| `app/shared/.../presentation/invoicing/template/DesignerPropertyInspector.kt` | Inspektur tunggal (kanan) |
| `app/shared/.../presentation/invoicing/template/TemplateCanvas.kt` | Kanvas A4 (menggambar apa adanya) |
| `app/shared/.../presentation/invoicing/template/InvoiceTemplateDesignerScreen.kt` | Perekatan + `fitZoomFor` |
| `app/shared/.../presentation/invoicing/template/TemplateDesignerViewModel.kt` | MVI state & mutasi elemen |
| `server/.../infrastructure/pdf/InvoicePdfRenderer.kt` | Konsumen kedua (PDFBox) |
| `tools/test-changed.sh` | Runner tes selektif berbasis perubahan berkas |

