# 🎓 Modul Pembelajaran: Tahap Penyimpanan (Siap Kirim) di Alur Sampling

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: DDD (aggregate kustodi), gerbang lintas-agregat di Use Case, Ktor route, Flyway, Compose MVI
> **Prasyarat**: Paham `SamplingPipelineStage`, `AdvanceSamplingStageUseCase`, dan pola `*Actions` di ViewModel sampling
> **Referensi Task**: Diskusi alur "Pengemasan → Kirim" (2026-09-27)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: Dulu alurnya `Pengemasan → Terkirim (Tunggu ACC)`. Di pabrik, barang yang selesai dikemas **tidak pernah langsung dikirim**. Barang ditaruh dulu, entah di rak ruang packing atau di gudang. Barang juga ditahan sampai **semua SPK dalam satu deal** siap: PO 1 = 100 pcs dan PO 2 = 100 pcs dikirim bersamaan. Sistem lama tidak tahu barang sedang di mana dan siapa yang memegangnya. Padahal di titik itulah barang paling sering "keselip".
- **Analogi**: Bayangkan penitipan tas di mall. Tas masuk, ada petugas yang menerima dan memberi nomor loker. Tas keluar, ada petugas yang menyerahkan. Kalau tas hilang, kamu tahu harus bertanya ke siapa.
- **Hasil akhir**: Alurnya menjadi `… → Pengemasan → Penyimpanan (Siap Kirim) → Terkirim → ACC`. Masuk ke penyimpanan mencatat **lokasi dan penerima simpan**. Keluar dari penyimpanan mencatat **PIC kirim**, dan bila deal belum lengkap, **alasan kirim parsial**.

---

## 🧭 2. "Start dari Mana?"

1. **Langkah 0: Tanya dulu "ini node atau edge?"** Di repo ini tahap adalah *simpul kustodi*, yaitu tempat barang berpindah tangan (KDoc `SamplingPipelineStage`).
   - Penyimpanan punya pemegang baru, jadi dia menjadi **node**.
   - "Kirim internal ke gudang" adalah **edge** (leg Surat Jalan). Edge ini sudah diturunkan otomatis oleh `FlowLegDerivation` kalau lokasinya beda gedung.
   - Jadi yang ditambahkan cukup **satu** tahap, bukan dua.
2. **Langkah 1: Enum.** `STORAGE_HOLDING` disisipkan di antara `PENGEMASAN` dan `IN_DELIVERY`. `nextStage` dihitung dari ordinal, jadi tombol "serahkan" otomatis ikut. Stage dipersist **per nama**, jadi tidak perlu migrasi data.
3. **Langkah 2: Value Object dan Aggregate** `SampleStorageRecord` beserta `StorageLocationLabel` dan `StorageCustodian`.
4. **Langkah 3: Kontrak repository** `SampleStorageRecordRepository`.
5. **Langkah 4: Use Case** `StoreSampleUseCase` dan `ReleaseSampleFromStorageUseCase`, plus guard di `AdvanceSamplingStageUseCase`.
6. **Langkah 5: Infrastruktur**: migrasi `V69__sample_storage_records.sql`, `SampleStorageRecordsTable`, dan `PostgresSampleStorageRecordRepository`.
7. **Langkah 6: Route** `SamplingStorageRoutes.kt` (`/store`, `/release`, `/storage`). Aktor diambil dari JWT.
8. **Langkah 7: Client**: `SamplingStorageApiClient`, `SamplingStorageActions`, kolom kanban "5. Penyimpanan", `SampleStorageDialogs.kt`, meja operator Kemas, dan tombol "Kirim ke Buyer" di Deal.

---

## 🧱 3. Bedah Blok per Blok

### Blok A: Aggregate kustodi, bukan field di `SamplingOrder`

```kotlin
data class SampleStorageRecord(
    val location: StorageLocationLabel,
    val storedBy: StorageCustodian,
    val releasedBy: StorageCustodian? = null,
    val partialReason: String? = null, …
) {
    fun release(pic: StorageCustodian, now: Instant, partialReason: String? = null): SampleStorageRecord {
        require(!isReleased) { "Barang sudah dilepas dari penyimpanan oleh …" }
        …
    }
}
```

**Mengapa begini?**
- Kustodi punya siklus hidup sendiri (masuk → dilepas). `SamplingOrder.kt` juga sudah 389 baris, mepet dengan hard limit 400. Memisahkannya menjaga Single Responsibility sekaligus menghormati batas ukuran file.
- `release()` menolak rilis ganda. Aturan bisnis ini tinggal di entity, bukan di route.

### Blok B: Gerbang "deal lengkap" di Use Case

```kotlin
private suspend fun requireDealComplete(order: SamplingOrder, partialReason: String?) {
    if (partialReason != null) return
    val dealId = order.dealId?.takeIf { it.isNotBlank() } ?: return
    val readiness = dealStorageReadiness(orderRepository.findByDealId(order.tenantId, dealId))
    if (readiness.isComplete) return
    throw IllegalArgumentException("${readiness.pending.size} dari ${readiness.total} SPK deal ini belum masuk penyimpanan: …")
}
```

**Mengapa begini?**
- Gerbang ini butuh data SPK **lain** (sibling satu deal). Itu data lintas-agregat, jadi tempatnya di Use Case, bukan di `SamplingOrder`. Polanya sama dengan gerbang Surat Jalan.
- Kirim parsial tetap **boleh**, tetapi harus beralasan. Alasan itu ikut tercatat di `stageHistory` (`actorRole = "… (kirim parsial: …)"`).
- `dealStorageReadiness` mengabaikan SPK `CANCELLED`/arsip. SPK yang sudah lewat penyimpanan dihitung siap.

### Blok C: Guard "jalur kustodi" di `AdvanceSamplingStageUseCase`

```kotlin
private fun requireCustodyPath(command: AdvanceSamplingStageCommand) {
    if (command.custodyRecorded) return
    when (command.target) {
        SamplingPipelineStage.STORAGE_HOLDING -> throw IllegalArgumentException("Masukkan ke penyimpanan lewat \"Simpan\" …")
        SamplingPipelineStage.IN_DELIVERY -> throw IllegalArgumentException("Pengiriman ke buyer hanya dari penyimpanan …")
        else -> Unit
    }
}
```

**Mengapa begini?** `POST /stage` biasa tidak tahu soal lokasi maupun PIC. Tanpa guard ini, orang bisa melompat ke `IN_DELIVERY` dan seluruh pencatatan kustodi jadi percuma. Guard tetap berlaku walaupun ada `overrideReason`. Override memang dibuat untuk gerbang Surat Jalan, bukan untuk melewati kustodi.

### Blok D: Migrasi

```sql
CREATE TABLE IF NOT EXISTS sample_storage_records (
    sampling_order_id VARCHAR(64) NOT NULL REFERENCES sampling_orders(id) ON DELETE CASCADE,
    location_label    VARCHAR(80) NOT NULL,
    qty_pcs           INTEGER     NOT NULL CHECK (qty_pcs > 0),
    stored_by_email   VARCHAR(255) NOT NULL DEFAULT '', …
);
```

**Mengapa begini?** Satu SPK bisa punya lebih dari satu record, karena setelah revisi SPK itu masuk penyimpanan lagi. Karena itu repository menyediakan `findLatestByOrderId` (diurutkan `stored_at DESC`). Setiap query memfilter `tenant_id` secara eksplisit, karena RLS belum efektif.

### Blok E: Route, aktor dari JWT

```kotlin
private fun ApplicationCall.custodian(): StorageCustodian {
    val email = callerPrincipalOrNull?.email?.takeIf { it.isNotBlank() } ?: callerPrincipalOrNull?.userId ?: "unknown"
    return StorageCustodian(email = email, name = email.substringBefore('@'))
}
```

**Mengapa begini?** Penanggung jawab tidak boleh diketik di body, karena klien bisa memalsukannya. Orang yang menekan tombol adalah orang yang bertanggung jawab. `loadTenantOrder` juga menolak order milik tenant lain dengan 404.

### Blok F: UI

- Kolom kanban `PENYIMPANAN`. Kelengkapan deal dihitung dari `allOrders`, **bukan** `filteredOrders`, supaya angka "3/5 SPK deal tersimpan" tidak ikut tersaring pencarian.
- `SamplingStorageActions` mengikuti pola `SamplingStageWorkActions`, supaya `SamplingViewModel` tidak membengkak.
- Kalau server menolak (422), dialog **tetap terbuka** dan menampilkan pesannya, supaya admin bisa langsung memilih kirim parsial.
- Meja Kemas memakai `DeskFinishAction.Store`, jadi tombol "selesai" membuka dialog simpan, bukan pindah tahap biasa.
- Tombol "Kirim ke Buyer" di Deal hanya berjalan kalau semua SPK deal sudah di penyimpanan. Kirim parsial diarahkan ke kartu Penyimpanan, karena perlu alasan per SPK.

---

## ⚖️ 4. The "Why"

| Pendekatan | Alternatif | Alasan | Risiko alternatif |
|---|---|---|---|
| 1 node Penyimpanan + leg otomatis | 2 node: "Kirim Internal" + "Gudang" | Perpindahan adalah edge dan sudah diturunkan `FlowLegDerivation` | Leg dicatat dua kali, dan tenant satu atap dipaksa menekan tombol yang tidak berarti |
| Aggregate `SampleStorageRecord` | Field `storageLocation` di `SamplingOrder` | SRP dan batas ukuran file | God Entity; riwayat revisi menimpa kustodi lama |
| Node wajib, tanpa bypass | Flag tenant "langsung kirim" | Selesai packing selalu disimpan dulu (keputusan user) | Jalur tanpa PIC, justru sumber barang keselip |
| Gerbang di Use Case | Gerbang di UI saja | Server adalah sumber kebenaran | Klien lain atau API langsung bisa melewatinya |

---

## ⚠️ 5. Jebakan Pemula

1. **Menambah tahap lalu lupa `when` yang exhaustive.** Compiler memang menangkap `SpkUrgency` dan `SamplingStageStyle`. Tetapi `if`/rentang seperti `isInDelivery` dan `rdProgress` tidak tertangkap. Cari semua pemakaian `IN_DELIVERY` dan `PENGEMASAN` dengan grep.
2. **Menghitung kelengkapan dari daftar yang sudah tersaring.** Gunakan data lengkap (`allOrders`).
3. **Menjalankan `./gradlew build` saat `:server:run` hidup.** `core.jar` dibangun ulang dan server melempar `NoClassDefFoundError: JsonParser`. Solusinya restart server. Ini masalah lingkungan, bukan bug kode.
4. **Mengambil aktor dari body request.** Selalu ambil dari `callerPrincipal`.

---

## 🧪 6. Pembuktian

- `core/src/commonTest/.../sampling/storage/SampleStorageFlowTest.kt` berisi 9 kasus:
  - simpan mencatat penerima;
  - simpan ditolak sebelum dikemas;
  - `/stage` biasa ditolak ke `STORAGE_HOLDING` dan `IN_DELIVERY`, termasuk dengan override;
  - rilis ditolak saat sibling belum lengkap, dan pesannya menyebut SPK-nya;
  - kirim parsial mencatat PIC dan alasan;
  - deal lengkap lolos tanpa alasan;
  - SPK tanpa deal lolos;
  - rilis ganda ditolak;
  - SPK CANCELLED diabaikan.
- `FlowLegDerivationTest` menguji dua kondisi: penyimpanan di gedung lain melahirkan leg `INTERNAL_SITE_TRANSFER`, sedangkan rak di gedung yang sama tidak melahirkan leg.
- `SamplingStageLegacyAliasTest` sekarang mengunci `PENGEMASAN → STORAGE_HOLDING → IN_DELIVERY`.
- Cek visual end-to-end dengan SPK-SMP-0010:
  1. drag ke kolom Penyimpanan;
  2. isi "Rak Packing A";
  3. kartu menampilkan "1/2 SPK deal tersimpan";
  4. rilis parsial dengan alasan;
  5. SPK pindah ke kolom Selesai;
  6. baris di `sample_storage_records` terisi lengkap.

---

## 🏆 7. Tantangan Mandiri

- [ ] Tampilkan lokasi penyimpanan langsung di kartu kanban tanpa request per kartu. Petunjuk: tambahkan endpoint list record per tenant.
- [ ] Terapkan pola yang sama ke produksi massal (`BulkWorkOrder`) dengan akumulasi qty per ukuran, lalu kirim parsial lewat `CreatePartialCustomerShipmentUseCase`.
- [ ] Tambahkan serah terima dua pihak (hitung buta di sisi penerima) di jalur masuk penyimpanan.
