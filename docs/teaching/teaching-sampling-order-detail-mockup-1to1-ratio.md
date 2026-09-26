# 🎓 Modul Pembelajaran: Desain Responsif & Aspek Rasio 1:1 Mockup Kartu Detail SPK Sampling

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform Layouts, Aspect Ratio Constraint, Responsive Two-Column Sizing, Claymorphism Design System  
> **Prasyarat**: Dasar Compose layout (`Row`, `Column`, `Box`, `Modifier.aspectRatio`, `Modifier.weight`)  
> **Referensi Task**: Detail Order Sampling — Mockup Image 1:1 & Compact Sizing

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada modal dialog **Detail SPK Sampling** (`SamplingSpkDetailDialog`), ditampilkan dua informasi utama yang saling berdampingan:
1. **Visual Desain (Mockup)**: Foto tampak depan dan tampak belakang garmen.
2. **Spesifikasi Ukuran (POM Matrix)**: Tabel ukuran spesifikasi garmen (Lingkar dada, panjang baju, toleransi, dsb) serta catatan khusus klien.

Sebelum perbaikan:
- Kolom kiri (Visual Desain) diberi bobot `Modifier.weight(1.15f)` dan masing-masing kartu gambar diberi `Modifier.weight(1f)` dengan tinggi statis `height(155.dp)`.
- Pada layar monitor desktop/laptop yang lebar, kolom kiri ini meregang hingga lebih dari 450dp. Akibatnya, dua kartu foto menjadi persegi panjang horizontal (~220dp × 155dp) yang mendominasi modal secara berlebihan.
- Sementara itu, tabel POM Matrix di kolom kanan terdesak dan kekurangan ruang horizontal ketika menampilkan banyak ukuran (seperti S, M, L, XL, XXL).

### Analogi Sederhana
Bayangkan sebuah formulir paspor yang menyandingkan **Pasfoto** dan **Tabel Riwayat Perjalanan**:
- Pasfoto harus berbentuk bujur sangkar / standar (1:1), ringkas, dan proporsional.
- Jika kolom pasfoto memakan 60% halaman hanya untuk merentangkan foto menjadi lanskap lebar, tabel data riwayat perjalanan menjadi terjepit dan sulit dibaca.

### Hasil Akhir yang Diharapkan
- Mockup foto tampak depan & tampak belakang berukuran **kompak** dan memiliki rasio **1:1 (bujur sangkar)**.
- Kolom kiri hanya memakai lebar yang dibutuhkannya (`wrapContentWidth()`), memberikan sisa ruang yang leluasa (`weight(1f)`) kepada tabel spesifikasi POM.
- Pengguna tetap dapat melihat detail foto beresolusi tinggi dengan sekali klik via dialog zoom popup (`MockupZoomPreviewDialog`).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda diminta menata ulang kartu media di dalam layout dua kolom di Jetpack Compose / Compose Multiplatform:

1. **Langkah 1: Identifikasi Komponen Penentu Ukuran (Size Drivers)**
   - Cek apakah lebar kartu dikendalikan oleh parent (`weight(1.15f)`) atau oleh konten kartu itu sendiri.
   - Jika gambar harus berukuran tetap atau berorientasi rasio 1:1, ubah wadah gambar agar menerapkan `Modifier.aspectRatio(1f)`.

2. **Langkah 2: Tentukan Lebar Kartu & Padding**
   - Batasi lebar kartu menjadi ukuran kompak, misal `Modifier.width(120.dp)`.
   - Di dalam kartu, kontainer `Box` yang memiliki `Modifier.fillMaxWidth().aspectRatio(1f)` otomatis memiliki tinggi yang persis sama dengan lebarnya (~112dp × 112dp setelah padding).

3. **Langkah 3: Atur ContentScale & Fitur Interaktif**
   - Gunakan `ContentScale.Fit` di dalam `Image` agar mockup pakaian tidak terpotong (cropped) dan mempertahankan proporsi garmen aslinya.
   - Berikan ikon zoom (kaca pembesar) di pojok atas untuk menegaskan bahwa kartu dapat diklik untuk melihat pratinjau penuh.

4. **Langkah 4: Sesuaikan Distribusi Kolom Parent**
   - Ganti `Modifier.weight(1.15f)` pada kolom mockup menjadi `Modifier.wrapContentWidth()`.
   - Pastikan kolom tabel spesifikasi di kanannya memiliki `Modifier.weight(1f)` agar mengisi seluruh sisa ruang yang tersedia dengan rapi.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pengaturan Layout Dua Kolom di `ClientSamplingReferenceCard`

File: [ClientSamplingReferenceCard.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/components/ClientSamplingReferenceCard.kt#L125-L165)

```kotlin
// Layout 2 Kolom: Visual Desain 1:1 Kompak (Kiri) dan Spesifikasi POM + Memo (Kanan)
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
) {
    // Kolom Kiri: Visual Desain (Mockup Polaroid Side-by-Side 1:1)
    Column(
        modifier = Modifier.wrapContentWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconPackage(modifier = Modifier.size(13.dp), color = WeMadeColors.Primary)
            Text(
                text = "Visual Desain (Mockup)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            MockupPolaroidCard(
                label = "Tampak Depan",
                reference = frontRef,
                modifier = Modifier.width(160.dp),
                onClick = frontRef?.let { ref -> { zoomTarget = "Tampak Depan" to ref } }
            )
            MockupPolaroidCard(
                label = "Tampak Belakang",
                reference = backRef,
                modifier = Modifier.width(160.dp),
                onClick = backRef?.let { ref -> { zoomTarget = "Tampak Belakang" to ref } }
            )
        }
    }

    // Kolom Kanan: Spesifikasi Ukuran (POM Matrix) + Catatan Klien
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        // SizeChartTable & ClientNotesMemo
    }
}
```

**Mengapa blok ini ditulis begini?**
- `modifier = Modifier.wrapContentWidth()`: Kolom kiri tidak lagi memaksakan porsi 53% dialog, melainkan hanya selebar 2 kartu 120dp + jarak antarkartu (~246dp).
- `modifier = Modifier.weight(1f)` pada kolom kanan: Kolom tabel spesifikasi ukuran kini mendapatkan porsi lebar yang dominan dan lapang, sangat ramah untuk multi-size chart.

---

### Blok B: Kartu Mockup Polaroid Rasio 1:1

File: [ClientSamplingReferenceCard.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/components/ClientSamplingReferenceCard.kt#L225-L270)

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(ClayShapes.Tile)
        .background(WeMadeColors.SurfaceMuted),
    contentAlignment = Alignment.Center
) {
    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap,
                contentDescription = label,
                modifier = Modifier.fillMaxSize().padding(ClaySpacing.Xs),
                contentScale = ContentScale.Fit
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clayFlat(
                        shape = ClayShapes.Tile,
                        background = WeMadeColors.Surface.copy(alpha = 0.92f),
                        outline = WeMadeColors.Outline,
                        borderWidth = ClayBorder.Hairline
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconSearch(modifier = Modifier.size(10.dp), color = WeMadeColors.OnSurface)
            }
        }
        reference != null -> {
            Text(
                text = "Memuat…",
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
        else -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                IconImage(modifier = Modifier.size(20.dp), color = WeMadeColors.OnSurfaceMuted)
                Text(text = "Belum ada foto", fontSize = 9.sp, color = WeMadeColors.OnSurfaceMuted)
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- `aspectRatio(1f)`: Mengunci rasio kotak gambar tepat 1:1, tidak peduli apa resolusi gambar aslinya.
- `ContentScale.Fit` + `padding(ClaySpacing.Xs)`: Memastikan siluet pakaian terlihat utuh dari ujung kerah sampai ujung bawah tanpa terpotong (crop) dan tidak menempel keras ke border outline.
- Tombol zoom (`IconSearch`) berukuran kompak (20dp) di sudut kanan atas memberikan affordance intuitif bahwa thumbnail ini bisa diperbesar.

---

## ⚡ 4. Jebakan Pemula (Common Pitfalls)

1. **Jebakan Menggunakan `fillMaxWidth().aspectRatio(1f)` di Kolom Berbobot Besar**:
   - Jika kolom parent masih memakai `weight(1.15f)` (~450dp), memberi `aspectRatio(1f)` pada kartu justru akan membuat tingginya melonjak menjadi 220dp (makin raksasa, bukan makin kecil!).
   - Selalu pasangkan `aspectRatio(1f)` dengan pembatas lebar yang jelas (seperti `width(120.dp)` atau `widthIn(...)`) saat tujuannya adalah mengecilkan gambar.

2. **Jebakan Memakai `ContentScale.Crop` untuk Desain Garmen**:
   - Pada kartu ringkasan mockup teknis tekstil/konveksi, pemotongan (crop) dapat menyembunyikan detail saku, kancing, atau jenis kerah. Selalu prioritaskan `ContentScale.Fit`.

3. **Pelanggaran Aturan Ratchet Ukuran File**:
   - Sebelum diedit: 526 baris.
   - Setelah diedit: 519 baris.
   - Setiap refactoring wajib menjaga agar file tidak bertambah panjang melampaui ambang batas arsitektural.

---

## 🔬 5. Verifikasi & Checklist

- [x] Kode terkompilasi bersih di JVM (`./gradlew :app:shared:compileKotlinJvm`).
- [x] Kode terkompilasi bersih di WasmJS (`./gradlew :app:shared:compileKotlinWasmJs`).
- [x] Rasio wadah gambar terkunci 1:1 bujur sangkar.
- [x] Kolom tabel POM Matrix di sebelah kanan mendapatkan ruang yang proporsional dan nyaman dibaca.
- [x] Dialog zoom (`MockupZoomPreviewDialog`) tetap dapat dipanggil saat thumbnail diklik.
