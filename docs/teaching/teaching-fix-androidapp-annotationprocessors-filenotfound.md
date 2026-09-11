# 🎓 Modul Pembelajaran: Mengatasi "FileNotFoundException: annotationProcessors.json" pada Gradle Model Sync di Antigravity / IntelliJ

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Android Gradle Plugin (AGP), Variant API (`androidComponents`), Language Server Gradle Model Sync, Build Optimization  
> **Prasyarat**: Pemahaman dasar Gradle Multi-Project, Android Gradle Plugin (AGP), dan konfigurasi `build.gradle.kts`  
> **Referensi Task**: Fix `java.io.FileNotFoundException: .../debugAndroidTest/.../annotationProcessors.json` in `:app:androidApp`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat membuka proyek Kotlin Multiplatform (KMP) di **Antigravity IDE** atau **IntelliJ IDEA**, tiba-tiba muncul notifikasi error merah menyala di event log:
```text
Unable to resolve all compiler arguments for module debugAndroidTest
java.io.UncheckedIOException: java.io.FileNotFoundException: .../app/androidApp/build/intermediates/annotation_processor_list/debugAndroidTest/javaPreCompileDebugAndroidTest/annotationProcessors.json (No such file or directory)
    at com.android.build.gradle.tasks.JavaCompileUtils.readAnnotationProcessorsFromJsonFile(JavaCompileUtils.kt:335)
    at com.android.build.gradle.tasks.JavaCompileOptionsForRoom.asArguments(JavaCompile.kt:213)
    at org.gradle.api.tasks.compile.CompileOptions.getAllCompilerArgs(CompileOptions.java:356)
    at com.jetbrains.ls.imports.gradle.model.builder.android.AndroidSourceSets.resolveToModuleSourceSet(resolveAndroidSourceSets.kt:62)
```

Jika seorang developer junior belum memahami siklus hidup (*lifecycle*) sinkronisasi IDE vs eksekusi build Gradle, reaksi pertamanya biasanya:
1. Mengira ada dependensi annotation processor / Room yang hilang.
2. Mencoba menambahkan plugin `kapt` atau library Room secara manual di `build.gradle.kts`.
3. Menghapus folder `.gradle` atau `.idea` tanpa menyelesaikan akar masalah.

### Analogi Sederhana
Bayangkan sebuah **kantor pendaftaran atlet lari maraton**:
- Panitia lomba (**IDE Language Server**) ingin mendata daftar semua peserta beserta perlengkapannya (**Compiler Arguments**).
- Saat panitia mengecek nomor lari untuk kategori *uji coba ekstrem* (**`debugAndroidTest`**), panitia langsung mencari laporan hasil uji laboratorium kesehatan (**`annotationProcessors.json`**).
- Padahal lomba maraton belum dimulai dan atlet bersangkutan belum pernah masuk lab (**task `javaPreCompileDebugAndroidTest` belum pernah dijalankan**).
- Alih-alih menunggu atau mengabaikannya jika atlet itu sebenarnya tidak ikut kategori maraton tersebut, panitia langsung panik dan membatalkan seluruh pendaftaran (**`FileNotFoundException`**).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan & Investigasi (Order of Operations)

Jika kamu menemukan error pembacaan file build cache atau intermediate file saat proses import/sync:

1. **Langkah 0: Bedah Siapa yang Memanggil (Trace The Caller)**
   - Perhatikan baris teratas stack trace:
     `com.jetbrains.ls.imports.gradle.model.builder.android.AndroidSourceSets`
   - Ini adalah **JetBrains Language Server (LS)** yang ditanam di Antigravity / IntelliJ untuk keperluan navigasi kode dan code intelligence.
   - Panggilan terjadi pada saat pembentukan model AST/SourceSet, bukan saat kamu memencet tombol *Run* aplikasi.

2. **Langkah 1: Identifikasi Komponen yang Menyebabkan Crash**
   - Method yang gagal: `JavaCompileUtils.readAnnotationProcessorsFromJsonFile`.
   - Modul target: `:app:androidApp` pada variant `debugAndroidTest`.
   - AGP secara otomatis mendaftarkan task `javaPreCompileDebugAndroidTest` yang bertugas memproduksi file `annotationProcessors.json`. Namun, sebelum task ini dijalankan, filenya **belum ada di disk**.

3. **Langkah 2: Evaluasi Kebutuhan Domain & Modul**
   - Apakah modul `:app:androidApp` membutuhkan instrumentation test (`androidTest`)?
   - **Tidak!** Di arsitektur Clean DDD & Kotlin Multiplatform proyek ini:
     - Modul `:core` berisi Entity, Value Objects, dan UseCases murni.
     - Modul `:app:shared` berisi UI Composable dan ViewModel yang diuji dengan host unit tests (`testAndroidHostTest`).
     - Modul `:app:androidApp` **hanya runner tipis** (`MainActivity.kt`) yang membungkus pemanggilan Composable `App()`. Tidak ada folder `src/androidTest` di sana.

4. **Langkah 3: Terapkan Solusi Permanen Menggunakan AGP Variant API**
   - Nonaktifkan pembuatan variant `androidTest` pada modul tersebut menggunakan blok `androidComponents`.
   - Hal ini membuat Gradle tidak lagi mendaftarkan variant `debugAndroidTest`, sehingga JetBrains Language Server tidak akan pernah mencari file pre-compile test tersebut.

5. **Langkah 4: Verifikasi dengan `./gradlew clean` dan Build Ulang**
   - Pastikan build bersih dari nol tetap berjalan sukses tanpa error `FileNotFoundException`.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Perubahan dilakukan pada file [app/androidApp/build.gradle.kts](file:///Volumes/amalari/Projects/wemade/app/androidApp/build.gradle.kts):

```kotlin
// app/androidApp/build.gradle.kts

android {
    namespace = "com.eventverse.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    // ... konfigurasi android lainnya ...
    buildFeatures {
        compose = true
    }
}

// ========================================================================
// BLOK PERBAIKAN: AGP Android Components Variant API
// ========================================================================
androidComponents {
    beforeVariants { variantBuilder ->
        // Menonaktifkan pembuatan target instrumentation test pada modul launcher ini
        variantBuilder.enableAndroidTest = false
    }
}
```

### Mengapa Blok Ini Ditulis Begini?

1. **`androidComponents` vs Blok Lama `variantFilter`**:
   - Di AGP versi lawas (AGP 4.x - 7.x), developer sering memakai `android.variantFilter { ... }`.
   - Namun di AGP modern (AGP 8.x dan 9.x), Google merancang ulang arsitektur Gradle menggunakan **New Variant API** (`androidComponents`). Blok ini dieksekusi **sebelum** task-task variant dibuat ke dalam task graph Gradle, sehingga jauh lebih hemat memori dan aman dari race condition.

2. **`beforeVariants { variantBuilder -> variantBuilder.enableAndroidTest = false }`**:
   - `beforeVariants` memberikan akses ke `VariantBuilder` sebelum *variant final* dikunci.
   - Dengan mematikan `enableAndroidTest`, AGP tidak akan membuang siklus CPU untuk membuat puluhan task test seperti `compileDebugAndroidTestSources`, `processDebugAndroidTestManifest`, dan `javaPreCompileDebugAndroidTest`.
   - Ketika JetBrains Language Server di Antigravity meminta daftar compiler arguments untuk modul `:app:androidApp`, variant `debugAndroidTest` sudah tidak ada di dalam daftar, sehingga crash pembacaan file `annotationProcessors.json` tidak pernah terjadi lagi.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Menonaktifkan `androidTest` via `androidComponents`** | Menjalankan task manual: `./gradlew :app:androidApp:javaPreCompileDebugAndroidTest` | **Permanen & Preventif**: Begitu developer melakukan `./gradlew clean` atau clone repository di komputer baru, error tidak akan pernah muncul kembali. | **Solusi Sementara (Quick Fix)**: Begitu build folder terhapus atau dilakukan *Clean Project*, error `FileNotFoundException` akan langsung muncul kembali di IDE. |
| **Penyederhanaan Modul Runner (`:app:androidApp`)** | Membuat folder kosong `src/androidTest` dan dummy test file | Menjaga modul runner tetap ramping dan terfokus hanya pada tugasnya sebagai launcher platform Android. | Menambah bloat build time dan konfigurasi palsu yang tidak ada nilainya bagi bisnis. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Meletakkan `androidComponents` di dalam `afterEvaluate`**
   - *Kenapa bahaya*: Pemanggilan `beforeVariants` harus didaftarkan saat evaluasi proyek awal. Jika dibungkus di dalam `afterEvaluate { ... }`, variants sudah terbentuk dan terkunci, sehingga Gradle akan melempar exception: *`Cannot query the value of this property because it has no value available`* atau *`Failed to call 'onVariants'`*.
   - *Solusi elegan kita*: Letakkan `androidComponents { ... }` langsung di root level file `build.gradle.kts`.

2. **Jebakan 2: Mematikan Test di Modul yang Seharusnya Punya Test**
   - *Kenapa bahaya*: Jika kamu mematikan `enableAndroidTest` di modul yang memang membutuhkan UI Test (misalnya `:app:shared`), tes integrasi UI di CI/CD pipeline tidak akan bisa berjalan.
   - *Solusi elegan kita*: Hanya matikan di modul runner tipis (`:app:androidApp`). Pengujian aplikasi tetap berjalan aman di `:app:shared` dan `:core`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### Verifikasi 1: Daftar Task Bersih dari `AndroidTest`
Jalankan di terminal:
```bash
./gradlew :app:androidApp:tasks --all | grep -i androidtest
```
*Hasil*: Tidak ada task `compileDebugAndroidTestSources` atau `javaPreCompileDebugAndroidTest` yang terdaftar.

### Verifikasi 2: Clean Build dari Nol
Jalankan skenario ekstrem penghapusan cache total:
```bash
./gradlew clean :app:androidApp:assembleDebug
```
*Hasil*: 
```text
BUILD SUCCESSFUL in 5s
91 actionable tasks: 48 executed, 32 from cache, 11 up-to-date
```
Build sukses 100% tanpa ada keluhan file hilang, dan IDE Antigravity dapat melakukan Gradle Sync dengan mulus!

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka file [app/androidApp/build.gradle.kts](file:///Volumes/amalari/Projects/wemade/app/androidApp/build.gradle.kts) dan periksa apakah modul ini juga membutuhkan `unitTest` (`testDebugUnitTest`). Jika tidak ada unit test di modul launcher ini, bagaimana sintaks untuk menonaktifkan unit test juga? (Petunjuk: `variantBuilder.enableUnitTest = false`).
- [ ] **Tantangan 2**: Pelajari perbedaan antara callback `beforeVariants` dan `onVariants` di dokumentasi resmi [Android Developers - Android Gradle Plugin Variant API](https://developer.android.com/build/extend-agp). Kapan kita harus memakai `beforeVariants` dan kapan memakai `onVariants`?
