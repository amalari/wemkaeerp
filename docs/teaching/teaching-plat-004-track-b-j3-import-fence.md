# 🎓 Modul Pembelajaran: Pagar Impor Kode Khusus Tenant (J3) — TRD-PLAT-004 Track B

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Arsitektur berlapis dan arah ketergantungan, registri sebagai titik tunggal, tes arsitektur yang memblokir, memindah paket Kotlin tanpa mengubah perilaku
> **Prasyarat**: Tahu apa itu pack tenant dan modul (`ModuleId`), paham `internal` di Kotlin, pernah membaca `RouteOwnership` dan `ModuleSchemaMap`
> **Referensi Task**: [`TRD-PLAT-004`](../trd/TRD-PLAT-004-module-ownership-lanes.md) keputusan P2 · [`PLAN-module-ownership-lanes.md`](../plannings/PLAN-module-ownership-lanes.md) Track B

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah Nyata**: Sebelum Track B, tiga file *mesin* (`RouteOwnership`, `ModuleSchemaMap`, `PilotTenantSeeder`) mengimpor `LayananPilotPack` langsung, dan `DomainRouteWiring` memanggil `layananChangeRequestRoutes(...)` dengan nama. Itu aman untuk **satu** modul tenant. Tapi tiap modul tenant baru akan menambah satu impor lagi ke file mesin yang sama. Setahun kemudian mesin "mengenal" lima tenant, dan menghapus satu modul tenant berarti mengedit kode mesin yang dipakai semua orang.
- **Analogi Sederhana**: Gedung perkantoran. Resepsionis (mesin) tidak menghafal nomor telepon tiap penyewa; ia membuka **buku tamu** (registri). Penyewa baru cukup menulis namanya di buku tamu — meja resepsionis tidak diubah.
- **Hasil Akhir yang Diharapkan**: Mesin tidak pernah menyebut kode tenant. Semua kode tenant tinggal di dua paket (`domain/pack/tenant/…` di core, `tenant/…` di server), dan satu file, `TenantPackContributions`, menjadi pintu satu-satunya. Sebuah tes menggagalkan build kalau ada yang melanggar.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0: Ukur dulu jejaknya.** `grep` siapa saja yang menyebut kode tenant. Tanpa angka ini kita tidak tahu apa yang harus dipagari (hasilnya: 3 impor + 1 panggilan).
2. **Langkah 1: Pindah paket, jangan ubah perilaku.** Kumpulkan kode tenant ke paket tersendiri. Kode pack, id modul, nama schema, dan migrasi **tidak boleh** berubah — hanya baris `package`.
3. **Langkah 2: Buat registri.** Satu objek yang mendeklarasikan apa yang disumbangkan modul tenant: pack, tabel per modul, awalan route, dan fungsi pendaftaran route.
4. **Langkah 3: Ubah mesin agar membaca registri**, lalu hapus impor langsung.
5. **Langkah 4: Tulis pagar (tes) yang memblokir**, dan **buktikan ia bisa gagal** (fixture + mutasi nyata). Pagar yang tidak pernah terlihat gagal hanyalah dekorasi.
6. **Langkah 5: Jalankan semua tes terdampak** — memindah paket paling sering rusak di tes yang menulis nama lama sebagai string.

Kenapa pagar ditulis **terakhir**? Karena sebelum kodenya dikumpulkan, pagar akan langsung merah di mana-mana dan tidak bisa dibedakan dari kegagalan sungguhan.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Registri
```kotlin
object TenantPackContributions {
    class Contribution(
        val pack: DomainPack,
        val tables: Map<ModuleId, Set<String>>,
        val routePrefixes: Map<String, ModuleId>,
        val registerRoutes: Route.(RoleRepository, ModuleAssignmentRepository) -> Unit
    )
    val all: List<Contribution> = listOf(Contribution(pack = LayananPilotPack.pack, /* … */))
    val tables get() = all.flatMap { it.tables.entries }.associate { it.key to it.value }
}
```
**Mengapa begini?**
- Registri berisi **data + satu lambda**, bukan logika. Mesin hanya bertanya "tabel apa yang kau punya?" dan "daftarkan routemu".
- `registerRoutes` berbentuk `Route.(…) -> Unit` supaya modul tenant tetap memakai gerbang RBAC-nya sendiri (`requireModuleAccess`); registri tidak melonggarkan keamanan.

### Blok B: Mesin membaca registri
```kotlin
// ModuleSchemaMap
val byModule = mapOf( /* modul garment */ ) + TenantPackContributions.tables
// RouteOwnership
) + TenantPackContributions.routePrefixes.map { (prefix, m) -> prefix to RouteOwner.Module(m) }
// DomainRouteWiring
TenantPackContributions.all.forEach { it.registerRoutes(this, roleRepo, assignmentRepo) }
```
- Tiga file mesin justru **lebih pendek** setelah perubahan (256→254, 87→84, 75→72). Pagar yang baik biasanya mengurangi kode, bukan menambah.

### Blok C: Pagar yang memblokir
```kotlin
private val j3Reference = Regex("""com\.eventverse\.app\.(domain\.pack\.tenant|tenant\.[a-z][A-Za-z0-9_]*)\.""")
private fun mayReferenceJ3(path: String) = "/domain/pack/tenant/" in path || "/com/eventverse/app/tenant/" in path
```
- Regex hanya mengenai sub-paket tenant (`tenant.layanan.`), **bukan** `tenant.TenantPackContributions` (huruf besar setelah `tenant.`). Itu sebabnya mesin boleh menyebut registri.
- Yang dipindai: `src/main` dan `src/commonMain`. **Tes dikecualikan** dengan sengaja — tes modul tenant memang mengimpor J3.
- Tiga tes: (1) kode asli bersih, (2) fixture pelanggar harus terdeteksi, (3) fixture sah (paket J3, registri, akses ke registri) harus lolos. Tes (2) dan (3) membuktikan pemindainya tidak buta dan tidak paranoid.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa Ini? | Risiko Alternatif |
|---|---|---|---|
| **Registri objek statis** | Service locator / reflection scanning | Terlihat jelas di satu file, aman di kompilasi 5 target KMP | Reflection tidak jalan di JS/Wasm; locator menyembunyikan dependensi |
| **Tes yang memblokir** | Skrip audit yang "melapor" (`audit-variability.sh`) | Build merah = tidak bisa di-merge | Laporan yang bisa diabaikan = pagar yang diabaikan |
| **Satu pohon sumber** (P3) | Repo/binary per tenant | Satu rilis, satu CI; pagar P2/P4 cukup untuk isolasi saat ini | Matriks rilis N tenant; ditinjau ulang bila tenant menuntut kode tertutup |
| **Pindah paket saja** | Rename modul sekalian | Nama schema = kode modul, mengganti nama = migrasi tabel | Risiko data tanpa manfaat |

---

## ⚠️ 5. Jebakan Pemula

1. **Nama lama tersisa sebagai string.** Setelah pindah paket, kompilasi hijau tapi dua tes memakai `"com.eventverse.app.domain.pack.LayananPilotPack.pack"` sebagai *string* (ekspresi Kotlin yang digenerate) dan baru gagal saat dijalankan. *Solusi*: `grep` nama lama di seluruh repo, termasuk string dan dokumen, bukan hanya `import`.
2. **Mengira kompilasi main = kompilasi test.** Gradle berhenti di error pertama; error di source set test baru muncul setelah main bersih. Selalu jalankan `compileTestKotlin`.
3. **Pagar yang tak pernah dilihat gagal.** Tes "tidak ada pelanggaran" lulus walau pemindainya rusak. *Solusi*: fixture pelanggar + mutasi nyata (sisipkan satu referensi liar, lihat tes merah, pulihkan).
4. **Melonggarkan `internal`.** `moduleDecision`/`requireModuleAccess` bersifat `internal` dan tetap terjangkau dari paket lain di modul Gradle yang sama; tidak perlu dijadikan `public`.
5. **Menganggap pagar = keamanan penuh.** Ia hanya menangkap sebutan nama paket di sumber, bukan refleksi atau nama yang dirakit dari string.

---

## 🧪 6. Bagaimana Membuktikan Ini Bekerja?

- `TenantCodeBoundaryTest` (3 tes) — termasuk fixture pelanggar dan fixture sah.
- **Mutasi nyata**: menambahkan satu baris yang menyebut `…pack.tenant.layanan.LayananPilotPack` ke `RouteOwnership.kt` membuat tes gagal (1 dari 3); setelah file dipulihkan, tes hijau.
- Paritas perilaku: `RouteOwnershipTest`, `ModuleSchemaOwnershipTest`, tes gerbang `layanan`, `PilotTenantSeed*`, `BuilderDataPackDraftTest` tetap hijau; `:core:jvmTest` 1617, `:app:shared:jvmTest` 268, tes server terarah 38 — semua 0 gagal; `audit-variability.sh` 0 temuan.

---

## 🏆 7. Tantangan Mandiri

- [ ] Tambahkan modul tenant kedua (mis. `klinik_antrean`) **hanya** dengan menambah satu entri di `TenantPackContributions`. Apakah ada file mesin yang perlu disentuh? (Seharusnya tidak.)
- [ ] Tulis fixture yang menyebut J3 lewat `Class.forName("…")`. Apakah pagar menangkapnya? Apa artinya bagi batas pagar ini?
- [ ] Perluas pagar agar juga memindai `src/jvmMain`/`src/androidMain` — apa yang berubah?
