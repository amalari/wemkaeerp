# 🎓 Modul Pembelajaran: Redesain Detail SPK dengan Claymorphism & Bento Grid

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform, Claymorphism Design System, Neo-Brutalism, Responsive Bento Grid, Smart Table Column Adaptation  
> **Prasyarat**: Dasar Compose UI, Pemahaman Modifier Chain, Konsep Design Tokens (`WeMadeTheme`)  
> **Referensi Task**: SPK Masuk Detail Modal Redesign (Claymorphism Style)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada versi awal dialog Detail SPK:
1. **Dead Space Berlebihan**: Slot foto mockup tampak depan & belakang ditumpuk vertikal dengan tinggi tetap (2 × 185dp = 370dp), sementara tabel POM di sampingnya hanya 3 baris (~100dp). Akibatnya, ada ruang putih kosong yang sangat luas di bawah tabel.
2. **Spreadsheet Bloat (Kolom Berlebih)**: Meskipun produk berukuran *All Size*, tabel memaksakan menampilkan 8 kolom (`ALL SIZE`, `S`, `M`, `L`, `XL`, `XXL`, `XXXL`) yang semuanya terisi tanda `-`. Tampilan menjadi melar dan terkesan "bolong".
3. **Mockup Terpotong**: Menggunakan `ContentScale.Crop` pada rasio sempit memotong foto baju/hoodie milik klien.
4. **Catatan Klien Tenggelam**: Hanya berupa sebaris teks kecil di pojok tanpa pembungkus visual yang jelas.

### Solusi: Bento Grid Bergaya Claymorphism
Dengan menerapkan prinsip **Bento Grid** dan estetika **Claymorphism + Neo-Brutalism** khas WeMade:
- Konten ditata dalam 2 kolom berimbang:
  - **Kiri**: Dua kartu foto "Polaroid Clay" (Tampak Depan & Belakang) side-by-side dengan aspect ratio pas, background kanvas netral, `ContentScale.Fit`, dan tombol zoom.
  - **Kanan**: Tabel POM cerdas (hanya menampilkan kolom ukuran yang aktif) + Sticky Memo catatan klien bertema neo-brutalisme.
- Ketinggian kedua kolom menjadi seimbang sempurna (~200dp), menghilangkan seluruh dead space.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu harus mendesain ulang komponen modal seperti ini dari nol:

1. **Langkah 1: Audit Layout & Keseimbangan Visual (Height Balancing)**
   - Hitung estimasi tinggi konten kiri vs konten kanan. Jika slot foto memakan 160dp, jangan menumpuknya menjadi 320dp jika konten sebelah kanannya hanya 100dp. Sejajarkan secara horizontal (`Row`).
2. **Langkah 2: Smart Data Normalization / Column Filtering**
   - Buat fungsi pembantu seperti `usedSizeColumns(matrix, sizeMode)` untuk memotong kolom-kolom yang tidak relevan sebelum dirender oleh UI.
3. **Langkah 3: Bangun Atomic Sub-Composables**
   - Buat `MockupPolaroidCard` untuk slot foto.
   - Buat `ClientNotesMemo` untuk sticky note catatan klien.
   - Perbarui `SizeChartTable` dengan styling token clay (`clayFlat`, `ClayShapes.Card`, `ClayBorder.Medium`).
4. **Langkah 4: Rakit di Parent Composable (`ClientSamplingReferenceCard`)**
   - Gabungkan ke dalam layout 2-kolom seimbang dengan `weight(1.15f)` dan `weight(1f)`.
5. **Langkah 5: Tinjau Batasan Container Modal (`SamplingSpkDetailDialog`)**
   - Pasang batasan lebar adaptif: `fillMaxWidth(0.72f).widthIn(min = 680.dp, max = 960.dp)` agar tidak terlalu sempit di layar laptop dan tidak melar berlebihan di monitor ultrawide.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Smart Column Filtering (`usedSizeColumns`)
```kotlin
private fun usedSizeColumns(matrix: List<SizeChartRow>, sizeMode: SizeMode): List<String> {
    if (sizeMode == SizeMode.ALL_SIZE) {
        return listOf("ALL SIZE")
    }
    val used = STANDARD_SAMPLING_SIZE_COLUMNS.filter { col ->
        matrix.any { it.values[col]?.isNotBlank() == true }
    }
    return used.ifEmpty { listOf("ALL SIZE") }
}
```
**Mengapa blok ini penting?**
- Jika mode ukuran adalah `ALL_SIZE`, kita langsung mengembalikan `listOf("ALL SIZE")`.
- Tabel tidak akan pernah merender kolom kosong `S`, `M`, `L`, `XL`, `XXL` jika order tersebut hanya All Size.
- Tabel menjadi kompak, padat, dan elegan.

### Blok B: Polaroid Clay Mockup Card (`MockupPolaroidCard`)
```kotlin
@Composable
private fun MockupPolaroidCard(
    label: String,
    reference: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    val bitmap = rememberMockupBitmap(reference)
    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Card,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(ClaySpacing.Xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(ClayShapes.Tile)
                .background(WeMadeColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap,
                        contentDescription = label,
                        modifier = Modifier.fillMaxWidth().height(155.dp),
                        contentScale = ContentScale.Fit
                    )
                    // Zoom icon badge di sudut kanan atas
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clayFlat(
                                shape = ClayShapes.Tile,
                                background = WeMadeColors.Surface.copy(alpha = 0.92f),
                                outline = WeMadeColors.Outline,
                                borderWidth = ClayBorder.Hairline
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconSearch(modifier = Modifier.size(12.dp), color = WeMadeColors.OnSurface)
                    }
                }
                // ... state memuat dan placeholder kosong
            }
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}
```
**Mengapa blok ini ditulis begini?**
- `ContentScale.Fit` memastikan foto baju klien utuh dan tidak terpotong.
- Di sudut kanan atas dipasang badge kecil dengan `IconSearch` untuk memberi sinyal visual jelas bahwa foto bisa di-klik untuk memperbesar (zoom).
- Label "Tampak Depan" / "Tampak Belakang" diletakkan di bawah gambar seperti foto Polaroid fisik.

### Blok C: Sticky Note Memo Catatan Klien (`ClientNotesMemo`)
```kotlin
@Composable
private fun ClientNotesMemo(notes: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Tile,
                background = WeMadeColors.WarningBg,
                outline = WeMadeColors.Warning.copy(alpha = 0.5f),
                borderWidth = ClayBorder.Hairline
            )
            .padding(ClaySpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.Top
    ) {
        IconNote(modifier = Modifier.size(14.dp), color = WeMadeColors.Warning)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Catatan Klien",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Warning
            )
            Text(
                text = notes,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = WeMadeColors.OnSurface,
                lineHeight = 14.sp
            )
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
- Memanfaatkan token semantik `WeMadeColors.WarningBg` (latar kuning hangat) dan `WeMadeColors.Warning` (oranye amber).
- Menggunakan ikon kanvas `IconNote` (bebas emoji tofu di Wasm).
- Memberikan aksen warna hangat yang memecah dominasi warna putih dan abu-abu di dialog.

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Jebakan Hardcoded Column Span**:
   - *Kesalahan*: Menulis tabel yang selalu mengasumsikan ada kolom S, M, L, XL, XXL.
   - *Dampaknya*: Tampilan terlihat seperti template kosong yang belum selesai dibuat.
   - *Solusi*: Selalu filter kolom berdasarkan data aktual atau `sizeMode`.
2. **Jebakan Rasio Foto Tetap (Fixed Square Crop)**:
   - *Kesalahan*: Menggunakan `Modifier.size(190.dp)` dengan `ContentScale.Crop`.
   - *Dampaknya*: Gambar pola baju rajut atau foto hoodie terpotong leher atau ujung bawahnya.
   - *Solusi*: Berikan ruang aspect ratio fleksibel dengan `ContentScale.Fit` di atas background netral `SurfaceMuted`.
3. **Mengabaikan Batas Ukuran File (File Size Budget)**:
   - *Kesalahan*: Menumpuk ratusan baris kode styling baru ke dalam file yang sudah 400+ baris.
   - *Pencegahan*: Refactoring ini justru mereduksi panjang `ClientSamplingReferenceCard.kt` dari 401 baris menjadi 388 baris, menjaga kepatuhan aturan §14.

---

## ✅ 5. Verifikasi & Tantangan Mandiri

1. **Uji Kompilasi Multiplatform**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm
   ./gradlew :app:shared:compileKotlinWasmJs
   ```
2. **Verifikasi Visual di Browser**:
   Buka `http://localhost:3000/sampling-order`, klik kartu di kolom "1. SPK Masuk", pastikan:
   - Dua kartu foto muncul bersebelahan di sisi kiri.
   - Tabel POM di sisi kanan hanya menampilkan kolom All Size dan Jumlah Sampel dengan badge.
   - Catatan klien muncul sebagai memo kuning hangat di bawah tabel POM.
   - Tidak ada dead space putih di bawah tabel.
