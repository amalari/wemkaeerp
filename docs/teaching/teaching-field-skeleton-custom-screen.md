# 🎓 Modul Pembelajaran: Kerangka Layar Kustom yang Dinyatakan Agent (Irisan 3b / C10)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Kontrak dulu baru paralel (gerbang A0), validator "kumpulkan, jangan lempar", kosakata tertutup, codec yang menolak (bukan fallback senyap), satu sumber batas untuk validator/prompt/katalog, fungsi murni untuk tata letak Compose
> **Prasyarat**: Tahu `sealed interface` Kotlin, enum, dan secara garis besar apa itu "usulan layar" (`ScreenProposal`) di wizard discovery
> **Referensi Task**: `docs/plannings/PLAN-field-component-gaps.md` — C10, Irisan 3b, keputusan D5 dan D6

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Agent discovery bisa mengusulkan layar bertipe `CUSTOM_SCREEN` ketika tidak ada jenis layar baku yang cocok (kanban, tabel, form, …). Tetapi layar itu dulu selalu digambar sebagai **tiga kotak generik** ("Ringkasan {modul}", "Daftar {modul}", "Panel aksi"). Agent tidak punya tempat untuk berkata "layar ini berisi *Keranjang* dan *Pembayaran*". Calon pelanggan melihat pratinjau yang sama untuk toko online maupun klinik.

**Analogi.** Bayangkan arsitek yang hanya boleh menyerahkan denah berisi tiga kotak bernama "Ruang A, B, C". Kita menambah satu hal: ia boleh menamai kotaknya ("Dapur", "Kamar Anak"), memilih lebarnya (penuh/separuh), dan memberi petunjuk bentuknya (tabel/formulir/kartu angka/aksi). Tapi tetap **maket**, bukan rumah yang bisa dihuni.

**Hasil akhir.** `CUSTOM_SCREEN` boleh membawa `view.blocks`; validator, codec, katalog/prompt agent, server, dan renderer semuanya mengenalnya. Draf lama tanpa `view` tetap terbaca dan tampil persis seperti sebelumnya.

Dua keputusan yang membatasi desain:

- **D5 — petunjuk tertutup.** `hint` bukan teks bebas, melainkan `TABLE | FORM | METRIC_CARDS | ACTIONS`. Teks bebas mengundang "UI bebas" yang sengaja dihindari.
- **D6 — non-interaktif.** Kerangka tidak punya entitas, field, atau aksi. Bila ternyata bagus, ia jadi blok sungguhan lewat jalur biasa.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Pekerjaan ini dibagi tiga *track* yang jalan paralel (A = `core`, B = `server`, C = `app/shared`). Paralel hanya aman bila **bentuk data disepakati lebih dulu**.

1. **Langkah 0 — Gerbang A0 (kontrak, kecil, merge duluan).**
   `ViewSkeleton.kt` (`SkeletonBlock`, `SkeletonWidth`, `SkeletonHint`) dan varian `ViewProposal.Skeleton(blocks)`. Tanpa ini, B dan C tidak punya tipe untuk dirujuk, dan karena `when` atas sealed dilarang memakai `else`, kompilator langsung memaksa semua titik disentuh. A0 sengaja **hanya bentuk**: cabang yang dipaksa kompilator diisi seminimal mungkin dan *fail-closed* (validator menolak `Skeleton`, `encode` melempar error).
2. **Langkah 1 — Track A sisa (`core`)**: batas (`ProposalLimits.BLOCKS`), aturan (`ProposalSkeletonRules`), kemurnian vertikal, codec, sampel (`WidgetRegistry.sampleRowsFor`).
3. **Langkah 2 — Track B (`server`) dan Track C (`app/shared`) paralel**, hanya bergantung pada kontrak A.
4. **Langkah 3 — Integrasi**: merge berurutan, kompilasi target, tes, audit, cek visual.

> **Mental model:** *kontrak dulu, isi belakangan.* Yang dibekukan lebih awal adalah bentuk (tipe dan kunci JSON), bukan perilaku.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Kosakata tertutup (A0)

```kotlin
data class SkeletonBlock(
    val label: String,
    val width: SkeletonWidth = SkeletonWidth.FULL,
    val hint: SkeletonHint = SkeletonHint.TABLE
)
enum class SkeletonWidth { FULL, HALF }
enum class SkeletonHint { TABLE, FORM, METRIC_CARDS, ACTIONS }
```

**Mengapa begini?**
- Lolos **Uji Variabilitas** (`tenant-variability-rules` Kontrak 1): ini kosakata sistem untuk *menggambar sketsa*, tidak berbeda per tenant atau industri. Kosakata domain ada di `label`, yang bebas dari model.
- Enum, bukan string: nilai yang salah tidak bisa lahir di domain.

### Blok B: Validator yang mengumpulkan

```kotlin
internal object ProposalSkeletonRules {
    fun check(view: ViewProposal.Skeleton, sink: IssueSink) {
        if (view.blocks.isEmpty()) sink.add(".view.blocks", "Kerangka wajib punya minimal 1 blok")
        if (view.blocks.size > ProposalLimits.BLOCKS) { /* ... */ }
        view.blocks.forEachIndexed { i, b -> sink.text(".view.blocks[$i].label", b.label, "Label blok") }
        // label kembar (tanpa beda huruf besar/kecil & spasi tepi) ditolak
    }
}
```

**Mengapa begini?**
- Validator memakai `sink.add` (kumpulkan semua masalah dengan path), bukan `throw` di masalah pertama — model LLM bisa diberi tahu *semua* kekeliruan sekaligus.
- Kecocokan "Skeleton hanya untuk `CUSTOM_SCREEN`" **tidak** diulang di sini; sudah dijaga `ProposalViewRules.matches`. Satu aturan, satu tempat.

### Blok C: Kemurnian vertikal — `else -> Unit` yang diam-diam melewatkan

```kotlin
when (val v = p.view) {
    is ViewProposal.Form -> at(".view.submitLabel", v.submitLabel)
    // ...
    is ViewProposal.Skeleton -> v.blocks.forEachIndexed { i, b -> at(".view.blocks[$i].label", b.label) }
    is ViewProposal.Table, is ViewProposal.Checklist, is ViewProposal.Print, ViewProposal.None -> Unit
}
```

**Mengapa begini?**
- Sebelumnya `else -> Unit`. Label blok adalah **teks bebas dari model**; tanpa cabang ini label yang membawa istilah vertikal lain (mis. "SPK" di pack klinik) lolos tanpa diperiksa.
- Mengganti `else` dengan daftar eksplisit berarti varian baru berikutnya memaksa kompilator bertanya "perlu diperiksa atau tidak?". Ini pola yang sama dengan Kontrak 6 `field-component-rules`.

### Blok D: Codec yang menolak, bukan menebak

```kotlin
hint = b.optString("hint")?.let { raw ->
    SkeletonHint.entries.firstOrNull { it.name == raw }
        ?: b.fail("hint", "Petunjuk '$raw' bukan kosakata tertutup: ...")
} ?: SkeletonHint.TABLE
```

```kotlin
// Tanpa `view` / null = ViewProposal.None (draf lama)
val r = parent.optObject("view") ?: return ViewProposal.None
```

**Mengapa begini?**
- Kunci `hint` **hilang** → default `TABLE` (wajar). Kunci ada tapi **nilainya tak dikenal** → ditolak lengkap dengan path. Beda antara "tidak diisi" dan "salah isi" inilah inti Kontrak 4 variability: *fallback senyap = data berubah.*
- `view` yang tidak ada tetap `None`, jadi draf yang sudah tersimpan tidak rusak (kompatibilitas mundur).

### Blok E: Satu sumber batas untuk validator, prompt, dan katalog

```kotlin
internal object KoogDiscoverySkeletonVocabulary {
    private val hints get() = SkeletonHint.entries.joinToString("|") { it.name }
    val promptRule get() = "... 1–${ProposalLimits.BLOCKS} blok, label unik maksimal ${ProposalLimits.TEXT} karakter ..."
    fun catalogJson() = jsonObjectOf("maxBlocks" to jsonOf(ProposalLimits.BLOCKS), /* ... */ "interactive" to jsonOf(false))
}
```

**Mengapa begini?**
- Kalau angka 8 ditulis di prompt *dan* di validator, suatu hari salah satunya berubah dan model terus diajari batas lama. Membaca dari `ProposalLimits` dan `entries` membuat pergeseran mustahil. Ada tes paritas yang membandingkannya.
- Ini **bukan** tipe field, jadi tidak masuk kosakata `FieldType`; `field-component-rules` hanya berlaku sebagian (katalog agent dan penolakan nilai tak dikenal).

### Blok F: Server — dua jalur, satu bentuk

```kotlin
"sampleRows" to jsonArrayOf(
    ((proposal.view as? ViewProposal.Skeleton)?.blocks?.map { it.toSampleRow() } ?: proposal.seed)
        .map { jsonStringMapOf(it) }
)
```

**Mengapa begini?**
- Jalur layar ber-proposal sebelumnya mengirim `sampleRows = proposal.seed`, yang **kosong** untuk `CUSTOM_SCREEN`. Kini bila `view` adalah `Skeleton`, barisnya diambil dari blok, dengan kunci yang sama (`Blok`, `Lebar`, `Petunjuk`) seperti jalur lama. Klien cukup memahami **satu** bentuk.

### Blok G: Renderer — keputusan layout sebagai fungsi murni

```kotlin
fun planSketchRows(rows: List<Map<String, String>>): List<List<SketchBlock>> { /* pasangkan separuh+separuh */ }
fun sketchShapeOf(hint: SkeletonHint?): SketchShape = when (hint) { null -> NEUTRAL; /* ... tanpa else */ }
```

**Mengapa begini?**
- Memasangkan blok separuh, memilih rupa, dan memetakan teks → petunjuk adalah *logika*; ditaruh di fungsi murni agar bisa diuji tanpa Compose. Composable `SkeletonSketch` hanya menggambar hasilnya.
- Petunjuk kosong/tak dikenal → rupa `NEUTRAL` (kotak berlabel lama) dan tidak crash, jadi draf lama tampil seperti dulu.
- Batang abu-abu yang dipakai di tiga rupa diangkat ke `designsystem/ClaySketchBar` (**Aturan Tiga Kali**), buta domain. `PrototypeRenderer.kt` justru turun 364 → 321 baris (Ratchet).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa dipilih | Risiko alternatif |
|---|---|---|---|
| Kerangka = varian baru `ViewProposal.Skeleton`, `None` tetap sah | Mengubah `None` menjadi punya isi | Draf lama tersimpan tetap sah, tidak ada migrasi | Dokumen lama mendadak invalid |
| `hint` enum tertutup (D5) | Teks bebas | Sketsa tetap sketsa; blok bisa dipetakan ke blok sungguhan nanti | Model "mendesain UI" bebas yang tak bisa dirender |
| Non-interaktif (D6) | Blok yang bisa diklik | Pratinjau untuk menilai prospek, bukan aplikasi | Dua jalur membangun layar, saling bentrok |
| Batas dibaca dari `ProposalLimits` | Salin angka ke prompt | Tidak ada pergeseran validator vs prompt | Model diajari batas basi |
| Kontrak (A0) dulu, lalu paralel | Satu agent berurutan | Tiga track jalan bersamaan tanpa saling sunting | Konflik kompilasi `when` tanpa `else` |

---

## ⚠️ 5. Jebakan Pemula (dan yang benar-benar terjadi di sini)

1. **`else -> Unit` yang melewatkan jenis baru.** Pemeriksaan kemurnian label tidak menyentuh `Skeleton` sampai `else` diganti daftar eksplisit.
2. **Menyalin angka batas ke prompt.** Gunakan sumber tunggal; tulis tes paritas.
3. **Fallback senyap pada nilai tak dikenal.** Bedakan "kunci hilang" (default) dari "nilai salah" (tolak).
4. **Worktree agent bercabang dari komit lama.** Pada Irisan 3b, worktree Track C awalnya bercabang dari A0, bukan `main` yang sudah memuat Track A. Kompilasinya hijau karena Track C hanya memakai kunci string, tapi ia **belum pernah dites bersama Track A**. Selalu periksa `git merge-base` cabang agent sebelum menerima hasilnya, dan uji ulang setelah merge.
5. **Menganggap "hijau di cabang" = "hijau terintegrasi".** Gerbang integrator (kompilasi semua target, tes, audit) tetap wajib.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

- **Domain (tes murni, `core/commonTest`)** — `ProposalSkeletonTest` (13 tes, fixture klinik): kerangka sah lolos; draf lama `None` lolos; `Skeleton` pada widget TABLE ditolak; batas blok (kosong/lebih/tepat batas); label kosong, terlalu panjang, kembar; label berisi "SPK" ditolak bila kemurnian vertikal aktif; round-trip codec; `hint`/`width` tak dikenal ditolak dengan path; sampel dari blok vs tiga blok generik.
- **Server** — `KoogDiscoverySkeletonTest` (5 kasus, pack non-garment): usulan sah lolos validator; paritas angka/daftar katalog & prompt terhadap `ProposalLimits` dan enum; `summaryObj` mengirim baris blok dan `proposal.view.blocks`.
- **Klien** — `SkeletonSketchPlanTest` (5 tes) untuk pemeta dan pemasangan separuh.

### Yang BELUM terverifikasi (jangan dianggap selesai)

- **Cek visual belum dilakukan.** Tidak ada satu agent pun yang menjalankan aplikasi, dan integrator tidak bisa menjalankan server karena kata sandi DB scratch tidak tersedia. Yang perlu dilihat manusia: layar `CUSTOM_SCREEN` ber-blok dan satu draf lama tiga-blok, di tenant non-garment, lebar ~1280dp dan ~360dp — blok separuh tidak pecah per huruf, sketsa formulir/kartu angka tidak terpotong, separuh ganjil tetap setengah lebar.
- **Android tidak dikompilasi** (SDK tidak terpasang di mesin ini). JVM, Wasm, dan JS hijau.
- **Eval agent dengan LLM sungguhan belum ada** — belum diketahui apakah model memakai `view.blocks` dengan baik atau tetap memilih tiga blok generik.
- **Tes server lama:** `:server:test` penuh punya kegagalan yang sudah ada di `main` sebelum pekerjaan ini (`TechPackApiTest`, `CostingEstimatorTuningApiTest`, `AccessSnapshotB6Test`, `MasterDataApiTest`). `DiscoveryInterviewApiTest` kadang timeout 60 detik (lambat; jumlah dan nama tes yang gagal berubah antar-run), dan `DiscoveryApiTest` sempat gagal setelahnya tetapi lulus 10/10 bila dijalankan sendiri — gejala bergantung urutan, bukan regresi yang terbukti dari perubahan ini.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Tambah `SkeletonHint.CHART`. Daftar semua titik yang dipaksa kompilator. Mana yang *tidak* dipaksa kompilator tetapi tetap harus disentuh (petunjuk: baca teks katalog/prompt dan tesnya)?
- [ ] **Tantangan 2**: Buat tes yang memastikan setiap `SkeletonHint` punya rupa selain `NEUTRAL` di renderer. Mengapa tes ini sulit ditulis tanpa fungsi murni `sketchShapeOf`?
- [ ] **Tantangan 3**: Rancang eval deterministik (tanpa LLM berbayar) yang menolak usulan di mana label blok menyebut istilah dari vertikal lain.
