# Discovery Note — Aktivasi Tenant Pilot (`layanan`) untuk Port Data (Jalur C, butir C0)

**Tanggal**: 2026-10-04 · **Penulis**: Agent C (Claude Sonnet 5.5) · **Rencana**: [PLAN-prototype-data-port-rich-blocks](PLAN-prototype-data-port-rich-blocks.md) / `parallel2/PLAN-dp-C-api-pilot.md`
**Status**: temuan selesai; **butuh satu keputusan koordinator** (§4) sebelum C4 (aktivasi dev) bisa dikerjakan.

## 1. Pertanyaan
Bagaimana pack **data** `layanan` (modul `layanan_change_request`) menjadi dikenal runtime sebuah tenant, dan bagaimana halaman `/builder/prototype` tenant itu mendapat layar kanban pilot yang terikat ke API?

## 2. Temuan (terbaca dari kode dan dibuktikan dengan tes)

### 2.1 Pack data dimuat malas dan **tidak** perlu mengubah daftar pack bawaan
- `ResolveDomainPackUseCase` (dipakai `TenantResolutionPlugin` pada tiap request tenant): jawab dari `DomainPackRegistry` bila sudah ada, kalau belum **muat dari tabel `domain_packs`** (`DomainPackRepository.findEffective`) lalu `DomainPackRegistry.register` — sekali, saat tenant pemiliknya pertama datang.
- `findEffective` = versi `LOCKED` tertinggi, atau `DRAFT` tertinggi bila belum ada yang terkunci.
- `register` menolak pack yang melanggar identitas global (`violations`): kode `layanan` tidak boleh milik pack bawaan; modul/slot baru wajib berprefiks `layanan_` — **sudah terpenuhi** pilot.
- Kesimpulan: aktivasi pack = **satu baris di `domain_packs`** (JSON `DomainPackCodec`) + tenant dengan `domain_pack = 'layanan'`. Tidak perlu menambah ke `DomainPackRegistry.shipped` → **opsi (b) di rencana tidak diperlukan**.
- Akibat baik untuk gerbang route pilot: `moduleDecision` membutuhkan `DomainPackRegistry.moduleDefinition(modul)`; karena plugin tenant sudah memuat pack sebelum handler, route pilot tidak mengalami "modul tak dikenal" untuk tenant `layanan`.

### 2.2 **Penghalang**: draf kerja tenant tidak bisa dibangun/diambil untuk pack tanpa blueprint bawaan garment
`EnsureTenantWorkingDraftUseCase.invoke` (dipanggil `GET /api/builder/draft`, sumber halaman `/builder/prototype`):
```kotlin
val pack = DomainPackRegistry.find(domainPack) ?: return null
val blueprint = GarmentBlueprints.all.firstOrNull { it.pack == pack.code } ?: return null   // ← di sini
...
drafts.findByTenant(tenantId)?.let { existing -> ... return existing }                      // ← baru sesudahnya
```
Pack `layanan` tidak punya blueprint di `GarmentBlueprints.all`, sehingga fungsi mengembalikan **`null` sebelum** sempat mengembalikan draf yang **sudah tersimpan** untuk tenant itu.

**Bukti** (tes sementara terhadap repository memori; dihapus setelah dijalankan): pack `layanan` terdaftar, draf berlayar tersimpan untuk tenant, `EnsureTenantWorkingDraftUseCase(...)(tenant, layanan, user)` →
```
EVIDENCE existing-draft-for-layanan => NULL (draf tersimpan tidak dikembalikan)
```
Akibatnya halaman prototype menampilkan "Belum ada draf kerja" untuk tenant `layanan`, **walau drafnya sudah disemai**. Menyemai draf lewat skrip tidak cukup; ini keterbatasan kode platform, bukan data.

### 2.3 Hal lain yang diperiksa
- **Login uji**: tombol "Owner wemade-demo" terikat tenant garment. Untuk tenant `layanan` jalur uji yang ada adalah **Superadmin + act-as** (`PlatformActAsRoutes`) pada host tenant; belum diverifikasi dengan mata (itu bagian A/G2).
- **Menyemai lewat API superadmin** (`PUT` pack/draf) membutuhkan JWT superadmin — rapuh untuk skrip. Lebih mudah dan idempoten: **alat semai berbasis repository** (`PostgresDomainPackRepository`, `PostgresTenantRepository`, `PostgresDiscoveryDraftRepository`) yang dijalankan sebagai test terjaga env, pola `LayananChangeRequestApiIntegrationTest`.
- **Pipeline/kanvas Factory Flow** tenant `layanan`: tidak dibutuhkan pilot; `effectiveActiveCodes` jatuh ke modul blueprint bila pipeline kosong.
- **`Tenant.businessPreset`** default `GarmentBlueprints.DEFAULT` (blueprint garment) — untuk tenant `layanan` nilai itu tidak bermakna; tidak dipakai jalur pilot, tetapi perlu diperhatikan agar tidak memicu bootstrap garment.

## 3. Pilihan keputusan
| Opsi | Perubahan | Dampak | Risiko |
|---|---|---|---|
| **1 (disarankan, kecil)**: `EnsureTenantWorkingDraftUseCase` memeriksa **draf yang sudah ada lebih dulu**, baru mencari blueprint | ±5 baris di `core/.../domain/builder/EnsureTenantWorkingDraftUseCase.kt` + tes | pack tanpa blueprint bawaan tetap bisa menampilkan draf yang **disemai/di-Terapkan**; perilaku garment tak berubah (draf berlayar sudah dikembalikan tanpa disentuh) | sangat rendah; file di domain `builder` (di luar kepemilikan C) — butuh persetujuan |
| **2 (lebih umum, lebih besar)**: bila pack tak punya blueprint bawaan, **turunkan blueprint dari modul pack** (semua modul aktif) | fungsi `Blueprint.fromPack(pack)` + dipakai bootstrap | **semua** pack data (hasil agent) langsung punya draf kerja dan layar dari `screenSuggestions` — jalur yang kelak dibutuhkan bisnis non-garment | sedang: menyentuh perilaku bootstrap umum; perlu tes paritas garment |
| 3: menambah blueprint `layanan` ke `GarmentBlueprints.all` | menambah ke registry bernama "garment" | memperkeruh batas pack; menyalahi aturan Jalur B #4 (kode mesin tak boleh menyebut satu industri) | tinggi — **tidak disarankan** |

**Rekomendasi:** Opsi **1 sekarang** (membuka pilot), Opsi **2 sebagai kelanjutan** begitu rencana "kontrak usulan layar" jalan (agent menghasilkan pack data yang butuh draf kerja).

## 4. Keputusan yang dibutuhkan dari koordinator
> Setujui **Opsi 1** (perubahan kecil di `EnsureTenantWorkingDraftUseCase`)? Tanpa ini, C4 (aktivasi dev) dan verifikasi G3 skenario 3 **terblokir**: tenant `layanan` tidak akan pernah menampilkan layar pilot di `/builder/prototype`.

Agent C **tidak** mengubah file itu sendiri (di luar kepemilikan jalur C pada rencana).

## 5. Langkah reproduksi aktivasi (setelah keputusan §4; C4)
1. Buat database uji bernama berisi `scratch` (`docker exec wemade-postgres psql -U postgres -c "CREATE DATABASE wemake_dp_c_scratch"`).
2. `DB_NAME=wemake_dp_c_scratch ./gradlew :server:test --tests '*PilotTenantSeedTool*'` (alat semai, idempoten, menolak DB tanpa `scratch`):
   - simpan pack `layanan` ke `domain_packs` sebagai `LOCKED` (`SaveDomainPackDraftUseCase` + `LockDomainPackUseCase`);
   - buat tenant `layanan-demo` (`domain_pack = 'layanan'`, status `ACTIVE`);
   - simpan draf kerja `draft-<tenantId>` (pack + blueprint + layar `default-layanan_change_request`, `binding = Api(...)`) — memakai tipe dari kontrak B.
3. Jalankan server dengan `DB_NAME=wemake_dp_c_scratch`; masuk sebagai Superadmin lalu act-as tenant `layanan-demo`.

## 6. Risiko
| Risiko | Mitigasi |
|---|---|
| Opsi 1 menyembunyikan masalah umum (pack data lain tak punya draf) | Opsi 2 sebagai tindak lanjut; dicatat |
| Alat semai menyentuh DB dev | menolak berjalan bila `DB_NAME` tak berisi `scratch`; tes mutasi |
| Draf semai usang bila pack berubah | `EnsureTenantWorkingDraftUseCase` tidak menyentuh draf berlayar; semai ulang idempoten (ganti draf) |
| `Tenant.businessPreset` garment pada tenant `layanan` | tak dipakai pilot; dicatat untuk Opsi 2 |
| Tergantung kontrak B (`DataBinding`, `ScreenSuggestion.dataBinding`) | C1–C4 menunggu B0 |

## 7. Status butir C
| Butir | Status |
|---|---|
| C0 Discovery | **selesai** (dokumen ini; menunggu keputusan §4) |
| C1 `ApiBlockDataPort` | menunggu B0 (`BlockDataPort`) |
| C2 pack pilot kanban kaya | menunggu B0 (`CardElement`, `ColumnMeta`, `DataBinding`) |
| C3 JSON draf `binding` | menunggu B0 |
| C4 aktivasi dev | menunggu B0 **dan** keputusan §4 |
| C5 penguatan server | **selesai** (hak per verb, atomik multi-field, urutan stabil) |
| C6 verifikasi G3 | menunggu semua di atas + A |
