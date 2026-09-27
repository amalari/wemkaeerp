# 🎓 Modul Pembelajaran: Penanganan Tombol Tab & Auto-Commit pada Komponen Input Tag (ClayTagInput)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Keyboard Navigation, Focus Management, State Commitment, Compose Multiplatform  
> **Prasyarat**: Paham `BasicTextField`, `LocalFocusManager`, `onPreviewKeyEvent`, dan arsitektur form sampling  
> **Referensi Task**: "intruksi panah dan tenselity ini kalau tab jadi ga ke save gitu, handle tab juga"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat mengisi form garmen pada tahap **Program CAM**, operator sampling biasanya mengetik dengan cepat menggunakan keyboard fisik:
1. Mengetik **Kode Program CAM** (mis. `Test`) → menekan tombol `Tab`.
2. Fokus berpindah ke field **Instruksi Panah** → mengetik `1. test` → menekan tombol `Tab` untuk lanjut ke field **Tenselity**.
3. Di field **Tenselity** → mengetik `test` → menekan tombol `Tab`.

**Apa yang terjadi sebelumnya?**
Komponen `ClayTagInput` hanya mendengarkan penekanan tombol `Enter` (`ImeAction.Done`) atau karakter koma (`,`) di dalam event `onValueChange`.
Ketika pengguna menekan tombol `Tab` di keyboard:
- Teks yang baru saja diketik (`input`) masih menggantung di memory lokal komponen dan **belum di-commit** menjadi tag (`onTagsChange` belum dipanggil).
- Fokus keyboard berpindah, meninggalkan teks tersebut tanpa pernah tersimpan ke state order / autosave.
- Begitu form disimpan atau dicek validasinya, Instruksi Panah dan Tenselity terdeteksi kosong atau tidak tersimpan ("jadi ga ke save gitu").

### Solusi & Mental Model
Sebuah input tag modern (seperti di GitHub / Jira / Gmail) harus memiliki 3 jalur komitmen:
1. **Explicit Key Commit**: Menekan `Enter`, koma (`,`), atau **`Tab`**.
2. **Focus Navigation**: Saat tombol `Tab` ditekan ketika ada teks, sistem harus secara atomik menambahkan tag baru **dan** memajukan fokus ke input berikutnya (`focusManager.moveFocus(FocusDirection.Next)`). Jika `Shift + Tab` ditekan, fokus mundur ke input sebelumnya.
3. **Blur/Unfocus Auto-Commit**: Jika pengguna mengetik teks lalu mengklik elemen lain dengan mouse (losing focus), event `onFocusChanged` harus mendeteksi `!isFocused && input.isNotBlank()` dan otomatis meng-commit teks tersebut menjadi tag.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0: Telusuri Komponen Input Tag Bersama (`ClayTagInput.kt`)**
   Cari di mana field instruksi panah dan tenselity didefinisikan. Keduanya menggunakan komponen bersama `ClayTagInput`.

2. **Langkah 1: Sediakan Multi-Item Addition Helper (`addTags`)**
   Perbaiki fungsi penambahan tag agar mendukung pemecahan batch (koma `,` dan tab `\t`) tanpa race-condition penimpaan state:
   ```kotlin
   fun addTags(newItems: List<String>) {
       val filtered = newItems
           .map { it.trim().removeSuffix(",").removeSuffix("\t") }
           .filter { it.isNotBlank() }
       if (filtered.isNotEmpty()) {
           currentOnTagsChange(currentTags + filtered)
           input = ""
       }
   }
   ```

3. **Langkah 2: Pasang Keyboard Interceptor (`onPreviewKeyEvent`)**
   Tangkap event `Key.Tab` saat `KeyEventType.KeyDown`:
   - Jika `input.isNotBlank()`, panggil `addTag(input)`.
   - Gunakan `focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)`.
   - Kembalikan `true` agar event dikonsumsi secara rapi dan tidak memicu input karakter aneh di browser.

4. **Langkah 3: Pasang Safety Net pada Blur (`onFocusChanged`)**
   Saat komponen kehilangan fokus (`!focusState.isFocused`), jika ada `input` yang belum kosong, panggil `addTag(input)`.

5. **Langkah 4: Perbarui Teks Petunjuk Placeholder**
   Beri tahu pengguna bahwa tombol `Tab` sekarang didukung sejajar dengan `Enter` dan koma.

---

## 🔍 3. Bedah Kode Blok per Blok

### File: `app/shared/.../presentation/designsystem/ClayTagInput.kt`

#### Blok A — Keyboard Tab Interceptor & Focus Mover
```kotlin
val focusManager = LocalFocusManager.current
...
.onPreviewKeyEvent { event ->
    if (event.key == Key.Tab && event.type == KeyEventType.KeyDown) {
        if (input.isNotBlank()) {
            addTag(input)
        }
        focusManager.moveFocus(
            if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
        )
        true
    } else {
        false
    }
}
```
- **`onPreviewKeyEvent`**: Mengintersepsi event keyboard sebelum ditelan oleh implementasi internal `BasicTextField`.
- **`Key.Tab && KeyEventType.KeyDown`**: Hanya bereaksi saat tombol ditekan (bukan saat dilepas/KeyUp).
- **`focusManager.moveFocus(...)`**: Memindahkan fokus ke elemen focusable berikutnya sesuai urutan traversal Compose. `event.isShiftPressed` memastikan navigasi mundur saat pengguna menekan `Shift+Tab`.
- **`return true`**: Mengonsumsi event keyboard sehingga browser tidak menjalankan aksi default yang tidak diinginkan.

#### Blok B — Auto-Commit on Blur
```kotlin
.onFocusChanged { focusState ->
    if (!focusState.isFocused && input.isNotBlank()) {
        addTag(input)
    }
    isFocused = focusState.isFocused
}
```
- Jika pengguna mengetik tetapi tidak menekan Enter/Tab/Koma, melainkan langsung mengklik tombol "Mulai Pembuatan" atau mengklik tab lain, `focusState.isFocused` berubah menjadi `false`.
- Kondisi ini langsung meng-commit teks yang tertinggal sehingga data tidak hilang saat pengguna beralih konteks.

---

## 🛡️ 4. Jebakan Pemula (Common Pitfalls)

1. **Hanya Menangkap `\t` di `onValueChange`**:
   - `BasicTextField` dengan `singleLine = true` di Compose Web/Desktop sering kali **tidak memasukkan** karakter `\t` ke dalam string `onValueChange`, melainkan memicu navigasi fokus default sistem. Jika hanya mengandalkan `onValueChange.contains("\t")`, penekanan Tab tetap gagal membuat tag. Harus dikombinasikan dengan `onPreviewKeyEvent`.

2. **Lupa Menangani `Shift + Tab`**:
   - Jika `focusManager.moveFocus` selalu dipanggil dengan `FocusDirection.Next`, pengguna yang ingin mundur dengan `Shift + Tab` justru akan dipaksa maju ke depan. Selalu periksa `event.isShiftPressed`.

3. **Duplikasi Tag saat Tab Ditekan**:
   - Jika `onPreviewKeyEvent` memanggil `addTag(input)` yang mengosongkan `input = ""`, lalu fokus berpindah dan memicu `onFocusChanged`, pastikan `onFocusChanged` mengecek `input.isNotBlank()`. Karena `input` sudah kosong, tag tidak akan tertambah dua kali.

---

## 🧪 5. Verifikasi & Tantangan Mandiri

### Langkah Verifikasi
1. Buka http://localhost:3000/sampling-order dan buka modal Detail SPK pada tahap **Program CAM**.
2. Masukkan Kode Program CAM, lalu tekan `Tab`. Fokus berpindah ke **Instruksi Panah**.
3. Ketik `1. jarum kanan` lalu tekan `Tab`.
   - **Hasil**: Teks `1. jarum kanan` seketika berubah menjadi chip tag berurutan `#1`, dan fokus otomatis berpindah ke **Tenselity**.
4. Di field **Tenselity**, ketik `30 tension` lalu tekan `Tab`.
   - **Hasil**: Teks `30 tension` otomatis menjadi tag dan fokus berpindah ke **Catatan Rumus Pola**.
5. Indikator di kanan atas header modal langsung menampilkan `Tersimpan ✓` yang menandakan autosave berjalan sukses tanpa data hilang.
