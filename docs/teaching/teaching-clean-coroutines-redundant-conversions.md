# 🎓 Modul Pembelajaran: Pembersihan Redundant Type Conversion & Asynchronous Coroutine Test di Ktor

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Kotlin Type System, JetBrains Exposed Mapping, Ktor `testApplication`, Coroutine Dispatchers & Suspend Scope  
> **Prasyarat**: Pemahaman dasar tentang Kotlin Coroutines (`suspend`, `runBlocking`), Ktor Server Testing, dan JetBrains Exposed ORM  
> **Referensi Masalah**: `current_problems` (Redundant conversion call di `PostgresDepartmentRepository` & `PostgresEmployeeRepository`, `runBlocking` anti-pattern di `EmployeeApiTest` & `RbacApiTest`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Dalam ekosistem Kotlin backend modern (khususnya kombinasi **Ktor + Coroutines + JetBrains Exposed**), sering muncul kebiasaan kecil yang tampak sepele namun sebenarnya merupakan kode berlebih (*code smell*) atau bahkan anti-pattern performa:

1. **Redundant Conversion (`?.toString()`) pada String Column**:
   - Kolom database yang didefinisikan sebagai `varchar(..., ...).nullable()` di Exposed secara default telah dipetakan menjadi tipe data `String?`.
   - Menambahkan `?.toString()` pada ekspresi yang sudah bertipe `String?` adalah redundan, membingungkan pembaca kode, dan menambah instruksi opcode yang tidak berguna.
2. **`runBlocking` di dalam `testApplication`**:
   - Ktor `testApplication { ... }` mendesain lambda eksekusinya sebagai receiver `ApplicationTestBuilder.() -> Unit` yang merupakan **`suspend` function**.
   - Ketika seorang developer memanggil `runBlocking { ... }` di dalam fungsi `suspend`, eksekusi thread yang sedang berjalan akan **diblokir (blocked)** secara sinkron, menghilangkan seluruh manfaat non-blocking I/O coroutines dan memicu peringatan linter: *"Using 'runBlocking' inside a suspend function blocks the calling thread and defeats the purpose of asynchronous programming"*.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penanganan Masalah (Order of Operations)

Jika kamu menemukan serangkaian peringatan linter sejenis di proyekmu:

1. **Langkah 1: Periksa Definisi Skema Tabel (Exposed Table Definition)**
   - Buka file tabel yang bersangkutan (`DepartmentsTable.kt`, `EmployeesTable.kt`).
   - Cek tipe kolom: `val archivedAt = varchar("archived_at", 50).nullable()`.
   - Di Exposed, `row[Table.column]` untuk `varchar().nullable()` langsung menghasilkan `String?`. Jadi tidak perlu lagi dipanggil `?.toString()`.
2. **Langkah 2: Perbaiki Mapper Repository ke Domain Entity**
   - Hapus pemanggilan `?.toString()` pada assignment entity field `archivedAt`.
3. **Langkah 3: Audit Lingkungan Test (`testApplication`)**
   - Periksa signature fungsi pembungkus pengujian. Di Ktor:
     ```kotlin
     fun testApplication(block: suspend ApplicationTestBuilder.() -> Unit)
     ```
   - Lambda `block` sudah berada di dalam coroutine scope yang `suspendable`.
4. **Langkah 4: Eliminasi `runBlocking` di dalam Suspend Lambdas**
   - Hapus blok `runBlocking { ... }` dan langsung panggil fungsi `suspend` (seperti `repo.restoreDefaultPresets()`, `repo.findAllByTenant()`) secara langsung (*direct invocation*).
5. **Langkah 5: Ganti Operator `!!` dengan `requireNotNull`**
   - Hindari Kotlin double-bang `!!` yang dapat memicu NPE liar tanpa pesan konteks. Gunakan `requireNotNull(value) { "Pesan error jelas" }`.
6. **Langkah 6: Validasi Kompilasi dan Eksekusi Test**
   - Jalankan `./gradlew :server:test` untuk memverifikasi bahwa pengujian lulus dan semua warning IDE terselesaikan.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Repository Entity Mapping Tanpa Redundansi
File: `server/src/main/kotlin/com/eventverse/app/infrastructure/PostgresDepartmentRepository.kt` & `PostgresEmployeeRepository.kt`

```kotlin
// SEBELUM (Redundant Conversion):
private fun toDepartment(row: ResultRow): Department = Department(
    id = DepartmentId(row[DepartmentsTable.id]),
    code = row[DepartmentsTable.code],
    displayName = row[DepartmentsTable.displayName],
    shortName = row[DepartmentsTable.shortName],
    colorHex = row[DepartmentsTable.colorHex],
    isCustom = row[DepartmentsTable.isCustom],
    tenantId = TenantId(row[DepartmentsTable.tenantId]),
    archivedAt = row[DepartmentsTable.archivedAt]?.toString() // ⚠️ Redundant!
)

// SESUDAH (Bersih & Idiomatik):
private fun toDepartment(row: ResultRow): Department = Department(
    id = DepartmentId(row[DepartmentsTable.id]),
    code = row[DepartmentsTable.code],
    displayName = row[DepartmentsTable.displayName],
    shortName = row[DepartmentsTable.shortName],
    colorHex = row[DepartmentsTable.colorHex],
    isCustom = row[DepartmentsTable.isCustom],
    tenantId = TenantId(row[DepartmentsTable.tenantId]),
    archivedAt = row[DepartmentsTable.archivedAt] // ✅ Tipe row[...] sudah String?
)
```

**Mengapa perubahan ini penting?**
- `row[DepartmentsTable.archivedAt]` mengembalikan nilai bernilai `String?`.
- Memanggil `?.toString()` pada objek bertipe `String` hanyalah operasi no-op (`return this`), yang memicu peringatan linter `Redundant call of conversion method`.

---

### Blok B: Asynchronous Test di Ktor Tanpa `runBlocking`
File: `server/src/test/kotlin/com/eventverse/app/EmployeeApiTest.kt` & `RbacApiTest.kt`

```kotlin
// SEBELUM (Anti-Pattern Blocking Thread di dalam Suspend Scope):
@Test
fun createEmployee_andGetTShapeHierarchy_shouldSucceed() = testApplication {
    val tenantRepo = setupTestTenantRepo()
    val deptRepo = InMemoryDepartmentRepository()
    val empRepo = InMemoryEmployeeRepository()

    // ⚠️ runBlocking memblokir thread yang sedang menjalankan coroutine testApplication!
    runBlocking {
        deptRepo.restoreDefaultPresets(tenantId)
        val depts = deptRepo.findAllByTenant(tenantId)
        empRepo.restoreDefaultPresets(tenantId, depts)
    }

    application { ... }
    ...
}

// SESUDAH (Fully Non-Blocking & Asynchronous):
@Test
fun createEmployee_andGetTShapeHierarchy_shouldSucceed() = testApplication {
    val tenantRepo = setupTestTenantRepo()
    val deptRepo = InMemoryDepartmentRepository()
    val empRepo = InMemoryEmployeeRepository()

    // ✅ Dipanggil langsung tanpa membungkus runBlocking!
    deptRepo.restoreDefaultPresets(tenantId)
    val depts = deptRepo.findAllByTenant(tenantId)
    empRepo.restoreDefaultPresets(tenantId, depts)

    application { ... }
    ...
}
```

**Mengapa blok ini ditulis begini?**
- `testApplication` Ktor sudah menyediakan coroutine dispatcher internal untuk menguji client HTTP dan server pipeline secara terintegrasi.
- Fungsi-fungsi repository seperti `restoreDefaultPresets` dan `findAllByTenant` adalah fungsi `suspend`.
- Karena lambda `testApplication` adalah `suspend`, kita cukup melakukan suspensi wajar (suspension point) alih-alih menahan thread secara paksa dengan `runBlocking`.

---

### Blok C: Menghindari Operator Double-Bang (`!!`)
File: `server/src/test/kotlin/com/eventverse/app/EmployeeApiTest.kt`

```kotlin
// SEBELUM:
val activeBudi = runBlocking { empRepo.findAllByTenant(tenantId).find { it.name.contains("Budi") }!! }

// SESUDAH:
val activeBudi = requireNotNull(empRepo.findAllByTenant(tenantId).find { it.name.contains("Budi") }) { 
    "Active Budi not found" 
}
```

**Mengapa perubahan ini penting?**
- Aturan arsitektur tim (AGENTS.md Rule 7): *"Tidak ada `!!` operator — gunakan safe call + Elvis atau requireNotNull dengan pesan jelas"*.
- Jika suatu hari seeder data berubah dan "Budi" tidak ditemukan, test tidak akan melempar `KotlinNullPointerException` yang membingungkan tanpa pesan, melainkan langsung memberikan `IllegalArgumentException("Active Budi not found")` yang jelas letak kesalahannya.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Kita Pilih | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Direct Suspend Invocation** | Membungkus dengan `runBlocking` | Mempertahankan sifat non-blocking coroutines secara murni | Memblokir thread worker, memperlambat eksekusi test suite saat dijalankan secara paralel, dan berpotensi memicu thread starvation / deadlock |
| **`requireNotNull` dengan pesan** | Operator `!!` (double-bang) | Pesan kegagalan test deskriptif dan menaati rule tim | Debugging sulit karena log hanya mencetak `NullPointerException` tanpa informasi context apa yang hilang |
| **Strict Type Reference (`row[Col]`)** | Menambahkan `?.toString()` defensif | Kode bersih, idiomatik Kotlin, tanpa operasi redundan | Menimbulkan noise warning pada linter CI/CD dan mengurangi keterbacaan kode |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Asal Pasang `runBlocking` Saat Memanggil `suspend fun`**
   - *Kenapa bahaya*: Developer pemula sering mengira setiap kali ingin memanggil fungsi `suspend`, mereka wajib membungkusnya dengan `runBlocking`. Padahal `runBlocking` hanya jembatan antara dunia synchronous (seperti `fun main()`) ke dunia coroutines.
   - *Solusi elegan*: Jika kamu sudah berada di dalam fungsi atau lambda bertipe `suspend` (seperti Ktor routes, Compose LaunchedEffect, atau `testApplication`), panggil langsung tanpa `runBlocking`.

2. **Jebakan 2: Terlalu Defensif Memanggil `?.toString()`**
   - *Kenapa bahaya*: Mengira database driver mengembalikan objek generik seperti `Any?` sehingga dipaksa `?.toString()`.
   - *Solusi elegan*: Percayakan pada type-safety DSL JetBrains Exposed. Jika kolom dideklarasikan `varchar()`, tipe ekspresi kembaliannya dijamin `String` (atau `String?` jika `.nullable()`).

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Uji Kompilasi Bersih**:
   ```bash
   ./gradlew :server:compileKotlin :server:compileTestKotlin
   ```
   Pastikan tidak ada warning redundant conversion method ataupun coroutine misuse.

2. **Jalankan Test Suite Lengkap**:
   ```bash
   ./gradlew :server:test --rerun-tasks
   ```
   Seluruh test case (`EmployeeApiTest`, `RbacApiTest`, dll.) harus berstatus `SUCCESSFUL`.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Periksa file test lain di modul `server` (misalnya `GoogleAuthIntegrationTest` atau `RbacApiTest`), pastikan tidak ada lagi fungsi `suspend` yang dibungkus `runBlocking` di dalam blok `testApplication`.
- [ ] **Tantangan 2**: Pelajari perbedaan antara `testApplication` (Ktor Server Testing) vs `runTest` (Kotlinx Coroutines Test Framework). Kapan kita harus memakai `runTest` untuk unit test use case murni?
