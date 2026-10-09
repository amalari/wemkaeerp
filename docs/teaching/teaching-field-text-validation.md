# 🎓 Modul Pembelajaran: Validasi Bentuk Teks — Email dan Telepon (C9, Irisan 2)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Satu sumber aturan validasi, parameter pada tipe yang ada, validasi di UI tanpa menduplikasi aturan, galat yang tidak memblokir pengetikan, jebakan `Char.isDigit()`, larangan heuristik nama field
> **Prasyarat**: Modul [teaching-field-number-format-currency-percent.md](teaching-field-number-format-currency-percent.md) (pola A0 → Track A/B/C) dan dasar Compose `KeyboardOptions`
> **Referensi Task**: `docs/plannings/PLAN-field-component-gaps.md` — C9, Irisan 2

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Kolom "Email pasien" dan "No. telepon" bertipe `TEXT` menerima apa saja: `budi@`, `08123 abc`, `-`. Beberapa minggu kemudian, mengirim pengingat gagal karena data tak terpakai — padahal kesalahannya terjadi saat mengetik, bukan saat mengirim.

**Analogi.** Seperti petugas loket yang memeriksa formulir sebelum dicap: bukan memutuskan apakah emailnya *benar-benar ada* (itu butuh mengirim surel), tetapi apakah *bentuknya* seperti email. Ia tidak menulis ulang isian Anda; ia hanya berkata "ini belum seperti alamat email".

**Hasil akhir.** `TEXT` punya parameter `validation`: `NONE`, `EMAIL`, atau `PHONE`. Penyimpanan tetap `TEXT`. Nilai disimpan **apa adanya**, dan hanya *bentuknya* yang diperiksa.

**Yang sengaja bukan cakupannya:** memastikan alamat itu aktif, memformat nomor menjadi `+62...`, atau menormalkan huruf besar-kecil. Itu perubahan data tanpa jejak.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0 — Parameter atau tipe baru?** Penyimpanan, urutan, dan filter sama dengan `TEXT`; hanya syarat nilai yang berbeda. Jadi parameter, bukan tipe `EMAIL`/`PHONE`.
2. **Langkah 1 — Letakkan aturan di satu tempat murni** (`TextValidations`), sebelum menyentuh validator, UI, atau generator.
3. **Langkah 2 — Kontrak A0:** enum `TextValidation`, `FieldSpec.validation`, invarian "hanya `TEXT`", kawat, dan route hasil scaffold.
4. **Langkah 3 — Track A sisa:** operasi suntingan `SetFieldValidation`, `FieldHint`.
5. **Langkah 4 — Track B:** katalog dan prompt (kapan memilih EMAIL/PHONE), penyunting modul menolak nilai salah.
6. **Langkah 5 — Track C:** keyboard yang sesuai, `isError`, pesan galat — memakai aturan core, bukan menulis ulang.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Aturan di satu tempat

```kotlin
enum class TextValidation { NONE, EMAIL, PHONE }

object TextValidations {
    fun isValid(validation: TextValidation, value: String): Boolean = when (validation) {
        TextValidation.NONE -> true
        TextValidation.EMAIL -> isEmail(value)
        TextValidation.PHONE -> isPhone(value)
    }
    private fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'
}
```

**Mengapa begini?**
- `FieldSpec.accepts`, validator usulan, `ProposalEdit`, route hasil scaffold, **dan UI** semuanya memanggil fungsi yang sama. Aturan yang ditulis dua kali akan berbeda suatu hari — dan perbedaannya baru ketahuan ketika UI bilang "sah" sedangkan server menolak.
- `when` tanpa `else` pada enum: validasi baru tanpa aturan **gagal kompilasi**.
- Lolos Uji Variabilitas: `TextValidation` adalah kosakata sistem untuk *memeriksa bentuk*, tidak berbeda per tenant atau industri. Yang berbeda per tenant (field mana yang berisi email) adalah data, dan itu ada di `FieldSpec`.

### Blok B: Jebakan digit

```kotlin
/** Hanya 0-9 ASCII; `Char.isDigit()` juga meloloskan digit Unicode lain. */
private fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'

private fun isPhone(value: String): Boolean {
    val body = if (value.startsWith('+')) value.substring(1) else value
    if (body.any { !isAsciiDigit(it) && it !in PHONE_SEPARATORS }) return false
    return body.count(::isAsciiDigit) in PHONE_MIN_DIGITS..PHONE_MAX_DIGITS
}
```

**Mengapa begini?**
- `Char.isDigit()` di Kotlin mengembalikan `true` untuk digit Unicode seperti `٣` (Arab-Indic). Nomor telepon yang lolos validasi tetapi berisi karakter itu tidak bisa dihubungi dan sulit dilacak. Aturan memakai `'0'..'9'` eksplisit.
- Aturan juga tanpa `Regex`: pemeriksaan eksplisit paling mudah dibaca dan sama perilakunya di semua target KMP.
- Rentang 8–15 digit: 15 adalah batas maksimum nomor internasional (E.164); 8 adalah batas bawah pilihan implementasi, bukan standar. Pemisah ` -()` diizinkan karena orang mengetik `(021) 555-0100`.

### Blok C: Validasi di UI tanpa menduplikasi aturan

```kotlin
fun FieldSpec.validationMessageFor(value: String): String? =
    if (type != FieldType.TEXT || value.isEmpty() || accepts(value)) null else validationErrorText(validation)

fun keyboardTypeFor(validation: TextValidation): KeyboardType = when (validation) {
    TextValidation.NONE -> KeyboardType.Text
    TextValidation.EMAIL -> KeyboardType.Email
    TextValidation.PHONE -> KeyboardType.Phone
}
```

**Mengapa begini?**
- UI memanggil `FieldSpec.accepts` dari core. Tidak ada regex kedua di `presentation`, jadi UI dan penyimpanan tidak bisa berselisih.
- Galat hanya muncul bila nilai **tidak kosong** dan tidak sah, dan **tidak memblokir pengetikan**: sambil mengetik `budi@` orang belum selesai. Nilai juga **tidak dinormalisasi** (tidak di-trim, tidak diubah huruf kecil).
- Pesan galat hanya ASCII (font Nunito tak punya glyph non-ASCII).
- Jalur simpan tetap menegakkan aturan: `PrototypeReducer` memanggil `field.accepts(...)` pada `Create` dan `SetField`, jadi UI yang longgar tidak bisa menyimpan nilai tak sah.

### Blok D: Mengubah validasi pada field yang sudah punya data

```kotlin
fun setValidation(screen: InteractiveScreen, op: SpecOp.SetFieldValidation): InteractiveScreen {
    val f = fieldOf(screen, op.entityId, op.field)
    require(f.type == FieldType.TEXT) { /* hanya TEXT */ }
    if (f.validation == op.validation) return screen
    return replace(screen, op.entityId, f.copy(validation = op.validation), "mengubah validasi")
}
```

**Mengapa begini?**
- Menjadikan kolom "Catatan" yang sudah berisi 40 baris sebagai `EMAIL` membuat baris-baris itu tak sah. Operasi **ditolak** dengan pesan yang menyebut jumlah baris dan satu contoh nilai, bukan mengosongkan atau memperbaikinya diam-diam.
- Hanya `TEXT`, bukan `LONG_TEXT`: isi panjang/multibaris bukan tempat email atau telepon.

### Blok E: Route hasil scaffold yang bergantung pada core

**Keputusan yang menyimpang dari brief awal.** Brief meminta route hasil generate memuat aturan tertanam. Agent memilih route memanggil `FieldSpec.accepts` dari core (`textProblem`), dengan alasan `PrototypeReducer` sudah bergantung pada `SPEC` dari core, dan kode tertanam akan menduplikasi aturan tanpa tes yang mengompilasinya. **Konsekuensinya:** route hasil scaffold terikat pada versi core. Bila ingin mandiri, ubah isi `textProblem` di `SpecRoutesWriter.routesFile`.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa dipilih | Risiko alternatif |
|---|---|---|---|
| Parameter `validation` pada `TEXT` | Tipe `EMAIL`, `PHONE` | Penyimpanan identik; satu tipe teks | Dua tipe baru di puluhan titik pendaftaran |
| Simpan apa adanya | Normalisasi (trim, huruf kecil, `+62`) | Tidak ada perubahan data tanpa jejak | Pengguna melihat nilai yang tak pernah diketik |
| Aturan di `TextValidations` | Regex di tiap lapisan | Satu sumber kebenaran | UI dan server berselisih |
| Enum tertutup `NONE/EMAIL/PHONE` | Regex bebas dari model | Model tak bisa menyelundupkan aturan | Validasi tak terduga dari keluaran LLM |
| Tidak ada heuristik nama field | Field bernama "email" otomatis EMAIL | Heuristik satu industri dilarang aturan variabilitas | Perilaku berbeda tanpa sumber data |

---

## ⚠️ 5. Jebakan Pemula (yang benar-benar terjadi di sini)

1. **`Char.isDigit()`.** Meloloskan digit Unicode non-ASCII; pakai `'0'..'9'`.
2. **Menulis aturan dua kali.** Godaan terbesar: regex cepat di Composable "supaya langsung". Selalu panggil `accepts`.
3. **Memblokir pengetikan.** Menolak karakter di tengah jalan membuat orang tak bisa mengetik `budi@`. Tandai galat, jangan menahan.
4. **Menormalisasi diam-diam.** `trim()` terlihat ramah, tetapi mengubah data tanpa jejak.
5. **Heuristik nama field.** Mengisi `validation = EMAIL` karena namanya `email`. Tidak ada sumber data yang sah di narasi atau pack; biarkan `NONE` dan laporkan.
6. **`FieldHint` tertinggal.** Petunjuk pack harus membawa `validation` agar tabel dari petunjuk tidak kehilangannya. Saat menambah parameter field, cari semua jalur yang membangun `FieldSpec`.
7. **Boolean/teks salah-tipe di JSON.** `"validation": 5` harus ditolak, bukan dibaca sebagai `NONE` (pembaca ketat `strictOptString`).

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

- **Core:** `PrototypeTextValidationTest` dan `PrototypeTextValidationCodecTest` mengiterasi `TextValidation.entries` (contoh sah dan tak sah tiap entri), invarian per tipe (`LONG_TEXT` ditolak), round-trip tiga codec, penolakan `email`/`URL`/kosong, SQL tetap `TEXT`, dan pack klinik/bordir. `FieldParamOpsTest` untuk operasi suntingan.
- **UI:** `PrototypeDateTimeTextValidationParityTest` — keyboard dan pesan galat tiap `TextValidation`, `TextValidations.sample` tidak menghasilkan galat, konteks form pada entitas klinik non-garment.
- **Server:** `KoogDiscoveryDateTimeValidationTest` — paritas katalog dan prompt, usulan EMAIL/PHONE lolos validator, seed tak sah ditolak (surel tanpa `@`), parameter pada tipe salah ditolak, editor menolak 10 kasus.

### Yang BELUM terverifikasi (jangan dianggap selesai)

- **Cek visual belum dilakukan:** pesan galat merah muncul saat nilai tak sah dan hilang saat sah atau kosong; di sel tabel dan `InlineRowEditor` pesan tidak merusak lebar kolom.
- **Keyboard `Email`/`Phone`** di Android dan iOS tidak dicoba (hanya logika pemetaan yang dites; tidak ada tes yang merender Compose).
- **Perilaku model sungguhan:** apakah agent memilih `EMAIL`/`PHONE` dengan benar.
- **Android tidak dikompilasi.**
- **CRM** tidak diubah; kosakata CRM terpisah (D2).

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Tambah `TextValidation.URL`. Daftar titik yang dipaksa kompilator dan titik yang *tidak* dipaksa tetapi tetap harus disentuh (katalog agent, prompt, keyboard).
- [ ] **Tantangan 2**: Tulis tes yang memastikan nomor berisi digit Arab-Indic (`٠١٢٣٤٥٦٧٨٩`) ditolak `PHONE`. Apa yang akan terjadi pada tes itu bila `isAsciiDigit` diganti `Char.isDigit()`?
- [ ] **Tantangan 3**: Seorang tenant ingin nomor telepon hanya boleh diawali `+62`. Apakah itu `TextValidation` baru atau data tenant? Jelaskan dengan Uji Variabilitas.
