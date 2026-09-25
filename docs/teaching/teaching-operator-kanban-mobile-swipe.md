# 🎓 Modul Pembelajaran: Kanban Responsif Mobile dengan HorizontalPager & Swipe Gestures

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform, Adaptive UI, HorizontalPager, Segmented Tab Switcher, Neo-Brutalism / Claymorphism  
> **Prasyarat**: Dasar Jetpack / Compose Multiplatform (`BoxWithConstraints`, `rememberPagerState`, `HorizontalPager`, `rememberCoroutineScope`)  
> **Referensi Task**: Lantai Produksi `/operator-exec` — Kanban meja operator di smartphone muncul 1 per 1 dan dapat di-swipe

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada papan Kanban tradisional 3 kolom (*Antrian*, *Sedang Dikerjakan*, *Selesai*), layar desktop (≥ 840dp) memiliki ruang lebar yang cukup untuk menampilkan ketiga kolom secara berdampingan.

Namun di layar smartphone (< 840dp, misalnya lebar 360dp–430dp):
- Jika dipaksakan tampil 3 kolom sekaligus berdampingan, kolom akan terhimpit sangat sempit dan kartu tidak terbaca.
- Jika memakai `horizontalScroll` bebas dengan lebar kolom tetap (misal 324dp), kolom terpotong setengah di tepi layar (*awkward cropping*), tidak memiliki batas henti (*page snapping*), dan pengguna harus terus menggeser secara manual tanpa indikasi jelas kolom mana yang sedang aktif.

### Analogi Sederhana
Bayangkan membaca koran 3 kolom di meja besar (desktop) vs membuka kartu flashcard di genggaman tangan (HP). Di tangan, kita fokus melihat **satu halaman penuh** (*1 per 1*), lalu membalik halaman berikutnya dengan jempol melalui gestur *swipe* lembut.

### Hasil Akhir yang Diharapkan
1. **Desktop / Tablet Lebar (≥ 840dp)**: Ketiga kolom tetap sejajar berdampingan (`weight(1f)`).
2. **Smartphone / Layar Sempit (< 840dp)**:
   - Kolom tampil **1 per 1** memenuhi lebar layar (`fillMaxWidth()`).
   - Pengguna dapat melakukan **swipe geser horizontal** ke kiri dan kanan dengan mulus (*HorizontalPager*).
   - Terdapat **Segmented Tab Switcher** di bagian atas (*Antrian*, *Sedang Dikerjakan*, *Selesai*) yang tersinkronisasi dua arah: menggeser halaman akan memperbarui tab aktif, dan mengetuk tab akan menganimasikan halaman ke kolom yang dipilih.
   - Header operator beradaptasi (nama operator dan tombol riwayat terstruktur rapi tanpa berhimpitan).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika seorang developer membangun tampilan adaptif ini dari nol:

1. **Langkah 1: Tinjau Breakpoint dan Batasan Ruang (`ClayBreakpoints.MasterDetail`)**
   - Gunakan `BoxWithConstraints` untuk membaca `maxWidth`.
   - Tetapkan ambang `isNarrow = maxWidth < ClayBreakpoints.MasterDetail` (840dp).
2. **Langkah 2: Ekstraksi Konten Kolom Menjadi Fungsi Reusable (`OperatorDeskColumnContent`)**
   - Agar tidak menduplikasi isi kolom (Antrian, Sedang Dikerjakan, Selesai) antara cabang desktop dan mobile, ekstrak fungsi terpisah yang menerima `column: OperatorDeskColumn`.
3. **Langkah 3: Bangun State Pager & Sinkronisasi Coroutine**
   - Inisialisasi `rememberPagerState { columns.size }`.
   - Siapkan `rememberCoroutineScope()` untuk menangani klik tab yang men-trigger `pagerState.animateScrollToPage(index)`.
4. **Langkah 4: Buat Segmented Tab Switcher**
   - Buat deretan tab clay (`ClayButton` bergaya `Ghost` saat inaktif, dan warna fungsional seperti `Primary`, `Accent`, `Success` saat aktif).
   - Sinkronkan `isSelected = pagerState.currentPage == index`.
5. **Langkah 5: Rangkai `HorizontalPager`**
   - Pasang `HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f), pageSpacing = ClaySpacing.Md)`.
   - Render halaman aktif sesuai `columns[page]`.
6. **Langkah 6: Uji Kompilasi Multiplatform (Wasm & JVM)**
   - Jalankan kompilasi untuk memastikan tidak ada konflik dependensi atau peringatan deprecation.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Mari kita bedah perubahan pada file `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/operator/OperatorStageDesk.kt`.

### Blok A: Pengkondisian Adaptif & Header Responsif
```kotlin
BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isNarrow = maxWidth < ClayBreakpoints.MasterDetail

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        if (isNarrow) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = board.stage.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = WeMadeColors.OnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    ClayButton(
                        text = "Riwayat (${board.history.size})",
                        style = ClayButtonStyle.Secondary,
                        onClick = onOpenHistory
                    )
                }
                ClayTextField(
                    value = operatorName,
                    onValueChange = onOperatorNameChange,
                    placeholder = "Nama operator",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Layout 1 baris untuk desktop
            ...
        }
```

**Mengapa blok ini ditulis begini?**
- Pada smartphone, layar selebar 360dp–400dp tidak cukup menampung judul meja, text field 220dp, dan tombol riwayat dalam 1 baris horizontal.
- Dengan memecah menjadi 2 baris vertikal saat `isNarrow`, input nama operator mendapat lebar penuh (`fillMaxWidth()`) dan tombol riwayat tetap mudah ditekan dengan ibu jari.

---

### Blok B: Segmented Tab Switcher Antar Kolom di HP
```kotlin
val columns = remember { OperatorDeskColumn.entries }
val pagerState = rememberPagerState(initialPage = 0) { columns.size }
val coroutineScope = rememberCoroutineScope()

Row(
    modifier = Modifier
        .fillMaxWidth()
        .clayFlat(
            shape = ClayShapes.Chip,
            background = WeMadeColors.SurfaceMuted,
            outline = WeMadeColors.Outline,
            borderWidth = ClayBorder.Hairline
        )
        .padding(ClaySpacing.Xs),
    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
) {
    columns.forEachIndexed { index, col ->
        val count = when (col) {
            OperatorDeskColumn.QUEUE -> board.queue.size
            OperatorDeskColumn.IN_PROGRESS -> board.inProgress.size
            OperatorDeskColumn.DONE -> board.doneToday.size
        }
        val isSelected = pagerState.currentPage == index
        val activeStyle = when (col) {
            OperatorDeskColumn.QUEUE -> ClayButtonStyle.Primary
            OperatorDeskColumn.IN_PROGRESS -> ClayButtonStyle.Accent
            OperatorDeskColumn.DONE -> ClayButtonStyle.Success
        }

        ClayButton(
            text = "${col.displayName} ($count)",
            style = if (isSelected) activeStyle else ClayButtonStyle.Ghost,
            fontSize = 11.sp,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
            onClick = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}
```

**Mengapa blok ini ditulis begini?**
1. **Two-Way Synchronization**: `pagerState.currentPage` menjadi *single source of truth*. Ketika pengguna melakukan gesture swipe di area kanban bawah, tab aktif otomatis berubah. Ketika pengguna mengklik salah satu tombol tab, coroutine memanggil `pagerState.animateScrollToPage(index)`.
2. **Kesesuaian Desain Neo-Brutalism**: Menggunakan kontainer `clayFlat` dengan `ClayShapes.Chip` dan tombol `ClayButton` bergaya `Ghost` ketika tidak terpilih, serta warna peran (`Primary`, `Accent`, `Success`) ketika aktif.
3. **Jumlah Item di Label Tab**: Memberikan visibilitas langsung kepada operator mengenai berapa kartu yang ada di kolom lain tanpa harus swipe terlebih dahulu (misal: "Antrian (3)", "Sedang Dikerjakan (1)", "Selesai (12)").

---

### Blok C: Pager Kanban Penuh & Ekstraksi Komponen
```kotlin
HorizontalPager(
    state = pagerState,
    modifier = Modifier.fillMaxWidth().weight(1f),
    pageSpacing = ClaySpacing.Md
) { page ->
    val col = columns[page]
    OperatorDeskColumnContent(
        column = col,
        board = board,
        isSubmitting = isSubmitting,
        timeZone = timeZone,
        finishLabel = finishLabel,
        canRework = canRework,
        onStart = onStart,
        onRelease = onRelease,
        onFinish = onFinish,
        onRework = onRework,
        modifier = Modifier.fillMaxSize()
    )
}
```

**Mengapa blok ini ditulis begini?**
- `HorizontalPager` bawaan `androidx.compose.foundation.pager` menangani akselerasi geser jari (*fling gesture*), batas snappiness, dan animasi transisi halaman secara native.
- `OperatorDeskColumnContent` menerima modifier `Modifier.fillMaxSize()`, sehingga tiap kolom kanban membentang 100% penuh di layar smartphone (*muncul 1 per 1*).
- `LazyColumn` di dalam `ClayKanbanColumn` otomatis mendukung *nested scrolling*: gestur vertikal menggeser daftar kartu SPK ke atas/bawah, sedangkan gestur horizontal menggeser kolom antar halaman tanpa bentrok.

---

## 🔬 4. Technology & Approach ("The Why")

| Pendekatan | Kelebihan | Kekurangan / Risiko |
|---|---|---|
| **`HorizontalPager` (Terpilih)** | - Native gesture fling & page snapping.<br>- Sinkronisasi mudah dengan `rememberPagerState`.<br>- Tampilan 1 per 1 fokus tanpa elemen terpotong. | Memerlukan manajemen state pager untuk tombol tab. |
| **`horizontalScroll` bebas dengan lebar tetap (Pendekatan Lama)** | Sederhana tanpa state pager. | Kolom terpotong di layar HP, tidak ada snap, pengguna harus menggeser presisi dengan tangan. |
| **Tab biasa tanpa gesture swipe** | Sederhana. | Kurang ergonomis di HP karena pengguna harus selalu menjangkau tab di bagian atas alih-alih menggeser langsung kartunya. |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Membiarkan `horizontalScroll` di dalam `HorizontalPager`**:
   - Jika kolom tetap dibungkus `horizontalScroll`, event touch horizontal akan ditelan oleh scroll row dan `HorizontalPager` tidak akan merespons gesture swipe. Pastikan `horizontalScroll` dihilangkan saat beralih ke Pager.
2. **Memanggil `pagerState.scrollToPage` secara synchronous tanpa coroutine**:
   - Fungsi navigasi pager bertipe `suspend fun`. Harus selalu diluncurkan melalui `coroutineScope.launch { ... }`.
3. **Hardcode index kolom (0, 1, 2) alih-alih iterasi `OperatorDeskColumn.entries`**:
   - Menggunakan `OperatorDeskColumn.entries` menjamin konsistensi bila urutan kolom di masa depan diperbarui atau diubah.

---

## ✅ 6. Verifikasi & Pengujian

1. **Verifikasi Kompilasi**:
   - `./gradlew :app:shared:compileKotlinWasmJs` → Sukses.
   - `./gradlew :app:shared:compileKotlinJvm` → Sukses.
   - `./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.operator.*"` → Seluruh test unit papan operator lolos.
2. **Verifikasi Tampilan di Browser / Mobile Viewport**:
   - Buka `http://localhost:3000/operator-exec`.
   - Buka DevTools (`F12`), aktifkan toggle *Device Toolbar* (pilih tampilan smartphone, misal iPhone 14 atau Galaxy S20).
   - Pastikan kanban tampil 1 per 1 memenuhi lebar layar.
   - Lakukan drag/swipe mouse atau touch ke kiri/kanan: kolom berpindah dengan mulus dan segmented tab di atas mengikuti status kolom aktif.
