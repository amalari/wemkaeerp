# 🎓 Modul Pembelajaran: Penanganan Konflik Email pada Pola Soft-Delete & Modal Konfirmasi Profil

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Soft-Delete / Archive Pattern (Odoo Style), PostgreSQL Partial Unique Index, Optimistic UI Rollback, Modal Konfirmasi Compose Multiplatform  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform, Coroutines, SQL Constraints, dan Compose State Management  
> **Referensi Task**: Penanganan Duplikasi Email Karyawan & Modal Konfirmasi Status Arsip

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada aplikasi ERP konveksi (WeMade ERP), kita menerapkan pola **Soft-Delete (Archive)** seperti Odoo. Saat seorang karyawan (misalnya "Dimas Pratama" dengan email `dimas.sales@wemade.id`) dinonaktifkan atau keluar, datanya **tidak dihapus secara fisik** dari tabel PostgreSQL demi menjaga integritas data historis (riwayat pesanan, komisi penjualan, penggajian). Baris data tersebut hanya diberi cap `archived_at = '2026-09-09...'`.

Namun, muncul masalah besar ketika:
1. HR / Admin mencoba menambahkan kembali karyawan baru dengan email `dimas.sales@wemade.id`.
2. PostgreSQL melempar error mentah:
   ```text
   PSQLException: ERROR: duplicate key value violates unique constraint "uq_tenant_employee_email"
   Detail: Key (tenant_id, email)=(ten-demo-001, dimas.sales@wemade.id) already exists.
   ```
3. Akibatnya, sistem mengalami *crash* atau *silent failure*, dan pengguna awam tidak mengerti apa yang terjadi karena tidak ada informasi ramah yang menjelaskan bahwa email tersebut milik karyawan yang sedang diarsipkan.

### Analogi Sederhana
Bayangkan sebuah **lemari arsip berkas kepegawaian fisik**:
- Seorang karyawan bernama Dimas pensiun, map berkasnya dipindahkan dari **Laci Aktif** ke **Kotak Arsip Gudang**.
- Saat HR ingin membuat berkas baru dengan NIK/Email Dimas, resepsionis tidak boleh hanya berteriak *"Error duplikat!"* lalu kabur.
- Yang benar: Resepsionis berkata: *"Pak/Bu, email ini tercatat atas nama Dimas Pratama di Divisi Sales, tetapi statusnya saat ini Diarsipkan di gudang. Apakah Bapak/Ibu mau kami ambilkan kembali berkas lama Dimas (Pulihkan/Restore), atau ingin menggunakan email baru?"*

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun alur penanganan konflik bisnis seperti ini dari layar kosong, berikut adalah urutan lapisan (*order of operations*) yang benar sesuai kaidah DDD:

```
[Pure Domain Layer]          Langkah 1: Buat Domain Exception (EmailConflictException)
       ↓
[Domain Use Cases]           Langkah 2: Validasi email di Create & Update Use Case
       ↓
[Database & Migration]       Langkah 3: Migrasi PostgreSQL (Partial Unique Index)
       ↓
[Server & REST Routes]       Langkah 4: Tangkap Exception & Respond HTTP 409 Conflict
       ↓
[Client API & State]         Langkah 5: Parsing 409 & Simpan ConflictInfo di UiState
       ↓
[ViewModel Orchestration]    Langkah 6: Optimistic Update Rollback & Trigger Modal
       ↓
[Shared Compose UI]          Langkah 7: Render EmailConflictDialog & Instant Restore Action
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pure Domain Exception (`core`)
File: `core/src/commonMain/kotlin/com/eventverse/app/domain/orgchart/EmailConflictException.kt`

```kotlin
class EmailConflictException(
    val email: String,
    val existingEmployeeId: String,
    val existingEmployeeName: String,
    val existingDepartmentName: String,
    val existingRoleTitle: String,
    val isArchived: Boolean,
    message: String = if (isArchived) {
        "Email '$email' sudah terdaftar pada karyawan $existingEmployeeName (Divisi $existingDepartmentName, Status: Diarsipkan). Silakan pulihkan karyawan tersebut atau gunakan email lain."
    } else {
        "Email '$email' sudah digunakan oleh karyawan $existingEmployeeName (Divisi $existingDepartmentName, Status: Aktif). Silakan gunakan email lain."
    }
) : IllegalArgumentException(message)
```

**Mengapa blok ini ditulis begini?**
- Exception ini adalah **Domain Exception murni** tanpa dependensi framework (Ktor/Android/Compose).
- Ia membawa seluruh konteks yang dibutuhkan UI untuk mengabarkan pengguna: nama pemilik email, divisinya, jabatannya, serta status apakah aktif atau diarsipkan.

---

### Blok B: Validasi di Domain Use Case (`CreateEmployeeUseCase.kt`)
File: `core/src/commonMain/kotlin/com/eventverse/app/domain/orgchart/usecases/CreateEmployeeUseCase.kt`

```kotlin
val targetEmail = command.email.trim().ifBlank { "${slug}@wemade.id" }
val existingWithEmail = employeeRepository.findByEmail(command.tenantId, targetEmail)
if (existingWithEmail != null) {
    throw EmailConflictException(
        email = targetEmail,
        existingEmployeeId = existingWithEmail.id.value,
        existingEmployeeName = existingWithEmail.name,
        existingDepartmentName = existingWithEmail.department.displayName,
        existingRoleTitle = existingWithEmail.roleTitle,
        isArchived = existingWithEmail.archivedAt != null
    )
}
```

**Mengapa blok ini ditulis begini?**
- Validasi dilakukan **sebelum objek entity baru disimpan**.
- Method `findByEmail` memeriksa tenant terkait. Karena record yang diarsipkan tetap tersimpan di database, `existingWithEmail.archivedAt != null` memberi tahu kita apakah karyawan tersebut berstatus aktif atau diarsipkan.

---

### Blok C: Database Migration — Partial Unique Index (`V6`)
File: `server/src/main/resources/db/migration/V6__soft_delete_partial_unique_indexes.sql`

```sql
-- Ganti unique constraint global tabel dengan partial unique index (hanya baris aktif)
ALTER TABLE employees DROP CONSTRAINT IF EXISTS uq_tenant_employee_email;
DROP INDEX IF EXISTS uq_tenant_employee_email;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_employee_email_active 
    ON employees(tenant_id, email) 
    WHERE archived_at IS NULL;
```

**Mengapa blok ini ditulis begini?**
- Constraint standar `UNIQUE (tenant_id, email)` memblokir seluruh baris tabel secara global.
- Dengan `WHERE archived_at IS NULL`, database mengizinkan baris yang sudah diarsipkan memiliki email yang sama, namun **tetap menjamin tidak ada dua karyawan aktif yang memiliki email kembar**.

---

### Blok D: HTTP 409 Conflict di Server Route (`EmployeeRoutes.kt`)
File: `server/src/main/kotlin/com/eventverse/app/routes/EmployeeRoutes.kt`

```kotlin
val ex = result.exceptionOrNull()
if (ex is EmailConflictException) {
    val dto = EmailConflictResponseDto(
        email = ex.email,
        existingEmployeeId = ex.existingEmployeeId,
        existingEmployeeName = ex.existingEmployeeName,
        existingDepartmentName = ex.existingDepartmentName,
        existingRoleTitle = ex.existingRoleTitle,
        isArchived = ex.isArchived,
        message = ex.message ?: ""
    )
    call.respondText(
        text = dto.toJson(),
        status = HttpStatusCode.Conflict,
        contentType = ContentType.Application.Json
    )
}
```

**Mengapa blok ini ditulis begini?**
- Menggunakan kode status HTTP standar: **`409 Conflict`** (bukan sekadar 400 atau 500).
- Payload JSON menyajikan metadata lengkap sehingga klien frontend tidak perlu melakukan kueri sekunder ke database.

---

### Blok E: Optimistic UI Rollback & Trigger Modal (`OrgChartViewModel.kt`)
File: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartViewModel.kt`

```kotlin
val previousEmployees = state.employees

// 1. Optimistic update ke layar agar responsif seketika
applyEmployeeSaveToState(newNode, isCreating, successionAction)

// 2. Kirim ke API backend
val result = client.createEmployee(...)
if (result.isFailure) {
    val ex = result.exceptionOrNull()
    
    // ROLLBACK: Kembalikan daftar karyawan ke kondisi sebelum klik Simpan!
    _uiState.update { current -> current.copy(employees = previousEmployees) }

    if (ex is EmailConflictException) {
        _uiState.update { current ->
            current.copy(
                emailConflictModal = EmailConflictInfo(
                    email = ex.email,
                    existingEmployeeId = ex.existingEmployeeId,
                    existingEmployeeName = ex.existingEmployeeName,
                    existingDepartmentName = ex.existingDepartmentName,
                    existingRoleTitle = ex.existingRoleTitle,
                    isArchived = ex.isArchived
                )
            )
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- **Jebakan Fatal Optimistic UI**: Banyak pemula lupa melakukan *rollback* saat API menolak data. Jika tidak di-rollback, karyawan hantu (*ghost employee*) akan tetap tampak di bagan UI padahal di database gagal tersimpan!

---

### Blok F: Modal Dialog Profil & Instant Restore (`OrgChartScreen.kt`)
File: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartScreen.kt`

```kotlin
@Composable
private fun EmailConflictDialog(
    conflict: EmailConflictInfo?,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (conflict == null) return
    val isArchived = conflict.isArchived

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isArchived) "📦 Email Terdaftar di Arsip" else "⚠️ Email Sudah Digunakan")
        },
        text = {
            // Menampilkan Kartu Profil Pemilik Email:
            // Nama, Badge Status (🟢 AKTIF / 📦 DIARSIPKAN), Divisi/Bagian, dan Jabatan
        },
        confirmButton = {
            if (isArchived) {
                Button(onClick = { onRestore(conflict.existingEmployeeId) }) {
                    Text("🔄 Pulihkan Karyawan Ini")
                }
            } else {
                Button(onClick = onDismiss) { Text("Ganti Email Lain") }
            }
        },
        dismissButton = {
            if (isArchived) {
                OutlinedButton(onClick = onDismiss) { Text("Gunakan Email Lain") }
            }
        }
    )
}
```

**Alur Pemulihan Instan:**
Saat user menekan tombol **"🔄 Pulihkan Karyawan Ini"**:
1. ViewModel memanggil endpoint `POST /api/tenant/employees/{id}/restore`.
2. Karyawan yang diarsipkan langsung diaktifkan kembali.
3. Form otomatis beralih memilih karyawan tersebut (`selectedEmployeeId`) sehingga HR bisa langsung melihat dan memperbarui profilnya tanpa harus membuka tab arsip secara terpisah.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif Lain | Mengapa Kita Memilih Pendekatan Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Domain Pre-Validation + 409 Conflict** | Membiarkan DB throw SQL Exception | Pengguna mendapatkan dialog profil manusiawi lengkap dengan tombol aksi | User melihat error seram `PSQLException unique constraint`, mengira aplikasi rusak |
| **PostgreSQL Partial Unique Index** (`WHERE archived_at IS NULL`) | Table-level `UNIQUE (tenant_id, email)` | Fleksibel: mengizinkan riwayat arsip tersimpan tanpa mengunci pembuatan entri baru di DB | Database melempar fatal constraint violation saat soft-delete diimplementasikan |
| **Optimistic State with Reversion** | Menunggu API (loading blocking) | Antarmuka terasa instan 60 FPS tanpa jeda jaringan, namun tetap aman karena di-rollback jika gagal | Antarmuka terasa lambat (*laggy*) atau sebaliknya menampilkan data palsu jika tidak di-rollback |
| **Instant Restore Action di Modal** | Menampilkan teks *"Pergi ke tab arsip"* | Menghemat waktu HR dari navigasi berputar-putar (High UX delight) | HR kebingungan mencari di mana menu arsip berada |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Asal Menghapus Constraint Unik Tanpa Partial Index**
   - *Kenapa bahaya*: Jika kamu hanya melakukan `DROP CONSTRAINT uq_tenant_employee_email` tanpa membuat partial index, dua karyawan aktif bisa memiliki email yang sama persis sehingga merusak sistem autentikasi login.
   - *Solusi*: Selalu pasang `CREATE UNIQUE INDEX ... WHERE archived_at IS NULL`.
2. **Jebakan 2: Optimistic Update "Lupa Ingatan"**
   - *Kenapa bahaya*: Developer melakukan `state.copy(employees = state.employees + newNode)` sebelum network request, tapi tidak menyimpan salinan state sebelumnya. Saat server merespons error 409, kartu karyawan tetap menempel di layar.
   - *Solusi*: Selalu simpan `val previousEmployees = state.employees` dan lakukan rollback pada blok `isFailure`.
3. **Jebakan 3: Melakukan `singleOrNull()` pada Kueri Tanpa Filter Arsip**
   - *Kenapa bahaya*: Jika di database ada 1 record aktif dan 1 record arsip dengan email yang sama, method `singleOrNull()` di Exposed akan melempar exception `More than one row found`.
   - *Solusi*: Kueri pencarian aktif wajib menyertakan `EmployeesTable.archivedAt.isNull()`.
4. **Jebakan 4: Kebocoran Status `isCreatingNew = false` Saat Optimistic Failure (Form Create Berubah Menjadi PUT)**
   - *Kenapa bahaya*: Saat klik Simpan Karyawan Baru, fungsi optimistic update terlanjur mengubah `isCreatingNew = false` dan `selectedEmployeeId = emp-random-123`. Jika server melempar 409 Conflict dan rollback hanya mengembalikan `employees = previousEmployees` tanpa mengembalikan `isCreatingNew = true`, form secara gaib berubah menjadi mode *Edit*. Ketika pengguna mengganti email dan mengklik Simpan kembali, frontend mengirim `PUT /api/tenant/employees/emp-random-123` yang berujung HTTP 400 karena ID tersebut belum pernah terdaftar di database!
   - *Solusi*: Jangan pernah mematikan `isCreatingNew = false` sebelum server mengonfirmasi `result.isSuccess`, dan selalu pulihkan snapshot `previousIsCreatingNew` serta `previousSelectedEmployeeId` saat terjadi kegagalan.


---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### A. Core Unit Tests (`EmployeeUseCaseTest.kt`)
Memverifikasi use case melempar `EmailConflictException` dengan payload yang benar:
```kotlin
@Test
fun createEmployee_withDuplicateEmailOfArchivedEmployee_shouldThrowEmailConflictExceptionWithArchivedTrue() = runBlocking {
    restoreDefaultEmployeesUseCase(tenantId)
    val emp = empRepo.findAllByTenant(tenantId).find { it.name.contains("Maya") }!!
    archiveEmployeeUseCase(tenantId, emp.id)

    val result = createEmployeeUseCase(
        CreateEmployeeCommand(
            tenantId = tenantId,
            name = "Maya Baru",
            email = emp.email,
            departmentId = emp.department.id,
            level = HierarchyLevel.STAFF_OPERATOR
        )
    )

    assertTrue(result.isFailure)
    val ex = result.exceptionOrNull()
    assertIs<EmailConflictException>(ex)
    assertEquals(emp.email, ex.email)
    assertEquals(emp.name, ex.existingEmployeeName)
    assertTrue(ex.isArchived)
}
```

### B. Server Integration Tests (`EmployeeApiTest.kt`)
Memverifikasi endpoint mengembalikan HTTP 409:
```kotlin
@Test
fun createEmployee_withDuplicateEmail_shouldReturn409ConflictWithDetails() = testApplication {
    // ... setup test repo ...
    val res = client.post("/api/tenant/employees") {
        header("X-Tenant-Slug", tenantSlug)
        contentType(ContentType.Application.Json)
        setBody("{\"name\":\"Budi Duplikat\",\"email\":\"budi.santoso@wemade.id\",\"departmentId\":\"dept-sales\",\"level\":\"STAFF_OPERATOR\"}")
    }

    assertEquals(HttpStatusCode.Conflict, res.status)
    val body = res.bodyAsText()
    assertTrue(body.contains("EMAIL_CONFLICT"))
    assertTrue(body.contains("\"isArchived\":false"))
}
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Terapkan pola yang sama persis untuk penanganan kode divisi duplikat pada `CreateDepartmentUseCase` (jika kode divisi misalnya `PROD` sudah pernah diarsipkan).
- [ ] **Tantangan 2**: Buat efek animasi transisi halus (*slide-in* atau *shake*) pada kotak dialog modal saat pengguna mencoba mengetikkan email yang terdeteksi konflik.
