# Redesign Papan Kanban CRM Sales & Executive KPI Metric Strip (End-to-End Full-Stack)

Dokumen rencana arsitektur teknis dan panduan pengerjaan redesign menyeluruh modul **CRM Sales (`/crm-sales`)** WeMade ERP. Perubahan ini mengentaskan masalah layout melar ("Giant Wireframe Box"), menghadirkan **Executive KPI Metric Strip**, mengembalikan bahasa visual **Claymorphism + Neo-Brutalism** pada kontainer kolom Kanban, serta memperkaya **Kartu Lead Garmen** dengan metadata pesanan pakaian (*product archetype* & *qty*), tombol cepat WhatsApp, avatar sales PIC, dan status SLA follow-up.

---

## 🖼️ Mockup Desain Acuan (Claymorphism & Neo-Brutalism)

![CRM Sales Redesign Mockup](file:///Volumes/amalari/Projects/wemade/docs/plannings/crm_sales_redesign_mockup.jpg)

---

## User Review Required

> [!IMPORTANT]
> **Migrasi Skema Database (`V36`)**: Penambahan kolom `product_category` (tipe produk pakaian, misal: *Kaos / Polo*, *Kemeja Drill*, *Jaket / Hoodie*, *Seragam / Uniform*) dan `last_contacted_at` (timestamp interaksi terakhir) pada tabel `crm_leads`. Kolom ini memiliki nilai default yang aman (`''` dan `NULL`) sehingga backward-compatible 100% dengan data yang sudah ada.

> [!NOTE]
> **Pilar Visual Claymorphism**: Kolom Kanban tidak lagi menggunakan outline warna terang (biru/hijau/merah) di seluruh bingkai 800px. Bingkai kolom diubah menjadi kontainer `SurfaceMuted` (`#EEF2F6`) dengan outline netral tebal (`ClayBorder.Thick`) dan hard shadow. Aksen warna difokuskan pada **Header Badge Pill** (`Inquiry Masuk`, `Qualified Lead`, `Unqualified`) dan area drop preview. Lebar kolom dibatasi proporsional (`maxWidth = 380.dp`) sehingga tidak melar tak terhingga di layar ultra-wide desktop.

---

## Arsitektur 5 Pilar (Full-Stack End-to-End)

### Pilar 1: Database & Persistence Layer (`server/`)
1. **Flyway Migration (`V36__add_product_category_and_last_contacted_to_crm_leads.sql`)**:
   - Menambahkan kolom `product_category VARCHAR(100) NOT NULL DEFAULT ''`.
   - Menambahkan kolom `last_contacted_at TIMESTAMPTZ`.
   - Membuat index `idx_crm_leads_tenant_last_contacted ON crm_leads(tenant_id, last_contacted_at)` untuk performa query peringatan SLA follow-up.
2. **Exposed Table (`CrmTables.kt`)**:
   - Mendaftarkan kolom `productCategory = varchar("product_category", 100).default("")`.
   - Mendaftarkan kolom `lastContactedAt = timestampWithTimeZone("last_contacted_at").nullable()`.
3. **Postgres Repository (`PostgresCrmLeadRepository.kt`)**:
   - Memetakan field baru saat `findActive()`, `save()`, dan `patch()`.
   - Menambahkan query agregasi KPI / metrik lead per tenant.

### Pilar 2: Pure Domain Layer (`core/`)
1. **Value Objects (`CrmLeadValueObjects.kt`)**:
   - Menambahkan `value class ProductCategory(val value: String)`.
   - Menambahkan data class `CrmLeadKpiMetrics(val totalPipelineValue: MoneyIdr, val activeLeadsCount: Int, val qualifiedConversionRate: Double, val followUpNeededCount: Int)`.
2. **Domain Entity (`CrmLead.kt`)**:
   - Menambahkan properti `productCategory: ProductCategory = ProductCategory("")`.
   - Menambahkan properti `lastContactedAt: Instant? = null`.
   - Menambahkan domain helper `fun recordContact(now: Instant): CrmLead`.
3. **Use Cases**:
   - Memperbarui `CreateLeadUseCase.kt` & `UpdateLeadUseCase.kt` agar menerima parameter `productCategory`.
   - Memperbarui `AddLeadActivityUseCase.kt`: ketika sales mencatat aktivitas, otomatis update `lastContactedAt = now` pada lead terkait.
   - Membuat `GetCrmLeadKpiMetricsUseCase.kt` untuk menghitung metrik ringkasan eksekutif secara deterministik.

### Pilar 3: Backend API & Routing (`server/`)
1. **REST Endpoints (`CrmRoutes.kt`)**:
   - `GET /api/tenant/crm/leads/metrics`: Mengembalikan ringkasan metrik pipeline sales (`totalPipelineValue`, `activeLeadsCount`, `conversionRate`, `followUpNeededCount`).
   - `POST /api/tenant/crm/leads`: Menerima `product_category` pada request body JSON.
   - `PATCH /api/tenant/crm/leads/{id}`: Menerima patch `product_category` dan `last_contacted_at`.
2. **DTO & Serialization (`CrmLeadCodec.kt`)**:
   - Memperbarui encoder/decoder JSON untuk menyertakan `productCategory`, `lastContactedAt`, dan payload `CrmLeadKpiMetrics`.
   - Menegakkan RBAC (`requireCrmAccess`, tenant RLS).

### Pilar 4: Client-Server Integration (`app/shared/`)
1. **HTTP Repository (`KtorCrmLeadRepository.kt`)**:
   - Mengambil metrik KPI dari server via Ktor Client.
   - Memetakan payload DTO baru ke domain entity `CrmLead`.
2. **State & Events (`CrmUiState.kt` & `CrmViewModel.kt`)**:
   - Menambahkan state: `metrics: CrmLeadKpiMetrics?`.
   - Menambahkan state filter: `selectedEmployeeId: OrgNodeId?`, `selectedSource: String?`, `selectedProductCategory: String?`.
   - Menambahkan event: `FilterByEmployee`, `FilterBySource`, `FilterByProductCategory`.
   - `visibleLeads` memfilter berdasarkan kata kunci, sales PIC terpilih, dan channel sumber.

### Pilar 5: Shared Presentation Layer (`app/shared/presentation/`)
1. **Komponen Baru: `CrmKpiMetricsRow.kt`**:
   - Menampilkan 4 kartu Clay:
     - 💼 **Total Pipeline Value** (cth: `Rp 245.000.000` dengan ikon uang/dompet).
     - 📋 **Active Leads** (cth: `24 Leads` dengan ikon inbox/clipboard).
     - 📊 **Qualified Conversion** (cth: `68%` dengan ikon pie/chart).
     - ⚠️ **Follow-up Needed** (cth: `3 Alerts` dengan background lembut alert saat > 0).
2. **Penyempurnaan Toolbar (`CrmKanbanBoard.kt`)**:
   - Search input terintegrasi dengan filter dropdown Sales Rep dan Source Channel.
   - Toggle beralih antara Kanban Board dan Table View.
   - Pembatasan lebar kolom (`maxWidth = 380.dp`) dan centering container.
3. **Desain Ulang Kontainer Kolom (`CrmKanbanColumn.kt`)**:
   - Background kolom diganti menjadi `WeMadeColors.SurfaceMuted` (`#EEF2F6`).
   - Outline netral tebal (`ClayBorder.Thick`, warna `OutlineSoft` / `Border`), hard shadow neo-brutalist.
   - Header badge pill menggunakan warna stage (`Primary`, `Success`, `Error`).
   - Empty state informatif dengan petunjuk drop area.
4. **Desain Ulang Kartu Lead (`CrmKanbanCard.kt`)**:
   - Judul brand dan sub-spesifikasi garmen (misal: *Erigo Apparel — 1.000 pcs Polo Pique*).
   - Nilai estimasi dicetak tebal dengan rupiah hijau kontras.
   - Tombol cepat **WhatsApp** (`IconChat`) yang langsung membuka `https://wa.me/...`.
   - Avatar sales PIC dan nama kontak klien.
   - Badge sumber lead (*WhatsApp Ads*, *Referral*) dan status SLA follow-up (*Follow-up 2j lalu*).
   - Patuh total aturan styling WeMade: Zero emoji Unicode, memakai vektor canvas `ClayIcons.kt`, nol literal `Color(...)` liar.

---

## Proposed Changes

### Server & Persistence

#### [NEW] [V36__add_product_category_and_last_contacted_to_crm_leads.sql](file:///Volumes/amalari/Projects/wemade/server/src/main/resources/db/migration/V36__add_product_category_and_last_contacted_to_crm_leads.sql)
- Migrasi Flyway untuk penambahan kolom `product_category` dan `last_contacted_at` berserta index-nya.

#### [MODIFY] [CrmTables.kt](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/infrastructure/tables/CrmTables.kt)
- Menambahkan mapping kolom `productCategory` dan `lastContactedAt` pada `CrmLeadsTable`.

#### [MODIFY] [PostgresCrmLeadRepository.kt](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/infrastructure/PostgresCrmLeadRepository.kt)
- Mapping query baca & simpan untuk kolom baru.

#### [MODIFY] [CrmRoutes.kt](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/routes/CrmRoutes.kt)
- Endpoint baru `GET /api/tenant/crm/leads/metrics` dan integrasi use case terkait.

---

### Pure Domain (`core/`)

#### [MODIFY] [CrmLeadValueObjects.kt](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/crm/CrmLeadValueObjects.kt)
- Menambahkan `ProductCategory` dan `CrmLeadKpiMetrics`.

#### [MODIFY] [CrmLead.kt](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/crm/CrmLead.kt)
- Menambahkan atribut `productCategory` dan `lastContactedAt`.

#### [NEW] [GetCrmLeadKpiMetricsUseCase.kt](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/crm/usecases/GetCrmLeadKpiMetricsUseCase.kt)
- Menghitung metrik KPI sales CRM dari kumpulan entity lead.

---

### Client Integration & Presentation (`app/shared/`)

#### [MODIFY] [CrmLeadCodec.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/shared/crm/CrmLeadCodec.kt)
- Serializer/deserializer JSON untuk field dan metrik baru.

#### [MODIFY] [CrmUiState.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/CrmUiState.kt)
- Menampung state KPI metrics, filter sales rep, dan filter sumber.

#### [MODIFY] [CrmViewModel.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/CrmViewModel.kt)
- Memproses event filter dan kalkulasi/fetch metrik.

#### [NEW] [CrmKpiMetricsRow.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/CrmKpiMetricsRow.kt)
- Komponen baris 4 kartu KPI bergaya Claymorphism.

#### [MODIFY] [CrmKanbanBoard.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/CrmKanbanBoard.kt)
- Layout toolbar terpadu (search + dropdown filter + toggle view) dan penataan kolom responsif.

#### [MODIFY] [CrmKanbanColumn.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/CrmKanbanColumn.kt)
- Kontainer `SurfaceMuted`, outline netral tebal, dan header stage pill.

#### [MODIFY] [CrmKanbanCard.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/CrmKanbanCard.kt)
- Rincian kartu konveksi garmen: produk, pcs, nilai rupiah tebal, tombol WhatsApp cepat, PIC avatar, dan tag channel.

---

## Verification Plan

### Automated Tests
1. **Core Unit Tests**:
   - `CrmLeadTest.kt`: Memastikan `ProductCategory`, `lastContactedAt`, dan `CrmLeadKpiMetrics` berperilaku immutably sesuai aturan domain.
   - `GetCrmLeadKpiMetricsUseCaseTest.kt`: Menguji akurasi kalkulasi rasio konversi dan total pipeline value.
2. **Server Integration Tests**:
   - Menjalankan tes endpoint CRM Ktor untuk verifikasi DTO JSON baru dan kalkulasi metrik.
3. **Gradle Build Verification**:
   - Menjalankan `./gradlew :app:shared:compileKotlinDesktop` dan `./gradlew :server:test` untuk memvalidasi tidak ada regresi kompilasi multiplatform.

### Manual Verification
1. **Tampilan Desktop (Widescreen 1440px+)**:
   - Buka `http://localhost:3000/crm-sales`.
   - Pastikan kartu metrik KPI muncul rapi di bagian atas dengan nilai teragregasi.
   - Pastikan 3 kolom Kanban memiliki lebar seimbang (tidak melar kosong) dengan background kontainer slate yang nyaman dipandang.
2. **Uji Kartu Lead & Tombol WhatsApp**:
   - Buat lead baru dengan kategori produk garmen (*1.000 pcs Polo Pique*).
   - Pastikan nomor WhatsApp memiliki tombol aksi cepat dan langsung membuka tautan WA chat.
3. **Drag & Drop Interactivity**:
   - Uji drag kartu antar kolom (New Lead $\to$ Qualified Lead $\to$ Unqualified) dan pastikan feedback visual drop target bekerja mulus.
