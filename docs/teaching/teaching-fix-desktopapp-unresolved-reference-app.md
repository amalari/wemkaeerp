# 🎓 Modul Pembelajaran: Mengatasi "Unresolved reference 'App'" pada Modul Desktop JVM di Proyek Kotlin Multiplatform (KMP)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Kotlin Multiplatform (KMP), Gradle Variant Resolution, IDE Language Server Classpath, JVM vs KMP Module Dependency  
> **Prasyarat**: Dasar Kotlin, Gradle Multi-Project Build, dan pemahaman konsep Kotlin Multiplatform  
> **Referensi Task**: Fix Unresolved reference 'App' in `:app:desktopApp` (`main.kt:L11`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Bayangkan kamu sedang mengembangkan aplikasi konveksi & garmen berbasis **Kotlin Multiplatform (KMP)**. Di terminal, saat kamu menjalankan perintah build:
```bash
./gradlew :app:desktopApp:compileKotlin
```
Gradle tersenyum manis dan menampilkan **`BUILD SUCCESSFUL`**. Tidak ada error satu pun!

Namun, begitu kamu membuka file [main.kt](file:///Volumes/amalari/Projects/wemade/app/desktopApp/src/main/kotlin/com/eventverse/app/main.kt) di editor IDE:
Fungsi `App()` digarisbawahi warna merah menyala dengan pesan:
```text
Unresolved reference 'App'
```
Jika seorang developer junior belum memahami cara kerja internal IDE Language Server vs Gradle Compiler, mereka biasanya akan mencoba:
1. Menambahkan `import com.eventverse.app.App` secara manual. (Lalu IDE malah memunculkan garis merah di baris import).
2. Menghapus baris import. (Garis merah kembali pindah ke pemanggilan `App()`).
3. Mengubah visibility modifier menjadi `public` (padahal fungsi sudah public).
4. Restart IDE berulang kali tanpa hasil.

### Analogi Sederhana
Bayangkan sebuah **gudang pusat logistik (Gradle CLI)** dan **kantor resepsionis (IDE Language Server)**.
- Gudang pusat punya katalog multi-bahasa yang sangat canggih (KMP Variant Resolution). Gudang tahu persis bahwa pesanan untuk truk JVM harus mengambil kardus varian JVM (`shared-jvm.jar`).
- Resepsionis kantor (Language Server berbasis Eclipse JDT / standard Java tooling) hanya mengerti formulir standar Java zaman dulu. Saat melihat proyek `:app:shared` yang berformat KMP multi-target (Android, iOS, Wasm, JS, JVM), resepsionis bingung dan mencoret folder tersebut dari buku daftar tamu. Akibatnya, resepsionis memberitahu kamu: *"Saya tidak tahu siapa itu `App`!"*, meskipun gudang pusat di belakang bisa memproduksinya dengan sempurna.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan & Investigasi (Order of Operations)

Jika kamu menghadapi situasi di mana Gradle berhasil mengompilasi proyek tetapi IDE mengeluh simbol hilang antar-modul KMP dan JVM:

1. **Langkah 0: Bedakan Compiler Truth vs IDE Truth**
   - Jalankan `./gradlew :app:desktopApp:compileKotlin --rerun-tasks` di terminal. Jika sukses, berarti kode Kotlin-mu secara sintaksis dan kontrak Gradle **sudah benar**. Masalahnya murni berada di **classpath resolution pada IDE Language Server**.
2. **Langkah 1: Identifikasi Jenis Modul Konsumen dan Modul Produsen**
   - Modul Produsen: `:app:shared` menggunakan plugin `kotlinMultiplatform`. Artefak JVM-nya adalah file JAR `app/shared/build/libs/shared-jvm.jar`.
   - Modul Konsumen: `:app:desktopApp` menggunakan plugin JVM standar `kotlinJvm` (`org.jetbrains.kotlin.jvm`).
3. **Langkah 2: Telusuri Konsistensi Solusi di Seluruh Codebase**
   - Cek modul lain yang memiliki arsitektur serupa. Di proyek WeMade ERP, modul `:server` juga merupakan modul JVM murni yang mengonsumsi modul KMP `:core`.
   - Di [server/build.gradle.kts](file:///Volumes/amalari/Projects/wemade/server/build.gradle.kts#L14-L15), kita menemukan preseden arsitektur:
     ```kotlin
     implementation(project(":core"))
     // Ensure IDE Language Server (without KMP support) can resolve domain symbols from compiled jar
     compileOnly(files(rootProject.file("core/build/libs/core-jvm.jar")))
     ```
4. **Langkah 3: Terapkan Pola `compileOnly` Fallback pada Modul Konsumen**
   - Tambahkan direct JAR mapping ke `app/desktopApp/build.gradle.kts` menggunakan konfigurasi `compileOnly`.
5. **Langkah 4: Verifikasi Kompilasi & Indeks Simbol**
   - Jalankan kembali Gradle task agar konfigurasi cache diperbarui dan language server membaca ulang classpath JAR.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Entry Point Desktop Application
File: [app/desktopApp/src/main/kotlin/com/eventverse/app/main.kt](file:///Volumes/amalari/Projects/wemade/app/desktopApp/src/main/kotlin/com/eventverse/app/main.kt)

```kotlin
package com.eventverse.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

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
- Package file ini adalah `com.eventverse.app`.
- Fungsi `@Composable fun App()` di modul `:app:shared` dideklarasikan di file `App.kt` dengan package yang sama persis: `package com.eventverse.app`.
- Dalam aturan bahasa Kotlin, simbol top-level yang berada di package yang sama berada dalam satu lingkup (*same package scope*), sehingga pemanggilan `App()` tidak memerlukan baris `import com.eventverse.app.App`.

---

### Blok B: Konfigurasi Dependensi Desktop App
File: [app/desktopApp/build.gradle.kts](file:///Volumes/amalari/Projects/wemade/app/desktopApp/build.gradle.kts)

```kotlin
dependencies {
    implementation(project(":app:shared"))
    // Ensure IDE Language Server (without KMP support) can resolve shared & domain symbols from compiled jar
    compileOnly(files(rootProject.file("app/shared/build/libs/shared-jvm.jar")))
    compileOnly(files(rootProject.file("core/build/libs/core-jvm.jar")))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}
```
**Mengapa blok ini ditulis begini?**
- `implementation(project(":app:shared"))`: Ini adalah kontrak resmi Gradle. Ini memberitahu Gradle bahwa saat build berjalan, modul `:app:desktopApp` bergantung pada subproyek `:app:shared`. Gradle CLI mengerti cara mengekstrak varian JVM dari `:app:shared` lewat metadata KMP.
- `compileOnly(files(rootProject.file("app/shared/build/libs/shared-jvm.jar")))`: Konfigurasi `compileOnly` hanya aktif pada saat *compilation / code analysis*, dan **tidak dimasukkan ke dalam paket distribusi runtime final** (sehingga tidak ada duplikasi class atau bloatware). Eclipse JDT LS / Antigravity IDE membaca direct JAR file ini secara instan dan mengindeks seluruh class, termasuk `AppKt.class`.
- `compileOnly(files(rootProject.file("core/build/libs/core-jvm.jar")))`: Memastikan seluruh domain model, entities, dan value objects dari `:core` juga langsung dikenali oleh IDE saat membuka file desktop.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif Lain | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **`compileOnly(files(...))` di `build.gradle.kts`** | Memaksa user menginstal plugin IntelliJ KMP komersial atau re-import manual | **Portabilitas instan**: Bekerja mulus di semua editor (VS Code, Antigravity IDE, Cursor, IntelliJ, Android Studio) tanpa ketergantungan konfigurasi lokal developer. | Jika developer lain membuka proyek di IDE non-IntelliJ KMP, editor mereka akan penuh garis merah palsu (*ghost red squiggles*). |
| **Gunakan `compileOnly` (bukan `implementation`)** | Memakai `implementation(files(...))` | **Mencegah Duplikasi Runtime**: `compileOnly` tidak di-pack ke dalam fat JAR atau native installer desktop distribution (`.dmg`, `.deb`, `.msi`), sehingga tidak terjadi *duplicate class definition*. | Jika memakai `implementation(files(...))`, saat packaging desktop app akan terjadi bentrok nama kelas (*Dex/Jar packaging collision*). |
| **Pertahankan Single-Package `com.eventverse.app`** | Memecah package name per modul (misal: `com.eventverse.app.desktop`) | Konsistensi arsitektur domain dan aturan DDD dasar proyek: shared application UI menyatu di base package. | Memecah package hanya untuk memuaskan parser IDE yang bermasalah merusak clean architecture dan menambah boilerplate import di seluruh file. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Mengira Error IDE Pasti Kesalahan Kode Kotlin**
   - *Kenapa bahaya*: Developer junior sering menghabiskan berjam-jam mengubah kode fungsional (menambahkan import aneh, memindahkan fungsi, mengubah signature) hanya karena editor menampilkan garis merah, padahal `./gradlew compileKotlin` sukses 100%.
   - *Solusi elegan*: Selalu uji via `./gradlew :<module>:compileKotlin`. Jika CLI lulus, fokuslah pada konfigurasi resolusi classpath IDE tooling.

2. **Jebakan 2: Terjebak Ping-Pong "Line 5 vs Line 11"**
   - *Kenapa bahaya*: Ketika baris `import com.eventverse.app.App` digarisbawahi merah, menghapus baris itu hanya memindahkan error ke tempat `App()` dipanggil di baris 11. Keduanya adalah gejala dari akar masalah yang sama: simbol `App` tidak ada di classpath IDE.
   - *Solusi elegan*: Jangan hanya mengobati gejalanya (menghapus import), tetapi sediakan sumber class-nya ke classpath (`compileOnly(files(".../shared-jvm.jar"))`).

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Verifikasi Kompilasi Bersih Gradle**:
   ```bash
   ./gradlew :app:desktopApp:compileKotlin --rerun-tasks
   ```
   *Expected Output*:
   `BUILD SUCCESSFUL` (semua 16 task dieksekusi dengan sukses).

2. **Verifikasi Simbol pada JAR**:
   Jalankan inspeksi isi JAR untuk memastikan `AppKt.class` benar-benar ada di dalam `shared-jvm.jar`:
   ```bash
   jar tf app/shared/build/libs/shared-jvm.jar | grep "com/eventverse/app/AppKt.class"
   ```
   *Expected Output*:
   `com/eventverse/app/AppKt.class` terdaftar.

3. **Verifikasi Integrasi Desktop App**:
   Jalankan task assemble jar desktop:
   ```bash
   ./gradlew :app:desktopApp:jar
   ```
   *Expected Output*:
   `BUILD SUCCESSFUL` dan artefak `app/desktopApp/build/libs/desktopApp.jar` berhasil dibuat.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka file [server/build.gradle.kts](file:///Volumes/amalari/Projects/wemade/server/build.gradle.kts) dan bandingkan deklarasi dependensi `:core` dengan deklarasi di [app/desktopApp/build.gradle.kts](file:///Volumes/amalari/Projects/wemade/app/desktopApp/build.gradle.kts). Amati bagaimana kedua modul JVM murni ini menerapkan pola yang sama.
- [ ] **Tantangan 2**: Jalankan `./gradlew :app:desktopApp:dependencies` di terminal untuk melihat pohon dependensi Gradle. Amati bagaimana varian `jvm` dari `:app:shared` dipilih secara otomatis oleh Gradle.
