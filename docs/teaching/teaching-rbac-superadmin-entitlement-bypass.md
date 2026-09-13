# 🎓 Modul Pembelajaran: Platform Superadmin Entitlement Bypass di Matriks RBAC

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Multi-Tenant SaaS, RBAC Access Decision Engine, Entitlement vs Permission, Anti-Lockout  
> **Prasyarat**: Pemahaman dasar tentang Kotlin Sealed Hierarchy / Data Classes, StateFlow/Combine, dan konsep multi-tenancy SaaS  
> **Referensi Task**: Fix RBAC & Navigation Menu Disappearing for Platform Superadmin when Module Disconnected from Tenant

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Bayangkan sebuah platform SaaS ERP manufaktur pakaian (*garment*). Di dalam sistem ini, setiap tenant pabrik (misalnya `PT WeMade Garmen Ekspor`) dapat berlangganan modul tertentu:
- CRM & Sales
- Bahan Baku & Stok
- Alur Pabrik (Pipeline)
- Hak Akses & Matriks RBAC (`DYNAMIC_RBAC`)

Ketika modul `DYNAMIC_RBAC` tidak disambungkan ke tenant (karena paket langganan tenant tidak mencakupnya, atau sedang dicabut via billing/entitlement):
1. Pengguna tenant biasa (Kepala Produksi, Staf Gudang) tentu **dilarang mengakses** modul tersebut (`AccessSource.NOT_ENTITLED`).
2. Bahkan Direktur / Owner pabrik sekalipun **tidak boleh membukanya**, karena pabrik mereka memang belum membayar paket itu.
3. **Namun, apa yang terjadi pada SaaS Platform Superadmin (`PLATFORM_SUPERADMIN`)?**  
   Superadmin adalah operator penyedia SaaS — orang yang bertugas mengonfigurasi sistem, memasang modul, mengatur matriks, dan membantu pabrik mengelola ERP-nya.
   Jika evaluasi entitlement tenant dilakukan secara membabi buta kepada siapa saja tanpa memeriksa peran platform:
   - Menu RBAC di sidebar/drawer navigasi **ikut lenyap** dari pandangan superadmin!
   - Ketika superadmin membuka URL `/rbac`, sistem memblokirnya dengan pesan *"Modul tidak termasuk dalam paket langganan pabrik ini"*.
   - **Kekacauan (Deadlock)**: Superadmin tidak bisa mengonfigurasi wewenang tenant karena menunya hilang dan layarnya terkunci oleh aturan yang sebenarnya ditujukan untuk pelanggan!

### Analogi Sederhana
Bayangkan sebuah gedung apartemen dengan unit-unit sewaan:
- **Tenant Owner (Pemilik Unit)**: Memiliki kunci master untuk seluruh kamar di unitnya sendiri. Namun, jika ia tidak membayar fasilitas *lounge* VIP atau *helipad*, kartu aksesnya tidak akan bisa membuka lift ke lantai tersebut.
- **Building Engineer / Superadmin Gedung**: Memegang kunci darurat & bypass panel teknis gedung. Sekalipun penyewa unit tertentu tidak berlangganan akses lift servis atau ruang kontrol listrik, teknisi gedung **harus selalu bisa masuk** ke ruang kontrol tersebut untuk melakukan *maintenance* dan instalasi saklar!

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu dihadapkan pada perbaikan bug logika akses di sistem DDD, **jangan mulai dari Composable UI**. UI hanyalah cermin dari state domain. Ikuti urutan ini:

```
Step 0: Domain Model (TestingPersona) 
   ↓
Step 1: Decision Service (AccessDecisionEngine & AccessSource)
   ↓
Step 2: Core Domain Unit Tests
   ↓
Step 3: Presentation State Holder (AuthViewModel)
   ↓
Step 4: UI Screen Handling (ModuleWorkspaceScreen & PersonaSwitcherDropdown)
```

1. **Langkah 0: Memperjelas Kontrak Domain (`TestingPersona`)**
   - Sebelumnya, `isOwnerOrSuperAdmin: Boolean` mencampuradukkan konsep "Owner Pabrik" dan "Superadmin SaaS".
   - Kita pisahkan secara eksplisit dengan menambahkan `val isPlatformSuperAdmin: Boolean = false`.
   - Perbarui invarian `require` agar persona berjabatan tidak bisa membawa bypass.

2. **Langkah 1: Menambahkan Status Sumber Wewenang (`AccessSource`) & Logika Evaluasi**
   - Tambahkan enum `SUPERADMIN_BYPASS` ke dalam `AccessSource`.
   - Di dalam `AccessDecisionEngine.explain()`, posisikan evaluasi `persona.isPlatformSuperAdmin` **sebelum** pemeriksaan `grantedModules != null && module !in grantedModules`.

3. **Langkah 2: Tulis Test Pembuktian (Regression & Boundary Tests)**
   - Uji skenario:
     - Tenant owner tanpa modul di paket: ditolak dengan `NOT_ENTITLED`.
     - Platform superadmin tanpa modul di paket: **lolos** dengan wewenang `MANAGE` dan asal `SUPERADMIN_BYPASS`.

4. **Langkah 3: Tautkan Sesi Autentikasi ke Domain Persona (`AuthViewModel`)**
   - Saat sesi login pengguna dimuat ulang (reload), setel `isPlatformSuperAdmin = (user.customRoleId == null && user.role == Role.PLATFORM_SUPERADMIN)`.

5. **Langkah 4: Sesuaikan Tampilan UI & Badge Status**
   - Tambahkan `AccessSource.SUPERADMIN_BYPASS` ke pemetaan warna badge di `ModuleWorkspaceScreen.kt`.
   - Pastikan kartu bypass di `PersonaSwitcherDropdown.kt` mengenali status superadmin aktif.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pembedaan Superadmin di Entity Domain (`TestingPersona.kt`)
```kotlin
data class TestingPersona(
    val userId: String,
    val name: String,
    val tenantId: TenantId,
    val tenantSlug: String,
    val departmentId: String?,
    val departmentName: String,
    val roleId: RoleId?,
    val roleTitle: String,
    val isOwnerOrSuperAdmin: Boolean = false,
    val isPlatformSuperAdmin: Boolean = false,
    val sourceEmployeeId: OrgNodeId? = null
) {
    init {
        require(name.isNotBlank()) { "Nama persona tidak boleh kosong" }
        // Persona yang membawa jabatan (roleId != null) sengaja dilarang memakai bypass.
        // Memilih jabatan berarti penguji ingin melihat persis apa yang dilihat jabatan tersebut!
        require(!((isOwnerOrSuperAdmin || isPlatformSuperAdmin) && roleId != null)) {
            "Persona berjabatan tidak boleh memakai bypass; hapus roleId atau matikan bypass"
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
- `isPlatformSuperAdmin` memiliki nilai default `false`, sehingga seluruh pemanggilan kode lama (seperti `fromEmployee` atau factory generator) tetap aman dan tidak bocor wewenang superadmin secara tak sengaja.
- Invarian `require` menjaga integritas simulasi: jika seorang superadmin menggunakan switcher untuk menyamar sebagai "Staff Gudang", wewenang superadmin-nya tidak boleh bocor ke simulasi tersebut.

---

### Blok B: Penataan Urutan Evaluasi Wewenang (`AccessDecisionEngine.kt`)
```kotlin
enum class AccessSource(val label: String) {
    ROLE("Jabatan"),
    DEPARTMENT("Divisi"),
    OWNER_BYPASS("Owner (bypass)"),
    SUPERADMIN_BYPASS("Superadmin (bypass)"),
    NOT_ENTITLED("Tidak termasuk paket"),
    NONE("Tidak ada")
}

// ... di dalam fungsi explain():

// 1. Superadmin platform melewati batasan paket (entitlement) maupun matriks wewenang.
// Superadmin adalah pengelola SaaS yang bertugas mengonfigurasi seluruh tenant.
// Menunya tidak boleh ikut hilang ketika modul diputus dari tenant!
if (persona.isPlatformSuperAdmin) {
    return AccessDecision(
        config = ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA)
            .sanitizeFor(module),
        source = AccessSource.SUPERADMIN_BYPASS,
        fromRole = roleAccess,
        fromDepartment = ModuleAccessConfig(AccessLevel.NONE)
    )
}

// 2. Entitlement tenant diperiksa sebelum wewenang internal tenant (termasuk bypass Owner).
// Modul yang tidak disambungkan ke sebuah pabrik bukan modul yang "Owner-nya berwenang tapi
// stafnya tidak" — modul itu memang tidak ada untuk pabrik tersebut.
if (grantedModules != null && module !in grantedModules) {
    val denied = ModuleAccessConfig(AccessLevel.NONE)
    return AccessDecision(
        config = denied,
        source = AccessSource.NOT_ENTITLED,
        fromRole = roleAccess,
        fromDepartment = denied
    )
}
```
**Mengapa blok ini ditulis begini?**
- **Urutan adalah segalanya**:
  - Pengecekan `persona.isPlatformSuperAdmin` diletakkan **paling atas** sebelum `grantedModules`.
  - Mengapa bypass `isOwnerOrSuperAdmin` (Owner pabrik) diletakkan **setelah** `grantedModules`? Karena Owner pabrik adalah entitas internal tenant. Jika pabrik tidak bayar paket PPIC, Owner pabrik tidak boleh pakai PPIC.
  - Sedangkan Superadmin adalah entitas platform lintas-tenant. Superadmin harus tetap bisa mengonfigurasi dan melihat modul pabrik tersebut.

---

### Blok C: State Hydration di Session Client (`AuthViewModel.kt`)
```kotlin
private fun restorePersonaFrom(session: UserSession) {
    val user = session.user
    val tenantId = user.tenantId ?: return
    val slug = session.tenantSlug ?: "wemade-demo"

    policyRepository.setPersona(
        TestingPersona(
            userId = user.id.value,
            name = user.username.value,
            tenantId = tenantId,
            tenantSlug = slug,
            departmentId = user.departmentId,
            departmentName = user.departmentId ?: "Tanpa Divisi",
            roleId = user.customRoleId?.let { com.eventverse.app.domain.rbac.RoleId(it) },
            roleTitle = user.role.name,
            isOwnerOrSuperAdmin = user.customRoleId == null &&
                (user.role == Role.TENANT_ADMIN || user.role == Role.PLATFORM_SUPERADMIN),
            isPlatformSuperAdmin = user.customRoleId == null && user.role == Role.PLATFORM_SUPERADMIN
        )
    )
    policyRepository.load(tenantId, slug)
}
```
**Mengapa blok ini ditulis begini?**
- Ketika superadmin me-refresh halaman browser, sesi dibaca dari LocalStorage dan `restorePersonaFrom` dipanggil.
- Dengan menyetel `isPlatformSuperAdmin` berdasarkan `user.role == Role.PLATFORM_SUPERADMIN`, persona hasil restorasi langsung memegang wewenang superadmin tanpa menunggu panggilan network.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Alasan Memilih Pendekatan Kita | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Eksplisit `isPlatformSuperAdmin` di Pure Domain Layer** | Membuat pengecualian khusus (`if (isSuperadmin)`) di Composable UI `NavMenu.kt` atau `App.kt` | UI tetap deklaratif dan bebas dari *business logic*. `NavMenu` murni menerima `permissions` dan tidak perlu tahu siapa yang sedang login. | Logika wewenang bocor dan tersebar di berbagai layer UI; jika ada layar baru, aturan superadmin lupa disematkan dan bug berulang. |
| **Sumber Asal Akses `SUPERADMIN_BYPASS` yang Transparan** | Menyamarkan superadmin sebagai `OWNER_BYPASS` | Audit trail dan panel penjelas (*provenance*) di layar dapat membedakan secara jujur mengapa modul terbuka (karena bypass SaaS, bukan karena jabatan tenant). | Membingungkan pengujian: penguji mengira role tenant-nya yang memberikan izin, padahal hanya efek bypass. |
| **Urutan Evaluasi Tingkat Engine (Superadmin → Entitlement → Owner → Matrix)** | Menambah flag `allowSuperadmin` ke `grantedModules` di server | Server cukup mengirim status entitlement faktual tenant. Filter peran dievaluasi deterministik di Domain Engine. | Mengotori database dan API entitlement tenant dengan payload yang tidak ada hubungannya dengan status kontrak tenant. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Menyatukan Tenant Owner dan Platform Superadmin dalam Satu Flag**
   - *Kenapa bahaya*: Pemilik pabrik (*tenant owner*) dan operator SaaS (*platform superadmin*) memiliki yurisdiksi yang berbeda. Owner tunduk pada paket langganan pabriknya, sedangkan Superadmin mengelola paket langganan tersebut. Menyatukannya membuat salah satu dari dua skenario pasti rusak (antara owner bisa mencuri modul yang belum dibeli, atau superadmin terkunci dari sistemnya sendiri).
   - *Solusi kita*: Pisahkan `isOwnerOrSuperAdmin` (lingkup tenant) dengan `isPlatformSuperAdmin` (lingkup platform).

2. **Jebakan 2: Membiarkan Bypass Bocor Saat Simulasi Jabatan / Karyawan**
   - *Kenapa bahaya*: Superadmin sering masuk ke mode simulasi (persona testing) untuk memeriksa *"apa yang dilihat oleh staf gudang?"*. Jika bypass superadmin tetap menyala saat memilih jabatan staf gudang, staf gudang akan terlihat memiliki akses ke seluruh modul! Pengujian menjadi tidak valid.
   - *Solusi kita*: Enforce invariant `require(!((isOwnerOrSuperAdmin || isPlatformSuperAdmin) && roleId != null))` langsung di konstruktor `TestingPersona`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian wewenang diuji pada layer Domain murni tanpa framework Compose:

```kotlin
@Test
fun `ownerBypass should NOT resurrect a module the tenant does not have`() {
    // Tenant Owner TETAP ditolak jika tenant belum berlangganan
    val decision = AccessDecisionEngine.explain(
        persona = persona(role = null, isOwner = true, isSuperAdmin = false),
        module = BusinessModule.DYNAMIC_RBAC,
        role = null,
        assignments = emptyList(),
        grantedModules = BusinessModule.entries.toSet() - BusinessModule.DYNAMIC_RBAC
    )

    assertEquals(AccessSource.NOT_ENTITLED, decision.source)
    assertFalse(decision.config.isAccessible)
}

@Test
fun `superadminBypass should retain access to module even when not entitled to tenant`() {
    // Platform Superadmin TETAP BISA AKSES untuk tujuan konfigurasi & administrasi
    val decision = AccessDecisionEngine.explain(
        persona = persona(role = null, isOwner = true, isSuperAdmin = true),
        module = BusinessModule.DYNAMIC_RBAC,
        role = null,
        assignments = emptyList(),
        grantedModules = BusinessModule.entries.toSet() - BusinessModule.DYNAMIC_RBAC
    )

    assertEquals(AccessSource.SUPERADMIN_BYPASS, decision.source)
    assertEquals(AccessLevel.MANAGE, decision.config.level)
    assertTrue(decision.config.isAccessible)
    assertFalse(decision.blockedByEntitlement)
}
```

Perintah verifikasi lokal:
```bash
./gradlew :core:jvmTest
./gradlew :app:shared:jvmTest
./gradlew :server:test
./gradlew :app:webApp:wasmJsBrowserDevelopmentWebpack --no-configuration-cache
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Amati kartu `ModuleWorkspaceScreen`. Ketika kamu masuk sebagai Platform Superadmin dan membuka modul yang sebenarnya tidak disambungkan ke tenant, badge apa yang muncul di panel asal wewenang? Mengapa badge tersebut berwarna hijau (*Success*) dan bukan amber (*Warning*)?
- [ ] **Tantangan 2**: Cobalah buka modal Persona Switcher di top bar, lalu pilih persona karyawan riil (misalnya `Budi Santoso · Kepala Penjualan`). Apakah modul yang tidak disambungkan ke tenant tetap hilang dari pandangan persona tersebut?
