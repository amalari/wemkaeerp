# PLAN: Pertanyaan Tingkat Skenario di Utas Semua (SC3 / TRD-BLDR-002 FR-9)

**Tanggal**: 2026-10-17
**Status**: Disetujui 2026-10-18 (D1–D3 sesuai rekomendasi) — mulai Track 0
**TRD**: [`TRD-BLDR-002-scenario-simulation.md`](../../trd/features/TRD-BLDR-002-scenario-simulation.md) FR-9
**Induk**: [`PLAN-builder-module-briefs.md`](PLAN-builder-module-briefs.md) (butir SC3)

---

## 1. Kebutuhan
- **Siapa memakai**: pemilik/admin tenant yang menyusun sistem lewat chat Builder dan meninjau skenario usulan.
- **Data milik**: tenant (utas chat tenant + draf kerja tenant).
- **Berubah kapan**: tiap kali pengusul skenario menghasilkan skenario baru (per proposal, bukan sekali onboarding).

Masalah: model kadang ragu pada urutan atau syarat antar-modul ("DP harus lunas dulu sebelum sampel dikerjakan?"). Hari ini ia hanya bisa menebak, atau memilih modul terdekat. FR-9 memberi jalur bertanya: tiap skenario usulan boleh membawa **paling banyak 2 pertanyaan** di utas **Semua**, memakai mekanik follow-up yang sudah ada, dan badge utas Semua ikut menghitungnya.

## 2. Fitur serupa
- Graphify dulu (`pendingFollowUps`, `Clarification`, `QUESTION`, `threadsOf`), lalu `scripts/find-similar-feature.sh scenario pertanyaan`.
- **Mirip → tiru pola follow-up per modul** (TRD-BLDR-001): `GenerateModuleBriefsUseCase.followUps` (`core/.../domain/builder/GenerateModuleBriefsUseCase.kt:153`), `BriefKeys.followUpId`/`isAiQuestionId` (`ModuleBriefContract.kt:38-45`), `pendingFollowUps` (`BuilderThreads.kt:27`).
- Fakta terverifikasi di kode:
  1. `pendingFollowUps(null)` memuat **semua** pesan QUESTION (`inThread(null)` = tanpa filter) → pesan skenario berutas `moduleId = null` **otomatis** ikut badge Semua dan **tidak** masuk badge modul mana pun. Syarat "badge ikut" tidak butuh perubahan pada fungsi ini.
  2. Jawaban sudah kembali ke model: `composeNarrative` menyertakan "Pertanyaan/Jawaban" dari pesan QUESTION yang terjawab, dan `narrative` itulah yang dibaca pengusul skenario (`BuilderRoutes.kt`, lambda `narrative`).
  3. `DiscoveryScenarioService` **tidak punya akses chat** (KDoc kelasnya, sengaja) dan signature konstruktornya dibekukan. Jadi layanan ini tidak boleh menulis pesan.
- Keputusan: **Mirip → tiru pola brief**; bagian baru hanya penghubung skenario ke pesan QUESTION.

## 3. Jenis
**Fitur dalam Builder (platform), bukan modul `BusinessModule`.** Builder bukan modul yang dijual atau dihitung kuota; tidak ada `BusinessModule`/entitlement/katalog baru. Mewarisi gerbang rute chat/skenario yang sudah ada. Menu §5.7: **tidak ada layar/menu baru** (`Hidden`: pertanyaan tampil sebagai bubble di panel chat yang sudah ada). Pertanyaan "dipakai tenant lain?": tidak relevan (bukan modul).

## 4. Uji Variabilitas
| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---|---|---|---|---|
| Teks pertanyaan | ya | ya | tidak (dijawab, bukan diedit) | **Data** (keluaran model dari narasi tenant) | — |
| Batas ≤ 2 per skenario | tidak | tidak | tidak | **Kode** (`ScenarioLimits`, konstanta platform) | — |
| Awalan id `scn:` | tidak | tidak | tidak | **Kode** (kunci mekanik platform; pola `ai:`/`gap:`) | — |
| Status terjawab/menunggu | per pesan | — | pengguna menjawab | **Data** (`Clarification.answer`) | — |

Tidak ada `enum`/`when` domain baru.

## 5. Core & extend
- **Core**: paket `core/.../domain/discovery/scenario` (kontrak skenario) dan `core/.../domain/builder` (pesan/utas).
- **Titik extend**:
  - `ScenarioSketch` — field baru `questions: List<String> = emptyList()` (default kosong → draf & codec lama terbaca tanpa transformasi; Kontrak 4 variabilitas: nilai tak sah ditolak, bukan dibuang senyap).
  - `ScenarioLimits` — `QUESTIONS_MAX = 2`; `ScenarioRules` — validasi jumlah, tak kosong, panjang.
  - `ScenarioSketchCodec` — baca/tulis `questions` opsional.
  - `ScenarioKeys` (baru, kecil) — `questionId(scenarioId, question) = "scn:<id>:<fp>"` memakai fingerprint yang sama dengan `BriefKeys`.
  - Use case baru `AskScenarioQuestionsUseCase(chats)` — membuat pesan QUESTION utas Semua dari skenario yang **baru ditambahkan**, idempoten (dedup per id dan per teks ternormalisasi terhadap utas).
  - `KoogScenarioProposer` + `ScenarioReplyParser` + prompt — model boleh mengisi `questions`; parser menolak lebih dari 2 (tanpa fallback senyap).
  - `BuilderScenarioRoutes` propose — setelah `svc.propose`, panggil use case untuk `list`.
- **Contoh yang ditiru**: `GenerateModuleBriefsUseCase.kt:153` (`followUps`: batas, dedup, id stabil); `BriefKeys.kt` (id + fingerprint); `FollowUpMessageIdTest` (id harus unik: id pesan memuat id skenario, jangan nomor urut saja — pelajaran tabrakan PK).
- **Jangan disentuh**: signature `DiscoveryScenarioService` (dibekukan); `pendingFollowUps` (sudah benar untuk utas Semua); `DeterministicScenarioBuilder` (fallback tidak mengarang pertanyaan); `BuilderChatPane.kt` (363 baris, batas 400, Ratchet).

## 6. I/O & kanvas
- Masuk: skenario usulan model (+ `questions`). Keluar: pesan QUESTION utas Semua. Tidak ada port pack/kanvas baru; tidak ada node kanvas.
- Telemetri: belum ada. Pantau lewat log `scenario_propose` (tambahkan `questions=<n>`), tanpa isi pertanyaan di log.

## 7. Governance
| Operasi | Level minimum | Peran yang ditolak (dites 403) |
|---|---|---|
| Usulkan skenario (menulis pesan pertanyaan) | sama dengan `POST /draft/scenarios/propose` hari ini | peran tanpa akses Builder → 403 **dan nol pesan QUESTION tercipta** |
| Jawab pertanyaan | sama dengan menjawab follow-up chat | idem |
- Gate: gerbang rute Builder yang sudah ada · `ScopeCapability`: tidak berlaku (bukan modul) · Entitlement: tidak ada.
- Fail-closed: pesan dibuat **hanya setelah** gerbang lolos dan propose sukses; draf `LOCKED` (423) tidak membuat pesan.

## 8. Ukuran, Effort & Rencana Track
- **Agregat baru**: tidak ada (field baru + 1 use case + 1 helper kunci) · **Migrasi DB**: **tidak ada** (`chat_messages` dan dokumen draf jsonb sudah menampung) · **TRD perlu**: Tidak (FR-9 sudah ada di TRD-BLDR-002; cukup catat jawaban D1–D3).
- **Estimasi Effort**: Medium-rendah. **Reasoning Effort**: logika sederhana, ±8 file; risiko ada di kontrak bersama (`ScenarioSketch` dipakai codec, validator, klien) dan perilaku model.
- **Ukuran file**: `DiscoveryScenarioService.kt` 178 (soft 250), `ScenarioSketchCodec.kt` 136, `BuilderScenarioRoutes.kt` 121, `ScenarioSketch.kt` 92 — aman. Use case baru ≤ 100 baris.
- **Rencana Track**:
  - **Track 0 (kontrak, serial)**: `ScenarioSketch.questions`, `ScenarioLimits.QUESTIONS_MAX`, `ScenarioKeys`, codec opsional; tes kontrak + round-trip draf lama.
  - **Track A (core)**: validator (jumlah/tak kosong/panjang), `AskScenarioQuestionsUseCase` + tes (id unik multi-skenario via `StrictChats`, idempoten, ≤2, template non-default/tenant kedua klinik).
  - **Track B (server)**: parser + prompt pengusul (`questions` opsional), pemasangan di route propose, log `questions=`; tes route 403/423 → nol pesan.
  - **Track C (klien)**: kemungkinan **tanpa perubahan** — bubble QUESTION generik sudah ada; periksa teks bubble menyebut judul skenario (lihat D3).
  - **Track D (verifikasi)**: model sungguhan: proposal menghasilkan ≤ 2 pertanyaan; badge Semua naik tepat sebanyak itu, badge modul tidak; jawab → narasi memuatnya; cek mata di `wemade-demo` dan tenant non-garment.

## 9. Keputusan terbuka (rekomendasi disertakan)
- **D1 — Tempat menyimpan pertanyaan.** (a) **di `ScenarioSketch.questions`** (disarankan: validator memeriksa, codec membawa, idempotensi bisa dibandingkan; perubahan paling kecil) vs (b) tipe hasil baru dari `ScenarioService.propose` (mengubah antarmuka dan semua pemakainya).
- **D2 — Setelah skenario dikonfirmasi, pertanyaan yang belum dijawab tetap dihitung?** Disarankan **tetap dihitung** pada v1: `pendingFollowUps` tetap murni dan sederhana; batasnya ≤ 2 per skenario × `SCENARIOS_MAX`. Alternatif (disembunyikan seperti pola "digantikan") butuh status skenario masuk ke fungsi utas — tunda sampai ada keluhan badge bising.
- **D3 — Teks bubble.** Disarankan pertanyaan diawali judul skenario ("Skenario 'Sampel Kaos Bordir': …") supaya jelas di utas Semua; id tetap `scn:<id>:<hash>`.

**Batas yang perlu diketahui sejak awal**: jawaban masuk ke `narrative`, jadi hanya mempengaruhi **proposal berikutnya**. Skenario yang sudah ada tidak berubah sendiri (penyimpanan skenario hanya menambah dan membuang urutan modul yang sama). Memberi jalur "usulkan ulang setelah dijawab" adalah pekerjaan terpisah, bukan SC3.

## 10. Checklist (diisi saat dikerjakan)
- [x] Keputusan pemilik D1–D3 (2026-10-18: ketiganya sesuai rekomendasi)
- [ ] T0 Kontrak: field + batas + `ScenarioKeys` + codec + tes draf lama
- [ ] A Validator + `AskScenarioQuestionsUseCase` + tes (id unik, idempoten, ≤2, tenant kedua)
- [ ] B Parser/prompt + route + tes 403/423 nol pesan
- [ ] C Klien (bila ada perubahan) + ukuran file
- [ ] D Model sungguhan + cek visual; kompilasi 4 target; `audit-variability.sh`
- [ ] Teaching doc diperbarui; `TASKS-SUMMARY.md` sinkron (§18)
