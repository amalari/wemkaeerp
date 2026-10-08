# 🎓 Modul Pembelajaran: Input Multiline ClayTextArea & Paritas Tipe LONG_TEXT di KMP

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform, Design System (Claymorphism), Paritas Tipe Field, Clean Architecture, Test Parity  
> **Prasyarat**: Dasar Compose UI (Modifier, TextField, KeyboardOptions), Kotlin Multiplatform (KMP), Pemahaman DDD & Single Source of Truth  
> **Referensi Plan**: [PLAN-field-component-gaps.md](../../docs/plannings/PLAN-field-component-gaps.md) (Irisan 2 Track C — C3 `LONG_TEXT`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat merancang sistem low-code / form builder fleksibel (seperti Prototype Renderer atau modul CRM Custom Field), tipe data sering kali memiliki variasi panjang:
1. **Single-line Text (`TEXT`)**: Nama, kode barang, nomor telepon singkat.
2. **Multi-line Text (`LONG_TEXT` / `LongText`)**: Catatan khusus, instruksi kerja, alamat, deskripsi keluhan prospek.

Jika sistem menyamakan perlakukan keduanya:
- Pengguna yang mengisi catatan panjang di dalam kotak satu baris (`singleLine = true`) akan frustrasi karena teks terpotong secara visual dan kursor harus digeser horizontal terus-menerus.
- Jika pengguna menekan tombol `Enter` di tabel inline saat mengisi deskripsi, sistem bisa mengira pengguna ingin "submit / selesai edit" padahal mereka hanya ingin membuat baris baru (*newline*).
- Jika ada `else ->` pada `when (fieldType)` di UI state, penambahan tipe baru di masa depan akan secara senyap jatuh ke perilaku default teks tanpa peringatan kompilator (pelanggaran Kontrak 6 & 8 `field-component-rules`).

### Analogi Sederhana
Bayangkan sebuah formulir kertas. Kolom "Nama Lengkap" disediakan satu garis tipis (cukup untuk satu baris). Tetapi kolom "Alamat Lengkap / Catatan Tambahan" disediakan kotak luas bergaris-garis (3–4 baris). Anda tidak bisa memaksakan alamat 3 paragraf masuk ke dalam garis satu baris nama tanpa merusak keterbacaan.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun kontrol input multi-baris dan menyambungkannya ke platform, urutannya adalah:

```
[Design System] (ClayTextArea) 
     ↓
[Shared UI Input] (FieldInput - pemetaan FieldType.LONG_TEXT)
     ↓
[Table / Kanban Context] (TableCell Shift+Enter, InlineRowEditor)
     ↓
[UI State Reducers] (InteractiveFormState & InteractiveTableState tanpa else)
     ↓
[CRM Integration] (LeadFieldControl, LeadFormState, LeadCustomField)
     ↓
[Test Parity] (PrototypeFieldControlParityTest & LeadFieldControlParityTest)
```

1. **Langkah 1: Komponen Dasar di Design System (`presentation/designsystem`)**
   - Bangun `ClayTextArea` berbasis `ClayTextField` yang sudah ada. Jangan buat dari nol jika `ClayTextField` sudah memiliki logika border, clay offset, focus state, dan validasi error.
   - Set parameter default: `singleLine = false`, `minLines = 3`.
2. **Langkah 2: Sambungkan ke Pintu Masuk Kontrol Bersama (`fields/FieldInput`)**
   - Pisahkan cabang `FieldType.TEXT` dan `FieldType.LONG_TEXT`.
   - Gunakan `ClayTextArea` untuk `LONG_TEXT` dengan dukungan mode `compact` (`minLines = 2` saat di sel sempit, `3` pada form luas).
3. **Langkah 3: Atur Interaksi Keyboard di Sel Tabel (`TableCell`)**
   - Tangani `onPreviewKeyEvent`: jika field adalah `LONG_TEXT` dan `isShiftPressed == true`, jangan cegat event `Key.Enter` agar `BasicTextField` dapat membuat baris baru.
4. **Langkah 4: Tutup Semua Celah `else` di State Holder**
   - Pastikan `InteractiveFormState.resetForm()` dan `InteractiveTableState.startInlineCreate()` menangani setiap entri `FieldType` secara eksplisit.
5. **Langkah 5: Paritas di CRM Domain**
   - Pisahkan `LeadFieldControl.LONG_TEXT` dari `TEXT`.
   - Update `LeadCustomFieldInputs` dan `LeadCustomField` agar inspektur lead merender area multi-baris saat tipe adalah `FieldType.LongText`.
6. **Langkah 6: Validasi lewat Tes Paritas (Automated Tests)**
   - Tulis `PrototypeFieldControlParityTest` untuk memastikan form reset, table inline create, form submit, dan table inline edit berjalan sukses di JVM test runner.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Komponen Multi-line Reusable di Design System (`ClayTextField.kt`)

```kotlin
@Composable
fun ClayTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    minLines: Int = 3,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null
) {
    ClayTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        singleLine = false,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        errorMessage = errorMessage,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        focusRequester = focusRequester
    )
}
```

**Mengapa ditulis begini?**
- **Prinsip Don't Repeat Yourself (DRY)**: Kita tidak menduplikasi box rendering, perhitungan outline, atau shadow clay. `ClayTextArea` adalah *semantic wrapper* yang mengunci konfigurasi `singleLine = false` dan memberikan tinggi minimum awal (`minLines = 3`).
- **Aesthetic First**: Teks multi-baris dengan `minLines = 3` langsung memberi sinyal visual ke pengguna bahwa area ini dimaksudkan untuk penjelasan panjang, bukan sekadar kata tunggal.

---

### Blok B: Penanganan Keyboard di Editor Inline Sel Tabel (`TableCell.kt`)

```kotlin
.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        when {
            event.key == Key.Enter && (field.type != FieldType.LONG_TEXT || !event.isShiftPressed) -> {
                state.submitCellEdit(row.id, column)
                true
            }
            event.key == Key.Escape -> {
                state.cancelCellEdit()
                true
            }
            else -> false
        }
    } else false
}
```

**Mengapa ditulis begini?**
- Di spreadsheet modern (Google Sheets, Notion, Airtable):
  - Menekan **`Enter`** biasa pada sel -> Menyimpan dan menutup editor sel.
  - Menekan **`Shift + Enter`** pada sel multi-baris -> Menambahkan baris baru (*newline*) di dalam sel.
- Dengan kondisi `(field.type != FieldType.LONG_TEXT || !event.isShiftPressed)`, event `Shift + Enter` diteruskan ke `BasicTextField` sehingga baris baru terbentuk, sementara `Enter` biasa tetap mengirimkan perubahan.

---

### Blok C: State Exhaustive Exhaustion Tanpa `else` (`InteractiveFormState.kt`)

```kotlin
formValues[f.key] = when (f.type) {
    FieldType.BOOL -> "tidak"
    FieldType.ENUM -> f.options.firstOrNull().orEmpty()
    FieldType.TEXT, FieldType.LONG_TEXT, FieldType.NUMBER, FieldType.DATE -> ""
}
```

**Mengapa ditulis begini?**
- **Aturan Proyek (Kontrak 6)**: Dilarang menggunakan `else ->` pada `when (FieldType)`.
- Jika di kemudian hari ada `FieldType.MULTI_SELECT` atau `FieldType.FILE`, kompilator Kotlin akan langsung melempar error saat build jika developer lupa menambahkan inisialisasi default-nya di sini.

---

### Blok D: Integrasi CRM Form & Inspector (`LeadCustomFieldInputs.kt`)

```kotlin
is FieldType.LongText -> ClayTextArea(
    value = value,
    onValueChange = { form.update(f.fieldId, it) },
    label = label,
    minLines = 3,
    modifier = Modifier.fillMaxWidth()
)
```

**Mengapa ditulis begini?**
- Di form pembuatan deal/lead baru CRM, deskripsi kebutuhan pelanggan yang menggunakan `FieldType.LongText` kini secara seragam menggunakan `ClayTextArea`.
- Menggunakan komponen design system yang sama di seluruh vertikal (Prototype Discovery & CRM) menjaga konsistensi visual aplikasi WeMade ERP.

---

## 🧪 4. Mengapa Kita Butuh Parity Tests?

Lihat pengujian di `PrototypeFieldControlParityTest.kt`:

```kotlin
@Test
fun allFieldTypes_handledInFormResetAndTableInline() {
    val testedTypes = allFields.map { it.type }.toSet()
    assertEquals(FieldType.entries.toSet(), testedTypes, "Semua FieldType wajib diuji kontrolnya")
    ...
}
```

1. **Jaminan Kelengkapan**: Jika seorang developer menambahkan `FieldType` baru di `core/`, tes ini akan gagal jika tipe tersebut belum didaftarkan di form reset dan tabel inline.
2. **KMP Async/Coroutine Safety**: Pengujian tabel inline menggunakan coroutine dispatcher (`testScope.advanceUntilIdle()`) untuk memverifikasi bahwa mutasi state asinkronus selesai dengan benar sebelum pengujian assertion dievaluasi.

---

## 🎯 5. Jebakan Umum (Common Pitfalls) yang Dihindari

1. **Jebakan `minLines` pada `singleLine = true`**:
   `BasicTextField` di Compose akan melempar `IllegalArgumentException` saat runtime jika `minLines > 1` sementara `singleLine = true`. `ClayTextArea` memastikan `singleLine = false` sehingga aman.
2. **Jebakan Fallback Senyap**:
   Menyamakan `LONG_TEXT` dengan `TEXT` tanpa area multi-baris melanggar Kontrak 8 ("komponen belum ada tidak dipalsukan menjadi teks satu baris biasa").
3. **Jebakan Multiplatform ABI**:
   Semua styling outline, border, dan typography menggunakan token dari `WeMadeColors` dan `ClaySpacing`, memastikan konsistensi piksel sempurna di JVM (Desktop), WasmJS, dan JS.
