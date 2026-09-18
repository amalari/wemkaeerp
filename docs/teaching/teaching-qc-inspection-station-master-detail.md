# 🎓 Modul Pembelajaran: Stasiun Inspeksi QC — Dari Daftar Kartu ke Master-Detail

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design, Compose Multiplatform, Master-Detail Layout, Integritas Data Form, Clay Design System
> **Prasyarat**: Dasar Compose (`remember`, `State`, recomposition), paham lapisan `core` vs `app/shared`, pernah membaca [`.claude/rules/design-system-rules.md`](../../.claude/rules/design-system-rules.md)
> **Referensi Task**: Refactor UI/UX layar `/quality-control` (branch `feat/modules`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Apa yang sebenarnya dikerjakan orang di layar ini?

Bayangkan meja panjang di ujung lantai produksi. Di atasnya ada tumpukan baju rajut yang baru
keluar dari finishing, sebuah meteran kain, dan satu layar. Petugas QC mengambil satu baju,
membentangkannya, mengukur panjang badan, lebar dada, panjang tangan — lalu membandingkannya
dengan *size chart* yang dikirim buyer. Kalau selisihnya lebih dari 1 cm, baju itu bermasalah.
Kalau ada bolong, jahitan loncat, atau belang benang, itu masalah lain lagi.

Hasilnya bukan sekadar "centang di aplikasi". Hasilnya adalah **berita acara** — dokumen yang
dikirim ke buyer sebagai bukti bahwa sampel ini sudah diperiksa. Kalau buyer komplain enam
minggu kemudian, lembar inilah yang dibuka.

### Kekacauan yang terjadi kalau dibuat asal-asalan

Versi lama layar ini punya satu baris kode yang terlihat sangat tidak berbahaya:

```kotlin
var actualBodyLength by remember(order.id) { mutableStateOf(finishedChart.bodyLength.toString()) }
```

Kolom "hasil ukur" **diisi lebih dulu dengan nilai target**. Niatnya mungkin baik — "biar
petugas tinggal mengubah yang meleset". Tapi konsekuensinya:

1. Begitu dialog dibuka, keempat baris langsung menampilkan badge hijau `Lolos (+/-0.0cm)`.
2. Petugas yang buru-buru menekan `QC PASSED` tanpa mengukur apa pun.
3. Sistem menyimpan **empat pengukuran sempurna yang tidak pernah dilakukan siapa pun**, dan
   mengirimkannya ke buyer sebagai bukti.

Ini bukan bug tampilan. Ini bug integritas data yang bentuknya kebetulan berupa nilai awal
sebuah `TextField`. Pelajaran pertamanya: **nilai default sebuah form adalah keputusan
arsitektural, bukan kenyamanan UI.**

### Masalah kedua: dialog untuk pekerjaan yang diulang 40 kali sehari

Lembar inspeksi dulu berupa `Dialog`. Modal masuk akal untuk hal yang jarang dilakukan dan
harus mengunci perhatian ("Yakin mau hapus?"). Tapi di stasiun QC, mengisi lembar inspeksi
**adalah seluruh pekerjaannya**. Membuka-menutup modal puluhan kali sehari itu gesekan murni,
dan selama modal terbuka, antreannya hilang dari pandangan.

### Hasil akhir yang diharapkan

Layar satu-tugas bergaya *master-detail*: antrean kerja terurut di kiri, lembar inspeksi yang
selalu terbuka di kanan. Petugas memilih SPK, mengukur, memutuskan, lalu SPK berikutnya sudah
menunggu — tanpa satu pun modal.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Ini refactor, bukan fitur dari nol. Tapi urutannya tetap sama, dan urutannya penting.

### Langkah 0: Baca dulu domain yang sudah ada — jangan langsung buka file Composable

Godaan terbesar saat disuruh "perbaiki UI" adalah langsung membuka file layarnya. Jangan.

Waktu task ini dimulai, ternyata di `core/domain/sampling/qc/QcInspection.kt` **sudah ada**
agregat kaya berisi `suggestedResult`, `missingPassRequirements`, `isResultConsistentWithFindings`,
dan enum `QcDefectType` lengkap dengan `DefectLiability`. Semua aturan yang dibutuhkan sudah
ditulis orang sebelumnya — dan UI-nya tidak memakai satu pun.

> **Mental model**: kalau UI terasa "bodoh", curigai dulu bahwa domainnya pintar tapi tidak
> tersambung. Memperbaiki UI sering kali berarti **menyambungkan kabel**, bukan menulis aturan baru.

### Langkah 1: Rapikan rumah domain dulu (`core`)

Tipe QC tercecer: `QcInspectionReport` (yang dipakai persistensi) ada di
`SamplingOrderValueObjects.kt` yang sudah 308 baris, sementara agregat `QcInspection` yang kaya
ada di package terpisah dan tidak dipakai siapa-siapa.

Yang dikerjakan: pindahkan tipe QC ke filenya sendiri, `core/domain/sampling/QcInspectionReport.kt`,
lalu **bawa aturannya ke tipe yang benar-benar dipakai**. Kenapa tidak sekalian migrasi ke
agregat `QcInspection`? Karena itu berarti menyentuh codec, tabel Postgres, dan route server —
pekerjaan yang jauh lebih besar dan berisiko, untuk keuntungan yang sama. Aturan bisa pindah
tanpa skema ikut pindah.

### Langkah 2: Model UI murni sebelum satu pun `@Composable`

`QcQueueUiModel.kt` berisi `buildQcQueue()` — fungsi biasa, bukan Composable. Ia menerima
`List<SamplingOrder>` + `Instant`, mengembalikan `List<QcQueueItem>` yang sudah terurut dan
sudah dihitung lama tunggunya.

Kenapa dipisah? Karena fungsi murni **bisa diuji tanpa menyalakan Compose sama sekali**, dan
karena menghitung di dalam Composable berarti menghitung ulang setiap recomposition.

### Langkah 3: State form, masih tanpa `@Composable`

`QcInspectionFormState.kt` — kelas biasa pemegang `mutableStateOf`. Di sinilah aturan "kolom
dimulai kosong" dan sanitasi input desimal tinggal.

### Langkah 4: Komponen render, dari yang terkecil

`QcInspectionSections.kt` (baris POM, baris cacat, field catatan) → `QcQueuePane.kt` (panel
kiri) → `QcInspectionPane.kt` (panel kanan) → baru `QcInspectorWorkspaceScreen.kt` sebagai
*shell* yang hanya merakit.

### Langkah 5: Hapus yang digantikan

`QcInspectionDialog.kt` dihapus, bukan dibiarkan "siapa tahu dipakai". Cek dulu pemanggilnya:

```bash
grep -rn QcInspectionDialog app --include="*.kt"
```

Kalau hanya satu pemanggil dan pemanggil itu kita ganti, file itu sudah jadi kode mati.

### Langkah 6: Kompilasi 5 target, lalu **jalankan dan lihat dengan mata**

Ini bukan formalitas — dua bug nyata di task ini baru ketahuan setelah screenshot. Detailnya di §6.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Aturan domain yang tadinya menganggur

[`core/.../domain/sampling/QcInspectionReport.kt`](../../core/src/commonMain/kotlin/com/eventverse/app/domain/sampling/QcInspectionReport.kt)

```kotlin
val suggestedResult: QcInspectionResult
    get() = when {
        hasSizeDeviation -> QcInspectionResult.REJECT
        defectTypes.any { it.liability != DefectLiability.FACTORY_WORKMANSHIP } -> QcInspectionResult.REJECT
        defectsFound.isNotEmpty() -> QcInspectionResult.REWORK
        else -> QcInspectionResult.PASSED
    }

val missingPassRequirements: List<String>
    get() = buildList {
        if (qcResult != QcInspectionResult.PASSED) return@buildList
        if (pomMeasurements.isEmpty()) {
            add("Minimal satu titik ukur (POM) wajib diisi sebelum QC dinyatakan lolos.")
        }
    }
```

**Mengapa blok ini ditulis begini?**

- **`suggestedResult` menyarankan, tidak memaksa.** Buyer kadang menyetujui deviasi 1,5 cm lewat
  email. Sistem yang memaksa REJECT akan dilawan penggunanya (dan mereka akan menang — dengan
  cara mengisi data bohong). Yang benar: sistem menyatakan pendapatnya, manusia memutuskan, dan
  penyimpangan tercatat lewat `isResultConsistentWithFindings`.
- **`missingPassRequirements` mengembalikan `List<String>`, bukan `Boolean`.** Boolean cuma bisa
  bilang "tidak boleh"; daftar kalimat bisa bilang **kenapa** tidak boleh, dan kalimat itu
  langsung bisa ditampilkan di bilah keputusan. UI tidak perlu menerjemahkan apa pun.
- **Aturan tinggal di `core`, bukan di Composable.** Kalau besok ada endpoint server yang
  menerima lembar QC dari aplikasi Android inspektor, aturan yang sama ikut terbawa gratis.

Perhatikan juga penanganan data lama:

```kotlin
private fun resolveDefectType(raw: String): QcDefectType? {
    val trimmed = raw.trim()
    return QcDefectType.entries.firstOrNull { it.name == trimmed }
        ?: QcDefectType.entries.firstOrNull { it.displayName.equals(trimmed, ignoreCase = true) }
}
```

Kolom `defectsFound` bertipe `List<String>` dan sudah berisi data lama seperti `"Jarum Patah"`.
Kita ingin menyimpan `QcDefectType.name` supaya `liability` bisa dipulihkan, **tanpa** migrasi
skema. Solusinya: tulis sebagai nama enum, baca dengan toleransi dua bentuk. Ini pola umum yang
layak dihafal — *write narrow, read wide*.

### Blok B: Nilai awal form sebagai keputusan arsitektural

[`app/shared/.../presentation/qc/QcInspectionFormState.kt`](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/qc/QcInspectionFormState.kt)

```kotlin
val actualByKey: SnapshotStateMap<String, String> = mutableStateMapOf()

fun setActual(key: String, raw: String) {
    val sanitized = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
    if (sanitized.count { it == '.' } > 1) return
    actualByKey[key] = sanitized
}

val measurements: List<QcPomMeasurement>
    get() = pomFields.mapNotNull { field ->
        val actual = actualOf(field.key).toDoubleOrNull() ?: return@mapNotNull null
        QcPomMeasurement(pomName = field.label, targetCm = field.targetCm, actualCm = actual)
    }
```

**Mengapa blok ini ditulis begini?**

- **Map kosong, bukan map berisi target.** Ini inti perbaikannya. "Belum diukur" adalah keadaan
  yang sah dan berbeda dari "diukur dan pas".
- **`mapNotNull` di `measurements`.** Hanya baris yang benar-benar diketik yang masuk berita
  acara. Baris kosong tidak diam-diam berubah jadi angka nol — nol itu pengukuran, dan
  pengukuran palsu adalah persis masalah yang sedang kita perbaiki.
- **Sanitasi menerima koma.** Keypad Indonesia mengetik `70,5`. Menolaknya berarti memaksa
  petugas berkelahi dengan keyboard sambil memegang meteran.

Dan titik ukurnya diturunkan dari data, bukan dari daftar tetap:

```kotlin
fun pomFieldsFor(chart: SizeMeasurement): List<QcPomField> = listOf(
    QcPomField("bodyLength", "Panjang Baju (Body Length)", chart.bodyLength),
    // … 11 titik ukur …
).filter { it.targetCm > 0.0 }
```

Versi lama menampilkan 4 POM yang ditulis tangan di Composable. Versi ini menampilkan semua
titik yang **buyer memang tetapkan** (target > 0). Untuk SPK-SMP-0008 hasilnya 9 baris, bukan 4
— lima titik ukur yang dulu tidak pernah diperiksa siapa pun.

### Blok C: Antrean sebagai fungsi murni

[`app/shared/.../presentation/qc/QcQueueUiModel.kt`](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/qc/QcQueueUiModel.kt)

```kotlin
fun buildQcQueue(orders: List<SamplingOrder>, now: Instant): List<QcQueueItem> =
    orders.asSequence()
        .filter { /* ada barang fisik yang bisa diperiksa */ }
        .map { order -> order.toQueueItem(now) }
        .sortedWith(
            compareBy<QcQueueItem> { it.bucket.ordinal }
                .thenByDescending { it.waitingFor }
        )
        .toList()
```

**Mengapa blok ini ditulis begini?**

- **`now` adalah parameter, bukan `Clock.System.now()` di dalam.** Fungsi jadi deterministik dan
  bisa diuji: beri `now` tetap, harapkan urutan tetap. Memanggil `Clock` di dalam fungsi berarti
  hasilnya berubah tiap dipanggil — mustahil ditulis assertion-nya.
- **Urutan dua tingkat: bucket dulu, baru lama tunggu.** Yang butuh keputusan naik ke atas, dan
  di dalam tiap kelompok berlaku FIFO. Versi lama tidak mengurutkan sama sekali; yang tampil di
  layar adalah urutan penyimpanan, yang dari sudut pandang petugas terlihat acak.
- **Jam mulai menunggu di-reset setelah rework:**

```kotlin
val waitingSince = latest?.inspectedAt
    ?: stageHistory.lastOrNull { it.toStage == SamplingPipelineStage.FINISHING_QC }?.at
    ?: finishingDeposits.mapNotNull { it.createdAt }.maxOrNull()
    ?: updatedAt
```

Rantai fallback ini membaca dari yang paling akurat ke yang paling kasar. Kalau sampel baru
dikembalikan ke lantai produksi untuk rework, menghitung tunggunya dari hari SPK dibuat akan
menampilkan "menunggu 12 hari" padahal barangnya bahkan belum kembali.

### Blok D: Bilah keputusan yang membaca saran dari domain

[`app/shared/.../presentation/qc/components/QcInspectionPane.kt`](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/qc/components/QcInspectionPane.kt)

```kotlin
val preview = remember(form.filledCount, form.defects.size) {
    QcInspectionReport(
        inspectorName = "-",
        inspectedAt = Clock.System.now(),
        pomMeasurements = form.measurements,
        defectsFound = form.defects.map { it.name },
        qcResult = QcInspectionResult.PASSED
    )
}
val suggested = preview.suggestedResult
val blockers = preview.missingPassRequirements
```

**Mengapa blok ini ditulis begini?**

- **Kita merakit objek domain hanya untuk bertanya kepadanya.** Ini terasa boros ("kenapa tidak
  hitung `if` saja di sini?"), tapi inilah yang mencegah aturan bercabang dua. Kalau logika
  saran ditulis ulang di Composable, suatu hari domain berubah dan UI tidak ikut — lalu tombol
  yang disorot berbeda dari yang divalidasi server.
- **`remember` dengan kunci `filledCount` dan `defects.size`** membatasi perakitan ulang hanya
  saat temuannya benar-benar berubah.

Lalu tombolnya:

```kotlin
enabled = canSubmit && (result != QcInspectionResult.PASSED || blockers.isEmpty())
```

`REWORK` dan `REJECT` selalu bisa ditekan (menaikkan keketatan tidak butuh bukti tambahan);
`PASSED` yang dijaga. Asimetri ini disengaja dan sesuai arah risikonya — meluluskan barang
buruk jauh lebih mahal daripada menahan barang bagus.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipakai | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Master-detail (panel kiri-kanan)** | Dialog/modal seperti versi lama | Stasiun QC adalah layar satu-tugas; antrean harus terlihat sementara mengukur. Ruang 1900px terpakai, bukan menganggur | Modal buka-tutup 40×/hari; antrean hilang saat lembar terbuka; ruang horizontal terbuang |
| **Master-detail** | Kanban 3 kolom (seperti `SamplingPipelineKanbanBoard`) | Kanban bagus untuk *melihat* beban kerja, buruk untuk *mengerjakan*. Kartu "selesai" menyita sepertiga layar permanen | Layar penuh kartu yang tidak butuh aksi; lembar inspeksi tetap harus modal |
| **Aturan di `core`, dibaca UI** | `if/else` langsung di Composable | Satu sumber kebenaran; ikut terbawa kalau nanti ada klien lain (Android/server) | Aturan bercabang dua; UI dan server bisa tidak sepakat |
| **Nilai awal kosong + gerbang `PASSED`** | Prefill target "biar cepat" | "Belum diukur" ≠ "diukur dan pas" | Berita acara berisi pengukuran fiktif yang dikirim ke buyer |
| **POM diturunkan dari `SizeMeasurement`** | Daftar 4 POM hardcode | Mengikuti apa yang buyer tetapkan per SPK | Titik ukur yang disepakati buyer tidak pernah diperiksa |
| **Simpan `QcDefectType.name` di kolom `String`** | Migrasi kolom ke tipe enum | `liability` pulih tanpa menyentuh Postgres/codec/route | Migrasi berisiko untuk keuntungan yang sama |
| **`ClayTextField` / `ClayCheckbox`** | `OutlinedTextField` Material + `colors(...)` manual | Kontrak 5 design system; blok styling tidak disalin lagi | Satu perubahan desain = menyentuh belasan file |

### Catatan: kenapa `ClayBreakpoints.MasterDetail` dipakai, bukan angka baru

Repo ini sudah punya preseden master-detail di CRM Leads
([`LeadsMasterDetailLayout.kt`](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/LeadsMasterDetailLayout.kt))
beserta tokennya: `ClayBreakpoints.MasterDetail` (840dp) dan `ClayPaneWidth.List` (380dp).

Menulis `if (maxWidth < 900.dp)` sendiri akan "berhasil" dan sekaligus memulai perpecahan:
dua layar master-detail dengan titik putus berbeda tanpa alasan. **Sebelum memilih angka,
cari dulu apakah angka itu sudah punya nama.**

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

### Jebakan 1: Mengisi form dengan nilai "yang mungkin benar"

- *Kenapa bahaya*: Nilai default yang kebetulan valid akan tersimpan sebagai fakta. Di form
  pengukuran, prefill target berarti sistem mencatat pengukuran yang tidak pernah terjadi.
- *Solusi elegan kita*: Mulai kosong, tampilkan target sebagai `placeholder`/label, dan biarkan
  "belum diisi" jadi keadaan yang punya tampilan sendiri (`Belum diukur`, badge abu-abu).
- *Cara mengenali di kode orang lain*: setiap `mutableStateOf(sesuatuDariDomain.toString())`
  pada field yang seharusnya diisi manusia patut dicurigai.

### Jebakan 2: Menghitung ulang aturan domain di dalam Composable

- *Kenapa bahaya*: Aturannya jadi ada dua. Yang satu diuji, yang satu tidak. Suatu hari mereka
  berbeda pendapat, dan yang dilihat pengguna adalah yang tidak diuji.
- *Solusi elegan kita*: rakit objek domain dari state form lalu **tanya** dia
  (`preview.suggestedResult`). Sedikit boros, sangat murah dibanding perbedaan aturan.

### Jebakan 3: Membaca `Clock.System.now()` di dalam Composable atau fungsi kalkulasi

- *Kenapa bahaya*: fungsi jadi tidak deterministik (mustahil diuji), dan di Composable nilainya
  berubah tiap recomposition sehingga memicu recomposition berikutnya.
- *Solusi elegan kita*: `now` sebagai parameter `buildQcQueue`, dibaca sekali di layar di dalam
  `remember(state.orders)`.

### Jebakan 4: Angka ringkasan dihitung dari sumber yang berbeda dari yang dirender

- *Kenapa bahaya*: Versi lama menampilkan badge "3 Menunggu QC" di atas daftar berisi **4**
  kartu, karena badge menghitung `pipelineStage == FINISHING_QC` sementara daftar memakai filter
  yang lebih longgar. Pengguna membaca itu sebagai "sistemnya rusak" — dan berhenti mempercayai
  angka lain di layar yang sama.
- *Solusi elegan kita*: `QcWorkspaceHeader(queue)` menerima **daftar yang sama** dengan yang
  dirender, lalu `count` dari situ. Mustahil tidak sinkron secara konstruksi.

### Jebakan 5: Menyingkat satuan waktu sampai ambigu

- *Kenapa bahaya*: label `"4h 18j"` (4 hari 18 jam) terbaca sebagai "4 jam 18 menit" oleh
  separuh pembaca, karena `h` juga berarti *hour*. Akibatnya antrean terlihat **terurut salah**
  padahal urutannya benar — bug yang hanya bisa ditemukan dengan melihat layarnya.
- *Solusi elegan kita*: satu satuan saja, dieja: `"4 hari"`, `"22 jam"`, `"35 mnt"`. Ketelitian
  menit tidak dipakai untuk apa pun di sini.

### Jebakan 6: Menyarankan "Lolos" pada lembar yang masih kosong

- *Kenapa bahaya*: `suggestedResult` mengembalikan `PASSED` saat tidak ada temuan — dan "tidak
  ada temuan" juga benar untuk lembar yang belum disentuh. Badge hijau di lembar kosong secara
  halus **mengajari** petugas bahwa meluluskan tanpa mengukur itu normal.
- *Solusi elegan kita*: bedakan "tidak ada temuan" dari "belum ada data":

```kotlin
val hasFindings = form.filledCount > 0 || form.defects.isNotEmpty()
// badge: hasFindings ? suggested.displayName : "Belum cukup data"
```

### Jebakan 7: Membiarkan file lama "siapa tahu dipakai"

- *Kenapa bahaya*: `QcInspectionDialog.kt` yang tidak dihapus akan tetap dikompilasi, tetap
  muncul di hasil pencarian, dan suatu hari ada yang memperbaiki bug di sana — di kode yang
  tidak pernah dijalankan.
- *Solusi elegan kita*: `grep` pemanggilnya, pastikan nol, hapus. Git yang mengingatnya.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### Lapis 1 — Kompilasi 5 target, bukan satu

```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
          :app:shared:jvmTest
```

> **Catatan jujur dari task ini**: target Android **gagal**, tapi bukan karena perubahan ini —
> `MockupCropDialog.kt` (commit `c68a358`, tidak tersentuh) memakai API Skia
> (`Paint`, `EncodedImageFormat`, `SamplingMode`) yang tidak tersedia di Android. Cara
> memastikannya bukan tebakan: `git status` pada file itu kosong, dan errornya tidak menyebut
> satu pun file QC. **Selalu pisahkan "rusak karena saya" dari "sudah rusak sebelum saya"
> dengan bukti, bukan dengan perasaan.**

### Lapis 2 — Unit test untuk yang memang bisa diuji

Karena `buildQcQueue` fungsi murni, testnya ringan dan tidak butuh Compose:

```kotlin
@Test
fun `queue when rework and waiting mixed should put rework first`() {
    val now = Instant.parse("2026-09-18T08:00:00Z")
    val queue = buildQcQueue(listOf(orderWaiting2Days, orderRework1Hour), now)

    assertEquals(QcQueueBucket.REWORK, queue.first().bucket)
}

@Test
fun `report when no measurement should block pass`() {
    val report = QcInspectionReport(
        inspectorName = "Budi", inspectedAt = now, qcResult = QcInspectionResult.PASSED
    )
    assertFalse(report.isReadyToPass)
}
```

Perhatikan penamaannya: `[what]_[condition]_[expected]` sesuai
[CLAUDE.md §9](../../.claude/CLAUDE.md).

### Lapis 3 — Jalankan dan lihat dengan mata

Ini **wajib**, bukan pelengkap. Dev server di repo ini menyajikan target **wasm**, jadi setelah
mengubah kode:

```bash
./gradlew :app:webApp:wasmJsDevelopmentExecutableCompileSync
# lalu muat ulang http://localhost:3000/quality-control
```

> Jebakan waktu yang nyata di task ini: rebuild pertama memakai
> `jsDevelopmentExecutableCompileSync` (target **js**) dan layarnya tidak berubah sama sekali.
> Cara memastikan target mana yang disajikan:
> ```bash
> lsof -a -p <PID_node> -d cwd -Fn   # → build/wasm/packages/...
> ```

**Dua bug yang hanya tertangkap di sini**, bukan oleh test mana pun: label waktu ambigu
(Jebakan 5) dan saran "Lolos" pada lembar kosong (Jebakan 6). Keduanya kompilasi hijau dan
logikanya "benar" — yang salah adalah apa yang dikomunikasikannya ke manusia.

### Lapis 4 — Uji interaksi, bukan cuma tampilan diam

Screenshot layar diam tidak membuktikan form bekerja. Yang membuktikan: mengetik `75` pada POM
bertarget `70` dan memastikan (a) outline baris berubah merah, (b) badge jadi `Deviasi +5.0 cm`,
(c) penghitung jadi `1 dari 9 terisi`, (d) **saran berubah jadi REJECT dan tombolnya tersorot**.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1 — Tutup lingkaran rework.** Saat ini keputusan `REWORK` tersimpan, tapi
      `pipelineStage` order tidak dikembalikan ke lantai produksi. Telusuri
      `SamplingUiEvent.SubmitQcInspection` sampai ke server, dan rancang: apakah transisi tahap
      itu tanggung jawab use case QC, atau efek samping yang dipancarkan lewat domain event?
      Tuliskan argumenmu sebelum menulis kodenya.

- [ ] **Tantangan 2 — Foto verifikasi.** `QcInspectionReport` sudah punya
      `verifiedPhotoFrontKey`, dan agregat `qc/QcInspection.kt` mewajibkannya untuk `PASSED`.
      Gerbang itu **sengaja belum dipasang** karena belum ada jalur unggah gambar di modul
      sampling (yang ada baru `DealRemoteDataSource` untuk file PO). Rancang jalurnya, lalu
      tambahkan syaratnya ke `missingPassRequirements`. Pertanyaan yang harus kamu jawab dulu:
      apa yang terjadi pada lembar QC lama yang sudah lolos tanpa foto?

- [ ] **Tantangan 3 — Riwayat, bukan cuma yang terakhir.** Panel kanan menampilkan
      `latestReport` saja, padahal domain menyimpan seluruh `qcInspections`. Untuk sampel yang
      sudah 3 kali rework, jejak itu justru datanya yang paling bernilai. Tambahkan bagian
      riwayat — dan sambil mengerjakannya, perhatikan batas 600 baris
      ([file-size-rules §2](../../.claude/rules/file-size-rules.md)): `QcInspectionPane.kt`
      sudah 325 baris, jadi kemungkinan besar kamu perlu file baru, bukan menambah di sana.

- [ ] **Tantangan 4 (analisis, tanpa menulis kode)** — Buka `git log` versi lama layar ini dan
      cari kapan baris prefill target masuk. Menurutmu kenapa waktu itu terlihat masuk akal, dan
      pengaman apa dalam proses review yang bisa menangkapnya lebih awal?
