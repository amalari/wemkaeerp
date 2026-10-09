# 🎓 Modul Pembelajaran: Track B — Tipe Field `MULTI_SELECT` di Sisi Agent & Penyunting (Server)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Kotlin Multiplatform, katalog kosakata agent (`screen_catalog`), prompt sebagai "guru" yang aturannya dites,
> pembaca parameter ketat (tolak, bukan fallback), evaluasi agent deterministik tanpa LLM berbayar, paritas enum↔katalog.
> **Prasyarat**: Kotlin data class, `enum`/`when` tanpa `else`, JSON (`JsonValue`), dasar RBAC & generator route.
> **Referensi Task**: `docs/trd/TRD-FIELD-003-multi-select.md` (FR-8, §4.3, §4.6 baris **B**, §5),
> `docs/teaching/teaching-trd-field-003-a0.md` (A0 — bentuk tipe & kawat), `.claude/rules/field-component-rules.md`.

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Bayangkan **model AI (agent)** yang menyusun layar untuk pemilik klinik. Kalau agent hanya tahu "ada tipe `ENUM` untuk pilihan",
ia akan memaksa "daftar alergi pasien" (yang bisa **beberapa** nilai sekaligus) menjadi `ENUM`—salah makna—atau `TEXT` bebas—data kotor.

A0 sudah menambah tipe ke-6 `MULTI_SELECT` di **domain** (`FieldType`), dan memasang *stub kompilasi-forced* di sisi server:

```kotlin
FieldType.MULTI_SELECT -> "… (stub) … "   // A0: biar build tahu tipenya ada, isinya diserahkan ke Track B
```

Track B menutup janji itu. Ada **empat hal** yang harus dibereskan di sisi server:

1. **Katalog** (`screen_catalog`) menjelaskan *arti* tipe ini.
2. **Prompt** mengajari *kapan* pilih `MULTI_SELECT` vs `ENUM`, dan *larangan* jadi status.
3. **Penyunting modul** (`KoogModuleEditor`) menolak `maxSelections`/opsi yang salah — *fail-closed*, bukan diam-diam dibetulkan.
4. **Bukti** bahwa semua di atas benar — lewat **tes deterministik tanpa LLM berbayar**.

**Analogi.** Kalau prompt itu *guru* dan validator itu *polisi*, catalog adalah **buku pegangan**. Track B menulis "bab field pilihan ganda"
di buku pegangan itu, mengajar gurunya, lalu memastikan penjaga pintu (`KoogModuleEditor`) menolak nilai yang tidak masuk akal.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0 — Satu sumber kosakata.** `KoogDiscoveryFieldTypeVocabulary` membaca `FieldType.entries`; catatan per tipe ditulis
   dalam `when` **tanpa `else`**. Artinya menambah tipe ke-7 nanti **wajib** gagal build sampai ada catatannya. Update tipe yang ada
   dulu, bukan menulis kosakata kedua.
2. **Langkah 1 — Katalog.** `note(MULTI_SELECT)` = definisi + perbedaan dari `ENUM`. Ini yang dibaca model saat memanggil `screen_catalog()`.
3. **Langkah 2 — Prompt.** `promptRule` (satu baris) disisipkan ke prompt sistem **dan** ke prompt penyunting; diuji `assertEquals` terhadapnya.
4. **Langkah 3 — Penyunting.** Pembaca parameter ketat: `maxSelections` hanya `MULTI_SELECT`, `1..options.size`; `options` wajib & unik.
5. **Langkah 4 — Tes evaluasi.** Tiru `KoogDiscoveryNumberFormatTest`: katalog, prompt, validator, penyunting menolak.
6. **Langkah 5 — Verifikasi route scaffold.** Pastikan route hasil generator memvalidasi di POST **dan** PUT, tanpa mengubah modul berjalan.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Katalog — `KoogDiscoveryFieldTypeVocabulary.note()`

```kotlin
FieldType.MULTI_SELECT -> "MULTI_SELECT: pilihan ganda dari daftar tertutup …; wajib options unik 2-8; " +
    "parameter opsional `maxSelections` (1..jumlah opsi) …; PILIH INI HANYA bila nilai boleh lebih dari satu — " +
    "satu nilai saja tetap ENUM; BUKAN status kerja: dilarang dipakai sebagai statusField/StateMachine …"
```

**Mengapa begini?**
- **`when` tanpa `else`.** Menambah `else` akan mematikan pagar: tipe ke-7 jatuh diam-diam ke cabang lain. Dengan tanpa `else`,
  kompilator itu sendiri yang menjadi *checklist* (lihat `field-component-rules.md` Kontrak 6).
- **Satu catatan, dua pemakai.** `note()` dipakai `screen_catalog` (`KoogDiscoveryTools`) *dan* prompt; tak ada dua daftar yang bisa menyimpang.
- **Menyebut `ENUM` di catatan `MULTI_SELECT`** (dan sebaliknya) memaksa model melihat **pasangan pembeda**, bukan tipe soliter.

### Blok B: Prompt — `promptRule` + penyisipannya

```kotlin
val promptRule: String
    get() = "`fields` bertipe {$names} (ENUM wajib `options` unik 2–8 untuk satu nilai; " +
        "MULTI_SELECT = pilihan ganda, `options` unik & `maxSelections` opsional, dilarang jadi statusField; " +
        "tipe lain tanpa `options`; LONG_TEXT untuk isi sekalimat atau lebih; TEXT untuk nama/kode/judul satu baris); " + …
```

**Mengapa begini?**
- **`promptRule` diuji apa adanya.** `KoogDiscoveryFieldTypeTest` mencari `{A|B|…}` dan membandingkannya persis dengan
  `FieldType.entries`. Jadi daftar tipe di prompt **tidak mungkin** menyimpang dari enum.
- **Anggaran panjang prompt itu nyata.** `KoogDiscoveryPromptTest` membatasi prompt sistem ≤ 8.000 karakter (hemat token tiap putaran).
  Menambah aturan berarti harus **memangkas kata** di tempat lain — makanya versi akhir kalimat `MULTI_SELECT` pendek, sementara
  penjelasan panjang tinggal di **katalog** (yang tidak ikut batas 8 rb). Ini pelajaran penting: *detail ke katalog, aturan keras ke prompt*.
- **Prompt itu guru, bukan polisi.** Prompt memuat aturan; validator (`ProposalEntityRules`) dan penyunting (`KoogModuleEditor`) yang menegakkan.

### Blok C: Penyunting — pembaca parameter ketat

```kotlin
private fun readMaxSelections(o: JsonValue.Obj, type: FieldType, options: List<String>): Int? {
    val raw = o["maxSelections"]
    if (raw == null || raw is JsonValue.Null) return null
    val value = (raw as? JsonValue.Num)?.raw?.toIntOrNull()
        ?: error("field.maxSelections harus bilangan bulat atau null")   // "2.5"/"2"/nilai lain = DITOLAK
    if (type != FieldType.MULTI_SELECT) error("field.maxSelections hanya untuk type MULTI_SELECT, bukan ${type.name}")
    if (value !in 1..options.size) error("field.maxSelections harus 1..${options.size} …, dapat $value")
    return value
}
```

```kotlin
private fun requireOptions(type: FieldType, options: List<String>) {
    if (type != FieldType.ENUM && type != FieldType.MULTI_SELECT) return
    require(options.isNotEmpty()) { "field.options wajib untuk type ${type.name}" }
    require(options.distinct().size == options.size) { "field.options ${type.name} wajib unik" }
}
```

**Mengapa begini?**
- **Tolak, jangan fallback senyap** (`tenant-variability-rules.md` Kontrak 4). Membaca `"maxSelections":"2"` lalu diam-diam jadi `2`
  **mengubah data tanpa jejak**. Menolak dengan pesan `field.…` membuat model bisa memperbaiki sendiri (umpan balik berpath).
- **Ditaruh di pembaca parameter (`…FieldParams`), bukan di `fieldOf`.** Sudah ada pola yang sama untuk `withTime` (C6), `validation` (C9),
  dan `format`/`currencyCode` (C4). Konsisten > pintar.
- **Diuji dari dua sisi.** `KoogDiscoveryMultiSelectTest` memastikan nilai **sah** dibaca (`maxSelections=2`) **dan** tujuh bentuk salah ditolak.

### Blok D: Route scaffold — verifikasi, bukan ubah

```kotlin
// SpecRoutesWriter (core) menghasilkan, di POST **dan** PUT:
val problem = dateProblem(values) ?: textProblem(values) ?: multiProblem(values)
    ?: PrototypeReducer.reduce(SPEC, …, Create/SetField).exceptionOrNull()?.message
```

**Mengapa begini?**
- **Validasi tunggal, dua pemakai.** `multiProblem` memanggil `FieldSpec.accepts` yang **sama** dengan prototype — aturan server tak bisa berbeda dari klien.
- **Route ter-check-in tidak disentuh.** `LayananChangeRequestRoutes` adalah "milik tim" setelah diterapkan. Track B hanya
  **menambah gerbang** `LayananChangeRequestRoutesContractTest` yang membaca berkasnya dan memastikan POST/PUT memvalidasi sebelum
  `repository.save`. Bila ada yang menghapus validasi di salah satu verb, build gagal.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan | Alternatif | Mengapa Memilih Ini | Risiko Alternatif |
|---|---|---|---|
| Catatan per tipe di `when` tanpa `else` | Peta `Map<FieldType, String>` | Kompilator memaksa tiap entri; tak bisa lupa | Entri baru lolos build → model tak tahu tipenya |
| Detail di **katalog**, aturan di **prompt** | Semua di prompt | Prompt dibatasi 8 rb karakter (hemat token) | Prompt membengkak → biaya naik, fokus model turun |
| Penolakan berpath `field.…` | Diam-diam normalisasi | Model bisa mengoreksi diri; data jujur | Data berubah tanpa jejak (Kontrak 4 dilanggar) |
| Tes deterministik (scripted) | Panggil LLM berbayar | Cepat, murah, deterministik, jalan di CI | Tes lambat, flaky, dan berbiaya |
| Gerbang baca-berkas untuk route ter-check-in | Regenerasi & diff | Tidak menyentuh modul berjalan; cepat | Berisiko mengubah perilaku modul produksi |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls)

1. **Jebakan: menambah `else ->` "supaya kompilasi hijau".**
   *Solusi*: jangan. Entri baru = build gagal = daftar titik pendaftaran yang tak bisa terlewat.

2. **Jebakan: menaruh penjelasan panjang di prompt.**
   *Solusi*: prompt hanya memuat **aturan singkat**; detail masuk `screen_catalog`. Batas 8 rb karakter dijaga tes.

3. **Jebakan: `raw.asInt` untuk `maxSelections`.**
   *Solusi*: `asInt` memotong `2.5` → `2` (fallback senyap!). Pakai `raw.raw.toIntOrNull()` supaya pecahan/string ditolak.

4. **Jebakan: menganggap "punya options" otomatis "sah".**
   *Solusi*: opsi wajib **tidak kosong dan unik**; `["a","a"]` ditolak sebelum sampai validator.

5. **Jebakan: menyamakan `MULTI_SELECT` dengan `ENUM` sebagai status.**
   *Solusi*: status berpindah lewat transisi; label ganda tidak. Prompt & katalog melarang; validator `checkStatus` menolaknya.

6. **Jebakan: "memperbaiki" route ter-check-in agar seragam dengan generator terbaru.**
   *Solusi*: itu mengubah modul berjalan. Cukup **verifikasi** lewat tes gerbang.

---

## 🧪 6. Bagaimana Membuktikan Kodingan Kita Bekerja?

Tes deterministik (tanpa LLM berbayar), pola `KoogDiscoveryNumberFormatTest`:

- **Katalog & paritas dua arah** — `KoogDiscoveryMultiSelectTest` membaca `screenCatalogJson`, memastikan `fieldTypes` =
  `FieldType.entries` (persis, tidak lebih/ kurang) dan tiap catatan tidak kosong.
- **Prompt** — memastikan setiap nama tipe enum muncul, `promptRule` tampil apa adanya, daftar `{…}` = enum, dan aturan
  `MULTI_SELECT`/`ENUM`/`statusField`/`maxSelections` ada. `KoogDiscoveryPromptTest` mengunci batas ≤ 8 rb karakter.
- **Validator** — usulan `MULTI_SELECT` + `maxSelections` pada pack **klinik** (non-garment) lolos validator **dan** round-trip
  `DiscoveryDraftCodec`; `MULTI_SELECT` sebagai `statusField` ditolak dengan pesan `ENUM` (FR-5).
- **Penyunting menolak** — `KoogModuleEditor` membaca `MULTI_SELECT`+`maxSelections` yang sah, dan menolak: `maxSelections`
  pada tipe lain, di luar `1..options.size`, bertipe string/pecahan, `options` kosong, `options` kembar.
- **Route ter-check-in** — `LayananChangeRequestRoutesContractTest` membuktikan POST & PUT memvalidasi sebelum menyimpan.

Cara menjalankan:

```bash
./gradlew :server:compileKotlin
./gradlew :server:test --tests '*MultiSelect*'
# set lengkap (katalog/prompt/penyunting/route):
./gradlew :server:test --tests '*Koog*' --tests '*MultiSelect*' \
          --tests '*LayananChangeRequestRoutesContractTest'
```

Hasil yang tercatat: `KoogDiscoveryMultiSelectTest` tests=7 failures=0; `LayananChangeRequestRoutesContractTest` tests=2 failures=0;
seluruh `*Koog*` (122 tests) hijau, prompt sistem 7.996 ≤ 8.000 karakter.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: Kalau Track A sisa menambahkan operasi `SetFieldMaxSelections`, tambahkan pembaca ketat yang sama untuk
      operasi itu (pola `FieldParamWire.maxSelections`) dan tes penolakannya.
- [ ] **Tantangan 2**: Tambahkan evaluasi agent penuh (multi-putaran, model sungguhan) yang meminta "daftar layanan yang bisa dipilih
      lebih dari satu", lalu periksa agent memilih `MULTI_SELECT` (bukan `ENUM`/`TEXT`). Bandingkan dengan evaluasi deterministik ini.
- [ ] **Tantangan 3**: Perluas `screen_catalog` dengan contoh `seed` kanonik `MULTI_SELECT` agar model melihat bentuk `["a","b"]`.

---

## 🔭 Sisa yang Sengaja Ditinggalkan

- **Track C (UI)**: kontrol chip pilih-ganda di `FieldInput`/tabel/kanban. Di luar batas direktori Track B.
- **CRM**: `MULTI_SELECT` di kosakata CRM tetap ditunda (keputusan D2/R5 TRD).
- **Evaluasi LLM berbayar**: sengaja tidak dijalankan; Track B memakai evaluasi deterministik agar murah dan stabil di CI.
