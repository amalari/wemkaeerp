# 🎓 Modul Pembelajaran: Implementasi End-to-End Modul COSTING_HPP (Mesin Kalkulasi HPP yang Bisa Di-puzzle)

> **Level Target**: Junior to Mid Fullstack Kotlin Multiplatform Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Strategy Pattern, Commercial Freeze Point (`CostingSnapshot`), Exact Penny Arithmetic (`Money`, `Ratio`), Cost Buckets Waterfall, Consigned Material Accounting, Costing Drift Detection, PostgreSQL Row-Level Security (RLS), Cross-Module RBAC Guard, Compose Multiplatform Claymorphism Design System.  
> **Prasyarat**: Dasar Kotlin Multiplatform, SQL & Flyway Migrations, DDD & MVI Pattern di Compose Multiplatform.  
> **Referensi Task**: Modul `COSTING_HPP` — Mesin Kalkulasi HPP yang Bisa Di-puzzle (FOB, CMT, Brand D2C, Indirect Overhead)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata di Industri Manufaktur Garmen
Kalkulasi Harga Pokok Produksi (HPP atau COGS — *Cost of Goods Sold*) di pabrik garmen sering kali disalahpahami sebagai formula perkalian matematika sederhana di spreadsheet Excel:
$$\text{HPP} = \text{Bahan Baku} + \text{Ongkos Jahit} + \text{Keuntungan}$$

Kenyataan di lantai produksi manufaktur ribuan kali lebih kompleks dan dinamis:
1. **Model Bisnis yang Berubah-ubah (Bisa Di-puzzle)**:
   Pabrik yang sama bisa melayani klien dengan skema berbeda dalam satu minggu:
   - **FOB (Full Package)**: Pabrik membiayai seluruh pembelian kain, kancing, benang, memotong, menjahit, hingga kemasan. Semua biaya masuk HPP.
   - **CMT (*Cut, Make, Trim* / Makloon)**: Klien menitipkan rol kain mewah mereka ke pabrik. Pabrik **hanya** menjual jasa jahit/potong. Jika developer salah memasukkan harga kain titipan ke dalam tagihan, klien akan membatalkan kontrak ratusan juta karena ditagih atas kain miliknya sendiri!
   - **Brand D2C (*Direct to Consumer*)**: Pabrik memproduksi merek pakaiannya sendiri dan menjual via marketplace (Shopee, Tokopedia, TikTok Shop). HPP harus menghitung *Retail Markup* dan *Marketplace Fee* (potongan platform).
   - **Indirect Overhead**: Modul kalkulasi khusus untuk biaya pendukung (listrik generator, depresiasi mesin, sewa gudang) yang dibebankan ke setiap lini.
2. **Prinsip Komitmen Komersial (*Commercial Freeze Point*)**:
   Setelah negosiasi dengan buyer selesai dan lembar HPP disetujui (*APPROVED*), angka tersebut **tidak boleh berubah lagi**. Kenapa? Karena angka itulah yang tercetak di Surat Perjanjian Kontrak (SPK) dan invoice. Jika bulan depan harga kain katun dunia melonjak 20%, pabrik tidak bisa secara sepihak mengubah invoice yang sudah ditandatangani. Sistem harus mengunci snapshot kalkulasi tersebut secara permanen.
3. **Deteksi Pergeseran Biaya (*Costing Drift*)**:
   Kendati snapshot dikunci, manajer pabrik tetap harus tahu: *"Berapa kerugian kita hari ini akibat lonjakan harga kain dibanding kesepakatan awal?"* Sistem membutuhkan mesin pendeteksi *Drift* yang membandingkan snapshot lama dengan harga material terkini secara *real-time*.
4. **Jebakan Aritmetika Floating-Point & Overflow**:
   Di industri pakaian jadi dengan kuantitas 50.000 potong, selisih pembagian sen (misalnya biaya packing Rp 4.200 per order yang dibagi ke 9 potong baju) akan menciptakan atau melenyapkan uang dari ketiadaan jika dihitung memakai tipe data primitif `Double` atau `Float`.

### Analogi Sederhana: Dapur Katering Serbaguna
- **FOB (Full Package)** = Anda memesan nasi kotak komplit: katering yang belanja beras, ayam, bumbu, memasak, mengemas kotak, dan mengantarkannya. Anda membayar seluruh paket.
- **CMT (Makloon)** = Anda membawa daging wagyu sendiri ke dapur katering dan berkata: *"Tolong iris dan panggangkan daging saya ini, lalu masukkan ke kotak. Ini ongkos masaknya Rp 25.000 per porsi."* Dapur katering tidak berhak menagih harga daging wagyu kepada Anda, tetapi mereka wajib mencatat berapa gram sisa perca lemak daging yang terbuang agar tidak dituduh mencuri.
- **Brand D2C** = Katering menjual rice bowl beku langsung di aplikasi ojek online. Selain biaya masak, katering harus memperhitungkan komisi aplikasi 20% yang dipotong dari harga jual akhir aplikasi.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun fitur kalkulasi HPP skala enterprise dari nol, ikuti roadmap berdisiplin DDD berikut:

```
Step 0: Kontrak Komersial & Value Objects Murni (core/domain/costing/CostingValueObjects.kt)
   ↓
Step 1: Strategi Kalkulasi Polimorfik (core/domain/costing/strategies/)
   ↓
Step 2: Resolusi Parameter Multi-Sumber (core/domain/costing/ResolvedCostingParameters.kt)
   ↓
Step 3: Agregat Root, Snapshot Komersial & Invarian Domain (core/domain/costing/CostingSheet.kt)
   ↓
Step 4: Use Cases Operasional (core/domain/costing/usecases/)
   ↓
Step 5: Codec Jaringan & Serialisasi Portabel (core/shared/costing/)
   ↓
Step 6: Skema Database Flyway & Row-Level Security (server/resources/db/migration/V33..., server/tables/)
   ↓
Step 7: REST API Ktor & Cross-Module RBAC Guard (server/routes/CostingRoutes.kt)
   ↓
Step 8: Ktor HTTP Client Remote Data Source (app/shared/infrastructure/api/CostingApiClient.kt)
   ↓
Step 9: State Management MVI & ViewModel (app/shared/presentation/costing/CostingViewModel.kt)
   ↓
Step 10: Komponen UI Claymorphism Responsif (app/shared/presentation/costing/CostingWorkspaceScreen.kt)
```

### Mengapa Mulai dari Core Domain?
Jika kamu mulai dari tabel database atau rute REST API:
- Keputusan bisnis kamu akan terdistorsi oleh keterbatasan SQL/ORM.
- Logika kalkulasi HPP kamu akan tercecer di SQL Stored Procedures atau di dalam controller Ktor.
- Ketika aplikasi di-porting ke Web (Wasm), Desktop (JVM), atau Mobile (Android/iOS), kalkulasi HPP tidak bisa di-share karena bergantung pada server.

Dengan meletakkan seluruh matematika dan invarian di `core/`, logika bisnis 100% independen, murni, dan dapat diuji dalam hitungan milidetik tanpa database maupun server.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Mari kita bedah arsitektur kode lapis demi lapis.

### Blok A: Strategy Pattern untuk Puzzle HPP (`CostingFormulaStrategy.kt`)

Alih-alih membuat fungsi raksasa dengan puluhan `if/else` atau `when(preset)`, kita menggunakan **Strategy Pattern**:

```kotlin
interface CostingFormulaStrategy {
    val behavior: CostingBehavior
    fun calculate(input: CostingFormulaInput): Result<CostingFormulaOutput>
}
```

Implementasi FOB (`FullPackageCogsStrategy.kt`):
```kotlin
object FullPackageCogsStrategy : CostingFormulaStrategy {
    override val behavior = CostingBehavior.FULL_PACKAGE_COGS

    override fun calculate(input: CostingFormulaInput): Result<CostingFormulaOutput> = runCatching {
        val params = input.parameters
        val currency = input.currency
        val bomPreview = input.bomCostPreview

        // 1. MATERIAL BUCKETS: Diambil dari preview BOM
        val materialBuckets = mutableListOf<CostBucket>()
        for (line in bomPreview.lines) {
            val bucket = CostBucket.material(
                label = line.material.displayLabel,
                amountPerUnit = line.costPerGarment,
                ownership = line.ownership,
                behavior = behavior
            )
            materialBuckets.add(bucket.copy(sourceRefs = listOf(line.lineId)))
        }

        // 2. LABOR BUCKET: SAM menit langsung × tarif labor per menit
        val samDirect = input.samBreakdown.directMinutes()
        val laborCost = params.laborRatePerSamMinute * samDirect
        val laborBucket = CostBucket(
            kind = CostBucketKind.LABOR,
            label = "Tenaga Kerja Langsung (${samDirect.toDouble().let { "%.1f".format(it) }} menit SAM)",
            amountPerUnit = laborCost,
            isBillableToClient = true
        )

        // 3. SUBCONTRACT BUCKET: SAM menit subkon × tarif subkon
        val samSubcon = input.samBreakdown.subcontractMinutes()
        // ...
        // 4. OVERHEAD & PACKAGING BUCKETS
        // ...
    }
}
```

Implementasi CMT (`ServiceFeeOnlyStrategy.kt`):
```kotlin
object ServiceFeeOnlyStrategy : CostingFormulaStrategy {
    override val behavior = CostingBehavior.SERVICE_FEE_ONLY

    override fun calculate(input: CostingFormulaInput): Result<CostingFormulaOutput> = runCatching {
        // Pada CMT, kain titipan bernilai 0 di neraca pabrik dan TIDAK boleh ditagihkan:
        val buckets = mutableListOf<CostBucket>()
        for (line in input.bomCostPreview.lines) {
            buckets.add(
                CostBucket.material(
                    label = line.material.displayLabel,
                    amountPerUnit = Money.zero(input.currency),
                    ownership = line.ownership,
                    behavior = behavior // isBillableToClient = false
                )
            )
        }

        // Tagihan hanya berupa Jasa CMT (Service Fee)
        buckets.add(
            CostBucket(
                kind = CostBucketKind.LABOR,
                label = "Ongkos Jasa CMT (Cut-Make-Trim)",
                amountPerUnit = input.parameters.serviceFeePerUnit,
                isBillableToClient = true
            )
        )
        // ...
    }
}
```

**Mental Model**:
Setiap strategi adalah "kartu puzzle kalkulasi" yang independen. Menambah model bisnis baru (misalnya: *Licensing Royalty Model*) cukup dengan membuat satu file baru yang mengimplementasikan `CostingFormulaStrategy`, tanpa mengubah satu baris pun kode strategi FOB atau CMT yang sudah ada (*Open/Closed Principle*).

---

### Blok B: Invarian Konsinyasi di Domain Primitive (`CostBucket.kt`)

Aturan bisnis nomor satu pada makloon garmen dikunci langsung di level konstruktor Value Object:

```kotlin
data class CostBucket(
    val kind: CostBucketKind,
    val label: String,
    val amountPerUnit: Money,
    val ownership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
    val isBillableToClient: Boolean = true,
    val sourceRefs: List<String> = emptyList()
) {
    init {
        require(!(ownership == StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL && isBillableToClient)) {
            "Bahan konsinyasi '$label' tidak boleh ditagihkan ke klien (Kontrak 4)."
        }
        require(ownership != StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL || amountPerUnit.isZero) {
            "Bahan konsinyasi '$label' harus bernilai 0 di neraca pabrik (Kontrak 3)."
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
Jika seorang junior developer secara tidak sengaja mencoba membuat tagihan dari bahan konsinyasi (`ownership = CONSIGNED_CLIENT_MATERIAL, isBillableToClient = true`), sistem akan langsung melempar `IllegalArgumentException` saat inisialisasi obyek. Bug tidak akan pernah bisa lolos ke database maupun layar UI.

---

### Blok C: SAM Calculation Tanpa Ratio Overflow (`SamMinutesCalculator.kt`)

```kotlin
object SamMinutesCalculator {
    private const val MICROS_PER_MINUTE = 1_000_000L

    fun calculate(operations: List<LaborOperation>): SamMinutesBreakdown {
        var directMicros = 0L
        var subcontractMicros = 0L

        for (op in operations) {
            // Konversi pecahan ke mikro-detik integer murni
            val num = op.samMinutes.numerator
            val den = op.samMinutes.denominator
            val micros = (num * MICROS_PER_MINUTE + (den / 2)) / den

            if (op.isSubcontracted) {
                subcontractMicros += micros
            } else {
                directMicros += micros
            }
        }

        return SamMinutesBreakdown(directMicros, subcontractMicros)
    }
}
```
**Mengapa blok ini ditulis begini?**
Pecahan rasional `Ratio.percent(x)` memakai penyebut $10^6$. Jika kita menjumlahkannya menggunakan fungsi penjumlahan pecahan biasa ($\frac{a}{b} + \frac{c}{d} = \frac{ad + bc}{bd}$), pada operasi ke-4 penyebutnya akan mencapai $(10^6)^4 = 10^{24}$, melampaui batas maksimum integer 64-bit (`Long.MAX_VALUE` $\approx 9 \times 10^{18}$), dan menghasilkan nilai negatif (*arithmetic overflow*) secara diam-diam!
Solusi elegan: Kita normalisasi semua durasi ke unit mikro-detik ($10^{-6}$ menit) bertipe `Long`, lalu dijumlahkan secara linear.

---

### Blok D: Freeze Point Komersial & Deteksi Drift (`CostingSheet.kt`)

```kotlin
data class CostingSnapshot(
    val snapshotId: String,
    val approvedAt: Instant,
    val approvedByUserId: String,
    val result: CostingCalculationResult,
    val inputFingerprint: String
)

data class CostingSheet(
    val id: CostingSheetId,
    val status: CostingSheetStatus = CostingSheetStatus.DRAFT,
    val latestResult: CostingCalculationResult? = null,
    val approvedSnapshot: CostingSnapshot? = null,
    // ...
) {
    init {
        if (status == CostingSheetStatus.APPROVED) {
            requireNotNull(approvedSnapshot) { "Lembar yang APPROVED wajib punya approvedSnapshot" }
        }
    }

    fun driftAgainst(currentResult: CostingCalculationResult): CostingDrift {
        val snapshot = approvedSnapshot ?: return CostingDrift.None
        val snapshotBillable = snapshot.result.billablePerUnit
        val currentBillable = currentResult.billablePerUnit
        if (snapshotBillable.minorUnits == 0L) return CostingDrift.None

        val delta = currentBillable - snapshotBillable
        val deltaPercent = (delta.minorUnits.toDouble() / snapshotBillable.minorUnits.toDouble()) * 100.0

        return if (delta.isZero) {
            CostingDrift.None
        } else {
            CostingDrift.Detected(
                deltaPerUnit = delta,
                deltaPercent = deltaPercent,
                changedInputs = emptyList(),
                currentResult = currentResult
            )
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
1. Invarian `requireNotNull(approvedSnapshot)` memastikan bahwa status `APPROVED` tidak bisa dibuat tanpa snapshot.
2. `driftAgainst` membandingkan tagihan terkini dengan snapshot awal dan mengembalikan `CostingDrift.Detected` beserta `deltaPercent` dan selisih rupiah (`deltaPerUnit`).

---

### Blok E: Database Multi-Tenant RLS & Flyway (`V33__create_costing_schema.sql`)

```sql
CREATE TABLE IF NOT EXISTS costing_sheets (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    number VARCHAR(64) NOT NULL,
    tech_pack_id VARCHAR(64) NOT NULL,
    order_quantity BIGINT NOT NULL,
    behavior VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    pricing_as_of TIMESTAMPTZ NOT NULL,
    parameter_overrides JSONB NOT NULL DEFAULT '{}'::jsonb,
    calculation_result JSONB,
    approved_snapshot JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Isolasi data multi-tenant dengan Row-Level Security
ALTER TABLE costing_sheets ENABLE ROW LEVEL SECURITY;
CREATE POLICY costing_sheets_tenant_isolation ON costing_sheets
    USING (tenant_id = CURRENT_SETTING('app.current_tenant_id', true));
```
**Mengapa blok ini ditulis begini?**
RLS memastikan bahwa bahkan jika ada bug di query backend yang lupa menuliskan `WHERE tenant_id = ?`, database PostgreSQL sendiri yang akan mencegat dan memfilter baris data. Tenant A tidak akan pernah bisa mengintip HPP milik Tenant B.

---

### Blok F: Server Routing & Strict RBAC Guard (`CostingRoutes.kt`)

```kotlin
post("/sheets/{id}/approve") {
    val tenantSlug = call.parameters["tenantSlug"] ?: return@post call.respond(HttpStatusCode.BadRequest)
    val sheetId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)

    // Wewenang khusus APPROVE_COSTING wajib dimiliki
    val principal = call.principal<UserSessionPrincipal>()
    val hasApprovePermission = principal?.permissions?.contains(Permission.APPROVE_COSTING) == true ||
            principal?.role == Role.TENANT_ADMIN ||
            principal?.role == Role.SUPERADMIN

    if (!hasApprovePermission) {
        call.respond(HttpStatusCode.Forbidden, "Anda tidak memiliki wewenang APPROVE_COSTING")
        return@post
    }

    val updated = approveUseCase(tenantId, CostingSheetId(sheetId), principal?.userId ?: "system").getOrThrow()
    call.respond(CostingSheetCodec.encode(updated))
}
```

---

### Blok G: Claymorphism Design System di Presentation Layer (`CostingWorkspaceScreen.kt`)

Bahasa visual WeMade ERP mematuhi kontrak **Claymorphism + Neo-Brutalism**:
- Sudut membulat besar (`ClayShapes.CardMedium` $\approx 16$dp)
- Outline tebal 3dp (`ClayBorder.Medium`)
- Hard shadow tanpa blur (`Modifier.claySurface(...)` atau `ClayCard`)
- Font Fredoka (Heading) dan Nunito (Body)
- Zero literal `Color(0xFF...)` di luar tema: semua warna wajib merujuk ke token semantik `WeMadeColors.*`.

Waterfall Cost Buckets di UI:
```kotlin
// Material: ClaySurface lembut dengan outline 3dp
ClayCard(
    modifier = Modifier.fillMaxWidth(),
    outlineColor = WeMadeColors.TextSecondary.copy(alpha = 0.2f),
    color = WeMadeColors.Surface
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Komposisi Biaya Pokok (COGS Waterfall)",
            fontFamily = Fredoka,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Render waterfall per bucket
        result.buckets.forEach { bucket ->
            CostBucketRow(bucket = bucket, showMargin = uiState.showMargin)
        }
    }
}
```

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi / Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Strategy Pattern** (`CostingFormulaStrategy`) | `when(preset) { ... }` raksasa di satu file | Setiap model bisnis (FOB, CMT, Retail) terisolasi. Menambah model baru tidak menyentuh kode lama. | File menjadi *God Class* 3.000 baris. Perubahan kecil di CMT bisa merusak formula FOB tanpa sengaja. |
| **Commercial Freeze Point** (`CostingSnapshot`) | Kalkulasi ulang on-the-fly setiap kali lembar HPP dibuka | Menjamin invoice dan komitmen harga komersial tidak pernah berubah meskipun harga bahan naik. | Jika harga benang naik minggu depan, invoice yang sudah dicetak bulan lalu akan ikut berubah nilainya (kekacauan akuntansi). |
| **Integer Minor Units** (`Money(minorUnits, IDR)`) | `Double` atau `Float` biasa | Nol kehilangan presisi desimal (*exact penny arithmetic*). | `0.1 + 0.2 = 0.30000000000000004`. Akumulasi ribuan potong pakaian menghasilkan selisih rupiah yang merugikan audit pajak. |
| **Normalized Micro-Minutes** (`SamMinutesCalculator`) | `Ratio.plus` berantai | Menghindari *integer overflow* pada penyebut $10^6 \times 10^6 \times 10^6$ yang melampaui `Long.MAX_VALUE`. | Nilai waktu kerja SAM tiba-tiba menjadi angka negatif besar, menyebabkan HPP hancur total. |
| **Claymorphism Design System** | Material Design standar | Memenuhi identitas brand visual WeMade (Neo-Brutalism clay, outline 3dp, hard shadow, tipografi ramah). | Tampilan terlihat generik seperti template admin gratisan dan melanggar aturan desain pabrik. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

### 1. Jebakan Double Margin Counting
- **Kenapa bahaya**: Pemula sering membuat bucket `MARGIN` dengan `isBillableToClient = true`, lalu di akhir rumus menghitung lagi: `hargaJual = billable + (billable * marginRatio)`.
- **Dampaknya**: Margin dihitung dua kali lipat! Harga penawaran ke buyer menjadi terlalu mahal dan pabrik kalah tender.
- **Solusi kita**: Bucket `MARGIN` selalu berstatus `isBillableToClient = false` (hanya bersifat informatif).

### 2. Menagih Bahan Konsinyasi (Klien)
- **Kenapa bahaya**: Pemula memasukkan nilai total kain titipan ke dalam tagihan invoice.
- **Dampaknya**: Buyer marah besar karena ditagih atas kain yang mereka serahkan sendiri ke pabrik.
- **Solusi kita**: `CostBucket.material(...)` otomatis mengatur `amountPerUnit = Money.zero()` dan `isBillableToClient = false` jika kepemilikan kain adalah `CONSIGNED_CLIENT_MATERIAL`.

### 3. Mutasi Lembar yang Sudah Disetujui (*Approved State Mutation*)
- **Kenapa bahaya**: Mengizinkan tombol edit atau override parameter tetap aktif saat status lembar `APPROVED`.
- **Dampaknya**: Kontrak hukum yang sudah ditandatangani dirusak oleh operator.
- **Solusi kita**: State `APPROVED` adalah *terminal state*. Untuk mengubah angka, lembar harus di-revisi terlebih dahulu melalui `ReviseCostingSheetUseCase` yang menurunkan status kembali ke `DRAFT` dan membatalkan persetujuan sebelumnya.

### 4. Circularity Trap pada Marketplace Fee
- **Kenapa bahaya**: Menghitung potongan marketplace (misal: 6.5%) dari HPP: $6.5\% \times \text{HPP}$.
- **Dampaknya**: Marketplace seperti Shopee atau Tokopedia memotong komisi dari **Harga Jual Retail**, bukan dari modal pabrik! Pabrik tekor karena potongan nyata platform jauh lebih besar dari perkiraan.
- **Solusi kita**: Di `RetailValuationWithFeesStrategy`, fee marketplace dihitung di atas harga jual retail: $\text{Retail Price} \times \text{marketplaceFeeRatio}$.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian dilakukan berlapis di ketiga modul:

### 1. Pure Domain Unit Tests (`CostingEngineTest.kt` di `:core`)
Menguji strategi kalkulasi, SAM breakdown, dan codec tanpa server:
```kotlin
@Test
fun fullPackageCogsStrategy_calculatesDirectCostsLaborOverhead() {
    // Verifikasi Material + Labor + Subcon + Overhead + Packaging = COGS eksak
    val result = FullPackageCogsStrategy.calculate(input).getOrThrow()
    assertEquals(51_500_00L, materialSum.minorUnits)
    assertEquals(10_000_00L, laborBucket.amountPerUnit.minorUnits)
    assertEquals(3_000_00L, subconBucket.amountPerUnit.minorUnits)
}
```

### 2. Ktor Integration & RBAC Tests (`CostingApiTest.kt` di `:server`)
Menguji endpoint HTTP dan memastikan penolakan HTTP 403 jika role tidak berwenang:
```kotlin
@Test
fun approve_withoutApproveCostingPermission_returnsForbidden() = testApplication {
    // Kirim request approve dengan role SALES (tanpa wewenang APPROVE_COSTING)
    val response = client.post("/api/tenant/costing/sheets/cst-1/approve") { ... }
    assertEquals(HttpStatusCode.Forbidden, response.status)
}
```

### 3. ViewModel State & RBAC Tests (`CostingViewModelTest.kt` di `:app:shared`)
Menguji penyembunyian margin komersial untuk staf operator dan pencegahan aksi approval client-side:
```kotlin
@Test
fun viewModel_withoutViewCostingMarginPermission_marginAndSellingPriceHidden() = testScope.runTest {
    val vm = CostingViewModel(..., showMarginOverride = false)
    assertFalse(vm.uiState.value.showMargin)
}
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

Untuk memperdalam pemahamanmu, coba kerjakan latihan berikut di lingkungan lokalmu:

- [ ] **Tantangan 1 (Volumetric Scrap Rebate)**:
  Buat parameter baru `scrapRebatePerKg` di mana limbah perca kain dari pemotongan pola dijual ke pabrik daur ulang, dan nilai penjualannya mengurangi HPP sebagai bucket diskon negatif.
- [ ] **Tantangan 2 (Currency Exchange Fluctuations)**:
  Tambahkan kalkulasi di mana bahan baku diimpor dalam mata uang USD, sementara biaya tenaga kerja dibayar dalam IDR. Uji bagaimana `CostingDrift` bereaksi ketika kurs USD/IDR melonjak dari Rp 15.000 ke Rp 16.500.
- [ ] **Tantangan 3 (Tiered Quantity Discount)**:
  Modifikasi `SimulateCostingUseCase` untuk membandingkan HPP per pcs pada kuantitas 500 pcs vs 2.000 pcs vs 10.000 pcs guna membuktikan efek *economies of scale* pada alokasi biaya kemasan dan overhead.
