# Modul Pembelajaran: Scoping Data Hierarkis & Read-Only Layout pada Bagan Organisasi (DDD & Compose Multiplatform)

> **Level**: Intermediate to Advanced  
> **Topik**: Dynamic Data Boundary Scoping, Pure Domain Filtering (`OrgChartVisibility`), Conditional UI Layout (100% Full-Width Chart), State Guarding  
> **Target Audience**: Junior / Mid Developer di WeMade ERP

---

## 1. Konteks Masalah & Kebutuhan Bisnis

Di WeMade ERP, modul **Bagan Organisasi & Karyawan** (`ORG_CHART`) adalah modul bertipe `ScopeCapability.HIERARCHICAL`. Artinya, wewenang modul ini tidak hanya mengenal tingkatan aksi (*Access Level*: `VIEW`, `OPERATE`, `MANAGE`), tetapi juga jangkauan data (*Data Scope*: `OWN_DATA_ONLY`, `SUBORDINATE_DATA`, `ALL_TENANT_DATA`).

Sebelum perbaikan ini dilakukan:
1. **Form Input Selalu Muncul**: Meskipun pengguna hanya memiliki akses baca (`VIEW` / Hanya Lihat), form input karyawan baru ("Input Karyawan Baru") tetap memakan 420dp ruang di sisi kiri layar, sementara tombol simpan dinonaktifkan.
2. **Draft Card Dummy Terfokus**: Saat pertama kali dibuka, bagan langsung merender kartu draft semu (*"POSISI BARU DIKEHENDAKI / Nama Karyawan Baru"*) alih-alih menampilkan karyawan aktual.
3. **Data Tidak Ter-scope**: Staf Gudang & Logistik yang seharusnya hanya memiliki jangkauan data bawahan/divisinya (`SUBORDINATE_DATA` pada divisi `dept-warehouse`) tetap disajikan 5 divisi template konveksi (Sales, Produksi, Gudang, QC, Finance) dan bisa berpindah divisi secara bebas.

### Target Hasil
1. **Hanya Tampilkan Bagan**: Bila pengguna memiliki wewenang `VIEW` (`!canWrite`), sembunyikan form input dan bentangkan `ChartPreviewPanel` memenuhi 100% lebar layar.
2. **Fokus pada Karyawan Aktual**: Saat dalam mode baca, sistem langsung memilih kepala divisi atau staf pertama pada divisi terkait tanpa kartu semu draft.
3. **Kunci Divisi (Strict Scoping)**: Jika wewenang pengguna dibatasi pada divisinya (`SUBORDINATE_DATA`), daftar divisi disaring ke divisi tersebut, pemilihan divisi lain/direksi dicegah (`isDepartmentLocked = true`), dan seluruh aksi mutasi (tambah karyawan, tambah divisi, reset preset) diguard.

---

## 2. Order of Operations ("Start dari Mana?")

Ketika membangun fitur isolasi data hierarkis di arsitektur DDD + Compose Multiplatform, urutan pengerjaannya adalah:

```
[1. Pure Domain Logic]          -> OrgChartVisibility.visibleTo()
          ↓
[2. Presentation State Modeling]-> OrgChartUiState (fallback focus & draft guard)
          ↓
[3. Presentation State Holder]  -> OrgChartViewModel (constructor scoping & event guards)
          ↓
[4. Shared UI Composition]      -> OrgChartScreen (conditional panel & 100% width)
          ↓
[5. Application Wiring]         -> App.kt (inject activePersona / session attributes)
          ↓
[6. Infrastructure & Route]     -> DepartmentRoutes.kt & Application.kt (server gate)
          ↓
[7. Automated Verification]     -> Unit Tests (:app:shared:jvmTest)
```

---

## 3. Bedah Kode Blok per Blok & Mental Model

### A. Fallback Focus Node di `OrgChartUiState.kt`

```kotlin
val focus = if (isCreatingNew) {
    draftNode
} else {
    val targetEmployee = if (selectedEmployeeId != null) {
        employees.find { it.id.value == selectedEmployeeId }
    } else {
        // Fallback cerdas: cari Head of Department atau karyawan pertama divisi
        val dept = selectedDepartment ?: activeDepartment
        employees.find { it.department?.id == dept.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT }
            ?: employees.find { it.department?.id == dept.id }
            ?: employees.firstOrNull()
    }
    if (targetEmployee != null) {
        if (nameInput.isBlank()) {
            targetEmployee // Mode baca: jangan timpa dengan default form
        } else {
            targetEmployee.copy(...)
        }
    } else {
        draftNode
    }
}
```

#### Mental Model:
- **Pemisahan Draft vs Real**: Ketika `isCreatingNew = false`, fokus tidak boleh jatuh ke `draftNode`. Jika `selectedEmployeeId` masih `null` (misal saat inisialisasi awal layar baca), kita otomatis mencari Kepala Divisi (`HEAD_OF_DEPARTMENT`) atau karyawan pertama di divisi tersebut.
- **Form Input Sanitization**: Jika `nameInput.isBlank()`, form tidak sedang digunakan untuk mengedit; jangan timpa level asli karyawan dengan default form (`level = STAFF_OPERATOR`).

---

### B. Inisialisasi & Scoping di `OrgChartViewModel.kt`

```kotlin
class OrgChartViewModel(
    private val tenantSlug: String = "wemade-demo",
    private val apiClient: OrgChartApiClient? = null,
    private val access: ModuleAccessConfig = ModuleAccessConfig(AccessLevel.MANAGE),
    private val viewerDepartmentId: String? = null,
    private val viewerEmployeeId: String? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val isScoped: Boolean
        get() = access.scope != DataScope.ALL_TENANT_DATA && !viewerDepartmentId.isNullOrBlank()

    private fun filterByScope(nodes: List<OrgNode>, deptId: String?): List<OrgNode> {
        if (!isScoped) return nodes
        return OrgChartVisibility.visibleTo(
            nodes = nodes,
            scope = access.scope,
            viewerEmployeeId = viewerEmployeeId?.let { OrgNodeId(it) },
            viewerDepartmentId = deptId ?: viewerDepartmentId
        )
    }
```

#### Mental Model:
- `OrgChartVisibility.visibleTo` adalah pure Kotlin domain service. Fungsi ini menggabungkan dua sumbu hierarki: divisi kerja (`viewerDepartmentId`) dan pohon pelaporan komando (`subordinateClosure` dari `viewerEmployeeId`).
- Ketika `isScoped == true`:
  1. `departments` disaring hanya menyertakan divisi pengguna.
  2. `isDepartmentLocked` diset `true`.
  3. `isCreatingNew` diset `access.canWrite` (sehingga `false` bagi peran Hanya Lihat).
  4. `selectedEmployeeId` otomatis memilih pimpinan atau staf divisi.

---

### C. Defensive Event Guarding di `OrgChartViewModel.kt`

```kotlin
is OrgChartUiEvent.SelectDepartment -> {
    // Larang perpindahan divisi jika scope terkunci
    if (isScoped || _uiState.value.isDepartmentLocked) return
    ...
}

is OrgChartUiEvent.SelectDireksi -> {
    if (isScoped || _uiState.value.isDepartmentLocked) return
    ...
}

is OrgChartUiEvent.StartCreateNewEmployee -> {
    if (!access.canWrite) return
    ...
}

is OrgChartUiEvent.SaveEmployee -> {
    if (!access.canWrite) return
    handleSaveEmployee()
}

is OrgChartUiEvent.ClearAllDataToEmpty, is OrgChartUiEvent.RestoreDefaultPresets -> {
    if (!access.canManage || isScoped) return
    ...
}
```

#### Mental Model:
- UI yang menyembunyikan tombol belum tentu aman dari event yang tidak sengaja terpanggil. State holder (`ViewModel`) wajib menerapkan pertahanan internal (*defensive invariants*) agar mutasi ilegal ditolak secara deterministik.

---

### D. Kondisional Layout & Responsivitas di `OrgChartScreen.kt`

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    horizontalArrangement = Arrangement.spacedBy(20.dp)
) {
    // SISI KIRI: Form Input Karyawan (HANYA DITAMPILKAN JIKA canWrite)
    if (canWrite) {
        EmployeeFormPanel(
            state = state,
            ...
            modifier = Modifier.width(420.dp)
        )
    }

    // SISI KANAN: Live Org Chart Preview (Mengembang 100% jika !canWrite)
    ChartPreviewPanel(
        state = state,
        canWrite = canWrite,
        canManage = canManage,
        modifier = Modifier.weight(1f)
    )
}
```

#### Mental Model:
- Dalam Compose, `Modifier.weight(1f)` pada child `Row` akan membagi sisa ruang secara proporsional.
- Ketika `canWrite == true`: Form memiliki lebar tetap `420.dp`, dan bagan mengambil sisa ruang (`weight(1f)`).
- Ketika `canWrite == false`: Form tidak di-komposisi (`if (canWrite)` false), sehingga satu-satunya child yang memiliki `weight(1f)` di dalam `Row` akan mengembang memenuhi **100% lebar layar** secara alami tanpa perlu perhitungan floating manual.

---

## 4. Jebakan Pemula (Common Pitfalls)

1. **Mengira "Hanya Lihat" Cukup dengan Disable Tombol Simpan**:
   - *Jebakan*: Membiarkan form tetap tampil dengan tombol disable. Akibatnya pengguna merasa form itu masih relevan untuk diisi, dan bagan terdesak ke ruang sempit di sebelah kanan.
   - *Solusi*: Hilangkan seluruh panel form (`if (canWrite)`) agar bagan menjadi satu-satunya fokus pandangan.

2. **Clobbering State saat Membaca Data**:
   - *Jebakan*: Saat node karyawan dipilih di bagan, `nameInput = emp.name` dieksekusi. Lalu saat fokus dihitung, `emp.copy(level = selectedLevel)` mengganti jabatan karyawan menjadi default form (misal `STAFF_OPERATOR`).
   - *Solusi*: Di mode `VIEW`, jangan isi buffer input form (`nameInput = ""`) dan gunakan data asli karyawan secara langsung di `resolvedHierarchy`.

3. **Scoping Hanya di Klien**:
   - *Jebakan*: Hanya menyembunyikan tab divisi di frontend. Jika API endpoint `GET /api/tenant/departments` dipanggil via Postman/cURL, seluruh data departemen tetap bocor.
   - *Solusi*: Pasang guard yang sama di `DepartmentRoutes.kt` dan `EmployeeRoutes.kt` menggunakan `call.orgChartDecision(...)`.

---

## 5. Cara Pengujian & Verifikasi

Jalankan test suite KMP untuk memverifikasi logika scoping dan read-only mode:

```bash
./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.orgchart.OrgChartViewModelScopingTest"
```

Seluruh skenario pengujian:
1. Pengguna `VIEW` + `SUBORDINATE_DATA` pada divisi `dept-warehouse` terkunci pada divisi Gudang & Logistik.
2. Pemilihan divisi lain diabaikan.
3. Form input tidak merusak node saat node diklik.
4. Pengguna `MANAGE` + `ALL_TENANT_DATA` tetap memiliki keleluasaan penuh mengelola seluruh divisi pabrik.
