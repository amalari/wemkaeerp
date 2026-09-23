# 🎓 Modul Pembelajaran: Memecah Satu Tahap Jadi Empat — "Finishing & QC" di Alur Sampel

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design, Enum sebagai Kontrak Lintas Lapisan, Migrasi Data Destruktif (Enum Rename), Backward-Compatible Parsing, Flyway, Compose Multiplatform
> **Prasyarat**: Paham dasar enum Kotlin, `data class` immutable, Flyway, dan pernah membaca [teaching-dua-mode-serah-terima-per-rute.md](teaching-dua-mode-serah-terima-per-rute.md)
> **Referensi Task**: Fase 0 — "Papan Antrean Stasiun + Aliran Kartu Kerja"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Sampai hari ini, satu SPK sampel yang selesai dijahit masuk ke tahap bernama `FINISHING_QC` —
dan berdiam di sana sampai dinyatakan siap kirim. Di dalam satu nama itu sebenarnya terjadi
**empat pekerjaan oleh empat orang berbeda**: dicuci dan diberi softener, disetrika uap,
diperiksa QC, lalu dilipat dan dikemas.

Kenapa itu masalah? Karena buyer menelepon sales dan bertanya: *"Sampel saya sudah sampai mana?"*
Sales membuka papan, melihat tulisan **"Finishing & QC"**, dan… tidak bisa menjawab apa pun yang
berguna. Barangnya bisa jadi masih basah di bak cuci, bisa jadi sudah di meja pemeriksa, bisa
jadi tinggal dimasukkan polybag. Ketiganya tampak identik di layar.

Dan kalau sampelnya **hilang**? Tidak ada yang bisa ditanya, karena sistem tidak pernah mencatat
tangan siapa yang terakhir memegangnya.

### Analogi Sederhana

Bayangkan resi pengiriman paket yang hanya punya satu status: **"Sedang Diproses"** — dari gudang
asal sampai kurir terakhir. Secara teknis tidak salah, paketnya memang sedang diproses. Tapi resi
itu tidak menjawab satu pun pertanyaan yang membuat orang membukanya.

Status yang berguna adalah status yang **berubah setiap kali paket berpindah tangan**: diterima
gudang → dimuat truk → tiba di kota tujuan → dibawa kurir. Setiap batasnya adalah titik serah
terima, bukan titik ganti nama pekerjaan.

Itu persis aturan yang kita pakai di sini: **satu tahap = satu tangan.**

### Hasil Akhir yang Diharapkan

```
… 5 Linking & Tambahan
  6 Cuci & Softener      ← baru
  7 Setrika Uap          ← baru       menggantikan satu "FINISHING_QC"
  8 QC Finishing         ← baru
  9 Pengemasan           ← baru
 10 Terkirim (Tunggu ACC)
 11 ACC Produksi
```

Papan Kanban tetap **enam kolom** (bukan sembilan), tahapnya dibaca dari badge di kartu.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Ini bukan fitur baru; ini **mengubah kosakata yang sudah dipakai di 9 berkas dan 3 tabel**. Urutan
pengerjaannya berbeda dari fitur greenfield, dan urutan yang salah akan membuatmu memperbaiki hal
yang sama tiga kali.

### Langkah 0 — Cari dulu, jangan ketik dulu

```bash
grep -rn "FINISHING_QC" . --include="*.kt" --include="*.sql" | grep -v "/build/"
```

Satu perintah ini memberi tahu ukuran sebenarnya dari pekerjaan: 30+ titik, tersebar di domain,
codec, repository, route, migrasi, dan lima berkas Compose. **Sebelum tahu angkanya, kamu tidak
bisa memutuskan apakah nilai enum lama dihapus atau dipertahankan.**

### Langkah 1 — Ubah enum-nya, dan biarkan kompilator jadi checklist-mu

Di Kotlin, `when` atas enum yang **exhaustive** (tanpa `else`) akan gagal kompilasi begitu ada
anggota baru. Itu bukan gangguan — itu **daftar tugas gratis** yang lebih teliti daripada
`grep`-mu sendiri. Ubah enum lebih dulu, lalu ikuti jejak error merahnya satu per satu.

### Langkah 2 — Perbaiki transisi di entity (`core/`)

Kalau kamu mengubah UI lebih dulu, kamu akan menghabiskan setengah jam menebak kenapa kartu tidak
pindah kolom, padahal masalahnya di `completeQcInspection` yang masih melompat ke `IN_DELIVERY`.

### Langkah 3 — Pusatkan parsing sebelum menulis migrasi

Ini langkah yang paling sering dilewati. Penjelasannya di Blok B.

### Langkah 4 — Migrasi Flyway

### Langkah 5 — Sesuaikan test, lalu UI

### Langkah 6 — Jalankan servernya, curl, lalu **lihat layarnya dengan mata**

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Enum sebagai Kontrak Kustodi

```kotlin
// core/.../domain/sampling/SamplingOrderValueObjects.kt
enum class SamplingPipelineStage(val displayName: String, val order: Int) {
    …
    LINKING_ASSEMBLY("Linking & Tambahan", 5),
    CUCI_SOFTENER("Cuci & Softener", 6),
    SETRIKA_UAP("Setrika Uap", 7),
    QC_FINISHING("QC Finishing", 8),
    PENGEMASAN("Pengemasan", 9),
    IN_DELIVERY("Terkirim (Tunggu ACC)", 10),
    ACC_APPROVED("ACC Produksi", 11);

    val isOnFinishingFloor: Boolean
        get() = order in LINKING_ASSEMBLY.order..PENGEMASAN.order
}
```

**Mengapa blok ini ditulis begini?**

- **Nilai `FINISHING_QC` DIHAPUS, bukan dibiarkan berdampingan.** Godaannya besar: biarkan saja
  di sana sebagai "deprecated", biar tidak ada yang rusak. Tapi dua kosakata untuk satu kenyataan
  tidak pernah rukun — setengah tim akan terus memakai yang lama karena masih bisa dipilih, dan
  enam bulan lagi kamu punya papan dengan kolom hantu yang tidak ada yang berani hapus.
  **Kalau sebuah konsep sudah tidak ada, hapus namanya sekalian**; kompilator akan menunjukkan
  semua tempat yang perlu dipikirkan ulang, dan itu justru yang kamu mau.
- **`isOnFinishingFloor` dinyatakan sebagai RENTANG, bukan daftar.** `order in 5..9` otomatis
  mencakup tahap yang kelak disisipkan tenant di antaranya. Kalau ditulis sebagai
  `setOf(LINKING, CUCI, SETRIKA, …)`, tahap sisipan akan **hilang diam-diam** dari antrean meja
  finishing — bug yang tidak melempar error apa pun, hanya membuat satu SPK raib dari layar.
- **Properti ini ada di `core/`, bukan di berkas UI.** Sebelum ada properti ini, aturan "barang ada
  di lantai finishing" ditulis ulang dengan kalimat berbeda di tiga berkas Compose. Satu aturan
  yang ditulis tiga kali adalah satu aturan yang akan berbeda di tiga tempat setelah revisi
  berikutnya.

### Blok B: Alias Legacy — Jaring Pengaman yang Paling Mudah Dianggap Berlebihan

```kotlin
companion object {
    private val LEGACY_ALIASES = mapOf("FINISHING_QC" to CUCI_SOFTENER)

    fun parseOrNull(name: String?): SamplingPipelineStage? {
        if (name.isNullOrBlank()) return null
        return entries.firstOrNull { it.name == name } ?: LEGACY_ALIASES[name]
    }
}
```

**Mengapa blok ini ditulis begini?**

- **Karena `valueOf` yang gagal di repository TIDAK melempar error — ia jatuh ke `NEW_INTAKE`.**
  Lihat kode aslinya:

  ```kotlin
  // SEBELUM — terlihat aman, sebenarnya berbahaya
  pipelineStage = runCatching { SamplingPipelineStage.valueOf(row[…]) }
      .getOrNull() ?: SamplingPipelineStage.NEW_INTAKE
  ```

  Bayangkan satu baris lolos dari `UPDATE` migrasi. SPK yang tinggal dikemas akan muncul di papan
  sebagai **SPK yang baru masuk**. Tidak ada exception, tidak ada log merah, tidak ada yang
  kelihatan rusak — hanya satu kartu yang pindah ke kolom paling kiri dan tim mulai mengerjakan
  ulang program CAM untuk baju yang sebenarnya sudah jadi. **Kegagalan yang senyap selalu lebih
  mahal daripada kegagalan yang berisik.**
- **Parser dipusatkan karena pemanggilnya tersebar.** Ada **9 tempat** yang memanggil
  `SamplingPipelineStage.valueOf`: dua di `SamplingOrderCodec`, satu di `StageWorkInputCodec`, satu
  di `ProcessCatalogCodec`, dua repository Postgres, dua route, dan satu lagi. Menambal aliasnya
  satu per satu berarti sembilan kesempatan untuk lupa. Satu fungsi, sembilan pemanggil.
- **Alias menunjuk ke sub-tahap PERTAMA, bukan ke `QC_FINISHING`** yang namanya lebih mirip.
  Alasannya ada di Blok C.

### Blok C: Migrasi — Memilih Ke Mana Data Lama Mendarat

```sql
-- server/.../V63__sampling_finishing_split.sql
UPDATE sampling_orders
SET pipeline_stage = 'CUCI_SOFTENER'
WHERE pipeline_stage = 'FINISHING_QC';

-- Konfigurasi lokasi simpul alur ikut dipindahkan
UPDATE tenant_flow_node_locations
SET node_key = 'CUCI_SOFTENER'
WHERE node_kind = 'STAGE' AND node_key = 'FINISHING_QC' AND NOT EXISTS (…);

-- Jaring pengaman: tolak kalau masih ada sisa
DO $$
DECLARE leftover INT;
BEGIN
    SELECT count(*) INTO leftover FROM sampling_orders WHERE pipeline_stage = 'FINISHING_QC';
    IF leftover > 0 THEN
        RAISE EXCEPTION 'Masih ada % baris sampling_orders bertahap FINISHING_QC', leftover;
    END IF;
END $$;
```

**Mengapa blok ini ditulis begini?**

- **Mendarat di tahap PALING AWAL, bukan yang paling mirip namanya.** Ini keputusan yang layak
  dipikirkan lima menit. Database ini **tidak pernah menyimpan** apakah sampel itu sudah dicuci
  dan disetrika — informasinya memang tidak ada. Memetakannya ke `QC_FINISHING` berarti mengarang
  dua pekerjaan yang sudah beres tanpa dasar apa pun.
  Aturan umumnya: **kalau harus menebak, tebak ke arah yang bisa dikoreksi manusia dalam sepuluh
  detik.** Orang yang melihat sampelnya sudah disetrika akan langsung menggeser kartunya maju.
  Sebaliknya, kemajuan yang diarang membuat orang **berhenti mencari** barang yang sebenarnya
  masih di bak cuci.
- **`tenant_flow_node_locations` ikut dipindah.** Ini yang paling mudah terlewat: tabel itu
  memetakan tahap ke gedung, dan `AdvanceSamplingStageUseCase` memakainya untuk menentukan apakah
  perpindahan butuh surat jalan. Kalau pemetaan tertinggal di nama lama, gerbang serah terima
  macet total — dan gejalanya muncul sebagai "tombol naik tahap tidak berfungsi", bukan sebagai
  error migrasi.
- **`stage_history` (JSONB) SENGAJA tidak ditulis ulang.** Riwayat mencatat apa yang **dulu
  benar-benar tercatat**. Menulis ulang arsip agar cocok dengan kosakata hari ini adalah
  memalsukan jejak audit. Kalau suatu hari ada sengketa dengan buyer, yang dibaca adalah riwayat
  ini — dan riwayat yang pernah dirapikan tidak lagi bisa dipercaya.
- **`DO $$ … RAISE EXCEPTION` di akhir.** Migrasi yang "berhasil" tapi menyisakan baris adalah
  skenario terburuk: Flyway mencatat v63 sukses, dan sisanya baru ketahuan berminggu-minggu
  kemudian. Lebih baik migrasinya gagal keras sekarang, selagi kamu masih menatap terminalnya.

### Blok D: Transisi Entity — Tiga Method yang Diam-diam Memindahkan Tahap

```kotlin
// core/.../domain/sampling/SamplingOrder.kt
fun completeQcInspection(report: QcInspectionReport, updatedAt: Instant): SamplingOrder {
    val newStage = if (report.kind == QcInspectionKind.FINISHING &&
        report.qcResult == QcInspectionResult.PASSED &&
        pipelineStage == SamplingPipelineStage.QC_FINISHING
    ) {
        SamplingPipelineStage.PENGEMASAN   // dulu: IN_DELIVERY
    } else pipelineStage
    …
}
```

**Mengapa blok ini ditulis begini?**

- **QC lolos ≠ siap kirim.** Sejak pengemasan jadi tahapnya sendiri, melompat langsung ke
  `IN_DELIVERY` berarti menyatakan sampel sudah dilipat, di-hangtag, dan masuk polybag — padahal
  belum ada yang mengerjakannya. Jejak kustodinya jadi bolong **tepat di langkah terakhir**, yaitu
  langkah yang paling sering jadi bahan sengketa ("katanya sudah dikirim, barangnya mana?").
- **Ada tiga method serupa** (`recordVendorReturn`, `addFinishingDeposit`, `completeQcInspection`)
  yang memindahkan `pipelineStage` **tanpa melewati** `AdvanceSamplingStageUseCase` — artinya
  tanpa gerbang serah terima. KDoc di use case itu sudah menandainya sebagai utang. Saat memecah
  tahap, ketiganya wajib dibidik bersamaan; memperbaiki enum tapi membiarkan tiga pintu belakang
  ini berarti setengah SPK akan melompati tahap baru tanpa jejak.
- **`addFinishingDeposit` diubah dari `== LINKING_ASSEMBLY` jadi rentang.** Setoran finishing kini
  bisa dicatat dari tahap mana pun sepanjang lantai penyelesaian akhir. Syarat `==` yang lama akan
  membuat operator di tahap `CUCI_SOFTENER` mencatat setoran dan… tidak terjadi apa-apa.

### Blok E: UI — Lima Tahap, Satu Kolom

```kotlin
// app/shared/.../presentation/sampling/components/SamplingPipelineKanbanBoard.kt
FINISHING_QC(
    title = "5. Penyelesaian Akhir",
    subtitle = "Linking, cuci, setrika, QC & kemas",
    stages = listOf(
        SamplingPipelineStage.LINKING_ASSEMBLY,
        SamplingPipelineStage.CUCI_SOFTENER,
        SamplingPipelineStage.SETRIKA_UAP,
        SamplingPipelineStage.QC_FINISHING,
        SamplingPipelineStage.PENGEMASAN
    ),
    dropStage = SamplingPipelineStage.LINKING_ASSEMBLY,
    showActions = false
),
```

**Mengapa blok ini ditulis begini?**

- **Memecah domain TIDAK berarti memecah kolom.** Sembilan kolom × ~210dp = di layar 1280dp hanya
  empat yang terlihat, dan kolom "SPK Masuk" — yang paling sering dilihat sales — tergeser keluar
  layar setiap kali. Struktur data dan tata letak adalah dua keputusan terpisah; menyamakannya
  otomatis adalah refleks yang harus dilawan.
- **Pola `stages: List` + `dropStage` sudah ada di kode ini sebelumnya** (kolom "Tunggu ACC" sudah
  memuat dua tahap). Selalu cari pola yang sudah dipakai sebelum mengarang mekanisme baru.
- **Keempat tahap baru berbagi satu warna (`Teal`).** Pembedanya adalah teks badge di kartu.
  Memberi empat rona teal yang berbeda-beda memaksa orang menghafal palet — dan operator yang buta
  warna tidak akan pernah bisa. Warna membedakan **kolom**, teks membedakan **tahap**.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan Kita | Alternatif | Kenapa Kita Pilih Ini | Risiko Alternatif |
|---|---|---|---|
| **Hapus nilai enum lama** | Tandai `@Deprecated`, biarkan hidup | Kompilator jadi checklist; tidak ada kosakata ganda | Kolom hantu yang tak ada yang berani hapus; setengah tim tetap memakai yang lama |
| **Alias legacy di parser** | Andalkan migrasi 100% bersih | Satu baris yang lolos tidak berubah jadi `NEW_INTAKE` senyap | Bug tak terdeteksi yang baru ketahuan setelah tim mengerjakan ulang SPK yang sudah jadi |
| **Petakan ke sub-tahap pertama** | Petakan ke `QC_FINISHING` (nama mirip) | Tidak mengarang kemajuan yang tidak pernah tercatat | Orang berhenti mencari barang yang masih di bak cuci |
| **Enum berurutan (`WorkLineTemplate` nanti)** | DAG penuh per tenant sekarang | Nol tenant membutuhkannya hari ini; penggantinya cuma satu fungsi resolver | Tabel, CRUD, UI penyusun, validasi siklus — semua untuk kebutuhan yang belum ada |
| **Empat tahap sebagai BAWAAN** | Empat tahap wajib untuk semua tenant | CLAUDE.md §11: preset = starter template. Penyesuaian lewat `TenantOptionalProcess` yang sudah jalan | Pabrik kecil mencatat serah terima yang tidak pernah terjadi → data diisi asal → seluruh papan bohong |
| **Lima tahap dalam satu kolom Kanban** | Satu kolom per tahap | Enam kolom muat di 1280dp | Papan sembilan kolom; kolom terpenting tergeser keluar layar |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

### Jebakan 1: Mengira `runCatching { valueOf() }.getOrNull() ?: DEFAULT` itu "aman"

- *Kenapa bahaya*: Pola ini terlihat defensif, padahal ia **mengubah kegagalan jadi kebohongan**.
  Tidak ada error, tidak ada log — hanya nilai default yang salah, dan kesalahannya baru terasa
  berhari-hari kemudian dalam bentuk yang tidak berhubungan.
- *Solusi elegan kita*: `parseOrNull` terpusat + alias legacy + `RAISE EXCEPTION` di migrasi.
  Tiga lapis, karena kegagalan senyap layak dijaga tiga kali.

### Jebakan 2: Mengubah UI lebih dulu karena "itu yang kelihatan"

- *Kenapa bahaya*: Kamu akan menghabiskan berjam-jam menebak kenapa kartu tidak pindah kolom,
  padahal penyebabnya ada di entity `core/` yang bahkan belum kamu buka.
- *Solusi elegan kita*: domain → codec/repo → migrasi → test → UI. Urutan ini bukan formalitas
  DDD; ia mengikuti **arah aliran data**, dan debugging selalu lebih murah dari hulu.

### Jebakan 3: Menulis ulang riwayat (`stage_history`) supaya "konsisten"

- *Kenapa bahaya*: Jejak audit yang pernah dirapikan tidak lagi bisa dipakai membuktikan apa pun.
  Dan ironisnya, kamu baru butuh membuktikan sesuatu justru saat ada sengketa.
- *Solusi elegan kita*: arsip dibiarkan apa adanya; hanya **keadaan saat ini** yang dimigrasi.

### Jebakan 4: Lupa tabel pendukung (`tenant_flow_node_locations`, `tenant_optional_processes`)

- *Kenapa bahaya*: Kolom utama sudah benar, jadi migrasi "terlihat" sukses. Gejalanya muncul
  belakangan sebagai fitur yang macet, bukan sebagai error migrasi — jarak antara sebab dan akibat
  inilah yang membuatnya mahal.
- *Solusi elegan kita*: `grep` nama enumnya di seluruh `*.sql` **sebelum** menulis migrasi, bukan
  sesudah.

### Jebakan 5: Menyamakan "memecah domain" dengan "memecah kolom UI"

- *Kenapa bahaya*: Struktur data yang benar bisa menghasilkan tata letak yang tidak bisa dipakai.
- *Solusi elegan kita*: kelompokkan di lapisan UI (`stages: List<…>`), bedakan lewat badge.

### Jebakan 6: Menambah baris ke berkas yang sudah melewati batas ukuran

- *Kenapa bahaya*: `DealDetailDialog.kt` ada di tabel utang teknis
  ([file-size-rules §6](../../.claude/rules/file-size-rules.md)). Aturan Ratchet: berkas itu boleh
  disentuh, tapi **tidak boleh bertambah panjang**.
- *Solusi elegan kita*: aturan yang berulang diangkat jadi properti domain (`isWetOrPressWork`),
  sehingga berkas UI justru jadi lebih pendek, bukan lebih panjang. `wc -l` sebelum dan sesudah:
  2679 → 2679.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### Strategi berlapis

| Lapis | Menguji apa | Kenapa tidak bisa digantikan lapis lain |
|---|---|---|
| Unit test domain | Alias legacy, urutan `order`, rentang lantai finishing | Murni, cepat, jalan di 5 target KMP |
| Test entity | Transisi `completeQcInspection` → `PENGEMASAN` | Aturan bisnis, bukan aturan data |
| Migrasi di Postgres nyata | `UPDATE` benar-benar kena, tabel pendukung ikut | Unit test tidak tahu apa-apa soal isi DB |
| `curl` ke server hidup | Route menerima nama tahap baru **dan** nama lama | Codec + route + repo baru bertemu di sini |
| **Lihat dengan mata** | Densitas kolom, teks tidak pecah | Tidak ada test yang bisa menangkap ini |

### Test yang paling berharga

```kotlin
// core/src/commonTest/.../SamplingStageLegacyAliasTest.kt
@Test
fun `parse nama tahap lama harus mendarat di sub-tahap pertama bukan fallback`() {
    assertEquals(SamplingPipelineStage.CUCI_SOFTENER, SamplingPipelineStage.parseOrNull("FINISHING_QC"))
}

@Test
fun `urutan tahap tidak boleh bolong atau bertukar`() {
    // `order` dipakai membandingkan kemajuan (`pipelineStage.order >= …`) di lima berkas UI.
    // Satu angka terlewat saat menyisipkan tahap = perbandingan itu diam-diam salah.
    assertEquals(
        SamplingPipelineStage.entries.indices.map { it + 1 },
        SamplingPipelineStage.entries.map { it.order }
    )
}
```

Perhatikan test kedua. Ia tidak menguji "fitur" apa pun — ia menjaga **invarian yang dipakai
diam-diam di tempat lain**. Saat seseorang menyisipkan tahap baru enam bulan lagi dan lupa
menggeser nomornya, test inilah yang menangkapnya, bukan QA.

### Perintah verifikasi

```bash
./gradlew :core:jvmTest :server:compileKotlin
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:jvmTest

# Migrasi di DB sungguhan — angka totalnya wajib sama sebelum & sesudah
psql … -c "select pipeline_stage, count(*) from sampling_orders group by 1 order by 1;"

# Alias legacy lewat HTTP: kirim nama tahap LAMA, harus diterima (bukan 400)
curl -X POST localhost:8080/api/tenant/sampling/orders/<id>/stage \
  -H "Authorization: Bearer <JWT>" -d '{"targetStage":"FINISHING_QC"}'
```

Hasil nyata saat dikerjakan: **755 test hijau**, 3 baris `FINISHING_QC` → 3 baris `CUCI_SOFTENER`
dengan total baris tetap 15, dan tiga layar (papan sampling, QC, lantai produksi) terverifikasi
dengan mata di 1280dp.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1 — Cari pintu belakang berikutnya.** Buka
      `AdvanceSamplingStageUseCase` dan baca KDoc-nya. Ia menyebut empat method yang memindahkan
      `pipelineStage` tanpa gerbang kustodi. Kita membidik tiga. **Yang keempat mana, dan kenapa
      dibiarkan?** Tulis argumenmu, lalu bandingkan dengan `assignMakloonVendor`.
- [ ] **Tantangan 2 — Sisipkan tahap tanpa menulis kode.** Pakai `tenant_optional_processes`
      dengan `sampling_anchor_after = 'SETRIKA_UAP'` untuk menyisipkan proses "Gantung & Angin-Angin"
      hanya untuk satu tenant. Buktikan tenant lain tidak terpengaruh. (Petunjuk: `FlowLegDerivation.resolveNodes`)
- [ ] **Tantangan 3 — Rancang mekanisme yang BELUM ada.** Pabrik dengan ruang sampel dua orang
      ingin **menggabungkan** Cuci dan Setrika jadi satu tahap. Menyisipkan sudah bisa;
      menyembunyikan belum. Rancang kontraknya: di mana konfigurasinya disimpan, apa yang terjadi
      pada SPK yang **sedang berada** di tahap yang baru saja disembunyikan, dan apa yang terjadi
      pada `stage_history` yang menyebut tahap itu? Pertanyaan ketiga adalah yang paling sulit —
      dan paling penting.
