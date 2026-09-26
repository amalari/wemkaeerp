# 🎓 Modul Pembelajaran: Modul Antrian Kanban Stasiun Kerja Konveksi (`domain/workqueue`)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Shop Floor Kanban, Dynamic Routing Bypass, Bundle-to-Lot Melting, Defect Ticket Lifecycle, Piece-rate Tariff Integrity  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform, Value Objects, dan Invariant Aggregates  
> **Referensi Modul**: `core/src/commonMain/kotlin/com/eventverse/app/domain/workqueue/`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Bayangkan kamu sedang membangun sistem untuk pabrik konveksi dan pakaian rajut (*knitwear*). Di atas kertas, alur produksi sering digambarkan sederhana: `Potong ➔ Jahit ➔ Finishing ➔ Kirim`. Namun ketika kamu turun ke lantai pabrik yang sebenarnya, kamu akan menemukan **tiga dinamika lapangan yang sangat kompleks**:

1. **Dilema Ikat Bundle vs Mesin Cuci Masal:**
   * Di meja potong dan jahit, potongan kain diikat dalam bendel-bendel kecil berukuran 20–24 pcs (disebut **Bundle**) agar tidak tercecer dan ukuran tidak tertukar.
   * Tapi begitu sampai di stasiun **Washing (Pencucian)**, tali pengikat bundle **wajib dilepas** dan ratusan potong baju dicampur ke dalam drum mesin cuci masal. Jika sistem memaksakan pelacakan berbasis bundle setelah cuci, operator setrika dan packing akan mogok kerja karena harus membongkar ratusan baju demi mencari nomor bundle aslinya!
2. **Kekacauan Baju Cacat (Defect) & Klaim Upah Ganda:**
   * Saat staf QC memeriksa baju dan menemukan 4 pcs cacat jahitan dan 2 pcs afkir (scrap total), bagaimana mencatatnya?
   * Jika tidak dipisahkan lewat **Tiket Reparasi (Rework Ticket)**, status stok pesanan akan berantakan dan tidak seimbang. Parahnya lagi, jika penjahit yang memperbaiki baju cacat tersebut menyetorkan kembali pekerjaannya, sistem bodoh akan menggaji dia dua kali untuk baju yang sama!
3. **Variasi Konveksi & Alur Stasiun Opsional (Bypass):**
   * Konveksi kaos oblong tidak butuh mesin lubang kancing atau suntek linking rajut.
   * Pabrik rajut *fully-fashioned* tidak punya meja potong (langsung turun mesin rajut).
   * Jika kamu meng-hardcode 11 stasiun dalam `enum class` dengan urutan kaku, alur kerja kaos oblong akan macet di pos kancing yang tidak pernah ada pekerjanya!

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun modul ini dari layar kosong, berikut urutan penulisan yang benar:

### Langkah 0: Desain Mental & Pembedaan Satuan Lacak
* **Mental Model**: Stasiun kerja adalah **baris data konfigurasi (`WorkStationSpec`)**, bukan `enum class` dengan puluhan `when (station)`.
* Tentukan titik gerbang peleburan (*Merge Gate*): Hulu dilacak per `BUNDLE`, hilir dilacak per `LOT_ACCUMULATION`.

### Langkah 1: Value Classes & Enums Domain (`WorkQueueValueObjects.kt`)
* Buat tipe identitas aman: `WorkCardId`, `WorkDepositId`, `ReworkTicketId`, `WorkStationCode`, `DefectCode`.
* Definisikan status: `WorkCardStatus`, `WorkTrackingUnit`, `WorkExecutionMode`, `ReworkTicketStatus`.

### Langkah 2: Spesifikasi & Katalog Bawaan (`WorkStationSpec.kt`, `WorkDefectCatalog.kt`)
* Tulis spesifikasi stasiun dengan aturan merge gate.
* Buat fungsi `nextAfter(current, activeStations)` yang mendukung pelompatan stasiun opsional (*dynamic bypass*).
* Buat katalog cacat yang langsung memetakan masalah ke stasiun penanggung jawab (*Defect Routing*).

### Langkah 3: Agregat Inti & Invarian Bisnis (`WorkCard.kt`, `ReworkTicket.kt`, `WorkDeposit.kt`)
* Tegakkan aturan: Kartu `BUNDLE` wajib punya `bundleNo > 0`; kartu `LOT_ACCUMULATION` wajib `bundleNo == null`.
* Catat `tariffSnapshotIdr` pada `WorkDeposit` agar riwayat upah tidak rusak saat tarif stasiun diubah di masa depan.
* Cegah penutupan langsung pada `ReworkTicket` tanpa melewati status `READY_FOR_RE_CHECK`.

### Langkah 4: Kalkulator Rekonsiliasi (`WorkQueueBalance.kt`)
* Rumus identitas fisik wajib balance:
  $$\text{OrderedPcs} = \text{WipPcs} + \text{InRepairPcs} + \text{ScrapPcs} + \text{FinishedPcs}$$

### Langkah 5: Use Cases Terisolasi (`usecases/*`)
* `InitializeWorkCardsFromCuttingUseCase`: Generator bundle 20 pcs.
* `RecordStationOutputUseCase`: Setoran operator & akumulasi upah.
* `IssueReworkTicketUseCase`: Pemisahan kuantitas cacat ke nampan reparasi.
* `CloseBundlesAtMergeGateUseCase`: Peleburan bundle di mesin washing.
* `AdvanceReworkTicketUseCase`: State machine perbaikan.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Dynamic Bypass di `WorkStationCatalog.kt`

```kotlin
fun nextAfter(
    current: WorkStationCode,
    activeStations: Set<WorkStationCode>? = null,
    customStations: List<WorkStationSpec> = emptyList()
): WorkStationCode? {
    val fullLine = line(customStations)
    val currentIndex = fullLine.indexOfFirst { it.code == current }
    if (currentIndex < 0 || currentIndex >= fullLine.lastIndex) return null

    for (i in (currentIndex + 1)..fullLine.lastIndex) {
        val candidate = fullLine[i].code
        // Jika activeStations didefinisikan, lewati stasiun yang tidak aktif!
        if (activeStations == null || candidate in activeStations) {
            return candidate
        }
    }
    return null
}
```

**Mengapa blok ini ditulis begini?**
- Jika pesanan adalah kaos oblong polos, Tech Pack hanya menyertakan `CUTTING, JAHIT_LURUS, OBRAS, STEAM, QC_FINAL, PACKAGING`.
- Saat operator obras menuntaskan bundle, sistem memanggil `nextAfter(OBRAS, activeStations)`. Sistem mendeteksi bahwa `SUNTEK`, `LUBANG_KANCING`, `PASANG_KANCING`, `PASANG_ZIPER`, dan `WASHING` tidak ada dalam `activeStations`.
- Kartu tugas **langsung melompat ke STEAM** tanpa perlu intervensi manual dari supervisor!

---

### Blok B: Invarian Satuan Lacak di `WorkCard.kt`

```kotlin
when (trackingUnit) {
    WorkTrackingUnit.BUNDLE -> {
        require(bundleNo != null && bundleNo > 0) {
            "Bundle tracking unit requires a positive bundleNo, got $bundleNo"
        }
    }
    WorkTrackingUnit.LOT_ACCUMULATION -> {
        require(bundleNo == null) {
            "Lot accumulation unit cannot have bundleNo, got $bundleNo"
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- Ini adalah implementasi *Make Illegal States Unrepresentable*.
- Kartu lot di stasiun setrika atau packing tidak boleh memiliki nomor bundle, karena bundle fisiknya sudah bubar di drum mesin cuci.
- Sebaliknya, kartu jahit di meja obras tidak boleh tanpa nomor bundle, karena penjahit bekerja berdasarkan ikatan bundle fisik.

---

### Blok C: Snapshot Tarif Upah & Anti-Klaim Ganda di `WorkDeposit.kt`

```kotlin
data class WorkDeposit(
    val id: WorkDepositId,
    val cardId: WorkCardId,
    val operatorId: String,
    val operatorName: String,
    val qtyPcs: Int,
    val tariffSnapshotIdr: Long = 0L,
    val isReworkDeposit: Boolean = false,
    val submittedAt: Instant
) {
    val earnedPayIdr: Long
        get() = if (isReworkDeposit) 0L else qtyPcs.toLong() * tariffSnapshotIdr
}
```

**Mengapa blok ini ditulis begini?**
1. **Snapshot Tarif:** Jika bulan depan manajemen menaikkan tarif obras dari Rp 1.000 ke Rp 1.500, upah yang tercatat bulan lalu tetap dihitung dengan Rp 1.000 (tarif saat transaksi terjadi).
2. **Anti-Klaim Ganda:** Jika penjahit memperbaiki cacat kerah miring yang dia buat sendiri (`isReworkDeposit == true`), properti `earnedPayIdr` bernilai `0`. Penjahit bertanggung jawab atas kesalahannya tanpa membebani keuangan pabrik.

---

### Blok D: Siklus Tiket Reparasi di `ReworkTicket.kt`

```kotlin
fun closeAsPassed(notes: String = "", now: Instant): ReworkTicket {
    require(status == ReworkTicketStatus.READY_FOR_RE_CHECK) {
        "Cannot close ticket directly from $status; must go through READY_FOR_RE_CHECK inspection"
    }
    return copy(
        status = ReworkTicketStatus.CLOSED,
        qcNotes = if (notes.isNotBlank()) notes else qcNotes,
        closedAt = now
    )
}
```

**Mengapa blok ini ditulis begini?**
- Penjahit yang telah membongkar dan menjahit ulang pakaian cacat **tidak punya wewenang meloloskan bajunya sendiri**.
- Penjahit hanya bisa menekan `markReadyForRecheck()`.
- Hanya staf QC yang berhak menekan `closeAsPassed()` setelah menguji ulang hasil perbaikan secara fisik.

---

## 🔬 4. Technology & Approach ("The Why")

| Pendekatan yang Dipilih | Alternatif Lain yang Ditolak | Alasan & Risiko yang Dihindari |
|---|---|---|
| **Stasiun sebagai Baris Data (`WorkStationSpec`)** | `enum class WorkStationKind` kaku | Enum menutup himpunan. Tenant konveksi baru yang butuh stasiun unik (misal *Meja Sablon Manual* atau *Plisket*) tidak bisa menambah stasiun tanpa merilis ulang aplikasi. |
| **Setoran Append-Only (`WorkDeposit`)** | Counter angka kumulatif yang ditimpa | Laporan upah mingguan penjahit mustahil diaudit jika sistem hanya menyimpan satu angka total kumulatif. Dengan append-only, sistem mencatat detail: siapa setor, jam berapa, berapa pcs. |
| **Defect Routing berbasis Join Tabel** | Percabangan `when (defect)` di kode | Jenis cacat dapat dipetakan langsung ke stasiun tujuan (`obras_lepas` $\rightarrow$ `OBRAS`, `noda_oli` $\rightarrow$ `STEAM`). Penambahan jenis cacat baru tidak memerlukan perubahan logic domain. |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Jebakan "Semua Stasiun Pasti Dilewati":**
   * *Kesalahan*: Menghubungkan stasiun secara statis $1 \rightarrow 2 \rightarrow 3 \dots 11$.
   * *Akibat*: Baju kaos oblong terdampar di antrean lubang kancing atau suntek yang tidak pernah disentuh pekerja.
   * *Solusi*: Selalu gunakan parameter `activeStations` saat memanggil `nextAfter()`.
2. **Jebakan "Upah Menggunakan Tarif Terkini":**
   * *Kesalahan*: Menghitung upah borongan dengan rumus $\sum \text{qty} \times \text{spec.tariff}$.
   * *Akibat*: Saat tarif dinaikkan, seluruh rekap upah bulan-bulan lalu ikut berubah secara retroaktif dan merusak laporan keuangan.
   * *Solusi*: Selalu simpan snapshot tarif di dalam baris setoran (`tariffSnapshotIdr`).
3. **Jebakan Menutup Gerbang Cuci saat Masih Ada Bundle Menggantung:**
   * *Kesalahan*: Operator menekan *Terima & Cuci Masal* padahal masih ada bundle yang belum tuntas dijahit.
   * *Akibat*: Kuantitas pakaian di stasiun hilir menjadi tidak seimbang dengan kain yang dipotong.
   * *Solusi*: Use case `CloseBundlesAtMergeGateUseCase` melempar `require()` exception jika masih ada bundle dengan `wipPcs > 0`.

---

## 🎯 6. Verifikasi & Tantangan Mandiri

### Cara Menguji Kebenaran Modul:
Jalankan unit test suite berikut:
```bash
./gradlew :core:jvmTest --tests "*WorkQueue*" --tests "*WorkCard*" --tests "*ReworkTicket*"
```

### Tantangan untuk Kamu:
1. **Tambahkan Stasiun Kustom:**
   * Coba buat stasiun kustom `SABLON_MANUAL` dengan archetype `SEWING`, tarif Rp 1.200/pcs, dan letakkan di antara `CUTTING` dan `JAHIT_LURUS`. Uji apakah fungsi `nextAfter` berjalan mulus.
2. **Uji Kasus Keseimbangan Stok:**
   * Buat skenario pesanan 500 pcs: 480 pcs lolos packing, 15 pcs masuk tiket reparasi obras, dan 5 pcs dinyatakan kain sobek (scrap). Pastikan `WorkQueueBalance.isBalanced` bernilai `true` dan `reconciliationDeltaPcs == 0`.
