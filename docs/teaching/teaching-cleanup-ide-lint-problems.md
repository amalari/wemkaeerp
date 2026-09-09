# 🎓 Modul Pembelajaran: Mengatasi Masalah IDE Lint, Cross-Module Resolution, dan Coroutine Anti-Pattern

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Kotlin Multiplatform Module Resolution, JSON Schema Validation, Kotlin Constructor vs Property, Coroutines Non-Blocking Best Practice  
> **Prasyarat**: Pemahaman dasar tentang Gradle multi-module, Kotlin OOP, Ktor testApplication, dan Coroutines  
> **Referensi Masalah**: `current_problems` (Desktop App reference, MCP JSON schema, JwtTokenService constructor property, GoogleAuthIntegrationTest runBlocking)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Saat mengembangkan aplikasi multi-platform berskala enterprise (seperti WeMade ERP) dengan arsitektur modular, compiler dan IDE linter adalah benteng pertahanan pertama kita. Sering kali developer pemula mengabaikan peringatan (*warning*) atau kebingungan saat IDE menunjukkan error merah padahal terminal build sukses, atau membiarkan kode coroutine memblokir thread eksekusi.

Masalah yang kita selesaikan hari ini mencakup 4 area umum:
1. **Unresolved Reference Across Modules**: IDE compiler analyzer tidak otomatis mengenali Composable function `App()` lintas target source-set tanpa explicit import.
2. **JSON Schema Strictness**: Metadata tool seperti MCP config yang kemasukan key internal IDE/prototyping (`$typeName`).
3. **Unused Backing Field di Constructor Kotlin**: Mendeklarasikan `val` atau `var` di primary constructor padahal parameternya hanya dipakai saat instansiasi awal.
4. **Blocking Call di dalam Suspend Context**: Menggunakan `runBlocking` di dalam blok `testApplication` Ktor yang notabene sudah asynchronous (`suspend`).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penanganan Masalah (Order of Operations)

1. **Langkah 1: Verifikasi Antara IDE Warning vs Gradle Build**
   - Jalankan perintah build terminal (`./gradlew :app:desktopApp:compileKotlin`) untuk memastikan apakah kode benar-benar gagal kompilasi atau hanya masalah indeks/import pada IDE.
2. **Langkah 2: Tambahkan Explicit Import untuk Simbol Lintas Modul**
   - Meskipun berada di package yang sama (`com.eventverse.app`), menambahkan import eksplisit `import com.eventverse.app.App` memastikan IDE language server mengenali simbol tanpa ambigu lintas modul Gradle.
3. **Langkah 3: Pembersihan Konfigurasi JSON Schema**
   - Hapus properti sisa serialisasi internal seperti `$typeName` pada `mcp_config.json`.
4. **Langkah 4: Optimasi Primary Constructor di Kotlin**
   - Bedakan kapan sebuah parameter butuh disimpan sebagai property (`val`/`var`) dan kapan hanya dibutuhkan sebagai argumen inisialisasi lokal.
5. **Langkah 5: Konversi Synchronous Blocking ke Asynchronous Idiomatik**
   - Hapus `runBlocking` jika context pemanggilnya sudah merupakan `suspend` scope (seperti di Ktor `testApplication`).

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Desktop Entry Point & Cross-Module Import
File: `app/desktopApp/src/main/kotlin/com/eventverse/app/main.kt`

```kotlin
package com.eventverse.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.eventverse.app.App // <-- Ditambahkan secara eksplisit

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "WeMade ERP — Sistem Manajemen Konveksi & Garmen",
    ) {
        App()
    }
}
```
**Mengapa blok ini ditulis begini?**
- Modul `:app:desktopApp` adalah JVM application module yang bergantung pada KMP library module `:app:shared`.
- Walaupun kedua file berada di package `com.eventverse.app`, IDE Kotlin analyzer terkadang gagal mengindeks fungsi Composable `@Composable fun App()` dari `commonMain` sebelum diimpor secara eksplisit.
- Penambahan `import com.eventverse.app.App` menyelesaikan isu indexing pada IDE tanpa menimbulkan overhead kompilasi.

---

### Blok B: Pembersihan Schema MCP Config
File: `~/.gemini/config/mcp_config.json`

```json
    "figma-dev-mode-mcp-server": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://127.0.0.1:3845/sse"
      ],
      "env": {}
    }
```
**Mengapa blok ini ditulis begini?**
- Field `"$typeName": "exa.cascade_plugins_pb.CascadePluginCommandTemplate"` adalah artefak serialisasi Protobuf internal Cascade/Windsurf.
- Skema JSON standar MCP tidak mengizinkan atribut `$typeName`. Menghapusnya mengembalikan validitas skema JSON.

---

### Blok C: Constructor Parameter vs Class Property di Kotlin
File: `server/src/main/kotlin/com/eventverse/app/infrastructure/auth/JwtTokenService.kt`

```kotlin
class JwtTokenService(
    secret: String = System.getenv("JWT_SECRET") ?: "wemade-erp-default-development-secret-key-32-chars-long!", // <-- Tanpa 'private val'
    private val issuer: String = "wemade-erp",
    private val validityDurationMillis: Long = 7 * 24 * 60 * 60 * 1000L // 7 days
) {
    private val algorithm = Algorithm.HMAC256(secret)
    private val verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()
```
**Mengapa blok ini ditulis begini?**
- Di Kotlin:
  - `class Contoh(val x: String)` membuat parameter konstruktor **sekaligus field/property** pada objek.
  - `class Contoh(x: String)` hanyalah **argumen konstruktor** murni.
- Parameter `secret` hanya dikonsumsi saat konstruksi objek untuk membuat instance `Algorithm.HMAC256(secret)`. Tidak ada method lain di dalam `JwtTokenService` yang memanggil `this.secret`.
- Menghapus `private val` menghilangkan alokasi backing field yang tidak perlu dan menghapus peringatan compiler *"Constructor parameter is never used as a property"*.

---

### Blok D: Menghilangkan Anti-Pattern `runBlocking` di Ktor Test
File: `server/src/test/kotlin/com/eventverse/app/GoogleAuthIntegrationTest.kt`

```kotlin
    @Test
    fun google_login_with_registered_user_should_return_jwt_session() = testApplication {
        val tenantRepo = InMemoryTenantRepository()
        val userRepo = TestUserRepository()

        // Seed demo tenant and user (Langsung suspend call tanpa runBlocking)
        val tenant = Tenant(
            id = TenantId("ten-demo"),
            slug = TenantSlug("berkah-konveksi"),
            name = TenantName("Konveksi Berkah"),
            status = TenantStatus.ACTIVE,
            tier = SubscriptionTier.PRO
        )
        tenantRepo.save(tenant)

        val user = User(
            id = UserId("usr-owner-01"),
            tenantId = TenantId("ten-demo"),
            username = Username("owner_berkah"),
            email = EmailAddress("owner@berkah.com"),
            role = Role.TENANT_ADMIN,
            isActive = true
        )
        userRepo.save(user)

        application {
            module(tenantRepository = tenantRepo, userRepository = userRepo)
        }
        ...
```
**Mengapa blok ini ditulis begini?**
- Di Ktor, lambda pada `testApplication { ... }` memiliki signature `suspend ApplicationTestBuilder.() -> Unit`.
- Artinya, seluruh isi blok tersebut **sudah berjalan di dalam coroutine (suspendable context)**.
- Memanggil `runBlocking { ... }` di dalam fungsi `suspend` adalah **code smell dan anti-pattern**:
  - `runBlocking` memblokir thread saat ini hingga coroutine selesai, menghilangkan keuntungan non-blocking coroutine.
  - IDE compiler akan memberikan peringatan: *"Using 'runBlocking' inside a suspend function blocks the calling thread and defeats the purpose of asynchronous programming"*.
- Solusinya sangat sederhana: panggil langsung fungsi suspend `tenantRepo.save(tenant)` dan `userRepo.save(user)` secara direct tanpa membungkusnya.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Masalah & Solusi | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Pembersihan `runBlocking` di test** | Membiarkan `runBlocking` | Mematuhi model concurrency non-blocking coroutines secara murni | Memblokir thread worker test runner dan memicu peringatan linter |
| **Constructor Param tanpa `val`** | Menambahkan `private val` dan `@Suppress` | Bersih secara bahasa Kotlin dan menghemat memori object instance | Tercipta dead-code backing field dan warning code-smell |
| **Explicit Import di `main.kt`** | Mengubah struktur package atau wildcard import | Presisi, eksplisit, dan kompatibel dengan Kotlin Multiplatform IDE indexing | IDE terus menampilkan error merah "Unresolved reference" |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Asal Menaruh `val` di Setiap Parameter Constructor**
   - *Kenapa bahaya*: Developer baru sering terbiasa menulis `class Service(val a: A, val b: B)`. Jika `a` hanya dipakai sekali di init block, objek akan terus memegang referensi ke `a` selama lifecycle objek hidup, berpotensi menyebabkan memory leak jika `a` berukuran besar.
   - *Solusi elegan*: Hapus `val`/`var` jika parameter hanya dipakai saat inisialisasi awal.

2. **Jebakan 2: Menggunakan `runBlocking` Sebagai "Jalan Pintas" Memanggil Suspend Fun**
   - *Kenapa bahaya*: Sering kali pemula panik ketika fungsi membutuhkan `suspend`, lalu membungkusnya dengan `runBlocking`. Di lingkungan production atau reactive server, memblokir thread pool bisa menyebabkan server hang (*thread starvation*).
   - *Solusi elegan*: Selalu periksa apakah scope tempat kamu berada sudah `suspend`. Jika sudah `suspend`, panggil fungsi tersebut secara langsung.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Kita memverifikasi perubahan menggunakan perintah berikut:

1. **Kompilasi Desktop App**:
   ```bash
   ./gradlew :app:desktopApp:compileKotlin
   ```
   *Hasil*: `BUILD SUCCESSFUL` (0 error, simbol `App()` terselesaikan).

2. **Kompilasi Server**:
   ```bash
   ./gradlew :server:compileKotlin
   ```
   *Hasil*: `BUILD SUCCESSFUL` (Warning unused constructor property `secret` hilang).

3. **Uji Integrasi Ktor Auth**:
   ```bash
   ./gradlew :server:test --tests "com.eventverse.app.GoogleAuthIntegrationTest"
   ```
   *Hasil*: `BUILD SUCCESSFUL` (Semua test passing tanpa blocking thread warning).

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Amati perbedaan bytecode yang dihasilkan oleh `kotlinc` ketika menambahkan `val` vs tanpa `val` pada primary constructor class biasa.
- [ ] **Tantangan 2**: Buat satu unit test Ktor baru dengan `testApplication` dan coba integrasikan mock repository yang menggunakan `delay(50)` tanpa `runBlocking` untuk mengamati kelancaran coroutine execution.
