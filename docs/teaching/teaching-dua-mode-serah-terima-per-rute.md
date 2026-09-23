# 🎓 Modul Pembelajaran: Dua Pola Serah Terima Antar Divisi (DIRECT vs ADMIN_HUB)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design, snapshot vs lookup, invarian bersyarat, migrasi yang melonggarkan `NOT NULL`, Compose state hoisting, `remember` pada parameter default
> **Prasyarat**: Kotlin dasar, paham `data class` & `enum`, pernah membaca `core/domain/fulfillment/InternalTransfer.kt`
> **Referensi Task**: Modul Fulfillment — pola serah terima per rute

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Buka `/fulfillment`. Sebelum fitur ini, alurnya **satu-satunya**:

```
operator → tuang bundel ke karung → tutup karung → timbang → foto →
ajukan → admin TTD → DIANTAR → diterima di tujuan
```

Pola itu benar untuk konveksi menengah ke bawah. Operator membawa bundel yang selesai ke
**meja admin** tiap jam; admin menyortir (satu SPK, satu size, satu warna), menuangkannya ke
karung, menutup karung, lalu mengirimkannya ke divisi lain. Karung **berpindah kustodi** di
meja itu — dan di situlah timbangan, foto, serta tanda tangan punya alasan: merekalah yang
membedakan "hilang sebelum meja admin" dari "hilang sesudahnya" saat selisih diselidiki.

Tapi ada pabrik yang mejanya bersebelahan dan operatornya mengantar sendiri. Untuk mereka,
sistem lama **tidak punya jalur sama sekali**: bundel bukan karung, jadi satu-satunya cara
adalah membuat karung berisi satu bundel — birokrasi murni tanpa informasi tambahan.

### Masalah nyata kalau dibiarkan

1. **Pabrik kecil menolak memakai modulnya.** Tiap serah terima antar meja menuntut timbang +
   foto + tunggu ACC. Operator berhenti mencatat, dan begitu pencatatan berhenti, seluruh
   telusur di hulu jadi sia-sia.
2. **Pencatatan ganda.** `WorkDeposit` sudah merekam operator, jumlah pcs, dan waktunya.
   Memaksa perjalanan karung untuk perpindahan satu atap berarti memotret kejadian fisik yang
   sama dari dua sudut — dan `FlowLegDerivation.kt:27-30` sudah menyatakan aturannya:
   *"mencatatnya dua kali membuat operator berhenti mengisi keduanya."*
3. **Melanggar prinsip composable.** [CLAUDE.md §11](../../.claude/CLAUDE.md): preset adalah
   *starter template*, bukan kunci mati. Satu pola kerja yang dipaksakan ke semua tenant adalah
   persis kunci mati itu.

### Analogi sederhana

Bayangkan mengirim paket. **ADMIN_HUB** adalah kirim lewat agen ekspedisi: ada loket, ada
timbangan, ada resi, ada tanda tangan. **DIRECT** adalah menitipkan ke tetangga sebelah rumah.
Menuntut resi dan timbangan untuk titipan ke tetangga bukan membuatnya lebih aman — hanya
membuat orang berhenti menitip lewat jalur resmi.

### Hasil akhir yang diharapkan

- Mode dipilih **per rute**, karena satu pabrik bisa memakai dua pola sekaligus.
- Tenant yang tidak menyentuh apa pun melihat perilaku **persis seperti kemarin**.
- Formulirnya **berubah bentuk sendiri** saat chip rute diklik.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

```
[1. Enum & invarian domain (core)]   ← simpul perubahannya ada di sini, bukan di UI
        │
[2. Konfigurasi + port repository]
        │
[3. Use case: kelayakan wadah & status awal]
        │
[4. Codec bersama]                    ← satu kontrak JSON untuk server & client
        │
[5. Migrasi + tabel + repository]
        │
[6. Route API]
        │
[7. UI]                               ← terakhir, tinggal menggambar
```

**Kenapa domain dulu?** Karena simpul kesulitannya ada di sana, dan menemukannya di akhir
berarti membongkar ulang lima lapisan. Lihat Blok A.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Satu properti yang mencampur dua pertanyaan

Ini bagian terpenting di seluruh task. Sebelumnya:

```kotlin
// core/domain/fulfillment/InternalTransferValueObjects.kt (SEBELUM)
val sudahDisetujui: Boolean get() = this != MENUNGGU_ACC && this != DITOLAK && this != DIPERIKSA
```

dipakai di:

```kotlin
// InternalTransfer.init (SEBELUM)
if (status.sudahDisetujui) {
    requireNotNull(approvedBy) { "Status ${status.displayName} wajib mencatat siapa yang menyetujui" }
    requireNotNull(approvalSignatureKey) { "ACC tanpa tanda tangan tidak sah" }
}
```

Perhatikan: `DIANTAR` masuk ke `sudahDisetujui`. Artinya **setiap karung yang sedang berjalan
wajib punya tanda tangan admin.** Di mode DIRECT tidak ada penyetuju sama sekali — syarat itu
mustahil dipenuhi, dan seluruh fitur mentok di sini.

Namanya sendiri yang menipu: `sudahDisetujui` terdengar seperti satu fakta, padahal ia menjawab
**dua** pertanyaan sekaligus — *"sudah lewat gerbang admin?"* dan *"barangnya sudah berangkat?"*
Selama hanya ada satu pola kerja, kedua jawaban itu selalu sama, jadi tidak ada yang sadar
mereka berbeda.

```kotlin
// SESUDAH — dua pertanyaan, dua properti
val sedangBerjalan: Boolean get() = this == DIANTAR || isFinal
val butuhAccAdmin: Boolean get() = this == MENUNGGU_ACC
```

**Mengapa blok ini ditulis begini?**

- **Properti yang menggabungkan dua konsep akan pecah begitu keduanya berbeda.** Gejalanya
  bukan "kodenya jelek", melainkan "fiturnya mustahil ditulis".
- **Pelajaran umum**: kalau sebuah fitur baru terasa mentok di satu baris invarian, curigai
  *nama* baris itu. Sering kali ia mewakili dua hal yang kebetulan selalu sama.

### Blok B: Invarian yang bercabang pada mode — dua arah, bukan satu

```kotlin
// core/domain/fulfillment/InternalTransfer.kt
if (handoverMode == HandoverMode.ADMIN_HUB) {
    requireNotNull(dispatchWeightKg) { "Berat dispatch wajib — timbang dulu sebelum mengajukan ke meja admin" }
    require(!dispatchScalePhotoKey.isNullOrBlank()) { "Foto timbangan dispatch wajib ada" }

    if (status.sedangBerjalan) {
        requireNotNull(approvedBy) { … }
        requireNotNull(approvalSignatureKey) { "ACC tanpa tanda tangan tidak sah" }
    }
} else {
    // Bukan sekadar "tidak wajib": terisi berarti record dibuat dengan aturan yang
    // bertentangan dengan modenya, dan menyimpannya diam-diam membuat audit berbohong.
    require(approvedBy == null && approvalSignatureKey == null) { … }
    require(status != SackTransferStatus.DITOLAK && status != SackTransferStatus.DIPERIKSA) { … }
}
```

**Mengapa blok ini ditulis begini?**

- **Cabang `else` bukan cabang kosong.** Naluri pertama adalah "di DIRECT syaratnya tidak
  berlaku, sudah". Tapi kalau `approvedBy` boleh terisi pada DIRECT, suatu hari akan ada baris
  yang mengaku di-ACC padahal tidak ada meja admin yang meng-ACC-nya. Invarian yang baik
  menolak **kedua** arah: yang kurang *dan* yang berlebih.
- **`DITOLAK` ikut dilarang** karena tanpa gerbang berangkat tidak ada yang bisa menolak
  keberangkatan. Kalau ini dilewatkan, `DIRECT + DITOLAK` tetap bisa dibuat — dan tidak ada
  yang tahu artinya apa.

### Blok C: Mode disimpan, bukan dilihat

```kotlin
data class InternalTransfer(
    …
    /** Snapshot, bukan dibaca ulang dari konfigurasi. */
    val handoverMode: HandoverMode,
```

**Mengapa blok ini ditulis begini?**

Pabrik boleh mengubah mode sebuah rute kapan saja. Kalau `handoverMode` dibaca dari tabel
konfigurasi saat record dimuat, mengubah rute dari DIRECT ke ADMIN_HUB akan membuat **setiap
record lama mendadak melanggar invarian** karena tidak punya tanda tangan admin.

Polanya sudah ada di repo ini: `WorkDeposit.tariffSnapshotIdr` membekukan tarif saat setoran
dibuat supaya perubahan tarif tidak merusak riwayat upah. Aturannya sama — **apa pun yang
dipakai memvalidasi masa lalu harus ikut disimpan bersama masa lalu itu.**

### Blok D: Aturan kelayakan wadah yang berbeda per mode

```kotlin
// core/domain/fulfillment/usecases/TransferLifecycleUseCases.kt
when (mode) {
    HandoverMode.ADMIN_HUB -> {
        require(container.isSack) {
            "Rute ${leg.displayName} lewat meja admin, jadi yang dikirim harus karung — " +
                "${TraceCodec.grouped(code)} adalah kartu bundel. Tuang dulu ke karung."
        }
        require(container.state == TraceContainerState.CLOSED) { … }
    }
    HandoverMode.DIRECT -> {
        val siap = (container.isSack && container.state == TraceContainerState.CLOSED) ||
            (container.isBundle && container.state == TraceContainerState.TALLIED)
        require(siap) { … }
    }
}
```

**Mengapa blok ini ditulis begini?**

- **Pesannya menyebutkan jalan keluarnya** ("Tuang dulu ke karung"), bukan sekadar menyatakan
  penolakan. Operator di lantai tidak membaca dokumentasi; pesan error adalah dokumentasinya.
- **DIRECT tetap menerima karung tertutup.** Pabrik boleh mengonsolidasi ke karung walau
  rutenya langsung; yang dilonggarkan adalah keharusannya, bukan kemampuannya.

Satu jebakan kecil di sini:

```kotlin
val declaredPcs = declaredPcsOverride
    ?: container.declaredPcs.takeIf { it > 0 }
    ?: error("Jumlah pcs wajib diisi untuk ${TraceCodec.grouped(code)} — kartu bundel tidak menyimpan hitungan baju jadi.")
```

`TraceContainer` bundel menghitung **lembar panel**, bukan pcs baju — `declaredPcs`-nya selalu
0. Menyalinnya begitu saja akan melanggar `require(declaredPcs > 0)` di entity, dengan pesan
yang menyesatkan. Sumber jujurnya adalah hitungan setoran operator.

### Blok E: Migrasi yang melonggarkan `NOT NULL` tanpa melemahkan disiplin

```sql
-- V61__fulfillment_handover_mode.sql
ALTER TABLE fulfillment_transfers
    ADD COLUMN IF NOT EXISTS handover_mode VARCHAR(16) NOT NULL DEFAULT 'ADMIN_HUB';

ALTER TABLE fulfillment_transfers ALTER COLUMN dispatch_weight_kg DROP NOT NULL;

ALTER TABLE fulfillment_transfers
    ADD CONSTRAINT ck_fulfillment_dispatch_evidence
        CHECK (
            handover_mode <> 'ADMIN_HUB'
            OR (dispatch_weight_kg IS NOT NULL AND dispatch_scale_photo_key IS NOT NULL)
        );
```

**Mengapa blok ini ditulis begini?**

- **`DEFAULT 'ADMIN_HUB'` inilah yang menjaga seluruh baris lama tetap sah.** Tanpa default,
  kolom `NOT NULL` pada tabel berisi data akan menolak ditambahkan.
- **`NOT NULL` diganti `CHECK` bersyarat, bukan dihapus.** Melonggarkan saja akan membiarkan
  baris ADMIN_HUB tanpa timbangan masuk diam-diam — persis disiplin bukti yang tidak boleh
  melemah. Batasannya tidak hilang; ia jadi *bersyarat*.
- **Nol `INSERT` seed.** Menyemai satu rute demo ke DIRECT berarti mengubah disiplin bukti
  sebuah tenant yang sedang berjalan hanya karena migrasi dipasang.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan Kita | Alternatif | Mengapa Kita Memilih Ini? | Risiko Alternatif |
|---|---|---|---|
| Mode **per rute** | Satu saklar per tenant | Satu pabrik bisa punya dua pola: Rajut→Finishing lewat meja admin (volume besar, perlu sortir size), Finishing→QC langsung (meja bersebelahan) | Pabrik hibrida terpaksa memilih satu pola dan mengakali yang lain |
| `handoverMode` **snapshot** di record | Lookup ke tabel konfigurasi saat hydrate | Record lama tetap sah saat konfigurasi berubah | Mengubah mode rute membuat seluruh riwayat melanggar invarian |
| Pecah `sudahDisetujui` | Longgarkan syaratnya untuk semua | Pelonggaran menyeluruh mematikan gerbang ADMIN_HUB juga | Karung lewat meja admin bisa berangkat tanpa TTD |
| `CHECK` bersyarat di DB | Cukup invarian domain | Domain bisa di-bypass lewat SQL/seed; DB adalah lapisan terakhir | Baris cacat masuk lewat migrasi atau skrip, ketahuan berbulan kemudian |
| Ketiadaan baris = "belum disentuh" | Semai semua rute dengan ADMIN_HUB | Bisa dibedakan dari "sengaja dikembalikan ke meja admin" | Layar konfigurasi tidak bisa menawarkan pengaturan awal |
| Bukti DIRECT = **hitungan pcs** | Tetap wajib timbang + foto | Menimbang satu bundel tiap serah terima adalah friksi yang tidak dibayar informasi | Operator berhenti mencatat — dan modul ini hanya berguna kalau dipakai |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

### Jebakan 1: Placeholder ke dalam value class ber-invarian

Ini **benar-benar terjadi saat mengerjakan task ini**, dua kali.

```kotlin
// ❌ Yang saya tulis
val routeConfig: FulfillmentRouteConfig = FulfillmentRouteConfig(TenantId("-"))
```

`TenantId` menuntut 3–64 karakter. `"-"` satu karakter → **melempar di konstruktor
`FulfillmentUiState`**, dan seluruh layar mati sebelum sempat menggambar.

Kembarannya di codec:

```kotlin
// ❌ decode() membangun TenantId dari payload yang tidak punya field itu
tenantId = TenantId(obj.string("tenantId") ?: "")   // → "TenantId cannot be blank", HTTP 500
```

*Kenapa lolos begitu lama*: kompilasi hijau, unit test domain hijau. Yang menangkapnya adalah
**menjalankan aplikasinya** — layar blank di browser dan HTTP 500 di `curl`.

*Solusi kita*: pemiliknya tidak pernah datang dari payload. Codec dipecah jadi `decodeModes()`
(tanpa tenant) dan `decode(obj, tenantId)` yang menerima tenant dari pemanggil — di server dari
sesi, di client dari slug aktif. Untuk UI state, `routeConfig` dibuat **nullable**, karena
"belum dimuat" memang null, bukan tenant palsu.

> **Pelajaran umum**: value class ada justru supaya nilai tidak sah tidak bisa dibuat.
> Placeholder adalah nilai tidak sah yang sedang kamu paksa masuk. Setiap kali menulis `""`
> atau `"-"` ke dalam value class, cek invariannya dulu.

### Jebakan 2: Parameter default Composable tanpa `remember`

```kotlin
// ❌ Ada di repo ini sejak modul fulfillment lahir
viewModel: FulfillmentViewModel = FulfillmentViewModel(tenantSlug = persona?.tenantSlug ?: "wemade-demo")
```

*Kenapa bahaya*: nilai default parameter Composable **dievaluasi ulang setiap rekomposisi**.
Setiap perubahan state melahirkan ViewModel baru yang `init`-nya memuat dari nol, dan state
sementara seperti `scannedSack` terhapus sebelum sempat digambar.

*Gejalanya*: kode karung dipindai → kolomnya kosong kembali → formulir pengajuan **tidak pernah
muncul**. Tidak ada error, tidak ada log. Hanya tombol yang "tidak melakukan apa-apa".

*Cara menemukannya*: bandingkan dengan `git stash` — jalankan UI pada kode asli untuk memastikan
apakah ini regresi atau bug lama. Ternyata lama.

```kotlin
// ✅
viewModel: FulfillmentViewModel = run {
    val tenantSlug = persona?.tenantSlug ?: "wemade-demo"
    remember(tenantSlug) { FulfillmentViewModel(tenantSlug = tenantSlug) }
}
```

### Jebakan 3: Mengira label ikut berubah sendiri

Setelah domain menerima bundel, UI masih berkata **"SCAN / KETIK KODE KARUNG"**, tombolnya
**"Pindai QR Karung"**, placeholder-nya **"W1SK-…."** — semuanya menyiratkan karung saja.
Fiturnya jalan, tapi tidak ada operator yang akan mencoba memindai bundel.

*Pelajaran*: kemampuan baru yang tidak diumumkan di label sama saja dengan tidak ada. Ini kelas
bug yang **tidak tertangkap test mana pun** — hanya tertangkap dengan melihat layarnya.

### Jebakan 4: Chip yang disembunyikan, bukan diredupkan

Saat kartu bundel dipindai, rute ber-mode ADMIN_HUB tidak bisa dipakai. Naluri pertama:
sembunyikan chip-nya.

*Kenapa bahaya*: chip yang hilang membuat orang mengira sistemnya rusak atau datanya belum
ter-seed. *Solusi kita*: chip tetap tampil, diredupkan, disertai alasan tertulis —
*"Rute yang diredupkan lewat meja admin — tuang dulu bundel ini ke karung."*

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### Strategi berlapis

| Lapisan | Cara uji | Kenapa |
|---|---|---|
| Invarian & mode | Unit test murni (17 test di `InternalTransferTest`) | Jalan dalam hitungan detik |
| Migrasi & API | **Jalankan servernya**, lalu `curl` | Satu-satunya cara menangkap Jebakan 1 |
| UI | **Jalankan dan lihat dengan mata** | Satu-satunya cara menangkap Jebakan 2, 3, 4 |

### Test yang paling penting bukan yang paling rumit

```kotlin
@Test
fun `route config without entries should default every route to admin hub`() {
    val config = FulfillmentRouteConfig(tenantId = tenant)
    SackRoute.entries.forEach { route ->
        assertEquals(HandoverMode.ADMIN_HUB, config.modeFor(route), "Rute $route berubah diam-diam")
    }
}
```

Terlihat sepele. Justru ini yang paling berharga: **mayoritas tenant belum menyetel apa pun**,
dan janji utama perubahan ini adalah mereka tidak merasakan perubahan sama sekali. Kalau test
ini merah, fitur ini merugikan lebih banyak orang daripada yang dibantunya.

Pasangannya yang menjaga arah sebaliknya:

```kotlin
@Test
fun `receive admin hub transfer without signature should still throw exception`() { … }
```

Tanpa test ini, pelonggaran TTD bisa diam-diam berlaku untuk semua mode.

### Verifikasi end-to-end yang benar-benar dijalankan

```bash
# Migrasi naik
./gradlew :server:run     # → flyway_schema_history: v61 success = t

# Regresi utama: tanpa konfigurasi, semua rute tetap ADMIN_HUB
curl …/api/tenant/fulfillment/route-settings
# → semua "mode":"ADMIN_HUB", "isExplicit":false

# ADMIN_HUB tetap menuntut bukti
curl -X POST …/transfers -d '{"leg":"QC_RAJUT_TO_FINISHING",…}'   # tanpa berat
# → HTTP 400 "Berat timbangan wajib diisi"

# ADMIN_HUB tetap menolak kartu bundel
# → HTTP 409 "…harus karung — W1SB-0100-3000-0164 adalah kartu bundel. Tuang dulu ke karung."

# DIRECT: bundel TALLIED langsung DIANTAR, tanpa berat & tanpa approver
# → handoverMode DIRECT, status DIANTAR, dispatchWeightKg None, approvedBy None

# DIRECT: selisih terdeteksi lewat pcs, bukan berat
# → DITERIMA_SELISIH (declared 5, received 4)
```

Langkah `:server:run` itulah yang menemukan Jebakan 1. **Kompilasi hijau bukan bukti fitur
bekerja.**

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1 — Layar pengaturan rute.** Endpoint `GET/PUT
      /api/tenant/fulfillment/route-settings` sudah ada dan terjaga MANAGE; layarnya belum.
      Petunjuk: `/api/tenant/locations` juga belum punya layar — rancang **satu** layar
      konfigurasi untuk keduanya, jangan dua layar setengah jadi.

- [ ] **Tantangan 2 — Rute yang bisa disusun tenant.** `SackRoute` masih enum yang ditambah
      developer. Sebelum menulis kode, jawab dulu: apa yang terjadi pada baris
      `fulfillment_route_settings` dan kolom `leg` di `fulfillment_transfers` ketika sebuah
      rute dihapus tenant? (Petunjuk: lihat bagaimana `FlowNodeRef.parse` mengembalikan `null`
      alih-alih melempar.)

- [ ] **Tantangan 3 — Cari jebakan berikutnya sendiri.** Jalankan
      `grep -rn "TenantId(\"\|TenantId(\"-\"\|Id(\"\")" --include='*.kt' app core server`
      dan bandingkan dengan invarian tiap value class. Berapa banyak placeholder lain yang
      menunggu meledak seperti Jebakan 1?

- [ ] **Tantangan 4 — Audit `remember` yang hilang.** Jebakan 2 hampir pasti tidak sendirian.
      Cari Composable lain yang membuat ViewModel di parameter default tanpa `remember`:
      `grep -rn "ViewModel = [A-Z].*ViewModel(" app/shared/src/commonMain`
