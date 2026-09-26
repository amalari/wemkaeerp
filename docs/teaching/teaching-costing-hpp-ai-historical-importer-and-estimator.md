# 🎓 Modul Pembelajaran: Importer Excel Historis & Estimator Cepat AI pada Modul Costing HPP

> **Level Target**: Junior to Mid Kotlin Multiplatform Developer
> **Topik Utama**: Knowledge-Base-Driven Estimation, AI sebagai Sumber Data (bukan Sumber Kebenaran), Apache POI, Batch Import Idempoten, Multi-Tenant Isolation
> **Prasyarat**: Kotlin dasar, DDD 4-lapis WeMade, Exposed + Flyway, Compose Multiplatform
> **Referensi Rencana**: [`docs/plannings/planning-costing-hpp-ai-historical-importer-and-estimator.md`](../plannings/planning-costing-hpp-ai-historical-importer-and-estimator.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Seorang CS pabrik rajut menerima WhatsApp: *"Mas, kalau cardigan kayak gini 100 pcs berapa?"* — disertai satu foto.

Hari ini alurnya: CS meneruskan ke bagian produksi, produksi mencari file Excel HPP lama yang mirip di folder bersama, membuka tiga-empat file, menebak gramasinya, lalu membalas dua hari kemudian. Kliennya sudah keburu memesan di tempat lain.

Padahal jawabannya **sudah ada** — tersimpan di 100+ berkas Excel di folder `data/excel-hpp/`. Yang tidak ada adalah cara membacanya secara kolektif.

### Analogi Sederhana: Buku Resep vs Timbangan Dapur

Bayangkan koki yang ditanya "berapa tepung untuk kue ini?" sambil ditunjukkan foto kue.

- **Pendekatan salah (rumus geometri)**: mengukur volume kue dari foto, mengalikan dengan densitas adonan. Rumusnya benar secara matematis, hasilnya selalu meleset — karena kue bukan balok.
- **Pendekatan benar (buku resep)**: membuka catatan, mencari tiga kue yang bentuk dan ukurannya paling mirip, melihat berapa tepung yang **benar-benar dipakai** waktu itu, lalu menyesuaikan sedikit.

Modul ini adalah **buku resep**-nya. Variabel paling tidak pasti dalam HPP rajut bukan harga benang (itu sudah pasti, tinggal lihat nota) melainkan **gramasi** — berapa gram benang yang habis untuk satu baju. Gramasi ditebak dari rumus selalu meleset; dibaca dari artikel serupa yang pernah diproduksi, jauh lebih dekat.

---

## 🧭 2. "Start dari Mana?" — Urutan Pengerjaan

```
Step 0: Tentukan unit pengetahuannya      → CostingProductBenchmark (entity)
   ↓
Step 1: Bentuk pertanyaan CS jadi tipe    → QuickEstimateInput (5 parameter awam)
   ↓
Step 2: Tulis mesin estimasinya           → EstimateCostingFromAiDesignUseCase
   ↓
Step 3: Siapkan penyimpanannya            → V36 + Exposed table + repository
   ↓
Step 4: Baca dunia luar yang berantakan   → POI reader + parser heuristik + Gemini
   ↓
Step 5: Jalur impor massal                → CLI + task Gradle
   ↓
Step 6: Buka lewat HTTP                   → 4 route baru
   ↓
Step 7: Antarmuka CS                      → 2 pane Clay + 2 tab baru
```

**Kenapa Step 2 sebelum Step 3?** Karena mesin estimasi yang menentukan bentuk kuerinya, dan bentuk kueri yang menentukan indeksnya. Kalau tabel dirancang lebih dulu, hampir pasti ada kolom yang tidak pernah dibaca dan indeks yang tidak pernah dipakai.

---

## 🔬 3. Pembedahan Kode Blok per Blok

### Step 0 — Kenapa `CostingProductBenchmark` entity terpisah, bukan `CostingSheet`?

Rencana awal menyebutkan: simpan arsip sebagai lembar HPP resmi di `costing_sheets` + `costing_sheet_buckets`.

Itu **ditolak saat implementasi**, dan alasannya penting untuk dipahami:

```kotlin
// CostingSheet punya siklus hidup transaksional:
// DRAFT → CALCULATED → PENDING_APPROVAL → APPROVED
```

Kalau 100 arsip historis dipaksa lewat pintu itu, akibatnya konkret:

1. `countByStatus(PENDING_APPROVAL)` melonjak → telemetri modul menyala merah palsu.
2. Antrean persetujuan manajer terisi 100 dokumen yang tidak perlu disetujui siapa pun.
3. `CostingSheet` mewajibkan `techPackId` — arsip Excel 2019 tidak punya Tech Pack di sistem ini.

> **Pelajaran umum**: kalau sebuah data tidak mengalami *siklus hidup* yang dimiliki agregat yang ada, ia bukan instance agregat itu — sekalipun field-nya mirip. **Fakta historis** dan **dokumen transaksional** adalah dua jenis makhluk yang berbeda.

Rinciannya tidak hilang, hanya pindah tempat:

```kotlin
data class BenchmarkCostLine(val label: String, val amountPerUnit: Money)

data class CostingProductBenchmark(
    // …
    val costBreakdown: List<BenchmarkCostLine> = emptyList(),
    val sourceSheetId: CostingSheetId? = null,   // tetap bisa menunjuk lembar resmi, bila ada
)
```

### Step 1 — Menerjemahkan bahasa CS, bukan bahasa gudang

Perhatikan beda dua desain enum ini:

```kotlin
// ❌ Bahasa gudang — CS tidak bisa menjawabnya di telepon
enum class YarnSpec { ACRYLIC_2_32, COTTON_COMBED_30S, WOOL_BLEND_70_30 }

// ✅ Bahasa klien — inilah yang benar-benar ditanyakan orang
enum class MaterialCharacter(
    val displayName: String,
    val yarnKeyword: String,      // jembatan ke katalog material
    val yarnPriceFactor: Double   // jembatan ke harga
) {
    ADEM_KATUN("Adem & menyerap keringat (katun)", "cotton", 1.00),
    HANGAT_AKRILIK("Hangat & ringan (akrilik)", "acrylic", 0.75),
    // …
}
```

Enum-nya menyimpan **dua jembatan sekaligus**: kata kunci untuk mencari benchmark dan material, serta faktor harga. Jadi satu pilihan awam CS otomatis punya arti teknis di dua sistem yang berbeda.

Hal yang sama untuk ketebalan:

```kotlin
enum class KnitThickness(val displayName: String, val gauge: Int, val weightFactor: Double) {
    TIPIS("Tipis & ringan (musim panas)", 12, 0.72),
    SEDANG("Sedang (paling umum)",         7, 1.00),
    TEBAL("Tebal & berat (chunky)",        5, 1.45)
}
```

`gauge` (jarum per inci) berbanding **terbalik** dengan ketebalan: makin banyak jarum, makin halus dan ringan kainnya. `weightFactor` inilah yang nanti dipakai menormalkan gramasi antar-gauge.

### Step 2 — Mesin estimasi: tiga keputusan yang layak ditiru

#### (a) Kemiripan diberi skor di domain, bukan di SQL

```kotlin
fun similarityTo(category: KnitCategory, gauge: Int?, yarnKeyword: String?): Double {
    var score = 0.0
    score += when {
        this.category == category -> 0.45      // bobot terbesar
        // …
    }
    score += when {
        ownGauge == gauge -> 0.30
        else -> (0.30 - (abs(ownGauge - gauge) * 0.06)).coerceAtLeast(0.0)
    }
    // + kecocokan benang 0.25
}
```

Repository hanya **mempersempit** kandidat (kategori, gauge terdekat, limit). Skor akhir dihitung di entity.

**Kenapa?** Aturan kemiripan adalah aturan bisnis yang akan sering diubah. Kalau ia hidup di `ORDER BY` SQL, setiap perubahan butuh database untuk diuji. Di domain, ia diuji dalam milidetik tanpa Docker.

Bobot kategori paling besar (0.45) karena dua artikel bergramasi mirip tapi siluet berbeda — vest vs dress — punya struktur biaya jahit yang sama sekali lain.

#### (b) Menit mesin diturunkan dari produktivitas, bukan disalin

```kotlin
// ❌ Naif: ambil menit benchmark apa adanya
val minutes = match.benchmark.metrics.knittingMinutes

// ✅ Turunkan dari gram-per-menit, lalu kalikan gramasi yang diestimasi
val gramsPerMinute = /* rata-rata tertimbang dari arsip */
return (weightGrams / gramsPerMinute).roundToInt()
```

Dua artikel di mesin dan gauge yang sama punya **gram/menit** yang mirip; **menit**-nya tidak, karena ukurannya berbeda. Menyalin menit berarti cardigan XL diperkirakan sama cepatnya dengan cardigan S.

#### (c) Hasilnya rentang, dan itu keputusan domain

```kotlin
data class QuickQuotationEstimateResult(
    val hppLow: Money, val hppHigh: Money,
    val suggestedPriceLow: Money, val suggestedPriceHigh: Money,
    // …
)
```

Estimasi bottom-up dari gramasi tebakan punya galat nyata. Kalau UI menampilkan satu angka besar, **itulah** yang akan disalin CS ke WhatsApp, lalu pabrik terkunci pada angka yang belum pernah diverifikasi produksi. Rentang memaksa percakapan tetap terbuka sampai sampling selesai.

Lebarnya rentang mengikuti keyakinan:

| Kondisi arsip | Confidence | Rentang |
|---|---|---|
| ≥3 acuan, kemiripan tertinggi ≥0,70 | `HIGH` | ±10% |
| ada acuan | `MEDIUM` | ±18% |
| arsip kosong | `LOW` | ±30% |

#### (d) Kalimat pagar komersial hidup di domain, bukan di UI

```kotlin
fun toWhatsAppSummary(currencyFormatter: (Money) -> String): String = buildString {
    // …
    append("_Angka di atas adalah estimasi awal berbasis arsip produksi kami. " +
           "Harga final ditetapkan setelah sampel disetujui._")
}
```

Kalimat itu melindungi pabrik secara komersial. Kalau ia ditulis di Composable, integrasi berikutnya (bot WhatsApp, aplikasi mobile) akan mengirim angkanya **tanpa** pagar itu. Di domain, ia ikut ke mana pun hasilnya dipakai.

### Step 4 — Membaca dunia luar yang berantakan

#### Kenapa seluruh sheet diratakan jadi teks?

```kotlin
builder.append("### SHEET: ").append(sheet.sheetName).append('\n')
sheet.forEach { row -> builder.append(row.toTabbedText()).append('\n') }
```

Seratus berkas HPP buatan tangan **tidak pernah** punya tata letak yang sama: gramasi ada di `C7`, `B12`, atau di sheet kedua. Memetakan koordinat = menulis seratus aturan. Teks rata membiarkan parser mencari **labelnya** (`"GRAMASI"`, `"BERAT"`) — yang justru stabil, karena ditulis orang yang sama.

#### Bug seribu kali lipat yang ditemukan test

Ini bagian paling berharga untuk dipelajari. Versi pertama:

```kotlin
val normalised = when {
    lastComma > lastDot -> cleaned.replace(".", "").replace(',', '.')
    lastDot > lastComma -> cleaned.replace(",", "")   // ← bug di sini
    else -> cleaned
}
```

`"Rp 45.000"` → tidak ada koma → cabang kedua → hapus koma (tidak ada) → `"45.000".toDouble()` = **45.0**.

HPP Rp 45.000 tercatat sebagai **Rp 45**. Tidak ada exception, tidak ada log, dan angkanya baru ketahuan salah berbulan-bulan kemudian ketika ada yang membandingkan penawaran dengan arsip.

Perbaikannya: kalau hanya satu jenis pemisah yang muncul, cek **panjang kelompoknya**.

```kotlin
/** `45.000` dan `1.234.567` benar; `45.5` dan `1.2345` tidak. */
private fun String.looksLikeGroupedThousands(separator: Char): Boolean {
    val groups = split(separator)
    if (groups.size < 2) return false
    if (groups.first().trimStart('-').length !in 1..3) return false
    return groups.drop(1).all { it.length == 3 && it.all(Char::isDigit) }
}
```

> **Pelajaran umum**: parsing angka dari dokumen manusia **selalu** butuh test dengan data asli. Bug yang tidak melempar exception adalah bug yang paling mahal.

#### AI dipakai sebagai pengisi celah, bukan penimpa

```kotlin
val heuristic = fallback.parse(extract, mockupImageUrl)
val aiJson = runCatching { requestJson(...) }.getOrNull() ?: return heuristic

return heuristic.copy(
    netWeightGrams = heuristic.netWeightGrams ?: aiJson.double("netWeightGrams"),
    hppPerUnitMinor = heuristic.hppPerUnitMinor ?: aiJson.double("hppPerUnitRupiah")?.toMinorUnits(),
    // …
)
```

Tiga prinsip sekaligus:

1. **Angka yang labelnya jelas terbaca lebih dipercaya daripada tebakan model.** `?:` bukan penimpaan.
2. **Kegagalan AI tidak menghentikan batch.** Rate limit di berkas ke-73 dari 100 tidak boleh membuang 72 yang sudah berhasil.
3. **Hasil AI tetap divalidasi domain.** Ia masuk ke `ParsedHistoricalCosting` yang longgar (semua nullable), lalu `PhysicalMetrics` menolaknya:

```kotlin
require(netWeightGrams < 10_000.0) { "Berat bersih $netWeightGrams gram tidak masuk akal untuk satu pcs" }
```

Model yang berhalusinasi 40.000 gram akan ditolak di sana — dan berkasnya masuk daftar `skipped`, bukan tersimpan diam-diam.

Ada satu konsekuensi desain yang membuat ini berharga: **fitur ini jalan penuh tanpa `GEMINI_API_KEY`**. Parser heuristik bukan cadangan darurat — ia yang membuat impor bisa diuji di laptop developer tanpa jaringan dan tanpa biaya.

### Step 5 — Impor massal yang idempoten

```kotlin
val alreadyImported = benchmarkRepository.findImportedFileNames(tenantId)

records.forEach { record ->
    if (record.sourceFileName in alreadyImported) {
        skipped += SkippedImport(record.sourceFileName, "Sudah pernah diimpor sebelumnya")
        return@forEach
    }
    runCatching { record.toBenchmark(tenantId, now) }
        .onSuccess { imported += it }
        .onFailure { skipped += SkippedImport(record.sourceFileName, it.message ?: "…") }
}
```

Dua keputusan:

- **Satu berkas gagal ≠ batch gagal.** Impor 100 berkas dijalankan sekali oleh manusia. Kalau berkas ke-73 punya sel gramasi kosong dan seluruh batch di-rollback, operator mengulang dari nol tanpa tahu berkas mana yang salah.
- **Idempoten berdasar nama berkas**, dikunci di database:

```sql
CREATE UNIQUE INDEX uq_costing_benchmarks_source_file
    ON costing_product_benchmarks(tenant_id, source_file_name)
    WHERE source_file_name <> '';
```

Impor besar hampir selalu dijalankan dua kali — sekali gagal di tengah, sekali lagi setelah diperbaiki.

### Step 6b — Koefisien rumus per tenant (Kontrak 4)

Versi pertama estimator menyimpan seluruh koefisiennya sebagai `const val`:

```kotlin
private const val YARN_WASTE_RATIO = 0.08
private fun smallRunFactor(quantity: Long) = when {
    quantity < 24L -> 1.35
    // …
}
```

Datanya sudah per-tenant (arsip + rate card), tapi **rumusnya tidak** — dan itu melanggar
Kontrak 4. Dampaknya konkret, bukan teoretis:

| Koefisien | Kenapa tenant lain beda |
|---|---|
| Susut benang 8% | Masuk akal untuk jacquard rumit; makloon rapi 3% ditagih susut yang tak pernah terjadi |
| Penalti run kecil 1,35× | Menghukum pabrik yang justru punya banyak mesin kecil |
| Gramasi baku cardigan 480 g | Angka rajut mesin; pabrik handknit chunky jauh di atasnya |
| Margin 20%, rentang ±10/18/30% | Selera risiko dan komersial tiap pabrik berbeda |

#### Kenapa tidak bikin tabel setelan baru

Proyek ini **sudah punya** tempatnya, dan Kontrak 4 menunjuk ke situ:
`customFormulaParameters` (JSONB pada node pipeline tenant), yang sudah dipakai
`CostingParameterCodec` untuk kalkulasi HPP resmi.

```
default sistem  <  customFormulaParameters (node pipeline, per tenant)
                <  CostingRateCard (per tenant, ber-tanggal-berlaku)
                <  override per lembar
```

Menambah tabel kedua berarti dua tempat menyimpan hal yang sama — dan cepat atau lambat
keduanya berbeda isi. Hasilnya: **nol migrasi**, dan provenance ikut gratis.

```kotlin
data class QuickEstimateTuning(
    val yarnWasteRatio: Double = 0.08,
    val smallRunTiers: List<SmallRunTier> = listOf(SmallRunTier(24L, 1.35), …),
    val fallbackWeightGramsByCategory: Map<KnitCategory, Double> = DEFAULT_FALLBACK_WEIGHTS,
    // …
)
```

Admin pabrik menyetelnya sebagai string di node HPP:

```
estimatorYarnWastePercent   = 3
estimatorSmallRunTiers      = 20:1.5,40:1.25
estimatorFallbackGrams      = CARDIGAN:900,VEST:520
```

#### Tiga keputusan halus di sini

**(a) Salah ketik tidak boleh menggagalkan penawaran.** CS sedang menelepon klien; konfigurasi
pipeline yang belum rapi bukan alasan untuk gagal memberi harga.

```kotlin
// 'delapan persen' → peringatan, kunci itu jatuh ke default, estimasi tetap keluar
resolved.warnings  // ["Nilai 'estimatorYarnWastePercent' = 'delapan' bukan angka; memakai default."]
```

Peringatan itu digabung ke `result.warnings` di route, jadi muncul **di layar CS** — bukan cuma
di log server yang tidak pernah dibaca siapa pun.

**(b) Ambang keyakinan ikut bergerak bersama bobotnya.** Ini jebakan yang mudah terlewat:

```kotlin
// ❌ Ambang tetap: tenant yang mengecilkan bobot benang tidak akan pernah dapat HIGH lagi
matches.size >= 3 && top >= 0.70

// ✅ Ambang relatif terhadap skor maksimum yang mungkin bagi tenant itu
val highThreshold = (tuning.categoryWeight + tuning.gaugeWeight + tuning.yarnWeight) * 0.70
```

**(c) Gramasi baku sebaiknya datang dari arsip tenant itu sendiri.** Tabel hardcode hanya relevan
saat tenant belum punya data. Begitu arsipnya terisi, median miliknya sendiri jauh lebih benar:

```kotlin
val ownSamples = benchmarkRepository.netWeightSamples(tenantId, category)
if (ownSamples.size >= tuning.archiveFallbackMinSamples) {
    return normaliseThickness(ownSamples.median(), …)   // median, bukan rata-rata
}
```

Median dipilih karena satu artikel salah input (40.000 g) tidak menggeser hasilnya — sementara
rata-rata akan hancur karenanya.

Repository mengembalikan **daftar gramasi mentah**, bukan median, karena "berapa sampel minimal
sebelum median boleh dipercaya" adalah kebijakan bisnis yang bisa disetel per tenant. Kalau
median dihitung di SQL, aturan itu pindah ke database dan tidak bisa diuji tanpa database.

---

### Step 7 — Dua jebakan UI yang layak dicatat

#### (a) Tab yang tidak butuh lembar HPP

Struktur lama:

```kotlin
if (sheet == null) {
    Text("Pilih atau buat lembar HPP terlebih dahulu")
} else {
    when (state.activeWorkbenchTab) { /* … */ }
}
```

Kalau dua tab baru dimasukkan begitu saja, CS di tenant yang belum punya satu pun lembar HPP hanya melihat pesan itu — padahal **estimator justru pintu masuk sebelum lembar HPP ada**.

```kotlin
val isSheetIndependent: Boolean
    get() = this == QUICK_ESTIMATOR || this == HISTORICAL_BENCHMARKS
```

…dan tab mandiri dirender lebih dulu, sebelum pemeriksaan `sheet == null`.

#### (b) `Row` → `FlowRow` untuk bilah tab

Tab bertambah dari 5 menjadi 7. `Row` **tidak** membungkus; ia diam-diam memotong tab terakhir di luar layar pada lebar 1280dp. Ini persis jenis bug yang tidak tertangkap test apa pun — hanya terlihat kalau aplikasinya dijalankan dan dilihat dengan mata (design-system rules §7).

---

## 🏗️ 4. Teknologi & Alasan Pemilihannya

| Keputusan | Alternatif yang ditolak | Alasan |
|---|---|---|
| `java.net.http.HttpClient` untuk Gemini | Ktor Client | Modul server belum memuat Ktor Client sama sekali; menambah engine + TLS baru demi satu `POST` JSON tidak sepadan |
| Apache POI `poi-ooxml` | Parsing XML manual | `.xlsx` adalah ZIP berisi XML dengan gambar tertanam; POI juga menyediakan `allPictures` untuk mockup |
| Body mentah + query param untuk upload | Multipart | Mengikuti konvensi upload PO di `DealRoutes`: satu request, tanpa plugin server tambahan, tanpa merakit multipart di 5 target KMP |
| Task Gradle `JavaExec` terpisah | `application { mainClass }` | Supaya `./gradlew :server:run` tetap menjalankan server Ktor, bukan importer |
| Nama berkas gambar = hash SHA-256 isinya | Nama asli berkas | Dua Excel dengan mockup sama tidak menggandakan gambar; nama berkas Indonesia berspasi dan berkurung tidak bocor ke URL |
| `QuickEstimateRates` sebagai VO terpisah | Baca `CostingRateCard` langsung di use case | Kontrak 4 (`module-integration-rules`): rumus costing tidak boleh mengunci tenant pada satu sumber tarif |

---

## 🕳️ 5. Jebakan Umum yang Dihindari

1. **Anemic AI trust** — memakai balikan model sebagai kebenaran. Di sini balikan AI diperlakukan setara sel Excel: data mentah yang harus lolos invariant domain.
2. **Menimpa isian manusia** — jumlah kancing hasil pembacaan AI hanya mengisi kolom yang **masih kosong**. Menimpa isian CS berarti angka penawaran berubah diam-diam setelah ia mengetiknya sendiri.
3. **Kebocoran lintas tenant** — `findSimilar` melebar ke seluruh arsip bila kategori kosong, tapi **selalu** dalam `tenant_id` yang sama. Ada test khusus untuk ini: tenant B tidak boleh menyimpulkan gramasi pesaingnya dari rentang harga yang dikembalikan.
4. **Menggantung alih-alih gagal** — ditemukan saat menulis test rute: `/estimate-quick` merakit tarif dari katalog material, dan tanpa repository yang disuntikkan ia jatuh ke Postgres default lalu **menggantung** menunggu koneksi yang tidak ada. Test rute apa pun yang menyentuh rute ini wajib menyuntikkan `materialItemRepository` dan `materialPriceRepository`.
5. **Baris spesifikasi terbaca sebagai biaya** — ditemukan hanya karena impor dijalankan sungguhan ke database lalu isinya dilihat: `MENIT RAJUT 97` lolos filter kata kunci lewat kata "rajut" dan tersimpan sebagai biaya **Rp 97**; `KANCING 7` jadi **Rp 7**. Angka receh seperti itu tidak pernah terlihat salah sekilas. Saringannya sekarang tiga lapis: buang baris total, buang baris spesifikasi, buang nominal di bawah Rp 100.
6. **Run kecil dihargai seperti run besar** — 12 pcs membayar biaya setting mesin yang sama dengan 500 pcs. Tier `smallRunFactor` diterapkan hanya pada biaya non-material; harga benang per kg tidak berubah karena ordernya sedikit.

---

## ✅ 6. Verifikasi

```bash
# Domain & use case — estimator, importer, tuning per tenant (24 test)
./gradlew :core:jvmTest --tests "com.eventverse.app.domain.costing.*"

# Parser Excel, angka Indonesia, round-trip POI .xlsx nyata (8 test)
./gradlew :server:test --tests "com.eventverse.app.services.*"

# Rute + isolasi tenant (6 test)
./gradlew :server:test --tests "com.eventverse.app.CostingBenchmarkApiTest"

# Setelan per tenant benar-benar sampai dari pipeline ke angka HTTP (4 test)
./gradlew :server:test --tests "com.eventverse.app.CostingEstimatorTuningApiTest"

# PostgreSQL sungguhan: DECIMAL, JSONB, indeks unik parsial, RLS (7 test)
#   butuh docker-compose DB hidup + kredensial dari .env
set -a && . ./.env && set +a
./gradlew :server:test --tests "*PostgresCostingBenchmarkRepositoryIntegrationTest"

# Kompilasi lintas target
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain
```

> **Catatan menjalankan test yang menyentuh database**: kredensial hidup di `.env`, dan JVM test
> Gradle **tidak** membacanya otomatis. Tanpa `set -a && . ./.env`, seluruh `Postgres*IntegrationTest`
> gagal dengan `password authentication failed` — yang mudah disalahartikan sebagai kode yang rusak.

Impor massal:

```bash
export GEMINI_API_KEY=...            # opsional
./gradlew :server:importHistoricalCosting \
    --args="--dir=data/excel-hpp --tenant=<tenantId> --tenant-slug=<slug>"
```

Tanpa kunci API, perintah yang sama tetap berjalan memakai parser heuristik.

---

## 📌 7. Ringkasan Satu Paragraf

Modul ini mengubah 100+ berkas Excel yang tergeletak di folder bersama menjadi **basis pengetahuan yang bisa dikueri**, lalu memakainya untuk menjawab pertanyaan harga dalam hitungan detik. Empat gagasan arsitektural yang menopangnya: (1) fakta historis bukan dokumen transaksional — ia butuh agregatnya sendiri; (2) AI adalah **sumber data** yang divalidasi domain, bukan sumber kebenaran, sehingga seluruh fitur tetap jalan tanpa kunci API; (3) estimasi dari data tak pasti wajib disajikan sebagai **rentang** beserta keyakinannya, karena satu angka di layar akan diperlakukan sebagai janji; dan (4) multi-tenant bukan hanya soal memisahkan *data* — **rumusnya** juga harus bisa berbeda, karena pabrik rajut mesin dan pabrik handknit chunky punya susut benang, penalti run kecil, dan gramasi baku yang tidak ada hubungannya satu sama lain.
