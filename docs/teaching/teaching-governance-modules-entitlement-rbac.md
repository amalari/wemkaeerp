# 🎓 Modul Pembelajaran: Menjadikan RBAC, Org Chart & Alur Pabrik sebagai Modul Ber-entitlement

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design, Enum sebagai Katalog, Capability Slot, Multi-Tenant Entitlement, Access Decision Engine, Anti-Lockout Invariant, Data Scope Filtering, Flyway Backfill
> **Prasyarat**: Kotlin dasar, paham `enum class` & `sealed interface`, pernah membaca `AccessDecisionEngine.kt`, tahu apa itu Flyway
> **Referensi Task**: Modularisasi tiga layar tata kelola (tanpa nomor issue)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Bayangkan WeMade ERP sebelum perubahan ini. Kita punya sembilan modul operasional — Gudang, HPP,
QC, dan seterusnya — yang diperlakukan sangat rapi: masing-masing punya entri di enum
`BusinessModule`, muncul di matriks Hak Akses, dan bisa disambung/diputus per pabrik lewat tabel
`tenant_module_entitlements`.

Lalu ada **tiga layar yang justru paling berbahaya**:

| Layar | Isinya |
|---|---|
| Bagan Organisasi | seluruh data karyawan: nama, email, telepon, atasan |
| Hak Akses (RBAC) | matriks wewenang seluruh pabrik |
| Alur Pabrik | topologi operasional tenant |

Ketiganya di-*hardcode* sebagai daftar di `NavMenu.kt`, dan penjagaannya hanya satu baris:
`if (isAuthenticated)`. Artinya **operator jahit yang baru dibuatkan akun bisa membuka layar Hak
Akses dan menaikkan wewenangnya sendiri menjadi Akses Penuh.** Bukan karena ada bug — karena
memang tidak ada yang menghalanginya.

Masalah kedua lebih sunyi: platform tidak punya cara menjual Alur Pabrik sebagai fitur. Semua
tenant mendapatkannya, selamanya, karena layarnya tidak pernah menjadi "barang" yang bisa
disambungkan.

### Analogi Sederhana

Sembilan modul operasional itu seperti **mesin-mesin di lantai produksi**: dibeli per unit,
dipasang di jalur, dan operator butuh izin untuk menyalakannya.

Tiga layar tata kelola itu seperti **panel listrik, daftar karyawan, dan denah pabrik**. Dulu
kita menaruhnya di lorong tanpa pintu: siapa pun yang sudah masuk gerbang bisa membaca — dan
mengubah — semuanya.

Perubahan ini memasang pintu berkunci pada ketiganya, **tanpa** memindahkannya ke lantai
produksi. Panel listrik tetap bukan mesin jahit: ia tidak ikut dihitung saat kita bilang "pabrik
ini punya 9 mesin".

### Hasil Akhir yang Diharapkan

1. Superadmin bisa memutus Alur Pabrik dari satu tenant lewat dialog, dan menunya hilang.
2. Admin pabrik bisa memberi Kepala Gudang akses "Hanya Lihat" ke Bagan Organisasi — dan tombol
   Tambah Karyawan benar-benar mati, bukan sekadar terlihat.
3. Kepala Sales bisa dibatasi hanya melihat karyawan divisinya sendiri, **dan payload API-nya
   ikut menyusut**, bukan hanya tampilannya.
4. Tidak ada satu pun skenario di mana seorang admin bisa mengunci dirinya sendiri keluar.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Pertanyaan pertama seorang junior biasanya: *"aku mulai dari mana? Bikin tabel dulu? Bikin
layarnya dulu?"*

Jawabannya: **dari enum.** Dan alasannya bukan selera.

### Langkah 0: Temukan dulu "sumbu kebenaran"-nya

Sebelum mengetik satu baris pun, cari **daftar mana yang menggerakkan fitur ini**. Di codebase
kita, jawabannya ketemu dengan satu perintah:

```bash
grep -rn "BusinessModule.entries" core/ app/ server/
```

Hasilnya menunjukkan bahwa `BusinessModule.entries` menggerakkan matriks RBAC, menu navigasi,
dan entitlement tenant. Artinya: **menambah satu entri ke enum itu otomatis memberi kita tiga
fitur sekaligus.**

Tapi ada temuan kedua yang sama pentingnya. Grep kedua:

```bash
grep -rn "OperationalModuleCatalog" core/
```

Ternyata kanvas Alur Pabrik **tidak** membaca `BusinessModule.entries`, melainkan
`OperationalModuleCatalog.all`. Dua daftar yang selama ini kebetulan berisi hal yang sama.

> 🔑 **Mental model kuncinya**: fitur ini aman dikerjakan justru karena dua daftar itu sudah
> terpisah. Kita menambahkan modul tata kelola ke daftar pertama saja. Kalau keduanya dulu
> disatukan, pekerjaan ini akan berubah menjadi refactor besar sebelum bisa dimulai.

### Langkah 1: Perluas enum + beri pembeda jenis

`core/.../domain/rbac/BusinessModule.kt`. Tambah `ModuleKind`, kategori `GOVERNANCE`, tiga entri.

### Langkah 2: Ikuti jejak kompilator

Ini bagian paling menyenangkan. Jalankan `./gradlew :core:compileKotlinJvm` dan **biarkan
kompilator memberi tahu di mana saja asumsi lama kita pecah.** Setiap `when` yang ekshaustif atas
`BusinessModule` akan gagal:

- `ModuleArchetype.forModule()` — modul tata kelola tidak mengisi slot kapabilitas
- `sampleRowsFor()` — layar kerja generik
- `ModuleCardView` — warna kategori

Tiga tempat itu adalah **peta lengkap dampak perubahan kita**, dan kita tidak perlu mencarinya
sendiri. Inilah imbalan memakai enum + `when` ekshaustif alih-alih `String` + `if`.

### Langkah 3: Tambahkan penyaring entitlement di mesin keputusan

`AccessDecisionEngine` — satu parameter opsional, satu cabang baru.

### Langkah 4: Tegakkan invarian anti-lockout di entity

`CustomRole` — sebelum menyentuh UI sama sekali.

### Langkah 5: Fungsi domain untuk penyaringan jangkauan

`OrgChartVisibility` — murni, tanpa I/O, karena akan dipanggil dari **dua** tempat.

### Langkah 6: Server — route, plugin, penyaringan otoritatif

### Langkah 7: Client — jalur data entitlement, gerbang layar, dialog superadmin

### Langkah 8: Migrasi Flyway

**Terakhir, bukan pertama.** Skema baru hanya ditulis setelah domainnya jelas. Di task ini kita
bahkan tidak butuh kolom baru sama sekali — hanya *backfill* data.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Pembeda jenis modul (`ModuleKind`)

```kotlin
enum class ModuleKind { OPERATIONAL, GOVERNANCE }

enum class ModuleCategory(val displayName: String) {
    GOVERNANCE("Sistem & Struktur"),   // ← WAJIB entri pertama
    SALES(...), LOGISTICS(...), /* … */
}
```

**Mengapa blok ini ditulis begini?**

- **Kenapa butuh `ModuleKind` padahal sudah ada `ModuleCategory`?** Karena keduanya menjawab
  pertanyaan berbeda. `category` menjawab *"dikelompokkan di mana di menu"* — itu keputusan
  tampilan. `kind` menjawab *"boleh berdiri di lini produksi atau tidak"* — itu keputusan
  arsitektur, dan ia memengaruhi kuota paket. Menyatukan keduanya berarti mengunci dua hal yang
  bisa berubah sendiri-sendiri.
- **Kenapa `GOVERNANCE` wajib entri pertama?** Karena `NavMenu` menyusun urutan seksi drawer
  dengan `ModuleCategory.entries.forEach`. Urutan enum **adalah** urutan menu. Ini contoh
  *implicit coupling* — jadi kita menuliskannya sebagai komentar di kodenya, bukan menyimpannya
  di kepala.

### Blok B: Arketipe yang boleh kosong

```kotlin
fun forModule(module: BusinessModule): ModuleArchetype? = when (module) {
    BusinessModule.CRM_SALES -> ORDER_INGESTION
    // … 8 modul operasional lain …
    BusinessModule.ORG_CHART,
    BusinessModule.DYNAMIC_RBAC,
    BusinessModule.FACTORY_FLOW -> null
}
```

**Mengapa blok ini ditulis begini?**

- **Kenapa `null`, bukan menambah `ModuleArchetype.GOVERNANCE`?** Ini pilihan desain yang paling
  banyak saya pertimbangkan. Sebuah *capability slot* menggambarkan **stasiun di lini produksi**:
  ada yang menyerahkan pekerjaan kepadanya, dan ia menyerahkan pekerjaan ke stasiun berikutnya.
  Bagan organisasi bukan stasiun — tidak ada yang mengirim kain ke sana.

  Kalau kita paksakan membuat slot `GOVERNANCE`, modul tata kelola akan menjadi kandidat sah di
  `interchangeableWith()` dan bisa dipasang ke kanvas pabrik. **Tipe yang longgar mengundang
  pemakaian yang salah.** `null` di sini bukan "belum diisi", melainkan pernyataan bahwa
  pertanyaannya memang tidak berlaku.

- Konsekuensinya, `OperationalModuleSpecification.archetype` jadi begini:

```kotlin
val archetype: ModuleArchetype
    get() = requireNotNull(ModuleArchetype.forModule(module)) {
        "Modul '${module.code}' bertipe ${module.kind} sehingga tidak mengisi slot kapabilitas…"
    }
```

  Kenapa `requireNotNull` dan bukan `?: CUSTOM_EXTENSION`? Karena spesifikasi operasional **hanya
  ada** untuk modul operasional. Kalau modul tata kelola sampai ke sini, itu kesalahan wiring —
  dan gagal keras dengan pesan jelas jauh lebih baik daripada diam-diam menyintesis stasiun palsu
  yang baru ketahuan salah tiga layar kemudian.

### Blok C: Penyaringan entitlement — dan kenapa urutannya menentukan artinya

```kotlin
fun explain(
    persona: TestingPersona,
    module: BusinessModule,
    role: CustomRole?,
    assignments: List<DepartmentModuleAssignment>,
    grantedModules: Set<BusinessModule>? = null
): AccessDecision {
    val roleAccess = …

    if (grantedModules != null && module !in grantedModules) {
        return AccessDecision(config = ModuleAccessConfig(AccessLevel.NONE),
                              source = AccessSource.NOT_ENTITLED, …)
    }

    if (persona.isOwnerOrSuperAdmin) { /* bypass */ }
    // …
}
```

**Mengapa blok ini ditulis begini?**

- **Urutannya adalah aturannya.** Cek entitlement diletakkan **sebelum** bypass Owner. Kalau
  dibalik, memutus modul lewat billing tidak akan berpengaruh apa pun bagi orang yang paling
  sering memakai sistem. Modul yang tidak dibeli sebuah pabrik bukan modul yang "Owner-nya
  berwenang tapi stafnya tidak" — ia **tidak ada** untuk pabrik itu.

- **Kenapa `null` punya arti khusus?** `null` berarti *"entitlement belum diketahui"*, bukan
  *"tidak ada modul"*. Bedanya besar: permintaan HTTP yang gagal akan menghasilkan `null`, dan
  kalau `null` diartikan "kosong", **kegagalan jaringan akan tampil persis seperti langganan yang
  dicabut**. Menu mendadak hilang, dan tidak ada satu pun pesan error yang menjelaskannya.
  Dengan `null` = "belum tahu", perilakunya kembali seperti sebelum fitur ini ada.

- **Kenapa perlu `AccessSource.NOT_ENTITLED` terpisah dari `NONE`?** Karena keduanya diperbaiki
  oleh **orang yang berbeda di tempat yang berbeda**:

  | Keadaan | Diperbaiki oleh | Di mana |
  |---|---|---|
  | `NONE` | admin pabrik | matriks RBAC |
  | `NOT_ENTITLED` | superadmin platform | pengaturan tenant |

  Menyamakan pesannya akan mengirim admin menyisir layar RBAC berjam-jam untuk masalah yang tidak
  akan pernah bisa diselesaikan di sana.

### Blok D: Dua lapis anti-lockout

Ini bagian yang paling sering salah dipahami, jadi perhatikan baik-baik.

```kotlin
private fun enforce(module: BusinessModule, config: ModuleAccessConfig): ModuleAccessConfig {
    val locked = isSystemOwnerRole && module == BusinessModule.DYNAMIC_RBAC
    return if (locked) ModuleAccessConfig(AccessLevel.MANAGE, DataScope.ALL_TENANT_DATA)
           else config.sanitizeFor(module)
}
```

**"Bukankah bypass Owner sudah cukup? Kenapa perlu kunci kedua?"**

Pertanyaan bagus — dan jawabannya ada di invarian `TestingPersona`:

```kotlin
require(!(isOwnerOrSuperAdmin && roleId != null)) {
    "Persona berjabatan tidak boleh memakai bypass owner…"
}
```

Baca pelan-pelan: **persona yang punya jabatan TIDAK mendapat bypass.** Itu disengaja — menguji
sebuah jabatan harus benar-benar menguji jabatan itu, bukan jabatan-plus-hak-dewa.

Konsekuensinya: seorang Owner yang punya jabatan terkonfigurasi (`role-owner`) berjalan lewat
matriks seperti orang lain. Kalau admin menurunkan `DYNAMIC_RBAC` jabatan itu ke `NONE`, **tidak
tersisa satu pun layar untuk menaikkannya kembali.**

Jadi: bypass menutup kasus direksi tanpa jabatan; penguncian menutup kasus Owner berjabatan. Dua
lubang berbeda, dua penambal berbeda.

**Dan kenapa ada `withModulePermissions()` di samping `updateModuleAccess()`?**

```kotlin
fun withModulePermissions(permissions: Map<BusinessModule, ModuleAccessConfig>): CustomRole {
    val enforced = permissions.mapValues { (module, config) -> enforce(module, config) }
    val withOwnerLock = if (isSystemOwnerRole) {
        enforced + (BusinessModule.DYNAMIC_RBAC to ModuleAccessConfig(AccessLevel.MANAGE, …))
    } else enforced
    return copy(modulePermissions = withOwnerLock)
}
```

Karena `UpdateRoleUseCase` dulu menulis `copy(modulePermissions = command.modulePermissions)` —
dan **`copy()` melewati setiap invarian yang kita tulis di atas.** Sebuah permintaan API yang
dirakit tangan (`curl`) bisa menurunkan hak Owner tanpa menyentuh layar mana pun.

Perhatikan juga baris `withOwnerLock`: matriks yang **tidak menyebut** `DYNAMIC_RBAC` sama saja
dengan menyetelnya ke `NONE`, karena `getAccess()` mengembalikan `NONE` untuk kunci yang hilang.
Jadi tidak cukup menyaring nilai yang dikirim; kuncinya harus **disisipkan**.

> 💡 **Pelajaran umum**: setiap kali kamu menulis invarian di sebuah entity, cari semua jalan
> masuk ke state itu. `copy()` pada data class adalah pintu belakang yang paling sering terlupa.

### Blok E: Penyaringan jangkauan yang tidak bisa dibohongi

```kotlin
DataScope.SUBORDINATE_DATA -> {
    val byDepartment = /* semua orang sedivisi */
    val byCommandChain = /* bawahan transitif via reportsToId */
    (self + byDepartment + byCommandChain).distinctBy { it.id.value }
}
```

**Mengapa dua sumbu digabung, bukan dipilih salah satu?**

Karena "bawahan" di pabrik berarti dua hal sekaligus:
- Kepala Gudang atas seluruh stafnya → sumbu **divisi**
- Kepala Produksi atas seorang lead yang ditempatkan di divisi lain → sumbu **rantai komando**

Pakai divisi saja → bawahan lintas divisi hilang. Pakai rantai komando saja → rekan sedivisi yang
tidak melapor langsung hilang, dan bagannya tampak bolong.

Dan perhatikan penelusurannya:

```kotlin
val visited = mutableSetOf(rootId.value)
val queue = ArrayDeque<String>().apply { add(rootId.value) }
while (queue.isNotEmpty()) { /* … */ }
```

**Iteratif dengan himpunan `visited`, bukan rekursif.** Alasannya konkret: data hierarki diketik
manusia. Cepat atau lambat akan ada A-melapor-ke-B dan B-melapor-ke-A akibat salah input.
Penelusuran rekursif polos akan menggantung selamanya — server hang, bukan bagan yang sedikit
keliru. Ada test khusus untuk ini (`subordinateData_withCyclicReportingShouldTerminate`).

### Blok F: Penyaringan di server, bukan cuma di client

```kotlin
private suspend fun ApplicationCall.applyOrgChartScope(…): List<OrgNode> {
    val principal = callerPrincipalOrNull ?: return employees
    if (roleRepository == null || moduleAssignmentRepository == null) return employees
    if (principal.customRoleId == null && principal.departmentId == null) return employees
    // … hitung scope lewat AccessDecisionEngine, lalu OrgChartVisibility.visibleTo(…)
}
```

**Mengapa blok ini ditulis begini?**

- **Kenapa harus di server padahal client sudah menyaring?** Karena penyaringan di client
  **tidak menyembunyikan apa pun**. Payload-nya tetap utuh dan terbaca siapa saja yang membuka
  tab Network di browser. Kalau kamu hanya boleh mengingat satu kalimat dari dokumen ini:
  *penyaringan yang hanya ditegakkan klien bukan penyaringan.*

- **Kenapa scope dihitung lewat `AccessDecisionEngine`, bukan `role.getAccess(ORG_CHART).scope`?**
  Karena wewenang datang dari **dua sumbu yang disatukan** (jabatan ∪ divisi). Menghitung
  salah satunya sendiri di sini akan menjadi aturan kedua yang bisa menyimpang dari aturan yang
  dipakai menu dan layar. Satu mesin, banyak pemanggil.

- **Kenapa ada tiga `return` lebih awal?** Yang ketiga menarik: pemanggil tanpa jabatan **dan**
  tanpa divisi tidak punya sumbu apa pun untuk dipersempit. Menanyakannya ke database hanya
  menghasilkan dua query untuk jawaban yang sudah pasti.

  > ⚠️ **Cerita nyata dari task ini**: versi pertama saya tidak punya guard ini, dan langsung
  > memecahkan `EmployeeApiTest`. Test itu memakai repository in-memory untuk karyawan, tapi
  > `Application.kt` tetap memberi `PostgresRoleRepository` — jadi route yang tadinya tidak
  > pernah menyentuh DB mendadak menyentuhnya, dan gagal dengan `ConnectException`. Guard ini
  > memperbaikinya **dan** sekaligus menghemat dua query di produksi. Optimasi yang benar
  > biasanya begitu: ia jatuh dari alasannya, bukan dari menebak yang mana yang lambat.

### Blok G: Migrasi yang menyelamatkan data, bukan mengubah skema

```sql
UPDATE tenant_module_entitlements
SET granted_modules = (
        SELECT jsonb_agg(DISTINCT combined.m)
        FROM (
            SELECT jsonb_array_elements_text(granted_modules) AS m
            UNION
            SELECT unnest(ARRAY['ORG_CHART', 'DYNAMIC_RBAC', 'FACTORY_FLOW'])
        ) AS combined
    )
WHERE granted_modules IS NOT NULL;
```

**Mengapa blok ini ada sama sekali?**

Kolom `granted_modules` punya **dua arti yang berlawanan** tergantung nilainya:

| Nilai | Arti |
|---|---|
| `NULL` | "apa pun yang diberikan paket" — kasus normal |
| array | daftar yang **MEMPERSEMPIT** |

Baris jenis kedua itulah bahayanya. Tenant yang entitlement-nya pernah disetel tangan **tidak
akan pernah menerima modul yang dirilis kemudian**. Tanpa backfill ini, pabrik tersebut kehilangan
Bagan Organisasi-nya pada deploy pertama — dan kehilangan itu tidak muncul sebagai error apa pun,
hanya sebagai menu yang mendadak tidak ada.

> 🔑 **Prinsipnya**: hak yang sudah ada tidak boleh menyusut karena sebuah rilis. Superadmin yang
> kemudian memutusnya adalah keputusan sadar; efek samping migrasi bukan.

Perhatikan juga `WHERE granted_modules IS NOT NULL` — baris `NULL` sengaja tidak disentuh, karena
artinya sudah benar apa adanya.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan Kita | Alternatif | Mengapa Kita Memilih Ini? | Risiko Alternatif |
|---|---|---|---|
| Tambah entri ke `enum BusinessModule` | Tabel `modules` di DB | Kompilator menemukan **semua** tempat yang harus disesuaikan lewat `when` ekshaustif | Modul baru yang lupa ditangani baru ketahuan saat runtime, di produksi |
| `forModule()` mengembalikan `ModuleArchetype?` | Menambah slot `GOVERNANCE` | Tipe menolak pemakaian yang salah: modul tata kelola jadi mustahil dipasang di kanvas | Slot palsu membuat panel listrik muncul sebagai kandidat mesin jahit |
| `grantedModules: Set<…>?` dengan `null` = "belum tahu" | `emptySet()` sebagai default | Kegagalan jaringan tidak tersamar sebagai langganan dicabut | Menu kosong tanpa penjelasan setiap kali server lambat |
| Gerbang tunggal `onWriteEvent(…)` di layar | Menonaktifkan tiap tombol satu per satu | Tombol yang terlewat **tidak berefek**, bukan diam-diam menembus wewenang | Satu tombol terlupa di file kartu = lubang wewenang |
| Penyaringan scope di **server + client** | Client saja | Payload ikut menyusut; sembunyi bukan sekadar kosmetik | Data karyawan tetap terkirim utuh, terbaca di tab Network |
| Backfill data di V18 | Biarkan `NULL` menangani semuanya | Tenant dengan daftar eksplisit tidak kehilangan modul | Regresi senyap yang tidak memunculkan error apa pun |

### Satu catatan tentang `NavMenu`

Rencana awal sempat menambahkan parameter `entitledModules: Set<BusinessModule>` ke
`buildNavMenu()`. Itu **tidak jadi dipakai**, dan alasannya layak dipahami:

Entitlement sudah disaring di `AccessDecisionEngine`. Modul yang diputus tiba di `buildNavMenu`
sebagai `AccessLevel.NONE`, dan aturan "modul tanpa akses dihilangkan" yang sudah ada
menanganinya. Memeriksanya lagi di sini berarti **dua tempat yang bisa menyimpang**, dan suatu
hari salah satunya akan lupa diperbarui.

> 💡 Kalau sebuah aturan sudah ditegakkan di hulu, jangan menegakkannya lagi di hilir — cukup
> pastikan hasilnya mengalir ke bawah.

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

1. **Jebakan: `copy()` pada data class melewati semua invarian**
   - *Kenapa bahaya*: kamu menulis validasi cantik di `updateModuleAccess()`, lalu ada use case
     lain memanggil `copy(modulePermissions = …)` dan seluruh aturanmu menguap.
   - *Solusi kita*: sediakan fungsi domain (`withModulePermissions`) untuk **setiap** jalan masuk
     ke state itu, dan salurkan keduanya lewat satu fungsi `enforce()` privat.

2. **Jebakan: menganggap `null` dan "kosong" itu sama**
   - *Kenapa bahaya*: `grantedModules = null` berarti "semua". Memutus satu modul dengan
     pengurangan himpunan (`null - module`) akan menghasilkan himpunan kosong — **toggle pertama
     mencabut sembilan modul lain sekaligus.**
   - *Solusi kita*: `withModule()` memadatkan `null` menjadi seluruh katalog dulu, baru mengurangi:
     ```kotlin
     val current = grantedModules ?: BusinessModule.entries.toSet()
     val updated = if (enabled) current + module else current - module
     ```

3. **Jebakan: penelusuran hierarki rekursif**
   - *Kenapa bahaya*: data yang diketik manusia pasti suatu saat memuat siklus. Rekursi polos =
     `StackOverflowError` atau server menggantung.
   - *Solusi kita*: BFS iteratif dengan himpunan `visited`.

4. **Jebakan: mengira penyaringan di UI itu keamanan**
   - *Kenapa bahaya*: `state.employees.filter { … }` di ViewModel tidak menghapus apa pun dari
     response HTTP.
   - *Solusi kita*: fungsi domain murni yang dipanggil **server** sebagai penentu, dan client
     sebagai kenyamanan.

5. **Jebakan: menambah modul ke enum tanpa memikirkan tenant lama**
   - *Kenapa bahaya*: regresi senyap — tanpa error, tanpa log, hanya menu yang hilang.
   - *Solusi kita*: migrasi backfill, dan kebiasaan bertanya *"apa arti nilai NULL di kolom ini?"*
     sebelum menambah anggota baru ke sebuah himpunan.

6. **Jebakan: komentar blok Kotlin ternyata bisa bersarang**
   - *Kenapa bahaya*: menulis path `/api/admin/**` di dalam KDoc membuka komentar baru
     (`/*`), dan `*/` penutupnya hanya menutup yang bersarang. Errornya muncul di **baris terakhir
     file**, jauh dari penyebabnya.
   - *Solusi kita*: hindari urutan `/*` di dalam teks komentar. Saya kena jebakan ini di task ini.

7. **Jebakan: invarian di kode tidak berlaku surut ke data lama** ⭐
   - *Kenapa bahaya*: `CustomRole.enforce()` mengunci hak Owner atas modul RBAC — tetapi hanya
     saat jabatan **ditulis**. Baris `role-owner` yang sudah lama tersimpan di database tidak
     pernah melewatinya, dan `getAccess()` mengembalikan `NONE` untuk kunci yang tidak ada. Hasilnya:
     begitu rilis mendarat, **Owner kehilangan ketiga layar** — dan bypass tidak menolong, karena
     `matchRole()` memasangkan direksi ke `role-owner` sehingga personanya berjabatan.
   - *Solusi kita*: migrasi `V19` yang mem-backfill `custom_roles.module_permissions`.
   - *Pelajarannya*: setiap invarian baru punya dua sisi — kode untuk data yang akan datang,
     migrasi untuk data yang sudah ada. Menulis salah satunya saja terasa selesai, padahal belum.

---

## 🔥 5b. Dua Bug yang Hanya Ketahuan Saat Aplikasinya Dijalankan

Bagian ini ditambahkan **setelah** fitur dinyatakan selesai dan 542 test berwarna hijau. Keduanya
lolos dari seluruh unit test, dan baru terlihat ketika server benar-benar dinyalakan dan
di-`curl`. Kalau kamu hanya membaca satu bagian dari dokumen ini, baca yang ini.

### Bug 1 — Matriks lama tidak punya kunci modul baru

Gejalanya: setelah V18, `SELECT module_permissions FROM custom_roles WHERE id='role-owner'`
berisi sembilan modul, tak satu pun governance.

Rantai sebabnya panjang dan tiap mata rantainya masuk akal sendiri-sendiri:

```
getAccess(DYNAMIC_RBAC) → kunci tidak ada → NONE
    ↓
matchRole(Hendra) → dept null → role-owner  →  persona.roleId != null
    ↓
invarian TestingPersona: persona berjabatan DILARANG pakai bypass
    ↓
Owner berjalan lewat matriks → NONE → ketiga menu hilang untuk Owner sendiri
```

Yang menarik: invarian di mata rantai ketiga itu **benar dan disengaja** — menguji sebuah jabatan
harus menguji jabatan itu. Bug muncul dari kombinasi keputusan yang masing-masing benar. Inilah
alasan menjalankan aplikasinya tidak pernah bisa digantikan membaca kode.

### Bug 2 — `scope` dibaca tanpa memeriksa `level`

Ini yang lebih berbahaya. Versi pertama `applyOrgChartScope` berbunyi kira-kira begini:

```kotlin
if (decision.config.scope == DataScope.ALL_TENANT_DATA) return employees   // ❌
```

Sekilas benar. Tapi lihat nilai bawaan `ModuleAccessConfig`:

```kotlin
data class ModuleAccessConfig(
    val level: AccessLevel = AccessLevel.NONE,
    val scope: DataScope = DataScope.ALL_TENANT_DATA   // ← bawaan, bahkan saat level = NONE
)
```

Artinya `ModuleAccessConfig(NONE)` punya scope `ALL_TENANT_DATA`. Kode di atas membaca
**"tanpa akses"** sebagai **"seluruh data pabrik"** — kebalikan persis dari maksudnya.

Buktinya di server sungguhan:

```
Agus — Operator (ORG_CHART = NONE)   HTTP 200   12 karyawan
```

Menunya memang tidak muncul di drawer. Tapi operator jahit dengan tokennya sendiri bisa
mengunduh seluruh direktori karyawan pabrik — nama, email, nomor telepon — dengan satu `curl`.

Setelah `OrgChartAccessGuard.kt`:

```
admin (TENANT_ADMIN)               HTTP 200   12 karyawan
Budi — Kepala Sales (VIEW/SUB)     HTTP 200    7 karyawan
Agus — Operator (NONE)             HTTP 403   Butuh wewenang Hanya Lihat…
```

### Bug 3 — jangkauan data hanya menyaring baca

Ditemukan saat menjawab pertanyaan *"bagan organisasi kan per divisi, sedangkan `DataScope` cuma
punya tiga nilai — gimana?"*. Jawaban pertanyaannya ada di §5c; yang ditemukan sambil menjawabnya
adalah ini:

```
→ BACA  : 7 karyawan, semuanya divisi Penjualan
→ TULIS : HTTP 201 — berhasil menambah karyawan ke divisi GUDANG
```

Orang yang hanya bisa **melihat** divisi Penjualan berhasil **menulis** ke divisi Gudang — lalu
baris yang baru saja ia buat tidak muncul di daftarnya sendiri.

Aturannya sekarang satu kalimat: **yang boleh diubah adalah yang boleh dilihat.**
`OrgChartWriteReach` menurunkan batas tulis dari daftar karyawan yang **sudah tersaring**, memakai
`applyOrgChartScope` yang sama dengan sisi baca — jadi kedua batas itu mustahil dihitung dengan
aturan berbeda.

Satu detail kecil yang mudah terlewat: divisi penonton sendiri ikut dimasukkan ke jangkauan
meskipun divisinya sedang kosong. Tanpa itu, kepala divisi yang baru dibentuk tidak akan pernah
bisa menambahkan orang pertamanya — jangkauannya kosong, jadi setiap penambahan ditolak.

---

## 🧭 5c. Menjawab: "Bagan organisasi kan per divisi, tapi `DataScope` cuma tiga nilai?"

Kebingungan ini wajar, dan sumbernya adalah satu kata dipakai untuk **tiga konsep berbeda**:

| Arti "per divisi" | Mekanismenya | Status |
|---|---|---|
| Siapa yang **dapat wewenang** — "seluruh staf Gudang boleh buka Org Chart" | `DepartmentModuleAssignment` | ✅ ada, dan sengaja terpisah dari `DataScope` |
| Batas **data** = divisi penonton sendiri | `DataScope.SUBORDINATE_DATA` | ✅ ada |
| Batas **data** = divisi tertentu yang ditunjuk — "HRD boleh lihat divisi Gudang saja" | — | ❌ belum ada |

Kuncinya: **`DataScope` selalu relatif terhadap penonton, bukan absolut.** Ia menjawab "seberapa
jauh dari saya", bukan "divisi mana".

### Kenapa `SUBORDINATE_DATA` sudah berarti "per divisi"

Namanya menyesatkan; implementasinya menyatukan dua sumbu:

```kotlin
val byDepartment   = /* semua orang sedivisi dengan penonton */
val byCommandChain = /* bawahan transitif via reportsToId    */
(self + byDepartment + byCommandChain).distinctBy { it.id.value }
```

Sumbu pertama itulah "per divisi". Kepala Penjualan melihat 7 dari 12 orang — seluruh divisinya.

Kalau hanya dipakai salah satu, keduanya rusak dengan cara berbeda: hanya divisi → bawahan lintas
divisi hilang; hanya rantai komando → rekan sedivisi yang tidak melapor langsung hilang, dan
bagannya bolong.

### Yang belum terpecahkan, dan kapan itu penting

Untuk staf biasa, "divisi sendiri" bukan "bawahan". Sales eksekutif dengan `SUBORDINATE_DATA`
melihat seluruh divisinya **termasuk atasannya** — label "Data Tim & Bawahan" menutupi ini, tapi
"lihat tim saya" dan "lihat bawahan saya" sekarang tidak bisa dibedakan.

Kalau suatu saat perlu dibedakan, tambahkan `DataScope.DEPARTMENT_DATA` (divisi sendiri saja,
tanpa rantai komando). Kalau yang dibutuhkan scope **bertarget** ("HRD → divisi Gudang"),
perubahannya jauh lebih besar: `ModuleAccessConfig` harus membawa daftar divisi target, ikut ke
skema DB, codec, dan UI wewenang. Itu task tersendiri, bukan tambalan.

### Bug 4 — id per-item bisa ditebak, dan detail-nya membawa penumpang gelap

Ditemukan saat menjawab pertanyaan pengguna: *"kalau jabatan diberi akses lihat bagan tapi klik
per karyawan tidak bisa, itu gimana sekarang handle-nya?"* Jawabannya, sebelum diperiksa: **tidak
ditangani sama sekali.**

`GET /employees` menyaring 12 karyawan menjadi 7 dengan benar. Tapi id-nya berpola
(`emp-joko`, `emp-budi`, …) dan `GET /employees/{id}` memeriksa *level* — sama seperti Bug 2 —
tanpa pernah memeriksa *scope*. Menebak satu id melewati seluruh penyaringan list begitu saja.

Lebih halus lagi: bahkan setelah id fokusnya divalidasi, `GET /{id}/t-shape` tetap bocor. Buktinya
di server sungguhan — Budi (Kepala Penjualan, jangkauan `SUBORDINATE_DATA`) membuka T-Shape
dirinya sendiri:

```json
"superior": {"id": "emp-hendra", "name": "Bpk. Hendra Kusuma", "email": "hendra.owner@wemade.id", ...}
"peerHeads": ["Joko Susilo", "Siti Rahma", "Anton Prasetyo"]
```

`emp-joko` **ditolak 403** saat diakses langsung, tapi email dan telepon Joko tetap muncul lengkap
sebagai `peerHeads` di respons T-Shape yang sama. Memvalidasi node fokus saja memberi rasa aman
yang keliru — objek hasilnya (`TShapeHierarchyResult`) membawa empat kelompok karyawan lain
(`superior`, `peerHeads`, `subordinates`, `peersInDepartment`), dan keempatnya harus disaring
sendiri-sendiri:

```kotlin
internal fun TShapeHierarchyResult.restrictToReach(reach: OrgChartDataReach): TShapeHierarchyResult {
    if (reach.isUnrestricted) return this
    return copy(
        superior = superior?.takeIf { reach.allowsEmployee(it.id.value) },
        peerHeads = peerHeads.filter { reach.allowsEmployee(it.id.value) },
        subordinates = subordinates.filter { reach.allowsEmployee(it.id.value) },
        peersInDepartment = peersInDepartment.filter { reach.allowsEmployee(it.id.value) }
    )
}
```

Kelasnya juga diganti nama dari `OrgChartWriteReach` ke `OrgChartDataReach` di tengah perbaikan
ini — nama lamanya sudah tidak jujur begitu dipakai untuk baca satu-per-satu, bukan hanya tulis.
Pelajarannya: kalau sebuah abstraksi mulai dipakai untuk kasus yang namanya tidak lagi mencakup,
ganti namanya saat itu juga — bukan nanti, karena "nanti" jarang datang.

### Bug 4b — apakah list dan detail perlu dipisah?

Pertanyaan susulannya masuk akal: *"list sama detail dipisah aja gimana?"* Jawabannya butuh
diperiksa dulu, bukan diasumsikan — dan pemeriksaannya mengubah jawabannya:

```kotlin
// EmployeeDto.toJsonList() memanggil toJson() yang SAMA dengan endpoint detail
fun toJsonList(employees: List<OrgNode>): String =
    "[${employees.joinToString(",") { toJson(it) }}]"
```

`GET /employees` **sudah** mengirim field lengkap — email, telepon — untuk setiap baris di list.
Endpoint detail tidak menambah data baru; ia hanya mengulang satu baris yang sama. Jadi
"pisahkan list dari detail" sebenarnya dua pertanyaan berbeda:

1. **Wewenangnya** — sudah identik hari ini (`VIEW` untuk keduanya), dan itu masuk akal: kalau
   tidak boleh membaca satu karyawan, tidak ada alasan boleh membaca 12 karyawan sekaligus dalam
   satu respons.
2. **Bentuk datanya** — list mengirim field yang dipakai kartu bagan (`name`, `roleTitle`,
   `department`, `level`); detail baru mengirim `email`/`phone`. Ini murni soal ukuran payload,
   bukan wewenang, dan baru masuk akal dikerjakan **setelah** Bug 4 tertutup — memisahkan payload
   sebelum jangkauannya benar hanya memindahkan kebocoran, bukan menutupnya.

Sebelum menulisnya, cek dulu siapa yang memakai field itu:

```bash
grep -nE "node\.(name|email|phone|roleTitle|level|department)" \
  app/shared/.../presentation/orgchart/components/OrgNodeCard.kt
```

Ternyata kartu bagan hanya merender `name`, `roleTitle`, `department.shortName`, warna, dan
`level` — tidak pernah `email` atau `phone`. Dan `OrgChartApiClient` tidak pernah memanggil
endpoint detail sama sekali; semuanya dibaca dari hasil list yang di-cache. Jadi mengirim
email+telepon di list bukan kebutuhan fitur — itu **over-fetching**: pabrik dengan 200 staf
berarti 200 nomor telepon mendarat di browser setiap kali bagan dibuka, padahal tidak ada satu
piksel pun yang memakainya.

### Pelajaran yang bisa dibawa ke task lain

1. **Nilai bawaan sebuah field bisa menjadi lubang keamanan.** `scope` yang bawaannya paling luas
   aman selama selalu dibaca bersama `level`. Begitu ada satu pemanggil yang lupa, bawaan itu
   berubah menjadi izin penuh. Kalau kamu mendesain value object seperti ini, pertimbangkan
   bawaan yang paling *sempit*, atau paksa keduanya dibaca bersama.

2. **"Menu sudah disembunyikan" bukan jawaban atas "apakah ini aman".** Menu adalah tampilan;
   endpoint adalah pintu. Selalu tanyakan: *kalau orang ini memanggil API-nya langsung, apa yang
   dia dapat?*

2b. **Membatasi baca tanpa membatasi tulis menghasilkan sistem yang tidak konsisten.** Setiap kali
   kamu menambahkan penyaringan data, tanyakan pasangannya: *bolehkah ia menulis ke tempat yang
   tidak bisa ia lihat?* Jawabannya hampir selalu tidak.

2c. **Menyaring sebuah list tidak otomatis menyaring detailnya.** Kalau id-nya bisa ditebak
   (berpola, berurutan, atau sekadar diketahui dari respons lain), endpoint detail butuh
   penjagaannya sendiri — memakai aturan penyaringan yang **sama persis** dengan list-nya, bukan
   yang mirip.

2d. **Objek gabungan (satu respons berisi beberapa entitas) harus disaring per-bagian.** Memvalidasi
   hanya entitas utamanya (fokus) memberi rasa aman yang keliru kalau entitas pendamping di objek
   yang sama (superior, peer, related records) tidak ikut disaring. Periksa setiap field yang
   berisi entitas lain, satu per satu.

3. **Test yang tidak pernah kamu lihat gagal adalah test yang belum terbukti.** Setelah menulis
   `OrgChartAccessApiTest`, saya sengaja melumpuhkan guard-nya sebentar untuk memastikan empat
   test-nya benar-benar merah. Test yang lolos baik dengan maupun tanpa perbaikan tidak menjaga
   apa pun.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Strateginya berlapis sesuai lapisan arsitekturnya:

**Domain (murni, tanpa DB, tanpa framework)** — ini yang paling banyak dan paling murah:

```kotlin
@Test
fun ownerBypass_shouldNotResurrectAModuleTheTenantDoesNotHave() {
    val decision = AccessDecisionEngine.explain(
        persona = persona(role = null, isOwner = true),
        module = BusinessModule.DYNAMIC_RBAC,
        role = null,
        assignments = emptyList(),
        grantedModules = BusinessModule.entries.toSet() - BusinessModule.DYNAMIC_RBAC
    )

    assertEquals(AccessSource.NOT_ENTITLED, decision.source)
    assertFalse(decision.config.isAccessible)
}
```

Test ini menjaga **urutan** cek di dalam `explain()`. Kalau suatu hari ada yang memindahkan blok
bypass Owner ke atas, test inilah yang berteriak.

**Test yang menjaga bentuk arsitektur** — jenis ini sering dilupakan tapi sangat berharga:

```kotlin
@Test
fun governanceModules_shouldFillNoCapabilitySlot() {
    BusinessModule.governance.forEach { module ->
        assertEquals(null, ModuleArchetype.forModule(module),
            "${module.code} tidak berdiri di lini produksi…")
    }
}
```

Ia tidak menguji perilaku pengguna; ia menguji **keputusan desain**. Kalau nanti ada yang
menambah modul tata kelola keempat dan iseng memberinya arketipe, test ini menahannya.

**Presentation (tanpa merender)** — mungkin karena `buildNavMenu` sengaja pure function yang
mengembalikan data, bukan Composable:

```kotlin
@Test
fun first_accessible_screen_when_everything_locked_should_be_null()
```

**Test yang menjaga sisi API** (`OrgChartAccessApiTest`) — lahir dari dua bug di §5b:

```kotlin
@Test
fun listEmployees_whenModuleAccessIsNone_shouldBeForbidden() = testApplication {
    installApp(role("role-operator", salesDeptId, AccessLevel.NONE, DataScope.ALL_TENANT_DATA))
    val response = client.get("/api/tenant/employees") {
        asStaff(slug, customRoleId = "role-operator", departmentId = salesDeptId)
    }
    assertEquals(HttpStatusCode.Forbidden, response.status)
}
```

Perhatikan `listEmployees_withSubordinateScope_shouldReturnFewerThanAllTenantScope`: ia
**membandingkan dua pemanggil**, bukan mencocokkan angka tetap. Menuliskan `assertEquals(7, …)`
akan membuat test itu pecah setiap kali ada yang menambah karyawan contoh — dan test yang sering
pecah tanpa alasan akan segera dimatikan orang.

**Status verifikasi akhir**: 559 test hijau (core 324, app:shared 81, server 154), migrasi V18 & V19
tercatat sukses di `flyway_schema_history` terhadap database terisi, dan seluruh perilaku kunci
diuji manual lewat `curl` terhadap server yang berjalan. Yang **masih** terbuka: pemeriksaan visual
layarnya — bug layout clay tidak tertangkap satu pun test di daftar ini; dan payload list yang
masih lebih gemuk dari yang dipakai kartu bagan (Bug 4b), menunggu keputusan produk sebelum
dipangkas.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1 — Buat modul tata kelola keempat.** Tambahkan `AUDIT_LOG` ke `BusinessModule`
      (tabel `audit_logs` sudah ada sejak V12). Jalankan `./gradlew :core:compileKotlinJvm` dan
      catat **berapa file** yang diminta kompilator untuk disesuaikan. Bandingkan dengan
      pengalamanmu menambah fitur yang identitasnya berupa `String`.

- [ ] **Tantangan 2 — Bocorkan datanya, lalu tambal.** Nonaktifkan sementara penyaringan di
      `applyOrgChartScope` (jangan di-commit). Login sebagai persona Sales dengan scope
      `SUBORDINATE_DATA`, buka tab Network, dan lihat response `/api/tenant/employees`. Berapa
      karyawan yang terkirim? Nyalakan kembali penyaringannya dan bandingkan. Ini cara tercepat
      merasakan kenapa "sembunyikan di UI" bukan keamanan.

- [ ] **Tantangan 3 — Pikirkan `OWN_DATA_ONLY` untuk Bagan Organisasi.** Saat ini ia
      mengembalikan satu baris: kartu penonton sendiri. Bagan berisi satu orang tidak berguna
      sebagai bagan. Menurutmu, mana yang benar: (a) biarkan seperti ini dan jangan pernah
      memilihnya untuk modul ini, (b) sembunyikan opsinya di UI khusus untuk Org Chart, atau
      (c) artikan sebagai "saya + atasan langsung"? Tuliskan argumenmu — pertanyaan desain seperti
      ini tidak punya jawaban tunggal, dan kemampuan membelanya lebih penting daripada jawabannya.

- [ ] **Tantangan 4 — Baca V18 sebagai penyerang.** Kalau kamu superadmin yang jahil, perintah
      `curl` apa yang bisa membuat sebuah tenant kehilangan akses ke layar RBAC-nya selamanya?
      Lalu telusuri: apa yang tercatat di `audit_logs` setelah kamu melakukannya, dan apakah itu
      cukup untuk melacakmu?

---

## 📎 Lampiran: Peta Berkas yang Disentuh

| Lapisan | Berkas | Peran |
|---|---|---|
| Domain | `rbac/BusinessModule.kt` | `ModuleKind`, kategori `GOVERNANCE`, 3 entri baru |
| Domain | `rbac/AccessDecisionEngine.kt` | `NOT_ENTITLED` + parameter `grantedModules` |
| Domain | `rbac/CustomRole.kt` | `enforce()`, `withModulePermissions()`, preset peran |
| Domain | `orgchart/OrgChartVisibility.kt` | **baru** — penyaringan jangkauan murni |
| Domain | `pipeline/OperationalModuleContract.kt` | `forModule()` jadi nullable |
| Domain | `pipeline/TenantEntitlementGrants.kt` | `withModule()` |
| Domain | `pipeline/TenantModuleEntitlement.kt` | `permitsModule()`, kuota abai-governance |
| Server | `routes/PipelineRoutes.kt` | `GET /api/tenant/entitlement` |
| Server | `routes/OrgChartAccessGuard.kt` | **baru** — penjagaan level + penyaringan scope |
| Server | `routes/EmployeeRoutes.kt` | 9 endpoint dijaga: VIEW / OPERATE / MANAGE |
| Server | `plugins/CallerPrincipal.kt` | `departmentId`, `customRoleId`, `email` |
| Server | `V18__add_governance_modules.sql` | **baru** — backfill entitlement + seed katalog |
| Server | `V19__backfill_governance_role_permissions.sql` | **baru** — backfill matriks jabatan |
| Client | `navigation/NavMenu.kt` | hapus daftar admin hardcode, `firstAccessibleScreen()` |
| Client | `workspace/GovernanceModuleGate.kt` | **baru** — gerbang bertingkat |
| Client | `workspace/ModuleNotEntitledCard.kt` | **baru** — pesan "belum berlangganan" |
| Client | `tenant/TenantModuleEntitlementDialog.kt` | **baru** — dialog superadmin |
| Client | `infrastructure/api/AdminApiClient.kt` | **baru** — klien rute platform |
