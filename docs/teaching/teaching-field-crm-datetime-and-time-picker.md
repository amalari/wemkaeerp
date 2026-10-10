# 🎓 Modul Pembelajaran: Tanggal Berwaktu di CRM & ClayTimePicker Mandiri (C6/D7 — Penutup Irisan 2)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Value object tanggal-jam ketat (strict parsing), validasi tulis fail-closed, konversi tipe field sadar-parameter, komponen input buta domain di Compose Multiplatform
> **Prasyarat**: Paham struktur modul repo (core/app/server), konsep custom field CRM (`customfield/FieldType.kt`), dan aturan pendaftaran komponen input (`.claude/rules/field-component-rules.md`)
> **Referensi Task**: `docs/plannings/PLAN-field-component-gaps.md` C6 + keputusan D7; `docs/teaching/teaching-field-date-with-time.md` (sisi prototype)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah Nyata**: Sisi prototype sudah lama punya `DATE` dengan `withTime = true` (nilai `TTTT-BB-HH'T'JJ:MM`, kolom SQL `TIMESTAMP`). Tapi di CRM, field `DateField(withTime = true)` masih dirender **kolom teks** — dan yang lebih berbahaya, validasi tulis di server (`CustomFieldValidation`) hanya menerima tanggal-saja. Jadi meski UI-nya diganti picker, nilai `2026-10-08T14:30` yang di-commit akan ditolak server sebagai `TypeMismatch`. Dua lapisan tidak pernah bersepakat tentang "apa itu nilai sah".
- **Analogi Sederhana**: Prototype adalah cabang bank yang menerima transfer jam 14:30, sedangkan kantor pusat (validator) hanya mencatat tanggal. Formulirnya boleh canggih; uangnya tetap tidak masuk.
- **Hasil Akhir**: CRM merender/menyunting tanggal berwaktu dengan `ClayDateTimePicker` yang sama dengan prototype; server menerima **tepat** `TTTT-BB-HH'T'JJ:MM` untuk field berwaktu dan **menolak** tanggal-saja padanya (dan sebaliknya) — satu semantik, dua kosakata.

## 🧭 2. "Start dari Mana?" — Order of Operations

1. **Langkah 0: Satu parser murni di `core/shared`** — `DateTimeCodec.parseLocalDateTimeMinuteOrNull`: panjang 16, huruf `T` di indeks 10, `LocalDateTime.parse`. Kenapa di `core/shared`, bukan di validator? Karena aturan bentuk nilai harus bisa dipakai validator tulis, konversi tipe, dan test paritas **tanpa** salin-tempel.
2. **Langkah 1: Validasi tulis** (`CustomFieldValidation.validateType`) — cabang `DateField` bercabang per `def.type.withTime`. Ini gerbang fail-closed: nilai tak sah = `TypeMismatch`, bukan dikonversi diam-diam.
3. **Langkah 2: Konversi tipe** (`FieldTypeConversion`) — `coerce` ikut sadar-`withTime`; ganti `withTime` diklasifikasi `LOSSY` (bukan `IDENTITY`!) supaya butuh dry run + konfirmasi.
4. **Langkah 3: UI client** (`app/shared/presentation/crm`) — ganti `DATE_TIME_TEXT` jadi `DATE_TIME_PICKER` yang membuka `ClayDateTimePicker` milik `designsystem/`.
5. **Langkah 4: Test paritas** — `CrmFieldTypeParityTest` di-update: sel valid/invalid per varian + test penolakan silang.

Pelajaran besar: **urutan 0–2 lebih dulu dari UI.** Kalau UI dulu, kamu memproduksi nilai yang servermu tolak.

## 🧱 3. Bedah Blok per Blok

### Blok A: Parser ketat (core/shared/common/DateTimeCodec.kt)

```kotlin
fun parseLocalDateTimeMinuteOrNull(value: String?): LocalDateTime? {
    if (value == null || value.length != 16 || value[10] != 'T') return null
    return try { LocalDateTime.parse(value) } catch (_: Exception) { null }
}
```

**Mengapa begini?**
- `LocalDateTime.parse` saja **tidak cukup**: ia menerima `2026-10-08T14:30:45` (dengan detik) dan bentuk lain. Cek panjang 16 + `T` di indeks 10 mereplikasi semantik `DateFieldValues` di kosakata prototype — "tepat menit, tanpa detik/zona" — sehingga dua kosakata tidak bisa berbeda tafsir.
- Pola `try/catch` (bukan `runCatching`) di file ini bukan selera: itu dokumentasi bug `IrLinkageError` di Kotlin/Wasm akibat boxing `Result<Instant>` — lihat KDoc `DateTimeCodec`.

### Blok B: Validasi tulis fail-closed (CustomFieldValidation.kt)

```kotlin
val valid = raw != null && if (def.type.withTime) {
    DateTimeCodec.parseLocalDateTimeMinuteOrNull(raw) != null
} else {
    DateTimeCodec.parseLocalDateOrNull(raw) != null
}
if (valid) null else mismatch(def)
```

**Mengapa ketat dua arah (tanggal-saja ditolak di field berwaktu, dan sebaliknya)?** Karena koersi diam-diam = data berubah tanpa jejak (pelajaran Kontrak 4 `tenant-variability-rules`). Kolom SQL-nya berbeda (`DATE` vs `TIMESTAMP`); membiarkan bentuk campuran berarti isi kolom tidak bisa dipercaya saat filter/urut.

### Blok C: Konversi tipe sadar-parameter (FieldTypeConversion.kt)

```kotlin
if (from is FieldType.DateField && to is FieldType.DateField) {
    return if (from.withTime == to.withTime) ConversionSafety.IDENTITY else ConversionSafety.LOSSY
}
```

**Ini jebakan paling instruktif di task ini.** Pemeriksaan identitas lama (`from::class == to::class && from.code == to.code`) mencaplos `DateField()` dan `DateField(withTime = true)` — kelas sama, kode `"DATE"` sama — sehingga ganti `withTime` dianggap `IDENTITY` dan di-apply tanpa konfirmasi, padahal semua nilai tanggal-saja lama akan terhapus saat `coerce` ketat berjalan. Uji dugaanmu lewat test, bukan lewat intuisi: test pertamaku gagal justru di sini (`expected LOSSY but was IDENTITY`).

### Blok D: UI CRM (LeadFieldControl / LeadCustomField / LeadCustomFieldInputs)

```kotlin
is FieldType.DateField -> if (type.withTime) LeadFieldControl.DATE_TIME_PICKER else LeadFieldControl.DATE_PICKER
```

`ClayDateTimePicker` (designsystem) dipakai lewat slot `input` `TextEditor` dengan `displayText` untuk tampilan baca-saja; format simpan tetap string ISO — identik dengan pemakaian prototype di `FieldInput.kt`. Semua `when (FieldType)` tetap **tanpa `else`** (Kontrak 6): varian baru wajib gagal kompilasi, bukan lolos senyap.

### Blok E: `ClayTimePicker` mandiri (designsystem, D7)

Komponen waktu-murni (`JJ:MM`) yang membuka ulang `ClayTimePickerDialog` yang sudah ada; logika murninya di `ClayTimeValues.kt` (parse/validasi tanpa fallback senyap). Buta domain: hanya `String`/lambda. **Status**: belum terdaftar ke kosakata field karena tidak ada tipe `TIME` — sesuai Kontrak 1, pendaftaran menyusul bersama tipe itu, bukan dipalsukan jadi `TEXT`.

## ⚖️ 4. The "Why" — Keputusan Teknis

| Keputusan | Alternatif | Mengapa ini | Risiko alternatifnya |
|---|---|---|---|
| Parser ketat 16-karakter di `DateTimeCodec` | Percaya `LocalDateTime.parse` mentah | Detik/zona lolos = dua bentuk nilai untuk satu kolom | Filter dan urutan SQL jadi tidak konsisten |
| Validasi ketat dua arah | Terima keduanya lalu normalisasi | Tidak ada koersi senyap; kolom `DATE` vs `TIMESTAMP` jujur | Data "berubah bentuk" tanpa jejak audit |
| Ganti `withTime` = `LOSSY` | Biarkan `IDENTITY` | Apply langsung akan menghapus nilai lama tanpa konfirmasi | Kehilangan data massal lewat operasi "tak berbahaya" |
| Pakai ulang `ClayDateTimePicker`/`ClayTimePickerDialog` | Buat kontrol CRM sendiri | Satu bahasa visual & satu sumber perilaku (Aturan Tiga Kali) | Dua picker berbeda perilaku di dua modul |
| `ClayTimePicker` belum didaftarkan ke kosakata | Palsukan sebagai `TEXT` | Kontrak 8 field-component-rules: komponen tak berpasangan ≠ tipe palsu | Data berubah tanpa jejak |

## ⚠️ 5. Jebakan Pemula

1. **Ganti UI tanpa cek validator server** — picker menghasilkan nilai yang server tolak; bug baru terlihat saat commit, jauh dari tempat perbaikannya. *Solusi*: audit dulu jalur tulis (Langkah 1) sebelum sentuh UI.
2. **`IDENTITY` berdasar kelas/kode saja** — parameter (`withTime`, `format`, `validation`) mengubah bentuk nilai sah tapi tidak mengubah kode tipe. *Solusi*: periksa kesamaan **parameter**, bukan hanya kelas.
3. **Parser "yang penting parse"** — `LocalDateTime.parse("2026-10-08T14:30:45")` lolos padahal kontrak bilang tepat menit. *Solusi*: cek bentuk sebelum parse, uji bentuk terlarang di test.
4. **Menambah ikon ke `ClayIcons.kt`** — file itu di atas hard limit ratchet (file-size-rules §2); ikon jam digambar kecil via `Canvas` di `ClayTimePicker` sampai pemakaian kedua memicu pengangkatan (Aturan Tiga Kali).
5. **Mengira test lama sudah menjamin** — `validCell` paritas lama memakai `dateCell(LocalDate)` untuk *semua* varian `DateField`, jadi varian berwaktu diuji dengan nilai yang (menurut aturan baru) salah. Paritas hanya sekuat contoh yang kamu berikan.

## 🧪 6. Pembuktian

- **Core (murni, tanpa DB)**: `CrmFieldTypeParityTest` — `validation_dateFieldWithTime_acceptsOnlyMinuteDateTime` (menolak tanggal-saja, berdetik, pemisah spasi, jam 25, teks acak), `validation_dateFieldWithoutTime_rejectsDateTimeValue`, `conversion_toDateFieldWithTime_convertsMinuteDateTime_clearsDateOnly`, `conversion_dateFieldChangingWithTime_isLossy`. `./gradlew :core:jvmTest` hijau.
- **App shared**: `LeadFieldControlParityTest` (12 test, termasuk `dateField_withTime_usesDateTimePicker` dan `submit_whenDateTimeMalformed_isBlocked_evenWhenOptional`), `ClayTimeValuesTest` (round-trip 24×60 kombinasi jam:menit + paritas dengan parser tanggal-jam). Kompilasi JVM/WasmJs/JS + `:app:shared:jvmTest` hijau.

## 🏆 7. Tantangan Mandiri

- [ ] Tambahkan opsi "Tanggal berwaktu" ke `AddCustomFieldDialog` (CRM) — ikuti Kontrak 4 field-component-rules: grep semua penyebut konfigurasi tipe, pastikan codec `withTime` sudah ada (`CustomAttributesCodec` memang sudah), dan uji di pack non-default.
- [ ] Rancang pendaftaran tipe `TIME` untuk `ClayTimePicker`: mulai dari Uji Variabilitas (Kontrak 1) — apakah "jam operasional" bervariasi per tenant, dan kosakata mana (prototype, CRM, atau keduanya) yang harus kena?
- [ ] Periksa: apa yang terjadi pada rekam lama berisi `2026-10-08` di field yang admin ubah jadi `withTime = true`? Telusuri jalur dry run `LOSSY` dan jelaskan mengapa teks asli dipulihkan (`Cleared`), bukan dihapus.
