# Modul Pembelajaran: Validasi Domain & Pop-up Konfirmasi Penerbitan SPK Sampling

**Target Audiens**: Junior Developer  
**Topik**: Domain-Driven Validation Rules, Two-Phase Confirmation Dialogs, State Gating, dan Claymorphism Design System

---

## 1. Start Dari Mana? (Order of Operations)

Ketika ada kebutuhan alur operasional garmen di mana data dari satu modul (CRM Deals) akan diteruskan ke antrean kerja modul pabrik lain (Divisi Sampling):

1. **Definisikan Kontrak Domain (`core/`)**:
   - Jangan membuat validasi hanya di UI Composable (`if (qty < 1)` di dalam onClick).
   - Tuliskan aturan bisnis murni pada domain entity (`SamplingOrder`):
     - `missingSpkRequirements()`: Syarat fatal yang menghalangi terbitnya SPK:
       1. Nama desain wajib diisi.
       2. Foto mockup Tampak Depan wajib diunggah.
       3. Size chart wajib memiliki minimal 1 ukuran dengan seluruh baris spesifikasi POM terisi lengkap (misal: kolom ALL SIZE terisi seluruhnya).
       4. Jumlah sampel minimal 1 pcs (diisi pada baris Qty untuk kolom ukuran yang aktif).
     - `spkValidationWarnings()`: Peringatan kelengkapan opsional (misal: foto mockup Tampak Belakang belum ada).
     - `isReadyForSpk`: Properti boolean yang menjadi gerbang utama.
2. **Unit Test di Domain Layer (`core/test`)**:
   - Tulis unit test untuk memvalidasi kasus data kosong, data valid, kuantitas 0, ketiadaan foto Tampak Depan, serta ketiadaan baris POM yang lengkap.
3. **Presentasi Dialog Konfirmasi (Two-Phase Action)**:
   - Tombol "Buat SPK Sampling" tidak boleh langsung memicu mutasi backend/API.
   - Buka dialog konfirmasi modal (`ConfirmSpkDialog`) yang merangkum data pesanan, menampilkan callout edukasi alur kerja pabrik, dan menampilkan status validasi secara transparan.
4. **State Gating pada Tombol Terbitkan**:
   - Tombol "Ya, Terbitkan SPK" di-disable jika terdapat error fatal dari `missingSpkRequirements()` (nama kosong, foto depan belum ada, size chart belum lengkap, atau qty < 1).
   - Hanya ketika pengguna menekan konfirmasi dan data valid, `DealUiEvent.CreateSamplingSpk` dikirim ke ViewModel.

---

## 2. Bedah Kode Blok per Blok

### A. Validasi Domain di `SamplingOrder.kt`
```kotlin
fun missingSpkRequirements(sizeMatrix: List<SizeChartRow> = this.sizeMatrix): List<String> = buildList {
    if (styleName.isBlank()) {
        add("Nama desain tidak boleh kosong.")
    }
    if (mockupFrontKey.isNullOrBlank()) {
        add("Foto mockup Tampak Depan wajib diunggah.")
    }
    if (!hasAtLeastOneCompleteMeasurementColumn(sizeMatrix)) {
        add("Size chart wajib memiliki minimal 1 ukuran dengan seluruh baris spesifikasi (POM) terisi lengkap (misal: ALL SIZE terisi seluruhnya).")
    }
    val totalQty = calculateTotalSampleQuantity(sizeMatrix, sampleQuantity)
    if (totalQty < 1) {
        add("Jumlah sampel minimal 1 pcs. Silakan tentukan alokasi kuantitas pada kolom ukuran yang aktif di tabel Size Chart.")
    }
}

fun spkValidationWarnings(): List<String> = buildList {
    if (mockupBackKey.isNullOrBlank()) {
        add("Foto mockup Tampak Belakang belum diunggah (opsional).")
    }
}

val isReadyForSpk: Boolean get() = missingSpkRequirements().isEmpty()
```
**Mental Model**:
- **Pola Fisik Garmen Memerlukan Data Lengkap**: Operator mesin rajut/potong tidak bisa memotong atau merajut pakaian jika hanya satu bagian (misal Panjang Baju) yang diisi sementara Lebar Dada dibiarkan kosong. Minimal satu ukuran (misalnya ALL SIZE) harus terisi seluruh parameter fisiknya agar mesin dapat diprogram dengan benar.

---

### B. Dialog Konfirmasi Dua Tahap (`ConfirmSpkDialog`)
```kotlin
@Composable
private fun ConfirmSpkDialog(
    order: SamplingOrder,
    sizeMatrix: List<SizeChartRow>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val totalQty = calculateTotalSampleQuantity(sizeMatrix, order.sampleQuantity)
    val missingReqs = order.missingSpkRequirements(sizeMatrix)
    val warnings = order.spkValidationWarnings()

    // ... Dialog UI rendering with ClayCard ...
}
```
**Mental Model**:
- Mengoper `order.copy(styleName = styleNameInput, notes = notesInput, ...)` dan `sizeMatrixInput` ke dalam dialog memastikan dialog selalu mencerminkan data terbaru yang sedang diedit pengguna di layar, meskipun autosave debounce 800ms belum tuntas dikirim ke backend.

### C. Two-Step Inline Pre-Check Sebelum Pop-up Muncul
```kotlin
val totalQty = calculateTotalSampleQuantity(sizeMatrixInput, order.sampleQuantity)
val hasMockup = !frontRef.isNullOrBlank() || !order.mockupFrontKey.isNullOrBlank()
val hasName = styleNameInput.isNotBlank()
val hasValidQty = totalQty >= 1

if (!hasName || !hasMockup || !hasValidQty) {
    showValidationErrors = true
    if (!expanded) {
        onToggleExpanded() // Buka kartu accordion otomatis agar user melihat letak salahnya
    }
} else {
    showValidationErrors = false
    isConfirmSpkDialogOpen = true
}
```
**Mental Model**:
- **Jangan Membuka Dialog Jika Form Belum Layak**: Menampilkan pop-up konfirmasi saat data belum lengkap hanya membuat frustrasi karena tombol di dalam pop-up akan disabled dan user harus menutup modal lalu mencari apa yang salah. Dengan menampilkan error inline tepat di bawah field yang bermasalah (dan meng-expand kartu secara otomatis), user langsung tahu field mana yang perlu diperbaiki.

---

## 3. Technology & Approach ("The Why")

1. **Kenapa Validasi Diletakkan di Domain Entity, Bukan Hanya di ViewModel/UI?**
   Jika nanti ada endpoint API REST Ktor atau script bulk migration yang ingin menerbitkan SPK, aturan validasi yang sama (`missingSpkRequirements`) bisa langsung dipanggil tanpa menduplikasi logika.
2. **Kenapa Menghindari Literal Emoji pada Indikator Status?**
   Menggunakan `IconCheckCircle`, `IconWarning`, dan `IconBan` berbasis Canvas Skia memastikan tidak akan terjadi rendering kotak kosong (*tofu*) pada Compose Wasm di peramban web.

---

## 4. Jebakan Pemula (Common Pitfalls)

1. **Popup Terbuka Padahal Data Kosong**:
   Membuka modal konfirmasi hanya untuk memberi tahu user bahwa datanya salah adalah UX yang buruk. Gunakan inline field errors terlebih dahulu.
2. **Kartu Terlipat Saat Validasi Error**:
   Jika kartu dalam kondisi collapsed, pesan error di dalam form tidak terlihat. Pastikan memanggil `onToggleExpanded()` saat error terdeteksi.

---

## 5. Verifikasi & Pengujian

- **Unit Test Domain**: Dijalankan via `./gradlew :core:jvmTest --tests "com.eventverse.app.domain.sampling.SamplingApprovalReadinessTest"` (lulus 100%).
- **UI Compilation**: Dijalankan via `./gradlew :app:shared:compileKotlinJvm` (lulus tanpa error).
