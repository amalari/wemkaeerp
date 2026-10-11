# TRD-PLAT-008: Blueprint Non-Garment sebagai Data Milik Pack (B4 lanjutan)

## 1. Konteks dan Administrasi

- **ID**: TRD-PLAT-008 — Blueprint milik pack
- **Status**: **Disetujui dan di-merge** (2026-10-09; keputusan K1–K7 ditinjau pengguna, termasuk K5 — tenant rusak dilewati di `findAll()` dengan log ERROR — dan K7 — handoff ke pack yang sama dengan blueprint berbeda tetap 409)
- **Riwayat**: 0.1 — 2026-10-09 — Claude (riset dari kode); keputusan di §4 diambil atas nama pengguna dan wajib ditinjau.
- **Riwayat**: 0.2 — 2026-10-09 — Pengguna menyetujui dan meminta merge; perubahan perilaku yang dikonfirmasi: handoff garment dengan blueprint di luar tiga starter ditolak; `BlueprintCodec` menolak `parameters` non-string (dulu dibuang diam-diam). Catatan integrasi: migrasi V97 bisa bentrok dengan sesi lain yang menambah V97 — nomori ulang saat integrasi.
- **Rujukan**: `PLAN-dual-track-garment-and-general-platform.md` baris B4 ("preset = Blueprint milik pack"),
  `discovery-B4-blueprint.md`, TRD-PLAT-001-blueprint, TRD-PLAT-004/005 (kepemilikan pack),
  `tenant-variability-rules.md` Kontrak 4/5/6/7/8, `module-integration-rules.md` §5.1/§5.6.

### Masalah (diverifikasi dari kode)

1. `HandoffDiscoveryDraftUseCase` menyalin `draft.blueprint` ke `tenant.businessPreset`, tetapi `Blueprint` non-garment
   (mis. `klinik_starter`) hidup **hanya** di `ops.discovery_drafts`; `DomainPack` tidak membawanya.
2. `PostgresTenantRepository.save` hanya menulis `business_preset = code`; `toTenant` membaca lewat
   `GarmentBlueprints.parse(...)` yang **melempar** untuk selain tiga starter garment. Akibat nyata: tenant hasil
   handoff non-garment tak bisa dimuat (500 "Blueprint tidak dikenal"), dan `findAll()` (daftar tenant platform,
   trial admin, `DomainPackRoutes`) gagal total karena **satu** baris.
3. Tes tak menangkapnya: `InMemoryTenantRepository` tak punya langkah parse, dan tak ada tes round-trip Postgres
   untuk preset non-garment.
4. Kolom `tenants.business_preset` adalah `VARCHAR(50)`, sedangkan `BlueprintCode` sah sampai 64 karakter
   (insert kode 51–64 karakter gagal di DB, bukan di domain).

## 2. Persyaratan

- **FR-1** `DomainPack` membawa `blueprints: List<Blueprint>` (bawaan kosong). Invarian: kode unik; tiap blueprint
  `pack == code pack`; tiap `moduleCode` ada di `modules` pack (sama dengan invarian `DiscoveryDraft`).
- **FR-2** `DomainPackCodec` menulis/membaca kunci `blueprints` **hanya bila tidak kosong**; nilai rusak ditolak
  berpath (Kontrak 4). Pack tanpa blueprint ter-encode **byte-identik** dengan sebelumnya.
- **FR-3** Tenant tetap menyimpan **hanya kode** (`business_preset`). Resolusi kode → `Blueprint` = fungsi domain
  murni `resolveBlueprint(pack, code)`: (1) blueprint milik pack tenant; (2) starter platform (`GarmentBlueprints`);
  tak ketemu = `null` (tak pernah menebak).
- **FR-4** Handoff non-bawaan menyalin `draft.blueprint` ke `pack.blueprints` **sebelum** pack disimpan/dikunci;
  handoff pack bawaan dengan blueprint di luar katalog pack itu **ditolak sebelum tenant dibuat**.
- **FR-5** Penulisan tenant **fail-closed**: `save` menolak (`Result.failure`) bila kode `businessPreset` tak
  ter-resolve oleh aturan FR-3 terhadap pack tenant (baik Postgres maupun in-memory).
- **FR-6** Pembacaan tak boleh diracuni satu baris: `findAll()` tidak melempar; `findById`/`findBySlug` untuk baris
  yang tak ter-resolve melempar `UnresolvableBlueprintException` berpesan jelas (tenant itu saja).
- **NFR-1** Tenant garment berperilaku identik (tes paritas). **NFR-2** Domain murni di `core`, tanpa `!!`, tanpa
  `else` baru. **NFR-3** Batas ukuran file (core 250/400, server 300/500).

## 3. Titik Pendaftaran (grep segar 2026-10-09)

`grep -rn businessPreset|GarmentBlueprints.parse|findByCode` pada `core/commonMain`, `server/src/main`, `app/shared`:

| Lapisan | Titik | Tindakan |
|---|---|---|
| Domain pack | `DomainPack.kt` (+`blueprints`), `ShippedPackIdentity.kt` | field baru; pack bawaan tetap kosong sehingga draf yang menambahkan blueprint ke pack bawaan **ditolak** sebagai penulisan ulang |
| Resolusi | berkas baru `domain/pack/BlueprintResolution.kt` | `resolveBlueprint`, `UnresolvableBlueprintException` |
| Codec | `DomainPackCodec.kt`, berkas baru `BlueprintCodec.kt` (diekstrak dari `DiscoveryDraftCodec`) | satu parser Blueprint untuk draf dan pack |
| Handoff | `DiscoveryHandoffUseCases.kt` | salin blueprint ke pack; tolak sebelum tenant dibuat |
| Persistensi | `PostgresTenantRepository.kt`, `InMemoryTenantRepository.kt` | resolusi via pack (DB), validasi tulis, isolasi baca |
| Migrasi | `V97__tenants_business_preset_width.sql` | lebarkan kolom ke 64 |
| Tidak disentuh | `GarmentDomainPack.pack`, `GarmentBlueprints.parse` (tetap untuk jalur admin garment), endpoint HTTP | tak ada endpoint ditambah/diubah → tes 403 tak relevan |

## 4. Keputusan dan Opsi yang Ditolak

**K1 — Di mana blueprint non-garment disimpan: di `DomainPack.blueprints` (kolom JSON `domain_packs.definition`).**
Sesuai judul B4 ("milik pack") dan Kontrak 5 (pack terkunci membeku: blueprint ikut versi pack, tenant tak berubah
di tengah jalan). Ditolak: (a) tabel `blueprints` terpisah — dua sumber kebenaran untuk satu versi pack, migrasi
tambahan, tanpa manfaat; (b) kolom JSON snapshot di `tenants` — menyalin template ke tenant sah menurut Kontrak 5
tetapi membalik arah B4 dan membuat blueprint tak bisa dipakai tenant lain yang memasang pack yang sama;
(c) tetap di draf — status quo, akar masalah.

**K2 — Pack bawaan garment tetap `blueprints = emptyList()`; starter garment tetap dari `GarmentBlueprints`.**
Mengisinya akan mengubah byte `DomainPackCodec`/`GET /api/tenant/pack` untuk garment dan membuat setiap draf
garment tersimpan tak lagi "identik" dengan yang dikirim (`ShippedPackIdentity`). Strangler Fig: tidak ada yang
berubah untuk garment; blueprint pack data tinggal di pack-nya. Ditolak: memindahkan tiga starter ke pack garment
sekarang (bisa dilakukan kelak sebagai tahap sendiri, dengan paritas byte-identik yang diuji lebih dulu).

**K3 — Aturan resolusi dua langkah: pack tenant, lalu starter platform.** Alasan: tenant ber-pack data
(mis. pilot `layanan`) yang dibuat tanpa pilihan memegang `business_preset = fob_full_package` (nilai bawaan kolom);
resolusi "hanya lewat pack" akan membuat semuanya tak terbaca. Lookup ini **berurutan dan deterministik** pada dua
katalog yang dinyatakan, bukan tebakan: kode tak ada di keduanya → `null` → ditolak (Kontrak 4). Ditolak: fallback
ke `DEFAULT` untuk kode tak dikenal (data berubah tanpa jejak).

**K4 — Pembacaan di Postgres lewat tabel `domain_packs` (bukan `DomainPackRegistry`).** Registry diisi malas saat
tenant pemilik pertama datang (`ResolveDomainPackUseCase`), jadi `findAll()` pada proses baru belum melihat pack
data. Repository menerima `DomainPackRepository` (bawaan `PostgresDomainPackRepository()` sehingga seluruh pemanggil
`PostgresTenantRepository()` tak berubah), memakai registry hanya sebagai cache cepat, dan memuat versi yang
di-pin tenant (`domainPackVersion`) atau effective. Resolusi dilakukan **di luar** transaksi baca baris dan
dikelompokkan per (pack, versi) agar `findAll()` tidak N+1.

**K5 — Perilaku kode tak ter-resolve: tolak saat tulis (FR-5) + isolasi baris saat baca (FR-6).**
- Tulis: menolak adalah satu-satunya pencegahan; setelah itu keadaan "tak ter-resolve" hanya bisa lahir dari
  korupsi/penghapusan pack di luar aplikasi.
- Baca `findAll()`: baris yang tak ter-resolve **dilewati dan dicatat `ERROR`** (bukan menebak blueprint, bukan
  melempar). Ini bukan fallback senyap: tenant itu tidak diubah menjadi tenant lain, ia absen dari daftar dengan
  log yang bisa ditelusuri, sedangkan alternatif (melempar) mengorbankan seluruh daftar tenant platform.
- Baca `findById`/`findBySlug`: **melempar** `UnresolvableBlueprintException` (pesan memuat slug, pack, kode) — satu
  tenant itu gagal dengan alasan jelas, tenant lain tak terpengaruh.
- Ditolak: mengembalikan tenant dengan blueprint "placeholder" (mengubah tipe `Tenant`, banyak pemanggil, dan
  memalsukan data); menambah status tenant "RUSAK" (skema baru tanpa kebutuhan terbukti).

**K6 — Perbedaan isi blueprint tak diperiksa saat tulis, hanya keterselesaian kode.** Sumber kebenaran isi adalah
pack; baca selalu mengembalikan versi pack. Memeriksa kesetaraan penuh membuat tulis rapuh terhadap revisi pack.

**K7 — Handoff dengan pack yang sudah ada dan blueprint berbeda tetap 409.** Pemeriksaan yang ada (`effective.pack
!= pack`) kini ikut membandingkan `blueprints` (draf pack digabung blueprint lebih dulu). Dua tenant dengan pack
sama tetapi starter berbeda butuh **versi pack baru** (review manual) — konsisten dengan "versi terkunci tak
berubah". Dicatat sebagai batasan, bukan diselesaikan di sini.

## 5. Kompatibilitas Mundur / Strangler Fig / Data Lama

- **Garment**: nol perubahan perilaku. Tes paritas: tiga blueprint round-trip Postgres, `DomainPackCodec`
  byte-identik untuk pack bawaan dan pack data tanpa blueprint, `ShippedPackIdentity` menolak draf yang menambah
  blueprint ke pack bawaan.
- **Tenant hasil handoff lama** dengan `business_preset` non-garment: pada DB dev tidak ada (handoff non-garment
  tak pernah berhasil dimuat). Bila ada di lingkungan lain, packnya **belum membawa blueprint**: baca akan
  melempar/melewati (K5) dan **perlu perbaikan data sekali jalan** — simpan ulang pack versi baru berisi blueprint
  dari draf (`ops.discovery_drafts.document->'blueprint'`) lalu kunci. Tidak ada migrasi otomatis karena blueprint
  tak bisa diturunkan dari kode saja.
- **Migrasi V97** (`ALTER TABLE tenants ALTER COLUMN business_preset TYPE VARCHAR(64)`): pelebaran murni,
  idempoten secara perilaku, tanpa backfill; tak ada entitlement/katalog/peran/schema modul baru (bukan modul).
  Nomor berikutnya setelah V96; dicek tak bentrok dengan worktree `.kilo/worktrees/track-*` (semuanya di V96).

## 6. Risiko

| Risiko | Mitigasi |
|---|---|
| Sesi lain menambah V97 | Nomor dicek 2026-10-09; integrator wajib menomori ulang bila bentrok |
| Registry basi vs DB | Repository memakai DB bila registry tak punya pack; versi pin dihormati |
| `DomainPack.kt` membesar | Hanya field + invarian pendek; logika resolusi di berkas sendiri |
| Blueprint non-garment belum teruji di mesin kanvas/katalog modul | Di luar cakupan; dicatat di laporan sebagai tak terverifikasi |
