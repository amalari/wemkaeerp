# PLAN: Ringkasan & Follow-Up per Modul di Chat Builder

**Tanggal**: 2026-10-14
**Status**: Sebagian — Track 0/A/B/C selesai dan di `main` (2026-10-14); Track D (cek visual, teaching doc) belum. Flag server `BUILDER_MODULE_BRIEFS_ENABLED` default MATI
**TRD**: [`TRD-BLDR-001-module-briefs.md`](../../trd/features/TRD-BLDR-001-module-briefs.md) (Fase 1) ·
[`TRD-BLDR-002-scenario-simulation.md`](../../trd/features/TRD-BLDR-002-scenario-simulation.md) (Fase 2)
**Induk**: [`PLAN-builder-interview-chat.md`](PLAN-builder-interview-chat.md)

---

## 0. Discovery Note (ringkas)

| Pertanyaan | Jawaban |
|---|---|
| Kebutuhan bisnis | Pengguna cukup bercerita sekali; tiap modul otomatis punya ringkasan + fitur + pertanyaan lanjutan sendiri, dan riwayat chat sendiri |
| Fitur serupa sudah ada? | Sebagian (graphify gagal pada kueri pertama; dilanjut grep/baca file): utas per modul, `ModuleGapAnalyzer` (follow-up template), `EditModuleFromChat`, `InterviewPlanner` (`RoleModuleLink.features`). **Belum ada**: ringkasan AI per modul & follow-up kontekstual |
| Jenis | **Fitur di dalam modul/platform Builder** — bukan `BusinessModule` baru (mewarisi gerbang `MANAGE_BUILDER`) |
| Uji Variabilitas | Isi ringkasan = data tenant/pack. `ChatMessageKind.BRIEF` = mekanik platform → enum sah |
| Core & extend | `core/domain/builder` (port + use case), `KoogModuleEditor` sebagai pola, `BuilderChatRepository` |
| Posisi kanvas Factory Flow | Tidak ada (bukan modul operasional) |
| Governance | Gerbang Builder yang ada; tulis fail-closed + tes 403 |
| Menu (§5.7) | **Tidak ada menu baru** — hidup di layar `/builder/chat` yang sudah ada |

Perbaikan terkait yang **sudah dikerjakan** (2026-10-14, belum di-commit): modal follow-up hanya memuat pertanyaan utas aktif +
"Lewati" diingat + modal di-reset saat ganti utas; badge semua modul dihitung dari utas Semua dan follow-up modul
diseed saat draf termuat; ringkasan lokal `openingEntryForModule` untuk utas kosong
(`BuilderChatPane.kt`, `ChatModel.kt`). Plan ini menggantikan ringkasan lokal itu dengan hasil AI.

---

## 1. Keputusan Desain

1. **BRIEF = `ChatMessage` ber-`kind` baru**, bukan tabel baru (RLS, utas, API ikut gratis).
2. **Satu panggilan model per modul**, konkurensi 3, idempoten via `basisRef`.
3. **AI hanya mengusulkan teks**; validator domain memutuskan; gagal → fallback deterministik bertanda `source`.
4. **Follow-up AI + celah deterministik berbagi kuota 3**; follow-up yang sudah ditanyakan tidak diulang.
5. **Flag server** `builder.moduleBriefs.enabled` selama stabilisasi.

## 2. Track & Tugas

### Track 0 — Kontrak (serial, wajib selesai dulu)
- [x] T0.1 `ChatMessageKind.BRIEF`; field `features`, `stages` (`StageSketch`), `basisRef`, `briefSource` pada `ChatMessage` (+ `init` guard)
- [x] T0.2 `ModuleBrief`, `ModuleBriefRequest`, `ModuleBriefer` (port), `BriefSource`
- [x] T0.3 Bentuk JSON `BRIEF` dibekukan: `ModuleBriefCodec` (parser tunggal, tolak nilai tak dikenal) + fixture garment/non-garment sebagai konstanta Kotlin di `commonTest` (`BriefContractFixtures`; tidak memakai `resources`). DTO `POST /chat/briefs` mengikuti TRD §4 dan `ModuleBriefOutcome`
- [x] T0.5 Kontrak pendukung: `ModuleBriefValidator`, `ModuleBriefService` (pintu rute/pemicu; Track B memakai fake), `BriefKeys` (`followUpId`, `basisRef`, `fingerprint` FNV-1a 64 — rumus beku), `BriefIssue`, `ModuleBriefOutcome`
- [x] T0.4 Nomor migrasi dipastikan (`ls … | sort -V | tail -1`, kini V104 → V105; terverifikasi 2026-10-14)

### Track A — Core (paralel setelah T0)
- [x] A1 `ModuleBriefValidator` + tes tiap aturan (berpath)
- [x] A2 `DeterministicModuleBriefer` + tes (peran/tautan tak ada → aman)
- [x] A3 `GenerateModuleBriefsUseCase`: idempoten, basis berubah, retry 1x, fallback; tes dengan template **non-default**
- [x] A4 Gabung kuota follow-up AI + `ModuleGapAnalyzer` (maks 3, tanpa duplikat); `AskModuleFollowUpsUseCase` jadi pengisi sisa
- [x] A5 Tes paritas: iterasi `ChatMessageKind.entries` (tiap kind punya parser klien + kolom DB)

### Track B — Server (paralel)
- [x] B1 Migrasi aditif (kolom `features`, `basis_ref`, `brief_source`, constraint kind diperlebar, indeks)
- [x] B2 `PostgresBuilderChatRepository`: baca/tulis kolom baru; tes integrasi (pesan lama tetap terbaca)
- [x] B3 `KoogModuleBriefer` + prompt + parser JSON tunggal (tolak bentuk tak dikenal)
- [x] B4 `POST /api/builder/chat/briefs` (gate `MANAGE_BUILDER`, 403/400/409) + tes peran tak berwenang
- [x] B5 Pemicu otomatis di `SendBuilderMessageUseCase` setelah rencana wawancara; SSE `module_started/ready`
- [x] B6 Flag `builder.moduleBriefs.enabled`; log terstruktur + rasio fallback

### Track C — Klien (paralel)
- [x] C1 `ChatModel`: parse `BRIEF`, `features`, `briefSource`; tolak kind tak dikenal
- [x] C2 (kode + tes parser; **tampilan belum dilihat mata** → D1) `ChatMessageBubble` varian ringkasan (token clay, nol literal warna); versi lama dilipat
- [x] C3 Tombol "Segarkan ringkasan" memanggil `POST /briefs`
- [x] C4 Hapus ketergantungan `openingEntryForModule` kecuali untuk kosong-state saat flag off
- [x] C5 Jaga `BuilderChatPane.kt` ≤ 400 baris (kini 363) — pecah ke file bertema bila perlu (mis. `ChatFollowUpState`)

### Track D — Verifikasi (serial, akhir)
- [ ] D1 `wemade-demo` (superadmin demo) + `bordir-uji` ~1280dp: ringkasan & follow-up per modul, badge sejak awal
- [ ] D2 Skenario AI mati → brief `DETERMINISTIC` terlihat
- [ ] D3 Kompilasi 5 target + `jvmTest`; `scripts/audit-variability.sh`; `graphify update .`
- [ ] D4 Teaching doc (`docs/teaching/teaching-builder-module-briefs.md`) + tutup di `TASKS-SUMMARY.md` (§18)

## 2b. Fase 2 — Skenario & Simulasi (TRD-BLDR-002)

Keputusan pemilik 2026-10-14: **simulasi tampilan** (memori, tidak tersimpan). Mulai setelah Fase 1 Track 0 beku.
Verifikasi awal sudah dilakukan (lihat TRD-BLDR-002 §1): `PrototypeSession` sudah bersama untuk semua layar;
`TenantStageFlow` terikat sampling → Tahap di brief hanya sketsa.

- [ ] S0 Kontrak: `ScenarioSketch/Step/FieldCarry`, JSON, `ScenarioProposer`, fixture garment + non-garment
- [ ] SA1 `ScenarioValidator` (+tes tiap aturan) · SA2 `DeterministicScenarioBuilder` dari `ModuleHandoff`
- [ ] SA3 `ScenarioCarry` (pemetaan + koersi, murni) · SA4 use case propose/confirm/edit · SA5 codec `scenarios` opsional
- [ ] SB1 `KoogScenarioProposer` + parser tunggal · SB2 rute propose/confirm (403/422) · SB3 flag `builder.scenarios.enabled`
- [ ] SC1 `ScenarioRun` (file baru, bukan menambah `PrototypeSession`) · SC2 `ScenarioPicker`/`ScenarioStepper`
- [ ] SC3 Pertanyaan skenario di utas Semua (badge ikut)
- [ ] SD1 Cek visual `wemade-demo` + `bordir-uji` · SD2 nol HTTP saat simulasi · SD3 teaching doc + `TASKS-SUMMARY`

Estimasi Fase 2: ≈ 5–6 hari (**Medium–High**).

### Bukti penutupan Fase 1 (2026-10-14)
- Core: `ModuleBriefRulesTest`, `GenerateModuleBriefsUseCaseTest`, `ChatMessageKindParityTest`, `ModuleBriefContractTest`, `ModuleBriefCodecTest`, `BriefTriggerTest` (core jvmTest 2029+ hijau, JS/WasmJs terkompilasi).
- Server: `BuilderModuleBriefRoutesTest` (termasuk 403 peran SALES), `KoogModuleBrieferTest`; `PostgresBuilderChatBriefIntegrationTest` **dijalankan pada DB scratch**: V105 terterap, 4 kolom + constraint ada, 1 tes lulus (0 dilewati).
- Klien: `ChatBriefModelTest`, `ChatBriefOpeningTest`; kompilasi JVM/WasmJs/JS. `jvmTest` 1 merah **sudah ada sebelumnya**: `UiTextLatin1GuardTest` (karakter non-Latin-1 di `MoqupsWireframeCanvas.kt`, `InteractiveTable.kt`, `ModuleDetailPane.kt`, bukan dari fitur ini).
- Belum: Android target, cek visual (D1/D2), FR-10 progres SSE per modul (kontrak `generate` tanpa callback — hanya hasil akhir), tes `shownEntries` dengan draf sungguhan.

## 3. Urutan & Estimasi

Fase 1: `T0 (0,5 hari) → A ∥ B ∥ C (≈2 hari paralel) → D (0,5–1 hari)` ≈ 3–4 hari, **Medium**.
Fase 2 menyusul setelah Fase 1 (konteks brief dipakai pengusul skenario).

## 4. Risiko

| Risiko | Mitigasi |
|---|---|
| Mutu ringkasan/pertanyaan AI rendah | Validator ketat + evaluasi deterministik + metrik rasio fallback |
| Biaya model naik (N modul × tiap perubahan cerita) | Idempotensi `basisRef`; hanya regenerasi bila basis berubah; konkurensi 3 |
| Prompt-injection dari cerita | Narasi = data; keluaran dibatasi panjang/format; tidak menulis draf |
| Klien dan server tak sinkron (kind baru) | Dirilis bersamaan; parser klien menolak kind tak dikenal dengan pesan jelas |
| `BuilderChatPane` melewati batas ukuran | C5 |

## 5. Definisi Selesai

Semua AC di TRD §5 terpenuhi dan dicentang **hanya dengan bukti** (nama tes / cek visual); `TASKS-SUMMARY.md` diperbarui.
