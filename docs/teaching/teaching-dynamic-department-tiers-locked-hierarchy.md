# 🎓 Modul Pembelajaran: Dynamic Department Tiers & Locked Hierarchy State Flow

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Dynamic Hierarchies, Kotlin Multiplatform, Compose Multiplatform MVI Form Orchestration  
> **Prasyarat**: Dasar Kotlin, MVI State Management, dan Konsep Dasar DDD (Entity, Value Object, Domain Aggregates)  
> **Referensi Task**: Implementasi Tingkat Wewenang Dinamis per Divisi, Direksi Nullable Department, dan Hierarki Atasan Terkunci Otomatis

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Dalam sistem ERP garmen/konveksi riil (seperti WeMade ERP), struktur organisasi lapangan tidak pernah bersifat *one-size-fits-all*:
1. **Perbedaan Struktur per Departemen**:
   - Di divisi **Produksi & PPIC**, alur hierarkinya sangat berjenjang: `Kepala Divisi (Joko Susilo)` $\rightarrow$ `Kepala Tim / Mandor (Agus Setiawan)` $\rightarrow$ `Operator Jahit / Staf Lapangan (Bambang dkk)`.
   - Di divisi **Keuangan & Akuntansi** atau **Quality Control**, strukturnya jauh lebih ramping: `Kepala Divisi` langsung membawahi `Staf Auditor / Kasir`.
   - Jika sistem memaksakan bahwa semua divisi memiliki dropdown tingkat jabatan statis yang kaku, pengguna akan kesulitan memodelkan tim mandor, regu potong, atau kepala shift.
2. **Posisi Direksi (Executive Management)**:
   - Direktur Utama / CEO (`Hendra Kusuma`) membawahi seluruh perusahaan, bukan kepala satu departemen tertentu. Memaksakan Direksi masuk ke departemen "Executive" atau "Direksi" sebagai divisi tersendiri menciptakan departemen fiktif yang merusak pelaporan operasional, beban biaya per divisi, dan agregasi data per departemen.
3. **Integritas Relasi Atasan-Bawahan (Line of Reporting)**:
   - Ketika seorang operator jahit baru didaftarkan di bawah Mandor Jahit (yang bekerja di Divisi Produksi), secara logika operasional karyawan baru tersebut **pasti** berada di Divisi Produksi. Membiarkan user memilih sendiri divisi lain (misal memilih Atasan Mandor Produksi tetapi memilih Divisi Penjualan) adalah celah human error yang memicu data anomali (orphaned / cross-department corruption).

### Analogi Sederhana
Bayangkan sebuah rumah sakit:
- **Direktur Rumah Sakit** tidak bernaung di bawah "Poli Gigi" atau "Poli Mata"; beliau menaungi keseluruhan rumah sakit (`department = null`).
- Seorang **Perawat Bedah** yang bekerja di bawah **Kepala Perawat Bedah** secara otomatis terdaftar di instalasi bedah. Formulir pendaftaran tidak boleh membiarkan perawat tersebut memilih penempatan di instalasi farmasi sementara atasannya adalah kepala perawat bedah.
- Setiap instalasi memiliki jenjang sendiri: Poli Penyakit Dalam mungkin hanya butuh Dokter Spesialis dan Asisten, sedangkan Ruang Operasi memiliki Kepala Bedah, Dokter Bedah Utama, Anestesiolog, dan Instrumentator.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu harus mengoding fitur ini dari awal (layar kosong), jangan pernah langsung lompat ke komponen UI Jetpack Compose! Mulailah dari inti bisnis:

```
[Core Domain Layer]               [Application Layer]           [Infrastructure / DB]           [Presentation Layer]
  DepartmentTier (VO)      -->    CreateEmployeeUseCase  -->    Postgres Schema & Repo   -->    OrgChartUiState
  Department.tiers                UpdateEmployeeUseCase         Nullable department_id          OrgChartViewModel (Locking)
  OrgNode.department?                                                                           OrgChartScreen (Step 1-5 UI)
```

1. **Langkah 0: Value Object & Domain Model (`core/.../domain/orgchart/DepartmentTier.kt`)**
   - Definisikan apa itu "Tingkat Wewenang" dalam domain: identitas, nama label, peringkat hierarki (`rank`), dan apakah posisi tersebut merupakan pimpinan divisi (`isHead`).
2. **Langkah 1: Hubungkan ke Aggregate Divisi (`Department.kt`)**
   - Pasang list `tiers: List<DepartmentTier>` di dalam `Department`. Buat factory method untuk preset default (Divisi Produksi memiliki 3 jenjang: Kepala Divisi, Kepala Tim/Mandor, Staf Operator; divisi lain memiliki 2 jenjang standar). Sediakan fungsi mutasi immutability `addTier(name)`.
3. **Langkah 2: Perbaiki Node Karyawan (`OrgNode.kt`)**
   - Ubah `department: Department?` menjadi nullable. Seorang `HierarchyLevel.EXECUTIVE` memiliki `department = null`.
   - Tambahkan properti `tierName: String?` untuk mencatat label wewenang dinamis yang dipilih.
4. **Langkah 3: Aturan Bisnis di Use Case (`CreateEmployeeUseCase.kt` & `UpdateEmployeeUseCase.kt`)**
   - Validasi bahwa hanya `HierarchyLevel.EXECUTIVE` yang boleh memiliki `departmentId == null`. Karyawan level lain wajib memiliki divisi.
5. **Langkah 4: Sinkronisasi Persistensi & API DTO (`server/.../PostgresEmployeeRepository.kt` & `EmployeeDto.kt`)**
   - Sesuaikan skema tabel agar kolom `department_id` menerima nilai `NULL`. Pastikan parser DTO tidak crash saat menerima karyawan tanpa departemen.
6. **Langkah 5: State Machine & Auto-Locking Logic (`app/shared/.../OrgChartViewModel.kt`)**
   - Definisikan alur saat user memilih atasan (`SelectReportsTo`):
     - Jika atasan memiliki departemen $\rightarrow$ set `selectedDepartment = superior.department` dan `isDepartmentLocked = true`.
     - Jika atasan adalah Direksi (tanpa departemen) $\rightarrow$ buka kunci `isDepartmentLocked = false`, biarkan user memilih divisi.
     - Jika "Tanpa Atasan" $\rightarrow$ otomatis level `EXECUTIVE`, `selectedDepartment = null`, `isDepartmentLocked = true`.
7. **Langkah 6: Dekomposisi UI Step-by-Step (`OrgChartScreen.kt`)**
   - Susun form secara ergonomis:
     - **Langkah 1**: Data Diri (Nama, Email, WhatsApp).
     - **Langkah 2**: Atasan Langsung (Under Siapa).
     - **Langkah 3**: Divisi Penempatan (Status Terkunci vs Status Bebas Pilih).
     - **Langkah 4**: Tingkat Wewenang Dinamis (Daftar jenjang milik divisi terpilih + Tombol `+ Tambah Tingkat`).
     - **Langkah 5**: Nama Jabatan Operasional.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Value Object `DepartmentTier` & Presets di `Department`
File: [`DepartmentTier.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/orgchart/DepartmentTier.kt) & [`Department.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/orgchart/Department.kt)

```kotlin
@Serializable
data class DepartmentTier(
    val id: String,
    val name: String,
    val rank: Int,
    val isHead: Boolean = false,
)

// Di Department.kt:
data class Department(
    val id: String,
    val displayName: String,
    val tiers: List<DepartmentTier> = defaultTiers(displayName),
    // ...
) {
    fun addTier(name: String): Department {
        val trimmed = name.trim()
        if (tiers.any { it.name.equals(trimmed, ignoreCase = true) }) return this
        val nextRank = (tiers.maxOfOrNull { it.rank } ?: 1) + 1
        val newTier = DepartmentTier(
            id = "tier-${trimmed.lowercase().replace(" ", "-")}",
            name = trimmed,
            rank = nextRank,
            isHead = false,
        )
        return copy(tiers = tiers + newTier)
    }
}
```

**Mengapa blok ini ditulis begini?**
- **Immutability**: `addTier` tidak memodifikasi list yang ada secara in-place (`tiers.add(...)`), melainkan mengembalikan instance baru hasil `.copy(...)`. Hal ini mencegah *race condition* dan menjaga prediksi alur data di state management Compose.
- **Dynamic Rank Assignment**: Nilai `rank` dihitung secara otomatis dari `maxOfOrNull { it.rank } + 1`, memastikan urutan hierarki selalu konsisten.

---

### Blok B: Penanganan Direksi Tanpa Divisi di Core Node & Use Case
File: [`OrgNode.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/orgchart/OrgNode.kt) & [`CreateEmployeeUseCase.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/orgchart/usecases/CreateEmployeeUseCase.kt)

```kotlin
// OrgNode.kt
data class OrgNode(
    val id: String,
    val name: String,
    val role: String,
    val level: HierarchyLevel,
    val department: Department?, // Nullable untuk Direksi / Executive
    val reportsToId: String? = null,
    val tierName: String? = null,
    // ...
)

// CreateEmployeeUseCase.kt
suspend operator fun invoke(command: CreateEmployeeCommand): Result<OrgNode> = runCatching {
    // Direksi TIDAK wajib memiliki departemen
    val dept = if (command.level == HierarchyLevel.EXECUTIVE) {
        command.departmentId?.let { departmentRepository.findById(it) }
    } else {
        requireNotNull(command.departmentId) { "Department ID cannot be null for non-executive employee" }
        departmentRepository.findById(command.departmentId)
            ?: throw DepartmentNotFoundException(command.departmentId)
    }
    // ...
}
```

**Mengapa blok ini ditulis begini?**
- **Explicit Domain Constraint**: Menggunakan `requireNotNull` dengan pesan deskriptif. Jika ada programmer yang mencoba membuat staf biasa tanpa departemen, domain akan menolaknya di gerbang utama, bukan melempar NullPointerException acak di lapisan database.
- **Zero Framework Dependency**: Kode ini murni Kotlin standard library (`runCatching`, `requireNotNull`), tidak tergantung pada Ktor, Android, ataupun Compose.

---

### Blok C: State Machine Auto-Locking di ViewModel
File: [`OrgChartViewModel.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartViewModel.kt)

```kotlin
is OrgChartUiEvent.SelectReportsTo -> {
    val superior = state.employees.find { it.id == event.reportsToId }
    if (superior != null) {
        if (superior.department != null) {
            // Kasus 1: Atasan punya divisi -> Kunci divisi mengikuti atasan
            _uiState.update { current ->
                val deptInState = current.availableDepartments.find { it.id == superior.department.id } 
                    ?: superior.department
                current.copy(
                    selectedReportsToId = event.reportsToId,
                    selectedDepartment = deptInState,
                    isDepartmentLocked = true, // TERKUNCI!
                    selectedTierName = deptInState.tiers.firstOrNull { !it.isHead }?.name,
                )
            }
        } else {
            // Kasus 2: Atasan adalah Direksi (department == null) -> Buka kunci divisi
            _uiState.update { current ->
                current.copy(
                    selectedReportsToId = event.reportsToId,
                    isDepartmentLocked = false, // BEBAS PILIH!
                )
            }
        }
    } else {
        // Kasus 3: Tanpa atasan -> Direksi
        _uiState.update { current ->
            current.copy(
                selectedReportsToId = null,
                selectedDepartment = null,
                selectedLevel = HierarchyLevel.EXECUTIVE,
                isDepartmentLocked = true,
            )
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- **Single Source of Truth**: UI tidak perlu menghitung sendiri apakah tombol divisi harus disabled atau tidak. Cukup membaca flag boolean `state.isDepartmentLocked`.
- **Intelligent Defaulting**: Saat divisi terkunci ke departemen atasan, sistem secara otomatis memilihkan tier non-head pertama (`!it.isHead`) yang relevan untuk divisi tersebut.

---

### Blok D: Presentasi Visual Mengunci Divisi di Compose
File: [`OrgChartScreen.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartScreen.kt)

```kotlin
if (state.isDepartmentLocked && state.selectedDepartment != null) {
    // Banner info terkunci
    Surface(
        color = Color(0xFF0284C7).copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔒", fontSize = 14.sp)
            Text(
                text = "Divisi otomatis mengikuti atasan langsung (${state.selectedDepartment?.displayName ?: "Direksi"}).",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF0369A1),
                fontWeight = FontWeight.Medium,
            )
        }
    }
} else {
    // Horizontal scrollable chips jika user bebas memilih divisi
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        state.availableDepartments.forEach { dept ->
            FilterChip(
                selected = state.selectedDepartment?.id == dept.id,
                onClick = { onEvent(OrgChartUiEvent.SelectDepartment(dept)) },
                label = { Text(dept.displayName) }
            )
        }
    }
}
```

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Kita Ambil | Alternatif Konvensional | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Nullable Department (`Department?`) untuk Direksi** | Membuat departemen dummy `"EXECUTIVE"` atau `"DIREKSI"` di database. | Direksi adalah pemegang kendali lintas divisi. Menjadikannya nullable mencerminkan struktur korporasi riil dan tidak mengotori analitik per departemen. | Muncul departemen bayangan di laporan keuangan dan analitik beban gaji konveksi yang seharusnya dialokasikan ke General & Administrative overhead. |
| **Tiers Dinamis Melekat pada Objek Department** | Enum statis global untuk seluruh tingkatan karyawan (misal: `CEO, MANAGER, SUPERVISOR, STAFF`). | Tiap divisi di industri manufaktur punya kultur dan spesialisasi berbeda (contoh: Mandor & Kepala Regu di Jahit, Formulator di Sablon). | Enum statis tidak fleksibel; setiap kali pabrik menambah jabatan "Kepala Shift Malam", developer harus mengubah source code dan rebuild aplikasi. |
| **Atasan Menentukan Divisi (Locked State Machine)** | User bebas memilih dropdown Atasan dan dropdown Divisi secara terpisah tanpa validasi. | Mencegah inkonsistensi hirarki sejak dini (Shift Left error prevention). UX jauh lebih cepat karena user hanya perlu memilih 1 hal. | Potensi data korup: Operator Jahit memilih Atasan Kepala Gudang, merusak pohon bagan organisasi (T-Shape View & Hierarchy tree). |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Asumsi Bahwa Semua Karyawan Punya Departemen**
   - *Kenapa bahaya*: Developer junior sering menulis `node.department.displayName` tanpa operator `?.`. Begitu node Direktur Utama dimuat di layar, aplikasi akan langsung melempar `NullPointerException` (atau crash di runtime Wasm).
   - *Solusi elegan kita*: Selalu gunakan safe call `node.department?.displayName ?: "DIREKSI"`.
2. **Jebakan 2: Default Value Hardcoded di Form State**
   - *Kenapa bahaya*: Mengisi initial state form dengan data contoh (seperti `"Dimas Pratama"`, `"dimas.sales@wemade.id"`) yang lupa dihapus saat masuk production. Ketika user menekan "+ Tambah Karyawan", form sudah terisi data fiktif.
   - *Solusi elegan kita*: Pastikan event `OpenAddEmployeeModal` selalu mereset form ke nilai kosong (`nameInput = ""`, `emailInput = ""`, `whatsappInput = ""`, `editingEmployeeId = null`).
3. **Jebakan 3: Menggunakan Equality Check Kasar untuk Nama Tier Baru**
   - *Kenapa bahaya*: User memasukkan `"mandor"` sementara sudah ada `"Mandor "` (ada spasi di akhir dan huruf kecil). Sistem membuat dua tier duplikat.
   - *Solusi elegan kita*: Selalu gunakan `.trim()` dan `equalsIgnoreCase`:
     ```kotlin
     val trimmed = name.trim()
     if (tiers.any { it.name.equals(trimmed, ignoreCase = true) }) return this
     ```

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian dipecah sesuai arsitektur piramida pengujian:

1. **Unit Test Pure Domain (`core:jvmTest`)**:
   - Uji penambahan tier baru pada departemen (`addTier` menambahkan rank berikutnya).
   - Uji pembuatan Direksi dengan `departmentId = null` (harus berhasil).
   - Uji pembuatan Staf dengan `departmentId = null` (harus gagal dengan `IllegalArgumentException`).
   - Berkas: [`DepartmentTest.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonTest/kotlin/com/eventverse/app/domain/orgchart/DepartmentTest.kt) & [`EmployeeUseCaseTest.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonTest/kotlin/com/eventverse/app/domain/orgchart/EmployeeUseCaseTest.kt).

2. **Integration Test Server API (`server:test`)**:
   - Uji endpoint Ktor `POST /api/tenant/employees` dan `PUT /api/tenant/employees/{id}` dengan payload tanpa `departmentId` untuk `EXECUTIVE` dan dengan `departmentId` untuk staf biasa.
   - Berkas: [`EmployeeApiTest.kt`](file:///Volumes/amalari/Projects/wemade/server/src/test/kotlin/com/eventverse/app/EmployeeApiTest.kt).

3. **MVI ViewModel Test (`app:shared:jvmTest`)**:
   - Uji event `SelectReportsTo`: saat memilih Joko Susilo (Produksi), pastikan `state.isDepartmentLocked == true` dan `state.selectedDepartment.displayName == "Produksi & PPIC"`.
   - Saat memilih Hendra Kusuma (Direksi), pastikan `state.isDepartmentLocked == false`.
   - Uji modal tambah tingkat wewenang dinamis (`SaveNewDepartmentTier`).

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

Untuk mengasah pemahamanmu setelah membaca modul ini, coba selesaikan tantangan berikut:

- [ ] **Tantangan 1**: Tambahkan validasi batas karakter pada dialog tambah tingkat wewenang baru (minimal 3 karakter, maksimal 30 karakter). Tampilkan pesan error inline di dialog jika nama tier tidak memenuhi syarat.
- [ ] **Tantangan 2**: Buat fitur untuk menghapus atau menonaktifkan tingkat wewenang kustom yang baru dibuat jika belum ada karyawan yang menggunakannya (`canDeleteTier` logic).
- [ ] **Tantangan 3**: Tambahkan warna aksen/badge khusus di T-Shape Chart untuk tingkatan `TEAM_LEAD` (Mandor/Kepala Regu) agar secara visual tampak membedakan antara Kepala Divisi dan Operator Lapangan.
