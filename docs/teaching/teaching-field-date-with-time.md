# 🎓 Modul Pembelajaran: Tanggal + Jam — `DATE` dengan `withTime` (C6, Irisan 2)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Waktu dinding vs zona waktu, parameter yang mengubah penyimpanan, validasi nilai di satu tempat, operasi suntingan yang menolak (bukan mengonversi), pemilih waktu buatan sendiri di Compose Multiplatform
> **Prasyarat**: `kotlinx.datetime` (`LocalDate`, `LocalDateTime`), dasar Compose, dan modul [teaching-field-number-format-currency-percent.md](teaching-field-number-format-currency-percent.md) untuk pola A0 → Track A/B/C
> **Referensi Task**: `docs/plannings/PLAN-field-component-gaps.md` — C6, Irisan 2, keputusan D7

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Jadwal praktik klinik, janji temu, dan jam masuk antrean punya **jam**, bukan hanya tanggal. Field `DATE` hanya menyimpan `2026-10-08`. Orang menyiasatinya dengan `TEXT` bebas ("8 Okt jam 2 siang") — tak bisa diurutkan, tak bisa divalidasi, dan setiap orang menulis dengan gaya berbeda.

**Analogi.** Tanggal-saja seperti halaman kalender dinding; tanggal-jam seperti catatan di buku janji: "Kamis, 14.30". Penting, jam 14.30 itu **jam yang tertulis di dinding klinik**, bukan titik absolut di planet ini. Klinik di Medan dan Makassar menulis 14.30 untuk dua momen yang berbeda, dan itu memang yang dimaksud.

**Hasil akhir.** `DATE` punya parameter `withTime`. Bila `true`, nilainya `2026-10-08T14:30` dan layar menampilkan pemilih tanggal lalu pemilih jam.

**Catatan penting: ini mengubah penyimpanan, bukan hanya tampilan.** Berbeda dengan `format` pada `NUMBER` (C4), kolom database berpindah dari `DATE` ke `TIMESTAMP`. Karena itu `withTime` tidak boleh diperlakukan seringan "format".

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0 — Putuskan semantik waktu dulu**, sebelum satu baris kode: waktu dinding tanpa zona, atau titik absolut (UTC)? Keputusan ini menentukan kolom SQL, parser, dan UI.
2. **Langkah 1 — Aturan nilai di satu tempat** (`DateFieldValues`), bukan disalin di validator, reducer, dan generator.
3. **Langkah 2 — Kontrak A0:** `FieldSpec.withTime`, invarian "hanya DATE", kawat di tiga codec, SQL/Exposed, route hasil scaffold.
4. **Langkah 3 — Track A sisa:** operasi suntingan pasca-pembuatan (`SetFieldWithTime`), `FieldHint`.
5. **Langkah 4 — Track B:** katalog dan prompt agent, penyunting modul menolak nilai salah.
6. **Langkah 5 — Track C:** `ClayDateTimePicker` + `ClayTimePickerDialog`, dipasang lewat satu pintu `FieldInput`.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Aturan nilai tunggal

```kotlin
object DateFieldValues {
    private const val DATE_TIME_LENGTH = 16          // TTTT-BB-HHTJJ:MM
    private const val SEPARATOR_INDEX = 10

    fun isValid(value: String, withTime: Boolean): Boolean =
        if (withTime) isValidDateTime(value) else runCatching { LocalDate.parse(value) }.isSuccess

    private fun isValidDateTime(value: String): Boolean =
        value.length == DATE_TIME_LENGTH && value[SEPARATOR_INDEX] == 'T' &&
            runCatching { LocalDateTime.parse(value) }.isSuccess
}
```

**Mengapa begini?**
- `LocalDateTime.parse` sendiri menerima `2026-10-08T14:30:15`, pecahan detik, bahkan bentuk lain. Pemeriksaan `length == 16` dan huruf `T` di posisi 10 yang membuat bentuknya **tepat satu**: tanpa detik, tanpa `Z`, tanpa offset. Kontrak yang ketat membuat codec byte-stabil dan tes bisa membandingkan string.
- Satu fungsi dipakai `FieldSpec.accepts`, validator usulan, `ProposalEdit`, dan route scaffold. Kalau aturan ditulis di empat tempat, keempatnya akan berbeda suatu hari.
- Nilai tanggal-saja pada field `withTime` **ditolak**, dan sebaliknya. Tidak ada koersi diam-diam (mis. menambah `T00:00`).

### Blok B: Mengapa `TIMESTAMP`, bukan `TIMESTAMPTZ`

```kotlin
FieldType.DATE -> (if (field.withTime) "TIMESTAMP" else "DATE") + notNull      // SpecColumns
FieldType.DATE -> if (c.field.withTime) "datetime($n)" else "date($n)"         // SpecPostgresWriter (Exposed)
```

**Mengapa begini?**
- `TIMESTAMPTZ` menyimpan titik absolut dan mengonversi zona saat dibaca. Untuk "jadwal 14.30 di klinik ini", konversi itu justru **merusak** maksud: pengguna di zona lain melihat jam yang berbeda dari yang diketik.
- `TIMESTAMP` tanpa zona menyimpan persis jam dinding yang diketik. Konsekuensi yang sengaja diterima: field ini tidak cocok untuk momen lintas zona (mis. log kejadian server). Kalau itu kebutuhannya, itu keputusan baru, bukan `withTime`.

### Blok C: Operasi suntingan yang menolak

```kotlin
fun setWithTime(screen: InteractiveScreen, op: SpecOp.SetFieldWithTime): InteractiveScreen {
    val f = fieldOf(screen, op.entityId, op.field)
    require(f.type == FieldType.DATE) { /* hanya DATE */ }
    if (f.withTime == op.withTime) return screen
    return replace(screen, op.entityId, f.copy(withTime = op.withTime), "mengubah waktu (jam)")
}
// replace(): bila ada baris seed tak kosong yang tak lolos bentuk baru -> require gagal, layar tak berubah
```

**Mengapa begini?**
- Mengubah `withTime` pada field yang sudah punya data membuat nilai lama tak sah (`2026-10-08` bukan tanggal-jam). Pilihannya: konversi (`T00:00`), buang, atau tolak. **Konversi mengarang jam; membuang menghapus data.** Keduanya keputusan manusia, jadi operasi ditolak dengan pesan yang menyebut jumlah baris dan satu contoh nilai.
- Perbandingannya: `SetFieldFormat` (C4) tidak pernah menyentuh nilai, jadi tidak perlu menolak. `SetFieldWithTime` perlu — itu sebabnya dipisah jadi operasi sendiri dan `FieldParamOps` sendiri.

### Blok D: Kunci yang bertipe salah tidak boleh menjadi `false`

```kotlin
fun JsonValue.Obj.strictBoolean(key: String, default: Boolean): Boolean = when (val v = this[key]) {
    null, JsonValue.Null -> default
    is JsonValue.Bool -> v.value
    else -> throw IllegalArgumentException("Bidang '$key' harus boolean (true/false).")
}
```

**Mengapa begini?**
- `JsonValue.Obj.boolean(key)` mengembalikan `null` untuk tipe salah, lalu pemanggil memakai `?: false`. Jadi `"withTime": "ya"` diam-diam menjadi `false`, dan field tanggal-jam tersimpan sebagai tanggal-saja. Itu bug yang tidak meledak, hanya mengubah data.
- Kunci **absen** tetap berarti bawaan (kompatibilitas draf lama); kunci **ada tapi salah tipe** ditolak.

### Blok E: Pemilih jam sebagai stepper, bukan `TimePicker` Material

```kotlin
internal fun stepCyclic(current: Int, delta: Int, modulus: Int): Int = ((current + delta) % modulus + modulus) % modulus
// 23 + 1 jam = 0 ; 0 - 1 jam = 23
```

**Mengapa begini?**
- `TimePicker` Material berbeda bentuk dan perilaku antar target (dial di Android, input di web) dan mewarnai ulang di luar token Clay. Stepper dari `ClayButton`/`ClayCard` berperilaku sama di lima target, bisa diketuk tanpa keyboard, dan **nilainya selalu sah**: tidak ada teks setengah ketik yang harus ditangani.
- `% modulus + modulus` mengatasi hasil negatif pada `%` Kotlin. Tanpa itu `0 - 1` menghasilkan `-1`, bukan `23`.
- Komponen `designsystem/` buta domain: ia mengulang aturan tanggal-jam sendiri (`parseIsoDateTimeOrNull`), dan **tes paritas** di sisi `discovery` menjaga agar keduanya sama dengan `DateFieldValues`, tanpa mengimpor domain ke design system.
- Alurnya dua langkah: kalender (`ClayDatePickerDialog` dipakai ulang, tidak disalin — Aturan Tiga Kali), lalu dialog jam dengan tombol Kembali dan Pilih.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa dipilih | Risiko alternatif |
|---|---|---|---|
| Parameter `withTime` pada `DATE` | Tipe `DATETIME` | Sejajar CRM `DateField(withTime)`; satu tipe tanggal | Dua tipe tanggal di kosakata |
| Waktu dinding, `TIMESTAMP` | `TIMESTAMPTZ`/UTC | Jam yang diketik = jam yang tersimpan | Jam berubah menurut zona pembaca |
| Bentuk tepat `TTTT-BB-HHTJJ:MM` | Terima semua ISO | String kanonik, byte-stabil | Dua nilai berbeda untuk momen sama |
| Tolak perubahan bila ada data tak sah | Konversi atau buang | Tidak mengarang jam / menghapus data | Data berubah tanpa jejak |
| Stepper buatan sendiri | `TimePicker` Material | Seragam di 5 target, nilai selalu sah | Perilaku berbeda per platform |

---

## ⚠️ 5. Jebakan Pemula (yang benar-benar terjadi di sini)

1. **Parser yang terlalu longgar.** `LocalDateTime.parse` menerima lebih banyak daripada kontrak kita. Selalu tambah pemeriksaan bentuk di depannya.
2. **Boolean salah-tipe yang menjadi `false`.** Lihat Blok D. Gunakan pembaca ketat untuk parameter baru.
3. **Format CRM yang dikira ada.** CRM `DateField(withTime)` hanya menyimpan flag-nya; nilainya tetap `LocalDate` (CRM belum punya format simpan jam). Jangan berasumsi CRM bisa dipakai sebagai rujukan format.
4. **Memakai `Char.isDigit()` / `Regex` sembarangan** di kode `core` yang harus jalan di semua target — gunakan pemeriksaan eksplisit.
5. **Route scaffold yang sudah ter-check-in.** `LayananChangeRequestRoutes` memuat `DATE_FIELDS`/`dateProblem` bentuk lama. Aman selama specnya hanya `DATE` polos, tetapi jangan menyunting file hasil generate dengan tangan; generate ulang.
6. **Tampilan yang bocor.** Kartu kanban menampilkan nilai mentah `2026-10-08T14:30` dengan huruf `T`; nilai tampil seharusnya `2026-10-08 14:30` (`displayValue`). Setiap konteks yang menggambar `DATE` perlu dicek, bukan hanya form.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

- **Core:** `PrototypeDateWithTimeTest` dan `PrototypeDateWithTimeCodecTest` — nilai sah/tak sah di kedua mode, penolakan detik/`Z`/spasi, invarian `withTime` hanya DATE (termasuk `LONG_TEXT`), teks SQL `TIMESTAMP` dan Exposed `datetime`, round-trip tiga codec, pack bordir dan klinik; `FieldParamOpsTest` — operasi sah dan ditolak, seed campuran.
- **UI:** `ClayDateTimeValuesTest` — 00:00 dan 23:59, jam 24, menit 60, 29 Februari bukan kabisat, round-trip 24 × 60 menit, langkah melingkar, paritas dengan `DateFieldValues.isValid`.
- **Server:** `KoogDiscoveryDateTimeValidationTest` — editor menolak `withTime` non-boolean atau pada tipe selain DATE; usulan klinik lolos validator dan round-trip.

### Yang BELUM terverifikasi (jangan dianggap selesai)

- **Cek visual belum dilakukan.** Dialog jam di ~1280dp dan ~360dp: baris menit berisi empat tombol (`-5`, `-`, `+`, `+5`) tidak boleh terpotong; kotak pemicu, tombol silang untuk mengosongkan, dan mode `enabled = false`.
- **Perilaku di Postgres sungguhan** (kolom `TIMESTAMP`, Exposed `datetime`) tidak dijalankan; yang diuji hanya teks SQL/Exposed yang dihasilkan.
- **Tidak ada tes yang mengompilasi kode route hasil generate**; hanya teksnya.
- **Android tidak dikompilasi.**
- **CRM** belum memakai picker baru (`DateField(withTime)` masih kolom teks).
- **D7 hanya terpenuhi sebagian.** Plan D7 merekomendasikan `ClayTimePicker` **mandiri** (format `JJ:MM`, mis. untuk jam operasional) selain `ClayDateTimePicker`. Yang dibuat hanyalah `ClayTimePickerDialog`, dipakai *di dalam* `ClayDateTimePicker`; tidak ada komponen waktu-saja yang berdiri sendiri, dan tipe `TIME` (jam tanpa tanggal) tidak ada di kosakata. Kebutuhan "jam operasional" belum terlayani. Itu keputusan terbuka: tipe baru, atau komponen yang dibuat lebih dulu tanpa tipe.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Rancang format simpan jam untuk CRM `DateField(withTime)`. Apakah mengikuti aturan prototype? Apa yang terjadi pada nilai `LocalDate` yang sudah tersimpan?
- [ ] **Tantangan 2**: Seorang pengguna butuh "tenggat 17.00 WIB" yang tidak bergeser menurut zona pembaca, dan "waktu kejadian" yang harus absolut. Apakah keduanya bisa diwakili `withTime`? Jelaskan batasnya.
- [ ] **Tantangan 3**: Tulis tes yang gagal bila `ClayDateTimeValues` dan `DateFieldValues` menerima nilai yang berbeda. Mengapa tes ini tinggal di `discovery`, bukan di `designsystem`?
