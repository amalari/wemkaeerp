# Teaching — Alur Proses Sampling Dinamis Per-Desain & Default Flow Pabrik

> Task: "flow ini harusnya per design karena bisa beda beda, tapi ada default flow nya gitu"
> Mengimplementasikan arsitektur Prototype Pattern pada flow sampling: Pabrik memiliki alur proses default (template), namun setiap desain/artikel (SPK) dapat meng-override dan menyesuaikan urutan prosesnya sendiri tanpa merusak alur default.

---

## 1. Mulai dari Mana? (Urutan Menulis dari Nol)

Sebagai Senior Lead Developer, urutan eksekusi **Full-Stack End-to-End** wajib mengikuti Dependency Rule:

```
[1. Database & Flyway] 
       ↓
[2. Pure Domain Layer (core)]
       ↓
[3. Persistence & API (server)]
       ↓
[4. Client API & State Holder (app/shared)]
       ↓
[5. Claymorphism Presentation (app/shared/presentation)]
```

1. **Database Layer (`server/resources/db/migration/`)**:
   - Tentukan skema persistensi: Tambahkan kolom `custom_flow_processes JSONB DEFAULT NULL` dan `is_custom_flow BOOLEAN NOT NULL DEFAULT FALSE` ke tabel `sampling_orders` (Flyway `V57__sampling_order_custom_process_flow.sql`).
2. **Pure Domain Layer (`core/domain/sampling/`)**:
   - Tambahkan properti `customFlowProcesses: List<TenantOptionalProcess>?` dan `isCustomFlow: Boolean` ke entity agregat `SamplingOrder`.
   - Tambahkan domain mutator murni yang immutable: `customizeProcessFlow(processes)` dan `resetProcessFlowToDefault()`.
   - *Refactoring Wajib*: Pisahkan validasi ke `SamplingOrderValidation.kt` agar file `SamplingOrder.kt` tidak melewati Hard Limit 400 baris.
3. **Domain Codec (`core/shared/process/` & `sampling/`)**:
   - Perluas `ProcessCatalogCodec` dengan fungsi serialisasi `encodeProcesses()` dan `decodeProcesses()` yang dipakai bersama oleh server dan client multiplatform.
   - Perbarui `SamplingOrderCodec` agar field alur kustom ikut tersimpan dan terkirim di payload JSON.
4. **Backend API & Routing (`server/routes/SamplingRoutes.kt`)**:
   - Implementasikan 3 endpoint RESTful yang aman multi-tenancy:
     - `GET /api/tenant/sampling/orders/{id}/flow` — menghasilkan alur efektif (alur kustom jika ada, atau fallback ke template default katalog tenant).
     - `PUT /api/tenant/sampling/orders/{id}/flow` — menyimpan alur khusus desain (`is_custom_flow = true`).
     - `DELETE /api/tenant/sampling/orders/{id}/flow` — mereset alur desain kembali mengikuti default pabrik (`is_custom_flow = false`).
5. **Client-Server Integration (`app/shared/infrastructure/api/`)**:
   - Lengkapi `ProcessCatalogApiClient` dengan metode remote: `fetchOrderFlow()`, `saveOrderFlow()`, dan `resetOrderFlow()`.
6. **State Holder & UI Presentation (`app/shared/presentation/sampling/`)**:
   - Rancang `ProcessFlowViewModel` dengan model `ProcessFlowScope` (`DefaultTenant` vs `Design`).
   - Tambahkan tahap eksplisit `SamplingPipelineStage.FLOW_REVIEW` dan kolom Kanban **"2. Penentuan Alur"** di antara SPK Masuk dan Program CAM.
   - Pada kartu SPK Masuk, sediakan tombol `"Tentukan Alur Desain ->"`, dan pada kartu Penentuan Alur tombol `"Alur Siap -> Mulai CAM ->"`.
   - Sematkan `ProcessFlowAdjusterPanel` langsung di dalam `SamplingSpkDetailDialog` untuk tiap SPK.
   - Sediakan tombol `Template Flow Pabrik` di toolbar atas untuk mengatur alur standar bawaan.
   - Pastikan klik pada kartu SPK mana pun di papan Kanban membuka detail SPK beserta alur prosesnya.

---

## 2. Bedah Kode Blok per Blok

### Mental Model: Prototype Pattern (Template vs Instance)

```
[Template Default Tenant] ────── (Clone saat buat SPK) ──────> [Alur SPK Desain A]
(Bordir setelah Linking)                                       (Bordir setelah Linking)
                                                                           │
                                                    User sesuaikan         │ (+ Sablon)
                                                                           ▼
                                                               [Alur Kustom Desain A]
                                                               (Bordir & Sablon)
                                                               *Template tenant tidak berubah*
```

Setiap pakaian rajut memiliki keunikan: Cardigan polos tidak membutuhkan sablon atau bordir, sedangkan sweater motif memerlukan bordir di dada dan laundry khusus. Bila alur hanya dibuat global per tenant, fleksibilitas ini hilang. Bila dibuat murni per desain tanpa default, operator harus menyusun alur dari nol setiap kali membuat SPK baru.

### A. Flyway Migration `V57` & PostgreSQL JSONB
```sql
ALTER TABLE sampling_orders
    ADD COLUMN IF NOT EXISTS custom_flow_processes JSONB DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS is_custom_flow BOOLEAN NOT NULL DEFAULT FALSE;
```
*Mengapa JSONB?* Urutan proses opsional pada sebuah desain adalah **snapshot dokumen terstruktur** yang melekat pada siklus hidup SPK tersebut. JSONB memungkinkan struktur fleksibel tanpa perlu membuat join tabel anak yang berlebihan untuk urutan proses yang sering dibaca bersamaan dengan SPK.

### B. Pure Domain Entity (`SamplingOrder.kt`)
```kotlin
data class SamplingOrder(
    // ...
    val customFlowProcesses: List<TenantOptionalProcess>? = null,
    val isCustomFlow: Boolean = false,
) {
    fun customizeProcessFlow(processes: List<TenantOptionalProcess>): SamplingOrder =
        copy(customFlowProcesses = processes, isCustomFlow = true)

    fun resetProcessFlowToDefault(): SamplingOrder =
        copy(customFlowProcesses = null, isCustomFlow = false)
}
```
Aturan DDD: **Entity dilarang memiliki mutable state (`var`)**. Perubahan dilakukan melalui fungsi domain yang menghasilkan salinan baru (`copy()`), menjaga auditabilitas dan mempermudah pengujian.

### C. Backend Endpoint (`SamplingRoutes.kt`)
```kotlin
// GET Effective Flow: Desain kustom atau fallback ke template default
val effectiveProcesses = if (order.isCustomFlow && order.customFlowProcesses != null) {
    order.customFlowProcesses
} else {
    processCatalogRepository?.getCatalog(tenantId)?.processes ?: emptyList()
}
call.respond(HttpStatusCode.OK, jsonObjectOf(
    "orderId" to jsonOf(order.id.value),
    "isCustomFlow" to jsonOf(order.isCustomFlow),
    "processes" to ProcessCatalogCodec.encodeProcesses(effectiveProcesses)
).encode())
```
Logika *fallback* berada di layer aplikasi backend, sehingga klien selalu menerima alur yang siap pakai tanpa perlu melakukan kalkulasi fallback ganda.

### D. Scope Management di Presentation (`ProcessFlowViewModel.kt`)
```kotlin
sealed interface ProcessFlowScope {
    data object DefaultTenant : ProcessFlowScope
    data class Design(val orderId: String, val styleName: String, val spkNumber: String) : ProcessFlowScope
}
```
Satu komponen UI `ProcessFlowAdjusterPanel` dapat mengedit dua konteks berbeda secara elegan melalui state `scope`. Operator dapat berpindah antara mengedit alur default pabrik atau alur khusus desain yang sedang dipilih.

---

## 3. Technology & Approach ("The Why")

### Mengapa Memisahkan `SamplingOrderValidation.kt`?
Berdasarkan Rule §14 (Batas Ukuran File), layer domain murni (`core/**`) memiliki hard limit **400 baris**. File `SamplingOrder.kt` sebelumnya mencapai 434 baris. Dengan mengekstrak logika validasi kelengkapan SPK (`missingSpkRequirements`, `spkValidationWarnings`) ke file tersendiri, ukuran `SamplingOrder.kt` berhasil dipangkas menjadi 389 baris, mematuhi prinsip Single Responsibility dan Ratchet Rule.

### Mengapa Menghindari Emoji di String UI?
Di lingkungan Compose Multiplatform Wasm (WebAssembly), browser tidak memiliki font emoji bawaan OS. Menulis literal emoji (`🎨`, `✂️`, `🔽`) akan menyebabkan rendering kotak kosong / **tofu (`▯`)**. Seluruh visualisasi ikon menggunakan vektor kanvas terstandarisasi di `ClayIcons.kt` (`IconChevronDown`, dll.).

---

## 4. Jebakan Pemula (Common Pitfalls)

1. **Mutasi Data Global Secara Tidak Sengaja**:
   *Kesalahan*: Mengubah urutan flow pada desain A tapi memanggil API pembaruan katalog tenant (`/api/tenant/process-catalog`). Akibatnya, seluruh desain lain ikut terpengaruh.
   *Pencegahan*: Bedakan dengan tegas antara rute katalog tenant dan rute flow SPK (`/api/tenant/sampling/orders/{id}/flow`).
2. **Kompilasi HTTP Client Receiver Mismatch**:
   *Kesalahan*: Menggunakan `httpClient.put(...)` tanpa mengimpor `io.ktor.client.request.put`. Ktor client menyediakan ekstensi HTTP method per package; ketiadaan import menyebabkan lambda kehilangan receiver `HttpRequestBuilder` sehingga `setBody()` dan `contentType()` ikut unresolvable.
3. **Import Top-Level Extension Function**:
   *Kesalahan*: Saat memecah fungsi validasi dari entity ke file lain sebagai top-level extension functions, lupa menambahkan import eksplisit di file pemanggil (`ConfirmSpkDialog.kt`).

---

## 5. Verifikasi & Tantangan Mandiri

### Cara Menguji Kebenaran:
1. Jalankan unit test domain:
   ```bash
   ./gradlew :core:jvmTest --rerun
   ```
2. Jalankan kompilasi multiplatform dan backend server:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm :server:compileKotlin
   ```
3. Uji interaksi di UI:
   - Pilih dropdown scope: "Template Default Pabrik". Sisipkan proses Bordir.
   - Buat SPK baru atau pilih SPK yang ada: Perhatikan bahwa alur mengikuti template default (Badge hijau "Mengikuti Alur Default").
   - Sisipkan proses Sablon pada SPK tersebut: Badge otomatis berubah menjadi oranye ("Alur Kustom Desain") dan tombol "Reset ke Default" muncul.
   - Klik "Reset ke Default": Alur kembali sinkron dengan alur template pabrik.

### Tantangan Mandiri untuk Junior Developer:
- Buat sebuah unit test di `core/src/commonTest/...` yang mensimulasikan skenario: sebuah `SamplingOrder` yang memiliki alur kustom di-reset menggunakan `resetProcessFlowToDefault()`, lalu verifikasi bahwa `isCustomFlow` bernilai `false` dan `customFlowProcesses` bernilai `null`.
