# Rencana Dua Jalur: WeMade ERP Konveksi + Platform Alur General

> Status: draf 2026-09-29 · Pemilik: Achmad Jamaludin
> Konteks: diskusi TRD-FLOW-002 (kanvas dari katalog) → kebutuhan AI agent yang menyusun kanvas dari
> deskripsi bisnis, termasuk bisnis non-konveksi (contoh: e-learning).

## 0. Keputusan

| # | Keputusan | Alasan |
|---|---|---|
| K1 | **Jalur A** = repo ini, tetap **ERP konveksi**. Enum konveksi (`BusinessModule`, `ModuleArchetype`, `PipelineStage`, `GarmentBusinessPreset`) **tidak dibongkar**. | Klien nyata berjalan di sini; RBAC/entitlement bersandar pada enum (97 file, 572 referensi `BusinessModule`). |
| K2 | **Jalur B** = repo baru hasil fork, menjadi **platform alur general**: fase, slot, tipe port, dan modul menjadi **data per Domain Pack**; konveksi = pack pertama. | Refactor besar tanpa menahan Jalur A. |
| K3 | Aliran kode **satu arah: A → B** (cherry-pick mesin kanvas). B tidak pernah di-merge balik ke A. | Menjaga A stabil; B boleh merombak. |
| K4 | Titik temu (konvergensi) **diputuskan belakangan** dengan kriteria di §5 — bukan target tanggal. | Abstraksi dari satu vertikal belum terbukti; butuh vertikal kedua nyata. |

---

## 1. Langkah 0 — Titik Fork (dikerjakan sekali, sebelum dua jalur berjalan)

1. **Commit pekerjaan TRD-FLOW-002 Fase 1–5** di repo ini (saat ini ±62 file belum di-commit).
2. Pastikan hijau: `core` 932, `app:shared` 158, `server` 231 test; kompilasi JVM/Wasm/JS (+ Android di mesin ber-SDK).
3. **Tag**: `git tag fork-point/general-2026-09` — titik rujukan semua cherry-pick berikutnya.
4. Buat repo B dari tag itu (repo terpisah, bukan branch — riwayat, CI, dan izin berbeda).
5. Di repo B, perbarui `.claude/CLAUDE.md` + rules: sebut bahwa enum konveksi **boleh dan sedang** dimigrasi,
   lalu `scripts/sync-agent-config.sh`. Tanpa ini, asisten AI di repo B akan menolak refactor-nya sendiri
   karena rules menyebut enum sebagai kontrak.

**Keluaran**: tag fork, repo B berjalan, rules kedua repo tidak saling bertentangan.

---

## 2. Aturan Batas "Mesin Kanvas" (berlaku di Jalur A)

Supaya cherry-pick A → B murah, bagian **mesin** di A ditulis netral dari kosakata konveksi.

**Mesin (wajib netral — boleh di-cherry-pick):**
- `CatalogPortWiring`, `CatalogPipelineBuilder`, `PipelineTelemetryOverlay`, `ModuleTelemetryCodec`,
  `ModuleTelemetryProvider`, `ModuleFeature` (bentuk registry-nya), kontrak baru `EdgeFlowProvider`,
  kontrak baru `PipelineBlueprint` + validatornya.
- Aturan: kontrak **baru** memakai **string/value object** untuk fase, slot, tipe port
  (`PhaseCode`, `SlotCode`, `PortType`), bukan enum konveksi. Adaptor tipis memetakan enum konveksi ke string.

**Vertikal (boleh khusus konveksi — tidak di-cherry-pick):**
- `OperationalModuleCatalog` (isi spec), `PresetNodeSeeds`, `IndustryStageTemplates`, provider telemetri per
  modul konveksi, layar modul konveksi.

**Penjaga**: tambahkan pemeriksaan ke `scripts/audit-variability.sh` — file mesin yang mengimpor
`GarmentBusinessPreset`/`PipelineStage`/`ModuleArchetype` dilaporkan.

---

## 3. Jalur A — ERP Konveksi (repo ini)

Urutan disarankan; tiap butir satu PR.

| # | Pekerjaan | Migrasi | Catatan |
|---|---|---|---|
| A1 | Header kanvas dari data tenant (nama pabrik, bukan "PT WeMade Garmen Ekspor · FOB"); ringkasan atas tidak menjumlah angka estimasi; badge health dari estimasi diredupkan | – | Bug yang terlihat di kanvas sekarang |
| A2 | **6a**: node aktif = entitlement + aktivasi tenant (preset hanya saran onboarding) | – | |
| A3 | **6b**: kontrak `EdgeFlowProvider` (terkirim/menunggu per panah) + 6 panah siap: CRM→Sampling, Tech Pack→HPP, MRP→Operator, Operator→QC, QC→Fulfillment, Sampling→Tech Pack (sementara lewat nomor SPK). Panah lain "belum terlacak". **Prototipe 1 panah dulu, dilihat mata, baru semua.** | – | Label panah butuh ruang antar kolom |
| A4 | **6c**: kolom rujukan `tech_packs.source_sampling_order_id`, `bulk_work_orders.costing_sheet_id`, `costing_sheets.sampling_order_id` (CMT) | 3 kolom aditif | Buka panah #2, #7, #11 |
| A5 | Kolom kanvas jadi data: `phase` pada spec modul (default dari archetype); pindahkan Sampling ke fase pengembangan produk | – | Prasyarat modul kustom buatan AI |
| A6 | **Blueprint konveksi**: `PipelineBlueprint` (modul katalog, modul kustom + port wajib, template industri, parameter) + validator port + pratinjau kanvas; perbarui `ProposedFlowValidator` (KDoc-nya basi sejak Fase 2) | – | Format netral (§2) |
| A7 | **AI agent konveksi**: LLM mengisi `FlowTranslationDraft`/Blueprint menggantikan `KeywordFlowTranslator`; keluaran = Blueprint + daftar slot kosong; tenant menyetujui sebelum disimpan | – | Keluaran model = input tak tepercaya |
| A8 | Utang: provider telemetri modul lain, resolusi node plugin kustom, skema id node, FK restore-presets, GET locations fail-open, editor tahap terlihat oleh yang tak berwenang | sebagian | Dicicil |

Gudang (permintaan & alokasi bahan, panah #4–6) = **fitur baru**, butuh discovery sendiri; di luar rencana ini.

---

## 4. Jalur B — Platform Alur General (repo baru)

Strangler Fig per enum (pola TRD-FLOW-001), **dari yang paling dangkal ke yang paling berisiko**.
Setiap tahap: struktur data baru di samping enum → test paritas yang mengiterasi enum → pindahkan pembaca
per paket → enum dihapus.

| # | Tahap | Ukuran | Keluaran |
|---|---|---|---|
| B0 | **Domain Pack** sebagai konsep: `DomainPack(code, phases, slots, portTypes, stageTemplates, modules)`; konveksi = `garment` pack yang dibangun **dari enum lama** (identik) | kecil | Paritas 100% dengan enum |
| B1 | `PipelineStage` → `PhaseCode` dari pack | 11 file | Kolom kanvas dari pack |
| B2 | Tipe port → `PortType` dari pack (`PortDataTypeRegistry` per pack) | sedang | |
| B3 | `ModuleArchetype` → `SlotCode` dari pack | 63 file | |
| B4 | `GarmentBusinessPreset` → preset = Blueprint milik pack | 64 file | |
| B5 | **Test keamanan dulu**: setiap modul terdaftar wajib punya gerbang; route tanpa gerbang gagal (perluas `RouteOwnershipTest`) | kecil | Prasyarat B6 — wajib hijau sebelum B6 dimulai |
| B6 | `BusinessModule` → `ModuleId` dari pack; RBAC/entitlement/menu membaca pack; migrasi JSONB entitlement & `custom_roles` dengan test "tidak ada pengguna yang kehilangan/mendapat akses" | 97 file, 20 migrasi | **Risiko tertinggi**, dikerjakan terakhir, beberapa PR. **Alarm `AccessSnapshotB6Test` opt-in** (`RUN_ACCESS_SNAPSHOT=1`, tidak jalan di `:server:test` biasa): wajib dijalankan manual terhadap salinan data nyata sebelum merge tiap tahap B6c–B6f; snapshot baru dibuat di awal B6c. Prosedur di KDoc tes |
| B7 | **Vertikal kedua nyata** (e-learning atau klien pertama yang datang): pack kedua + modul minimum + AI agent lintas pack | tergantung | Bukti abstraksi benar |

**Aturan Jalur B**
- Setiap tahap harus lulus test paritas: tenant konveksi di B berperilaku **identik** dengan A.
- Cherry-pick dari A hanya file mesin (§2), dicatat di `docs/plannings/sync-log.md` (commit A → commit B).
- Tidak ada fitur konveksi baru ditulis pertama kali di B.

---

## 5. Kriteria Konvergensi (kapan A pindah ke B)

Diputuskan ulang saat **semua** terpenuhi:
1. B6 selesai dan paritas konveksi A = B pada seluruh test + cek visual tenant rajut & bordir.
2. Ada minimal **satu vertikal kedua** berjalan di B dengan klien nyata (bukan demo).
3. Migrasi data tenant A → B teruji pada salinan DB produksi (entitlement, peran, SPK beku).

Bila belum terpenuhi, A tetap produk utama — B tetap eksperimen yang boleh dihentikan tanpa merugikan klien.

---

## 6. Risiko Dua Jalur & Mitigasinya

| Risiko | Mitigasi |
|---|---|
| Kedua repo melenceng; perbaikan di A tidak sampai ke B | Batas mesin (§2) + `sync-log.md`; cherry-pick rutin per PR mesin |
| Bug keamanan diperbaiki di A tapi lupa di B | Setiap PR governance di A diberi label `port-to-general`; B tidak rilis ke klien sebelum label kosong |
| Rules AI saling bertentangan antar repo | Langkah 0.5: rules repo B menyatakan migrasi enum sebagai tujuan |
| Tenaga terpecah dua | A = prioritas klien; B dikerjakan per tahap kecil, boleh dijeda di antara tahap mana pun |
| Abstraksi B salah bentuk karena baru satu vertikal | B7 (vertikal kedua nyata) sebelum menganggap B stabil; konvergensi (§5) bergantung padanya |

---

## 7. Langkah Berikutnya (minggu ini)

1. **[Anda]** Putuskan & jalankan Langkah 0 (commit, tag, fork).
2. **[Jalur A]** Mulai A1 → A3 (prototipe satu panah dulu).
3. **[Jalur B]** Mulai B0 di repo baru — dimulai dengan Discovery Note (`wemade-feature-discovery`) untuk konsep Domain Pack.
