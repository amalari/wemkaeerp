# 🎓 Modul Pembelajaran: Menyatukan Muatan Parameter Kosakata Field (SelectOption & NumberFormat)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design, "Kode vs Data", kompatibilitas kawat (wire compatibility), Strangler Fig untuk penyatuan tipe, batas ukuran file
> **Prasyarat**: Membaca `docs/plannings/PLAN-unify-field-vocabulary.md` (§2a/§3a/§3b), `.claude/rules/field-component-rules.md`, `.claude/rules/tenant-variability-rules.md`
> **Referensi Task**: Follow-up #2 keputusan D2 — penyatuan muatan parameter prototype ↔ CRM (2026-10-11)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Bayangkan dua kantor cabang punya **dua buku formulir yang isinya hampir sama tapi ditulis dengan dua kosakata berbeda**. Cabang A menulis status pesanan sebagai *kata* ("Hijau", "Kuning"), cabang B menulisnya sebagai *kode stiker* ("opt_hijau", "opt_kuning"). Sepintas tidak masalah sampai ada yang harus memindahkan formulir antar cabang: setiap orang menulis ulang aturannya sendiri, dan cepat atau lambat dua salinan itu **melenceng**.

Di repo ini "dua cabang" itu adalah **dua kosakata tipe field**:

- **Prototype/discovery** (`FieldSpec`) — dipakai generator layar prototype dan agent AI.
- **CRM** (`CrmFieldType`) — field kustom di modul CRM.

Keputusan **D2** (2026-10-10) sudah menyatukan **daftar tipe**-nya: keduanya kini memakai satu enum `FieldType`. Tapi **muatan parameter**-nya masih bercabang:

| Parameter | Prototype (sebelum) | CRM (sebelum) |
|---|---|---|
| Opsi pilihan | `List<String>` (nama polos) | `List<SelectOption>` (id + warna + arsip) |
| Format angka | enum `NumberFormat { PLAIN, CURRENCY, PERCENT }` + `currencyCode` | sealed `NumberFormat { Plain, Percent, Currency(code) }` |
| Batas rujukan | *(tidak ada)* | `maxCount` |

- **Masalah nyata**: setiap parameter baru harus ditambahkan **dua kali** (di `FieldSpec` dan di `CrmFieldType`), dengan risiko keduanya menyimpang. Menambah satu format baru berarti menyentuh dua tempat, dan test paritas harus mengiterasi dua kosakata.
- **Analogi sederhana**: alih-alih dua buku formulir, kita jadikan **satu lembar stiker parameter** (`SelectOption`, `NumberFormat`) yang ditempel di formulir mana pun. Nilainya boleh beda (cabang A tidak butuh warna, cabang B butuh), tapi **bentuk stikernya sama**.
- **Hasil akhir yang diharapkan**: `FieldSpec` (prototype) dan `CrmFieldType` (CRM) memakai **tipe parameter yang sama**, sehingga "satu tipe baru = satu tempat" benar-benar tercapai.

> **Penting — "satu tipe" ≠ "satu nilai".** Menyatukan *tipe* tidak berarti memaksa cabang A memakai warna. Prototype cukup mengisi label; CRM mengisi warna. Yang sama adalah **wadah**-nya.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Kalau kamu harus mengetik perubahan ini dari nol, urutannya begini — dan alasan tiap langkah penting.

1. **Langkah 0 — Baca konsumen & kawat dulu, jangan tulis kode.**
   Cari **siapa yang menulis dan membaca** parameter itu ke JSON. Untuk opsi: `InteractiveScreenCodec`, `SpecOpCodec`, `CustomAttributesCodec`. Untuk format: `CustomAttributesCodec`. Kunci kawat yang tersimpan (`options`, `format`, `currencyCode`, `maxCount`) **tidak boleh berubah** — kalau berubah, semua data lama pecah. Ini yang kita sebut **Kontrak 4 variability: parser tunggal, tanpa fallback senyap**.

2. **Langkah 1 — Pindahkan tipe bersama ke registry dulu (`core/domain/prototype`).**
   Pindahkan `SelectOption`, `SelectOptionId`, dan jadikan `NumberFormat` satu enum. Biarkan **kompilator** menunjukkan semua tempat yang harus disentuh. Ini lebih aman daripada mencari manual dengan `grep`.

3. **Langkah 2 — Ubah `FieldSpec` (registry) lebih dulu**, bukan `CrmFieldType`.
   `FieldSpec` adalah **hub**: begitu ia memakai tipe bersama, semua konsumen prototype (proposal, generator SQL, brief, UI) langsung gagal kompilasi dan memberi daftar kerja yang jujur.

4. **Langkah 3 — Perbaiki codec (kawat) dengan hati-hati.**
   Di sinilah kompatibilitas dijaga: tulis/membaca **bentuk kawat lama**, tapi simpan di memori sebagai tipe baru. Contoh: kawat opsi tetap `["A","B"]` (string), di memori jadi `[SelectOption("A"), SelectOption("B")]`.

5. **Langkah 4 — Terakhir `CrmFieldType`.**
   CRM adalah kosakata "turunan" (ia sudah bergantung ke registry lewat `FieldType`), jadi menyentuhnya terakhir memastikan semua keputusan bentuk sudah matang.

6. **Langkah 5 — Tulis test kompatibilitas** yang membaca **contoh config lama tulis-tangan** (bukan hasil `encode` sendiri). Inilah bukti paling kuat bahwa data lama tetap terbaca.

7. **Langkah 6 — Ratchet ukuran file.** Setelah semua hijau, cek `wc -l` file yang tumbuh; pecah bila melewati batas lunak.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Satu `SelectOption`, warna boleh kosong

```kotlin
// core/.../domain/prototype/SelectOption.kt
data class SelectOption(
    val id: SelectOptionId,
    val label: String,
    val colorHex: String? = null,
    val archivedAt: String? = null
) {
    /** Opsi dari nama polos (kawat prototype): id diturunkan, label = nilai sel. */
    constructor(label: String) : this(SelectOptionId(label.take(SelectOptionId.MAX_LENGTH)), label, null, null)
}
```

**Mengapa begini?**
- **`colorHex` nullable** adalah inti penyatuan tanpa memalsukan data. Prototype tidak punya UI pemilih warna; memaksa warna default di domain berarti **mengarang data tenant**. `null` = "belum diwarnai" — jujur. CRM tetap mewajibkan warna di kawat (opsi tanpa `colorHex` tetap ditolak), jadi perilaku CRM tidak berubah.
- **Konstruktor sekunder `SelectOption(label)`** menjawab satu pertanyaan sulit: *"apa id opsi prototype?"* Prototype tidak pernah memakai id (nilai selnya = label), jadi id cukup diturunkan dari label. Label >64 karakter (sah menurut `ProposalLimits.TEXT`) menghasilkan id terpotong — **tidak apa-apa**, karena id tak pernah mengalir ke kawat dan keunikan dijaga pada **label**, bukan id.

> **Jebakan yang dihindari**: kalau kita memaksa `id = label` tanpa potong, label 65 karakter akan **menggagalkan pembacaan dokumen lama** yang tadinya valid. Itu regresi senyap. Memotong id (bukan menolak dokumen) menjaga kompatibilitas.

### Blok B: `FieldSpec.options` naik kelas, nilai sel tetap label

```kotlin
// core/.../domain/prototype/FieldSpec.kt
val options: List<SelectOption> = emptyList()

/** Label opsi = nilai sel ENUM/MULTI_SELECT prototype; jembatan aturan berbasis label. */
val optionLabels: List<String> get() = options.map { it.label }
```

dan di `accepts`:

```kotlin
FieldType.ENUM -> value in optionLabels
FieldType.MULTI_SELECT -> MultiSelectValues.isValid(value, optionLabels, maxSelections)
```

**Mengapa begini?**
- **`optionLabels`** adalah jembatan murah (computed property) yang membuat aturan lama (mesin status, kolom kanban, `CHECK` SQL) tetap berbicara dalam **label**. Ini **bukan** duplikasi state — ia turunan satu arah dari `options`.
- **Nilai sel prototype tetap label** (bukan id) adalah keputusan kompatibilitas paling krusial: seluruh seed/dokumen lama menyimpan label. Kalau nilai sel berpindah ke id, kita butuh migrasi data — yang dilarang.

### Blok C: Kawat prototype tetap larik string

```kotlin
// InteractiveScreenCodec (encode)
"options" to jsonArrayOf(f.optionLabels.map(::jsonOf))
// InteractiveScreenCodec (decode)
f.stringArray("options").map { SelectOption(it) }
```

**Mengapa begini?**
- **Encode** menurunkan kembali ke label → dokumen lama **byte-identik** (tidak ada kunci/format baru yang bocor). Ini dijaga test `options_wireStaysPlainStringArray_...`.
- **Decode** membungkus tiap string jadi `SelectOption` → opsi kaya hanya hidup di memori. Satu arah masuk, satu arah keluar, tanpa fallback.

### Blok D: Satu `NumberFormat` + `currencyCode` terpisah (CRM)

```kotlin
// CrmFieldType.kt — sealed lokal dihapus
val format: NumberFormat = NumberFormat.PLAIN,      // enum bersama prototype
val currencyCode: String? = null,

init {
    if (format == NumberFormat.CURRENCY) {
        require(currencyCode != null && currencyCode.length == 3) { ... }
    } else {
        require(currencyCode == null) { ... }
    }
}
```

dan di codec:

```kotlin
// CustomAttributesCodec — tag kawat TIDAK berubah
NumberFormat.PLAIN -> "plain"; NumberFormat.PERCENT -> "percent"; NumberFormat.CURRENCY -> "currency"
// decode
"currency" -> if (config.string(KEY_CURRENCY_CODE) != null) NumberFormat.CURRENCY else null
```

**Mengapa `currencyCode` diteruskan hanya saat `CURRENCY`?**
```kotlin
currencyCode = if (format == NumberFormat.CURRENCY) config.string(KEY_CURRENCY_CODE) else null
```
Perilaku lama **mengabaikan** kode mata uang liar pada format non-currency. Kalau kita teruskan mentah-mentah, invarian `require(currencyCode == null)` akan **melempar** saat membaca baris lama yang (walau jarang) punya `{"format":"plain","currencyCode":"IDR"}` — data yang dulu terbaca, kini gagal. Jadi: **jaga perilaku baca lama**, walau tampak "kurang ketat". Ini contoh nyata prinsip *kompatibilitas mengalahkan kerapian*.

### Blok E: `maxCount` masuk registry, ditulis hanya bila ≠ 1

```kotlin
// FieldSpec — hanya USER_REF/RELATION boleh ≠ 1
if (type == FieldType.USER_REF || type == FieldType.RELATION) require(maxCount >= 1)
else require(maxCount == 1)

// codec encode — pola "tulis hanya bila bukan bawaan"
(if (f.maxCount != 1) mapOf("maxCount" to jsonOf(f.maxCount)) else emptyMap())
```

**Mengapa "tulis hanya bila ≠ 1"?** Dokumen lama tidak memuat `maxCount`. Kalau encode selalu menulisnya, dokumen lama akan **berubah byte** setelah dibaca-tulis — dan test paritas byte-stabil gagal. Menulis hanya saat ≠ 1 membuat bawaan tetap tak terlihat.

### Blok F: Memecah file mengikuti tanggung jawab, bukan baris

```text
core/.../domain/prototype/
├── FieldSpec.kt   (232 baris)  ← FieldType, NumberFormat, FieldSpec, relationTargetFormatError
└── EntitySpec.kt  (61 baris)   ← StateMachine, EntitySpec
```

**Mengapa?** `EntitySpec.kt` melewati batas *lunak* 250 baris untuk `core/**`. Karena kedua file berada di **paket yang sama**, pemecahan ini **tidak mengubah satu pun `import`** di seluruh repo — titik pecah paling murah yang menaati aturan §14 dan memberi tiap file satu nama yang jujur.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi / Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Satu tipe bersama di registry (`domain/prototype`)** | Bentuk tipe terpisah per kosakata | Satu tempat pendaftaran; kompilator memaksa semua konsumen diperbarui | Dua salinan menyimpang; parameter baru ditambah dua kali |
| **Kawat prototype tetap larik string** | Kawat opsi jadi objek (id/warna) di prototype | Dokumen lama byte-identik **tanpa migrasi data** | Semua dokumen lama pecah; butuh migrasi yang dilarang Kontrak 4 |
| **`colorHex: String?`** | Warna wajib, isi default di domain | Menolak "mengarang data tenant"; `null` = jujur | Warna palsu masuk data tenant; UI memilih warna yang tak pernah dipilih user |
| **`id` prototype = label terpotong** | `require(id == label)` / menaikkan kap id | Kompatibel dengan label panjang yang tadinya sah | Dokumen lama dengan label >64 karakter gagal dibaca |
| **`maxCount` ditulis hanya bila ≠ 1** | Selalu tulis semua parameter | Dokumen lama byte-stabil; test paritas byte tetap hijau | Byte dokumen lama bergeser setelah round-trip |
| **Pecah `FieldSpec.kt` dari `EntitySpec.kt` (paket sama)** | Biarkan file tumbuh | Batas §14 dipenuhi **tanpa** mengubah import mana pun | `EntitySpec.kt` jadi God File berisi konsep yang tak seharusnya |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1 — Mengubah kunci kawat demi "kerapian".**
   *Kenapa bahaya*: `options` → `selectOptions`, `currencyCode` → `code` terasa lebih rapi, tapi setiap baris DB lama berhenti terbaca. Data tak hilang secara fisik, tapi tak terbaca = sama saja hilang.
   *Solusi kita*: **kunci wire adalah kontrak beku**. Ubah **tipe di memori**, jangan kunci di kawat.

2. **Jebakan 2 — Fallback senyap ke default.**
   *Kenapa bahaya*: format `"euro"` yang tak dikenal lalu dibaca sebagai `PLAIN` mengubah data tanpa jejak. Test kita menolak ini (`decodeFieldType_numberWithUnknownFormat_isRejectedNotPlain`).
   *Solusi kita*: nilai tak dikenal → `null`/gagal, **bukan** default.

3. **Jebakan 3 — Menguji kompatibilitas dengan hasil `encode` sendiri.**
   *Kenapa bahaya*: test yang `decode(encode(x))` hanya membuktikan kodec konsisten dengan dirinya sendiri; ia **buta** terhadap bentuk data lama.
   *Solusi kita*: tulis JSON lama **tangan** di test (`configCodec_legacySingleSelectJsonWithColoredOptions_readsIdentically`), lalu buktikan tetap terbaca identik.

4. **Jebakan 4 — Memaksa satu aturan validasi untuk dua kosakata.**
   *Kenapa bahaya*: CRM mengizinkan opsi kosong (dialog belum selesai) dan `maxSelections` tak dibatasi `options.size`; prototype lebih ketat. Menyeragamkan aturan bisa **menolak data CRM lama**.
   *Solusi kita*: satukan **tipe**, bukan aturan yang berbeda; aturan yang berbeda dipertahankan di `CrmFieldType.init` dengan komentar eksplisit.

5. **Jebakan 5 — Menambah baris ke file yang sudah panjang.**
   *Kenapa bahaya*: satu baris lagi ke file 287 baris memindahkannya makin jauh dari batas, dan berikutnya jadi 300.
   *Solusi kita*: aturan **ratchet** (§14) — begitu menyentuh, cicil. Di sini kita justru memecah `EntitySpec.kt`.

6. **Jebakan 6 — Menganggap `id` opsi bisa sama dengan `label` tanpa batas.**
   *Kenapa bahaya*: `SelectOptionId` dibatasi 64 karakter (batas identitas CRM), tetapi label opsi prototype boleh 200 karakter. Menyatukan tanpa potong = crash saat decode dokumen lama.
   *Solusi kita*: mint id terpotong yang **tidak pernah** ke kawat.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Strategi pengujian berlapis (semua **murni**, tanpa DB):

1. **Test kompatibilitas kawat (paling penting).**
   - Opsi prototype tetap larik string & byte-identik; label panjang tetap utuh.
   - `NUMBER` legacy `{"format":"currency","currencyCode":"IDR"}` tetap → `(CURRENCY, "IDR")`.
   - `SINGLE_SELECT` legacy dengan opsi berwarna + arsip tetap terbaca identik.
   - Kode mata uang "liar" pada format non-currency **diabaikan** (perilaku lama).
   - `maxCount` bawaan tidak ditulis; `0` ditolak invarian.

2. **Test paritas tipe (pagar kompilator).**
   `PrototypeFieldTypeCodecParityTest`, `CrmFieldTypeParityTest`, `LeadFieldControlParityTest` mengiterasi `FieldType.entries` dengan `when` **tanpa `else`** — tipe baru langsung menggagalkan kompilasi.

3. **Jalankan seluruh suite.**
   ```bash
   ./gradlew :core:jvmTest :app:shared:jvmTest :server:test
   # core 1870 · app/shared 468 · server 806 — semua hijau
   ```
   (Target Android `assembleAndroidMain` butuh Android SDK; tidak tersedia di lingkungan ini.)

> **Catatan**: perubahan ini **tidak** menyentuh UI yang bisa dilihat, kecuali satu penyesuaian tipe (fallback warna `null`) yang menjaga tampilan CRM lama. Cek visual bukan kebutuhan utama task ini.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1 — Buktikan kompatibilitas lebih keras.** Tulis sebuah test yang mengambil JSON `InteractiveScreen` **lama** (sebelum penyatuan) dari git history, lalu decode dengan kode sekarang dan pastikan field `options`-nya identik. Diskusikan mengapa test ini lebih kuat daripada `decode(encode(x))`.
- [ ] **Tantangan 2 — Satu `targetResource`.** Konsep `FieldSpec.target` (prototype) dan `CrmFieldType.targetResource` (CRM) sebenarnya sama (rujukan RELATION). Rancang penyatuannya **tanpa** mengubah kunci kawat kedua kosakata, lalu tulis Discovery Note singkat: apa risikonya, dan aturan validasi mana yang berbeda?
- [ ] **Tantangan 3 — Nilai sel prototype vs id.** Kalau suatu hari kita ingin prototype **juga** memakai id opsi (bukan label) sebagai nilai sel, apa yang harus terjadi pada seluruh seed & dokumen lama? Tuliskan rencana migrasinya (atau jelaskan mengapa itu tidak layak).
- [ ] **Tantangan 4 — Batas aturan vs tipe.** Tambahkan satu test yang membuktikan `CrmFieldType(MULTI_SELECT, maxSelections = 5)` dengan 2 opsi tetap **sah** (aturan CRM), tetapi `FieldSpec` yang ekuivalen ditolak. Jelaskan mengapa perbedaan ini disengaja.

---

## 📎 Rujukan

- Rencana: [`docs/plannings/PLAN-unify-field-vocabulary.md`](../plannings/PLAN-unify-field-vocabulary.md) §2a, §3a, §3b
- Aturan: [`.claude/rules/field-component-rules.md`](../../.claude/rules/field-component-rules.md), [`tenant-variability-rules.md`](../../.claude/rules/tenant-variability-rules.md), [`file-size-rules.md`](../../.claude/rules/file-size-rules.md)
- Kode: `core/.../domain/prototype/SelectOption.kt`, `FieldSpec.kt`, `EntitySpec.kt`, `core/.../domain/customfield/CrmFieldType.kt`, `CustomAttributesCodec.kt`
- Pendahulu: [`teaching-field-unify-vocabulary-crm.md`](teaching-field-unify-vocabulary-crm.md) (keputusan D2 — penyatuan registry tipe)
