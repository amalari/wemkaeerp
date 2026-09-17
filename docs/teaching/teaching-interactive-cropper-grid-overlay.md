# Modul Pembelajaran: Implementasi Image Cropper Interaktif 1:1 dengan Rule-of-Thirds Grid & Pivot Zoom

**Target Audiens**: Junior Developer  
**Topik**: Skia / Compose Multiplatform Image Cropper, Transform Gestures, Rule-of-Thirds Dashed Grid Overlay, dan Math Centering & Clamping

---

## 1. Start Dari Mana? (Order of Operations)

Saat membangun fitur pemotongan (cropping) gambar di UI desktop & mobile multiplatform, jangan langsung terburu-buru memotong byte array. Urutan pengerjaannya adalah:

1. **State & Viewport Geometry**:
   - Tentukan ukuran kotak crop (misal `340.dp` persegi 1:1).
   - Hitung faktor skala dasar (`scaleFor`) agar sisi terpendek gambar selalu mengisi penuh kotak viewport (tidak boleh ada ruang kosong di dalam kotak crop).
2. **Initial Centering & Clamping**:
   - Jangan letakkan gambar di `(0, 0)` secara default karena jika gambar portrait atau landscape, gambar akan condong ke pojok kiri atas.
   - Hitung `(previewPx - imgW * scale) / 2` agar objek utama di tengah langsung tampak.
3. **Pivoted Zoom & Pan Gestures**:
   - Ketika pengguna melakukan pinch-zoom atau menggeser slider, titik pusat pembesaran harus berada di tengah kotak crop (pivot center), bukan pojok kiri atas.
   - Batasi translasi (`clamping`) agar gambar tidak pernah tertarik keluar dari batas kotak crop.
4. **Visual Guides (Dashed Lines & Corner Brackets)**:
   - Pengguna butuh kepastian area mana yang akan terpotong. Tambahkan overlay garis putus-putus (*dashed lines*) rule-of-thirds dan bracket siku di keempat sudut.
5. **Skia Pixel Extraction (The Export Pipeline)**:
   - Konversi koordinat relatif viewport menjadi koordinat piksel asli gambar sumber (`sourcePx = -offset * (imgSize / (viewportSize * zoom))`).
   - Ekstrak subset piksel via Skia `makeSubset` dan simpan sebagai PNG/JPEG byte array.

---

## 2. Bedah Kode Blok per Blok

### A. Auto-Centering Saat Gambar Pertama Kali Terbuka
```kotlin
val initialOffset = remember(source, previewPx) {
    if (source == null) Offset.Zero
    else {
        val imgW = source.width.toFloat()
        val imgH = source.height.toFloat()
        val f = maxOf(previewPx / imgW, previewPx / imgH)
        clamped(
            Offset((previewPx - imgW * f) / 2f, (previewPx - imgH * f) / 2f),
            zoomValue = 1f
        )
    }
}
```
**Mental Model**:
- Tanpa perhitungan ini, gambar beresolusi 2000x1000 akan menampilkan sisi kiri atasnya saja.
- Dengan membagi selisih lebar/tinggi dengan 2, titik tengah gambar berimpit sempurna dengan titik tengah kotak crop.

---

### B. Pivoted Zoom (Zooming Terhadap Titik Pusat)
```kotlin
fun updateZoom(newZoom: Float) {
    val oldZoom = zoom
    val clampedZoom = newZoom.coerceIn(1f, 3f)
    if (clampedZoom == oldZoom) return

    val centerX = previewPx / 2f
    val centerY = previewPx / 2f
    val scaleFactor = clampedZoom / oldZoom

    // Pindahkan offset sedemikian rupa sehingga titik tengah tetap berada di posisi yang sama
    val newOffsetX = centerX - (centerX - offset.x) * scaleFactor
    val newOffsetY = centerY - (centerY - offset.y) * scaleFactor

    zoom = clampedZoom
    offset = clamped(Offset(newOffsetX, newOffsetY), clampedZoom)
}
```
**Mental Model**:
- Jika kita hanya mengubah `zoom` tanpa menggeser `offset`, gambar akan membesar menjauh dari pojok kiri atas `(0,0)`, sehingga objek yang sedang diamati pengguna bergeser keluar layar.
- Rumus `center - (center - offset) * ratio` mengunci titik tengah viewport agar tetap diam di tengah saat pembesaran berlangsung.

---

### C. Visual Grid: Dashed Border & Rule-of-Thirds Grid
```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
    val gridDashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
    val guideColor = WeMadeColors.Primary
    val thirdW = size.width / 3f
    val thirdH = size.height / 3f

    // 1. Grid Rule-of-Thirds Vertikal & Horizontal
    drawLine(color = guideColor.copy(alpha = 0.55f), start = Offset(thirdW, 0f), end = Offset(thirdW, size.height), pathEffect = gridDashEffect)
    drawLine(color = guideColor.copy(alpha = 0.55f), start = Offset(thirdW * 2f, 0f), end = Offset(thirdW * 2f, size.height), pathEffect = gridDashEffect)
    drawLine(color = guideColor.copy(alpha = 0.55f), start = Offset(0f, thirdH), end = Offset(size.width, thirdH), pathEffect = gridDashEffect)
    drawLine(color = guideColor.copy(alpha = 0.55f), start = Offset(0f, thirdH * 2f), end = Offset(size.width, thirdH * 2f), pathEffect = gridDashEffect)

    // 2. Border Luar Putus-putus
    drawRect(color = guideColor, size = size, style = Stroke(width = 3.dp.toPx(), pathEffect = dashEffect))

    // 3. Corner Brackets Siku Tebal di 4 Sudut (Visual Anchor)
    // Digambar dengan Stroke padat 5dp untuk memberikan kesan "Viewfinder Kamera"
}
```

---

## 3. Technology & Approach ("The Why")

1. **Kenapa Canvas PathEffect dibanding Box Border?**
   `Modifier.border()` standar Compose Multiplatform belum mendukung pattern putus-putus (`dashPathEffect`). Menggunakan `Canvas` native Skia memberikan kebebasan menggambar garis bantu fotografi (rule-of-thirds) yang tajam dan presisi pada level piksel.
2. **Kenapa Menghindari Literal Emoji untuk Tombol Zoom?**
   Sesuai aturan arsitektur (§12), emoji Unicode seperti `➖` atau `➕` akan menghasilkan *tofu* (`▯`) pada target web Wasm/Skiko karena ketiadaan font emoji sistem operasi. Kita menggunakan vector canvas `IconMinus` dan `IconPlus` yang 100% konsisten di semua platform.

---

## 4. Jebakan Pemula (Common Pitfalls)

1. **Lupa Clamping Saat Drag**:
   Jika batas offset tidak di-clamp dengan `coerceIn(previewPx - imgW * scale, 0f)`, pengguna bisa menarik foto sampai hilang ke luar kotak, menghasilkan gambar hasil crop yang belang hitam / transparan.
2. **Silent Dismiss Saat Encoding Gagal**:
   Jika Skia `encodeToData` mengembalikan `null` (misal memori terbatas atau format color space aneh), jangan langsung membatalkan operasi tanpa hasil. Selalu sediakan fallback ke byte array asli agar foto pengguna tidak hilang.

---

## 5. Verifikasi Mandiri

1. **Tes Auto Centering**: Pilih foto banner horizontal (misal 1920x1080). Pastikan saat modal muncul, bagian tengah foto langsung berada tepat di dalam kotak crop.
2. **Tes Pivot Zoom**: Geser slider zoom ke 3x. Pastikan objek yang berada di tengah kotak tidak kabur ke pojok kiri atas.
3. **Tes Drag Boundary**: Coba seret foto ke segala arah. Pastikan foto tidak pernah bisa ditarik sampai menyisakan celah kosong di dalam kotak crop.
