# 🎓 Modul Pembelajaran: Layout Vertikal Mockup & Size Chart Data Riil pada Detail SPK Sampling

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform Layout, Weighted Column Layout, Clay Design System, BE Data Flow Verification (Ktor → Codec → UI)
> **Prasyarat**: Paham dasar Compose `Row`/`Column`/`weight()`, aliran data `SamplingOrder` dari BE ke UI, dan Clay Design System (ClaySpacing, clayFlat)
> **Referensi Task**: Penyesuaian kartu "Referensi dari Klien (Deal)" pada dialog Detail SPK (`/sampling-order`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah Nyata**: Pada dialog Detail SPK, dua slot mockup (Tampak Depan & Belakang) dan tabel matriks
ukuran POM dipaksakan berdampingan dalam satu `Row`. Akibatnya:
1. Slot mockup hanya dapat lebar 160dp — foto desain yang seharusnya menjadi acuan pola jadi kecil.
2. Tabel ukuran dihimpit — kolomnya memakai lebar tetap `46.dp` sehingga ketika tabel mendapat ruang
   lebih lebar, sisanya kosong; ketika sempit, kolom saling berdesak.

**Analogi Sederhana**: Meja kerja pola di pabrik. Kalau foto desain dan tabel ukuran dipaksa
berjajar horizontal di meja sempit, keduanya kecil-kecil semua. Lebih baik foto-foto ditumpuk
di kiri (diambil satu per satu), dan tabel ukuran membentang selebar sisa meja.

**Hasil Akhir**: Mockup Depan & Belakang ditumpuk **vertikal (atas-bawah)** di kiri; tabel ukuran
memenuhi sisa lebar dengan kolom yang terdistribusi merata (`weight(1f)`).

---

## 🧭 2. "Start dari Mana?" — Order of Operations

1. **Langkah 0: Verifikasi asumsi "data tidak muncul" SEBELUM menyentuh UI.**
   Ambil token demo, curl `GET /api/tenant/sampling/orders`, dan inspeksi `sizeMatrix` per SPK.
   Temuan penting: SPK-SMP-0002/0001 kosong karena memang dibuat manual tanpa nilai ukuran —
   **bukan bug pipe data**. SPK lain (0006, 0010, dst.) terisi. Tanpa langkah ini, kita bisa
   salah memperbaiki "backend" yang sebenarnya sehat.
2. **Langkah 1: Ubah struktur layout** di `ClientSamplingReferenceCard.kt` — bungkus dua
   `MockupSlot` dalam satu `Column`, tabel dapat `Modifier.weight(1f)`.
3. **Langkah 2: Perbesar slot** (`MOCKUP_SLOT_WIDTH` 160→190dp, tinggi 170→185dp) karena slot
   kini tidak lagi bersaing ruang horizontal dengan tabel.
4. **Langkah 3: Jadikan kolom tabel elastis** — ganti `Modifier.width(46.dp)` dengan
   `Modifier.weight(1f)` di `SizeChartLine`.
5. **Langkah 4: Kompilasi multi-target** (`compileKotlinJvm`, `compileKotlinWasmJs`,
   `compileKotlinJs`) + `jvmTest`.
6. **Langkah 5: Verifikasi visual dengan mata** — jalankan ulang webpack dev server dan buka
   dua kasus: SPK satu kolom (`ALL SIZE`) dan SPK multi-kolom (`S`/`M`/`L`).

---

## 🔬 3. Bedah Kode Blok per Blok

### Blok A — Penumpukan vertikal (`ClientSamplingReferenceCard.kt`)

```kotlin
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {   // tumpukan atas-bawah
        MockupSlot(label = "Tampak Depan", reference = frontRef, modifier = Modifier.width(MOCKUP_SLOT_WIDTH), ...)
        MockupSlot(label = "Tampak Belakang", reference = backRef, modifier = Modifier.width(MOCKUP_SLOT_WIDTH), ...)
    }
    SizeChartTable(matrix = order.sizeMatrix, modifier = Modifier.weight(1f))  // sisa lebar penuh
}
```

**Mental model**: `Row` = pembagi horizontal. Anak pertama (`Column` mockup) ambil lebar tetap
190dp; anak kedua (tabel) dengan `weight(1f)` menyerap **seluruh sisa** lebar. Slot mockup tidak
diberi weight karena ukurannya kontrak desain (tetap); tabel adalah satu-satunya elemen fleksibel.

### Blok B — Kolom elastis (`SizeChartLine`)

```kotlin
columns.forEach { col ->
    Text(
        text = values[col]?.takeIf { it.isNotBlank() } ?: "-",
        modifier = Modifier.weight(1f),   // ← dulu: Modifier.width(46.dp)
        textAlign = TextAlign.Center, maxLines = 1, ...
    )
}
```

`weight(1f)` membuat semua kolom ukuran berbagi sisa lebar secara sama rata, apakah ada 1 kolom
(`ALL SIZE` saja) atau 7 kolom (`ALL SIZE`…`XXXL`). Fallback "-" tetap untuk sel kosong.

### Blok C — Dari mana data itu berasal? (verifikasi, bukan perubahan)

```
PostgreSQL `sampling_orders.size_matrix` (JSONB)
  → PostgresSamplingOrderRepository.parseSizeMatrix()        (server)
  → GET /api/tenant/sampling/orders → SamplingOrderCodec.encode("sizeMatrix")
  → SamplingApiClient.getOrders() → SamplingOrder.sizeMatrix (client)
  → SizeChartTable(matrix = order.sizeMatrix)                (UI read-only)
```

Data yang tampil **sudah riil dari BE**. Sel "-" muncul hanya ketika nilai di DB memang kosong
(SPK buatan manual yang belum diisi size chart-nya oleh Sales di dialog Deal).

---

## 🚀 4. Technology & Approach ("The Why")

- **Kenapa `weight(1f)`, bukan `fillMaxWidth()` atau `Arrangement.SpaceEvenly`?**
  `weight` adalah satu-satunya mekanisme Compose yang membagi ruang *sisa* secara proporsional
  dan menjaga seluruh baris tabel selaras (kolom ke-N semua baris berada pada x yang sama).
  `SpaceEvenly` menyisakan gap, bukan merentang sel.
- **Kenapa slot mockup tetap lebar tetap, bukan ikut weighted?** Ukuran foto acuan adalah
  keputusan desain yang stabil (ukuran = token; Kontrak 7 design system). Yang fleksibel hanya
  konten yang jumlahnya berubah-ubah (kolom ukuran).
- **Kenapa verifikasi BE dulu?** Prinsip debugging KMP: bukti paling murah adalah respons HTTP
  mentah. `curl` + token demo (`POST /api/public/auth/demo?tenantSlug=...&role=...`) menyelesaikan
  perdebatan "UI-nya yang salah atau datanya" dalam satu menit.
- **Risiko alternatif**: `SubcomposeLayout` kustom untuk tabel = over-engineering; `fillMaxWidth`
  per sel tanpa weight = kolom tak selaras antar baris.

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Menuduh pipe data rusak sebelum membuktikannya.** Sel "-" bukan berarti decoder gagal —
   `SamplingOrderCodec` sudah membaca `id`, `pomName`, `values` dengan benar. Cek isi DB dulu.
2. **Lupa bahwa `weight()` hanya ada di `RowScope`/`ColumnScope`.** `Modifier.weight(1f)` tidak
   terkompilasi di luar scope Row/Column — jangan menyalinnya ke komponen yang dirender di `Box`.
3. **Satu kontrak, satu elemen fleksibel.** Jika slot mockup dan tabel sama-sama diberi weight,
   layout berubah setiap kali jumlah kolom berubah. Beri weight hanya pada elemen yang memang
   harus menyerap sisa ruang.
4. **Lupa restart webpack dev server yang yatim.** Proses webpack di :3000 bisa menjadi *orphan*
   (parent Gradle-nya sudah mati) sehingga menyajikan build lama tanpa watch. Solusi:
   `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun --continuous` (jalur resmi `dev.sh wasm`).
5. **Verifikasi hanya lewat kompilasi.** Perubahan `Modifier` chain tidak selalu gagal kompilasi
   tapi bisa merusak layout — buka dialognya dan lihat dengan mata (sudah dilakukan untuk kasus
   single-column dan multi-column).

---

## ✅ 6. Verifikasi

- Kompilasi: `compileKotlinJvm`, `compileKotlinWasmJs`, `compileKotlinJs` → **BUILD SUCCESSFUL**;
  `jvmTest` → hijau. (`assembleAndroidMain` gagal **pra-eksisting** di `MockupCropDialog.kt`
  — referensi Skia di commonMain, tercatat di `planning-deal-sampling-mockup-views-and-size-chart.md`.)
- Visual via browser (build terbaru, login demo):
  - **SPK-SMP-0006** (satu kolom): mockup Depan di atas, Belakang di bawah; tabel menampilkan
    `Jumlah Sampel (pcs) = 2`, `Lebar Dada = 55`, `Panjang Baju = 60` — data riil BE.
  - **SPK-SMP-0010** (multi-kolom): kolom `S`/`M`/`L` terentang merata dengan nilai
    48/51/54 dan 56/58/60 — `weight(1f)` bekerja pada N kolom.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: Tambahkan indikator "tabel kosong" ketika `order.sizeMatrix` tidak punya
  nilai terisi sama sekali (hint: `matrix.any { row -> row.values.any { it.value.isNotBlank() } }`).
- [ ] **Tantangan 2**: Jadikan lebar slot mockup responsif terhadap lebar dialog (hint:
  `BoxWithConstraints` + proporsi, tetap lewat token spacing Clay).
- [ ] **Tantangan 3**: Telusuri apa yang terjadi jika baris qty diisi pada kolom yang POM-nya
  kosong — temukan `sanitizeSamplingMatrix()` di `SamplingSizeMatrix.kt` dan jelaskan mengapa
  sanitasi itu ada.
