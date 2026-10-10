# 🎓 Modul Pembelajaran: Tipe Field `TIME` pada Kosakata Prototype (C6)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Closed System Vocabulary, Uji Variabilitas (kode vs data), Single Source of Truth nilai field, Parity Test, Kode Generator (SQL/Exposed/Route)
> **Prasyarat**: Membaca `.claude/rules/field-component-rules.md` (Kontrak 1–8), paham struktur `core/` vs `app/shared/` vs `server/`
> **Referensi Task**: `docs/plannings/PLAN-field-component-gaps.md` C6/D7

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah Nyata**: Pack prospek sering mencatat **jam dinding murni** — jam operasional toko, jadwal shift workshop, jam buka/tutup klinik. Sebelum C6, satu-satunya jalan adalah memalsukannya jadi `TEXT` bebas (`"9 pagi"`, `"09:30:15"`, `"pukul 9"`) — data yang tidak bisa difilter (`WHERE jam_mulai >= '09:00'`), tidak bisa diurutkan benar, dan bentuknya berbeda di tiap baris. Menambahkan tipe `TIME` setengah hati (hanya di UI, atau hanya di SQL) justru lebih berbahaya: komponen input ada tapi generator kode dan agent tidak mengenalnya — *komponen mati* menurut kosakata platform.
- **Analogi Sederhana**: Kosakata tipe field itu seperti **daftar pil resmi di apotek**. Menjual pil baru tanpa mencatatnya di sistem resep (validator), label (generator SQL), dan katalog apoteker (katalog agent) berarti pil itu tidak bisa diresepkan siapa pun — meski fisiknya ada di rak.
- **Hasil Akhir yang Diharapkan**: `FieldType.TIME` adalah anggota penuh kosakata prototype: tervalidasi di domain, di codec, di validator usulan; terpetakan ke kolom SQL `TIME` + kolom Exposed `time()` + gerbang route; dikenal agent discovery; dan dirender `ClayTimePicker` di seluruh konteks UI (form, tabel inline, kanban detail).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Kunci mental model: **tipe field adalah kosakata tertutup milik sistem** (lolos Uji Variabilitas Kontrak 1 karena *bentuk simpan*-nya sama di semua industri — bukan konsep bisnis yang bervariasi per tenant). Konsekuensinya, menambah anggota = **menyentuh semua titik pendaftaran**, dan kompilator yang menjaga daftarnya (Kontrak 6: `when` tanpa `else`).

1. **Langkah 0: Aturan nilai dulu, bukan enum-nya.** Buat `TimeFieldValues` (pola `DateFieldValues`): satu objek murni pemegang bentuk `JJ:MM`. Semua gerbang lain *mendelegasi* ke sini — bukan menyalin aturannya.
2. **Langkah 1: Enum + `FieldSpec` (`EntitySpec.kt`).** Tambah `TIME` ke enum, tulis KDoc semantik (termasuk alasan lolos Uji Variabilitas), tambah cabang `accepts`. Kompilasi → kompilator menunjuk semua `when` yang pecah.
3. **Langkah 2: Perbaiki pecahan domain** (`ChangeWidgetOp` gaya kartu, `ProposalEdit` accepts/sample, `ProposalEntityRules` seed) — semua dengan cabang eksplisit, tanpa `else`.
4. **Langkah 3: Generator handoff** (`SpecColumns` SQL, `SpecPostgresWriter` Exposed/read/write, `SpecRoutesWriter` gerbang route fail-closed).
5. **Langkah 4: Kosakata agent** (`KoogDiscoveryFieldTypeVocabulary.note` — `when` tanpa `else` memaksa catatan baru).
6. **Langkah 5: UI** (`FieldInput` → `ClayTimePicker`; default kosong di form/table state; tampil apa adanya di `displayValue`).
7. **Langkah 6: Test paritas** — fixture `PrototypeFieldTypeSampleFields` iterasi enum; test nilai sah/rusak; test paritas UI↔domain.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Aturan Nilai Tunggal (`TimeFieldValues`)

```kotlin
object TimeFieldValues {
    fun isValid(value: String): Boolean {
        if (value.length != TIME_LENGTH || value[COLON_INDEX] != ':') return false
        return runCatching { LocalTime.parse(value) }.isSuccess
    }
    fun sample(): String = "09:30"
}
```

**Mengapa blok ini ditulis begini?**
- **Pengecekan bentuk lokal sebelum parser**: `LocalTime.parse` ISO sebenarnya menerima `"09:30:15"` (dengan detik). Bentuk simpan kita *tepat menit* — jadi panjang 5 + `:` di indeks 2 dicek dulu, parser baru menjaga keabsahan angka (`24:00`, `09:60` gagal di sini). Dua lapis, dua tanggung jawab berbeda.
- **`runCatching`, bukan try-catch biasa**: gaya repo untuk parser yang bisa gagal; gagal = `false`, **tanpa fallback** ke nilai lain (Kontrak 4 variability: fallback senyap = data berubah diam-diam).
- **`sample()` = satu sumber contoh**: fixture test, seed reconciler (`ProposalEdit.sampleValue`), dan parity test UI semuanya memanggil ini — contoh tidak mungkin menyimpang dari aturan.

### Blok B: Gerbang Domain (`FieldSpec.accepts`)

```kotlin
FieldType.TIME -> TimeFieldValues.isValid(value)
```

**Mengapa blok ini ditulis begini?**
- `when` **tanpa `else`**: menambah `TIME` ke enum membuat kompilasi gagal di setiap `when` yang belum mengenalinya. Itu *fitur*, bukan gangguan — daftar titik pendaftaran dijaga kompilator, bukan ingatan (Kontrak 6).
- Kosong (`""`) sudah disaring `accepts` sebelum `when` — "belum diisi" bukan urusan bentuk.

### Blok C: Generator SQL & Exposed (`SpecColumns` / `SpecPostgresWriter`)

```kotlin
FieldType.TIME -> "TIME$notNull"                    // kolom SQL
FieldType.TIME -> "time($n)"                         // kolom Exposed (kotlin-datetime)
FieldType.TIME -> if (optional) "$raw.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it) }" else "LocalTime.parse($raw)"
FieldType.DATE, FieldType.TIME -> if (c.field.required) "$cell.toString()" else "($cell?.toString() ?: \"\")"
```

**Mengapa blok ini ditulis begini?**
- `TIME` (bukan `TIMETZ`): waktu dinding tanpa zona — sama filosofinya dengan `TIMESTAMP` (bukan `TIMESTAMPTZ`) untuk DATE+withTime. Zona adalah keputusan tampilan, bukan simpan.
- **Round-trip simetris**: `LocalTime.parse("09:30")` saat tulis, `LocalTime.toString()` = `"09:30"` saat baca. Bentuk simpan = bentuk baca = bentuk tampil.
- **Kode hasil generate sengaja lurus** (satu baris per kolom, tanpa framework ajaib) supaya tim yang menerima handoff bisa membacanya.

### Blok D: Gerbang Route Fail-Closed (`SpecRoutesWriter`)

```kotlin
val problem = dateProblem(values) ?: timeProblem(values) ?: textProblem(values) ?: multiProblem(values)
```

**Mengapa blok ini ditulis begini?**
- Urutan gerbang tidak boleh diubah: RBAC dulu (403 sebelum body dibaca), baru validasi isi. `timeProblem` menyisip di antara tanggal dan teks — kolom `TIME` tidak menerima teks bebas, pola persis `dateProblem`.
- Validasi route memakai `f.accepts(v)` = aturan tunggal yang sama dengan domain; server dan klien **tidak mungkin berbeda tafsir**.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi / Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| `TIME` sebagai **tipe baru** di enum | Parameter pada `DATE` (`withTime` mode "time-only") | Menyimpan `JJ:MM` tanpa tanggal adalah **bentuk simpan berbeda** (kolom `TIME` vs `TIMESTAMP`, parser `LocalTime` vs `LocalDateTime`) — Kontrak 2 rules: beda cara menyimpan = tipe baru | Parameter akan memaksa setiap pemakai DATE memeriksa mode, dan kolom tetap salah bentuk |
| Bentuk **tepat menit** (panjang 5) | Menerima ISO penuh `JJ:MM:SS` | UI (`ClayTimePicker` + stepper dialog) memang hanya menghasilkan menit; dua bentuk kosong dilarang (pola MULTI_SELECT `[]`) | Data campuran `09:30` vs `09:30:15` memecah filter/equality |
| `TIME` tanpa zona (kolom `TIME`) | `TIMETZ` / simpan UTC offset | Jam operasional adalah **konvensi lokal** ("buka 09:00 di kota itu"); konversi zona justru menggeser nilai | `TIMETZ` di Postgres punya perilaku perbandingan yang mengejutkan dan jarang dipahami |
| Codec **membaca enum per nama** + tolak tak dikenal | Daftar tipe hardcoded di codec | Entri baru otomatis ikut kawat; test paritas menambat penolakan | Hardcode = codec basi diam-diam saat enum bertambah |
| Prompt/katalog agent **dibaca dari `FieldType.entries`** | Teks daftar tipe ditempel di prompt | Prompt tidak mungkin basi; test menegakkan catatan per tipe | Model diajari kosakata yang tidak ada → draf ditolak validator |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

1. **Jebakan 1: Menambah `else ->` untuk "menenangkan kompilator".**
   - *Kenapa bahaya*: tipe berikutnya lolos tanpa pendaftaran — katalog agent, SQL, dan UI menyimpang diam-diam.
   - *Solusi elegan kita*: cabang `FieldType.TIME ->` eksplisit di **semua** `when`; fixture test juga `when` tanpa `else` (Kontrak 6).
2. **Jebakan 2: Memetakan nilai rusak ke `""` atau `09:00` (fallback senyap).**
   - *Kenapa bahaya*: data berubah tanpa jejak — pelajaran TRD-FLOW-001 (SPK terbaca ulang sebagai `NEW_INTAKE`).
   - *Solusi elegan kita*: nilai tidak sah ditolak di gerbang (`accepts`, validator seed, route) dan ditampilkan **apa adanya** + ditandai galat di UI.
3. **Jebakan 3: Mengira "komponen UI sudah ada = selesai".**
   - *Kenapa bahaya*: `ClayTimePicker` tanpa pendaftaran = kode mati — agent tidak bisa memilihnya, generator tidak punya kolom SQL, validator menolak nilainya.
   - *Solusi elegan kita*: rantai Kontrak 4 penuh; test paritas mengiterasi enum sehingga anggota tanpa padanan menggagalkan build.
4. **Jebakan 4: Menggembungkan prompt agent melebihi anggaran.**
   - *Kenapa bahaya*: `KoogDiscoveryPromptTest` mengunci prompt sistem ≤ 8.000 karakter (SP-C0, hemat token); menambah satu kalimat deskriptif bisa mematahkannya.
   - *Solusi elegan kita*: biarkan kosakata mengalir dari `FieldType.entries` dan katalog `screen_catalog()`; tulis catatan penuh di `KoogDiscoveryFieldTypeVocabulary.note`, bukan di prompt.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

- **Paritas SQL (Kontrak 7)**: `PrototypeFieldTypeSqlParityTest` iterasi `FieldType.entries` — tiap tipe wajib punya kolom SQL, kolom Exposed, dan ekspresi baca/tulis. TIME punya test bentuk nilai sendiri: `accepts_timeField_acceptsOnlyWallClockMinuteShape` menolak `"9:30"`, `"24:00"`, `"09:60"`, `"09:30:15"`.
- **Paritas codec**: `PrototypeFieldTypeCodecParityTest` round-trip semua tipe di 4 kawat (layar interaktif, SpecOp, ScreenSuggestion, dokumen draf) + menolak nama tipe tak dikenal (tidak jatuh ke `TEXT`).
- **Paritas UI↔domain**: `ClayTimeValuesTest.parity_withCoreTimeFieldValues` — parser komponen bersama dan `TimeFieldValues` **tidak boleh berbeda tafsir** untuk sampel yang sama.
- **Paritas kontrol state**: `PrototypeFieldControlParityTest` memastikan TIME punya default terdefinisi di form reset dan tabel inline.
- **Agent deterministik**: `KoogDiscoveryFieldTypeTest` menegakkan katalog memuat tiap tipe dengan catatan tak kosong dan prompt menyebut tiap tipe — tanpa LLM berbayar.
- **Perintah verifikasi**: `./gradlew :core:jvmTest :app:shared:jvmTest :server:test` — semuanya hijau (1.821 test core + 132 test discovery/builder server, dsb.).

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: Tambahkan filter tabel untuk kolom TIME di prototype (rentang `09:00–17:00`). Gerbang validasinya di mana supaya tidak perlu aturan kedua? (Petunjuk: `TimeFieldValues.isValid` + perbandingan `LocalTime`.)
- [ ] **Tantangan 2**: Sertakan TIME pada satu pack non-garment sungguhan (mis. jam operasional di pack layanan) — jalankan `PilotBoardParityTest` dan perhatikan kolom `TIME` muncul di scaffold SQL.
- [ ] **Tantangan 3**: Bayangkan tipe `DURATION` (durasi, mis. `01:30` = 1,5 jam). Lolos Uji Variabilitas? Beda simpan dari TIME? Tulis Discovery Note 5 baris sebelum menyentuh enum.
