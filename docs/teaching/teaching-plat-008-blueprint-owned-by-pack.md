# Modul Pembelajaran: Blueprint Non-Garment sebagai Data Milik Pack (TRD-PLAT-008)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Kepemilikan data oleh Domain Pack, resolusi kode ke objek (lookup dua katalog), fail-closed saat tulis, isolasi baris rusak saat baca, migrasi pelebaran kolom
> **Prasyarat**: Tahu bahwa `Tenant` menyimpan `businessPreset` (sebuah `Blueprint`), bahwa `DomainPack` adalah paket kosakata industri, dan dasar repository Exposed/Postgres
> **Referensi Task**: `docs/trd/TRD-PLAT-008-blueprint-owned-by-pack.md` (B4 lanjutan). Komit: `bba4957f` (fitur), `6c96e543` (merge), `9abbd510` (TRD disetujui)

---

## 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: handoff dari hasil wawancara (misalnya pack `klinik`) membuat tenant dengan `businessPreset = klinik_starter`. Tetapi blueprint `klinik_starter` hanya hidup di draf (`ops.discovery_drafts`). Kolom `tenants.business_preset` hanya menyimpan **kodenya**, dan saat tenant dibaca ulang, kode itu di-parse lewat `GarmentBlueprints.parse(...)` yang hanya mengenal tiga starter garment dan **melempar** untuk yang lain. Akibatnya: tenant non-garment tersimpan, tetapi tidak pernah bisa dimuat lagi (500), dan lebih buruk lagi `findAll()` untuk daftar tenant platform gagal total karena satu baris itu.
- **Analogi**: tenant menyimpan nomor resep di buku catatan, tetapi resepnya sendiri hanya ada di dapur restoran lain (draf). Begitu dapur itu tutup, nomor resepnya tidak bisa dibuka. Perbaikannya: resep disimpan di **buku menu milik restoran itu sendiri** (pack), dan catatan tenant tetap hanya nomor.
- **Hasil akhir**: `DomainPack` membawa `blueprints`; handoff menyalin blueprint draf ke pack; repository tenant me-resolve kode lewat pack tenant; kode yang tak bisa di-resolve **ditolak saat tulis** dan **diisolasi saat baca**.

---

## 2. "Start dari Mana?" — Urutan Penulisan

1. **Langkah 0 - Temukan bug lewat pertanyaan "siapa pemilik data ini?"** Bukan lewat gejala 500. Pemiliknya seharusnya pack (Kontrak 5 `tenant-variability-rules.md`: pack terkunci membeku, blueprint ikut versi pack).
2. **Langkah 1 - Domain murni dulu**: field `DomainPack.blueprints`, invarian, dan fungsi `resolveBlueprint(pack, code)` di `core`. Belum ada database.
3. **Langkah 2 - Codec**: satu parser Blueprint (`BlueprintCodec`) yang dipakai draf **dan** pack, kunci `blueprints` aditif agar pack lama byte-identik.
4. **Langkah 3 - Handoff**: salin blueprint draf ke pack sebelum pack disimpan/dikunci.
5. **Langkah 4 - Persistensi**: repository tenant me-resolve lewat `domain_packs`; tulis fail-closed; baca mengisolasi baris rusak.
6. **Langkah 5 - Migrasi** pelebaran kolom (V97).
7. **Langkah 6 - Tes**: round-trip Postgres untuk non-garment, paritas garment, penolakan.

Kenapa domain dulu? Aturan resolusi (dua katalog, berurutan, `null` bila tak ketemu) adalah keputusan bisnis yang bisa diuji tanpa DB. Repository hanya menjalankannya.

---

## 3. Bedah Blok Kode

### Blok A: Field dan invarian pack (`core/.../domain/pack/DomainPack.kt`, `BlueprintResolution.kt`)

```kotlin
val blueprints: List<Blueprint> = emptyList()
...
init { requireBlueprintsValid() ... }
```

```kotlin
internal fun DomainPack.requireBlueprintsValid() {
    blueprints.groupingBy { it.code }.eachCount().filterValues { it > 1 }.keys.firstOrNull()
        ?.let { error("Pack ${code.value}: blueprint ganda '${it.value}'") }
    val moduleIds = modules.map { it.id.value }.toSet()
    blueprints.forEach { bp ->
        require(bp.pack == code) { ... }
        bp.modules.firstOrNull { it.moduleCode !in moduleIds }?.let { error(...) }
    }
}
```

**Kenapa begini?**
- Bawaan `emptyList()` membuat semua pack lama sah tanpa perubahan.
- Tiga invarian: kode unik, `bp.pack == code pack`, dan setiap `moduleCode` ada di `modules` pack. Aturannya sama dengan `DiscoveryDraft`, sehingga blueprint yang lolos sebagai draf pasti lolos sebagai milik pack.
- Validasi di `init` (bukan di use case) karena kalimat "pack yang tidak konsisten tidak boleh ada" adalah sifat entity.
- Logika dipisah ke `BlueprintResolution.kt` supaya `DomainPack.kt` tidak membengkak (batas ukuran file core 250/400).

### Blok B: Resolusi kode menjadi Blueprint

```kotlin
fun resolveBlueprint(pack: DomainPack?, code: BlueprintCode): Blueprint? =
    pack?.blueprints?.firstOrNull { it.code == code } ?: GarmentBlueprints.find(code)
```

**Kenapa begini?**
- Dua katalog, berurutan, deterministik: (1) blueprint milik pack tenant, (2) starter platform (`GarmentBlueprints`). Tidak ketemu = `null`; **tidak pernah menebak** (Kontrak 4).
- Katalog kedua ada karena tenant ber-pack data yang dibuat tanpa memilih starter memegang nilai bawaan kolom `fob_full_package`. Tanpa langkah (2), semua tenant itu tak terbaca (K3 di TRD).
- **Konsekuensi yang perlu kamu sadari (dibaca langsung dari kode)**: karena langkah (2) tidak melihat pack, tenant pack mana pun **bisa** memegang kode starter garment (`fob_full_package`, `cmt_makloon`, `brand_d2c`) dan tetap ter-resolve. Inilah satu-satunya jalur "blueprint garment terlihat dari pack lain". Ini sengaja (tes `resolveBlueprint - milik pack dulu, lalu starter platform, tak dikenal null` menegaskannya), tetapi arah sebaliknya **tidak** berlaku: blueprint klinik pada tenant `garment` ditolak (tes `save menolak kode starter yang tak ter-resolve ...`). Kalau kelak starter garment dipindah menjadi data pack garment (opsi yang ditunda di K2), katalog (2) ini harus ikut dikaji ulang.
- `UnresolvableBlueprintException(tenantSlug, packCode, blueprintCode)` membawa tiga fakta agar log bisa langsung ditelusuri.

### Blok C: Handoff menyalin blueprint ke pack (`DiscoveryHandoffUseCases.kt`)

```kotlin
val pack = if (DomainPackRegistry.isShipped(stored.draft.pack.code)) {
    require(resolveBlueprint(stored.draft.pack, blueprint.code) == blueprint) { "... handoff ditolak" }
    stored.draft.pack
} else {
    stored.draft.pack.let { it.copy(blueprints = it.blueprints.filter { b -> b.code != blueprint.code } + blueprint) }
    ...
}
```

**Kenapa begini?**
- Pack **bawaan** (garment) tidak menyimpan blueprint (K2: byte pack garment tidak boleh berubah). Maka blueprint draf wajib sudah ada di katalognya, dan ditolak **sebelum tenant dibuat** supaya tidak ada tenant yang kodenya tak bisa dimuat. Ini perubahan perilaku yang dikonfirmasi pengguna: handoff garment dengan blueprint di luar tiga starter kini ditolak.
- Pack **data** menyerap blueprint draf. `filter { it.code != blueprint.code } + blueprint` membuat operasi idempoten (handoff ulang tidak menggandakan).
- Perbandingan pack lama vs baru yang sudah ada (409 bila beda) kini ikut membandingkan `blueprints` (K7): dua tenant dengan pack sama tetapi starter berbeda butuh versi pack baru.

### Blok D: Repository tenant (`TenantBlueprintResolver.kt`, `PostgresTenantRepository.kt`)

```kotlin
private suspend fun packOf(code: DomainPackCode, pinnedVersion: Int?): DomainPack? =
    loaded.getOrPut(code to pinnedVersion) {
        when {
            DomainPackRegistry.isShipped(code) -> DomainPackRegistry.find(code)
            pinnedVersion != null -> packs.findVersion(code, pinnedVersion)?.pack
            else -> DomainPackRegistry.find(code) ?: packs.findEffective(code)?.pack
        }
    }
```

**Kenapa begini?**
- Registry diisi malas saat tenant pemilik pertama datang, jadi di proses baru `findAll()` belum melihat pack data. Karena itu pack dibaca dari tabel `domain_packs` bila registry kosong (K4), **tanpa** mendaftarkannya ke registry (membaca tidak boleh punya efek samping global).
- Versi yang di-pin tenant dihormati; versi tak dikenal menghasilkan `null`, **tidak** jatuh ke versi lain (tidak ada fallback senyap).
- `getOrPut` per (pack, versi) mencegah N+1 pada `findAll()`.

```kotlin
// PostgresTenantRepository
override suspend fun save(tenant: Tenant) = runCatching {
    TenantBlueprintResolver(packs).require(tenant.slug.value, tenant.domainPack, tenant.domainPackVersion, tenant.businessPreset.code.value)
    ...
}
override suspend fun findAll(): List<Tenant> { ... try { toTenant(row, resolver) } catch (e: UnresolvableBlueprintException) {
    logger.error("Tenant dilewati dari findAll(): {}", e.message) } ... }
```

**Kenapa begini?**
- **Tulis fail-closed** (FR-5): kode yang tak ter-resolve ditolak sebelum menyentuh DB. `InMemoryTenantRepository.save` menerapkan aturan yang sama supaya tes berbasis memori tidak lolos pada data yang akan ditolak di produksi.
- **Baca**: `findById`/`findBySlug` melempar (satu tenant gagal dengan alasan jelas); `findAll()` melewati baris rusak dengan log `ERROR`. Melewati bukan fallback senyap: tenant tidak diubah menjadi tenant lain; ia absen dengan jejak log. Alternatif melempar mengorbankan seluruh daftar tenant platform.

### Blok E: Migrasi V97

```sql
ALTER TABLE tenants ALTER COLUMN business_preset TYPE VARCHAR(64);
```

`BlueprintCode` sah sampai 64 karakter, kolom V10 hanya 50. Tanpa ini, kode 51-64 karakter gagal di DB, bukan di domain. Pelebaran murni, tanpa backfill; bukan modul sehingga tidak ada entitlement/katalog/peran baru.

---

## 4. Teknologi & Pendekatan: The "Why"

| Pendekatan | Alternatif | Kenapa dipilih | Risiko alternatif |
|---|---|---|---|
| Blueprint di `DomainPack.blueprints` (JSON `domain_packs.definition`) | Tabel `blueprints` terpisah; snapshot JSON di `tenants` | Satu sumber kebenaran, ikut versi pack yang terkunci | Dua sumber kebenaran; template tak bisa dipakai tenant lain |
| Tenant tetap hanya menyimpan kode | Salin blueprint penuh ke tenant | Pack membeku, tenant tidak berubah di tengah jalan | Salinan basi |
| `resolveBlueprint` berurutan, `null` bila tak ketemu | Fallback ke `DEFAULT` | Data tidak berubah tanpa jejak | Tenant "berubah" jadi garment diam-diam |
| Pack garment tetap `blueprints = emptyList()` | Pindahkan tiga starter ke pack garment | Byte `GET /api/tenant/pack` garment tak berubah (Strangler Fig) | Paritas pecah |

---

## 5. Jebakan Nyata yang Ditemukan

1. **Bug yang tak ditangkap tes**: `InMemoryTenantRepository` tidak punya langkah parse, dan tak ada tes round-trip Postgres untuk non-garment, jadi 500 hanya muncul di produksi. Pelajaran: fake repository harus menjalankan aturan yang sama dengan yang asli.
2. **Satu baris meracuni daftar**: `findAll()` yang melempar karena satu tenant membuat daftar tenant platform, trial admin, dan `DomainPackRoutes` gagal total. Isolasi per baris adalah keputusan K5.
3. **Starter garment terlihat dari semua pack** (lihat Blok B): fallback ke katalog platform tidak mengecek pack. Disengaja, tetapi mudah salah dikira "kebocoran" atau, sebaliknya, tidak disadari.
4. **Kolom 50 vs kode 64 karakter**: validasi domain dan skema DB tidak selaras; gagalnya di DB.
5. **Registry vs DB**: mengandalkan `DomainPackRegistry` saja membuat `findAll()` pada proses yang baru dinyalakan tidak melihat pack data.
6. **Bukan menyalin ingatan**: nomor migrasi V97 sempat berisiko bentrok dengan sesi lain (dicatat di TRD); cek `ls db/migration` sebelum menomori.

---

## 6. Cara Memverifikasi

- Tes domain: `core/src/commonTest/.../pack/PackBlueprintTest.kt` (round-trip codec, byte-identik tanpa blueprint, invarian, decode rusak berpath, `resolveBlueprint`, draf garment yang menambah blueprint ke pack bawaan ditolak).
- Tes integrasi: `server/src/test/.../infrastructure/PostgresTenantBlueprintIntegrationTest.kt` (round-trip `findById`/`findBySlug`/`findAll` non-garment, paritas tiga starter garment, kode bawaan kolom, 64 karakter, `save` menolak, baris rusak tidak meracuni `findAll`, handoff klinik dimuat ulang). Butuh Postgres; jalankan dengan DB scratch sesuai catatan memori proyek.
- Tes handoff: `DiscoveryHandoffUseCasesTest`.
- Perintah: `./gradlew :core:jvmTest --tests '*PackBlueprintTest*'` dan `./gradlew :server:test --tests '*PostgresTenantBlueprintIntegrationTest*'`. (Dokumen ini tidak menjalankannya; lihat laporan.)

---

## 7. Tantangan Mandiri

- [ ] Rancang tes yang menunjukkan perilaku saat tenant pin ke versi pack yang tidak ada (`null` -> hanya starter platform ter-resolve).
- [ ] Tulis rencana (bukan kode) memindahkan tiga starter garment ke `GarmentDomainPack.blueprints` dengan paritas byte-identik. Apa yang terjadi pada fallback katalog (2)?
- [ ] Tulis skrip perbaikan data untuk tenant lama non-garment yang packnya belum membawa blueprint (petunjuk: `ops.discovery_drafts.document->'blueprint'`, simpan sebagai versi pack baru, kunci, pin tenant).
