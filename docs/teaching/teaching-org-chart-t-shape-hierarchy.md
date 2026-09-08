# 🎓 Modul Pembelajaran: Interactive Org Chart & T-Shape Hierarchy View

> **Level Target**: Junior to Mid-Level Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Dynamic Tenant Entities, Visual Org Chart, Reactive State Preview, T-Shape Layout Pattern, Compose Multiplatform  
> **Prasyarat**: Dasar Kotlin Multiplatform, pemahaman relasi pohon hirarki (Tree Data Structure), dan konsep MVI State Machine.  
> **Referensi Task**: Fitur Bagan Organisasi Interaktif & Live Input User ([WeMade ERP](https://github.com/amalari/wemade-erp))

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata: Keruwetan Bagan Organisasi Konvensional & Keterbatasan Preset Kaku
Di banyak software HR / ERP enterprise (seperti SAP atau sistem lama), bagan organisasi (*Org Chart*) digambar sebagai pohon raksasa yang kaku:
1. **Layar Penuh & Ruwet**: Garis-garis konektor saling silang, membuat mata lelah dan membingungkan pemilik pabrik.
2. **Setup Karyawan Membutuhkan Tebak-Tebakan**: Ketika admin menambah staf baru dan memilih "Atasan Langsung" dari dropdown 50 nama, admin tidak punya visual feedback apakah orang tersebut benar berada di bawah kepala divisi yang tepat.
3. **Approval Chain Tidak Transparan**: Staf sering kali tidak tahu kepada siapa dokumen SPK atau sampling mereka harus divalidasi.
4. **Jebakan Enum Statis (Hardcoded Departments)**: Divisi pabrik sering dikunci dalam `enum class Department` (Sales, PPIC, Gudang, QC, Direksi). Padahal setiap konveksi punya variasi divisi berbeda: ada yang butuh *Bordir & Sablon*, *Finishing & Packing*, *Pola & Cutting*, bahkan ada yang ingin mulai menyusun struktur dari **kanvas kosong (blank state)**.

### Solusi Elegan: "T-Shape Hierarchy View" & Dynamic Department Management
- **1 Tingkat ke Atas (Superior)**: Hanya menampilkan 1 kotak atasan langsung tempat meminta persetujuan (*approval chain*).
- **Rekan Sejajar (Horizontal Peer Chips)**: Kepala divisi lain cukup ditampilkan sebagai badge kecil tanpa menggambar pohon anak buah mereka.
- **Posisi Fokus**: Kotak karyawan yang sedang diedit/diinput ditandai dengan bingkai bersinar (*glowing highlight*).
- **Full ke Bawah (Subordinates)**: Seluruh anggota tim di divisinya ditampilkan secara lengkap.
- **Dynamic Department Entity**: Divisi bukan lagi enum mati, melainkan *Domain Entity* yang dapat ditambah secara dinamis oleh pengguna kapan saja (`+ Divisi Baru`).
- **Starter Presets vs Blank Slate**: Preset 5 divisi hanya disediakan sebagai starter template saat registrasi awal/demo, namun pengguna dapat mengosongkan seluruh bagan (*start from scratch*) atau memuat ulang preset kapan pun dibutuhkan.

Saat form di sebelah kiri diubah, bagan di sebelah kanan langsung bereaksi dan berpindah posisi secara instan (*Live Reactive Preview*).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Berikut adalah urutan langkah (order of operations) untuk membangun fitur ini:

```
[ Step 0: Dynamic Domain Entity ] ➔ [ Step 1: OrgNode & Algoritma T-Shape ] ➔ [ Step 2: Unit Testing ]
                                                                                       │
[ Step 5: Split-View Screen & Dialog ] ⬅ [ Step 4: Visual Chart Components ] ⬅ [ Step 3: MVI State & Draft ]
```

1. **Langkah 0: Definisikan Dynamic Entity & Value Object di Pure Domain (`core`)**
   - Buat `DepartmentId` (@JvmInline value class) dan `data class Department` dengan properti `isCustom`.
   - Di `companion object`, sediakan default template konveksi (`Department.defaultPresets()`) dan factory function `createCustom(...)`.
2. **Langkah 1: Bangun Entitas `OrgNode` dan Fungsi Resolusi `resolveTShapeView()`**
   - Algoritma murni Kotlin yang menerima daftar karyawan + node fokus, lalu menghasilkan `TShapeHierarchyResult` (atasan, rekan sejajar, fokus, dan bawahan).
3. **Langkah 2: Tulis Unit Test Domain Terlebih Dahulu**
   - Pastikan resolusi Head Divisi dan suksesi kepemimpinan lulus 100%, serta validasi integritas entitas divisi dinamis.
4. **Langkah 3: Rancang MVI State dengan Virtual Draft Node & Koleksi Divisi Dinamis (`app/shared`)**
   - Di `OrgChartUiState`, properti `draftNode` dibuat secara dinamis dari string input nama, email, dan pilihan divisi aktif.
   - Sediakan event penambahan divisi baru, reset data ke kosong (*blank slate*), dan pemulihan preset bawaan.
5. **Langkah 4: Bangun Komponen Visual Org Chart**
   - `OrgNodeCard`: Kartu avatar inisial dengan badge divisi dan glowing highlight.
   - `TShapeChartView`: Perender layout 3-layer dengan garis konektor vertikal.
6. **Langkah 5: Rakit Layar Split-View `OrgChartScreen` & Dialog Tambah Divisi**
   - Gabungkan Form di panel kiri (420dp) dengan horizontal scrollable chip divisi dan Live Chart di panel kanan.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Mengubah Enum Menjadi Dynamic Domain Entity (`Department.kt`)

```kotlin
@JvmInline
value class DepartmentId(val value: String) {
    init {
        require(value.isNotBlank()) { "DepartmentId cannot be blank" }
    }
}

data class Department(
    val id: DepartmentId,
    val code: String,
    val displayName: String,
    val shortName: String,
    val colorHex: Long,
    val isCustom: Boolean = false
) {
    companion object {
        // Konstanta preset untuk backwards compatibility kode domain & testing
        val SALES = Department(DepartmentId("dept-sales"), "sales", "Penjualan & CRM", "Sales", 0xFF2563EB)
        val PRODUCTION_PPIC = Department(DepartmentId("dept-ppic"), "production_ppic", "Produksi & PPIC", "Produksi", 0xFFEA580C)
        val WAREHOUSE = Department(DepartmentId("dept-warehouse"), "warehouse", "Gudang & Logistik", "Gudang", 0xFF0D9488)
        val QUALITY_CONTROL = Department(DepartmentId("dept-qc"), "qc", "Quality Control (QC)", "QC", 0xFF16A34A)
        val FINANCE_EXECUTIVE = Department(DepartmentId("dept-exec"), "finance_executive", "Keuangan & Direksi", "Direksi", 0xFF7C3AED)

        fun defaultPresets(): List<Department> = listOf(SALES, PRODUCTION_PPIC, WAREHOUSE, QUALITY_CONTROL, FINANCE_EXECUTIVE)

        fun createCustom(name: String, shortName: String, colorHex: Long): Department {
            val trimmedName = name.trim()
            val trimmedShortName = shortName.trim().ifBlank { trimmedName.take(8) }
            val slug = trimmedShortName.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-').ifBlank { "custom" }

            return Department(
                id = DepartmentId("dept-$slug-${(100..999).random()}"),
                code = slug,
                displayName = trimmedName,
                shortName = trimmedShortName,
                colorHex = colorHex,
                isCustom = true
            )
        }
        // Master Palette & Eksklusi Warna Terpakai
        fun availableColors(existingDepartments: List<Department>): List<DepartmentColor> {
            val usedHexes = existingDepartments.map { it.colorHex }.toSet()
            val unused = AVAILABLE_COLORS.filter { it.hex !in usedHexes }
            return if (unused.isNotEmpty()) unused else AVAILABLE_COLORS
        }
    }
}
```

**Mengapa ditulis seperti ini?**
- **Mental Model DDD**: Enum adalah tipe data tertutup (*closed set*). Menggunakan enum untuk data bisnis yang bervariasi antar-klien (seperti departemen organisasi) adalah anti-pattern. Mengubahnya menjadi `data class` memungkinkan sistem berkembang menjadi multi-tenant di mana setiap pabrik punya divisi kustom sendiri.
- **Zero Breaking Changes**: Dengan meletakkan konstanta `SALES`, `PRODUCTION_PPIC`, dll. di dalam `companion object`, seluruh kode lama dan pengujian unit tidak ada yang rusak saat enum diganti menjadi data class!
- **Dynamic Resource Exclusion (Eksklusi Sumber Daya Dinamis)**: Fungsi `availableColors()` secara murni memfilter palet warna dari hex yang sudah dipakai divisi aktif. Ini menjamin setiap divisi memiliki identitas visual yang unik tanpa tabrakan warna.

---

### Blok B: Algoritma Resolusi Hirarki T-Shape (`OrgNode.kt`)

```kotlin
fun resolveTShapeView(
    nodes: List<OrgNode>,
    focusNode: OrgNode,
    isDraft: Boolean = false,
    successionAction: HeadSuccessionAction = HeadSuccessionAction.DEMOTE_TO_STAFF
): TShapeHierarchyResult {
    // 1. Cari atasan langsung (1 level ke atas)
    val superior = if (focusNode.reportsToId != null) {
        nodes.find { it.id == focusNode.reportsToId }
    } else if (focusNode.level != HierarchyLevel.EXECUTIVE) {
        nodes.find { it.level == HierarchyLevel.EXECUTIVE }
    } else null

    return when (focusNode.level) {
        HierarchyLevel.HEAD_OF_DEPARTMENT -> {
            // Rekan sesama kepala divisi (horizontal)
            val peerHeads = nodes.filter {
                it.level == HierarchyLevel.HEAD_OF_DEPARTMENT && it.id != focusNode.id
            }
            // Seluruh staf bawahan di divisinya (full down)
            val subordinates = nodes.filter {
                it.department.id == focusNode.department.id && it.level == HierarchyLevel.STAFF_OPERATOR
            }
            TShapeHierarchyResult(
                superior = superior,
                peerHeads = peerHeads,
                focusNode = focusNode,
                subordinates = subordinates,
                peersInDepartment = emptyList(),
                isDraft = isDraft
            )
        }
        HierarchyLevel.STAFF_OPERATOR -> { ... }
        HierarchyLevel.EXECUTIVE -> { ... }
    }
}
```

**Mengapa ditulis seperti ini?**
- **Isolasi Domain Murni**: Fungsi ini tidak peduli apakah outputnya akan digambar di Android, Web Wasm, Desktop, atau diekspor ke PDF. Ia hanya memproses struktur data pohon menjadi data siap pakai untuk UI.
- **O(N) Complexity**: Algoritma ini hanya melakukan beberapa kali filter pada list karyawan yang ukurannya puluhan hingga ratusan orang, sehingga eksekusinya instan (< 1 milidetik) pada setiap keystroke form.

---

### Blok C: Fleksibilitas Starter Preset vs Mulai dari Kosong (`OrgChartViewModel.kt`)

```kotlin
is OrgChartUiEvent.ClearAllDataToEmpty -> {
    _uiState.update { state ->
        state.copy(
            employees = emptyList(),
            departments = emptyList(),
            selectedDepartment = null,
            selectedEmployeeId = null,
            isCreatingNew = true,
            nameInput = "",
            emailInput = "",
            phoneInput = "",
            roleTitleInput = "",
            isResetMenuOpen = false,
            toastMessage = "Struktur organisasi berhasil dikosongkan. Anda dapat mulai menyusun dari awal!"
        )
    }
}

is OrgChartUiEvent.RestoreDefaultPresets -> {
    val defaultDepts = Department.defaultPresets()
    val defaultEmployees = OrgNode.createSampleEmployees()
    _uiState.update { state ->
        state.copy(
            employees = defaultEmployees,
            departments = defaultDepts,
            selectedDepartment = Department.SALES,
            ...
        )
    }
}
```

**Mengapa blok ini krusial untuk dipahami Junior Dev?**
- **Prinsip Onboarding Ramah Pengguna**: Pengguna baru sering bingung jika disajikan layar yang benar-benar kosong (*empty canvas paralysis*). Memberikan preset default sebagai template awal membuat mereka langsung paham cara kerja aplikasi.
- **Kebebasan Kustomisasi Total**: Namun, pengguna yang pabriknya berbeda tidak boleh dipaksa menggunakan divisi default. Satu klik tombol "Mulai dari Kosong" mengosongkan kanvas tanpa merusak state machine.

---

### Blok D: Suksesi Kepala Divisi & Single Active Head Rule (`OrgChartViewModel.kt`)

```kotlin
// Ketika admin menunjuk Kepala Divisi baru di divisi yang sudah ada pejabatnya:
val existingHead = current.existingHeadOfSelectedDept
if (newNode.level == HierarchyLevel.HEAD_OF_DEPARTMENT && existingHead != null) {
    when (current.successionAction) {
        HeadSuccessionAction.DEMOTE_TO_STAFF -> {
            val demotedHead = existingHead.copy(
                level = HierarchyLevel.STAFF_OPERATOR,
                roleTitle = "Staf Senior ${newNode.department.shortName}",
                reportsToId = newNode.id
            )
            // Seluruh staf lama + pejabat lama otomatis melapor ke Kepala Divisi baru
            current.employees.map { emp ->
                when (emp.id) {
                    existingHead.id -> demotedHead
                    newNode.id -> newNode
                    else -> if (emp.reportsToId == existingHead.id) emp.copy(reportsToId = newNode.id) else emp
                }
            }
        }
        HeadSuccessionAction.DEACTIVATE -> {
            current.employees.filter { it.id != existingHead.id } ...
        }
    }
}
```

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan Desain | Alternatif Lain | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Dynamic `Department` Data Class** | Tetap menggunakan `enum class Department` statis | Pengguna konveksi nyata dapat membuat divisi kustom (*Bordir*, *Cutting*, dll.) dan dapat mengosongkan struktur dari awal. | Aplikasi kaku, tidak cocok untuk pabrik konveksi yang memiliki alur produksi non-standar. |
| **Horizontal Scrollable Chip Selector** | Fixed `Row` dengan `Modifier.weight(1f)` | Saat jumlah divisi bertambah (6-10 divisi), tombol tidak akan berdesakan atau gepeng. | Jika ada 8 divisi, tombol menjadi tipis dan teks terpotong tidak terbaca. |
| **Live Preview Pill di Modal Dialog** | Hanya input teks tanpa pratinjau badge | Memberikan *visual feedback* langsung bagi pembuat divisi tentang bagaimana label badge mereka akan tampak di kartu bagan. | Pengguna sering salah memilih warna atau label terlalu panjang sehingga merusak estetika kartu. |
| **T-Shape Contextual View** | Canvas Org Chart raksasa dengan fitur Pan/Zoom | Sangat ringan, fokus pada konteks yang relevan (atasan langsung & bawahan). | Pengguna tersesat saat zoom-out, performa drop pada perangkat mobile/tablet. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

### 1. Jebakan: "Menghapus Enum Tanpa Memikirkan Backwards Compatibility"
- **Kesalahan Fatal**: Menghapus `Department.SALES` secara ceroboh sehingga puluhan file test dan domain error secara massal.
- **Solusi Benar**: Gunakan `companion object` untuk menampung konstanta preset dengan nama identik (`val SALES = ...`). Dengan begitu, semua kode lama tetap berjalan tanpa perlu satu pun perubahan drastis!

### 2. Jebakan: "Crash NullPointerException Saat Divisi Dikosongkan"
- **Kesalahan Fatal**: Menganggap `selectedDepartment` pasti selalu ada, sehingga ketika list divisi kosong, aplikasi melempar `NoSuchElementException`.
- **Solusi Benar**: Gunakan fallback yang aman di UI State:
  `val activeDepartment: Department get() = selectedDepartment ?: departments.firstOrNull() ?: Department.SALES`
  dan tampilkan UI panduan empty state yang ramah jika divisi belum dibuat.

### 3. Jebakan: "Menaruh Rekan Sejajar di Bawah Focus Node"
- **Kesalahan Fatal**: Ketika node fokus adalah level **Staf**, menaruh rekan-rekan kerja satu divisinya di lapisan bawah dengan garis panah ke bawah.
- **Solusi Benar**: Jika level fokus adalah Staf, seluruh anggota tim harus dideretkan **SEJAJAR (horizontal side-by-side)** tepat di bawah Atasan Langsung.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Jalankan Unit Test Domain (`:core:jvmTest`)**:
   ```bash
   ./gradlew :core:jvmTest --rerun-tasks
   ```
   *Verifikasi*: Pastikan test `defaultPresets_containsStandardGarmentDepartments` dan `createCustom_generatesValidDepartmentWithSlugAndIsCustomFlag` lulus 100%.

2. **Uji Fitur di Browser WasmJS**:
   - Klik tombol **"+ Divisi Baru"** di header atau di samping chip divisi.
   - Ketik Nama: *"Bordir & Sablon Printing"*, Singkatan: *"Bordir"*, pilih warna Rose/Pink.
   - Klik **"Buat Divisi"**: Verifikasi badge "Bordir" langsung muncul dan otomatis terpilih di form input karyawan!
   - Buka menu **"Opsi Struktur"** ➔ Klik **"Mulai dari Kosong"**: Bagan dan daftar divisi seketika bersih, siap diisi dari awal.
   - Buka kembali **"Opsi Struktur"** ➔ Klik **"Muat Template Konveksi"**: 5 divisi dan staf contoh kembali termuat seketika.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Tambahkan fitur "Edit & Hapus Divisi" dengan konfirmasi jika masih ada karyawan yang terdaftar di divisi tersebut.
- [ ] **Tantangan 2**: Buat dialog konfirmasi (*Are you sure?*) sebelum user mengklik tombol "Mulai dari Kosong".
- [ ] **Tantangan 3**: Tambahkan fitur ekspor konfigurasi divisi ke file JSON agar dapat diimpor ke cabang konveksi lainnya.
