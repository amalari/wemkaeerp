# Discovery Note — Modul Pilot "Permintaan Perubahan" (`layanan_change_request`)

**Tanggal**: 2026-10-04 · **Penulis**: Agent C (Claude Sonnet 5.5) · **Jalur**: [PLAN-proto-C](parallel/PLAN-proto-C-handoff-pilot.md) butir C0
**Status keputusan pilot**: *asumsi* — pilot "Permintaan Perubahan" dipilih sesuai usulan plan; koordinator belum mengonfirmasi. Mudah diganti: seluruh pilot hidup di satu pack data + keluaran generator.

## 1. Kebutuhan
- **Siapa memakai**: tim software house sendiri (PM/sales/developer) dan admin pabrik klien yang ingin memantau permintaan customisasi mereka. Peran: pembuat permintaan (OPERATE), peninjau/penyetuju (MANAGE), pembaca (VIEW).
- **Data milik**: tenant (satu daftar bersama per tenant, bukan per individu).
- **Berubah kapan**: per permintaan (puluhan per bulan); status berubah sesuai alur Baru → Ditinjau → Disetujui → Selesai.

## 2. Fitur serupa
- Perintah: `scripts/find-similar-feature.sh change request permintaan perubahan`
- Temuan: `ModuleCustomizationRequest` / `SubmitCustomizationRequestUseCase` + `/api/tenant/customization-requests` (permintaan kustomisasi **modul** ke tim platform — platform-level, bukan tracker generik tenant); `BuildRequest` (antrean build builder). Tidak ada tracker perubahan generik per tenant.
- Keputusan: **Baru**. Pola ditiru: `PostgresVendorRepository` + `VendorsTable` (Exposed, `DatabaseFactory.dbQuery(tenantId)`), `VendorRoutes` (`authorized(required)` + `moduleDecision` + `requireModuleAccess`), migrasi **V76/V77/V64**.

## 3. Jenis
**Modul OPERASIONAL pada pack data `layanan`** (bukan modul garment, bukan enum). Alasan: `BusinessModule` kini hanya alias `ModuleId` (B6d selesai), jadi modul pack data memakai RBAC/entitlement yang sama dengan modul bawaan. Tahap/proses/stasiun tidak relevan. Fitur "dalam modul" tidak berlaku karena pilot ini sendiri modul.
Jalur B aturan #5: bukan fitur konveksi — pack netral, tidak melanggar.

## 4. Uji Variabilitas
| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---|---|---|---|---|
| Status & alur (Baru→Ditinjau→Disetujui→Selesai) | ya | ya | ya | **Data** (`EntitySpec` + `StateMachine` di pack) | template pack; salinan tenant; dokumen mengikuti spec saat diterapkan |
| Prioritas, field tambahan | ya | ya | ya | **Data** | idem |
| Tipe field (TEXT/NUMBER/DATE/ENUM/BOOL) | tidak | tidak | tidak | **Kode** (kosakata sistem; alasan di KDoc `FieldType`) | — |
| Tingkat akses per operasi | ya (jabatan) | — | ya | Data RBAC (`custom_roles`) | backfill preset peran |

## 5. Core & extend
- **Core**: `core/.../domain/prototype/EntitySpec`/`PrototypeSpec` (sumber spec) → `core/.../domain/discovery/handoff/*` (generator baru). Pack: `core/.../domain/pack/LayananPilotPack.kt` (data).
- **Titik extend**: `HandoffScaffoldGenerator.generateFromSpec`; `DomainPackRegistry.register` (pack data, prefiks `layanan_`); `ModuleSchemaMap.byModule`; `RouteOwnership.moduleRoutes`; `DomainRouteWiring.registerIn`.
- **Contoh ditiru**: `PostgresVendorRepository.kt` (query/RLS), `VendorRoutes.kt:77-87` (gerbang), `V76`/`V64`/`V77` (schema+grant+RLS+katalog).
- **Jangan disentuh**: `DiscoveryRoutes.kt` (410 baris), `Application.kt` (di atas hard limit), `PrototypeSpec`/`EntitySpec` (milik B).

## 6. I/O & kanvas
- **Port masuk**: `Permintaan` (dari luar sistem — input klien). **Port keluar**: `Catatan`. Keduanya sudah di `portTypes` pack.
- **Kanvas**: modul operasional level 1 (node) pada pack `layanan` — hanya muncul di tenant yang packnya memuat modul ini; tidak mengubah kanvas garment.
- **Telemetri**: belum ada; tampil estimasi (WIP = jumlah permintaan belum Selesai) — di luar lingkup pilot.

## 7. Governance
| Operasi | Level minimum | Peran yang ditolak (dites 403) |
|---|---|---|
| Daftar / lihat | VIEW | pengguna tanpa akses modul (NONE) |
| Tambah, ubah, pindah status | OPERATE | VIEW |
| Hapus | MANAGE | VIEW, OPERATE |
- **Gate**: modul sendiri (`layanan_change_request`) + **modul harus ada di pack tenant** (kalau tidak: 404, bukan 403 — tidak membocorkan keberadaan).
- **ScopeCapability**: `GLOBAL_ONLY` (daftar bersama pabrik; hanya opsi Semua Data).
- **Entitlement**: modul baru, `granted_custom_module_ids` / kuota paket ikut mekanisme modul pack data yang ada (reconciler pipeline tenant otomatis).
- Tulis **fail-closed**: keputusan RBAC tak bisa dihitung = 403 sebelum body dibaca.

## 8. Ukuran → TRD?
- Agregat baru: 1 entitas pilot; **migrasi**: V90 (schema `layanan_change_request`, 1 tabel, RLS, grant, katalog); **TRD tidak diperlukan** — ini pembuktian generator yang sudah tercakup plan induk (§8 Jalur C); keputusan arsitektur dicatat di sini dan di dokumen C5.
- **Temuan arsitektur** yang memengaruhi desain generator: (1) route tenant wajib di bawah `/api/tenant/…` agar tercakup `RouteGateTest`/`RouteOwnershipTest`; (2) gerbang wajib memeriksa keanggotaan modul pada pack tenant; (3) `ModuleSchemaOwnershipTest` mewajibkan tabel baru terdaftar di `ModuleSchemaMap`.
