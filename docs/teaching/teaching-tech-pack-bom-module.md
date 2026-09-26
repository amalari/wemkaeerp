# 🎓 Modul Pembelajaran: Implementasi End-to-End Modul TECH_PACK_BOM (Product Engineering)

> **Level Target**: Junior to Mid Fullstack Kotlin Multiplatform Developer  
> **Topik Utama**: Domain-Driven Design, Aggregate Root & Immutability, Exact Rational/Penny Arithmetic (`Money`, `Quantity`, `Ratio`), Port Compatibility (Sampling → Tech Pack → Costing), Multi-Tenant PostgreSQL & Anti-N+1 Batching, Cross-Module RBAC Guard, Compose Multiplatform Claymorphism.  
> **Prasyarat**: Dasar Kotlin Multiplatform, SQL & Flyway Migrations, Pemahaman Dasar DDD & MVI Pattern di Compose.  
> **Referensi Task**: Modul `TECH_PACK_BOM` — Spesifikasi BOM & Tech Pack Garmen Rajut

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata di Industri Garmen
Bayangkan sebuah pabrik garmen rajut (*knitwear*) yang memproduksi 10.000 potong kardigan rajut untuk klien ritel besar.
Sebelum kain dipotong dan benang dipintal massal di lantai pabrik, bagian teknik (*Product Engineering*) harus membuat resep teknis yang sangat presisi:
1. **Berapa gram benang katun dan akrilik per potong pakaian?** Jika salah hitung 5 gram saja, pada 10.000 pcs pabrik akan tekor 50 kg benang (jutaan rupiah).
2. **Berapa persen toleransi susut atau buangan (*waste allowance*)?** Mesin rajut dan obras selalu menghasilkan potongan sisa benang dan limbah kain.
3. **Berapa lama waktu kerja (SAM - *Standard Allowed Minutes*)?** Jika menjahit kerah butuh 3 menit dan obras samping butuh 5 menit, berapa total menit tenaga kerja yang harus dibayar?
4. **Siapa pemilik benang?** Apakah benang dibeli sendiri oleh pabrik (*Owned Asset*), ataukah benang mewah kiriman dari klien (*Consigned Material*) yang tidak boleh dihitung sebagai aset finansial pabrik di neraca?

Jika developer pemula membuat modul ini hanya sebagai "tabel CRUD sederhana", malapetaka berikut akan terjadi:
- **Floating-point drift**: Menjumlahkan `0.1 + 0.2` menghasilkan `0.30000000000000004`. Di level 100.000 potong baju, uang perusahaan hilang tanpa jejak audit.
- **Kebocoran Batas Konteks (Context Bleed)**: Menghitung margin komersial, biaya listrik pabrik, dan gaji satpam di dalam modul Tech Pack. Padahal Tech Pack **hanya** bertugas menghitung biaya bahan baku murni (*Material Cost Preview*); margin dan HPP komprehensif adalah hak prerogatif modul akuntansi/costing (`COSTING_HPP`).
- **Kain Titipan Masuk Aset**: Pabrik menagih biaya benang titipan ke klien, padahal klien yang menyediakan benang tersebut.
- **N+1 Query Hell**: Ketika membuka daftar 50 style baju, backend memanggil database 50 kali untuk BOM, 50 kali untuk operasi kerja, dan 50 kali untuk ukuran, membuat server *down* saat musim produksi puncak.

### Analogi Sederhana: Dapur Restoran Bintang Lima
- **Sample Order (SPK Sample)** = Tahap juru masak mencicipi dan meracik satu porsi hidangan percobaan untuk disetujui *food critic*.
- **Tech Pack & BOM** = **Buku Resep Standar Pabrik**. Di sini dicatat gramasi pasti terigu, mentega, menit memanggang di oven, serta faktor porsi (Mini, Regular, Jumbo). Buku resep ini tidak menentukan harga jual menu di buku kasir—ia hanya mencatat bahan dan cara memasak.
- **Costing HPP** = Manajer keuangan restoran yang menghitung biaya sewa gedung, gas elpiji, gaji koki, dan margin laba bersih untuk menentukan harga jual menu ke pelanggan.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun fitur fullstack sebesar ini dari layar kosong, **JANGAN PERNAH** langsung mengetik `@Composable` atau membuat tabel database. Ikuti urutan langkah DDD baku:

```
Step 0: Kontrak Port & Primitif Eksak (core/domain/common, core/domain/contracts)
   ↓
Step 1: Value Objects & Aggregate Root (core/domain/techpack)
   ↓
Step 2: Repository Interface & Domain Use Cases (core/domain/techpack/usecases)
   ↓
Step 3: Network Codecs (core/shared/techpack)
   ↓
Step 4: Database DDL, Flyway Migration & Exposed Tables (server/resources, server/tables)
   ↓
Step 5: PostgreSQL Repository & Anti-N+1 Batching (server/infrastructure)
   ↓
Step 6: Ktor Server REST API & Cross-Module RBAC (server/routes)
   ↓
Step 7: HTTP Client Remote Data Source (app/shared/infrastructure/api)
   ↓
Step 8: Presentation State & MVI ViewModel (app/shared/presentation/techpack)
   ↓
Step 9: Responsive Claymorphism UI & Dialogs (app/shared/presentation/techpack/components)
```

### Mengapa Urutan Ini Wajib?
1. **Core Domain adalah Jantung Sistem**: Bebas dari framework apa pun (Ktor, Exposed, Compose, Android, JVM). Jika database berganti dari PostgreSQL ke SQLite atau UI berganti dari Compose ke WebAssembly, seluruh aturan matematika garmen tidak berubah satu baris pun.
2. **Tipe Data Menjamin Kebenaran Sebelum Kompilasi**: Jika `Money` dan `Quantity` sudah immutable dan menolak nilai negatif sejak di konstruktor, kamu tidak perlu menulis 50 `if (qty < 0)` di lapisan Controller atau UI.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Mari kita bedah kode kritis di setiap lapisan arsitektur.

### Blok A: Primitif Aritmetika Eksak Tanpa Float (`Quantity`, `Ratio`, `Money`)

Di `core/src/commonMain/kotlin/com/eventverse/app/domain/common/`:

```kotlin
// Exact rational factor numerator / denominator to prevent floating-point drift
data class Ratio(val numerator: Long, val denominator: Long) : Comparable<Ratio> {
    init {
        require(denominator != 0L) { "Denominator tidak boleh 0" }
    }

    fun applyTo(value: Long, rounding: Rounding = Rounding.HALF_UP): Long {
        if (value == 0L || numerator == 0L) return 0L
        val effectiveNumerator = value * numerator
        return divideWithRounding(effectiveNumerator, denominator, rounding)
    }

    operator fun plus(other: Ratio): Ratio {
        val commonDenom = denominator * other.denominator
        val num = (numerator * other.denominator) + (other.numerator * denominator)
        return Ratio(num, commonDenom)
    }
}
```

**Mental Model & Penjelasan**:
- Kita menyimpan rasio dan persentase buangan (*waste allowance*) bukan sebagai `Double = 0.05`, melainkan pecahan eksak `Ratio(5, 100)`.
- Mengapa? Karena perkalian pecahan `(value * numerator) / denominator` mengalikan bilangan bulat terlebih dahulu, lalu membagi dengan strategi *rounding* terstandarisasi (`HALF_UP`). Hal ini mencegah akumulasi selisih pembulatan (*rounding drift*) pada kalkulasi ribuan garmen.

---

### Blok B: Aggregate Root `TechPack` & Aturan Mutasi Domain

Di `core/src/commonMain/kotlin/com/eventverse/app/domain/techpack/TechPack.kt`:

```kotlin
data class TechPack(
    val id: TechPackId,
    val tenantId: String,
    val styleCode: StyleCode,
    val styleName: String,
    val clientName: String,
    val sourceSpkNumber: String,
    val samplingOrderId: String?,
    val version: Int = 1,
    val status: TechPackStatus = TechPackStatus.DRAFT,
    val bomLines: List<BomLine> = emptyList(),
    val laborOperations: List<LaborOperation> = emptyList(),
    val sizeYieldFactors: List<SizeYieldFactor> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(version >= 1) { "Versi harus >= 1" }
        require(styleName.isNotBlank()) { "Nama style tidak boleh kosong" }
        // Invarian: Garis BOM tidak boleh duplikat kombinasi material + ownership
        val distinctKeys = bomLines.map { "${it.material.displayLabel}::${it.ownership}" }.toSet()
        require(distinctKeys.size == bomLines.size) { "Terdapat duplikasi material dalam satu BOM" }
    }

    fun replaceLines(newLines: List<BomLine>, now: Instant): TechPack {
        check(isEditable) { "Tech Pack berstatus $status tidak dapat dimodifikasi" }
        return copy(bomLines = newLines, updatedAt = now)
    }

    fun release(now: Instant): TechPack {
        check(isEditable) { "Hanya Tech Pack berstatus DRAFT yang dapat dirilis" }
        check(bomLines.isNotEmpty()) { "Tech Pack tidak dapat dirilis tanpa baris BOM" }
        check(laborOperations.isNotEmpty()) { "Tech Pack tidak dapat dirilis tanpa daftar operasi kerja" }
        check(blockingUnresolvedLines.isEmpty()) { 
            "Tidak dapat merilis Tech Pack: masih ada ${blockingUnresolvedLines.size} bahan milik pabrik tanpa referensi Master Data" 
        }
        return copy(status = TechPackStatus.RELEASED, updatedAt = now)
    }
}
```

**Mental Model & Penjelasan**:
- **Immutabilitas Total**: Semua properti adalah `val`. Kita tidak pernah menulis `techPack.status = RELEASED`. Mutasi selalu melalui method bermakna domain (`release()`, `revise()`, `replaceLines()`) yang mengembalikan instans salinan baru (`copy(...)`).
- **Penjaga Invarian**: Konstruktor `init` dan method mutasi secara otomatis menolak status invalid. Contoh: mustahil merilis Tech Pack jika ada bahan baku pabrik yang belum terdaftar di Master Data (`blockingUnresolvedLines.isEmpty()`).

---

### Blok C: Database & Pencegahan Query N+1 di PostgreSQL

Di `server/src/main/kotlin/com/eventverse/app/infrastructure/PostgresTechPackRepository.kt`:

```kotlin
override suspend fun search(tenantSlug: String, query: TechPackQuery): TechPackPage = queryExecutor.transaction {
    // 1. Ambil baris header secara terpaginasi
    val headerRows = TechPacksTable
        .selectAll()
        .where { (TechPacksTable.tenantId eq tenantSlug) and ... }
        .orderBy(TechPacksTable.updatedAt to SortOrder.DESC)
        .limit(query.pageSize)
        .offset(offset)
        .toList()

    if (headerRows.isEmpty()) return@transaction TechPackPage.EMPTY

    val techPackIds = headerRows.map { it[TechPacksTable.id].value }

    // 2. Anti-N+1: Ambil SEMUA child table dalam 3 query batch berbasis `IN (techPackIds)`
    val bomByParent = TechPackBomLinesTable
        .selectAll()
        .where { (TechPackBomLinesTable.tenantId eq tenantSlug) and (TechPackBomLinesTable.techPackId inList techPackIds) }
        .groupBy { it[TechPackBomLinesTable.techPackId].value }

    val laborByParent = TechPackLaborOperationsTable
        .selectAll()
        .where { (TechPackLaborOperationsTable.tenantId eq tenantSlug) and (TechPackLaborOperationsTable.techPackId inList techPackIds) }
        .groupBy { it[TechPackLaborOperationsTable.techPackId].value }

    val sizeByParent = TechPackSizeYieldsTable
        .selectAll()
        .where { (TechPackSizeYieldsTable.tenantId eq tenantSlug) and (TechPackSizeYieldsTable.techPackId inList techPackIds) }
        .groupBy { it[TechPackSizeYieldsTable.techPackId].value }

    // 3. Rakit aggregate di memori secara instan
    val items = headerRows.map { row ->
        val id = row[TechPacksTable.id].value
        row.toTechPack(
            bomLines = bomByParent[id]?.map { it.toBomLine() } ?: emptyList(),
            laborOperations = laborByParent[id]?.map { it.toLaborOperation() } ?: emptyList(),
            sizeYieldFactors = sizeByParent[id]?.map { it.toSizeYieldFactor() } ?: emptyList()
        )
    }
    TechPackPage(items, totalCount, query.page, query.pageSize)
}
```

**Mengapa ini krusial?**
Jika developer menulis loop `for (header in headerRows) { header.bom = db.getBom(header.id) }`, satu halaman dengan 50 item akan memicu **151 query database** (1 header + 50 bom + 50 labor + 50 size). Dengan pola batching `inList`, hanya **4 query** yang dieksekusi terlepas dari berapa banyak item yang diambil!

---

### Blok D: Cross-Module RBAC Guard di Ktor REST API

Di `server/src/main/kotlin/com/eventverse/app/routes/TechPackRoutes.kt`:

```kotlin
get("/tech-packs/{id}/cost-preview") {
    val authContext = call.authContextOrNull() ?: return@get call.respond(HttpStatusCode.Unauthorized)
    
    // Perlindungan Wewenang Lintas Modul:
    // Tech Pack boleh dilihat staff teknik, TETAPI tarif harga bahan baku milik Master Data.
    // Jika user tidak memiliki izin baca MASTER_DATA, sembunyikan tarif harga!
    val masterDataAccess = authContext.resolveAccess(BusinessModule.MASTER_DATA)
    if (!masterDataAccess.level.canRead) {
        return@get call.respond(
            HttpStatusCode.Forbidden, 
            mapOf("error" to "Wewenang ditolak: Anda tidak memiliki akses untuk melihat tarif harga Master Data.")
        )
    }

    val costPreview = previewMaterialCostUseCase(tenantSlug, techPackId, orderQty).getOrThrow()
    call.respond(HttpStatusCode.OK, BomCostPreviewCodec.encode(costPreview))
}
```

**Mental Model**:
Seorang *pattern maker* (tukang pola) berhak menyusun resep benang, tapi perusahaan tidak ingin tukang pola mengintip harga kontrak rahasia dengan supplier benang. Dengan memeriksa `authContext.resolveAccess(BusinessModule.MASTER_DATA)`, sistem menjamin integritas kerahasiaan finansial tanpa merusak fungsionalitas penyusunan BOM.

---

### Blok E: Claymorphism Design System di Presentation Layer (`app/shared`)

Di `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/techpack/components/TechPackDesktopWorkbench.kt`:

```kotlin
// Penggunaan token semantik Claymorphism murni (Tanpa raw Color literal, Tanpa Modifier.shadow)
Box(
    modifier = Modifier
        .weight(1f)
        .clickable { onEvent(TechPackUiEvent.SelectWorkbenchTab(tab)) }
        .claySurface(
            shape = ClayShapes.Pill,
            background = if (isSelected) WeMadeColors.Primary else WeMadeColors.Surface,
            outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
            offset = if (isSelected) ClayOffset.Small else ClayOffset.Flat,
            borderWidth = ClayBorder.Hairline
        )
        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
) {
    Text(
        text = tab.displayName,
        fontSize = 12.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        color = if (isSelected) Color.White else WeMadeColors.OnSurface
    )
}
```

**Pelajaran Utama Desain Clay**:
1. **Nol Literal Hex**: Tidak boleh ada `Color(0xFF2563EB)`. Gunakan `WeMadeColors.Primary`.
2. **Hard Shadow Tanpa Blur**: Neo-Brutalism menggunakan bayangan offset tegas (`ClayOffset.Small = 4.dp`, `ClayOffset.Flat = 0.dp`), bukan bayangan kabur Material Design (`Modifier.shadow`).
3. **Pembedaan State**: State aktif dibedakan lewat perpindahan warna latar dan kedalaman offset, bukan mengubah ketebalan garis outline.

---

## 🔬 4. Technology & Approach: "The Why"

| Keputusan Arsitektur | Teknologi / Pola yang Dipilih | Mengapa Bukan Alternatifnya? |
|---|---|---|
| **Aritmetika Angka** | `Money`, `Quantity`, `Ratio` (Fixed-point micros & minor units) | **Mengapa bukan `Double`/`Float`?** `Double` memiliki floating-point error yang terakumulasi. Mengapa bukan `BigDecimal`? `BigDecimal` tidak seragam di Kotlin Multiplatform (JS/Wasm tidak punya `java.math.BigDecimal`). |
| **Pemisahan Modul** | Boundary ketat: `BomCostPreview` vs `CostingCalculationResult` | **Mengapa Tech Pack tidak langsung hitung HPP garmen?** Karena Tech Pack hanya tahu resep fisik. Biaya overhead pabrik, penyusutan jarum, margin keuntungan, dan diskon klien adalah ranah modul Costing & Keuangan. |
| **Penanganan Titipan Klien** | `StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL` bernilai Rp 0 di neraca, dengan pengungkapan nilai nosional (*disclosure*) | **Mengapa tidak diabaikan saja dari BOM?** Karena bagian gudang dan operator mesin tetap harus tahu berapa meter kain yang harus dipotong, meskipun keuangan tidak mencatatnya sebagai aset pabrik. |
| **Manajemen State UI** | MVI Pattern (`TechPackUiState`, `TechPackUiEvent`) | **Mengapa bukan multi-StateFlow tersebar?** Single state flow memudahkan testing, logging *time-travel debugging*, dan menjamin UI selalu konsisten (*single source of truth*). |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Jebakan Mengubah Tech Pack yang Sudah Dirilis**:
   *Salah*: Mengizinkan user mengedit baris BOM pada Tech Pack berstatus `RELEASED`.
   *Benar*: Begitu dirilis ke lantai produksi, Tech Pack terkunci rapat (*immutable snapshot*). Jika ada perubahan desain, user wajib menekan tombol **"Buat Revisi (v2)"** yang akan mengarsipkan versi lama dan membuat draf versi baru.
2. **Jebakan String-Type untuk ID dan Kode**:
   *Salah*: `val styleCode: String = "   "`
   *Benar*: `val styleCode: StyleCode`. Validasi regex format penamaan style (huruf, angka, dash) ditegakkan di konstruktor Value Object.
3. **Jebakan Smart-Cast Properti Modul Eksternal**:
   *Salah*: `if (lineCost.resolvedPrice != null) lineCost.resolvedPrice.unitPrice.format()` akan memicu compiler error di Kotlin Multiplatform karena properti `val` dari modul terpisah tidak dapat di-smart-cast secara aman.
   *Benar*: Salin ke variabel lokal terlebih dahulu: `val resolved = lineCost.resolvedPrice; if (resolved != null) resolved.unitPrice...`
4. **Jebakan Warna Material Mentah di Compose**:
   *Salah*: Menulis `Card(colors = CardDefaults.cardColors(containerColor = Color.Blue))`
   *Benar*: Gunakan komponen design system WeMade (`ClayCard`, `ClayBadge`, `.claySurface`) dengan token `WeMadeColors`.

---

## 🧪 6. Verifikasi & Tantangan Mandiri

### Cara Menguji Kebenaran Implementasi
Jalankan rangkaian pengujian otomatis di terminal proyek:

```bash
# 1. Uji logika matematika murni domain dan invariant di :core
./gradlew :core:jvmTest --tests "com.eventverse.app.domain.techpack.*"

# 2. Uji integrasi REST API, database Flyway, dan RBAC di :server
./gradlew :server:test --tests "com.eventverse.app.TechPackApiTest"

# 3. Uji kompilasi UI Compose Multiplatform
./gradlew :app:shared:compileKotlinJvm
```

### 🎯 Tantangan Mandiri untuk Junior Developer
Untuk mengasah pemahamanmu setelah membaca modul ini, coba selesaikan 2 tantangan berikut:
1. **Fitur Ekspor PDF / Lembar Kerja Cetak**: Tambahkan use case `ExportTechPackSummaryUseCase` yang menghasilkan representasi teks terstruktur yang ramah cetak untuk ditempel di papan fisik operator mesin rajut.
2. **Validasi Toleransi SAM**: Tambahkan aturan domain baru di mana jika ada operasi kerja yang memiliki SAM > 60 menit dalam satu workstation, sistem memberikan tanda peringatan (*warning badge*) bahwa operasi tersebut berpotensi menjadi *bottleneck* produksi.
