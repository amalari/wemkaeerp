# Teaching — Mockup Thumbnail Kanan Diperbesar + Navigasi Arrow Carousel di Dalam Gambar pada Kartu Kanban

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform In-Image Overlay Controls, Canvas Vector Chevrons, Dynamic Dot Indicators, Clean Card Balancing  
> **Prasyarat**: Paham dasar Compose layouting (`Box`, `Row`, `Column`, `weight`), Canvas drawing API, serta Design System Claymorphism WeMade.

---

## 1. Start dari Mana? (Order of Operations)

Saat kamu diminta menyederhanakan dan memperbesar kontrol visual mockup kartu Kanban:

1. **Pruning Tombol Teks Eksternal**:
   - Label teks seperti "Depan" dan "Belakang" di luar gambar memakan ruang vertikal kartu dan membuat layout terasa seperti formulir ketimbang katalog visual produk.
   - Pindahkan kontrol navigasi langsung ke **dalam gambar** (*in-image overlay*).
2. **Perbesar Dimensi Thumbnail**:
   - Karena ruang bawah thumbnail kini bersih dari tombol teks, ukuran tile gambar dapat ditingkatkan (misalnya dari `72.dp` menjadi `90.dp`).
   - Ukuran `90.dp` serasi 1:1 dengan tinggi total kolom teks di sebelah kiri (client name + style name + tag baris).
3. **Desain Arrow Navigation Tanpa Unicode Emoji**:
   - Jangan gunakan karakter emoji `◀` atau `▶` di Text karena akan merender kotak kosong (*tofu*) di browser WebAssembly/Skiko.
   - Gambar panah chevron `<` dan `>` yang presisi menggunakan `Canvas` dengan `StrokeCap.Round` dan `StrokeJoin.Round`.
4. **Overlay Transparan & Klik Responsif**:
   - Bungkus tombol panah dalam pill melingkar dengan latar `WeMadeColors.SurfaceDark.copy(alpha = 0.65f)`.
   - Pastikan pengguna tetap bisa mengklik tombol panah atau menyentuh area gambar secara langsung untuk membalik foto.

---

## 2. Bedah Kode Blok per Blok

### Blok A: Thumbnail Diperbesar (90dp) & Tombol Panah In-Image

```kotlin
@Composable
private fun SamplingMockupThumbnail(
    bitmap: ImageBitmap?,
    isBackView: Boolean,
    canToggle: Boolean,
    onToggle: () -> Unit
) {
    val imageSize = 90.dp
    Box(
        modifier = Modifier
            .size(imageSize)
            .clip(ClayShapes.Tile)
            .background(WeMadeColors.SurfaceMuted)
            .clickable(enabled = canToggle, onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = if (isBackView) "Mockup Tampak Belakang" else "Mockup Tampak Depan",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            IconInbox(modifier = Modifier.size(24.dp), color = WeMadeColors.OnSurfaceMuted)
        }

        if (canToggle) {
            // Tombol Arrow Kiri (Previous)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 3.dp)
                    .size(22.dp)
                    .clip(ClayShapes.Pill)
                    .background(WeMadeColors.SurfaceDark.copy(alpha = 0.65f))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                CarouselChevron(
                    isNext = false,
                    modifier = Modifier.size(12.dp),
                    color = WeMadeColors.Surface
                )
            }

            // Tombol Arrow Kanan (Next)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 3.dp)
                    .size(22.dp)
                    .clip(ClayShapes.Pill)
                    .background(WeMadeColors.SurfaceDark.copy(alpha = 0.65f))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                CarouselChevron(
                    isNext = true,
                    modifier = Modifier.size(12.dp),
                    color = WeMadeColors.Surface
                )
            }

            // Indikator titik carousel di bawah gambar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(WeMadeColors.SurfaceDark.copy(alpha = 0.55f))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = if (!isBackView) 10.dp else 4.dp, height = 4.dp)
                            .background(
                                color = if (!isBackView) WeMadeColors.Primary else WeMadeColors.Surface.copy(alpha = 0.6f),
                                shape = ClayShapes.Pill
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(width = if (isBackView) 10.dp else 4.dp, height = 4.dp)
                            .background(
                                color = if (isBackView) WeMadeColors.Primary else WeMadeColors.Surface.copy(alpha = 0.6f),
                                shape = ClayShapes.Pill
                            )
                    )
                }
            }
        }
    }
}
```

- `size(90.dp)`: Gambar terlihat jauh lebih jelas dan detail sablon/rajut terlihat sekilas oleh operator tanpa harus membuka dialog detail.
- `Alignment.CenterStart` dan `Alignment.CenterEnd`: Meletakkan tombol chevron tepat di garis tengah vertikal kiri-kanan gambar dengan padding pengaman `3.dp`.

---

### Blok B: Chevron Canvas Vektor Murni (`CarouselChevron`)

```kotlin
@Composable
private fun CarouselChevron(isNext: Boolean, modifier: Modifier = Modifier, color: Color = WeMadeColors.Surface) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density
        val path = Path().apply {
            if (isNext) {
                moveTo(w * 0.35f, h * 0.22f)
                lineTo(w * 0.65f, h * 0.50f)
                lineTo(w * 0.35f, h * 0.78f)
            } else {
                moveTo(w * 0.65f, h * 0.22f)
                lineTo(w * 0.35f, h * 0.50f)
                lineTo(w * 0.65f, h * 0.78f)
            }
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
```

- Menggunakan `Path` Canvas dengan titik relatif (`w * 0.35f`, dsb.) menjamin panah tetap tajam di layar resolusi berapa pun (Retina, 4K, maupun monitor pabrik beresolusi standar).
- Bebas dari dependensi OS font atau emoji.

---

## 3. Technology & Approach ("The Why")

| Keputusan Desain / Teknis | Alternatif yang Ditolak | Risiko Bila Memakai Alternatif |
|---|---|---|
| **Arrow Overlay di Dalam Gambar** | Tombol teks "Depan / Belakang" di luar gambar | Menguras tinggi kartu, teks terasa kaku, dan kartu Kanban bertambah tinggi tidak seragam |
| **Ukuran Gambar 90.dp** | Tetap 64dp atau 72dp | Pada kartu 300dp, thumbnail kecil sulit dilihat detail motif rajut/desainnya oleh operator |
| **Vektor Canvas Chevron** | Karakter Unicode `‹` / `›` di Text Composable | Rentan tofu rendering (`▯`) di Skiko/Wasm di beberapa browser OS Linux/Android lama |
| **Indicator Dot Pill di Bawah** | Tanpa indikator sama sekali | Pengguna tidak memiliki kepastian visual apakah saat ini sedang melihat sisi depan atau belakang |

---

## 4. Verifikasi & Pengujian

- Jalankan kompilasi Wasm:
  ```bash
  ./gradlew :app:shared:compileKotlinWasmJs
  ```
- Jalankan suite unit test sampling:
  ```bash
  ./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.sampling.*"
  ```
- Kedua perintah berhasil `BUILD SUCCESSFUL` tanpa warning atau error.
