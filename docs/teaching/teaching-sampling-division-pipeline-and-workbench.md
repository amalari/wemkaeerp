# 🎓 Modul Pembelajaran: Pipeline Multi-Divisi Sampling, Finishing, QC, & Admin Control Tower

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Full-Stack End-to-End Architecture, Partial Depositing Workflow, Multi-Division Task Routing, Tolerance Verification Engine, Claymorphism Neo-Brutalism UI  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform, Exposed ORM / PostgreSQL, Ktor Server, MVI di Compose Multiplatform  
> **Referensi Task**: [planning-sampling-division-pipeline-and-workbench.md](file:///Volumes/amalari/Projects/wemade/docs/plannings/planning-sampling-division-pipeline-and-workbench.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata di Pabrik Rajut Garment
Pada pabrik rajut (*knitwear manufacturer*), proses pembuatan sample (*prototyping*) melibatkan 4 divisi yang berbeda dengan pola kerja unik:
1. **Divisi Sampling (R&D & Operator Rajut Mesin CAM)**: Mengolah spec benang, program file mesin rajut CAM (Bian-D, Bian-B, dll), Feeder 1-7, tabel **Tenselity** (11 parameter kerapatan tarikan rajut), dan dual size chart (ukuran rajut mentah saat baru turun mesin vs ukuran jadi buyer). **Lingkup kerja divisi ini berhenti saat kain rajut mentah turun dari mesin.**
2. **Divisi Finishing (Internal & Vendor Makloon)**: Merakit potongan panel rajut (linking), pasang kerah, obras, jahit kancing, cuci, dan setrika uap. Di lantai produksi, perakitan sering kali **disetor bertahap (partial depositing)** setiap hari (misal target 50 pcs, hari ini selesai 20 pcs dengan berat timbangan 4.5 Kg, besok 30 pcs). Jika dialihkan ke vendor makloon luar, diperlukan monitoring tanggal kirim, tenggat kembali, dan follow-up WhatsApp.
3. **Divisi Quality Control (QC)**: Bertanggung jawab memverifikasi fisik sampel jadi terhadap *Size Chart Ukuran Jadi Buyer*. Di dunia rajut, kain bersifat elastis, sehingga diterapkan batas toleransi fisik maksimal $\pm 1.0\text{ cm}$ pada setiap Point of Measurement (POM). Selain itu ada audit visual cacat rajut (jarum patah, belang benang, drop stitch, jahitan loncat).
4. **Admin Produksi / Merchandiser (Control Tower)**: Mengawasi seluruh siklus dari satu layar, menentukan jalur finishing (*Internal* vs *Vendor Makloon*), memantau sisa setoran, mengirim pesan follow-up WA ke vendor, dan mengunci ACC Produksi (Golden Sample) atau mengajukan revisi.

Jika sistem dirancang seadanya (misal sekadar tabel CRUD status string `"pending" -> "done"` tanpa tracking setoran, tanpa toleransi POM, dan UI terfragmentasi ke puluhan menu berbeda), maka:
- Tim finishing bingung sisa barang yang belum disetor berapa pcs dan timbangan kg-nya berapa.
- Vendor luar sering telat karena tidak ada sistem pengingat WhatsApp terintegrasi.
- QC meloloskan sampel yang melar melebihi toleransi buyer karena tidak ada kalkulasi otomatis deviasi POM.
- Admin produksi kehilangan visibilitas kapan sampel siap dikirim ke buyer.

---

## 🧭 2. "Start dari Mana?" — Alur Urutan Penulisan (Order of Operations)

Jika kamu diminta membangun fitur multi-divisi end-to-end seperti ini dari layar kosong, **jangan pernah langsung membuat UI atau endpoint controller**. Ikuti urutan 5 Pilar Full-Stack DDD berikut:

```
Step 1 (Domain Core) ➔ Step 2 (Database & Flyway) ➔ Step 3 (Server Persistence & Ktor Routes) ➔ Step 4 (Client Data Source & MVI ViewModel) ➔ Step 5 (Compose UI Design System)
```

1. **Langkah 1: Pure Domain Layer (`core/`)**
   - Buat Value Objects & Enums: `SamplingPipelineStage` (7 tahapan alur), `FinishingPath` (`INTERNAL`, `MAKLOON_VENDOR`), `VendorFollowUpStatus`, `TenselityEntry`, `FinishingDeposit`, `QcInspectionReport`, `QcPomMeasurement`.
   - Perluas Entity `SamplingOrder` dengan metode bisnis murni: `advancePipelineStage()`, `addFinishingDeposit()`, `completeQcInspection()`, `assignMakloonVendor()`, `recordVendorReturn()`.
   - Tambahkan *business invariant*: akumulasi deposit $\ge$ target otomatis memajukan stage ke `FINISHING_QC`; hasil QC `PASSED` otomatis memajukan stage ke `IN_DELIVERY`.
   - Update `SamplingOrderCodec` untuk serialisasi JSON round-trip yang bebas framework.
   - Tulis unit test murni di `SamplingMultiDivisionWorkflowTest.kt` dan jalankan `./gradlew :core:jvmTest`.

2. **Langkah 2: Database Schema & Migrasi (`server/resources/db/migration/`)**
   - Buat Flyway migration (`V41__sampling_multidivision_workflow.sql`).
   - Tambahkan kolom pipeline stage, vendor makloon info, dan tenselity matrix ke `sampling_orders`.
   - Buat tabel `sampling_finishing_deposits` dan `sampling_qc_inspections` dengan foreign key dan Row-Level Security (RLS) `tenant_id`.

3. **Langkah 3: Server Infrastructure & Ktor Routes (`server/`)**
   - Petakan schema Flyway ke DSL Exposed di `SamplingTables.kt`.
   - Implementasikan query insert/update/select di `PostgresSamplingOrderRepository.kt`.
   - Buat endpoint REST di `SamplingRoutes.kt`:
     - `POST /api/tenant/sampling/orders/{id}/stage`
     - `POST /api/tenant/sampling/orders/{id}/finishing/deposits`
     - `POST /api/tenant/sampling/orders/{id}/finishing/vendor`
     - `POST /api/tenant/sampling/orders/{id}/finishing/vendor-receive`
     - `POST /api/tenant/sampling/orders/{id}/qc/inspect`
     - `POST /api/tenant/sampling/orders/{id}/revision`

4. **Langkah 4: Client Data Layer & ViewModel (`app/shared/`)**
   - Tambahkan fungsi API jaringan di `SamplingRemoteDataSource` dan `SamplingApiClient`.
   - Buat state & event di `SamplingUiState.kt` & `SamplingUiEvent.kt` (mengatur tab view, dialog setoran, QC, vendor, revisi).
   - Sambungkan event handler di `SamplingViewModel.kt` dengan `StateFlow`.

5. **Langkah 5: Presentation & Komponen UI Claymorphism (`app/shared/presentation/`)**
   - Bangun komponen independen (*dumb components*):
     - `TenselityTable.kt` (tabel 11 parameter rajut).
     - `VendorMakloonCard.kt` (integrasi tautan langsung WhatsApp `wa.me` + konfirmasi terima).
     - `FinishingSetoranDialog.kt` (form input pcs & berat timbangan Kg).
     - `QcInspectionDialog.kt` (audit toleransi POM fisik $\pm 1$ cm & checklist cacat).
     - `AssignVendorDialog.kt` & `RevisionNotesDialog.kt`.
     - `SamplingPipelineKanbanBoard.kt` (papan 7-stage visual).
     - `SamplingVendorMonitoringView.kt` (monitoring KPI vendor).
   - Rakit ke dalam workspace:
     - `SamplingWorkspaceScreen.kt` (untuk Admin Produksi / Merchandiser).
     - `FinishingOperatorWorkspaceScreen.kt` (di-route ke `BusinessModule.OPERATOR_EXEC`).
     - `QcInspectorWorkspaceScreen.kt` (di-route ke `BusinessModule.QUALITY_CONTROL`).

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Domain Invariant — Partial Depositing & Auto-Advance Stage
Pada `core/src/commonMain/kotlin/com/eventverse/app/domain/sampling/SamplingOrder.kt`:

```kotlin
fun addFinishingDeposit(deposit: FinishingDeposit): SamplingOrder {
    val updatedDeposits = finishingDeposits + deposit
    val newTotal = updatedDeposits.sumOf { it.qtyPcs }
    
    // Invariant: Jika setoran telah mencukupi jumlah target sampel,
    // alur kerja otomatis maju dari LINKING_ASSEMBLY ke FINISHING_QC
    val nextStage = if (newTotal >= sampleQuantity && pipelineStage == SamplingPipelineStage.LINKING_ASSEMBLY) {
        SamplingPipelineStage.FINISHING_QC
    } else {
        pipelineStage
    }

    return copy(
        finishingDeposits = updatedDeposits,
        pipelineStage = nextStage,
        updatedAt = Clock.System.now()
    )
}
```
**Mengapa blok ini ditulis begini?**
- Operator finishing di pabrik tidak bekerja dalam model batching statis (1 order = 1 kali setor). Mereka menyetor berapa pun yang selesai dirakit hari ini.
- Dengan menghitung `newTotal >= sampleQuantity`, sistem tidak memaksa operator atau admin mengubah status secara manual: begitu fisik barang genap disetor, sistem otomatis memindahkan tiket ke meja kerja Divisi QC.

---

### Blok B: Formula Toleransi POM Fisik QC
Pada `core/src/commonMain/kotlin/com/eventverse/app/domain/sampling/SamplingOrderValueObjects.kt`:

```kotlin
data class QcPomMeasurement(
    val pomName: String,
    val targetCm: Double,
    val actualCm: Double,
    val toleranceCm: Double = 1.0
) {
    val deviationCm: Double get() = kotlin.math.abs(actualCm - targetCm)
    val isWithinTolerance: Boolean get() = deviationCm <= toleranceCm
}
```
**Mengapa blok ini ditulis begini?**
- Di industri garmen rajut (*sweater/cardigan*), deviasi ukuran selalu ada karena tarikan benang. Buyer memberikan toleransi baku $\pm 1.0\text{ cm}$.
- Dengan membungkus rumus deviasi ke dalam Value Object murni, UI tidak perlu melakukan hitung-hitungan matematika rumit: UI cukup memanggil `pom.isWithinTolerance` untuk memutuskan apakah warna badge hijau (*Lolos*) atau merah (*Deviasi*).

---

### Blok C: Skema Database Flyway dengan RLS Multi-Tenancy
Pada `server/src/main/resources/db/migration/V41__sampling_multidivision_workflow.sql`:

```sql
CREATE TABLE IF NOT EXISTS sampling_finishing_deposits (
    id VARCHAR(64) PRIMARY KEY,
    sampling_order_id VARCHAR(64) NOT NULL REFERENCES sampling_orders(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    deposit_date DATE NOT NULL,
    qty_pcs INT NOT NULL,
    weight_kg NUMERIC(6, 2) NOT NULL DEFAULT 0.0,
    operator_name VARCHAR(128) NOT NULL DEFAULT '',
    notes TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

ALTER TABLE sampling_finishing_deposits ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_sampling_finishing_deposits ON sampling_finishing_deposits
    FOR ALL
    USING (tenant_id = CURRENT_SETTING('app.current_tenant_id', true));
```
**Mengapa blok ini ditulis begini?**
- Kebocoran data antar pabrik (tenant) dicegah pada level PostgreSQL engine melalui Row-Level Security (`app.current_tenant_id`). Sekalipun ada bug di aplikasi client, database menolak query dari tenant yang tidak sah.
- Kolom `weight_kg` mencatat berat timbangan hasil jadi garmen untuk mencocokkan yield benang dengan kalkulasi awal R&D.

---

### Blok D: Integrasi Direct WhatsApp Makloon Vendor
Pada `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/components/VendorMakloonCard.kt`:

```kotlin
ClayButton(
    text = "Chat WhatsApp",
    style = ClayButtonStyle.Success,
    onClick = {
        val cleanPhone = vendor.vendorPhone.replace(Regex("[^0-9]"), "").let {
            if (it.startsWith("0")) "62" + it.substring(1) else it
        }
        val targetDateStr = vendor.expectedReturnAt?.toString() ?: "segera"
        val text = "Halo ${vendor.vendorName}, mau konfirmasi progres SPK ${order.spkNumber.value} (${order.styleName}) apakah sudah selesai linking/finishing nya? Target kembali tgl $targetDateStr. Terima kasih."
        val url = "https://wa.me/$cleanPhone?text=${text.replace(" ", "%20")}"
        uriHandler.openUri(url)
    }
)
```
**Mengapa blok ini ditulis begini?**
- Menghilangkan friksi admin saat mem-follow-up mitra luar: admin tidak perlu mengetik ulang nomor telepon, mencari nomor SPK, atau menyusun kalimat konfirmasi. Cukup 1 klik, WhatsApp Web / aplikasi WhatsApp terbuka dengan pesan sopan dan terstruktur.

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls & How We Avoided Them)

| Jebakan Pemula | Bahaya / Dampak | Solusi yang Kita Terapkan |
|---|---|---|
| **Menyimpan status sebagai String biasa** (`"linking"`, `"qc"`) | Typo sedikit di frontend menyebabkan order hilang dari antrean antardivisi. | Menggunakan `enum class SamplingPipelineStage` dengan serialisasi JSON serializer yang teruji. |
| **Membungkus token Clay ke `RoundedCornerShape()`** | Error tipe kompilasi: `ClayShapes.Tile` sudah bertipe `RoundedCornerShape`, membungkusnya lagi menghasilkan error type mismatch. | Gunakan token langsung: `shape = ClayShapes.Tile` atau `.background(color, ClayShapes.Card)`. |
| **Memisahkan menu makloon vendor dan internal ke halaman berbeda** | Merchandiser harus berpindah-pindah menu hanya untuk memeriksa status sampel yang sama. | Disatukan dalam satu card kendali dengan badge toggle jalur finishing (`INTERNAL` vs `MAKLOON_VENDOR`). |
| **Menggunakan karakter Emoji di teks Compose Wasm** | Emoji unicode (`💬`, `📱`, `✓`) akan dirender kotak kosong / tofu (`▯`) di browser karena keterbatasan engine Skiko Wasm. | Wajib menggunakan icon vektor canvas dari `ClayIcons.kt` (`IconTruck`, `IconCheck`, `IconClose`, dll). |
| **Mengandalkan memori operator untuk akumulasi sisa** | Terjadi selisih jumlah baju saat dikirim ke buyer. | Sistem secara otomatis menghitung `remainingFinishingQty = (sampleQuantity - totalFinishedDepositedQty).coerceAtLeast(0)`. |

---

## 🧪 5. Verifikasi & Tantangan Mandiri (Hands-On Lab)

### Cara Memverifikasi Implementasi
1. **Unit Test Pure Domain**:
   ```bash
   ./gradlew :core:jvmTest
   ```
   *Ekspektasi*: Test suite `SamplingMultiDivisionWorkflowTest` berjalan 100% hijau, menguji akumulasi deposit, transisi otomatis ke QC, toleransi deviasi POM, dan transisi vendor.

2. **Kompilasi Modul Ktor Server**:
   ```bash
   ./gradlew :server:compileKotlin
   ```

3. **Kompilasi Compose Shared UI**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm
   ```

### 🎯 Tantangan Mandiri untuk Junior Developer
1. **Challenge 1 (Upload Foto Timbangan)**:
   Tambahkan integrasi `LocalFilePicker` ke dalam `FinishingSetoranDialog` sehingga operator finishing dapat melampirkan foto timbangan digital saat menyetor hasil garmen jadi.
2. **Challenge 2 (Barcode/QR Scanner)**:
   Buat tombol scanner kamera di `FinishingOperatorWorkspaceScreen` yang membaca barcode pada lembar fisik SPK dan otomatis membuka dialog setoran untuk SPK tersebut tanpa perlu mencari di daftar.
