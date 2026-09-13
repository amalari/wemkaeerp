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

**Yang belum tercakup, dan saya sebut terus terang**: test integrasi PostgreSQL dan pemeriksaan
visual. Docker daemon tidak berjalan di lingkungan ini, jadi migrasi V18 **belum pernah benar-benar
dijalankan terhadap database**, dan layarnya belum dilihat dengan mata. Keduanya wajib dikerjakan
sebelum merge — bug layout dan bug SQL sama-sama tidak tertangkap unit test mana pun.

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
| Server | `routes/EmployeeRoutes.kt` | penyaringan scope otoritatif |
| Server | `plugins/CallerPrincipal.kt` | `departmentId`, `customRoleId`, `email` |
| Server | `V18__add_governance_modules.sql` | **baru** — backfill + seed katalog |
| Client | `navigation/NavMenu.kt` | hapus daftar admin hardcode, `firstAccessibleScreen()` |
| Client | `workspace/GovernanceModuleGate.kt` | **baru** — gerbang bertingkat |
| Client | `workspace/ModuleNotEntitledCard.kt` | **baru** — pesan "belum berlangganan" |
| Client | `tenant/TenantModuleEntitlementDialog.kt` | **baru** — dialog superadmin |
| Client | `infrastructure/api/AdminApiClient.kt` | **baru** — klien rute platform |
