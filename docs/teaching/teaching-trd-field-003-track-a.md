# 🎓 Modul Pembelajaran: Track A sisa — Operasi `SetFieldMaxSelections` untuk `MULTI_SELECT`

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Kotlin Multiplatform, DDD, operasi suntingan `sealed interface`, invarian yang mengubah bentuk nilai,
> penolakan seed alih-alih koersi, kawat JSON ketat, paritas kosakata di `core`.
> **Prasyarat**: Baca dulu `docs/teaching/teaching-trd-field-003-a0.md` (bentuk tipe + kawat `MULTI_SELECT`).
> **Referensi Task**: `docs/trd/TRD-FIELD-003-multi-select.md` §4.3/§4.6 (`A sisa`), §5 (acceptance);
> `.claude/rules/field-component-rules.md` (Kontrak 6/7/8), `.claude/rules/tenant-variability-rules.md` Kontrak 4.

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

A0 sudah membuat **tipe** dan **nilai** `MULTI_SELECT`: pilihan ganda dari daftar tertutup, nilai kanonik
`["Gigi","Jantung"]`. Yang belum ada adalah cara **mengubah batas pilihannya** setelah field itu hidup.

Bayangkan klinik mendeklarasikan field "Alergi" (`MULTI_SELECT`) tanpa batas. Setelah rapat, mereka ingin
"**maksimum 3 alergi per pasien**". Ini bukan sekadar mengubah tampilan: kalau ada baris contoh (seed) yang
sudah berisi 4 alergi, mengubah batas menjadi 3 membuat baris itu **tidak lagi sah**. Dua respons yang mungkin:

- **Diam-diam memotong** pilihan ke-4 → data pengguna berubah tanpa jejak. Ini *dilarang*.
- **Menolak operasi** dengan pesan "3 baris punya nilai yang tak sah di bentuk baru; ubah dulu" → ini yang kita pilih.

Pola ini sudah punya preseden di repo: `SetFieldWithTime` dan `SetFieldValidation` sama-sama **mengubah bentuk
nilai sah**, jadi keduanya menolak bila seed lama melanggar. Track A sisa tinggal menambahkan anggota keluarga
ketiga: `SetFieldMaxSelections`.

**Analogi sederhana.** Mengubah batas pilihan itu seperti mengganti ukuran baki penyaji di dapur: kalau piring
yang sudah terisi lebih banyak daripada baki baru, koki tidak boleh diam-diam membuang lauk — ia memberi tahu
pelanggan untuk merapikan dulu.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Kalau mengetik dari layar kosong, urutannya:

1. **Langkah 0 — Kosakata operasi** (`SpecOp.kt`): tambah `data class SetFieldMaxSelections`. Karena `SpecOp`
   adalah `sealed interface`, **kompilator langsung menyalakan alarm** di semua `when (op)` yang lupa menanganinya.
2. **Langkah 1 — Penerapan murni** (`FieldParamOps.setMaxSelections`): validasi tipe & rentang, lalu delegasikan
   pengecekan seed ke helper `replace` yang sudah ada.
3. **Langkah 2 — Tutup `when (op)` yang dipaksa kompilator**: `SpecOpApplier.apply` (logika) dan
   `BriefRenderer.describe` (bahasa pengguna).
4. **Langkah 3 — Kawat** (`SpecOpCodec`): encode + decode ketat.
5. **Langkah 4 — Tes**: nilai, seed yang menolak, kawat yang menolak, dan paritas titik pendaftaran lain.

Kuncinya: **mulai dari kosakata**, bukan dari UI. Menambah anggota `sealed` membuat daftar pekerjaan muncul
sendiri dari compiler error — tidak perlu grep manual.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Kosakata operasi — `SpecOp.SetFieldMaxSelections`

```kotlin
data class SetFieldMaxSelections(val entityId: String, val field: String, val maxSelections: Int?) : SpecOp
```

**Mengapa begini?**
- `maxSelections: Int?` — `null` berarti **hapus batas** (kembali dibatasi hanya oleh jumlah opsi). Ini sengaja:
  TRD R3 mendefinisikan `maxSelections` sebagai `Int?`, dan operator yang salah harus bisa dibatalkan.
- Operasi suntingan lain memakai `Boolean`/enum non-null; di sini nilai nullable itu **bermakna**, bukan "belum diisi".

### Blok B: Penerapan — `FieldParamOps.setMaxSelections`

```kotlin
fun setMaxSelections(screen: InteractiveScreen, op: SpecOp.SetFieldMaxSelections): InteractiveScreen {
    val f = fieldOf(screen, op.entityId, op.field)
    require(f.type == FieldType.MULTI_SELECT) {
        "Field '${f.label}' bertipe ${f.type.name}; batas pilihan hanya untuk field MULTI_SELECT."
    }
    val max = op.maxSelections
    require(max == null || max in 1..f.options.size) {
        "Batas pilihan '${f.label}' harus 1..${f.options.size}, dapat $max."
    }
    if (f.maxSelections == max) return screen
    return replace(screen, op.entityId, f.copy(maxSelections = max), "mengubah batas pilihan")
}
```

**Mengapa begini?**
- **Tiga gerbang sebelum mengubah apa pun**: field harus ada, tipenya harus `MULTI_SELECT`, dan rentangnya sah.
  Rentang diperiksa **di sini** (bukan hanya di `FieldSpec`) supaya pesan galatnya ramah; `FieldSpec` tetap
  penjaga terakhir — dua lapis, bukan saling menggantikan.
- **`replace(...)` melakukan pekerjaan berat**: ia menolak bila ada nilai seed yang tak lolos bentuk baru. Karena
  `FieldSpec.accepts` untuk `MULTI_SELECT` memanggil `MultiSelectValues.isValid(value, options, maxSelections)`,
  menurunkan batas langsung terdeteksi — **satu sumber aturan nilai**, tidak ada cek duplikat di sini.
- **`if (f.maxSelections == max) return screen`**: operasi yang tidak mengubah apa pun mengembalikan objek yang
  sama (identitas terjaga, tidak ada regenerasi dokumen yang menyebabkan diff palsu).

### Blok C: Menutup `when (op)` yang dipaksa kompilator

`SpecOpApplier.apply`:

```kotlin
is SpecOp.SetFieldMaxSelections -> FieldParamOps.setMaxSelections(screen, op)
```

`BriefRenderer.describe` (bahasa pengguna, bukan nama kelas):

```kotlin
is SpecOp.SetFieldMaxSelections -> "batasi pilihan '${op.field}' " + (op.maxSelections?.let { "maksimum $it" } ?: "tanpa batas")
```

**Mengapa begini?** `SpecOp` adalah `sealed interface` dan `when (op)` ditulis **tanpa `else`**. Menambah anggota
membuat build gagal sampai setiap titik menanganinya — daftar pekerjaan yang tidak bisa terlewat (pola paritas
kompilator, sama dengan `FieldType`).

### Blok D: Kawat ketat — `SpecOpCodec`

```kotlin
// encode
is SpecOp.SetFieldMaxSelections -> jsonObjectOf(
    "type" to jsonOf("SetFieldMaxSelections"), "entityId" to jsonOf(op.entityId), "field" to jsonOf(op.field),
    "maxSelections" to (op.maxSelections?.let { jsonOf(it) } ?: JsonValue.Null)
)

// decode
"SetFieldMaxSelections" -> {
    require(o.has("maxSelections")) { "Bidang 'maxSelections' wajib diisi (bilangan bulat atau null)." }
    SpecOp.SetFieldMaxSelections(str("entityId"), str("field"), FieldParamWire.maxSelections(o))
}
```

**Mengapa begini?**
- **Kunci wajib ada.** Operasi suntingan ini tidak punya arti tanpa nilainya, jadi kunci absen **ditolak** — bukan
  diam-diam dianggap "hapus batas". `o.has(...)` membedakan *kunci absen* dari *kunci bernilai JSON `null`*: yang
  pertama galat, yang kedua sah (hapus batas). Ini Kontrak 4: **tolak, jangan fallback senyap**.
- **`FieldParamWire.maxSelections` (`strictOptInt`)** menolak `2.5`, `"2"`, dll. Bilangan pecahan/overflow tidak
  dipotong diam-diam.
- Rentang `1..options.size` **tidak** diperiksa di codec karena codec tidak tahu `options`; itu tugas reducer.

### Blok E: Yang **tidak** berubah (dan mengapa)

- `ProposalEdit.accepts`/`sampleValue`, `ProposalEntityRules.checkOptions`/`checkValue`, `FieldHint.maxSelections`,
  `ScreenSuggestionCodec`, dan `DeterministicScreenProposer.cardOf` sudah menangani `MULTI_SELECT` sejak A0 untuk
  lolos kompilasi. Track A sisa hanya **mengunci perilakunya dengan tes**, bukan menulis ulang.
- `MULTI_SELECT` tidak pernah jadi `statusField` (`ProposalEntityRules.checkStatus` sudah menolak non-`ENUM`) dan
  tidak pernah jadi elemen kartu (`cardOf` → `null`). FR-5 terpenuhi tanpa kode baru.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan | Alternatif | Mengapa Memilih Ini | Risiko Alternatif |
|---|---|---|---|
| **Tolak bila seed melanggar batas baru** | Potong pilihan berlebih diam-diam | Data pengguna tidak berubah tanpa jejak; kesalahan terlihat saat sunting | Data korup senyap; tes paritas tak bisa membandingkan |
| **`maxSelections: Int?` (`null` = hapus batas)** | `Int` non-null (tak bisa hapus) | Mengikuti TRD R3; operator salah bisa dibatalkan | Tak ada cara membatalkan; harus bikin op baru |
| **Kunci wire wajib ada, `null` sah** | Kunci opsional (absen = hapus) | Membedakan "lupa isi" dari "sengaja hapus batas" | LLM yang lupa mengisi menghapus batas diam-diam |
| **Rentang divalidasi di reducer, kawat hanya tipe** | Validasi rentang di codec | Codec tak tahu `options`; satu miss tak bisa diperbaiki di dua tempat | Duplikasi aturan → bisa menyimpang |
| **Kumpulkan paritas dari compiler, bukan grep** | Daftar manual titik pendaftaran | `when` tanpa `else` menjamin tak ada titik terlewat | Titik terlewat lolos diam-diam saat tipe ke-7 datang |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls)

1. **Jebakan: menambah `else ->` pada `when (op)`.**
   *Bahaya*: mematikan pagar; operasi ke-11 nanti jatuh diam-diam. *Solusi*: biarkan build gagal dan tangani
   setiap cabang.

2. **Jebakan: menganggap kunci absen = `null`.**
   *Bahaya*: model yang lupa mengirim `maxSelections` akan **menghapus batas** tanpa disadari. *Solusi*:
   `require(o.has("maxSelections"))` di decode.

3. **Jebakan: memotong pilihan berlebih saat batas diturunkan.**
   *Bahaya*: nilai pengguna hilang tanpa jejak. *Solusi*: `replace(...)` menolak dan memberi pesan jumlah baris
   yang bermasalah.

4. **Jebakan: memeriksa rentang hanya di reducer.**
   *Bahaya*: pesan galat generik dari `FieldSpec` (mis. "harus 1..3") kurang ramah. *Solusi*: `require` eksplisit
   di reducer **plus** invarian `FieldSpec` sebagai penjaga terakhir.

5. **Jebakan: menaruh `maxSelections` di tengah parameter `FieldSpec`.**
   *Bahaya*: argumen positional lama tergeser. *Solusi*: selalu di akhir, dengan bawaan `null` (sudah dilakukan A0).

---

## 🧪 6. Bagaimana Membuktikan Kodingan Kita Bekerja?

- **Penerapan & penolakan seed** (`FieldParamOpsTest`):
  - `setMaxSelections_narrowsRaisesAndClears_whenSeedFits` — turunkan, naikkan, dan hapus batas; seed sah dipertahankan;
    operasi tanpa perubahan mengembalikan objek yang **sama** (`assertSame`).
  - `setMaxSelections_seedExceedingNewLimit_isRejectedNotTrimmed` — seed 2 pilihan, batas baru 1 → **ditolak** dengan
    pesan "1 baris ... batas pilihan"; menaikkan batas selalu lolos.
  - `setMaxSelections_nonMultiOutOfRangeOrMissing_isRejectedWithMessage` — non-`MULTI_SELECT`, `0`, `4`, field hantu.
- **Kawat ketat** (`FieldParamOpsTest.specOpCodec_setFieldMaxSelections_roundTripsEveryShape_andRejectsBadWire`) —
  `null` dan `1` round-trip; kunci absen, `1.5`, `"1"` ditolak.
- **Sunting usulan** (`ProposalEditTest`) — `MULTI_SELECT` wajib mengisi contoh kanonik `["Digitizing"]`, tak wajib
  tidak mengisi; mengganti tipe membuang nilai lama; batas lebih sempit membuang kelebihan.
- **Petunjuk pack & kawat** (`FieldHintParamsCodecTest`) — `FieldHint.maxSelections` hanya `MULTI_SELECT` dan dalam
  rentang; round-trip kawat; tipe/rentang salah **ditolak**, bukan diredam; kunci bawaan tidak ditulis (byte-identik).
- **Pembuat deterministik** (`DeterministicScreenProposerTest`) — `cardOf(MULTI_SELECT) == null`; `MULTI_SELECT`
  tidak pernah jadi `statusField` di seluruh keluaran.

Cara menjalankan:

```bash
./gradlew :core:jvmTest
```

Hasil: **BUILD SUCCESSFUL**, seluruh tes `core` hijau.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: Tambahkan operasi `SetFieldOptions` (ganti daftar `options` MULTI_SELECT) yang menolak bila
      ada seed memakai opsi yang dibuang — pola yang sama, tapi lebih tajam (rename opsi vs buang opsi).
- [ ] **Tantangan 2**: Tambah tes paritas yang mengiterasi `SpecOpCodec` untuk **semua** `SpecOp` dan memastikan
      setiap jenis round-trip (ekstensi dari `FieldType.entries` ke kosakata operasi).
- [ ] **Tantangan 3**: Rancang aturan **prompt** Track B: kapan model memilih `MULTI_SELECT` vs `ENUM`, dan tegaskan
      `MULTI_SELECT` bukan status.

---

## 🔭 Sisa untuk Track B/C

- **Track B (server)**: aturan katalog/prompt Koog "kapan `MULTI_SELECT` vs `ENUM`" (`promptRule` di
  `KoogDiscoveryFieldTypeVocabulary`), evaluasi deterministik, pemeriksaan route scaffold ter-check-in.
- **Track C (UI)**: kontrol chip pilih-ganda di `FieldInput` (+ tabel/kanban/dialog), komponen dasar
  `designsystem/` buta domain, `displayValue` (daftar label dipisah `, `), cek visual di pack non-garment.
- **Tes integrasi Postgres** (`TEXT[]` + CHECK) sudah hijau di A0 (`PrototypeMultiSelectPgTest`).
