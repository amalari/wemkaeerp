# 🎓 Modul Pembelajaran: Memperbaiki Kanvas Desainer Faktur — Pan, Drag, Nudge, Grid & Kepemilikan Fokus

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform (Wasm/Desktop) gesture & focus, layout titik-nol koordinat,
> MVI satu-arah, Domain-Driven Design (aturan geometri sebagai method entity), toleransi codec
> **Prasyarat**: Dasar Kotlin (`data class`, `value class`, lambda), dasar Compose
> (`UiState` + `onEvent`, `Modifier`, `pointerInput`), dasar Gradle multi-target
> **Referensi File**:
> - [`TemplateCanvas.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/invoicing/template/TemplateCanvas.kt)
> - [`TemplateDesignerViewModel.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/invoicing/template/TemplateDesignerViewModel.kt)
> - [`TemplateDesignerUiState.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/invoicing/template/TemplateDesignerUiState.kt)
> - [`TemplateGeometry.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/invoicing/template/TemplateGeometry.kt)
> - [`InvoiceTemplateCodec.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/shared/invoicing/InvoiceTemplateCodec.kt)
> - [`DesignerPropertyInspector.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/invoicing/template/DesignerPropertyInspector.kt)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Fitur "Desain Template Faktur" sudah ada, tombolnya bisa diklik, layarnya terbuka — **tapi tidak bisa
dipakai sama sekali**. Empat keluhan yang dilaporkan, dan keempatnya punya akar yang berbeda:

| Gejala yang dilihat pengguna | Akar masalah sebenarnya |
|---|---|
| "Kertasnya kosong melompong, tidak ada kolom isian" | `decodeElement` hanya membaca kunci `id`, sedangkan seed SQL menulis `elementId` → 23 elemen dibuang **diam-diam** |
| "Elemennya tidak mau digeser, balik lagi ke tempatnya" | `pointerInput` membaca `element.rect` dari nilai yang di-*capture* saat komposisi pertama (stale closure) |
| "Tombol panah tidak menggeser apa pun" | Tidak ada handler `KeyEvent` di kanvas **dan** fokus Compose dicuri tombol toolbar |
| "Kanvasnya penuh garis, susah melihat faktur" | `showGrid` bawaannya `true` (mesh 21 × 30 kotak) |

Pelajaran pertama yang harus diambil: **empat gejala, empat penyakit**. Godaan terbesar adalah mencari
satu "bug besar" yang menjelaskan semuanya, lalu menambal gejala satu per satu tanpa pernah menemukan
akar. Di modul ini kita akan membedah keempatnya, dan menunjukkan bahwa dua di antaranya (blank paper
dan snap-back) sama-sama berujung pada satu prinsip: **state adalah satu-satunya sumber kebenaran;
salinan lokal yang tidak pernah diperbarui selalu berbohong.**

### Analogi Sederhana

- **Kertas A4 di kanvas** = selembar kertas milimeter blok. Titik nol (0,0) ada di **sudut kiri-atas
  lembar**, bukan di sudut kiri-atas *kotak pembungkusnya*.
- **`Mm10`** = ukuran dalam satuan 1/10 mm sebagai **integer**. Bukan `Float`, bukan `Double`.
- **`TemplateRect.movedBy()`** = penggaris dan pembatas meja: satu-satunya alat yang boleh dipakai
  untuk memindahkan elemen. Semua jalur (drag, tombol panah, input milimeter) lewat alat yang sama,
  sehingga mustahil menghasilkan posisi berbeda untuk perpindahan yang sama.
- **`dragBase`** = titik pijak. Kalau setiap langkah tarikan dihitung dari posisi yang *baru saja*
  berubah, langkah-langkah itu saling menimpa dan elemen hanya bergetar di tempat.
- **Kepemilikan fokus** = siapa yang sedang memegang "mikrofon". Selama mikrofon di tangan tombol
  toolbar, kanvas boleh meminta pintasan papan-tik dan tidak akan didengar siapa pun.

### Hasil Akhir yang Diharapkan

Klik kartu template → kanvas A4 terbuka dengan **23 kolom isian** yang sudah terisi data contoh →
pilih elemen → geser dengan mouse 1:1 → haluskan dengan tombol panah (5 mm per tekan, `Shift` = 10×) →
aktifkan grid kalau perlu → tekan `Escape` untuk melepas pilihan.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Urutannya **dari data, bukan dari tampilan**. Kalau Anda mulai dari menambal gesture drag, Anda akan
menghabiskan satu jam menggeser elemen yang tidak terlihat karena halamannya kosong.

1. **Langkah 0 — Buktikan bahwa datanya ada.** Buka psql, periksa `invoice_templates.elements`.
   Di sini ketahuan kolomnya **tidak kosong** (`jsonb_array_length = 23`) padahal kanvas kosong →
   berarti bug-nya di **pembacaan**, bukan di penulisan.
2. **Langkah 1 — Buat kodec toleran (`core/InvoiceTemplateCodec.kt`, `MeasureCodec.kt`).** Terima
   `id` **dan** `elementId`, `header` **dan** `headerText`, rasio objek **dan** rasio teks `"5/12"`.
3. **Langkah 2 — Pindahkan aturan geometri ke domain (`TemplateRect.movedBy`).** Snap + clamp harus
   jadi **method entity**, bukan lambda di dalam UI.
4. **Langkah 3 — Perbaiki jalur drag di UI (`elementDragModifier`).** `dragBase` + `rememberUpdatedState`.
5. **Langkah 4 — Tambahkan jalur nudge papan-tik (`handleCanvasKeyEvent`).** Panah, `Shift`+panah,
   `Delete`, `Escape`.
6. **Langkah 5 — Selesaikan kepemilikan fokus.** `FocusRequester` + `LaunchedEffect` yang kuncinya
   memuat `selectedElementId` **dan** `canvasTool`.
7. **Langkah 6 — Ubah default & rapikan token desain.** `showGrid = false`, `ClayShapes.Paper` /
   `ClayShapes.Element`, dan pisahkan Box dekorasi dari Box isi.
8. **Langkah 7 — Kunci dengan test, baru percaya layar.** Test domain (geometri), test kodec (seed
   asli), test ViewModel, test server (baca migrasi asli). Baru setelahnya verifikasi visual.

---

## 🔬 3. Bedah Kode Blok per Blok

### 3.1 Bug #1 — Kanvas kosong: kunci JSON yang berbeda, kegagalan yang senyap

```kotlin
// ❌ SEBELUM — hanya satu bentuk kunci yang dikenali
fun decodeElement(obj: JsonValue.Obj): TemplateElement? {
    val id = obj.string("id") ?: return null
    ...
}

// ✅ SESUDAH — dua bentuk kunci, keduanya sah
fun decodeElement(obj: JsonValue.Obj): TemplateElement? {
    // `id` adalah nama kanonik yang ditulis [encodeElement]; `elementId` adalah nama yang
    // dipakai seed SQL `V32__register_invoicing_module.sql`. Membaca keduanya wajib: tanpa itu
    // seluruh 23 elemen template standar dibuang diam-diam dan kanvas A4 tampil kosong.
    val id = obj.string("id") ?: obj.string("elementId") ?: return null
    val rect = obj.obj("rect")?.let(::decodeRect) ?: return null
    ...
}
```

Bentuk yang **lebih berbahaya lagi** ada di kolom tabel item:

```kotlin
// ❌ SEBELUM — kolom dibuang total kalau header/rasio memakai bentuk seed
val header = obj.string("header") ?: return null
val widthRatio = MeasureCodec.decodeRatio(obj.obj("widthRatio"))   // null untuk "5/12"

// ✅ SESUDAH — toleran terhadap dua bentuk, dan punya nilai jatuh (fallback) yang aman
val header = obj.string("header") ?: obj.string("headerText") ?: return null
val widthRatio = obj.obj("widthRatio")?.let(MeasureCodec::decodeRatio)
    ?: MeasureCodec.parseRatioText(obj.string("widthRatio"))
    ?: Ratio.ONE
```

**Mental model yang wajib dipegang**: `?: return null` di dalam `mapNotNull` adalah **kegagalan
senyap**. Satu elemen yang gagal decode tidak melempar exception, tidak menulis log, tidak
menampilkan pesan — ia hanya **tidak ada**. Dari sisi pengguna, itu tidak bisa dibedakan dari
"fitur belum jadi". Karena itu, ketika berhadapan dengan data yang hilang tanpa error, pertanyaan
pertama selalu: *"di mana ada `return null` yang tidak bersuara?"*

`MeasureCodec.parseRatioText` sengaja ditulis sebagai fungsi kecil terpisah supaya aturan konversi
teks → `Ratio` bisa diuji langsung, tanpa perlu membangun JSON:

```kotlin
fun parseRatioText(text: String?): Ratio? {
    val raw = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val parts = raw.split('/')
    val numerator = parts[0].trim().toLongOrNull() ?: return null
    val denominator = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 1L
    return Ratio.of(numerator, if (denominator == 0L) 1L else denominator)
}
```

Dua detail pertahanan di sini: `"5"` (tanpa garis miring) sah dan berarti `5/1`, dan pembagi nol
dijepit ke `1` — pembagian nol tidak boleh sampai masuk ke `Ratio.of`.


### 3.2 Bug #2 — Elemen mental balik: closure basi di dalam `pointerInput`

Ini bug paling klasik di Compose, dan gejalanya menipu: elemen **sedikit bergerak** lalu kembali.
Kelihatannya seperti "drag tidak terdeteksi", padahal drag-nya terdeteksi sempurna — hanya saja
hasilnya selalu ditimpa oleh posisi lama.

```kotlin
// ❌ SEBELUM — `element` di-capture saat komposisi pertama dan tidak pernah diperbarui
Modifier.pointerInput(element.elementId) {
    detectDragGestures { change, dragAmount ->
        change.consume()
        val moved = element.rect.translated(...)   // element.rect = posisi PEMBUATAN closure
        onEvent(UpdateElementRect(element.elementId, moved))  // selalu dari titik yang sama
    }
}
```

```kotlin
// ✅ SESUDAH — dua hal dipisah tegas
private fun Modifier.elementDragModifier(
    elementId: String,
    mmToDp: Float,
    snapMm10: Int,
    paperSize: PaperSize,
    onEvent: (TemplateDesignerUiEvent) -> Unit,
    latestRect: () -> TemplateRect          // ← pembaca yang SELALU menunjuk state terbaru
): Modifier = this.pointerInput(elementId, mmToDp, snapMm10, paperSize) {
    var dragBase = latestRect()             // titik pijak, dibekukan selama satu tarikan
    var dragTotal = Offset.Zero             // akumulasi delta sejak tarikan dimulai

    detectDragGestures(
        onDragStart = {
            dragBase = latestRect()         // dibaca ulang SETIAP tarikan baru
            dragTotal = Offset.Zero
            onEvent(TemplateDesignerUiEvent.SelectElement(elementId))
        },
        onDrag = { change, dragAmount ->
            change.consume()
            dragTotal += dragAmount
            onEvent(
                TemplateDesignerUiEvent.UpdateElementRect(
                    elementId = elementId,
                    // Total perpindahan dihitung dari titik pijak — bukan dari posisi sebelumnya.
                    // Inilah yang membuat gerakan mengikuti kursor 1:1.
                    newBounds = dragBase.advancedByPixels(dragTotal, mmToDp, snapMm10 = 0, paperSize = paperSize)
                )
            )
        },
        ...
    )
}
```

Dan di sisi pemanggil, `latestRect` diisi dari `rememberUpdatedState`:

```kotlin
val renderRect by rememberUpdatedState(element.rect)   // selalu berisi rect terbaru
...
Modifier.elementDragModifier(element.elementId, mmToDp, snapMm10, paperSize, onEvent) { renderRect }
```

**Tiga konsep yang harus dipisahkan di kepala Anda:**

| Konsep | Perannya | Nilai berubah kapan? |
|---|---|---|
| `element.rect` | data dari state MVI | setiap frame tarikan |
| `renderRect` (`rememberUpdatedState`) | jembatan agar closure bisa membaca data terbaru | otomatis, mengikuti `element.rect` |
| `dragBase` + `dragTotal` | memori internal satu tarikan | `dragBase` sekali di `onDragStart`, `dragTotal` menumpuk tiap `onDrag` |

Menghitung `dragBase + dragTotal` (bukan `rectSekarang + dragAmount`) membuat hasilnya **idempoten**:
berapa kali pun frame dipanggil, hasil untuk total delta yang sama selalu sama. Tidak ada
akumulasi galat pembulatan, tidak ada arah balik.


### 3.3 Satu sumber kebenaran geometri: `TemplateRect.movedBy`

Sebelum refactor, aturan "geser lalu snap lalu jepit ke kertas" hidup **di dalam lambda drag**.
Akibatnya tombol panah dan input milimeter di panel properti menempuh jalur lain dan bisa menghasilkan
posisi berbeda untuk perpindahan yang sama — atau, lebih buruk, tidak menjepit sama sekali sehingga
elemen bisa keluar kertas.

```kotlin
// core/.../domain/invoicing/template/TemplateGeometry.kt
/**
 * Menggeser elemen sebesar [dx]/[dy] lalu mengunci hasilnya ke grid [snapMm10] dan ke dalam
 * bidang kertas [paperWidth] × [paperHeight].
 *
 * Perilaku ini adalah aturan domain, bukan urusan UI: kanvas yang digambar dengan pixel,
 * tombol panah nudge, dan input milimeter di panel properti harus menghasilkan posisi yang
 * **identik** untuk perpindahan yang sama.
 *
 * `snapMm10 <= 0` berarti grid magnet dimatikan. Pembulatan selalu ke bawah (`floor`) supaya
 * elemen tidak pernah keluar dari kertas walau grid-nya lebih besar dari ruang sisa.
 */
fun movedBy(
    dx: Mm10,
    dy: Mm10,
    snapMm10: Int,
    paperWidth: Mm10,
    paperHeight: Mm10
): TemplateRect {
    val maxX = (paperWidth.value - width.value).coerceAtLeast(0)
    val maxY = (paperHeight.value - height.value).coerceAtLeast(0)

    val rawX = (x.value + dx.value).coerceIn(0, maxX)   // 1. batasi dulu
    val rawY = (y.value + dy.value).coerceIn(0, maxY)

    val snappedX = if (snapMm10 > 0) (rawX / snapMm10) * snapMm10 else rawX  // 2. baru snap
    val snappedY = if (snapMm10 > 0) (rawY / snapMm10) * snapMm10 else rawY

    return copy(
        x = Mm10(snappedX.coerceIn(0, maxX)),   // 3. jepit ULANG setelah snap
        y = Mm10(snappedY.coerceIn(0, maxY))
    )
}
```

Perhatikan **urutan dan penjepitan ganda**-nya, karena inilah yang paling sering salah:

- `coerceIn` **sebelum** snap mencegah elemen "terbang" ke luar kertas ketika posisinya sudah di tepi.
- `snap` memakai pembagian integer (`floor`), jadi selalu membulat ke bawah.
- `coerceIn` **sesudah** snap wajib ada: kalau grid 10 mm sementara sisa ruang hanya 3 mm, hasil
  snap-nya akan melewati `maxX`. Tanpa penjepitan kedua, elemen keluar dari lembar.

Jembatan dari satuan layar ke satuan domain juga dibuat sekali saja:

```kotlin
private fun TemplateRect.advancedByPixels(
    delta: Offset, mmToDp: Float, snapMm10: Int, paperSize: PaperSize
): TemplateRect = movedBy(
    dx = Mm10((delta.x / mmToDp * 10f).roundToInt()),
    dy = Mm10((delta.y / mmToDp * 10f).roundToInt()),
    snapMm10 = snapMm10,
    paperWidth = paperSize.width,
    paperHeight = paperSize.height
)
```

`3f * zoomFactor` berarti 1 mm = 3 dp pada zoom 100%; di zoom 50% menjadi 1,5 dp. Konversi ini ditaruh
di satu tempat supaya perubahan rumus zoom tidak perlu diburu ke lima pemanggil.

### 3.4 Snap sekali di akhir, bukan setiap frame

```kotlin
onDrag = { change, dragAmount ->
    dragTotal += dragAmount
    // Snap dimatikan selama tarikan supaya gerakan mengikuti kursor 1:1.
    onEvent(UpdateElementRect(elementId, dragBase.advancedByPixels(dragTotal, mmToDp, snapMm10 = 0, paperSize)))
},
onDragEnd = {
    // Satu lompatan magnetik terakhir: posisi akhir dikunci ke grid & dijepit ke kertas.
    onEvent(UpdateElementRect(elementId, dragBase.advancedByPixels(dragTotal, mmToDp, snapMm10, paperSize)))
    dragTotal = Offset.Zero
}
```

Kalau snap dipaksa di setiap frame, gerakan jadi terpatah-patah: kursor sudah bergerak 2 mm tapi
elemen masih menunggu sampai kelipatan 5 mm. Yang diinginkan manusia adalah *feel* dua fase —
**mengikuti kursor dengan setia, lalu menempel sekali** saat dilepas. Ini juga sebabnya di seretan
`onDrag` kita mengirim `snapMm10 = 0`: parameter yang sama, dua nilai berbeda, satu sumber kebenaran.


### 3.5 Bug #3 — Tombol panah: handler papan-tik + kepemilikan fokus

Bagian 1: pemetaan tombol. Perhatikan bahwa langkah nudge diambil dari **snap grid yang sedang aktif**,
bukan angka ajaib `1`:

```kotlin
private fun handleCanvasKeyEvent(
    event: KeyEvent,
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false   // KeyDown saja: kalau tidak, satu tekan = dua langkah

    val selected = state.selectedElement

    when (event.key) {
        Key.Escape -> {
            onEvent(TemplateDesignerUiEvent.SelectElement(null))
            return true
        }
        Key.Delete -> {
            if (selected == null) return false
            onEvent(TemplateDesignerUiEvent.DeleteElement(selected.elementId))
            return true
        }
        else -> Unit
    }

    if (selected == null) return false

    val baseStep = state.snapGridMm.coerceAtLeast(1) * 10
    val step = if (event.isShiftPressed) baseStep * 10 else baseStep   // Shift = lompatan 10×
    ...
    onEvent(TemplateDesignerUiEvent.MoveElementBy(selected.elementId, dx, dy))
    return true
}
```

Dua hal yang mudah terlewat:

1. **`onPreviewKeyEvent` dipasang di `Box` yang sama dengan `focusable()`.** Tanpa `focusable()`,
   Compose tidak akan pernah mengirim event papan-tik ke sana — tidak peduli seberapa benar
   logikanya. Urutannya pun penting: `.focusRequester(focusRequester).focusable().onPreviewKeyEvent { … }`.
2. **`KeyDown` saja.** `KeyEvent` dikirim untuk `KeyDown` *dan* `KeyUp`. Tanpa penjagaan tipe,
   satu tekanan tombol menggerakkan elemen dua kali — pengguna melaporkannya sebagai "elemen
   ganda geser" yang sulit dipercaya.

Bagian 2: **kepemilikan fokus**, bagian yang paling licin dan baru ketahuan saat diuji manual.

```kotlin
val focusRequester = remember { FocusRequester() }

// Kertas menerima fokus setiap kali pilihan berubah, supaya tombol panah langsung bisa dipakai
// untuk menggeser elemen terpilih tanpa perlu mengeklik kertas lebih dulu.
//
// Kunci efek ini juga memuat `canvasTool`. Menekan tombol apa pun di toolbar memindahkan fokus
// Compose ke tombol itu, dan selama fokus di sana seluruh pintasan papan-tik (panah, Delete,
// Escape) mati diam-diam — pengguna hanya melihat "tombol panah tidak jalan". Mengembalikan
// fokus setiap kali alat berganti membuat pintasan itu hidup lagi tanpa perlu mengeklik kertas.
LaunchedEffect(selectedElementId, state.canvasTool) {
    if (selectedElementId != null) runCatching { focusRequester.requestFocus() }
}
```

Ini contoh sempurna dari bug yang **tidak akan tertangkap test apa pun**: state sudah benar, handler
sudah benar, tapi fokus Compose ada di `Button` toolbar. Pelajaran praktisnya — setiap kali sebuah
pintasan papan-tik "kadang jalan, kadang tidak", periksa siapa yang memegang fokus terakhir.

Ketukan di kertas juga ikut disesuaikan supaya tidak merampas pilihan di mode Geser Kanvas:

```kotlin
.pointerInput(isSelectTool) {
    detectTapGestures {
        runCatching { focusRequester.requestFocus() }   // selalu minta fokus
        if (isSelectTool) {                             // hanya SELECT yang melepas pilihan
            onEvent(TemplateDesignerUiEvent.SelectElement(null))
        }
    }
}
```

Kunci `pointerInput(isSelectTool)` — bukan `pointerInput(Unit)` — membuat detektor dipasang ulang
ketika alat berganti. Kalau kuncinya `Unit`, nilai `isSelectTool` di dalam lambda akan membeku pada
nilai saat pertama dipasang, dan ketukan di mode PAN akan tetap mengosongkan pilihan.

### 3.6 Bug #4 — Grid: bawaan mati, tapi tetap bisa dinyalakan

```kotlin
// TemplateDesignerUiState.kt
val showGrid: Boolean = false,      // ← satu karakter yang mengubah pengalaman pertama pengguna

// TemplateCanvas.kt — digambar hanya kalau diminta
// Background Grid (10mm squares) — hanya kalau diminta. Bawaannya mati: mesh
// 21 × 30 kotak terbaca lebih ramai daripada isi fakturnya sendiri.
if (state.showGrid) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stepPx = 10f * mmToDp * density
        ...
    }
}
```

Alasannya bukan estetika semata: 21 kolom × 30 baris garis tipis di atas kertas putih **mengalahkan
kontras** teks faktur. Grid adalah alat bantu penyelarasan yang dipakai sesaat, bukan latar default.
Toggle-nya tetap ada di panel **Pengaturan Template** ("Tampilkan Grid (10mm)" → `AKTIF`/`NONAKTIF`)
dan tetap memakai token warna `WeMadeColors.Border`, bukan literal.

### 3.7 Titik nol koordinat: pisahkan dekorasi dari isi

```kotlin
// Kotak pembungkus dilebihkan sebesar jarak bayangan: claySurface memakai dp pertama
// sebagai ruang gambar bayangan, bukan sebagai bagian dari lembar kertasnya.
Box(modifier = Modifier.size(paperWidthDp + ClayOffset.Rest, paperHeightDp + ClayOffset.Rest)) {
    // 1. Dekorasi: lembar kertas + hard shadow. Diletakkan lebih dulu agar di belakang.
    Box(modifier = Modifier.fillMaxSize().claySurface(shape = ClayShapes.Paper, ...))

    // 2. Isi kertas: ukuran persis 210 × 297 mm, titik nol (0,0) = sudut kiri-atas lembar.
    Box(modifier = Modifier.size(paperWidthDp, paperHeightDp) /* grid + elemen */)
}
```

Kalau keduanya digabung menjadi satu `Box` ber-`claySurface`, `padding` reservasi bayangan 6dp ikut
menggeser titik nol elemen → kanvas menampilkan elemen **~2 mm lebih ke kanan-bawah** daripada hasil
cetak PDF-nya. Bug seperti ini tidak terlihat di layar (semua elemen bergeser bersama), tapi terlihat
di kertas. Aturannya: **dekorasi dan isi adalah dua Box bersaudara, bukan satu Box yang ditumpuk**.


---

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

### Mengapa aturan geometri ditaruh di `core/` (domain) dan bukan di Composable?

Karena **snap dan clamp adalah aturan bisnis dokumen**, bukan efek visual. Yang harus benar adalah
"elemen tidak boleh keluar dari 210 × 297 mm", dan kalimat itu tetap benar walaupun UI-nya diganti
kanvas SVG, PDF.js, atau renderer server. Efek sampingnya yang paling bernilai: aturan itu bisa diuji
tanpa Compose, tanpa browser, dalam milidetik — `TemplateRectMovementTest` berjalan di 76 suite
`:core:jvmTest` tanpa satu pun dependensi UI.

**Risiko kalau ditaruh di UI (cara yang kita tinggalkan):** tiga jalur perpindahan (drag, panah,
input milimeter) masing-masing menyalin logikanya sendiri. Perubahan aturan harus diburu ke tiga
tempat, dan cepat atau lambat salah satunya tertinggal — persis yang terjadi sebelum refactor.

### Mengapa `Mm10` integer, bukan `Float` milimeter?

Karena kanvas ini berjalan di **lima target**: JVM (Desktop), Android, iOS, JS, dan WasmJS. Operasi
`Float` di JS/Wasm dan di JVM memiliki perilaku pembulatan yang bisa berbeda di digit terakhir.
Dengan integer 1/10 mm, hasil `drag` di browser dan hasil `nudge` di desktop **identik secara bit**,
dan uji kesetaraan posisi jadi masuk akal. Bonusnya: `snap` menjadi pembagian integer yang deterministik.

### Mengapa kodec dibuat toleran, bukan seed SQL-nya yang diperbaiki?

Karena data seed itu **sudah tersimpan di database yang berjalan**. Memperbaiki file migrasi `V32`
tidak akan mengubah baris yang sudah ada (Flyway mencatat migrasi sebagai *sudah dijalankan*), jadi
kita harus menulis migrasi `V33` baru untuk meng-`UPDATE` JSON yang sudah ada. Itu bekerja untuk satu
database, tapi tidak untuk dokumen lain yang sudah ditulis dengan bentuk lama di masa depan.

Yang lebih penting: **`encodeElement` dan seed adalah dua penulis yang sah**, dan satu-satunya pihak
yang tahu kedua bentuk itu adalah pembacanya. Toleransi di kodec = satu perubahan kecil, tidak ada
migrasi data, tidak ada downtime, dan template lama tetap terbaca. Test-nya pun jadi lebih kuat:
`InvoiceTemplateSeedDecodeTest` membaca **file migrasi aslinya** dari classpath, sehingga perbedaan
bentuk di masa depan ketahuan di test, bukan di layar pengguna.

### Mengapa nudge lewat tombol panah, bukan hanya drag?

Karena presisi 1 mm dengan mouse secara fisik tidak mungkin. Setelah elemen dipilih, tombol panah
memberi perpindahan **tepat sebesar grid magnet yang aktif** (5 mm), dan `Shift` memberi lompatan 10×
untuk memindahkan blok besar. Ini pembagian kerja yang jelas: **drag untuk kasar, panah untuk presisi.**

### Mengapa `detectTapGestures` dan `detectDragGestures` bisa hidup bersama?

Urutan dispatch pointer Compose berjalan **dari anak ke induk**. Karena itu tarikan di atas elemen
sampai ke handler elemen lebih dulu; ia memanggil `change.consume()`, dan `detectDragGestures` milik
kanvas (pan) otomatis **membatalkan** drag-nya sendiri. Jadi tidak perlu flag "apakah saya sedang
menyeret elemen" — konsumsi event sudah menjadi protokolnya. Yang perlu kita tambahkan hanyalah
`isInteractive = isSelectTool` supaya di mode Geser Kanvas handler elemen tidak dipasang sama sekali.


---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls)

### 5.1 Kegagalan senyap: `?: return null` di dalam `mapNotNull`

```kotlin
val elements = arr.items.mapNotNull(::decodeElement)   // 23 masuk, 0 keluar, tanpa satu pun error
```

Satu field dengan nama kunci berbeda = seluruh daftar kosong. **Aturan kerja:** setiap kali Anda menulis
`mapNotNull` pada data yang datang dari luar (DB, API, file), tanyakan *"kalau satu item gagal decode,
apakah ada cara tahu?"* Kalau jawabannya tidak, tambahkan minimal satu test yang memakai **data asli**
(seperti `InvoiceTemplateSeedDecodeTest` yang membaca file migrasi V32), bukan data buatan yang Anda
sendiri yang menyusunnya — data buatan selalu cocok dengan asumsi Anda, itulah masalahnya.

### 5.2 Closure basi di dalam `pointerInput`

`pointerInput` menyimpan lambda beserta **closure**-nya selama kuncinya tidak berubah. Variabel yang
di-capture dari luar tidak pernah diperbarui. Obatnya hanya dua: (a) masukkan variabel penentu ke
kunci `pointerInput`, atau (b) baca lewat lambda `rememberUpdatedState`. **Jangan** menyelesaikannya
dengan memasukkan `element.rect` ke kunci `pointerInput` — itu membongkar-pasang detektor di tengah
gestur dan membuat drag terputus setiap frame.

### 5.3 Fokus Compose dicuri tombol

Klik tombol di toolbar → fokus pindah ke tombol → `onPreviewKeyEvent` di kanvas berhenti dipanggil →
"tombol panah rusak". Ini **bukan** bug logika, dan tidak akan muncul di test mana pun. Obatnya:
`LaunchedEffect(selectedElementId, state.canvasTool) { focusRequester.requestFocus() }`. Bungkus
dengan `runCatching`: `requestFocus()` melempar jika node belum terpasang di komposisi, dan exception
di dalam effect akan mematikan layarnya.

### 5.4 `--tests` menyembunyikan suite lain (dan membuat Anda salah menyimpulkan)

Menjalankan `./gradlew :server:test --tests InvoiceTemplateSeedDecodeTest` hijau **bukan berarti**
`:server:test` hijau. Suite lain tidak dijalankan sama sekali, dan folder hasil test hanya berisi satu
XML. Saat akhirnya suite penuh dijalankan, kegagalan lama bermunculan dan tampak seperti regresi baru.
Selalu jalankan target penuh sebelum menyimpulkan.


### 5.5 Gradle TIDAK membaca `.env` — port database salah, error-nya menyesatkan

Ini jebakan yang benar-benar memakan waktu di sesi ini, jadi ditulis lengkap.

`DatabaseFactory.init()` memakai default `localhost:5432` (`DB_PORT`) dengan database `wemade_erp`:

```kotlin
jdbcUrl: String = getEnvOrDefault(
    "DB_JDBC_URL",
    "jdbc:postgresql://${getEnvOrDefault("DB_HOST", "localhost")}:${
        getEnvOrDefault("DB_PORT", "5432")
    }/${getEnvOrDefault("DB_NAME", "wemade_erp")}"
)
```

Sementara `.env` proyek ini menetapkan **`DB_PORT=5435`**, dan di mesin pengembang port **5432 sudah
dipakai project lain**. Gejalanya:

```
ApplicationTest > testRoot FAILED
    com.zaxxer.hikari.pool.HikariPool$PoolInitializationException
        Caused by: org.postgresql.util.PSQLException at ConnectionFactoryImpl.java:704
```

Ini terlihat persis seperti "fitur invoicing saya merusak backend", padahal aplikasi hanya berbicara
ke database yang salah. Pembanding cepat:

```bash
docker exec wemade-postgres psql -U postgres -p 5432 -lqt | cut -d'|' -f1   # → di sini ada wemade_erp
docker ps --format '{{.Names}}\t{{.Ports}}'                                  # 5435 = wemade-postgres, 5432 = project lain
```

Cara benar menjalankan test server:

```bash
set -a; source .env; set +a
./gradlew :server:test
```

**Pelajaran:** `HikariPool$PoolInitializationException` berarti "saya tidak bisa membuka koneksi",
bukan "kode Anda salah". Periksa konfigurasi koneksi dulu sebelum membaca diff.

### 5.6 Dua build Gradle paralel saling menimpa hasil test

Menjalankan build di background lalu memulai build kedua sebelum yang pertama selesai membuat folder
`build/test-results/` menjadi **campuran dua eksekusi**. Di sesi ini hal itu sempat membuat hasil
"hijau" hilang tertimpa hasil "merah" milik eksekusi lain yang tidak memakai env yang benar — dan
sempat membuat kesimpulan "test gagal" padahal sumbernya sudah lulus.

```bash
pgrep -fl 'GradleWrapperMain'                                   # harus kosong sebelum verifikasi
./gradlew ... ; stat -f '%Sm %N' server/build/test-results/test/*.xml
```

Biasakan membaca `timestamp="..."` di dalam `<testsuite>` (waktu eksekusi) dan membandingkannya
dengan jam sekarang, bukan hanya nama file.

### 5.7 Memverifikasi UI Compose di browser: `#cmp_a11y_root` bukan target klik

Compose Wasm mengekspos pohon aksesibilitas di `#cmp_a11y_root`, dan node-node itu
`pointer-events: none` — klik melalui DOM aksesibilitas **tidak** sampai ke Compose. Yang harus
dituju adalah elemen `<canvas>` di dalam shadow root, dengan `PointerEvent` sintetis (`bubbles`,
`cancelable`, dan **`composed: true`** — tanpa `composed`, event tidak menembus shadow boundary):

```js
const find = (root) => { /* telusuri shadowRoot secara rekursif sampai menemukan CANVAS */ };
const canvas = find(document);
const base = {bubbles: true, cancelable: true, composed: true, pointerId: 1,
              pointerType: 'mouse', isPrimary: true, clientX: x, clientY: y,
              screenX: x, screenY: y, button: 0, buttons: 1};
canvas.dispatchEvent(new PointerEvent('pointerdown', base));
canvas.dispatchEvent(new PointerEvent('pointerup', {...base, buttons: 0}));
```

Dengan `devicePixelRatio === 1` dan kanvas seukuran viewport, koordinat CSS sama dengan koordinat
screenshot — angka yang Anda lihat di screenshot bisa langsung dipakai sebagai target klik.

### 5.8 Jangan menambal tampilan dengan literal

Saat menyentuh `TemplateCanvas.kt`, semua nilai bentuk/warna diambil dari token: `ClayShapes.Paper`,
`ClayShapes.Element`, `ClayBorder.Thick`, `ClayBorder.Hairline`, `ClayOffset.Rest`, `WeMadeColors.*`.
Hasil pemeriksaan sesudah perubahan: **nol** `Color(0xFF…)`, **nol** `RoundedCornerShape(N.dp)`
telanjang, **nol** `Modifier.shadow()`, dan **nol** `Card`/`Button` Material di seluruh
`presentation/invoicing/template/*.kt`. Perbaikan bug **bukan** alasan untuk menambah utang desain.


---

## 🧪 6. Verifikasi & Cara Membuktikannya Sendiri

### 6.1 Test otomatis (dengan env yang benar!)

```bash
cd /Volumes/amalari/Projects/wemade
set -a; source .env; set +a
./gradlew --console=plain \
  :core:jvmTest :app:shared:jvmTest :server:test \
  :app:shared:compileKotlinJvm :app:shared:compileKotlinJs :app:shared:compileKotlinWasmJs
```

Hasil yang harus Anda lihat (**angka ini yang sesi ini buktikan**):

| Target | Suite | Test | Failures | Errors |
|---|---|---|---|---|
| `:core:jvmTest` | 76 | 457 | 0 | 0 |
| `:app:shared:jvmTest` | 20 | 106 | 0 | 0 |
| `:server:test` | 31 | 167 | 0 | 0 |

Test baru yang relevan untuk modul ini:

- `core/.../template/TemplateRectMovementTest.kt` — snap + clamp di `movedBy` (termasuk kasus grid
  lebih besar dari ruang sisa, yang membuktikan penjepitan kedua bekerja).
- `core/.../shared/invoicing/InvoiceTemplateCodecTest.kt` — decode bentuk seed SQL (`elementId`,
  `headerText`, `widthRatio: "5/12"`).
- `app/shared/.../TemplateDesignerViewModelTest.kt` (+ `FakeInvoicingRemoteDataSource.kt`) — 14 test
  state holder: pilih, geser, nudge, hapus, escape, toggle grid.
- `server/.../InvoiceTemplateSeedDecodeTest.kt` — membaca **file migrasi V32 asli** dari classpath,
  mengekstrak literal JSON elemennya, dan memastikan **23** elemen terbaca.

### 6.2 Membaca bukti JUnit XML, bukan sekadar percaya "BUILD SUCCESSFUL"

```bash
python3 - <<'PY'
import glob, re
for label, pat in [('server','server/build/test-results/test/*.xml'),
                   ('core','core/build/test-results/jvmTest/*.xml'),
                   ('shared','app/shared/build/test-results/jvmTest/*.xml')]:
    T=F=E=n=0
    for p in glob.glob(pat):
        a = re.search(r'<testsuite [^>]*>', open(p, encoding='utf-8').read(3000)).group(0)
        g = lambda k: int(re.search(k + r'="(\d+)"', a).group(1))
        T += g('tests'); F += g('failures'); E += g('errors'); n += 1
    print(f'{label}: suites={n} tests={T} failures={F} errors={E}')
PY
```

Skrip ini penting justru karena `BUILD SUCCESSFUL` bisa menyesatkan: build akan hijau kalau
task-nya `UP-TO-DATE`, padahal Anda ingin tahu apakah test benar-benar **dijalankan dan lulus**.
Kolom `failures`/`errors` per suite tidak bisa berbohong.

### 6.3 Membuktikan kompilasi benar-benar mencakup sumber terbaru

`UP-TO-DATE` pada tugas kompilasi berarti Gradle membandingkan **hash input** — jadi "UP-TO-DATE"
justru bukti terkuat bahwa sumber saat ini sudah pernah dikompilasi dengan sukses:

```
> Task :core:compileKotlinJvm UP-TO-DATE
> Task :core:compileKotlinJs UP-TO-DATE
> Task :core:compileKotlinWasmJs UP-TO-DATE
> Task :app:shared:compileKotlinJvm UP-TO-DATE
> Task :app:shared:compileKotlinJs UP-TO-DATE
> Task :app:shared:compileKotlinWasmJs UP-TO-DATE
```

Untuk bundle yang disajikan ke browser, bandingkan waktunya dengan sumber terakhir yang Anda ubah —
**bundle harus lebih baru dari sumber**:

```bash
stat -f '%Sm  %N' -t '%Y-%m-%dT%H:%M:%S' \
  ./build/wasm/packages/EventVerse-app-webApp/kotlin/EventVerse-app-webApp.wasm \
  app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/invoicing/template/TemplateCanvas.kt
```

### 6.4 Verifikasi visual (bukti yang dilihat mata)

Tempuh lewat Playwright/Chrome DevTools dengan resep di §5.7. Daftar periksa lengkap untuk kanvas:

| # | Yang diuji | Langkah | Bukti yang diharapkan |
|---|---|---|---|
| 1 | Kartu template muncul | buka `/invoicing` → **Desain Template** | daftar template render |
| 2 | Kanvas tidak kosong | ketuk kartu template | 23 elemen terisi data contoh (bukan kertas putih) |
| 3 | Pilih elemen | ketuk salah satu elemen | ada garis pilihan `Primary` + panel **Properti Elemen** |
| 4 | Drag 1:1 | seret +300 px, lalu lepas | elemen bergerak tanpa balik ke posisi awal |
| 5 | Arah negatif | seret −150 px | bergerak sejumlah yang benar, terjepit di tepi kertas |
| 6 | Nudge panah | 3× `ArrowRight` | X naik **3 × 5 mm** (dari snap grid aktif) |
| 7 | Pan kanvas | pilih alat **Geser Kanvas** → seret kanvas | viewport tergeser, **pilihan tidak hilang** |
| 8 | Panah tetap hidup setelah ganti alat | pilih elemen → pindah ke Geser Kanvas → `ArrowRight` | elemen tetap bergeser (fokus dikembalikan) |
| 9 | Escape | tekan `Escape` setelah kertas berfokus | pilihan dilepas |
| 10 | Grid bawaan | buka desainer, lihat kertas + tombol | **tanpa mesh**, tombol bertulis `NONAKTIF` |
| 11 | Toggle grid | klik tombol → klik lagi | `AKTIF` + mesh 10 mm muncul → `NONAKTIF` + mesh hilang |

Poin 4, 5, dan 11 adalah yang paling sering lolos dari test otomatis: ia hanya bisa dibuktikan dengan
**mata dan interaksi nyata**. Bug "teks terpecah satu huruf per baris" atau "elemen mental" tidak akan
pernah tertangkap unit test.

### 6.5 Reproduksi bug lama (untuk memahami, bukan untuk diulang)

Untuk merasakan bug #1: hapus sementara `obj.string("elementId")` dari `decodeElement`, jalankan web,
buka desainer → kertas kosong tanpa pesan kesalahan apa pun. Itulah bentuk kegagalan yang harus membuat
Anda curiga pada setiap `?: return null`.


---

## 🏆 7. Tantangan Mandiri

Kerjakan berurutan; setiap tantangan menaikkan satu tingkat kesulitan dan semuanya bisa diuji tanpa
menyentuh kode orang lain.

**Tantangan 1 — Buktikan snap terjadi sekali, bukan tiap frame.**
Tambahkan test di `TemplateRectMovementTest` yang memanggil `movedBy` dua kali berturut-turut
(meniru `onDrag` lalu `onDragEnd`) dan pastikan hasil akhirnya sama dengan satu panggilan
`movedBy` dengan snap aktif. *Petunjuk:* masalahnya idempotensi — pikirkan mengapa `dragBase`
harus dibekukan.

**Tantangan 2 — Tangkap kegagalan decode, jangan telan.**
Ubah `InvoiceTemplateCodec.decode` supaya mengembalikan juga daftar elemen yang gagal decode
(mis. `DecodeResult(template, droppedElementIndexes)`). Lalu tampilkan peringatan di panel
Pengaturan Template ketika `dropped.isNotEmpty()`. *Pertanyaan renungan:* kenapa informasi ini
tidak boleh dilempar sebagai exception?

**Tantangan 3 — Nudge dengan langkah yang bisa dipilih.**
Saat ini langkah panah = `snapGridMm`, dan `Shift` = 10×. Tambahkan opsi "langkah nudge" terpisah
(mis. 0,5 mm / 1 mm / 5 mm) tanpa menggandakan logika di `handleCanvasKeyEvent`. *Petunjuk:*
tambahkan field di `TemplateDesignerUiState`, dan ingat `Mm10` integer — 0,5 mm = 5.

**Tantangan 4 — Batas bawah ukuran elemen.**
Sekarang `TemplateRect` hanya menolak lebar/tinggi **negatif**. Tambahkan aturan domain "elemen
minimal 5 × 5 mm" dan pastikan drag tidak bisa melanggar. Tentukan sendiri: apakah pelanggaran harus
`require` (melempar) atau di-`coerce` (diam-diam diperbaiki)? *Jawaban yang diharapkan:* `require`
untuk input pengguna yang tidak masuk akal (form milimeter), `coerce` untuk gestur drag yang bersifat
kontinu.

**Tantangan 5 — Mode gelap kanvas.**
Grid dan outline kertas saat ini memakai token terang. Rancang varian gelap **lewat theme**
(bukan ternary `if (isPresentationMode)`), sesuai kontrak §6 design system WeMade. Kalau
`darkColorScheme` belum ada, itu artinya tantangan ini memang harus menunggu atau ikut
mengerjakan theme-nya — dan itulah jawaban yang benar.

**Tantangan 6 — Verifikasi otomatis untuk pinch-zoom.**
Kanvas mendukung zoom lewat tombol `+`/`−`. Tulis skenario Playwright yang membuktikan bahwa
memperbesar zoom **tidak menggeser elemen relatif terhadap kertas** (posisi mm harus konstan
walaupun `mmToDp` berubah). Ini menuntut Anda memahami batas antara satuan layar dan satuan domain.

---

## 📌 Ringkasan Satu Halaman

| Prinsip | Wujudnya di kode |
|---|---|
| Satu sumber kebenaran perpindahan | `TemplateRect.movedBy()` di `core/` |
| State adalah sumber gambar, bukan salinan lokal | `rememberUpdatedState` + `dragBase` |
| Kegagalan harus bersuara | kodec toleran + test yang memakai data seed asli |
| Snap hanya di akhir gestur | `snapMm10 = 0` saat `onDrag`, snap saat `onDragEnd` |
| Fokus adalah sumber daya yang harus diperjuangkan | `LaunchedEffect(selectedElementId, canvasTool)` |
| Alat bantu mati secara bawaan | `showGrid = false` |
| Bug fix tidak menambah utang desain | token `ClayShapes`/`ClayBorder`/`ClayOffset` |
| Verifikasi = test **dan** mata | JUnit XML + screenshot interaksi nyata |

