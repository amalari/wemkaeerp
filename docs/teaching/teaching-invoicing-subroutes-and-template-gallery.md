# 🎓 Modul Pembelajaran: Sub-Route Desain Invoice & Katalog Layout Berdasarkan Jenis Tagihan

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Kotlin Multiplatform Routing, Browser History API (pushState & popstate), WasmJS Navigation, Domain-Driven Design (applicableKinds), Compose Multiplatform Master-Detail  
> **Prasyarat**: Memahami enum `AppNavScreen`, kontrak `PlatformNavigation`, dan struktur domain `InvoiceTemplate`  
> **Referensi Task**: Sub-route reloadable `/invoicing/templates`, galeri template per jenis faktur (Sample Invoice garmen), dan context-aware canvas preview

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
1. **Kehilangan Status Saat Reload (F5 Problem)**: Sebelumnya, membuka desainer faktur hanya mengubah state internal memori (`isDesignerOpen = true`) tanpa mengubah URL di address bar (`/invoicing`). Jika pengguna tanpa sengaja menekan F5 atau browser crash, mereka langsung terlempar kembali ke daftar invoice dan kehilangan pekerjaan desainnya.
2. **Kebutuhan Layout Tiap Jenis Tagihan Berbeda Drastis**: Di industri konveksi/garmen:
   - **Sample Invoice**: Menagih pembuatan prototipe (ongkos pola & grading, kain sample 2 meter, jahit sample, catatan fitting review).
   - **DP Produksi**: Menagih uang muka 50% dari total nilai kontrak PO massal.
   - **Pelunasan (Settlement)**: Menagih sisa pembayaran setelah potong DP, mencantumkan nomor surat jalan dan rincian ukuran/size pack.
   Jika template tidak memiliki label peruntukan jenis tagihan yang jelas, operator pabrik bisa salah mencetak faktur sample dengan layout DP produksi.

### Analogi Sederhana
Bayangkan sebuah rumah sakit yang memiliki berbagai jenis form cetak: resep obat, kwitansi lab, dan surat rujukan. Anda tidak bisa menggunakan satu blangko polos yang sama untuk semuanya. Harus ada **katalog template** terorganisir per jenis kebutuhan, dan meja kerjanya memiliki alamat ruangan sendiri (URL sub-route) agar perawat yang kembali dari istirahat bisa langsung kembali ke form yang sedang diisi tanpa harus melapor ke resepsionis (tabel utama) dari awal.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun fitur seperti ini dari nol, ikuti urutan berikut:

1. **Langkah 0 — Pemodelan Domain (`core/`)**:
   Pastikan agregat `InvoiceTemplate` memiliki pemetaan jenis tagihan (`applicableKinds: Set<InvoiceKind>`). Jangan gunakan string bebas, gunakan enum terpadu.
2. **Langkah 1 — Routing Kontrak & Subpath (`app/shared/.../navigation/`)**:
   - Definisikan rute baru di `AppNavScreen` (`/invoicing/templates`).
   - Beri tanda `isNavMenuItem = false` agar rute sekunder tidak menimpa menu primer di sidebar drawer.
   - Perbarui parser URL (`AppNavScreen.fromPath`) agar mampu mengenali subpath berparameter (`/invoicing/templates/{id}`).
   - Buat helper ekstraksi ID (`extractTemplateId`).
   - Tulis unit test untuk mengunci perilaku URL parser.
3. **Langkah 2 — Data Pratinjau Kanvas Kontekstual (`TemplateDesignerUiState.kt`)**:
   Sediakan generator data dummy yang realistis untuk jenis tagihan target (misal `createDummySamplePreviewInvoice` untuk garmen sampling).
4. **Langkah 3 — UI Galeri / Katalog Layout (`InvoiceTemplateGalleryScreen.kt`)**:
   Buat antarmuka katalog dengan filter bar jenis tagihan, kartu layout (`ClayCard`), dan tombol navigasi ke desainer.
5. **Langkah 4 — Orchestrator Sub-Route di Workspace (`InvoiceWorkspaceScreen.kt`)**:
   Sambungkan state internal dengan URL browser (`PlatformNavigation.pushPath` dan `PlatformNavigation.listenToPathChanges`).

---

## 🔬 3. Bedah Kode Blok per Blok

### A. Subpath Matching di `AppNavScreen.kt`

```kotlin
// 1. Coba pencocokan tepat (exact match)
val exactMatch = entries.firstOrNull { screen ->
    screen.route.equals(normalized, ignoreCase = true) ||
        screen.aliases.any { alias -> alias.equals(normalized, ignoreCase = true) }
}
if (exactMatch != null) return exactMatch

// 2. Jika tidak cocok tepat, periksa apakah merupakan subpath (misal: /invoicing/templates/tpl-001)
return entries
    .sortedByDescending { it.route.length }
    .firstOrNull { screen ->
        screen.route != "/" && (
            normalized.startsWith("${screen.route}/", ignoreCase = true) ||
                screen.aliases.any { alias -> normalized.startsWith("$alias/", ignoreCase = true) }
        )
    }
```

**Mental Model**:
- Urutan pencocokan sangat penting. Mengapa kita melakukan `.sortedByDescending { it.route.length }`?
- Karena jika ada rute `/invoicing` dan rute `/invoicing/templates`, path `/invoicing/templates/tpl-001` memenuhi awalan keduanya! Jika diperiksa dari rute pendek lebih dulu, ia akan keliru dikenali sebagai `/invoicing`. Dengan mengurutkan dari rute terpanjang, `/invoicing/templates` akan menang lebih dulu.

### B. Isolasi Sidebar Drawer di `NavMenu.kt`

```kotlin
val screensByModule = AppNavScreen.entries
    .filter { it.isNavMenuItem }
    .mapNotNull { screen ->
        screen.businessModule?.let { it to screen }
    }.toMap()
```

**Mengapa ini penting?**
Drawer menu mengelompokkan layar berdasarkan `BusinessModule`. Baik `/invoicing` maupun `/invoicing/templates` bernaung di bawah modul yang sama (`BusinessModule.INVOICING`). Tanpa filter `isNavMenuItem`, `toMap()` akan menimpa entry `/invoicing` dengan `/invoicing/templates` di sidebar!

### C. State Sub-Route Bersinkronisasi dengan Browser di `InvoiceWorkspaceScreen.kt`

```kotlin
sealed interface InvoicingSubRoute {
    data object Workspace : InvoicingSubRoute
    data object TemplateGallery : InvoicingSubRoute
    data class Designer(val templateId: String?) : InvoicingSubRoute
}

private fun resolveInvoicingSubRoute(path: String): InvoicingSubRoute {
    val templateId = AppNavScreen.extractTemplateId(path)
    val screen = AppNavScreen.fromPath(path)
    return when {
        templateId != null -> InvoicingSubRoute.Designer(templateId)
        screen == AppNavScreen.INVOICING_TEMPLATES -> InvoicingSubRoute.TemplateGallery
        else -> InvoicingSubRoute.Workspace
    }
}
```

**Penanganan Dua Arah**:
1. **Dari Browser ke State**: `LaunchedEffect(Unit)` mendengarkan `PlatformNavigation.listenToPathChanges` (tombol Back/Forward browser dan reload halaman) -> memanggil `resolveInvoicingSubRoute(newPath)` -> UI langsung menyesuaikan.
2. **Dari Interaksi Pengguna ke Browser**: Saat pengguna menekan tombol "Buka Desain Layout", panggil `PlatformNavigation.pushPath("/invoicing/templates/$id")` dan ubah state `subRoute`.

---

## 🛠️ 4. Teknologi & Pendekatan (The "Why")

| Keputusan Arsitektur | Alternatif yang Ditolak | Alasan Pemilihan |
|---|---|---|
| **Sub-Route berbasis Path (`/invoicing/templates`)** | Modal Dialog popup di atas tabel faktur | URL dapat di-bookmark, di-share antar-staf finance, dan tetap bertahan saat reload F5 di browser WasmJS. |
| **Pemisahan `TemplateGalleryScreen`** | Menggabungkan tabel faktur dan grid template dalam satu halaman raksasa | Mengurangi beban kognitif pengguna; desainer template adalah aktivitas konfigurasi berkala, bukan transaksi harian. |
| **Enum `applicableKinds: Set<InvoiceKind>`** | Kolom string tag bebas (`"sample"`, `"dp"`) | Keamanan tipe (Type-safety), mencegah typo, dan menjamin integritas validasi backend serta Flyway migration. |
| **Data Pratinjau Garmen Sampling Realistis** | Menggunakan data acak lorem ipsum | Pengguna desainer perlu melihat langsung apakah kolom ukuran pola, bahan kain, dan catatan fitting muat di kertas A4 sebelum dicetak ke PDF. |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Jebakan `toMap()` pada Enum Routing**: Menambahkan rute anak dengan modul bisnis yang sama tanpa mengecualikannya dari pembuatan menu sidebar.
2. **Jebakan Lupa `listenToPathChanges`**: Mengubah URL dengan `pushState` saat klik tombol, tetapi lupa mendengarkan event `popstate` saat pengguna menekan tombol "Back" di browser.
3. **Jebakan Hardcode Warna & Emoji**: Menaruh emoji Unicode mentah (`🧪`, `💳`) ke dalam string teks Compose Wasm. Browser tanpa font emoji OS akan merender kotak kosong (`▯`). Gunakan `ClayIcons.kt` atau badge warna semantik dari `WeMadeColors`.
4. **Jebakan Template Default Tertimpa**: Menyimpan template baru tanpa membuat `InvoiceTemplateId` baru sehingga menimpa template standar tenant.

---

## ✅ 6. Verifikasi Mandiri

Jalankan perintah berikut untuk menguji keabsahan kode:

```bash
# 1. Jalankan unit test routing & subpath parsing
./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.navigation.AppNavScreenTest"

# 2. Pastikan kompilasi target WasmJS dan JVM bersih
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs

# 3. Verifikasi Server API Invoicing Template
./gradlew :server:test --tests "com.eventverse.app.InvoicingApiTest"
```

### Verifikasi Manual di Browser:
1. Buka `http://localhost:3000/invoicing`.
2. Klik tombol **"Katalog & Desain Template"** -> perhatikan URL berubah menjadi `http://localhost:3000/invoicing/templates`.
3. Filter berdasarkan **"Sample Invoice (Fokus)"** -> klik **"Buka Desain Layout"** -> URL berubah ke `http://localhost:3000/invoicing/templates/{id}`.
4. Perhatikan badge **`[SAMPLE INVOICE]`** di toolbar dan data kain/pola sample di kanvas A4.
5. Tekan **F5 (Reload Browser)** -> pastikan halaman tetap berada di desainer template tersebut dan tidak mental ke `/invoicing`!
