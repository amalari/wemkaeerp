# 🎓 Modul Pembelajaran: Scoping Spesifikasi SPK Sampling per-Ukuran (1 SPK = 1 Ukuran) pada Dialog Detail SPK

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design, Compose Multiplatform, UI State Scoping, Size Chart Matrix  
> **Prasyarat**: Dasar Kotlin Multiplatform, pemahaman Compose Multiplatform, domain sampling ERP konveksi  
> **Referensi Task**: Fix data scoping Detail SPK (SPK-SMP-0051) agar menampilkan spesifikasi dan kuantitas per-ukuran (Size S, 2 pcs), bukan data akumulatif All Size  

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Ketika sebuah pabrik garmen/rajut memecah SPK dari kesepakatan Deal yang multi-ukuran (misalnya ALL SIZE 2 pcs dan S 2 pcs), setiap surat perintah kerja (SPK) di lantai produksi memegang tanggung jawab spesifik **1 SPK = 1 ukuran**.
- SPK 1 (`SPK-SMP-0050`): Ukuran `ALL SIZE` (2 pcs).
- SPK 2 (`SPK-SMP-0051`): Ukuran `S` (2 pcs).

Pada implementasi sebelumnya:
1. Kartu Kanban di luar sudah menampilkan tag ukuran `S • 2 Pcs`.
2. Namun saat kartu diklik untuk membuka **Detail SPK**, komponen `ClientSamplingReferenceCard` masih mengambil data akumulatif secara global:
   - Menghitung `totalQty` dengan menjumlahkan seluruh kolom aktif di matriks (`2 + 2 = 4 Pcs Sample`).
   - Tag ukuran di badge atas menampilkan `ALL SIZE (Satu Ukuran)` karena membaca `order.sizeMode.displayName` alih-alih `order.sizeLabel`.
   - Tabel Point of Measurement (POM) memeriksa `if (sizeMode == ALL_SIZE) return listOf("ALL SIZE")`, sehingga untuk `SPK-SMP-0051` yang sebenarnya adalah ukuran `S`, tabel malah menampilkan kolom `ALL SIZE` dengan ukuran dada 60 cm alih-alih 10 cm.

### Analogi Sederhana
Bayangkan seorang koki di dapur restoran menerima slip pesanan meja khusus meja 5 untuk "Steak Rare". Tetapi di slip tersebut tercantum total 4 piring (termasuk 2 Steak Well Done meja sebelah), dan resep yang dicetak adalah panduan memasak Well Done. Koki akan bingung dan berisiko salah potong atau salah masak. Setiap SPK harus memuat resep dan jumlah piring khusus untuk varian yang sedang dikerjakannya.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

1. **Langkah 1: Cek Sumber Kebenaran Data (Domain Entity & Invarian)**
   - Periksa `SamplingOrder.sizeLabel` vs `order.sizeMode`.
   - Pastikan SPK yang memiliki `sizeLabel` (per-ukuran) dianggap sebagai pesanan spesifik satu ukuran, di mana kuantitasnya adalah `order.sampleQuantity`.

2. **Langkah 2: Sinkronisasi Use Case Pemecahan (`PublishSamplingSpkFromDealUseCase`)**
   - Pastikan saat use case memecah root order dan child order, `sizeMode` diatur secara tepat: `ALL_SIZE` jika ukurannya `"ALL SIZE"`, dan `MULTI_SIZE` jika berupa ukuran grading (`S`, `M`, `L`, dst.).

3. **Langkah 3: Perbaiki Scoping di Kartu Referensi Klien (`ClientSamplingReferenceCard`)**
   - Hitung `totalQty`: jika `order.sizeLabel` ada, gunakan `order.sampleQuantity`; jika legacy multi-size tanpa `sizeLabel`, gunakan `calculateTotalSampleQuantity(order.sizeMatrix)`.
   - Format label badge ukuran: jika `order.sizeLabel` ada, tampilkan `"Size ${order.sizeLabel}"` (atau `"All Size (Satu Ukuran)"` jika `ALL SIZE`).
   - Lewatkan `order.sizeLabel` ke tabel matriks POM `SizeChartTable`.

4. **Langkah 4: Filter Kolom Matriks POM (`usedSizeColumns`)**
   - Jika `sizeLabel` ada, kembalikan hanya satu kolom yang cocok (`listOf(sizeLabel)`).
   - Jangan fallback ke `ALL SIZE` jika SPK tersebut ditujukan khusus untuk ukuran `S`.

5. **Langkah 5: Terapkan ke Komponen Terkait (`SamplingSpkDetailDialog` & `SpkDetailPanel`)**
   - Pada judul modal: tampilkan `(Size S)` pada subtitle style pakaian.
   - Pada panel lembar kerja operator (`SpkDetailPanel`): tampilkan jumlah `2 Pcs • Size S` dan kolom ukuran `S`.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Scoping Kuantitas & Tag Ukuran di `ClientSamplingReferenceCard.kt`

```kotlin
val isSplitSize = !order.sizeLabel.isNullOrBlank()
val totalQty = if (isSplitSize) {
    order.sampleQuantity
} else {
    calculateTotalSampleQuantity(order.sizeMatrix, order.sampleQuantity)
}
```
**Mengapa begini?**
- `order.sizeMatrix` menyimpan data seluruh tabel dari Deal awal. Jika Deal awal punya 3 ukuran aktif (S: 2, M: 2, L: 2), fungsi `calculateTotalSampleQuantity` akan menjumlahkan semuanya menjadi 6 pcs.
- Namun untuk SPK per-ukuran, SPK ini hanya memproduksi ukuran miliknya sendiri (`order.sampleQuantity = 2`).

```kotlin
val sizeTagLabel = when {
    isSplitSize -> {
        val label = order.sizeLabel.orEmpty().trim()
        if (label.equals("ALL SIZE", ignoreCase = true)) {
            "All Size (Satu Ukuran)"
        } else {
            "Size $label"
        }
    }
    else -> order.sizeMode.displayName
}
ClayTag(
    text = sizeTagLabel,
    tint = WeMadeColors.Primary,
    leading = { IconRuler(modifier = Modifier.size(11.dp), color = WeMadeColors.Primary) }
)
ClayBadge(text = "$totalQty Pcs Sample", tint = WeMadeColors.Info)
```
**Mengapa begini?**
- Menghindari hardcoded `order.sizeMode.displayName` yang akan selalu bertuliskan `"All Size (Satu Ukuran)"` jika `sizeMode == ALL_SIZE`.
- Sekarang, untuk SPK-SMP-0051 akan tampil tag `Size S` dan badge `2 Pcs Sample`.

---

### Blok B: Filter Kolom Matriks POM di `SizeChartTable`

```kotlin
private fun usedSizeColumns(matrix: List<SizeChartRow>, sizeMode: SizeMode, sizeLabel: String?): List<String> {
    if (!sizeLabel.isNullOrBlank()) {
        val target = sizeLabel.trim()
        val match = STANDARD_SAMPLING_SIZE_COLUMNS.firstOrNull { it.equals(target, ignoreCase = true) }
            ?: target
        return listOf(match)
    }
    if (sizeMode == SizeMode.ALL_SIZE) {
        return listOf("ALL SIZE")
    }
    val used = STANDARD_SAMPLING_SIZE_COLUMNS.filter { col ->
        matrix.any { it.values[col]?.isNotBlank() == true }
    }
    return used.ifEmpty { listOf("ALL SIZE") }
}
```
**Mengapa begini?**
- Jika SPK memiliki `sizeLabel = "S"`, kita hanya ingin kolom `S` yang tampil di tabel POM modal kerja.
- Operator dan teknisi CAM yang membuka SPK `SPK-SMP-0051` langsung melihat ukuran parameter fisik spesifik untuk ukuran `S`, bukan tertukar dengan ukuran `ALL SIZE`.

---

### Blok C: Header Kolom yang Ramah Pembaca

```kotlin
columns.forEach { col ->
    val colHeader = if (columns.size == 1 && !col.equals("ALL SIZE", ignoreCase = true)) {
        "SIZE $col"
    } else {
        col
    }
    Text(
        text = colHeader,
        modifier = Modifier.weight(1f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = WeMadeColors.Primary,
        textAlign = TextAlign.Center
    )
}
```
**Mengapa begini?**
- Jika hanya satu kolom tunggal yang dirender (SPK per-ukuran), header `"SIZE S"` lebih tegas dan jelas daripada hanya huruf `"S"`, mencegah keraguan di lantai pabrik.

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Smart-cast pada Kotlin Multiplatform Public Property**:
   - Menulis `if (!order.sizeLabel.isNullOrBlank()) order.sizeLabel` menghasilkan compiler error: `Smart cast to 'String' is impossible, because 'sizeLabel' is a public API property declared in different module`.
   - **Solusi**: Selalu tampung ke `val sizeLabel = order.sizeLabel` lokal terlebih dahulu sebelum pengecekan null/blank.

2. **Merusak Backward Compatibility untuk SPK Lama**:
   - Masih ada SPK lama sebelum migrasi per-ukuran yang tidak memiliki `sizeLabel` (`null`).
   - Jika kita langsung mengasumsikan `sizeLabel` selalu ada, SPK legacy akan rusak. Logika di atas menyertakan fallback ke `calculateTotalSampleQuantity` dan `order.sizeMode.displayName` bila `sizeLabel` bernilai `null`.

---

## ✅ 5. Verifikasi & Pengujian

1. **Unit Test Gunakan Domain Use Case**:
   - Jalankan `./gradlew :core:jvmTest --tests "com.eventverse.app.domain.sampling.PublishSamplingSpkFromDealUseCaseTest"`
   - Verifikasi bahwa `spk1.sizeMode == ALL_SIZE` dan `spk2.sizeMode == MULTI_SIZE`.

2. **Kompilasi WasmJS & Compose**:
   - Jalankan `./gradlew :app:shared:compileKotlinWasmJs` dan `./gradlew :app:webApp:wasmJsBrowserDevelopmentWebpack`.
   - Memastikan tidak ada runtime type mismatch atau layout break pada target Web Browser.

3. **Verifikasi Visual pada UI**:
   - Buka Kanban Sampling di `http://localhost:3000/sampling-order`.
   - Klik kartu `SPK-SMP-0051`.
   - Pastikan header menampilkan `Detail SPK — SPK-SMP-0051` dengan subtitle `(Size S)`.
   - Pastikan badge menampilkan `Size S` dan `2 Pcs Sample`.
   - Pastikan tabel POM Matrix menampilkan kolom `SIZE S` dengan spesifikasi ukuran untuk S (10 cm) dan kuantitas sampel 2 pcs.
