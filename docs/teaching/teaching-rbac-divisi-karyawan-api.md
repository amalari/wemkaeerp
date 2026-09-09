# 🎓 Modul Pembelajaran: Arsitektur API Multi-Tenant RBAC, Divisi, dan Karyawan (DDD + Ktor + PostgreSQL RLS)

> **Level Target**: Junior to Mid-Level Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Multi-Tenant SaaS, PostgreSQL Row-Level Security (RLS), Ktor Routing, T-Shape Hierarchy Resolution, Single Active Head Succession  
> **Prasyarat**: Dasar Kotlin Multiplatform, pemahaman interface & value class, dasar SQL & ORM Exposed, serta konsep REST API di Ktor.  
> **Referensi Task**: Fitur API Backend RBAC, Divisi, dan Karyawan ([WeMade ERP Issue Tracker](https://github.com/amalari/wemade-erp))

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata: Mengapa ERP Konveksi Butuh Struktur Organisasi & RBAC Terintegrasi?
Bayangkan pabrik garmen konveksi dengan 50-100 orang karyawan. Masalah umum yang terjadi pada software konvensional:
1. **Data Leakage Lintas Pabrik (Tenant)**: Jika sistem SaaS gagal mengisolasi data, Pabrik A bisa melihat daftar karyawan, gaji/ongkos jahit, atau harga pokok produksi (HPP) milik Pabrik B.
2. **Kekacauan Approval Chain (Bagan Organisasi Kaku)**: Staf penjahit bingung harus meminta approval SPK atau sampling ke siapa. Jika atasan langsung berhenti atau dimutasi, struktur bawahan menjadi "anak yatim" (*orphaned nodes*).
3. **Konflik Dual-Head (Dua Kepala di Satu Divisi)**: Jika dua orang sama-sama tercatat sebagai Kepala Divisi Sales tanpa mekanisme suksesi, terjadi perang persetujuan (*conflicting approvals*).
4. **Hardcoded Roles di Kodingan**: Programmer junior sering mengunci role di kodingan: `if (role == "MANDOR")`. Begitu pemilik konveksi ingin jabatan baru bernama "Koordinator Mesin Obras", sistem harus di-deploy ulang.

### Analogi Sederhana: "Struktur Komando & Kartu Akses Pabrik"
- **Divisi (Department)**: Seperti ruangan kerja fisik di pabrik (Ruang Pola & Cutting, Lantai Mesin Jahit, Gudang Kain, QC Lab, Kantor Direksi). Masing-masing memiliki kode warna untuk identifikasi visual instan.
- **Jabatan (Custom Role)**: Seperti kartu akses RFID berlevel (*None, View, Operate, Manage*) yang menentukan pintu mana yang bisa dibuka oleh pemegang kartu.
- **Karyawan (OrgNode)**: Orang yang memegang kartu akses tersebut dan memiliki garis komando (*reports to*) kepada atasannya dalam format **T-Shape Hierarchy** (1 level ke atas, rekan sejajar di samping, dan seluruh bawahan di bawah).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun fitur API backend multi-entitas seperti ini dari nol, jangan pernah langsung membuat endpoint Ktor atau mengetik query SQL! Inilah urutan langkah seorang Senior Engineer:

```
[ Step 0: Pure Domain Model ] ➔ [ Step 1: Repository Interfaces ] ➔ [ Step 2: Use Cases (Domain Business Logic) ]
                                                                                   │
[ Step 5: Route & Serialization ] ⬅ [ Step 4: Exposed Mapping & RLS ] ⬅ [ Step 3: Flyway Migration (DDL) ]
         │
[ Step 6: Pure Unit & Integration Tests ]
```

1. **Langkah 0: Definisikan Entitas Domain di `core` (Zero External Dependencies)**
   - Pastikan entitas `Department` dan `OrgNode` mendukung multi-tenancy dengan properti opsional `tenantId: TenantId? = null`.
2. **Langkah 1: Buat Domain Repository Interfaces di `core`**
   - `RoleRepository`, `DepartmentRepository`, `EmployeeRepository` sebagai kontrak murni Kotlin.
3. **Langkah 2: Tulis Application Layer (Use Cases) Berorientasi Single Responsibility**
   - Satu use case = satu operasi bisnis (`CreateRoleUseCase`, `CreateEmployeeUseCase`, `GetTShapeHierarchyUseCase`).
   - Tanamkan aturan suksesi kepemimpinan (*Single Active Head Rule*) di layer ini!
4. **Langkah 3: Rancang Skema PostgreSQL & Enforce RLS (`V4__create_rbac_orgchart_schema.sql`)**
   - Terapkan `apply_tenant_rls(...)` pada tabel `custom_roles`, `departments`, dan `employees`.
5. **Langkah 4: Petakan Tabel ke JetBrains Exposed & Buat Postgres Implementation**
   - Buat `CustomRolesTable`, `DepartmentsTable`, `EmployeesTable`.
   - Implementasikan repository PostgreSQL yang membungkus query di dalam `DatabaseFactory.dbQuery(tenantId)`.
6. **Langkah 5: Rancang DTO & Routing Ktor (`/api/tenant/...`)**
   - Pisahkan representasi JSON dari domain entity menggunakan DTO.
   - Buat `rbacRoutes`, `departmentRoutes`, `employeeRoutes` dengan resolusi otomatis `call.tenantContext`.
7. **Langkah 6: Tulis Unit Test Domain & API Integration Test**
   - Unit test use case di `core/src/commonTest/` dengan fake repository.
   - Integration test API di `server/src/test/` menggunakan Ktor `testApplication`.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Aturan Suksesi Kepala Divisi di Domain Use Case (`CreateEmployeeUseCase.kt`)

```kotlin
// Single Active Head Rule and Succession Management
if (newNode.level == HierarchyLevel.HEAD_OF_DEPARTMENT) {
    val existingHead = allEmployees.find {
        it.department.id == department.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
    }

    if (existingHead != null) {
        when (command.successionAction) {
            HeadSuccessionAction.DEMOTE_TO_STAFF -> {
                val demotedHead = existingHead.copy(
                    level = HierarchyLevel.STAFF_OPERATOR,
                    roleTitle = "Staf Senior ${department.shortName}",
                    reportsToId = newNode.id
                )
                val staffToUpdate = allEmployees.filter {
                    it.reportsToId == existingHead.id && it.id != existingHead.id
                }.map { it.copy(reportsToId = newNode.id) }

                val toSave = listOf(demotedHead) + staffToUpdate
                employeeRepository.saveAll(command.tenantId, toSave).getOrThrow()
            }
            HeadSuccessionAction.DEACTIVATE -> {
                employeeRepository.delete(command.tenantId, existingHead.id).getOrThrow()
                val staffToUpdate = allEmployees.filter {
                    it.reportsToId == existingHead.id && it.id != existingHead.id
                }.map { it.copy(reportsToId = newNode.id) }

                if (staffToUpdate.isNotEmpty()) {
                    employeeRepository.saveAll(command.tenantId, staffToUpdate).getOrThrow()
                }
            }
        }
    }
}
```

**Mengapa ditulis seperti ini?**
- **Integritas Pohon Struktur Organisasi**: Dalam sebuah divisi, hanya boleh ada **satu Kepala Divisi aktif**. Jika admin menunjuk pejabat baru, pejabat lama tidak boleh dibiarkan gantung. 
- **Opsi Suksesi yang Fleksibel**: Pemilik pabrik dapat memilih apakah pejabat lama diturunkan menjadi Staf Senior (`DEMOTE_TO_STAFF`) atau dinonaktifkan (`DEACTIVATE`).
- **Auto-Reassign Bawahan**: Seluruh staf yang sebelumnya melapor ke pejabat lama otomatis dialihkan untuk melapor ke Kepala Divisi yang baru, menjamin *approval chain* tidak putus.

---

### Blok B: Pencegahan Orphaned Nodes Saat Karyawan Dihapus (`DeleteEmployeeUseCase.kt`)

```kotlin
val allEmployees = employeeRepository.findAllByTenant(tenantId)
val subordinates = allEmployees.filter { it.reportsToId == id }

// Reassign direct subordinates to parent superior to avoid orphaned branches
if (subordinates.isNotEmpty()) {
    val remapped = subordinates.map { it.copy(reportsToId = existing.reportsToId) }
    employeeRepository.saveAll(tenantId, remapped).getOrThrow()
}

employeeRepository.delete(tenantId, id).getOrThrow()
```

**Mengapa ditulis seperti ini?**
- **Masalah Tree Dangling Pointer**: Jika seorang manajer dihapus dari database begitu saja, kolom `reports_to_id` dari 10 anak buahnya akan bernilai ID orang yang sudah mati atau NULL. Bagan organisasi akan pecah.
- **Auto Grandparent Reassignment**: Potongan kode ini otomatis menaikkan garis komando bawahan langsung ke atasan sang manajer (kakek/atasan lebih tinggi), menjaga hierarki tetap utuh.

---

### Blok C: Database Row-Level Security (RLS) PostgreSQL (`V4 Migration`)

```sql
ALTER TABLE custom_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE departments ENABLE ROW LEVEL SECURITY;
ALTER TABLE employees ENABLE ROW LEVEL SECURITY;

SELECT apply_tenant_rls('custom_roles');
SELECT apply_tenant_rls('departments');
SELECT apply_tenant_rls('employees');
```

Dan pada level Exposed transaction (`DatabaseFactory.kt`):
```kotlin
suspend fun <T> dbQuery(
    tenantId: TenantId? = null,
    block: suspend () -> T
): T = newSuspendedTransaction(Dispatchers.IO) {
    if (tenantId != null) {
        exec("SET LOCAL app.current_tenant_id = '${tenantId.value}';")
    }
    block()
}
```

**Mengapa ditulis seperti ini?**
- **Pertahanan Berlapis (Defense in Depth)**: Walaupun developer backend membuat kesalahan lupa menulis `WHERE tenant_id = ...`, PostgreSQL engine di level kernel database akan memblokir baris data dari tenant lain secara otomatis berdasarkan session variable `app.current_tenant_id`.

---

### Blok D: Isolasi Tenant & Resolusi Otomatis di Ktor (`RbacRoutes.kt`)

```kotlin
route("/api/tenant/roles") {
    get {
        val tenant = call.tenantContextOrNull ?: run {
            call.respond(HttpStatusCode.NotFound, "No tenant context found")
            return@get
        }
        val result = getRolesUseCase.getAll(tenant.tenantId)
        ...
    }
}
```

**Mengapa ditulis seperti ini?**
- **Zero Query Param Manipulation**: ID tenant tidak dibaca dari query string yang bisa dipalsukan oleh pengguna (`?tenantId=123`), melainkan diekstraksi dari header kriptografis atau subdomain terverifikasi (`TenantResolutionPlugin`).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan Arsitektur | Alternatif Lain | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Use Case per Operasi Bisnis** | Fat Service / Fat Controller (misal satu class `OrgService` 1500 baris) | Mematuhi Single Responsibility Principle (SRP). Kode sangat mudah diuji per fungsi dan bebas *merge conflict* tim. | God Service membengkak ribuan baris, sulit di-maintain, dan rapuh saat banyak developer bekerja bersamaan. |
| **PostgreSQL RLS (Row-Level Security)** | Manual filtering `WHERE tenant_id = ?` di setiap query SQL | Jaminan isolasi data 100% di level database engine. | Satu saja developer magang lupa menulis `WHERE tenant_id`, seluruh data konveksi kompetitor langsung bocor. |
| **DTO Deserialization Terpisah** | Menempelkan `@Serializable` langsung di Domain Entity | Domain layer tetap 100% murni (*pure Kotlin*), tidak bocor dependensi framework serialization. | Jika schema JSON API berubah (misal ganti nama key camelCase ke snake_case), domain bisnis ikut terdistorsi. |
| **In-Memory Test Doubles** | Menggunakan Mockito / Mockk untuk mock repository | Sangat cepat (eksekusi ratusan test dalam hitungan milidetik), deterministic, dan tidak bergantung pada bytecode instrumentation. | Unit test lambat, mocking rentan *false positive* karena mock tidak merefleksikan penyimpanan state nyata. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

### 1. Jebakan: "Shadowed Variable saat Menulis Exposed Insert"
- **Kesalahan Fatal**:
  ```kotlin
  override suspend fun save(tenantId: TenantId, dept: Department) {
      DepartmentsTable.insert {
          it[tenantId] = tenantId.value // ❌ ERROR: Argument type mismatch
      }
  }
  ```
- **Penyebab**: Nama parameter fungsi `tenantId` bertabrakan dengan kolom `DepartmentsTable.tenantId`. Kotlin mengira kamu memasukkan objek `TenantId` ke dalam kurung siku `it[...]`.
- **Solusi Benar**: Disambiguasi eksplisit dengan nama tabel:
  ```kotlin
  it[DepartmentsTable.tenantId] = tenantId.value // ✅ BENAR
  ```

### 2. Jebakan: "Menghapus Divisi yang Masih Ada Karyawannya"
- **Kesalahan Fatal**: Menjalankan query `DELETE FROM departments WHERE id = 'dept-sales'` saat ada 10 karyawan sales aktif.
- **Penyebab**: Foreign key constraint error atau karyawan menjadi tanpa divisi.
- **Solusi Benar**: Di `DeleteDepartmentUseCase`, selalu verifikasi:
  ```kotlin
  val assigned = employeeRepository.findByDepartment(tenantId, id)
  require(assigned.isEmpty()) { "Tidak dapat menghapus divisi yang masih memiliki staf" }
  ```

### 3. Jebakan: "Mengekspos Internal ID Database Tanpa Validasi Hak Milik"
- **Kesalahan Fatal**: `GET /api/tenant/roles/role-owner` langsung mengambil baris database tanpa mencocokkan `tenant_id`.
- **Solusi Benar**: Selalu sertakan `tenantId` pada setiap signature repository: `findById(tenantId: TenantId, id: RoleId)`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian di WeMade ERP dibagi menjadi dua tahap yang dapat dijalankan secara otomatis:

### 1. Pure Unit Test di Domain Layer (`:core:jvmTest`)
Menguji logika suksesi, integritas hirarki T-Shape, dan aturan bisnis tanpa memerlukan database atau server jaringan:
```bash
./gradlew :core:jvmTest --rerun-tasks
```
*Hasil Verifikasi*: 
- `createEmployee_newHeadOfDept_shouldDemoteOldHeadAndReassignSubordinates` ✅ PASS
- `deleteEmployee_shouldReassignSubordinatesToGrandparentSuperior` ✅ PASS
- `deleteRole_customRoleWithAssignedUsers_shouldFail` ✅ PASS

### 2. HTTP Integration Test di Server Layer (`:server:test`)
Menguji pipeline routing Ktor, penolakan akses tanpa tenant header, dan isolasi data antar tenant:
```bash
./gradlew :server:test --rerun-tasks
```
*Hasil Verifikasi*:
- `RbacApiTest`: CRUD role kustom dan restore presets via HTTP JSON ✅ PASS
- `DepartmentApiTest`: Pembuatan divisi dan response status 201 Created ✅ PASS
- `EmployeeApiTest`: Query T-Shape hierarchy dan suksesi via HTTP ✅ PASS
- `TenantIsolationApiTest`: Data Tenant Alpha terbukti tidak terlihat oleh Tenant Beta ✅ PASS

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

Untuk memperkuat pemahamanmu, coba kerjakan latihan tantangan berikut:
- [ ] **Tantangan 1 (Audit Log)**: Tambahkan event domain `EmployeePromotedToHead(employeeId, departmentId, occurredAt)` saat suksesi kepala divisi terjadi, dan log aktivitas tersebut.
- [ ] **Tantangan 2 (Bulk Import Karyawan)**: Buat endpoint `POST /api/tenant/employees/bulk-import` yang menerima array JSON karyawan baru dan menyimpannya dalam satu batch transaction atomic.
- [ ] **Tantangan 3 (Department Member Cap)**: Tambahkan limitasi kuota di `CreateEmployeeUseCase`: jika paket langganan tenant adalah `STARTER`, maksimal hanya boleh ada 5 karyawan per divisi.
