# 🎓 Modul Pembelajaran: Peleburan Bundle Berfoto di Stasiun Cuci & Rekonsiliasi Lot Pasca-Dryer Menuju Setrika

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Physical Lot Melting (Merge Gate), Chain of Custody Audit Trail, Reconciliation Balances, 5 Pilar Full-Stack WeMade ERP  
> **Prasyarat**: Memahami pembedaan `WorkTrackingUnit.BUNDLE` vs `WorkTrackingUnit.LOT_ACCUMULATION`, dasar Exposed PostgreSQL, dan Claymorphism Compose Multiplatform.  
> **Referensi Modul**: `core/.../domain/workqueue/`, `server/.../routes/WashingBatchRoutes.kt`, `app/shared/.../presentation/washing/`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Di pabrik konveksi dan garment skala massal, kebocoran barang paling sering terjadi di **zona transisi pencucian (Washing)**:

```
[ Meja Potong / Jahit ]              [ Drum Mesin Cuci Masal ]             [ Meja Setrika & QC ]
   Satuan: BUNDLE                        Satuan: BULK BATCH                    Satuan: LOT ACCUMULATION
• Ikat 20–25 potong per bundle        • Tali bundle wajib dilepas           • Dipisah kembali per PO & per Ukuran
• Ada tiket kertas berpeniti          • Kertas tiket hancur terkena air     • Siap masuk QC & polybag packing
• Tiap penjahit bawa bendel           • Ratusan pcs diaduk dalam drum       • Dicocokkan ke Surat Jalan PO
```

### Mengapa Barang Sering Hilang di Sini?
1. **Tiket Kertas Hancur**: Sebelum masuk drum cuci industri, tali pengikat bundle harus dibuka agar air, deterjen, dan pelembut (*softener*) membasahi kain secara merata. Akibatnya, kertas identitas bundle tidak bisa ikut dicuci.
2. **Ketiadaan Bukti Serah Terima (*Chain of Custody*)**: Seringkali penjahit mengaku menyetor 5 bundle @ 20 pcs (100 pcs). Tapi setelah keluar dari mesin pengering (*tumbler*), dihitung hanya ada 96 pcs. Penjahit menyalahkan tukang cuci, tukang cuci menyalahkan penjahit.
3. **Pencampuran PO yang Mematikan Divisi Setrika**: Jika setelah keluar dari pengering baju dibiarkan menumpuk campur aduk tanpa dipisah kembali per PO dan per Ukuran, operator setrika dan tim packing akan pusing mencari mana baju pesanan Buyer A dan mana Buyer B.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun fitur fisik yang kompleks seperti ini dari nol, jangan pernah langsung membuat UI! Ikuti urutan 5 Pilar Full-Stack (§13):

```
[ Langkah 1: Skema Database Flyway (V70) ]
        │  → Tabel `washing_batches`, `washing_batch_items`, `washing_batch_sort_outputs`
        ▼
[ Langkah 2: Pure Domain Layer (core) ]
        │  → Invarian `bundlePhotoKey.isNotBlank()` & State Machine Batch
        │  → Use Case: `CreateWashingBatchUseCase` & `CompleteWashingSortToLotsUseCase`
        ▼
[ Langkah 3: Backend API & Ktor Routing (server) ]
        │  → Endpoint `/pending-bundles`, `/batches`, `/complete-sort`
        │  → Repositori Exposed `PostgresWashingBatchRepository`
        ▼
[ Langkah 4: Client-Server Integration (app/shared) ]
        │  → `WashingBatchApiClient` via `tenantRequest`
        ▼
[ Langkah 5: Presentation UI (Claymorphism) ]
        │  → `WashingBatchEntryDialog`: checklist bundle + validasi foto wajib
        │  → `WashingSortingTableDialog`: meja sortir pasca-dryer per PO & Ukuran
        │  → `WashingBatchWorkbenchScreen`: dasbor kontrol operator cuci
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Menegakkan Syarat Wajib Foto per Bundle (`CreateWashingBatchUseCase.kt`)

```kotlin
for (input in command.bundleInputs) {
    // INVARIAN KRITIS: Tolak pembuatan batch cuci jika ada bundle tanpa foto!
    require(input.bundlePhotoKey.isNotBlank()) {
        "Bundle ${input.cardId.value} wajib menyertakan foto bukti fisik sebelum masuk cuci"
    }
    val card = cardRepository.findById(input.cardId)
        ?: error("WorkCard tidak ditemukan: ${input.cardId.value}")

    require(card.trackingUnit == WorkTrackingUnit.BUNDLE) {
        "Hanya unit BUNDLE yang dapat dilebur di mesin cuci, ditemukan: ${card.trackingUnit}"
    }
    ...
}
```

**Mengapa ini penting?**
- Jika validasi foto hanya ditaruh di UI, seseorang bisa menembak API dan meloloskan bundle tanpa bukti fisik.
- Dengan meletakkan `require(input.bundlePhotoKey.isNotBlank())` di domain layer murni, integritas *audit trail* fisik terlindungi di level paling dasar.

---

### Blok B: Rekonsiliasi & Pemecahan Kembali Menjadi Lot Setrika (`CompleteWashingSortToLotsUseCase.kt`)

```kotlin
// 1. Tandai seluruh bundle asal menjadi status MERGED
for (item in batch.items) {
    val card = cardRepository.findById(item.workCardId) ?: continue
    mergedCards.add(card.markMerged(command.now).copy(wipPcs = 0))
}

// 2. Terbitkan Kartu Lot Hilir (Setrika Uap / STEAM) per PO & per Ukuran
for (output in command.sortOutputs) {
    if (output.outputPcs <= 0) continue

    val lotCardId = WorkCardId("${output.subjectId}-${command.downstreamStationCode.value}-${output.sizeLabel}-lot")
    val lotCard = WorkCard(
        id = lotCardId,
        tenantId = command.tenantId,
        subject = subjectRef,
        stationCode = command.downstreamStationCode, // Stasiun STEAM
        sizeLabel = output.sizeLabel,
        bundleNo = null, // Invarian: LOT_ACCUMULATION tidak membawa bundleNo
        queuedPcs = output.outputPcs,
        wipPcs = output.outputPcs,
        scrapPcs = output.scrapPcs,
        reworkPcs = output.defectPcs,
        trackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
        status = WorkCardStatus.QUEUED,
        createdAt = command.now
    )
    createdLotCards.add(lotCard)
}
```

**Mental Model di Balik Kode Ini:**
- Di stasiun cuci, unit lacak `BUNDLE` **mati** (status menjadi `MERGED` dengan `wipPcs = 0`).
- Di stasiun setrika, unit lacak baru lahir sebagai `LOT_ACCUMULATION`.
- Kartu lot ini **dipisah secara ketat per PO (`subjectId`) dan per Ukuran (`sizeLabel`)**, sehingga saat operator setrika bekerja, pakaian sudah rapi di nampan per PO dan siap diserahkan ke QC & Packing!

---

## 🔬 4. Technology & Approach ("The Why")

| Pendekatan yang Dipilih | Alternatif yang Ditolak | Alasan & Risiko yang Dihindari |
|---|---|---|
| **Foto per Bundle Sebelum Tali Dibuka** | Foto borongan satu keranjang campur | Foto keranjang masal tidak membuktikan bundle nomor berapa yang hilang isinya. Foto per bundle memperlihatkan nomor etiket potong dan jumlah fisik ikatan. |
| **Pemisahan Lot per PO & Size Pasca-Dryer** | Meneruskan tumpukan gundukan masal ke setrika | Jika gundukan masal langsung diteruskan ke setrika, penataan per pesanan tertunda sampai tahap packing. Akibatnya meja packing menjadi macet total (*bottleneck*). |
| **Pencatatan Selisih Otomatis (`missingPcs`)** | Memaksa total input harus sama persis dengan total output | Di mesin cuci nyata, ada pakaian yang tersedot ke filter atau robek parah. Memaksa angka sama membuat operator memalsukan data. ERP yang sehat mencatat selisih (*transparent variance*). |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Jebakan Membawa `bundleNo` ke Stasiun Setrika**:
   * *Kesalahan*: Mengira setelah dicuci pakaian masih bisa dilacak per nomor bundle aslinya (misal: "Bundle #3").
   * *Akibat*: Operator setrika mogok kerja karena harus membalik ratusan baju untuk mencari jahitan benang bundle.
   * *Aturan Baku*: Pasca-washing, satuan lacak wajib berubah dari `BUNDLE` menjadi `LOT_ACCUMULATION` (`bundleNo = null`).
2. **Jebakan Lupa Mengunci Bundle Asal**:
   * *Kesalahan*: Menerbitkan kartu lot setrika tapi kartu bundle asal masih berstatus `QUEUED` atau `IN_PROGRESS`.
   * *Akibat*: Di dasbor WIP pabrik, kuantitas pakaian terhitung ganda (2x lipat dari pesanan asli).
   * *Solusi*: Selalu panggil `.markMerged(now)` pada kartu bundle asal.

---

## 🎯 6. Verifikasi & Pengujian Mandiri

### Uji Coba Unit Test:
Jalankan test suite domain workqueue:
```bash
./gradlew :core:jvmTest --tests "*WashingBatchTest*"
```

Hasil yang diharapkan:
- `creating washing batch without bundle photo should throw exception`: Hijau (gagal jika foto kosong).
- `creating washing batch and sorting pasca-dryer should split lots per PO and per size`: Hijau (kartu lot per PO & Size terbit di stasiun STEAM).
