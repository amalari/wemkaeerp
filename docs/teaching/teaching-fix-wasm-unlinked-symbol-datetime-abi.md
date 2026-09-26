# 🎓 Modul Pembelajaran: Mengatasi Kotlin Wasm IR Linkage Error & ABI Mismatch pada kotlinx-datetime

> **Level Target**: Junior to Mid / Senior Multiplatform Engineer  
> **Topik Utama**: Kotlin Multiplatform (KMP), WebAssembly (WasmJs), Kotlin IR Partial Linkage, Gradle Dependency Conflict Resolution, kotlinx-datetime ABI  
> **Prasyarat**: Dasar Kotlin Multiplatform, pemahaman Gradle dependency graph, arsitektur WebAssembly klib  
> **Referensi Task**: Fix Runtime Exception: `Can not read value from variable 'createdAt': Variable uses unlinked class symbol 'kotlinx.datetime/Instant|null[0]'` di `/crm-sales`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat membuka halaman CRM Sales (`/crm-sales`) di browser pada target **Kotlin/Wasm (WebAssembly)**, aplikasi tiba-tiba crash dengan pesan error berwarna merah:
```text
Gagal memuat: Can not read value from variable 'createdAt': 
Variable uses unlinked class symbol 'kotlinx.datetime/Instant|null[0]'
```
Yang aneh bagi developer pemula adalah: **kode berhasil dikompilasi tanpa satupun error oleh Gradle (BUILD SUCCESSFUL)!** Namun begitu aplikasi berjalan di browser dan mengakses data lead, terjadi crash fatal.

### Analogi Sederhana: Puzzle Blok Kayu vs IR Partial Linkage
Bayangkan kamu memesan mainan puzzle kayu bertingkat:
1. Tukang kayu pertama membuat papan dasar (`core.klib`) menggunakan ukuran pasak standar 10mm (`kotlinx-datetime 0.6.2`).
2. Namun saat perakitan akhir di toko mainan (`webApp.wasm`), manajer toko menukar pasak dengan versi baru 12mm (`kotlinx-datetime 0.7.1`) yang didatangkan oleh supplier lain (`compose-material3`).
3. Toko tidak membatalkan pengiriman karena Kotlin punya fitur **Partial Linkage**—sistem berusaha mentolerir ketidakcocokan simbol agar aplikasi tetap bisa jalan sebagian.
4. Namun begitu tangan anak mencoba memasukkan pasak 12mm ke lubang 10mm saat bermain (mengakses variabel `lead.createdAt`), kayu tersebut patah seketika (**IrLinkageError / Unlinked Symbol Exception**).

---

## 🧭 2. "Start dari Mana?" — Alur Investigasi Akar Masalah (Order of Operations)

Jika menghadapi error `unlinked class symbol` di Kotlin Multiplatform/Wasm:

1. **Langkah 1: Jangan Terburu-buru Mengubah Kode Kotlin!**
   - Pesan `unlinked class symbol` **bukan** error logika Kotlin atau sintaks, melainkan tanda **Binary Incompatibility (ABI mismatch)** antara dependensi yang dipakai saat modul di-compile vs saat binary akhir di-link.
2. **Langkah 2: Periksa Dependency Insight di Gradle**
   - Jalankan command `dependencyInsight` pada configuration classpath target yang bersangkutan (`wasmJsRuntimeClasspath`):
     ```bash
     ./gradlew :app:webApp:dependencyInsight --configuration wasmJsRuntimeClasspath --dependency kotlinx-datetime
     ```
3. **Langkah 3: Temukan Pelaku Upgrade Transisi (Conflict Resolution)**
   - Periksa output Gradle:
     - Modul `:core` mengompilasi terhadap `kotlinx-datetime:0.6.2`.
     - Namun `org.jetbrains.compose.material3:material3-wasm-js:1.11.0-alpha07` menarik `kotlinx-datetime:0.7.1`.
     - Secara default, strategi resolusi konflik Gradle memilih versi tertinggi (`0.7.1`).
     - Akibatnya, modul `:core` yang dikompilasi dengan IR klib `0.6.2` di-link dengan klib `0.7.1` di tahap akhir, memicu perbedaan representasi simbol `Instant`.
4. **Langkah 4: Selaraskan Versi Melalui Resolution Strategy di Root Project**
   - Kunci (`force` / `useVersion`) versi `kotlinx-datetime` agar seluruh subprojek dan dependensi transitif menggunakan versi yang identik.
5. **Langkah 5: Bersihkan Cache Klib Stale & Validasi Binary**
   - Hapus cache klib lama di `build/klib/cache/wasm-js/developmentExecutable/`.
   - Gunakan `strings` pada file `.wasm` untuk memastikan tidak ada lagi string `unlinked class symbol`.

---

## 🧱 3. Bedah Solusi Blok per Blok Kode

### Blok A: Mengunci Versi Dependensi di `build.gradle.kts` Root
```kotlin
// build.gradle.kts (Root)
allprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-datetime")) {
                useVersion("0.6.2")
                because("Align all modules and Compose Material3 to 0.6.2 to avoid IR linkage mismatch")
            }
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
- `allprojects`: Memastikan aturan berlaku di seluruh modul (`:core`, `:app:shared`, `:app:webApp`, `:server`).
- `resolutionStrategy.eachDependency`: Mencegah Gradle menaikkan versi transitif secara otomatis ketika library pihak ketiga (seperti `compose.material3:1.11.0-alpha07`) meminta versi minor yang lebih tinggi (`0.7.1`).
- Dengan cara ini, modul `:core` dan modul `:webApp` sama-sama mengonsumsi ABI `kotlinx-datetime 0.6.2` yang 100% konsisten.

### Blok B: Robust Date/Time Parsing Menggunakan `DateTimeCodec`
Sebelum perbaikan, banyak parser di domain menggunakan blok `runCatching` telanjang yang rentan terhadap unlinked symbol:
```kotlin
// SEBELUM: Rentan unlinked symbol & silent failure
val date = runCatching { LocalDate.parse(str) }.getOrNull()

// SESUDAH: Menggunakan helper terisolasi DateTimeCodec
val date = DateTimeCodec.parseLocalDateOrNull(str)
```
**Mengapa blok ini ditulis begini?**
- Di `core/src/commonMain/kotlin/com/eventverse/app/domain/datetime/DateTimeCodec.kt`, fungsi helper mengisolasi parsing dan validasi string tanggal (ISO-8601) secara terpusat.
- Menghilangkan duplikasi blok parsing di puluhan file codec domain (`CustomAttributesCodec`, `SamplingOrderCodec`, `MaterialPriceCodec`, `TechPackAndYieldDataCodec`, dll).

### Blok C: Menghindari Value Class Boxing saat Pengurutan (Sorting)
```kotlin
// SEBELUM:
val latest = prices.maxByOrNull { it.effectiveFrom }

// SESUDAH:
val latest = prices.maxByOrNull { it.effectiveFrom.toEpochMilliseconds() }
```
**Mengapa blok ini ditulis begini?**
- Pada Kotlin/Wasm, `Instant` adalah value class. Pemanggilan lambda generic seperti `Comparable<Instant>` terkadang memerlukan boxing ke interface runtime.
- Mengambil primitif `toEpochMilliseconds()` (`Long`) memastikan perbandingan nilai berlangsung cepat di level register WebAssembly tanpa risiko delegasi method klib yang tidak terhubung.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif Lain | Mengapa Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Pin `0.6.2` via `resolutionStrategy`** | Memaksa upgrade ke `0.7.1` di semua modul | `kotlinx-datetime 0.7.1` memecah package JVM dan memindahkan `Clock.System`, merusak kompilasi modul Ktor Server dan backend. Pin `0.6.2` menjaga stabilitas fullstack. | Kompilasi server rusak, butuh refactor massal imports di backend. |
| **Pembersihan Cache Klib Manual** | Hanya mengandalkan `./gradlew --continuous` | Incremental compiler Webpack Wasm terkadang mempertahankan AST `.bin` lama yang sudah memuat pesan error `unlinked symbol`. | Error runtime tetap muncul meski kodingan sudah benar. |
| **`DateTimeCodec` Centralization** | `runCatching { Instant.parse() }` ad-hoc di tiap file | Single Source of Truth, penanganan fallback format tanggal yang seragam dan mudah di-audit. | Inkonsistensi format, silent bug jika exception tertelan tanpa log. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Mengira Error Kotlin Wasm Selalu Terdeteksi Saat `compileKotlin`**
   - *Kenapa bahaya*: Kotlin mengaktifkan Partial Linkage secara default. Jika ada simbol hilang, compiler tidak selalu memutus build, melainkan mengganti panggilan fungsi tersebut dengan kode `throw IrLinkageError(...)`. Build sukses, tapi meledak di tangan user.
   - *Solusi*: Selalu periksa output `strings *.wasm | grep -i "unlinked"` jika mencurigai ada ketidakcocokan library klib.
2. **Jebakan 2: Menambahkan Library Baru Tanpa Memeriksa Versi Transitifnya**
   - *Kenapa bahaya*: Menambahkan satu library Compose alpha dapat mendongkrak versi `kotlinx-datetime` atau `kotlinx-coroutines` yang dipakai seluruh proyek.
   - *Solusi*: Selalu jalankan `dependencyInsight` saat mengintegrasikan dependensi baru.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Bekerja?

1. **Cek Tidak Ada Unlinked Symbol pada Binary Wasm**:
   ```bash
   strings app/webApp/build/kotlin-webpack/wasmJs/developmentExecutable/*.wasm | grep -i "unlinked"
   # Output wajib: 0 unlinked symbols found!
   ```
2. **Kompilasi Seluruh Target Lintas Platform**:
   ```bash
   ./gradlew :core:compileKotlinWasmJs :core:compileKotlinJvm :app:shared:compileKotlinWasmJs :server:compileKotlin
   ```
3. **Verifikasi End-to-End di Browser**:
   - Jalankan `./dev.sh all`.
   - Buka `http://localhost:3000/crm-sales`.
   - Amati Kanban Board CRM: kolom "New Lead", "Qualified Lead", dan total pipeline `Rp 25.000.000` harus ter-render mulus tanpa pesan error merah.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka terminal dan jalankan `./gradlew :app:webApp:dependencyInsight --configuration wasmJsRuntimeClasspath --dependency kotlinx-datetime`. Perhatikan bagaimana baris `Selected by rule` bekerja membatalkan upgrade dari `material3-wasm-js`.
- [ ] **Tantangan 2**: Pelajari dokumentasi Kotlin mengenai [Partial Linkage in Kotlin/Native and Kotlin/Wasm](https://kotlinlang.org/docs/native-improving-compilation-time.html#partial-linkage). Pahami kapan compiler melempar warning vs kapan compiler melempar runtime exception.
