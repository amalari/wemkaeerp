# 🎓 Modul Pembelajaran: SPK Split per Ukuran (1 PO / Desain Multi-Size → N SPK Mandiri)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Full-Stack Kotlin Multiplatform (KMP), Compose Web & Desktop, Flyway Migration, Idempotent SPK Generation  
> **Cakupan**: **Fase 1** (Produksi Massal / `BulkWorkOrder`) & **Fase 2** (Siklus Sampling / `SamplingOrder`)  
> **Referensi Task**: Split SPK per Ukuran (ALL SIZE + S → 2 SPK Sampling terpisah)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata di Pabrik Garmen
Bayangkan sebuah brand baju memesan desain "Hoodie Rajut Vintage" dengan 2 pcs ukuran **ALL SIZE** dan 2 pcs ukuran **S**.
Jika sistem hanya membuat **1 lembar SPK (Surat Perintah Kerja)** untuk kedua ukuran tersebut:
1. **Lantai Produksi (Workbench) Bingung**: Operator rajut membuat settingan gramasi, ketebalan benang, dan program mesin (CAM) yang berbeda antara `ALL SIZE` dan `S`. Lembar SPK fisik atau kartu antrian digital tidak bisa berada di 2 stasiun kerja sekaligus.
2. **Pelacakan (Traceability) Buram**: Saat ukuran `ALL SIZE` sudah selesai dirajut dan masuk tahap QC, sedangkan ukuran `S` masih tertahan di mesin obras, status SPK menjadi tidak jelas: apakah "IN_PROGRESS", "QC", atau "SELESAI"?
3. **Surat Jalan & Approval Buyer**: Buyer mungkin menyetujui sampel `ALL SIZE` terlebih dahulu, sementara ukuran `S` minta revisi panjang lengan. Satu SPK tunggal tidak dapat memisahkan status approval per ukuran.

### Solusi Arsitektural Kita
**1 Desain Multi-Size → N SPK Mandiri**.
- Desain tetap 1 entitas induk (memiliki 1 Size Chart matriks bersama dan Mockup visual bersama).
- Namun saat diterbitkan ke lantai kerja sampling, sistem menerbitkan **1 SPK unik per ukuran aktif**:
  - `SPK-SMP-0050` untuk ukuran `ALL SIZE` (qty: 2 pcs, root order).
  - `SPK-SMP-0051` untuk ukuran `S` (qty: 2 pcs, child order dengan `parentSamplingOrderId = smp-seed-0050`).
- Di kartu Deal Detail, kedua SPK ditampilkan di dalam kartu desain yang sama dengan tombol aksi mandiri.
- Di Meja Kerja Sampling (Kanban Meja Kerja), masing-masing SPK memiliki kartunya sendiri (`ALL SIZE • 2 Pcs` dan `S • 2 Pcs`) yang dapat digeser dan dikerjakan secara independen!

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun fitur full-stack seperti ini dari nol, jangan pernah langsung loncat mengetik tampilan UI Compose. Ikuti 5 pilar berurutan:

```
[Pilar 1: Database & Persistence] → [Pilar 2: Pure Domain (core)] → [Pilar 3: Backend API (server)] → [Pilar 4: Client Network (shared)] → [Pilar 5: Presentation UI]
```

1. **Langkah 1: Pure Domain Layer (`core/`)**
   - Tambahkan `sizeLabel: String?` dan `parentSamplingOrderId: SamplingOrderId?` ke dalam `SamplingOrder`.
   - Buat fungsi ekstensi domain matriks ukuran di `SamplingSizeMatrix.kt`: `activeSizesWithAllocatedQty(matrix): List<Pair<String, Int>>`.
   - Buat Use Case murni: `PublishSamplingSpkFromDealUseCase.kt` yang mengekstrak ukuran aktif dan menerbitkan N SPK secara atomik & idempoten.
   - Perbarui `ApproveSamplingFromDealUseCase.kt` agar approval mengalir ke seluruh SPK anak dari desain tersebut.
   - Sinkronkan `SamplingOrderCodec.kt` (JSON serialization) untuk platform multiplatform JS & Wasm.

2. **Langkah 2: Database & Skema Persistence (`server/`)**
   - Buat migrasi Flyway baru: `V68__sampling_order_size_label_and_split.sql` dengan `ADD COLUMN IF NOT EXISTS size_label` dan `parent_sampling_order_id`.
   - Update definisi Exposed di `SamplingTables.kt`.
   - Refaktor `PostgresSamplingOrderRepository.kt` untuk membaca dan menulis kedua kolom baru, serta pastikan `nextSpkNumber` menghitung nomor tertinggi `(highest + 1)` agar tidak terjadi tabrakan nomor SPK berurutan.

3. **Langkah 3: Backend API & Routing (`server/`)**
   - Buat endpoint baru di `DealRoutes.kt`: `POST /api/tenant/deals/{id}/sampling-orders/{samplingId}/publish-spk`.
   - Return daftar SPK yang terbit dalam format array `[SamplingOrder]`.

4. **Langkah 4: Client-Server Integration (`app/shared/`)**
   - Tambahkan `publishSamplingSpk` di `DealRemoteDataSource.kt` dan `DealApiClient.kt`.
   - Hubungkan ke `DealViewModel.kt` pada fungsi `createSamplingSpk`.

5. **Langkah 5: Shared Presentation Layer (`app/shared/presentation/`)**
   - Di `DealUiState.kt`, buat grouping `designRoots` (order dengan `parentSamplingOrderId == null`).
   - Di `DealDetailDialog.kt`, render kartu desain berdasarkan root, lalu kumpulkan seluruh SPK terkait (`root.id` + child orders) ke dalam `SamplingDesignCard`.
   - Di `ConfirmSpkDialog.kt`, tampilkan edukasi dinamis: *"Ya, Terbitkan N SPK Sampling"* berdasarkan jumlah ukuran yang memiliki kuota > 0.
   - Di `SamplingKanbanCard.kt`, tampilkan badge `ClayTag` ukuran: `${order.sizeLabel} • ${order.sampleQuantity} Pcs`.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Ekstraksi Ukuran Aktif dari Matriks (`SamplingSizeMatrix.kt`)

```kotlin
fun activeSizesWithAllocatedQty(matrix: List<SizeChartRow>): List<Pair<String, Int>> {
    val qtyRow = matrix.find { it.id == SAMPLING_QTY_ROW_ID } ?: return emptyList()
    return qtyRow.values.mapNotNull { (size, qtyStr) ->
        val qty = qtyStr.toIntOrNull() ?: 0
        if (qty > 0) size to qty else null
    }
}
```
**Mengapa blok ini ditulis begini?**
- Baris `SAMPLING_QTY_ROW_ID` adalah baris khusus kuantitas sampel per ukuran di size chart.
- Dengan `activeSizesWithAllocatedQty`, kita hanya memproses ukuran yang memiliki nilai kuantitas > 0. Ukuran dengan nilai `0` atau kosong tidak akan memboroskan nomor SPK.
- Hasil berupa `List<Pair<String, Int>>` memberikan nama ukuran (`ALL SIZE`, `S`) dan jumlah pcs (`2`, `2`) sekaligus.

---

### Blok B: Penerbitan SPK Terpisah di Use Case (`PublishSamplingSpkFromDealUseCase.kt`)

```kotlin
// 1. Ekstrak ukuran yang punya alokasi qty > 0
val activeSizes = activeSizesWithAllocatedQty(rootOrder.sizeMatrix)
val sizesToProcess = if (activeSizes.isNotEmpty()) activeSizes else listOf("ALL SIZE" to rootOrder.sampleQuantity)

// 2. Cek apakah child SPK sudah pernah dibuat sebelumnya (Idempotensi)
val existingDealOrders = samplingRepository.findByDealId(command.tenantId, command.dealId)
val existingForThisRoot = existingDealOrders.filter { 
    it.id == rootOrder.id || it.parentSamplingOrderId == rootOrder.id 
}

// 3. Update Root Order untuk ukuran pertama
val firstSize = sizesToProcess.first()
val updatedRoot = rootOrder.copy(
    sizeLabel = firstSize.first,
    sampleQuantity = firstSize.second,
    status = SamplingStatus.IN_PROGRESS,
    updatedAt = now
)
samplingRepository.save(updatedRoot)

// 4. Terbitkan SPK baru untuk ukuran ke-2, ke-3, dst.
val childOrders = mutableListOf<SamplingOrder>()
for (i in 1 until sizesToProcess.size) {
    val (sizeLabel, qty) = sizesToProcess[i]
    val alreadyExists = existingForThisRoot.find { 
        it.parentSamplingOrderId == rootOrder.id && it.sizeLabel.equals(sizeLabel, ignoreCase = true) 
    }
    if (alreadyExists != null) {
        val activated = alreadyExists.copy(status = SamplingStatus.IN_PROGRESS, updatedAt = now)
        samplingRepository.save(activated)
        childOrders.add(activated)
    } else {
        val newSpkNumber = samplingRepository.nextSpkNumber(command.tenantId)
        val child = SamplingOrder(
            id = SamplingOrderId(idGenerator()),
            tenantId = command.tenantId,
            spkNumber = newSpkNumber,
            clientName = rootOrder.clientName,
            styleName = rootOrder.styleName,
            status = SamplingStatus.IN_PROGRESS,
            sizeLabel = sizeLabel,
            parentSamplingOrderId = rootOrder.id,
            sampleQuantity = qty,
            sizeMatrix = rootOrder.sizeMatrix,
            ...
        )
        samplingRepository.save(child)
        childOrders.add(child)
    }
}
```
**Mengapa blok ini ditulis begini?**
- **Root Order Reuse**: Root order yang sudah ada di database tidak kita hapus atau duplikasi. Kita menggunakannya kembali untuk ukuran pertama (`ALL SIZE`), sehingga ID referensi lama tidak rusak.
- **Idempotensi**: Jika user menekan tombol "Terbitkan SPK" dua kali karena lag jaringan, use case memeriksa `existingForThisRoot`. Sistem tidak akan menduplikasi SPK yang sudah pernah terbit untuk ukuran yang sama.
- **Transisi Deal Otomatis**: Setelah minimal 1 SPK terbit, status Deal otomatis dimajukan ke `PO_RECEIVED` (`DealStage.PO_RECEIVED`).

---

### Blok C: Pengelompokan Kartu Desain di UI Presentation (`DealUiState.kt` & `DealDetailDialog.kt`)

```kotlin
// Di DealUiState.kt:
val designRoots: List<SamplingOrder>
    get() = samplingOrders.filter { it.parentSamplingOrderId == null }
```

```kotlin
// Di DealDetailDialog.kt:
val roots = state.designRoots
roots.forEachIndexed { index, root ->
    val spkOrders = state.samplingOrders.filter { 
        it.id == root.id || it.parentSamplingOrderId == root.id 
    }
    SamplingDesignCard(
        designNumber = index + 1,
        rootOrder = root,
        spkOrders = spkOrders,
        ...
    )
}
```
**Mengapa blok ini ditulis begini?**
- Seorang buyer memesan **1 Desain**. Bagi buyer dan tim sales, ini adalah 1 kartu desain.
- Namun bagi tim produksi, kartu tersebut memiliki **N SPK** (`SPK-SMP-0050 (ALL SIZE)`, `SPK-SMP-0051 (S)`).
- Dengan memfilter `designRoots`, tab Deal Detail tetap menampilkan 1 kartu desain yang rapi, namun di dalamnya terdapat daftar seluruh SPK yang terbit beserta tombol `[Lihat SPK]` untuk masing-masing ukuran.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Root-Child Hierarchy (`parentSamplingOrderId`)** | Tabel relasi baru `deal_design_spk_links` | Tidak perlu tabel join baru; relasi self-referencing pada `sampling_orders` sangat sederhana dan didukung cascade delete | Menambah kompleksitas query DB, overhead join, dan risiko sync data |
| **Generasi Nomor SPK Incremental Tunggal** | Nomor ber-subkode (`SPK-0050-A`, `SPK-0050-B`) | Penomoran fisik pabrik menggunakan barcode scanner standar yang mengharapkan pola numerik seragam `SPK-SMP-XXXX` | Scanner barcode gudang atau form cetak fisik sering error jika pola nomor SPK berubah-ubah format karakternya |
| **Pure Domain Matriks Helper di `core/`** | Parsing matriks di UI Composable | Logic penentuan kuota per ukuran murni bebas framework, dapat diuji dengan Unit Test murni secepat kilat | Logic parsing tersebar di UI, rawan bug perbedaan hitungan antara Android, Desktop, dan Web |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: File Size Limit & Aturan Ratchet (`PostgresSamplingOrderRepository.kt`)**
   - *Masalah*: Menambahkan kolom baru dan helper mapper ke file repository yang sudah panjang (> 700 baris) akan melanggar Rule 14 (Hard Limit & Ratchet).
   - *Solusi Elegan*: Ekstraksi fungsi parsing JSON dan mapping DTO ke file terpisah: `SamplingOrderPersistenceMapper.kt`. Hasilnya ukuran `PostgresSamplingOrderRepository.kt` berkurang dari **702 baris** menjadi **622 baris** (surplus 80 baris!).
2. **Jebakan 2: Nomor SPK Tabrakan pada Loop Terbit Bersamaan**
   - *Masalah*: Jika `nextSpkNumber` hanya membaca `COUNT(*)`, maka ketika SPK 1 disimpan dan SPK 2 diproses dalam loop cepat, nomor SPK bisa duplikat.
   - *Solusi Elegan*: Gunakan query `MAX(spk_number)` di repository Postgres: `(highestNumber + 1)`.
3. **Jebakan 3: Emoji di Label Ukuran atau Tag SPK**
   - *Masalah*: Menulis teks seperti `"🏷️ ALL SIZE"` akan merender karakter kotak kosong / tofu (`▯`) di Compose Web (Wasm).
   - *Solusi Elegan*: Gunakan selalu `ClayTag` dengan warna semantik (`WeMadeColors.Primary`) dan ikon Canvas `ClayIcons` bila diperlukan.

---

## 🧪 6. Cara Membuktikan Kodingan Bekerja (Verifikasi)

### 1. Unit Test Murni (`core`)
Jalankan pengujian unit use case yang memverifikasi split N SPK:
```bash
./gradlew :core:jvmTest --tests "com.eventverse.app.domain.sampling.PublishSamplingSpkFromDealUseCaseTest"
```
✅ **Hasil**: 3 test lulus dalam 750ms:
- `publish_withMultiSize_shouldSplitIntoMultipleSpks`: Memastikan 1 root order dengan `ALL SIZE` (2 pcs) dan `S` (2 pcs) menghasilkan 2 SPK terpisah (`SPK-SMP-0050` dan `SPK-SMP-0051`).
- `publish_isIdempotent_shouldNotDuplicateSpks`: Memastikan pemanggilan berulang tidak membuat duplikat SPK.
- `publish_withSingleSize_shouldPublishSingleSpk`: Memastikan jika hanya ada 1 ukuran aktif, hanya 1 SPK yang terbit.

### 2. Multiplatform Build Compilation
Pastikan seluruh target backend dan frontend multiplatform berhasil dikompilasi tanpa error:
```bash
./gradlew :server:compileKotlin :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs
```
✅ **Hasil**: Seluruh target build berstatus **BUILD SUCCESSFUL**.

### 3. PostgreSQL Database Verification
Periksa data di database PostgreSQL container:
```bash
docker exec wemade-postgres psql -U postgres -d wemade_erp -c "SELECT id, spk_number, size_label, sample_quantity, parent_sampling_order_id, deal_id FROM sampling_orders WHERE id LIKE 'smp-seed-0050%';"
```
```text
       id        |  spk_number  | size_label | sample_quantity | parent_sampling_order_id |    deal_id    
-----------------+--------------+------------+-----------------+--------------------------+---------------
 smp-seed-0050   | SPK-SMP-0050 | ALL SIZE   |               2 |                          | deal-seed-005
 smp-seed-0050-s | SPK-SMP-0051 | S          |               2 | smp-seed-0050            | deal-seed-005
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka `PublishSamplingSpkFromDealUseCaseTest.kt`, tambahkan skenario test di mana user mengisi 3 ukuran (`S`, `M`, `L`) dengan masing-masing kuota 1 pcs. Verifikasi bahwa SPK 1 mengambil ukuran `S`, SPK 2 mengambil `M`, dan SPK 3 mengambil `L`, dengan total 3 SPK terbit.
- [ ] **Tantangan 2**: Pelajari `SamplingOrderPersistenceMapper.kt`. Mengapa serialisasi JSONB lebih baik didelegasikan ke mapper murni daripada ditumpuk langsung di dalam method repository? Diskusikan prinsip Single Responsibility Principle (SRP) di balik keputusan ini.
