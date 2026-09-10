# 🎓 Modul Pembelajaran: Arsitektur Node-Style Input ala n8n & Mapping Kontrak Data Antar Modul Pabrik

> **Level Target**: Junior to Mid Multiplatform Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Node-Based Graph UI, Flow Contract Mapping, Compose Multiplatform Overlay Modals, Skiko Vector Icons, Visual Workflow Orchestration  
> **Prasyarat**: Pemahaman dasar Compose Multiplatform (Box, Column, Row, Card, Dialog/Overlay), State Flow & MVI Pattern, serta pemisahan layer `core` (domain) dan `app/shared` (presentation).  
> **Referensi Task**: n8n-Style Node Input Ports, Operator Manual Indicators, and Interactive Upstream Data Contract Inspector (WeMade ERP)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada sistem ERP manufaktur garmen konvensional, visualisasi pipeline sering kali direduksi menjadi diagram kotak statis atau daftar status tabel. Ketika seorang operator atau manajer pabrik melihat modul *"Spesifikasi BOM & Tech Pack"*, muncul kebingungan operasional:
1. **Ketidakjelasan Asal Data**: Dari mana modul ini mendapatkan data? Apakah file Tech Pack ini muncul sendiri, ataukah otomatis dikirim setelah modul *Sampling Order* selesai di-approve oleh buyer?
2. **Ketiadaan Pembeda Otomatisasi vs. Intervensi Manusia**: Sistem lama memperlakukan semua input setara sebagai teks. Padahal di lantai pabrik nyata, ada input yang **mengalir otomatis tanpa jeda** (data PO, BOM breakdown) dan ada input yang **membutuhkan kehadiran manusia fisik** (misalnya tukang pola mengunggah file CAD DXF, staff gudang mencocokkan lot celupan kain *shading*, atau QC memeriksa toleransi jahitan).
3. **Bottleneck Blindspot**: Jika modul mengalami *bottleneck*, manajemen sulit membedakan apakah hambatan terjadi karena *sistem lambat memproses data otomatis* ataukah karena *operator belum mengentri formulir manual*.

### Analogi Sederhana: Node-Based Automation Engine (ala n8n / Node-RED)
Bayangkan sebuah perakitan otomasi ala **n8n**:
- Setiap node memiliki **soket input (IN port)** dan **soket output (OUT port)**.
- Kabel yang menghubungkan node merepresentasikan pipa data otomatis (**Automated Data Stream** ⚡). Begitu node hulu selesai, data langsung mengalir ke soket node hilir dengan *zero latency*.
- Namun, pabrik bukanlah robot 100%. Di beberapa node, pipa data membutuhkan soket intervensi manusia (**Manual Operator Port** 👤). Soket ini menyala dengan tanda orang untuk memperingatkan bahwa mesin tidak bisa lanjut sebelum seorang staf fisik mengisi formulir atau memindai barcode fisik.
- Mengklik soket **IN** membuka papan kendali (*inspector modal*) yang memperlihatkan peta kabel: dari mana data hulu berasal, field apa yang dipetakan, serta siapa penanggung jawab manusianya.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda diminta membangun fitur node-style input dan modal mapping ini dari nol pada arsitektur Kotlin Multiplatform (KMP), berikut urutan logis langkah penulisan yang wajib diikuti:

```text
Step 0: Domain Modeling (PipelineInputPort di core/src/commonMain)
   ↓
Step 1: Domain Entity Enrichment (PipelineNode inputs & helper properties)
   ↓
Step 2: Realistic Factory Data Presets (PipelinePresetFactory FOB, CMT, Brand D2C)
   ↓
Step 3: MVI State & Events (FactoryFlowUiState inspectingInputNode & ViewModel)
   ↓
Step 4: Skiko Vector Visual Icons (IconPerson, IconZap, IconNodePort, IconClose)
   ↓
Step 5: Node Card Interactive Port Refactor (PipelineNodeCard IN socket handle)
   ↓
Step 6: n8n Flow Inspector Modal (NodeInputInspectorModal visual mapping canvas)
   ↓
Step 7: Canvas & Screen Orchestration (PipelineFlowCanvas & FactoryFlowScreen wiring)
   ↓
Step 8: Automated Tests & Browser Verification (jvmTest, Wasm compilation, Playwright)
```

### Mengapa Urutan Ini Krusial?
- **Domain First**: Jangan pernah mendesain UI sebelum struktur data domainnya jelas. Jika Anda langsung membuat kartu n8n di Compose tanpa tahu apakah sebuah input memiliki `sourceModuleCode` atau `operatorRole`, Anda akan terjebak merombak UI berulang kali.
- **Pure Vector Icons**: Di WebAssembly/Skiko, jangan mengandalkan emoji teks sistem (seperti `👤` atau `⚡`) karena sering menghasilkan kotak tahu kosong (*tofu boxes*) di browser tertentu. Buatlah gambar vektor Canvas murni terlebih dahulu.
- **Composable Decoupling**: Buat `NodeInputInspectorModal` sebagai composable mandiri sebelum menautkannya ke event klik di `PipelineNodeCard`.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pure Domain Model (`core/domain/pipeline/PipelineInputPort.kt`)

```kotlin
package com.eventverse.app.domain.pipeline

data class PipelineInputPort(
    val id: String,
    val name: String,
    val isManual: Boolean,
    val operatorRole: String? = null,
    val inputMethod: String? = null,
    val sourceModuleCode: String? = null,
    val sourceModuleName: String? = null,
    val sourceOutputContract: String? = null,
    val description: String = "",
    val isRequired: Boolean = true
) {
    val isAutomated: Boolean get() = !isManual
}
```

**Mengapa blok ini ditulis begini?**
1. **Zero External Dependency**: Diletakkan di modul `core`. Tidak ada import Android, Jetpack Compose, Three.js, atau pustaka eksternal manapun. Ini adalah *Pure Kotlin*.
2. **Flag `isManual` vs `isAutomated`**: Menggunakan boolean eksplisit dengan helper getter. Jika `isManual == true`, field `operatorRole` dan `inputMethod` menjadi bermakna. Jika `false`, field `sourceModuleName` dan `sourceOutputContract` yang menjadi acuan pemetaan data hulu.
3. **Immutability**: Menggunakan `data class` dengan properti `val`. Setiap perubahan data menghasilkan instance baru, menjamin *thread-safety* di multiplatform runtime.

---

### Blok B: Pengayaan Domain Node (`core/domain/pipeline/PipelineNode.kt`)

```kotlin
data class PipelineNode(
    val id: String,
    val module: BusinessModule,
    // ... properti metrik lainnya ...
    val downstreamModuleCodes: List<String> = emptyList(),
    val inputs: List<PipelineInputPort> = emptyList()
) {
    val isBypassed: Boolean get() = healthStatus == FlowHealthStatus.BYPASSED
    val isBottleneck: Boolean get() = healthStatus == FlowHealthStatus.BOTTLENECK || healthStatus == FlowHealthStatus.CRITICAL

    val manualInputs: List<PipelineInputPort> get() = inputs.filter { it.isManual }
    val automatedInputs: List<PipelineInputPort> get() = inputs.filter { it.isAutomated }
    val manualInputCount: Int get() = manualInputs.size
    val automatedInputCount: Int get() = automatedInputs.size
}
```

**Mengapa blok ini ditulis begini?**
- **Default Argument `emptyList()`**: Mencegah *breaking change* pada kode lama atau test suite yang belum menginisialisasi `inputs`.
- **Computed Domain Helpers**: Helper seperti `manualInputCount` dan `automatedInputCount` ditempatkan di domain model agar UI composable tidak perlu melakukan operasi filtering berulang di setiap frame recomposition.

---

### Blok C: Skiko Vector Icons Bebas Tofu-Box (`PipelineIcons.kt`)

```kotlin
@Composable
fun IconPerson(modifier: Modifier = Modifier, color: Color = WeMadeColors.Primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6f * density

        // Lingkaran kepala
        drawCircle(
            color = color,
            radius = w * 0.22f,
            center = Offset(w * 0.5f, h * 0.28f),
            style = Stroke(width = stroke)
        )

        // Busur pundak dan torso
        val bodyPath = Path().apply {
            moveTo(w * 0.16f, h * 0.88f)
            cubicTo(w * 0.18f, h * 0.58f, w * 0.32f, h * 0.56f, w * 0.5f, h * 0.56f)
            cubicTo(w * 0.68f, h * 0.56f, w * 0.82f, h * 0.58f, w * 0.84f, h * 0.88f)
        }
        drawPath(bodyPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}
```

**Mengapa blok ini ditulis begini?**
- **Dukungan Skiko & Wasm**: Di browser modern yang menjalankan Compose Multiplatform Web via WebAssembly, font emoji bawaan OS sering tidak termuat ke memory buffer kanvas Skia. Dengan menggambar garis lengkung kurva Bezier (`cubicTo`) langsung di atas Canvas, icon dijamin tajam pada resolusi layar apa pun tanpa dependensi font.

---

### Blok D: n8n-Style Interactive Node Port (`PipelineNodeCard.kt`)

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(if (isPresentationMode) Color(0xFF1E293B) else Color(0xFFEFF6FF))
        .border(1.dp, if (isPresentationMode) Color(0xFF334155) else Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
        .clickable {
            if (onInspectInputs != null) onInspectInputs(node) else onClick()
        }
        .padding(horizontal = 9.dp, vertical = 7.dp)
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Port Socket Handle: Lingkaran konektor n8n + label IN
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconNodePort(modifier = Modifier.size(11.dp), color = WeMadeColors.Primary)
                Text(text = "IN", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = WeMadeColors.Primary)
            }

            // Badges pembeda: Otomatis vs Manual
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (node.automatedInputCount > 0) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF0284C7).copy(alpha = 0.16f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            IconZap(modifier = Modifier.size(9.dp), color = Color(0xFF0284C7))
                            Text(text = "${node.automatedInputCount}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                        }
                    }
                }

                if (node.manualInputCount > 0) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFEA580C).copy(alpha = 0.18f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            IconPerson(modifier = Modifier.size(10.dp), color = Color(0xFFEA580C))
                            Text(text = "${node.manualInputCount} Manual", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEA580C))
                        }
                    }
                }

                Text(text = "Mapping ↗", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Primary)
            }
        }

        // Teks prasyarat input ringkas
        Text(text = node.inputContract, fontSize = 10.sp, color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurface, maxLines = 1)
    }
}
```

**Mengapa blok ini ditulis begini?**
- **Micro-Interaction**: Area `IN` memiliki click handler mandiri `onInspectInputs`. Jika user mengklik area lain di kartu, kartu akan terpilih dan membuka drawer samping umum (`NodeInspectorDrawer`). Tetapi jika user mengklik port `IN` atau chip `Mapping ↗`, dialog modal pemetaan data spesifik yang akan dibuka.

---

### Blok E: Modal Dialog Pemetaan Alur Data (`NodeInputInspectorModal.kt`)

```kotlin
@Composable
fun NodeInputInspectorModal(
    node: PipelineNode,
    isPresentationMode: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Backdrop overlay gelap
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}), // Consume click
            // ...
        ) {
            // Konten: Header, n8n Flow Bridge Visual Banner, Automated Section, Manual Section, & Footer
        }
    }
}
```

**Mental Model Penting pada Dialog Compose**:
- Perhatikan idiom: `onClick = {}` pada `Card` anak di dalam `Box` backdrop. Jika Anda lupa menambahkan click consumer ini, setiap klik di dalam kartu modal akan "tembus" (*bubble up*) ke `Box` luar, menyebabkan modal menutup sendiri saat user mengklik teks atau menggulir konten!

---

## 🚀 4. Technology & Approach ("The Why")

| Pendekatan | Pilihan Kita | Mengapa Bukan Alternatif Lain? |
|---|---|---|
| **Struktur Data Input** | Strongly typed `PipelineInputPort` | Alternatif: Menyimpan JSON string atau regex parsing pada teks `inputContract`. Risiko: Rapuh (*brittle*), mudah pecah saat format teks diubah, dan sulit diuji via unit test. |
| **Ikonografi Vektor** | Custom Skiko Canvas Path | Alternatif: Menggunakan emoji Unicode (`👤`, `⚡`). Risiko: Emoji platform-dependent, sering berubah menjadi simbol kotak silang (*tofu*) pada sistem Linux atau WebAssembly kanvas tanpa font bawaan. |
| **Penanganan Modal Overlay** | Composable `Box` dengan alpha backdrop & consumer absorption | Alternatif: Native browser `window.alert` atau `dialog` HTML5. Risiko: Tidak sinkron dengan siklus hidup Compose state, tampilan tidak konsisten antar platform, dan tidak mendukung styling dark mode tema ERP kita. |
| **Separation of Click Scope** | Dual-Action Target (Kartu vs Port IN) | Alternatif: Menjejalkan semua data ke dalam satu drawer samping. Risiko: Drawer samping menjadi sangat panjang dan padat informasi (*cognitive overload* bagi manajer pabrik). |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls & Anti-Patterns)

1. **Jebakan Click Event Bubbling**:
   - *Salah*: Hanya membuat `Box` latar belakang dengan `clickable { onClose() }` tanpa menghentikan propagasi klik pada kartu modal di dalamnya.
   - *Akibat*: Pengguna mengklik tombol scroll atau teks di dalam modal, modal malah tertutup tiba-tiba.
   - *Solusi*: Selalu pasang `.clickable(indication = null) {}` kosong pada permukaan kartu modal utama.

2. **Recomposition Loop pada State Filtering**:
   - *Salah*: Memanggil `node.inputs.filter { it.isManual }` berkali-kali di dalam fungsi Composable kartu atau canvas.
   - *Akibat*: Operasi alokasi list baru terjadi di setiap frame rendering, memicu lag saat user melakukan scroll horizontal.
   - *Solusi*: Buat properti getter `val manualInputs` atau simpan di domain entity.

3. **String Primitive Obsession**:
   - *Salah*: Mengidentifikasi modul sumber hanya lewat nama string seperti `"Sales"`.
   - *Akibat*: Jika nama tampilan modul di-rename dari `"Pelanggan & Prospek Sales"` menjadi `"CRM Komersial"`, pemetaan langsung putus.
   - *Solusi*: Selalu sertakan `sourceModuleCode: String?` (mengacu pada `BusinessModule.code` yang stabil).

---

## 🧪 6. Verifikasi & Tantangan Mandiri (Self-Verification & Hands-on Challenge)

### Cara Menguji Kebenaran Implementasi

1. **Unit Test Domain & Compilation**:
   Pastikan kode domain dan shared UI lulus pengujian otomatis tanpa error:
   ```bash
   ./gradlew :core:jvmTest
   ./gradlew :app:shared:jvmTest
   ./gradlew :app:webApp:wasmJsBrowserDevelopmentExecutableDistribution
   ```

2. **Visual & Behavioral Audit di Browser (`localhost:3000/factory-flow`)**:
   - Buka alur pabrik.
   - Perhatikan kartu modul: port `● IN` sekarang memiliki indikator `⚡ 1` (biru muda) dan `👤 1 Manual` (oranye).
   - Klik pada `Mapping ↗` modul **Spesifikasi BOM & Tech Pack**:
     - Modal terbuka dengan diagram visual n8n stream dari **Pola & Sampling Order**.
     - Di section input otomatis, periksa apakah field output modul hulu tertulis dengan jelas.
     - Di section input manual, pastikan peran penanggung jawab (*Spesialis Teknikal & R&D*) dan metode input (*Matrix Input BOM Digital*) muncul dengan ikon orang.
   - Klik tombol `✕` di kanan atas untuk memverifikasi modal tertutup kembali dengan bersih.

### 🏆 Tantangan Mandiri untuk Junior Developer
> **Tantangan**: Tambahkan filter toggle sederhana di dalam modal: sebuah chip `[Tampilkan Semua]`, `[Hanya Otomatis]`, dan `[Hanya Manual]`. Gunakan `remember { mutableStateOf(...) }` lokal di dalam `NodeInputInspectorModal` untuk menyaring daftar kartu yang ditampilkan secara reaktif!
