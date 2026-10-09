# 🎓 Modul Pembelajaran: Angka Berformat — Mata Uang dan Persen (C4, Irisan 2)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Parameter vs tipe baru, "simpan polos, tampilkan berformat", mata uang sebagai data per field, codec yang menolak (bukan fallback senyap), fungsi murni untuk format tampilan, kerja paralel A0 → Track A/B/C
> **Prasyarat**: Tahu `data class` Kotlin, enum, dan gambaran wizard discovery (usulan layar → `FieldSpec` → generator SQL)
> **Referensi Task**: `docs/plannings/PLAN-field-component-gaps.md` — C4, Irisan 2, keputusan D3

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Field `NUMBER` hanya angka polos: `250000`. Untuk kolom "Tarif" atau "Diskon", pengguna melihat `250000` dan `12.5` tanpa tahu itu rupiah atau persen. Cara tergoda untuk memperbaikinya: menambah tipe `CURRENCY` dan `PERCENT`. Itu salah arah, karena penyimpanan, filter, urutan, dan agregat kedua "tipe" itu **identik** dengan angka.

**Analogi.** Angka di kolom itu seperti tulisan di struk kasir. Struk menyimpan angka `250000`; yang berubah hanya cetakannya ("Rp 250.000"). Kalau tiap cara cetak menjadi jenis struk baru, kita punya lima mesin cetak untuk satu isi yang sama.

**Hasil akhir.** `NUMBER` punya parameter `format` (`PLAIN`, `CURRENCY`, `PERCENT`) dan, khusus `CURRENCY`, parameter `currencyCode` (`IDR`, `USD`). Database tetap menyimpan satu kolom `NUMERIC(18,4)`. Layar menampilkan `Rp 12.000`, `USD 1.500`, atau `12,5 %`.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0 — Tentukan: tipe baru atau parameter?** Pakai tangga keputusan `field-component-rules` Kontrak 2. Beda *tampilan* saja → parameter (`format`). Beda *cara menyimpan/memvalidasi/mengurutkan* → tipe baru. Di sini penyimpanannya sama, jadi parameter.
2. **Langkah 1 — Kontrak di `core` (gerbang A0).** `enum NumberFormat` dan `FieldSpec.format`, plus invarian di `init`: format selain `PLAIN` hanya untuk `NUMBER`. Tulis juga **arti nilai** di KDoc, bukan hanya bentuknya.
3. **Langkah 2 — Putuskan sumber kode mata uang** (lihat §4). Ini keputusan yang menambah satu parameter lagi, jadi A0 diamandemen: `currencyCode`.
4. **Langkah 3 — Kawat (codec).** Kunci `format` dan `currencyCode` di tiga codec; tolak nilai tak dikenal.
5. **Langkah 4 — Track A sisa, B, C paralel**, hanya bergantung pada kontrak:
   - **A (`core`)**: operasi suntingan `SetFieldFormat`, kode bawaan dari pack, usulan deterministik, tes paritas.
   - **B (`server`)**: katalog agent, prompt, dan pembaca ketat di penyunting modul.
   - **C (`app/shared`)**: pemformat murni, kontrol masukan ber-prefix/suffix, tampilan sel dan kartu.
6. **Langkah 5 — Integrasi:** merge berurutan A → B → C, lalu kompilasi semua target, tes, audit variabilitas, cek visual.

> **Mental model:** *Simpan polos, tampilkan berformat.* Tidak ada bagian penyimpanan yang boleh tahu bahwa angka itu "rupiah".

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Kontrak dan arti nilai

```kotlin
enum class NumberFormat { PLAIN, CURRENCY, PERCENT }

data class FieldSpec(
    // ...
    val format: NumberFormat = NumberFormat.PLAIN,
    val currencyCode: String? = null,
) {
    init {
        require(type == FieldType.NUMBER || format == NumberFormat.PLAIN) { /* format hanya untuk NUMBER */ }
        if (format == NumberFormat.CURRENCY) {
            require(currencyCode != null && CurrencyCode.isValid(currencyCode)) { /* wajib tiga huruf besar */ }
        } else {
            require(currencyCode == null) { /* tidak boleh membawa kode */ }
        }
    }
}
```

**Mengapa begini?**
- Invarian "kode wajib **tepat** bila CURRENCY, null selain itu" mencegah dua keadaan setengah jadi: uang tanpa mata uang, dan persen yang membawa kode `IDR` entah kenapa.
- Parameter ditambah di **akhir** `FieldSpec`, supaya argumen posisional lama tidak bergeser.
- KDoc `NumberFormat` menulis **arti** nilai: `PERCENT` = angka persen apa adanya (`12.5` berarti 12,5%, *bukan* pecahan `0.125`). Tanpa kalimat ini, Track B dan C bisa memakai skala berbeda dan tidak ada tes yang menangkapnya.

### Blok B: Mata uang adalah data, bukan daftar di kode

```kotlin
object CurrencyCode {
    private val SHAPE = Regex("[A-Z]{3}")
    fun isValid(code: String): Boolean = SHAPE.matches(code)
}
```

**Mengapa begini?**
- Tiga opsi yang dipertimbangkan: **IDR tetap**, **satu mata uang per pack**, **per field**. Dipilih per field: satu entitas bisa punya harga IDR dan harga ekspor USD (**Uji Variabilitas**: bisa beda per tenant, industri, dan bahkan per field).
- Yang dijaga hanya **bentuk**, bukan daftar mata uang. Daftar di kode akan basi; mata uang adalah data.
- Kode yang salah bentuk (`idr`, `RP`, `RUPIAH`) **ditolak**, tidak diubah diam-diam menjadi `IDR`.

### Blok C: Kode bawaan datang dari pack, tetapi setelah itu milik field

```kotlin
// DomainPack
val defaultCurrencyCode: String = DEFAULT_CURRENCY_CODE   // "IDR"
init { require(CurrencyCode.isValid(defaultCurrencyCode)) { /* gagal saat pack dibangun */ } }

// DeterministicScreenRoles
"tagihan" -> listOf(FieldProposal("jumlah", "Jumlah", FieldType.NUMBER,
    format = NumberFormat.CURRENCY, currencyCode = defaultCurrencyCode))
```

**Mengapa begini?**
- Pack hanya **sumber nilai awal** saat usulan dibuat. Setelah masuk dokumen, kode itu milik field: mengubah bawaan pack tidak mengubah usulan yang sudah tersimpan. Ini prinsip "template disalin, dokumen membeku".
- Pilihan format di proposer berasal dari **peran** slot (`tagihan`), bukan dari nama field. Tidak ada tebakan "kalau namanya `harga` berarti uang" — itu heuristik satu industri yang dilarang aturan variabilitas.
- Label "Jumlah (Rp)" diubah menjadi "Jumlah", karena "(Rp)" salah untuk pack USD.

### Blok D: Format tampilan sebagai fungsi murni

```kotlin
fun formatNumberForDisplay(stored: String, format: NumberFormat, currencyCode: String?): String { /* "12000" -> "Rp 12.000" */ }
fun parseNumberInput(text: String, format: NumberFormat): String? { /* "Rp 1.500.000" -> "1500000" */ }
fun normalizeNumberTyping(input: String, format: NumberFormat): String? { /* "12," -> "12." */ }
```

**Mengapa begini?**
- Semuanya common Kotlin: tanpa `java.text`, `Locale`, atau `String.format`, supaya hasilnya sama di JVM, Android, Wasm, dan JS.
- Hanya karakter ASCII. Font Nunito di proyek ini tidak punya glyph non-ASCII, jadi simbol seperti `€` tampil sebagai kotak. Itu sebabnya mata uang non-IDR diawali kodenya (`USD 1.500`), bukan simbolnya.
- Desimal tidak dibulatkan: hanya nol di ekor yang dibuang (`12000.0000` menjadi `12.000`). Menampilkan "pembulatan" mengubah data tanpa jejak.
- Nilai yang bukan angka (data lama) ditampilkan apa adanya, tidak crash dan tidak dikosongkan.
- `onValueChange` selalu menerima **string simpan** (titik desimal, tanpa ribuan). Tempelan `Rp 1.500.000` diurai kembali menjadi `1500000`.

### Blok E: Penyunting modul yang dulu membuang parameter diam-diam

```kotlin
raw is JsonValue.Str -> NumberFormat.entries.firstOrNull { it.name == raw.value.uppercase() }
    ?: error("field.format '${raw.value}' tidak dikenal; wajib salah satu ...")
// ...
if (format == NumberFormat.CURRENCY && (code == null || !CurrencyCode.isValid(code))) error("...")
```

**Mengapa begini?**
- Sebelum C4, `KoogModuleEditor` membaca field model tanpa `format`. Model yang meminta uang akan mendapat angka polos, **tanpa pesan apa pun**. Itu persis "fallback senyap = data berubah".
- Sekarang tiap kasus ditolak dengan pesan berpath (`field.format ...`), sehingga model atau pengguna melihat alasannya.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa dipilih | Risiko alternatif |
|---|---|---|---|
| Format = parameter `NUMBER` (D3) | Tipe `CURRENCY` dan `PERCENT` | Penyimpanan, filter, urutan identik | Dua tipe baru yang harus didaftarkan di puluhan titik (kosakata field tersebar di domain, codec, generator, agent, dan UI), tanpa manfaat |
| `currencyCode` per field | IDR tetap / per pack | Satu entitas bisa memakai dua mata uang | Tenant ekspor dipaksa mata uang salah |
| Kode hanya dijaga bentuknya | Daftar tertutup mata uang | Daftar akan basi | Mata uang baru butuh rilis kode |
| `PERCENT` = angka apa adanya | Pecahan `0.125` | Tidak ada skala tersembunyi; SQL tak perlu tahu format | Agregat dan filter harus tahu mengalikan 100 |
| Format murni common Kotlin | `java.text.NumberFormat` | Sama di 5 target | Perbedaan perilaku antar platform sulit dilacak |

---

## ⚠️ 5. Jebakan Pemula (yang benar-benar terjadi di sini)

1. **Mengira "tipe baru" bila hanya tampilannya berbeda.** Gunakan tangga keputusan Kontrak 2.
2. **Codec yang jatuh diam-diam.** Codec lama `InteractiveScreenCodec` dan `SpecOpCodec` membaca `"format": 5` sebagai `PLAIN`. Diperbaiki dengan pembaca ketat (`strictOptString`) dan tes penolakan. Kunci *absen* tetap berarti bawaan, nilai *salah tipe* ditolak.
3. **Label yang menyisipkan asumsi.** "Jumlah (Rp)" tertanam di label; saat mata uang menjadi data, labelnya ikut salah.
4. **Heuristik nama field.** Tergoda membuat `harga` otomatis jadi uang. Ditolak: tidak ada sumber data yang sah.
5. **Simbol Unicode.** `€` dan `¥` tampil sebagai kotak di Nunito. Tulis hanya ASCII.
6. **`FieldHint` tertinggal.** Tabel dari petunjuk pack tampil `PLAIN` karena `FieldHint` belum membawa `format`; ditutup di pekerjaan C6/C9. Saat menambah parameter field, cek semua jalur yang membangun `FieldSpec`, bukan hanya yang jelas.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

- **Core:** `NumberFormatParityTest` mengiterasi `NumberFormat.entries`: kolom SQL identik `NUMERIC(18,4)` untuk semua format dan **tidak ada kolom kode mata uang**; round-trip di tiga codec; penolakan nilai tak dikenal; pack klinik, bordir, dan USD (bukti bawaan pack mengalir ke usulan).
- **Server:** `KoogDiscoveryNumberFormatTest` — paritas katalog dan prompt dua arah terhadap `NumberFormat.entries`, usulan IDR/USD/PERCENT lolos validator dan round-trip, 6 kasus penolakan editor.
- **UI:** `NumberFormattingTest` — IDR, USD, PERCENT, kosong, data lama, desimal, angka besar, negatif, dan **round-trip** `parse(format(x)) == x`.

### Yang BELUM terverifikasi (jangan dianggap selesai)

- **Cek visual belum dilakukan.** Tidak ada tes yang merender Compose. Yang perlu dilihat manusia: prefix `Rp`/`USD` dan suffix `%` di dalam kotak masukan tidak terpotong; kolom sempit (prefix `USD` memakan lebar); kartu kanban bertipe `NUMBER`; lebar ~1280dp dan ~360dp.
- **Pemisah ribuan saat mengetik tidak ada.** Saat mengetik tampil `12000`; hanya mode baca berpemisah. Pemisah langsung butuh `VisualTransformation`.
- **Nilai antara `12.`** (titik di ujung) belum dicek apakah ditolak saat simpan.
- **Android tidak dikompilasi** (SDK tidak terpasang di mesin pengembangan ini).
- **Perilaku model sungguhan** tidak diuji: apakah model memilih `CURRENCY` dan kode yang benar.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Tambah `NumberFormat.ACCOUNTING` (negatif dalam kurung). Daftar semua titik yang *dipaksa* kompilator, lalu titik yang *tidak* dipaksa tetapi tetap perlu disentuh (petunjuk: baca teks katalog agent dan prompt).
- [ ] **Tantangan 2**: Rancang cara `FieldHint` dari pack menyatakan mata uang tanpa heuristik nama field. Dari mana data itu seharusnya datang?
- [ ] **Tantangan 3**: Buat `VisualTransformation` yang menampilkan pemisah ribuan saat mengetik tanpa mengubah string yang dikirim ke `onValueChange`. Apa yang terjadi pada posisi kursor?
