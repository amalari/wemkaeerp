# 🎓 Modul Pembelajaran: Pemangkasan Toolbar Simulasi & Presentasi Alur Pabrik (UI Surface Simplification)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: UI Simplification, Dead Code Elimination, Compose Multiplatform Declarative Layout, Clean Architecture  
> **Prasyarat**: Compose Multiplatform Layouts, State Management, Clean Code  
> **Referensi Task**: Factory Flow UI Simplification (Toolbar Pruning)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Saat membangun dashboard atau alat visualisasi proses manufaktur enterprise, developer sering tergoda menambahkan berbagai kontrol sekunder di layar utama: tombol simulasi real-time, mode presentasi layar penuh, dropdown alur operasional, dan indikator status live streaming.

- **Masalah Nyata**:
  1. **Visual Overcrowding**: Layar operasional pabrik yang seharusnya fokus menampilkan 5 tahapan produksi (Komersial ➔ Spesifikasi ➔ Rantai Pasok ➔ Produksi ➔ Mutu) terdistraksi oleh toolbar kendali yang belum dibutuhkan oleh user di fase ini.
  2. **Cognitive Overhead**: Operator dan manajer pabrik bingung membedakan antara "alur nyata yang tersimpan di sistem" dengan "tombol skenario simulasi".
  3. **Dead / Unused Code Debt**: Menyimpan komponen UI yang tidak terpakai membebani waktu kompilasi Kotlin/Wasm dan menambah risiko regresi.
- **Analogi Sederhana**:
  Bayangkan papan petunjuk jalan tol. Di papan petunjuk yang dibutuhkan pengemudi adalah jalur pintu keluar dan jarak kilometer (alur inti). Jika kita memasang tombol simulator navigasi, pengatur cuaca, dan tombol saklar demo di bawah papan petunjuk tol tersebut, pengemudi akan bingung dan terdistraksi.
- **Hasil Akhir yang Diharapkan**:
  Layar [FactoryFlowScreen.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/pipeline/FactoryFlowScreen.kt) kini bersih, langsung mengalir dari Header Identitas Pabrik ke Macro Stepper dan Kanvas Kolom Swimlane 5 Tahap tanpa toolbar perantara.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta memangkas atau menyederhanakan antarmuka pengguna (UI refactoring) pada arsitektur Compose Multiplatform:

1. **Langkah 1: Cek Dependensi Komponen (Blast Radius Analysis)**
   - Gunakan `grep` untuk melacak di mana saja `PresetSelectorBar` dipanggil.
   - Pastikan tidak ada screen atau widget lain yang bergantung pada komponen tersebut.
2. **Langkah 2: Hapus Invokasi & Import di Screen Container**
   - Hapus blok pemanggilan `PresetSelectorBar(...)` di [FactoryFlowScreen.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/pipeline/FactoryFlowScreen.kt).
   - Bersihkan unused import `com.eventverse.app.presentation.pipeline.components.PresetSelectorBar`.
3. **Langkah 3: Hapus File Komponen yang Usang (Zero Dead Code)**
   - Hapus berkas fisik `PresetSelectorBar.kt` agar tidak menjadi dead code di source tree.
4. **Langkah 4: Validasi Kompilasi & Multi-Target Test**
   - Jalankan kompilasi WasmJS dan unit test JVM (`:core:jvmTest`, `:app:shared:jvmTest`, `:app:webApp:wasmJsBrowserDevelopmentExecutableDistribution`).

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Struktur Baru `FactoryFlowScreen.kt`

```kotlin
// Header Identitas Pabrik & GCP Company Switcher
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Alur Operasional Pabrik",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnBackground
            )
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WeMadeColors.Primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = activeCompany.name,
                    color = WeMadeColors.Primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(
            text = "Visualisasi alur kerja modul dari order hingga pengiriman, dilengkapi deteksi bottleneck dan kontrak data antar divisi.",
            fontSize = 13.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

// Langsung menuju ke Kanvas Utama Swimlane tanpa toolbar perantara
PipelineFlowCanvas(
    nodes = state.filteredNodes,
    selectedNode = state.selectedNode,
    selectedStageFilter = state.selectedStageFilter,
    isPresentationMode = isPresentationMode,
    hideBypassedNodes = state.hideBypassedNodes,
    onSelectNode = { viewModel.onEvent(FactoryFlowUiEvent.SelectNode(it)) },
    onInspectInputs = { viewModel.onEvent(FactoryFlowUiEvent.InspectNodeInputs(it)) },
    onStageFilterChanged = { viewModel.onEvent(FactoryFlowUiEvent.FilterByStage(it)) }
)
```

**Mengapa blok ini ditulis begini?**
- **Direct Layout Flow**: Elemen header langsung diikuti oleh `PipelineFlowCanvas`. Tidak ada lagi baris pemisah bertuliskan alur operasional dummy, status streaming, atau tombol presentasi.
- **Vertical Rhythm**: Menghilangkan `PresetSelectorBar` menghemat ~64dp ruang vertikal di layar, sehingga kartu modul garmen di dalam swimlane langsung terlihat tanpa perlu banyak scroll.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif yang Ada | Mengapa Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Hapus Total Komponen Usang (`git rm`)** | Menyembunyikan dengan `if (false)` atau `visibility = GONE` | Menjaga codebase tetap higienis, bersih dari dead code, dan mempercepat build WasmJS. | Code rot; kode yang disembunyikan tetap dikompilasi dan rentan error saat dependensi sekitarnya berubah. |
| **GCP Dropdown sebagai Single Source of Truth** | Menyediakan dropdown kedua untuk memilih alur/perusahaan | Menghindari dual-state desinkronisasi. Penggantian tenant cukup dilakukan satu kali di navbar atas. | User bingung jika dropdown navbar memilih PT A tetapi dropdown di toolbar memilih PT B. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Menyisakan Unused Import**
   - *Kenapa bahaya*: Menyisakan unused imports membuat linter IDE merah dan menunjukkan kode yang tidak terawat.
   - *Solusi*: Selalu hapus import yang bersangkutan segera setelah komponen dihapus.
2. **Jebakan 2: Takut Menghapus Kode (Fear of Deleting Code)**
   - *Kenapa bahaya*: Sering kali junior developer mengomentari kode (`// PresetSelectorBar(...)`) karena takut nanti diperlukan lagi. Hal ini mengotori file.
   - *Solusi*: Manfaatkan Git history. Semua baris yang pernah ditulis tercatat di VCS, sehingga jangan ragu menghapus kode yang memang sudah tidak diperlukan.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Unit Testing**:
   ```bash
   ./gradlew :core:jvmTest :app:shared:jvmTest
   ```
   Memastikan state flow dan view model tidak memiliki dependensi yang patah.
2. **WasmJS Bundle Verification**:
   ```bash
   ./gradlew :app:webApp:wasmJsBrowserDevelopmentExecutableDistribution
   ```
   Memastikan bundel WebAssembly ter-compile sukses tanpa warning referensi yang hilang.
3. **Manual Browser Verification**:
   Buka `http://localhost:3000/#pipeline/flow` dan lakukan hard refresh (`Cmd + Shift + R`).
   Pastikan tampilan langsung menyajikan:
   - Header identitas perusahaan.
   - 5 indikator proses makro.
   - 5 kolom swimlane modul alur kerja pabrik.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Periksa apakah ada action event di [FactoryFlowUiEvent.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/pipeline/FactoryFlowUiEvent.kt) yang sudah tidak pernah dipicu oleh UI (`TogglePresentationMode`, `ToggleSimulation`), lalu tandai atau sederhanakan state holder-nya.
- [ ] **Tantangan 2**: Ukur perbedaan tinggi kanvas yang didapat di browser sebelum dan sesudah toolbar dihapus menggunakan Inspect Element.
