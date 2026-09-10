# 🎓 Modul Pembelajaran: Perbaikan Modal Penugasan Divisi (Fixed Level Akses, Tanda Scroll, & Jabatan Dinamis per Divisi)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform Modal Layout, Pinning Critical Form Controls, Visual Scroll Affordance, Dynamic Domain Filtering  
> **Prasyarat**: Dasar Compose Multiplatform Layout (`Modifier.verticalScroll`, `ScrollState.canScrollForward`, `Dialog`, `Card`), State Resolution  
> **Referensi File**: [AssignDepartmentModal.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/rbac/components/AssignDepartmentModal.kt)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
1. **Level Akses Ikut Ter-scroll ke Bawah**:
   Pada iterasi sebelumnya, seluruh formulir dibungkus dalam satu `Column` scrollable. Ketika opsi *"Pilih Jabatan Spesifik"* dibuka, bagian *"Level Akses"* dan *"Jangkauan Data"* terdorong ke bawah lipatan layar (*fold*) sehingga pengguna mengira kontrol izin tersebut hilang atau terpotong. Padahal, level akses adalah pengaturan utama yang **wajib selalu terlihat (full selalu ada)** di layar.
2. **Ketiadaan Tanda/Indikator Scroll**:
   Meskipun daftar jabatan sudah bisa di-scroll, pengguna tidak mendapatkan sinyal visual (*affordance*) bahwa kotak tersebut bisa digulir ke bawah. Pengguna yang melihat 3-4 item tidak tahu apakah ada item berikutnya.
3. **Daftar Jabatan Statis (Tidak Dinamis Mengikuti Divisi)**:
   Ketika pengguna mengganti divisi (misal dari *Penjualan & CRM* ke *Produksi & PPIC* atau *Gudang*), daftar jabatan tetap sama (menampilkan seluruh 6 jabatan pabrik tanpa filter). Pengguna menanyakan apakah frontend sudah terhubung ke API backend untuk menarik jabatan berdasarkan divisi.

### Status Koneksi API (Pertanyaan Pengguna)
- **Kondisi Saat Ini**: Modul RBAC frontend (`DynamicRbacViewModel`) saat ini **belum tersambung ke API live** (masih menggunakan local state / domain preset).
- **Backend API yang Tersedia**: Di sisi server Ktor (`server`), endpoint sudah tersedia:
  - `GET /api/tenant/departments`
  - `GET /api/tenant/employees?departmentId={id}` (mengambil data karyawan berserta `roleTitle` per divisi)
  - `GET /api/tenant/roles`
- **Solusi Arsitektural**: Sambil menunggu integrasi jaringan penuh ke ViewModel, kita mengimplementasikan **Dynamic Department Role Resolution** di presentation layer. Setiap kali pengguna memilih divisi, daftar jabatan langsung tersaring dan menampilkan jabatan yang relevan dengan divisi tersebut!

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

1. **Langkah 1: Mengeluarkan Kontrol Kritis dari Area Scroll (Pinning Pattern)**
   - Pindahkan *"Level Akses"* dan *"Jangkauan Data"* keluar dari kontainer scrollable. Taruh tepat di atas tombol footer (*Batal* / *Tugaskan Divisi*).
   - Dengan tinggi kartu yang proporsional (~520dp), seluruh formulir (Divisi, Opsi Lingkup, Level Akses, Jangkauan Data, Tombol Aksi) **100% muat di layar tanpa dialog ikut ter-scroll**.

2. **Langkah 2: Menambahkan Tanda Visual Scroll (Visual Affordance)**
   - Pada toolbar daftar jabatan, tambahkan pill badge: `↕ Scrollable`.
   - Di bagian bawah kotak daftar jabatan, gunakan kondisi `if (rolesScrollState.canScrollForward)` untuk menampilkan bar petunjuk navigasi:  
     `"▼ Gulir ke bawah untuk melihat jabatan lainnya"`.

3. **Langkah 3: Menghubungkan Divisi dengan Jabatan Dinamis (Dynamic Role Resolver)**
   - Buat fungsi resolusi `resolveRolesForDepartment(selectedDept, roles)`.
   - Ketika `selectedDeptId` berubah:
     - Jabatan langsung berubah sesuai divisi (Sales -> Head of Sales, Sales Eksekutif; Produksi -> PPIC, Operator Jahit; Gudang -> Staff Gudang; QC -> Kepala QC, QC Inspector; Keuangan -> Kepala Keuangan, Staff Akuntansi).
     - State `selectedRoleIds` otomatis di-reset (`emptySet()`) agar ID peran divisi lama tidak terbawa ke divisi baru.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Resolusi Jabatan Dinamis per Divisi (`resolveRolesForDepartment`)

```kotlin
private fun resolveRolesForDepartment(
    dept: Department?,
    allRoles: List<CustomRole>
): List<CustomRole> {
    if (dept == null) return allRoles
    val code = dept.code.lowercase()
    val name = dept.displayName.lowercase()

    // 1. Saring role kustom atau sistem yang cocok dengan divisi terpilih
    val matchedFromRoles = allRoles.filter { role ->
        val rName = role.name.lowercase()
        val isOwner = rName.contains("owner") || rName.contains("direktur")

        when {
            code.contains("sales") || name.contains("penjualan") ->
                isOwner || rName.contains("sales") || rName.contains("penjualan") || rName.contains("crm")
            code.contains("ppic") || code.contains("production") || name.contains("produksi") ->
                isOwner || rName.contains("ppic") || rName.contains("produksi") || rName.contains("operator") || rName.contains("jahit") || rName.contains("mandor")
            code.contains("warehouse") || name.contains("gudang") ->
                isOwner || rName.contains("gudang") || rName.contains("logistik") || rName.contains("warehouse")
            code.contains("qc") || name.contains("quality") || name.contains("kualitas") ->
                isOwner || rName.contains("qc") || rName.contains("quality") || rName.contains("kualitas")
            code.contains("finance") || name.contains("keuangan") || name.contains("akuntansi") ->
                isOwner || rName.contains("keuangan") || rName.contains("akuntansi") || rName.contains("finance") || rName.contains("kasir")
            else -> isOwner || rName.contains(code)
        }
    }

    // 2. Fallback jabatan standar divisi jika tenant belum mendefinisikan role kustom
    val departmentSpecificDefaults = when {
        code.contains("qc") || name.contains("quality") -> listOf(
            CustomRole(id = RoleId("role-qc-head"), name = "Kepala Quality Control (QC)", ...),
            CustomRole(id = RoleId("role-qc-inspector"), name = "QC Inspector (In-Line & End-Line)", ...)
        )
        code.contains("finance") || name.contains("keuangan") -> listOf(
            CustomRole(id = RoleId("role-finance-head"), name = "Kepala Keuangan & Akuntansi", ...),
            CustomRole(id = RoleId("role-finance-staff"), name = "Staff Akuntansi & Kasir", ...)
        )
        code.contains("warehouse") || name.contains("gudang") -> listOf(
            CustomRole(id = RoleId("role-warehouse-head"), name = "Kepala Gudang & Logistik", ...)
        )
        else -> emptyList()
    }

    val combined = (matchedFromRoles + departmentSpecificDefaults).distinctBy { it.id.value }
    return combined.ifEmpty { allRoles }
}
```

**Mengapa ditulis begini?**
- Menjamin setiap divisi pabrik memiliki daftar jabatan spesifik yang tepat dan masuk akal secara operasional.
- Peran pimpinan tertinggi (*Owner / Direktur Pabrik*) tetap disertakan di semua divisi sebagai supervisor eksekutif.

---

### Blok B: Tanda Visual Scroll (`canScrollForward`)

```kotlin
// Toolbar dengan badge scrollable
Box(
    modifier = Modifier
        .clip(RoundedCornerShape(4.dp))
        .background(Color(0xFFE2E8F0))
        .padding(horizontal = 5.dp, vertical = 1.5.dp)
) {
    Text(
        text = "↕ Scrollable",
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF475569)
    )
}

// Kotak daftar jabatan
Column(
    modifier = Modifier
        .fillMaxWidth()
        .height(105.dp)
        .verticalScroll(rolesScrollState),
    verticalArrangement = Arrangement.spacedBy(2.dp)
) {
    currentDeptRoles.forEach { role -> ... }
}

// Indikator dinamis di bawah kotak jika masih ada item yang belum terlihat
if (rolesScrollState.canScrollForward) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFEFF6FF))
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "▼ Gulir ke bawah untuk melihat jabatan lainnya",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.PrimaryDark
        )
    }
}
```

**Mengapa ditulis begini?**
- Properti bawaan `rolesScrollState.canScrollForward` secara otomatis bernilai `true` selama pengguna belum mencapai dasar daftar.
- Begitu pengguna menggulir sampai ke item paling bawah, bilah indikator akan menghilang otomatis!

---

### Blok C: Level Akses Selalu Terlihat (Full Selalu Ada)

```kotlin
// 3. Level Akses & Jangkauan Data (Diletakkan di Card level, BUKAN di dalam nested scroll)
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(14.dp)
) {
    // Level Akses (Hanya Lihat | Input & Kerja | Akses Penuh)
    Column(modifier = Modifier.weight(1f)) { ... }

    // Jangkauan Data (Data Sendiri | Data Bawahan | Semua Data)
    Column(modifier = Modifier.weight(1f)) { ... }
}

Spacer(modifier = Modifier.height(16.dp))
HorizontalDivider(...)
Spacer(modifier = Modifier.height(12.dp))

// Actions Footer (Batal & Simpan)
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ... }
```

**Mengapa ditulis begini?**
- Pengguna tidak perlu melakukan scroll apapun pada dialog utama untuk mengatur hak akses (*Level Akses* & *Jangkauan Data*).
- Hanya komponen yang memang memiliki banyak data (*Daftar Jabatan*) yang diberi scroll mandiri.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa Kita Memilih Ini? | Risiko Alternatif |
|---|---|---|---|
| **Localized Bounded Scroll (105dp)** | Full Dialog Scroll | Memastikan kontrol formulir esensial (*Level Akses* & *Footer*) selalu terlihat di layar. | Pengguna mengabaikan pemilihan hak akses karena tersembunyi di bawah layar. |
| **`canScrollForward` Affordance Banner** | Scrollbar tipis tanpa teks | Teks petunjuk langsung memberikan kepastian pada pengguna bahwa masih ada opsi jabatan lain di bawahnya. | Pengguna mengira jabatan hanya ada 3 karena scrollbar browser seringkali tidak terlihat (*hidden/transparent by default*). |
| **Presentation Dynamic Filtering** | Menunggu integrasi API penuh | Memberikan UX dinamis secara instan kepada pengguna tanpa mengubah struktur kontrak database/use case yang sudah ada. | Tampilan tetap statis dan membingungkan pengguna jika harus menunggu deployment backend API baru. |

---

## 🧪 5. Verifikasi & Pengujian Mandiri

1. **Jalankan Verifikasi Build**:
   ```bash
   ./gradlew :app:shared:compileCommonMainKotlinMetadata
   ./gradlew :app:webApp:compileKotlinWasmJs
   ./gradlew :app:shared:jvmTest
   ```
   *Hasil*: Seluruh task menghasilkan `BUILD SUCCESSFUL`.

2. **Pengujian Fungsional di Browser (`http://localhost:3000/rbac`)**:
   - Buka modal *"Tugaskan Divisi ke Modul"*.
   - Pilih divisi **Penjualan & CRM** -> Klik *"Pilih Jabatan Spesifik"*:
     - Terlihat badge `↕ Scrollable`.
     - Jabatan yang muncul: *Owner / Direktur*, *Kepala Penjualan*, *Sales Eksekutif*.
     - Bilah *"Level Akses"* dan *"Jangkauan Data"* **tetap tampak utuh di bawah kotak tanpa perlu di-scroll**.
   - Ganti divisi ke **Produksi & PPIC**:
     - Daftar jabatan otomatis berubah secara dinamis menjadi: *Owner*, *Kepala Produksi (PPIC)*, *Operator Mesin Jahit*.
     - Checkbox checklist otomatis bersih kembali (*reset*).
   - Ganti ke **Gudang & Logistik**:
     - Menampilkan jabatan: *Owner*, *Kepala Gudang*, *Staff Gudang & Logistik*.
   - Saat item melebihi area 105dp, muncul banner biru di bawah: *"▼ Gulir ke bawah untuk melihat jabatan lainnya"*.

---

## 🌐 6. Integrasi Dynamic RBAC API Client (`RbacApiClient`)

Untuk menjembatani antara UI Compose Multiplatform dan server Ktor WeMade ERP:
1. **`RbacApiClient`** (`app/shared/.../infrastructure/api/RbacApiClient.kt`):
   - Mengonsumsi endpoint Ktor:
     - `GET /api/tenant/departments`: Mengambil daftar divisi aktif tenant.
     - `GET /api/tenant/roles`: Mengambil daftar jabatan dan matriks wewenang modul.
     - `POST /api/tenant/roles`: Menyimpan penambahan jabatan baru.
     - `PUT /api/tenant/roles/{id}`: Memperbarui hak akses modul per jabatan.
     - `DELETE /api/tenant/roles/{id}`: Menghapus jabatan kustom.
2. **Graceful Failover di `DynamicRbacViewModel`**:
   - Saat inisialisasi, ViewModel langsung memuat factory preset lokal (`CustomRole.createFactoryPresets()` dan `Department.defaultPresets()`) agar UI langsung responsif seketika tanpa jeda *loading spinner* putih.
   - Secara paralel di background, ViewModel memanggil `fetchRemoteData()` untuk menarik data terbaru dari server Ktor dan memperbarui state UI secara mulus (*non-blocking*).

---

## 📐 7. Aturan Baku Kapabilitas Jangkauan Modul (`ScopeCapability` & `DataScope`)

Dalam arsitektur WeMade ERP, data scope **TIDAK DI-HARDCODE SECARA BEBAS**, melainkan dikendalikan oleh kontrak baku di `core/src/commonMain/kotlin/com/eventverse/app/domain/rbac/`:

1. **`ScopeCapability.GLOBAL_ONLY` (Hanya 1 Opsi: Semua Data)**
   - **Karakteristik**: Modul yang datanya adalah aset kolektif pabrik (misal: `INVENTORY` stok kain roll, `PRODUCTION_MRP` alokasi mesin, `COSTING_HPP` biaya rahasia, `QUALITY_CONTROL`, `TECH_PACK_BOM`, `FULFILLMENT`).
   - **Tampilan UI**: Otomatis hanya menampilkan badge `🌐 Seluruh Pabrik (Shared)`. Opsi *Data Sendiri* dan *Data Bawahan* dihilangkan agar staf tidak sengaja memilih filter yang menyebabkan data kain/mesin kosong.
2. **`ScopeCapability.HIERARCHICAL` (Tersedia 3 Opsi Lengkap)**
   - **Karakteristik**: Modul transaksional dengan garis komando (misal: `CRM_SALES` prospek order, `SAMPLING_ORDER`, `OPERATOR_EXEC`).
   - **Tampilan UI**: Menampilkan 3 tab pilihan:
     - `Data Sendiri`: Hanya melihat dokumen yang dibuat pengguna.
     - `Data Bawahan`: Melihat dokumen pengguna dan seluruh tim di bawahnya.
     - `Semua Data`: Akses lintas divisi.
3. **Proteksi Domain Sanitasi**:
   - `ModuleAccessConfig.sanitizeFor(module)` secara otomatis menjamin jika ada konfigurasi yang mencoba memasukkan `OWN_DATA_ONLY` ke modul `GLOBAL_ONLY`, scope akan otomatis dipaksa kembali ke `ALL_TENANT_DATA`.
   - Aturan ini telah resmi dikodifikasikan ke dalam kontrak arsitektur di `.agents/rules/module-integration-rules.md` (Kontrak 8).
