# 🎓 Modul Pembelajaran: Kontrak `ScreenProposal` — Satu Bahasa untuk Semua Pembuat Layar (SP-B0 s/d B5)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Kontrak antar-pembuat (anti-corruption boundary), validator tunggal berpath, Strangler Fig + test paritas, data pack vs kode (Uji Variabilitas), fail-closed vs tebakan senyap, operasi spec tertutup (`SpecOp`)
> **Prasyarat**: Kotlin (`sealed interface`, `data class`, `Result`), paham `InteractiveScreen`/`PrototypeSpec` secara garis besar, paham konsep "Domain Pack" di repo ini
> **Referensi Task**: `docs/plannings/parallel3/PLAN-sp-B-contract.md` (induk: `PLAN-screen-proposal-contract-koog.md`) — butir B0–B5

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Keputusan "modul ini tampil sebagai papan, tabel, atau formulir — dengan field, status, dan kartu begini" punya dua keadaan yang tidak setara:

- **Garment**: ditulis tangan di pack (`GarmentScreenSuggestions`) — kaya, tapi tidak menurun ke bisnis lain.
- **Bisnis baru dari narasi klien**: agent hanya mengeluarkan *kode jenis tampilan* (`"KANBAN"`). Isi layarnya tidak ada, jadi prototype menampilkan "contoh 1".

Kalau kita langsung membangun agent AI (Koog) di atas kondisi ini, kita mendapat dua kerusakan sekaligus: LLM mengarang bentuk data sesukanya, dan tidak ada yang bisa menolaknya dengan alasan yang bisa dikoreksi.

**Analogi.** Bayangkan tiga juru gambar (arsitek manusia, juru gambar otomatis, dan juru gambar AI) mengirim denah ke satu kantor perizinan. Kalau tiap juru gambar punya format sendiri, kantor itu butuh tiga loket. Solusi kita: **satu formulir baku** (`ScreenProposal`) dan **satu petugas pemeriksa** (`ScreenProposalValidator`). Siapa pun yang mengirim — manusia, mesin, atau LLM — diperiksa dengan daftar periksa yang sama, dan penolakannya selalu menyebut *di baris mana* salahnya.

**Hasil akhir.**
```
Pack garment (manusia) ─┐
Agent deterministik     ─┼─►  ScreenProposal ─► ScreenProposalValidator ─► toInteractiveScreen() ─► prototype
Agent Koog (LLM, jalur C)┘      (ringkas)        (satu aturan, galat berpath)   (kode, bukan LLM)
```

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Urutan ini sengaja *kontrak dulu, produsen kemudian*:

1. **B0 — Kontrak & validator (`core/domain/discovery/proposal/`)**. Mulai dari **batas dan kosakata tertutup**, bukan dari fitur. Kenapa dulu? Dua agent lain (A: UI, C: Koog) menunggu bentuknya; perubahan kontrak belakangan = kerja ulang tiga jalur. Karena itu B0 dibuat pendek dan diuji dengan fixture non-garment sejak hari pertama.
2. **B1 — Acuan emas dari data yang sudah ada.** Ekspresikan pack garment sebagai proposal, lalu buktikan *setara* dengan jalur lama lewat test paritas. Kontrak yang belum pernah memeluk data nyata belum terbukti.
3. **B2 — Pemetaan peran → tampilan sebagai data pack** (`SlotDefinition.defaultWidget/defaultStatuses`). Ini data, bukan `when`, karena beda per industri.
4. **B3 — Pembuat deterministik.** Baru sekarang ada yang *menghasilkan* proposal untuk bisnis tanpa pack tulisan tangan.
5. **B4 — Memperketat validator.** Setelah ada pembuat sungguhan, barulah kelihatan celah (kemurnian vertikal, lintas-layar). Memperketat sebelum ada pembuat = menebak.
6. **B5 — Operasi `ChangeWidget`.** Fitur turunan yang aman karena fondasi di atas sudah ada.

Aturan DDD yang dipegang: semuanya `core/` murni — tanpa Ktor, tanpa Compose, tanpa DB.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A — Kontrak yang tidak melempar di konstruktor
```kotlin
data class ScreenProposal(
    val screenId: String, val moduleId: ModuleId, val title: String,
    val widget: WidgetKind, val rationale: String,
    val entity: EntityProposal?, val view: ViewProposal,
    val seed: List<Map<String, String>> = emptyList(),
    val binding: DataBinding = DataBinding.Memory
)
```
**Mengapa begini?**
- Biasanya kita validasi di `init { require(...) }` (lihat `FieldHint`). Di sini **tidak**: pelanggaran dikumpulkan validator sebagai daftar `ProposalIssue(path, message)`. Alasan: dokumen dari LLM biasanya salah di *lebih dari satu tempat*; satu `require` hanya membocorkan galat pertama, sehingga putaran koreksi jadi tiga kali lebih mahal.
- Bentuknya **ringkas** (bukan `InteractiveScreen` penuh): murah token, dan konversi ke layar interaktif dikerjakan *kode*, bukan model.
- `ViewProposal` adalah `sealed interface` dengan varian per widget. Di JSON tidak ada kunci jenis: `widget` induknya yang menentukan varian, jadi dokumen tidak bisa menulis `view` yang tak cocok dengan `widget`.

### Blok B — Validator: kumpulkan, jangan lempar
```kotlin
internal class IssueSink(private val root: String, private val packKeys: Boolean = false) {
    val issues = mutableListOf<ProposalIssue>()
    fun add(sub: String, message: String) { issues += ProposalIssue("$root$sub", message) }
}
```
**Mengapa?**
- Path (`$.entity.fields[2].key`) dirakit dari `root`, sehingga validator yang sama dipakai untuk usulan berdiri sendiri (`$`) maupun di dalam draf (`$.screens[3].proposal`).
- Pesan ditulis untuk dibaca **LLM dan manusia**: menyebut apa yang salah *dan* apa yang diharapkan ("Status asal 'Hantu' bukan pilihan 'status' (Menunggu, Diperiksa, Selesai)"). Pesan ini akan dikembalikan ke model pada putaran koreksi.
- Aturan dipecah per tanggung jawab (`ProposalEntityRules`, `ProposalViewRules`, `ProposalPurityRules`, `CrossScreenRules`) — bukan `…Part2.kt` — supaya tiap file < 250 baris (batas core).

### Blok C — Konversi yang tidak pernah melempar mentah
```kotlin
fun ScreenProposal.toInteractiveScreen(source: ProposalSource? = null): Result<InteractiveScreen> {
    val issues = ScreenProposalValidator.validate(this, source = source)
    if (issues.isNotEmpty()) return Result.failure(ProposalConversionException(issues))
    ...
    return runCatching { ... PrototypeSpec(...) }.recoverCatching { ... ProposalConversionException(...) }
}
```
**Mengapa?** Validator adalah lapisan pertama; konstruktor `PrototypeSpec` yang fail-closed adalah lapisan kedua. Keduanya dipertahankan. `PRINT` dan `CUSTOM_SCREEN` sah sebagai usulan tetapi *tidak punya bentuk interaktif* (sama seperti jalur lama), jadi konversinya gagal bermesej — pemanggil menggambar statis, tidak menebak.

### Blok D — Test paritas (Strangler Fig)
```kotlin
GarmentScreenSuggestions.all.forEach { s ->
    assertTrue(all.any { it.moduleId == s.moduleId && it.widget == s.widget }, "Suggestion ${s.moduleId.value} tanpa proposal")
}
// dan: p.toInteractiveScreen(Pack) == WidgetRegistry.interactiveFor(...) untuk setiap proposal
```
**Mengapa?** Test *mengiterasi data lama*. Entri suggestion baru tanpa padanan proposal → test merah. Ini pola `tenant-variability-rules` Kontrak 8: struktur baru dibangun di samping yang lama dan dijaga setara oleh test, sebelum ada pembaca yang dipindahkan.

### Blok E — Pemetaan peran → tampilan sebagai data, dan "tanpa tebakan"
```kotlin
data class SlotDefinition(
    ..., val defaultWidget: WidgetKind? = null, val defaultStatuses: List<String> = emptyList()
)
// DeterministicScreenProposer
if (slot == null || widget == null) emptyList() else listOf(build(pack, module, slot, widget))
```
**Mengapa?** `null` berarti *pack tidak berpendapat* — pembuat deterministik tidak membuat layar, dan **tidak pernah meminjam** watak dari pack/slot lain (misalnya diam-diam jatuh ke garment). Papan tanpa status gagal bermesej, bukan menjadi tabel diam-diam. Prinsipnya sama dengan Kontrak 4: fallback senyap = data berubah tanpa ada yang tahu.

### Blok F — Kemurnian vertikal: tolak, jangan saring
```kotlin
private val PATTERN = Regex("(?<![\\p{L}\\p{N}])(kain|jahit|spk|po|...)(?![\\p{L}\\p{N}])", IGNORE_CASE)
```
**Mengapa?** Pencocokan per **kata utuh** supaya "Poli" dan "polimer" tidak terkena "po". Keluaran yang bocor *ditolak*, tidak dihapus bagian bocornya — LLM yang menyalin contoh garment harus tahu agar bisa mengoreksi. Daftar ini hanya dipakai untuk menolak; ia tidak mengambil keputusan perilaku apa pun.

### Blok G — Konsistensi lintas-layar
Banyak layar boleh berbagi satu `entity.id` (tabel dan formulirnya), tetapi field yang sama harus identik (label, tipe, pilihan). `required` sengaja **bukan** identitas: formulir boleh mewajibkan lebih banyak daripada tabelnya. Aturan ini langsung menemukan **konflik nyata** di pack garment ("Kepemilikan" TEXT di tabel, ENUM di form) — diperbaiki di data pack, bukan dengan melonggarkan aturan.

### Blok H — `ChangeWidget`: operasi tertutup dengan kelayakan
```kotlin
data class ChangeWidget(val screenId: String, val widget: WidgetKind) : SpecOp
// ChangeWidgetOp: tabel selalu mungkin; papan butuh tepat satu field ENUM yang jelas,
// ≥ 1 field lain sebagai judul kartu, dan seed tanpa status ditolak (kartunya akan hilang).
```
**Mengapa?** `SpecOp` sealed = LLM tidak bisa menyelundupkan operasi bebas. Spec hasil selalu dibangun lewat `PrototypeSpec(...)`, jadi validasinya tidak dilonggarkan demi operasi. Logikanya di `ChangeWidgetOp.kt` terpisah agar `SpecOpApplier` tidak membengkak (197 baris).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa kita memilih ini | Risiko jika memakai alternatif |
|---|---|---|---|
| Validator mengumpulkan semua galat berpath | `require()` di konstruktor | Satu putaran koreksi LLM memperbaiki semua kesalahan; path memandu lokasi | Putaran koreksi berlipat; pesan tanpa lokasi |
| Satu validator untuk semua pembuat | Validator per pembuat | Aturan tak bisa berselisih antar pembuat | Pack manusia lolos, keluaran LLM berbeda aturan |
| Proposal ringkas → dikonversi kode | LLM langsung menulis `InteractiveScreen` | Murah token, bentuk dijaga kode | Biaya tinggi, model memegang detail internal |
| Pemetaan peran sebagai data pack | `when (kataKunci)` di mesin | Tiap industri bisa beda (Uji Variabilitas) | Kode mesin menyebut konsep satu industri |
| Tolak (reject) istilah bocor | Saring/ganti otomatis | Kesalahan terlihat dan dikoreksi | Data berubah diam-diam |
| Test paritas yang mengiterasi data lama | Beberapa contoh manual | Entri baru tanpa padanan gagal otomatis | Divergensi baru ketahuan saat produksi |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls)

1. **Validator yang bisa melempar.** Kami sendiri menemukannya: kanban dengan `statusField` salah *dan* seed membuat validator melempar `NoSuchElementException` saat menyusun pesan. Validator harus selalu mengembalikan daftar. *Solusi*: lewati pemeriksaan turunan bila akarnya sudah dilaporkan; uji dengan masukan yang sengaja rusak berlapis.
2. **Menyamakan "tidak ada pendapat" dengan "pakai bawaan".** Slot tanpa `defaultWidget` bukan berarti "pakai tabel". *Solusi*: `null` = tidak ada layar; test khusus `tanpa pendapat tidak meminjam dari garment`.
3. **Melonggarkan aturan agar data lama lolos.** Saat lintas-layar menolak garment, godaannya membuang aturan. *Solusi*: periksa dulu apakah datanya yang salah (di sini memang salah) — perbaiki datanya, jaga paritas.
4. **Regex kata pendek tanpa batas kata.** `po` mencocokkan "poli". *Solusi*: lookaround `(?<![\p{L}\p{N}])…(?![\p{L}\p{N}])` + test "Poli gigi, polimer lolos".
5. **Merge ke cabang yang salah.** Folder kerja utama sempat berpindah ke cabang agent lain, sehingga merge B1/B2 mendarat di sana, bukan di `main`. *Solusi*: sebelum merge, `git branch --show-current`; bila folder utama bukan milik kita, merge dari worktree sendiri.
6. **Seed yang membuat kartu "hilang".** Kartu kanban tanpa nilai status tidak muncul di kolom mana pun. *Solusi*: validator menolak, dan `ChangeWidget` ke papan menolak data seperti itu.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

- **Domain murni, tanpa DB/jaringan.** `:core:jvmTest` (1414 tes, 0 gagal saat B5 selesai).
- **Satu tes per aturan, menegaskan *path*** (bukan hanya "ada galat"): `assertIssueAt("$.entity.transitions.Hantu", issues, "Status asal")`.
- **Tenant kedua wajib**: fixture klinik, bengkel, katering, retail, gudang — dan uji kebocoran kosakata terhadap *seluruh JSON draf*.
- **Paritas**: `GarmentProposalParityTest` — proposal garment ≡ jalur lama, dan seluruh pack garment lolos validator termasuk aturan lintas-layar.
- **Kompatibilitas mundur**: draf lama (tanpa `proposal`) terbaca dan ter-encode *byte-per-byte sama*; pack JSON lama tanpa `defaultWidget`/`rationale` terbaca.
- **Determinisme**: `encodeToString(draf)` dua kali identik.
- **Belum terverifikasi di sesi ini**: test server yang butuh database scratch (`DiscoveryApiTest` dll.) — jalankan dengan `DB_NAME` yang berisi `scratch`.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: Tambah peran baru ke `DeterministicScreenRoles` (mis. `jadwal` dengan field `tanggal`). Tulis dulu tes yang gagal, pastikan draf tetap lolos validator dan bersih dari kosakata konveksi.
- [ ] **Tantangan 2**: `ChangeWidget` saat ini hanya TABLE↔KANBAN. Rancang aturan kelayakan untuk target `CHECKLIST` (apa syaratnya? field BOOL?) — tulis daftar penolakan bermesej *sebelum* menulis kodenya.
- [ ] **Tantangan 3**: Daftar kosakata konveksi di `VerticalPurity` masih berupa kode. Rancang bagaimana memindahkannya menjadi data pack (`DomainPack.reservedTerms`?) tanpa membuat pack baru bisa melemahkan penjaganya sendiri.
