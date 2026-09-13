# 🎓 Modul Pembelajaran: Stabilitas Urutan Posisi Kartu pada T-Shape Org Chart (In-Place Selection vs Draft Appending)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Layout Stability, UI Consistency, Compose Multiplatform, State Modeling  
> **Prasyarat**: Kotlin Multiplatform, Compose UI Basics, Pemahaman Model Hierarki Organisasi  
> **Referensi Task**: Fix Org Chart Card Jumping on Selection (`teaching-org-chart-stable-card-order`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada modul **Bagan Struktur Organisasi & Karyawan (T-Shape Dynamic Org)** di WeMade ERP, seorang HR atau Manajer Pabrik melihat barisan kartu rekan kerja sejajar (misalnya divisi *Sales & Marketing* beranggotakan 6 staf: Rian, Dedi, Maya, Dimas 1, Dimas 2, Dimas 3).

Ketika user sedang **menambah karyawan baru** (`isCreatingNew = true`), sistem menampilkan kartu draf `[POSISI BARU DITAMBAHKAN]` di ujung paling kanan barisan. Ini perilaku yang benar dan intuitif karena karyawan baru memang ditambahkan di akhir urutan (*appended*).

Namun, ada *bug layout* yang mengganggu ketika user **mengklik salah satu karyawan yang sudah ada** untuk melihat atau mengedit profilnya (misalnya mengklik kartu kedua, "Dedi Kurniawan"):
- Kartu Dedi tiba-tiba **teleportasi/pindah ke ujung paling kanan**.
- Seluruh kartu lain (Maya, Dimas) bergeser ke kiri.
- Jika user kemudian mengklik kartu pertama ("Rian Firmansyah"), Rian pun tiba-tiba melompat ke ujung paling kanan!

Perpindahan tempat yang liar ini membingungkan user (*disorienting*): *"Kenapa orang yang saya klik malah lari ke kanan? Bukankah posisi mereka harusnya tetap di tempatnya?"*

### Mental Model & Solusi
1. **Draf Karyawan Baru (`isDraft == true`)**: Merupakan entitas sementara yang belum tersimpan. Tempat alaminya adalah di akhir barisan (paling kanan) agar jelas membedakan antara karyawan aktif dengan posisi yang sedang dirancang.
2. **Karyawan yang Sudah Ada (`isDraft == false`)**: Sudah memiliki urutan kanonikal di dalam data divisi/perusahaan. Ketika diklik, kartu tersebut **wajib tetap berada di posisi aslinya**, hanya status visualnya yang berubah menjadi tersorot (`isHighlighted = true` dengan badge `"POSISI FOKUS / DIEDIT"`). Tidak boleh ada kartu yang bertukar posisi atau melompat ke ujung kanan.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika seorang developer ingin membangun solusi ini dengan arsitektur yang bersih:

1. **Langkah 1: Identifikasi Kontrak Domain (`core/domain/orgchart`)**
   - Periksa `TShapeHierarchyResult`. Objek ini adalah *read model* hasil kalkulasi murni Kotlin.
   - Sebelumnya, `peersInDepartment` hanya berisi rekan-rekan selain `focusNode`. Ketika layer UI merender `peersInDepartment` lalu menempelkan `focusNode` di akhir, `focusNode` otomatis terlempar ke kanan.
   - Tambahkan field `orderedDepartmentMembers: List<OrgNode>` ke `TShapeHierarchyResult`.

2. **Langkah 2: Perhitungan Urutan di Algoritma Domain (`OrgNode.resolveTShapeView`)**
   - Ambil seluruh anggota departemen pada tingkat yang sama (`allStaffInDept`).
   - Jika `isDraft == true`: `orderedMembers = allStaffInDept + focusNode` (draf di paling kanan).
   - Jika `isDraft == false`: `orderedMembers = allStaffInDept.map { if (it.id == focusNode.id) focusNode else it }` (pertahankan indeks asli dan injeksikan state fokus/edit).

3. **Langkah 3: Sinkronisasi Server & Serialization (`server`)**
   - Perbarui `restrictToReach` di `OrgChartAccessGuard.kt` agar `orderedDepartmentMembers` juga terfilter berdasarkan RBAC jangkauan data.
   - Perbarui `EmployeeDto.toTShapeJson` bila API RESTful perlu mengirimkan urutan ini.

4. **Langkah 4: Sinkronisasi State Presentasi (`app/shared/.../OrgChartUiState.kt`)**
   - Pastikan ketika mengedit karyawan yang ada, `focusNode` membawa identitas asli (`existing.id`) dan perubahan input form secara reaktif.

5. **Langkah 5: Rendering Komponen Compose (`app/shared/.../TShapeChartView.kt`)**
   - Ubah iterasi kartu di `Row` horizontal: loop seluruh `members` dari `orderedDepartmentMembers`.
   - Cek `member.id == focusNode.id`: jika ya, render sebagai kartu fokus (`isHighlighted = true`), jika tidak, render sebagai kartu biasa dengan `onClick`.

6. **Langkah 6: Verifikasi Unit Test Murni (`core/src/commonTest/.../OrgHierarchyTest.kt`)**
   - Uji pemilihan karyawan di tengah list dan pastikan indeksnya tidak bergeser.
   - Uji pembuatan draf dan pastikan berada di indeks terakhir.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Domain Layer — Kontrak & Algoritma Penentuan Urutan

File: `core/src/commonMain/kotlin/com/eventverse/app/domain/orgchart/OrgNode.kt`

```kotlin
// 1. Tambahkan properti orderedDepartmentMembers dengan default emptyList() agar tidak memecah pemanggil lama
data class TShapeHierarchyResult(
    val superior: OrgNode?,
    val peerHeads: List<OrgNode>,
    val focusNode: OrgNode,
    val subordinates: List<OrgNode>,
    val peersInDepartment: List<OrgNode>,
    val isDraft: Boolean = false,
    val orderedDepartmentMembers: List<OrgNode> = emptyList()
)
```

**Mengapa `orderedDepartmentMembers` ditambahkan sebagai field baru, bukan mengubah `peersInDepartment`?**
- Secara definisi domain, *peers* (rekan kerja sejajar) berarti orang lain di luar diri sendiri (`it.id != focusNode.id`). Unit test domain dan filter otorisasi RBAC mengandalkan definisi ini (misal: "Rian punya 2 peers yaitu Dedi dan Maya").
- `orderedDepartmentMembers` adalah proyeksi visual representatif yang membawa **seluruh** anggota sejajar termasuk node fokus dalam urutan kanonikal.

Berikut algoritma pembentukannya di `OrgNode.resolveTShapeView`:

```kotlin
HierarchyLevel.STAFF_OPERATOR -> {
    val allStaffInDept = nodes.filter {
        it.department != null &&
        it.department == focusNode.department &&
        it.level == HierarchyLevel.STAFF_OPERATOR
    }
    val peersInDept = allStaffInDept.filter { it.id != focusNode.id }
    
    // KUNCI STABILITAS POSISI:
    val orderedMembers = if (isDraft) {
        // Kasus 1: Draf baru -> tempel di ujung kanan
        allStaffInDept + focusNode
    } else {
        // Kasus 2: Karyawan eksisting -> ganti elemen di slot aslinya tanpa mengubah urutan indeks
        if (allStaffInDept.any { it.id == focusNode.id }) {
            allStaffInDept.map { if (it.id == focusNode.id) focusNode else it }
        } else {
            allStaffInDept + focusNode
        }
    }
    
    TShapeHierarchyResult(
        superior = superior,
        peerHeads = emptyList(),
        focusNode = focusNode,
        subordinates = emptyList(),
        peersInDepartment = peersInDept,
        isDraft = isDraft,
        orderedDepartmentMembers = orderedMembers
    )
}
```

---

### Blok B: Presentation State — Penggabungan Input Form ke Node Fokus

File: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartUiState.kt`

```kotlin
val resolvedHierarchy: TShapeHierarchyResult
    get() {
        val focus = if (isCreatingNew) {
            draftNode
        } else {
            val existing = employees.find { it.id.value == selectedEmployeeId }
            if (existing != null) {
                // Pertahankan ID asli karyawan dan merge perubahan live dari input text form
                existing.copy(
                    name = nameInput.ifBlank { existing.name },
                    email = emailInput.ifBlank { existing.email },
                    phone = phoneInput,
                    department = if (selectedLevel == HierarchyLevel.EXECUTIVE) null else (selectedDepartment ?: existing.department),
                    level = selectedLevel,
                    tierName = selectedTierName ?: existing.tierName,
                    roleTitle = roleTitleInput.ifBlank { selectedTierName ?: selectedLevel.displayName },
                    reportsToId = selectedReportsToId?.let { OrgNodeId(it) }
                )
            } else {
                draftNode
            }
        }
        return OrgNode.resolveTShapeView(
            nodes = employees,
            focusNode = focus,
            isDraft = isCreatingNew,
            successionAction = successionAction
        )
    }
```

**Mengapa ini krusial?**
Jika `focus` hanya mengambil `employees.find`, maka ketika user mengetik nama baru di form sebelum menekan tombol "Simpan", nama di kartu bagan tidak ikut ter-update. Sebaliknya, dengan merge live ini, kartu di posisinya langsung merespons apa yang diketik user tanpa berpindah tempat!

---

### Blok C: UI Rendering — Menjaga Posisi Kartu di Compose Multiplatform

File: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/components/TShapeChartView.kt`

```kotlin
Row(
    modifier = Modifier
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xl),
    verticalAlignment = Alignment.CenterVertically
) {
    val members = if (result.orderedDepartmentMembers.isNotEmpty()) {
        result.orderedDepartmentMembers
    } else {
        result.peersInDepartment + result.focusNode
    }

    members.forEach { member ->
        if (member.id == result.focusNode.id) {
            // Posisi fokus (karyawan yang diklik atau draf baru)
            OrgNodeCard(
                node = result.focusNode,
                isHighlighted = true,
                badgeLabel = if (result.isDraft) "POSISI BARU DITAMBAHKAN" else "POSISI FOKUS / DIEDIT"
            )
        } else {
            // Rekan kerja sejajar di slot posisi aslinya
            OrgNodeCard(
                node = member,
                onClick = { onSelectNode(member.id.value) }
            )
        }
    }
}
```

**Sebelumnya (Kode Lama Bermasalah):**
```kotlin
// ❌ SALAH: Selalu me-loop peers dulu, baru menaruh focusNode di paling kanan
result.peersInDepartment.forEach { peer ->
    OrgNodeCard(node = peer, onClick = { onSelectNode(peer.id.value) })
}
OrgNodeCard(
    node = result.focusNode,
    isHighlighted = true,
    ...
)
```
Di kode lama, siapa pun yang menjadi `focusNode` dicabut dari `peersInDepartment` lalu dipasang di paling akhir baris. Akibatnya kartu yang diklik selalu terlempar ke kanan.

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Memodifikasi `peersInDepartment` Langsung Menjadi Berisi `focusNode`**:
   - *Jebakan*: Menghapus filter `it.id != focusNode.id` di `peersInDept`.
   - *Akibat*: Unit test yang memeriksa `peersInDepartment` akan gagal, dan penghitungan jumlah rekan kerja sejajar di header teks (`${peers.size} Orang`) menjadi salah hitung (menghitung diri sendiri sebagai rekan sejajar).
2. **Hardcode Index di UI**:
   - *Jebakan*: Mencoba mengurutkan atau menyisipkan node di level Composable UI dengan logika ad-hoc.
   - *Akibat*: Melanggar prinsip DDD. Logika urutan struktur organisasi adalah tanggung jawab domain (`OrgNode`), bukan layer presentasi Compose.
3. **Lupa Menangani Draf Baru**:
   - *Jebakan*: Hanya memperhatikan karyawan eksisting sehingga saat draf baru dibuat, draf tersebut tidak muncul atau menggantikan karyawan lain.
   - *Solusi*: Percabangan eksplisit `if (isDraft) allStaff + focusNode else allStaff.map { ... }`.

---

## 🧪 5. Verifikasi & Tantangan Mandiri

### Verifikasi Otomatis (Unit Test)
Jalankan tes domain murni:
```bash
./gradlew :core:jvmTest
```
Test `existing_staff_selection_should_keep_original_card_position_in_orderedDepartmentMembers` memastikan:
1. Memilih Dedi Kurniawan (indeks 1) menghasilkan `[Rian, Dedi, Maya]` — Dedi tetap di indeks 1.
2. Memilih Maya (indeks 2) menghasilkan `[Rian, Dedi, Maya]` — Maya tetap di indeks 2.
3. Memilih Rian (indeks 0) menghasilkan `[Rian, Dedi, Maya]` — Rian tetap di indeks 0.
4. Membuat draf baru menghasilkan `[Rian, Dedi, Maya, draftNode]` — draf tepat di indeks 3 (paling kanan).

### Verifikasi Manual di Browser / Desktop
1. Buka halaman `/org-chart`.
2. Klik tombol **"+ Tambah Karyawan"**: Pastikan kartu ber-badge biru `[POSISI BARU DITAMBAHKAN]` muncul di ujung kanan barisan.
3. Klik kartu karyawan di tengah barisan (misal: "Dedi Kurniawan"):
   - Kartu Dedi **wajib tetap berada di posisi tengah**.
   - Kartu Dedi berubah menjadi tersorot dengan badge `[POSISI FOKUS / DIEDIT]`.
   - Tidak ada kartu lain yang bergeser tempat.
4. Klik kartu paling kiri ("Rian Firmansyah"):
   - Kartu Rian tetap di posisi paling kiri dan tersorot.
   - Dedi kembali menjadi kartu biasa tanpa berpindah tempat.
