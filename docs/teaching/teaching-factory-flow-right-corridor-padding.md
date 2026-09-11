# 🎓 Modul Pembelajaran: Reserved Right Corridor Padding & Connector Clearance di Factory Flow Canvas

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform Layout Bounds, Canvas Coordinate Spaces, Scroll Container Clipping, Neo-Brutalist Shadow Clearance  
> **Prasyarat**: Dasar Compose Layout (`Box`, `Row`, `Column`, `Modifier.horizontalScroll`), Canvas DrawScope, dan Konsep Graph Routing  
> **Referensi Task**: Fix Right-side Connector Clipping (Red & Grey Dashed Feedback Lines) on Factory Flow Screen

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Bayangkan kamu sedang menggambar denah jalur pipa pabrik pada sebuah papan tulis putih panjang. Di ujung paling kanan papan tulis, ada sebuah mesin inspeksi mutu (QC). Jika sebuah baju dinyatakan reject atau rusak, pipa harus berbelok ke arah kanan keluar dari mesin, turun ke bawah, lalu meluncur kembali ke gudang kain di sebelah kiri.

Sekarang bayangkan tepi kanan papan tulis itu dibatasi oleh kusen tembok tepat di batas fisik mesin QC. Ketika pipa belok ke kanan, pipanya menabrak kusen dan terpotong di udara! Jalur pipa merah (retur kain ke suplier) dan abu-abu (rework jahit) terpotong dan tidak terlihat garis belokannya saat operator menggulir layar sampai mentok kanan.

Inilah yang terjadi di `HorizontalSwimlaneLayout`:
1. Kolom terakhir (*Mutu & Pengiriman*) ditaruh di dalam `Row(horizontalArrangement = Arrangement.spacedBy(80.dp))`.
2. Antara kolom 1 s/d 5 ada celah 80dp untuk jalur pipa vertikal.
3. Tapi **setelah kolom 5 (paling kanan)**, celahnya adalah **0dp**!
4. Sementara itu, algoritma multi-lane routing menggambar garis belok kanan ke koordinat `exitX = from.right + 14.dp + slot * 14.dp`.
5. Karena kolom terakhir memiliki padding 12dp, `from.right + 14.dp` jatuh di luar batas kolom (+2dp) dan slot kedua jatuh di +16dp di luar kolom!
6. Akibatnya, garis digambar di luar dimensi `Box` dan `Canvas` yang berukuran pas-pasan mengikuti `Row`. Saat pengguna men-scroll ke kanan mentok, garis-garis tersebut terpotong (clipped).

### Analogi Sederhana
Jika kamu membangun jalan tol 5 gerbang, kamu tidak bisa menaruh tebing jurang tepat 0 sentimeter di sebelah kanan gerbang terakhir. Truk yang harus memutar balik (*U-turn*) membutuhkan bahu jalan ekstra di sebelah kanan agar bisa berbelok dengan aman tanpa jatuh ke jurang. `SWIMLANE_CORRIDOR_END_PADDING` adalah bahu jalan tersebut.

### Hasil Akhir yang Diharapkan
- Terdapat reserved right corridor selebar `96.dp` setelah kolom swimlane terakhir.
- `Box` dan `SwimlaneConnectionCanvas` otomatis mengembang mencakup koridor kanan tersebut.
- Pengguna yang melakukan scroll horizontal dapat melihat seluruh lengkungan belokan garis putus-putus merah (*fabric defect return*) dan garis abu-abu (*workmanship rework*) dengan lega, rapi, dan tidak ada lagi yang terpotong.
- Garis vertikal memiliki clearance bersih dari bayangan hard shadow (Neo-brutalist clay shadow) milik kartu kolom.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta menyelesaikan masalah layout clipping pada Canvas custom seperti ini dari nol, ikuti urutan berpikir berikut:

```
[Langkah 0: Audit Sistem Koordinat]
       │ Membedah relasi koordinat: Window vs Root Box vs Child Card
       ▼
[Langkah 1: Tentukan Boundary Provider]
       │ Menentukan komponen mana yang mendikte ukuran Canvas (Row vs Column vs Box)
       ▼
[Langkah 2: Definisikan Token Desain Spasial]
       │ Membuat konstanta semantik (SWIMLANE_CORRIDOR_END_PADDING)
       ▼
[Langkah 3: Perlebar Container Scrollable]
       │ Menerapkan padding(end = ...) pada Row stage
       ▼
[Langkah 4: Kalibrasi Offset Garis terhadap Shadow]
       │ Memberi jarak aman antara garis vertikal dengan bayangan tebal kartu
       ▼
[Langkah 5: Tulis Unit Test Spasial]
       │ Memastikan garis selalu berada di luar kartu tapi di dalam padding
```

### Mengapa Urutan Ini Penting?
Pemula sering kali langsung menambahkan `Modifier.padding(end = ...)` di level layar terluar (`FactoryFlowScreen`). Akibatnya, layar terluar menciut, tetapi container scrollable di dalamnya tetap memiliki rasio lebar yang sama dan garisnya tetap terpotong di dalam viewport canvas! Kita harus memahami **komponen mana yang mengontrol ukuran Canvas yang sebenarnya**.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Deklarasi Token Koridor Kanan (`SwimlaneConnectionOverlay.kt`)

```kotlin
/**
 * Vertical space reserved under the stage columns as a routing corridor. Long connectors
 * (stage skips and feedback loops) run through here instead of cutting across the columns.
 */
internal val SWIMLANE_CORRIDOR_HEIGHT = 108.dp

/**
 * Horizontal space reserved to the right of the final stage column as a routing corridor.
 * Multi-lane feedback loops and defect return lines exiting the last stage drop down
 * through here into the bottom corridor without being clipped at the canvas edge.
 */
internal val SWIMLANE_CORRIDOR_END_PADDING = 96.dp
```

**Mengapa blok ini ditulis begini?**
- **Simetri Spasial**: Sama seperti `SWIMLANE_CORRIDOR_HEIGHT` (108dp) yang mencadangkan ruang di bawah kartu untuk kabel horizontal, `SWIMLANE_CORRIDOR_END_PADDING` (96dp) mencadangkan ruang di kanan kolom terakhir untuk kabel vertikal.
- **Konsistensi Skala 8-Point Grid**: 96dp adalah kelipatan 8dp dan 16dp, serta sepadan dengan `Arrangement.spacedBy(80.dp)` antarkolom + 16dp margin bernapas.

---

### Blok B: Penerapan Padding pada `Row` Swimlane (`PipelineFlowCanvas.kt`)

```kotlin
Row(
    // Wide enough for multi-lane vertical corridor connectors between columns to breathe freely.
    horizontalArrangement = Arrangement.spacedBy(80.dp),
    verticalAlignment = Alignment.Top,
    modifier = Modifier.padding(end = SWIMLANE_CORRIDOR_END_PADDING)
) {
    visibleStages.forEach { stage ->
        StageSwimlaneColumn(
            stage = stage,
            nodes = groupedByStage[stage] ?: emptyList(),
            selectedNode = selectedNode,
            isPresentationMode = isPresentationMode,
            bounds = bounds,
            onSelectNode = onSelectNode,
            onInspectInputs = onInspectInputs,
            modifier = Modifier.width(324.dp)
        )
    }
}
```

**Mengapa blok ini ditulis begini?**
- `Row` ini berada di dalam `Column`, yang dibungkus oleh `Box(modifier = Modifier.swimlaneRoot(bounds))`.
- Di dalam `Box`, ada `SwimlaneConnectionCanvas(modifier = Modifier.matchParentSize())`.
- `matchParentSize()` artinya Canvas akan memiliki ukuran **tepat sama** dengan ukuran `Box`.
- Ukuran `Box` didikte oleh ukuran `Column` dan `Row`.
- Dengan menambahkan `.padding(end = SWIMLANE_CORRIDOR_END_PADDING)` pada `Row`, maka:
  1. Lebar `Row` bertambah 96dp.
  2. Lebar `Box` bertambah 96dp.
  3. Lebar `SwimlaneConnectionCanvas` bertambah 96dp.
  4. Jarak scroll maksimal di `horizontalScroll(scrollState)` bertambah 96dp.
  5. Semua garis yang digambar di koordinat `exitX` (di sebelah kanan kolom 5) berada **di dalam area Canvas** dan **dapat di-scroll sepenuhnya**.

---

### Blok C: Kalibrasi Offset Garis terhadap Neo-Brutalist Shadow (`SwimlaneConnectionOverlay.kt`)

```kotlin
// Multi-lane corridor routing: dynamically assigned vertical exit channels,
// dedicated horizontal altitude tracks, and dedicated vertical entry channels.
val exitSlot = exitSlotByEdgeId[edge.id] ?: 0
val exitX = from.right + 20.dp.toPx() + exitSlot * 16.dp.toPx()

val entrySlot = entrySlotByEdgeId[edge.id] ?: 0
val entryX = to.left - 20.dp.toPx() - entrySlot * 16.dp.toPx()
```

**Mengapa blok ini ditulis begini?**
Mari kita hitung matematikanya:
1. `from` adalah `Rect` kartu node di dalam kolom (`PipelineNodeCard`).
2. `StageSwimlaneColumn` membungkus kartu dengan `contentPadding = PaddingValues(12.dp)`.
3. Tepi kanan kolom adalah `from.right + 12.dp`.
4. Kolom memiliki bayangan hard shadow Neo-Brutalist (`ClayOffset.Small = 4.dp`). Bayangan ini menjulur hingga `from.right + 16.dp`.
5. **Sebelumnya**: `from.right + 14.dp` menempatkan garis di `from.right + 14.dp`, yaitu **tepat di atas bayangan kolom** (hanya 2dp di luar batas kartu fisik)!
6. **Sekarang**: `from.right + 20.dp` menempatkan garis di `from.right + 12.dp + 8.dp = column.right + 8.dp`.
7. Garis berjarak 4dp bersih dari ujung bayangan terluar kolom (`column.right + 4.dp`). Garis tidak lagi menabrak atau menimpa bayangan hitam solid kartu!
8. Jarak antarlajur (`slot * 16.dp`) memberikan separasi yang tegas antara garis putus-putus merah (slot 0) dan garis abu-abu (slot 1).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Pendekatan Alternatif | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Padding pada Container `Row` (`SWIMLANE_CORRIDOR_END_PADDING`)** | Menambah `Spacer(width = 96.dp)` sebagai anak terakhir `Row` | Karena `Row` memakai `Arrangement.spacedBy(80.dp)`, `Spacer` akan memicu penambahan 80dp + 96dp = 176dp yang terlalu renggang. | Terjadi celah kosong abnormal di kanan yang tidak konsisten dengan ritme desain. |
| **Integrasi ke `matchParentSize()` Canvas** | Menggambar Canvas dengan ukuran fixed atau offset manual | `matchParentSize()` menjamin Canvas selalu responsif mengikuti pertambahan kolom atau tahapan yang di-bypass. | Koordinat Canvas desinkronisasi dengan kartu saat layar di-resize atau preset berganti. |
| **Kompensasi Padding Kolom (20dp offset)** | Membiarkan 14dp offset | Kartu dibungkus kolom 12dp + bayangan 4dp. Offset 20dp menjamin garis berada di ruang bebas tanpa tumpang tindih. | Garis putus-putus terlihat bertumpuk kotor di atas outline/bayangan kartu kolom. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Menaruh Padding di Luar `horizontalScroll`**
   - *Kenapa bahaya*: Jika kamu menambahkan padding di luar modifier `horizontalScroll`, padding tersebut menjadi margin statis di layar. Konten yang di-scroll di dalamnya tetap terpotong pada batas scroll maksimalnya.
   - *Solusi elegan*: Taruh padding di dalam konten scrollable (`Row` anak langsung dari `Box` yang diukur Canvas).

2. **Jebakan 2: Mengabaikan Ketebalan Bayangan Neo-Brutalist (Clay Shadow)**
   - *Kenapa bahaya*: Pada Material Design biasa, bayangan adalah blur halus (elevation). Pada Neo-Brutalism (Clay), bayangannya adalah balok solid hitam (`4dp` hingga `6dp`). Garis yang digambar terlalu dekat (misal 2dp) akan terlihat seperti garis patah atau tertutup balok bayangan.
   - *Solusi elegan*: Selalu hitung `contentPadding (12dp) + border (2dp) + shadow offset (4dp) + margin bernapas (4dp) = 22dp ~ 20dp`.

3. **Jebakan 3: Lupa Menguji Mode Presentasi & Filter Tahapan**
   - *Kenapa bahaya*: Saat user memfilter tahapan tertentu, jumlah kolom berubah. Jika penentuan koridor kanan di-hardcode berdasarkan nama kolom tertentu, padding bisa hilang saat difilter.
   - *Solusi elegan*: Terapkan padding pada level kontainer `Row`, sehingga berapa pun kolom yang aktif (1 s/d 5 kolom), koridor akhir selalu otomatis ada di sebelah kanan kolom terakhir.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Kita menambahkan unit test otomatis di `SwimlaneRoutingCollisionTest.kt`:

```kotlin
@Test
fun corridorFeedbackEdges_exitingLastColumn_fitWithinEndPadding() {
    val col5Width = 324f
    val cardPadding = 12f
    val cardRight = col5Width - cardPadding // 312f
    val colRight = col5Width // 324f
    val endPadding = SWIMLANE_CORRIDOR_END_PADDING.value // 96f

    val nodes = PipelinePresetFactory.createSnapshot(GarmentBusinessPreset.FOB_FULL_PACKAGE).nodes
    val graph = PipelineGraph.from(nodes)
    val qcNode = nodes.first { it.id == "fob-qc-defect" }
    val feedbackEdges = graph.edges.filter { it.fromNodeId == qcNode.id && it.isFeedback }

    assertTrue(feedbackEdges.isNotEmpty(), "Expected feedback edges from QC")

    feedbackEdges.forEachIndexed { slot, _ ->
        val exitX = cardRight + 20f + slot * 16f
        assertTrue(exitX > colRight, "Exit lane $slot must be outside column right border")
        assertTrue(exitX - colRight >= 8f, "Exit lane $slot must clear the column shadow")
        assertTrue(exitX < colRight + endPadding, "Exit lane $slot must be within end corridor padding")
    }
}
```

Jalankan pengujian via terminal:
```bash
./gradlew :app:shared:jvmTest
```
**Hasil Verifikasi**:
- `BUILD SUCCESSFUL in 1s`
- Seluruh 20 actionable tasks sukses, membuktikan relasi spasial antara batas kolom, bayangan, garis connector, dan reserved padding terbukti valid secara matematis.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Amati apa yang terjadi jika preset diganti ke `CMT_SUBCONTRACT` di mana tahapan awal di-bypass. Pastikan koridor kanan tetap mempertahankan padding 96dp dengan membuka dropdown preset tenant.
- [ ] **Tantangan 2**: Coba ubah `exitSlot * 16f` menjadi nilai dinamis yang menyesuaikan dengan jumlah total edge keluar (`outCount`). Apakah ada threshold di mana 96dp perlu membesar jika sebuah node memiliki > 5 rute feedback?
- [ ] **Tantangan 3**: Tambahkan indikator visual mini (misal badge atau panah bantu) di pojok kanan bawah canvas saat ada feedback loop aktif yang sedang mengalir melalui koridor bawah.
