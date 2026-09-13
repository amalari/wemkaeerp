# Modul Pembelajaran: Mengunci Modul ke Seluruh Pabrik (ScopeCapability.GLOBAL_ONLY) pada WeMade Garment ERP

> **Level**: Intermediate to Advanced  
> **Topik**: Domain-Driven Design (DDD), RBAC Data Scope Capabilities, Dynamic UI Form Locking, Multi-Tenant Governance  
> **Target Audience**: Junior & Mid-level Engineers di WeMade ERP

---

## 1. Konteks Masalah & Kebutuhan Bisnis

Pada sistem ERP pabrik konveksi WeMade, setiap modul bisnis memiliki dua dimensi otorisasi:
1. **Access Level (Tingkat Hak Aksi)**: `NONE` (tidak punya akses), `VIEW` (hanya lihat), `OPERATE` (input & edit), dan `MANAGE` (kelola penuh, hapus, konfigurasi).
2. **Data Scope (Jangkauan Data)**: `OWN_DATA_ONLY` (data sendiri), `SUBORDINATE_DATA` (data bawahan/divisi), dan `ALL_TENANT_DATA` (seluruh pabrik).

### Kasus Spesifik Modul Bagan Organisasi (`ORG_CHART`):
Bagan struktur organisasi perusahaan adalah peta hierarki kolektif. Siapapun karyawan di pabrik (termasuk Staff Gudang, Operator Jahit, dsb.) secara operasional **harus bisa melihat keseluruhan bagan struktur organisasi pabrik** (mulai dari Direksi hingga antar divisi terkait), sehingga mereka paham alur eskalasi dan hierarki perusahaan.

Namun wewenang mereka tetap harus dibedakan:
- **Staff biasa**: Hanya boleh **melihat (VIEW)** bagan penuh secara read-only (form input disembunyikan, bagan tampil 100% full width).
- **HR / Supervisor**: Boleh **menginput dan mengedit karyawan (OPERATE)**.
- **Direktur / Admin**: Boleh **mengarsipkan, menghapus, atau mereset bagan (MANAGE)**.

### Masalah Sebelum Refactor:
Sebelumnya, `BusinessModule.ORG_CHART` diklasifikasikan sebagai `ScopeCapability.HIERARCHICAL`.
Akibatnya:
1. Di layar pengaturan Matriks Hak Akses (RBAC), admin disajikan pemilih jangkauan data: *Sendiri*, *Bawahan*, *Semua Data*.
2. Jika admin salah memilih *Bawahan*, sistem akan memotong struktur organisasi sehingga staf hanya melihat divisinya sendiri secara terisolasi tanpa mengetahui siapa Direktur atau divisi koordinasi lainnya.
3. User meminta: **"di case ini sebenernya modul bagan ini ga ada scope data semua bisa lihat full bagan cuma ada hak nya ada yang bisa input dan edit, view aja dan manage. Jadi buat settingan agar ini dianggap datanya terkunci ga muncul scope data itu gimana ya?"**

---

## 2. Order of Operations ("Start dari Mana?")

Ketika mengubah kapabilitas scoping modul pada arsitektur DDD, urutan pengerjaan yang benar adalah dari **lapisan inti (Core Domain)** ke **lapisan luar (Presentation & Infrastructure)**:

```
[1. Domain Entity & Enum]       -> core: BusinessModule.kt (scopeCapability = GLOBAL_ONLY)
          ↓
[2. Default Role Permissions]   -> core: CustomRole.kt (ORG_CHART scope = ALL_TENANT_DATA)
          ↓
[3. Core Unit Tests]            -> core: GovernanceModuleAccessTest.kt (isGlobalOnly & supportedScopes)
          ↓
[4. Database Migration]         -> server: V18__add_governance_modules.sql (scope_capability = 'GLOBAL_ONLY')
          ↓
[5. Server API Guards & Tests]  -> server: OrgChartAccessApiTest.kt
          ↓
[6. Shared UI & ViewModel]      -> app:shared: ModuleMatrixCard.kt, OrgChartViewModel.kt, OrgChartScreen.kt
          ↓
[7. Integration Verification]   -> ./gradlew :core:jvmTest :app:shared:jvmTest :server:test
```

---

## 3. Bedah Kode Blok per Blok & Mental Model

### A. Definisi Domain di `BusinessModule.kt`

```kotlin
// core/src/commonMain/kotlin/com/eventverse/app/domain/rbac/BusinessModule.kt

enum class BusinessModule(
    val code: String,
    val displayName: String,
    val category: ModuleCategory,
    val description: String,
    val iconKey: String,
    val scopeCapability: ScopeCapability = ScopeCapability.GLOBAL_ONLY,
    val kind: ModuleKind = ModuleKind.OPERATIONAL,
    val supportedScopes: Set<DataScope> = if (scopeCapability == ScopeCapability.GLOBAL_ONLY) {
        setOf(DataScope.ALL_TENANT_DATA)
    } else {
        setOf(DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA, DataScope.ALL_TENANT_DATA)
    }
) {
    ORG_CHART(
        code = "org_chart",
        displayName = "Bagan Struktur Organisasi & Karyawan",
        category = ModuleCategory.GOVERNANCE,
        description = "Struktur divisi, jenjang jabatan, dan data karyawan pabrik.",
        iconKey = "users",
        // Mengunci modul ke GLOBAL_ONLY: seluruh pengguna melihat bagan penuh.
        // Pembedaan wewenang hanya pada AccessLevel (VIEW, OPERATE, MANAGE).
        scopeCapability = ScopeCapability.GLOBAL_ONLY,
        kind = ModuleKind.GOVERNANCE
    ),
    ...
```

#### Mental Model:
- **Single Source of Truth**: Atribut `scopeCapability` menentukan kapabilitas scope modul di seluruh sistem.
- Properti `supportedScopes` secara otomatis menjadi `setOf(DataScope.ALL_TENANT_DATA)` jika kapabilitasnya `GLOBAL_ONLY`. Ini mencegah nilai scope yang tidak valid tersimpan.

---

### B. Otomatisasi UI Lock di `ModuleMatrixCard.kt`

Pada UI konfigurasi hak akses per jabatan (`ModuleMatrixCard.kt`), komponen `DynamicDataScopeSelector` membaca properti `module.isGlobalOnly`:

```kotlin
// app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/rbac/components/ModuleMatrixCard.kt

if (module.isGlobalOnly) {
    // Kunci data scope: jangan render pilihan radio button 'Sendiri'/'Bawahan'/'Semua'
    ClayBadge(
        text = "🌐 Seluruh Pabrik (Data Bersama)",
        backgroundColor = WeMadeColors.SurfaceMuted,
        textColor = WeMadeColors.TextSecondary
    )
} else {
    // Render 3 opsi radio button interaktif:
    // [Data Sendiri] [Data Bawahan/Divisi] [Semua Data Pabrik]
}
```

#### Mental Model:
- **Defensive UI Design**: Alih-alih membiarkan admin mengklik opsi yang tidak masuk akal lalu memberi error saat disimpan, UI secara proaktif mengunci elemen kontrol dan memberikan label informatif (`Seluruh Pabrik (Data Bersama)`).
- Admin tidak lagi dibingungkan dengan opsi scope untuk bagan organisasi. Mereka hanya fokus menentukan: apakah jabatan ini `NONE`, `VIEW`, `OPERATE`, atau `MANAGE`.

---

### C. Normalisasi Otomatis di `ModuleAccessConfig.kt`

Untuk melindungi jika ada payload lama dari database atau input API yang mengirim scope selain `ALL_TENANT_DATA` untuk modul `GLOBAL_ONLY`:

```kotlin
// core/src/commonMain/kotlin/com/eventverse/app/domain/rbac/ModuleAccessConfig.kt

fun sanitizeFor(module: BusinessModule): ModuleAccessConfig {
    if (level == AccessLevel.NONE) {
        return copy(scope = DataScope.OWN_DATA_ONLY)
    }
    if (module.isGlobalOnly) {
        // Otomatis normalisasi ke ALL_TENANT_DATA
        return copy(scope = DataScope.ALL_TENANT_DATA)
    }
    return this
}
```

#### Mental Model:
- **Defense in Depth**: Bahkan jika seorang penyerang memanggil API dengan payload `scope: "SUBORDINATE_DATA"`, domain sanitizer akan otomatis menormalkannya ke `ALL_TENANT_DATA`, memastikan integritas data tetap terjaga.

---

### D. Tampilan Bagan 100% Full-Width untuk Hak Akses `VIEW`

Di layar `OrgChartScreen.kt`:

```kotlin
// app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartScreen.kt

val canWrite = uiState.canWrite // true jika level >= OPERATE

Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    // 1. Panel Form Input Karyawan hanya muncul jika memiliki izin OPERATE atau MANAGE
    if (canWrite) {
        EmployeeFormPanel(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier.width(420.dp).fillMaxHeight()
        )
        Spacer(modifier = Modifier.width(16.dp))
    }

    // 2. Panel Bagan Organisasi mengambil sisa ruang layar (atau 100% jika !canWrite)
    ChartPreviewPanel(
        uiState = uiState,
        onEvent = onEvent,
        modifier = Modifier.weight(1f).fillMaxHeight()
    )
}
```

#### Mental Model:
- `Modifier.weight(1f)` pada Compose `Row` secara otomatis mengisi seluruh ruang yang tersedia. Ketika `EmployeeFormPanel` tidak dirender (`!canWrite`), `ChartPreviewPanel` langsung membentang 100% dari kiri ke kanan.

---

### E. Prioritas Default Divisi Pengguna & Navigasi Antar Divisi (`DivisionSelectorTabs`)

Agar pengalaman pengguna intuitif:
1. **Default ke Divisi Sendiri**: Saat membuka bagan organisasi, sistem mencocokkan `viewerDepartmentId` milik persona pengguna (misal `dept-warehouse` untuk Staff Gudang). Sistem langsung membuka bagan divisi Gudang dan menyorot Head of Department / karyawan terkait.
2. **Navigasi Terbuka ke Divisi Lain & Direksi**: Karena bagan organisasi adalah data bersama seluruh pabrik, pengguna read-only tetap dapat melihat divisi lain. Kita menambahkan komponen `DivisionSelectorTabs` langsung di atas bagan pohon:
   - Chip `Direksi (Pimpinan Puncak)`
   - Chip untuk setiap divisi (`Sales`, `Produksi`, `Gudang`, `QC`, dsb.) dengan indikator warna dan jumlah anggota.
3. **Navigasi Cepat via Rekan Sejajar (Peer Heads)**: Pada bagan pohon `TShapeChartView`, label `Rekan Sejajar (Kepala Divisi Lain)` dijadikan interaktif (`.clickable`), sehingga mengklik kepala divisi lain langsung memindahkan fokus dan bagan ke divisi tersebut.

---

## 4. Technology & Approach ("The Why")

### Mengapa menggunakan `ScopeCapability.GLOBAL_ONLY` daripada hardcoded `if (module == ORG_CHART)` di UI?
1. **Ekstensibilitas**: WeMade ERP memiliki modul lain yang juga bersifat data bersama (misalnya `DYNAMIC_RBAC` untuk daftar role, `FACTORY_FLOW` untuk kanvas alur pabrik, `QUALITY_CONTROL` untuk standar grading). Dengan konsep `ScopeCapability`, kita memiliki abstraksi umum yang reusable.
2. **Eliminasi Magic Strings & Special Cases**: UI dan API tidak perlu tahu nama modul spesifik; mereka cukup bertanya `module.isGlobalOnly`.

---

## 5. Jebakan Pemula (Common Pitfalls)

### ⚠️ Jebakan 1: Lupa Menyesuaikan Nilai Default di `CustomRole.kt`
Ketika mengubah kapabilitas modul menjadi `GLOBAL_ONLY`, pastikan preset role bawaan (seperti `MANAGER_PRODUKSI`, `STAFF_GUDANG`, dll.) diperbarui dari `DataScope.SUBORDINATE_DATA` menjadi `DataScope.ALL_TENANT_DATA`. Jika tidak, sanitasi atau test role bawaan akan menimbulkan inkonsistensi.

### ⚠️ Jebakan 2: Perbedaan Tipe Koleksi pada Assertion Test (`List` vs `Set`)
Di Kotlin:
```kotlin
// ❌ SALAH: supportedScopes bertipe Set<DataScope>, sedangkan listOf menghasilkan List<DataScope>
assertEquals(listOf(DataScope.ALL_TENANT_DATA), BusinessModule.ORG_CHART.supportedScopes)

// ✅ BENAR: Gunakan setOf
assertEquals(setOf(DataScope.ALL_TENANT_DATA), BusinessModule.ORG_CHART.supportedScopes)
```

### ⚠️ Jebakan 3: Mengandalkan Validasi Frontend Saja
Jangan hanya menyembunyikan radio button di UI. API backend (`OrgChartAccessGuard.kt` dan route Ktor) wajib memvalidasi `accessLevel`:
- Endpoint POST/PUT karyawan harus memverifikasi `hasOperateAccess(BusinessModule.ORG_CHART)`.
- Endpoint DELETE / RESET harus memverifikasi `hasManageAccess(BusinessModule.ORG_CHART)`.

---

## 6. Verifikasi & Tantangan Mandiri

### Verifikasi Otomatis yang Dijalankan:
```bash
./gradlew :core:jvmTest --tests "com.eventverse.app.domain.rbac.GovernanceModuleAccessTest"
./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.orgchart.*"
./gradlew :server:test --tests "com.eventverse.app.OrgChartAccessApiTest"
```
Hasil: **Seluruh pengujian lulus 100% tanpa regresi.**

### Tantangan Mandiri untuk Junior Developer:
1. Coba tambahkan modul baru bernama `COMPANY_ANNOUNCEMENT` (Pengumuman Pabrik). Tentukan apakah modul ini harus `GLOBAL_ONLY` atau `HIERARCHICAL`?
2. Bagaimana cara memastikan bahwa saat pengguna dalam mode `VIEW`, klik pada kartu karyawan tetap menampilkan detail popover atau fokus, tetapi tombol *Hapus* di dalam detail tersebut tidak aktif?
