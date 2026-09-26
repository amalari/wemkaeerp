# 🎓 Modul Pembelajaran: Pengiriman Antar-Lokasi sebagai Konektor Turunan & Gerbang Surat Jalan

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design, pemodelan graf (node vs edge), sealed hierarchy, gerbang lintas-agregat, Flyway, Compose Multiplatform, RBAC server-side
> **Prasyarat**: Kotlin dasar, paham `data class` & `sealed interface`, pernah membaca `core/domain/transfer/SuratJalanManifest.kt`
> **Referensi Task**: Alur Proses SPK Sampling — pengiriman lintas gedung/vendor/buyer

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Buka Detail SPK Sampling, lihat baris **ALUR PROSES**:

```
( SPK Masuk ) + ( Program CAM ) + ( Rajut Turun Mesin ) + ( Linking & Tambahan ) + ( Finishing & QC ) + ( Terkirim )
```

Sekarang bayangkan pabriknya nyata. Mesin rajut ada di **Gedung A** di Blok A-2. Meja linking dan
finishing ada di **Gedung B** di Blok B-5, lima menit naik mobil pickup. Dan bordirnya? Dikerjakan
**CV Bordir Jaya**, vendor makloon di luar pabrik.

Sebelum fitur ini, baris di atas menampilkan perpindahan `Rajut → Linking` **persis sama** apakah
mejanya bersebelahan atau barangnya harus naik mobil. Sistem tidak tahu bedanya.

### Masalah nyata kalau dibiarkan

1. **Barang hilang tanpa jejak.** 200 potong berangkat dari Gedung A, 195 sampai di Gedung B. Tidak
   ada dokumen yang bisa ditunjuk untuk menanyakan 5 sisanya ke siapa.
2. **Kain di vendor tidak pernah direkonsiliasi.** Barang dikirim ke CV Bordir Jaya, lalu... sudah.
   Tidak ada catatan kapan harus kembali, berapa yang kembali, dan siapa yang menanggung cacatnya.
3. **Operator mengerjakan barang yang belum sampai.** Papan kanban bilang SPK sudah di tahap
   Linking, padahal barangnya masih di bak mobil.

### Analogi sederhana

Bayangkan alur proses sebagai **jadwal kereta**. Stasiun adalah tempat kerja terjadi; **rel di antara
dua stasiun** adalah perpindahannya. Yang sering salah dipikirkan pemula: memperlakukan "naik kereta"
sebagai stasiun ketiga di antara keduanya. Padahal naik kereta bukan tempat — ia sifat dari
*hubungan* antara dua stasiun yang letaknya berjauhan.

### Hasil akhir yang diharapkan

- Celah antar chip **berubah sendiri** menjadi konektor bergaris putus + ikon truk kalau perpindahan
  itu melintasi gedung atau keluar ke vendor.
- SPK **tidak bisa maju** ke tahap berikutnya sebelum Surat Jalan terbit dan ditandai diterima.
- Pabrik satu atap tanpa makloon melihat layar **persis seperti sebelumnya** — nol beban tambahan.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Kalau kamu harus mengetik fitur ini dari layar kosong, jangan mulai dari UI. Ini urutannya, beserta
alasan tiap langkah:

```
[0. Bereskan utang yang menghalangi]  ← paling sering dilewati, paling sering menyakitkan
        │
[1. Model & derivasi murni (core)]    ← bisa diuji tanpa DB, server, maupun layar
        │
[2. Status: gabungkan dengan dokumen] ← butuh satu kolom DB penghubung
        │
[3. Persistensi konfigurasi lokasi]   ← baru sekarang menyentuh Postgres & route
        │
[4. Gerbang (penegakan)]              ← use case, bukan invarian entity
        │
[5. UI]                               ← terakhir, karena sekarang ia tinggal menggambar
```

### Langkah 0: Bereskan utang dulu

Ada dua hal yang kalau tidak dibereskan membuat langkah berikutnya mustahil:

- `ProcessFlowViewModel` tidak pernah mengisi `executionMode`/`vendorRef`. Seluruh fitur bergantung
  pada informasi "proses ini dikerjakan vendor luar". Tanpa itu, derivasi tidak punya bahan.
- Ada **dua** tipe bernama `TransferStatus` di repo ini (`domain.transfer` dan
  `domain.fulfillment`). Menambah kode baru di sekitarnya berarti menambah peluang salah import.

Pelajaran: *kalau langkah pertama terasa tersendat, sering kali bukan langkahnya yang salah — ada
utang yang harus dibayar lebih dulu.*

### Langkah 1: Model & derivasi murni

Mulai dari `core/`, tanpa I/O sama sekali. Ini yang membuat 18 test bisa jalan dalam 3 detik.

### Langkah 2–3: Baru sentuh database

Kolom penghubung (`leg_key`), lalu tabel konfigurasi lokasi.

### Langkah 4: Gerbang

Di sinilah keputusan arsitektur paling halus berada — lihat Blok D.

### Langkah 5: UI terakhir

Karena begitu domainnya benar, UI tinggal menggambar apa yang sudah dihitung.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Pengiriman adalah **sambungan**, bukan **simpul**

Ini keputusan terpenting di seluruh fitur. Godaan pertamanya begini: palet sudah punya chip
*Bordir Komputer*, *Sablon*, *Laundry* — tinggal tambah satu chip lagi bernama *Pengiriman*, selesai.

**Jangan.** Alasannya tiga, berurut dari yang paling menentukan:

```kotlin
// core/domain/transfer/FlowTransferLeg.kt
data class FlowTransferLeg(
    val legKey: String,
    val fromNode: FlowNodeRef,
    val toNode: FlowNodeRef,
    val origin: LegEndpoint,
    val destination: LegEndpoint,
    val transferType: TransferType
)
```

**Mengapa blok ini ditulis begini?**

1. **Sebagai simpul, ia punya kebebasan untuk salah.** Chip punya `samplingAnchorAfter` — artinya
   manusia bisa menaruh "Kirim ke Vendor" setelah *Finishing* padahal vendornya dipakai di *Bordir*.
   Tidak ada invarian yang bisa mencegahnya, karena sebuah simpul **tidak tahu tetangganya**.
   Sebagai sambungan, ia hanya bisa ada di antara dua simpul yang memang berjauhan — kontradiksi itu
   secara struktural tidak bisa dinyatakan.
2. **Bukan `ModuleArchetype`.** Enum itu punya 10 slot kapabilitas (CUTTING, SEWING, FINISHING…) dan
   tidak satu pun logistik — dan itu benar. Truk tidak mengubah barang, jadi ia tidak mengisi slot
   apa pun. Menambah entri ke-11 memaksa setiap `when` di repo menumbuhkan cabang untuk sesuatu yang
   bukan tahap kerja.
3. **N stasiun → sampai N−1 leg.** Sebagai simpul, baris alur jadi dua kali panjang, dan setiap kali
   user menggeser satu chip ia harus membetulkan simpul truk secara manual.

> **Mental model**: tanyakan *"apakah benda ini mengubah produknya?"* Kalau ya → simpul. Kalau ia
> hanya memindahkan produk dari satu keadaan ke keadaan lain tanpa mengubahnya → sambungan.

---

### Blok B: Satu peta, bukan satu peta per lapisan

Alur produksi di repo ini hidup di **dua bentuk**: tahap sampling (`SamplingPipelineStage`) dan
stasiun kerja masal (`WorkStationCode`), plus proses opsional tenant. Ketiganya sama-sama "tempat
kerja terjadi", jadi ketiganya perlu diberi lokasi.

Godaan keduanya: dua field `Map` di satu data class.

```kotlin
// ❌ DITOLAK
data class TenantLocationConfig(
    val stationLocations: Map<WorkStationCode, LocationId>,
    val stageLocations: Map<SamplingPipelineStage, LocationId>   // sumber kebenaran kedua
)
```

Kenapa ditolak? Karena CAM di flow sampling dan stasiun CAM di line adalah **tempat yang sama secara
fisik**, tapi sekarang punya dua baris yang harus dijaga konsisten — dan **tidak ada apa pun yang
menegakkan konsistensinya**. Cepat atau lambat keduanya berbeda, dan tidak ada yang tahu mana yang
benar.

```kotlin
// ✅ core/domain/transfer/FlowNodeRef.kt
sealed interface FlowNodeRef {
    val key: String                                          // "STAGE:MACHINE_KNITTING"

    data class Stage(val stage: SamplingPipelineStage) : FlowNodeRef
    data class Process(val code: String) : FlowNodeRef       // "PROC:BORDIR"
    data class Station(val code: WorkStationCode) : FlowNodeRef

    companion object {
        fun parse(key: String): FlowNodeRef? { … }           // null, bukan throw
    }
}
```

**Mengapa blok ini ditulis begini?**

- **Satu `Map<FlowNodeRef, LocationId>`, satu tabel, satu repository.** Tidak ada kemungkinan
  divergensi karena tidak ada duplikat untuk didivergensikan.
- **`key` adalah bentuk tersimpannya**, stabil lintas rilis dan aman jadi kolom teks.
- **`parse` mengembalikan `null`, bukan melempar.** Kalau suatu hari `SamplingPipelineStage` kehilangan
  satu entri, baris pemetaan lama untuk tahap itu **dilewati satu per satu** — bukan menjatuhkan
  seluruh konfigurasi tenant. Layar alur harus tetap terbuka meski satu baris usang.

---

### Blok C: Endpoint bertipe mengalahkan tiga field nullable

Lihat `SuratJalanManifest` yang sudah ada:

```kotlin
val originLocationId: LocationId? = null,
val destinationLocationId: LocationId? = null,
val vendorRef: String? = null,
val customerName: String? = null,
```

Empat field nullable, dan **kombinasi mana yang sah bergantung pada `transferType`**. Pembaca harus
menebak; penulis harus ingat mengisi `null` yang benar. Invariannya ditegakkan di `init` dengan
serangkaian `when` — bekerja, tapi setiap pembaca kode harus membaca `init` dulu untuk tahu aturannya.

Untuk model baru, kita pakai bentuk yang aturannya **terbaca dari tipenya**:

```kotlin
// core/domain/transfer/LegEndpoint.kt
sealed interface LegEndpoint {
    val displayLabel: String

    data class Site(val locationId: LocationId, val name: String = locationId.value) : LegEndpoint
    data class Vendor(val ref: String) : LegEndpoint
    data class Customer(val name: String) : LegEndpoint
}
```

**Mengapa blok ini ditulis begini?**

- **Buyer bukan baris `tenant_locations`, dan vendor juga bukan.** Memaksakan ketiganya jadi
  `LocationId` berarti berbohong tentang bentuk datanya.
- **Kesetaraan ujung inilah yang melahirkan leg** (lihat Blok D). `data class` memberi `equals`
  gratis, dan seluruh aturan derivasi bertumpu pada perbandingan itu.

---

### Blok D: Satu aturan, tiga perilaku rumit yang jatuh sendiri

Ini bagian yang paling layak dipelajari. Perhatikan betapa pendeknya aturan intinya:

```kotlin
// core/domain/transfer/FlowLegDerivation.kt
val anchored = nodes.mapNotNull { node ->
    val process = (node as? FlowNodeRef.Process)?.let { processByCode[it.code] }
    config.endpointFor(node, process?.executionMode ?: IN_HOUSE, process?.vendorRef)
        ?.let { node to it }
}

for (index in 1 until anchored.size) {
    val (fromNode, origin) = anchored[index - 1]
    val (toNode, destination) = anchored[index]
    if (origin == destination) continue          // ← seluruh aturannya ada di baris ini
    legFor(fromNode, toNode, origin, destination, config)?.let(legs::add)
}
```

**Aturannya cuma satu: leg muncul di setiap pergantian ujung.** Dari situ, tiga perilaku yang
kedengarannya butuh kode khusus jatuh dengan sendirinya:

| Perilaku | Kenapa jatuh sendiri |
|---|---|
| **Makloon menghasilkan DUA leg** | Barisan `Gedung A → Vendor → Gedung A` berganti ujung **dua kali**. Berangkat dan pulang keduanya lahir tanpa satu baris `if` pun. |
| **Dua proses di vendor yang sama tergabung** | Ujungnya identik, jadi `origin == destination` → `continue`. Dan itu memang benar secara fisik: barangnya dikirim sekali. |
| **Simpul tanpa pemetaan transparan** | `endpointFor` mengembalikan `null`, `mapNotNull` membuangnya dari barisan. `A → (tak dipetakan) → B` tetap menghasilkan satu leg `A→B` yang benar. |

> **Mental model**: kalau sebuah fitur butuh banyak cabang `if` untuk kasus-kasus khusus, sering kali
> kamu belum menemukan aturan yang benar. Aturan yang benar membuat kasus khususnya hilang.

Kenapa perjalanan pulang dari vendor penting? Bukan formalitas administrasi: **di situlah kuantitas
direkonsiliasi dan `DefectLiability` ditentukan** — apakah cacatnya tanggung jawab vendor
(`SUPPLIER_VENDOR_DEFECT`) atau bawaan kain buyer (`CLIENT_SUPPLIED_DEFECT`).

---

### Blok E: Gerbang lintas-agregat wajib jadi Use Case

`SamplingOrder` sudah punya gerbangnya sendiri:

```kotlin
// core/domain/sampling/SamplingOrder.kt
private fun requireStageGate(target: SamplingPipelineStage) {
    if (pipelineStage != CAM_PROGRAMMING || target != MACHINE_KNITTING) return
    val camInput = stageInputFor(CAM_PROGRAMMING)
    …
}
```

Perhatikan: gerbang itu **murni**. Ia hanya membaca `stageInputs` milik agregatnya sendiri.

Gerbang baru kita butuh status Surat Jalan — dan itu **milik agregat lain**. Godaannya:

```kotlin
// ❌ DITOLAK
fun advancePipelineStage(target: …, manifests: List<SuratJalanManifest>)
```

Kenapa ditolak? Karena itu membuat `SamplingOrder` bergantung pada agregat lain lewat pintu belakang.
Agregat adalah **batas konsistensi**: yang ada di dalamnya dijamin konsisten dalam satu transaksi.
Begitu sebuah agregat butuh membaca isi agregat lain untuk memvalidasi dirinya, batas itu bocor —
dan setiap pemanggil `advancePipelineStage` mendadak wajib tahu cara mengambil manifest.

```kotlin
// ✅ core/domain/sampling/usecases/AdvanceSamplingStageUseCase.kt
class AdvanceSamplingStageUseCase(private val legsUseCase: GetFlowTransferLegsUseCase?) {
    suspend operator fun invoke(command: AdvanceSamplingStageCommand) = runCatching {
        requireLegReceived(command)                      // aturan lintas-agregat: di sini
        command.order.advancePipelineStage(…)            // aturan internal agregat: tetap di sana
    }
}
```

**Aturan praktis**: validasi yang hanya butuh isi agregat itu sendiri → invarian entity. Validasi
yang butuh melihat agregat lain → use case.

Satu detail yang mudah terlewat:

```kotlin
FlowLegStatus.DIKIRIM ->
    "Barang menuju ${leg.destination.displayLabel} masih dalam perjalanan. …"
```

**`DIKIRIM` tetap ditolak.** Barang yang masih di bak mobil bukan barang yang bisa dijahit. Menerima
status itu sebagai cukup akan membuat gerbangnya hanya seremonial.

---

### Blok F: `leg_key` — jembatan deterministik ke dokumen

Bagaimana menghubungkan leg (dihitung di memori) dengan Surat Jalan (tersimpan di DB)?

Godaannya: cocokkan tuple `(transferType, origin, destination, vendorRef)`. Kelihatan cukup — sampai
satu SPK melewati Gedung A→B **dua kali** (rajut → bordir → balik), atau memakai vendor yang sama
untuk dua proses. Tuple-nya identik; dokumennya bukan.

```kotlin
fun keyFor(from: FlowNodeRef, to: FlowNodeRef, transferType: TransferType): String =
    "${from.key}>${to.key}:${transferType.name}".take(MAX_LEG_KEY_LENGTH)
```

```sql
-- V58__surat_jalan_leg_key.sql
ALTER TABLE surat_jalan_manifests ADD COLUMN IF NOT EXISTS leg_key VARCHAR(120);
CREATE INDEX IF NOT EXISTS idx_surat_jalan_manifests_leg
    ON surat_jalan_manifests (tenant_id, subject_id, leg_key);
```

**Mengapa blok ini ditulis begini?**

- `transferType` ikut masuk kunci karena satu pasangan simpul melahirkan **dua** leg berlawanan arah
  pada kasus makloon.
- `NULL` tetap sah: dokumen yang diterbitkan manual tidak melayani leg mana pun. Baris lama jatuh ke
  pencocokan heuristik dan **ditandai** `isLegacyMatch` — tebakan tidak boleh disamarkan sebagai fakta.
- Indeksnya `(tenant_id, subject_id, leg_key)` karena pencarian selalu dalam konteks satu SPK.

---

### Blok G: Penjagaan wewenang di server, bukan di tombol

```kotlin
// server/routes/TenantLocationRoutes.kt
get {
    val decision = call.factoryFlowDecision(tenant, roleRepository, moduleAssignmentRepository)
    if (!call.requireFactoryFlowAccess(decision, AccessLevel.VIEW)) return@get
    …
}
put {
    if (!call.requireFactoryFlowAccess(decision, AccessLevel.MANAGE)) return@put   // MANAGE, bukan OPERATE
    …
}
```

**Mengapa blok ini ditulis begini?**

1. **Menyembunyikan tombol bukan penjagaan.** Ini pelajaran yang sudah dibayar mahal di repo ini —
   baca KDoc `OrgChartAccessGuard.kt`: dulu operator jahit yang modulnya tertutup tetap menerima
   seluruh daftar karyawan lengkap dengan email hanya dengan memanggil API-nya langsung.
2. **`MANAGE`, bukan `OPERATE`.** Ini bukan data harian; ini topologi yang menentukan kapan gerbang
   menutup. **Siapa pun yang bisa menulisnya bisa mematikan gerbang itu dari luar.**
3. **Bergerbang di `FACTORY_FLOW`, bukan modul baru.** Menambah `BusinessModule` merembet ke matriks
   RBAC, penugasan divisi, entitlement, seed tiap tenant, dan `when` exhaustive di workspace.
   `AppNavScreen.TRACEABILITY` sudah memilih jalan menumpang ini lebih dulu, dengan komentar yang
   menyebut alasannya. Efek sampingnya menyenangkan: wewenangnya **sudah ter-seed di V19** — Owner
   dan PPIC `MANAGE`, Sales/Gudang/Operator `NONE`. Tidak perlu migrasi wewenang sama sekali.

> `MASTER_DATA` sempat jadi kandidat dan ditolak: di sana Staff Gudang punya `OPERATE`, jadi
> menumpang di situ ikut memberi mereka hak menulis kecuali ditambah pemeriksaan khusus.

---

### Blok H: Kelas visual yang berbeda, bukan sekadar warna berbeda

```kotlin
// app/shared/presentation/designsystem/ClayModifier.kt
fun Modifier.clayDashedOutline(shape: Shape, background: Color, outline: Color, …) = this
    .clip(shape)
    .background(background)
    .drawBehind {
        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            color = outline,
            style = Stroke(width = …, pathEffect = PathEffect.dashPathEffect(…))
        )
    }
```

**Mengapa blok ini ditulis begini?**

- **`drawBehind`, bukan `border`.** `Modifier.border` tidak menerima `PathEffect`. Konsekuensinya
  outline digambar sebelum konten — cukup, karena permukaan ini rata dan tidak berbayang.
- **Diangkat ke `designsystem/`, bukan ditulis di tempat.** `PathEffect.dashPathEffect` sudah dipakai
  **3×** di `presentation/` sebelum ini. Aturan Tiga Kali sudah terlampaui; pemakaian keempat wajib
  mengangkatnya.
- **Garis putus bukan hiasan.** Chip stasiun berisi penuh = "di sini pekerjaan terjadi". Konektor
  tanpa isi + outline putus = "di sini barang berpindah". Kalau keduanya digambar sama, orang akan
  membaca konektor sebagai stasiun — persis salah paham yang seluruh fitur ini hindari.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan Kita | Alternatif | Mengapa Kita Memilih Ini? | Risiko Alternatif |
|---|---|---|---|
| Pengiriman = **edge turunan** | Chip "Pengiriman" di palet | Data turunan tidak bisa bertentangan dengan konfigurasi | Chip bisa ditaruh di celah yang lokasinya sama, atau lupa ditaruh di celah yang melintas — dan tidak ada yang tahu mana yang benar |
| `sealed interface LegEndpoint` | 3 field nullable | Aturan terbaca dari tipe; `when` dipaksa exhaustive compiler | Pembaca menebak kombinasi sah; penulis lupa mengisi `null` yang benar |
| `FlowNodeRef` polimorfik | Satu `Map` per lapisan | Satu sumber kebenaran, mustahil divergen | Dua peta yang wajib konsisten tanpa penegak — pasti berbeda suatu hari |
| Gerbang di **Use Case** | Parameter di `advancePipelineStage` | Batas agregat utuh | Setiap pemanggil wajib tahu cara mengambil manifest; agregat bocor |
| **Fail-open** pada pemetaan kosong | Fail-closed | Hari deploy tidak mengunci pabrik | Belum ada satu baris pemetaan pun → seluruh SPK berhenti, dan tidak ada yang tahu kenapa |
| Kolom `leg_key` | Cocokkan tuple asal/tujuan | Deterministik, tahan kunjungan berulang | Ambigu begitu satu SPK lewat gedung yang sama dua kali |
| Dua bounded context transfer **dipisah** | Satukan `InternalTransfer` & `SuratJalanManifest` | Satuan hitung beda (kg vs pcs); disiplin bukti bertentangan | Salah satu invarian harus dilemahkan — dan yang dilemahkan selalu yang lebih ketat |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

### Jebakan 1: Value class yang menolak string kosong, dipanggil dengan string kosong

```kotlin
// ❌ Kode yang benar-benar ada di repo ini sebelum fitur ini
val newProc = TenantOptionalProcess(
    tenantId = TenantId(""),      // TenantId punya require(value.isNotBlank())
    …
)
```

*Kenapa bahaya*: `TenantId("")` **melempar seketika**. Artinya menyisipkan proses opsional ke alur
per-desain **selalu crash** — dan karena ada di dalam `scope.launch` tanpa `runCatching`,
kegagalannya tidak pernah sampai ke layar sebagai pesan yang bisa dibaca.

*Kenapa lolos begitu lama*: karena tidak ada test yang menyentuh jalur itu, dan placeholder `""`
terlihat tidak berbahaya saat ditulis.

*Solusi kita*: ambil tenant dari `StoredTenantSlugProvider` seperti seluruh API client lain, dan
tulis KDoc yang menjelaskan kenapa nilainya tetap harus sah meski server akan menimpanya.

> **Pelajaran umum**: setiap kali kamu menulis placeholder ke dalam value class, cek dulu
> invariannya. Value class ada justru supaya nilai tidak sah tidak bisa dibuat — placeholder adalah
> nilai tidak sah yang sedang kamu paksa masuk.

### Jebakan 2: Seed yang menunjuk id yang tidak ada, dan tidak ada yang protes

```sql
-- V55, sudah ada sebelum fitur ini
INSERT INTO tenant_locations (id, tenant_id, …)
VALUES ('loc-rajut-01', 'demo-tenant', …);   -- id tenant aslinya 'ten-demo-001'
```

*Kenapa bahaya*: `tenant_locations.tenant_id` **tidak punya foreign key** di V55, jadi baris yatim itu
masuk tanpa keluhan. Selama tabelnya tidak pernah di-query, tidak ada yang tahu.

Begitu konfigurasi mulai dibaca, akibatnya **nyata dan senyap**: repository mencari
`tenant_id = 'ten-demo-001'`, tidak menemukan apa pun, lalu menyimpulkan "tenant ini tidak punya
lokasi" — yang secara kebetulan **tampak persis seperti perilaku fail-open yang benar**. Nol error.
Nol petunjuk.

*Cara menemukannya*: menjalankan servernya sungguhan dan memanggil API-nya, bukan hanya
mengompilasi. Test unit tidak akan pernah menangkap ini.

*Solusi kita*: V60 membetulkan ketiga tabel, dan tidak menyunting V55/V59 — checksum Flyway sudah
tercatat di basis data yang berjalan.

> **Pelajaran umum**: kolom yang mereferensikan tabel lain tanpa FK adalah bom waktu. Dan bug yang
> gejalanya sama persis dengan perilaku normal adalah bug yang paling mahal ditemukan.

### Jebakan 3: Mengira satu pintu sudah menutup semua jalan

`requireStageGate` hanya dipanggil dari `advancePipelineStage`. Tapi `pipelineStage` juga dimutasi di
**empat tempat lain** di `SamplingOrder.kt`:

| Method | Mutasi |
|---|---|
| `assignMakloonVendor` | → `LINKING_ASSEMBLY` |
| `recordVendorReturn` | → `FINISHING_QC` |
| `addFinishingDeposit` | `LINKING_ASSEMBLY → FINISHING_QC` |
| `completeQcInspection` | `FINISHING_QC → IN_DELIVERY` |

*Kenapa bahaya*: yang pertama adalah **persis kejadian makloon**. Gerbang yang hanya dipasang di satu
pintu akan dilewati justru pada transisi yang paling mungkin lintas-lokasi.

*Cara menemukannya*: jangan cari pemanggil fungsi gerbangnya — cari **semua tempat yang mengubah
field-nya**:

```bash
grep -n "pipelineStage = " core/src/commonMain/.../SamplingOrder.kt
```

*Status saat ini*: **belum ditutup**, dan itu dinyatakan eksplisit di KDoc
`AdvanceSamplingStageUseCase`. Kebocoran yang diketahui dan ditulis jauh lebih baik daripada
kebocoran yang disangka sudah beres.

### Jebakan 4: Fail-closed yang terdengar lebih aman

Naluri pertama: "kalau belum ada pemetaan lokasi, tolak saja — lebih aman."

*Kenapa bahaya*: pada hari deploy **belum ada satu baris pemetaan pun**. Fail-closed berarti seluruh
SPK di seluruh pabrik berhenti serentak, dengan pesan yang tidak dimengerti siapa pun.

*Solusi kita*: simpul tanpa pemetaan **transparan** — tidak melahirkan leg dan tidak memutus barisan.
Keamanannya datang dari admin yang mengisi pemetaan, bukan dari sistem yang mengunci diri.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### Strategi berlapis

| Lapisan | Cara uji | Kenapa |
|---|---|---|
| Derivasi & status | Unit test murni, tanpa DB | 33 test jalan dalam 3 detik — bisa dijalankan tiap simpan |
| Gerbang | Unit test dengan fake repository | Menguji keputusan, bukan SQL |
| Migrasi & API | **Jalankan servernya** | Satu-satunya cara menangkap Jebakan 2 |
| UI | Jalankan dan lihat dengan mata | Bug layout tidak tertangkap test mana pun |

### Test yang paling penting bukan yang paling rumit

```kotlin
@Test
fun `derive legs when single site without subcontract should produce none`() {
    val legs = derive(config = config())
    assertEquals(emptyList(), legs, "Pabrik satu atap tidak boleh melihat konektor apa pun")
}
```

Test ini terlihat sepele. Ia justru yang paling berharga: **mayoritas tenant adalah konveksi satu
atap**, dan janji utama fitur ini adalah mereka tidak terbebani sama sekali. Kalau test ini merah,
fiturnya merugikan lebih banyak orang daripada yang dibantunya.

### Test yang mengoreksi asumsi penulisnya

Saat merencanakan, saya menulis *"dua proses subkon dengan vendor berbeda → 4 leg"*. Testnya merah:
hasilnya **3**.

Ternyata implementasinya benar dan ekspektasi saya yang salah — alur tidak menyebut barang mampir
balik ke pabrik di antara dua vendor, jadi barangnya berpindah langsung vendor→vendor. Mengarang
singgahan itu berarti mesin menebak.

```kotlin
// Tiga leg, bukan empat: kalau pabrik memang ingin barangnya kembali dulu, itu dinyatakan
// dengan menaruh simpul in-house di antara keduanya — keputusan tenant, bukan asumsi mesin.
assertEquals(3, legs.size)
assertEquals("CV Jaya → CV Warna", legs[1].summary)
```

> **Pelajaran**: test yang merah tidak selalu berarti kodenya salah. Kadang ia sedang memberi tahu
> bahwa **model mentalmu** yang perlu diperbaiki.

### Verifikasi end-to-end yang benar-benar dijalankan

```bash
# 1. Migrasi benar-benar naik
./gradlew :server:run           # → "Successfully applied 2 migrations … now at version v59"

# 2. Regresi utama: satu atap = nol leg
curl -H "Authorization: Bearer $TOKEN" .../flow-legs
# → {"legs":[],"orphanSjNumbers":[]}

# 3. Nyalakan multi-site → tepat satu leg, di celah yang benar
# → STAGE:MACHINE_KNITTING>STAGE:LINKING_ASSEMBLY:INTERNAL_SITE_TRANSFER

# 4. Gerbang menolak
curl -X POST … -d '{"targetStage":"LINKING_ASSEMBLY"}'
# → HTTP 422
#   "Tahap Linking & Tambahan ada di Gedung Jahit & Finishing.
#    Terbitkan Surat Jalan dari Gedung Rajut & Bordir lebih dulu."

# 5. Override tercatat, bukan lewat diam-diam
# → actorRole: "TENANT_ADMIN (lewat gerbang: Mobil pickup rusak, diantar manual)"
```

Langkah 1 inilah yang menemukan Jebakan 2. **Kompilasi hijau bukan bukti fitur bekerja.**

### Audit kepatuhan yang wajib sebelum merge

```bash
# Nol literal warna baru di luar theme
grep -n "Color(0xFF" app/shared/.../presentation/sampling/components/ProcessFlow*.kt

# Nol Modifier.shadow, nol RoundedCornerShape telanjang, nol Card/Button Material
grep -nE "Modifier\.shadow|RoundedCornerShape\(|^\s+(Card|Button)\(" …

# Batas ukuran file per lapisan
git diff --name-only --diff-filter=ACM main...HEAD -- '*.kt' | xargs wc -l | sort -rn | head
```

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1 — Tutup satu pintu samping.** Salurkan `assignMakloonVendor` melalui
      `AdvanceSamplingStageUseCase`. Pertanyaan yang harus kamu jawab dulu: method itu mengubah
      `finishingPath` **dan** `pipelineStage` sekaligus — apakah gerbangnya harus menolak keduanya,
      atau hanya perpindahan tahapnya? Tulis testnya sebelum kodenya.

- [ ] **Tantangan 2 — Buat layar pengaturan lokasi.** Endpoint `GET/PUT /api/tenant/locations` sudah
      ada dan terjaga. Yang belum ada layarnya. Petunjuk: rute baru di `AppNavScreen` dengan
      `isNavMenuItem = false` bergerbang `FACTORY_FLOW`, mengikuti pola `SURAT_JALAN`. Pikirkan:
      bagaimana menampilkan 6 tahap + 12 stasiun + proses opsional tanpa membuat layarnya jadi
      daftar 20 baris dropdown yang melelahkan?

- [ ] **Tantangan 3 — Tambahkan `sequenceWithinAnchor`.** Saat ini dua proses opsional dengan anchor
      yang sama tidak punya urutan yang dinyatakan, dan tidak punya celah di antaranya. Rancang
      perubahannya — dan sebelum menulis kode, jawab dulu: apa yang terjadi pada `legKey` yang sudah
      tersimpan ketika urutan berubah? (Petunjuk: lihat `issuedLegsLostBy` di `SamplingFlowRoutes`.)

- [ ] **Tantangan 4 — Cari jebakan berikutnya sendiri.** Jalankan
      `grep -rn "REFERENCES" server/src/main/resources/db/migration/*.sql` dan bandingkan dengan
      kolom `*_id` yang **tidak** punya `REFERENCES`. Berapa banyak kolom lain yang bisa menampung
      id yatim seperti Jebakan 2?
