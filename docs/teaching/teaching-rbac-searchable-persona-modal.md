# Mentoring Teknis: Transformasi Dropdown Sempit Menjadi Searchable Clay Modal untuk Persona Pengujian RBAC

> **Level**: Intermediate — Senior Lead Developer to Junior Developer  
> **Konteks**: Arsitektur Hybrid Kotlin Multiplatform (Compose UI) & DDD WeMade ERP  
> **Modul**: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/navigation/PersonaSwitcherDropdown.kt` & `ClayIcons.kt`  
> **Topik**: UX Refactoring, Compose Dialog vs DropdownMenu, Real-time List Filtering, Claymorphism Design System

---

## Ringkasan Eksekutif

Pada pengujian wewenang (RBAC), penguji atau admin pabrik ingin menjawab satu pertanyaan kunci:
*"Bagaimana rupa aplikasi dan menu navigasi jika saya masuk sebagai posisi X?"*

Sebelumnya, fitur ini berada di dalam `DropdownMenu` Material bawaan dengan lebar sempit (`380dp`) yang menempel di bawah tombol header. Terdapat dua kelemahan fatal pada UX lama:
1. **Dropdown sempit dan terpotong**: Konten panjang terdesak secara vertikal dan horizontal.
2. **Pemisahan Divisi & Jabatan yang Berlebihan**: UI menampilkan daftar seluruh tombol divisi dan daftar seluruh tombol jabatan secara terpisah. Pengguna terpaksa harus memilih divisi dulu, lalu mencari jabatan, dan mengetik nama persona sebelum tombol simpan aktif.
3. **Tidak ada fitur pencarian (Non-searchable)**: Pada pabrik skala menengah dengan puluhan divisi dan jabatan, mencari peran tertentu menjadi sangat melelahkan.

Solusi yang kita bangun:
- Mengganti `DropdownMenu` menjadi modal dialog berukuran luas (`widthIn(min = 600.dp, max = 740.dp)`).
- Menyatukan data jabatan dan divisi dengan format jelas: **`{Nama Jabatan} - {Nama Divisi}`** (contoh: *Sales Eksekutif - Penjualan & CRM*, *Kepala Produksi (PPIC) - Produksi & PPIC*, *Owner / Direktur Pabrik - Lintas Divisi*).
- Menambahkan **pencarian instan real-time** dengan ikon Canvas vector `IconSearch`.
- **1-Click Apply**: Pengguna cukup mengklik kartu peran untuk langsung menerapkan persona simulasi secara instan.

---

## 1. Start dari Mana? (Order of Operations)

Jika kamu diminta merombak UI interaktif seperti ini dari nol, ikuti urutan kerja disiplin berikut:

```
[1. Siapkan Aset Desain (IconSearch di ClayIcons)]
                    ↓
[2. Rancang Domain / Resolusi Relasi (Role ke Department)]
                    ↓
[3. Bangun Komponen Modal (Dialog + ClayCard Container)]
                    ↓
[4. Implementasikan State & Filter Pipeline (Search & Tabs)]
                    ↓
[5. Wire-up Aksi 1-Click Apply & Validasi Aksesibilitas]
```

1. **Langkah 1: Siapkan Design System Token / Aset Vektor.**  
   Sebelum menyusun kolom pencarian, kita butuh ikon search yang konsisten dengan tema Neo-Brutalist Claymorphism. Kita buat `IconSearch` di `ClayIcons.kt` menggunakan Canvas murni (bukan emoji font atau image raster).
2. **Langkah 2: Pahami Relasi Data (Role ke Department).**  
   `CustomRole` memiliki `departmentId: String?`. Namun ada peran lintas divisi (seperti Direktur) atau role yang divisinya ditentukan lewat pencocokan semantik. Kita manfaatkan `DynamicRbacViewModel.resolveDepartmentForRole(role, departments)` untuk mendapatkan nama divisi yang akurat.
3. **Langkah 3: Bangun Kontainer Modal Besar & Bersih.**  
   Alih-alih `DropdownMenu` yang anchoring-nya sempit, gunakan `androidx.compose.ui.window.Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` yang membungkus `ClayCard` dengan dimensi lapang (`widthIn(min = 900.dp, max = 1200.dp)`, `fillMaxWidth(0.88f)`, `fillMaxHeight(0.88f)`), tanpa tombol tutup yang redundan dan tanpa ikon dekoratif agar fokus murni pada tipografi dan kejelasan informasi.
4. **Langkah 4: Logika Filtering & Tab Responsif.**  
   Gunakan `remember(roles, departments, searchQuery)` untuk memfilter data secara reaktif dan hemat alokasi memori.
5. **Langkah 5: Interaksi Sekali Klik (1-Click Apply).**  
   Hilangkan tombol simpan terpisah yang mewajibkan input manual. Klik pada kartu peran langsung membungkus data ke `TestingPersona.custom` dan menutup modal.

---

## 2. Bedah Kode Blok per Blok

### A. Canvas Vector Icon: `IconSearch` (`ClayIcons.kt`)

```kotlin
@Composable
fun IconSearch(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density
        val radius = w * 0.30f
        val center = Offset(w * 0.42f, h * 0.42f)

        // 1. Lingkaran lensa kaca pembesar
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )

        // 2. Gagang kaca pembesar pada sudut 45 derajat
        val handleStart = Offset(
            x = center.x + radius * 0.7071f,
            y = center.y + radius * 0.7071f
        )
        drawLine(
            color = color,
            start = handleStart,
            end = Offset(w * 0.88f, h * 0.88f),
            strokeWidth = stroke * 1.2f,
            cap = StrokeCap.Round
        )
    }
}
```

#### Mental Model & Mengapa:
- **Zero OS Emoji Dependency**: Menggunakan emoji `🔍` sering kali menghasilkan tofu box (kotak silang) atau rupa berbeda drastis di Skiko WebAssembly, Linux, dan macOS.
- **Trigonometri Sederhana**: `0.7071f` adalah $\cos(45^\circ) = \sin(45^\circ)$. Ini menempatkan titik awal gagang tepat di keliling lingkaran arah tenggara (45°), memberikan estetika kaca pembesar modern yang presisi.

---

### B. Trigger Capsule Top Bar (`PersonaSwitcherDropdown.kt`)

```kotlin
@Composable
fun PersonaSwitcherDropdown(
    activePersona: TestingPersona?,
    // ... parameter data lainnya ...
) {
    var isModalOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ClayActionSurface(onClick = { isModalOpen = true }) {
            Text(text = "🧪", fontSize = 12.sp)
            Column {
                Text(
                    text = activePersona?.displayLabel ?: "Pilih Persona Pengujian",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = activePersona?.let { "${it.roleTitle} · ${it.departmentName}" } ?: "Belum ada persona aktif",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(text = "▼", fontSize = 8.sp, color = WeMadeColors.OnSurfaceMuted)
        }

        if (isModalOpen) {
            PersonaTestingModal(...)
        }
    }
}
```

#### Mental Model:
- **Clean Trigger**: Tombol di top bar tetap ringkas sebagai indikator persona yang sedang aktif dan pemicu dialog.
- **Lazy Rendering Modal**: Modal hanya di-compose saat `isModalOpen == true`. Tidak ada overhead layout saat modal ditutup.

---

### C. Pipeline Pencarian & Filter Real-Time

```kotlin
val filteredRoles = remember(effectiveRoles, departments, searchQuery) {
    if (searchQuery.isBlank()) {
        effectiveRoles
    } else {
        val q = searchQuery.trim().lowercase()
        effectiveRoles.filter { role ->
            val dept = DynamicRbacViewModel.resolveDepartmentForRole(role, departments)
            val deptName = dept?.displayName?.lowercase().orEmpty()
            val deptCode = dept?.code?.lowercase().orEmpty()
            role.name.lowercase().contains(q) ||
                deptName.contains(q) ||
                deptCode.contains(q) ||
                role.description.lowercase().contains(q)
        }
    }
}
```

#### Mental Model:
- **Multi-Field Matching**: Pengguna bisa mencari berdasarkan nama jabatan (`"sales"`), nama divisi (`"penjualan"`, `"ppic"`), kode divisi (`"warehouse"`), ataupun kata kunci pada deskripsi pekerjaan (`"potong"`).
- **Zero Query Lag**: Filtering dieksekusi di thread UI secara synchronous karena ukuran daftar role di tenant berkisar puluhan hingga ratusan (eksekusi `< 1ms`), tanpa perlu debounce asynchronous yang malah membuat respons terasa lambat.

---

### D. Format Kartu Jabatan: `{Jabatan} - {Divisi}`

```kotlin
roles.forEach { role ->
    val dept = DynamicRbacViewModel.resolveDepartmentForRole(role, departments)
    val deptName = dept?.displayName ?: "Lintas Divisi"
    val isCurrentRole = activePersona?.roleId == role.id

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isCurrentRole) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
        outlineColor = if (isCurrentRole) WeMadeColors.Primary else WeMadeColors.Outline,
        contentPadding = PaddingValues(ClaySpacing.Md),
        offset = ClayOffset.Small,
        onClick = {
            val finalName = customName.trim().ifBlank { role.name }
            val persona = TestingPersona.custom(
                name = finalName,
                tenantId = tenantId,
                tenantSlug = tenantSlug,
                department = dept,
                role = role
            )
            onSelect(persona)
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Text(
                        text = role.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "-",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    ClayTag(
                        text = deptName,
                        tint = if (dept != null) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                    )
                }
                if (role.description.isNotBlank()) {
                    Text(
                        text = role.description,
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                modifier = Modifier.padding(start = ClaySpacing.Md)
            ) {
                if (isCurrentRole) {
                    ClayTag(text = "Aktif", tint = WeMadeColors.Primary)
                }
                ClayTag(
                    text = if (isCurrentRole) "Terpilih" else "Pilih ➔",
                    tint = if (isCurrentRole) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}
```

#### Mental Model:
- **Visual Hierarchy**:
  1. Nama jabatan tebal (`13.sp`, `FontWeight.Bold`).
  2. Pemisah tanda hubung `-`.
  3. Badge divisi (`ClayTag`) dengan warna aksen brand bila memiliki divisi, atau muted bila lintas divisi.
  4. Deskripsi peran 2 baris dengan `TextOverflow.Ellipsis`.
- **Sensitivitas Klik Penuh**: Seluruh area kartu dibungkus `onClick = { ... }`. Pengguna tidak perlu membidik tombol kecil, mengklik di mana saja pada baris tersebut langsung mengaktifkan persona.

---

## 3. Technology & Approach ("The Why")

| Pendekatan | Pilihan Kita | Mengapa Bukan Alternatifnya? |
|---|---|---|
| **Komponen Kontainer** | `Dialog(onDismissRequest = ...)` dengan `ClayCard` | `DropdownMenu` terbatas pada anchor posisi tombol dan memiliki batas lebar bawaan platform yang sempit (sulit membaca deskripsi peran). |
| **Penyajian Data** | Satu daftar terpadu `{Jabatan} - {Divisi}` | Memisahkan pilihan divisi dan jabatan memaksa pengguna melakukan 2 langkah pemilihan dan menghalangi pemahaman konteks bahwa jabatan tertentu sejatinya melekat pada divisi tertentu. |
| **Ikon Pencarian** | Vector Canvas murni di `ClayIcons.kt` | Material `Icons.Default.Search` menarik dependensi Material Icons besar yang tidak kompatibel di semua KMP runtime tanpa setup tambahan; emoji `🔍` rentan font rendering glitch di browser. |
| **Penerapan Persona** | 1-Click apply dengan fallback nama otomatis | Form input manual (nama + divisi + jabatan + submit) menghasilkan gesekan tinggi (friction) saat admin sedang menguji beberapa skenario hak akses berturut-turut. |

---

## 4. Jebakan Pemula (Common Pitfalls)

1. **Jebakan Tombol Bertingkat (Nested Clickables)**:  
   *Kesalahan*: Membuat `ClayCard(onClick = { ... })` lalu di dalamnya meletakkan `ClayButton(onClick = { ... })`.  
   *Dampaknya*: Event klik bertubrukan (gestural ambiguity) dan indikator hover/pressed menjadi kacau di layar sentuh maupun pointer mouse.  
   *Solusi*: Buat satu target klik pada kontainer kartu, dan gunakan `ClayTag` visual ("Pilih ➔" atau "Terpilih") sebagai penanda aksi tanpa click handler terpisah.

2. **Jebakan `runBlocking` di KMP Test**:  
   *Kesalahan*: Menggunakan `runBlocking` pada unit test di `commonTest`.  
   *Dampaknya*: Kompilasi target JavaScript dan WebAssembly akan gagal (`Unresolved reference 'runBlocking'`) karena runtime JS bersifat single-threaded non-blocking.  
   *Solusi*: Selalu gunakan `runTest` dari `kotlinx.coroutines.test.*`.

3. **Jebakan Scroll Parent Conflict**:  
   *Kesalahan*: Membungkus seluruh modal dalam satu `verticalScroll` yang besar, lalu meletakkan list di dalamnya.  
   *Dampaknya*: Header pencarian dan tombol tutup ikut tergulung ke atas saat scroll, sehingga pengguna kehilangan konteks pencarian.  
   *Solusi*: Pisahkan layout: Header, Banner, dan Search Bar bersifat `sticky` di atas; hanya area list konten yang diberi `Modifier.weight(1f)` dan `verticalScroll()`.

---

## 5. Verifikasi & Tantangan Mandiri

### Verifikasi yang Telah Dilakukan:
1. **Automated JVM & Shared Test**:  
   `./gradlew :app:shared:compileKotlinJvm` dan `./gradlew :app:shared:compileKotlinWasmJs` sukses tanpa warning atau error.
2. **Browser Subagent E2E Recording**:  
   Membuka `http://127.0.0.1:3000/org-chart`, mengklik capsule `🧪`, memastikan modal dialog luas muncul di tengah layar, mengetik `"Sales"`, dan mengklik peran *Sales Eksekutif*. Top bar langsung berganti ke persona yang dipilih. Rekaman video tersimpan di artifacts.

### Tantangan Mandiri untuk Junior Developer:
1. **Keyboard Navigation**: Tambahkan shortcut keyboard `Escape` untuk menutup dialog dan panah `Down`/`Up` untuk berpindah fokus antar kartu jabatan.
2. **Recent Personas**: Simpan 3 persona terakhir yang pernah dipilih ke dalam `PlatformLocalStorage` dan tampilkan sebagai chip "Cepat Digunakan" di bawah bilah pencarian.
