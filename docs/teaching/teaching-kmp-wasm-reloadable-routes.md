# 🎓 Modul Pembelajaran: Arsitektur Routing Terpisah & Reloadable Pages pada Kotlin Multiplatform (Wasm & Compose)

> **Level Target**: Junior to Mid Multiplatform Developer  
> **Topik Utama**: Client-Side Routing, Browser History API, Kotlin/Wasm JS Interop (`@JsFun`), Webpack `historyApiFallback`, Compose Multiplatform State Synchronization, Expect/Actual Platform Bridges  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform, Compose State & LaunchedEffect, Webpack Dev Server, serta siklus hidup URL di Single Page Application (SPA).  
> **Referensi Task**: Separate Routes & Browser Reload for Org Chart, RBAC, and Factory Pipeline (WeMade ERP)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Bayangkan sebuah pabrik konveksi modern dengan workstation komputer di lini produksi, meja supervisor, dan ruang direksi. Seorang manajer operasional sedang memantau antrean kain di layar **Alur Pabrik (Pipeline)**. Karena ada instruksi baru, ia menekan tombol **Reload / Refresh (F5)** pada peramban (browser). 

Jika navigasi aplikasi hanya disimpan di dalam memori lokal Compose (`mutableStateOf`) tanpa terhubung ke URL browser:
1. Setiap kali halaman di-reload, aplikasi langsung **reset** ke halaman awal default (misalnya Bagan Organisasi atau Login). Manajer harus mengklik ulang tab untuk kembali ke Alur Pabrik.
2. Tautan ke halaman spesifik seperti `http://localhost:3000/rbac` tidak bisa dibagikan (*bookmark* atau *share URL*) ke rekan kerja, karena peramban selalu menampilkan root URL (`/`) atau bahkan melempar galat `404 Cannot GET /rbac`.
3. Tombol **Back** dan **Forward** bawaan peramban tidak berfungsi. Pengguna yang menekan Back malah terlempar keluar dari aplikasi ke situs sebelumnya.

### Analogi Sederhana: Denah Gedung & Papan Penunjuk Ruangan
- **Aplikasi Tanpa Routing URL**: Seperti masuk ke gedung raksasa tanpa nomor pintu. Siapapun yang bertanya posisi Anda hanya dijawab "di dalam gedung". Begitu Anda berkedip (reload), Anda dipindahkan paksa kembali ke lobi utama.
- **Aplikasi dengan Browser Routing & History API**: Setiap ruangan memiliki nomor dan papan petunjuk resmi (`/org-chart`, `/rbac`, `/factory-flow`, `/login`). Jika Anda membagikan alamat atau berjalan bolak-balik (Back/Forward), sistem resepsionis dan peta gedung langsung tahu posisi Anda tanpa harus bertanya ulang dari nol.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun sistem routing terpisah yang mendukung reload di aplikasi Kotlin Multiplatform Compose Web (Wasm), berikut urutan logis pengerjaannya dari nol:

```
Step 0: Kontrak Platform Navigation (expect object di commonMain)
   ↓
Step 1: Implementasi Platform-Specific Edge (actual object wasmJs, js, jvm, android, ios)
   ↓
Step 2: Typed Route Modeling & Normalizer (AppNavScreen enum & fromPath parser)
   ↓
Step 3: Webpack Dev Server & HTML Base Configuration (historyApiFallback & <base href="/">)
   ↓
Step 4: Root Composition Orchestrator (App.kt state, pushState, & popstate listener)
   ↓
Step 5: Automated Unit Tests (AppNavScreenTest untuk validasi canonical, alias, & hash)
```

1. **Langkah 0: Kontrak Multiplatform Navigation (`commonMain`)**  
   Jangan langsung memanggil API JavaScript seperti `window.location` di dalam UI Composable. Tentukan kontrak abstraksi `expect object PlatformNavigation` terlebih dahulu agar kode UI di `commonMain` tetap murni dan bisa berjalan di desktop/mobile.
2. **Langkah 1: Implementasikan Edge Tiap Platform (`actual`)**  
   Di `wasmJsMain`, gunakan `@JsFun` untuk berinteraksi dengan `window.location.pathname`, `history.pushState`, dan event `popstate`. Di platform native (JVM, Android, iOS), implementasikan *in-memory route fallback* agar tidak menyebabkan runtime crash.
3. **Langkah 2: Model Rute Tersendiri (`AppNavScreen`)**  
   Hindari *magic strings* di mana-mana. Petakan rute canonical (`/org-chart`, `/rbac`, `/factory-flow`, `/login`), alias toleran (`/pipeline`, `/roles`), dan buat fungsi parser pembersih query string serta hash.
4. **Langkah 3: Konfigurasi Webpack & HTML Assets**  
   Aktifkan `historyApiFallback: true` pada Webpack Dev Server dan tambahkan `<base href="/">` pada `index.html`. Ini kunci vital agar reload pada subpath tidak melempar 404 atau merusak pemuatan binary WebAssembly.
5. **Langkah 4: Orkestrasi State di Root UI (`App.kt`)**  
   Inisialisasi layar dari URL browser saat booting, perbarui URL via `pushPath` saat tombol diklik, dengarkan event `popstate` untuk tombol Back/Forward, dan simpan *redirect intent* saat user mengakses rute terproteksi.
6. **Langkah 5: Tulis Unit Test Verifikasi**  
   Uji parsing URL untuk berbagai variasi input (canonical, alias, trailing slash, query parameter, dan hash).

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Mari kita bedah implementasi yang telah dibuat di proyek ini secara mendalam.

### Blok A: Kontrak Multiplatform Navigation (`PlatformNavigation.kt`)

File: [`app/shared/src/commonMain/kotlin/com/eventverse/app/infrastructure/navigation/PlatformNavigation.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/infrastructure/navigation/PlatformNavigation.kt)

```kotlin
package com.eventverse.app.infrastructure.navigation

expect object PlatformNavigation {
    fun getCurrentPath(): String
    fun pushPath(path: String)
    fun replacePath(path: String)
    fun listenToPathChanges(onPathChanged: (String) -> Unit)
}
```

**Mengapa blok ini ditulis begini?**
- **Dependency Inversion**: Layer UI di `commonMain` hanya bergantung pada abstraksi ini, tidak mengetahui apakah aplikasi sedang berjalan di Google Chrome (Wasm), Node.js, JVM Desktop, atau Android.
- **Minimal Surface Area**: Hanya mengekspos 4 fungsi esensial: membaca path saat ini, menambahkan history entry (`push`), menimpa history entry tanpa menumpuk (`replace`), dan mendengarkan navigasi peramban (`listen`).

---

### Blok B: Implementasi Kotlin/Wasm JS Interop (`PlatformNavigation.wasmJs.kt`)

File: [`app/shared/src/wasmJsMain/kotlin/com/eventverse/app/infrastructure/navigation/PlatformNavigation.wasmJs.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/wasmJsMain/kotlin/com/eventverse/app/infrastructure/navigation/PlatformNavigation.wasmJs.kt)

```kotlin
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("() => { try { var h = window.location.hash || ''; if (h.startsWith('#/')) return h.substring(1); if (h.startsWith('#') && h.length > 1) return '/' + h.substring(1).replace(/^\\//, ''); var p = window.location.pathname || '/'; return p.length > 0 ? p : '/'; } catch(e) { return '/'; } }")
private external fun jsGetCurrentPath(): String

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(path) => { try { if (window.location.pathname !== path) { window.history.pushState(null, '', path); } } catch (e) {} }")
private external fun jsPushPath(path: String)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("(callback) => { try { var notify = () => { var h = window.location.hash || ''; var p = window.location.pathname || '/'; if (h.startsWith('#/')) p = h.substring(1); else if (h.startsWith('#') && h.length > 1) p = '/' + h.substring(1).replace(/^\\//, ''); callback(p); }; window.addEventListener('popstate', notify); window.addEventListener('hashchange', notify); } catch (e) {} }")
private external fun jsListenToPathChanges(callback: (String) -> Unit)
```

**Mengapa blok ini ditulis begini?**
- **Dukungan Ganda Path & Hash**: Fungsi membaca `window.location.pathname` terlebih dahulu. Jika pengguna menggunakan format hash seperti `#/rbac` atau `#rbac`, fungsi otomatis mengonversinya menjadi `/rbac`.
- **Cegah Push Berulang**: `if (window.location.pathname !== path)` mencegah penumpukan riwayat browser palsu jika pengguna mengklik tab yang sama berkali-kali.
- **Event Listener Terintegrasi**: Mendengarkan event `popstate` (tombol Back/Forward browser) dan `hashchange` sekaligus, lalu meneruskannya ke lambda callback Kotlin.

---

### Blok C: Typed Route Modeling & Normalizer (`AppNavScreen.kt`)

File: [`app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/navigation/AppNavScreen.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/navigation/AppNavScreen.kt)

```kotlin
enum class AppNavScreen(
    val route: String,
    val title: String,
    val aliases: List<String> = emptyList()
) {
    ORG_CHART(
        route = "/org-chart",
        title = "Bagan Organisasi",
        aliases = listOf("/orgchart", "/organization", "/bagan-organisasi")
    ),
    DYNAMIC_RBAC(
        route = "/rbac",
        title = "Hak Akses (RBAC)",
        aliases = listOf("/roles", "/hak-akses", "/permissions")
    ),
    FACTORY_FLOW(
        route = "/factory-flow",
        title = "Alur Pabrik (Pipeline)",
        aliases = listOf("/pipeline", "/alur-pabrik", "/flow")
    ),
    LOGIN(
        route = "/login",
        title = "Login Akun",
        aliases = listOf("/masuk")
    );

    val isProtected: Boolean
        get() = this != LOGIN

    companion object {
        fun fromPath(rawPath: String): AppNavScreen? {
            val trimmed = rawPath.trim()
            val withoutHash = if (trimmed.startsWith("#")) {
                trimmed.removePrefix("#").removePrefix("/")
            } else {
                trimmed
            }

            val normalized = withoutHash
                .substringBefore("?")
                .substringBefore("#")
                .removeSuffix("/")
                .let { if (it.isEmpty() || it == "/") "/" else if (it.startsWith("/")) it else "/$it" }

            if (normalized == "/") return null

            return entries.firstOrNull { screen ->
                screen.route.equals(normalized, ignoreCase = true) ||
                    screen.aliases.any { alias -> alias.equals(normalized, ignoreCase = true) }
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- **Toleransi Masukan**: Jika pengguna mengetik `/pipeline` atau `/alur-pabrik`, sistem otomatis memetakan ke `FACTORY_FLOW` tanpa error.
- **Pembersihan URL**: `substringBefore("?")` memastikan URL yang membawa query parameter (misal `/login?redirect=%2Frbac`) tetap dikenali sebagai rute `LOGIN`.

---

### Blok D: Konfigurasi Webpack Dev Server & HTML Base

File: [`app/webApp/webpack.config.d/devServer.js`](file:///Volumes/amalari/Projects/wemade/app/webApp/webpack.config.d/devServer.js)
```javascript
if (config.output) {
    config.output.publicPath = '/';
}
if (config.devServer) {
    config.devServer.port = 3000;
    config.devServer.historyApiFallback = true;
    config.devServer.proxy = [
        {
            context: ['/api'],
            target: 'http://localhost:8080',
            changeOrigin: true
        }
    ];
}
```

File: [`app/webApp/src/webMain/resources/index.html`](file:///Volumes/amalari/Projects/wemade/app/webApp/src/webMain/resources/index.html)
```html
<head>
    <meta charset="UTF-8">
    <base href="/">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WeMade ERP — Sistem Manajemen Konveksi</title>
    <link type="text/css" rel="stylesheet" href="/styles.css">
    ...
</head>
<body>
    ...
    <script type="application/javascript" src="/webApp.js"></script>
</body>
```

**Mengapa blok ini ditulis begini?**
- **Solusi 404 pada Reload**: Saat pengguna menekan F5 pada `http://localhost:3000/rbac`, Webpack Dev Server tidak memiliki file fisik bernama `rbac`. Dengan `historyApiFallback: true`, Webpack mengalihkan permintaan ke `index.html`.
- **Mencegah Kerusakan Path Relatif Wasm**: Tanpa `<base href="/">` dan path absolut (`/styles.css`, `/webApp.js`), peramban akan mencoba mengunduh file binary WebAssembly dari `http://localhost:3000/rbac/webApp.wasm` yang menghasilkan syntax error.

---

### Blok E: Sinkronisasi State & History di Root App (`App.kt`)

File: [`app/shared/src/commonMain/kotlin/com/eventverse/app/App.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/App.kt)

```kotlin
// 1. Ambil path dari peramban saat inisialisasi awal (booting / reload)
val initialPath = remember { PlatformNavigation.getCurrentPath() }
val initialScreen = remember {
    val matched = AppNavScreen.fromPath(initialPath)
    when {
        matched != null -> matched
        isAuthenticated -> AppNavScreen.ORG_CHART
        else -> AppNavScreen.LOGIN
    }
}

var currentScreen by remember { mutableStateOf(initialScreen) }
var pendingRedirectScreen by remember { mutableStateOf<AppNavScreen?>(null) }

// 2. Fungsi navigasi terpusat dengan browser pushState
val navigateTo: (AppNavScreen) -> Unit = remember {
    { target ->
        if (currentScreen != target) {
            currentScreen = target
            PlatformNavigation.pushPath(target.route)
        }
    }
}

// 3. Dengarkan event Back / Forward dari peramban
LaunchedEffect(Unit) {
    val current = PlatformNavigation.getCurrentPath()
    if (AppNavScreen.fromPath(current) == null) {
        PlatformNavigation.replacePath(currentScreen.route)
    }

    PlatformNavigation.listenToPathChanges { newPath ->
        val matched = AppNavScreen.fromPath(newPath)
        if (matched != null && matched != currentScreen) {
            currentScreen = matched
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- **Dukungan Reload Mulus**: Ketika pengguna me-reload pada `/rbac`, `initialPath` langsung membaca `/rbac`, dan `currentScreen` langsung terisi `DYNAMIC_RBAC` sejak *first composition frame*.
- **Preservasi Rute Terproteksi**: Jika pengguna belum login membuka `/factory-flow`, ia akan melihat `AuthGuardCard`. Saat ia menekan tombol login, `pendingRedirectScreen` mencatat bahwa pengguna ingin ke `FACTORY_FLOW`, sehingga setelah login sukses ia langsung diarahkan ke sana, bukan di-reset ke Bagan Organisasi.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi / Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **HTML5 History API (`pushState`)** | Hash Routing (`#/rbac`) saja | URL terlihat bersih, standar industri enterprise ERP modern, dan ramah SEO/REST. | Hash routing terasa seperti aplikasi web lama tahun 2010 dan canggung untuk integrasi OAuth redirect callback. |
| **KMP Expect/Actual Bridge** | Menggunakan library routing pihak ketiga eksternal | Zero external dependencies, ukuran binary Wasm tetap sangat kecil, dan kontrol penuh atas lifecycle. | Library pihak ketiga seringkali belum stabil di target Kotlin/Wasm atau memiliki overhead dependensi yang berat. |
| **Webpack `historyApiFallback`** | Redirect 404 via file HTML statis per rute | Standar Single Page Application (SPA), seluruh rute dilayani oleh satu bundler terpadu. | Membuat file HTML terpisah untuk tiap halaman merusak state memori dan skiko canvas rendering. |
| **`<base href="/">` di Head** | Path relatif (`./webApp.js`) | Memastikan browser selalu mencari chunk WASM, worker, dan CSS dari root terlepas dari seberapa dalam kedalaman path URL. | Terjadi kegagalan runtime `Uncaught SyntaxError: Unexpected token '<'` saat membuka rute bersarang. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Path Asset Relatif yang Rusak saat Reload Subpath**
   - *Kenapa bahaya*: Ketika pengguna membuka `http://localhost:3000/rbac`, browser menganggap folder aktif adalah `/rbac/`. Jika script ditulis `<script src="webApp.js">`, browser akan meminta `/rbac/webApp.js`. Karena Webpack mengembalikan `index.html`, browser mencoba mem-parse HTML sebagai JavaScript dan aplikasi langsung *crash*.
   - *Solusi elegan kita*: Selalu pasang `<base href="/">` dan gunakan path absolut `/webApp.js` serta `/styles.css`.

2. **Jebakan 2: Recomposition Infinite Loop saat URL Listener Mengubah State**
   - *Kenapa bahaya*: Jika event listener `listenToPathChanges` langsung memanggil `navigateTo()`, dan `navigateTo()` memicu perubahan URL yang kembali didengar listener, Compose akan terjebak dalam *infinite recomposition*.
   - *Solusi elegan kita*: Selalu cek kondisi guard `if (currentScreen != target)` sebelum mengubah state atau memanggil `pushPath`.

3. **Jebakan 3: Kebocoran API Platform ke `commonMain`**
   - *Kenapa bahaya*: Pemula sering tergoda mengimpor package JavaScript di `commonMain`. Akibatnya, saat proyek dikompilasi untuk target Android atau Desktop JVM, kompilasi langsung gagal (*Unresolved reference*).
   - *Solusi elegan kita*: Isolasi API browser hanya di `wasmJsMain` dan `jsMain` melalui pola `expect/actual`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian dilakukan pada dua tingkatan:

### A. Automated Unit Test (`AppNavScreenTest.kt`)
File: [`app/shared/src/commonTest/kotlin/com/eventverse/app/presentation/navigation/AppNavScreenTest.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonTest/kotlin/com/eventverse/app/presentation/navigation/AppNavScreenTest.kt)

Jalankan perintah pengujian:
```bash
./gradlew :app:shared:jvmTest
```
Pengujian ini memastikan:
- Rute canonical (`/org-chart`, `/rbac`, `/factory-flow`, `/login`) terpetakan 100% akurat.
- Alias rute seperti `/pipeline`, `/roles`, dan `/masuk` dikenali dengan benar.
- Query params dan hash URL (`#/rbac`, `/factory-flow?tab=live`) terurai dengan bersih.

### B. Manual Verification di Peramban Web
1. Buka aplikasi di peramban: `http://localhost:3000/`
2. Klik navigasi chip **Hak Akses (RBAC)** -> Perhatikan URL browser berubah menjadi `http://localhost:3000/rbac`.
3. Tekan **F5 / Cmd+R (Reload)** -> Layar tetap berada di modul **Hak Akses (RBAC)** dan tidak reset ke Bagan Organisasi.
4. Klik navigasi chip **Alur Pabrik (Pipeline)** -> URL berubah menjadi `http://localhost:3000/factory-flow`.
5. Tekan tombol **Back** browser -> Aplikasi berpindah kembali ke **Hak Akses (RBAC)** secara instan.
6. Buka tab baru dan masukkan langsung `http://localhost:3000/org-chart` -> Aplikasi langsung membuka layar **Bagan Organisasi**.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Tambahkan parameter query `tab` pada rute `/factory-flow` (misalnya `/factory-flow?filter=CUTTING`), lalu sambungkan ke `FactoryFlowViewModel` agar otomatis memilih filter stage yang sesuai saat halaman di-reload.
- [ ] **Tantangan 2**: Buat layar kustom `NotFoundScreen` (404 Page) yang ditampilkan saat pengguna mengetik URL yang tidak terdaftar (misalnya `/gudang-rahasia`), lengkap dengan tombol "Kembali ke Dashboard".
