# 🎓 Modul Pembelajaran: Sinkronisasi Kartu Jabatan RBAC dengan Wewenang Bawaan & Penugasan Divisi

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Role-Based Access Control (RBAC), Compose Multiplatform, State Projection, Highest Privilege Union  
> **Prasyarat**: Dasar Kotlin, pemahaman dasar Domain-Driven Design (DDD) dan Compose Multiplatform  
> **Referensi Task**: Fix RBAC Role Card Accessible Modules Projection (`teaching-rbac-role-card-accessible-modules`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata (The Discrepancy Bug)
Di sebuah sistem ERP konveksi & manufaktur, wewenang pengguna berasal dari **dua sumbu**:
1. **Wewenang Bawaan Jabatan** (`CustomRole.modulePermissions`): Hak dasar yang melekat pada peran pekerjaan (misalnya: *Staff Gudang & Logistik* memiliki hak bawaan `VIEW` pada modul Bagan Organisasi, atau *PPIC* memiliki wewenang mengelola Alur Pabrik).
2. **Penugasan Tingkat Divisi** (`DepartmentModuleAssignment`): Modul-modul operasional yang ditugaskan ke seluruh anggota divisi tertentu (misalnya: Divisi Gudang & Logistik ditugaskan modul Bahan Baku dan Packing).

Di backend dan runtime client, sistem mengevaluasi izin menggunakan aturan **Highest Privilege Union** melalui `AccessDecisionEngine`: jika salah satu sumbu memberikan izin `VIEW` atau `OPERATE`, maka pengguna tersebut berhak membuka modul tersebut.

Namun di antarmuka administrasi `/rbac` pada tab **Per Jabatan**, kartu jabatan sebelumnya hanya melakukan iterasi pada tabel `assignments` (penugasan divisi) dan **mengabaikan wewenang bawaan jabatan (`role.modulePermissions`)** kecuali untuk role Owner via *hardcoded fallback*.

Akibatnya terjadi kontradiksi fatal:
- Di layar matriks RBAC (Gambar 1), kartu *Staff Gudang & Logistik* hanya mencantumkan 2 modul operasional (`Bahan Baku` & `Packing`), tanpa modul `Bagan Organisasi`.
- Namun ketika admin beralih persona menjadi *Staff Gudang & Logistik* dan membuka `/org-chart` (Gambar 2), halaman Bagan Organisasi ternyata **bisa dibuka**!

Admin dan manajer pabrik menjadi bingung: *"Kenapa di kartu RBAC tertulis tidak punya akses ke bagan, tapi orangnya bisa buka halaman bagan?"*

### Analogi Sederhana
Bayangkan kartu akses masuk gedung kantor:
- Dari **divisi**, Anda mendapat stempel untuk membuka Ruang Gudang dan Ruang Ekspedisi.
- Namun dalam **kontrak jabatan** Anda, HRD sudah memberikan izin untuk melihat papan pengumuman struktur organisasi di lobi utama.
- Jika resepsionis hanya memeriksa daftar stempel divisi Anda, resepsionis akan mengira Anda cuma boleh ke gudang. Padahal kunci pintu lobi tetap terbuka ketika kartu Anda ditempelkan ke sensor RFID karena chip kartu Anda membawa izin jabatan tersebut!

### Hasil Akhir yang Diharapkan
Kartu di tab **Per Jabatan** harus memproyeksikan wewenang gabungan (Union) secara utuh dan jujur:
- Modul dari penugasan divisi ditampilkan dengan label `"Dari [Nama Divisi] (Full Divisi)"`.
- Modul dari wewenang bawaan jabatan ditampilkan dengan label `"Khusus Jabatan Ini"`.
- Jika sebuah modul mendapat izin dari kedua sumbu, level wewenang tertinggi yang menang (*Highest Privilege Union*).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun atau memperbaiki proyeksi data kartu ini dari awal, ikuti urutan berikut:

1. **Langkah 0: Pahami Single Source of Truth Otorisasi (`core`)**
   - Pelajari kontrak `AccessDecisionEngine` di lapisan domain. Ketahui bahwa wewenang efektif adalah hasil gabungan (`fromRole` vs `fromDepartment`).
2. **Langkah 1: Hindari Duplikasi Logika di UI Layer**
   - Jangan menyalin logika evaluasi secara sembarangan di dalam block composable yang bersarang. Ekstrak fungsi perhitungan menjadi fungsi murni (*pure function*) yang tidak bergantung pada Compose UI context.
3. **Langkah 2: Tulis Fungsi Proyeksi Murni (`resolveAccessibleModulesForRole`)**
   - Lakukan iterasi pada seluruh `BusinessModule.entries`.
   - Untuk setiap modul, periksa penugasan spesifik role, penugasan full divisi, dan wewenang bawaan `role.getAccess(mod)`.
   - Gabungkan dengan aturan wewenang tertinggi menang.
4. **Langkah 3: Sambungkan ke Composable (`remember` Projection)**
   - Gunakan `remember(role, dept, assignments) { resolveAccessibleModulesForRole(...) }` di dalam UI rendering loop.
5. **Langkah 4: Tulis Unit Test Pembuktian (`RoleCardViewTest`)**
   - Buat skenario pengujian unit tanpa Compose engine untuk memastikan modul gabungan terhitung dengan benar.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Berikut pembedahan fungsi pure `resolveAccessibleModulesForRole` di [`RoleCardView.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/rbac/components/RoleCardView.kt):

```kotlin
fun resolveAccessibleModulesForRole(
    role: CustomRole,
    dept: Department?,
    assignments: Map<BusinessModule, List<DepartmentModuleAssignment>>
): List<RoleAccessibleModule> {
    val list = mutableListOf<RoleAccessibleModule>()
    val roleIdStr = role.id.value
    val deptIdStr = dept?.id?.value

    BusinessModule.entries.forEach { mod ->
        val assignList = assignments[mod].orEmpty()
        val specific = assignList.find { it.specificRoleIds.contains(roleIdStr) }
        val deptWide = if (deptIdStr != null) {
            assignList.find { it.departmentId == deptIdStr && it.appliesToAllRoles }
        } else null

        val roleCfg = role.getAccess(mod)
        val hasRoleAccess = roleCfg.isAccessible

        when {
            // Cabang 1: Ada penugasan khusus pada role ini untuk modul ini
            specific != null -> {
                val roleWins = hasRoleAccess && roleCfg.level.weight > specific.accessLevel.weight
                list.add(
                    RoleAccessibleModule(
                        module = mod,
                        accessLevel = if (roleWins) roleCfg.level else specific.accessLevel,
                        scope = if (roleWins) roleCfg.scope else specific.scope,
                        isSpecificToRole = true,
                        departmentName = specific.departmentName,
                        assignment = specific
                    )
                )
            }

            // Cabang 2: Ada penugasan tingkat divisi (seluruh anggota divisi)
            deptWide != null -> {
                if (hasRoleAccess && roleCfg.level.weight > deptWide.accessLevel.weight) {
                    // Wewenang bawaan jabatan lebih tinggi dari divisi
                    list.add(
                        RoleAccessibleModule(
                            module = mod,
                            accessLevel = roleCfg.level,
                            scope = roleCfg.scope,
                            isSpecificToRole = true,
                            departmentName = dept?.displayName ?: "Bawaan Jabatan",
                            assignment = null
                        )
                    )
                } else {
                    list.add(
                        RoleAccessibleModule(
                            module = mod,
                            accessLevel = deptWide.accessLevel,
                            scope = deptWide.scope,
                            isSpecificToRole = false,
                            departmentName = deptWide.departmentName,
                            assignment = deptWide
                        )
                    )
                }
            }

            // Cabang 3: Modul tidak ditugaskan ke divisi, tapi role memilikinya di wewenang bawaan
            hasRoleAccess -> {
                list.add(
                    RoleAccessibleModule(
                        module = mod,
                        accessLevel = roleCfg.level,
                        scope = roleCfg.scope,
                        isSpecificToRole = true,
                        departmentName = dept?.displayName ?: "Bawaan Jabatan",
                        assignment = null
                    )
                )
            }
        }
    }

    return list
}
```

### Mengapa blok ini ditulis begini?

1. **Iterasi `BusinessModule.entries`, Bukan Hanya Kunci `assignments`**:
   Kode lama hanya melakukan `assignments.forEach { ... }`. Jika sebuah modul tata kelola seperti `ORG_CHART` belum pernah ditugaskan lewat tabel divisi, modul itu tidak akan pernah diproses sama sekali! Dengan mengiterasi `BusinessModule.entries`, setiap modul bisnis dievaluasi secara adil.
2. **Pengecekan `level.weight`**:
   Ketika wewenang ada di kedua sumbu, kita tidak boleh menebak siapa yang menang. Enum `AccessLevel` memiliki properti `weight` (`NONE=0, VIEW=1, OPERATE=2, MANAGE=3`). Membandingkan `weight` menjamin prinsip *Highest Privilege Union*.
3. **Preservasi Objek `assignment` untuk Aksi Edit / Hapus**:
   Jika modul berasal dari penugasan spesifik (`specific != null`), kita tetap menyimpan referensi `assignment = specific`. Hal ini penting agar tombol aksi **Edit** dan **Hapus** pada kartu tetap berfungsi normal untuk menghapus override wewenang.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Pure Helper Function di Luar Composable** | Menulis logika perhitungan langsung di dalam lambda `remember` di dalam Composable | Fungsi murni dapat langsung diuji dengan unit test kilat tanpa perlu framework Compose UI / Robolectric / Android testing. | Jika ditaruh di dalam Composable, logika tidak bisa diuji secara terisolasi dan rawan memicu re-komposisi yang tidak efisien. |
| **Highest Privilege Union (`weight` comparison)** | Menganggap salah satu sumbu selalu menimpa sumbu lain (*Strict Precedence*) | Konsisten dengan `AccessDecisionEngine` domain service di core. Pengguna tidak akan mengalami bug inkonsistensi antara tampilan UI dan hak akses riil saat navigasi. | Layar kartu akan menampilkan hak akses yang berbeda dari apa yang diizinkan oleh rute URL dan gerbang otentikasi. |
| **Iterasi `BusinessModule.entries`** | Menggabungkan map secara manual lalu konversi list | Menjamin urutan modul konsisten sesuai enum domain dan tidak ada modul yang lolos dari evaluasi. | Rentan *missing keys* untuk modul baru yang ditambahkan di kemudian hari. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Hardcoded Fallback Khusus Role Tertentu (`if role.name == "owner"`)**
   - *Kenapa bahaya*: Developer sebelumnya menulis `if (list.isEmpty() && role.name.contains("owner"))`. Ini jebakan klasik! Begitu ada peran lain seperti *Staff Gudang* atau *Kepala Sales* yang punya hak bawaan, kode tersebut mengabaikannya karena hanya mengistimewakan kata kunci "owner".
   - *Solusi elegan*: Buat aturan umum berbasis kontrak domain `role.getAccess(mod).isAccessible` untuk **semua** role tanpa memandang nama.
2. **Jebakan 2: Menampilkan Modul dengan `AccessLevel.NONE`**
   - *Kenapa bahaya*: Di database, default permission sering kali berupa object dengan level `NONE`. Jika developer hanya mengecek keberadaan kunci (`role.modulePermissions.containsKey(mod)`), kartu akan membludak menampilkan modul-modul yang sebenarnya ditutup!
   - *Solusi elegan*: Selalu gunakan helper domain `roleCfg.isAccessible` (yang memeriksa `level != AccessLevel.NONE`).

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Kita memverifikasinya melalui unit test murni di [`RoleCardViewTest.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonTest/kotlin/com/eventverse/app/presentation/rbac/RoleCardViewTest.kt):

```kotlin
@Test
fun `resolveAccessibleModulesForRole includes both department assignments and role permissions`() {
    val warehouseRole = CustomRole(
        id = RoleId("role-warehouse"),
        tenantId = tenantId,
        name = "Staff Gudang & Logistik",
        description = "Staff gudang",
        departmentId = warehouseDept.id.value,
        modulePermissions = mapOf(
            BusinessModule.ORG_CHART to ModuleAccessConfig(AccessLevel.VIEW, DataScope.SUBORDINATE_DATA),
            BusinessModule.INVENTORY to ModuleAccessConfig(AccessLevel.OPERATE, DataScope.ALL_TENANT_DATA),
            BusinessModule.CRM_SALES to ModuleAccessConfig(AccessLevel.NONE)
        )
    )

    val assignments = mapOf(...)

    val accessible = resolveAccessibleModulesForRole(warehouseRole, warehouseDept, assignments)
    val moduleKeys = accessible.map { it.module }.toSet()

    // Verifikasi: Mencakup penugasan divisi DAN wewenang bawaan jabatan
    assertTrue(moduleKeys.contains(BusinessModule.INVENTORY))
    assertTrue(moduleKeys.contains(BusinessModule.FULFILLMENT))
    assertTrue(moduleKeys.contains(BusinessModule.ORG_CHART))

    // Verifikasi: Modul NONE tidak boleh muncul
    assertFalse(moduleKeys.contains(BusinessModule.CRM_SALES))
}
```

Jalankan pengujian via terminal Gradle:
```bash
./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.rbac.RoleCardViewTest"
```
Hasil: `BUILD SUCCESSFUL` (semua assertion lolos).

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka browser ke `http://localhost:3000/rbac`, pilih tab **Per Jabatan**, dan periksa kartu *Staff Gudang & Logistik*. Pastikan kartu kini mencantumkan modul *Bagan Struktur Organisasi & Karyawan* dengan badge *Akses Lihat* dan *Tim & Bawahan*.
- [ ] **Tantangan 2**: Buat role kustom baru di tab "Per Jabatan" tanpa menugaskannya ke divisi mana pun, lalu beri akses `OPERATE` ke modul `Pola & Sampling Order`. Amati apakah kartu langsung menampilkan modul tersebut secara instan tanpa perlu reload.
