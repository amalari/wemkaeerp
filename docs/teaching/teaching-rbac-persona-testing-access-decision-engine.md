# 🎓 Modul Pembelajaran: Persona Pengujian Dinamis & Access Decision Engine

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: RBAC, Domain Service, JWT Claims, Derived State (StateFlow), Flyway Migration, Compose Multiplatform
> **Prasyarat**: Paham dasar DDD di repo ini, tahu apa itu JWT, pernah membaca `AccessLevel.kt` dan `CustomRole.kt`
> **Referensi Task**: Dynamic User Testing & RBAC Access View

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Admin pabrik mengatur matriks RBAC di layar Hak Akses. Ia menutup akses HPP untuk Divisi Sales,
menekan simpan, muncul toast hijau. Lalu ia bertanya satu pertanyaan yang sangat wajar:

> "Sekarang kalau Budi login, dia lihat apa?"

Sebelum task ini, tidak ada cara menjawabnya selain membuatkan akun Budi, keluar, login sebagai
Budi, dan melihat sendiri. Lebih buruk lagi: **perubahan tadi tidak pernah benar-benar tersimpan.**
Penugasan divisi hanya hidup di state view model (`createDefaultModuleAssignments()`), jadi toast
hijaunya berbohong — refresh halaman, semuanya kembali seperti semula.

Ada satu bug lagi yang lebih berbahaya dan lebih sunyi. Endpoint `/me` dulu menulis:

```kotlin
val role = runCatching { Role.valueOf(roleName) }.getOrDefault(Role.TENANT_ADMIN)
```

Baca ulang pelan-pelan: **kalau identitas tidak bisa dikenali, berikan wewenang paling luas.**
Itu kebalikan dari yang aman. Token dengan role tak dikenal — rusak, kedaluwarsa formatnya, atau
dibuat versi lama aplikasi — masuk sebagai admin tenant.

### Analogi Sederhana

Bayangkan gedung pabrik dengan kartu akses. Ada **dua** cara seseorang bisa membuka pintu ruang
kain:

1. **Lewat jabatannya** — "Kepala Gudang boleh masuk ruang kain." (`CustomRole`)
2. **Lewat divisinya** — "Semua orang Divisi Gudang boleh masuk ruang kain." (`DepartmentModuleAssignment`)

Pertanyaan desainnya: kalau seseorang punya keduanya dengan tingkat berbeda, yang mana yang
berlaku? Jawaban kita: **yang paling longgar** — akan dibahas di bagian 4, dan alasannya bukan
"biar gampang".

### Hasil Akhir

Penguji menekan capsule 🧪 di top bar, memilih "Budi Santoso — Kepala Penjualan", dan **seketika**:
menu navigasi berubah, badge wewenang muncul di tiap menu ("Penuh" / "Input" / "Lihat" /
"Terkunci"), dan membuka modul mana pun memperlihatkan banner mode kerja yang sesuai. Tanpa
logout. Dengan token JWT asli dari server.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Ini bagian yang paling sering salah urut. Godaannya besar untuk langsung membuat
`PersonaSwitcherDropdown` karena itu yang kelihatan. Jangan.

### Langkah 0: Baca dulu, jangan tulis apa pun

Sebelum satu baris pun ditulis, saya memeriksa: apakah `DepartmentModuleAssignment` punya tabel?
Apakah `CustomRole.departmentId` benar-benar tersimpan? Apakah `Role` enum bisa menampung
`"role-sales-head"`?

Jawabannya berturut-turut: **tidak, tidak, tidak.** Tiga temuan itu mengubah seluruh rencana.
Kalau saya mulai dari UI, ketiganya baru ketahuan setelah UI-nya jadi — dan semuanya harus
dibongkar.

> **Pelajaran**: rencana yang belum dicek ke kode adalah hipotesis, bukan rencana.

### Langkah 1: Database dulu (`V17__*.sql`)

Karena tiga temuan di atas adalah masalah **bentuk data**, dan tidak ada logika di atasnya yang
bisa benar kalau bentuk datanya tidak ada.

### Langkah 2: Domain murni (`core/`)

`TestingPersona` dan `AccessDecisionEngine`. Tanpa I/O, tanpa Ktor, tanpa Compose. Bisa diuji
dalam milidetik.

### Langkah 3: Test domain — **sebelum** infrastruktur

11 test ditulis di sini. Kalau aturan "hak tertinggi menang" salah, saya ingin tahu sekarang,
bukan setelah tiga lapisan menumpuk di atasnya.

### Langkah 4: Infrastruktur server

Tabel Exposed, repository, route, claim JWT.

### Langkah 5: Client infrastructure

`AuthApiClient.loginPersona`, `RbacApiClient.getModuleAssignments`.

### Langkah 6: State turunan (`RbacAccessPolicyRepository`)

Satu tempat yang memegang persona + jabatan + penugasan, dan **menghitung** wewenang dari
ketiganya.

### Langkah 7: UI — paling akhir

Karena pada titik ini UI hanya perlu membaca state yang sudah benar.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Mesin Keputusan Akses (Domain Murni)

```kotlin
object AccessDecisionEngine {
    fun evaluate(
        persona: TestingPersona,
        module: BusinessModule,
        role: CustomRole?,
        assignments: List<DepartmentModuleAssignment>
    ): ModuleAccessConfig {
        if (persona.isOwnerOrSuperAdmin) {
            return ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA)
                .sanitizeFor(module)
        }

        val fromRole = role?.getAccess(module) ?: ModuleAccessConfig(AccessLevel.NONE)
        val fromDepartment = resolveDepartmentAccess(persona, assignments)

        val winner = when {
            fromDepartment == null -> fromRole
            fromDepartment.accessLevel.weight > fromRole.level.weight ->
                ModuleAccessConfig(fromDepartment.accessLevel, fromDepartment.scope)
            else -> fromRole
        }

        return winner.sanitizeFor(module)
    }
}
```

**Mengapa blok ini ditulis begini?**

1. **`object`, bukan `class`.** Tidak ada state yang perlu disimpan. Fungsi murni: input sama →
   output sama, selamanya. Ini yang membuatnya bisa diuji tanpa mock apa pun.

2. **Owner bypass ditaruh paling atas.** Bukan pintasan kenyamanan. Tanpa ini, admin bisa
   mengunci dirinya sendiri keluar dari layar RBAC — dan layar RBAC adalah satu-satunya tempat
   untuk membukanya kembali. Itu deadlock yang hanya bisa dibereskan lewat SQL manual.

3. **`sanitizeFor(module)` di setiap jalur keluar.** Modul `GLOBAL_ONLY` (inventaris, HPP, jadwal
   mesin) tidak punya konsep "data milik siapa" — kainnya milik pabrik. Kalau scope sempit lolos
   ke sana, layarnya tampak kosong, dan staf melaporkannya sebagai sistem rusak. Perhatikan bahwa
   ia dipanggil di **dua** tempat, termasuk di jalur owner. Melewatkan satu jalur saja sudah cukup
   untuk memunculkan bug itu.

4. **Perbandingan lewat `.weight`, bukan urutan enum.** `AccessLevel` sudah menyediakan `weight`
   dan `isAtLeast()`. Membandingkan `ordinal` akan diam-diam rusak begitu ada yang menyisipkan
   nilai enum baru di tengah.

### Blok B: Mengapa satu divisi bisa punya banyak penugasan

```kotlin
private fun resolveDepartmentAccess(
    persona: TestingPersona,
    assignments: List<DepartmentModuleAssignment>
): DepartmentModuleAssignment? {
    val departmentId = persona.departmentId ?: return null
    return assignments
        .filter { it.departmentId == departmentId }
        .filter { it.appliesToAllRoles || persona.roleId?.value in it.specificRoleIds }
        .maxByOrNull { it.accessLevel.weight }
}
```

**Mengapa blok ini ditulis begini?**

Admin lazim mengatur dua hal sekaligus: *"seluruh Divisi Sales boleh lihat CRM"* **dan**
*"khusus Kepala Sales boleh kelola CRM"*. Keduanya sah dan hidup berdampingan.

Tiga operasi berurutan menjawabnya: saring per divisi → saring per jabatan yang cocok →
ambil yang tertinggi. Kalau langkah ketiga diganti `firstOrNull()`, hasilnya bergantung pada
urutan baris di database — bug yang muncul dan hilang sendiri, jenis paling menyiksa.

### Blok C: Migrasi — Aditif, Selalu

```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS department_id VARCHAR(64)
    REFERENCES departments(id) ON DELETE SET NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS custom_role_id VARCHAR(64)
    REFERENCES custom_roles(id) ON DELETE SET NULL;
```

**Mengapa blok ini ditulis begini?**

1. **`ON DELETE SET NULL`, bukan `CASCADE`.** Baca keras-keras apa arti `CASCADE` di sini:
   *"menghapus satu divisi akan menghapus semua karyawannya."* Itu bencana. Yang benar: divisinya
   hilang, orangnya tetap ada tanpa divisi.

2. **Keduanya nullable.** Superadmin platform tidak punya tenant, apalagi divisi. Kolom `NOT NULL`
   akan membuat migrasi gagal di baris pertama pada database yang sudah terisi.

3. **`IF NOT EXISTS`.** Migrasi harus aman dijalankan ulang.

4. **Backfill lewat kode divisi, bukan id divisi.** Versi pertama migrasi ini saya tulis begini —
   dan langsung gagal:

```sql
-- ❌ Gagal: dept-exec sudah dihapus oleh V7
UPDATE custom_roles SET department_id = 'dept-exec' WHERE id = 'role-owner';
-- ERROR: violates foreign key constraint "custom_roles_department_id_fkey"
```

Id divisi adalah **data milik tenant** — bisa ditambah, diganti, dihapus. Menyebutnya langsung di
migrasi berarti mengunci skema pada satu potret data di satu saat. Yang benar:

```sql
UPDATE custom_roles cr
SET department_id = d.id
FROM (VALUES ('sales-head','sales'), ('ppic','production_ppic'), …)
     AS m(role_suffix, dept_code)
JOIN departments d ON d.code = m.dept_code
WHERE cr.department_id IS NULL
  AND cr.tenant_id = d.tenant_id
  AND cr.id LIKE 'role-%' || m.role_suffix;
```

JOIN membuat pasangan yang divisinya tidak ada **tersaring dengan sendirinya**, bukan meledak. Dan
karena join-nya per `tenant_id`, tenant dengan id divisi berbeda ikut terisi benar tanpa migrasi
tambahan.

Klausa `AND department_id IS NULL` juga penting: kalau migrasi dijalankan ulang setelah admin
memindahkan jabatan ke divisi lain, perubahan admin tidak ditimpa.

5. **Idempoten, termasuk kebijakan RLS.** `apply_tenant_rls()` memanggil `CREATE POLICY` tanpa
   `IF NOT EXISTS`, jadi pemanggilan kedua gagal. Dibuang dulu:

```sql
DROP POLICY IF EXISTS department_module_assignments_tenant_isolation
    ON department_module_assignments;
SELECT apply_tenant_rls('department_module_assignments');
```

Migrasi yang tidak aman dijalankan ulang adalah migrasi yang belum selesai ditulis.

### Blok D: Claim JWT — Dua Sumbu Identitas

```kotlin
.withClaim("role", user.role.name)
.withClaim("department_id", user.departmentId)
.withClaim("custom_role_id", user.customRoleId)
```

**Mengapa tidak menumpang claim `role` saja?**

Karena `Role` adalah enum tetap (`TENANT_ADMIN`, `SALES`, `OPERATOR`, …) sementara
`"role-sales-head"` adalah id baris di tabel `custom_roles`. Menaruhnya di claim `role` membuat
`Role.valueOf()` melempar di sisi baca — dan lemparan itulah yang dulu ditelan menjadi
`TENANT_ADMIN`.

Dua sumbu ini menjawab dua pertanyaan berbeda dan keduanya perlu:

| Sumbu | Pertanyaan yang dijawab | Siapa yang menentukan |
|---|---|---|
| `Role` (enum) | Boleh menyentuh apa di tingkat sistem | Platform |
| `custom_role_id` | Melihat modul apa di layar | Tenant |

### Blok E: Perbaikan Fallback `/me`

```kotlin
// SEBELUM — identitas tak dikenal diberi wewenang terluas
val role = runCatching { Role.valueOf(roleName) }.getOrDefault(Role.TENANT_ADMIN)

// SESUDAH — tak dikenal berarti wewenang tersempit
val role = roleName
    ?.let { name -> runCatching { Role.valueOf(name) }.getOrNull() }
    ?: Role.OPERATOR
```

**Mengapa blok ini ditulis begini?**

Ini penerapan prinsip **fail closed**: ketika sistem tidak yakin, ia harus menutup, bukan membuka.
Bandingkan konsekuensi kedua arah salahnya:

- Salah ke arah sempit → pengguna mengeluh "kok saya tidak bisa lihat X". **Terdeteksi dalam
  hitungan menit.**
- Salah ke arah luas → pengguna diam-diam bisa melihat laporan keuangan. **Tidak terdeteksi sampai
  ada yang kebetulan menyadarinya.**

Kesalahan yang berisik jauh lebih murah daripada kesalahan yang sunyi.

### Blok F: State Turunan, Bukan Salinan

```kotlin
val effectivePermissions: StateFlow<Map<BusinessModule, ModuleAccessConfig>> =
    combine(_activePersona, _roles, _departmentAssignments) { persona, roles, assignments ->
        if (persona == null) emptyMap()
        else AccessDecisionEngine.evaluateAll(persona, roles, assignments)
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())
```

**Mengapa blok ini ditulis begini?**

Ini inti dari keseluruhan fitur, dan sekaligus polanya yang paling penting untuk dipahami.

Wewenang **tidak pernah disimpan**. Ia selalu dihitung ulang dari tiga sumber. Konsekuensinya:
begitu admin menyimpan perubahan matriks dan `_roles` diperbarui, `combine` berjalan, menu
navigasi berubah — tanpa satu baris kode pun yang "memberi tahu" menu untuk memperbarui diri.

Alternatif naifnya adalah menyimpan `var permissions: Map<...>` lalu memanggil
`recalculatePermissions()` di setiap tempat yang mengubah sumbernya. Pola itu rusak bukan kalau,
melainkan **kapan** — cukup satu pemanggilan terlewat, dan layar menampilkan wewenang basi tanpa
gejala apa pun.

### Blok G: Pemindai Depth-Aware (jebakan yang hampir saya masuki)

```kotlin
// ❌ Versi pertama yang saya tulis — rusak
val arrayBody = "\"${module.name}\"\\s*:\\s*\\[(.*?)\\]".toRegex().find(json)
```

JSON-nya berbentuk:

```json
{"CRM_SALES":[{"id":"...","specificRoleIds":["role-sales-head"]}]}
```

Pola non-greedy `\[(.*?)\]` berhenti di `]` milik **`specificRoleIds`**, bukan milik array modul.
Hasilnya: daftar terpotong di tengah, **tanpa error**. Ini kelas bug terburuk — data hilang diam-diam.

```kotlin
// ✅ Versi yang benar — hitung kedalaman
for (i in start until json.length) {
    val c = json[i]
    when {
        escaped -> escaped = false
        c == '\\' -> escaped = true
        c == '"' -> inQuotes = !inQuotes
        inQuotes -> Unit
        c == '[' -> depth++
        c == ']' -> { depth--; if (depth == 0) return json.substring(start, i + 1) }
    }
}
```

> **Pelajaran umum**: regex tidak bisa mem-parsing struktur bersarang. Ini bukan keterbatasan
> kecil, melainkan sifat matematis regular language. Begitu ada kurung di dalam kurung, butuh
> penghitung.

### Blok H: Menjaga Persona Benar-Benar Identik dengan Jabatannya

Tujuan fitur ini bukan "membuktikan RBAC hidup", melainkan **membuktikan konfigurasi sebuah jabatan
sudah benar**. Bedanya halus dan menentukan: begitu persona melihat lebih banyak daripada
jabatannya, pengujiannya berhenti berarti — dan yang berbahaya, ia tetap terlihat berhasil.

Ada tiga kebocoran yang harus ditutup, dan ketiganya sunyi.

**1. Bypass owner yang ikut menempel**

```kotlin
init {
    require(!(isOwnerOrSuperAdmin && roleId != null)) {
        "Persona berjabatan tidak boleh memakai bypass owner"
    }
}
```

Ditegakkan di **konstruktor**, bukan di pemanggil. Aturan yang dititipkan ke pemanggil akan
dilanggar oleh pemanggil berikutnya yang belum membaca aturannya. Versi sebelumnya menyimpulkan
"owner" dari *nama* jabatan (`contains("Owner")`), sehingga jabatan bernama "Direktur Sablon" diam-
diam membuka seluruh modul apa pun isi matriksnya.

**2. Reload yang memulihkan persona sebagai admin**

```kotlin
isOwnerOrSuperAdmin = user.customRoleId == null &&
    (user.role == Role.TENANT_ADMIN || user.role == Role.PLATFORM_SUPERADMIN)
```

Klausa `customRoleId == null` itu seluruh perbaikannya. Tanpa itu, persona bertahan sampai penguji
menekan refresh — lalu berubah jadi admin penuh tanpa pemberitahuan apa pun. Jalur reload adalah
jalur yang paling jarang diuji manual, jadi kebocoran di sana paling lama hidup.

**3. Menu administrasi yang selalu ikut tampil**

Tampilan "jabatan A plus menu admin" bukan tampilan jabatan A. Menu admin disembunyikan saat
menyamar; aman karena switcher persona hidup di **top bar**, bukan di menu — penguji selalu punya
jalan kembali. Kalau switchernya ikut di dalam menu, menyembunyikan menu berarti mengurung penguji.

**Asal wewenang, supaya kesimpulannya tidak keliru**

```kotlin
val grantedByDepartmentOnly: Boolean
    get() = source == AccessSource.DEPARTMENT && !fromRole.isAccessible
```

Karena hak disatukan dari dua arah, menu yang muncul **tidak** membuktikan jabatannya sudah benar —
bisa jadi divisinya yang memberi. Layar kerja karena itu menyebut asalnya terang-terangan, dan
menyalakan peringatan kuning saat menu terbuka hanya karena divisi. Tanpa ini, penguji menarik
kesimpulan yang salah dan tidak akan pernah dibantah oleh layar mana pun.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa Kita Memilih Ini | Risiko Alternatif |
|---|---|---|---|
| **Union hak (max)** | Irisan (min) | Kedua sumbu bersifat *memberi*. Admin menambah assignment divisi bermaksud memperluas akses | Dengan irisan, menambah assignment justru **mencabut** hak Kepala Gudang — perilaku yang tak seorang pun harapkan |
| **State turunan (`combine`)** | Cache + `recalculate()` manual | Mustahil basi secara konstruksi | Satu pemanggilan terlewat = wewenang basi tanpa gejala |
| **Claim JWT terpisah** | Menumpang claim `role` | `Role.valueOf` tidak pernah gagal | Kegagalan parsing ditelan jadi wewenang admin |
| **`object` domain service** | Method di `CustomRole` | Keputusan butuh **dua** agregat (role + assignment); menaruhnya di salah satu berarti agregat itu harus tahu yang lain | Ketergantungan silang antar agregat, pelanggaran batas DDD |
| **Persona ≠ `User`** | Menambah field ke `User` | Persona adalah *permintaan* identitas, `User` adalah identitas terverifikasi | Mencampurnya membuat client bisa mengklaim jadi siapa pun |
| **Fail closed (`OPERATOR`)** | Fail open (`TENANT_ADMIN`) | Kesalahan berisik lebih murah dari kesalahan sunyi | Kebocoran data yang tak terdeteksi |

### Catatan khusus: kenapa `AccessDecisionEngine` di `core/` dan bukan di `app/shared/`?

Karena ia **aturan bisnis**, bukan logika presentasi. Server nantinya harus menegakkan aturan yang
sama persis saat memfilter data. Kalau enginenya tinggal di lapisan UI, server terpaksa menuliskan
ulang aturan yang sama — dan dua salinan aturan akan menyimpang, selalu.

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

### Jebakan 1: Percaya bahwa toast hijau berarti tersimpan

*Kenapa bahaya*: Layar RBAC menampilkan toast sukses untuk perubahan yang hanya menyentuh state
memori. Fitur terlihat bekerja selama sesi berlangsung, lalu "kehilangan data" setelah refresh —
dan bug itu dilaporkan berbulan-bulan kemudian sebagai "kadang setting-nya hilang".

*Solusi kita*: Tabel `department_module_assignments` + endpoint + `persistAssignment()`. Kalau
penyimpanan gagal, yang muncul adalah **error toast**, bukan diam.

### Jebakan 2: Fail open saat ragu

*Kenapa bahaya*: Sudah dibahas di Blok E. Perhatikan bahwa `getOrDefault(TENANT_ADMIN)` **terlihat
seperti kode defensif yang baik** — ada penanganan error, ada nilai default. Yang salah adalah
*pilihan* defaultnya.

*Solusi kita*: Default ke wewenang tersempit yang masih bisa login.

### Jebakan 3: Membuat `HttpClient` di konstruktor

*Kenapa bahaya*: Ini bug nyata yang saya buat di tengah pengerjaan. `RbacAccessPolicyRepository`
membuat `RbacApiClient()` saat konstruksi; `AuthViewModel` memegangnya; dan **seluruh test
`AuthViewModel` langsung gagal** dengan `NoClassDefFoundError` — karena test JVM tidak punya engine
ktor di classpath.

*Solusi kita*:

```kotlin
private val apiClient: RbacApiClient? by lazy {
    runCatching { apiClientProvider() }.getOrNull()
}
```

Malas (dibuat saat dipakai) dan tahan-gagal (null kalau tidak bisa dibuat). Wewenang tetap bisa
dihitung tanpa jaringan; yang hilang hanya data jarak jauh.

> **Pelajaran umum**: konstruktor sebaiknya tidak melakukan pekerjaan. Ia menyusun, bukan
> menjalankan.

### Jebakan 4: Regex untuk struktur bersarang

Sudah dibahas di Blok G. Ulangi sekali lagi karena penting: **tanpa error, data hilang diam-diam.**

### Jebakan 5: Menganggap "kompilasi hijau" sama dengan "tampilannya benar"

Seluruh test lolos dan lima target kompilasi bersih, lalu aplikasinya dijalankan — dan **dua bug
langsung terlihat**, keduanya mustahil ditangkap test mana pun:

1. **Kurung bersarang di label persona.** `displayLabel` merakit `"$name ($roleTitle)"`, padahal
   nama jabatan di pabrik lazim sudah memuat kurung sendiri. Hasilnya
   `Achmad Jamaludin (Kepala Penjualan (Head of Sales)` — kurung yang tak pernah tertutup rapi
   begitu teksnya terpotong. Diganti pemisah `·`.

2. **Tombol toolbar berjarak tidak rata.** `ClayGuardedButton` menumpuk tombol dan keterangan
   alasan dalam satu `Column`; kalimat alasan yang panjang menentukan lebar kolomnya, sehingga
   jarak antar tombol ikut melar mengikuti panjang kalimat. Diperbaiki dengan
   `Modifier.widthIn(max = 160.dp)` pada keterangannya.

Keduanya sekelas dengan "teks pecah satu huruf per baris" yang disebut Kontrak 13: **bug tata
letak tidak punya assertion.** Satu-satunya alat deteksinya adalah mata.

### Jebakan 6: Menambah entri enum tanpa memeriksa `when`

*Kenapa bahaya*: Menambah 9 entri `AppNavScreen` langsung membuat `when (screen)` di `App.kt`
non-exhaustive. Di Kotlin ini **kegagalan kompilasi**, jadi tertangkap. Tapi kalau ada yang
"memperbaikinya" dengan menambah `else -> {}`, layar baru akan diam-diam menampilkan halaman
kosong selamanya.

*Solusi kita*: Daftarkan kesembilannya eksplisit, tanpa `else`. Biarkan compiler menjadi
checklist-nya.

### Jebakan 7: Melupakan Kontrak 6 design system

*Kenapa bahaya*: Godaannya besar menulis `ClayRbacButton(currentLevel: AccessLevel, ...)` di
`designsystem/`. Itu membuat design system tahu soal domain, dan sejak saat itu komponennya tidak
bisa dipakai ulang di konteks lain.

*Solusi kita*: Dua lapis —

```kotlin
// designsystem/ — netral, hanya Boolean dan String
ClayGuardedButton(text, onClick, enabled, lockedHint)

// workspace/ — tahu domain, menerjemahkan
RbacGuardedButton(text, onClick, currentLevel, requiredLevel)
```

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### Strategi berlapis

| Lapisan | Jenis test | Kenapa |
|---|---|---|
| `AccessDecisionEngine` | Unit murni, tanpa mock | Fungsi murni — bisa diuji habis-habisan dalam milidetik |
| Repository server | Integration dengan PostgreSQL | Yang diuji kueri dan RLS-nya, bukan Kotlin-nya |
| ViewModel | Unit dengan fake client | Yang diuji transisi state |
| Layout | **Mata** | Bug teks pecah per huruf tidak tertangkap test mana pun |

### Test kunci yang ditulis

Sebelas test untuk engine. Yang paling berharga bukan yang menguji jalur sukses, melainkan yang
menguji **batas**:

```kotlin
@Test
fun `evaluate when assignment targets specific roles should apply only to those roles`() {
    val assignments = listOf(
        assignment(salesDept, AccessLevel.MANAGE, specificRoleIds = setOf("role-sales-head"))
    )

    val forHead  = AccessDecisionEngine.evaluate(persona(roleId = "role-sales-head"), ...)
    val forStaff = AccessDecisionEngine.evaluate(persona(roleId = "role-sales"), ...)

    assertEquals(AccessLevel.MANAGE, forHead.level)
    assertEquals(AccessLevel.NONE, forStaff.level)   // ← ini yang penting
}
```

Assertion kedua itu yang bernilai. Ia memastikan penugasan bertarget **tidak bocor** ke jabatan
lain — kegagalan yang, kalau terjadi, tidak akan terlihat di layar mana pun.

Perhatikan juga pola ini:

```kotlin
assertTrue(BusinessModule.COSTING_HPP.isGlobalOnly, "Prasyarat uji: COSTING_HPP harus GLOBAL_ONLY")
```

Test yang menyatakan prasyaratnya sendiri. Kalau suatu hari ada yang mengubah `COSTING_HPP` jadi
`HIERARCHICAL`, test ini gagal dengan pesan yang menjelaskan **kenapa**, bukan sekadar
`expected ALL_TENANT_DATA but was OWN_DATA_ONLY`.

### Verifikasi lima target — bukan satu

```bash
./gradlew :core:jvmTest
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
          :app:shared:jvmTest
./gradlew :server:compileKotlin
```

Kompilasi JVM lolos **tidak berarti** WasmJS lolos. Beberapa API JVM tidak ada di sana, dan
kegagalannya baru muncul di target itu.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1 — Tegakkan di server.**
  Saat ini `AccessDecisionEngine` hanya menentukan apa yang *ditampilkan*. Klien yang nakal masih
  bisa memanggil API langsung. Buat Ktor plugin yang memuat persona dari claim JWT, menjalankan
  engine yang sama, dan menolak request ke modul yang wewenangnya `NONE`.
  *Petunjuk*: enginenya ada di `core/`, jadi server sudah bisa memakainya tanpa duplikasi — itu
  memang alasan ia ditaruh di sana.

- [ ] **Tantangan 2 — Terapkan `DataScope` sungguhan.**
  Sekarang scope hanya *ditampilkan* di banner. Ubah satu repository (mis. prospek CRM) agar
  benar-benar memfilter: `OWN_DATA_ONLY` → hanya dokumen buatan sendiri; `SUBORDINATE_DATA` →
  miliknya + bawahannya lewat `reports_to_id`.
  *Pertanyaan yang harus kamu jawab dulu*: rekursif berapa tingkat? Bawahan langsung saja, atau
  seluruh sub-pohon?

- [ ] **Tantangan 3 — Tautkan karyawan ke jabatan.**
  `matchRole()` di `PersonaSwitcherDropdown` **menebak** jabatan karyawan dari divisinya, karena
  tidak ada kolom yang menautkan `employees` ke `custom_roles`. Tambahkan kolomnya, lalu hapus
  fungsi tebakan itu.
  *Pertanyaan desain*: satu karyawan boleh punya berapa jabatan? Kalau boleh lebih dari satu,
  bagaimana `AccessDecisionEngine` harus berubah?

- [ ] **Tantangan 4 — Cari bug urutan.**
  Ganti `maxByOrNull { it.accessLevel.weight }` di `resolveDepartmentAccess` menjadi
  `firstOrNull()`. Jalankan `:core:jvmTest`. Test mana yang gagal, dan **kenapa test itu yang
  gagal** sementara yang lain lolos?

---

## 📌 Ringkasan Satu Halaman

1. **Cek asumsi ke kode sebelum menulis rencana.** Tiga temuan mengubah seluruh desain.
2. **Database → domain → test → infrastruktur → state → UI.** UI paling akhir, selalu.
3. **Hitung, jangan simpan.** State turunan tidak bisa basi.
4. **Fail closed.** Ragu berarti menutup.
5. **Konstruktor menyusun, bukan menjalankan.**
6. **Regex tidak bisa struktur bersarang.**
7. **Kompilasi lima target, lalu lihat dengan mata.**
