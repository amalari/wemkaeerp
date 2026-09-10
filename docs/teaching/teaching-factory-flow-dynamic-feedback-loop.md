# 🎓 Modul Pembelajaran: Dynamic Feedback Loops & Unified 1-Layout Monitoring di Factory Pipeline

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Single-Pane-of-Glass Monitoring, Directed Acyclic Graph (DAG) dengan Backward Exception Routes, Visualisasi Kolom Alur dengan Garis Putus Merah & Anotasi Tujuan.  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform (KMP), struktur graph (Node & Edge), dan proses manufaktur konveksi/garmen.  
> **Referensi Task**: Implementasi 1 Layout Monitoring Terpadu: Panah Balik Putus-Putus Merah & Keterangan Disposisi QC Gagal.

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata di Lantai Pabrik Konveksi
Dalam software engineering, developer pemula sering memodelkan proses bisnis manufaktur sebagai **jalur linier searah (*pure forward-only pipeline*)**:
```
Pengadaan Bahan ──▶ Pembuatan Pola ──▶ Pemotongan ──▶ Penjahitan ──▶ QC Finis ──▶ Pengiriman
```
Di atas kertas, alur ini terlihat rapi dan ideal (*happy path*). Namun di dunia nyata pabrik konveksi (*garment manufacturing*), **alur murni searah adalah sebuah ilusi**:

1. **Bagaimana jika kain susut (*shrinkage*) atau belang warna (*color shading*) saat dicek di QC akhir?**
   Pakaian tidak bisa dikirim ke buyer. Pabrik harus segera menerbitkan **Retur Bahan Baku** dan **Klaim Komplain Supplier** ke gudang/rantai pasok untuk meminta ganti rugi lot kain (*yarn/fabric defect*).
2. **Bagaimana jika jahitan melenceng atau kancing lepas (*workmanship defect*)?**
   Baju tidak dibuang, melainkan dikembalikan ke meja operator jahit untuk dibongkar dan dijahit ulang (*rework sewing*).
3. **Dampak Finansial & Operasional**:
   Setiap kali QC gagal, terjadi penundaan waktu (*lead time penalty*), penumpukan barang setengah jadi tak terencana (*unplanned WIP penalty*), dan penurunan skor kesehatan operasional (*Health Score drop*).

### Mengapa Pendekatan "Multi-Skenario Terpisah" Ditolak Operasional?
Awalnya, tim mungkin tergoda membuat 3 tombol skenario terpisah:
- Tombol 1: `Happy Path (Normal)`
- Tombol 2: `Skenario Cacat Kain`
- Tombol 3: `Skenario Rework Jahit`

Namun, di ruang kontrol operasional pabrik, **pendekatan multi-skenario seperti ini sangat tidak efektif**:
- Supervisor tidak ingin mengklik tombol bolak-balik hanya untuk melihat apa yang terjadi jika ada kegagalan.
- Mengubah tampilan per skenario menyembunyikan gambaran utuh (*fragmented cognitive load*).
- **Kebutuhan Sebenarnya**: Supervisor ingin **1 layout tunggal (*single-pane-of-glass*)** di mana alur produksi normal (garis maju) dan alur mitigasi kegagalan (panah balik garis putus merah beserta keterangannya) terlihat berdampingan secara transparan setiap saat!

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun fitur alur dinamis 1 layout dengan feedback loop terpadu dari nol, ikuti urutan berikut:

```
Step 0: Desain Mental & Kontrak Tipe Edge (core/domain/pipeline/)
   └── PipelineEdgeType.kt (FORWARD, FEEDBACK_DEFECT, FEEDBACK_REWORK, CONDITIONAL_BRANCH)

Step 1: Entitas Model Rute Feedback (core/domain/pipeline/)
   └── PipelineFeedbackRoute.kt (fromNode, toNode, delayDaysPenalty, wipPenalty, isActive)

Step 2: Factory Snapshot dengan Kontrak Feedback Permanen (core/domain/pipeline/)
   └── PipelinePresetFactory.kt (selalu sediakan feedbackRoutes aktif dengan deskripsi aksi mitigasi)

Step 3: Graph Resolver Tanpa Merusak Topological Sort (core/domain/pipeline/)
   └── PipelineGraph.kt (pisahkan forward vs feedback edges agar urutan 5 kolom tidak kolaps)

Step 4: Presentation Layer: Bar Legenda Terpadu (app/shared/presentation/)
   └── PresetSelectorBar.kt (gantikan tombol skenario dengan legenda kontras alur normal & jalur putus merah)

Step 5: Visualisasi Kolom Alur: Disposisi & Titik Balik (app/shared/presentation/)
   └── PipelineFlowCanvas.kt (tampilkan kotak merah putus-putus di QC & kotak penerimaan di Rantai Pasok/Jahit)

Step 6: Kanvas Graf Interaktif: Kurva Bezier Koridor Bawah (app/shared/presentation/)
   └── PipelineNodeGraphCanvas.kt (gambar kurva putus-putus merah dari QC kembali ke target upstream)
```

---

## 🔍 3. Bedah Kode Blok per Blok & Mental Model

### Blok A: Kontrak Rute Feedback Permanen (`PipelinePresetFactory.kt`)
Lokasi: `core/src/commonMain/kotlin/com/eventverse/app/domain/pipeline/PipelinePresetFactory.kt`

```kotlin
// Pada node QC & Inspeksi Akhir:
val qcFeedbackRoutes = listOf(
    PipelineFeedbackRoute(
        id = "fb-qc-fabric-defect",
        fromNodeId = "fob-qc-finishing",
        toNodeId = "fob-procurement",
        label = "Retur ke Rantai Pasok: Klaim Suplier Tekstil & Penggantian Kain (+3-4 Hari)",
        reason = "Kain susut >5% atau lot warna shading saat inspeksi akhir.",
        edgeType = PipelineEdgeType.FEEDBACK_DEFECT,
        delayDaysPenalty = 4,
        additionalWipPenalty = 180,
        urgency = RouteUrgency.CRITICAL,
        isActive = true // Selalu aktif sebagai rute kontingensi
    ),
    PipelineFeedbackRoute(
        id = "fb-qc-sewing-rework",
        fromNodeId = "fob-qc-finishing",
        toNodeId = "fob-sewing-operator",
        label = "Rework ke Lantai Jahit: Bongkar Jahitan & Alterasi Operator (+1-2 Hari)",
        reason = "Toleransi jahitan melenceng atau kancing lepas.",
        edgeType = PipelineEdgeType.FEEDBACK_REWORK,
        delayDaysPenalty = 2,
        additionalWipPenalty = 95,
        urgency = RouteUrgency.HIGH,
        isActive = true
    )
)
```
**Mental Model:**
- Dalam arsitektur terpadu, rute feedback bukan sekadar "state sementara saat tombol diklik", melainkan **kontrak prosedur operasional standar (SOP)** yang melekat pada node inspeksi.

---

### Blok B: Pemisahan Layer DAG untuk Mencegah Cyclic Crash (`PipelineGraph.kt`)
Lokasi: `core/src/commonMain/kotlin/com/eventverse/app/domain/pipeline/PipelineGraph.kt`

Dalam graf berarah asiklik (DAG), algoritma pemetaan kolom (layering) akan crash atau membentuk siklus jika ada edge yang bergerak mundur dari Stage 5 ke Stage 3.
Solusinya:

```kotlin
private fun assignLayers(nodes: List<PipelineNode>, edges: List<PipelineEdge>): Map<String, Int> {
    val nodeMap = nodes.associateBy { it.id }
    
    // KUNCI: Hanya gunakan forward edges untuk menentukan urutan kolom kiri-ke-kanan!
    val forwardEdges = edges.filter { it.kind == PipelineEdgeKind.FORWARD && !it.isFeedback }
    
    val inDegree = nodes.associate { it.id to 0 }.toMutableMap()
    val adjacency = nodes.associate { it.id to mutableListOf<String>() }

    forwardEdges.forEach { edge ->
        if (nodeMap.containsKey(edge.from) && nodeMap.containsKey(edge.to)) {
            adjacency[edge.from]?.add(edge.to)
            inDegree[edge.to] = (inDegree[edge.to] ?: 0) + 1
        }
    }
    // ... kalkulasi layer berbasis topological sorting murni ...
}
```
**Mengapa penting?**
- Kolom fisik pabrik tidak berpindah tempat. Dengan memfilter `!it.isFeedback`, 5 panggung produksi (Desain ➔ Pola ➔ Rantai Pasok ➔ Jahit ➔ QC) tetap berdiri kokoh dari kiri ke kanan.

---

### Blok C: Visualisasi Kolom Alur dengan Garis Putus Merah (`PipelineFlowCanvas.kt`)
Lokasi: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/pipeline/components/PipelineFlowCanvas.kt`

Di dalam kolom Stage 5 (QC & Pengiriman), kita merender kotak disposisi kegagalan dengan garis tepi putus-putus merah:

```kotlin
// Box Disposisi Rejek QC
Column(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFFE11D48).copy(alpha = 0.08f))
        .border(1.dp, Color(0xFFE11D48), RoundedCornerShape(8.dp))
        .padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
) {
    Text(
        text = "⚠️ KETIKA QC GAGAL (DISPOSISI REJEK):",
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFFE11D48)
    )
    
    // Rute Balik 1: Retur ke Rantai Pasok
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFE11D48).copy(alpha = 0.12f))
            .border(0.8.dp, Color(0xFFE11D48), RoundedCornerShape(6.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("⤶ ◀- - -", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFE11D48))
        Text(
            text = "Retur ke Rantai Pasok (Tahap 3): Klaim Suplier Tekstil & Penggantian Kain (+3-4 Hari)",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE11D48)
        )
    }

    // Rute Balik 2: Rework ke Lantai Jahit
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFD97706).copy(alpha = 0.12f))
            .border(0.8.dp, Color(0xFFD97706), RoundedCornerShape(6.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("⤶ ◀- - -", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFD97706))
        Text(
            text = "Rework ke Lantai Jahit (Tahap 4): Bongkar Jahitan & Alterasi Operator (+1-2 Hari)",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB45309)
        )
    }
}
```
Sementara di Stage 3 dan Stage 4, diletakkan kartu penerimaan:
- Stage 3: `📥 TITIK TERIMA RETUR KAIN (DARI QC TAHAP 5)`
- Stage 4: `📥 TITIK TERIMA REWORK JAHIT (DARI QC TAHAP 5)`

Dan di bawah seluruh kolom, terdapat `StageFeedbackHighwayTrack` yang menggambarkan jalur rel bawah tempat barang retur dialirkan kembali ke pos hulu.

---

### Blok D: Kurva Bezier Garis Putus Merah di Kanvas Node (`PipelineNodeGraphCanvas.kt`)
Lokasi: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/pipeline/components/PipelineNodeGraphCanvas.kt`

```kotlin
val strokeStyle = if (edge.isFeedback) {
    Stroke(
        width = 2.4f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), phase = 0f),
        cap = StrokeCap.Round
    )
} else {
    Stroke(width = 2.2f, cap = StrokeCap.Round)
}

// Bypass Y di koridor bawah agar tidak menabrak kartu node di tengah
val bypassY = contentH - CANVAS_PAD / 2f + (edge.label.hashCode() % 3) * 18f
```
**Mengapa menggunakan dashed path effect?**
- Mata manusia secara naluriah mengenali garis putus-putus sebagai indikator perkecualian/alternatif/retur, sedangkan garis solid adalah alur utama yang sedang berjalan.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi / Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Unified 1-Layout Monitoring** | Multi-Scenario Toggle Buttons | Memberikan pandangan utuh (*Single-Pane-of-Glass*). Supervisor langsung tahu ke mana barang pergi jika gagal tanpa perlu mengklik apapun. | Tombol skenario menyembunyikan risiko operasional jika user lupa mengklik skenario terkait. |
| **Separated DAG Layering** | Standard Topological Sort tanpa filter | Memastikan 5 panggung konveksi tetap berada di urutan kolom yang benar (1 sampai 5). | Terjadi cycle crash (*infinite recursion*) atau posisi kolom terbalik jika edge balik dihitung dalam layering. |
| **Bypass Highway Corridor** | Garis lurus diagonal melintasi tengah kanvas | Garis balik dialirkan melalui koridor bawah sehingga tidak memotong teks pada kartu node. | Garis menutupi metrik WIP dan nama operator di kolom tengah, merusak keterbacaan. |
| **Dashed Stroke & Contrast Palette** | Garis solid berwarna sama | Menghadirkan diferensiasi visual instan antara alur maju (hijau/biru/netral) dan alur kegagalan (merah/amber putus-putus). | Operator kebingungan membedakan alur produksi normal dengan jalur retur barang rusak. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

### 1. Jebakan 1: Kotlin Multiplatform Smart-Cast pada Properti Publik Modul Lain
- **Masalah**: Kamu menulis `if (node.activeFeedbackBadge != null) { Text(node.activeFeedbackBadge) }`. Di Kotlin JVM ini mungkin lolos, namun di KMP lintas modul (`core` ➔ `app/shared`), compiler akan menolak dengan error: *Smart cast to 'String' is impossible, because 'node.activeFeedbackBadge' is a public API property declared in different module*.
- **Solusi**: Selalu tampung ke variabel lokal immutable terlebih dahulu:
  ```kotlin
  val badge = node.activeFeedbackBadge
  if (badge != null) {
      Text(badge)
  }
  ```

### 2. Jebakan 2: Ketinggian Swimlane dan Konten Terpotong di Layar Laptop
- **Masalah**: Layar laptop umumnya memiliki resolusi vertikal ~900px. Jika ada 5 kartu node ditambah kotak disposisi QC dan highway track, bagian bawah kolom bisa terpotong jika hanya diberi `horizontalScroll`.
- **Solusi**: Pasang `verticalScroll(rememberScrollState())` pada container utama dan berikan scroll internal pada kartu kolom jika melebihi batas tinggi layar.

### 3. Jebakan 3: Z-Fighting pada Multiple Feedback Edges
- **Masalah**: Jika ada dua edge feedback sekaligus (QC ➔ Bahan dan QC ➔ Jahit), jika keduanya berjalan pada `bypassY` yang sama, kedua garis putus-putus akan bertabrakan dan saling menutupi.
- **Solusi**: Gunakan offset terdistribusi berbasis hash label: `(edge.label.hashCode() % 3) * 18f`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### A. Unit Test Domain Layer
Jalankan pengujian unit murni tanpa UI:
```bash
./gradlew :core:jvmTest --tests "com.eventverse.app.domain.pipeline.PipelineGraphTest"
```
**Hasil**: 17 tests passed (0 failures).

### B. Kompilasi Multiplatform
Pastikan shared UI dan Wasm frontend terkompilasi sempurna:
```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinJs :app:webApp:compileKotlinWasmJs
```
**Hasil**: BUILD SUCCESSFUL di seluruh target KMP.

### C. Verifikasi Tampilan di Browser (Playwright / Chrome DevTools)
1. Buka `http://localhost:3000/factory-flow`.
2. Verifikasi tidak ada lagi tombol radio/skenario terpisah di bagian atas.
3. Amati legenda atas: `──▶ Alur Produksi Normal (Forward)` dan `⤶ - - - ◀ Garis Putus Merah: Jika QC Gagal ➔ Balik ke Rantai Pasok / Lantai Jahit`.
4. Periksa kolom Stage 5: temukan kotak merah putus-putus bertuliskan `⚠️ KETIKA QC GAGAL (DISPOSISI REJEK)` dengan panah `⤶ ◀- - -` menuju Tahap 3 dan Tahap 4.
5. Periksa kolom Stage 3 dan 4: temukan kartu penerimaan retur dan rework.
6. Pindah ke tab **Kanvas Node**: amati kurva merah putus-putus yang melengkung dari Node QC kembali ke Node Pengadaan Bahan dan Node Jahit.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1: Tooltip Animasi Alur Retur**  
  Pada `StageSwimlaneColumn`, tambahkan efek hover pada kartu disposisi QC yang menyorot (*highlight*) kolom tujuan (Stage 3 atau 4) dengan animasi glow border tipis saat kursor diletakkan di atas rute retur terkait.
- [ ] **Tantangan 2: Menghitung Akumulasi Biaya Rejek (Rework Cost Estimator)**  
  Perluas data class `PipelineFeedbackRoute` dengan properti `estimatedCostPenaltyRupiah: Long`. Tampilkan total potensi kerugian jika QC gagal langsung di samping keterangan lead time penalty.
