# Rencana Implementasi: Discovery → Generator AI → Blueprint & Pack → Prototype → Handoff (Jalur B)

> Status: revisi 2 · 2026-09-30 untuk **repo B (wemkaeerp)** — putaran kedua: diaudit ke kode aktual (§0.1, T11–T15) · Pemilik: Achmad Jamaludin
> Asal: draf repo A `PLAN-discovery-blueprint-prototype-studio.md` (2026-09-29), ditinjau terhadap kondisi repo B
> setelah B4 (Blueprint), B6 (modul = data), B7 (pack per tenant), dan B8 (schema per modul + RLS).
>
> Prasyarat baca: `docs/teaching/teaching-b7-tenant-domain-pack.md`, `teaching-b8-schema-per-module.md`,
> `docs/trd/TRD-PLAT-001-blueprint.md`, `TRD-PLAT-001-tenant-pack.md`.

## 0. Hasil tinjauan draf asli

Arahnya **tepat** dan dipertahankan: funnel ber-login, output AI tidak tepercaya, fallback deterministik, prototype
sebagai data, dan kontrak membeku saat dikunci. Yang perlu diubah karena bertabrakan dengan repo B:

| # | Draf asli | Masalah di repo B | Revisi |
|---|---|---|---|
| T1 | DSL baru `PipelineBlueprint` | B4 sudah punya `domain/blueprint/Blueprint` (modul + parameter per pack), yang sudah disebut sebagai "format yang ditulis AI agent". Dua DSL = dua sumber kebenaran | Keluaran generator = **`DiscoveryDraft`** yang **membungkus** kontrak yang ada: `DomainPack` (kosakata) + `Blueprint` (alur) + `screens` (baru) |
| T2 | D8: modul kustom lewat `DynamicModuleDescriptor` | Di B, vertikal baru = modul **pack data** (`ModuleDefinition`, id berprefiks, B7). `DynamicModuleDescriptor` adalah plugin di dalam kanvas garment | Modul hasil AI = `ModuleDefinition` dalam pack draf; `DynamicModuleDescriptor` tetap untuk plugin garment saja |
| T3 | R0: tegakkan RLS | Sebagian besar **sudah selesai** (V77, `TenantRlsIsolationTest`, suite hijau di bawah `DB_APP_USER`) | R0 menyempit jadi `DB_APP_USER` wajib di produksi + audit sisa. `FORCE RLS` **tidak** dipakai: jalur owner (login, admin, platform) sengaja melewati RLS |
| T4 | Migrasi V75/V76 | Sudah terpakai (pack per tenant, schema per modul) | Mulai **V78** |
| T5 | Tabel `prospect_blueprints`, `prototype_patterns` tanpa schema | B8: tabel wajib di schema pemiliknya. Funnel prospek adalah platform dan sudah tinggal di `ops` | `ops.discovery_drafts`, `ops.prototype_patterns`; daftarkan di `ModuleSchemaMap`/`OpsSchemaBoundaryTest` |
| T6 | Handoff = scaffold kode saja | B7 sudah menyediakan jalur **tanpa kode**: simpan pack → kunci → tetapkan ke tenant | Handoff dua lapis: (a) otomatis membuat tenant + pack (modul langsung tampil di `/m/{code}`); (b) scaffold kode modul untuk tim |
| T7 | Validasi keluaran AI hanya `ProposedFlowValidator` | B7 punya validator ketat berpath (`DomainPackCodec`, `DomainPackRegistry.violations`) | Galat berpath dikembalikan ke agent untuk **koreksi diri** (maks. N putaran) sebelum draf disimpan |
| T8 | Tidak menyebut schema untuk modul hasil AI | B8: modul baru = schema `<kode>` + RLS lewat `apply_tenant_rls_in` | Scaffold handoff menghasilkan migrasi schema modul + entri `ModuleSchemaMap` |
| T9 | Kosakata layar generik tidak dibahas | Layar `/m/{code}` masih memakai istilah konveksi ("Setujui SPK", "pabrik") | Label aksi & istilah masuk ke **data pack** (`ModuleDefinition.actions`/`vocabulary`) di Fase A |
| T10 | Urutan fase: renderer & Studio sebelum AI | User ingin generator AI dulu | Irisan vertikal tipis dulu (Fase A): narasi → draf pack+blueprint → pratinjau lewat menu & `/m`. Renderer, Studio, dan PDF menyusul |

Keputusan asli yang **tetap berlaku**:
- D2: prototype = data; satu renderer generik, widget registry tertutup.
- D3: Studio mengikuti pola `TemplateDesigner`.
- D4: LLM lewat Koog in-process, di belakang `FlowTranslator`; `KeywordFlowTranslator` jadi fallback.
- D5: widget baru hanya lewat Rule of Three.
- D6: kecocokan port bersifat informasional, **bukan** gerbang.
- D7: kontrak netral terhadap vertikal.

## 0.1 Audit lanjutan ke kode aktual (putaran kedua, 2026-09-30)

Klaim T1–T10 diverifikasi ke file. Fondasi yang **sudah ada** — jangan dibangun ulang:

| Fondasi | Bukti |
|---|---|
| Domain, codec & translator prospek | `core/.../domain/prospect/` (`FlowTranslation` tak tepercaya; KDoc `ProposedFlowValidator` = dasar D6), use case `TranslateProspectFlow`/`AnalyzeCoverage`/`PriceProspectFlow`/`SubmitProspectLead`, `shared/prospect/ProspectCodec.kt`, `KeywordFlowTranslator` + `ProspectRoutes` di server |
| Funnel tersimpan di schema `ops` | V15 buat tabel → V16 `SET SCHEMA ops` (`prospect_leads`, `prospect_flow_translations`, `prospect_price_estimates`) + role `wemade_app` |
| Blueprint (B4) & pack (B7) | `core/.../domain/blueprint/`; `core/.../domain/pack/` — `DomainPackRegistry.violations`, use case `SaveDomainPackDraft`/`LockDomainPack`/`ResolveDomainPack`/`AssignTenantDomainPack`, `DomainPackRoutes` (PUT + `/{code}/lock`) |
| Schema & RLS (B8) | V76 schema per modul, V77 utang RLS, `ModuleSchemaMap`; test `ModuleSchemaOwnership`/`TenantRlsIsolation`/`OpsSchemaBoundary`; `DatabaseFactory` mendukung `DB_APP_USER` (dengan warning bila kosong) |
| Kanvas, layar generik & pola handoff | `presentation/pipeline/components/PipelineFlowCanvas.kt`; `ModuleScreenRegistry` + `GenericModuleRoute` (`/m/{code}`); pola `TemplateDesigner` di invoicing; `GenerateSeedTopologyTool` di server test |
| Versi & urutan migrasi | `kotlinx-datetime` 0.6.2 (catatan Koog di A8 akurat); migrasi terakhir **V77** → V78/V79 di rencana ini benar |

Temuan baru hasil audit — revisi terhadap fase berikutnya:

| # | Temuan | Revisi |
|---|---|---|
| T11 | T3 terlalu optimis: kebijakan & test RLS ada, tetapi penegakan **belum aktif** — `DB_APP_USER` belum disetel di `.env`/`docker-compose.yml`, dan `../tenant-isolation-rls-status.md` basi vs B8 | Item **A0** baru (Fase A); doc status RLS diperbarui 2026-09-30 |
| T12 | A5 kekurangan kolom pemilik: `ProspectLead` tak punya `owner_user_id` (hanya kontak), padahal A6 menuntut "pemilik draf saja" | **A5** menambah `owner_user_id` FK `users`; A6 gate = pemilik **atau** admin |
| T13 | Belum diketahui apakah `AssignTenantDomainPackUseCase` menerima pack non-LOCKED untuk pratinjau | **Kriteria A7** diperjelas: registry sementara per prospek, tak menyentuh registry LOCKED platform, dibersihkan saat sesi berakhir (dites) |
| T14 | Relasi ke `PLAN-dual-track-garment-and-general-platform.md` §A6/A7 belum dinyatakan | Plan ini **mencakup** A6/A7 secara generik (garment = data pack); A6/A7 Jalur A ditinjau ulang setelah Fase A–B; sync tetap satu arah A→B |
| T15 | DoD ringkas draf asli (§10) terpotong di §8 | §8 ditambah checklist design system, ukuran file & `audit-variability.sh` |

## 1. Arsitektur target

```text
[Prospek login] ─ DiscoveryWizardScreen (Wasm)
   │ narasi + profil industri
   ▼
DiscoveryAgent (server, di belakang interface domain)
   ├─ KoogDiscoveryAgent (LLM, tool: katalog modul platform, kosakata pack, validator)
   └─ DeterministicDiscoveryAgent (KeywordFlowTranslator + template) ← fallback / kill-switch
   │ keluaran tak tepercaya
   ▼
DiscoveryDraftValidator ── galat berpath ──► kembali ke agent (koreksi diri, maks. 3×)
   │ sah
   ▼
DiscoveryDraft (ops.discovery_drafts, DRAFT)
   = DomainPack (kosakata: modul, seksi, fase, slot, port, label aksi)
   + Blueprint  (modul aktif + parameter per pack)
   + screens    (deskriptor prototype, Fase C)
   │
   ├─► Pratinjau: pack draf didaftarkan ke tenant sandbox prospek → menu + /m/{code} (sudah ada di B7)
   ├─► Estimasi/SOW (PriceProspectFlowUseCase)
   ▼
LOCK & APPROVE → LOCKED (beku) → Handoff:
   (a) otomatis: tenant + SaveDomainPackDraft → Lock → AssignTenantDomainPack (B7)
   (b) kandidat PR: migrasi schema modul (B8) + stub route + layar; review manusia wajib
```

Batas lapisan:
- **core**: `domain/discovery/` (kontrak, validator, use case) dan `shared/discovery/` (codec). Murni, tanpa framework.
- **server**: Koog, repository Postgres (`ops.*`), route platform fail-closed.
- **app/shared**: `presentation/discovery/`, memakai design system Clay.

## 2. Fase A — Irisan vertikal generator (prioritas)

Tujuannya satu hal: calon klien menulis cerita, lalu **dalam hitungan detik melihat menu & modulnya sendiri**.

| # | Pekerjaan | Lokasi | Kriteria selesai |
|---|---|---|---|
| A0 | Tuntaskan penegakan RLS (sisa T11): setel `DB_APP_USER`/`DB_APP_PASSWORD` di `.env`, `.env.example`, `docker-compose.yml`; audit `dbQuery()` tanpa `tenantId` (sah hanya: `tenants`, `flyway_schema_history`, login lintas-tenant, tabel `ops`); tabel `ops` baru V78 tetap owner-only tanpa grant `wemade_app` | `.env`, `.env.example`, `docker-compose.yml` | Server mulai tanpa warning `DB_APP_USER`; `TenantRlsIsolationTest` & `OpsSchemaBoundaryTest` hijau, termasuk saat suite dijalankan dengan env aktif (`--no-daemon`) |
| A1 | Kontrak `DiscoveryDraft` (pack + blueprint + screens kosong), memakai `DomainPack`/`Blueprint` yang ada | `core/domain/discovery/` | Test: draf garment = pack + blueprint bawaan, round-trip codec identik |
| A2 | `DiscoveryDraftValidator`: gabungan `DomainPack.init`, `DomainPackRegistry.violations` (prefiks, id bersama identik), dan blueprint hanya menyebut modul pack. Galat **berpath** (`$.pack.modules[2].kind`) | `core/domain/discovery/` | Test dengan pack rusak: setiap galat punya path |
| A3 | Interface `DiscoveryAgent` + `DeterministicDiscoveryAgent` (narasi → kata kunci → modul platform + modul berprefiks) | `core/domain/discovery/` | Narasi klinik → draf sah tanpa jaringan |
| A4 | ~~Label aksi & istilah sebagai data pack~~: `DomainPack.actions` (tambah/ubah/setujui/hapus beserta label) + `DomainPack.vocabulary` (`WORKPLACE`, `DOCUMENT`); layar `/m/{code}` membacanya | `core/domain/pack/`, `presentation/navigation/GenericModuleRoute.kt`, `workspace/ModuleWorkspaceScreen.kt` | Layar klinik tanpa kata "SPK"/"pabrik"; garment identik (tabel emas). **Selesai 2026-09-30** — aksi & istilah ditaruh di **pack**, bukan `ModuleDefinition`: `DomainPackRegistry.violations` menuntut definisi modul bersama (`org_chart`) identik lintas pack, sedangkan kata chrome memang berbeda per vertikal (test: `PackVocabularyTest.packReusingPlatformModule_…`). Bukti: `PackVocabularyTest` (9 test), baris emas `GarmentModulesParityTest.actionsAndVocabulary_…`, `DomainPackApiTest` (route→DB→klien), dan cek mata `klinik-uji` (`/m/klinik_antrean`, `/m/org_chart`, `/m/klinik_kasir`): 0× "pabrik", 0× "SPK", 7× "Kunjungan" |
| A5 | V78 `ops.discovery_drafts` (id, **owner_user_id** FK `users` [T12], prospect_lead_id, status DRAFT/LOCKED, document JSONB, schema_version, locked_at) + repository + use case create/update/lock (LOCKED immutable) | server, `core/domain/discovery/usecases/` | Update ke LOCKED ditolak (test); `OpsSchemaBoundaryTest` diperbarui |
| A6 | Route platform `POST /api/discovery/drafts` (narasi → draf), `GET`, `PUT`, `POST …/lock`; login wajib, pemilik draf saja; tulis fail-closed + test 403 | `server/routes/DiscoveryRoutes.kt` | Test 403 untuk pengguna lain |
| A7 | **Pratinjau tanpa kode**: tenant sandbox per prospek (`sandbox-<lead>`); pack draf didaftarkan ke **registry sementara per prospek** — bukan `LOCKED`, tidak menyentuh registry LOCKED platform (T13); modul & menu prospek tampil di `/m`; registry dibersihkan saat sesi berakhir/kedaluwarsa | server + klien | Browser :3001: narasi → menu modul prospek tampil; registry LOCKED platform tak berubah (test); sesi berakhir → modul draf hilang |
| A8 | `KoogDiscoveryAgent`: dependensi `ai.koog:koog-agents` di `server` saja (cek `kotlinx-datetime` 0.6.2 vs Koog di branch terpisah); structured output = JSON `DiscoveryDraft`; tool: `platformModules()`, `validate(draft)`; loop koreksi diri maks. 3×; kill-switch env → deterministik | `server/infrastructure/discovery/` | Env mati → jalur deterministik; env hidup → draf sah dari narasi emas. **Selesai 2026-09-30** — `koog:1.3.0` ternyata tidak menarik `kotlinx-datetime` (tak ada benturan); evals LLM hidup 4/4 (`teaching-discovery-a8-koog-agent.md`) |
| A9 | Evals: narasi emas (klinik, bengkel, katering, garment CMT) dengan grader = validator A2 + cakupan modul yang diharapkan; skor per model/prompt dicatat | `server/src/test/` | Skor tercatat; regresi prompt ketahuan |

## 3. Fase B — Estimasi, lock, handoff

| # | Pekerjaan | Kriteria selesai |
|---|---|---|
| B1 | `PriceProspectFlowUseCase` membaca `DiscoveryDraft` (per modul baru + per layar kustom) | Estimasi berubah bila modul/layar berubah |
| B2 | Lock & freeze + CTA "Bangun Sistem Ini" → `ProspectLead` di funnel tim | LOCKED tidak bisa diubah dari API mana pun |
| B3 | Handoff (a) otomatis: buat tenant → simpan & kunci pack (owner = tenant) → tetapkan pack → salin blueprint | Tenant baru login → menu modulnya sendiri |
| B4 | Handoff (b) `HandoffGenerator` (pola `GenerateSeedTopologyTool`): migrasi `CREATE SCHEMA <modul>` + tabel + `apply_tenant_rls_in` + entri `ModuleSchemaMap` + stub route bergerbang + entri `ModuleScreenRegistry`; hasilnya **kandidat PR**, review manusia wajib | Kandidat lolos `ModuleSchemaOwnershipTest`, `TenantRlsIsolationTest`, `RouteGateTest` |

## 4. Fase C — Prototype renderer & Studio (dari draf asli R8–R15)

Isinya tetap sama dengan draf asli, dan statusnya kini:

| # | Pekerjaan | Status |
|---|---|---|
| C1 | `ModuleMapPane` (kanvas read-only dari blueprint), `DataFlowPane` (hint port informasional) | **Selesai** (Fase A/D) |
| C2 | `PrototypeRenderer` + `WidgetRegistry` v1: FORM, TABLE, KANBAN, DASHBOARD, CHECKLIST, PRINT, CUSTOM_SCREEN | **Selesai** (Fase A/D) |
| C3 | Widget dipanen dari layar produksi, sample data berupa data | **Selesai** (Fase A/D — `WidgetRegistry.sampleRowsFor`) |
| C4 | Studio internal pola `TemplateDesigner`, disimpan di `ops.prototype_patterns` (V79) | **Selesai 2026-09-30** — lihat di bawah |

Perubahan dari draf asli yang tetap berlaku: deskriptor layar menunjuk **`ModuleId` pack**, dan `screens`
masuk ke `DiscoveryDraft`, bukan DSL kedua.

**C4 — Studio Pola Prototipe (selesai).** Rute (`GET/POST /api/discovery/patterns`) dan tabel `ops.prototype_patterns` (V79)
sudah ada sebelum fase ini; yang dibangun adalah **pemakainya di `app/`**, karena sebelumnya nol.

| Berkas | Peran |
|---|---|
| `presentation/discovery/studio/PrototypePatternUiModel.kt` | model + kodek `{"rows":[…]}` + panen kerangka + pembungkus pratinjau |
| `presentation/discovery/studio/PrototypeStudioScreen.kt` | shell: galeri ↔ perancang, pratinjau hidup, simpan |
| `presentation/discovery/studio/PrototypePatternGallery.kt` | daftar + pencarian + pemilihan (warna outline, bukan ketebalan) |
| `presentation/discovery/studio/PrototypeRowEditor.kt` | penyunting baris; kolom `Lebar` berupa pil `penuh/separuh` |
| `presentation/designsystem/ClayChoiceGroup.kt` | grup pil berlabel — Aturan Tiga Kali (Widget/Pack/Modul) |
| `presentation/navigation/StudioDrawerSection.kt` | section drawer "Studio" (funnel + pola), dipisah dari `App.kt` |

Yang diputuskan sadar, beserta alasannya:

| Keputusan | Alasan |
|---|---|
| Pola = **bentuk baris** `{"rows":[{"<kolom>":"<contoh>"}]}`, bukan DSL kedua | Bentuk itu **sudah** keluaran `WidgetRegistry` dan **sudah** masukan `PrototypeRenderer`; format baru = sumber kebenaran kedua |
| Pratinjau memakai `PrototypeRenderer` yang sama dengan draf prospek | Dua renderer akan menyimpang, dan yang menyimpang adalah yang diperlihatkan ke prospek (plan D2) |
| Kerangka dipanen dari `WidgetRegistry`, bukan diketik dari nol | Pola mulai dari bentuk yang dikenali sistem; kosong = jawaban (modul × widget tanpa bentuk baku), bukan kegagalan senyap |
| `moduleId` **tidak** disimpan di pola | Menyimpannya = klaim terikat modul yang mungkin tidak ada di pack tenant lain |
| Kolom `Lebar` pada `CUSTOM_SCREEN` dikunci ke pil | Nilainya dibaca renderer untuk memasangkan blok; salah ketik mengubah tata letak tanpa memecahkan apa pun |
| Menulis pola wajib superadmin platform (server), layar hanya menyembunyikan tombol | Gerbang wewenang milik server; UI menjelaskan, termasuk bahwa isi pola tetap bisa dibaca |
| Rute klien `/discovery/studio` di bawah `/discovery` | `fromPath` memilih prefiks **terpanjang**, jadi tidak perlu alias; gerbang sesinya sama dengan funnel |

Bukti: `PrototypePatternUiModelTest` (7, `commonTest` — jalan di kelima target), `DiscoveryApiTest::payload
studio dari klien tersimpan utuh dan urut` (+1 = 8). Suite: `:core:jvmTest` **1030**, `:app:shared` **169**,
`:server:test` **284** (1 skip = eval LLM opt-in), nol gagal. Cek mata di :3001 (superadmin **dan** owner
pabrik): panen kerangka → sunting → simpan → entri muncul di galeri → reload → masih ada (lewat Postgres),
tag "Hanya baca" + tombol mati untuk non-superadmin, lebar 1280dp tidak pecah. Pengajaran:
[`teaching-discovery-c2-studio-pola-prototipe.md`](../teaching/teaching-discovery-c2-studio-pola-prototipe.md).

**Dua cacat yang hanya ketahuan setelah layarnya dibuka** (bukan oleh test maupun kompilasi):

1. Daftar pola menampilkan `Expected a JSON object at root` pada endpoint yang sehat: `DiscoveryApiClient.call()`
   mem-parse **wajib objek**, sedangkan `GET /api/discovery/patterns` mengembalikan array. Kini `JsonParser.parse`
   — bentuk respons diperiksa pemanggil, tempat maknanya diketahui.
2. Pesan baris-kosong `PrototypeRenderer` ("modulnya tidak ada di pak ini") menyesatkan di Studio: yang kosong
   adalah pola yang sedang disusun. Studio memeriksa konteksnya sendiri; renderer bersama tetap satu pesan.

### Sisa Fase C (temuan, belum ditutup)

- **Test pola menulis ke DB pengembang**: `module()` belum menerima `PrototypePatternRepository`, jadi
  `PostgresPrototypePatternRepository()` (`Application.kt:550`) yang dipakai — termasuk oleh test. Akibatnya
  fixture test muncul di Studio, dan `UNIQUE(name)` menuntut test idempoten. Perbaikannya satu parameter, tapi
  `Application.kt` (697 baris) sudah di atas hard limit 500, sehingga harus mendarat **bersamaan** dengan
  pemecahan file itu (konfigurasi plugin → file terpisah). Efek samping: jalur `id: null → server membuat id`
  tidak lagi diuji end-to-end.
- **Pola belum dipakai di pratinjau draf**: hari ini Studio adalah alat internal; `screens` draf tetap dari agent.
- Kosakata "pabrik" di layar lain (`OrgChartScreen`, `ModuleCardView`, `AssignModuleModal`) masih di utang
  design system. Funnel discovery sudah bersih (judul + contoh kini lintas vertikal).

## 5. Fase D — Wizard & PDF

- `DiscoveryWizardScreen` 4 langkah, digerbang lewat `accessDecisions`. **Selesai 2026-09-30.**
- `BlueprintPdfRenderer` ber-watermark. **Selesai 2026-09-30** — lihat rincian di bawah.
- Isi kedua pekerjaan ini tetap sama dengan draf asli R16–R18.

**D1 — PDF blueprint ber-watermark (selesai).** Dibangun mengikuti jalur cetak yang sudah ada di repo,
bukan jalur baru: isi & geometri diputuskan di **domain** (`core/.../domain/discovery/print/`:
`BlueprintPdfDocument` + `BlueprintSheetLayout`), digambar PDFBox di
`server/.../infrastructure/pdf/BlueprintPdfRenderer.kt`, dan disajikan lewat rute terpisah
`server/.../routes/DiscoveryBlueprintPdfRoutes.kt` (`POST /{id}/print-ticket` →
`GET /{id}/blueprint.pdf?ticket=…`).

Yang diputuskan sadar, beserta alasannya:

| Keputusan | Alasan |
|---|---|
| Baris dipecah sekali di domain lewat `InvoiceTextLayout` | Renderer tidak boleh punya mesin pengukur kedua; kalau ia memutus barisnya sendiri, PDF bisa berbeda dari yang dihitung domain (pelajaran faktur) |
| Pemenggalan halaman di tingkat **baris**, halaman lanjut diberi judul "(lanjutan)" | Deskripsi blueprint panjang; pemenggalan per blok menyisakan setengah halaman kosong dan halaman terpisah tanpa identitas saat difotokopi |
| Modul **bypass** ikut dicetak (`[bypass]`) | Blueprint yang hanya menampilkan modul aktif membuat prospek membandingkan penawaran dengan sistem yang berbeda (B4/TRD-PLAT-001 FR-2) |
| Watermark diagonal 40pt abu-abu 0,88 di tengah **setiap** halaman | Berkas ini beredar lewat WhatsApp prospek; penanda kecil di kaki halaman terbaca sebagai catatan kaki dan mudah difoto lalu dirujuk sebagai kesepakatan |
| Teks yang dicetak disaring `ASCII_FALLBACK` + `?` | Font yang dibundel tidak memuat seluruh glyph Unicode (`→` U+2192 melempar `IllegalStateException`); isi PDF sebagian berasal dari kosakata pack tenant, dan satu karakter aneh tidak boleh menggagalkan seluruh dokumen |
| Tiket cetak membawa `subject` + `platform_superadmin` (`PrintTicketService.verifyUser`) | Tab browser tidak bisa mengirim Bearer; tiket berumur 60 detik itu tetap diperiksa **kepemilikan draf** di rute (gerbang T12 yang sama dengan endpoint JSON) |
| Tanpa harga di PDF | Harga hidup di `/price`; PDF berpindah tangan dan tidak boleh berisi angka yang bisa dibaca sebagai penawaran |

Bukti: `BlueprintPdfDocumentTest` (5), `BlueprintSheetLayoutTest` (8, termasuk invariant "semua baris di
dalam margin & tidak menimpa" dan "40 modul tercetak tepat sekali"), `BlueprintPdfRendererTest` (4,
termasuk uji tinta pita tengah halaman: halaman yang sama dicetak dengan & tanpa watermark lalu
dibandingkan pikselnya), `PrintTicketServiceTest` (+3: `verifyUser`, kedaluwarsa, silang-cakupan), dan
`DiscoveryApiTest::pdf blueprint memakai tiket pendek dan gerbang pemilik` (401 tanpa sesi, 200 pemilik
lewat tiket **tanpa** Bearer, 200 Bearer & superadmin, 403 pengguna lain, 401 tiket draf lain, 404 draf
hantu). Pengajaran lengkap: [`teaching-discovery-d1-blueprint-pdf.md`](../teaching/teaching-discovery-d1-blueprint-pdf.md).

**Dua cacat yang hanya ketahuan setelah PDF-nya dilihat** (bukan oleh test mana pun, bukan oleh kompilasi):

1. Fase tercetak bernomor dobel (`"1. 1. Operasi — Alur kerja harian"`) karena `BlueprintPdfDocument`
   menambahkan `order` padahal `PhaseDefinition.displayName` pack sudah memuat nomornya. Sekarang
   `displayName` dipakai apa adanya; test membandingkan baris terhadap pack.
2. Teks yang dicetak sempat kehilangan tipografi: fallback pertama memetakan em dash/titik tengah
   padahal font yang dibundel **punya** glyph-nya — hanya `→` dan `✓` yang absen (dibuktikan dengan
   menyondir `getStringWidth` per karakter). Fallback dipersempit ke dua karakter itu; em dash kembali
   tercetak sebagai `—`.

### Sisa Fase D
- Pratinjau PDF di dalam aplikasi (saat ini membuka tab browser; Android/iOS masih no-op seperti fitur cetak lain).


## 6. Fase E — Operasi produk

Sama dengan draf asli R22–R25: sesi interview persisten, demand ledger "tidak bisa diekspresikan", dan gerbang widget
lewat Rule of Three.

| # | Pekerjaan | Status |
|---|---|---|
| E1 | Sesi interview persisten: wizard memuat `GET /api/discovery/drafts`, menawarkan "Lanjutkan sesi sebelumnya" untuk draf DRAFT milik pengguna; ringkasan penuh → resume langsung ke langkah 2 | **Selesai 2026-09-30** — `DiscoveryWizardScreen` + `ResumeDraftsCard`; langkah wizard dipecah ke `DiscoveryWizardSteps.kt` (file melewati soft limit 400) |
| E2 | Buku demand: narasi prospek **verbatim** kini tersimpan (sebelumnya tidak di mana pun!) — `ops.discovery_demands` (V80), dicatat fail-loud saat `POST /drafts`, dibaca `GET /api/discovery/demands` (superadmin saja, 403 untuk pengguna lain) | **Selesai 2026-09-30** — `DiscoveryDemand` + `DemandLedger` (core, murni), `PostgresDiscoveryDemandRepository`, insert-saja (demand = catatan historis) |
| E3 | Gerbang Rule of Three: istilah narasi yang belum terwakili modul dikelompokkan lintas demand; ≥ 3 demand berbeda → kandidat modul/widget, dihitung saat dibaca (bukan disimpan) | **Selesai 2026-09-30** — `DemandLedger.unmatchedTerms/candidates`, diekspos di respons `GET /demands` (`minimum`, `candidates`) |

Keputusan Fase E, beserta alasannya:

| Keputusan | Alasan |
|---|---|
| Narasi verbatim disimpan di buku demand, bukan ditambahkan ke dokumen `DiscoveryDraft` | Dokumen draf = kontrak yang dibekukan & divalidasi ketat; narasi adalah **sinyal produk**, bukan bagian kontrak. Menaruhnya di dokumen berarti mengubah schema dokumen lama hanya demi telemetri |
| Gagal menyimpan demand menggagalkan `POST /drafts` (bukan best-effort) | Narasi kini **satu-satunya** tempatnya — kegagalan senyap = demand hilang tanpa jejak, persis penyakit "fallback senyap" (tenant-variability Kontrak 4) |
| Pencocokan istilah = substring sederhana terhadap kosakata pack, bukan embedding | Sasarannya menyaring kata yang sudah terjawab, bukan memahami bahasa; false positive tidak fatal karena kandidat disertai kutipan narasi asli untuk dibaca manusia |
| Kandidat Rule of Three dihitung saat dibaca | Ambang bisa berubah (2 → 4) tanpa migrasi; yang disimpan adalah fakta per demand |
| Pencatatan demand diecek `​OpsSchemaBoundaryTest` + test 403 | Tabel baru di `ops` tanpa baris guard akan "pindah" ke public diam-diam — pola yang sama dengan discovery_drafts |
| Sambil menyentuh wiring: `prototypePatterns` di-inject lewat `module()` (utang Fase C dicicil) | Sebelumnya `PostgresPrototypePatternRepository()` dibuat langsung di `Application.kt:550` — test Studio menulis ke DB pengembang. `Application.kt` tetap 698 baris (ratchet: dipadatkan dua val 2-baris) |
| Layar buku demand di drawer Studio (bukan modul/governance), barisnya disembunyikan untuk non-superadmin | Buku demand tetap bukan `BusinessModule` (platform, bukan tenant) — pola yang sama dengan dua layar Studio lain. Menyembunyikan baris mencegah pengguna menabrak layar yang pasti 403; layarnya tetap menjelaskan gerbang jika diakses langsung lewat URL |
| Narasi dipulihkan lewat ringkasan draf (`findByDraftId`), bukan endpoint "my demands" baru | Demand milik pemilik draf yang sama — membuangnya ke ringkasan yang sudah tergerbang kepemilikan menambah nol permukaan serangan baru; klien tidak perlu fetch kedua saat resume |
| Non-superadmin tidak menembak `GET /demands` sama sekali | Server pasti 403; request yang pasti gagal hanya menambah bising console dan jejak audit palsu |

Irisan kedua Fase E (2026-09-30, sore) — selesai:
- **Layar Buku Demand** `DemandLedgerScreen` di rute `/discovery/demands` (drawer Studio, baris hanya untuk superadmin): kandidat Rule of Three di atas (kartu outline Primary + badge jumlah demand + kutipan narasi), lalu semua demand (narasi verbatim, ClayTag istilah tak terwakili, footer agent + tanggal). Non-superadmin melihat kartu penjelasan gerbang (dan tidak menembak endpoint sama sekali).
- **Pemulihan narasi saat resume**: `DiscoveryDemandRepository.findByDraftId` → narasi masuk ringkasan draf (`summaryWithNarrative`), wizard mengisi ulang textarea saat "Ubah Narasi" — draf pra-V80 tetap `null` dan mulai kosong.

Sisa Fase E (belum ditutup):
- Autosave narasi saat mengetik (resume kini sudah memulihkan teks, tapi masih ada jendela kehilangan jika tab tertutup sebelum "Susun Draf").
- Gerbang Rule of Three belum terhubung ke keputusan widget Studio (saat ini hanya melapor di layar Buku Demand).

## 7. Risiko

| Risiko | Mitigasi |
|---|---|
| LLM mengarang modul/port tak sah | Validator berpath + loop koreksi; yang tidak lolos tidak pernah tersimpan |
| Prospek merebut id modul platform | `DomainPackRegistry.violations` (prefiks wajib, id bersama wajib identik) |
| Pack draf prospek mencemari registry server | Hanya tenant sandbox yang memakainya; draf tidak `LOCKED`; dilepas saat sesi berakhir/kedaluwarsa |
| Biaya & latensi LLM | Kill-switch, fallback deterministik, batas putaran, evals sebelum ganti model |
| Data prospek bocor antar-prospek | `ops.*` tanpa akses tenant; route memeriksa pemilik draf; test 403 |
| Kosakata garment bocor ke vertikal lain | A4 + tabel emas garment + cek visual `klinik-uji` |

## 8. Verifikasi per fase

- core/app/server hijau dengan `--rerun`, juga di bawah `DB_APP_USER` (`--no-daemon`). Hitung file hasil test, jangan
  hanya exit code.
- `ModuleSchemaOwnershipTest`, `TenantRlsIsolationTest`, `RouteGateTest`, dan snapshot akses 690 tetap hijau.
- Browser :3001 (repo B): narasi → menu prospek; `klinik-uji` & `wemade-demo` tidak berubah; 0 error console.
- Teaching doc per fase di `docs/teaching/`.
- Checklist ringkas per fase (T15): nol `Color(0xFF)` di luar theme; `ClayShapes`/`ClaySpacing`/`ClayBorder`; tanpa `Modifier.shadow()`; tanpa ternary mode gelap baru; file Kotlin baru di bawah soft limit lapisannya; `scripts/audit-variability.sh` tanpa temuan baru.
