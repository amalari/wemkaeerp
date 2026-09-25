# Teaching — SPK Split per Size (1 PO Multi-Size → N SPK)

> **Level Target**: Junior Developer
> **Fase 1**: SPK Massal (`BulkWorkOrder`). Fase 2 (sampling per desain × ukuran) menyusul —
> lihat [`docs/plannings/planning-spk-per-size-split.md`](../plannings/planning-spk-per-size-split.md).

## 1. Start dari Mana? (Order of Operations)

Kalau membangun ulang dari nol, urutannya:

1. **Domain dulu** (`core/domain/production/BulkWorkOrder.kt`): tambah field `sizeLabel` +
   invarian di `init`. Domain adalah fondasi — server, codec, dan UI semuanya membaca dari sini.
2. **Use case** (`LaunchBulkWorkOrderFromDealUseCase.kt`): pecah launch per ukuran.
3. **Codec** (`BulkWorkOrderCodec.kt`): ikutkan `sizeLabel` di encode/decode JSON.
4. **DB**: migrasi Flyway (`V67__bulk_work_order_size_label.sql`) + tabel Exposed + repo Postgres.
5. **Route**: respons launch berubah jadi array.
6. **Client**: `ProductionApiClient.launchFromDeal` → `Result<List<BulkWorkOrder>>`.
7. **UI**: badge ukuran (`ClayTag`).
8. **Test**: invarian domain + perilaku use case dengan fake repository.

## 2. Bedah Kode Blok per Blok

### Invarian di `BulkWorkOrder.init`
```kotlin
if (sizeLabel != null) {
    require(sizeBreakdown.size == 1 &&
        sizeBreakdown.single().sizeLabel.equals(sizeLabel, ignoreCase = true)) { ... }
}
```
Mental model: `sizeLabel` adalah **janji** "SPK ini satu ukuran". Janji tanpa penjagaan cuma
komentar — `require` di konstruktor menjadikannya mustahil dilanggar, di modul mana pun.

### Split di use case
```kotlin
val sizesWithActiveSpk = existing.mapNotNull { it.sizeLabel?.uppercase() }.toSet()
val pending = sizeBreakdown.filter { it.sizeLabel.uppercase() !in sizesWithActiveSpk }
pending.forEach { requireGoldenSampleCoversSize(goldenSample, it.sizeLabel) }
val newlyLaunched = pending.map { save(perSizeDraft(...).release(now)) }
```
Tiga keputusan di sini:
1. **Idempotensi pindah level**: dulu per-deal, kini per-`(dealId, sizeLabel)`. Klik kedua
   melengkapi ukuran yang hilang, bukan error.
2. **Validasi sebelum simpan**: kalau satu ukuran ditolak Golden Sample, tidak ada SPK setengah
   terbit tertinggal (semantik transaksional tanpa transaksi DB).
3. **Golden sample longgar pada label non-standar**: deskripsi PO dipakai apa adanya
   ("KEMEJA PDH UKURAN L"), jadi pengecekan matriks hanya berlaku bila label persis kolom
   standar S/M/L/... Parser yang menebak pola akan salah diam-diam — dokumentasi use case lama
   sudah memperingatkannya.

### Fallback decode di `ProductionApiClient`
```kotlin
is JsonValue.Arr -> ... ; is JsonValue.Obj -> listOf(...) 
```
Respons route berubah objek → array. Klien menerima keduanya selama periode deployment agar
server lama + klien baru (atau sebaliknya) tidak saling merusak.

## 3. Technology & Approach (The Why)

- **Split di titik launch, bukan di progres** — `recordStageProgress`, `wipPieces`, dan
  traceability sudah bekerja per-SPK. Dengan 1 SPK = 1 ukuran, semua laporan per ukuran dapat
  **gratis**; nol perubahan di lapisan lantai produksi.
- **`sizeLabel` nullable, bukan wajib** — SPK lama multi-size tetap valid dan terbaca. Memecah
  SPK yang sedang berjalan mengacaukan progres tahap dan surat jalan yang sudah terbit. Ini
  pola "legacy read-only" yang sama dengan `mockupImageUrls`.
- **Fake repository di test, bukan mock library** — repo ini tanpa framework mocking; interface
  kecil (`DealRepository` 9 method) membuat fake eksplisit lebih jujur dan multiplatform-safe.

## 4. Jebakan Pemula (Common Pitfalls)

1. **Menaruh `nextSpkNumber` di luar `suspend`** — dia suspend function; helper pemformat
   harus `private suspend fun`. Compiler akan menangkap ini, tapi paham alasannya: penomoran
   membaca DB, jadi wajib konteks korutin.
2. **Smart cast lintas modul** — `order.sizeLabel` bertipe `String?` dari modul lain tidak bisa
   di-smart-cast langsung di `when`; capture ke variabel lokal dulu (`val sizeTag = ...`).
3. **Validasi di dalam `map` yang menyimpan** — kalau validasi dan save bercampur dalam satu
   loop, kegagalan di ukuran ke-3 meninggalkan ukuran 1–2 sudah terbit. Pisahkan fase validasi
   dan fase penulisan.
4. **Menambah ternary/emoji di UI** — badge ukuran wajib `ClayTag` + token warna
   (`WeMadeColors.Primary`), sesuai design-system rules.

## 5. Verifikasi & Tantangan Mandiri

```bash
./gradlew :core:jvmTest --tests '*BulkWorkOrder*' --tests '*LaunchBulkWorkOrder*'
./gradlew :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs :server:compileKotlin
```
- 28 test lulus (19 BulkWorkOrder + 5 LaunchUseCase + 4 Codec), 0 gagal.
- **Tantangan**: tambahkan test "cancel lalu launch ulang → SPK ukuran sama terbit kembali"
  (petunjuk: filter `existing` mengecualikan CANCELLED, jadi otomatis — buktikan dengan test).
- **Pikirkan**: apa yang terjadi bila PO diedit setelah sebagian SPK terbit? Ukuran lama tidak
  terpengaruh (idempotensi per ukuran), ukuran baru dapat SPK sendiri — tapi qty ukuran lama
  tidak ikut berubah. Apakah itu yang diinginkan bisnis? (Catat jawabannya di planning doc.)
