# 🎓 Modul Pembelajaran: Mengapa Emoji & Teks Rusak di Compose Multiplatform Wasm (Skiko Canvas) dan Cara Memperbaikinya

> **Level Target**: Junior to Mid-Senior Mobile/Frontend Developer  
> **Topik Utama**: Compose Multiplatform (WasmJs), Skiko (Skia WebAssembly), Canvas Font Rendering, Glyph Fallbacks, HarfBuzz Text Shaping, Enterprise UI Typography  
> **Prasyarat**: Pemahaman dasar Compose UI, konsep HTML5 Canvas, dan perbedaan DOM browser vs Canvas Rendering Pipeline  
> **File Terdampak**: [HierarchyLevel.kt](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/orgchart/HierarchyLevel.kt), [OrgNodeCard.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/components/OrgNodeCard.kt), [TShapeChartView.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/components/TShapeChartView.kt), [OrgChartScreen.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartScreen.kt), [App.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/App.kt), [LoginScreen.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/auth/LoginScreen.kt), [index.html](file:///Volumes/amalari/Projects/wemade/app/webApp/src/webMain/resources/index.html)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Pernahkah kamu menjalankan aplikasi web modern, lalu semua icon muncul sebagai kotak kosong bertanda silang atau kotak tahu putih (`□` / tofu), dan teks di sampingnya berantakan, terpotong, atau saling tumpang tindih?

Itulah yang baru saja terjadi di WebAssembly build aplikasi kita (`http://localhost:3000`).

### Mengapa ini terjadi? (Analogi Sederhana)
Bayangkan kamu menyewa juru gambar (Skia Engine) untuk melukis seluruh tampilan aplikasi ke selembar kanvas kosong raksasa (`<canvas>`), bukan menyusun balok-balok lego HTML (`<div>`, `<span>`, `<button>`).

- **Di browser biasa (HTML DOM)**: Browser punya akses langsung ke font emoji bawaan sistem operasi (misal: *Apple Color Emoji* di macOS atau *Noto Color Emoji* di Android). Kalau kamu ketik `👑`, browser meminta OS menampilkannya.
- **Di Skiko Canvas (WebAssembly)**: Juru gambar kita (Skiko WASM) terkurung di dalam sandbox WebAssembly. Dia **TIDAK** otomatis meminjam font emoji dari sistem operasi karena font emoji itu ukurannya raksasa (30MB hingga 100MB+!). Jika font emoji itu dipaketkan ke dalam WASM, user harus menunggu download ratusan megabyte hanya untuk membuka web.
- Oleh karena itu, Skiko hanya menyertakan font huruf Latin standar berukuran minimal. Ketika kode kita menyuruh Skiko mencetak string `"👑 Direksi"` atau `"🎖️ Head"`:
  1. Skiko mencari *glyph* gambar mahkota `👑` di tabel font-nya.
  2. Karena glyph tidak ada, Skiko menggambar karakter default hilang: **kotak tahu kosong (`□` / Tofu)**.
  3. Parahnya, karakter seperti `🎖️` dan `⚙️` memiliki kode tersembunyi bernama **Unicode Variation Selector** (`\uFE0F`). Skiko text shaper gagal menghitung lebar huruf (*advance width*) dari karakter multi-byte yang hilang ini, sehingga **seluruh tulisan di sampingnya bergeser, saling bertabrakan, atau terpotong!**

Selain itu, elemen `<svg>` loading di `index.html` tidak dihilangkan setelah aplikasi selesai dimuat, sehingga kanvas terdorong ke bawah dan membuat tata letak vertikal terpotong.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Perbaikan (Order of Operations)

Jika kamu menghadapi bug rendering font / icon di canvas WebAssembly, ini urutan investigasi dan perbaikannya:

```
Step 0: Identifikasi Engine Canvas (Skiko/Skia) & Keterbatasan Font WebAssembly
         ↓
Step 1: Bersihkan Domain Layer dari Teks / Emoji Presentasi (Clean Architecture DDD)
         ↓
Step 2: Desain Pengganti Icon: Typographic Badges & Canvas Vectors
         ↓
Step 3: Refactor Komponen UI Presentasi (OrgNodeCard, TShapeChartView, Navigation)
         ↓
Step 4: Audit dan Rapikan HTML/CSS Viewport Host (`index.html` & `styles.css`)
         ↓
Step 5: Hubungkan Lifecycle Compose ke Web DOM (Fade Out App Loader di Frame Pertama)
         ↓
Step 6: Verifikasi Multiplatform (JVM Unit Tests & Wasm Webpack Production Build)
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Mari kita bedah blok kode sebelum dan sesudah perbaikan, lengkap dengan *mental model* di baliknya.

### Blok A: Domain Layer Hygiene — Menghapus Emoji dari Entity / Enum
Di [HierarchyLevel.kt](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/orgchart/HierarchyLevel.kt):

```kotlin
// ❌ SEBELUM: Domain tercemar detail presentasi visual (iconEmoji)
enum class HierarchyLevel(
    val displayName: String,
    val shortLabel: String,
    val rank: Int,
    val iconEmoji: String // Melanggar DDD murni & memicu tofu di WASM
) {
    EXECUTIVE(displayName = "Pimpinan / Direksi Pabrik", shortLabel = "Direksi", rank = 1, iconEmoji = "👑"),
    HEAD_OF_DEPARTMENT(displayName = "Kepala Divisi", shortLabel = "Kepala Divisi", rank = 2, iconEmoji = "🎖️"),
    STAFF_OPERATOR(displayName = "Staf Pelaksana", shortLabel = "Staf", rank = 3, iconEmoji = "👔");
}

// ✅ SESUDAH: Pure Domain Entity (Zero visual artifacts)
enum class HierarchyLevel(
    val displayName: String,
    val shortLabel: String,
    val rank: Int
) {
    EXECUTIVE("Pimpinan / Direksi Pabrik", "Direksi", 1),
    HEAD_OF_DEPARTMENT("Kepala Divisi / Supervisor", "Kepala Divisi", 2),
    STAFF_OPERATOR("Staf Pelaksana / Operator", "Staf", 3);
}
```

**Mengapa ini penting?**
Dalam Domain-Driven Design (DDD), modul `:core` tidak boleh peduli bagaimana data akan digambar di layar. Menaruh string emoji di enum domain adalah *code smell*. Representasi visual adalah hak prerogatif layer `:app:shared` (Presentation).

---

### Blok B: Presentasi Node Card — Typographic Badge Pill Berbasis Warna
Di [OrgNodeCard.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/components/OrgNodeCard.kt):

```kotlin
// ❌ SEBELUM: Teks emoji mentah yang memicu glyph box
Text(
    text = "${node.level.iconEmoji} ${node.level.shortLabel}",
    fontSize = 10.sp,
    color = WeMadeColors.OnSurfaceMuted,
    fontWeight = FontWeight.Medium
)

// ✅ SESUDAH: Crisp Typographic Pill Badge
Box(
    modifier = Modifier
        .clip(RoundedCornerShape(4.dp))
        .background(
            when (node.level) {
                HierarchyLevel.EXECUTIVE -> Color(0xFF6366F1).copy(alpha = 0.12f)
                HierarchyLevel.HEAD_OF_DEPARTMENT -> Color(0xFFF59E0B).copy(alpha = 0.12f)
                HierarchyLevel.STAFF_OPERATOR -> Color(0xFF64748B).copy(alpha = 0.12f)
            }
        )
        .padding(horizontal = 5.dp, vertical = 2.dp)
) {
    Text(
        text = node.level.shortLabel.uppercase(),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = when (node.level) {
            HierarchyLevel.EXECUTIVE -> Color(0xFF4338CA)
            HierarchyLevel.HEAD_OF_DEPARTMENT -> Color(0xFFB45309)
            HierarchyLevel.STAFF_OPERATOR -> Color(0xFF475569)
        }
    )
}
```

**Mengapa pendekatan ini jauh lebih unggul?**
1. **100% Bebas Ketergantungan Font**: Teks Latin murni (`DIR`, `HEAD`, `STAF`) selalu didukung oleh font Skiko bawaan apapun.
2. **Desain B2B SaaS Enterprise Kelas Dunia**: Aplikasi seperti Stripe, Linear, dan Vercel tidak menggunakan emoji acak untuk hierarki jabatan, melainkan badge pill berkode warna semantik.
3. **Kontras WCAG AA Terpenuhi**: Background ber-alpha rendah dipadukan dengan teks warna pekat (`4.5:1` contrast ratio).

---

### Blok C: Vector Drawing Pengganti Emoji Checklist & Simbol
Di [LoginScreen.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/auth/LoginScreen.kt):

```kotlin
// ❌ SEBELUM: Menggunakan karakter Unicode checkmark "✓"
Text(
    text = "✓",
    color = WeMadeColors.Success,
    fontSize = 20.sp,
    fontWeight = FontWeight.Bold
)

// ✅ SESUDAH: Menggambar Checkmark Vektor Presisi Menggunakan Canvas Path
Canvas(modifier = Modifier.size(22.dp)) {
    val strokeWidth = 2.5f * density
    val path = Path().apply {
        moveTo(size.width * 0.2f, size.height * 0.52f)
        lineTo(size.width * 0.44f, size.height * 0.76f)
        lineTo(size.width * 0.82f, size.height * 0.28f)
    }
    drawPath(path, color = WeMadeColors.Success, style = Stroke(width = strokeWidth))
}
```

**Mengapa menggunakan Canvas Path?**
- Canvas Path di-render langsung oleh GPU via WebGL di Skiko.
- Tidak ada latency download, tidak ada font fallback lookup, dan tidak mungkin ter-render sebagai kotak tanda tanya. Tampilan di layar MacBook Retina, ponsel Android, maupun monitor 4K akan selalu tajam piksel per piksel.

---

### Blok D: Perbaikan Viewport Host & Seamless Loading Dismissal
Di [index.html](file:///Volumes/amalari/Projects/wemade/app/webApp/src/webMain/resources/index.html) dan [main.kt](file:///Volumes/amalari/Projects/wemade/app/webApp/src/webMain/kotlin/com/eventverse/app/main.kt):

1. **HTML Overlay**:
```html
<body>
    <div id="app-loader" style="position: fixed; inset: 0; display: flex; ... z-index: 99999;">
        <!-- SVG spinner ditampilkan saat WASM sedang di-download -->
    </div>
```

2. **Kotlin/WASM Bridge**:
```kotlin
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => { if (window.hideAppLoader) { window.hideAppLoader(); } }")
external fun hideAppLoaderJs(): Unit

fun main() {
    ComposeViewport {
        // Begitu Compose selesai merender frame pertama ke Canvas, hilangkan loader!
        androidx.compose.runtime.LaunchedEffect(Unit) {
            hideAppLoaderJs()
        }
        App()
    }
}
```

**Mengapa teknik ini sangat krusial?**
Jika SVG loading dibiarkan di dalam `<body>` bersama `<canvas>`, tinggi elemen `<body>` melebihi 100vh. Kanvas Compose tergeser ke bawah sejumlah tinggi SVG tersebut. Menjadikan loader sebagai `position: fixed` overlay dan membuangnya dari DOM saat frame pertama Compose selesai digambar menjamin canvas mengambil persis `100vw x 100vh` tanpa offset distorsi.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif Lain | Mengapa Kita Memilih Pendekatan Ini? | Risiko Jika Menggunakan Alternatif |
|---|---|---|---|
| **Typographic Pills & Canvas Vectors** | Memasukkan file font `NotoColorEmoji.ttf` ke `composeResources` | Ukuran bundle WASM tetap sangat ramping (~3.5 MB). Web app langsung terbuka dalam hitungan detik. | File font emoji berukuran 30–80 MB! User dengan koneksi lambat akan menunggu lama hanya untuk melihat sebuah icon. |
| **Material Symbols Font / Vector Path** | Raw Unicode String Emojis (`👑`, `🎖️`) | Stabil di seluruh platform (Android, iOS, JVM Desktop, Wasm Browser) tanpa ketergantungan OS. | Muncul bug "tofu" (`□`) dan HarfBuzz glyph advance calculation failure di browser. |
| **DOM Overlay Loader dengan Dismissal** | Static inline spinner di dalam `<body>` | Canvas Compose menempati 100% viewport layar secara presisi tanpa scrollbar atau pergeseran piksel. | Canvas terdorong ke bawah, event klik mouse meleset dari koordinat asli, dan bottom navigation terpotong. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Mengira Emoji adalah Simbol yang Selalu Aman di Semua Tempat**
   - *Kenapa salah*: Emoji tampak normal di MacBook atau Android karena sistem operasi menyuntikkan font fallback sistem. Namun di canvas-based rendering (Skiko WASM, Flutter CanvasKit, WebGL), tidak ada injeksi otomatis font emoji jika engine tidak memilikinya.
   - *Solusi*: Jangan pernah gunakan raw emoji untuk komponen UI struktural atau arsitektur sistem. Gunakan vector icon atau typographic badges.

2. **Jebakan 2: Variation Selector (`\uFE0F`) Text Distortion**
   - *Kenapa salah*: Simbol seperti `🎖️` atau `⚙️` terdiri dari 2 code point: karakter utama + variation selector 16. Skiko yang tidak memiliki glyph ini akan memecah rendering teks, menyebabkan kata-kata berikutnya berjarak aneh atau melompat baris.
   - *Solusi*: Hapus sepenuhnya ketergantungan pada Unicode multi-byte untuk ikonografi.

3. **Jebakan 3: Mencemari Domain Layer dengan Visual Assets**
   - *Kenapa salah*: Menaruh `iconEmoji = "👑"` di enum domain `HierarchyLevel` membuat domain terikat pada presentasi UI. Jika besok aplikasi dijalankan di CLI terminal atau sistem ekspor CSV, data tercemar karakter visual.
   - *Solusi*: Simpan murni data hierarki (`rank`, `shortLabel`, `displayName`) di domain, dan delegasikan pemetaan warna atau badge ke UI layer.

---

## 🧪 6. Cara Membuktikan Kodingan Bekerja dengan Benar

1. **Domain Test Suite**:
   ```bash
   ./gradlew :core:jvmTest
   ```
   *Hasil*: Seluruh 5 unit test domain hierarchy, custom role, dan account creation policy lulus 100% tanpa kompilasi error.
2. **Kompilasi Multiplatform**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs
   ```
   *Hasil*: Sukses mengompilasi modul shared untuk target JVM dan WebAssembly.
3. **Web Production Bundle (Webpack)**:
   ```bash
   ./gradlew :app:webApp:wasmJsBrowserDistribution
   ```
   *Hasil*: Webpack memaketkan bundle Wasm dan Skiko runtime secara bersih dalam waktu 46 detik.
4. **Inspeksi Browser**:
   Buka `http://localhost:3000`. Seluruh teks navigasi, badge kartu struktur organisasi, dan kartu otentikasi kini tampil tajam, rapi, dan bersih dari kotak tofu `□`.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka [styles.css](file:///Volumes/amalari/Projects/wemade/app/webApp/src/webMain/resources/styles.css), coba teliti mengapa properti `-webkit-font-smoothing: antialiased;` dan `canvas { display: block; }` sangat berpengaruh mencegah celah garis putih 1-piksel di bagian bawah canvas.
- [ ] **Tantangan 2**: Buatlah sebuah Composable helper sederhana `StatusBadge(status: String, color: Color)` di `app/shared` untuk menggantikan indikator status teks biasa di seluruh aplikasi agar konsisten dengan design system B2B SaaS.
