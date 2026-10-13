# TRD-I18N-002: Lokalisasi Data Pack (Domain Pack) — `LocalizedText`

## 1. Document Context and Administration

- **Title & ID**: TRD-I18N-002 — Fase 2 multibahasa: teks data pack (garment dan pack lain) dua bahasa
- **Status**: Draf 1 untuk persetujuan · **Tanggal**: 2026-10-13 · **Pemilik**: Achmad Jamaludin
- **Prasyarat**: [`TRD-I18N-001`](TRD-I18N-001-multibahasa-ui-dan-label-sistem.md) (`LanguageCode`, pemilih bahasa) · **Plan**: [`PLAN-i18n-phase2-pack-localization.md`](../../plannings/features/PLAN-i18n-phase2-pack-localization.md)
- **Prioritas**: `features/`

### Revision History

| Versi | Tanggal | Penulis | Catatan |
|---|---|---|---|
| 0.1 | 2026-10-13 | Claude (atas permintaan Achmad) | Draf dari scan `core/domain/pack|pipeline|rbac` |

### Summary & Business Context

Teks yang dilihat pengguna tidak hanya ada di UI. Sebagian besar adalah **data pack**: `PhaseDefinition.displayName/subtitle`,
`SlotDefinition.displayName/defaultStatuses`, nama modul, `ScreenSuggestion.title` + baris contoh, tutorial,
wawancara referensi, petunjuk peran (`GarmentRoleHints`), katalog modul/spec pipeline.

Ukuran (hitungan kasar literal berawalan huruf kapital): `domain/pack` ±740 (28 file), `domain/pipeline` ±490 (41 file),
`domain/rbac` ±60 → **±1.300 string**, sebagian tumpang tindih dengan hitungan label di TRD-I18N-001.

Mengapa bukan sekadar menambah terjemahan: semua field itu `String` biasa, tersimpan di `StoredDomainPack`, dibaca ulang oleh codec,
dan sebagian **dibekukan** di dokumen (SPK, `frozenStageFlow`). Mengubahnya menyentuh bentuk data, codec, dan paritas konveksi —
satu tahap Strangler Fig sendiri (tenant-variability Kontrak 8, setara tahap B0–B6).

### Stakeholders & Approvers
Product (Achmad), Tech Lead, QA, Developer; penerjemah (tutorial dan wawancara = review manusia).

### Goals (In-Scope)

- Value object `LocalizedText` (peta `LanguageCode → String`, wajib memuat `id`) dengan parser tunggal.
- Pack mendeklarasikan bahasa yang dilengkapinya (`PackLanguages`); validator memastikan klaim bahasa **lengkap**.
- Migrasi bertahap per tipe: Phase → Slot → Module/Catalog → ScreenSuggestion → RoleHint → Tutorial/Interview.
- Codec membaca bentuk lama (string biasa = `id`) dan bentuk baru; **tanpa migrasi data**.
- Terjemahan Inggris pack `garment` (kecuali tutorial/wawancara panjang bila belum ada penerjemah — ditandai belum lengkap, tidak dipalsukan).
- Indikator eksplisit di UI bila teks pack belum tersedia dalam bahasa terpilih.

### Non-Goals (Out-of-Scope)

- Teks yang ditulis tenant (nama tahap/modul kustom, isi dokumen): satu bahasa penulis, tidak diterjemahkan otomatis.
- Terjemahan otomatis oleh LLM sebagai bawaan (boleh sebagai usulan draf terjemah, bukan data final).
- Bahasa ketiga.
- Mengubah kode/kunci pack (`ModuleId`, `SlotCode`, `PhaseCode`): kunci tidak pernah dilokalkan.
- Mengubah dokumen yang sudah beku (SPK `frozenStageFlow`): tetap seperti saat dibekukan.

## 2. Functional Requirements

### F1 — `LocalizedText`
- Konstruksi: `LocalizedText.of(id = "…", en = "…")`; `id` wajib terisi; bahasa lain opsional; nilai kosong ditolak.
- `resolve(language)` mengembalikan `Resolved(text, usedLanguage)` — **bukan** string polos — agar pemanggil
  tahu bila terjadi pengembalian ke `id`. Tidak ada fallback senyap (Kontrak 4): UI menandai
  "Belum diterjemahkan" (lencana) saat `usedLanguage != requested`.
- Kode bahasa tak dikenal di dokumen tersimpan → ditolak oleh codec.

### F2 — Klaim kelengkapan pack
- `DomainPack.languages: Set<LanguageCode>` (selalu memuat `id`).
- Validator pack: bila `en ∈ languages`, **setiap** `LocalizedText` di pack wajib punya `en`; selisih dilaporkan per jalur (`phases[2].displayName`).
- Pack tanpa klaim `en` tetap sah; UI memakai `id` + lencana.

### F3 — Strangler Fig per tipe (urutan tetap)
| Tahap | Tipe | Penanda selesai |
|---|---|---|
| P1 | `PhaseDefinition` (`displayName`, `subtitle`) | Pembaca fase tak lagi menyentuh `String` lama |
| P2 | `SlotDefinition` (`displayName`, `defaultStatuses`) | idem |
| P3 | `ModuleDefinition`, katalog `OperationalModuleCatalog`/`FoundationModuleCatalog` | idem |
| P4 | `ScreenSuggestion` (`title`, baris contoh) | idem |
| P5 | `RoleHint`, template tahap (`IndustryStageTemplates`) | idem |
| P6 | Tutorial, wawancara referensi | idem |

Setiap tahap: struktur baru di samping field lama → jembatan (`displayName` tetap tersedia sebagai `id`) → pindah pembaca satu paket per PR →
pemindai "pembacaan jembatan" kosong → hapus jembatan.

### F4 — Codec & dokumen tersimpan
- Bentuk lama `"displayName":"Potong"` dibaca sebagai `{id:"Potong"}`; bentuk baru `{"id":"Potong","en":"Cutting"}`.
- Penulisan selalu bentuk baru bila ada >1 bahasa; bila hanya `id`, tetap boleh bentuk lama (kompatibel mundur dengan pembaca versi sebelumnya selama masa transisi).
- Dokumen beku tidak ditulis ulang.

### F5 — Pack buatan tenant/agent
- Agent/Builder yang membuat pack mengisi `id`; bahasa tambahan opsional dan diusulkan sebagai draf terjemah yang **harus disetujui** manusia sebelum masuk data.
- `PackSuggestionMapping` dan `ProposalEntityRules` menolak `LocalizedText` tanpa `id`.

## 3. Non-Functional Requirements (NFRs)

| Category | Requirement & Target Metrics | Rationale / Mitigation |
| :--- | :--- | :--- |
| **Performance** | `resolve` O(1); ukuran dokumen pack naik < 40% | Peta kecil (≤2 bahasa) |
| **Scalability** | Bahasa ke-3 = tambah kode di `LanguageCode.supported` + terjemahan, tanpa ubah skema | Peta terbuka per kode, daftar kode tertutup |
| **Security** | Teks pack dibersihkan seperti data lain; tidak dieksekusi; panjang dibatasi (`ProposalLimits.TEXT`) | Cegah muatan besar via bahasa tambahan |
| **Availability & Reliability** | Pack lama terbaca identik; **tes paritas konveksi** per tahap | Strangler Fig; tanpa migrasi data |
| **Maintainability & Observability** | Satu parser; `else ->` dilarang; laporan kelengkapan terjemahan per pack | Validator menunjuk jalur yang kurang |
| **Content Quality** | Tutorial/wawancara diterjemahkan manusia; draf mesin ditandai | Teks panjang berisiko makna melenceng |

## 4. System Architecture & Technical Design

### High-Level Architecture

```mermaid
flowchart LR
  PACK[DomainPack<br/>languages={id,en}] --> LT[LocalizedText fields]
  STORED[(StoredDomainPack JSONB)] -->|Codec: lama→{id}| LT
  LT --> RES[resolve(language)]
  PREF[LanguageProvider<br/>TRD-I18N-001] --> RES
  RES --> UI[Kanvas, menu, tutorial, layar usulan]
  RES -->|usedLanguage≠requested| BADGE[Lencana 'Belum diterjemahkan']
  VAL[PackLanguageValidator] --> PACK
```

### Detailed Component Design

- `core/.../domain/i18n/LocalizedText.kt` — `data class LocalizedText(private val byLanguage: Map<LanguageCode,String>)`,
  `init { require(id ada & tak kosong) }`, `resolve()`, `languages`, `withTranslation()`. `PackLanguageValidator` di `domain/pack/`.
- `LocalizedTextCodec` di `shared/pack/` — satu parser; menolak kode bahasa tak dikenal.
- Field berubah (P1–P6): `displayName: String` → `displayName: LocalizedText`, jembatan sementara `val displayNameId: String`.
- UI: komponen pembungkus **di package fitur** (bukan `designsystem/`, buta domain) memanggil `resolve` dan merender lencana via `ClayBadge(text, tint)`.

### Data Model & Schema
- **Tanpa migrasi SQL**: pack tersimpan sebagai dokumen (`StoredDomainPack`); bentuk baru kompatibel lewat codec.
- ~~Opsional: kolom `pack_languages TEXT[]`~~ — **Diputuskan (Track B, 2026-10-13): TIDAK ditambahkan.** Dokumen JSONB (`domain_packs.definition`) sudah memuat kunci `languages`; tidak ada kebutuhan menyaring pack per bahasa di SQL (daftar pack kecil, dibaca penuh oleh `findAllEffective`). Tanpa migrasi SQL ⇒ `J3MigrationFenceTest` tak tersentuh. Tambahkan kolom hanya bila muncul kebutuhan nyata menyaring ribuan pack tanpa membuka dokumen.
- **Kontrak API Track B** (semua di bawah `/api/admin`, superadmin saja, fail-closed oleh plugin tenant sebelum handler):
  - `PUT /api/admin/domain-packs/{code}` — body = dokumen `DomainPackCodec` (bentuk lama atau `{id,en}`); kode bahasa tak dikenal → 400. Respons ringkasan kini memuat `languages` (array kode).
  - `GET /api/admin/domain-packs/{code}/translation-report[?version=N]` — `{code, version, claimed:["en","id"], missing:[{path,language}], countByLanguage:{"en":N}}` dari `DomainPack.missingTranslations()`; tanpa `version` = versi terbaru (termasuk draf); 400 bila `version` bukan bilangan bulat, 404 bila pack/versi tak ada.
  - `GET /api/tenant/pack` — tetap *open by design*; kini mengembalikan teks dua bahasa bila pack memilikinya. Pack id-saja ditulis byte-identik dengan format lama. Kompatibilitas klien lama dicatat di versi API tahap P6.
  - Dokumen beku (`frozenStageFlow`, SPK) hidup di tabel sampling, tidak membaca `domain_packs`; memperbarui pack tidak menulis ke sana.

### API Specifications & External Contracts
- Endpoint pack yang ada mengembalikan `LocalizedText` sebagai objek `{id,en?}`; klien lama (hanya string) tidak didukung setelah tahap P6 — versi API dicatat di TRD, bukan diam-diam.
- Tulis pack (Builder/superadmin) tetap fail-closed dengan gerbang yang berlaku; test peran tak berwenang 403 tidak berubah.

### Technology Usage & Tradeoff Justification

| Pilihan | Alasan | Alternatif ditolak |
|---|---|---|
| `LocalizedText` bersifat data di pack | Uji Variabilitas: beda per pack/tenant | Resource string (XML): pack adalah data tenant/superadmin, bisa diubah tanpa rilis |
| `resolve` mengembalikan `Resolved` | Tak ada fallback senyap | `String` polos: menyembunyikan kekurangan terjemahan |
| Bentuk lama dibaca sebagai `id` | Tanpa migrasi data | Migrasi massal: risiko pada pack tersimpan |
| Klaim bahasa per pack | Kelengkapan bisa dinilai mesin | Bendera global: pack parsial menyesatkan |

### Assumptions, Constraints, & Dependencies
- TRD-I18N-001 (Track 0) selesai: `LanguageCode`, `LanguageProvider`.
- Menyentuh/mengganti enum atau struktur konveksi dibolehkan (CLAUDE.md Jalur B); menambah entri enum konveksi tidak.
- Kode mesin (`domain/pipeline` kanvas, RBAC) tidak boleh menyebut konsep satu industri; terjemahan garment hanya di pack `garment`.
- `TenantCodeBoundaryTest` dan `J3MigrationFenceTest` harus tetap hijau.
- File besar (mis. `GarmentModules`, `GarmentTutorials`) tunduk Ratchet; bila perlu dipecah per bahasa/tema (bukan `Part2`).
- Penerjemah manusia untuk tutorial/wawancara tersedia; bila tidak, tahap P6 ditandai belum lengkap.

## 5. Testing, Deployment, and Operations

### Effort Estimation & Reasoning
- **Rating**: `High` (menyentuh bentuk data pack, codec, dan paritas konveksi).
- **Reasoning**: model + codec + validator ±1 minggu; P1–P5 ±1,5–2 minggu; P6 + terjemahan ±1 minggu (di luar waktu penerjemah) → **±3–4 person-week**.
  Tanpa migrasi DB; risiko ada di paritas konveksi dan pack tersimpan.

### Parallel Execution Plan (Tracks)
- **Track 0 — Contract**: `LocalizedText`, `PackLanguages`, parser/codec kontrak, fixture pack lama + baru di `commonTest/resources`, keputusan kolom `pack_languages`.
- **Track A — Core**: tahap P1–P6 per PR; validator; tes paritas konveksi per tahap; tes dengan **pack non-default** (fixture bordir) dan `bordir-uji`.
- **Track B — Server**: pembaca/penulis pack dengan codec baru, endpoint tulis fail-closed + tes 403.
- **Track C — Client**: pembungkus resolve + lencana, tampilan kanvas/menu/tutorial dua bahasa, tes ViewModel.
- **Track D — Visual**: `wemade-demo` dan `bordir-uji` ~1280dp, id↔en, lencana pada pack parsial.

### Technical Acceptance Criteria (AC)
1. Tenant konveksi bahasa `id`: keluaran identik dengan sebelum tahap (tes paritas lulus untuk P1–P6).
2. Pack `garment` yang mengklaim `en` dan **lengkap** (validator hijau); selisih menggagalkan tes.
3. Dokumen pack lama (string biasa) terbaca; kode bahasa tak dikenal ditolak.
4. `resolve("en")` pada teks tanpa `en` → `Resolved(usedLanguage=id)` dan UI menampilkan lencana.
5. Dokumen beku tidak berubah setelah pack diperbarui.
6. Tidak ada `else ->` baru; tak ada pembacaan jembatan tersisa saat tahap ditutup.
7. Kompilasi 5 target hijau; `scripts/audit-variability.sh` tak menambah temuan.

### Testing Strategy
- Unit: `LocalizedText`, codec (round-trip, bentuk lama, kode tak dikenal), validator kelengkapan.
- Paritas: iterasi pack tersimpan/preset, bandingkan keluaran id sebelum vs sesudah.
- Integrasi: baca/tulis pack di DB scratch; 403 untuk peran tak berwenang.
- Visual: Track D, dua tenant dan dua bahasa.

### Monitoring & Error Handling
- Laporan kelengkapan terjemahan per pack (jumlah jalur kurang) sebagai keluaran skrip/tes.
- Codec menolak muatan korup dengan pesan menunjuk jalur; tidak ada penggantian diam-diam.

### Deployment & Rollback Plan
- Rilis per tahap P1…P6; setiap tahap bisa berdiri sendiri (jembatan menjaga pembaca lama).
- Rollback = kembali ke pembaca jembatan (`displayNameId`); data tersimpan tetap kompatibel karena bentuk lama tetap terbaca.
- Kompilasi 5 target sebelum merge; `J3MigrationFenceTest` dan `TenantCodeBoundaryTest` hijau.

### Catatan Track A (2026-10-13) — P3–P5 selesai, P6 ditunda

- **Selesai (jembatan + codec + validator + terjemahan `en` garment)**: `ModuleDefinition.displayNameText/descriptionText`, `ModuleSection.displayNameText`, `ScreenSuggestion.titleText/rationaleText`, `RoleHint.labelText`, `ModuleAction.labelText`, `DomainPack.vocabularyText/portLabelTexts`. Semua tercatat di `PackTranslations.localizedTexts()`. Pack `garment` mengklaim `en` dan lengkap di seluruh jalur itu.
- **Sengaja tidak dilokalkan**: `ScreenSuggestion.sampleRows` (kunci `Kolom`/`Simpan`/`Butir` = protokol struktur renderer), `SlotDefinition.defaultStatuses` (nilai = kunci data kolom kanban), `RoleHint.word` (kunci pencocokan teks cerita), `OperationalModuleCatalog`/`FoundationModuleCatalog` (spec mesin, tanpa teks tampilan). Terjemahan nilai-nilai ini butuh keputusan desain terpisah (kunci vs label).
- **P6 ditunda**: `GarmentTutorials`/`ShippedTutorialSource` hidup di `domain/tutorial` (`ModuleTutorial`), bukan di `DomainPack`; `GarmentReferenceInterview` adalah wawancara acuan, bukan teks tampilan. Butuh jembatan terpisah dan penerjemah manusia; tidak dipalsukan.
- **Bahaya `copy()`**: `copy(description = …)` pada tipe berpasangan `xxxText` melanggar invarian jembatan. Pakai `withIdText`/`withIdRationale`/`withIdLabels` (`PackIdTextEditing.kt`) yang mengganti `id` bersama pasangannya.
- **Identitas pack bawaan**: terjemahan = kolom aditif. `matchesShipped` dan `DomainPackRegistry.identityViolations` membandingkan di luar terjemahan (`withoutTranslations()`); dokumen yang membawa terjemahan wajib identik dengan milik platform.
