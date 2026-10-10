# Modul Pembelajaran: Data Sampel vs Keadaan Kosong di Org Chart dan RBAC (TRD-PLAT-010, T1/T2/T3 dan §11)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Model keadaan eksplisit (`Loading | Empty | Loaded | Failed`), "sampel adalah aksi, bukan keadaan", server sebagai satu-satunya sumber kebenaran, restore sadar-pack (409), tenant dari sesi, Composable dengan satu call site
> **Prasyarat**: Dasar Compose Multiplatform + ViewModel/StateFlow, Ktor client/route, konsep Domain Pack, RBAC jabatan/divisi
> **Referensi Task**: `docs/trd/TRD-PLAT-010-sample-data-vs-empty-state-orgchart-rbac.md`. Komit utama: T1 `aef98135` + `d2f41020` (merge `616c6700`); T2 `5db2e8a5` (merge `189ca6ae`); T3 `321a9678` + `549fa170` (merge `c19fb5a6`); TRD `1ecbfec3`, `ca566280`, `1fc99865`

---

## 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: Org Chart dan RBAC memulai dari **data contoh konveksi** secara otomatis. Tenant klinik yang kosong melihat divisi "Produksi & PPIC" dan 6 jabatan garment seolah miliknya. Lebih berbahaya: respons sukses `[]`, galat jaringan, dan "sedang memuat" **tampak identik** di layar (semuanya = sampel), karena ada `catch` kosong ("Silently fallback to presets") dan kondisi `isNotEmpty()` yang menahan sampel. Di sisi server, `restore-presets` menulis divisi garment ke database tenant mana pun.
- **Analogi**: papan petunjuk bandara yang menampilkan jadwal penerbangan contoh saat koneksi putus. Penumpang tidak tahu bedanya jadwal asli dan contoh, dan salah naik pesawat.
- **Hasil akhir**:
  - T1: klien Org Chart punya empat keadaan eksplisit, tanpa sampel otomatis; "Muat contoh" dieksekusi server lalu dibaca ulang.
  - T2: `restore-presets` sadar-pack (409 untuk pack tanpa contoh), tenant demo CMT/D2C mendapat data nyata lewat migrasi V98.
  - T3: RBAC tanpa preset lokal, tenant dari sesi (bukan default demo), dan `GovernanceModuleGate` memanggil `content` dari satu call site.

---

## 2. "Start dari Mana?" — Urutan Penulisan

1. **Langkah 0 - Inventaris pembaca/penulis sampel.** `grep defaultPresets|createSampleEmployees|createFactoryPresets` di `core`, `server`, `app/shared`. Pelajaran: bug sampel punya **tiga** sumber (klien Org Chart, klien RBAC, dan server restore).
2. **Langkah 1 - Model keadaan** (`OrgChartLoadState`), karena semua keputusan UI turun darinya. Fungsi `from(departments, employees)` murni dan bisa diuji tanpa jaringan.
3. **Langkah 2 - Ekstrak pemuatan dari ViewModel** ke `OrgChartDataLoader` (ratchet: ViewModel 1196 baris tak boleh membesar; sesudah T1 tercatat 1057 di komit, 1028 di HEAD).
4. **Langkah 3 - Server**: predikat `StarterOrgChartPolicy` + gerbang 409 setelah pemeriksaan wewenang.
5. **Langkah 4 - Data**: migrasi V98 agar demo tetap berisi lewat database, bukan klien.
6. **Langkah 5 - RBAC**: pecah dengan pola yang sama (`RbacLoadState`, `RbacDataLoader`).
7. **Langkah 6 - Temuan §11**: tenant sesi dan call site tunggal pada gerbang.
8. **Langkah 7 - Tes paritas**: tes lama diberi `seed` eksplisit; tes baru memakai tenant non-garment (klinik).

Pola kerja penting: tulis dulu probe/tes yang **membuktikan atau membantah dugaan** sebelum mengubah kode (TRD §6, §11).

---

## 3. Bedah Blok Kode

### Blok A (T1): Keadaan pemuatan (`presentation/orgchart/OrgChartLoadState.kt`)

```kotlin
sealed interface OrgChartLoadState {
    data object Loading; data object Empty
    data class Loaded(val departments: List<Department>, val employees: List<OrgNode>)
    data class Failed(val message: String)
    companion object {
        fun from(departments: Result<List<Department>>, employees: Result<List<OrgNode>>): OrgChartLoadState {
            val depts = departments.getOrElse { return Failed(failureMessage(it)) }
            val emps = employees.getOrElse { return Failed(failureMessage(it)) }
            return if (depts.isEmpty() && emps.isEmpty()) Empty else Loaded(depts, emps)
        }
```

**Kenapa begini?**
- Empat keadaan yang **tidak boleh dicampur**. "Kosong" adalah fakta dari server; "gagal" adalah ketidaktahuan. Mengubah salah satunya menjadi sampel adalah berbohong kepada pengguna.
- Salah satu sumber gagal = `Failed` seluruhnya (bukan "setengah sampel"). Divisi saja atau karyawan saja tetap `Loaded`.
- `sealed interface` + `data object` sesuai aturan proyek; tidak ada `else`.

### Blok B (T1): Sampel jadi parameter, bukan default (`OrgChartSeed.kt`, `OrgChartDataLoader.initialState`)

```kotlin
enum class OrgChartSeed { None, GarmentSample }   // None = default produksi
```

`None` memberi `Loading` bila ada klien, `Empty` bila tidak. `GarmentSample` **hanya untuk tes** dan sengaja bukan default: pemanggil yang lupa mengisinya tidak mengulang bug (K8). Tiga tes lama (Delete/Scoping/SuperiorAutoFill) hanya diberi `seed = OrgChartSeed.GarmentSample` tanpa mengubah asersi, itulah bukti paritas.

### Blok C (T1): Data live menang penuh (`OrgChartDataLoader.fetch/apply`)

```kotlin
suspend fun fetch(client, tenantSlug) = try {
    OrgChartLoadState.from(client.getDepartments(tenantSlug), client.getEmployees(tenantSlug))
} catch (e: CancellationException) { throw e }
  catch (e: Exception) { OrgChartLoadState.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Gagal memuat struktur organisasi.") }
```

**Kenapa begini?**
- `CancellationException` **harus dilempar ulang**, jika tidak, coroutine yang dibatalkan malah menjadi `Failed`.
- `apply(Empty)` mengosongkan `departments`, `employees`, dan seleksi; tidak ada sisa sampel (menutup baris lama yang menahan karyawan sampel saat divisi live ada tetapi karyawan live kosong).

### Blok D (T1): "Muat contoh" menunggu server (`OrgChartViewModel`, event `RestoreDefaultPresets`)

Klien tidak lagi mengubah state ke sampel secara optimistis. Ia memanggil `restoreDepartmentPresets` lalu `restoreEmployeePresets`, dan **hanya jika keduanya sukses** memanggil `loader.fetch` lalu `apply`. Gagal (403/409/galat) = toast ramah (`OrgChartErrorMessages.restoreFailure`), state tak berubah. Tanpa klien: toast "Tidak terhubung ke server; contoh tidak dapat dimuat."

### Blok E (T2): Predikat dan gerbang 409

```kotlin
object StarterOrgChartPolicy {
    fun isAvailableFor(pack: DomainPack): Boolean = pack.code == GarmentDomainPack.CODE
```

```kotlin
internal suspend fun ApplicationCall.requireStarterOrgChart(tenant: TenantContext): Boolean {
    val pack = runCatching { tenant.pack }.getOrNull()
    if (pack != null && StarterOrgChartPolicy.isAvailableFor(pack)) return true
    respond(HttpStatusCode.Conflict, ...); return false
}
```

**Kenapa begini?**
- Predikat ini adalah **jembatan Strangler yang dinyatakan**: contoh yang ada adalah divisi konveksi, jadi hanya pack garment yang menyediakannya. Kelak menjadi data pack (`DomainPack.starterOrgChart`, T4, ditunda).
- Urutan: wewenang dulu (403), baru pack (409). Peran tak berwenang tidak boleh tahu apakah pack punya contoh. Terpasang di `DepartmentRoutes` (baris 277) dan `EmployeeRoutes` (baris 368).
- Pack yang tak bisa di-resolve diperlakukan sama: **tidak ada contoh**, tidak jatuh ke garment (Kontrak 4).
- Bonus yang ditemukan: `requireManageOrgChartForBulkWrite` menutup celah restore karyawan untuk token tanpa jabatan/divisi (cikal bakal TRD-PLAT-011).

### Blok F (T2): Migrasi V98

Menyemai lima divisi dan sembilan karyawan untuk `ten-demo-cmt` dan `ten-demo-d2c` dengan `ON CONFLICT DO NOTHING` dan `JOIN public.tenants` (tenant tak ada dilewati). Alasan: sebelum ini kedua demo "tampak berisi" hanya karena sampel yang dirakit klien. Setelah sampel klien dihapus, data demo harus **nyata di DB** (Q2). `ten-demo-001` tidak disentuh.

### Blok G (T3): `RbacLoadState` dan `RbacDataLoader`

```kotlin
val r = roles.getOrElse { return Failed(message(it)) }
...
return if (r.isEmpty() && d.isEmpty()) Empty else Loaded
```

`RbacDataLoader.load(client, tenantSlug)` memuat jabatan, divisi, penugasan, lalu jumlah karyawan terpisah: `employeeCount = client.getEmployees(tenantSlug).getOrNull()?.size`. `null` berarti **tak terbaca** (admin RBAC tanpa akses Org Chart) dan chip "Total Karyawan" disembunyikan (`if (totalUsers != null)`), bukan ditampilkan 0. Fallback `onFailure` di `RbacAccessPolicyRepository` (roles/departments/employees sampel) dihapus.

### Blok H (T3 / §11): Tenant dari sesi

```kotlin
val rbacSlug = session?.tenantSlug
if (rbacSlug == null) RbacNoTenantView() else DynamicRbacScreen(tenantId = ..., tenantSlug = rbacSlug, ...)
```

`DynamicRbacScreen(tenantId: TenantId, tenantSlug: String, viewModel = remember(tenantId, tenantSlug) { ... })`: **parameter wajib tanpa default**. Sebelumnya `remember { DynamicRbacViewModel() }` memakai default `wemade-demo`, sehingga layar RBAC tenant lain selalu mengirim slug demo.

### Blok I (T3 / §11): Satu call site `content(...)`

```kotlin
when (val view = resolveGateView(decision)) {
    GateView.NotEntitled -> GateMessage { ... }
    GateView.Denied -> GateMessage { ... }
    is GateView.Open -> content(view.config)
}
internal fun resolveGateView(decision: AccessDecision?) = when {
    decision == null -> GateView.Open(ModuleAccessConfig())
    decision.blockedByEntitlement -> GateView.NotEntitled
    !decision.config.isAccessible -> GateView.Denied
    else -> GateView.Open(decision.config)
}
```

Dulu ada dua call site `content(...)` (keputusan belum tiba vs sudah tiba). Dua call site = dua grup komposisi berbeda, sehingga `remember { ... }` di dalamnya (ViewModel layar) dibuat ulang ketika keputusan tiba, dan tiga permintaan RBAC dikirim dua kali. Logika keputusan dipisah murni (`resolveGateView`) agar bisa diuji (`GovernanceGateViewTest`).

---

## 4. Teknologi & Pendekatan: The "Why"

| Pendekatan | Alternatif | Alasan | Risiko alternatif |
|---|---|---|---|
| Sampel = aksi pengguna, dieksekusi server | Sampel sebagai keadaan awal klien | Satu sumber kebenaran, tidak berbohong | Tenant kosong tampak berpenghuni |
| Sumber kebenaran = isi DB tenant (tanpa flag "demo" di klien) | `if (slug == "wemade-demo")`; kolom `is_demo`; `TenantStatus.TRIAL` | Kode mesin tak menyebut tenant tertentu (FR-7) | Rapuh; tenant TRIAL asli dianggap demo |
| Predikat pack di server (409) | Sembunyikan tombol di klien saja | Fail-open bila hanya klien | Garment tertulis ke DB klinik |
| `seed` parameter, default `None` | Default sampel | Pemanggil lupa = bug kembali | Bug lama hidup lagi |
| Hapus preset lokal RBAC | Tunggu `ActiveTenantPack` lalu preset lokal | Server sudah memutuskan jabatan per pack | Sumber kedua yang bisa salah/balapan |

---

## 5. Jebakan Nyata yang Ditemukan

1. **Tiga kondisi identik di layar**: sukses-kosong, galat, dan memuat semuanya menampilkan sampel karena `catch` kosong dan `isNotEmpty()`. Perbaikan = model keadaan eksplisit, bukan banner.
2. **Sisa sampel setengah-jalan**: baris lama menahan karyawan sampel meski divisi live ada. "Data live menang penuh" menutupnya.
3. **Server ikut menanam garment**: `restore-presets` tidak memeriksa pack. Menyembunyikan tombol di klien tidak cukup.
4. **Restore optimistis**: klien mengubah state sebelum server menjawab dan mengabaikan hasil 403/gagal.
5. **T3: RBAC tak pernah memakai slug sesi (§11)**: `DynamicRbacViewModel` punya default `tenantSlug = "wemade-demo"` dan satu-satunya pemanggil tak meneruskan slug. Server benar menolak (`TenantResolutionPlugin`: `requestedSlug != principal.tenantSlug` -> 403); **jangan melonggarkan server**. Org Chart dan Builder tidak terdampak. TRD §11 menyatakan rencana T3 awal (§5) "tidak cukup": tanpa perbaikan slug, menghapus fallback sampel malah membuat 403 tampak sebagai keadaan galat/kosong.
6. **Dua kali per rute**: dugaan penyebab di TRD (dua call site `content`), diperbaiki di `321a9678`. TRD menandainya "belum dibuktikan"; saya tidak menemukan tes yang membuktikan berkurangnya jumlah permintaan di level jaringan, hanya tes murni `resolveGateView` yang menegaskan cabang `Open` untuk keputusan belum tiba dan terbuka.
7. **Dugaan balapan `ActiveTenantPack` (6 jabatan di klinik)**: TRD menyatakan belum terbukti. Kode memang tak lagi membaca pack aktif untuk memutuskan daftar jabatan, dan ada tes regresi (`server memberi satu jabatan Owner - hanya itu yang tampil walau pack aktif masih garment`), tetapi apakah balapan itu penyebab nyata gejala asli **tidak dapat diverifikasi dari kode**.
8. **Fallback slug demo yang masih ada (utang dicatat, tidak diubah)**: di HEAD `App.kt` masih memuat `session?.tenantSlug ?: "wemade-demo"` (baris 361, 437, 461, 586). `PublicAuthRoutes.kt:132` kini berkomentar "Tanpa fallback senyap ke wemade-demo" (perubahan dari komit lain setelah T3; saya tidak menelusuri komitnya). `AuthViewModel` masih memakai `TenantId("ten-default")` bila `tenantId` user null (terkonfirmasi di HEAD, tiga tempat).
9. **Tes yang menguji bug**: tes lama bergantung pada state awal bersampel; solusinya `seed` eksplisit, bukan mengubah asersi.
10. **Ratchet**: `OrgChartViewModel.kt` 1196 -> 1028 baris di HEAD (hard limit 600, masih utang). Pemecahan mengikuti tanggung jawab (`OrgChartDataLoader`, `OrgChartLoadState`, `OrgChartStatusViews`, `OrgChartDefaultSuperior`).

---

## 6. Cara Memverifikasi

- Domain/klien murni: `OrgChartLoadStateTest` (commonTest), `StarterOrgChartPolicyTest` (core).
- Klien dengan MockEngine (fixture klinik): `OrgChartViewModelServerStateTest` (Empty, Failed, setengah gagal = Failed, Loaded sebagian, "Muat contoh" 200/403/409, retry Loading -> Empty -> Loaded).
- RBAC: `DynamicRbacViewModelTest` (slug diteruskan ke klien, Empty/Failed/Loaded, total karyawan null, Reload). Gerbang: `GovernanceGateViewTest`.
- Server: `StarterOrgChartRestoreApiTest` (klinik Owner 409 dan tidak menulis apa pun; peran tak berwenang 403 **sebelum** pack ditanya; garment staff tanpa MANAGE 403; garment Owner tetap 200; restore klinik tidak memengaruhi tenant garment) dan `DemoOrgChartSeedMigrationTest` (V98).
- Perintah: `./gradlew :core:jvmTest --tests '*StarterOrgChartPolicyTest*'`, `./gradlew :app:shared:jvmTest --tests '*OrgChart*' --tests '*DynamicRbac*' --tests '*GovernanceGate*'`, `./gradlew :server:test --tests '*StarterOrgChartRestoreApiTest*' --tests '*DemoOrgChartSeedMigrationTest*'`. Tidak dijalankan saat menulis dokumen ini.
- **Yang belum terverifikasi secara visual (TRD §6.7/§10)**: urutan Loading -> Empty/Loaded, keadaan galat, 360 dp, bukti B3 lewat login asli. Cek dengan tenant uji non-garment (`bordir-uji`/klinik) dan `wemade-demo`, login superadmin demo bila diminta.

---

## 7. Tantangan Mandiri

- [ ] Tulis tes yang gagal bila seseorang menambahkan kembali `isNotEmpty()` yang menahan sampel pada `OrgChartDataLoader.apply`.
- [ ] Rancang `DomainPack.starterOrgChart` (T4) dan jelaskan apa yang harus dijaga agar byte-identik untuk pack garment.
- [ ] Buktikan atau bantah dugaan "dua kali per rute" dengan menghitung permintaan pada MockEngine saat keputusan akses tiba setelah ViewModel dibuat.
