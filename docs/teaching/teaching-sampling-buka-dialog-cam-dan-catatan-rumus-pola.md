# 🎓 Modul Pembelajaran: Dialog Input Program CAM & Catatan Rumus Pola saat Transisi Tahap

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform UI, Domain-Driven Design (DDD), Workflow Stage Gating, State Synchronization  
> **Prasyarat**: Pemahaman MVI StateFlow (`UiState`/`UiEvent`), Lifecycle transisi Kanban, Single Source of Truth Stage Input  
> **Referensi Task**: `http://localhost:3000/sampling-order` — Transisi ke tahap Program CAM membuka dialog pengisian Program CAM & Catatan Rumus Pola

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada alur kerja produksi sampling garmen knitwear (rajut), programmer mesin CAM (seperti Shima Seiki / Stoll) tidak dapat mulai memprogram mesin rajut tanpa instruksi program dan catatan kalkulasi pola yang jelas.
Sebelumnya, terdapat anomali alur di antarmuka Kanban:
1. Ketika kartu di kolom **Penentuan Alur** diklik tombol *"Alur Siap -> Mulai CAM ->"* atau di-drag langsung ke kolom **Program CAM**, sistem langsung memajukan tahap kartu ke `CAM_PROGRAMMING` di server **tanpa membuka dialog apapun**. Kartu berpindah secara senyap dengan lembar Program CAM kosong.
2. Ketika kartu sudah berada di kolom Program CAM, tombol pada kartu justru *"Masuk Mesin Rajut ->"*, dan saat diklik malah membuka dialog pengisian Program CAM dengan tombol *"Simpan Program -> Masuk Mesin Rajut"*. Ini menyebabkan kartu melompati tahap Program CAM dan langsung terdorong ke Mesin Rajut.
3. Programmer kehilangan momen kerja untuk mengisi file program per komponen/bagian garmen (Depan, Belakang, Lengan, dll.) serta memo kalkulasi **Catatan Rumus Pola** saat berpindah ke meja CAM.

### Analogi Sederhana
Bayangkan loket pendaftaran di rumah sakit:
- **Alur yang Salah**: Pasien masuk ke ruang dokter spesialis tanpa mengisi form keluhan dan rekam medis di meja perawat. Begitu dokter mau memeriksa, dokter terpaksa menyuruh pasien mengisi formulir pendaftaran perawat, lalu pasien langsung dipulangkan ke apotek tanpa diobati.
- **Alur yang Benar**: Saat pasien diarahkan dari meja skrining (Penentuan Alur) menuju meja perawat (Program CAM), pintu masuknya adalah pengisian data program rajut dan catatan rumus pola. Setelah data terekam, barulah pasien resmi duduk di ruang tunggu Program CAM.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun atau merefaktor alur transisi bertahap ini dari awal:

1. **Langkah 0: Kontrak Domain (`core/`)**
   - Di `StageWorkInput.kt`, tegakkan bahwa transisi ke `CAM_PROGRAMMING` menuntut lembar kerja (`requiresStageWorksheet() = true`).
   - Buat helper extension `SamplingOrder.hasCompleteCamWorksheet()` untuk mengecek apakah section wajib CAM (`CAM_REQUIRED`: `PROGRAM`, `FEEDER_INSTRUCTIONS`) sudah terpenuhi sebelum boleh masuk ke Mesin Rajut.
2. **Langkah 1: State & Event MVI (`app/shared/`)**
   - Di `SamplingUiState.kt`, tambahkan flag `spkDetailFocusCam: Boolean` pada state dan event `OpenSpkDetailDialog(order, focusFlow, focusCam)`.
   - Di `SamplingViewModel.kt`, tangani `focusCam` agar tersimpan di state saat dialog dibuka.
3. **Langkah 2: Router Transisi Kanban (`SamplingWorkspaceScreen.kt`)**
   - Pada callback `onAdvanceStageRequested(order, stage)`:
     - Jika `stage == CAM_PROGRAMMING`: Buka `SamplingSpkDetailDialog` dengan `focusCam = true` (atau `StageAdvanceDialog`).
     - Jika order maju ke tahap lain yang butuh worksheet: Buka `StageAdvanceDialog`.
4. **Langkah 3: Integrasi Dialog Detail SPK (`SamplingSpkDetailDialog.kt`)**
   - Terima parameter `initialShowCamSection: Boolean = false`.
   - Inisialisasi `isCamSectionVisible = initialShowCamSection || isCamStage`.
   - Bila `isGateStage` (`NEW_INTAKE` / `FLOW_REVIEW`):
     - Tombol *"Alur Siap -> Mulai CAM"* menampilkan/menggulir ke `CamProgramTabbedSection`.
     - Tombol aksi utama berubah menjadi **"Simpan & Masuk Program CAM"**.
     - Tombol ini memvalidasi tab kelengkapan (Kode Program & Feeder terisi) lalu memanggil `onSubmitCamProgram` dengan `targetStage = CAM_PROGRAMMING`.
5. **Langkah 4: Tombol Aksi di Tahap Program CAM**
   - Ketika order sudah berada di `CAM_PROGRAMMING`:
     - Tombol *"Simpan Program"* menyimpan perubahan draft tanpa memajukan tahap.
     - Tombol *"Masuk Mesin Rajut ->"* menyimpan program sekaligus memajukan SPK ke `MACHINE_KNITTING`.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Domain Gating (`core/StageWorkInput.kt`)
```kotlin
fun SamplingPipelineStage.requiresStageWorksheet(): Boolean =
    this == SamplingPipelineStage.CAM_PROGRAMMING ||
        this == SamplingPipelineStage.LINKING_ASSEMBLY

fun SamplingOrder.hasCompleteCamWorksheet(): Boolean {
    val camInput = stageInputFor(SamplingPipelineStage.CAM_PROGRAMMING) ?: return false
    return StageSectionNames.CAM_REQUIRED.all { name ->
        camInput.section(name)?.hasFilledRow == true
    }
}
```
**Mengapa blok ini ditulis begini?**
- `requiresStageWorksheet()` menjadi *single source of truth* bagi UI Kanban board untuk mengetahui tahap mana yang wajib meminta formulir sebelum perpindahan sah.
- `hasCompleteCamWorksheet()` memberikan jaminan domain bahwa kartu yang sudah di CAM tidak dapat didorong ke Mesin Rajut jika kode program biner mesin dan instruksi feeder panah masih kosong.

---

### Blok B: Kanban Stage Advance Interceptor (`SamplingWorkspaceScreen.kt`)
```kotlin
onAdvanceStageRequested = { order, stage ->
    if (stage == SamplingPipelineStage.CAM_PROGRAMMING) {
        // Masuk Program CAM menuntut lembar Program CAM (program, feeder,
        // tenselity, dan catatan rumus pola) diisi di dialog Detail SPK.
        viewModel.onEvent(SamplingUiEvent.OpenSpkDetailDialog(order, focusCam = true))
    } else if (stage.requiresStageWorksheet()) {
        viewModel.onEvent(SamplingUiEvent.OpenStageAdvanceDialog(order, stage))
    } else {
        viewModel.onEvent(SamplingUiEvent.AdvanceStage(order.id, stage))
    }
}
```
**Mengapa blok ini ditulis begini?**
- Semua jalur menuju Program CAM (baik mengklik tombol *"Alur Siap -> Mulai CAM ->"* di kartu Kanban maupun drag-and-drop kartu ke kolom Program CAM) dicegat di sini.
- Alih-alih diam-diam mengubah tahap di backend, UI langsung membukakan dialog `SamplingSpkDetailDialog` dengan fokus otomatis ke lembar kerja CAM.

---

### Blok C: State & UI Dialog Detail SPK (`SamplingSpkDetailDialog.kt`)
```kotlin
var isCamSectionVisible by remember(order.id, initialShowCamSection, isCamStage) {
    mutableStateOf(initialShowCamSection || isCamStage)
}
val isFlowLocked = order.pipelineStage.order >= SamplingPipelineStage.CAM_PROGRAMMING.order || isCamSectionVisible

// Auto-scroll ke section CAM saat dibuka
LaunchedEffect(isCamSectionVisible) {
    if (isCamSectionVisible && !isCamStage) {
        delay(120)
        scrollState.animateScrollTo(scrollState.maxValue)
    }
}
```
Dan kontrol tombol footer:
```kotlin
if (isGateStage) {
    if (!isFlowSectionVisible) {
        ClayButton(text = "Tentukan Alur Desain ->", onClick = { isFlowSectionVisible = true })
    } else if (!isCamSectionVisible) {
        ClayButton(text = "Alur Siap -> Mulai CAM", onClick = { isCamSectionVisible = true })
    } else {
        ClayButton(
            text = "Simpan & Masuk Program CAM",
            style = ClayButtonStyle.Primary,
            onClick = {
                val (currentTabs, _) = parseCamSections(camSections)
                if (currentTabs.isEmpty() || currentTabs.any { !it.isComplete }) {
                    camValidationTrigger++
                } else {
                    onSubmitCamProgram(camSections)
                }
            }
        )
    }
}
```
**Mengapa blok ini ditulis begini?**
- User yang baru membuka detail SPK dari kolom SPK Baru dapat menelaah alur desain terlebih dahulu, kemudian menekan *"Alur Siap -> Mulai CAM"*, yang secara mulus memunculkan form tab bagian garmen (`CamProgramTabbedSection`) dan textarea Catatan Rumus Pola.
- Tombol *"Simpan & Masuk Program CAM"* menjalankan validasi frontend (`isComplete` pada setiap tab bagian garmen). Jika valid, data dikirim ke endpoint `/api/tenant/sampling/orders/{id}/stage` dengan `targetStage = CAM_PROGRAMMING` beserta payload `stageInputs`.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Ekspansi Dialog Detail SPK (`focusCam = true`)** | Membuat modal dialog terpisah khusus CAM | Operator membutuhkan referensi Deal Klien (gambar mockup, request buyer, ukuran POM) sambil mengetik program CAM dan rumus pola di satu layar. | Modal terpisah menutupi gambar mockup; operator terpaksa bolak-balik menutup modal untuk mencocokkan desain. |
| **Stage Inputs Payload di Endpoint Stage Advance** | Dua kali panggil API: Save Input lalu Advance Stage terpisah | Menjamin atomic transaction: tidak akan ada SPK yang sudah pindah ke tahap CAM tetapi input programnya gagal tersimpan karena jaringan putus di tengah. | SPK bisa berada di kolom Program CAM dalam kondisi data kosong (*ghost state*). |
| **Auto-scroll dengan Animated Scroll State** | Tab accordion kaku | Form terasa mengalir alami: menentukan alur -> meluncur ke pengisian program -> simpan & masuk CAM. | Pengguna bingung mencari posisi form yang baru terbuka bila dialog memiliki tinggi terbatas. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan State Mutasi Dini**:
   - *Kenapa bahaya*: Memanggil `AdvanceStage` ke server saat tombol *"Mulai CAM"* diklik sebelum operator mengisi form. Akibatnya status di database sudah berubah jadi CAM padahal operator belum tentu menyelesaikan pengisian.
   - *Solusi*: Tahan mutasi backend. Buka form secara lokal di antarmuka dialog terlebih dahulu; kirim perubahan tahap bersamaan dengan payload data saat tombol *"Simpan & Masuk Program CAM"* ditekan.

2. **Jebakan Target Stage Ambigu**:
   - *Kenapa bahaya*: Fungsi submit lembar CAM dipanggil untuk dua momen berbeda (saat masuk CAM dan saat maju dari CAM ke Rajut). Jika `targetStage` di-hardcode ke `MACHINE_KNITTING`, SPK akan melompati tahap CAM.
   - *Solusi*: Tentukan `targetStage` berdasarkan status pipeline kartu saat itu:
     ```kotlin
     val targetStage = if (target.pipelineStage == SamplingPipelineStage.CAM_PROGRAMMING) {
         SamplingPipelineStage.MACHINE_KNITTING
     } else {
         SamplingPipelineStage.CAM_PROGRAMMING
     }
     ```

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Kompilasi Multi-Target**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs :app:shared:jvmTest :core:jvmTest
   ```
   Pastikan seluruh target terkompilasi sukses dengan 0 error.
2. **Pengujian Manual di Browser (`http://localhost:3000/sampling-order`)**:
   - Cari kartu SPK di kolom **Penentuan Alur**.
   - Klik tombol **"Alur Siap -> Mulai CAM ->"** pada kartu.
   - Pastikan dialog terbuka dan langsung menampilkan section **"PROGRAM CAM — INPUT TIM SAMPLING"** (tab per bagian: Depan, Belakang, Lengan) dan **"CATATAN RUMUS POLA"**.
   - Isi kode program (mis. `BIAN-D`), instruksi feeder (mis. `1 RIB STRIPE 1 PLAY`), tenselity, dan catatan rumus pola.
   - Klik **"Simpan & Masuk Program CAM"**.
   - Pastikan dialog tertutup dan kartu SPK berpindah ke kolom **Program CAM**.
   - Klik kartu tersebut di kolom Program CAM: data program dan catatan rumus pola yang diisi tadi tetap tersimpan dan ditampilkan rapi.
