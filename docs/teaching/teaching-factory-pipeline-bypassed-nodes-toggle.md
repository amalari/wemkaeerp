# 🎓 Modul Pembelajaran: Dynamic Bypassed Node Filtering & Value Stream Mapping (VSM)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Value Stream Mapping (VSM), State-Driven UI Filtering, Compose Multiplatform, Skiko Canvas Custom Icons, Clean DDD  
> **Prasyarat**: Dasar Kotlin, MVI State Modeling, Compose Multiplatform Declarative UI  
> **Referensi Task**: Pipeline Monitoring — Bypassed Stage Toggle & Streamlined Flow  

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Dalam industri manufaktur garmen, model kontrak pabrik sangat bervariasi:
1. **FOB (Full Package)**: Pabrik membiayai pengadaan kain rol, mendesain pola CAD, dan menjahit baju.
2. **CMT (Cut, Make, Trim / Makloon)**: Pabrik hanya menjual jasa potong dan jahit. Kain dan panduan pola disuplai 100% oleh Buyer.

Jika sebuah ERP memaksakan alur statis atau menyembunyikan modul secara hardcoded:
- **Jika modul dimunculkan terus**: Pengguna CMT mengeluh layar penuh kartu abu-abu (*visual clutter*) yang tidak mereka kerjakan.
- **Jika modul dihapus mentah-mentah**: Klien atau auditor bingung: *"Kainnya dari mana? Kok tiba-tiba langsung jahit di mesin? Apakah ada modul yang rusak atau data yang hilang?"*

### Solusi Desain UI/UX Pro (Opsi B: Adaptive Toggle)
Kami menerapkan solusi **Dua Mode (Toggleable VSM)**:
- **Default (Clean Mode)**: Modul yang di-bypass disembunyikan secara otomatis agar kanban/swimlane pabrik fokus hanya pada tugas aktif. Kolom tahapan yang kosong (*Rantai Pasok*) dilewati secara mulus (*bridge handoff* langsung menyambung).
- **Audit/Presentation Mode**: Pengguna dapat menyalakan toggle `[Sembunyikan Bypass (2)]` kapan saja untuk memperlihatkan audit lengkap bahwa bahan baku disuplai pihak ketiga.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun fitur filter dinamis seperti ini dari awal:

1. **Langkah 0: State Modeling di UI State (`FactoryFlowUiState.kt`)**
   - Tambahkan flag boolean `hideBypassedNodes: Boolean = true`.
   - Hitung properti terderivasi `bypassedCount` dan filter `filteredNodes` secara deklaratif.
2. **Langkah 1: Event & ViewModel (`FactoryFlowViewModel.kt`)**
   - Tambahkan sealed event `ToggleHideBypassed`.
   - Di ViewModel, mutasikan state secara aman (`_uiState.update { ... }`).
3. **Langkah 2: Skiko Vector Icons (`PipelineIcons.kt`)**
   - Hindari icon font emoji OS yang berisiko menjadi kotak "tofu" di WebAssembly (WasmJs).
   - Gambar icon `IconEye` dan `IconEyeOff` menggunakan API native `androidx.compose.ui.graphics.Path` dan `drawPath`.
4. **Langkah 3: Komponen Kanvas & Adaptasi Swimlane (`PipelineFlowCanvas.kt`)**
   - Tambahkan tombol pill toggle di bilah kontrol hanya jika `bypassedCount > 0`.
   - Di layout swimlane horizontal, filter daftar tahapan (`visibleStages`) sehingga kolom yang modulnya 0 tidak memakan ruang kosong 300dp.
   - Di stepper makro atas, beri label `Di-bypass (Buyer)` agar pengguna tetap melihat gambaran 5 horizon pabrik.
5. **Langkah 4: Unit Testing (`FactoryFlowViewModelTest.kt`)**
   - Uji transisi state ketika preset berganti ke CMT dan tombol toggle ditekan.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Deklaratif Filter di UiState
```kotlin
data class FactoryFlowUiState(
    // ...
    val hideBypassedNodes: Boolean = true
) {
    val bypassedCount: Int get() = snapshot.nodes.count { it.isBypassed }

    val filteredNodes: List<PipelineNode>
        get() = snapshot.nodes
            .filter { node ->
                if (hideBypassedNodes) !node.isBypassed else true
            }
            .filter { node ->
                selectedStageFilter == null || node.stage == selectedStageFilter
            }
            // ... filter pencarian
}
```
**Mental Model:**
- Perhatikan bahwa `filteredNodes` adalah **computed getter (`get()`)**, bukan variabel mutable yang disimpan terpisah. Ini menjamin *Single Source of Truth* dari `snapshot.nodes` tanpa risiko data tidak sinkron.

---

### Blok B: Menghindari Kolom Hantu di Swimlane Horizontal
```kotlin
val visibleStages = if (hideBypassedNodes) {
    PipelineStage.entries.filter { stage -> (groupedByStage[stage] ?: emptyList()).isNotEmpty() }
} else {
    PipelineStage.entries
}

visibleStages.forEachIndexed { stageIndex, stage ->
    // Render kolom aktif saja
    if (stageIndex < visibleStages.size - 1) {
        StageTransitionBridge(...) // Panah handoff otomatis menghubungkan kolom aktif berikutnya
    }
}
```
**Mental Model:**
- Jika Stage 3 (*Rantai Pasok*) kosong karena modul kain di-bypass, kita **tidak boleh** merender kolom kosong selebar 305dp.
- Dengan memfilter `visibleStages`, Stage 2 (*Spesifikasi*) langsung memiliki jembatan handoff panah yang mengarah ke Stage 4 (*Produksi*). Alur terasa mengalir natural.

---

### Blok C: Skiko Canvas Vector Icon (Eye & EyeOff)
```kotlin
@Composable
fun IconEye(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5f * density

        val eyePath = Path().apply {
            moveTo(w * 0.1f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.15f, w * 0.9f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.85f, w * 0.1f, h * 0.5f)
            close()
        }
        drawPath(eyePath, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
        drawCircle(color = color, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.5f))
    }
}
```
**The "Why":**
- Mengapa menggunakan Canvas Path murni daripada SVG loader atau Unicode emoji?  
  Pada Compose Multiplatform WasmJS, emoji bergantung pada font lokal OS klien. Jika klien menggunakan Windows atau Linux lawas tanpa font warna NotoColorEmoji, emoji akan menjadi kotak "tofu" kosong. Canvas Path dirender langsung oleh Skia engine, 100% konsisten di semua browser dan OS.

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Membuang Data Node dari Snapshot Domain:**  
   Jangan menghapus node dari `snapshot.nodes`. Data domain harus tetap utuh agar saat pengguna mematikan filter, data kartu dan metrik WIP tidak hilang atau perlu di-fetch ulang dari server.
2. **Lupa Menyesuaikan Jembatan Panah (*StageTransitionBridge*):**  
   Jika indeks `stageIndex < PipelineStage.entries.size - 1` digunakan langsung tanpa mengecek `visibleStages`, panah terakhir bisa muncul menggantung di ujung kanan kanvas tanpa tujuan kolom berikutnya.
3. **Menggunakan `quadraticBezierTo` yang Deprecated:**  
   Compose 1.7+ mendeprekasi `quadraticBezierTo` dan menggantinya dengan `quadraticTo` agar konsisten dengan `cubicTo`.

---

## 🧪 5. Verifikasi & Pengujian

Jalankan perintah berikut untuk memvalidasi:
```bash
./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.pipeline.FactoryFlowViewModelTest"
./gradlew :app:webApp:compileKotlinWasmJs
```

Semua pengujian unit test dan kompilasi WebAssembly dipastikan 100% hijau (*passing*).
