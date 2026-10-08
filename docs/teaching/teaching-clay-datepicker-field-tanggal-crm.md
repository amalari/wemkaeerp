# Teaching — ClayDatePicker untuk Field Tanggal CRM (Irisan 1 Track C)

> Slice: **Irisan 1 Track C** dari [`PLAN-field-component-gaps.md`](../plannings/PLAN-field-component-gaps.md).
> Komit: `57cce776` (wiring inspector) + `d68dcf46` (form lead baru, validasi, isError) →
> merge `3524f1d9` ke `main`.

## 1. Konteks

Aturan [`field-component-rules.md`](../../.claude/rules/field-component-rules.md) lahir dari temuan
2026-10-08: komponen `ClayDatePicker` sudah ada di `designsystem/`, tetapi **tidak ada satu pun
konteks CRM yang memakainya** — field tanggal custom di CRM dirender sebagai kolom teks
(`TTTT-BB-HH`), dan dialog lead baru bahkan tidak punya input untuk `DateField` sama sekali.

Irisan 1 Track C menjawab: **aplikasikan `ClayDatePicker` ke field tanggal CRM** —
(`LeadCustomField`, `AddCustomFieldDialog`/`DateField`) + tes paritas — via kompilasi 5 target
dan cek visual live, di worktree terpisah.

## 2. Masalah yang ditemukan di lapangan

1. **Inspector lead** (komit `57cce776`): `DateField` **tanpa waktu** masih dirender sebagai input
   teks; ada juga label dobel (label baris + label internal `ClayDatePicker`).
2. **Dialog "Tambah Lead Baru"**: field `DateField` **tidak dirender sama sekali** — `supportsInput`
   di `LeadFormState` menolaknya, jadi user tidak bisa mengisi tanggal custom dari form utama.
3. **Tidak ada validasi**: teks `2026-13-45` diterima diam-diam; submit tidak diblokir.

## 3. Keputusan desain

### 3.1 Satu enum kecil sebagai sumber kebenaran paritas: `LeadFieldControl`

Tiga pertanyaan yang sebelumnya dijawab secara tersebar ("kontrol apa?", "bisakah diedit?",
"masuk form mana?") kini dijawab satu fungsi murni:

```kotlin
enum class LeadFieldControl { DATE_PICKER, DATE_TIME_TEXT, NUMERIC, TEXT; }

fun leadFieldControl(type: FieldType): LeadFieldControl = when (type) {
    is FieldType.DateField  -> if (type.withTime) DATE_TIME_TEXT else DATE_PICKER
    is FieldType.Number     -> if (type.format == NumberFormat.PLAIN) NUMERIC else TEXT
    is FieldType.Checkbox   -> TEXT        // sengaja: belum ada kontrol checkbox (jujur)
    is FieldType.UserRef    -> TEXT        // sengaja: belum ada kontrol user picker (jujur)
    is FieldType.Text,
    is FieldType.LongText,
    is FieldType.SingleSelect -> TEXT
}  // ← tanpa `else`: cabang dipaksa kompilator (Kontrak 6)
```

- **`withTime = true` tetap teks — bukan kegagalan.** `ClayDatePicker` belum punya pemilih jam;
  memaksakan picker berarti membuang informasi jam. Jujur lebih baik daripada terlihat selesai.
- **`Checkbox`/`UserRef` tetap teks — bukan kegagalan.** Komponennya memang belum ada; memalsukan
  kontrol berarti melanggar Kontrak 8. Ini utang yang tercatat, bukan yang disembunyikan.

### 3.2 `supportsInput` dirutekan lewat kontrol, bukan keputusan baru

`LeadFormState.supportsInput` tidak lagi punya logika sendiri — ia bertanya ke `leadFieldControl`
plus set kontrol form lead baru (`CREATE_FORM_CONTROLS`). Satu perubahan di pemetaan kontrol
otomatis mengalir ke: inspector, form lead baru, dan tes paritas.


### 3.3 Validasi tanggal: `isBlankOrIsoDate` via `DateTimeCodec`

```kotlin
fun isBlankOrIsoDate(raw: String): Boolean =
    raw.isBlank() || DateTimeCodec.parseIsoDateOrNull(raw.trim()) != null
```

- Kosong = sah (field boleh kosong).
- Selain itu harus kalender ISO nyata (`2026-02-30` ditolak) — **parser tunggal** yang sudah
  dipakai codec (Kontrak 4 variability: tidak ada parser kedua).
- Ditaruh di `commonMain` murni → aman dipanggil dari Wasm (ditemukan saat cek live: pemanggilan
  utilitas tertentu dari jalur klik Wasm melempar; parser murni menghilangkan kelas masalah ini).
- Dipakai dua arah: `isError` di UI (merah saat nilai rusak) **dan** `canSubmit` (blokir Simpan).

### 3.4 Label dobel

`ClayDatePicker` punya `label` internal. Di inspector CRM, label field sudah digambar header
barisnya → kirim `label = ""`. Pelajaran umum: **komponen `designsystem/` yang membawa label
sendiri butuh kontrak pemakaian yang eksplisit**; wrapper domain-lah yang memutuskan.

## 4. Tes paritas

`LeadFieldControlParityTest` (commonTest, 7 tes) — mengiterasi **semua varian** `FieldType`:

| Kelompok tes | Menjaga apa |
|---|---|
| `DATE (tanpa waktu)` → `DATE_PICKER` | Cabang utama slice ini |
| `DATE withTime` → `DATE_TIME_TEXT` | Jujur bahwa jam belum didukung picker |
| Semua varian `Number` → satu kontrol `NUMERIC`/`TEXT` | Varian format ≠ kontrol baru (Kontrak 2) |
| `DATE` masuk `CREATE_FORM_CONTROLS` | Form lead baru menerima field tanggal |
| `supportsInput` konsisten di kedua host | Inspector & form tidak berbeda diam-diam |
| Tanggal rusak → `isError` | Parser menolak `2026-13-45`, `bukan-tanggal` |
| `canSubmit` menolak draf bertanggal rusak | Validasi sampai ke gerbang simpan |

Tes ini **bukan** yet tes iterasi penuh gaya Kontrak 7 (SQL mapping, katalog agent, round-trip
codec) — untuk kosakata CRM itu sudah jadi tugas Irisan 1 Track A
(`CrmFieldTypeParityTest` di `core`). Yang Track C jaga adalah **paritas lapisan UI**.

## 5. Cara memverifikasi (yang sudah dilakukan)

1. `./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs
   :app:shared:compileKotlinJs :app:shared:jvmTest` → hijau di worktree **dan** setelah merge di
   `main`. (`assembleAndroidMain` membutuhkan mesin dengan Android SDK — tidak tersedia di mesin
   verifikasi; bukan karena perubahan.)
2. Cek visual live (server 8081 + web 3001, login demo superadmin, tenant `wemade-demo`):
   - **Inspector lead**: field inti *Target Closing* dan kolom kustom *Target Kirim* dirender
     picker, tanpa label dobel.
   - **Form lead baru**: *Target Kirim* kini muncul dengan input tanggal — bukti kontribusi utama.
   - **Lebar sempit 1280px**: dialog & kanban utuh, tidak ada kartu bertimpa.
   - Catatan jujur: simulasi ketik-tanggal-tidak-valid via otomasi CDP tidak bisa menyuntik teks
     ke canvas Compose; jalur `isError`/`canSubmit` dibuktikan lewat unit test.

## 6. Jebakan yang dihindari (baca sebelum menyentuh file ini lagi)

- **Jangan tulis kontrol input tipe field di layar fitur** — satu pintu: `FieldInput` untuk
  prototype, komponen `LeadCustomField*` untuk CRM host-nya (Kontrak 3).
- **Jangan tambah `else ->`** pada `when (FieldType)` mana pun — biarkan kompilator menunjuk
  titik pendaftaran (Kontrak 6).
- **Jangan memetakan tipe tanpa kontrol ke kontrol palsu** — jujur ke `TEXT` + catat utang
  (Kontrak 8), jangan mengarang `CheckboxPicker`.
- **Jangan duplikat parser tanggal** — semua lewat `DateTimeCodec`.

## 7. Sisa pekerjaan (utang tercatat)

- `ClayDatePicker` belum mendukung pemilihan **jam** → `DateField(withTime = true)` masih teks.
- Kontrol `Checkbox` dan `UserRef` belum ada → keduanya masih input teks.
- `assembleAndroidMain` belum dibuktikan di mesin tanpa Android SDK.
- Tes paritas lintas-lapisan penuh (SQL/katalog/codec untuk kosakata CRM) ada di Track A;
  kalau varian `FieldType` baru ditambah, jalankan **kedua** tes paritas (core + UI).
