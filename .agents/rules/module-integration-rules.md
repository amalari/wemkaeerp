# WeMade ERP — Aturan Standar Pembuatan & Integrasi Modul (Composable & Puzzling Architecture)

Dokumen ini adalah **aturan baku arsitektur** yang wajib ditaati setiap kali membuat, memperluas, atau merefaktorisasi modul fungsional di WeMade ERP (misal: *Procurement*, *Sampling*, *Cutting SPK*, *Sewing Kanban*, *QC Inspection*, *Consignment Receiving*, *B2C Marketplace Sync*, *Custom Costing Engine*, *Invoicing*).

---

## 1. Paradigma Sistem: Composable "Lego / Puzzle" Architecture

Sistem WeMade ERP **TIDAK TERBATAS** pada enum model bisnis yang kaku. Kita menganut arsitektur **Composable Enterprise ERP**:

1. **Preset (FOB, CMT, Brand D2C) adalah Starter Templates**:
   - FOB, CMT, dan D2C **BUKAN batasan mati (hardcoded lock)**.
   - Preset hanyalah **blueprint awal (starter template)** siap pakai saat onboarding agar tenant baru tidak perlu menyusun dari kanvas kosong.
2. **Kebebasan Merangkai Alur (Puzzling / Directed Acyclic Graph)**:
   - Setiap tenant memiliki kebebasan 100% untuk menambah node, menghapus node, menukar urutan proses, atau membuat alur hibrida (misal: *Makloon Sablon & Jahit*, *FOB Custom Seragam*, atau *Distro D2C dengan Sub-kon Jahit Luar*).
3. **Konsep "Padanan Modul" (Module Archetype / Capability Slot)**:
   - Modul yang dibuat developer diklasifikasikan ke dalam **Archetype** (slot kemampuan fungsional).
   - **Contoh Nyata**: Modul HPP Tenant A (*Full BOM Costing*) dan Modul HPP Tenant B (*Tarif Menit SAM Makloon*) adalah **SEPADAN** karena sama-sama mengisi slot archetype `COSTING_HPP`. Keduanya bisa saling ditukar (*interchangeable*) tanpa merusak modul sebelum (*Tech Pack*) dan modul sesudahnya (*Alokasi Mesin/SPK*).

---

## 2. Klasifikasi Slot Kemampuan (Module Archetype Registry)

Setiap modul baru **WAJIB** menyatakan padanannya pada salah satu `ModuleArchetype`:

| Archetype Code | Nama Padanan Slot | Ekspektasi Data Masuk (Input Port) | Ekspektasi Data Keluar (Output Port) | Contoh Variasi Implementasi |
|---|---|---|---|---|
| `ORDER_INGESTION` | Penerimaan Pesanan / Sales | `CommercialInquiry` | `ProductionOrderDraft` | RFQ B2B Ekspor, SPK Makloon Buyer, Order Marketplace Shopee/TikTok |
| `RAW_MATERIAL` | Bahan Baku & Gudang | `MaterialRequisition` | `VerifiedMaterialStock` | Pembelian Kain Supplier (FOB), Penerimaan Kain Titipan Konsinyasi (CMT), Gudang Kain Multi-Roll |
| `COSTING_HPP` | Mesin Hitung Biaya & HPP | `TechPackAndYieldData` | `CostingCalculationResult` | HPP Rumus Penuh (Kain+Jahit+Margin), HPP Borongan SAM per Menit, HPP Valuasi Retail + Fee Marketplace |
| `CUTTING` | Pemotongan Pola (Spreading) | `CuttingOrderWithFabric` | `CutPiecesBundle` | Meja Potong Manual, Laser Cutting Otomatis, Makloon Jasa Potong Saja |
| `SEWING` | Penjahitan & Perakitan | `CutPiecesBundle` | `AssembledGarmentBundle` | Lini Jahit Ban Berjalan, Stasiun Borongan Perorangan, Alur Jahit Makloon Luar (Subcontract) |
| `FINISHING` | Cuci, Gosok & Trimming | `AssembledGarmentBundle` | `FinishedGarmentUnit` | Setrika Uap Konveksi, Garment Washing/Dyeing, Labeling & Hangtag |
| `QUALITY_CONTROL` | Pengawasan Mutu & AQL | `FinishedGarmentUnit` | `InspectedAndGradedUnit` | QC 100% End-line, Sampling AQL 2.5 Ekspor, QC Verifikasi Cacat Bahan Buyer |
| `FULFILLMENT` | Packing, Surat Jalan & Kirim | `InspectedAndGradedUnit` | `DispatchedShipmentManifest` | Packing Karton Ekspor B2B, Retur Kain Sisa Makloon, Pick-Pack-Scan Barcode E-commerce |
| `CUSTOM_EXTENSION`| Plugin / Ekstensi Bebas | `AnyOperationalPayload` | `AnyOperationalPayload` | Sablon Manual, Bordir Komputer, Laundry Kimia, Approval Khusus Direksi |

---

## 3. Tujuh Kontrak Baku Pembuatan Modul (The 7 Modular Contracts)

Setiap kali developer membuat class modul operasional di WeMade ERP, kontrak berikut wajib dipenuhi:

### Kontrak 1: Deklarasi Archetype & Template Rekomendasi
- Modul wajib mendefinisikan `archetype: ModuleArchetype`.
- Modul mencantumkan `recommendedStarterPresets`: di template starter mana modul ini sebaiknya aktif secara default (misal: modul pengadaan kain aktif di FOB & D2C, tapi di-bypass di template starter CMT).

### Kontrak 2: Kompatibilitas Port Puzzle (Input & Output Port Typing)
- Modul adalah "blok puzzle" independen.
- Port Input harus mendeklarasikan tipe data yang diterima (`acceptedInputDataTypes`).
- Port Output harus mendeklarasikan tipe data yang dipancarkan (`producedOutputDataType`).
- Dua modul bisa disambungkan dalam alur custom tenant **jika dan hanya jika** tipe data output Modul A kompatibel dengan tipe data input Modul B.

### Kontrak 3: Semantik Kepemilikan Stok (`StockOwnershipSemantics`)
Modul gudang dan bahan baku dilarang mencampuradukkan status kepemilikan:
- `OWNED_RAW_MATERIAL`: Bahan baku dibeli dan dimiliki pabrik (masuk neraca aset keuangan).
- `CONSIGNED_CLIENT_MATERIAL`: Kain milik buyer/klien yang dititipkan (nilai Rp 0 di neraca pabrik; wajib rekonsiliasi sisa kain perca/waste).
- `INTERNAL_FINISHED_GOODS`: Stok baju jadi milik brand internal per SKU x Warna x Ukuran.

### Kontrak 4: Strategi Kalkulasi Biaya Fleksibel (`CostingBehavior`)
- Modul yang menempati slot `COSTING_HPP` harus memisahkan logika rumus dari core engine:
  - Gunakan **Strategy Pattern** atau parameter dinamis (`customFormulaParameters`).
  - Tenant bebas mengatur koefisien (misal: margin laba, tarif SAM per detik, biaya jarum/benang).
  - Pada model jasa makloon murni, harga kain titipan tidak boleh dijumlahkan ke total tagihan invoice klien.

### Kontrak 5: Penanganan Cacat & Alur Mundur (`DefectLiability & Rework Loop`)
Setiap modul lantai produksi wajib memiliki jalur penanganan jika terjadi cacat/reject:
- Bedakan tanggung jawab:
  - `FACTORY_WORKMANSHIP`: Kesalahan operator pabrik -> biaya pengerjaan ulang (*rework*) ditanggung pabrik.
  - `CLIENT_SUPPLIED_DEFECT`: Cacat serat kain bawaan buyer -> disisihkan dan dicatat berita acara agar tidak merugikan penjahit.
  - `SUPPLIER_VENDOR_DEFECT`: Cacat dari pabrik tekstil rekanan -> retur nota debit ke supplier.

### Kontrak 6: Telemetri Pemantauan Alur (`Operational Telemetry`)
Agar node modul dapat berkedip hijau/kuning/merah di visualisasi kanvas:
- Modul wajib menyediakan: `wipPieces` (antrean potong/baju yang sedang tertahan), `cycleTimeHours` (kecepatan kerja), dan `healthStatus` (`HEALTHY`, `WARNING`, `BOTTLENECK`, `CRITICAL`).

### Kontrak 7: Isolasi Multi-Tenant & Konfigurasi Pipeline
- Data konfigurasi alur kustom disimpan pada entitas `CustomTenantPipeline` berdasar `TenantId`.
- Alur pipeline diisolasi per tenant sehingga modifikasi node oleh satu pabrik tidak berdampak ke tenant lain.

### Kontrak 8: Aturan Baku Kapabilitas Jangkauan Data Modul (`ScopeCapability` & `DataScope`)
Setiap modul baru **WAJIB** mendeklarasikan salah satu dari dua kapabilitas jangkauan data (`ScopeCapability`):

#### 1. `ScopeCapability.GLOBAL_ONLY` (Kolektif / Shared Factory)
- **Karakteristik**: Digunakan untuk modul yang datanya merupakan aset bersama pabrik dan tidak logis dipartisi per individu pembuat.
- **Daftar Modul Contoh**: Bahan Baku Gudang (`INVENTORY`), Spesifikasi BOM (`TECH_PACK_BOM`), Kalkulasi HPP (`COSTING_HPP`), Jadwal Mesin (`PRODUCTION_MRP`), Inspeksi QC (`QUALITY_CONTROL`), Packing & Surat Jalan (`FULFILLMENT`).
- **Aturan Opsi Wewenang**:
  - Pilihan scope **otomatis terkunci 100% pada `DataScope.ALL_TENANT_DATA`** ("Seluruh Data Pabrik").
  - Opsi *Data Sendiri* (`OWN_DATA_ONLY`) dan *Data Bawahan* (`SUBORDINATE_DATA`) **TIDAK TERSEDIA** (disembunyikan dari UI modal wewenang) untuk mencegah kesalahan staf yang mengira sistem rusak saat data kain/mesin kosong.
  - Tampilan UI: Menampilkan badge informatif tunggal `🌐 Seluruh Pabrik (Shared)`.
- **Proteksi Domain**: Otomatis dilindungi oleh method `ModuleAccessConfig.sanitizeFor(module)` yang memaksa scope kembali ke `ALL_TENANT_DATA` jika ada request payload ilegal.

#### 2. `ScopeCapability.HIERARCHICAL` (Hirarkis / Per-Karyawan & Tim)
- **Karakteristik**: Digunakan untuk modul transaksional di mana dokumen dimiliki oleh staf pembuat (*maker*) dan diawasi oleh kepala divisinya (*checker/leader*).
- **Daftar Modul Contoh**: Pelanggan & Prospek Sales (`CRM_SALES`), Order Sampling Desain (`SAMPLING_ORDER`), Catatan Kerja Operator (`OPERATOR_EXEC`).
- **Aturan Opsi Wewenang**:
  - Tersedia **3 pilihan lengkap** yang dapat dipilih oleh admin pabrik:
    1. `DataScope.OWN_DATA_ONLY` ("Data Sendiri"): Pengguna hanya melihat dokumen miliknya sendiri.
    2. `DataScope.SUBORDINATE_DATA` ("Data Bawahan"): Pengguna melihat data miliknya dan seluruh staf bawahannya di divisi yang sama.
    3. `DataScope.ALL_TENANT_DATA` ("Semua Data"): Pengguna melihat seluruh data lintas cabang/pabrik.
  - Tampilan UI: Menampilkan segment selector 3 tab `[Data Sendiri | Data Bawahan | Semua Data]`.

---

## 4. Checklist Verifikasi Developer Sebelum Merge (Definition of Done)

- [ ] Apakah modul telah menetapkan `ModuleArchetype` yang tepat sehingga sepadan dengan modul sejenis?
- [ ] Apakah tipe data Input Port dan Output Port telah terdokumentasi dan kompatibel?
- [ ] Apakah modul mendukung parameter kustom per tenant tanpa perlu mengubah kode `core`?
- [ ] Jika memproses kain titipan, apakah status persediaan ditandai `CONSIGNED_CLIENT_MATERIAL` (Rp 0 di neraca)?
- [ ] **Apakah `scopeCapability` modul sudah ditentukan dengan benar?**
  - Jika data kolektif pabrik (kain/HPP/mesin) ➔ set `ScopeCapability.GLOBAL_ONLY` (hanya 1 opsi: Semua Data).
  - Jika data transaksi perorangan/sales/operator ➔ set `ScopeCapability.HIERARCHICAL` (tersedia 3 opsi: Sendiri / Bawahan / Semua Data).
- [ ] Apakah modul telah diuji berjalan pada alur bawaan (FOB/CMT/D2C) maupun alur custom hasil utak-atik (*puzzled*)?
- [ ] Apakah modul menyertakan dokumentasi pengajaran (*teaching*) di `docs/teaching/`?
