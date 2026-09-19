# Teaching: Modul Fulfillment — Kurir Antar Karung (Transfer Antar Divisi)

> Task: layar kerja `/fulfillment` mobile-first + alur transfer karung penuh (scan → ACC admin → antar → terima).

## 1. Start dari Mana? (Order of Operations)

Kalau membangun ulang dari nol, urutannya adalah urutan dependensi — bukan urutan folder:

1. **Domain dulu** (`core/domain/fulfillment/`): value objects → entity → repository interface → use cases. Semuanya Kotlin murni, nol framework. Kompilasi `:core` sebelum lanjut.
2. **Codec bersama** (`core/shared/fulfillment/InternalTransferCodec.kt`): kontrak JSON tunggal yang dipakai server *dan* client — kalau server dan client punya codec masing-masing, keduanya akan berpisah jalan diam-diam.
3. **Database**: migrasi Flyway (`V54`) mendahului kode Exposed, karena `apply_tenant_rls` harus tahu tabelnya sebelum repo jalan.
4. **Server**: tabel Exposed → repository Postgres → routes → wiring di `ServerRouteWiring.kt`.
5. **Client**: API client → ViewModel (MVI) → UI Compose → wiring rute `App.kt`.

## 2. Bedah Kode: Tiga Keputusan Kunci

### a. Entity `InternalTransfer` menjalani *perjalanan*, bukan *isi*
Karung sudah punya entity `TraceContainer` (bounded context telusur) yang mengurus isi: hitungan panel, silsilah bundel, penutupan. Entity kita tidak menyalin itu — ia menyimpan `sackCode` sebagai referensi dan hanya menjawab "karung ini sedang di mana dan siapa yang bertanggung jawab". Menggabungkan keduanya = satu agregat dengan dua alasan berubah.

### b. Guard ada di domain, bukan di UI
`InternalTransfer.approve()` menolak status selain `MENUNGGU_ACC`, dan `init` menolak record `DIANTAR` tanpa `approvalSignatureKey`. UI bisa di-bypass; domain tidak. Inilah pola yang sama dengan `TraceContainer.closeSack()`.

### c. Foto = bukti utama, angka = keterangan
`receive()` tetap sah walau `receivedWeightKg` null (foto wajib, angka opsional). Selisih hanya ditandai ketika angka ada dan meleset — karena yang di lapangan paling sulit adalah mengetik angka, bukan memotret.

## 3. The Why

- **`WeightKg` value class, bukan Double telanjang**: aturan "eksak sampai koma dari resi kurir" butuh satu rumah. `parse("8,35")` menerima koma keypad pabrik; `formatted()` menjamin dua desimal tanpa bergantung locale platform (`String.format` tidak ada di commonMain).
- **`HandoverProof` sealed interface**: dua jalur serah terima (TTD penerima vs resi kurir) adalah domain yang *tertutup* — `when` di atas sealed interface memaksa compiler menjerit kalau jalur ketiga muncul tanpa dipikirkan.
- **TTD sebagai vektor JSON, bukan PNG**: encoding PNG tidak seragam di 5 target KMP. Goresan dinormalisasi (0..1) dan disimpan sebagai `{"v":1,"strokes":[...]}` di kolom TEXT — bentuk tanda tangannya utuh, ringkas, dan bisa dirender ulang di mana pun.
- **Satu indeks parsial unik** (`uq_fulfillment_active_transfer`) menegakkan "satu karung, satu perjalanan aktif" di level database — use case hanya lapisan pertama.

## 4. Jebakan Pemula

1. **Mengira kurir harus scan bundel satu-satu.** Tidak — manifest terkunci saat karung *ditutup* (milik modul telusur). Kurir hanya berinteraksi dengan karung.
2. **Menanam warna status ke komponen.** `transferStatusTint()` memetakan status → token `WeMadeColors` (hijau=aman, amber=menunggu, merah=masalah). Literal `Color(0xFF…)` dilarang di luar `WeMadeTheme.kt`.
3. **Melupakan RLS.** Setiap tabel baru wajib `apply_tenant_rls` — tabel event pun diberi kolom `tenant_id` denormal semata untuk ini.
4. **Menunggu penerima login.** Pihak luar tidak pernah punya akun; petugas internal yang merekam atas nama mereka.

## 5. Verifikasi & Tantangan Mandiri

```bash
./gradlew :core:jvmTest --tests 'com.eventverse.app.domain.fulfillment.*'   # 8 test guard domain
./gradlew :server:compileKotlin
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs :app:shared:jvmTest
```

Alur manual: login persona berwewenang → `/fulfillment` → ketik kode karung yang sudah ditutup → isi berat + foto + nama → ajukan → ganti persona dengan akses MANAGE → ACC dengan TTD → status Diantar → catat penerimaan (coba pcs 118 dari 120 → status harus `DITERIMA_SELISIH`).

**Tantangan**: tambah rute `FINISHING_TO_VENDOR` (`TransferLeg`) — perhatikan bahwa yang harus berubah cuma enum, indeks, dan UI; use case dan tabel tidak boleh tersentuh. Kalau kamu sampai menyentuh `ReceiveTransferUseCase`, berarti desainnya bocor.
