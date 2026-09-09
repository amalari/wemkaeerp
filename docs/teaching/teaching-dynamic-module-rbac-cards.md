# 🎓 Modul Pembelajaran: Arsitektur 1 Modul 1 Card, Ketersediaan Jangkauan Dinamis (Dynamic Scope), dan Mode Demo Superadmin

> **Level Target**: Junior to Mid-Level Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Dynamic RBAC, Scope Capability (Global vs Hierarchical), Dual-View UI Presentation (Compose Multiplatform), Multi-Tenant Authentication & Superadmin Impersonation  
> **Prasyarat**: Dasar Kotlin Multiplatform (KMP), pemahaman Compose Multiplatform state hoisting, serta konsep Authorization & Multi-Tenancy.  
> **Referensi Task**: Fitur 1 Modul 1 Card, Dynamic Scopes, dan Tombol Demo Mode Superadmin Apps ([WeMade ERP Issue Tracker](https://github.com/amalari/wemade-erp))

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata: Mengapa RBAC Konvensional Per-Role Saja Tidak Cukup untuk ERP Garmen?
Di banyak sistem konvensional, hak akses pengguna hanya diatur dari sudut pandang **Role/Jabatan** (*Role-Centric*):
1. Admin memilih jabatan "Kepala Gudang", lalu mencentang modul apa saja yang boleh dibuka.
2. Namun, ketika Pemilik Pabrik (Owner) atau HRD ingin mengevaluasi keamanan data rahasia seperti **Kalkulasi HPP & Biaya Margin** atau **Bahan Baku Kain**, mereka harus mengklik satu per satu semua 10 jabatan di pabrik hanya untuk memastikan: *"Apakah ada jabatan staf yang tidak sengaja diberi akses ke HPP rahasia?"* Ini memakan waktu dan rawan kelalaian (*human error*).
3. **Bencana Jangkauan Seragam (*Uniform Scope Disaster*)**: Jika semua modul secara seragam diberi opsi jangkauan "Data Sendiri / Data Bawahan / Semua Data", admin pabrik akan bingung saat mengatur modul **Stok Kain Gudang**. Kain roll dan benang adalah milik pabrik (*Shared Resource*), bukan kepemilikan pribadi staf gudang. Jika staf gudang diset "Data Sendiri", sistem akan menampilkan stok kain kosong atau nol, sehingga alur potong kain terhenti dan sistem dikira rusak!

### Analogi Sederhana: "Papan Inventaris Kunci Ruangan Pabrik"
- **Model Per-Jabatan (Role-Centric)**: Seperti melihat dompet kunci milik satu orang satpam: *"Pak Joko memegang kunci apa saja?"*
- **Model 1 Modul 1 Card (Module-Centric)**: Seperti melihat lemari loker ruangan: *"Di pintu Gudang Bahan Baku & Ruang Direksi, divisi mana saja yang memegang kartu aksesnya?"*
- **Dynamic Scope (Ketersediaan Jangkauan)**: Pintu gudang kain bersifat umum untuk pabrik (`GLOBAL_ONLY`), sedangkan berkas komisi sales dan target potong operator bersifat spesifik per individu dan manajernya (`HIERARCHICAL`).

### Hasil Akhir yang Diharapkan
1. **Dua Sudut Pandang yang Sinkron (*Dual-View Mode*)**: Pengguna dapat beralih dengan satu klik antara mode **🗂️ 1 Modul 1 Card (Berdasarkan Modul)** dan mode **👤 Matriks Jabatan**.
2. **Dynamic Scope Guard**: Modul yang berstatus *Shared Resource* otomatis terkunci pada `🌐 Seluruh Pabrik` dengan label edukatif tanpa menampilkan tombol jangkauan yang menyesatkan.
3. **Demo Mode Superadmin**: Tersedia tombol instan `⚡ Demo Mode: Masuk Cepat (Superadmin Apps)` di layar login untuk mensimulasikan peran Platform Superadmin WeMade.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun fitur arsitektur wewenang dinamis seperti ini dari nol, ikuti urutan langkah Senior Engineer:

```
[ Step 0: Pure Domain Model ] ➔ [ Step 1: Sanitasi Aturan Scope ] ➔ [ Step 2: Entity Assignment Divisi ]
                                                                                   │
[ Step 5: Screen Orchestrator ] ⬅ [ Step 4: UI Presentation (Compose) ] ⬅ [ Step 3: Auth & Backend Superadmin ]
```

1. **Langkah 0: Definisikan Kapabilitas Jangkauan di `core` (Zero External Dependencies)**
   - Buat enum `ScopeCapability` (`GLOBAL_ONLY` vs `HIERARCHICAL`) dan sematkan ke `BusinessModule`.
2. **Langkah 1: Tulis Logika Sanitasi di `ModuleAccessConfig`**
   - Pastikan jika modul `isGlobalOnly`, jangkauan data tidak pernah bisa tersimpan sebagai `OWN_DATA_ONLY`.
3. **Langkah 2: Rancang Entitas `DepartmentModuleAssignment`**
   - Buat model wewenang modul per divisi yang fleksibel: bisa berlaku untuk *seluruh jabatan di divisi* (`appliesToAllRoles`) atau *jabatan spesifik*.
4. **Langkah 3: Perluas Endpoint Demo Auth di Backend Ktor & Client**
   - Dukung parameter `role=PLATFORM_SUPERADMIN` agar tim developer dapat masuk instan sebagai Superadmin Apps.
5. **Langkah 4: Bangun Komponen Presentasi Terisolasi (*Vertical Slice*)**
   - `ModuleCardView.kt` untuk kartu modul, `AssignDepartmentModal.kt` untuk dialog penugasan, dan `DynamicDataScopeSelector` di `ModuleMatrixCard.kt`.
6. **Langkah 5: Rakit di `DynamicRbacViewModel` & `DynamicRbacScreen`**
   - Tambahkan state `viewMode: RbacViewMode` dan jembatani sinkronisasi data dua arah.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Deklarasi Kapabilitas Modul di Domain Level (`BusinessModule.kt`)

```kotlin
enum class ScopeCapability(
    val displayName: String,
    val shortLabel: String,
    val description: String
) {
    GLOBAL_ONLY(
        displayName = "Seluruh Pabrik (Data Kolektif)",
        shortLabel = "Seluruh Pabrik",
        description = "Data inventaris, kalkulasi HPP, dan mesin dikelola kolektif untuk seluruh pabrik tanpa partisi kepemilikan."
    ),
    HIERARCHICAL(
        displayName = "Hirarkis (Sendiri & Bawahan)",
        shortLabel = "Hirarkis",
        description = "Mendukung isolasi data dokumen per pembuat (Data Sendiri) dan atasan komando (Data Bawahan)."
    );
}

enum class BusinessModule(
    val code: String,
    val displayName: String,
    val category: ModuleCategory,
    val description: String,
    val iconKey: String,
    val scopeCapability: ScopeCapability = ScopeCapability.GLOBAL_ONLY,
    val supportedScopes: Set<DataScope> = if (scopeCapability == ScopeCapability.GLOBAL_ONLY) {
        setOf(DataScope.ALL_TENANT_DATA)
    } else {
        setOf(DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA, DataScope.ALL_TENANT_DATA)
    }
) {
    CRM_SALES(
        code = "crm_sales",
        displayName = "Pelanggan & Prospek Sales",
        category = ModuleCategory.SALES,
        description = "Pencatatan prospek, riwayat follow-up negosiasi, dan kontak pelanggan konveksi.",
        iconKey = "handshake",
        scopeCapability = ScopeCapability.HIERARCHICAL
    ),
    INVENTORY(
        code = "inventory",
        displayName = "Bahan Baku & Stok Kain",
        category = ModuleCategory.LOGISTICS,
        description = "Penerimaan kain rol, stok benang, kancing, zipper, dan multi-satuan (Yard/Kg/Pcs).",
        iconKey = "package",
        scopeCapability = ScopeCapability.GLOBAL_ONLY
    );
    
    val isGlobalOnly: Boolean get() = scopeCapability == ScopeCapability.GLOBAL_ONLY
    val isHierarchical: Boolean get() = scopeCapability == ScopeCapability.HIERARCHICAL
}
```

**Mengapa blok ini ditulis begini?**
- `supportedScopes` dihitung secara otomatis saat class di-load berdasarkan `scopeCapability`. Modul global seperti `INVENTORY` otomatis hanya memiliki `setOf(ALL_TENANT_DATA)`.
- Sifat murni Kotlin: Tidak ada dependency Android, Compose, Ktor, atau Three.js di domain ini, sehingga dapat di-compile ke Android, iOS, Web (Wasm), Desktop, dan Server.

---

### Blok B: Sanitasi Otomatis Boundary Scope (`AccessLevel.kt`)

```kotlin
data class ModuleAccessConfig(
    val level: AccessLevel = AccessLevel.NONE,
    val scope: DataScope = DataScope.ALL_TENANT_DATA
) {
    val isAccessible: Boolean get() = level != AccessLevel.NONE
    val canWrite: Boolean get() = level.isAtLeast(AccessLevel.OPERATE)
    val canManage: Boolean get() = level.isAtLeast(AccessLevel.MANAGE)

    fun sanitizeFor(module: BusinessModule): ModuleAccessConfig {
        return if (module.isGlobalOnly && scope != DataScope.ALL_TENANT_DATA) {
            copy(scope = DataScope.ALL_TENANT_DATA)
        } else {
            this
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- Pola *Defensive Programming*: Mencegah bug data jika ada API request eksternal yang mengirimkan `scope = "own_data"` untuk modul stok bahan baku atau kalkulasi HPP. Fungsi `sanitizeFor` otomatis merestorasinya ke `ALL_TENANT_DATA`.

---

### Blok C: Entitas Penugasan Modul ke Divisi (`DepartmentModuleAssignment.kt`)

```kotlin
data class DepartmentModuleAssignment(
    val departmentId: String,
    val departmentName: String,
    val accessLevel: AccessLevel = AccessLevel.OPERATE,
    val specificRoleIds: Set<String> = emptySet(),
    val scope: DataScope = DataScope.ALL_TENANT_DATA
) {
    val appliesToAllRoles: Boolean get() = specificRoleIds.isEmpty()
}
```

**Mengapa blok ini ditulis begini?**
- Mewujudkan keinginan bisnis: Jika admin memilih divisi (misal *Divisi Gudang*) tanpa mencentang jabatan spesifik, maka `specificRoleIds` bernilai kosong, dan `appliesToAllRoles` bernilai `true`. Artinya, seluruh staf yang berada di divisi gudang langsung mendapatkan akses ke modul tersebut tanpa repot di-setting satu per satu.

---

### Blok D: Presentasi Dinamis Selector Jangkauan (`ModuleMatrixCard.kt`)

```kotlin
@Composable
private fun DynamicDataScopeSelector(
    module: BusinessModule,
    currentScope: DataScope,
    onSelectScope: (DataScope) -> Unit
) {
    if (module.isGlobalOnly) {
        // Badge informatif terkunci untuk Shared Enterprise Resource
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFEFF6FF))
                .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌐 Seluruh Pabrik", fontWeight = FontWeight.SemiBold, color = Color(0xFF1D4ED8))
                Text("(Data Bersama)", color = Color(0xFF64748B))
            }
        }
    } else {
        // Selector segmented untuk modul hirarkis (Sendiri, Bawahan, Semua)
        module.supportedScopes.forEach { scope ->
            val isSelected = scope == currentScope
            // render tombol scope pilihan...
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- UX yang tidak membingungkan: Menghilangkan opsi yang tidak valid langsung dari pandangan mata pengguna. Pengguna tidak akan bertanya-tanya mengapa tidak bisa memilih "Data Sendiri" pada stok gudang, karena badge informatif sudah menjelaskan status *Data Bersama*.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Mengapa Memilih Pendekatan Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Dual-View Switcher (1 Modul 1 Card & Matriks Jabatan)** | Membuang tampilan lama atau hanya menyediakan 1 mode | Owner pabrik berpikir per-modul (keamanan data), sedangkan HRD berpikir per-jabatan (onboarding staf baru). Menyediakan keduanya memberikan efisiensi maksimal. | Pengguna kebingungan saat ingin mengaudit satu karyawan baru jika hanya ada tampilan per-modul. |
| **Domain-Driven Dynamic Scope Capability** | Hardcoded if-else di UI Component (`if (module == INVENTORY)`) | Mendeklarasikan sifat data di Domain Model (`BusinessModule`) membuat aturan ini dapat dipakai di Frontend Compose, API Validation Ktor, dan SQL Query Filter. | Duplikasi logika di 5 tempat berbeda; rawan tidak sinkron saat modul baru ditambahkan. |
| **Pemisahan Wewenang 3-Layer** | Full dikendalikan Admin Apps (Sistem Request Tiket Manual) | Tenant dapat mengonfigurasi divisinya secara mandiri tanpa menunggu support WeMade, membuat sistem SaaS scalable 100%. | Tim developer kehabisan waktu melayani tiket operasional harian pabrik garmen. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Memaksa Semua Modul Memiliki Jangkauan "Data Sendiri"**
   - *Kenapa bahaya*: Staf gudang atau perencana produksi akan melihat data kosong karena dokumen pembelian bahan baku dibuat oleh orang lain.
   - *Solusi kita*: Kategori `GLOBAL_ONLY` yang mengunci jangkauan ke seluruh tenant.

2. **Jebakan 2: Menaruh Nama Divisi Statis di Level Master Platform**
   - *Kenapa bahaya*: Tiap pabrik garmen memiliki sebutan struktur organisasi sendiri (ada yang menyebut "PPIC", ada yang menyebut "Bagian Jadwal & Mesin"). Jika platform memaksakan, tenant tidak bisa menyesuaikan alur kerjanya.
   - *Solusi kita*: Modul Card mengambil daftar divisi langsung dari **Bagan Organisasi (OrgChart)** milik masing-masing tenant.

3. **Jebakan 3: Hardcoded Demo Credential di Client Saja**
   - *Kenapa bahaya*: Client menampilkan user superadmin, tetapi token JWT-nya palsu sehingga request ke backend HTTP 401 Unauthorized.
   - *Solusi kita*: Endpoint `/api/public/auth/demo?role=PLATFORM_SUPERADMIN` di server Ktor menghasilkan real signed JWT dengan claim role `PLATFORM_SUPERADMIN`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian dilakukan berlapis:
1. **Unit Test Pure Domain (`CustomRoleTest.kt`)**:
   ```kotlin
   @Test
   fun scope_capabilities_should_match_enterprise_garment_nature() {
       assertTrue(BusinessModule.INVENTORY.isGlobalOnly)
       assertEquals(setOf(DataScope.ALL_TENANT_DATA), BusinessModule.INVENTORY.supportedScopes)
       assertTrue(BusinessModule.CRM_SALES.isHierarchical)
   }
   ```
2. **Unit Test ViewModel State (`AuthViewModelTest.kt`)**:
   ```kotlin
   @Test
   fun demo_superadmin_login_authenticates_with_platform_superadmin_role() = testScope.runTest {
       viewModel.onEvent(LoginUiEvent.SubmitDemoSuperAdminLogin)
       testScheduler.advanceUntilIdle()
       assertEquals(Role.PLATFORM_SUPERADMIN, viewModel.uiState.value.authenticatedSession?.user?.role)
   }
   ```
3. **Hasil Eksekusi Gradle**:
   ```bash
   ./gradlew :app:shared:jvmTest :core:jvmTest :server:test
   # BUILD SUCCESSFUL in 2s, 27 actionable tasks, 0 failures
   ```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Tambahkan filter pencarian cepat di dalam modal `AssignDepartmentModal` jika suatu pabrik memiliki lebih dari 15 divisi khusus.
- [ ] **Tantangan 2**: Buat indikator visual progress bar di header `SingleModuleCard` yang menunjukkan persentase divisi yang sudah tercover oleh modul tersebut.
