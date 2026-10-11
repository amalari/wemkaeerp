# Rencana Implementasi: Modul Costing HPP — Importer Excel Historis & Estimator Cepat AI

Dokumen ini merinci arsitektur end-to-end (5 pilar DDD) untuk mentransformasi modul **Costing HPP** agar mampu:
1. **Mengimpor 100+ file Excel HPP lama** secara otomatis menggunakan AI parser (Gemini Flash) ke dalam database PostgreSQL.
2. **Membangun Knowledge Base / Benchmark Produk Rajut** sebagai acuan kecerdasan buatan.
3. **Menyediakan Form Estimasi Cepat Ramah CS (Customer Service)**: Cukup upload desain mockup + 5 parameter awam untuk menghasilkan penawaran harga instan ke klien.
4. **Mengintegrasikan Ground Truth Hasil Sampling**: Otomatis menarik gramasi & menit riil dari SPK Sampling yang di-ACC ke Lembar HPP Massal.

---

## User Review Required

> [!IMPORTANT]
> **Penyimpanan Berkas Excel & Kunci API**:
> 1. Batch impor 100 file Excel lama dapat dijalankan langsung via **CLI Script Gradle/Kotlin** (`./gradlew :server:importHistoricalCosting --dir=data/excel-hpp`) yang membaca folder lokal dan memasukkannya ke database lokal.
> 2. Untuk pemrosesan AI, sistem akan menggunakan API Key Gemini (`GEMINI_API_KEY`) yang dapat diletakkan di environment variable atau file `.env`. Biaya untuk 100 file adalah **Rp 0 (Free Tier)** atau **< Rp 500**.
> 3. Gambar mockup yang diekstrak dari dalam Excel akan disimpan ke folder upload lokal / MinIO bucket tenant.

---

## Open Questions

> [!NOTE]
> Apakah 100 file Excel lama Anda memiliki format kolom/baris yang seragam seperti contoh Parinara, atau memiliki beberapa variasi format sheet yang berbeda?
> *(AI Gemini Flash mampu menangani variasi format kolom/baris yang berbeda secara fleksibel, namun jika ada format khusus akan kita tambahkan di prompt normalisasi).*

---

## Proposed Changes

Pengerjaan mengikuti **arsitektur Full-Stack 5 Pilar** sesuai aturan proyek WeMade (`AGENTS.md`).

```
Database (Flyway V36) ➔ Domain Core ➔ Backend API & Script ➔ Ktor Client ➔ Compose UI (Clay)
```

### 1. Database & Persistence Layer (`server/`)

Menyediakan tabel acuan produk masa lalu untuk pencarian kemiripan AI dan integrasi ke tabel HPP yang sudah ada (`costing_sheets`, `costing_sheet_buckets`, `material_items`).

#### [NEW] [V36__create_costing_product_benchmarks.sql](file:///Volumes/amalari/Projects/wemade/server/src/main/resources/db/migration/V36__create_costing_product_benchmarks.sql)
- Tabel `costing_product_benchmarks`:
  - `id VARCHAR(64) PRIMARY KEY`, `tenant_id VARCHAR(64) REFERENCES tenants(id)`
  - Identitas: `style_name`, `client_name`, `category` (Cardigan, Pullover, Vest, dll.)
  - Karakteristik Teknis: `knit_type`, `yarn_type`, `gauge`
  - Metrik Fisik Riil: `net_weight_grams DECIMAL(10,2)`, `knitting_minutes INT`, `button_count INT`
  - Visual & AI Features: `mockup_image_url TEXT`, `features_json JSONB`
  - Finansial Historis: `hpp_per_unit_minor BIGINT`, `selling_price_minor BIGINT`, `margin_ratio_micros BIGINT`
  - Referensi asal: `source_sheet_id VARCHAR(64) REFERENCES costing_sheets(id)`
- Indeks GIN dan B-Tree untuk pencarian cepat berdasarkan tenant, kategori, dan jenis rajut.
- RLS tenant isolation (`apply_tenant_rls`).

#### [NEW] [CostingBenchmarkTables.kt](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/infrastructure/tables/CostingBenchmarkTables.kt)
- Definisi tabel Exposed `CostingProductBenchmarksTable` memetakan DDL V36.

#### [NEW] [PostgresCostingBenchmarkRepository.kt](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/infrastructure/PostgresCostingBenchmarkRepository.kt)
- Implementasi `CostingBenchmarkRepository` menggunakan Exposed DAO/DSL.
- Fungsi: `save`, `saveBatch`, `findById`, `findSimilar(category, knitType, limit)`, `listAll`.

---

### 2. Pure Kotlin Domain Layer (`core/`)

Zero-dependency domain models, value objects, dan use cases untuk estimasi HPP dan benchmark historis.

#### [NEW] [CostingProductBenchmark.kt](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/costing/CostingProductBenchmark.kt)
- Entity `CostingProductBenchmark`:
  - Value objects: `BenchmarkId`, `KnitCategory`, `KnitStructure`, `PhysicalMetrics`, `BenchmarkPricing`.

#### [NEW] [CostingBenchmarkRepository.kt](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/costing/CostingBenchmarkRepository.kt)
- Interface repository domain murni.

#### [NEW] [ImportHistoricalCostingUseCase.kt](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/costing/usecases/ImportHistoricalCostingUseCase.kt)
- Menyimpan sheet HPP resmi ke `costing_sheets` + rincian bucket ke `costing_sheet_buckets` + benchmark ke `costing_product_benchmarks` dalam satu transaksi atomik.

#### [NEW] [EstimateCostingFromAiDesignUseCase.kt](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/costing/usecases/EstimateCostingFromAiDesignUseCase.kt)
- Menerima 5 parameter CS + analisis AI mockup.
- Menemukan benchmark produk historis yang paling mirip.
- Menghitung rentang estimasi gramasi, menit rajut, dan mengalikannya dengan tarif acuan di `CostingRateCard` / `MaterialPriceRepository`.
- Mengembalikan `QuickQuotationEstimateResult` (rentang HPP, estimasi harga jual, dan format ringkasan chat WhatsApp).

---

### 3. Backend API, Script Importer & AI Service (`server/`)

#### [MODIFY] [libs.versions.toml](file:///Volumes/amalari/Projects/wemade/gradle/libs.versions.toml) & [server/build.gradle.kts](file:///Volumes/amalari/Projects/wemade/server/build.gradle.kts)
- Menambahkan dependensi `org.apache.poi:poi-ooxml` untuk membaca berkas spreadsheet `.xlsx` dan mengekstrak gambar sheet secara native di JVM.

#### [NEW] [GeminiCostingParserService.kt](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/services/GeminiCostingParserService.kt)
- Service yang mengubah baris mentah Excel menjadi format JSON terstruktur menggunakan Gemini Flash API:
  - Normalisasi nama benang, tarif CMT, overhead, dan detail kancing.
  - Ekstraksi gambar mockup baju dari sheet Excel dan disimpan ke storage lokal.

#### [NEW] [HistoricalCostingCliImporter.kt](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/cli/HistoricalCostingCliImporter.kt)
- Entry point CLI / Gradle task (`importHistoricalCosting`):
  - Membaca seluruh file `.xlsx` di direktori yang ditentukan (misal `data/excel-hpp/`).
  - Memproses batch dengan progress bar log.
  - Memanggil `GeminiCostingParserService` dan `ImportHistoricalCostingUseCase`.

#### [MODIFY] [CostingRoutes.kt](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/routes/CostingRoutes.kt)
- Menambahkan route Ktor:
  - `POST /api/v1/costing/benchmarks/import`: Endpoint multipart untuk upload file Excel / ZIP via web.
  - `POST /api/v1/costing/estimate-quick`: Endpoint kalkulasi cepat berbasis AI untuk CS.
  - `GET /api/v1/costing/benchmarks`: Daftar arsip produk historis.

---

### 4. Client-Server Integration (`app/shared/`)

#### [NEW] [CostingBenchmarkRemoteDataSource.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/infrastructure/api/CostingBenchmarkRemoteDataSource.kt) & [CostingBenchmarkApiClient.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/infrastructure/api/CostingBenchmarkApiClient.kt)
- Implementasi Ktor Client untuk memanggil API estimate cepat dan import file.
- DTO `@Serializable` untuk request dan response estimasi cepat.

#### [MODIFY] [CostingViewModel.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/costing/CostingViewModel.kt) & [CostingUiState.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/costing/CostingUiState.kt)
- State management untuk:
  - Tab Workbench baru: `CostingWorkbenchTab.QUICK_ESTIMATOR` dan `CostingWorkbenchTab.HISTORICAL_BENCHMARKS`.
  - State dialog Import Excel (upload file, parsing status, preview hasil).
  - State formulir CS (Mockup image, 5 parameter, hasil hitung instan).

---

### 5. Shared Presentation Layer (`app/shared/presentation/`)

Desain antarmuka mematuhi **Claymorphism + Neo-Brutalism** (`WeMadeColors`, `ClayCard`, `ClayButton`, `ClayBadge`, Canvas `ClayIcons`):

#### [NEW] [AiQuickEstimatorPane.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/costing/components/AiQuickEstimatorPane.kt)
- Formulir Cepat CS:
  - Upload/Drop Mockup Desain (dilengkapi AI visual badge: misal *"Terdeteksi Cardigan Crop, 7 Kancing, Jacquard"*).
  - 5 Parameter Sederhana (Jumlah Qty, Karakter Bahan, Ketebalan Rajut, Model Potongan, Kancing & Label).
  - Tombol aksi: `[ ⚡ Hitung Estimasi Harga ]`.
  - Kartu Hasil Clay Hijau: Rentang HPP, Rekomendasi Harga Jual (Margin 20%), dan tombol `[ 📋 Salin Format Penawaran WhatsApp ]`.

#### [NEW] [HistoricalBenchmarksPane.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/costing/components/HistoricalBenchmarksPane.kt)
- Tabel katalog 100+ artikel masa lalu:
  - Foto thumbnail, nama artikel, klien, gramasi netto riil, menit mesin riil, HPP, dan harga jual.
  - Tombol toolbar: `[ 📂 Import File Excel / ZIP ]` dengan dialog upload dan progress bar.

#### [MODIFY] [CostingWorkspaceScreen.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/costing/CostingWorkspaceScreen.kt)
- Menambahkan tab selector di workbench:
  `[ 📋 Lembar HPP Aktif ]` | `[ ⚡ Estimator Cepat (CS) ]` | `[ 📚 Knowledge Base 100 Excel ]` | `[ 🏷️ Master Rate Card ]`.

---

## Verification Plan

### Automated Tests
1. **Unit Test Domain & Use Cases**:
   ```bash
   ./gradlew :core:jvmTest --tests "com.eventverse.app.domain.costing.*"
   ```
   - Verifikasi kalkulasi estimasi dari kombinasi 5 parameter.
   - Verifikasi import use case menyimpan data ke repository tanpa corrupt.
2. **Integration Test Database (Flyway & Repository)**:
   ```bash
   ./gradlew :server:test --tests "com.eventverse.app.infrastructure.PostgresCostingBenchmarkRepositoryTest"
   ```
   - Verifikasi migrasi V36 berjalan sukses dan RLS tenant berfungsi.
3. **Parser Test**:
   - Unit test parsing sheet Excel contoh (Parinara) mengekstrak 494 gram dan 97 menit secara presisi.

### Manual Verification
1. **Uji Coba Script Import Batch**:
   - Taruh 1 atau beberapa file `.xlsx` di folder `data/excel-hpp/`.
   - Jalankan script CLI impor dan pastikan data masuk ke database lokal.
2. **Uji Coba Tampilan Web**:
   - Buka `http://localhost:3000/costing-hpp`.
   - Buka tab *Estimator Cepat (CS)*, pilih 5 parameter, klik hitung, dan verifikasi angka estimasi keluar akurat.
   - Buka tab *Knowledge Base*, verifikasi data 100 Excel muncul rapi lengkap dengan breakdown biayanya.
