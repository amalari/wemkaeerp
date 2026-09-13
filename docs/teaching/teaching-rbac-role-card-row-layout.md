# 🎓 Modul Pembelajaran: Desain Layout Responsif Row-Based untuk Matriks RBAC Per Jabatan

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: UI/UX Layout Architecture, Neo-Brutalism Claymorphism, Compose Multiplatform Responsive Design, Information Density  
> **Prasyarat**: Pemahaman dasar tentang `LazyColumn`, `BoxWithConstraints`, `chunked()`, dan prinsip token design system WeMade ERP  
> **Referensi Task**: Refactor Tab "Per Jabatan" dari Multi-Column Kanban Card Menjadi List per Row

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada halaman RBAC (Hak Akses), pengguna memiliki tab **"Per Jabatan"** untuk melihat modul apa saja yang boleh diakses oleh setiap peran/jabatan (*Owner*, *Kepala Produksi*, *Kepala Penjualan*, *Sales Eksekutif*, dll.).

Sebelum perbaikan:
- Tampilan menggunakan model grid Kanban 4 kolom (`columns = 4`).
- Dalam lebar layar standar desktop, 4 kolom berarti setiap kolom hanya memiliki lebar sekitar 280px–320px.
- Sebuah jabatan seperti **Owner** atau **Kepala Produksi** memiliki hak akses ke **11 hingga 12 modul bisnis**.
- Karena kolomnya sempit, 12 modul tersebut harus ditumpuk **secara vertikal ke bawah satu per satu**.
- Ditambah judul, badge wewenang (`Akses Penuh`), dan badge scope (`Shared`), setiap kartu jabatan membentang ke bawah sepanjang **1.200px hingga 1.500px**!
- **Akibatnya**: Pengguna harus melakukan *infinite scroll* yang melelahkan hanya untuk membandingkan wewenang antar jabatan, dan layar terasa sangat berat serta tidak efisien secara ruang visual.

### Analogi Sederhana
Bayangkan lembar menu restoran:
- **Cara Lama (Vertikal Sempit)**: Buku saku kecil di mana 12 hidangan ditulis dalam 1 kolom panjang ke bawah. Untuk melihat 4 kategori, kamu harus membolak-balik halaman berkali-kali.
- **Cara Baru (Row-Based Dashboard)**: Menu lembaran lebar (A3). Setiap kategori atau set menu diletakkan dalam 1 baris mendatar penuh, dan 12 hidangan di dalamnya ditata dalam kotak 4 kolom x 3 baris yang ringkas. Kamu bisa melihat semua variasi dalam satu tatapan mata tanpa perlu terus menggulung halaman.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Untuk merombak layout komponen dari kolom sempit menjadi baris horizontal yang padat dan terstruktur:

```
Step 0: Analisis Hierarki & Kebutuhan Ruang (Card vs Row)
   ↓
Step 1: Ganti Container Utama menjadi LazyColumn 1 Kolom Penuh
   ↓
Step 2: Desain Header Baris Jabatan (Identitas, Badge, Action, & Collapse Toggle)
   ↓
Step 3: Rancang Grid Responsif Modul di Dalam Baris (Adaptive 4/3/2/1 Kolom)
   ↓
Step 4: Miniaturisasi Item Modul (Tile Kompak dengan Two-Line Badges)
   ↓
Step 5: Integrasi Fitur Pencarian Real-Time
```

1. **Langkah 0: Menentukan Arsitektur Kontainer (`fillMaxWidth`)**
   - Menghapus pembagian kolom Kanban luar (`chunked(4)` pada daftar jabatan) agar setiap jabatan memegang 1 baris penuh layar.

2. **Langkah 1: Header Horizontal Terintegrasi**
   - Di sisi kiri: Avatar warna divisi, nama jabatan, tag divisi, total modul, dan deskripsi singkat.
   - Di sisi kanan: Tombol aksi `+ Tambah Akses Modul` dan tombol `Sembunyikan / Buka Detail` untuk melipat/membuka kartu secara mandiri.

3. **Langkah 2: Grid Modul Internal yang Adaptif**
   - Di dalam kartu yang sudah lebar, kita menggunakan `BoxWithConstraints` untuk membagi daftar modul menjadi 4 kolom (di layar lebar), 3 kolom (layar sedang), atau 2/1 kolom (layar kecil).
   - 12 modul ditata rapi dalam 3 baris x 4 kolom!

4. **Langkah 3: Perampingan Ukuran Kartu Modul (`RoleModuleItemCard`)**
   - Memampatkan tinggi tile modul menjadi hanya ~50px dengan 2 baris teks yang informatif.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Transformasi Kontainer Utama (`RoleCardList`)
```kotlin
@Composable
fun RoleCardList(
    roles: List<CustomRole>,
    departments: List<Department>,
    assignments: Map<BusinessModule, List<DepartmentModuleAssignment>>,
    onOpenAssignModal: (CustomRole, Department?, DepartmentModuleAssignment?, BusinessModule?) -> Unit,
    onRemoveAssignment: (BusinessModule, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (roles.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada jabatan yang terdaftar atau sesuai pencarian.", color = WeMadeColors.OnSurfaceMuted)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            contentPadding = PaddingValues(bottom = ClaySpacing.Xxl)
        ) {
            items(roles, key = { it.id.value }) { role ->
                // Hitung wewenang jabatan & divisi
                val dept = remember(role, departments) { DynamicRbacViewModel.resolveDepartmentForRole(role, departments) }
                val accessibleModules = remember(role, dept, assignments) { resolveAccessibleModulesForRole(role, dept, assignments) }

                // Satu baris penuh untuk satu jabatan!
                RoleRowCard(
                    role = role,
                    department = dept,
                    accessibleModules = accessibleModules,
                    onAddModuleClick = { onOpenAssignModal(role, dept, null, null) },
                    onEditAssignmentClick = { mod, assignment -> onOpenAssignModal(role, dept, assignment, mod) },
                    onRemoveAssignmentClick = { mod, assignKey -> onRemoveAssignment(mod, assignKey) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
- `LazyColumn` langsung mengiterasi `roles` per item (bukan per *row of roles*). Ini memberikan performa daur ulang memori (*view recycling*) Compose yang optimal saat jabatan berjumlah banyak.
- Setiap baris langsung berukuran `fillMaxWidth()`, memanfaatkan seluruh bidang kanvas desktop.

---

### Blok B: Header Baris Jabatan & Expand Toggle (`RoleRowCard`)
```kotlin
var isExpanded by remember { mutableStateOf(true) }
val deptColor = department?.let { Color(it.colorHex) } ?: WeMadeColors.Primary

ClayCard(
    modifier = modifier.fillMaxWidth(),
    shape = ClayShapes.Card,
    containerColor = WeMadeColors.Surface,
    outlineColor = WeMadeColors.Outline,
    borderWidth = ClayBorder.Thick,
    contentPadding = PaddingValues(ClaySpacing.Lg)
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        // Header: Identitas di Kiri, Aksi di Kanan
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                // Avatar Divisi
                Box(
                    modifier = Modifier.size(38.dp).clayFlat(shape = CircleShape, background = deptColor.copy(alpha = 0.15f), outline = deptColor),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(deptColor))
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                        Text(role.name, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                        ClayTag(text = if (department != null) "Divisi: ${department.displayName}" else "Direksi / Lintas Divisi", tint = deptColor)
                        ClayTag(text = "${accessibleModules.size} Modul", tint = if (accessibleModules.isNotEmpty()) deptColor else WeMadeColors.OnSurfaceMuted)
                    }
                    if (role.description.isNotBlank()) {
                        Text(role.description, color = WeMadeColors.OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Tombol Aksi & Toggle Lipat
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                ClayButton(text = "+ Tambah Akses Modul", onClick = onAddModuleClick, style = ClayButtonStyle.Secondary)
                ClayActionSurface(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) "Sembunyikan" else "Buka Detail (${accessibleModules.size})", fontWeight = FontWeight.SemiBold)
                    if (isExpanded) IconChevronUp(...) else IconChevronDown(...)
                }
            }
        }
        ...
```
**Mengapa blok ini ditulis begini?**
- Pengguna diberikan kendali interaktif: secara default modul terbuka (*expanded*), namun jika admin hanya ingin membandingkan daftar nama jabatan tanpa terganggu daftar modul, cukup klik tombol `Sembunyikan` untuk mengompres kartu menjadi tinggi ~50px saja!

---

### Blok C: Grid Responsif 4 Kolom di Dalam Kartu Baris
```kotlin
if (isExpanded) {
    HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val moduleColumns = when {
            maxWidth >= 1200.dp -> 4
            maxWidth >= 850.dp -> 3
            maxWidth >= 550.dp -> 2
            else -> 1
        }

        val moduleRows = remember(accessibleModules, moduleColumns) {
            accessibleModules.chunked(moduleColumns)
        }

        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            moduleRows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            RoleModuleItemCard(item, onEdit, onRemove)
                        }
                    }
                    // Isi sisa slot kolom agar ukuran kartu tetap seimbang
                    val remaining = moduleColumns - rowItems.size
                    if (remaining > 0) {
                        repeat(remaining) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
- `chunked(moduleColumns)` membagi daftar modul menjadi baris-baris kecil yang seimbang.
- Di layar monitor desktop (lebar > 1200dp), 12 modul tertata dalam **3 baris** saja!
- Tinggi modul turun dari ~1.200px menjadi **~180px**, pengurangan tinggi sebesar **85%** tanpa kehilangan detail wewenang apa pun!

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Alasan Memilih Pendekatan Kita | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Row List + Internal Responsive Grid (4 Kolom)** | Kolom Kanban Sempit (4 Jabatan Bersebelahan) | Memanfaatkan lebar monitor desktop secara optimal. Mengurangi panjang vertikal secara drastis (*information density* tinggi). | Kartu menjadi terlalu tinggi (1500px+), pengguna harus scroll ke bawah berulang kali untuk tiap kolom. |
| **Expand / Collapse Card State** | Selalu Terbuka (*Static*) | Fleksibilitas navigasi: admin dapat melipat jabatan yang sudah selesai ditinjau untuk fokus pada jabatan yang sedang diedit. | Layar tetap panjang jika ada puluhan jabatan kustom yang dibuat tenant. |
| **Weighted Spacer Balancing pada Baris Terakhir** | Membiarkan item terakhir melebar memenuhi sisa baris | Menjaga konsistensi lebar kotak modul di seluruh grid (tidak ada modul yang tiba-tiba 3x lebih lebar dari yang lain). | Desain tampak patah dan tidak profesional jika jumlah modul ganjil. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Nested LazyColumn di dalam LazyColumn**
   - *Kenapa bahaya*: Menaruh `LazyColumn` modul di dalam `LazyColumn` jabatan akan menyebabkan *Crash: IllegalStateException (Vertically scrollable component was measured with an infinity maximum height constraints)* di Compose Multiplatform.
   - *Solusi kita*: Gunakan kombinasi `BoxWithConstraints`, `chunked(columns)`, dan `Row` biasa di dalam kartu.

2. **Jebakan 2: Lupa Menangani Sisa Slot di Baris Terakhir**
   - *Kenapa bahaya*: Jika sebuah jabatan punya 7 modul dan kita membaginya ke 4 kolom, baris kedua hanya punya 3 modul. Jika tidak ada `Spacer(Modifier.weight(1f))` penyeimbang, 3 modul tersebut akan melebar secara tidak wajar (*stretched*).
   - *Solusi kita*: Selalu hitung `val remaining = moduleColumns - rowItems.size` dan isi dengan `Spacer(Modifier.weight(1f))`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Kompilasi & Unit Test**:
   ```bash
   ./gradlew :app:shared:jvmTest
   ./gradlew :app:webApp:wasmJsBrowserDevelopmentWebpack --no-configuration-cache
   ```
2. **Pengujian Visual di Browser**:
   - Buka `http://localhost:3000/rbac`.
   - Pilih tab **Per Jabatan**.
   - Amati bahwa setiap jabatan kini tampil sebagai kartu horizontal penuh.
   - Amati 12 modul tertata dalam 4 kolom x 3 baris yang rapi dan ringkas.
   - Klik tombol **Sembunyikan**: kartu langsung terlipat menjadi 1 baris ramping. Klik **Buka Detail**: kartu kembali terbuka.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Cobalah perkecil ukuran jendela browser ke ukuran tablet (lebar sekitar 800px). Amati apakah grid modul otomatis menyesuaikan diri menjadi 3 kolom atau 2 kolom tanpa ada elemen yang terpotong?
- [ ] **Tantangan 2**: Ketik nama jabatan di search bar (misal: "Sales"). Apakah filter pencarian langsung menyaring baris jabatan yang relevan secara instan?
