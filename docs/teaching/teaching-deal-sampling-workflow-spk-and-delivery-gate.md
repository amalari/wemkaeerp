# 🎓 Modul Pembelajaran: Workflow Deal Sampling — "Buat SPK Sampling" & Delivery Status Gate

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Progressive UI Gating, State-Driven Visibility, Clean Domain Integration, KMP Compose UI  
> **Prasyarat**: Pemahaman dasar Compose Multiplatform, MVI di ViewModel, Domain State di KMP  
> **Referensi Task**: Refinement UX Siklus Sampling pada Deal CRM

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Sebelum pembaruan ini, antarmuka Deal CRM pada Tab Sampling langsung menampilkan tombol **"Tandai ACC Desain"** dan **"Ajukan Revisi"** bersamaan dengan banner merah peringatan bertuliskan **"Belum bisa di-ACC — lengkapi dulu: ..."**.

Hal ini menimbulkan dua masalah nyata dalam operasional pabrik:
1. **Pola Pikir Bisnis Terbalik**: Sales/Admin baru saja menginput spesifikasi atau desain awal baju di CRM. Sampel fisiknya belum diprogram di mesin rajut, belum dirajut, belum di-linking, dan belum dikirim ke buyer. Menampilkan tombol persetujuan buyer ("Tandai ACC") di saat barang belum dikerjakan adalah *logical flaw* dari sudut pandang alur operasional garmen.
2. **Peringatan yang Memblokir & Berisik**: Tulisan merah "Belum bisa di-ACC — lengkapi dulu" terasa mengintimidasi dan mengganggu tim sales yang baru mulai mendata kebutuhan buyer. Padahal, sales hanya perlu mendata dan menerbitkan SPK ke Divisi Sampling.

### Analogi Sederhana
Bayangkan memesan makanan di restoran.
- Pelayan mencatat pesanan Anda (Draft Desain di Deal).
- Sebelum makanan dimasak oleh chef di dapur (Divisi Sampling), pelayan **tidak seharusnya** bertanya: *"Bagaimana makanannya, enak atau mau kami revisi resepnya?"* sambil menunjukkan peringatan merah: *"Belum bisa dinilai — makanan belum diantar!"*.
- Tombol evaluasi rasa ("ACC / Revisi") baru masuk akal diberikan ketika makanan sudah selesai dimasak dan disajikan di atas meja tamu (**Status Pengiriman / Terkirim**).
- Yang seharusnya dilakukan saat pesanan dicatat adalah menekan tombol **"Kirim Tiket Pesanan ke Dapur"** (**"Buat SPK Sampling"**).

### Hasil Akhir yang Diharapkan
1. **Tidak ada lagi banner bising**: Tulisan "Belum bisa di-ACC — lengkapi dulu: ..." dihapus dari tampilan Deal.
2. **Tombol "Buat SPK Sampling"**: Ditambahkan pada kartu desain baru (`DRAFT` / `NEW_INTAKE`). Menekan tombol ini akan menerbitkan lembar kerja resmi ke Divisi Sampling dan memindahkan status ke `IN_PROGRESS` (tahap `CAM_PROGRAMMING`).
3. **Penyembunyian Tombol ACC & Revisi**: Tombol "Tandai ACC Desain" dan "Ajukan Revisi" disembunyikan sampai sampel benar-benar berada pada **status pengiriman** (`IN_DELIVERY`, ada resi kurir, atau milestone kirim selesai).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda diminta menerapkan progressive gating semacam ini dari awal, ikuti urutan berikut:

1. **Langkah 1: Domain Property (`core/`)**
   - Definisikan invariant domain: *Kapan sebuah pesanan sampling dianggap berada di tahap pengiriman?*
   - Buat getter `val isInDelivery: Boolean` pada entity `SamplingOrder`.
   - Perbarui behavior method `advancePipelineStage` agar saat berpindah dari `NEW_INTAKE` ke stage berikutnya, status otomatis bertransisi dari `DRAFT` menjadi `IN_PROGRESS`.
2. **Langkah 2: Event Modeling (`app/shared/deal/DealUiState.kt`)**
   - Tambahkan UI event yang eksplisit: `CreateSamplingSpk(samplingId)` dan `AdvanceSamplingStage(samplingId, targetStage)`.
3. **Langkah 3: ViewModel Handler (`app/shared/deal/DealViewModel.kt`)**
   - Sambungkan event ke client repository / data source (`SamplingRemoteDataSource.advanceStage`).
   - Perbarui state Flow secara reaktif dengan item yang terupdate tanpa merusak urutan kode desain.
4. **Langkah 4: Presentation Refactoring (`app/shared/presentation/deal/components/DealDetailDialog.kt`)**
   - Hapus komponen checklist `ApprovalRequirementsCallout`.
   - Gunakan percabangan kondisi state-driven di slot aksi kartu:
     - Jika `isAccApproved` ──► Tombol "Terbitkan Invoice Sampling".
     - Jika `isInDelivery` ──► Tombol "Tandai ACC Desain" dan "Ajukan Revisi".
     - Jika belum `isInDelivery` ──► Tampilkan tombol **"Buat SPK Sampling"** (jika masih draft) atau Tag Status SPK & tombol navigasi/kirim.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Domain Predicate di `SamplingOrder.kt`
```kotlin
data class SamplingOrder(
    ...
) {
    val isAccApproved: Boolean get() = status == SamplingStatus.ACC_APPROVED
    val isArchived: Boolean get() = archivedAt != null

    // Helper penentu apakah sampel sudah berada dalam status pengiriman ke buyer
    val isInDelivery: Boolean get() = pipelineStage == SamplingPipelineStage.IN_DELIVERY ||
        pipelineStage == SamplingPipelineStage.ACC_APPROVED ||
        isAccApproved ||
        !courierTracking.isNullOrBlank() ||
        milestones.any { it.step == MilestoneStep.KIRIM && it.isCompleted }
```

**Mengapa ditulis begini?**
- Logika penentu status pengiriman tidak boleh tersebar acak di Composable. Dengan menjadikannya *computed property* di entity `SamplingOrder`, kita memiliki single source of truth yang bisa diuji unit test domain tanpa dependensi UI.

### Blok B: Penyesuaian `advancePipelineStage` di `SamplingOrder.kt`
```kotlin
fun advancePipelineStage(target: SamplingPipelineStage, updatedAt: Instant): SamplingOrder =
    copy(
        pipelineStage = target,
        status = if (status == SamplingStatus.DRAFT && target != SamplingPipelineStage.NEW_INTAKE) {
            SamplingStatus.IN_PROGRESS
        } else status,
        updatedAt = updatedAt
    )
```

**Mengapa ditulis begini?**
- Ketika SPK dibuat dan masuk antrean pemrograman CAM (`CAM_PROGRAMMING`), dokumen ini bukan lagi sebuah `DRAFT`. Secara bisnis, ia resmi berstatus `IN_PROGRESS`.

### Blok C: State-Driven Action Buttons di `DealDetailDialog.kt`
```kotlin
// ── Aksi: Terbitkan Invoice (jika ACC), ACC & Revisi (jika status pengiriman), atau Buat SPK Sampling ──
if (!isHistoricRevision && order.status != SamplingStatus.CANCELLED) {
    val navigator = LocalAppNavigator.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (order.isAccApproved) {
            ClayButton(
                text = "Terbitkan Invoice Sampling",
                onClick = { /* Prefill invoice */ },
                style = ClayButtonStyle.Accent,
                fontSize = 12.sp
            )
        } else if (order.isInDelivery) {
            // HANYA MUNCUL DI STATUS PENGIRIMAN
            ClayButton(
                text = "Tandai ACC Desain ${designCode.removePrefix("DSG-").toIntOrNull() ?: ""}",
                onClick = {
                    onEvent(DealUiEvent.ToggleSampleAcc(order.id.value, isApproved = true, notes = ""))
                },
                style = ClayButtonStyle.Success,
                fontSize = 12.sp
            )
            ClayButton(
                text = "Ajukan Revisi",
                onClick = { isRevisionDialogOpen = true },
                style = ClayButtonStyle.Accent,
                fontSize = 12.sp
            )
        } else {
            // BELUM DI STATUS PENGIRIMAN: Sembunyikan ACC & Revisi, tampilkan Buat SPK Sampling
            if (order.pipelineStage == SamplingPipelineStage.NEW_INTAKE || order.status == SamplingStatus.DRAFT) {
                ClayButton(
                    text = "Buat SPK Sampling",
                    onClick = {
                        onEvent(DealUiEvent.CreateSamplingSpk(order.id.value))
                    },
                    style = ClayButtonStyle.Primary,
                    leading = { IconPlus(Modifier.size(13.dp), color = WeMadeColors.Surface) },
                    fontSize = 12.sp
                )
            } else {
                ClayTag(
                    text = "SPK #${order.spkNumber.value} • ${order.pipelineStage.displayName}",
                    tint = WeMadeColors.Primary
                )
                ClayButton(
                    text = "Lihat SPK",
                    onClick = { navigator(AppNavScreen.SAMPLING_ORDER) },
                    style = ClayButtonStyle.Secondary,
                    fontSize = 11.sp
                )
                ClayButton(
                    text = "Kirim ke Buyer",
                    onClick = {
                        onEvent(DealUiEvent.AdvanceSamplingStage(order.id.value, SamplingPipelineStage.IN_DELIVERY))
                    },
                    style = ClayButtonStyle.Accent,
                    fontSize = 11.sp
                )
            }
        }
    }
}
```

**Mengapa ditulis begini?**
- Struktur `if / else if / else` menjamin bahwa antarmuka bersifat deterministik:
  1. Tahap ACC: Faktur sampling.
  2. Tahap Pengiriman: Buyer memberikan respon (ACC / Revisi).
  3. Tahap Persiapan: Sales menerbitkan SPK atau memantau SPK yang sedang diproduksi.

---

## 🛡️ 4. Jebakan Pemula (Common Pitfalls)

1. **Membiarkan Tombol Muncul tapi Men-disable-nya Tanpa Alasan Jelas**:
   - Jika tombol tidak relevan pada stage saat ini, lebih baik di-*hide* (sembunyikan) daripada di-*disable* dengan tooltip panjang yang membingungkan user.
2. **Hardcode Color Literal**:
   - Dilarang menulis `color = Color(0xFFFFFFFF)` atau `Color.White`. Selalu gunakan token semantik seperti `WeMadeColors.Surface` atau `WeMadeColors.OnSurface`.
3. **Mengabaikan Navigator CompositionLocal**:
   - `LocalAppNavigator.current` di project ini bertipe `(AppNavScreen) -> Unit`. Memanggil `.navigate()` akan menyebabkan compile error. Cukup panggil sebagai fungsi: `navigator(AppNavScreen.SAMPLING_ORDER)`.

---

## 🧪 5. Verifikasi & Pengujian

1. **Kompilasi Modul**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm
   ```
2. **Pengujian Domain Logic**:
   ```bash
   ./gradlew :core:jvmTest
   ```
3. **Verifikasi Visual di Browser**:
   - Buka Deal CRM -> Klik kartu Deal.
   - Pindah ke **Tab 1: Siklus Sampling**.
   - Perhatikan bahwa tulisan *"Belum bisa di-ACC — lengkapi dulu:"* sudah hilang sepenuhnya.
   - Pada kartu desain awal, tombol yang tampil adalah **"Buat SPK Sampling"**.
   - Tombol *"Tandai ACC Desain"* dan *"Ajukan Revisi"* tersembunyi sampai kartu berada dalam status pengiriman (`IN_DELIVERY`).
