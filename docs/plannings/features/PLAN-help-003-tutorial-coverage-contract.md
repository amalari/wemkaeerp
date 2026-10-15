# PLAN-HELP-003: Kontrak Cakupan Tutorial per Modul (Tutorial Coverage Contract)

> Discovery Note — 2026-10-15 · dibuat lewat skill `wemade-feature-discovery`
> Status: **Selesai** (2026-10-15) · Spesifikasi: revisi v0.5 [`TRD-HELP-001`](../../trd/features/TRD-HELP-001-module-tutorials.md)

## 0. Effort Estimation & Reasoning

- **Tingkat Effort**: **Low**
- **Reasoning**: 2 file baru (`TutorialCoverage.kt` ±60 baris, `TutorialCoverageTest.kt` ±130 baris) +
  revisi TRD/checklist. Tanpa agregat baru, tanpa migrasi Postgres/RLS, tanpa bridging Strangler Fig,
  tanpa lapisan server/client/UI. Logika bisnis: satu `when` tiga cabang + ratchet dua arah.

## 1. Track 0 — Kontrak Awal (Serial Prasyarat)

Fitur sekecil ini tidak memecah worktree paralel; seluruhnya dikerjakan serial dalam satu sesi:

- [x] Policy `TutorialCoverage` di `core/.../domain/tutorial/` (value: `ModuleCoverage`, `violations(...)`)
- [x] Frozen exempt list garment (11 modul, alasan wajib) di `TutorialCoverageTest.kt`
- [x] Fixture pack non-default (`ElearningPack`) membuktikan kontrak menggigit

## 2. Pembagian Track

| Track | Lingkup | Status |
|---|---|---|
| **Track A (Core)** | `core/**` domain + commonTest | Ada — satu-satunya track coding |
| **Track B (Server)** | — | **Tidak ada**: tanpa migrasi, route, repository (Gerbang 4 & 5 N/A) |
| **Track C (Client)** | — | **Tidak ada**: tanpa perubahan UI/ApiClient/ViewModel (Gerbang 6 N/A) |
| **Track D (Verifikasi)** | Test segar + audit + (visual N/A) | Ada — tanpa cek visual karena nol perubahan UI; dilaporkan jujur |

## Rencana implementasi (dikerjakan serial)

1. [x] `TutorialCoverage.kt` + `TutorialCoverageTest.kt` dengan exempt list awal → hijau (ratchet baseline).
2. [x] Revisi TRD-HELP-001 v0.5 (kontrak cakupan, utang exempt 11 modul).
3. [x] §4 checklist `module-integration-rules.md`: butir tutorial wajib per modul.
4. [x] Teaching doc (`skill teaching`) — wajib otomatis per CLAUDE.md §12.
5. [x] Verifikasi: kompilasi 3 target `core` + `core:jvmTest` segar; cek visual **N/A** (nol perubahan UI).

## Discovery Note (keputusan yang dibekukan)

- **Siapa memakai**: developer platform & pack (yang **wajib** menyediakan), pengguna akhir
  tenant (yang diuntungkan — chat helper bisa menjawab "cara pakai modul X" untuk *setiap* modul),
  CI (yang menegakkan).
- **Data milik siapa**: platform/pack — sama seperti tutorial itu sendiri (TRD-HELP-001 §0.1).
  Daftar modul yang dikecualikan adalah keputusan developer, disimpan sebagai data di test.
- **Kapan berubah**: setiap kali modul baru masuk ke sebuah pack (wajib Covered atau Exempt),
  dan setiap kali modul lama ditambahi tutorial (exempt dicabut — ratchet dua arah).

**Masalah yang dipecahkan**: hari ini tutorial hanya ada untuk 5 dari ±16 modul garment
(org_chart, dynamic_rbac, crm_sales, sampling_order, costing_hpp). Tidak ada yang memaksa
modul baru menyediakan tutorial, sehingga chat helper (`/help/ask`) diam-diam tidak bisa
menjawab untuk mayoritas modul — tidak pernah ada yang tahu sampai user bertanya.

## 0.2 Fitur serupa

Perintah: `scripts/find-similar-feature.sh tutorial panduan bantuan help`
→ **sudah ada**: `core/.../domain/tutorial/` + `domain/help/` (TRD-HELP-001 Fase 1–4 selesai).

**Keputusan: extend HELP-001, bukan fitur baru.** Pola yang ditiru:

| Pola | Lokasi | Yang dipakai |
|---|---|---|
| Kontrak menu §5.7 dengan frozen inventory + alasan wajib | `app/.../navigation/MenuInventoryTest.kt:21-35` | Bentuk test: daftar berisi di-test persis, `Hidden` wajib `reason` |
| Invarian katalog yang bisa dites | `TutorialCatalog.violations()` (`TutorialCatalog.kt:31-44`) | Model policy murni yang mengembalikan daftar pelanggaran |
| Ratchet untuk utang yang dicicil | `file-size-rules.md` §2 Kontrak 2 | Exempt awal = utang terdaftar; tidak boleh bertambah |

## 0.3 Jenis

**Fitur lintas modul — bukan `BusinessModule`** (mengikuti HELP-001 §0.3): tidak dijual,
tidak dihitung kuota, mewarisi RBAC modul yang dituju, tanpa node kanvas, tanpa migrasi DB.

## 0.4 Uji Variabilitas

| Konsep | Beda per tenant? | Per industri? | Admin mengubah? | → Kode / Data | Catatan |
|---|---|---|---|---|---|
| **Keberadaan kontrak** (setiap modul Covered/Exempt) | Tidak | Tidak | Tidak | **Kode** (kontrak sistem) | Ditegakkan test untuk pack bawaan yang dikenal `TutorialSource` |
| Isi tutorial | Tidak | **Ya** | Tidak | Data per pack (sudah diputuskan HELP-001 §0.4) | Tidak berubah |
| Daftar modul Exempt + alasannya | Tidak | Tidak (per pack) | Tidak | **Data di test** (frozen inventory, seperti `MenuInventoryTest.expectedShown`) | Utang terdaftar; dicicil |
| Pack dari DB (tak dikenal `TutorialSource`) | — | — | — | Hanya tutorial platform | Kontrak cakupan **tidak** ditegakkan — HELP-001 Non-Goals tetap |

## 0.5 Core & extend

- **Core (baru)**: `core/.../domain/tutorial/TutorialCoverage.kt` — policy murni, tanpa I/O:
  `coverageFor(pack, tutorials): List<ModuleCoverage>` dengan
  `ModuleCoverage(moduleId, Covered(tutorialIds) | Exempt(reason))`, plus
  `violations(pack, tutorials, exempt: Map<ModuleId, String>): List<String>` yang mengembalikan:
  1. modul pack tanpa tutorial **dan** tanpa entri exempt → `"Modul X: wajib tutorial atau Exempt(alasan)"`;
  2. exempt dengan alasan kosong → ditolak;
  3. exempt untuk modul yang sudah Covered → ratchet dua arah, `"Modul X sudah punya tutorial, cabut exempt"` (mencegah exempt mati).
- **Titik extend**: `TutorialSource` per pack (tidak berubah); `TutorialCatalog.forPack` dipakai sebagai input.
- **Test**: `core/src/commonTest/.../domain/tutorial/TutorialCoverageTest.kt` —
  mengiterasi `GarmentDomainPack.pack.modules` × `TutorialCatalog(ShippedTutorialSource).forPack(pack)`
  dengan frozen exempt list; fixture pack non-garment membuktikan perilaku untuk pack tanpa
  tutorial pack (Kontrak 6 tenant-variability: test template non-default).
- **Contoh nyata yang ditiru**: `MenuInventoryTest.kt`, `TutorialCatalog.violations`.
- **Jangan disentuh**: `ModuleDefinition` & `DomainPackCodec` (kunci HELP-001 §0.5),
  file utang (`App.kt` 576/600 hard, `Application.kt` 697), tanpa endpoint/route/server baru.

## 0.6 Input/Output & kanvas

Tidak ada port, tidak ada node kanvas, tidak ada telemetri. Input policy: daftar modul pack +
katalog tutorial + daftar exempt. Output: daftar pelanggaran (dipakai test, bukan runtime).

## 0.7 Governance

Tidak ada operasi baru, endpoint baru, atau perubahan RBAC. Aturan akses tutorial tetap
`TutorialAccess` (fail-closed, HELP-001 FR-2). "Peran yang tidak boleh" tidak berlaku —
kontrak ini dijaga kompilator/test, bukan gate runtime.

## 0.8 Ukuran & gerbang TRD

**Kecil**: 1 file domain (±60 baris) + 1 file test (±120 baris) + revisi TRD-HELP-001 (v0.5)
+ satu baris penegasan di `module-integration-rules.md` §4 checklist. **TRD baru tidak perlu**
(gerbang ukuran: tanpa agregat baru, tanpa migrasi, satu fase) — cukup revisi minor ke
TRD-HELP-001 mengingat ini menegaskan Non-Goal lama tetap (tidak ada DB, tidak ada vektor).

## Inventaris awal (baseline ratchet)

16 modul garment (`MenuInventoryTest.expectedShown`); Covered saat ini 5:
`ORG_CHART`, `DYNAMIC_RBAC`, `CRM_SALES`, `SAMPLING_ORDER`, `COSTING_HPP`.

Exempt awal (dicicil, masing-masing wajib alasan): `FACTORY_FLOW`, `INVENTORY`,
`TECH_PACK_BOM`, `PRODUCTION_MRP`, `OPERATOR_EXEC`, `TRACEABILITY`, `QUALITY_CONTROL`,
`FULFILLMENT`, `MASTER_DATA`, `VENDOR_CONTACTS`, `INVOICING`.

Konsekuensi untuk helper AI: sampai exempt dicicil, chat helper tetap menjawab
"tidak menemukan tutorial" untuk modul-modul itu — perilaku yang jujur dan sudah ada,
bukan fallback senyap.

## Rencana implementasi (untuk `wemade-feature-workflow`)

1. `TutorialCoverage.kt` + `TutorialCoverageTest.kt` dengan exempt list awal → hijau (ratchet baseline).
2. Revisi TRD-HELP-001 v0.5 (kontrak cakupan Fase 5, utang exempt 11 modul).
3. §4 checklist `module-integration-rules.md`: tambah satu butir ("Apakah modul punya tutorial atau `Exempt(alasan)` terdaftar?").
4. Teaching doc (`skill teaching`) — wajib otomatis per CLAUDE.md §12.
5. Verifikasi: `jvmTest` target terkait; tidak ada perubahan UI sehingga cek visual tidak berlaku.

## Non-Goals (tetap, sesuai HELP-001)

- Retrieval berbasis vektor / RAG embedding — tetap non-goal; kalau nanti jadi Fase 6,
  corpus-nya sudah siap (katalog tutorial + `docs/teaching/`) tanpa mengubah kontrak ini.
- Tutorial yang bisa diedit admin tenant; tutorial untuk pack dari DB.
