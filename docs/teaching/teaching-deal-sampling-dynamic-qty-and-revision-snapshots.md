# Teaching: Dynamic Size Chart Sample Quantity & Immutable Historical Revision Snapshots

> **Gaya**: Senior Lead Software Engineer membimbing Junior Developer  
> **Konteks Proyek**: WeMade ERP (Kotlin Multiplatform + Compose Multiplatform Wasm/JVM + Ktor + PostgreSQL)  
> **Modul**: Deal Detail Dialog & Sampling Order Domain (`core/`, `server/`, `app/shared/`)

---

## Ringkasan Eksekutif: Masalah Nyata di Lantai Sampling

Dalam industri konveksi & garmen rajut, pembuatan sampel pakaian (sampling) adalah tahapan paling krusial sebelum pesanan masuk ke meja potong massal. Namun, sering kali tim menghadapi dua friksi operasional yang fatal:

1. **Pemesanan Sampel Ukuran Fiktif (Ghost Sample Quantities)**:
   Pelanggan atau tim sales sering kali mengisi kuantitas sampel (misalnya "M = 2 pcs, XL = 5 pcs"), padahal tim teknis baru memasukkan spesifikasi ukuran (Point of Measurement / POM seperti Lebar Dada atau Panjang Baju) untuk ukuran `S`. Akibatnya, operator rajut bingung karena harus membuat ukuran `XL` tanpa ada patokan ukuran fisik di kartu kerja SPK.
2. **Revisi Tertimpa & Hilangnya Jejak Sejarah Desain (Revision Mutation Bug)**:
   Ketika buyer meminta revisi (misalnya dari Rev 0 ke Rev 1, lalu ke Rev 2), foto mockup lama dan tabel ukuran revisi sebelumnya tertimpa begitu saja oleh input revisi terbaru. Ketika buyer bertanya, *"Di Rev 0 kemarin kita pakai panjang berapa ya?"*, data tersebut sudah musnah karena disimpan secara overwrite di database.

Modul pembelajaran ini membedah bagaimana kita mengatasi kedua masalah tersebut secara elegan dan tangguh menggunakan **Dynamic Quantity Row Gating** dan **Immutable Historical Snapshot Pattern**.

---

## 1. Start dari Mana? (Order of Operations)

Jika kamu ingin mengimplementasikan fitur ini dari nol, ikuti urutan lapisan sesuai aturan Domain-Driven Design (DDD):

```
Step 1: Pure Domain Layer (core/)
  ├── Definisikan konstanta & ekstensi baris kuantitas (SAMPLING_QTY_ROW_ID, isQtyRow)
  ├── Buat business rule helper: isSizeColumnActive(), calculateTotalSampleQuantity(), sanitizeSamplingMatrix()
  ├── Rancang Value Object: SamplingSnapshot
  └── Perluas Entity SamplingOrder & RevisionFeedback dengan snapshot capturing

Step 2: Shared Codec / Data Serialization (core/)
  └── Update SamplingOrderCodec untuk encode/decode snapshot pada array JSONB revision_history

Step 3: Database & Repository Layer (server/)
  └── Update PostgresSamplingOrderRepository untuk persistensi snapshot ke JSONB PostgreSQL

Step 4: Design System Polish (app/shared/presentation/designsystem/)
  └── Tambahkan dukungan readOnly: Boolean pada ClayTextField agar teks tetap jelas terbaca tanpa glitch

Step 5: Presentation Layer (app/shared/presentation/deal/)
  ├── Buat banner info mode arsip saat selectedRevision < order.revisionCount
  ├── Sambungkan parameter readOnly ke slot foto mockup, size chart, fee, dan catatan
  └── Terapkan gating dinamis pada cell tabel: cell terkunci "-" jika !isSizeColumnActive()
```

---

## 2. Bedah Kode Blok per Blok & Mental Model

### A. Pure Domain: Dynamic Size Column Gating (`SamplingSizeMatrix.kt`)

Kunci dari gating dinamis adalah menentukan kapan sebuah kolom ukuran (misalnya `"ALL SIZE"`, `"S"`, `"M"`, `"L"`) dianggap **aktif**:

```kotlin
// core/src/commonMain/kotlin/com/eventverse/app/domain/sampling/SamplingSizeMatrix.kt

const val SAMPLING_QTY_ROW_ID = "sampling_qty_row"
const val SAMPLING_QTY_ROW_NAME = "Jumlah Sampel (pcs)"

val SizeChartRow.isQtyRow: Boolean
    get() = id == SAMPLING_QTY_ROW_ID || pomName.equals(SAMPLING_QTY_ROW_NAME, ignoreCase = true)

/**
 * Kolom ukuran [col] aktif dan dapat diisi kuantitas sampelnya jika ada minimal
 * satu baris parameter fisik (POM) yang memiliki nilai ukuran tidak kosong.
 */
fun isSizeColumnActive(matrix: List<SizeChartRow>, col: String): Boolean =
    matrix.filter { !it.isQtyRow }.any { it.values[col]?.isNotBlank() == true }
```

**Mental Model**:
- Kolom `"S"` tidak boleh menerima kuantitas jika belum ada satu pun POM (seperti Lebar Dada atau Panjang Baju) yang diisi untuk `"S"`.
- Ekstensi `isQtyRow` memastikan bahwa baris kuantitas tidak menganggap dirinya sendiri sebagai parameter fisik (menghindari rekursi semantik).

Ketika menghitung total sampel:
```kotlin
fun calculateTotalSampleQuantity(matrix: List<SizeChartRow>, fallback: Int = 1): Int {
    val qtyRow = matrix.firstOrNull { it.isQtyRow } ?: return fallback
    val sum = qtyRow.values.entries
        .filter { (col, _) -> isSizeColumnActive(matrix, col) }
        .sumOf { (_, value) -> value.trim().toIntOrNull() ?: 0 }
    return if (sum > 0) sum else fallback
}
```
*Mengapa kita filter `isSizeColumnActive` terlebih dahulu?*  
Karena jika pengguna sempat mengetik qty `"2"` di kolom `"XL"`, lalu kemudian seluruh baris POM `"XL"` dihapus, nilai qty yang tertinggal tidak boleh ikut dihitung ke dalam kalkulasi invoice atau beban produksi.

---

### B. Pure Domain: Immutable Historical Snapshot Pattern (`SamplingOrder.kt`)

Bagaimana cara kita menyimpan riwayat revisi tanpa menduplikasi seluruh tabel database? Jawabannya adalah **Event Sourcing / Snapshotting Mini**:

```kotlin
// core/src/commonMain/kotlin/com/eventverse/app/domain/sampling/SamplingOrder.kt

data class SamplingSnapshot(
    val mockupFrontKey: String? = null,
    val mockupBackKey: String? = null,
    val sizeMatrix: List<SizeChartRow> = emptyList(),
    val sampleQuantity: Int = 1,
    val samplingFeeIdr: Long = 0L,
    val notes: String = ""
)

data class RevisionFeedback(
    val revision: Int,
    val notes: String,
    val at: Instant,
    val snapshot: SamplingSnapshot? = null
)
```

Saat buyer mengajukan revisi (`requestRevision`):
```kotlin
fun requestRevision(notes: String, updatedAt: Instant): SamplingOrder {
    val nextRevision = revisionCount + 1
    val currentSnapshot = SamplingSnapshot(
        mockupFrontKey = mockupFrontKey,
        mockupBackKey = mockupBackKey,
        sizeMatrix = sizeMatrix,
        sampleQuantity = sampleQuantity,
        samplingFeeIdr = samplingFeeIdr,
        notes = this.notes
    )
    return copy(
        status = SamplingStatus.REVISION,
        accNotes = notes,
        revisionCount = nextRevision,
        revisionHistory = revisionHistory + RevisionFeedback(
            revision = revisionCount, // Mengarsipkan nomor revisi saat ini (mis. Rev 0)
            notes = notes,
            at = updatedAt,
            snapshot = currentSnapshot // Bekukan state saat ini!
        ),
        updatedAt = updatedAt
    )
}
```

Dan untuk menampilkan data saat user memilih revisi lama di dropdown UI:
```kotlin
fun snapshotFor(revision: Int): SamplingSnapshot? =
    revisionHistory.firstOrNull { it.revision == revision }?.snapshot
        ?: revisionHistory.firstOrNull { it.revision == revision + 1 }?.snapshot
```

---

### C. UI Presentation: Reactive State & Read-Only Locking (`DealDetailDialog.kt`)

Di sisi antarmuka pengguna Compose Multiplatform, kita membaca status revisi aktif vs lampau:

```kotlin
val isHistoricRevision = selectedRevision < order.revisionCount
val historicSnapshot = if (isHistoricRevision) order.snapshotFor(selectedRevision) else null

// Jika sedang melihat arsip lampau, gunakan nilai dari snapshot
val displayedFront = if (isHistoricRevision) historicSnapshot?.mockupFrontKey else order.mockupFrontKey
val displayedBack = if (isHistoricRevision) historicSnapshot?.mockupBackKey else order.mockupBackKey
val displayedMatrix = if (isHistoricRevision && historicSnapshot != null) {
    ensureSamplingQtyRow(historicSnapshot.sizeMatrix)
} else {
    sizeMatrixInput
}
```

Jika `isHistoricRevision == true`, sebuah banner peringatan otomatis muncul:
```kotlin
if (isHistoricRevision) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ClayShapes.Card)
            .background(WeMadeColors.SurfaceMuted)
            .border(ClayBorder.Thin, WeMadeColors.Outline.copy(alpha = 0.4f), ClayShapes.Card)
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        IconRefresh(Modifier.size(16.dp), tint = WeMadeColors.OnSurfaceMuted)
        Text(
            text = "Mode Arsip (Rev $selectedRevision): Anda sedang melihat snapshot data historis. Seluruh input terkunci.",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}
```

### C. UI Presentation: Pemisahan Dua Tabel (Size Chart vs Alokasi Sampel)

Untuk menciptakan pengalaman pengguna yang bersih dan tidak mencampuradukkan spesifikasi teknis garmen dengan jumlah pesanan, kita membagi antarmuka menjadi **dua tabel mandiri yang tersinkronisasi** di [DealDetailDialog.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/DealDetailDialog.kt):

#### 1. Tabel 1: Size Chart / POM (Spesifikasi Ukuran Fisik)
Murni berisi baris Point of Measurement (Lebar Dada, Panjang Baju, dll). Tidak ada baris kuantitas di dalam tabel ini:
```kotlin
SamplingSizeChartTable(
    pomRows = displayedSizeMatrix.filter { !it.isQtyRow },
    readOnly = isHistoricRevision,
    onUpdateRow = { updatedRow ->
        if (!isHistoricRevision) {
            val updated = sizeMatrixInput.map { if (it.id == updatedRow.id) updatedRow else it }
            sizeMatrixInput = updated
        }
    },
    onDeleteRow = { rowId ->
        if (!isHistoricRevision) {
            val updated = sizeMatrixInput.filter { it.id != rowId }
            sizeMatrixInput = sanitizeSamplingMatrix(updated)
        }
    },
    onAddRow = { /* Tambah parameter POM baru */ }
)
```

#### 2. Tabel 2: Alokasi Jumlah Sampel (Tabel Baru di Bawah Size Chart)
Diletakkan tepat di bawah tabel Size Chart, menampilkan baris kuantitas per ukuran serta kolom akumulasi `Total Pcs`:
```kotlin
SamplingQuantityTable(
    qtyRow = currentQtyRow,
    fullMatrix = displayedSizeMatrix,
    totalQty = totalSampleQty,
    readOnly = isHistoricRevision,
    onUpdateQty = { col, newQty ->
        if (!isHistoricRevision) {
            val withQty = ensureSamplingQtyRow(sizeMatrixInput)
            val qtyRow = withQty.first { it.isQtyRow }
            val newValues = qtyRow.values.toMutableMap()
            newValues[col] = newQty
            val updatedQtyRow = qtyRow.copy(values = newValues)
            sizeMatrixInput = listOf(updatedQtyRow) + withQty.filter { !it.isQtyRow }
        }
    }
)
```

**Gating Dinamis pada Cell Tabel Kuantitas**:
```kotlin
val isColActive = isSizeColumnActive(fullMatrix, col)
val isCellEnabled = isColActive && !readOnly

if (!isColActive) {
    // Ukuran belum memiliki spesifikasi di Size Chart -> Terkunci
    Text(
        text = "-",
        fontSize = 11.sp,
        color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.45f),
        textAlign = TextAlign.Center
    )
} else {
    // Ukuran aktif -> Input digit dengan styling beraksen
    BasicTextField(
        value = currentVal,
        readOnly = !isCellEnabled,
        onValueChange = { newVal -> onUpdateQty(col, newVal.filter { it.isDigit() }) },
        textStyle = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.Primary,
            textAlign = TextAlign.Center
        ),
        singleLine = true
    )
}
```

Jika user beralih ke revisi lama (`isHistoricRevision == true`), seluruh cell di kedua tabel otomatis terkunci (`readOnly = true`) dan banner mode arsip muncul di bagian atas kartu.

---

## 3. Technology & Approach ("The Why")

### Mengapa Menyimpan Snapshot di Dokumen JSONB `revision_history`?
- **Alternatif yang Dihindari**: Membuat tabel baru `sampling_order_revisions` yang mereplikasi puluhan kolom `sampling_orders`.
- **Alasan Pilihan**: Tabel `sampling_orders` sudah memiliki kolom PostgreSQL `revision_history jsonb`. Menyimpan snapshot mini `SamplingSnapshot` ke dalam array elemen JSONB memberikan fleksibilitas skema tanpa perlu alter table database berkali-kali. Karena revisi sifatnya append-only dan immutable, JSONB adalah representasi yang ideal.

### Mengapa Baris Kuantitas Diletakkan di dalam Matriks Ukuran?
- **Alternatif yang Dihindari**: Membuat kolom terpisah untuk setiap ukuran di database (mis. `qty_s`, `qty_m`, `qty_l`).
- **Alasan Pilihan**: Kolom ukuran di industri pakaian bisa sangat dinamis (bisa ada `4XL`, `6XL`, atau ukuran anak `2T`, `4T`). Menyimpan kuantitas dalam baris khusus ber-ID `sampling_qty_row` di dalam matriks ukuran membuat struktur data tetap kompak, dinamis, dan backward-compatible.

---

## 4. Jebakan Pemula (Common Pitfalls)

1. **Membiarkan Tombol Aksi Terbuka di Revisi Lampau**:
   - *Jebakan*: Menampilkan tombol "ACC Desain" atau "Ajukan Revisi" saat user sedang melihat `Rev 0` (padahal order sudah di `Rev 2`).
   - *Dampak Fatal*: Jika user mengklik ACC pada data arsip, status approval bisa kacau dan merusak alur invoice.
   - *Solusi*: Selalu bungkus tombol mutasi dengan proteksi `if (!isHistoricRevision)`.

2. **Mengubah Nilai Snapshot Saat Autosave Berjalan**:
   - *Jebakan*: Composable autosave memicu API update ke server saat user melihat revisi lampau.
   - *Solusi*: Blokir pemanggilan `onUpdateOrder` jika `isHistoricRevision == true`.

3. **Mengabaikan Sanitasi Kolom yang Dihapus**:
   - *Jebakan*: User menambah kolom baru, mengisi qty, lalu menghapus semua baris ukuran kolom tersebut. Qty tersembunyi tetap tersimpan dan ikut ditagihkan.
   - *Solusi*: Gunakan fungsi `sanitizeSamplingMatrix()` sebelum mengirim perubahan ke server.

---

## 5. Verifikasi & Pengujian Mandiri

Kamu dapat memverifikasi kebenaran implementasi melalui unit test domain di `core/src/commonTest/kotlin/com/eventverse/app/domain/sampling/SamplingSizeMatrixAndSnapshotTest.kt`:

```bash
./gradlew :core:jvmTest
```

Poin-poin yang diverifikasi:
1. `isSizeColumnActive_shouldReturnTrueOnlyWhenPomRowHasValue`: Kolom hanya aktif jika minimal ada 1 POM.
2. `calculateTotalSampleQuantity_shouldSumQuantitiesOfActiveColumnsOnly`: Qty pada kolom tidak aktif tidak dihitung.
3. `sanitizeSamplingMatrix_shouldClearQtyForInactiveColumns`: Nilai kuantitas pada kolom kosong otomatis di-reset.
4. `requestRevision_shouldCaptureSnapshotAndAllowRetrieval`: Snapshot tersimpan dan dapat dibaca kembali per revisi.
5. `codec_shouldSerializeAndDeserializeRevisionSnapshotProperly`: Snapshot bertahan melewati siklus JSON encode/decode.
