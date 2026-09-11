# 🎓 Modul Pembelajaran: Multi-Perspective RBAC (Per Modul, Per Divisi & Per Jabatan)

> **Level Target**: Junior to Mid Compose Multiplatform Developer  
> **Topik Utama**: Domain-Driven Design, Multi-Perspective RBAC, Compose Multiplatform Layouting, Claymorphism Design System  
> **Prasyarat**: Dasar Arsitektur DDD, Kotlin Multiplatform, MVI Pattern (`UiState`, `UiEvent`), dan Desain Token WeMade  
> **Referensi Task**: Penghapusan Matriks Jabatan dan Penambahan Tab Per Divisi & Per Jabatan dengan Data Divisi

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Dalam sistem ERP konveksi garmen, izin akses (*Role-Based Access Control* / RBAC) biasanya sangat rumit. Developer pemula sering kali membuat **Matriks Akses berbentuk Spreadsheet / Checkbox Raksasa** (Matriks Jabatan) di mana satu layar dipenuhi baris modul dan kolom checklist wewenang.

Kelemahan pendekatan matriks teknis jadul tersebut:
1. **Tidak Intuitif bagi Pemilik & Manajer Pabrik**: Manajer HR atau Kepala Pabrik berpikir dalam sudut pandang organisasi riil: *"Divisi Penjualan punya wewenang apa saja?"* atau *"Mandor Sablon ini di divisi mana dan bisa buka modul apa saja?"*. Mereka tidak ingin mencentang 40 kotak per izin.
2. **Ketiadaan Konteks Divisi pada Jabatan**: Sering kali nama jabatan mirip atau spesifik (misal: "Staff Administrasi", "Kepala Bagian", "Operator"). Tanpa penanda divisi yang jelas, admin sering keliru memberikan izin modul rahasia akuntansi ke staf administrasi gudang.
3. **Alur Satu Arah (*One-Way Workflow Trap*)**: Jika sistem hanya mengizinkan penambahan hak akses dari layar modul, admin yang sedang mengonfigurasi divisi penjualan harus melompat ke 5 layar modul berbeda hanya untuk memberikan akses ke divisi tersebut.

### Analogi Sederhana
Bayangkan sebuah denah gedung pabrik konveksi:
- **Sudut Pandang Modul (Ruangan)**: Kita berdiri di depan *Gudang Kain* dan melihat siapa saja yang memegang kunci pintu gudang (Divisi Gudang & Kepala PPIC).
- **Sudut Pandang Divisi (Departemen)**: Kita mendatangi meja *Divisi Penjualan* dan melihat kartu akses apa saja yang tergantung di leher mereka (Akses CRM, Sampling, dan Status Pengiriman). Jika butuh akses baru, kita langsung serahkan kunci dari meja itu.
- **Sudut Pandang Jabatan (Individu/Peran)**: Kita melihat tanda pengenal seorang *Sales Eksekutif* yang dengan jelas tertulis **Divisi: Penjualan & CRM**, lalu mengecek wewenang pribadinya apakah setingkat staf atau supervisor.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika seorang developer ingin membangun antarmuka multi-perspektif ini dari layar kosong, berikut urutan langkah yang benar:

1. **Langkah 0: Single Source of Truth pada Model Data Domain (`core`)**
   - Jangan membuat model data ganda untuk divisi vs jabatan!
   - Jadikan `moduleAssignments: Map<BusinessModule, List<DepartmentModuleAssignment>>` sebagai satu-satunya sumber kebenaran (*single source of truth*).
   - Tambahkan informasi divisi induk ke `CustomRole` (`val departmentId: String? = null`).

2. **Langkah 1: Perluas `RbacViewMode` pada Presentation State (`DynamicRbacUiState.kt`)**
   - Ubah mode tampilan menjadi 3 varian: `PER_MODULE`, `PER_DEPARTMENT`, dan `PER_ROLE`.
   - Definisikan state dialog penugasan dinamis dari arah divisi/jabatan (`isAssignModuleModalOpen`).

3. **Langkah 2: Buat Helper Resolusi Divisi (`resolveDepartmentForRole`)**
   - Buat logika pemetaan yang kokoh di ViewModel untuk menghubungkan jabatan ke divisinya, baik via `departmentId` eksplisit maupun fallback nama peran.

4. **Langkah 3: Bangun Komponen Kartu Divisi (`DepartmentCardView.kt`)**
   - Buat grid responsif.
   - Filter `moduleAssignments` berdasarkan `department.id.value`.
   - Sediakan tombol `+ Tambahkan Akses Modul` di setiap kartu divisi.

5. **Langkah 4: Bangun Komponen Kartu Jabatan (`RoleCardView.kt`)**
   - Buat grid kartu jabatan.
   - Tampilkan badge divisi dengan warna divisi secara mencolok di header kartu.
   - Pisahkan hak akses yang berasal dari divisi induk (*Full Divisi*) dengan penugasan wewenang khusus (*Khusus Jabatan Ini*).

6. **Langkah 5: Bangun Modal Penugasan Universal (`AssignModuleModal.kt`)**
   - Buat modal dua arah: memilih modul, menentukan cakupan jabatan, menentukan Level Akses (1 row penuh), dan Jangkauan Data (1 row penuh di bawahnya).

7. **Langkah 6: Komposisi Root & Verifikasi Visual di Browser**
   - Satukan semua kartu di `DynamicRbacScreen.kt` dengan tab switcher.
   - Jalankan di browser untuk memverifikasi tata letak, responsivitas, dan konsistensi token Clay.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pengaitan Divisi pada Entitas Peran (`CustomRole.kt`)

```kotlin
data class CustomRole(
    val id: RoleId,
    val tenantId: TenantId?,
    val name: String,
    val description: String,
    val isSystemDefault: Boolean = false,
    val modulePermissions: Map<BusinessModule, ModuleAccessConfig> = emptyMap(),
    val userCount: Int = 0,
    val departmentId: String? = null // Penanda divisi induk eksplisit
)
```

**Mengapa blok ini ditulis begini?**
- Nilai default `= null` menjaga kompatibilitas ke belakang (*backward compatibility*) sehingga tidak merusak konstruktor yang sudah ada.
- Memungkinkan peran seperti *Owner / Direktur* memiliki `departmentId = null` (karena bersifat eksekutif / lintas divisi), sedangkan peran operasional seperti *Kepala PPIC* terikat kuat ke `dept-ppic`.

---

### Blok B: Pemetaan Hak Akses Divisi secara Proaktif (`DepartmentCardView.kt`)

```kotlin
val deptAssignments = remember(assignments, dept) {
    assignments.flatMap { (module, assignList) ->
        assignList.filter { it.departmentId == dept.id.value }
            .map { assignment -> module to assignment }
    }
}
```

**Mengapa blok ini ditulis begini?**
- Daripada menduplikasi penyimpanan data, kita cukup melakukan proyeksi (*projection / inverse lookup*) secara instan dari `assignments` global.
- Jika pengguna menambah, mengedit, atau menghapus akses dari Tab Modul, Tab Divisi otomatis terbarui tanpa perlu sinkronisasi manual (*zero sync bug*).

---

### Blok C: Penanda Visual Divisi pada Kartu Jabatan (`RoleCardView.kt`)

```kotlin
Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(deptColor)
    )
    Text(
        text = if (department != null) "Divisi: ${department.displayName}" else "Direksi / Lintas Divisi",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = deptColor
    )
}
```

**Mengapa blok ini ditulis begini?**
- Menjawab kebutuhan spesifik user: *"terus di jabatan ini juga kasih data Divisi nya yah misal jabatan A divisi apa"*.
- Menggunakan `deptColor` (warna khas divisi pabrik) untuk menciptakan asosiasi visual instan bagi manajer pabrik.

---

### Blok D: Modal Penugasan Modul dari Kartu (`AssignModuleModal.kt`)

```kotlin
// 3. Level Akses (Full 1 Row)
Column(modifier = Modifier.fillMaxWidth()) {
    Text(text = "Level Akses", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.fillMaxWidth().clayFlat(...)) {
        listOf(AccessLevel.VIEW, AccessLevel.OPERATE, AccessLevel.MANAGE).forEach { level ->
            Box(modifier = Modifier.weight(1f)...) { ... }
        }
    }
}

Spacer(modifier = Modifier.height(10.dp))

// 4. Jangkauan Data (Full 1 Row di bawah Level Akses)
Column(modifier = Modifier.fillMaxWidth()) {
    Text(text = "Jangkauan Data", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    Row(modifier = Modifier.fillMaxWidth().clayFlat(...)) {
        selectedModule.supportedScopes.forEach { scope ->
            Box(modifier = Modifier.weight(1f)...) { ... }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- Menerapkan pelajaran dari perbaikan sebelumnya: Level Akses dan Jangkauan Data wajib memiliki baris penuh masing-masing agar teks chip seperti *"Data Bawahan"* tidak melipat (*no awkward line-breaking*).

### Blok E: Sticky Bottom Footer & Spacious Item Layout

```kotlin
// 1. Footer Button selalu di dasar kartu:
ClayCard(modifier = modifier.fillMaxWidth().fillMaxHeight(), ...) {
    // Konten kartu...
    
    // Spacer weight(1f) menyerap sisa tinggi vertikal:
    Spacer(modifier = Modifier.weight(1f))
    
    // Footer tombol konsisten di dasar:
    ClayButton(
        text = "+ Tambahkan Akses Modul",
        style = ClayButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth()
    )
}

// 2. Tata letak 3-baris lapang untuk item modul:
Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
    // Baris 1: Nama Modul (full width)
    Text(text = module.displayName, fontWeight = FontWeight.Bold)
    
    // Baris 2: Subtitle konteks
    Text(text = roleScopeText, color = WeMadeColors.PrimaryDark)
    
    // Baris 3: Bottom Bar (Tags di kiri, Action Buttons di kanan)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ClayTag(text = accessLevel.displayName, tint = levelColor)
            ClayTag(text = scope.shortLabel, tint = WeMadeColors.Primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ClayActionSurface(onClick = onEdit) { ... }
            ClayActionSurface(onClick = onRemove) { ... }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- `Spacer(modifier = Modifier.weight(1f))` di dalam kontainer ber-`fillMaxHeight()` menjamin bahwa kartu dengan 2 modul maupun 9 modul akan memiliki tombol footer yang sejajar sempurna di dasar kartu.
- Menempatkan tags di ujung kiri dan tombol aksi di ujung kanan baris bawah menghilangkan masalah "terlalu dempet" (tumpukan 4 badge dalam 2x2 grid) dan memberikan jeda pandang yang sangat nyaman bagi mata pengguna.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan / Keputusan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Multi-Perspective Projection (Satu Data, 3 Cara Pandang)** | Membuat 3 tabel / state terpisah untuk modul, divisi, dan peran | Data selalu 100% konsisten secara atomik tanpa risiko desinkronisasi | Inkonsistensi data: izin dihapus di tab divisi tapi masih muncul di tab modul |
| **Bilah Aksi Terpisah (Tags Kiri, Aksi Kanan)** | Menumpuk semua badge & tombol di pojok kanan | Visual bernapas lega, tidak ada tumpukan badge yang dempet | Tombol aksi sulit ditekan (*fat finger error*) dan tampak berantakan |
| **Sticky Bottom Footer (`weight(1f)`)** | Tinggi kartu dinamis (*wrapContentHeight*) | Baris kartu memiliki tombol yang rata dan seragam (*orderly rhythm*) | Tombol naik-turun tak beraturan mengikuti jumlah item modul |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Asumsi Bahwa Semua Peran Pasti Memiliki Divisi**
   - *Kenapa bahaya*: Peran seperti *Owner / Direktur* atau *Super Admin* berada di level eksekutif yang membawahi seluruh divisi. Jika kode mewajibkan `departmentId != null`, sistem akan *crash* dengan `NullPointerException`.
   - *Solusi elegan kita*: Berikan tipe *nullable* (`String?`) dan fallback visual yang elegan seperti `"Direksi / Lintas Divisi"`.

2. **Jebakan 2: Lupa Membedakan Izin Turunan (*Inherited*) vs Izin Khusus (*Override*)**
   - *Kenapa bahaya*: Jika staf bertanya *"Kenapa saya punya akses modul sampling?"*, manajer harus tahu apakah itu karena seluruh divisi penjualan memang berhak, atau karena staf tersebut diberi izin istimewa.
   - *Solusi elegan kita*: Berikan tag asal-usul yang jelas pada kartu jabatan (`Dari Penjualan & CRM (Full Divisi)` vs `Khusus Jabatan Ini`).

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Pengujian Reaktivitas Lintas Tab**:
   - Buka rute `/rbac` di browser dev.
   - Buka tab **Per Divisi**, pilih kartu *Gudang & Logistik*, klik `+ Tambahkan Akses Modul`.
   - Pilih modul baru (misal: *Spesifikasi BOM & Tech Pack*), pilih level *Hanya Lihat*, lalu simpan.
   - Pindah ke tab **Per Modul**, cek kartu *Spesifikasi BOM & Tech Pack*. Divisi Gudang harus langsung tercantum di kartu modul tersebut!
   - Pindah ke tab **Per Jabatan**, cek kartu *Staff Gudang & Logistik*. Modul tersebut harus langsung tercantum dengan label wewenang yang sesuai!

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Tambahkan bilah pencarian instan (*Search Bar*) di Tab Per Jabatan sehingga ketika admin mengetik nama jabatan (misal: "Jahit"), kartu langsung terfilter secara *real-time*.
- [ ] **Tantangan 2**: Coba tambahkan penanda jumlah karyawan per kartu divisi (dijumlahkan dari total karyawan yang ada di seluruh jabatan divisi tersebut).
