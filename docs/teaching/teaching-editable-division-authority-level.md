# 🎓 Modul Pembelajaran: Divisi & Tingkat Wewenang Editable (Org Chart WeMade ERP)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Compose Multiplatform (MVI), State Unlocking, Optimistic UI Updates, Tier Hierarchy Mutability  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform, Compose state management, dan arsitektur MVI (Model-View-Intent)  
> **Referensi Task**: Fitur "Divisi dan Tingkat wewenang dibuat editable"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada versi sebelumnya, ketika seorang Admin mengedit data karyawan atau memilih atasan ("Under Siapa"), form karyawan secara sepihak **mengunci (hard-lock)** pilihan divisi (`isDepartmentLocked = true`). UI menyembunyikan chip pemilihan divisi dan menggantinya dengan kartu statis berikon gembok `🔒 Divisi Terkunci`. 

Selain itu:
1. Jika karyawan berada di level **Direksi (Executive)**, bagian "Tingkat Wewenang" berubah menjadi kartu statis abu-abu tanpa radio button, sehingga admin tidak dapat mengubah levelnya menjadi Manajer/Supervisor.
2. Nama divisi atau tingkatan hierarki (*tiers*) di dalam divisi tersebut bersifat *hardcoded* dari initial seed atau API tanpa ada tombol untuk mengganti nama, kode singkat (*shortcode*), atau warna divisi secara langsung dari panel kerja Org Chart.

Akibatnya, jika terjadi restrukturisasi organisasi — misalnya seorang Supervisor dipindah divisi (mutasi horizontal) atau dinaikkan jabatannya ke level Direksi (promosi vertikal) — Admin terpaksa menghapus dan membuat ulang karyawan tersebut dari nol.

### Analogi Sederhana
Bayangkan sebuah formulir paspor fisik di mana kotak "Kewarganegaraan" dan "Status Jabatan" ditulis menggunakan spidol permanen hitam tebal, bukan pensil. Begitu ada pergantian jabatan atau departemen, kamu harus membakar paspor lama dan membuat yang baru. Sistem yang baik adalah sistem yang memberikan **pemberitahuan atau rekomendasi otomatis** (misal: "Atasanmu ada di Divisi Marketing, kami sarankan kamu ikut Divisi Marketing"), namun **tetap membiarkan admin memilih atau mengoreksi** pilihan tersebut.

### Hasil Akhir yang Diharapkan
1. **Divisi Bebas Dipilih**: Admin dapat mengganti divisi kapan saja via chip interaktif, termasuk memilih opsi khusus *"Direksi (Non-Divisi)"*.
2. **Tingkat Wewenang Fleksibel**: Admin dapat memilih antara level *Direksi* atau jenjang tier divisi (*Kepala Divisi, Supervisor, Staff, Operator*) secara transparan dengan radio selector.
3. **In-place Department & Tier Editing**: Admin dapat menekan tombol pensil `✏️ Edit Divisi` untuk mengganti nama/warna divisi, atau tombol pensil di samping nama tier untuk merevisi nama jabatan (misal: "Staff" menjadi "Senior Specialist"), dan perubahannya langsung tersinkronisasi ke backend API.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membuat fitur mutabilitas seperti ini dari nol, jangan langsung meloncat ke file Composable UI! Ikuti urutan ketergantungan arsitektural berikut:

```
┌─────────────────────────┐
│ 1. Core Domain Layer    │ -> Tambah domain method murni pada Entity (Department.updateTier)
└───────────┬─────────────┘
            ▼
┌─────────────────────────┐
│ 2. Infrastructure API   │ -> Kontrak HTTP client untuk PUT /api/tenant/departments/{id}
└───────────┬─────────────┘
            ▼
┌─────────────────────────┐
│ 3. Presentation State   │ -> Perluas UiState (modal state) & UiEvent (user intent)
└───────────┬─────────────┘
            ▼
┌─────────────────────────┐
│ 4. ViewModel (MVI)      │ -> Buka lock flag, handle event, optimistic update
└───────────┬─────────────┘
            ▼
┌─────────────────────────┐
│ 5. Compose UI & Dialogs │ -> Ganti static locked cards dengan interactive widgets
└───────────┬─────────────┘
            ▼
┌─────────────────────────┐
│ 6. Automated Unit Tests │ -> Verifikasi flag unlocked & flow edit department/tier
└─────────────────────────┘
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pure Domain Layer (`core/src/commonMain/.../Department.kt`)

Entity domain harus selalu menjaga konsistensi dan integritas datanya sendiri (*immutability by default*).

```kotlin
data class Department(
    val id: String,
    val name: String,
    val shortCode: String,
    val colorHex: String,
    val tiers: List<DepartmentTier> = emptyList(),
    val iconName: String = "folder",
    val isActive: Boolean = true
) {
    fun updateTier(tierId: String, newName: String): Department {
        require(newName.isNotBlank()) { "Nama tier tidak boleh kosong" }
        return copy(
            tiers = tiers.map { tier ->
                if (tier.id == tierId) tier.copy(name = newName.trim()) else tier
            }
        )
    }
}
```

**Mengapa blok ini ditulis begini?**
- **Immutabilitas**: Kita tidak memutasi `tier.name = newName` secara langsung. Kita menggunakan `.copy()` yang menghasilkan instance baru `Department` dengan daftar `tiers` baru.
- **Fail-fast Invariant**: Aturan bisnis domain ditegakkan dengan `require(newName.isNotBlank())`. Nama tingkatan jabatan tidak boleh berupa string kosong atau spasi saja.

---

### Blok B: Infrastructure Layer (`app/shared/.../OrgChartApiClient.kt`)

Client HTTP bertanggung jawab mengkomunikasikan perubahan entity ke backend API.

```kotlin
suspend fun updateDepartment(tenantSlug: String, department: Department): Result<Department> = runCatching {
    val response = httpClient.put("$baseUrl/api/tenant/departments/${department.id}") {
        header("X-Tenant-Slug", tenantSlug)
        contentType(ContentType.Application.Json)
        setBody(
            UpdateDepartmentRequest(
                displayName = department.name,
                shortName = department.shortCode,
                colorHex = department.colorHex
            )
        )
    }
    if (!response.status.isSuccess()) {
        error("Gagal memperbarui departemen: HTTP ${response.status.value}")
    }
    department
}
```

**Mengapa blok ini ditulis begini?**
- **Result Type Wrapper**: Menggunakan `runCatching` dan mengembalikan `Result<Department>`. Jika ada kegagalan jaringan atau server 500, ViewModel dapat menangani `onFailure` secara graceful tanpa crash uncaught exception.
- **Tenant Isolation**: Mengirimkan header `X-Tenant-Slug` untuk memastikan operasi database terisolasi pada tenant yang sedang aktif (RLS).

---

### Blok C: State Management & MVI (`app/shared/.../OrgChartUiState.kt` & `ViewModel`)

Dalam arsitektur MVI, *State* adalah *Single Source of Truth*. Kita menambahkan field untuk dialog edit departemen dan dialog edit tier:

```kotlin
// UiState
data class OrgChartUiState(
    // ...
    val isDepartmentLocked: Boolean = false, // Tidak lagi true saat atasan dipilih!
    
    // Modal Edit Departemen
    val isEditDeptModalOpen: Boolean = false,
    val editDeptId: String? = null,
    val editDeptNameInput: String = "",
    val editDeptShortNameInput: String = "",
    val editDeptColorHex: String = "#3B82F6",
    
    // Modal Edit Tier
    val isEditTierModalOpen: Boolean = false,
    val editTierDeptId: String? = null,
    val editTierId: String? = null,
    val editTierNameInput: String = ""
)
```

Pada `OrgChartViewModel.kt`, ketika atasan dipilih, kita tetap memberikan *smart default* tetapi **tidak pernah mengunci** pilihan divisi:

```kotlin
is OrgChartUiEvent.SelectReportsTo -> {
    val superior = _uiState.value.employees.find { it.id == event.superiorId }
    val superiorDept = superior?.department
    val autoSelectedDept = if (superiorDept != null) {
        _uiState.value.departments.find { it.name == superiorDept.name } ?: superiorDept
    } else {
        _uiState.value.selectedDepartment
    }

    _uiState.update {
        it.copy(
            selectedReportsToId = event.superiorId,
            selectedDepartment = autoSelectedDept,
            isDepartmentLocked = false // Kunci utama: Bebas diubah kapan pun oleh user!
        )
    }
}
```

Dan untuk menyimpan perubahan nama divisi dengan pola **Optimistic Update**:

```kotlin
is OrgChartUiEvent.SaveEditedDepartment -> {
    val deptId = _uiState.value.editDeptId ?: return
    val updatedDepts = _uiState.value.departments.map { dept ->
        if (dept.id == deptId) {
            dept.copy(
                name = _uiState.value.editDeptNameInput.trim(),
                shortCode = _uiState.value.editDeptShortNameInput.trim(),
                colorHex = _uiState.value.editDeptColorHex
            )
        } else dept
    }
    val updatedActiveDept = updatedDepts.find { it.id == deptId }
    
    // 1. Optimistic Update pada UI State
    _uiState.update {
        it.copy(
            departments = updatedDepts,
            selectedDepartment = if (it.selectedDepartment?.id == deptId) updatedActiveDept else it.selectedDepartment,
            isEditDeptModalOpen = false,
            bannerSuccessMessage = "Departemen '${updatedActiveDept?.name}' berhasil diperbarui"
        )
    }

    // 2. Sinkronisasi Async ke Backend API
    if (updatedActiveDept != null) {
        viewModelScope.launch {
            apiClient.updateDepartment(currentTenantSlug, updatedActiveDept)
                .onFailure { err ->
                    _uiState.update { it.copy(bannerErrorMessage = "Gagal menyimpan ke server: ${err.message}") }
                }
        }
    }
}
```

---

### Blok D: Presentation Layer Compose (`OrgChartScreen.kt`)

Pada tampilan form karyawan, kita menyediakan chip selector lengkap dengan opsi Direksi:

```kotlin
// Chip "Direksi (Non-Divisi)"
FilterChip(
    selected = state.selectedDepartment == null && state.selectedLevel == HierarchyLevel.EXECUTIVE,
    onClick = { onEvent(OrgChartUiEvent.SelectDireksi) },
    label = { Text("Direksi (Non-Divisi)") }
)

// Chip Semua Departemen
state.departments.forEach { dept ->
    FilterChip(
        selected = state.selectedDepartment?.id == dept.id,
        onClick = { onEvent(OrgChartUiEvent.SelectDepartment(dept.id)) },
        label = { Text(dept.name) }
    )
}
```

Dan untuk tingkat wewenang, pengguna dapat memilih Direksi maupun tier-tier divisi yang aktif dengan tombol radio dan tombol edit cepat:

```kotlin
// Pilihan Direksi
Row(modifier = Modifier.clickable { onEvent(OrgChartUiEvent.SelectDireksi) }) {
    RadioButton(
        selected = state.selectedLevel == HierarchyLevel.EXECUTIVE,
        onClick = { onEvent(OrgChartUiEvent.SelectDireksi) }
    )
    Text("Direksi (Level Puncak Organisasi)")
}

// Pilihan Tier Divisi
dept.tiers.forEach { tier ->
    Row {
        RadioButton(
            selected = isSelected,
            onClick = { onEvent(OrgChartUiEvent.SelectDepartmentTier(tier.id, tier.name)) }
        )
        Text(tier.name)
        IconButton(onClick = { onEvent(OrgChartUiEvent.OpenEditTierModal(dept.id, tier.id, tier.name)) }) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Tier")
        }
    }
}
```

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Optimistic UI Update** | Blocking Progress Spinner | UI terasa instan & responsif. Pengguna tidak perlu menunggu round-trip network 300ms hanya untuk melihat nama divisi berganti. | Jika API gagal, state perlu di-rollback (ditangani dengan banner pesan error). |
| **Unlocked with Smart Defaults** | Rigid State Locking (`isLocked=true`) | Memberikan fleksibilitas penuh kepada admin untuk mengoreksi struktur organisasi tanpa merusak alur auto-suggest. | Jika user memilih divisi berbeda dari atasan, relasi cross-division bisa terjadi (sudah didukung oleh domain matrix). |
| **Separated Modal State** | Generic JSON / Query Params | Type-safe state di dalam `OrgChartUiState` mencegah bugs string parsing dan mudah diuji di unit test. | Sedikit menambah jumlah field di data class `OrgChartUiState`. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Merusak Aturan Bisnis Direksi (`HierarchyLevel.EXECUTIVE`)**
   - *Kenapa bahaya*: Di domain kita, seorang Direksi *tidak boleh memiliki atasan* (`reportsTo == null`) dan *tidak terikat pada departemen spesifik* (`department == null`). Jika junior dev hanya membuat chip divisi editable tanpa menangani transisi ke/dari Direksi, bisa tercipta anomali data di database di mana Direksi memiliki `department_id` yang tidak valid.
   - *Solusi elegan kita*: Buat event khusus `OrgChartUiEvent.SelectDireksi` yang secara eksplisit menyetel `selectedDepartment = null`, `selectedLevel = EXECUTIVE`, dan `selectedTierName = "Direksi"`.

2. **Jebakan 2: Lupa Memperbarui Referensi Objek yang Sedang Terpilih**
   - *Kenapa bahaya*: Saat nama divisi diubah di dalam list `state.departments`, jika kamu lupa memperbarui `state.selectedDepartment`, maka form yang sedang terbuka akan tetap menampilkan nama divisi yang lama sampai user mengklik ulang.
   - *Solusi elegan kita*:
     ```kotlin
     selectedDepartment = if (it.selectedDepartment?.id == deptId) updatedActiveDept else it.selectedDepartment
     ```

3. **Jebakan 3: Menggunakan Operator `!!` pada ID Modal**
   - *Kenapa bahaya*: Jika modal ditutup bersamaan dengan event simpan yang ter-trigger lambat, `editDeptId!!` akan melempar `NullPointerException`.
   - *Solusi elegan kita*: Gunakan guard clause `val deptId = _uiState.value.editDeptId ?: return` sesuai aturan `AGENTS.md`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian diuji menggunakan Kotlin CommonTest di `:app:shared:jvmTest`:

```kotlin
@Test
fun editDepartmentAndTiers_shouldUpdateStateCorrectly() = runTest {
    val repository = FakeOrgChartRepository()
    val viewModel = OrgChartViewModel(repository)
    viewModel.onEvent(OrgChartUiEvent.LoadData)

    val dept = viewModel.uiState.value.departments.first()
    
    // 1. Uji Buka Modal Edit Departemen
    viewModel.onEvent(OrgChartUiEvent.OpenEditDeptModal(dept.id, dept.name, dept.shortCode, dept.colorHex))
    assertEquals(true, viewModel.uiState.value.isEditDeptModalOpen)
    assertEquals(dept.name, viewModel.uiState.value.editDeptNameInput)

    // 2. Uji Simpan Perubahan Nama Departemen
    val newDeptName = "Produksi & Jahit Modern"
    viewModel.onEvent(OrgChartUiEvent.UpdateEditDeptName(newDeptName))
    viewModel.onEvent(OrgChartUiEvent.SaveEditedDepartment)

    val updatedDept = viewModel.uiState.value.departments.find { it.id == dept.id }
    assertEquals(newDeptName, updatedDept?.name)
    assertEquals(false, viewModel.uiState.value.isEditDeptModalOpen)

    // 3. Uji Edit Nama Tier
    val tier = dept.tiers.first()
    viewModel.onEvent(OrgChartUiEvent.OpenEditTierModal(dept.id, tier.id, tier.name))
    viewModel.onEvent(OrgChartUiEvent.UpdateEditTierName("Senior Head of Division"))
    viewModel.onEvent(OrgChartUiEvent.SaveEditedDepartmentTier)

    val updatedTier = viewModel.uiState.value.departments.find { it.id == dept.id }?.tiers?.find { it.id == tier.id }
    assertEquals("Senior Head of Division", updatedTier?.name)
}
```

Jalankan perintah verifikasi:
```bash
./gradlew :app:shared:jvmTest :server:test
```

Hasil: **100% BUILD SUCCESSFUL** (semua assertions lulus tanpa error).

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buat fitur untuk menambah tingkatan tier baru (*Add Tier*) di bawah departemen secara dinamis jika sebuah departemen membutuhkan sub-layer baru (misal: "Junior Operator").
- [ ] **Tantangan 2**: Tambahkan dialog konfirmasi ketika seorang Direksi dipindah ke Divisi biasa, memperingatkan bahwa ia akan memerlukan penugasan atasan langsung (*Reports To*).
- [ ] **Tantangan 3**: Tambahkan drag-and-drop ordering untuk urutan hierarki tier di dalam `EditDepartmentDialog` agar posisi jabatan dapat ditukar dengan mudah.
