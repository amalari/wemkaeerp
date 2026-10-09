# 🎓 Modul Pembelajaran: A0 — Tipe Field `MULTI_SELECT` (Pilihan Ganda)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Kotlin Multiplatform, Domain-Driven Design, `enum`/`sealed` sebagai kosakata tertutup, kode vs data
> (Uji Variabilitas), JSON kanonik, pemetaan SQL/Exposed (`TEXT[]`), codec ketat, paritas kompilator.
> **Prasyarat**: Kotlin data class, `when` ekspresi, dasar SQL (array Postgres), konsep multi-tenant.
> **Referensi Task**: `docs/trd/TRD-FIELD-003-multi-select.md` (A0), `docs/plannings/PLAN-field-component-gaps.md` §2 Irisan 3,
> `.claude/rules/field-component-rules.md`.

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Bayangkan sebuah klinik butuh mencatat **alergi pasien**. Satu pasien bisa alergi beberapa hal: `["Gigi", "Jantung"]`.
Atau toko bordir mencatat **layanan yang dibeli** satu pesanan: `["Digitizing", "Hooping", "Selesai"]`.

Dulu, atribut berlabel ganda seperti ini dipaksa salah satu dari dua bentuk yang dua-duanya buruk:

- Menjadi **teks bebas** (`TEXT`) → datanya tak terstruktur. "Alergi: gigi, jantung, GIGI" tidak bisa difilter, tidak
  tervalidasi, dan seseorang mengetik "jantung" vs "Jantung" menghasilkan dua nilai berbeda.
- Menjadi **`ENUM`** (satu pilihan) → salah *makna*. `ENUM` adalah status kerja yang berpindah lewat transisi; alergi
  pasien tidak "berpindah status".

`MULTI_SELECT` hadir sebagai **tipe ke-6** yang menyelesaikan ini: satu field, **banyak** nilai dari daftar tertutup,
tanpa mengubah makna `ENUM`.

**Analogi sederhana.** `ENUM` itu seperti tombol radio (satu pilihan), `MULTI_SELECT` itu seperti kotak centang
(checklist) yang boleh dicentang lebih dari satu — tetapi **hanya dari daftar yang panitia sudah siapkan**.
Tidak ada "tulis harga sendiri di kotak lain".

**Kenapa A0 (gerbang kontrak) lebih dulu?** Menambah entri ke `enum class FieldType` membuat **semua** `when (FieldType)`
di seluruh aplikasi gagal kompilasi — dan itu memang disengaja (lihat §5). Jadi langkah pertama bukan "bikin UI cantik",
tetapi **menutup semua lubang kompilasi** dengan bentuk tipe yang persis, sekecil mungkin, agar Track B (agent/server)
dan Track C (UI chip) bisa berjalan paralel tanpa saling menunggu. A0 = *bentuk tipe + kawat*, **bukan** operasi
suntingan (`SetFieldMaxSelections` itu Track A sisa) dan **bukan** UI Track C.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Kalau mengetik dari layar kosong, urutannya begini. **Lebih dulu bentuk nilai daripada tabel database**, karena
tabel adalah turunan dari kontrak, bukan sebaliknya.

1. **Langkah 0 — Aturan nilai dulu** (`MultiSelectValues`): apa itu "sah", bagaimana "kanonik" (urutan, duplikat,
   kosong). Ini fungsi murni, bisa diuji tanpa apa pun.
2. **Langkah 1 — Kosakata & invarian** (`FieldType.MULTI_SELECT` + `FieldSpec.maxSelections`): menambah entri enum dan
   parameter, lalu **biarkan kompilator menunjukkan** semua `when` yang harus disentuh.
3. **Langkah 2 — Tutup semua `when` core** (`accepts`, `ProposalEdit`, `DeterministicScreenProposer`, `ChangeWidgetOp`,
   `ProposalEntityRules`): pilih perlakuan yang **benar**, bukan `else ->` yang menenangkan kompilator.
4. **Langkah 3 — Codec** (`InteractiveScreenCodec`, `SpecOpCodec`, `ScreenProposalCodec`, `ScreenSuggestionCodec`):
   tulis kunci `maxSelections` **hanya bila bukan null**, dan baca ketat (tipe salah = ditolak).
5. **Langkah 4 — Generator** (`SpecColumns`, `SpecPostgresWriter`, `SpecRoutesWriter`): kolom `TEXT[]`, CHECK opsi,
   tulis/baca larik, validasi route.
6. **Langkah 5 — Cabang stub kompilasi-forced di server/app** (katalog agent, `FieldInput`, `displayValue`):
   placeholder jujur, ditandai "Track B/C".
7. **Langkah 6 — Tes**: unit nilai, invarian, SQL, Exposed, **dan tes integrasi Postgres** (ini pemakaian larik pertama).

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Aturan Nilai — `MultiSelectValues` (file baru, tema sendiri)

```kotlin
object MultiSelectValues {
    fun isValid(raw: String, options: List<String>, maxSelections: Int?): Boolean {
        if (raw.isEmpty()) return true          // belum diisi
        val parsed = parse(raw) ?: return false // bukan array JSON string sah
        if (parsed.isEmpty()) return false      // "[]" DITOLAK — satu bentuk kosong saja
        if (parsed.distinct().size != parsed.size) return false
        if (parsed.any { it !in options }) return false
        if (maxSelections != null && parsed.size > maxSelections) return false
        return true
    }

    fun encode(selected: Collection<String>, options: List<String>): String {
        val ordered = options.filter { it in selected.toSet() }   // urut MENURUT options
        return if (ordered.isEmpty()) "" else jsonArrayOf(ordered.map { jsonOf(it) }).encode()
    }
}
```

**Mengapa begini?**
- **Satu bentuk kosong.** `""` = belum diisi, `"[]"` = tidak sah. Kalau dua-duanya sah, tiap pemakaian harus
  memutuskan arti keduanya, dan itu sumber bug klasik (*"kok data lama saya kosong, bukan null?"*).
- **Kanonik.** Urutan mengikuti `options`, bukan urutan klik. Akibatnya nilai yang sama **selalu** menghasilkan
  string yang sama → codec jadi byte-stabil → bisa dibandingkan di tes. Kalau urutan klik dipertahankan,
  `["a","b"]` dan `["b","a"]` adalah dua nilai berbeda meski artinya sama.
- **Ditaruh di file terpisah.** `EntitySpec.kt` punya batas 250 baris; menumpuk logika larik di sana akan menjadikannya
  God File. Satu file = satu konsep (CLAUDE.md §8).

### Blok B: Kosakata & Invarian — `EntitySpec.kt`

```kotlin
enum class FieldType { TEXT, LONG_TEXT, NUMBER, DATE, ENUM, MULTI_SELECT, BOOL, RELATION, FILE }
```

```kotlin
if (type == FieldType.ENUM || type == FieldType.MULTI_SELECT) {
    require(options.isNotEmpty() && options.distinct().size == options.size) {
        "Field ${type.name} '$key' wajib punya opsi unik"
    }
}
if (type == FieldType.MULTI_SELECT) {
    require(maxSelections == null || maxSelections in 1..options.size) { … }
} else {
    require(maxSelections == null) { "… bukan MULTI_SELECT, jadi tidak boleh punya maxSelections" }
}
```

```kotlin
FieldType.MULTI_SELECT -> MultiSelectValues.isValid(value, options, maxSelections)
```

**Mengapa begini?**
- **`MULTI_SELECT` diletakkan setelah `ENUM`**, bukan di akhir. Posisi enum tidak mengubah penyimpanan, tapi menaruhnya
  berdampingan dengan kerabat terdekatnya membuat niat terbaca.
- **`maxSelections` ditaruh di AKHIR daftar parameter** `FieldSpec`. Kalau ditaruh di tengah, **semua** pemanggilan
  positional lama (ada di puluhan file) akan tergeser dan salah nilai secara diam-diam. Menambah di akhir = argumen
  lama tidak bergeser.
- **Invarian di konstruktor** (`require`) berarti tidak mungkin ada `FieldSpec` MULTI_SELECT tanpa opsi atau dengan
  `maxSelections` di luar rentang. Fail-fast di gerbang masuk paling awal.

### Blok C: SQL — `SpecColumns.sqlDefinition()`

```kotlin
FieldType.MULTI_SELECT -> buildString {
    append("TEXT[]").append(notNull)
    append(" CHECK (").append(name).append(" <@ ARRAY[")
    append(field.options.joinToString(", ") { SpecNaming.sqlString(it) })
    append("]::text[])")
    if (field.required) append(" CHECK (cardinality($name) > 0)")
    field.maxSelections?.let { append(" CHECK (cardinality($name) <= $it)") }
}
```

**Mengapa begini?**
- **Integritas ditegakkan DB, bukan hanya aplikasi.** `col <@ ARRAY[...]` memastikan **setiap** elemen adalah opsi yang
  sah. Kalau hanya aplikasi yang menjaga, satu skrip migrasi manual atau bug di jalur tulis akan memasukkan sampah
  ke database yang tak pernah divalidasi lagi.
- **`cardinality`**: `required` menolak larik kosong (`> 0`), `maxSelections` menolak kelebihan (`<= N`). Angka `N`
  berasal dari spec, jadi CHECK selalu sinkron dengan kontrak.

### Blok D: Exposed — `SpecPostgresWriter`

```kotlin
FieldType.MULTI_SELECT -> "array<String>($n)"          // kolom
// tulis: optional → MultiSelectValues.parse(raw)  (null bila "")
//        required → MultiSelectValues.parse(raw).orEmpty()
// baca:  MultiSelectValues.encode(cell, listOf(...)) // string JSON kanonik
```

**Mengapa begini?**
- **`null` ↔ `""`.** Di Postgres, kolom opsional yang belum diisi = `NULL`; di prototype, belum diisi = `""`. Penulis
  memetakan `""` → `null` (parse dari string kosong = null) dan pembaca memetakan `null` → `""`. Satu bentuk kosong
  di dua dunia.
- **Baca selalu di-`encode` ulang**, bukan mengembalikan larik mentah. Artinya nilai yang dibaca selalu kanonik
  (urut menurut `options`), apa pun urutan yang tersimpan.

### Blok E: Route Scaffold — `SpecRoutesWriter`

```kotlin
val problem = dateProblem(values) ?: textProblem(values) ?: multiProblem(values) ?: PrototypeReducer…
```

**Mengapa begini?**
- **Pola `textProblem`/`dateProblem` diikuti**, memanggil `FieldSpec.accepts` yang sama dengan prototype. Tujuannya
  satu: aturan validasi **tidak boleh berbeda** antara yang ditegakkan di klien dan di server. Satu fungsi, dua pemakai.

### Blok F: Codec — kunci opsional "hanya bila bukan null"

```kotlin
JsonValue.Obj(
    jsonObjectOf(…).entries + (f.maxSelections?.let { mapOf("maxSelections" to jsonOf(it)) } ?: emptyMap())
)
```

**Mengapa begini?**
- Kunci baru yang selalu ditulis (mis. `"maxSelections":null`) akan mengubah **dokumen lama** saat di-encode ulang →
  tes byte-stabil gagal dan migrasi draf jadi berisik. "Null = tidak ditulis" menjamin dokumen lama **byte-identik**.
- **Pembaca ketat** (`FieldParamWire.maxSelections` → `strictOptInt`): absen/null = bawaan, tapi `"maxSelections":"dua"`
  **ditolak**, bukan diam-diam jadi `null`. Ini Kontrak 4 variability: *tolak, jangan fallback senyap*.

### Blok G: Validator Usulan & FR-5

```kotlin
if (f.type != FieldType.ENUM && f.type != FieldType.MULTI_SELECT) { /* tolak options */ }
FieldType.MULTI_SELECT -> if (!MultiSelectValues.isValid(v, f.options, f.maxSelections)) { /* galat seed */ }
```

`checkStatus` **sudah** menolak `statusField` yang bukan `ENUM` — jadi FR-5 (MULTI_SELECT bukan status) terpenuhi
tanpa kode baru. A0 hanya menambah **tes yang mengunci** perilaku itu, supaya di masa depan ada yang tak sengaja
"melonggarkan" dan tes menangkapnya.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan | Alternatif | Mengapa Memilih Ini | Risiko Alternatif |
|---|---|---|---|
| **Kolom `TEXT[]` Postgres** | Tabel tautan (satu tabel per field) | Menjaga pola *satu field = satu kolom* di generator; opsi tetap dijaga DB | `SpecColumns` harus hasilkan banyak tabel + join/transaksi; terlalu besar untuk fitur "Sedang" |
| **`TEXT[]`** | `JSONB` | Tipe larik bawaan Exposed, tak butuh `exposed-json` + serializer di kode hasil generate | Kode hasil generate butuh dependensi baru yang belum dipakai generator mana pun |
| **`TEXT[]` + CHECK** | `TEXT` berisi JSON tanpa CHECK | Validitas dijaga database | Nilai dipalsukan jadi teks bebas → alasan tipe ini ada hilang |
| **String JSON array** | Pemisah koma/titik koma | Opsi boleh memuat koma/spasi apa pun; parser JSON sudah dipakai semua codec | Pemisah bentrok dengan opsi yang memuat tanda itu; butuh escaping sendiri |
| **Urut menurut `options`** | Pertahankan urutan klik | Nilai sama = string sama (byte-stabil) | Nilai identik menghasilkan string berbeda → codec tak bisa dibandingkan |
| **Jangan sentuh CRM (R5)** | Satukan dua kosakata sekarang | CRM sudah berjalan; manfaat penyatuan baru terasa setelah tipe ke-6 | Menyentuh CRM yang berjalan tanpa alasan mendesak |

**Catatan audit variabilitas.** `scripts/audit-variability.sh` melaporkan `enum class FieldType` sebagai "enum baru".
Ini **lolos Uji Variabilitas**: `FieldType` adalah kosakata **tertutup milik sistem** (renderer harus bisa menggambar
setiap anggotanya di semua vertikal), sedangkan yang **data** adalah nama field dan daftarnya (`options`). Laporan itu
bersifat informatif, tidak memblokir, dan alasannya dicatat di sini.

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls)

1. **Jebakan: `else ->` untuk menenangkan kompilator.**
   - *Kenapa bahaya*: Menambah `else` pada `when (FieldType)` mematikan pagar kompilator. Tipe ke-7 nanti akan
     jatuh diam-diam ke cabang `else`, bukan gagal build.
   - *Solusi kita*: semua `when` kosakata **tanpa `else`**. Entri baru = build gagal = daftar titik pendaftaran
     yang tak bisa terlewat.

2. **Jebakan: fallback senyap ke `TEXT` / bawaan.**
   - *Kenapa bahaya*: Membaca `"maxSelections":"dua"` lalu diam-diam jadi `null` mengubah data tanpa jejak;
     memetakan tipe tak dikenal ke `TEXT` memalsukan nilai.
   - *Solusi kita*: pembaca ketat (`strictOptInt`) menolak tipe salah; codec menolak nama tipe tak dikenal.

3. **Jebakan: menaruh parameter baru di tengah daftar argumen.**
   - *Kenapa bahaya*: pemanggilan positional lama tergeser dan **salah nilai tanpa error**.
   - *Solusi kita*: `maxSelections` ditaruh paling akhir dengan nilai bawaan `null`.

4. **Jebakan: menulis `"[]"` untuk kosong.**
   - *Kenapa bahaya*: dua bentuk kosong (`""` dan `"[]"`) membuat tiap pemakai harus menebak artinya.
   - *Solusi kita*: `"[]"` ditolak di `isValid`, dan `encode` mengembalikan `""` untuk pilihan kosong.

5. **Jebakan: percaya Exposed `array<String>` "pasti jalan" tanpa diuji.**
   - *Kenapa bahaya*: ini **pemakaian kolom larik pertama** di repo; teks SQL yang benar tak membuktikan binding driver benar.
   - *Solusi kita*: tes integrasi Postgres yang benar-benar membuat tabel, menulis/membaca, dan melanggar CHECK.

6. **Jebakan: mengira MULTI_SELECT boleh jadi status.**
   - *Kenapa bahaya*: status berpindah lewat transisi; atribut berlabel ganda tidak. Menaruhnya sebagai kolom kanban
     akan membingungkan papan.
   - *Solusi kita*: `checkStatus` menolak, dan tes mengunci perilaku itu (FR-5).

---

## 🧪 6. Bagaimana Membuktikan Kodingan Kita Bekerja?

- **Domain murni** (`PrototypeMultiSelectTest`): `""` sah, `"[]"` ditolak, elemen di luar opsi, duplikat, melebihi
  `maxSelections`, bukan JSON; `encode` deterministik; `maxSelections` pada tipe lain / di luar rentang ditolak.
- **Paritas kosakata** (`PrototypeFieldTypeSqlParityTest`, `PrototypeFieldTypeCodecParityTest`): mengiterasi
  `FieldType.entries` — tiap tipe punya pemetaan SQL, penulis Exposed, dan round-trip codec. `MULTI_SELECT` sekarang
  **keluar** dari daftar `unknownNames` (yang menolak tipe tak dikenal), sementara `CURRENCY`/`text` dll tetap ditolak.
- **Integrasi Postgres** (`PrototypeMultiSelectPgTest`): membuat tabel `TEXT[]` + CHECK, menulis/membaca lewat Exposed
  `array<String>`, dan memastikan CHECK menolak. **Gerbang DB `TEXT[]` wajib hijau sebelum Track B/C** — dan di sini
  hijau (`tests=1 skipped=0 failures=0`).

Cara menjalankan:

```bash
./gradlew :core:jvmTest :server:compileKotlin :app:shared:compileKotlinJvm \
          :app:shared:compileKotlinJs :app:shared:compileKotlinWasmJs
# gerbang DB (butuh Postgres scratch):
./gradlew :server:test --tests '*PrototypeMultiSelectPgTest*'
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: Tambahkan operasi `SetFieldMaxSelections` (pola `SetFieldWithTime`) yang menolak bila ada nilai
      seed melebihi batas baru. Itu sengaja **ditinggalkan** di Track A sisa — coba implementasikan dan tulis tesnya.
- [ ] **Tantangan 2**: Ganti `TEXT[]` menjadi `JSONB` + CHECK `?&`/`<@` di generator, **tanpa** mengubah
      `MultiSelectValues`. Buktikan kontrak nilai (string JSON kanonik) tetap sama sehingga hanya generator yang berubah.
- [ ] **Tantangan 3**: Buat kontrol chip pilih-ganda di `FieldInput` (Track C) untuk ≥ 2 konteks (form & sel tabel),
      dengan komponen dasar di `presentation/designsystem/` yang buta domain. Jalankan cek visual di pack non-garment.

---

## 🔭 Sisa yang Sengaja Ditinggalkan untuk Track B/C

A0 berhenti tepat di **bentuk + kawat**. Yang belum (sengaja):

- **Track A sisa**: operasi suntingan `SetFieldMaxSelections`, penyesuaian penuh `ProposalEdit.reconcileFor`,
  `FieldHint`/`ScreenSuggestionCodec` kartu, `DeterministicScreenProposer` (gaya kartu), tes paritas lengkap.
- **Track B (server)**: aturan katalog/prompt Koog "kapan `MULTI_SELECT` vs `ENUM`" — catatan tipe di
  `KoogDiscoveryFieldTypeVocabulary.note()` sudah ditambahkan (kompilasi-forced), tetapi **`promptRule`** belum
  diperbarui dan sengaja diserahkan ke B.
- **Track C (UI)**: kontrol chip pilih-ganda sesungguhnya. Cabang di `FieldInput`/`displayValue` saat ini
  **placeholder baca-saja** (ditandai komentar `A0 (TRD-FIELD-003)`).
