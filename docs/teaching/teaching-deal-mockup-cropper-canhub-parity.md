# 🎓 Modul Pembelajaran: Mengangkat Cropper Mockup Deal ke Paritas Fitur CanHub Android-Image-Cropper

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform Canvas, Skia Matrix Pipeline, Pivot Rotation Math, Aspect Ratio Viewport, Clipping Oval, Fixed-Viewport Cropper Paradigm  
> **Prasyarat**: Paham dasar Compose `Canvas`/`DrawScope`, pernah membaca `MockupCropDialog.kt` versi lama (cropper 1:1), dan tahu kenapa kita **tidak** memakai library Android murni di `app/shared` (target kita iOS + JVM + WasmJS, tanpa Android target).  
> **Referensi Task**: Permintaan user — "samakan fungsi cropper internal dengan CanHub/Android-Image-Cropper"

---

## 💡 1. Konsep Dasar & Masalah Dunia Nyata

**Masalah nyata**: Cropper lama hanya bisa 1:1, tanpa rotasi, tanpa flip. Buyer mengirim foto
mockup yang miring 3° atau landscape, dan CS dipaksa meng-upload apa adanya — hasilnya kartu
sampling jelek. Library CanHub yang jadi acuan fitur adalah library **Android-only**
(`androidx.activity`), jadi satu-satunya jalan yang benar adalah menaikkan kelas cropper internal
kita, bukan menambah dependency yang tidak bisa dikompilasi di commonMain.

**Analogi sederhana**: Cropper itu seperti meja pemotong kaca dengan bingkai tembaga yang
tertempel di atasnya. Dua sekolah berbeda:

1. **CanHub (crop-window paradigm)**: kaca diam, bingkainya yang digeser/di-resize.
2. **Fixed-viewport (paradigma kita)**: bingkai diam di tengah modal, kacanya yang digeser,
   di-zoom, diputar di bawahnya.

Kita mempertahankan paradigma (2) karena modal kita sempit, matematika export-nya deterministik
(viewport 1:1 dipetakan langsung ke output), dan sudah teruji. Yang kita porting dari CanHub
adalah **fiturnya**, bukan paradigmanya: rasio aspek preset, rotasi 90°, rotasi bebas dengan
auto-fit, flip H/V, bentuk oval, toggle grid, dan reset.

**Hasil akhir**: `MockupCropDialog.kt` (±550 baris) dengan toolbar lengkap, dan pipeline ekspor
Skia yang **menjamin WYSIWYG** — piksel yang dilihat pengguna = piksel yang tersimpan.

---

## 🧭 2. "Start dari Mana?" — Order of Operations

Kalau menulis dari nol, urutannya begini (jangan terbalik!):

1. **Langkah 0: Kunci paradigma & kontrak API publik.** Signature tetap
   `MockupCropDialog(bytes, onConfirm: (ByteArray) -> Unit, onDismiss)` — call site di
   `DealDetailDialog.kt` tidak boleh berubah. Fitur baru adalah detail internal.
2. **Langkah 1: Definisikan state transformasi.** `quarterTurns`, `flipH/V`, `freeAngle`,
   `ratio`, `shape`, `showGuidelines`, `zoom`, `offset`. Semua rotate-90 dan flip **tidak
   pernah masuk rumus pan/zoom** — lihat §3A.
3. **Langkah 2: Matematika viewport.** `scaleFor()`, `clamped()`, `centeredOffset()` — skala
   cover × zoom × faktor auto-fit rotasi, dan clamping bounding box.
4. **Langkah 3: Preview draw** (`Canvas` + `withTransform`) — gambar foto, baru overlay
   (scrim oval → grid → border → corner brackets). Overlay selalu terakhir supaya tidak
   tertimpa foto.
5. **Langkah 4: Toolbar UI** pakai katalog clay (`ClayIconButton`, `ClayButton`, `ClayTag`,
   ikon vektor dari `ClayIcons.kt`) — jangan pernah emoji.
6. **Langkah 5: Pipeline ekspor** (`cropToBytes()`) — replika matriks preview di atas
   `Surface` Skia berukuran output (§3D).
7. **Langkah 6: Ikon baru** (`IconRotateCcw/Cw`, `IconFlipHorizontal/Vertical`) ditambahkan ke
   katalog `designsystem/`, bukan di file fitur.
8. **Langkah 7: Kompilasi lintas target** (`compileKotlinJvm` + `compileKotlinWasmJs` + `jvmTest`).
   WasmJS sering menangkap API skiko yang beda versi (kita kena `PathDirection.CW` →
   `PathDirection.CLOCKWISE` dan API `Path.addOval` deprecated → `PathBuilder.addOval`).

## 🔬 3. Bedah Kode Blok per Blok

### A. Rotasi 90° & Flip = "Bakar ke Bitmap", Bukan Rumus Pan

```kotlin
val oriented = remember(source, quarterTurns, flipH, flipV) {
    source?.let { runCatching { reorientImage(it, quarterTurns, flipH, flipV) }.getOrNull() }
}
```

**Mental model**: rumus clamping `coerceIn(vw - imgW·f, 0f)` hanya benar untuk gambar
axis-aligned. Kalau rotasi 90° ikut masuk rumus, lebar-tinggi ikut tertukar, offset tua tidak
valid lagi, dan drag jadi melompat. Solusinya: setiap 90°/flip berubah, render bitmap baru via
`Surface` Skia (rotate + scale di sekitar pusat), lalu **reset zoom & offset ke tengah**
(`LaunchedEffect(oriented, ratio) { recentre() }`). Pipeline pan/zoom tidak pernah tahu soal
rotasi.

### B. Auto-Fit Rotasi Bebas (Rahasia Slider "Miring")

```kotlin
val bboxW = imgW * c + imgH * s   // bounding box lebar setelah diputar θ
val bboxH = imgW * s + imgH * c
return zoomValue * maxOf(vw / bboxW, vh / bboxH)
```

Memutar foto 30° membuat sudut-sudutnya "keluar" dari viewport dan menyisakan celah kosong.
CanHub menyebutnya auto-zoom; rumusnya: bounding box persegi panjang yang diputar θ adalah
`(w·cosθ + h·sinθ) × (w·sinθ + h·cosθ)`, jadi skala cover wajib dinaikkan supaya bbox itu tetap
menutup viewport. Perhatikan pada θ=0 rumus jatuh ke `max(vw/w, vh/h)` — skala cover biasa.
Clamping pun memakai bbox yang sama, jadi foto yang miring tidak bisa ditarik sampai berlubang.

### C. Pivot Rotation — Pusat Viewport Tetap Diam

```kotlin
val nx = cx - (cx - offset.x) * (newS / oldS)
```

Saat slider "Miring" digeser, skala berubah (auto-fit). Tanpa kompensasi ini foto "terlempar".
Rumus di atas menjaga titik pusat viewport tetap memetakan ke titik gambar yang sama —
konsep yang sama dipakai `updateZoom` (pivot zoom) dan gesture cubit (pivot centroid).

### D. Ekspor WYSIWYG — Replikasi Matriks Preview

```kotlin
canvas.scale(k, k)                                  // viewport → output (mis. 340dp → 1024px)
canvas.clipPath(oval)                               // bentuk oval = PNG transparan
canvas.rotate(freeAngle, vw / 2f, vh / 2f)          // pivot = pusat viewport
canvas.translate(offset.x, offset.y)
canvas.drawImageRect(img, src, dst(0,0,imgW·f,imgH·f), ...)
```

Ini blok paling penting di seluruh file. Preview digambar dengan urutan transformasi
`rotate(pivot pusat) → translate(offset) → drawImage(dst size = imgW·f)`. Pipeline ekspor
**menyalin urutan yang sama persis** ke kanvas output, hanya ditambah `scale(k)` di depan
(koordinat kanvas output = koordinat viewport). Karena itu WYSIWYG dijamin secara konstruksi,
bukan dicek dengan mata. Untuk oval, `clipPath` dipanggil **sebelum** menggambar supaya piksel
di luar oval tetap transparan (PNG alpha) — output non-persegi, seperti `CropShape.OVAL` CanHub.

### E. Rasio Aspek = Viewport yang Berubah Bentuk

Di paradigma fixed-viewport, "rasio 4:3" berarti viewport memanjang:

```kotlin
val vpWdp = if (ratio.x >= ratio.y) longSide else longSide * ratio.x / ratio.y
```

Semua rumus skala sudah membaca `vw`/`vh`, jadi rasio aspek hampir gratis. Setiap ganti rasio,
`recentre()` dipanggil supaya pengguna tidak menemukan offset lama yang tidak valid. Catatan
penting: gambar **tidak pernah gepeng** karena `drawImage` memakai dst size dengan rasio asli
gambar (`imgW·f × imgH·f`), bukan `FillBounds` ke viewport.

---

## 🏗️ 4. Technology & Approach ("The Why")

1. **Kenapa tidak pakai CanHub langsung?** Dia `.aar` Android (`androidx.activity`,
   `AppCompatActivity`). Modul UI kita `app/shared` meng-compile ke **iOS + JVM + WasmJS** —
   tidak ada target Android sama sekali. Implementasi native per-platform berarti 3× pekerjaan
   dan 3× permukaan bug, melanggar tujuan KMP.
2. **Kenapa `PathBuilder`, bukan `Path.addOval`?** Skiko 0.144 memindahkan mutasi Path ke
   `PathBuilder` dan API lama ditandai deprecated — dan di proyek ini build-nya **gagal**, bukan
   sekadar warning. Tersandung ini justru bukti kenapa DoD mewajibkan kompilasi multi-target.
3. **Kenapa `PathDirection.CLOCKWISE`, bukan `CW`?** Konstanta enum skiko versi ini bernama
   `CLOCKWISE`/`COUNTER_CLOCKWISE` (dicek via `javap` ke jar skiko). Kebiasaan menyalin nama
   dari dokumentasi versi lain = error kompilasi.
4. **Kenapa ekspor tetap PNG?** `DealUiEvent.UploadSamplingMockup` mengirim `mimeType` dari file
   pilihan pengguna; output selalu PNG menjaga kontrak byte↔mime tetap jujur dan mendukung
   alpha oval. (JPEG lebih kecil tapi tidak transparan dan menambah kasus mime mismatch.)
5. **Kenapa ikon baru masuk `ClayIcons.kt`?** Aturan §12: nol emoji/Unicode glyph (tofu di
   Wasm), dan katalog ikon adalah komponen `designsystem/` yang buta fitur — dipakai siapa pun
   nanti tanpa mengimpor package `deal`.

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Menukar urutan `rotate` dan `translate`** di pipeline ekspor. Matriks tidak komutatif:
   `rotate → translate` ≠ `translate → rotate`. Kalau hasil crop "bergeser" dibanding preview,
   9 dari 10 kali urutan ini tertukar.
2. **Rotasi 90° dimasukkan ke rumus pan/zoom** — offset lama menjadi tidak valid dan clamping
   salah. Selalu bakar ke bitmap + recentre.
3. **Lupa clamp saat slider miring berubah.** Auto-fit menaikkan skala; tanpa `clamped()` setelah
   `freeAngle = newAngle`, foto bisa menyingkap celah viewport → crop belang.
4. **`pointerInput` dengan closure basi.** `vw`/`vh`/`freeAngle` adalah nilai lokal biasa yang
   di-capture saat efek pertama jalan. Kuncinya `pointerInput(oriented, vw, vh, freeAngle)`
   supaya detector gesture di-restart saat nilai berubah. State `zoom`/`offset` aman karena
   dibaca lewat delegate `mutableStateOf` saat gesture terjadi.
5. **Clip sesudah menggambar** — `clipPath` hanya memengaruhi draw berikutnya. Oval yang
   di-clip setelah `drawImageRect` tidak memotong apa pun.
6. **Menambah literal `Color(0xFF…)` baru** untuk warna toolbar — melanggar Kontrak 1 design
   system. Overlay putih/hektap di atas foto adalah pengecualian pragmatis (di atas konten
   foto arbitrer), pola yang sama sudah dipakai cropper lama.

---

## ✅ 6. Verifikasi Mandiri

1. **Kompilasi multi-target** (sudah lulus):
   ```bash
   ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:jvmTest
   ```
2. **Tes auto-fit miring**: buka cropper, geser slider "Miring" ke ±45°. Tidak boleh ada celah
   kosong di dalam bingkai, dan objek di pusat bingkai tidak boleh bergeser.
3. **Tes rotasi 90°**: foto landscape 1920×1080 → putar kanan → viewport tetap terisi penuh,
   posisi kembali ke tengah (bukan condong ke pojok).
4. **Tes oval WYSIWYG**: pilih bentuk Oval, posisikan objek, klik "Gunakan Foto". Hasil PNG
   harus oval transparan — buka file hasilnya dan cek sudutnya benar-benar alpha.
5. **Tes rasio**: 9:16 pada foto landscape → foto harus tetap ter-zoom cover, bukan gepeng.
6. **Tes reset**: acak semua transformasi (miring 30°, flip V, zoom 3×) → tekan tombol reset →
   kembali ke kondisi awal yang identik dengan saat modal dibuka.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1 (Freeform)**: CanHub punya mode *freeform* — crop window bisa di-resize
      pengguna. Petakan: state apa yang harus ditambah, dan bagian mana dari `cropToBytes()`
      yang berubah kalau viewport tidak lagi fixed?
- [ ] **Tantangan 2 (EXIF)**: Foto HP sering menyimpan orientasi di metadata EXIF, bukan di
      piksel. Cari cara membaca EXIF di commonMain (atau actual set) dan putar `source` sekali
      di awal sebelum pipeline apa pun jalan.
- [ ] **Tantangan 3 (Snap Angle)**: Tambahkan snap — saat slider "Miring" dilepas dalam ±3° dari
      0°, animasikan kembali ke 0° (lihat `animateFloatAsState`).



