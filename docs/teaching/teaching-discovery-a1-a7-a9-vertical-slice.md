# Teaching — Discovery Fase A: narasi → draf pack + blueprint (A1–A7, A9)

> Plan: [`docs/plannings/PLAN-discovery-blueprint-prototype-studio.md`](../plannings/PLAN-discovery-blueprint-prototype-studio.md) §2 · Status: A0 (config), A1–A7 + A9 selesai (A8 Koog menyusul di branch terpisah) · 2026-09-30

## Apa yang dibangun

Irisan vertikal pertama funnel discovery (Jalur B): calon klien **ber-login**, mengirim narasi bisnisnya,
dan mendapat **draf yang membungkus kontrak yang sudah ada** — `DomainPack` (B7) + `Blueprint` (B4) +
deskriptor layar (Fase C, masih kosong). Tidak ada DSL baru (keputusan T1).

```
POST /api/discovery/drafts  {"narrative": "...", "industryHint"?}
  → DiscoveryAgent.draft()            (interface domain; implementasi di server)
  → DiscoveryDraftValidator           (galat berpath; draf gagal tidak pernah tersimpan)
  → ops.discovery_drafts (V78)        status DRAFT, pemilik = caller
GET  /api/discovery/drafts            milik pemanggil (superadmin: semua, via act-as)
GET  /api/discovery/drafts/{id}       pemilik atau superadmin, selain itu 403
PUT  /api/discovery/drafts/{id}       dokumen DiscoveryDraftCodec; hanya DRAFT; LOCKED → 409
POST /api/discovery/drafts/{id}/lock  beku selamanya (Kontrak 5)
```

## Keputusan penting & alasannya

1. **Draf = bungkus, bukan DSL** (T1): `DiscoveryDraft(pack, blueprint, screens)`. Invarian lintas-bagian
   (blueprint hanya menyebut modul pack-nya) ditegakkan di `DiscoveryDraft.init` *dan* dilaporkan
   `DiscoveryDraftValidator` dengan path — dua lapis, karena AI output tidak tepercaya.
2. **Validator tidak menduplikasi aturan identitas**: ia memanggil `DomainPackRegistry.violations()` lalu
   hanya *menerjemahkan pesan ke path* (`$.pack.modules[i].id`). Satu sumber kebenaran aturan, satu penerjemah lokasi.
3. **Pack bawaan tak bisa ditulis ulang**: dokumen berkode `garment` sah hanya bila identik dengan pack
   yang dikirim platform; kalau berbeda → galat `$.pack`. Narasi konveksi pun memakai pack bawaan apa adanya.
4. **Agent deterministik = fallback + baseline evals** (A3/A9, D4): `DeterministicDiscoveryAgent` memetakan
   kata kunci → (a) starter garment FOB/CMT/D2C, atau (b) pack baru berprefiks `<kode>_` (modul & slot
   selalu berprefiks → tak bisa merebut id platform, risiko plan §7). `DiscoveryAgents.fromEnv()` adalah
   kill-switch: Koog (A8) tinggal mengganti satu cabang `when`.
5. **Kepemilikan dua lapis** (T12): route memeriksa pemilik (→ 403), use case memeriksa lagi
   (`NotOwnerException`) supaya pemanggil non-HTTP tidak bisa melewatinya. LOCKED ditolak dengan
   `LockedException` → 409.
6. **V78 di schema `ops`** tanpa grant `wemade_app` (pola V16): data prospek & harga tidak pernah
   terlihat koneksi tenant-scoped. `owner_user_id` FK `users` wajib (T12).

## Pengecekan visual/mode tidak berlaku di fase ini

Fase A belum menyentuh UI — layar wizard adalah Fase D. Uji lengkap lewat test API (401/403/409/201).

## A7 — pratinjau tanpa kode (tanpa menyentuh registry LOCKED)

```
POST   /api/discovery/drafts/{id}/preview?ttlMinutes=60  → {sandboxSlug, packCode, expiresAt}
DELETE /api/discovery/drafts/{id}/preview                → akhiri sesi lebih awal
```

- `DiscoveryPreviewRegistry` (core): ledger sesi dalam memori, **terkait waktu** (TTL default 120 menit,
  purge setiap kali ledger disentuh) dan **satu sesi per kode pack**. `start` memakai
  `DomainPackRegistry.register` — itu menulis peta *loaded* (pack data B7), **bukan** daftar `shipped`;
  test membuktikan `GarmentDomainPack.pack` identik sebelum/sesudah sesi.
- `StartDiscoveryPreviewUseCase` membuat tenant sandbox `sandbox-<kode>` (slug ≤30, idempoten — pakai
  ulang tenant yang sama; slug yang dipakai pack lain → ditolak). Klien tidak perlu perubahan apa pun:
  menu & `/m` sudah membaca `GET /api/tenant/pack` (jalur data B7).
- Setelah sesi berakhir/kedaluwarsa, pack dilepas → tenant sandbox ditolak **fail-closed 409** oleh
  mekanisme B7 FR-4 ("vertikal tidak dikenal"), tidak pernah jatuh ke garment. Dites di `DiscoveryApiTest`.

## A9 — evals narasi emas

`server/src/test/.../DiscoveryEvalsTest.kt`: 4 narasi emas (klinik, bengkel, katering, garment CMT) digrade
dengan grader yang sama dengan produksi — `DiscoveryDraftValidator` + cakupan modul/kode blueprint yang
diharapkan. Format log `evals | <kasus> | PASS|FAIL | …` dipakai juga agent Koog nanti, jadi regresi
prompt/model ketahuan sebelum ganti model. Baseline deterministik: **4/4 PASS**.

## Pelajaran saat implementasi

- **Ktor: handler di root vs child route.** Kebingungan awal (404 kosong) terjadi karena pembungkus
  `route("/api/discovery/drafts") { ... }` hilang saat penyuntingan, sehingga `post {}` terdaftar di root.
  Diagnosisnya berlapis: `println` di fungsi muncul, di handler tidak → masalah *registrasi*, bukan *matching*.
- **Superadmin tanpa `X-Tenant-Slug` selalu 404** untuk path non-`/api/admin`: `TenantResolutionPlugin`
  menuntut konteks tenant untuk semua path non-publik. Rute platform per-user seperti ini tetap
  memakai act-as (`asSuperadminActingAs`) — jangan tambahkan bypass baru.

## Utang & langkah berikutnya

- A8 (KoogDiscoveryAgent + loop koreksi diri) — branch terpisah karena dependensi `ai.koog:koog-agents`
  vs `kotlinx-datetime` 0.6.2; kill-switch & interface sudah siap.
- Fase B (estimasi → lock → handoff), C (renderer/Studio), D (wizard/PDF), E (operasi produk).

## Bukti verifikasi

- Kompilasi 5 target hijau (`core` Jvm/Js/WasmJs, `app:shared` Jvm/Js/WasmJs, `server` main+test).
- `:core:jvmTest --rerun-tasks`: **989 test, 0 gagal** (termasuk 20 test discovery: draf, validator,
  use case, registry pratinjau — dengan fixture non-garment, Kontrak 6).
- `:server:test`: `DiscoveryApiTest` (3, termasuk siklus pratinjau end-to-end), `DiscoveryEvalsTest`
  (4/4 PASS), `DomainPackApiTest` (2), `ProspectApiTest` (13) — semua hijau.
- `audit-variability.sh`: 2 temuan akhir (route mutasi `preview`) — sudah fail-closed: login wajib,
  gerbang pemilik (test 403), TTL dibatasi 1..480 menit.
- `Application.kt` **697 baris** ≤ 698 (Aturan Ratchet §14; blok `prospectRoutes`/`moduleDevRoutes`
  diringkas sebagai kompensasi).

---

# Fase B: estimasi draf, funnel, dan handoff otomatis (B1–B3)

Sambungan vertikal dari Fase A: draf yang terbukti sah kini bisa **dihargai, dititipkan ke funnel,
dan dijadikan tenant** — masih tanpa scaffold kode.

## B1 — `PriceDiscoveryDraftUseCase` (estimasi dari draf)

- Draf **tidak dihargai dengan rumus kedua**: modulnya disintesis menjadi `CoverageAnalysis`
  (`CoveredByCatalog` bila moduleId ada di katalog billable, `Gap` bila tidak), lalu didelegasikan
  ke `PriceProspectFlowUseCase` yang sudah ada. Satu sumber rumus harga; sifat "estimasi berubah
  bila modul/layar berubah" diwarisi dari `BuildFeatureVector.screenCount` (layar kustom draf).
- **Archetype gap = slot pack-nya sendiri** (mis. `klinik_antrean`), bukan `CUSTOM_EXTENSION` —
  produktivitas historis garment tidak dipinjam untuk modul klinik (filter archetype pada
  `ModuleBuildRepository` memang disengaja). Konsekuensi jujurnya: vertikal baru tanpa riwayat
  build → rentang **ditahan** (`isPublishable == false`), bukan dikarang.
- Vektor minimum konservatif untuk modul generik: 1 entitas, 1 use case, N layar (min. 1),
  1 endpoint, 1 tabel — terdokumentasi di `featuresFor`.
- Endpoint: `GET /api/discovery/drafts/{id}/price?marginPercent=35` (login + gerbang pemilik).

## B2 — CTA "Bangun Sistem Ini" (`SubmitDiscoveryDraftUseCase`)

- Wajib **LOCKED** dulu: yang ditawarkan ke klien harus beku (Kontrak 5). Menautkan `prospectLeadId`
  pada baris terkunci bukan revisi dokumen — kolom `document` tidak berubah.
- Lead dibuat lewat `SubmitProspectLeadUseCase` (invarian narasi tetap satu pintu), langsung
  `markTranslated()` karena penerjemahannya *adalah* draf. Endpoint: `POST /{id}/submit` (409 bila
  belum dikunci).

## B3 — Handoff otomatis (`HandoffDiscoveryDraftUseCase`)

- Urutan: **validasi versi pack dulu → buat tenant → simpan & kunci pack (owner = tenant) →
  tetapkan pack → salin blueprint**. Validasi sebelum pembuatan tenant adalah koreksi dari test:
  versi lama membuat tenant dulu, sehingga 409 meninggalkan tenant yatim.
- Pack bawaan (garment) tidak disimpan ulang — langsung ditetapkan, `packVersion = null`. Pack data
  baru disimpan versi 1 + dikunci (masuk `DomainPackRegistry`, layar aktif lewat jalur data B7).
  Pack yang sudah punya versi **berbeda** di platform → ditolak "butuh review manual" (409);
  versi **identik** dipakai ulang (draf prospek kedua yang sama).
- **Superadmin saja** — provisioning tenant produksi, bukan aksi pemilik draf.
  Endpoint: `POST /{id}/handoff`.

## Wiring

`server/routes/DiscoveryRouteFactory.kt` (`discoveryPlatformRoutes`) merakit ketiga use case dari
dependensi modul-dev & prospek, sehingga `Application.kt` hanya menambah satu pemanggilan — dan
tetap 697 baris (blok `prospectRoutes` diringkas sebagai kompensasi).

## Bukti verifikasi Fase B

- `:core:jvmTest --rerun-tasks`: **998 test, 0 gagal** (+9: 3 pricing, 6 handoff — termasuk bug
  tenant-yatim yang tertangkap test dan diperbaiki).
- `:server:test`: `DiscoveryApiTest` **4/4** (termasuk E2E baru: price → submit 409→lock→submit 201 →
  handoff 403→201 → tenant baru membaca pack `klinik`-nya sendiri via `/api/tenant/pack`).
- Kompilasi `core` & `app:shared` Jvm/WasmJs/Js hijau. **Android target tidak bisa dijalankan di
  mesin ini** (tidak ada ANDROID_HOME/SDK) — jalankan `assembleAndroidMain` di mesin ber-SDK.
- `audit-variability.sh`: 4 temuan (route mutasi `preview`/`submit`/`handoff`) — semuanya fail-closed:
  login wajib, gerbang pemilik (403), gerbang superadmin (403), syarat LOCKED (409).
- File terbesar yang disentuh: `DiscoveryRoutes.kt` 283 baris (< soft 600); test ≤ 202 baris.

## B4 — HandoffGenerator: scaffold kandidat PR (`HandoffScaffoldGenerator`)

- Generator **murni & deterministik** di `core/domain/discovery/HandoffScaffoldGenerator.kt`:
  pack data (mis. `klinik`) → daftar berkas teks. Ia **tidak menyentuh database maupun pohon
  sumber** — menimpa kosakata platform diam-diam melanggar plan §7; keluarannya kandidat yang
  wajib ditinjau manusia (pola `GenerateSeedTopologyTool`).
- Berkas yang dihasilkan per pack:
  1. `V<NNN>__register_<pack>_modules.sql` — per modul: `CREATE SCHEMA`, tabel stub
     (`<modul>_records`: id/tenant_id/payload JSONB + TODO kolom nyata), indeks tenant,
     `apply_tenant_rls_in`, `GRANT`+`ALTER DEFAULT PRIVILEGES` untuk `wemade_app` (pola V76/V77);
     lalu `INSERT INTO module_catalog_entries` lifecycle PLANNED tanpa harga (pola V64). Bagian
     backfill `custom_roles` & `granted_custom_module_ids` sengaja **TODO(review)**: level per
     jabatan adalah keputusan bisnis, bukan keputusan generator.
  2. `ModuleSchemaMap.snippet.kt.txt` — baris untuk `byModule` (dijaga `ModuleSchemaOwnershipTest`).
  3. `<Pack>StubRoutes.snippet.kt.txt` — stub route ber-gerbang fail-closed (pola `ModuleAccessGuard`).
  4. `ModuleScreenRegistry.snippet.kt.txt` — catatan: modul tanpa entri sudah memakai layar generik;
     jangan tambah entri sebelum layar kustom ada.
  5. `docs/handoff/<pack>/<modul>.catalog.md` — satu berkas review per modul (mengingatkan Uji
     Variabilitas: modul vs tahap/proses, Kontrak 1–2 tenant-variability-rules).
- Tidak ada berkas `.kt` langsung dari generator — semuanya `.snippet.*.txt` agar tidak mungkin
  ikut terkompilasi sebelum direview. Endpoint: `POST /api/discovery/drafts/{id}/scaffold`
  (superadmin saja; 409 bila draf belum LOCKED atau pack bawaan). Nomor migrasi dihitung dari
  direktori migrasi (fallback 79).
- Gotcha portabilitas: `String.format` tidak ada di commonMain KMP — nomor versi dipad dengan
  `padStart(3, '0')`.

## Fase C (parsial — C1/C2): WidgetRegistry & `ops.prototype_patterns` (V79)

**Sudah selesai:**

- `WidgetKind` — kosakata tertutup 7 widget (FORM, TABLE, KANBAN, DASHBOARD, CHECKLIST, PRINT,
  CUSTOM_SCREEN). Milik **sistem** (renderer harus bisa menggambar semuanya di semua vertikal) —
  lolos Uji Variabilitas; *isi* layar tetap data pack.
- `DiscoveryDraftValidator` kini menolak widget di luar kosakata dengan path `$.screens[i].widget`.
- `WidgetRegistry.sampleRowsFor(screen, pack)` — **sample data berupa data**: murni & deterministik
  (dijaga test), memakai kosakata pack (tanpa kata SPK/pabrik), kosong untuk modul asing — bukan
  data karangan.
- `PrototypePattern` + `PrototypePatternRepository` + `SavePrototypePatternUseCase` (core):
  fail-closed — widget wajib kosakata, `pattern_json` wajib objek JSON, pack (bila disebut) wajib
  dikenal registry, nama unik (upsert per id diperbolehkan).
- V79 `ops.prototype_patterns` (schema platform, tanpa RLS, tanpa grant `wemade_app` — pola V78).
  `created_by_user_id` **tanpa FK** ke users: pembuat adalah identitas token (superadmin platform
  tak selalu punya baris users); integritasnya tanggung jawab route (login wajib), bukan database.
- Route `GET /api/discovery/patterns` (login) & `POST` (superadmin; 409 fail-closed).
- Gotcha: migrasi yang sudah terlanjur ter-aply ke DB dev tidak boleh diedit diam-diam — setelah
  mengubah V79, tabel + baris `flyway_schema_history` versi 79 dibersihkan manual di DB dev agar
  Flyway meng-aply ulang (checksum).

**Menyusul (butuh host layar & verifikasi browser :3001):**
`PrototypeRenderer` + `ModuleMapPane`/`DataFlowPane` (kanvas read-only dari blueprint) dan Studio
internal — UI Compose memakai Clay, wajib dilihat dengan mata sebelum disebut selesai (Fase D
membawa `DiscoveryWizardScreen` sebagai hostnya).

## Utang & langkah berikutnya (diperbarui)

- A8 (Koog) tetap branch terpisah; kill-switch tidak berubah.
- Fase C (renderer/Studio), D (wizard/PDF), E (operasi produk). Verifikasi rutin Fase B:
  `:core:jvmTest --rerun-tasks` (**1001 tes, 0 gagal** setelah B4), `:server:test` hijau,
  kompilasi Jvm/WasmJs/Js core & app:shared (Android butuh mesin ber-SDK), dan `audit-variability.sh`
  (route mutasi `preview`/`submit`/`handoff`/`scaffold` — semuanya fail-closed: login wajib,
  gerbang pemilik/superadmin 403, syarat LOCKED 409).
