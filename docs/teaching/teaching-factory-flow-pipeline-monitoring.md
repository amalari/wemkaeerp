# 🎓 Modul Pembelajaran: Executive Factory Pipeline Diagram & Live Monitoring Dashboard (UI/UX Pro)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Manufacturing Process Pipeline, Preset-Driven Architecture vs Visual Drag-and-Drop, Compose Multiplatform UI/UX Pro  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform (KMP), Jetpack Compose / Compose Multiplatform, dan konsep alur manufaktur garmen (FOB vs CMT vs Brand D2C).  
> **Referensi Task**: Implementasi Diagram Alur Proses Pabrik & Live Monitoring Antrean (UI/UX Pro)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat membangun software ERP untuk industri manufaktur (seperti garmen/konveksi), founder dan developer sering kali tergoda membuat sistem yang *super fleksibel* layaknya **n8n, Zapier, atau node-cable drag-and-drop canvas**.
Di atas kertas, ide itu terdengar canggih: *"Klien bisa menyambungkan modul sesuka hati seperti menyambungkan kabel listrik"*.

Namun di dunia nyata industri konveksi, itu adalah **jebakan fatal (*over-engineering trap*)**:
1. **User Bukan Programmer**: Kepala pabrik, supervisor jahit, dan staf gudang tidak mau dan tidak punya waktu menyusun simpul kabel (*nodes & edges*) setiap kali ada order baru.
2. **Kekacauan Integritas Data**: Jika klien salah menyambungkan kabel (misal modul "Jahit" langsung dikirim ke "Surat Jalan" tanpa lewat "Quality Control"), celah cacat baju (*defect scrap*) lolos ke konsumen dan menghancurkan reputasi pabrik.
3. **Kompleksitas State**: Menyimpan graph serializer, visual geometry coordinates, dan cyclic-dependency resolver di database ERP membebani performa dan mempersulit audit data.

### Solusi Arsitektural: "The Sweet Spot"
Kita mengambil titik temu terbaik (*sweet spot*):
- **Konfigurasi tetap berbasis kode & preset terstruktur (*Preset-Driven Configuration*)**: Alur proses diatur rapi oleh kode domain yang aman, tervalidasi, dan type-safe (`FOB Full Package`, `CMT Jasa Jahit Makloon`, `Brand Konveksi D2C`).
- **Layout Alur Horizontal Swimlane (Left-to-Right Stages)**: Menghindari jebakan kartu memanjang ke bawah selebar 1900px yang membuat mata lelah. Kita membagi 5 tahapan pabrik ke dalam **5 kolom horisontal berdampingan** dengan panah konektor antar-tahap (`──▶`) dan *Macro Process Stepper* di bagian atas.
- **Antarmuka Superadmin menyajikan Diagram Alur Visual Otomatis (*Auto-Generated Interactive Flowchart & Live Monitoring*)**: Superadmin dan direktur pabrik bisa melihat alur visual yang menghubungkan seluruh departemen dalam 1 layar tanpa perlu scroll jauh ke bawah, mendeteksi titik penumpukan antrean (*bottleneck WIP*), serta menyalakan **Mode Presentasi Klien** untuk mendemonstrasikan kejelasan kontrak data (Input & Output) kepada calon pembeli jasa pabrik.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun fitur visual pipeline monitoring ini dari nol di proyek DDD multiplatform, jangan langsung menyentuh canvas UI! Ikuti urutan langkah berikut:

```
Step 1: Domain Preset & Stages (core/domain/pipeline/)
   └── GarmentBusinessPreset.kt & PipelineStage.kt
Step 2: Status Kesehatan & Node Model (core/domain/pipeline/)
   └── FlowHealthStatus.kt & PipelineNode.kt
Step 3: Factory Penghasil Snapshot & Metrik (core/domain/pipeline/)
   └── PipelinePresetFactory.kt
Step 4: Unit Test Logika Domain (core/commonTest/)
   └── PipelinePresetFactoryTest.kt (Pastikan kalkulasi WIP & bypass CMT teruji)
Step 5: State Holder & MVI Event (app/shared/presentation/pipeline/)
   └── FactoryFlowUiState.kt & FactoryFlowViewModel.kt
Step 6: Komponen UI/UX Pro Modular (app/shared/presentation/pipeline/components/)
   ├── ExecutiveKpiHeader.kt (4 Kartu Metrik Eksekutif)
   ├── PresetSelectorBar.kt (Pill Preset & Tombol Mode Presentasi)
   ├── PipelineNodeCard.kt (Kartu Node dengan Stage, Divisi, & Health Pill)
   ├── PipelineFlowCanvas.kt (Kanvas Alur dengan Panah Konektor Arah)
   └── NodeInspectorDrawer.kt (Side Drawer Pembongkar Kontrak Input/Output)
Step 7: Layar Orkestrasi Utama & Integrasi Navigasi (app/shared/)
   ├── FactoryFlowScreen.kt
   └── App.kt (Navigasi & Auth Guard)
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Mari kita bedah kode kunci di setiap lapisan:

### Blok A: Pure Domain Presets (`GarmentBusinessPreset.kt`)
```kotlin
enum class GarmentBusinessPreset(
    val code: String,
    val displayName: String,
    val shortBadge: String,
    val description: String,
    val targetClientProfile: String
) {
    FOB_FULL_PACKAGE(
        code = "fob_full_package",
        displayName = "FOB (Full Order / Buy) — Paket Lengkap",
        shortBadge = "FOB Full Package",
        description = "Pengerjaan hulu-ke-hilir: Dari pengadaan bahan baku kain, aksesoris, pembuatan pola/sample, produksi massal, hingga ekspedisi ekspor/retail.",
        targetClientProfile = "Pabrik OEM, Ekspor Garmen, atau Konveksi Skala Menengah ke Atas"
    ),
    CMT_MAKLOON(
        code = "cmt_makloon",
        displayName = "CMT (Cut, Make, Trim) — Jasa Jahit Makloon",
        shortBadge = "CMT Jasa Jahit",
        description = "Pengerjaan jasa jahit murni. Pola potong & kain rol utama disediakan sepenuhnya oleh Buyer/Brand. Pengadaan bahan baku di-bypass.",
        targetClientProfile = "Vendor Makloon, Sub-kontraktor Jahit, Mitra Konveksi Rumahan/Sentra"
    ),
    BRAND_D2C(
        code = "brand_d2c",
        displayName = "Brand Konveksi Sendiri (Direct to Consumer)",
        shortBadge = "Brand D2C Internal",
        description = "Model bisnis terintegrasi brand sendiri. Menghubungkan peluncuran katalog baru, sample approval cepat, stok jadi, dan pesanan multichannel.",
        targetClientProfile = "Clothing Line Lokal, Distro Brand, Pabrik Seragam Custom Mandiri"
    );
}
```
**Mengapa blok ini ditulis begini?**
- Kita memodelkan model bisnis riil industri garmen sebagai `enum class` kelas satu di layer domain `core`.
- Setiap preset membawa metadata edukatif (`description`, `targetClientProfile`) yang langsung bisa dipakai oleh UI presentasi tanpa membebani logic dengan string sembarangan.

---

### Blok B: Node Pipeline & Kontrak Input/Output (`PipelineNode.kt`)
```kotlin
data class PipelineNode(
    val id: String,
    val module: BusinessModule,
    val stage: PipelineStage,
    val stepNumber: Int,
    val title: String,
    val description: String,
    val assignedDepartment: String,
    val deptColorHex: Long,
    val inputContract: String,
    val outputContract: String,
    val wipPieces: Int,
    val cycleTimeHours: Double,
    val healthStatus: FlowHealthStatus,
    val healthMessage: String,
    val downstreamModuleCodes: List<String> = emptyList()
) {
    val isBypassed: Boolean get() = healthStatus == FlowHealthStatus.BYPASSED
    val isBottleneck: Boolean get() = healthStatus == FlowHealthStatus.BOTTLENECK || healthStatus == FlowHealthStatus.CRITICAL
}
```
**Mengapa blok ini ditulis begini?**
- `inputContract` dan `outputContract` adalah kunci edukasi ke klien. Daripada hanya memberi nama "Modul Sewing", kita mendefinisikan dengan presisi:
  - *Input*: "Kain Tergelar, Bundel Pola Bertiket Barcode, & SPK Line".
  - *Output*: "Pakaian Jadi Belum Diinspeksi (Grey Goods) + Catatan Target Harian".
- Properti `isBypassed` dan `isBottleneck` berupa *computed properties* (tanpa mutasi state), menjamin konsistensi saat kanvas me-render visual redup (opacity) pada tahapan makloon.

---

### Blok C: Factory Alur & Metrik (`PipelinePresetFactory.kt`)
```kotlin
object PipelinePresetFactory {
    fun createSnapshot(preset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT): FactoryPipelineSnapshot {
        val nodes = buildNodesForPreset(preset)
        val activeNodes = nodes.filterNot { it.isBypassed }
        val bottlenecksCount = nodes.count { it.isBottleneck }
        val totalWip = activeNodes.sumOf { it.wipPieces }

        val totalHours = activeNodes.sumOf { it.cycleTimeHours }
        val avgLeadDays = (totalHours / 8.0 * 10.0).let { kotlin.math.round(it) / 10.0 }

        val baseScore = 100
        val bottleneckPenalty = nodes.count { it.healthStatus == FlowHealthStatus.BOTTLENECK } * 7
        val criticalPenalty = nodes.count { it.healthStatus == FlowHealthStatus.CRITICAL } * 15
        val healthScore = (baseScore - bottleneckPenalty - criticalPenalty).coerceIn(40, 100)

        return FactoryPipelineSnapshot(...)
    }
}
```
**Mengapa blok ini ditulis begini?**
- Pure functions tanpa efek samping (*side-effects*). Fungsi ini bisa diuji 100% menggunakan unit test murni dalam waktu kurang dari 5 milidetik.
- Penalti skor kesehatan dihitung secara matematis (`healthScore = 100 - (bottleneck * 7) - (critical * 15)`), memberikan indikator visual yang hidup bagi eksekutif.

---

### Blok D: Visual Directional Connector (`PipelineFlowCanvas.kt`)
```kotlin
@Composable
private fun DirectionalFlowConnector(
    sourceStep: Int,
    outputLabel: String,
    isPresentationMode: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Vertical connecting stem
        Box(modifier = Modifier.width(2.dp).height(14.dp).background(...))

        // Data transfer capsule with directional indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(...).padding(...)
        ) {
            Text(text = "▼ Diteruskan:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Primary)
            Text(text = outputLabel.take(45) + "...", fontSize = 9.sp)
        }

        Box(modifier = Modifier.width(2.dp).height(14.dp).background(...))
    }
}
```
**Mengapa blok ini ditulis begini?**
- Alur divisualisasikan dengan garis konektor vertikal yang menyertakan label data serah terima (*handoff payload*).
- Menghilangkan kebingungan pengguna mengenai: *"Setelah modul ini selesai, data dikirim ke mana dan dalam bentuk apa?"*.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan Arsitektur | Alternatif yang Ada | Mengapa Kita Memilih Pendekatan Ini? | Risiko Fatal Jika Memakai Alternatif |
|---|---|---|---|
| **Preset-Driven Engine** | Visual Node Drag-and-Drop (n8n/ReactFlow clone) | Type-safe, tervalidasi di compile-time, bebas bug circular-loop, ramah operator pabrik. | Over-engineering, runtime crash akibat salah sambung kabel alur oleh user biasa. |
| **Pure Kotlin Domain Model** | Embed logic di Compose Composable | Portabel ke semua target KMP (Android, iOS, Wasm, JS, Server). | Sulit diuji otomatis, Compose recomposition membengkak dan laggy. |
| **Client Presentation Mode** | Screenshot statis / Dokumen PDF | Interaktif, data live, calon klien bisa mengklik node langsung untuk verifikasi input/output. | Presentasi statis terasa kaku dan tidak mencerminkan kecanggihan software ERP modern. |
| **Declarative Connectors** | Dynamic Physics Simulation (Canvas Force Directed) | Sangat ringan, deterministik, bekerja mulus di Wasm/Browser tanpa borong daya baterai laptop. | Animasi fisika berat membuat laptop low-end pabrik panas dan browser patah-patah. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

### 1. Jebakan "Stale Selected Node" saat Ganti Preset
- **Masalah**: Pengguna sedang membuka drawer modul *Bahan Baku Kain* di preset FOB, lalu tiba-tiba berganti preset ke CMT (di mana Bahan Baku di-bypass). Jika `selectedNode` tidak di-reset, drawer menampilkan data basi dari preset lama.
- **Solusi Kita**: Pada `FactoryFlowViewModel`:
  ```kotlin
  is FactoryFlowUiEvent.SelectPreset -> {
      val newSnapshot = PipelinePresetFactory.createSnapshot(event.preset)
      _uiState.update {
          it.copy(
              selectedPreset = event.preset,
              snapshot = newSnapshot,
              selectedNode = null // Wajib reset!
          )
      }
  }
  ```

### 2. Jebakan Lupa Mengimpor `border` di Jetpack Compose
- **Masalah**: Ingin menambahkan border dinamis ke `Box` menggunakan `.border(...)`, tetapi lupa import `androidx.compose.foundation.border`, menyebabkan error kompilasi KMP `Unresolved reference 'border'`.
- **Solusi**: Pastikan import `androidx.compose.foundation.border` selalu ada saat menambahkan border modifikasi pada UI.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Kodingan kita dibuktikan dengan 2 level pengujian:

### A. Automated Unit Tests (`core:jvmTest` & `app:shared:jvmTest`)
```bash
./gradlew :core:jvmTest :app:shared:jvmTest
```
Pengujian memastikan:
1. `fobPreset_shouldActivateAllModulesWithoutBypass`: 9 modul aktif, 0 bypassed, skor kesehatan 40-100%.
2. `cmtPreset_shouldBypassTechPackAndInventoryModules`: Tepat 2 modul di-bypass (Tech Pack & Bahan Baku Kain).
3. `FactoryFlowViewModelTest`: Filter pencarian `kain`, pemilihan stage `COMMERCIAL`, dan toggle mode presentasi bekerja presisi.

### B. Multiplatform Compilation Test
```bash
./gradlew :app:webApp:compileKotlinWasmJs :app:webApp:compileKotlinJs
```
Memverifikasi bahwa kode Compose UI dan domain ini 100% kompatibel dengan WebAssembly (Wasm) dan JavaScript tanpa ada API JVM-only yang bocor.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

Biar kemampuan arsitektur kamu makin tajam, coba selesaikan 2 tantangan mandiri ini:

- [ ] **Tantangan 1 (Filter Status Bottleneck)**: Tambahkan satu chip filter di samping `FilterByStage` untuk memfilter hanya modul yang sedang berstatus `BOTTLENECK` atau `CRITICAL` agar supervisor dapat melihat daftar masalah dalam 1 klik.
- [ ] **Tantangan 2 (Audio Alert Beep)**: Hubungkan event perubahan status bottleneck ke audio synthesizer sederhana (misal suara ping pelan) saat `isSimulatingRealtime` aktif dan mendeteksi WIP melebihi 1.000 pcs.
