# Teaching: Monitoring Alur Garment (Read-Only Process Stepper) pada Deals & Sampling

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Pemisahan Pipeline Komersial vs Eksekusi Pabrik, Affordance UI, State Machine Deterministik, Domain-Driven Design (DDD), Compose Multiplatform  
> **Prasyarat**: Pemahaman dasar Domain Entity di Kotlin, Design System Claymorphism WeMade, dan MVI Pattern.

---

## 1. Masalah: Mengapa Kanban Board Berbahaya untuk Monitoring Deals?

Ketika sales memantau pesanan garmen (Sampling $\rightarrow$ QC 1 $\rightarrow$ Finishing $\rightarrow$ QC 2 $\rightarrow$ Siap Kirim), godaan pertama developer biasanya adalah membuat **papan kolom Kanban** di dalam deals.

Namun, ini adalah **jebakan fatal UX dan integritas pabrik (*The False Affordance Trap*)**:

1. **False Affordance**: Kanban mengisyaratkan bahwa kartu bebas digeser (*drag-and-drop*). Padahal, sales **tidak boleh sembarangan memindahkan status ke "Lolos QC"** jika inspector QC belum mengetuk palu dan mengukur fisik baju.
2. **Kardinalitas 1 Deal vs Banyak Style**: 1 Deal buyer bisa berisi 3 model sekaligus (Cardigan, Crewneck, Polo). Masing-masing berjalan dengan kecepatan berbeda di pabrik. Kolom Kanban Deal akan tabrakan jika tiap style memiliki posisi berbeda.
3. **Pemisahan Wewenang (RBAC)**: Tim QC dan Finishing bekerja di modul lantai kerja masing-masing (`/qc`, `/finishing`). Mereka tidak boleh membuka Deal komersial yang memuat data omzet, margin, dan piutang.

**Solusinya**: Papan Kanban Deals tetap berada di level **Makro/Komersial** (Open, Sampling, PO, Produksi, Won), sedangkan di dalam Detail Deal, alur fisik garmen disajikan sebagai **Read-Only Horizontal Process Stepper** yang statusnya bergerak otomatis mengikuti setoran lantai kerja nyata.

---

## 2. Dua Fase Kronologis: Pra-Rilis vs Pasca-Rilis

Alur garmen memiliki ketergantungan urutan (*chronological gating*) yang ketat:

```
FASE 1: PRA-RILIS (Sales Intake)
┌────────────────────────────────────────────────────────┐
│ Step 1: Input Spek & Pola (AKTIF Diisi Sales)         │
│ • Upload Mockup Tampak Depan & Belakang                │
│ • Isi Tabel Size Chart / POM (Lebar Dada, Pjg Baju)   │
│ • Tentukan Alokasi Qty Sampel per ukuran               │
│ • Isi Biaya Sampling Fee & Catatan                     │
│ ──> Tombol [ + Buat & Rilis SPK Sampling ]             │
└──────────────────────────┬─────────────────────────────┘
                           │ SPK Terbit (#SPK-SMP-0013)
                           ▼
FASE 2: PASCA-RILIS (Lantai Pabrik Eksekusi - Read Only)
┌────────────────────────────────────────────────────────┐
│ (✔ Input) ── (✔ SPK Rilis) ── (● Rajut) ── (○ QC 1)   │
│ ── (○ Finishing) ── (○ QC 2) ── (○ Siap Kirim) ── (○ ACC)
│ • Form spesifikasi terkunci (Read-Only)                │
│ • Stepper menyala otomatis dari setoran QC & Finishing │
│ • Tombol aksi: [Lihat SPK] dan [Kirim ke Buyer]        │
└────────────────────────────────────────────────────────┘
```

---

## 3. Order of Operations (Start dari Mana?)

1. **Domain Layer Dulu (`core/`)**:
   * Definisikan `GarmentTrackingStep` enum (8 tahap berurutan) dan `GarmentStepState`.
   * Buat fungsi murni `SamplingOrder.resolveGarmentTimeline(): List<GarmentStepState>` yang menghitung status langkah dari akumulasi deposit finishing, inspeksi QC, dan status SPK.
2. **Design System Component (`presentation/designsystem/`)**:
   * Buat `ClayProcessStepper.kt` yang **buta domain** (menerima `ClayStepData(title, subtitle, status, badgeText)`).
   * Gunakan token Clay: `ClayShapes`, `ClayBorder.Thick`, `WeMadeColors.Outline`, dan `IconCheck`.
3. **Integrasi ke Dialog (`presentation/deal/components/`)**:
   * Pasang `ClayProcessStepper` di atas grid form `DesignCard` di dalam `DealDetailDialog.kt`.
   * Jaga agar seluruh form input intake (mockup, POM, alokasi qty, fee) **tetap 100% utuh**.
4. **Verifikasi & Test**:
   * Tulis unit test di `SamplingOrderTest.kt` untuk memastikan kalkulasi state di tahap Draft, Rajut, Finishing, Delivery, dan ACC.

---

## 4. Bedah Kode Blok per Blok

### Blok A: Model Status Deterministik di Domain Murni

```kotlin
// core/.../domain/sampling/SamplingOrderValueObjects.kt
enum class GarmentTrackingStep(val displayName: String, val order: Int) {
    INPUT_SPEK("Input Spek & Pola", 1),
    SPK_RELEASED("Rilis SPK", 2),
    KNITTING("Rajut / Potong", 3),
    QC_IN_LINE("QC 1 (In-Line)", 4),
    FINISHING("Finishing & Steam", 5),
    QC_FINAL("QC 2 (Final)", 6),
    READY_TO_SHIP("Siap Kirim", 7),
    ACC_APPROVED("ACC Buyer", 8);
}

data class GarmentStepState(
    val step: GarmentTrackingStep,
    val isCompleted: Boolean,
    val isActive: Boolean,
    val subtitle: String? = null,
    val badgeText: String? = null
)
```

**Mental Model**:
* Setiap tahap memiliki `order: Int` yang mengikat urutan fisiknya.
* `GarmentStepState` adalah status murni (immutable) yang siap dikonsumsi oleh lapisan presentasi mana pun (Web, Mobile, Desktop).

---

### Blok B: State Resolver Deterministik pada `SamplingOrder`

```kotlin
// core/.../domain/sampling/SamplingOrder.kt
fun resolveGarmentTimeline(): List<GarmentStepState> {
    val isDraft = status == SamplingStatus.DRAFT || pipelineStage == SamplingPipelineStage.NEW_INTAKE
    val totalDeposited = finishingDeposits.sumOf { it.qtyPcs }
    val isFinishingTuntas = totalDeposited >= sampleQuantity && totalDeposited > 0
    val isDeliveredOrApproved = pipelineStage == SamplingPipelineStage.IN_DELIVERY ||
        pipelineStage == SamplingPipelineStage.ACC_APPROVED

    return listOf(
        // 1. Input Spek & Pola (Pra-Rilis)
        GarmentStepState(
            step = GarmentTrackingStep.INPUT_SPEK,
            isCompleted = !isDraft,
            isActive = isDraft,
            subtitle = if (isDraft) "Sedang Diisi Sales" else "Spek & Pola Lengkap",
            badgeText = if (isDraft) "Draft" else null
        ),
        // ... step 2 s/d 8
    )
}
```

**Mental Model**:
* Tidak ada logika tebak-tebakan di UI. Fungsi ini menghitung fakta nyata:
  * Apakah finishing sudah tuntas? Jika `totalDeposited >= sampleQuantity`, maka tahap Finishing selesai dan tahap QC 2 aktif!
  * Apakah barang sudah di jalan? Jika `courierTracking` terisi, maka Siap Kirim selesai dan sistem menunggu ACC buyer!

---

### Blok C: Komponen Presentasi Netral Domain (`ClayProcessStepper`)

```kotlin
// presentation/designsystem/ClayProcessStepper.kt
@Composable
fun ClayProcessStepper(
    steps: List<ClayStepData>,
    modifier: Modifier = Modifier,
    activeColor: Color = WeMadeColors.Primary,
    completedColor: Color = WeMadeColors.Success,
    nodeSize: Dp = 28.dp
) {
    // Render garis penghubung + lingkaran node + icon canvas + teks subjudul
}
```

**Mental Model**:
* Sesuai **Kontrak 6 Design System WeMade**: Komponen bersama di `designsystem/` tidak boleh mengimpor kelas domain.
* Komponen menerima `ClayStepData` dan fokus pada rendering Claymorphism yang rapi: garis 3dp, node dengan outline solid `WeMadeColors.Outline`, dan canvas vector icon `IconCheck`.

---

## 5. Jebakan Pemula (Common Pitfalls)

1. **Menghapus Form Intake saat Memasang Stepper**:
   * *Kesalahan*: Mengira tampilan stepper menggantikan form spesifikasi ukuran.
   * *Akibat*: Sales kehilangan tempat mengunggah foto pola dan mengisi size chart!
   * *Pencegahan*: Stepper diletakkan di **header/atas**, form di bawahnya tetap utuh 100%.
2. **Mengizinkan Drag-and-Drop pada Alur Kualitas**:
   * *Kesalahan*: Memakai Kanban board untuk status QC.
   * *Akibat*: Sales melompati tahap inspeksi secara sepihak untuk mempercepat invoice.
   * *Pencegahan*: Buat tampilan stepper berstatus **Read-Only**.
3. **Hardcode Warna & Unicode Emoji**:
   * *Kesalahan*: Menulis `Color(0xFF...)` atau memakai emoji centang `✅` di teks.
   * *Akibat*: Pelanggaran token WeMade dan bug tofu `▯` pada Skiko Wasm.
   * *Pencegahan*: Selalu gunakan token `WeMadeColors` dan `IconCheck` canvas.

---

## 6. Verifikasi & Pengujian

Jalankan test suite otomatis untuk memvalidasi:

```bash
# 1. Test Domain Logic
./gradlew :core:jvmTest --tests "*SamplingOrderTest*"

# 2. Test Shared UI & Compose
./gradlew :app:shared:jvmTest --tests "*Sampling*" --tests "*Deal*"

# 3. Dry-run Kompilasi Wasm Browser
./gradlew :app:webApp:wasmJsBrowserDevelopmentWebpack --dry-run
```

Semua pengujian lolos 100% tanpa error kompilasi dan mempertahankan performa responsif WeMade ERP.
