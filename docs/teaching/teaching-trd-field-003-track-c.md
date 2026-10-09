# 🎓 Modul Pembelajaran: Field `MULTI_SELECT` — Track C (UI) TRD-FIELD-003

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform, Clay Design System, tipe field pilihan ganda, nilai kanonik
> (JSON array), pemisahan komponen buta-domain vs pembungkus fitur, satu pintu `FieldInput`
> **Prasyarat**: Membaca `docs/trd/TRD-FIELD-003-multi-select.md` (FR-9, §4.6), `.claude/rules/design-system-rules.md`,
> `.claude/rules/field-component-rules.md`
> **Referensi Task**: Track C TRD-FIELD-003 (worktree `track-c-multiselect-ui`; A0 merge `71fdb948`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah nyata

Kosakata field prototype hanya punya `ENUM` — satu pilihan. Atribut yang berlabel ganda (alergi
pasien, layanan yang dibeli, jenis bahan) dipaksa jadi `TEXT` bebas (tak terstruktur, tak
tervalidasi) atau `ENUM` yang salah makna. A0 sudah menambah `FieldType.MULTI_SELECT` di domain,
tetapi lapisan UI-nya hanya **stub baca-saja**: ia menampilkan daftar teks, belum bisa **memilih**.
Kompilator memaksa cabangnya ada (tanpa `else`), jadi ia terlihat "ada" — persis penyakit yang
mendasari `field-component-rules.md`: tipe yang terdaftar tapi belum punya kontrol.

### Analogi

Bayangkan formulir medis kertas dengan kotak centang "Alergi": kamu boleh mencentang **lebih dari
satu** (Gigi, Jantung, Kulit), urutannya tidak penting, dan cara menuliskannya di rekap selalu
sama. Yang kita inginkan:

- Tiap opsi satu pil yang bisa ditekan/dilepas — bukan mengetik bebas.
- Nilai tersimpan selalu **bentuk yang sama** apa pun urutan kliknya, supaya bisa dibandingkan.
- Batas jumlah (mis. "maks 2") dijaga di kontrol, bukan hanya di pesan galat server.

### Hasil akhir yang diharapkan

1. Komponen dasar `ClayMultiChoiceChips` yang **buta domain** (menerima `List<String>`/`Set<String>`/
   lambda) dan berbahasa visual Clay.
2. `FieldInput` menggambar chip pilihan ganda di form, sel tabel inline, dan dialog detail kanban —
   satu pintu, bukan salinan per layar (FR-9).
3. Tampilan baca = daftar label dipisah `, ` lewat `displayValue`; belum ada pilihan → `—`.
4. Nilai disusun **kanonik** (urut menurut `options`, tanpa duplikat) lewat fungsi murni yang
   dipakai bersama codec core `MultiSelectValues`.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Urutan ini dari lapisan yang paling tidak bergantung ke yang paling bergantung:

```
Step 0  Baca kontrak §4.3 (bentuk nilai = string JSON array) & FR-2/FR-9 — jangan mengarang bentuk simpan.
Step 1  Komponen dasar BUTA DOMAIN   → presentation/designsystem/ClayMultiChoiceChips.kt
Step 2  Helper murni (penyusun nilai) → presentation/discovery/fields/MultiSelectFieldValue.kt
Step 3  Sambungkan ke satu pintu      → FieldInput.kt (cabang MULTI_SELECT)
Step 4  Tampilan baca                 → NumberFormatting.kt (displayValue)
Step 5  Konteks tabel/kanban          → TableCell/InlineRowEditor/KanbanDetailDialog (lewat FieldInput — tanpa perubahan)
Step 6  Tes murni + paritas tipe      → commonTest
Step 7  Kompilasi target + jalankan & LIHAT dengan mata
```

**Kenapa komponen dasar dulu?** Karena satu kontrol yang sama dipakai di **tiga** konteks (form, sel
tabel, dialog kanban) — semuanya lewat `FieldInput`. Kalau baris chip ditulis langsung di `FieldInput`
saja tanpa diangkat, ia akan tersalin saat konteks keempat muncul. Komponen netral diangkat lebih
dulu (Kontrak 4: Aturan Tiga Kali), baru dipakai.

---

## 🔬 3. Pembedahan Kode Blok per Blok

### 3.1 `ClayMultiChoiceChips.kt` — komponen netral yang buta domain

```kotlin
@Composable
fun ClayMultiChoiceChips(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelOf: (String) -> String = { it },
    maxSelections: Int? = null,
    enabled: Boolean = true
) {
    if (options.isEmpty()) return
    ClayFlowRow(modifier = modifier, spacing = ClaySpacing.Xs) {
        val atLimit = maxSelections != null && selected.size >= maxSelections
        options.forEach { option ->
            val isSelected = option in selected
            ClayChoiceChip(
                text = labelOf(option),
                selected = isSelected,
                onClick = { onToggle(option) },
                enabled = enabled && (isSelected || !atLimit)
            )
        }
    }
}
```

Mental model: komponen **tidak tahu** apa itu `FieldSpec`, `MULTI_SELECT`, atau JSON. Ia hanya tahu
"ada daftar label, ada himpunan yang terpilih, ada aksi toggle". Itu sebabnya ia menerima
`List<String>`/`Set<String>`/`Int?` dan lambda — bukan domain. Aturan `design-system-rules.md`
Kontrak 6 mengunci ini; skrip review mengeceknya (`grep import ...domain... designsystem/` harus kosong).

Dua keputusan kecil yang penting:

- **`if (options.isEmpty()) return`** — mencegah `FlowRow` kosong yang tetap memakan spasi vertikal.
- **`enabled = enabled && (isSelected || !atLimit)`** — saat batas tercapai, opsi yang **belum**
  terpilih dinonaktifkan, tetapi opsi yang **sudah** terpilih tetap bisa dilepas. Menonaktifkan
  semuanya akan mengunci user di luar ; ini detail UX yang gampang terlewat.

### 3.2 `MultiSelectFieldValue.kt` — penyusun nilai murni

```kotlin
fun multiSelectToggleValue(current: String, option: String, options: List<String>): String {
    val selected = MultiSelectValues.parse(current).orEmpty().toSet()
    val next = if (option in selected) selected - option else selected + option
    return MultiSelectValues.encode(next, options)
}
```

Ini **jantung konsistensi** Track C. Alih-alih menyusun JSON sendiri di Composable (rawan format),
kita menyerahkan ke `MultiSelectValues.encode` milik core: hasilnya selalu urut menurut `options`,
tanpa duplikat, membuang elemen di luar `options`, dan tanpa pilihan = `""` (bukan `"[]"` — FR-3).

Mengapa fungsi **murni terpisah**, bukan inline di `FieldInput`? Supaya bisa diuji tanpa Compose.
Tes `MultiSelectFieldSupportTest` memverifikasi bahwa klik berurutan "Kulit" lalu "Gigi"
menghasilkan `["Gigi","Kulit"]` (urut opsi, bukan urut klik) — properti yang tidak bisa dibuktikan
lewat uji render biasa.

### 3.3 `FieldInput.kt` — satu pintu kontrol input

```kotlin
FieldType.MULTI_SELECT -> {
    val selected = MultiSelectValues.parse(value).orEmpty().toSet()
    ClayMultiChoiceChips(
        options = field.options,
        selected = selected,
        onToggle = { option -> onValueChange(multiSelectToggleValue(value, option, field.options)) },
        maxSelections = field.maxSelections,
        enabled = enabled
    )
}
```

`FieldInput` adalah satu pintu (Kontrak 3) untuk form blok, sel tabel, dan dialog kanban. Karena itu
cabang MULTI_SELECT dipasang **di sini**, bukan disalin ke tiap layar. Efeknya: `TableCell` (mode
edit), `InlineRowEditor`, dan `KanbanDetailDialog` **tidak perlu diubah** — mereka sudah memanggil
`FieldInput`.

### 3.4 `NumberFormatting.kt` — tampilan baca (`displayValue`)

```kotlin
FieldType.MULTI_SELECT -> MultiSelectValues.parse(stored)?.joinToString(", ")
    ?.ifEmpty { "—" } ?: stored.ifEmpty { "—" }
```

Tiga kasus yang dibedakan dengan sengaja:

- **Ada pilihan** → daftar label dipisah `, ` (`["Gigi","Jantung"]` → `Gigi, Jantung`).
- **Kosong / `"[]"`** → `—` (belum ada pilihan).
- **Nilai tak sah** (bukan array JSON, mis. data lama) → ditampilkan **apa adanya**, tidak
  disembunyikan di balik `—`. Ini meniru perilaku DATE (`nilai tak sah tidak disembunyikan`), supaya
  data rusak tetap terlihat dan bisa didiagnosis, bukan diam-diam hilang.

Catatan: pola `—` ini sudah dipakai lapisan lain (mis. `relationDisplay` mengembalikan `—` untuk
nilai kosong), jadi kanban card yang memanggil `displayValue` pun konsisten.

### 3.5 Konteks tabel & kanban — gratis dari satu pintu

- **`TableCell`**: mode baca memakai cabang generik `fieldSpec?.displayValue(rawValue) ?: rawValue`,
  lalu `.ifEmpty { "—" }` — sudah benar tanpa perubahan. Mode edit memakai `FieldInput` → chip ganda.
- **`InlineRowEditor`**: memanggil `FieldInput` per kolom → chip ganda.
- **`KanbanDetailDialog`**: memanggil `FieldInput` per field → chip ganda.

Prinsipnya: **jangan sentuh** tiga file itu kalau tidak perlu. Menambah override khusus
MULTI_SELECT di tiap konteks justru menciptakan tiga salinan logika yang akan menyimpang.

---

## 🏗️ 4. "Why" — Keputusan Arsitektur & Trade-off

| Keputusan | Alasan | Alternatif yang ditolak |
|---|---|---|
| Komponen netral `ClayMultiChoiceChips` di `designsystem/` | Sekali benar, dipakai 3 konteks (Kontrak 4) | Menyalin baris chip di `FieldInput`+layar → menyimpang |
| Nilai disusun `MultiSelectValues.encode` (core) | Satu sumber aturan dengan codec/reducer | Menyusun JSON sendiri di UI → format bisa beda |
| Helper murni `multiSelectToggleValue` | Bisa diuji tanpa Compose | Inline di Composable → hanya bisa diuji lewat render |
| `Set<String>` sebagai parameter `selected` | Cek keanggotaan O(1), makna "terpilih" jelas | `List<String>` → cek berulang & rawan duplikat |
| `—` untuk kosong, nilai tak sah apa adanya | Jujur; data rusak tidak disembunyikan | Mengembalikan `""` untuk semua → beda dengan instruksi & tak terlihat |
| Tanpa perubahan di `TableCell`/`Inline`/`Kanban` | Mereka sudah pakai `FieldInput` & `displayValue` | Override khusus per konteks → tiga salinan |

---

## 🪤 5. Jebakan Umum yang Dihindari

1. **Menyusun JSON manual di Composable.** Rawan tanda kutip/urutan. Selalu lewat
   `MultiSelectValues.encode`.
2. **Memakai urutan klik sebagai urutan nilai.** Nilai sama harus menghasilkan string sama
   (byte-stabil); urut menurut `options`.
3. **`"[]"` sebagai bentuk kosong.** Satu bentuk kosong saja: `""` (FR-3). `encode` sudah menjamin.
4. **Literal warna / `Modifier.shadow()` / `Card`-`Button` Material.** Semua gaya lewat token Clay;
   chip dasar memakai `ClayChoiceChip` yang sudah ada.
5. **Impor `domain/` di `designsystem/`. ** `ClayMultiChoiceChips` hanya menerima `String`/`Set`/`Int`/lambda.
6. **`else ->` pada `when (FieldType)`.** Cabang MULTI_SELECT ditulis eksplisit; kompilator-lah yang
   mengingatkan titik pendaftaran (Kontrak 6).
7. **Menonaktifkan semua chip saat batas tercapai.** Opsi yang sudah terpilih harus tetap bisa dilepas.

---

## ✅ 6. Verifikasi

```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinJs \
          :app:shared:compileKotlinWasmJs :app:shared:assembleAndroidMain :app:shared:jvmTest
```

Lalu **dijalankan dan dilihat**: login demo superadmin (`/login` → "Demo Mode: Masuk Cepat"),
buka layar prototype dengan entitas ber-`MULTI_SELECT`, dan periksa:

- Form: chip bisa dipilih >1; menekan chip terpilih melepasnya; saat `maxSelections` tercapai, chip
  yang belum terpilih meredup.
- Sel tabel (mode edit): chip ganda; mode baca: daftar label dipisah `, ` / `—` bila kosong.
- Kanban: dialog detail menampilkan chip ganda; kartu menampilkan daftar label.

Tes murni: `MultiSelectFieldSupportTest` (displayValue + penyusunan nilai) dan
`PrototypeFieldControlParityTest` (paritas kontrol tiap `FieldType.entries`).

---

## 🔭 7. Tindak Lanjut (dicatat, bukan dikerjakan di sini)

- **Cek visual manual** di build 5-target (khusus Android) belum dapat dijalankan di lingkungan
  tanpa Android SDK; verifikasi yang tersedia: 4 target (JVM/JS/WasmJs/jvmTest) hijau.
- **Track A sisa** (core): `SetFieldMaxSelections`, `ProposalEdit`, `FieldHint`/`ScreenSuggestionCodec`
  paritas lengkap — `maxSelections` sudah dibaca UI, tetapi operasi penyuntingannya belum.
- **Risiko tampilan kartu**: `CardStyle.BADGE` untuk `MULTI_SELECT` kini menampilkan daftar label
  (bukan JSON mentah) berkat `displayValue`; gaya chip khusus di kartu, bila diinginkan, adalah
  keputusan desain lanjutan.
