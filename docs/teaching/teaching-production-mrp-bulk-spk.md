# 🎓 Modul Pembelajaran: Modul PRODUCTION_MRP — Jadwal Mesin & SPK Produksi Massal

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design, Aggregate Design, Invariant Enforcement, Kotlin Multiplatform, Exposed + Flyway, jsonb vs tabel anak, Telemetri Operasional, Compose Multiplatform (Clay Design System)
> **Prasyarat**: Paham dasar Kotlin (data class, value class, sealed interface), pernah membaca `.claude/rules/module-integration-rules.md` dan `.claude/rules/design-system-rules.md`
> **Referensi Task**: Modul greenfield — tidak ada planning doc; lahir dari temuan bahwa tombol `[ Luncurkan SPK Massal ]` di Deal tidak melakukan apa pun

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Sebelum modul ini ada, alur pabrik punya lubang menganga di tengahnya.

Sales menutup deal. Tim sampling membuat sampel. Buyer bilang "ACC, lanjut produksi 1.000 pcs".
Admin membuka Tab 2 di Deal, menekan tombol besar bertuliskan **`[ Luncurkan SPK Massal ]`** …
dan yang terjadi hanyalah **label stage deal berubah dari `WON` menjadi `IN_PRODUCTION`**.

Itu saja. Tidak ada dokumen kerja yang terbit. Tidak ada angka per ukuran yang sampai ke meja
potong. Tidak ada lini mesin yang dialokasikan. Lantai produksi tetap menunggu WhatsApp dari
admin, dan admin mengira sistem sudah mengurusnya.

Kekacauan yang muncul kalau ini dibiarkan atau dibuat asal-asalan:

| Kalau begini dibiarkan… | Akibat nyatanya di pabrik |
|---|---|
| SPK bisa terbit dua kali untuk satu deal | Kain dipotong dua kali — 1.000 pcs jadi 2.000 pcs, dan yang 1.000 tidak ada pembelinya |
| SPK bisa terbit tanpa sampel ACC | Lantai produksi mengerjakan 1.000 pcs tanpa acuan spesifikasi yang disepakati buyer |
| Laporan hasil ditulis sebagai "tambahan hari ini" | Laporan yang terkirim dua kali menggandakan hasil; laporan yang telat bikin angka mundur |
| Meja jahit boleh lapor 500 pcs padahal potong baru 300 | Angka di sistem tidak mungkin secara fisik, dan tidak ada yang tahu mana yang bohong |
| Alokasi lini boleh melebihi pesanan | Muncul laporan "selesai 120%", dan tidak ada yang bisa melacak 20% itu dari mana |

### Analogi Sederhana

Bayangkan **dapur restoran**.

- **Deal** adalah pesanan dari pelanggan di meja: "saya mau 1.000 porsi".
- **Golden Sample** adalah piring contoh yang sudah dicicipi dan disetujui pelanggan. Tanpa itu,
  koki memasak 1.000 porsi berdasarkan tebakan.
- **SPK Massal** adalah *kertas order* yang ditempel di jendela dapur. Inilah yang selama ini
  tidak pernah tercetak — pelayan hanya berteriak ke arah dapur.
- **Alokasi Lini** adalah pembagian: kompor 1 pegang 400 porsi, kompor 2 pegang 600.
- **Tahap Potong → Jahit → Finishing** adalah stasiun berurutan. Stasiun plating tidak bisa
  menyelesaikan 500 porsi kalau stasiun kompor baru mengeluarkan 300.

### Hasil Akhir yang Diharapkan

Setelah modul ini selesai:
1. Tombol `[ Luncurkan SPK Massal ]` benar-benar menerbitkan dokumen kerja `SPK-MSL-0001`.
2. Modul `PRODUCTION_MRP` punya layarnya sendiri — bukan lagi placeholder generik.
3. Lantai produksi bisa mencatat hasil per tahap, dan sistem menolak angka yang mustahil.
4. Node `PRODUCTION_MRP` di kanvas Alur Pabrik punya telemetri nyata (`wipPieces`,
   `cycleTimeHours`, `healthStatus`).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Ini bagian yang paling sering ditanya junior: *"filenya banyak banget, aku ngetik dari mana?"*

Jawabannya: **dari yang paling tidak bergantung pada apa pun**, lalu merambat keluar.

### Langkah 0: Baca kontrak yang sudah ada — jangan langsung ngetik

Sebelum satu baris pun ditulis, saya mengecek `OperationalModuleCatalog.kt`:

```kotlin
object ProductionMrpModule : OperationalModuleSpecification {
    override val module = BusinessModule.PRODUCTION_MRP
    override val upstreamPrerequisites = listOf("CuttingOrderWithFabric")
    override val downstreamHandoffs = listOf("CutPiecesBundle")
    override fun stockOwnershipFor(preset: GarmentBusinessPreset) = when (preset) {
        GarmentBusinessPreset.CMT_MAKLOON -> StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL
        else -> StockOwnershipSemantics.OWNED_RAW_MATERIAL
    }
}
```

**Kenapa ini langkah nol?** Karena Kontrak 1 & 2 di `module-integration-rules.md` (deklarasi
archetype dan tipe port) **ternyata sudah dipenuhi orang sebelumnya**. Kalau saya tidak cek dan
langsung bikin registry sendiri, saya menambah sumber kebenaran kedua untuk hal yang sama.

> 💡 **Pelajaran mental model**: Membaca dulu bukan basa-basi. Kerjaan paling murah adalah
> kerjaan yang ternyata sudah ada.

### Langkah 1: Value Objects (`core/domain/production/BulkWorkOrderValueObjects.kt`)

Mulai dari potongan terkecil yang tidak bergantung apa-apa: `BulkWorkOrderId`, `BulkSpkNumber`,
`BulkSizeLine`, `MachineLineAllocation`, `ProductionStageProgress`, plus enum `ProductionStage`
dan `BulkProductionStatus`.

**Kenapa dari sini, bukan dari tabel database?**

Karena begitu Anda mulai dari tabel, cara berpikir Anda jadi *"kolom apa yang saya butuh"*, dan
aturan bisnis tercecer ke mana-mana. Mulai dari value object memaksa Anda menjawab pertanyaan
yang benar dulu: *"apa yang bikin sebuah `BulkSizeLine` itu sah?"*

### Langkah 2: Entity & Aturan Domain (`BulkWorkOrder.kt`)

Baru di sini aturan bisnis ditulis: kapan SPK boleh terbit, berapa batas alokasi, apa yang boleh
dilaporkan. **Nol import framework** — tidak ada Ktor, tidak ada Exposed, tidak ada Compose.

### Langkah 3: Repository Interface (`BulkWorkOrderRepository.kt`)

Domain *mendeklarasikan apa yang dia butuh*, bukan menerima apa yang kebetulan database sediakan.

### Langkah 4: Use Cases (`domain/production/usecases/`)

Satu operasi bisnis = satu use case. `LaunchBulkWorkOrderFromDealUseCase`,
`RecordProductionProgressUseCase`, `AllocateProductionLineUseCase`, `GetProductionTelemetryUseCase`.

### Langkah 5: Codec (`shared/production/BulkWorkOrderCodec.kt`)

Serialisasi. Ditulis **setelah** domain, karena codec adalah cermin domain — bukan sebaliknya.

### Langkah 6: Infrastructure (`server/`)

Barulah migrasi Flyway `V42`, `ProductionTables.kt`, dan `PostgresBulkWorkOrderRepository.kt`.

**Kenapa database baru di langkah 6 dari 8?** Karena sekarang bentuk datanya sudah *diketahui
pasti* dari domain. Kalau dibalik, Anda akan mendesain tabel berdasarkan tebakan, lalu domain
dipaksa mengikuti bentuk tabel — dan itulah asal-usul Anemic Domain Model.

### Langkah 7: Routes + Wiring (`ProductionRoutes.kt`, `Application.kt`)

### Langkah 8: Client & Presentation (`app/shared/`)

ApiClient → UiState → ViewModel → Screen. UI **terakhir**, karena UI adalah lapisan yang paling
sering berubah dan paling tidak layak menyandera desain.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Value Object yang Menolak Data Mustahil

```kotlin
data class ProductionStageProgress(
    val stage: ProductionStage,
    val completedPcs: Int = 0,
    val reworkPcs: Int = 0,
    val rejectPcs: Int = 0,
    val lastUpdatedAt: Instant? = null
) {
    init {
        require(completedPcs >= 0) { "Jumlah selesai tidak boleh negatif" }
        require(reworkPcs <= completedPcs) {
            "Rework (${reworkPcs}) tidak boleh melebihi jumlah selesai (${completedPcs}) di tahap ${stage.displayName}"
        }
    }

    /** Pcs yang benar-benar lolos ke tahap berikutnya. */
    val passedPcs: Int get() = completedPcs - reworkPcs
}
```

**Mengapa blok ini ditulis begini?**

- **`init { require(...) }` bukan validasi di controller.** Kalau validasi ditaruh di route
  handler, maka objek tidak sah masih *bisa dibentuk* dari jalur lain (test, migrasi data,
  use case lain). Dengan `init`, `ProductionStageProgress` yang tidak masuk akal **secara harfiah
  tidak bisa eksis di memori**. Ini yang disebut *making illegal states unrepresentable*.
- **Pesan error menyebut angkanya.** Bandingkan `"Data tidak valid"` dengan
  `"Rework (60) tidak boleh melebihi jumlah selesai (50) di tahap Jahit"`. Yang kedua bisa
  langsung ditindaklanjuti operator tanpa menelepon developer.
- **`passedPcs` adalah *derived property*, bukan kolom.** Kalau disimpan sebagai field, ia bisa
  menyimpang dari `completedPcs - reworkPcs`. Yang bisa dihitung, jangan disimpan.

### Blok B: Aturan Berurutan Antar Tahap — Invariant Paling Penting

```kotlin
fun recordStageProgress(
    stage: ProductionStage,
    completedPcs: Int,
    reworkPcs: Int = 0,
    rejectPcs: Int = 0,
    updatedAt: Instant
): BulkWorkOrder {
    require(status != BulkProductionStatus.DRAFT) {
        "SPK ${spkNumber.value} belum diterbitkan — terbitkan dulu sebelum mencatat hasil produksi."
    }
    require(completedPcs <= totalOrderedPcs) {
        "Hasil ${stage.displayName} ($completedPcs pcs) melebihi pesanan $totalOrderedPcs pcs."
    }
    val upstreamLimit = stage.previous?.let { progressFor(it).passedPcs }
    if (upstreamLimit != null) {
        require(completedPcs <= upstreamLimit) {
            "Hasil ${stage.displayName} ($completedPcs pcs) melebihi hasil ${stage.previous?.displayName} " +
                "yang lolos ($upstreamLimit pcs)."
        }
    }
    // …
}
```

**Mengapa blok ini ditulis begini?**

- **Angkanya KUMULATIF, bukan delta.** Ini keputusan besar. Kalau parameternya "tambahan hari
  ini", maka laporan yang ter-submit dua kali (jaringan pabrik jelek, operator klik dua kali)
  akan menggandakan hasil, dan tidak ada cara memperbaikinya selain edit database. Dengan angka
  kumulatif, submit dua kali menghasilkan nilai yang sama persis — ini disebut **idempoten**.
- **Batas atasnya adalah `passedPcs` hulu, bukan `completedPcs` hulu.** Perhatikan bedanya: kalau
  meja potong menyelesaikan 300 pcs tapi 50 di antaranya perlu rework, yang benar-benar sampai
  ke meja jahit hanyalah 250. Memakai `completedPcs` akan mengizinkan jahit melaporkan 300 pcs
  yang secara fisik belum ada di mejanya.
- **`stage.previous` diambil dari enum, bukan dari `when` bercabang tiga.** Kalau besok ada tahap
  baru (misal `WASHING` di antara jahit dan finishing), cukup tambah satu entri enum — bukan
  berburu `when` di lima file.

### Blok C: Status Tidak Boleh Mundur

```kotlin
val newStatus = when {
    totalOrderedPcs > 0 && newCompleted >= totalOrderedPcs -> BulkProductionStatus.COMPLETED
    // Status mengikuti tahap terjauh yang sudah punya hasil, bukan tahap yang baru dilapor —
    // koreksi angka potong tidak boleh menarik SPK mundur dari Jahit ke Potong.
    else -> newProgress
        .filter { it.completedPcs > 0 }
        .maxByOrNull { it.stage.order }
        ?.stage?.runningStatus
        ?: status
}

return copy(
    stageProgress = newProgress,
    status = if (newStatus.order > status.order || newStatus == BulkProductionStatus.COMPLETED) newStatus else status,
    updatedAt = updatedAt
)
```

**Mengapa blok ini ditulis begini?**

Ini kasus yang **tidak akan terpikir sampai Anda membayangkan hari kerja nyata**.

Skenarionya: SPK sudah masuk tahap Jahit. Lalu supervisor sadar angka Potong salah ketik —
tertulis 1.000, harusnya 950. Dia mengoreksi angka Potong.

Kalau status naif mengikuti "tahap yang baru saja dilaporkan", SPK ini akan **mundur dari
`SEWING` ke `CUTTING`** hanya karena ada koreksi ketik. Di dashboard, seluruh pabrik akan
melihat pekerjaan yang seolah-olah mundur.

Makanya: status mengikuti **tahap terjauh yang punya hasil**, dan hanya boleh naik
(`newStatus.order > status.order`). Satu-satunya pengecualian adalah `COMPLETED`, karena itu
kesimpulan dari angka, bukan urutan.

### Blok D: Telemetri yang Jujur — `largestStageBacklog` vs `wipPieces`

Ini blok yang **saya tulis salah dulu**, dan baru ketahuan karena ada test yang gagal. Justru
karena itu, ini pelajaran paling berharga di dokumen ini.

**Versi pertama (SALAH):**

```kotlin
val healthStatus: FlowHealthStatus
    get() = when {
        // …
        wipPieces * 2 > totalOrderedPcs -> FlowHealthStatus.BOTTLENECK
        else -> FlowHealthStatus.HEALTHY
    }
```

Kelihatan masuk akal: "kalau lebih dari separuh pesanan masih menganggur, berarti macet."

**Kenapa salah?** Karena **SPK yang baru saja diterbitkan punya WIP = 100%**. Seluruh 1.000 pcs
memang masih menunggu di meja potong — itu antrean normal, bukan kemacetan. Dengan rumus di atas,
**setiap SPK baru lahir langsung berkedip kuning**.

Dan lampu peringatan yang selalu menyala adalah lampu yang berhenti dibaca orang.

**Versi kedua (BENAR):**

```kotlin
/**
 * Tumpukan terbesar di satu titik serah-terima antar tahap.
 *
 * Dipakai sebagai dasar sinyal kemacetan, bukan [wipPieces] total: 600 pcs yang tersebar
 * merata di tiga tahap adalah aliran yang sehat, sedangkan 600 pcs yang semuanya menunggu
 * di depan meja jahit adalah kemacetan — dan keduanya punya [wipPieces] yang sama persis.
 */
val largestStageBacklog: Int
    get() = ProductionStage.entries.maxOfOrNull { stage ->
        val upstream = stage.previous?.let { progressFor(it).passedPcs } ?: totalOrderedPcs
        (upstream - progressFor(stage).completedPcs).coerceAtLeast(0)
    } ?: 0

val healthStatus: FlowHealthStatus
    get() = when {
        status == BulkProductionStatus.CANCELLED -> FlowHealthStatus.BYPASSED
        status == BulkProductionStatus.COMPLETED -> FlowHealthStatus.HEALTHY
        totalOrderedPcs <= 0 -> FlowHealthStatus.HEALTHY
        totalRejectPcs * 10 > totalOrderedPcs -> FlowHealthStatus.CRITICAL
        status == BulkProductionStatus.DRAFT || status == BulkProductionStatus.RELEASED ->
            FlowHealthStatus.HEALTHY
        largestStageBacklog * 2 > totalOrderedPcs -> FlowHealthStatus.BOTTLENECK
        else -> FlowHealthStatus.HEALTHY
    }
```

**Tiga pelajaran dari blok ini:**

1. **Total menyembunyikan distribusi.** 600 pcs tersebar merata di tiga tahap = aliran sehat.
   600 pcs menumpuk semua di depan meja jahit = kemacetan. `wipPieces` keduanya **sama persis**.
   Yang membedakan hanya `largestStageBacklog`.
2. **Antrean awal bukan kemacetan.** Makanya `DRAFT` dan `RELEASED` dikecualikan secara eksplisit.
3. **Jangan mengklaim sinyal yang tidak bisa Anda buktikan.** Saya *ingin* ada status `CRITICAL`
   untuk "macet terlalu lama". Tapi `BulkWorkOrder` tidak memegang jam kerja pabrik — dia tidak
   bisa membedakan "diam 2 jam" (istirahat siang) dari "diam 3 hari" (mesin rusak). Jadi
   `CRITICAL` hanya dipakai untuk hal yang benar-benar terbukti dari angkanya: **tingkat reject**.

> 💡 **Mental model**: Lebih baik tidak punya sinyal daripada punya sinyal yang berbohong.
> Sinyal palsu lebih berbahaya daripada tidak ada sinyal, karena orang mengambil keputusan
> berdasarkan sinyal palsu itu.

### Blok E: Database Schema & Migrasi Flyway

```sql
CREATE TABLE IF NOT EXISTS bulk_work_orders (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    spk_number VARCHAR(50) NOT NULL,
    deal_id VARCHAR(64) REFERENCES deals(id) ON DELETE SET NULL,
    golden_sample_order_id VARCHAR(64) REFERENCES sampling_orders(id) ON DELETE RESTRICT,

    size_breakdown JSONB NOT NULL DEFAULT '[]',
    line_allocations JSONB NOT NULL DEFAULT '[]',
    stage_progress JSONB NOT NULL DEFAULT '[]',
    -- …
);

-- Satu deal hanya boleh punya satu SPK massal hidup
CREATE UNIQUE INDEX IF NOT EXISTS idx_bulk_work_orders_one_active_per_deal
    ON bulk_work_orders(tenant_id, deal_id)
    WHERE deal_id IS NOT NULL AND archived_at IS NULL AND status <> 'CANCELLED';

SELECT apply_tenant_rls('bulk_work_orders');
```

**Mengapa blok ini ditulis begini?**

#### 1. Kenapa `jsonb`, bukan tabel anak?

Pertanyaan yang benar bukan *"mana yang lebih 'benar' secara normalisasi"*, tapi
**"apakah data ini pernah di-query lepas dari induknya?"**

| Pertanyaan | `size_breakdown` | Contoh yang butuh tabel anak |
|---|---|---|
| Pernah dibaca tanpa SPK-nya? | Tidak pernah | "Tampilkan semua invoice jatuh tempo" |
| Pernah di-`UPDATE` satu baris saja? | Tidak — selalu diganti seluruhnya | Status satu item bisa berubah sendiri |
| Perlu di-`JOIN` dari arah lain? | Tidak | Sering |

Ketiganya selalu dibaca-tulis **utuh bersama SPK-nya**. Tabel anak hanya akan menambah `JOIN`
tanpa menambah satu pun kemampuan. Ini penerapan prinsip *aggregate boundary* dari DDD: yang
satu paket dalam hidup dan matinya, disimpan satu paket.

> ⚠️ **Tapi hati-hati**: Ini BUKAN alasan untuk menaruh segalanya di jsonb. Kalau besok ada
> kebutuhan "tampilkan semua lini mesin yang bebas minggu depan lintas SPK", maka
> `line_allocations` **harus** dipromosikan jadi tabel.

#### 2. Kenapa `UNIQUE INDEX ... WHERE`(*partial index*)?

Ini gerbang anti-kain-dipotong-dua-kali, **ditegakkan di level database**.

Kenapa tidak cukup dicek di use case saja? Karena dua admin bisa menekan tombol bersamaan.
Pengecekan di aplikasi punya celah *race condition*: keduanya membaca "belum ada SPK", keduanya
lalu menulis. Database yang memegang kunci uniknya tidak punya celah itu.

Klausa `WHERE` membuatnya **partial**: SPK yang dibatalkan (`CANCELLED`) atau diarsipkan
dikecualikan, sehingga deal yang SPK-nya batal masih bisa diterbitkan ulang.

#### 3. Kenapa `golden_sample_order_id ON DELETE RESTRICT`?

Bandingkan tiga pilihan:

| Pilihan | Yang terjadi kalau sampling order dihapus |
|---|---|
| `ON DELETE CASCADE` | SPK massal ikut terhapus — **dokumen kerja pabrik lenyap** karena sampelnya dirapikan |
| `ON DELETE SET NULL` | SPK massal tetap ada tapi **kehilangan acuan spesifikasi** — lantai produksi bekerja tanpa panduan |
| `ON DELETE RESTRICT` ✅ | Penghapusan **ditolak** selama masih ada SPK yang mengacu padanya |

`deal_id` memakai `SET NULL` karena berbeda sifatnya: deal adalah konteks komersial, dan SPK
tetap bermakna tanpanya. Golden Sample adalah **acuan teknis** — SPK tanpa itu adalah dokumen
yang menyuruh orang bekerja tanpa spesifikasi.

### Blok F: Idempotensi di Use Case Penerbitan

```kotlin
suspend operator fun invoke(command: LaunchBulkWorkOrderCommand): Result<BulkWorkOrder> = runCatching {
    val existing = workOrderRepository.findByDealId(command.tenantId, command.dealId.value)
        .firstOrNull { it.status != BulkProductionStatus.CANCELLED && !it.isArchived }
    if (existing != null) return@runCatching existing   // ← klik kedua: kembalikan yang sudah ada

    val goldenSample = samplingOrders.firstOrNull { it.status == SamplingStatus.ACC_APPROVED }
        ?: error(
            "Deal \"${deal.title.value}\" belum punya sampel ber-ACC buyer. " +
                "Selesaikan ACC sampel dulu sebelum menerbitkan SPK massal."
        )
    // …
}
```

**Mengapa blok ini ditulis begini?**

- **Klik kedua tidak menghasilkan error, tapi mengembalikan hasil yang sama.** Ini penting secara
  pengalaman pengguna: admin yang tidak yakin tombolnya kepencet akan menekan lagi. Kalau kita
  melempar error `"SPK sudah ada"`, admin akan panik mengira ada yang rusak. Mengembalikan SPK
  yang sama membuat operasinya **aman diulang** — tepat seperti `recordStageProgress` yang
  kumulatif. Pola yang sama, dua tempat berbeda.
- **Pesan errornya menyebutkan langkah berikutnya**, bukan cuma masalahnya. Bandingkan
  `"Golden sample not found"` (admin bingung harus apa) dengan
  `"Selesaikan ACC sampel dulu sebelum menerbitkan SPK massal"` (admin tahu harus ke mana).

### Blok G: Menolak Godaan Menulis Parser "Pintar"

```kotlin
/**
 * Deskripsi baris PO dipakai apa adanya sebagai label ukuran — **tidak** ditebak polanya.
 * Parser yang mencoba menerka "Kemeja PDH ukuran L" jadi "L" akan salah diam-diam pada format
 * PO yang tak terduga, dan salahnya baru ketahuan setelah kain dipotong.
 */
fun deriveSizeBreakdown(purchaseOrders: List<PurchaseOrder>): List<BulkSizeLine> =
    purchaseOrders
        .flatMap { it.lines }
        .mapNotNull { line ->
            val pcs = line.quantity.roundToInt()
            if (pcs <= 0) null else line.description.trim().uppercase() to pcs
        }
        .groupBy({ it.first }, { it.second })
        .map { (label, quantities) -> BulkSizeLine(sizeLabel = label, orderedPcs = quantities.sum()) }
```

**Mengapa blok ini ditulis begini?**

Baris PO buyer isinya teks bebas: `"Kemeja PDH Lengan Panjang - L"`, `"PDH size L"`,
`"KEMEJA L (lengan panjang)"`. Godaan besarnya adalah menulis regex yang mengekstrak huruf
ukurannya.

**Kenapa itu ide buruk di sini?** Karena kalau parsernya salah, **tidak ada yang tahu**. Sistem
akan dengan percaya diri menampilkan "Ukuran L: 600 pcs" padahal yang dimaksud PO adalah hal
lain. Dan kesalahannya baru ketahuan **setelah kain terlanjur dipotong**.

Jadi kita ambil jalan yang jujur: **pakai deskripsinya apa adanya**, dan beri admin cara
merapikannya lewat `updateSizeBreakdown` selama SPK masih `DRAFT`.

> 💡 **Mental model**: Antara "salah diam-diam" dan "jelek tapi terlihat", selalu pilih yang
> terlihat. Manusia bisa memperbaiki apa yang dia lihat; dia tidak bisa memperbaiki apa yang
> tidak dia tahu salah.

Perhatikan juga `if (pcs <= 0) null` — baris dengan kuantitas 0 **dibuang**, bukan dipaksa jadi 1.
Nol di PO artinya nol.

### Blok H: Codec yang Tahan Data Rusak

```kotlin
/**
 * Menjamin ketiga tahap selalu hadir dan urut.
 *
 * `BulkWorkOrder.wipPieces` menjumlah seluruh `ProductionStage.entries`; tahap yang hilang
 * dari jsonb akan dibaca sebagai progres nol dan memunculkan WIP palsu sebesar satu pesanan penuh.
 */
private fun fillMissingStages(items: List<ProductionStageProgress>): List<ProductionStageProgress> =
    ProductionStage.entries.map { stage ->
        items.firstOrNull { it.stage == stage } ?: ProductionStageProgress(stage)
    }

/**
 * Baris rusak dibuang, bukan membatalkan seluruh SPK.
 */
private fun decodeSizeLine(obj: JsonValue.Obj): BulkSizeLine? = runCatching {
    BulkSizeLine(
        sizeLabel = obj.string("sizeLabel") ?: "",
        orderedPcs = obj.int("orderedPcs") ?: 0
    )
}.getOrNull()
```

**Mengapa blok ini ditulis begini?**

Ada ketegangan menarik di sini, dan junior perlu melihatnya:

- Di **Blok A**, kita bilang `init { require(...) }` itu bagus karena mencegah objek tidak sah.
- Di **sini**, `require` yang sama justru **berbahaya**: satu baris jsonb cacat dari data lama
  akan melempar exception dan membuat **seluruh SPK mustahil dibuka**.

Solusinya bukan membuang `require`, tapi **memilih di mana kegagalannya terjadi**. Di gerbang
masuk (input pengguna): tolak keras. Di gerbang baca (data lama yang sudah terlanjur ada):
selamatkan yang bisa diselamatkan.

Kehilangan satu baris ukuran jauh lebih murah daripada kehilangan seluruh dokumen kerja.

`fillMissingStages` menangani masalah berbeda: kalau jsonb hanya menyimpan tahap `CUTTING`,
maka `wipPieces` akan menghitung `SEWING` dan `FINISHING` sebagai progres nol — memunculkan
**WIP hantu** sebesar satu pesanan penuh di dashboard.

### Blok I: Nomor SPK — Kenapa Bukan `count()`

```kotlin
override suspend fun nextSpkNumber(tenantId: TenantId): BulkSpkNumber =
    DatabaseFactory.dbQuery(tenantId) {
        val highest = BulkWorkOrdersTable.selectAll()
            .where { BulkWorkOrdersTable.tenantId eq tenantId.value }
            .mapNotNull { row -> row[BulkWorkOrdersTable.spkNumber].removePrefix(SPK_PREFIX).toIntOrNull() }
            .maxOrNull() ?: 0

        BulkSpkNumber("$SPK_PREFIX${(highest + 1).toString().padStart(4, '0')}")
    }
```

**Mengapa blok ini ditulis begini?**

Pola `count() + 1` **terlihat benar dan diam-diam salah**:

1. Ada 3 SPK: `0001`, `0002`, `0003`. `count()` = 3 → berikutnya `0004`. ✅
2. SPK `0002` diarsipkan. `count()` aktif = 2 → berikutnya **`0003`**. ❌ Bentrok!

Mengambil **nomor tertinggi yang pernah dipakai** kebal terhadap ini, karena SPK yang diarsipkan
tetap memegang nomornya.

> ⚠️ **Catatan jujur**: Implementasi ini masih punya *race condition* kalau dua SPK terbit dalam
> milidetik yang sama. Yang menahannya adalah `UNIQUE INDEX` di Blok E — salah satu akan ditolak
> database. Ini contoh **pertahanan berlapis**: aplikasi berusaha benar, database memastikan.

### Blok J: Urutan Operasi di Tombol Deal

```kotlin
private fun launchBulkProduction() {
    val deal = _uiState.value.deal ?: return
    if (!_uiState.value.canWrite) return
    _uiState.update { it.copy(isSaving = true) }
    scope.launch {
        productionDataSource.launchFromDeal(tenantSlug, deal.id.value)
            .onSuccess { workOrder ->
                _uiState.update { /* … pesan sukses … */ }
                changeStage(DealStage.IN_PRODUCTION)   // ← stage digeser SETELAH SPK terbit
            }
            .onFailure { error ->
                _uiState.update { it.copy(isSaving = false, error = error.message) }
            }
    }
}
```

**Mengapa blok ini ditulis begini?**

Urutannya adalah keputusan desain, bukan kebetulan.

Kalau dibalik (geser stage dulu, terbitkan SPK kemudian) dan penerbitan gagal — misal belum ada
sampel ACC, atau PO belum ditempel — maka deal akan tampil sebagai **`IN_PRODUCTION`** di seluruh
dashboard sales, padahal lantai produksi tidak memegang dokumen apa pun.

Itu kebohongan yang paling mahal: bukan yang bikin sistem *error*, tapi yang bikin sistem
*terlihat baik-baik saja*.

Perhatikan juga logika bisnisnya ada di **ViewModel**, bukan di Composable. Composable-nya hanya:

```kotlin
ClayButton(
    text = "Luncurkan SPK Massal",
    onClick = { onEvent(DealUiEvent.LaunchBulkProduction) },
    // …
)
```

Sesuai aturan §5 `CLAUDE.md`: tidak ada logika bisnis di Composable.

### Blok K: Design System — Warna Domain vs Token

```kotlin
/**
 * Warna kesehatan node diambil dari domain (`badgeColorHex`), bukan dari token.
 */
@Composable
fun ProductionHealthBadge(health: FlowHealthStatus) {
    ClayBadge(text = health.label, tint = Color(health.badgeColorHex), dot = true)
}

fun BulkProductionStatus.tint(): Color = when (this) {
    BulkProductionStatus.DRAFT -> WeMadeColors.OnSurfaceMuted
    BulkProductionStatus.RELEASED -> WeMadeColors.Info
    BulkProductionStatus.CUTTING -> WeMadeColors.Accent
    // …
}
```

**Mengapa blok ini ditulis begini?**

Aturan §13 `CLAUDE.md` bilang: **nol literal `Color(0xFF……)` di luar `WeMadeTheme.kt`**. Tapi di
sini ada `Color(health.badgeColorHex)`. Apakah ini melanggar?

**Tidak** — dan bedanya penting:

| | Literal warna | Warna dari domain |
|---|---|---|
| Contoh | `Color(0xFF16A34A)` | `Color(health.badgeColorHex)` |
| Sifatnya | Keputusan **desain** | **Data** |
| Kalau tenant ganti palet | Harus edit kode | Ikut berubah sendiri |
| Sumber kebenaran | `WeMadeTheme.kt` | `FlowHealthStatus.kt` di `core/domain/` |

Alasan praktisnya: hijau/amber/merah pada `FlowHealthStatus` adalah **sinyal produksi**, dan
nilainya hidup di domain supaya **kanvas Alur Pabrik dan layar ini tidak pernah menampilkan dua
warna berbeda untuk keadaan yang sama**.

Perhatikan juga di mana file ini tinggal: `presentation/production/components/`, **bukan**
`presentation/designsystem/`. Sesuai Kontrak 6 aturan design system, komponen bersama harus
**buta terhadap domain** — `ClayBadge` hanya tahu `String` dan `Color`. Yang tahu apa itu
`BulkProductionStatus` adalah pembungkus tipis di package fiturnya.

### Blok L: Outline Konsisten, Warna yang Bicara

```kotlin
ClayCard(
    modifier = Modifier.fillMaxWidth(),
    // Ketebalan outline tetap; yang membedakan state adalah warnanya (Kontrak 8).
    outlineColor = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
    selected = isSelected,
    offset = ClayOffset.Small,
    onClick = onClick
) { /* … */ }
```

**Mengapa tidak menebalkan border untuk kartu terpilih?** Karena kartu yang "menggemuk" saat
diklik akan **menggeser layout tetangganya**. Bandingkan dengan pola yang ditolak aturan:

```kotlin
// ❌ DITOLAK
val border = when {
    isSelected -> BorderStroke(2.dp, Primary)
    else       -> BorderStroke(1.dp, Border)
}
```

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi / Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Progres kumulatif** | Delta ("hasil hari ini") | Idempoten — submit dua kali hasilnya identik | Laporan ganda menggandakan output; koreksi mustahil tanpa edit DB |
| **`jsonb` untuk 3 koleksi** | Tabel anak ternormalisasi | Ketiganya selalu dibaca-tulis utuh; tabel anak hanya menambah `JOIN` | Kompleksitas tanpa manfaat; 4 tabel untuk 1 dokumen |
| **Aggregate tunggal `BulkWorkOrder`** | Entity terpisah per tahap | Invariant antar tahap (jahit ≤ potong) butuh satu batas transaksi | Aturan lintas-entity jadi mustahil ditegakkan tanpa distributed lock |
| **Partial `UNIQUE INDEX`** | Cek duplikat di use case saja | Kebal *race condition* dua admin klik bersamaan | Kain dipotong dua kali |
| **`ON DELETE RESTRICT`** (golden sample) | `CASCADE` / `SET NULL` | SPK tanpa acuan spesifikasi = dokumen berbahaya | Dokumen kerja lenyap, atau produksi tanpa panduan |
| **`largestStageBacklog`** | `wipPieces` total | Total menyembunyikan distribusi tumpukan | SPK baru langsung berkedip kuning; alarm jadi diabaikan |
| **Deskripsi PO apa adanya** | Regex ekstraksi ukuran | Salah yang terlihat > salah yang diam-diam | Label salah baru ketahuan setelah kain dipotong |
| **Value class `BulkWorkOrderId`** | `String` telanjang | Compiler menolak `BulkWorkOrderId` ditukar `DealId` | Bug tukar-ID yang tidak terdeteksi sampai runtime |
| **Nomor SPK dari `max()`** | `count() + 1` | Kebal terhadap SPK yang diarsipkan | Nomor SPK bentrok |
| **Terbitkan SPK → geser stage** | Geser stage → terbitkan SPK | Kalau gagal, deal tidak berbohong soal statusnya | Deal tampak `IN_PRODUCTION` padahal tidak ada dokumen kerja |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

### Jebakan 1: Menerima "hasil hari ini" alih-alih angka kumulatif

- *Kenapa bahaya*: Terasa lebih natural bagi operator ("hari ini saya selesai 50"), tapi jaringan
  pabrik sering jelek. Satu submit yang terkirim dua kali langsung menggandakan hasil, dan
  tidak ada cara memperbaikinya selain edit database.
- *Solusi elegan kita*: Parameter kumulatif + dialog yang **menyatakan terang-terangan** bahwa
  angkanya kumulatif:
  ```kotlin
  text = "${order.spkNumber.value} — isi angka KUMULATIF sejak awal, bukan tambahan hari ini. " +
      "Batas atas $upstreamLimit pcs."
  ```

### Jebakan 2: Memvalidasi hanya di UI

- *Kenapa bahaya*: Dialog saya **memang** memvalidasi (`validationError`). Tapi UI bisa
  di-bypass: lewat API langsung, script migrasi, atau dua admin bekerja bersamaan.
- *Solusi elegan kita*: **Tiga lapis**, dan masing-masing punya tugas berbeda:
  | Lapis | Tugasnya |
  |---|---|
  | UI (`RecordProgressDialog`) | Umpan balik cepat, tanpa menunggu server |
  | Domain (`recordStageProgress`) | Sumber kebenaran aturan bisnis |
  | Database (`UNIQUE INDEX`, `RESTRICT`) | Benteng terakhir terhadap *race condition* |

### Jebakan 3: Menyimpan nilai yang bisa dihitung

- *Kenapa bahaya*: Kalau `passedPcs` atau `totalOrderedPcs` disimpan sebagai kolom, suatu saat ia
  akan menyimpang dari komponennya — dan Anda punya dua angka yang berbeda untuk hal yang sama.
- *Solusi elegan kita*: Semuanya *derived property*: `totalOrderedPcs`, `allocatedPcs`,
  `unallocatedPcs`, `wipPieces`, `completionRatio`, `passedPcs`.

### Jebakan 4: `require` di jalur baca data lama

- *Kenapa bahaya*: Kebalikan dari Jebakan 2 — validasi ketat di tempat yang salah. `require` di
  decoder membuat satu baris jsonb cacat **melumpuhkan seluruh dokumen**.
- *Solusi elegan kita*: `runCatching { … }.getOrNull()` per baris di decoder; `require` tetap
  galak di konstruktor value object. **Ketat di gerbang masuk, lunak di gerbang baca.**

### Jebakan 5: Membuat alarm yang selalu menyala

- *Kenapa bahaya*: Ini jebakan yang **saya sendiri masuki**. Rumus `healthStatus` versi pertama
  membuat setiap SPK baru langsung `BOTTLENECK`. Alarm yang selalu menyala = alarm yang diabaikan
  = alarm yang tidak berguna saat benar-benar ada masalah.
- *Solusi elegan kita*: Kecualikan keadaan yang **normal-tapi-mirip-buruk** (`DRAFT`/`RELEASED`),
  dan jangan mengklaim sinyal yang datanya tidak mendukung (stagnasi berbasis waktu).

### Jebakan 6: Menaruh logika bisnis di Composable

- *Kenapa bahaya*: `@Composable` bisa dipanggil ulang kapan saja oleh Compose (rekomposisi).
  Memanggil API dari sana bisa menembakkan request berkali-kali tanpa Anda sadari.
- *Solusi elegan kita*: Composable hanya mengirim event; ViewModel yang mengeksekusi di
  `scope.launch`.

### Jebakan 7: Menulis "parser pintar" untuk data yang bentuknya tidak dijamin

- *Kenapa bahaya*: Lihat Blok G. Parser yang salah **tidak melempar error** — dia menghasilkan
  angka yang salah dengan penuh percaya diri.
- *Solusi elegan kita*: Data apa adanya + jalur koreksi manual yang eksplisit.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### Strategi Berlapis

| Lapisan | Jenis Test | Kenapa begitu |
|---|---|---|
| Domain (`BulkWorkOrder`) | **Unit test murni** | Tanpa DB, tanpa mock — aturan bisnis bisa diuji dalam milidetik |
| Codec | **Round-trip test** | Encode→decode harus menghasilkan objek identik |
| Repository | Integration test (belum ditulis) | Butuh Postgres nyata untuk menguji RLS & partial index |

### Test yang Benar-benar Bermakna

Test yang bagus **mendokumentasikan aturan bisnis**, bukan sekadar menaikkan angka coverage.
Perhatikan penamaannya — `[apa]_[kondisi]_[ekspektasi]`, dibaca seperti kalimat:

```kotlin
@Test
fun `sewing beyond cutting passed output should fail`() {
    val order = workOrder()
        .recordStageProgress(ProductionStage.CUTTING, completedPcs = 300, updatedAt = NOW)

    val error = assertFailsWith<IllegalArgumentException> {
        order.recordStageProgress(ProductionStage.SEWING, completedPcs = 500, updatedAt = NOW)
    }
    assertTrue(error.message!!.contains("melebihi hasil Potong"))
}

@Test
fun `progress recording is cumulative not additive`() {
    val order = workOrder()
        .recordStageProgress(ProductionStage.CUTTING, completedPcs = 200, updatedAt = NOW)
        .recordStageProgress(ProductionStage.CUTTING, completedPcs = 200, updatedAt = NOW)

    assertEquals(200, order.progressFor(ProductionStage.CUTTING).completedPcs)  // bukan 400!
}

@Test
fun `status never walks backwards when an earlier stage is corrected`() {
    val order = workOrder()
        .recordStageProgress(ProductionStage.CUTTING, completedPcs = 1000, updatedAt = NOW)
        .recordStageProgress(ProductionStage.SEWING, completedPcs = 800, updatedAt = NOW)
    assertEquals(BulkProductionStatus.SEWING, order.status)

    val corrected = order.recordStageProgress(ProductionStage.CUTTING, completedPcs = 950, updatedAt = NOW)
    assertEquals(BulkProductionStatus.SEWING, corrected.status)   // tidak mundur ke CUTTING
}
```

Pasangan test ini yang paling saya sukai, karena membuktikan kenapa `largestStageBacklog` ada:

```kotlin
@Test
fun `pieces piling up in front of one stage reports bottleneck`() {
    val order = workOrder()
        .recordStageProgress(ProductionStage.CUTTING, completedPcs = 900, updatedAt = NOW)
        .recordStageProgress(ProductionStage.SEWING, completedPcs = 100, updatedAt = NOW)

    assertEquals(800, order.largestStageBacklog)
    assertEquals(FlowHealthStatus.BOTTLENECK, order.healthStatus)
}

@Test
fun `evenly flowing order is healthy even with the same total wip`() {
    val order = workOrder()
        .recordStageProgress(ProductionStage.CUTTING, completedPcs = 1000, updatedAt = NOW)
        .recordStageProgress(ProductionStage.SEWING, completedPcs = 700, updatedAt = NOW)
        .recordStageProgress(ProductionStage.FINISHING, completedPcs = 500, updatedAt = NOW)

    assertEquals(FlowHealthStatus.HEALTHY, order.healthStatus)
}
```

Dan test codec yang menjaga dari WIP hantu:

```kotlin
@Test
fun `missing stage in stored json should not create phantom wip`() {
    val partial = """[{"stage":"CUTTING","completedPcs":100,"reworkPcs":0,"rejectPcs":0}]"""

    val restored = BulkWorkOrderCodec.decodeStageProgress(partial)

    assertEquals(3, restored.size)   // dua tahap hilang diisi otomatis
    assertNotNull(restored.firstOrNull { it.stage == ProductionStage.FINISHING })
}
```

### Hasil Verifikasi Nyata

```
core:jvmTest (22 test domain + codec)     ✅ LULUS
core:compileKotlinJvm                     ✅ BERSIH
server:compileKotlin                      ✅ BERSIH
server:compileTestKotlin                  ✅ BERSIH
app:shared:compileKotlinJvm               ✅ BERSIH
app:shared:compileKotlinWasmJs            ✅ BERSIH
app:shared:compileKotlinJs                ✅ BERSIH
app:shared:jvmTest                        ✅ LULUS
app:shared:assembleAndroidMain            ❌ GAGAL — MockupCropDialog.kt
```

> ⚠️ **Catatan jujur tentang kegagalan Android.** Target Android gagal, tapi **bukan karena modul
> ini**. Seluruh errornya (27 baris) berasal dari satu file: `presentation/deal/components/MockupCropDialog.kt`,
> yang memakai `org.jetbrains.skia.*` di `commonMain` — API yang tidak ada di Android. File itu
> sudah rusak sebelum modul ini dimulai dan bukan bagian dari pekerjaan ini.
>
> **Yang juga belum dilakukan**: Definition of Done design system mewajibkan *"dijalankan dan
> dilihat dengan mata"*. Modul ini **belum** melewati tahap itu — bug layout seperti teks pecah
> per huruf tidak akan tertangkap satu test pun. Jangan anggap modul ini selesai 100% sampai
> layarnya benar-benar dibuka di browser.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1 — Validasi alokasi vs size breakdown.**
  Saat ini `allocateLine` hanya memeriksa total pcs. Tambahkan kemampuan mengalokasikan **per
  ukuran** (misal Line 1 pegang semua ukuran S dan M). Pertanyaan yang harus kamu jawab dulu:
  apakah ini pantas jadi field baru di `MachineLineAllocation`, atau justru tanda bahwa alokasi
  perlu jadi entity-nya sendiri? Petunjuk: tanyakan tiga pertanyaan di tabel Blok E.

- [ ] **Tantangan 2 — Integration test untuk partial index.**
  Tulis test yang membuktikan `idx_bulk_work_orders_one_active_per_deal` benar-benar menolak SPK
  kedua untuk deal yang sama, **lalu** membuktikan SPK baru bisa terbit setelah yang pertama
  di-`CANCELLED`. Test ini butuh Postgres nyata — lihat pola di
  `server/src/test/.../PostgresTenantEntitlementRepositoryIntegrationTest.kt`.

- [ ] **Tantangan 3 — Stagnasi berbasis waktu.**
  Di Blok D saya menolak menambahkan `CRITICAL` untuk "macet terlalu lama" karena entity tidak
  punya konsep jam kerja pabrik. Rancang solusinya: di mana jam kerja pabrik seharusnya tinggal
  (`core`? tabel konfigurasi tenant?), dan bagaimana `healthStatus` bisa memakainya **tanpa**
  membuat `BulkWorkOrder` bergantung pada `Clock`. Petunjuk: lihat bagaimana
  `GetProductionTelemetryUseCase` menerima `clock` sebagai parameter.

- [ ] **Tantangan 4 — Rework loop (Kontrak 5).**
  Aturan modul mewajibkan jalur penanganan cacat dengan pembedaan `DefectLiability`
  (`FACTORY_WORKMANSHIP` / `CLIENT_SUPPLIED_DEFECT` / `SUPPLIER_VENDOR_DEFECT`). Modul ini baru
  mencatat `reworkPcs` dan `rejectPcs` sebagai angka **tanpa atribusi tanggung jawab**. Rancang
  bagaimana atribusi itu masuk — dan pikirkan konsekuensi biayanya: cacat serat kain titipan
  buyer **tidak boleh** dibebankan ke penjahit.

---

## 📚 Peta File yang Dibuat/Diubah

```
core/src/commonMain/kotlin/com/eventverse/app/
├── domain/production/
│   ├── BulkWorkOrderValueObjects.kt          [BARU]  VO + enum
│   ├── BulkWorkOrder.kt                      [BARU]  Aggregate root
│   ├── BulkWorkOrderRepository.kt            [BARU]  Kontrak persistence
│   ├── BulkWorkOrderDomainEvents.kt          [BARU]  Domain events
│   └── usecases/
│       ├── LaunchBulkWorkOrderFromDealUseCase.kt   [BARU]
│       ├── GetBulkWorkOrderListUseCase.kt          [BARU]
│       ├── AllocateProductionLineUseCase.kt        [BARU]
│       ├── RecordProductionProgressUseCase.kt      [BARU]
│       └── GetProductionTelemetryUseCase.kt        [BARU]
└── shared/production/BulkWorkOrderCodec.kt   [BARU]

core/src/commonTest/kotlin/com/eventverse/app/domain/production/
├── BulkWorkOrderTest.kt                      [BARU]  18 test
└── BulkWorkOrderCodecTest.kt                 [BARU]  4 test

server/src/main/
├── resources/db/migration/V42__create_production_mrp_schema.sql   [BARU]
└── kotlin/com/eventverse/app/
    ├── infrastructure/tables/ProductionTables.kt                  [BARU]
    ├── infrastructure/PostgresBulkWorkOrderRepository.kt          [BARU]
    ├── routes/ProductionRoutes.kt                                 [BARU]
    └── Application.kt                                             [UBAH] wiring

app/shared/src/commonMain/kotlin/com/eventverse/app/
├── infrastructure/api/ProductionApiClient.kt                      [BARU]
├── presentation/production/
│   ├── ProductionUiState.kt                                       [BARU]
│   ├── ProductionViewModel.kt                                     [BARU]
│   ├── ProductionWorkspaceScreen.kt                               [BARU]
│   └── components/
│       ├── ProductionStatusVisuals.kt                             [BARU]
│       ├── BulkWorkOrderDetailPane.kt                             [BARU]
│       ├── AllocateLineDialog.kt                                  [BARU]
│       └── RecordProgressDialog.kt                                [BARU]
├── presentation/workspace/ModuleWorkspaceScreen.kt                [UBAH] routing modul
└── presentation/deal/
    ├── DealUiState.kt                                             [UBAH] event baru
    ├── DealViewModel.kt                                           [UBAH] handler
    └── components/DealDetailDialog.kt                             [UBAH] tombol
```

---

## 🔗 Endpoint yang Tersedia

| Method | Path | Fungsi |
|---|---|---|
| `GET` | `/api/tenant/production/work-orders` | Daftar SPK massal (filter `?status=`) |
| `GET` | `/api/tenant/production/work-orders/telemetry` | Telemetri node untuk kanvas Alur Pabrik |
| `GET` | `/api/tenant/production/work-orders/{id}` | Detail satu SPK |
| `POST` | `/api/tenant/production/work-orders/launch-from-deal` | Terbitkan SPK dari deal ACC |
| `POST` | `/api/tenant/production/work-orders/{id}/lines` | Alokasi lini mesin |
| `DELETE` | `/api/tenant/production/work-orders/{id}/lines/{lineName}` | Hapus alokasi lini |
| `POST` | `/api/tenant/production/work-orders/{id}/progress` | Catat realisasi tahap (kumulatif) |

> 💡 Perhatikan urutan pendaftaran rute: `/telemetry` **didaftarkan sebelum** `/{id}`. Kalau
> dibalik, Ktor akan menangkap `"telemetry"` sebagai ID SPK dan mengembalikan 404 yang
> membingungkan.

---

## ✅ Checklist Definition of Done

| Item | Status |
|---|---|
| `ModuleArchetype` ditetapkan | ✅ Sudah ada di `OperationalModuleCatalog.ProductionMrpModule` |
| Tipe Input/Output Port terdokumentasi | ✅ `CuttingOrderWithFabric` → `CutPiecesBundle` |
| Parameter kustom per tenant tanpa ubah `core` | ✅ `stockOwnership` per SPK |
| Kain titipan ditandai `CONSIGNED_CLIENT_MATERIAL` | ✅ + peringatan rekonsiliasi di UI |
| `scopeCapability` benar | ✅ `GLOBAL_ONLY` — jadwal mesin data kolektif |
| Telemetri (`wipPieces`, `cycleTimeHours`, `healthStatus`) | ✅ Endpoint `/telemetry` |
| Nol literal `Color(0xFF……)` baru | ✅ Terverifikasi via `grep` |
| Nol `RoundedCornerShape(N.dp)` telanjang | ✅ Terverifikasi via `grep` |
| Nol `Modifier.shadow()` | ✅ Terverifikasi via `grep` |
| Nol `Card`/`Button` Material mentah | ✅ Terverifikasi via `grep` |
| Kompilasi 5 target | ⚠️ 4/5 — Android gagal karena file pre-existing |
| **Dijalankan & dilihat dengan mata** | ❌ **BELUM** |
| Dokumentasi teaching | ✅ Dokumen ini |
| **Rework loop `DefectLiability` (Kontrak 5)** | ❌ **BELUM** — lihat Tantangan 4 |

---

*Dokumen ini ditulis sebagai bagian dari aturan §12 `CLAUDE.md`: setiap fitur yang selesai wajib
meninggalkan jejak pengajaran, bukan hanya kode.*
