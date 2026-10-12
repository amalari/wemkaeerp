# 🎓 Modul Pembelajaran: Telemetri Node Inventory (A8a) — Jujur Tanpa Ledger Stok

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Kontrak telemetri kanvas (`ModuleTelemetryProvider`), DDD use case murni, adaptor provider, KMP-safe Kotlin, kejujuran data di atas kelengkapan fitur
> **Prasyarat**: DDD dasar (domain/application/infrastructure), `Result<T>`, `suspend fun`, pola MVI tidak diperlukan di sini
> **Referensi Task**: `docs/plannings/mvp/PLAN-factory-flow-a8-debt.md` §1 & §1.1 (A8a), commit A8a

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Kanvas **Factory Flow** menampilkan node per modul (CRM, Gudang, Sampling, HPP, …). Setiap node
punya angka: jumlah barang tertahan (`wipPieces`), kecepatan (`cycleTimeHours`), dan warna
kesehatan (hijau/kuning/merah). Sebelum ada telemetri nyata, angka itu **contoh (seed)**. Masalahnya
bukan angka contohnya, tapi bahwa angka contoh itu **terlihat nyata** — PPIC bisa mengambil keputusan
produksi dari data palsu.

Kontrak 6 `module-integration-rules.md` menutup celah itu: node yang belum punya provider **wajib
ditandai "estimasi"**. Jadi pekerjaan A8a adalah: tambahkan provider nyata untuk modul yang belum
punya, mulai dari `inventory`.

### Analogi Sederhana

Bayangkan dashboard mobil. Jarum bensin harus menunjuk **tangkinya**, bukan angka default pabrik.
Kalau mobilnya belum punya sensor bensin (ledger stok), kamu **tidak** boleh memasang jarum yang
menunjuk "80%" supaya dashboard terlihat lengkap — pengemudi akan kehabisan bensin di jalan. Lebih
baik jarumnya bertuliskan "sensor belum terpasang" (estimasi) daripada menipu.

### Jebakan Premis yang Kami Temukan Lebih Dulu

Rencana awal A8a berkata: *Inventory → "stock level, low stock alerts", reuse repository yang ada*.
Saat Discovery (§15), ternyata premis itu **salah**: modul `inventory` di repo ini hanya dipunggungi
**katalog master data** — `MaterialItemRepository` (kode, nama, kategori, satuan, kepemilikan) dan
`MaterialPriceRepository` (riwayat harga). **Tidak ada tabel/kolom stok, tidak ada `onHand`, tidak
ada reorder level.** Ledger stok adalah fitur Gudang yang belum di-discovery.

Kita punya dua pilihan:
1. Mengarang angka "stok" → **dilarang** (nilai yang tidak ada tidak boleh dipalsukan).
2. Menurunkan metrik ke data yang **benar-benar ada** dan tetap berguna.

Kita pilih (2). Inilah pelajaran terbesar task ini: **kejujuran data lebih penting daripada
kelengkapan dashboard.**

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Bila kamu mengetik ini dari nol, urutannya begini (dan kenapa):

1. **Langkah 0 — Baca kontraknya dulu, jangan tulis kode.**
   Buka `core/.../domain/pipeline/ModuleTelemetry.kt`. Di sana ada `ModuleTelemetry` (bentuk
   keluarannya) dan `interface ModuleTelemetryProvider`. Kontrak ini sudah dibekukan; kita cuma
   mengisinya. Menulis provider sebelum membaca kontrak = menebak field.

2. **Langkah 1 — Inventaris data: apa yang NYATA ada?**
   Telusuri repository yang benar-benar tersedia (`MaterialItemRepository`, `MaterialPriceRepository`).
   Tanpa langkah ini, kita akan menulis metrik yang tidak bisa diisi — persis kesalahan rencana awal.

3. **Langkah 2 — Use case murni di domain (`GetInventoryNodeTelemetryUseCase`).**
   Kenapa di `core`, bukan di route server? Karena **aturan "apa arti sehat" adalah aturan domain**.
   Route server cuma menyambung; ia tidak boleh memutuskan ambang hijau/kuning/merah.

4. **Langkah 3 — Adaptor provider (`InventoryTelemetryProvider`).**
   Class tipis di `core/.../domain/pipeline/ExistingTelemetryProviders.kt` yang membungkus use case
   ke interface `ModuleTelemetryProvider`. Pola adaptor yang sama dipakai CRM/TechPack — tiru, jangan
   ciptakan yang baru.

5. **Langkah 4 — Wiring di server (`ServerRouteWiring.kt`).**
   Daftarkan provider ke daftar `pipelineTelemetryRoutes(providers = listOf(...))`.

6. **Langkah 5 — Test dengan fake repository.**
   Domain diuji **tanpa** DB. Kita bikin fake `MaterialItemRepository`/`MaterialPriceRepository`.

> **Urutan ini bukan gaya-gayaan.** Di DDD, arah dependensi selalu `Presentation → Application →
> Domain ← Infrastructure`. Kalau kamu mulai dari route/DB, aturan bisnis akan bocor ke lapisan
> yang salah dan mustahil diuji tanpa Postgres.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Use Case Murni (Domain)

```kotlin
class GetInventoryNodeTelemetryUseCase(
    private val materialRepository: MaterialItemRepository,
    private val priceRepository: MaterialPriceRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(tenantId: TenantId): Result<ModuleTelemetry> = runCatching {
        val active = materialRepository.searchCatalog(
            tenantId,
            MaterialCatalogQuery(includeArchived = false, pageSize = MAX_ACTIVE_MATERIALS)
        ).items

        val stillUnpriced = if (active.isEmpty()) 0
            else {
                val priced = priceRepository.effectivePricesAt(tenantId, active.map { it.id }, clock.now())
                active.count { it.id !in priced }
            }

        ModuleTelemetry(
            module = GarmentModules.INVENTORY,
            wipPieces = active.size,
            cycleTimeHours = 0.0,
            healthStatus = when {
                active.isEmpty() -> FlowHealthStatus.HEALTHY
                stillUnpriced > active.size * 0.5 -> FlowHealthStatus.CRITICAL
                stillUnpriced > active.size * 0.25 -> FlowHealthStatus.BOTTLENECK
                else -> FlowHealthStatus.HEALTHY
            }
        )
    }
}
```

**Mengapa ditulis begini?**

- **`operator fun invoke(...)`** → use case bisa dipanggil seperti fungsi: `useCase(tenantId)`.
  Satu use case = satu operasi bisnis (CLAUDE.md §4).
- **`runCatching { }` → `Result<ModuleTelemetry>`** → kegagalan (DB mati, dsb.) jadi *nilai*, bukan
  exception yang meledak di route. Route telemetri memang mengabaikan provider yang gagal
  (`getOrNull()`), jadi node itu tetap "estimasi". Ini desain yang disengaja.
- **`wipPieces = active.size`** → mengikuti pola CRM/TechPack: `wipPieces` melaporkan **seluruh
  populasi aktif**, sementara `healthStatus` dari **bagian yang tertahan** (di sini: material tanpa
  harga acuan).
- **`clock` disuntik, bukan `Clock.System` dipanggil langsung** → waktu jadi bisa ditentukan di test
  (determinisme). Ini alasan yang sama dipakai `SamplingTelemetryProvider`.
- **`if (active.isEmpty())` sebelum pembagian** → mencegah bagi-nol dan memberi makna eksplisit:
  katalog kosong = sehat, bukan merah.
- **Tidak ada `stageWip`** → modul Inventory tidak punya kerangka tahap, jadi peta per-tahap kosong.

### Blok B: Adaptor Provider (Domain → Kontrak Kanvas)

```kotlin
/** Inventory: WIP = material aktif; health dari rasio material aktif tanpa harga acuan. */
class InventoryTelemetryProvider(
    private val useCase: GetInventoryNodeTelemetryUseCase
) : ModuleTelemetryProvider {
    override val module = GarmentModules.INVENTORY
    override suspend fun read(tenantId: TenantId) = useCase(tenantId)
}
```

**Mengapa ditulis begini?**

- **Class tipis sengaja.** Provider bukan tempat logika; ia cuma "penerjemah" dari use case ke
  kontrak kanvas. Semua kecerdasan tinggal di use case supaya bisa diuji tanpa server.
- **`override val module`** → identitas node. Inilah yang dipakai overlay untuk **menimpa angka seed**
  di node `inventory` (lihat `PipelineTelemetryOverlay.applyTo`).

### Blok C: Wiring di Server

```kotlin
pipelineTelemetryRoutes(
    providers = listOf(
        // ... provider lain ...
        com.eventverse.app.domain.pipeline.InventoryTelemetryProvider(
            com.eventverse.app.domain.masterdata.usecases.GetInventoryNodeTelemetryUseCase(
                materialRepository = materialRepo,
                priceRepository = materialPriceRepo
            )
        )
    ),
    // ...
)
```

**Mengapa ditulis begini?**

- **Provider tidak mengaktifkan dirinya sendiri.** `ModuleTelemetry.kt` sudah menulis aturannya:
  provider baru harus "mendaftarkannya di wiring server". Lupa mendaftar = kode mati yang tetap
  mengompilasi; node tetap "estimasi" dan tidak ada yang tahu kenapa. Ini penyebab paling sering
  fitur telemetri "tidak muncul".
- **Repositori disuntik dari luar** (`materialRepo`, `materialPriceRepo` sudah tersedia di
  `ServerRouteWiring`) → domain tidak pernah mengimpor Postgres.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi / Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| Use case di `core` + adaptor provider terpisah | Logika langsung di route server | Bisa diuji tanpa DB; arah dependensi bersih | Aturan "sehat" bocor ke route, tidak bisa di-test murni |
| Suntik `Clock` (default `Clock.System`) | Panggil `Clock.System.now()` langsung | Determininisme di test | Test bergantung waktu nyata, flaky |
| `Result<ModuleTelemetry>` | Lempar exception | Provider gagal tidak boleh menahan seluruh kanvas (route pakai `getOrNull()`) | Satu modul rusak = seluruh endpoint telemetri 500 |
| Metrik katalog (material aktif + tak berharga) | Mengarang "stok" | Sistem **tidak punya** ledger stok; mengarang = menipu | PPIC mengambil keputusan dari angka palsu |
| Batas 1000 (mengikuti TechPack) | `pageSize` kecil berulang (paging loop) | Satu kueri cukup untuk kanvas; sederhana | Paging loop = lebih banyak round-trip & kode |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Mengarang metrik yang datanya belum ada.**
   - *Kenapa bahaya*: Node terlihat "hidup" dengan angka stok palsu; keputusan produksi salah. Ini
     pelanggaran aturan Kontrak 4/8 (nilai tak dikenal tidak boleh dipalsukan).
   - *Solusi elegan kami*: Turunkan metrik ke yang **terverifikasi ada** (katalog + harga), dan
     catat deviasi + alasannya di `PLAN-factory-flow-a8-debt.md` §1.1 supaya tidak "hilang" di commit.

2. **Jebakan 2: Menulis provider tapi lupa mendaftarkan di `ServerRouteWiring`.**
   - *Kenapa bahaya*: Kompilasi hijau, test domain hijau, tapi di produksi node tetap "estimasi".
   - *Solusi*: Jadikan "daftarkan di wiring" bagian dari Definition of Done, bukan langkah opsional.

3. **Jebakan 3: Bagi-nol saat katalog kosong.**
   - *Kenapa bahaya*: `stillUnpriced > active.size * 0.5` saat `size == 0` menghasilkan `0 > 0` = false
     (kebetulan aman), tapi niatnya tidak terbaca dan `0/0` di bentuk lain akan melempar.
   - *Solusi*: Tangani `active.isEmpty()` lebih dulu dan beri makna eksplisit (HEALTHY).

4. **Jebakan 4: Memakai API khusus JVM di `commonMain`.**
   - *Kenapa bahaya*: `core` dikompilasi ke JVM, JS, dan Wasm. `System.currentTimeMillis()`,
     `java.*`, atau `@JvmInline` tanpa `import kotlin.jvm.JvmInline` **lulus di JVM** tapi merah di
     JS/Wasm. (Justru inilah yang terjadi pada kode Fulfillment di `main` saat task ini dikerjakan:
     `Unresolved reference 'JvmInline'` / `'System'` hanya muncul di target JS/Wasm.)
   - *Solusi*: Pakai `kotlinx.datetime.Clock`, dan selalu uji **kelima target** — JVM hijau bukan
     bukti.

5. **Jebakan 5: Memuat seluruh katalog untuk sekadar menghitung.**
   - *Kenapa bahaya*: Di tenant besar, `pageSize` besar bisa berat.
   - *Solusi*: Kita batasi `MAX_ACTIVE_MATERIALS = 1000` (mengikuti `GetTechPackNodeTelemetryUseCase`)
     dan jaga pekerjaan tetap satu kueri. Kalau nanti ada kebutuhan lebih besar, angka ini titik
     tinjauannya.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Strategi: **unit test domain dengan fake repository** — tanpa DB, tanpa server.

```kotlin
@Test
fun read_oneUnpricedOfThree_shouldReportWipAndBottleneck() = runTest {
    val items = listOf(material(1), material(2), material(3))
    val t = useCase(items, pricedIds = setOf("mat_1", "mat_2")).invoke(tenant).getOrThrow()
    assertEquals(GarmentModules.INVENTORY, t.module)
    assertEquals(3, t.wipPieces)
    assertEquals(FlowHealthStatus.BOTTLENECK, t.healthStatus) // 1 dari 3 (33%) > 25%
}
```

Yang dibuktikan keempat test (lihat `GetInventoryNodeTelemetryUseCaseTest`):
1. **Ambang health bekerja** — 1 tak-berharga dari 3 → `BOTTLENECK`.
2. **Semua berharga** → `HEALTHY`.
3. **Material terarsip tidak dihitung** — `includeArchived = false` benar-benar menyaring.
4. **Katalog kosong** → `HEALTHY` dengan `wipPieces = 0` (bukan crash).

Cara menjalankan:

```bash
./gradlew :core:jvmTest --tests "com.eventverse.app.domain.masterdata.usecases.GetInventoryNodeTelemetryUseCaseTest"
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Tambahkan test untuk kasus ambang `CRITICAL` (mis. 2 dari 3 tak-berharga,
      > 50%). Berapa nilai `wipPieces` dan `healthStatus` yang kamu harapkan?
- [ ] **Tantangan 2**: Modul `quality_control` belum punya provider. Cek repository QC mana yang
      tersedia; kalau tidak ada, tuliskan **Discovery Note** yang menyatakan metrik apa yang jujur
      bisa dilaporkan — jangan mengarang "pass/fail rate" tanpa datanya.
- [ ] **Tantangan 3**: `MaterialPriceRepository.effectivePricesAt` default-nya `PriceSource.STANDARD`.
      Apa yang terjadi pada material bertipe **titipan klien** (harga Rp 0, `CLIENT_SUPPLIED_ZERO`)?
      Apakah ia dianggap "berharga"? Diskusikan apakah ini perilaku yang benar untuk metrik health kita.

---

## 📌 Catatan Keputusan (kenapa task ini menyimpang dari rencana)

`PLAN-factory-flow-a8-debt.md` §1 semula menulis Inventory → "stock level, low stock alerts" dengan
asumsi bisa reuse repository yang ada. Discovery membuktikan asumsi itu keliru (tak ada ledger stok).
Keputusan menurunkan metrik ke katalog dicatat di §1.1 dokumen tersebut, bukan disembunyikan — supaya
siapa pun yang membaca rencana tahu **mengapa** metriknya bukan stok, dan bisa memutuskan apakah
fitur Gudang (ledger stok) perlu di-discovery berikutnya.
