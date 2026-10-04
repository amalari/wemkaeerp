# 🎓 Modul Pembelajaran: Spec Ops, Blok Form & Brief — Jalur Agent B (B2–B5)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design murni, Spesifikasi sebagai Data, Operator Sealed (kosakata tertutup), Determinisme byte-per-byte, Parser kata kunci tanpa LLM
> **Prasyarat**: Paham struktur `core/src/commonMain/.../domain/prototype/` (EntitySpec/FieldSpec/PrototypeSpec), konsep tenant-variability (kode vs data), dan baca dulu `teaching-proto-b-contract-v1.md` (B0/B1)
> **Referensi Task**: `docs/plannings/parallel/PLAN-proto-B-spec-capture.md` (butir B2–B5)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Produknya: alat untuk software house. Klien bercerita di `/builder/prototype`, mencoba prototype
yang *benar-benar bisa diklik*, dan tim kita menerima **brief** yang bisa dikerjakan. Tiga pertanyaan
yang dijawab jalur B di gelombang ini:

1. **"Klien mau bisa nambah data dari form"** → butir B2: blok FORM yang interaktif.
2. **"Klien bilang 'tambah status Revisi setelah Dikerjakan'"** → butir B3 + B5: operasi pada spec
   (`SpecOp`) yang diterapkan aman, dan pengusul operasi dari bahasa Indonesia.
3. **"Besok pagi developer yang tidak ikut sesi harus tahu apa yang diminta klien"** → butir B4:
   `RequirementsBrief` + `BriefRenderer.markdown` yang deterministik.

**Masalah nyata yang dihindari**: kalau operasi spec ditulis sebagai string bebas ("klien boleh
ketik apa saja, AI yang nebak"), maka (a) LLM bisa menyelundupkan operasi berbahaya ("hapus semua"),
(b) spec hasil bisa tidak valid, (c) brief-nya berbeda setiap kali di-render sehingga tidak bisa
di-golden-test. Karena itu semua operasi **sealed**, semua kegagalan **`Result.failure` berpesan
siap-tampil**, dan semua keluaran teks **deterministik**.

**Analogi**: `SpecOp` itu seperti formulir permintaan perubahan bernomor di pabrik — hanya ada 5
jenis formulir, tidak bisa karang sendiri; `SpecOpApplier` petugas loket yang menolak dengan alasan
jelas; `DeterministicSpecOpProposer` petugas yang menuliskan formulir dari omongan klien — kalau
omongannya tidak jelas, dia bertanya, *bukan menebak*.

**Hasil akhir**: form Stok Kain garment yang wajib mengisi Bahan/Stok/Kepemilikan; lima operasi spec
yang menulis ulang seed & mesin status dengan benar; brief Markdown yang sama byte-per-byte; dan
pengusul kalimat Indonesia yang berlaku lintas industri (garment, bordir, sablon, tiket servis).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0 — Kontrak dulu (B0, sudah selesai sebelumnya)**: `FieldSpec.required`,
   `FormConfig`, `SpecOp`, codec, fixture `PrototypeContractSamples` (non-garment, tiket servis).
   *Mengapa lebih dulu?* Karena Agent A (UI) dan C (server) menunggu bentuk JSON-nya. Kontrak =
   jalan dua arah yang dibekukan.
2. **Langkah 1 — Petunjuk sebagai data (B2)**: `FormHints` di `PrototypeHints.kt`. Form tanpa
   petunjuk pack tidak punya isi — jadi petunjuknya **wajib data pack**, bukan karangan mesin.
3. **Langkah 2 — Factory + Registry (B2)**: `InteractiveScreenFactory.form(...)` lalu cabang
   `WidgetKind.FORM` di `WidgetRegistry.interactiveFor`.
4. **Langkah 3 — Kawat JSON (B2)**: `formHints` opsional di `DomainPackCodec` — kompatibel mundur.
5. **Langkah 4 — Applier penuh (B3)**: isi kerangka `SpecOpApplier` dengan lima operasi.
6. **Langkah 5 — Renderer + golden test (B4)**: `BriefRenderer.markdown` dikunci byte-per-byte.
7. **Langkah 6 — Pengusul (B5)**: `DeterministicSpecOpProposer`, parser kata kunci.

*Mengapa domain dulu, bukan UI/endpoint?* Karena UI (A) dan endpoint (C) hanya bisa dikembangkan
terhadap **fixture kontrak**. Domain murni tanpa framework = bisa dites semua target tanpa server.

## 🔍 3. Bedah Kode Blok per Blok

### 3.1 B2 — `FormHints` dan form yang berbagi entitas dengan layar sumber

```kotlin
// PrototypeHints.kt
data class FormHints(
    val fields: List<String>,                                  // urutan field di form
    val required: List<String> = emptyList(),                  // wajib diisi (ditegakkan reducer)
    val options: Map<String, List<String>> = emptyMap(),       // field ENUM + opsinya
    val submitLabel: String? = null
)
```

**Mental model**: form itu *pelengkap* layar sumber (tabel/papan) satu modul — bukan pulau sendiri.
Penghubungnya adalah pasangan **(moduleId, entityId)**: `PrototypeScreen.moduleId` form dan sumber
sama, dan spec keduanya menunjuk `entityId` yang sama (`"item"`). Konsekuensinya, dalam satu
`PrototypeSession` (satu store di memori), baris yang dibuat lewat form langsung tampil di tabel.

```kotlin
// WidgetRegistry.interactiveFor — cabang FORM
WidgetKind.FORM -> pack.screenSuggestions.firstOrNull { it.moduleId == screen.moduleId }
    ?.formHints?.let { InteractiveScreenFactory.form(screen.screenId, screen.title, it) }
```

**Dua keputusan desain di sini, pahami "why"-nya:**

1. *Petunjuk form dibaca dari usulan modul yang sama, apa pun watak widget sumbernya* — karena
   `formHints` menempel pada usulan TABLE Stok Kain, bukan pada usulan ber-widget FORM. Form
   melengkapi tabelnya. Tanpa `formHints` → `null` → digambar statis. **Tidak pernah menebak**
   (tolak, bukan fallback senyap).
2. *`InteractiveScreenFactory.form` mengembalikan `null` untuk petunjuk invalid* (field kosong,
   kembar) — pola yang sama dengan factory lain: bentuk tak cocok jatuh ke gambar statis.

Data pack garment (`GarmentScreenSuggestions`, usulan INVENTORY):

```kotlin
formHints = FormHints(
    fields = listOf("Bahan", "Stok", "Kepemilikan"),
    required = listOf("Bahan", "Stok", "Kepemilikan"),   // ← Bahan, Stok, Kepemilikan wajib
    options = mapOf("Kepemilikan" to listOf("Milik pabrik", "Titipan buyer")),
    submitLabel = "Catat bahan"
)
```

`Kepemilikan` sengaja ENUM dengan dua opsi itu — bayangan Kontrak 3 module-integration-rules
(`OWNED` vs `CONSIGNED`): field wajib yang memaksa klien mengaku status kepemilikan sejak awal.

**Codec kompatibel mundur** (`DomainPackCodec`): `formHints` di-encode selalu, di-decode **boleh
tidak ada** (`s.raw.obj("formHints")?` → null). Pack lama tanpa form tetap terbaca — diuji lewat
round-trip penuh `GarmentDomainPack`.

### 3.2 B3 — `SpecOpApplier`: lima operasi, validasi tidak pernah dilonggarkan

Aturan emasnya satu kalimat: **spec hasil selalu dibangun lewat konstruktor `PrototypeSpec`, jadi
spec tidak sah tidak akan pernah terbentuk** — validasi konstruktor bertindak sebagai gerbang terakhir.

| Operasi | Yang ditulis ulang | Yang ditolak |
|---|---|---|
| `AddEnumOption` | opsi field + kolom kanban (posisi `after`) | opsi ganda, `after` tak dikenal, field non-ENUM |
| `RenameEnumOption` | opsi, **kolom kanban**, **mesin status**, **seed** | `from` tak ada, `to` sudah ada |
| `AddTransition` | `StateMachine.transitions` | opsi tak ada, `from == to`, tanpa mesin status |
| `AddField` | entitas saja (lihat keputusan di bawah) | key ganda |
| `RenameFieldLabel` | label tampil saja | key tak ada, label kosong |

**⚠️ Jebakan yang benar-benar terjadi saat implementasi (belajar dari ini):** versi pertama
`RenameEnumOption` memakai helper `withField` lalu `withScreens` — masing-masing membangun ulang
`PrototypeSpec`. Tahap antaranya **tidak sah**: opsi sudah diganti "Arsip" tapi kolom kanban masih
"Selesai" → konstruktor menolak: *"kolom di luar opsi 'Kolom'"*. Pelajarannya: **operasi yang
menyentuh beberapa bagian spec yang saling divalidasi harus dihitung dulu semuanya, lalu membangun
spec sekali** (atomik):

```kotlin
val newEntity = e.copy(fields = ..., stateMachine = ...)   // hitung
val newScreens = screen.spec.screens.map { ... }           // hitung
val next = screen.copy(spec = PrototypeSpec(...), seed = ...)  // bangun SEKALI
```

(Perhatikan juga: mesin status milik field *lain* tidak boleh ikut tertimpa `null` — `when` di
`newEntity` menjaganya.)

**Keputusan B3 yang wajib kamu tahu (terdokumentasi di KDoc kelas):** `AddField` **hanya** menambah
field ke entitas; tidak ada layar yang berubah otomatis. Alasannya: `TableConfig.columns` dan
`FormConfig.fields` adalah daftar eksplisit per layar — model ini tidak punya layar "tampilkan semua
field", jadi memunculkan field baru di layar adalah keputusan lanjutan, bukan efek samping diam-diam.
Diuji eksplisit: `addField_addsToEntityOnly_screensUnchanged_documentedDecision`.

`applyAll` tidak berubah dari kontrak B0: maksimal `MAX_OPS_PER_TURN = 5`, satu per satu, yang gagal
tidak membatalkan yang sah, semuanya dicatat sebagai `CaptureEntry` untuk brief.

### 3.3 B4 — `BriefRenderer.markdown`: deterministik berarti bisa di-golden-test

Susunan tetap: **Ringkasan → Modul & layar → Perubahan dari klien → Cakupan katalog → Kebutuhan
kustom**. Tiga aturan determinisme:

1. **Tidak ada jam, tidak ada locale.** Waktu datang dari `CaptureEntry.at` (dikirim pemanggil);
   rupiah diformat manual: `"Rp " + v.toString().reversed().chunked(3).joinToString(".").reversed()`
   — trik yang sama dengan seed garment. `String.format`/`NumberFormat` dilarang karena keluarannya
   bergantung locale mesin.
2. **Isi datang dari data; renderer buta industri.** Tidak ada satu pun kata "kain"/"SPK" di
   `BriefRenderer.kt` — deskripsi operasi (`describe(op)`) hanya menerjemahkan *struktur* SpecOp
   ("tambah status 'X' pada 'Y' setelah 'Z'"), bukan kosakata vertikal.
3. **Golden test byte-per-byte untuk dua template** (`BriefRendererGoldenTest`): tiket servis
   (non-garment) dan papan SPK sampling (garment). Kalau sengaja mengubah format, golden test-nya
   harus diubah sadar-sadar — itu fiturnya, bukan kekurangannya.

Jebakan kecil yang terjadi: renderer mengakhiri output dengan `\n` (karena `appendLine` terakhir),
sedangkan `trimIndent()` golden tidak — golden test langsung menangkapnya. Persis gunanya golden test.

### 3.4 B5 — `DeterministicSpecOpProposer`: kata kunci, bukan kecerdasan

```kotlin
val segments = message.split("\n", ";", " lalu ")   // beberapa permintaan sekali kirim
require(segments.size <= SpecOpApplier.MAX_OPS_PER_TURN) { ... }
```

Empat pola regex (huruf besar/kecil bebas): `tambah(kan) status|kolom|field X [setelah Y]`,
`(ganti|ubah) nama X (jadi|menjadi|→) Z`, `(izinkan|bolehkan) X ke Y`. Dua aturan resolusi yang
membuatnya aman:

- **Resolusi entitas**: layar harus satu entitas; lebih dari itu → gagal dengan pesan jelas
  (kosakata operasi belum punya cara menyebut entitas — jadi jangan pura-pura bisa).
- **Resolusi field status**: kalau entitas punya tepat satu field ENUM → itu; kalau lebih, yang
  dipakai yang *ditunjuk struktur layar* (groupField kanban / statusField tabel / mesin status) —
  dan bila masih ambigu, **gagal**, minta disebut eksplisit.

Pencocokan nama yang sudah ada dilakukan **case-insensitive ke nilai kanonik** ("bolehkan baru ke
selesai" → `Baru`→`Selesai`), sedangkan **nama baru dipakai apa adanya** (klien yang menentukan
penamaan, bukan mesin). `parseRename` memutuskan RenameEnumOption vs RenameFieldLabel dengan urutan
yang pasti: cocok opsi status dulu, baru cocok field (key/label).

Prinsip terpenting: **kalimat tak dikenal menggagalkan seluruh pesan** dengan `EXAMPLES` yang
memuat contoh kalimat — bukan mengeksekusi bagian yang "kira-kira maksudnya itu". Dan keluaran
proposer **hanya usulan**: validasi final tetap di `SpecOpApplier`, jadi proposer salah pun tidak
pernah merusak spec.

## 🏗️ 4. Arsitektur: Mengapa Begini, Bukan Begitu

| Keputusan | Alternatif yang ditolak | Alasan |
|---|---|---|
| `SpecOp` sealed interface (kosakata tertutup) | String bebas / JSON schema terbuka | LLM tidak bisa menyelundupkan operasi di luar kosakata; kompilator memaksa `when` lengkap |
| `Result.failure` berpesan bahasa pengguna | Exception teknis ke UI | Pesan tampil apa adanya ke klien di kanvas ("Status 'Hantu' tidak ada di 'Status SPK'") |
| FormHints di data pack | Form digenerasi mesin dari kolom tabel | Uji Variabilitas: isi form beda per industri/pack; bentuk widget (FORM) yang jadi kode |
| Rename menulis ulang seed | Simpan alias/peta rename | Dokumen prototype harus jujur menampilkan status baru; alias = utang teknis tersembunyi |
| Golden test Markdown | Snapshot library | Nol dependensi (domain murni), dan kegagalan diff-nya terbaca manusia |
| Proposer kata kunci dulu, LLM belakangan | Langsung LLM | Endpoint `spec-ops` bisa hidup tanpa kunci API; adaptor LLM (C6) menumpang port yang sama & validator yang sama |

---

## 🧪 5. Tes: apa yang dikunci, dan template keduanya

Kontrak 6 (tenant-variability-rules): **setiap fitur diuji dengan template kedua yang non-default.**
Di jalur ini setiap test class memakai dua fixture — tiket servis (non-garment,
`PrototypeContractSamples`) dan papan SPK sampling garment (`InteractiveScreenFactory.kanban` +
`KanbanHints`).

| File | Yang dikunci |
|---|---|
| `SpecOpApplierTest` | tiap operasi sah → spec valid (kolom kanban, mesin status, seed ikut); tiap operasi tak sah → `failure` berpesan; `applyAll` lanjut setelah kegagalan; integrasi dengan `PrototypeReducer` (transisi baru benar-benar membuka jalan kartu) |
| `InteractiveScreenFactoryTest` (+`InteractiveFormTest`) | form interaktif lewat registry; `required` ditegakkan reducer dengan pesan nama field; form tanpa hints → null (statis); field form ⊆ kolom tabel sumber; `formHints` selamat round-trip pack codec |
| `BriefRendererGoldenTest` | Markdown byte-per-byte (garment + non-garment); determinisme; modul tanpa perubahan tetap tercetak; seksi kosong dijelaskan (`_Tidak ada perubahan._`) |
| `DeterministicSpecOpProposerTest` | kalimat emas 4 template (sampling, bordir, sablon, tiket servis); kalimat ngawur/berbahaya ditolak + memuat "Contoh:"; batas 5 permintaan |
| `PrototypeContractTest` (B0, tidak diubah) | kontrak lintas jalur tetap hijau — bukti B2–B5 tidak menggeser bentuk yang diandalkan A & C |

Perintah verifikasi jalur ini: `./gradlew :core:jvmTest` (hijau, 1240 test), kompilasi
`:core:compileKotlinJvm/-WasmJs/-Js` hijau, `:server:compileKotlin` hijau,
`scripts/audit-variability.sh` → 0 temuan.

---

## 🏆 6. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Tambahkan operasi keenam `MoveEnumOption(entityId, field, option, after)` —
      memindahkan posisi opsi. Ingat: hitung dulu semuanya (opsi + kolom kanban), bangun spec sekali,
      dan tulis test untuk opsi yang jadi `after`-nya sendiri.
- [ ] **Tantangan 2**: Pack bordir baru punya formHints untuk "Ukuran Jahitan" dengan field ENUM
      `Jahitan` — buktikan lewat test (fixture non-garment/non-rajut!) bahwa registry menyajikan form
      itu tanpa mengubah satu baris kode mesin. Kalau kamu tergoda mengubah `WidgetRegistry`,
      kamu sedang melanggar Uji Variabilitas.
- [ ] **Tantangan 3**: Tambahkan seksi "Pertanyaan terbuka untuk klien" di `BriefRenderer` untuk
      `CaptureEntry` yang `ok = false` — dan perbarui **kedua** golden test. Rasakan bagaimana
      golden test membuat perubahan format jadi keputusan yang sadar.

---

## 📎 Lampiran — Berkas yang disentuh (jalur B, B2–B5)

- `core/.../domain/prototype/`: `PrototypeHints.kt` (+`FormHints`), `InteractiveScreenFactory.kt`
  (+`form`), `WidgetRegistry.kt` (cabang FORM), `SpecOpApplier.kt` (penuh), `SpecOpProposer.kt`
  (port saja), `DeterministicSpecOpProposer.kt` (baru)
- `core/.../domain/pack/`: `DomainPack.kt` (+`formHints`), `GarmentScreenSuggestions.kt` (form Stok Kain)
- `core/.../shared/pack/DomainPackCodec.kt` (+`formHints` opsional, kompatibel mundur)
- `core/.../domain/discovery/brief/BriefRenderer.kt` (penuh)
- Test: `SpecOpApplierTest.kt`, `DeterministicSpecOpProposerTest.kt`,
  `BriefRendererGoldenTest.kt` (baru), `InteractiveScreenFactoryTest.kt` (+`InteractiveFormTest`)

