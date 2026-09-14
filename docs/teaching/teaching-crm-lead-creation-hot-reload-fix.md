# 🎓 Modul Pembelajaran: Diagnosa & Penanganan Error Hot Reload KMP Value Class pada Server Backend

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Kotlin Multiplatform (`@JvmInline value class`), In-memory ClassLoader vs Gradle Daemon, Ktor Serialization/Deserialization, Custom Field Validation  
> **Prasyarat**: Pemahaman dasar tentang JVM ClassLoader, Value Class Kotlin, dan integrasi Client-Server di Ktor/KMP  
> **Referensi Task**: Fix CRM Lead Creation Error (`500 Internal Server Error: com/eventverse/app/domain/crm/BrandName` & `ListLeadsUseCase$invoke$1`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat mengembangkan aplikasi full-stack Kotlin Multiplatform (KMP) yang menggabungkan:
- **Client (Wasm / Webpack)** yang terus berjalan dengan auto-watching (`--continuous`), dan
- **Server (Ktor JVM)** yang dijalankan via background process (`dev.sh`),

seorang developer sering kali memodifikasi domain model di modul `:core` (seperti mengubah value class `BrandName` agar bersifat opsional dengan nilai default `""`).

Tiba-tiba, saat menguji pengiriman data (misal melalui cURL atau form frontend):
```bash
curl --url 'http://localhost:3000/api/tenant/crm/leads' \
  --data-raw '{"brandName":"","contactPerson":"test","whatsappNumber":"62822444418322",...}'
```
Server mengembalikan response yang mengejutkan:
```text
HTTP/1.1 500 Internal Server Error
com/eventverse/app/domain/crm/BrandName
```
Di UI, lead tidak bisa ditambahkan sama sekali.

### Analogi Sederhana
Bayangkan sebuah restoran di mana tim koki di dapur (modul `:core`) telah memperbarui resep masakan dan format buku menu. Namun, pelayan di meja kasir (proses JVM Ktor yang sudah menyala selama 1 jam lebih) masih memegang hafalan menu lama di kepalanya tanpa pernah membaca ulang buku resep baru. Ketika ada pesanan datang dengan format baru, kasir mengalami kebingungan (*NoClassDefFoundError / NoSuchMethodError*) karena bentuk data di memori tidak cocok dengan ekspektasi bytecodenya.

### Hasil Akhir yang Diharapkan
1. Pemahaman mendalam mengapa perubahan signature Kotlin `@JvmInline value class` di `:core` menyebabkan ketidakcocokan in-memory bytecode di server yang sedang berjalan.
2. Memahami mengapa compile error di modul `:core` (seperti di `UpdateMaterialItemUseCase`) dapat menghambat pembaruan bytecode `:server`.
3. Alur diagnosa dan verifikasi sistematis dari log JVM, proses port, recompile, hingga testing API endpoint end-to-end.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Diagnosa & Perbaikan

Ketika menghadapi error `500` dengan pesan class name seperti `com/eventverse/app/domain/crm/BrandName` atau `java.lang.NoClassDefFoundError`:

1. **Langkah 1: Replikasi Request dengan cURL**
   - Jalankan request persis seperti yang dikirim client.
   - Periksa HTTP Status dan pesan response body. Jika response body hanya berisi nama kelas JVM, ini adalah indikasi kuat unhandled exception berupa `NoClassDefFoundError` atau `NoSuchMethodError`.

2. **Langkah 2: Periksa Proses & Port yang Sedang Berjalan**
   - Cek PID proses yang mendengarkan port 8080: `lsof -nP -i :8080`.
   - Lihat waktu mulai proses (`ps -p <PID> -f`). Jika server sudah menyala berjam-jam sementara file domain baru saja diubah, memori JVM memuat bytecode lama.

3. **Langkah 3: Periksa Integritas Kompilasi Modul (`:core` & `:server`)**
   - Jalankan `./gradlew :core:compileKotlinJvm :server:compileKotlin`.
   - Jangan berasumsi modul lain aman: periksa apakah ada compile error tersembunyi yang membuat Gradle menghentikan build modul hilir.

4. **Langkah 4: Perbaiki Compile Error di Domain Layer**
   - Perbaiki method invocation yang tidak sinkron (misalnya penggunaan `validateForPatch` dan `attrs.with` pada custom attributes).

5. **Langkah 5: Restart Bersih Proses Server JVM**
   - Matikan proses server lama (`kill -9 <PID>`).
   - Nyalakan ulang server backend dengan bytecode segar (`./gradlew :server:run`).

6. **Langkah 6: Verifikasi End-to-End dengan cURL**
   - Tes skenario `NEW_LEAD` dengan `brandName: ""`.
   - Tes skenario `QUALIFIED` dengan `email` terisi dan nomor HP lokal Indonesia.
   - Tes `GET /api/tenant/crm/leads` untuk memastikan listing data juga berjalan sempurna tanpa error class loading.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Karakteristik `@JvmInline value class`
```kotlin
// core/src/commonMain/kotlin/com/eventverse/app/domain/crm/CrmLeadValueObjects.kt
@JvmInline
value class BrandName(val value: String = "") {
    init {
        require(value.length <= 150) { "BrandName must be at most 150 characters" }
    }

    val isBlank: Boolean get() = value.isBlank()

    fun display(fallback: String = "Tanpa Nama Brand"): String =
        if (value.isBlank()) fallback else value
}
```
**Mengapa blok ini penting dipahami?**
- Kotlin me-mangling nama method dan konstruktor untuk inline value class pada level JVM bytecode (misal `constructor-impl(String)`).
- Ketika kita menambahkan default parameter `val value: String = ""` atau menghapus validasi `require(value.isNotBlank())`, compiler menghasilkan synthetic metadata baru.
- Jika server Ktor masih menjalankan bytecode lama, setiap panggilan ke `BrandName("")` akan mencari signature lama dan melempar `NoClassDefFoundError` atau `NoSuchMethodError`.

### Blok B: Penanganan Kolom Kustom yang Konsisten
```kotlin
// core/src/commonMain/kotlin/com/eventverse/app/domain/masterdata/usecases/UpdateMaterialItemUseCase.kt
if (command.customValues.isNotEmpty()) {
    val definitions = customFieldRepository.findActiveByResource(command.tenantId, OwnerResource.MASTER_DATA_MATERIAL)
    val errors = CustomFieldValidation.validateForPatch(definitions, updated.createdAt, command.customValues)
    if (errors.isNotEmpty()) throw MaterialValidationException(errors)

    var attrs = updated.customAttributes
    command.customValues.forEach { (fieldId, cell) ->
        attrs = attrs.with(fieldId, cell)
    }
    updated = updated.withCustomAttributes(attrs, now)
}
```
**Mengapa perbaikan ini krusial?**
- Di `CustomFieldValidation`, fungsi untuk update parsial adalah `validateForPatch(definitions, recordCreatedAt, patch)`.
- Di `CustomAttributes`, fungsi `attrs.with(fieldId, cell)` sudah secara elegan menangani kondisi jika `cell == null` dengan menghapus key tersebut dari map internal, sehingga tidak memerlukan fungsi `.without()`.
- Menyelesaikan compile error ini membuat seluruh jar `:core` ter-package sempurna.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif yang Ada | Mengapa Memilih Pendekatan Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **JVM Process Kill & Recompile Explicit** | Mengandalkan hot swapping (HotswapAgent / DCEVM) | Menjamin status memori JVM 100% konsisten dengan skema database dan bytecode baru tanpa resiko dirty state | Hot swapping pada Kotlin value class sering gagal mendeteksi perubahan synthetic method mangling |
| **Value Class dengan Default Parameter** | Menggunakan tipe data `String?` nullable biasa | Menjaga domain encapsulation; method seperti `brandName.display()` tetap bisa dipanggil tanpa safe-call berulang di mana-mana | Primitive obsession dan risiko string formatting inkonsisten di berbagai modul |
| **`attrs.with(fieldId, cell)` Null Removal** | Menambah method `without()` terpisah | Menjaga API surface dari value bag `CustomAttributes` tetap ringkas dan fungsional | Duplikasi logika mutasi map yang berpotensi menyebabkan bug state management |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Mengira Webpack Reload Berarti Server Backend Juga Reload**
   - *Kenapa bahaya*: `./dev.sh` menjalankan Webpack Wasm dengan `--continuous` (yang langsung meng-compile ulang bundle browser saat file frontend disimpan). Namun, Ktor server dijalankan dengan `./gradlew :server:run` biasa. Backend tidak me-restart dirinya sendiri saat file Kotlin `:core` atau `:server` berubah!
   - *Solusi*: Setiap kali ada perubahan di file domain Kotlin (`core/`) atau route backend (`server/`), server Ktor wajib di-restart agar memuat bytecode terbaru.

2. **Jebakan 2: Terkecoh oleh HTTP 500 Tanpa JSON Error Body**
   - *Kenapa bahaya*: Ketika server mengembalikan status 500 dengan body string mentah berupa path kelas Java (`com/eventverse/app/...`), banyak pemula mengira ada bug query SQL atau JSON parsing. Padahal itu adalah unhandled exception fatal di level ClassLoader sebelum request masuk ke controller logic.
   - *Solusi*: Periksa log server Ktor di console/task log untuk melihat stack trace lengkap (`ClassNotFoundException` / `NoClassDefFoundError`).

3. **Jebakan 3: `runCatching` pada Value Class di Kotlin/Wasm (`IrLinkageError: Instant|null[0]`)**
   - *Kenapa bahaya*: `kotlinx.datetime.Instant` adalah Kotlin `@JvmInline value class`. Menggunakan `runCatching { Instant.parse(it) }` di dalam lambda inlining menghasilkan `Result<Instant>` yang membungkus value class di level intermediate representation (IR) Wasm menjadi tipe `kotlinx.datetime/Instant|null[0]`. Saat dijalankan di browser, runtime Kotlin/Wasm gagal menautkan (link) symbol tersebut dan memunculkan error: `Can not read value from variable 'createdAt': Variable uses unlinked class symbol 'kotlinx.datetime/Instant|null[0]'`.
   - *Solusi*: Hindari inlined `runCatching` / `Result<T>` pada value class di shared wire codecs. Gunakan fungsi parser sederhana berbasis direct `try { ... } catch (_: Exception) { ... }` seperti yang diimplementasikan di `DateTimeCodec.kt`.

---

## 🧪 6. Bagaimana Membuktikan Kodingan Bekerja?

### 1. Uji Pembuatan Lead Baru Tanpa Nama Brand (`POST`)
```bash
curl -i --url 'http://localhost:3000/api/tenant/crm/leads' \
  -H 'Authorization: Bearer <TOKEN>' \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Slug: wemade-demo' \
  --data-raw '{"brandName":"","contactPerson":"test","whatsappNumber":"62822444418322","email":"","stage":"NEW_LEAD","source":"","estimatedPcs":null,"estimatedValueIdr":null,"ownerEmployeeId":null,"expectedCloseDate":null,"customAttributes":{}}'
```
**Hasil yang Diharapkan**:
- Status: `HTTP/1.1 201 Created`
- Body memuat objek JSON lengkap dengan ID lead yang baru di-generate dan `brandName: ""`.

### 2. Uji Pengambilan Daftar Lead (`GET`)
```bash
curl -s --url 'http://localhost:3000/api/tenant/crm/leads' \
  -H 'Authorization: Bearer <TOKEN>' \
  -H 'X-Tenant-Slug: wemade-demo'
```
**Hasil yang Diharapkan**:
- Status: `HTTP/1.1 200 OK`
- Mengembalikan array JSON daftar lead tanpa error `ListLeadsUseCase`.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Amati bytecode hasil kompilasi Kotlin untuk `@JvmInline value class BrandName`. Buka terminal dan jalankan `javap -c` pada class file yang dihasilkan di `core/build/classes/kotlin/jvm/main/com/eventverse/app/domain/crm/BrandName.class` untuk melihat method mangling yang digenerate oleh Kotlin.
- [ ] **Tantangan 2**: Buat skrip healthcheck sederhana pada dev tooling untuk mendeteksi apakah PID server backend lebih tua daripada file `.kt` yang baru saja dimodifikasi di direktori kerja.
