# 🎓 Modul Pembelajaran: Sinkronisasi Hirarki Bagan Organisasi Dua Arah (Dual-Path Hierarchy Sync)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: MVI UI Pattern, State Synchronization, Reactive Form UX, Domain-Driven Design (DDD)  
> **Prasyarat**: Kotlin Multiplatform (KMP), Compose Multiplatform State Management, Flow & StateFlow  
> **Referensi Task**: Dual-Path Smart Selection (Under Siapa vs. Manual Divisi & Wewenang)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Bayangkan seorang Manajer HR atau Mandor Pabrik Garment yang sedang merekrut karyawan baru:
- **Kasus A (Jalur Praktis Lapangan)**: Mandor berkata, *"Saya mau rekrut operator jahit baru untuk ditempatkan langsung di bawah Pak Joko (Kepala Produksi)."* Bagi mandor, yang ada di kepalanya adalah **sosok atasan langsungnya ("under siapa")**. Mandor tidak ingin repot memilih divisi produksi dulu, lalu memilih level staf, baru kemudian mencari nama Pak Joko.
- **Kasus B (Jalur Struktural Formal)**: Manajer HR berkata, *"Pabrik kita membuka lowongan Kepala Divisi QC baru."* Di sini HR berpikir secara **struktural**: pilih divisi Quality Control, pilih wewenang Kepala Divisi. Secara hierarki, seorang Kepala Divisi otomatis wajib melapor langsung kepada Direksi (Direktur Utama).

### Masalah Nyata Jika Tanpa Sinkronisasi Cerdas:
1. **Dropdown Mandek / Tidak Sinkron**: Jika user memilih Atasan "Pak Joko (Kepala Produksi)", namun form tetap berada di divisi "Sales", terjadi inkonsistensi fatal: operator terdaftar di Divisi Sales namun melapor ke Kepala Produksi.
2. **Form Kosong Membingungkan**: Ketika user baru membuka form karyawan baru, jika field atasan dibiarkan kosong (`null`) tanpa auto-selection, user bingung siapa atasan standarnya dan terpaksa melakukan klik manual berulang kali.
3. **Divisi Baru Tanpa Kepala**: Jika ada divisi baru yang belum memiliki Kepala Divisi, staf di divisi tersebut tidak memiliki atasan jika filter hanya mencari Kepala Divisi di divisi tersebut. Sistem harus cerdas memiliki *fallback* otomatis melapor ke Direksi (Executive).

### Solusi Elegan: Dual-Path Reactive Form
1. **Jalur 1 ("Pilih Under Siapa")**: Begitu user memilih sosok atasan dari daftar pimpinan perusahaan, divisi otomatis mengikuti divisi atasan tersebut, dan tingkat wewenang otomatis disesuaikan (di bawah Kepala Divisi -> otomatis jadi Staf/Operator; di bawah Direktur -> otomatis jadi Kepala Divisi).
2. **Jalur 2 ("Pilih Manual Divisi lalu Wewenang")**: Begitu user mengklik chip divisi dan radio wewenang, atasan otomatis langsung terpilih tanpa perlu dicari secara manual.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu harus mengimplementasikan fitur sinkronisasi cerdas ini dari nol, ikuti urutan berikut:

### Langkah 0: Pahami Aturan Domain & Hubungan Hirarki
Sebelum menyentuh UI Compose, rumuskan aturan bisnis hierarki:
- Seorang `EXECUTIVE` (Direksi) tidak memiliki atasan (`reportsTo = null`).
- Seorang `HEAD_OF_DEPARTMENT` (Kepala Divisi) melapor ke `EXECUTIVE`.
- Seorang `STAFF_OPERATOR` (Staf/Operator) melapor ke `HEAD_OF_DEPARTMENT` di divisinya sendiri. Jika divisinya belum memiliki kepala, melapor langsung ke `EXECUTIVE`.

### Langkah 1: Buat Domain Resolver Function Murni (`resolveDefaultSuperior`)
Tulis fungsi murni (*pure function*) yang menerima daftar karyawan aktif, divisi tujuan, dan tingkat wewenang, lalu mengembalikan ID atasan yang tepat. Karena ini murni tanpa efek samping, logika ini 100% mudah diuji dengan Unit Test.

### Langkah 2: Lengkapi State Properti Komputasi di UI State (`companyLeaders` & `availableSuperiors`)
Tambahkan selector properti pada `OrgChartUiState`:
- `companyLeaders`: Daftar seluruh pimpinan perusahaan (Direksi & Kepala Divisi) untuk dropdown Jalur 1.
- `availableSuperiors`: Daftar atasan yang valid untuk level & divisi yang dipilih (termasuk fallback ke Direksi jika belum ada Kepala Divisi).
- Pastikan menyaring `selectedEmployeeId` agar seorang karyawan tidak bisa menjadi atasan untuk dirinya sendiri saat mode edit (mencegah *circular dependency*).

### Langkah 3: Perbarui Event Handlers di ViewModel
- Tangani event `SelectReportsTo`: saat atasan dipilih, perbarui `selectedReportsToId`, sinkronkan `selectedDepartment = superior.department`, dan sesuaikan `selectedLevel`.
- Tangani event `SelectDepartment`: panggil `resolveDefaultSuperior` untuk mengupdate `selectedReportsToId`.
- Tangani event `SelectLevel`: panggil `resolveDefaultSuperior` untuk mengupdate `selectedReportsToId`.
- Tangani `loadInitialData()` dan `StartCreateNewEmployee`: pastikan `selectedReportsToId` langsung terisi sejak detik pertama form dibuka.

### Langkah 4: Rancang Komponen UI di Compose Multiplatform
- Tambahkan kartu **"⚡ Jalur Cepat: Under Siapa?"** dengan `DropdownMenu` yang menampilkan seluruh pimpinan beserta divisi dan peran barunya.
- Tambahkan badge **`[✓ Terpilih]`** dan indikator **`Otomatis terpilih`** di bagian Atasan Langsung agar user paham bahwa sistem telah mengisinya secara otomatis.

### Langkah 5: Tulis Unit Test Komprehensif
Uji skenario Jalur 1 dan Jalur 2 untuk memastikan regresi tidak terjadi di masa mendatang.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pure Resolver Function (`resolveDefaultSuperior`)

File: [OrgChartViewModel.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartViewModel.kt)

```kotlin
companion object {
    /**
     * Logika penentuan atasan default otomatis berdasarkan hirarki wewenang dan divisi:
     * - STAFF_OPERATOR -> Kepala Divisi dari divisi tersebut. Fallback ke Direksi jika belum ada kepala divisi.
     * - HEAD_OF_DEPARTMENT -> Direksi (Executive).
     * - EXECUTIVE -> null (tidak memiliki atasan).
     */
    fun resolveDefaultSuperior(
        employees: List<OrgNode>,
        dept: Department?,
        level: HierarchyLevel
    ): String? {
        return when (level) {
            HierarchyLevel.EXECUTIVE -> null
            HierarchyLevel.HEAD_OF_DEPARTMENT -> {
                employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
            }
            HierarchyLevel.STAFF_OPERATOR -> {
                employees.find {
                    it.department.id == dept?.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
                }?.id?.value ?: employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
1. **Single Source of Truth**: Logika pencarian atasan default tidak diduplikasi di 4 tempat berbeda (`loadInitialData`, `StartCreateNewEmployee`, `SelectDepartment`, `SelectLevel`). Cukup panggil satu fungsi ini.
2. **Fallback Safety**: Perhatikan operator elvis `?: employees.find { it.level == HierarchyLevel.EXECUTIVE }?.id?.value`. Jika pabrik baru saja membuat divisi "Divisi Bordir Khusus" yang belum memiliki Kepala Divisi, operator baru di divisi tersebut tidak akan kehilangan atasan, melainkan otomatis melapor ke Direksi.

---

### Blok B: Jalur 1 — Sinkronisasi Otomatis Saat Pilih Atasan Langsung

File: [OrgChartViewModel.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartViewModel.kt)

```kotlin
is OrgChartUiEvent.SelectReportsTo -> {
    _uiState.update { state ->
        if (event.superiorId == null) {
            // Memilih tanpa atasan -> otomatis tingkat wewenang menjadi Direksi (Executive)
            state.copy(
                selectedReportsToId = null,
                selectedLevel = HierarchyLevel.EXECUTIVE
            )
        } else {
            val superior = state.employees.find { it.id.value == event.superiorId }
            if (superior != null) {
                // Jalur 1: Pilih under siapa -> divisi & wewenang otomatis keisi
                val autoLevel = when (superior.level) {
                    HierarchyLevel.EXECUTIVE -> HierarchyLevel.HEAD_OF_DEPARTMENT
                    HierarchyLevel.HEAD_OF_DEPARTMENT -> HierarchyLevel.STAFF_OPERATOR
                    HierarchyLevel.STAFF_OPERATOR -> HierarchyLevel.STAFF_OPERATOR
                }
                state.copy(
                    selectedReportsToId = superior.id.value,
                    selectedDepartment = superior.department,
                    selectedLevel = autoLevel
                )
            } else {
                state.copy(selectedReportsToId = event.superiorId)
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- Ketika user memilih "Joko Susilo (Kepala Produksi)", state tidak hanya mencatat `selectedReportsToId = joko.id`.
- State secara reaktif memperbarui `selectedDepartment = joko.department` (otomatis switch ke Produksi) dan `selectedLevel = STAFF_OPERATOR`.
- Jika user memilih `null` ("Tanpa Atasan"), level otomatis diangkat menjadi `EXECUTIVE`.

---

### Blok C: Jalur 2 — Sinkronisasi Otomatis Saat Pilih Divisi & Wewenang

File: [OrgChartViewModel.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartViewModel.kt)

```kotlin
is OrgChartUiEvent.SelectDepartment -> {
    _uiState.update { state ->
        // Jalur 2: Pilih manual divisi -> atasan otomatis menyesuaikan
        val defaultSuperior = resolveDefaultSuperior(
            employees = state.employees,
            dept = event.dept,
            level = state.selectedLevel
        )

        state.copy(
            selectedDepartment = event.dept,
            selectedReportsToId = defaultSuperior
        )
    }
}

is OrgChartUiEvent.SelectLevel -> {
    _uiState.update { state ->
        // Jalur 2: Pilih manual wewenang -> atasan otomatis menyesuaikan
        val targetDept = state.selectedDepartment ?: state.activeDepartment
        val defaultSuperior = resolveDefaultSuperior(
            employees = state.employees,
            dept = targetDept,
            level = event.level
        )

        state.copy(
            selectedLevel = event.level,
            selectedReportsToId = defaultSuperior
        )
    }
}
```

**Mengapa blok ini ditulis begini?**
- Begitu user berganti divisi (misal klik chip "GUDANG"), atasan langsung berpindah dari Kepala Sales ke Kepala Gudang (Siti Rahma).
- User tidak perlu lagi mengarahkan kursor ke bawah untuk mengklik radio button atasan secara manual. Semuanya selesai secara otomatis.

---

### Blok D: UI Quick Selector di Compose Multiplatform

File: [OrgChartScreen.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartScreen.kt)

```kotlin
// ─── JALUR CEPAT: PILIH UNDER SIAPA (OTOMATIS ISI DIVISI & WEWENANG) ───
var isUnderSiapaMenuOpen by remember { mutableStateOf(false) }
val currentSuperiorNode = state.employees.find { it.id.value == state.selectedReportsToId }

Card(
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
    modifier = Modifier.fillMaxWidth()
) {
    Column(modifier = Modifier.padding(12.dp)) {
        // Label Header dengan Icon Petir
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⚡", fontSize = 13.sp)
                Text("Jalur Cepat: Under Siapa?", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text("Otomatis isi divisi & wewenang", fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
        }
        ...
        // DropdownMenu menampilkan seluruh pemimpin perusahaan
        DropdownMenu(expanded = isUnderSiapaMenuOpen, onDismissRequest = { isUnderSiapaMenuOpen = false }) {
            state.companyLeaders.forEach { leader ->
                ...
                DropdownMenuItem(
                    text = { ... },
                    onClick = {
                        onSuperiorChange(leader.id.value)
                        isUnderSiapaMenuOpen = false
                    }
                )
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- Menempatkan selector ini di bagian atas formulir memberikan *affordance* yang jelas bagi user yang ingin jalan pintas.
- Menu dropdown memberikan preview yang sangat informatif: menampilkan nama atasan, jabatan, dan wewenang yang akan otomatis dihasilkan (misal: *"Joko Susilo → otomatis jadi Staf / Operator (PROD)"*).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Mengapa Kita Memilih Pendekatan Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Unidirectional Data Flow (MVI) via StateFlow** | State lokal di dalam `@Composable` (`remember { mutableStateOf }`) | Menjamin sinkronisasi state antara form, chart node preview, dan modal dialog selalu konsisten 100%. Logika bisnis dapat diuji tanpa UI renderer. | State desinkronisasi: form menampilkan Divisi Produksi tetapi bagan menampilkan Sales; form mengira atasan A tetapi saat disimpan terkirim ID B. |
| **Dual-Path Bidirectional Auto-Correction** | Form sekuensial kaku (wajib isi step 1, baru step 2, baru step 3) | Memberikan fleksibilitas UX maksimal kepada dua tipe pengguna: mandor lapangan yang ingat orang vs. HR yang ingat struktur organisasi. | User frustrasi karena harus mengklik mundur/maju hanya untuk mengubah atasan. |
| **Fallback ke Executive jika Dept Kosong** | Membiarkan `selectedReportsToId = null` atau disable tombol simpan | Menjaga alur onboarding divisi baru tetap mulus. Pabrik konveksi sering kali menambah divisi baru dan menaruh staf pertama sebelum merekrut kepala divisi. | Error validasi *"Superior cannot be null"* yang membingungkan user ketika divisi baru dibuat. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Self-Referential Hierarchy Loop (Atasan = Diri Sendiri)**
   - *Kenapa bahaya*: Jika user sedang mengedit profil seorang Kepala Divisi (misal Budi Santoso), lalu di dropdown atasan nama Budi Santoso muncul dan dipilih, bagan organisasi akan mengalami *infinite loop* / *circular dependency* (`emp-budi` melapor ke `emp-budi`).
   - *Solusi elegan kita*:
     ```kotlin
     val companyLeaders: List<OrgNode>
         get() = employees.filter { candidate ->
             (candidate.level == HierarchyLevel.EXECUTIVE || candidate.level == HierarchyLevel.HEAD_OF_DEPARTMENT) &&
                     (selectedEmployeeId == null || candidate.id.value != selectedEmployeeId)
         }
     ```

2. **Jebakan 2: Menggunakan `activeDepartment` Alih-Alih `selectedDepartment`**
   - *Kenapa bahaya*: `activeDepartment` adalah divisi dari node bagan yang sedang di-klik/difokuskan di layar kanan. Jika user mengklik chip divisi di form kiri, yang berubah adalah `selectedDepartment`. Jika resolver menggunakan `activeDepartment`, atasan yang terpilih malah berasal dari divisi node yang sedang disorot di kanvas!
   - *Solusi elegan kita*: Selalu prioritaskan `val dept = selectedDepartment ?: activeDepartment`.

3. **Jebakan 3: Form Load dengan Nilai Atasan Kosong (`null`)**
   - *Kenapa bahaya*: Ketika form dibuka, jika `selectedReportsToId` bernilai `null`, semua radio button atasan dalam keadaan tidak tercentang, memaksa user melakukan klik tambahan.
   - *Solusi elegan kita*: Sejak `loadInitialData()`, inisialisasi `selectedReportsToId` dengan `resolveDefaultSuperior(sampleList, Department.SALES, HierarchyLevel.STAFF_OPERATOR)`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Unit test yang dibuat di [OrgChartViewModelSuperiorAutoFillTest.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonTest/kotlin/com/eventverse/app/presentation/orgchart/OrgChartViewModelSuperiorAutoFillTest.kt) menguji kedua jalur secara otomatis:

```kotlin
@Test
fun jalur1_selectReportsTo_shouldAutomaticallyFillDepartmentAndAdjustLevel() {
    val viewModel = OrgChartViewModel(tenantSlug = "wemade-demo")

    // Pilih Joko Susilo (Kepala Produksi & PPIC)
    val joko = viewModel.uiState.value.employees.find {
        it.department.id == Department.PRODUCTION_PPIC.id && it.level == HierarchyLevel.HEAD_OF_DEPARTMENT
    }
    assertNotNull(joko)

    viewModel.onEvent(OrgChartUiEvent.SelectReportsTo(joko.id.value))
    val state = viewModel.uiState.value
    
    // Verifikasi: Divisi otomatis jadi Produksi & wewenang jadi Staf
    assertEquals(joko.id.value, state.selectedReportsToId)
    assertEquals(Department.PRODUCTION_PPIC.id, state.selectedDepartment?.id)
    assertEquals(HierarchyLevel.STAFF_OPERATOR, state.selectedLevel)
}
```

Jalankan perintah pengujian:
```bash
./gradlew :app:shared:jvmTest
```

Output:
```text
BUILD SUCCESSFUL in 1s
20 actionable tasks: 6 executed, 14 up-to-date
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Tambahkan debounce atau animasi transisi ketika chip divisi berganti akibat pemilihan dari Jalur Cepat, sehingga user secara visual melihat efek pergantian chip yang halus.
- [ ] **Tantangan 2**: Buat unit test tambahan yang menguji divisi kustom buatan tenant baru yang sama sekali belum memiliki karyawan, dan buktikan bahwa atasan otomatis jatuh ke `EXECUTIVE`.
- [ ] **Tantangan 3**: Tambahkan tooltip penjelasan pada badge `[✓ Terpilih]` di radio button atasan yang menjelaskan mengapa atasan tersebut otomatis terpilih (misal: *"Terpilih otomatis sebagai Kepala Divisi aktif untuk penempatan ini"*).
