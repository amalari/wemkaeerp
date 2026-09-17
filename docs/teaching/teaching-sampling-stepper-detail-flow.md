# 🎓 Modul Pembelajaran: Transisi Status Detail Stepper Garmen & Golden Sample Lock

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), State-Driven UI, Claymorphism Design System, Production Tracking vs CRM Scope Boundary  
> **Prasyarat**: Kotlin Multiplatform, Compose Multiplatform dasar, Arsitektur DDD WeMade  
> **Referensi Task**: Implementasi Tampilan Kontekstual Detail Langkah Produksi Sampling (`DealDetailDialog`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata di Pabrik Garmen
Bayangkan sebuah konveksi garmen dan rajut. Tim Sales baru saja menyepakati spesifikasi sampel baju cardigan dengan buyer:
- Lebar Dada: 52 cm
- Panjang Baju: 68 cm
- Benang: Viscose Jaquard 3-Color
- Biaya Sampling: Rp 1.000.000

Sales menerbitkan SPK Sampling resmi (`#SPK-SMP-0013`). Operator programmer mesin CAM mulai menyusun file program rajut, operator benang mulai memasang cone di creel mesin rajut otomatis, dan mesin mulai berjalan.

Jika aplikasi CRM membiarkan Sales **masih bisa mengedit textfield Lebar Dada, mengubah foto, atau mengklik tombol ganti ukuran** saat mesin di pabrik sudah merajut, apa yang terjadi?
1. **Desinkronisasi Fatal**: Data di layar Sales berbeda dengan kartu kerja fisik di tangan operator mesin.
2. **Kekacauan Biaya**: Biaya sampling yang sudah disetujui mendadak berubah di tengah jalan.
3. **Dispute Buyer**: Sampel jadi dikirim dengan ukuran versi awal, tapi buyer memegang catatan bahwa Sales sudah mengubahnya di sistem.

### Analogi Sederhana
> **Analoginya seperti Pesanan Restoran**:
> - **Fase Draft (Step 1)**: Anda masih melihat buku menu dan menulis pesanan di kertas draft. Anda bebas menghapus atau mengganti menu.
> - **Fase SPK Terbit & Dapur Memasak (Step 2 - 6)**: Kertas pesanan sudah ditempel di papan dapur dan wajan sudah menyala. Anda tidak bisa seenaknya mencoret kertas pesanan tersebut. Yang Anda lihat di layar tunggu adalah **Status Pelacakan Dapur** (*Pesanan Masuk* $\rightarrow$ *Sedang Dimasak* $\rightarrow$ *Plating*). Jika ingin mengubah pesanan, mekanismenya adalah **Revisi / Pesan Ulang**, bukan mengubah catatan yang sedang dimasak.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika seorang junior developer harus mengimplementasikan fitur state-driven detail monitoring ini dari nol, berikut adalah urutan berpikir dan menulis (*Order of Operations*):

```text
1. Pure Domain Model (State Resolvers & Invariants)
       ↓
2. UI Gating Logic (isDraft vs isFormReadOnly)
       ↓
3. Contextual Active Step Component (Live Progress Banner)
       ↓
4. Immutable Reference View (Locked Spec Sheet)
       ↓
5. Defensive Auto-Save Suppression (Mencegah Network Noise)
```

1. **Langkah 1: Definisikan State Tracker di Pure Domain (`core`)**
   - Jangan buat UI dulu! Buka `SamplingOrder.kt` dan buat fungsi deterministik `resolveGarmentTimeline(): List<GarmentStepState>`.
   - Mengapa deterministik di domain? Agar status stepper (apakah sedang Rajut, QC In-Line, Finishing, atau Kirim) diturunkan dari data riil pabrik (`pipelineStage`, `milestones`, `finishingDeposits`, `qcInspections`), bukan state lokal UI yang gampang hilang saat di-refresh.

2. **Langkah 2: Tentukan Batas Boolean di UI State Holder (`DealDetailDialog.kt`)**
   - Hitung status kesiapan:
     ```kotlin
     val isDraft = order.status == SamplingStatus.DRAFT || order.pipelineStage == SamplingPipelineStage.NEW_INTAKE
     val isFormReadOnly = !isDraft || isHistoricRevision
     ```
   - `isDraft == true` $\rightarrow$ Tampilkan Formulir Input Aktif (Sales bebas isi spek).
   - `isDraft == false` $\rightarrow$ Tampilkan Live Production Status Monitor + Kunci Formulir Spek (*Golden Sample Reference*).

3. **Langkah 3: Bangun Komponen Status Operasional (`SamplingActiveStepCard`)**
   - Buat kartu kontekstual yang membaca `activeStep` saat ini.
   - Jika Step 3 (Rajut): Tampilkan status mesin CAM, rajut turun mesin, linking, dan spek benang.
   - Jika Step 4 (QC 1): Tampilkan status inspeksi in-line dan jahitan mentah.
   - Jika Step 5 (Finishing): Tampilkan progress setoran pcs cuci & steam uap.
   - Jika Step 7 (Kirim): Tampilkan nomor resi kurir dan tracking.

4. **Langkah 4: Kunci Form Input Lama (`readOnly = isFormReadOnly`)**
   - Alirkan flag `readOnly` ke `DesignMockupSlot`, `SamplingSizeChartTable`, `SamplingQuantityTable`, textfield fee, dan catatan.
   - Matikan tombol "+ Tambah Ukuran", tombol silang hapus baris, tombol pensil rename, dan tombol upload foto.

5. **Langkah 5: Beri Banner Integritas Data**
   - Tampilkan badge Neo-Brutalist: `🔒 Spesifikasi Fisik Terkunci (Golden Sample SPK #...)` agar pengguna tahu persis *mengapa* form tidak bisa diedit: "Hanya Baca • Acuan Produksi".

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Menentukan Mode Aktif vs Terkunci
```kotlin
// Garis batas mode: Draft (Step 1 - masih isi formulir) vs Produksi/Arsip (Step 2-8 - Spek Terkunci)
val isDraft = order.status == SamplingStatus.DRAFT || order.pipelineStage == SamplingPipelineStage.NEW_INTAKE
val isFormReadOnly = !isDraft || isHistoricRevision
val navigator = LocalAppNavigator.current
```
**Mengapa blok ini penting?**
- Jika kita hanya memeriksa `isHistoricRevision`, order yang revisi terakhirnya sudah terbit SPK akan tetap dianggap *draft* oleh form dan textfield-nya tetap bisa diedit.
- Dengan menyatukan `!isDraft || isHistoricRevision` menjadi `isFormReadOnly`, seluruh kontrol input terkunci otomatis begitu status beralih dari draft ke antrean produksi.

### Blok B: Penonaktifan Auto-Save pada Order Terkunci
```kotlin
LaunchedEffect(feeInput, notesInput, sizeMatrixInput) {
    if (!detailTouched || !isDraft) {
        if (!detailTouched) detailTouched = true
        return@LaunchedEffect
    }
    delay(800)
    val calculatedQty = calculateTotalSampleQuantity(sizeMatrixInput)
    val totalQty = if (calculatedQty > 0) calculatedQty else order.sampleQuantity
    onEvent(DealUiEvent.SaveSamplingOrder(...))
}
```
**Mengapa blok ini penting?**
- Tanpa pemeriksaan `!isDraft`, setiap kali dialog dibuka atau tab berganti, `LaunchedEffect` bisa memicu debounce autosave yang mengirimkan HTTP `PUT /sampling-orders` ke server.
- Selain boros bandwidth, mengirim update ke order yang sudah berstatus produksi bisa merusak integritas audit trail di backend.

### Blok C: Menampilkan Status Produksi Aktif Berdasarkan Kronologi Garmen
```kotlin
// ── Status Produksi Aktif & Banner Acuan Spek Terkunci (Saat SPK sudah rilis) ──
if (!isDraft) {
    Spacer(Modifier.height(ClaySpacing.Md))
    SamplingActiveStepCard(
        order = order,
        garmentTimeline = garmentTimeline,
        onNavigateToSampling = { navigator(AppNavScreen.SAMPLING_ORDER) }
    )
    Spacer(Modifier.height(ClaySpacing.Sm))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            IconLock(Modifier.size(13.dp), color = WeMadeColors.Primary)
            Text(
                text = "Spesifikasi Fisik Terkunci (Golden Sample SPK #${order.spkNumber.value})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
        }
        Text(
            text = "Hanya Baca • Acuan Produksi",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}
```
**Mengapa blok ini penting?**
- Menjawab kebingungan pengguna: *"Di step Rajut ini detailnya menampilkan apa?"*.
- Pengguna langsung melihat kartu operasional pabrik yang relevan dengan tahap saat ini, diikuti penegasan bahwa spesifikasi di bawahnya adalah acuan yang telah dibekukan.

### Blok D: Detail Langkah 3 (Rajut / Potong) di `SamplingActiveStepCard`
```kotlin
GarmentTrackingStep.KNITTING -> {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Text(
            text = "Produksi fisik sedang berlangsung di lantai sampling. Teknisi CAM menyiapkan program mesin & operator merakit potongan panel garmen.",
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Xxs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            val isCamDone = order.milestones.any { it.step == MilestoneStep.PROGRAM && it.isCompleted }
            val isKnitDone = order.milestones.any { it.step == MilestoneStep.RAJUT && it.isCompleted }
            val isLinkDone = order.milestones.any { it.step == MilestoneStep.LINKING && it.isCompleted }

            MilestoneMiniBadge(
                label = "1. Program CAM",
                status = if (isCamDone) "Selesai ✓" else "Pengerjaan",
                isDone = isCamDone,
                modifier = Modifier.weight(1f)
            )
            MilestoneMiniBadge(
                label = "2. Rajut Mesin",
                status = if (isKnitDone) "Selesai ✓" else if (order.pipelineStage >= SamplingPipelineStage.MACHINE_KNITTING) "Sedang Rajut" else "Antrean",
                isDone = isKnitDone,
                modifier = Modifier.weight(1f)
            )
            MilestoneMiniBadge(
                label = "3. Linking & Jahit",
                status = if (isLinkDone) "Selesai ✓" else if (order.pipelineStage >= SamplingPipelineStage.LINKING_ASSEMBLY) "Sedang Jahit" else "Menunggu Panel",
                isDone = isLinkDone,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(ClaySpacing.Xxs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            StatusDetailChip(label = "Benang", value = order.knitSpec.yarnType.ifBlank { "Cotton / Acrylic" })
            StatusDetailChip(label = "Gauge Mesin", value = order.machineProgram.effectiveTenselity.firstOrNull()?.parameter?.ifBlank { "12G CAM" } ?: "12G CAM")
            StatusDetailChip(label = "Rajutan", value = order.knitSpec.knitType.ifBlank { "Jaquard" })
        }
    }
}
```
**Mengapa blok ini penting?**
- Memberikan transparansi operasional secara *real-time* kepada tim Sales tanpa mengharuskan mereka membuka modul mesin pabrik secara manual. Sales dapat langsung menjawab pertanyaan buyer mengenai progress rajutan dengan akurat.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Pemisahan Mode Draft vs Monitoring Berbasis Domain State** | Form yang selalu editable dengan tombol "Simpan Perubahan" manual | Menjamin bahwa spesifikasi pabrik yang sudah dicetak ke SPK tidak bisa diubah sepihak tanpa mekanisme revisi formal | Operator merajut ukuran lama, sedangkan CRM mencatat ukuran baru (bencana produksi) |
| **Pewarisan Flag `isFormReadOnly` ke Komponen Input Bersama** | Membuat 2 set composable berbeda (mis. `SamplingFormView` vs `SamplingReadonlyView`) | Menggunakan kembali komponen yang sama (`DesignMockupSlot`, `SamplingSizeChartTable`) dengan parameter `readOnly = true`, menghemat baris kode dan menjaga konsistensi layout | Duplikasi kode UI hingga 2× lipat; jika ada perubahan tata letak, developer harus mengubah dua tempat terpisah |
| **Canvas Vector Icons (`ClayIcons.kt`)** | Unicode emoji (`🔒`, `🚚`, `🧵`, `✂️`) | Skiko / Wasm di browser tidak memiliki fallback font emoji OS dan merender kotak tahu (*tofu* / `▯`) | Tampilan UI rusak total dan terlihat tidak profesional di browser pengguna |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Lupa Menangani String Nullable di Komposable `Text`**
   - *Kenapa bahaya*: Di Kotlin Multiplatform, `Text(text = ...)` hanya menerima non-null `String`. Model domain seperti `GarmentStepState` memiliki `val subtitle: String? = null`. Memanggil `Text(text = activeStep.subtitle)` tanpa operator Elvis (`?:`) langsung menggagalkan kompilasi Wasm IR (`Argument type mismatch: actual type is 'String?', but 'String' was expected`).
   - *Solusi*: Selalu sediakan fallback teks default:
     ```kotlin
     Text(text = activeStep.subtitle ?: currentStep.displayName, ...)
     ```

2. **Jebakan 2: Membiarkan Rename Inline Aktif di Header Setelah SPK Terbit**
   - *Kenapa bahaya*: Mengizinkan tombol pensil mengedit nama desain setelah SPK terbit akan membuat nama di sistem berbeda dengan lembar SPK yang sudah ditempel di mesin rajut.
   - *Solusi*: Bungkus tombol edit pensil dengan `if (isDraft)`.

3. **Jebakan 3: Kebocoran Autosave Debounce**
   - *Kenapa bahaya*: Menjalankan `LaunchedEffect(fee, notes, sizeMatrix)` tanpa `if (!isDraft) return@LaunchedEffect` memicu pengiriman data berulang kali ke server meskipun form dalam mode baca.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Kompilasi Wasm Bersih**:
   ```bash
   ./gradlew :app:shared:compileKotlinWasmJs
   ```
   Pastikan tidak ada error tipe data atau kebocoran state.

2. **Verifikasi Visual di Browser**:
   - Buka `http://localhost:3000/crm-sales/deals` $\rightarrow$ klik kartu deal dengan status sampling yang sudah terbit SPK (misal "Test 3").
   - **Periksa Step 3**: Pastikan muncul kartu oranye/kuning Neo-Brutalist `Langkah 3 dari 8: Rajut / Potong` dengan status milestone Program CAM, Rajut Mesin, dan Linking.
   - **Periksa Golden Spec**: Pastikan muncul banner `Spesifikasi Fisik Terkunci (Golden Sample SPK #...)`.
   - **Periksa Form**: Pastikan tombol "+ Tambah Ukuran", tombol hapus 'X', tombol "Ganti Foto", dan kursor edit textfield dinonaktifkan secara rapi.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka `DealDetailDialog.kt` dan perhatikan bagaimana langkah 7 (`READY_TO_SHIP`) menangani nomor resi pengiriman. Tambahkan tombol interaktif "Salin Resi" menggunakan `LocalClipboard` ketika nomor resi kurir tersedia.
- [ ] **Tantangan 2**: Buat sebuah desain sampling baru (masih berstatus Draft / Step 1) dan pastikan form kembali terbuka 100% (bisa upload foto, bisa tambah baris ukuran POM, dan bisa mengubah sampling fee).
