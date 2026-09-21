# 🎓 Modul Pembelajaran: Surat Jalan Multi-Site, Makloon Vendor, dan Pengiriman Parsial

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Multi-Site Manufacturing Logistics, Bundle Integrity, Vendor Aggregation, Compose Multiplatform, Full-Stack End-to-End Architecture  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform, Domain-Driven Design, Exposed ORM, Ktor, dan Claymorphism Design System  
> **Referensi Task**: Modul Surat Jalan & Multi-Site Makloon (WeMade ERP)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Bayangkan kamu mengelola sebuah pabrik garmen dan konveksi rajut dengan dua gedung terpisah:
- **Gedung A**: Khusus mesin rajut, potong (cutting), dan bordir.
- **Gedung B**: Khusus jahit (sewing), obras, steam setrika uap, dan packaging.

Selain itu, karena kapasitas pasang kancing di dalam pabrik sedang penuh, kamu harus me-makloon-kan (subcontract) 1.000 pcs baju ke **Konveksi Berkah** di luar pabrik. Dan setelah barang selesai, pembeli (buyer) meminta 300 pcs dikirim terlebih dahulu ke tokonya di Jakarta, sedangkan 700 pcs sisanya menyusul minggu depan.

### Masalah Nyata Jika Dibuat Asal-asalan:
1. **Kehilangan Jejak Bundle (#01, #02, dst.) pada Mutasi Internal**:  
   Jika sopir mobil pickup membawa 500 potong pakaian dari Gedung A ke Gedung B hanya dengan catatan "500 pcs", operator jahit di Gedung B akan bingung menentukan bundle mana yang dikerjakan oleh siapa, nomor ikatan berapa, dan bagaimana menghitung upah borongannya. Di mutasi internal, **identitas bundle individual tidak boleh hilang**.
2. **Kekacauan di Vendor Makloon Luar**:  
   Sebaliknya, jika kamu mengirim 1.000 pcs ke vendor rekanan luar dengan rincian 50 bundle kecil-kecil nomor #01 s/d #50, vendor luar akan menolak sistem administrasi yang merepotkan tersebut. Vendor luar hanya peduli: *"Saya menerima 600 pcs size L dan 400 pcs size XL dengan ongkos jasa Rp 2.500/pcs dan harus dikembalikan tanggal 30 September."*
3. **Pengiriman Parsial ke Klien Kehilangan Pelacakan Backlog**:  
   Jika buyer memesan 1.000 pcs dan kamu kirim 300 pcs dengan Surat Jalan biasa tanpa validasi backlog, sistem finansial dan gudang akan menganggap order sudah selesai (completed) atau malah mencatat piutang yang keliru.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun fitur logistik multi-site dan makloon ini dari layar kosong, ikuti urutan operasi berikut:

```
[1. Pure Domain Layer (core)]
  ├── Value Objects (SuratJalanId, SuratJalanNumber, LocationId, CartonId, TransferType)
  ├── Aggregate Root (SuratJalanManifest) & Invariant Rules
  ├── Repository Interface (SuratJalanRepository)
  └── Vertical Slice Use Cases (Internal, Makloon, Customer Dispatch, Receive)
         │
[2. Database & Persistence Layer (server)]
  ├── Flyway Migration V55 SQL DDL (tenant_locations, surat_jalan_manifests, surat_jalan_items)
  ├── Exposed Table Objects (SuratJalanTables.kt)
  └── Postgres Repository Implementation (PostgresSuratJalanRepository.kt)
         │
[3. Shared Codec Layer (core/shared)]
  └── SuratJalanCodec.kt (Single Source of Truth JSON Serialization)
         │
[4. Backend Routing & RBAC Layer (server)]
  └── SuratJalanRoutes.kt (REST API Ktor & Tenant Isolation)
         │
[5. Client Remote Data Source & Presentation (app/shared)]
  ├── Ktor Client (SuratJalanApiClient.kt)
  ├── ViewModel MVI (SuratJalanViewModel.kt)
  └── Claymorphism Compose UI (SuratJalanWorkspaceScreen.kt & SuratJalanManifestCard.kt)
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Invarian Domain pada Agregat `SuratJalanManifest`

Di `core/src/commonMain/kotlin/com/eventverse/app/domain/transfer/SuratJalanManifest.kt`:

```kotlin
data class SuratJalanManifest(
    val id: SuratJalanId,
    val tenantId: String,
    val sjNumber: SuratJalanNumber,
    val transferType: TransferType,
    val subject: WorkSubjectRef,
    val originLocationId: LocationId? = null,
    val destinationLocationId: LocationId? = null,
    val vendorRef: String? = null,
    val customerName: String? = null,
    val items: List<SuratJalanItem> = emptyList(),
    ...
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId cannot be blank" }

        when (transferType) {
            TransferType.INTERNAL_SITE_TRANSFER -> {
                requireNotNull(originLocationId) { "Internal transfer requires originLocationId" }
                requireNotNull(destinationLocationId) { "Internal transfer requires destinationLocationId" }
                require(originLocationId != destinationLocationId) {
                    "Internal transfer origin and destination must be different physical locations"
                }
                require(items.all { it.bundleNo != null }) {
                    "Internal transfer must preserve individual bundle numbers for all items"
                }
            }
            TransferType.SUBCONTRACT_OUTBOUND -> {
                requireNotNull(vendorRef) { "Subcontract outbound transfer requires vendorRef" }
                require(vendorRef.isNotBlank()) { "vendorRef cannot be blank" }
            }
            TransferType.CUSTOMER_DISPATCH -> {
                requireNotNull(customerName) { "Customer dispatch requires customerName" }
                require(customerName.isNotBlank()) { "customerName cannot be blank" }
            }
            TransferType.SUBCONTRACT_INBOUND -> {
                requireNotNull(vendorRef) { "Subcontract inbound requires vendorRef" }
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- **Penegakan Invarian di Titik Masuk (Init Block)**: Jangan biarkan objek dengan state yang cacat (misal mutasi internal tanpa lokasi tujuan, atau tanpa nomor bundle) pernah tercipta di memori.
- **Pembedaan Pola Data Berdasarkan Tipe**: Satu agregat `SuratJalanManifest` mampu menangani 4 skenario logistik pabrik tanpa redundansi tabel, tetapi masing-masing tipe memiliki gerbang validasi yang ketat.

---

### Blok B: Peleburan Bundle ke Masal pada Makloon Outbound

Di `core/src/commonMain/kotlin/com/eventverse/app/domain/transfer/usecases/CreateMakloonOutboundSuratJalanUseCase.kt`:

```kotlin
// Agregasi kuantitas bundle per ukuran (size) menjadi baris lot masal
val aggregatedBySize = cards.groupBy { it.sizeLabel }
    .mapValues { (_, cardList) -> cardList.sumOf { if (it.wipPcs > 0) it.wipPcs else it.queuedPcs } }

val items = aggregatedBySize.entries.mapIndexed { index, (sizeLabel, totalPcs) ->
    SuratJalanItem(
        id = "item-sub-${command.sjNumber.value}-$index",
        workCardId = null, // Digabung masal
        bundleNo = null,   // Bundle dilebur untuk vendor luar
        cartonId = null,
        sizeLabel = sizeLabel,
        qtyPcs = totalPcs,
        notes = "Lot Makloon - $totalPcs pcs (dari ${cards.count { it.sizeLabel == sizeLabel }} bundle)"
    )
}

// Tandai seluruh kartu kerja yang dikirim menjadi SUBCONTRACTED
val updatedCards = cards.map { card ->
    card.copy(
        executionMode = WorkExecutionMode.SUBCONTRACTED,
        vendorRef = command.vendorRef
    )
}
cardRepository.saveAll(updatedCards)
```

**Mengapa blok ini ditulis begini?**
- **Transformasi Granularitas**: Kartu-kartu kerja kecil (misal 5 bundle @ 20 pcs) dilebur menjadi satu baris data 100 pcs untuk Surat Jalan vendor.
- **Sinkronisasi Mode Eksekusi**: Kartu kerja internal tidak dihapus, melainkan diperbarui statusnya menjadi `WorkExecutionMode.SUBCONTRACTED` dengan referensi nama vendor. Hal ini mencegah operator internal mengklaim upah borongan atas pekerjaan yang dikerjakan pihak luar!

---

### Blok C: Pelacakan Backlog pada Pengiriman Parsial Buyer

Di `core/src/commonMain/kotlin/com/eventverse/app/domain/transfer/usecases/CreatePartialCustomerShipmentUseCase.kt`:

```kotlin
val thisShipmentPcs = command.cartons.sumOf { it.qtyPcs }
val newTotalShipped = command.previouslyShippedPcs + thisShipmentPcs
val remainingBacklog = (command.totalOrderedPcs - newTotalShipped).coerceAtLeast(0)
val isFullyShipped = newTotalShipped >= command.totalOrderedPcs
```

**Mengapa blok ini ditulis begini?**
- Pengiriman pakaian ke buyer dilakukan berbasis nomor kardus/karung (`cartonId`).
- Use Case secara otomatis menghitung status pengiriman: apakah pengiriman saat ini menyelesaikan seluruh pesanan (`isFullyShipped == true`), ataukah masih menyisakan tunggakan (`remainingBacklogPcs > 0`).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pilihan Arsitektur | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Single Shared Codec (`SuratJalanCodec.kt`)** | Serialisasi terpisah di Client dan Server | Menjamin kedua sisi selalu berbicara dalam format JSON yang sama persis tanpa drift. | Server mengubah nama field, client crash secara diam-diam. |
| **Pemisahan Logika Internal vs Vendor** | Satu Surat Jalan generik tanpa validasi bundle | Menghormati realitas fisik pabrik: internal butuh pelacakan bundle, vendor butuh ringkasan lot. | Operator internal kehilangan nomor ikatan borongan, atau vendor menolak faktur karena terlalu rumit. |
| **Claymorphism + Canvas Vector Icons** | Emoji mentah di label UI (`🚚`, `📦`, `✅`) | Menghindari rendering tofu (`▯`) pada Compose Wasm / Skiko di web browser. | UI web terlihat rusak dengan kotak-kotak kosong saat dibuka di browser Linux/Windows. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Asal Gabung Bundle di Mutasi Internal**
   - *Kenapa bahaya*: Jika sopir membawa potongan baju dari Gedung A ke Gedung B lalu menggabungkannya jadi satu angka total tanpa nomor bundle, operator jahit di Gedung B tidak tahu kain mana yang satu set warna/serat (shading). Pakaian bisa belang saat dijahit!
   - *Solusi kita*: Validasi invariant `require(items.all { it.bundleNo != null })` di level domain.
2. **Jebakan 2: Double-Claim Upah Borongan pada Pekerjaan Makloon**
   - *Kenapa bahaya*: Baju dikirim ke vendor luar, tapi kartu kerja di pabrik tetap berstatus `IN_HOUSE`. Operator pabrik bisa memindai barcode dan mengklaim upah atas barang yang dikerjakan vendor.
   - *Solusi kita*: Use Case `CreateMakloonOutboundSuratJalanUseCase` secara otomatis mengunci `executionMode = WorkExecutionMode.SUBCONTRACTED`.
3. **Jebakan 3: Menggunakan Emoji Mentah di UI Web**
   - *Kenapa bahaya*: Skiko Wasm tidak mengikutsertakan emoji font OS secara default.
   - *Solusi kita*: Gunakan icon berbasis Canvas murni dari `ClayIcons.kt` (`IconTruck`, `IconPackage`, `IconCheck`).

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian dilakukan di level Unit Test Domain:
1. Jalankan unit test:
   ```bash
   ./gradlew :core:jvmTest --tests "*SuratJalan*" --tests "*Transfer*"
   ```
2. Verifikasi kompilasi multiplatform:
   ```bash
   ./gradlew :server:compileKotlin
   ./gradlew :app:shared:compileKotlinJvm
   ./gradlew :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs
   ```

Semua pengujian lolos 100% dan memvalidasi siklus hidup:
- Pembuatan DRAFT ➔ DISPATCHED ➔ RECEIVED
- Penolakan mutasi internal jika lokasi asal == lokasi tujuan
- Penguncian mode vendor saat makloon diterbitkan
- Perhitungan backlog pengiriman kardus ke buyer

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Tambahkan validasi batas berat maksimal (kg) per kendaraan (`vehiclePlate`) saat menerbitkan Surat Jalan Mutasi Internal.
- [ ] **Tantangan 2**: Buat dialog cetak PDF Surat Jalan resmi ukuran setengah folio (A5 landscape) menggunakan library PDF exporter yang sudah ada di `server/infrastructure/pdf/`.
- [ ] **Tantangan 3**: Tambahkan fitur foto bukti timbangan digital saat barang diterima kembali dari vendor makloon.
