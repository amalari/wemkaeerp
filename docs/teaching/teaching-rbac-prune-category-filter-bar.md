# 🎓 Modul Pembelajaran: Refactoring RBAC Toolbar — Pruning Category Filter Chips

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform, UI Layout Simplification, Clean UI/UX, State Pruning  
> **Prasyarat**: Dasar Jetpack Compose / Compose Multiplatform (`Row`, `Box`, `Modifier`), Pemahaman MVI State & Filtering  
> **Referensi Task**: Refactor Dynamic RBAC Screen — Remove Category Filter Chips  

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Ketika sebuah sistem berkembang, developer sering kali menambahkan filter di berbagai level tampilan secara berlebihan (cognitive overload). Pada layar **Manajemen Hak Akses & Jabatan Pabrik (RBAC)**, pengguna sudah memiliki:
1. **Segmented Perspective Switcher** utama: `[Per Modul]`, `[Per Divisi]`, dan `[Per Jabatan]` untuk mengubah sudut pandang analisis dan konfigurasi.
2. Setiap kartu modul sudah mengelompokkan tugas konveksi secara jelas dan ringkas (hanya ada 9 modul SaaS konveksi).

Ketika baris filter kategori (`Semua Modul`, `Penjualan`, `Gudang & Bahan Baku`, `Desain`, `Lantai Produksi`, `Kualitas`) diletakkan bersebelahan di baris yang sama, layout menjadi padat, membingungkan pengguna pabrik, dan menambah redundansi karena jumlah modul tidak cukup banyak untuk membutuhkan filter kategori ganda.

### Analogi Sederhana
Bayangkan sebuah rak pakaian di butik kecil yang hanya memajang 9 sampel baju. Jika pemilik toko memasang 6 plang pembatas kategori di atas rak tersebut, pembeli malah merasa toko tersebut penuh sesak dan terdistraksi. Dengan membiarkan semua 9 sampel terlihat rapi, pembeli dapat langsung meninjau seluruh katalog dalam satu pandangan.

### Hasil Akhir yang Diharapkan
- Menghapus deretan chip filter kategori horizontal dari header area kerja RBAC.
- Bar switcher perspektif (`Per Modul`, `Per Divisi`, `Per Jabatan`) berdiri rapi dan mandiri tanpa terdistraksi oleh chip filter.
- List kartu modul dalam mode `Per Modul` menampilkan seluruh modul yang tersedia secara bersih.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membersihkan UI filter yang redundan seperti ini, ikuti urutan berikut:

1. **Langkah 0: Audit Komponen UI & Konsumen State**
   - Cari di mana fungsi Composable filter dirender (`DynamicRbacScreen.kt`).
   - Cek apakah state filter (`selectedCategoryFilter`) masih digunakan oleh bagian lain yang esensial.
2. **Langkah 1: Rapikan Layout Penampung (Container)**
   - Saat filter dihapus dari baris yang menggunakan `Arrangement.SpaceBetween`, evaluasi apakah layout penampung masih membutuhkan pembungkus luar `Row(modifier = Modifier.fillMaxWidth(), ...)`.
   - Ubah agar switcher tombol berada di posisi yang stabil dan teratur.
3. **Langkah 2: Bersihkan Logika Pemfilteran di UI Composable**
   - Hapus ekspresi filter kategori pada `filteredModules` agar hanya menyaring modul berdasarkan pencarian teks (`searchQuery`) jika ada.
4. **Langkah 3: Hapus Dead Code / Unused Composables & Imports**
   - Hapus fungsi private `@Composable CategoryFilterBar`.
   - Bersihkan import `com.eventverse.app.domain.rbac.ModuleCategory` yang tidak lagi diperlukan di file screen.
5. **Langkah 4: Verifikasi Kompilasi & Tampilan Visual**
   - Jalankan `compileKotlinJvm` dan `compileKotlinWasmJs` untuk memastikan tidak ada kesalahan referensi.
   - Buka browser atau jalankan visual test untuk memeriksa estetika layout akhir.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Menyederhanakan View Mode Switcher Row

Sebelum refactoring:
```kotlin
// ❌ Baris penampung luar memisahkan Segmented Switcher dan Category Filter ke ujung kiri & kanan
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Row(
        modifier = Modifier.clayFlat(...),
        ...
    ) {
        // Switcher Per Modul, Per Divisi, Per Jabatan
    }

    if (state.viewMode == RbacViewMode.PER_MODULE) {
        CategoryFilterBar(...) // Chip filter kategori
    }
}
```

Sesudah refactoring:
```kotlin
// ✅ Switcher mandiri langsung berada di hierarki utama tanpa wrapper Row ekstra
Row(
    modifier = Modifier
        .clayFlat(
            shape = ClayShapes.Chip,
            background = WeMadeColors.SurfaceMuted,
            outline = WeMadeColors.Border
        )
        .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    RbacViewMode.entries.forEach { mode ->
        val isSelected = state.viewMode == mode
        Box(
            modifier = Modifier
                .then(
                    if (isSelected) {
                        Modifier.clayFlat(
                            shape = RoundedCornerShape(8.dp),
                            background = WeMadeColors.Surface,
                            outline = WeMadeColors.Outline,
                            borderWidth = 1.5.dp
                        )
                    } else {
                        Modifier.clip(RoundedCornerShape(8.dp))
                    }
                )
                .clickable { viewModel.onEvent(DynamicRbacUiEvent.SetViewMode(mode)) }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            // Konten icon + label tab
        }
    }
}
```

**Mengapa ditulis begini?**
- Menghilangkan wrapper `Row(fillMaxWidth(), SpaceBetween)` mencegah elemen tunggal melar atau melompat tak tentu arah saat anak keduanya dihilangkan.
- Switcher tetap mematuhi desain Claymorphism (`clayFlat`, `ClayShapes.Chip`).

---

### Blok B: Menyederhanakan Logika Filter Modul

Sebelum refactoring:
```kotlin
// ❌ Masih mengecek kategori yang nilainya sudah tidak diubah lagi dari UI
val filteredModules = BusinessModule.entries.filter { module ->
    (state.selectedCategoryFilter == null || module.category == state.selectedCategoryFilter) &&
            (state.searchQuery.isBlank() || module.displayName.contains(
                state.searchQuery,
                ignoreCase = true
            ) ||
                    module.description.contains(state.searchQuery, ignoreCase = true))
}
```

Sesudah refactoring:
```kotlin
// ✅ Murni menyaring berdasarkan pencarian teks pengguna
val filteredModules = BusinessModule.entries.filter { module ->
    state.searchQuery.isBlank() ||
            module.displayName.contains(state.searchQuery, ignoreCase = true) ||
            module.description.contains(state.searchQuery, ignoreCase = true)
}
```

**Mengapa ditulis begini?**
- Jika filter kategori di UI sudah ditiadakan, kondisi `module.category == state.selectedCategoryFilter` menjadi kondisi basi (*stale check*) yang berpotensi menyembunyikan modul jika suatu saat state tersebut tidak sengaja terisi nilai default yang salah.
- Kode menjadi lebih ekspresif dan mudah dibaca oleh developer lain.

---

### Blok C: Eliminasi Dead Code (`CategoryFilterBar`)

Fungsi `@Composable private fun CategoryFilterBar(...)` yang sebelumnya merender deretan chip dengan `ModuleCategory.entries.forEach` dihapus seluruhnya beserta import `com.eventverse.app.domain.rbac.ModuleCategory`.

**Prinsip**: *No dead composable left behind*. Jangan biarkan fungsi private menganggur jika tidak ada lagi pemanggilnya di dalam file, karena akan membebani compiler dan membingungkan developer yang membaca kode.

---

## ⚖️ 4. Technology & Approach ("The Why")

1. **Mengapa tidak sekadar menyembunyikannya dengan `if (false)` atau `Modifier.alpha(0f)`?**
   - Dalam Compose, menyembunyikan komponen dengan flag mati atau alpha menyisakan slot node di komposisi atau menyebabkan pemborosan alokasi recomposition. Menghapus kode secara tuntas (clean git history) adalah praktik rekayasa yang jauh lebih sehat.
2. **Mengapa mempertahankan `BusinessModule.entries.filter` untuk `searchQuery`?**
   - `searchQuery` adalah fungsionalitas pencarian global teks yang tetap berguna jika ke depannya diintegrasikan dengan search bar atau shortcut keyboard, sedangkan filter kategori chip memang spesifik dihilangkan sesuai kebutuhan UX pengguna.

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Lupa Merapikan Container Parent**:
   - Jika kamu hanya menghapus `CategoryFilterBar` tapi membiarkan `Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween)`, elemen yang tersisa mungkin akan berperilaku aneh atau meninggalkan ruang kosong yang tidak perlu.
2. **Menyisakan Unused Imports**:
   - Di Kotlin Multiplatform, import yang tidak terpakai sering kali memicu lint warning atau memperpanjang waktu analisa IDE. Selalu bersihkan import yang sudah tidak terpakai.
3. **Mengabaikan Platform Web (Wasm/JS)**:
   - Kompilasi JVM berhasil belum tentu kompilasi Wasm berhasil jika ada dependensi yang tidak sinkron. Selalu uji kedua target (`compileKotlinJvm` dan `compileKotlinWasmJs`).

---

## 🧪 6. Verifikasi & Tantangan Mandiri

### Verifikasi yang Telah Dilakukan
1. **JVM Compilation Check**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm
   ```
   *Hasil*: `BUILD SUCCESSFUL`.
2. **WasmJS Compilation Check**:
   ```bash
   ./gradlew :app:shared:compileKotlinWasmJs
   ```
   *Hasil*: `BUILD SUCCESSFUL`.
3. **Visual Inspection**:
   - Membuka halaman `/rbac` di browser.
   - Mengonfirmasi bahwa baris chip filter (`Semua Modul`, `Penjualan`, `Gudang`, dll.) sudah hilang dan hanya switcher `[Per Modul] [Per Divisi] [Per Jabatan]` yang tampak di pojok kiri atas dengan layout yang bersih.

### Tantangan Mandiri untuk Junior Developer
- [ ] Coba buat animasi transisi halus (`AnimatedContent`) ketika beralih di antara `Per Modul`, `Per Divisi`, dan `Per Jabatan`.
- [ ] Buat input pencarian (`ClayTextField`) opsional yang memanfaatkan `searchQuery` untuk menyaring kartu modul secara instan secara real-time.
