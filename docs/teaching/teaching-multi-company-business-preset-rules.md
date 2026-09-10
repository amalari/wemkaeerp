# 🎓 Modul Pembelajaran: Arsitektur Multi-Perusahaan (FOB, CMT, Brand D2C) & 7 Kontrak Baku Modul ERP

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Multi-Tenant Enterprise Architecture, Business Model Polymorphism, Garment Supply Chain (FOB vs CMT vs D2C), Pipeline Handoff Contracts  
> **Prasyarat**: Dasar Kotlin, konsep dasar DDD (Entity, Value Objects), dan alur operasional garmen.  
> **Referensi Task**: Standarisasi 3 Model Bisnis dan Rules Pembuatan Modul Baru di WeMade ERP

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Bayangkan kamu sedang membangun software ERP untuk pabrik garmen. Jika kamu berasumsi bahwa semua pabrik baju bekerja dengan cara yang sama, sistemmu akan hancur lebur di dunia nyata. 

Di industri tekstil dan konveksi, ada 3 jenis bisnis dengan logika finansial dan operasional yang bertolak belakang 180 derajat:

1. **FOB (Full Package / OEM)**: Pabrik melayani pesanan baju utuh dari hulu ke hilir. Pabrik membeli kain, membuat pola, menjahit, hingga packing ekspor. Kain adalah **aset milik pabrik**.
2. **CMT (Cut, Make, Trim / Jasa Makloon)**: Klien/Buyer membawa kain dan aksesoris sendiri ke pabrik. Pabrik hanya menjual **jasa tenaga kerja & mesin potong/jahit**. Kain klien **BUKAN aset pabrik** (titipan konsinyasi bernilai Rp 0 di neraca). Invoice penagihan murni ongkos jasa per pcs/lusin.
3. **Brand D2C (Direct to Consumer)**: Pabrik membuat koleksi busana brand milik sendiri untuk dijual langsung ke end-user (melalui e-commerce, TikTok Shop, Shopee, retail). Tidak ada kontrak PO buyer dari luar; fokusnya adalah **katalog varian produk jadi (SKU x Ukuran x Warna)**, perkiraan stok (*demand forecasting*), dan *marketplace order fulfillment*.

### Masalah Nyata Jika Tanpa Kontrak Modular
Jika junior developer langsung membuat modul (misal modul gudang atau modul penagihan) tanpa aturan baku:
- **Bencana Akuntansi**: Kain titipan klien seharga ratusan juta di CMT salah dicatat sebagai aset/pembelian pabrik. Laporan rugi laba kacau balau.
- **Spaghetti Code (`if-else` Hell)**: Kode dipenuhi `if (company == "CMT") doA() else doB()` yang bertebaran di puluhan controller.
- **Pipeline Broken**: Node visual di *Factory Flow Canvas* terputus karena alur CMT tidak memiliki data *Fabric Purchasing Approval* yang diharapkan oleh modul berikutnya.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun dukungan multi-model bisnis ini dari awal, ikuti urutan berikut:

1. **Langkah 0: Definisikan Enum & Identitas Model Bisnis (`core/domain/pipeline`)**
   - Buat `GarmentBusinessPreset` dengan profil bisnis, deskripsi, dan perusahaan percontohan.
2. **Langkah 1: Ciptakan Standar Kontrak Modul (`OperationalModuleSpecification`)**
   - Tentukan *semantic rules*: kepemilikan stok (`StockOwnershipSemantics`), aturan HPP (`CostingBehavior`), dan kebijakan bypass (`ModuleExecutionPolicy`).
3. **Langkah 2: Sambungkan Model Bisnis ke Identitas Tenant (`core/domain/tenant`)**
   - Tambahkan `businessPreset: GarmentBusinessPreset` pada entitas `Tenant`. Dengan ini, setiap perusahaan (*tenant*) tahu kodrat model bisnisnya sejak login.
4. **Langkah 3: Seed Perusahaan Referensi di Repository (`server/infrastructure`)**
   - Buat 3 data perusahaan nyata di *InMemory* / *Postgres* agar tim QA dan developer bisa langsung berganti profil untuk pengujian.
5. **Langkah 4: Tulis Dokumen Aturan Baku di `.agents/rules/`**
   - Arsipkan `module-integration-rules.md` agar setiap modul baru yang dibuat oleh developer/AI berikutnya wajib mematuhi 7 kontrak ini.
6. **Langkah 5: Visualisasikan di UI Dashboard (`app/shared`)**
   - Hubungkan selector profil ke *Factory Flow Canvas* agar alur node yang dilewati dan di-bypass terlihat jelas secara visual.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Mari kita bedah kode yang baru saja kita bangun di layer domain murni (`core`).

### Blok A: Kontrak Baku Operasional (`OperationalModuleContract.kt`)

```kotlin
interface OperationalModuleSpecification {
    val module: BusinessModule
    val supportedPresets: Set<GarmentBusinessPreset>
    val stockOwnership: StockOwnershipSemantics
    val costingBehavior: CostingBehavior
    val upstreamPrerequisites: List<String>
    val downstreamHandoffs: List<String>

    fun getExecutionPolicy(preset: GarmentBusinessPreset): ModuleExecutionPolicy {
        return if (supportedPresets.contains(preset)) {
            ModuleExecutionPolicy.MANDATORY
        } else {
            ModuleExecutionPolicy.BYPASSED
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
- `supportedPresets`: Menghindari hardcoded `if-else`. Modul cukup mendaftarkan dirinya relevan untuk model bisnis mana saja.
- `getExecutionPolicy()`: Fungsi cerdas ini otomatis memberi tahu sistem visualisasi alur apakah modul ini harus aktif (`MANDATORY`) atau diloncati secara elegan (`BYPASSED`) tanpa merusak relasi rantai alur.

---

### Blok B: Semantik Kepemilikan Stok & Biaya

```kotlin
enum class StockOwnershipSemantics(
    val code: String,
    val displayName: String,
    val hasFinancialAssetValue: Boolean,
    val requiresWasteReconciliation: Boolean
) {
    OWNED_RAW_MATERIAL("owned_raw_material", "Bahan Baku Milik Pabrik", true, false),
    CONSIGNED_CLIENT_MATERIAL("consigned_client_material", "Kain Titipan Klien", false, true),
    INTERNAL_FINISHED_GOODS("internal_finished_goods", "Stok Baju Jadi Brand", true, false),
    NON_STOCK_SERVICE("non_stock_service", "Non-Fisik / Jasa Murni", false, false);
}
```
**Mengapa blok ini penting?**
- Perhatikan flag `hasFinancialAssetValue`: Di model CMT, nilai ini bernilai `false`! Akuntan pabrik tidak akan pernah salah memasukkan kain titipan buyer ke jurnal aset.
- Perhatikan flag `requiresWasteReconciliation`: Pada CMT, pabrik wajib mempertanggungjawabkan pemakaian kain ke buyer (misal dari 100 yard kain, jadi 80 pcs baju, sisa perca 5 yard harus dikembalikan).

---

### Blok C: Pengikatan Model Bisnis ke Tenant (`Tenant.kt`)

```kotlin
data class Tenant(
    val id: TenantId,
    val slug: TenantSlug,
    val name: TenantName,
    val status: TenantStatus = TenantStatus.TRIAL,
    val tier: SubscriptionTier = SubscriptionTier.PRO,
    val activeMachineCount: Int = 0,
    val businessPreset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT
) {
    // Immutable Domain Mutation
    fun updateBusinessPreset(newPreset: GarmentBusinessPreset): Tenant = copy(businessPreset = newPreset)
}
```
**Mengapa ditaruh nilai default (`= GarmentBusinessPreset.DEFAULT`)?**
- **Backward Compatibility**: Seluruh kode server, database mapping, dan puluhan unit test yang memanggil konstruktor `Tenant(...)` tanpa argumen baru ini tidak akan rusak (zero compilation errors).

---

### Blok D: 3 Perusahaan Referensi di Repository (`InMemoryTenantRepository.kt`)

```kotlin
// 1. Perusahaan FOB (Full Package / OEM)
val fobTenant = Tenant(
    id = TenantId("ten-demo-001"),
    slug = TenantSlug("wemade-demo"),
    name = TenantName("PT WeMade Garmen Ekspor (FOB)"),
    businessPreset = GarmentBusinessPreset.FOB_FULL_PACKAGE
)

// 2. Perusahaan CMT (Cut, Make, Trim / Makloon Jahit)
val cmtTenant = Tenant(
    id = TenantId("ten-demo-cmt"),
    slug = TenantSlug("cv-berkah-makloon"),
    name = TenantName("CV Berkah Makloon Jahit (CMT)"),
    businessPreset = GarmentBusinessPreset.CMT_MAKLOON
)

// 3. Perusahaan Brand D2C (Direct-to-Consumer / Distro Mandiri)
val d2cTenant = Tenant(
    id = TenantId("ten-demo-d2c"),
    slug = TenantSlug("urbanwear-d2c"),
    name = TenantName("UrbanWear Studio Apparel (Brand D2C)"),
    businessPreset = GarmentBusinessPreset.BRAND_D2C
)
```

### Blok E: Composable Puzzling Architecture & Module Archetype

```kotlin
// Padanan Kemampuan (Archetype Slot)
enum class ModuleArchetype(
    val code: String,
    val displayName: String,
    val defaultExpectedInputType: String,
    val defaultProducedOutputType: String
) {
    ORDER_INGESTION("order_ingestion", "Penerimaan Pesanan / Sales", ...),
    RAW_MATERIAL("raw_material", "Bahan Baku & Persediaan Gudang", ...),
    COSTING_HPP("costing_hpp", "Perhitungan Biaya & HPP (Costing Engine)", ...),
    CUTTING("cutting", "Pemotongan Pola Kain", ...),
    SEWING("sewing", "Penjahitan & Perakitan", ...),
    FINISHING("finishing", "Finishing & Setrika", ...),
    QUALITY_CONTROL("quality_control", "Pengawasan Mutu & Inspeksi", ...),
    FULFILLMENT("fulfillment", "Pengemasan & Pengiriman", ...),
    CUSTOM_EXTENSION("custom_extension", "Modul Khusus Tambahan / Plugin", ...);
}

// Entitas Graf Alur Dinamis per Tenant
data class CustomTenantPipeline(
    val tenantId: TenantId,
    val pipelineName: String,
    val baseStarterPreset: GarmentBusinessPreset? = null,
    val nodes: List<CustomPipelineNode>,
    val edges: List<CustomPipelineEdge>
)
```
**Mengapa konsep Archetype & Composable Puzzling ini adalah game-changer?**
- **Padanan Tanpa Merusak Rantai**: Jika Tenant A menggunakan rumus HPP *Full Package* ($Bahan + Jahit + Margin$) dan Tenant B menggunakan rumus HPP *CMT* ($SAM \times Tarif Operator$), keduanya **sepadan** karena sama-sama mengisi slot `ModuleArchetype.COSTING_HPP`.
- **Bebas Merangkai Alur**: Melalui `CustomTenantPipeline`, pabrik yang hanya melakukan *Bordir & Sablon* atau pabrik yang ingin alur instan dari *Marketplace ke Packing* bisa menyambungkan node seperti blok Lego asalkan tipe data port input/output-nya cocok.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan Terpilih | Alternatif Lain | Mengapa Memilih Pendekatan Ini? | Risiko Alternatif Lain |
|---|---|---|---|
| **Polymorphic Preset Contract (`OperationalModuleSpecification`)** | Hardcoded boolean flag `isCMT` di setiap file service | Menjamin Open-Closed Principle (SOLID). Menambah preset ke-4 (misal: Sub-kon Bordir saja) tidak merombak kode lama. | File service akan menjadi ribuan baris penuh `if/else`, sulit di-test, dan rapuh (*fragile*). |
| **Pemisahan Semantik Kepemilikan Stok (`StockOwnershipSemantics`)** | Kolom `price = 0` di tabel barang biasa | Memiliki pemaknaan domain yang tegas (Domain Ubiquitous Language). Membedakan hak milik hukum barang. | Barang titipan orang bisa tidak sengaja terhitung di audit pajak atau neraca laba-rugi pabrik. |
| **Explicit Upstream & Downstream Ports** | Event Bus generik tanpa validasi kontrak | Memungkinkan *Factory Flow Canvas* me-render visualisasi node secara real-time dan mengetahui jalur dependensi. | Kesalahan pengiriman dokumen baru ketahuan di runtime saat data sudah rusak di tengah jalan. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Menganggap CMT itu "FOB yang dipotong harganya"**
   - *Kenapa salah*: Di CMT, kamu sama sekali tidak berhak menjual bahan baku. Jika buyer membawa kain 1.000 meter, lalu ada 50 meter cacat dari pabrik kain asalnya, kamu tidak boleh menanggung biayanya. Kontrak `DefectLiability.CLIENT_SUPPLIED_DEFECT` harus memisahkan tanggung jawab ini.
2. **Jebakan 2: Menghapus Node yang Tidak Dipakai sehingga Jalur Putus**
   - *Kenapa salah*: Di alur CMT, jika kamu *menghapus* node *Procurement*, sistem pipeline akan bingung dari mana input *Cutting* berasal.
   - *Solusi kita*: Gunakan status `ModuleExecutionPolicy.BYPASSED`. Node tetap ada dalam peta alur namun dilewati (*skipped*), sehingga konektor Bezier tetap mengalir dari *Penerimaan Kain* langsung ke *Cutting*.
3. **Jebakan 3: Mengabaikan Unit of Measure (UOM) Conversion**
   - *Kenapa salah*: Kain dibeli atau diterima dalam satuan **Yard** atau **Kilogram (Kg)**, tetapi saat dipotong dan dijahit menjadi pakaian, satuannya berubah menjadi **Pieces (Pcs)** atau **Lusin**. Modul handoff harus memperhitungkan faktor konversi konveksi (*Yield ratio*).

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Verifikasi Kompilasi Multiplatform**:
   ```bash
   ./gradlew compileKotlinMetadata
   ```
   *Hasil*: Menjamin kode di `core` dan `app/shared` lolos kompilasi untuk seluruh target (JVM, JS, WasmJS, Android, iOS).
2. **Verifikasi Server Unit & Integration Tests**:
   ```bash
   ./gradlew :server:test
   ```
   *Hasil*: 28 test server Ktor lolos 100% tanpa regresi pada *TenantResolutionPlugin* dan *DepartmentApiTest*.
3. **Verifikasi UI Factory Flow**:
   Buka peramban di `http://localhost:3000/factory-flow`:
   - Klik pill **FOB Full Package** $\rightarrow$ Perhatikan nama `PT WeMade Garmen Ekspor` aktif dan alur 9 modul berjalan lengkap.
   - Klik pill **CMT Jasa Jahit** $\rightarrow$ Perhatikan nama `CV Berkah Makloon Jahit` aktif, modul pengadaan kain di-bypass, dan fokus pada jasa jahit & rekonsiliasi sisa kain.
   - Klik pill **Brand D2C Internal** $\rightarrow$ Perhatikan nama `UrbanWear Studio Apparel` aktif, berorientasi pada stok barang jadi retail per SKU.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka file `core/src/commonMain/kotlin/com/eventverse/app/domain/pipeline/OperationalModuleContract.kt`. Buat satu *Dummy Specification* untuk modul baru bernama `SubcontractEmbroideryModule` (Jasa Makloon Bordir Luar) yang mengimplementasikan `OperationalModuleSpecification`.
- [ ] **Tantangan 2**: Tentukan apa `StockOwnershipSemantics` dan `CostingBehavior` yang tepat untuk pesanan seragam instansi pemerintah (apakah FOB atau CMT?). Jelaskan argumenmu!
