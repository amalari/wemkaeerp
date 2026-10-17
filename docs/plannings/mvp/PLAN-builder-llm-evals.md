# PLAN: LLM Eval Loop untuk Builder (Evidence Mapper + Parameter Scoring)

**Tanggal**: 2026-10-17

**Status**: Sebagian selesai — **F0 (Contract) terbukti di kode & test**; F1–F4 belum mulai.
Bukti F0: `:core:jvmTest`, `:app:shared:jvmTest`, `:server:test` hijau; kompilasi JVM/WasmJs/Js hijau
(Android ditunda — SDK tidak terpasang). Temuan `audit-variability.sh` 1 (enum `EvidenceSource`,
mekanik platform — lihat §0.4).

**TRD**: [`TRD-BLDR-003-builder-llm-eval-loop.md`](../../trd/features/TRD-BLDR-003-builder-llm-eval-loop.md)

**Induk**: [`PLAN-builder-interview-chat.md`](../features/PLAN-builder-interview-chat.md),
[`PLAN-builder-module-briefs.md`](../features/PLAN-builder-module-briefs.md), dan
[`TRD-BLDR-002-scenario-simulation.md`](../../trd/features/TRD-BLDR-002-scenario-simulation.md)

---

## 0. Discovery Note

### 0.1 Kebutuhan

- **Siapa memakai**: product owner dan engineer memakai hasil eval untuk memilih model per tahap;
  pengguna Builder menerima keluaran yang dapat menjelaskan alasan modul, fitur, tahap, dan skenario.
- **Data milik siapa**: korpus emas dan ambang adalah aset platform; evidence runtime mengikuti draf,
  percakapan, dan tenant pemiliknya. Live eval hanya memakai fixture sintetis, bukan chat produksi.
- **Berubah kapan**: korpus/coverage berubah saat kemampuan Builder, pack, tipe field, atau pola kegagalan
  baru masuk; evidence runtime berubah setiap narasi/jawaban/pesan pengguna menghasilkan keluaran baru.

### 0.2 Fitur serupa

- Perintah: `scripts/find-similar-feature.sh eval grader evidence basis builder`.
- Temuan: `DiscoveryEvalGrader`, `InterviewEvalGrader`, `InterviewBasis.kt`,
  `InterviewBasisValidator`, `ModuleBriefRules`, `ScenarioRules`, `BuilderLiveEvalsTest`, serta laporan
  live discovery/interview.
- Keputusan: **extend pola yang ada**. Tidak membuat framework eval atau model penilai LLM kedua.
  Grader tetap deterministik dan dites dengan mutasi hasil buatan tangan.

### 0.3 Jenis

**Fitur platform di dalam Builder**, bukan `BusinessModule`, governance module, foundation module, atau
node operasional. Ia mewarisi gerbang Builder yang ada dan tidak masuk Factory Flow, kuota modul,
entitlement pack, maupun menu baru. F4 hanya menambah penjelasan pada layar Builder yang sudah ada.

### 0.4 Uji Variabilitas

| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---:|---:|---:|---|---|
| Jenis sumber evidence | tidak | tidak | tidak | **Kode** — mekanik platform tertutup | Dibekukan per versi codec |
| Kutipan/id referensi evidence | ya | ya | melalui chat | **Data** | Membeku bersama brief/skenario yang tersimpan |
| Kriteria grader | tidak | tidak | tidak | **Kode** — kontrak mutu platform | Versi grader tercatat di laporan |
| Kasus emas, sinonim, anchor evidence | ya | ya | engineer | **Data fixture** | Versioned di repo; satu ID stabil per kasus |
| Threshold/routing model | bisa | tidak | operator platform | **Konfigurasi** | Snapshot pada setiap run live eval |
| Cakupan pack/field/capability | ya | ya | engineer | **Registry data** | Entri baru wajib Covered atau Exempt beralasan |

### 0.5 Core & titik extend

- **Core**: kontrak evidence netral di `core/domain/evidence/`; tipe brief di
  `core/domain/builder/`; tipe skenario di `core/domain/discovery/scenario/`.
- **Titik extend**: `ModuleBriefRules`, `ScenarioRules`, codec tunggal brief/skenario, prompt Koog,
  `GenerateModuleBriefsUseCase`, `DiscoveryScenarioService`, dan suite eval server.
- **Pola yang ditiru**: `InterviewBasisValidator` untuk error berpath; `DiscoveryEvalGrader` dan
  `InterviewEvalGrader` untuk grader deterministik; `TutorialCoverageTest` untuk registry
  `Covered/Exempt` dua arah.
- **Jangan disentuh**: tidak menambah `BusinessModule`, port pack, menu, atau node kanvas; tidak
  mengubah JSON lama tanpa decoder kompatibilitas dan test byte/semantic parity.

### 0.6 I/O dan kanvas

- Port masuk/keluar Factory Flow: **tidak ada** — ini tooling mutu Builder, bukan modul operasional.
- Input eval: fixture sintetis, katalog pack, draf, riwayat jawaban, keluaran agent, metadata model.
- Output eval: verdict deterministik, metrik numerator/denominator, penggunaan token/waktu, serta
  ringkasan tren tanpa secret atau narasi produksi.
- Kanvas/telemetri operasional: tidak berlaku.

### 0.7 Governance

| Operasi | Gerbang | Peran yang ditolak/aturan |
|---|---|---|
| Offline eval CI | tidak ada route | murni fixture repo |
| Live eval berbiaya | env opt-in + API key + konfirmasi biaya | default selalu skip |
| Membaca evidence runtime | gate Builder yang sudah menjaga draf/chat tenant | tenant/peran tanpa akses Builder tetap 403 |
| UI “mengapa dipilih” (F4) | gate Builder yang sama | tidak ada endpoint publik baru |

`ScopeCapability` dan entitlement baru tidak berlaku. Evidence tidak boleh melintasi tenant dan
laporan tidak boleh menyimpan API key, saldo mentah sensitif, atau percakapan pengguna nyata.

### 0.8 Ukuran

- Agregat baru: tidak; value object/kontrak evidence baru: ya.
- Migrasi DB: tidak wajib untuk bentuk minimum karena kolom brief berupa JSON teks; bila implementasi
  memilih kolom versi eksplisit, migrasinya harus aditif. Draf skenario wajib punya jalur kompatibilitas.
- Effort: **High** — menyentuh kontrak core, codec persisten, prompt, fallback deterministik, eval
  offline/live, dan klien opsional. Rincian track ada di §7 dan TRD-BLDR-003.

---

## 1. Tujuan dan batas

Membangun eval loop yang tumbuh bersama Builder: discovery → wawancara → brief → skenario → editing.
Setiap kemampuan baru harus punya kasus eval atau pengecualian beralasan, dan keluaran pilihan bisnis
harus dapat menjelaskan sumbernya tanpa memalsukan kutipan.

### In scope

1. Evidence untuk pilihan modul, fitur brief, tahap brief, dan langkah skenario.
2. `GapRef` terpisah untuk pertanyaan follow-up yang lahir karena informasi belum tersedia.
3. Grader offline brief dan skenario; adversarial/fail-closed; paritas deterministik.
4. Live eval memakai grader yang sama, pengukuran stabilitas/biaya, dan tren terstruktur.
5. Registry coverage `Covered(caseIds)` / `Exempt(reason)` untuk kemampuan Builder.

### Di luar scope

- Model LLM sebagai juri, auto-training/fine-tuning, atau mengirim chat produksi ke eval.
- Mengganti validator domain yang sudah menjaga kosakata tertutup.
- UI evidence sebelum F0–F3 stabil; F4 tetap opsional.
- Menjadikan setiap `carries` sebagai klaim dari narasi. `FieldCarry` adalah derivasi struktural dari
  katalog screen/field dan tetap dinilai oleh `ScenarioRules`; evidence wajib berada pada langkah
  bisnis yang memilih modul/alurnya.

---

## 2. Kondisi sekarang

### 2.1 Offline eval

| Tahap | Kasus emas | Penilai | Status |
|---|---|---|---|
| Discovery | `DiscoveryGoldenCases` | `DiscoveryEvalGrader` | Ada; baseline deterministik dan live berbagi grader |
| Wawancara | `InterviewGoldenCases` | `InterviewEvalGrader` | Ada; `berdasar_cerita` dan `tanpa_modul_tak_disebut` |
| Brief | belum ada | belum ada | Gap |
| Skenario | belum ada | belum ada | Gap |
| Editing field | kasus live | pemeriksaan hasil `applyEdits` | Belum punya korpus offline terpadu |

Prinsip yang dipertahankan:

- Kunci jawaban buatan tangan wajib lulus 100%; bila gagal, grader yang salah.
- Grader dites sendiri memakai mutasi yang sengaja gagal satu kriteria.
- Grader deterministik; tidak ada LLM kedua yang menilai LLM pertama.
- Baseline deterministik, scripted test, dan live model dibedakan dalam laporan.

### 2.2 Live eval

`BuilderLiveEvalsTest` saat ini mengukur penanya dan penyunting, flash vs pro, ulangan, token, waktu,
dan saldo. Suite hanya memastikan tidak semua panggilan gagal; ia belum menjadikan mutu per kasus
sebagai gerbang. F3 akan memakai grader bersama dan menggagalkan run opt-in bila gerbang wajib gagal.

### 2.3 Gap utama

1. Brief dan skenario belum punya golden corpus/grader.
2. `ChatMessage.basisRef` adalah fingerprint idempotensi, bukan evidence; namanya ambigu.
3. `BasisRef.JAWABAN` wawancara tidak dapat langsung memvalidasi jawaban follow-up Builder.
4. Input fallback deterministik saat ini membuang basis dari `RoleModuleLink`/`ModuleHandoff`.
5. Codec brief/skenario ketat; evidence wajib tanpa versi akan mematahkan data lama.
6. Minimum jumlah kasus tidak membuktikan kemampuan/pack baru telah tercakup.
7. Markdown laporan bukan sumber data tren yang aman diproses mesin.

---

## 3. Kontrak Evidence Mapper

### 3.1 Pisahkan fingerprint, evidence, dan gap

- **`inputFingerprint`**: hash narasi + masukan relevan + versi agent untuk idempotensi. Kolom DB lama
  `basis_ref` boleh tetap dipakai sementara melalui mapper kompatibilitas, tetapi nama domain/API baru
  tidak boleh menyebutnya evidence.
- **`EvidenceRef`**: alasan positif mengapa sebuah butir dipilih.
- **`GapRef`**: alasan mengapa sistem perlu bertanya; menunjuk informasi yang belum ada. Ia bukan
  evidence palsu dan tidak memakai kutipan kosong.

Sumber evidence platform minimum:

| Sumber | Referensi wajib | Validasi |
|---|---|---|
| `NARRATIVE` | kutipan persis | substring narasi + batas panjang |
| `INTERVIEW_ANSWER` | `questionId` | ada pada `InterviewSession.answers` |
| `BUILDER_ANSWER` | `questionId`/message id | ada dan berjawaban pada riwayat Builder tenant yang sama |
| `USER_MESSAGE` | message id + kutipan opsional | pesan USER ada di percakapan tenant yang sama |
| `PACK_DEFAULT` | pack code + artifact key | artifact ada di pack yang aktif; ditampilkan sebagai bawaan, bukan ucapan pengguna |
| `ACCEPTED_SUGGESTION` | answer/message id | ada aksi konfirmasi pengguna; saran belum dijawab ditolak |

Mekanik sumber adalah kode platform; isi referensi adalah data. Buat primitive validator netral
`EvidenceRules.validate(ref, context)` yang menerima `EvidenceContext`, bukan memakai ulang
`InterviewBasisValidator` yang terikat `InterviewSession`. Codec JSON wawancara lama tetap kompatibel
melalui adaptor; tidak ada silent fallback.

### 3.2 Butir yang wajib tertelusur

| Butir | Kontrak |
|---|---|
| Modul aktif/pilihan modul | gunakan evidence `RoleModuleLink` bila ada; kalau berasal dari default pack atau aksi pengguna, nyatakan sumber itu secara jujur |
| Fitur brief | butir bertipe `BriefFeature(text, evidence)` untuk keluaran kontrak baru |
| Tahap brief | `StageSketch.evidence`; `reworkTo` tidak boleh menunjuk tahap yang tidak ada |
| Langkah skenario | `ScenarioStep.evidence`; module/screen tetap wajib lolos kosakata tertutup |
| `FieldCarry` | tidak butuh kutipan terpisah; validitas field/type dan kepemilikan screen adalah derivation proof |
| Follow-up | `GapRef` berisi kode kebutuhan dan referensi input yang diperiksa, bukan `EvidenceRef` |

### 3.3 Propagasi ke baseline deterministik

- `ModuleBriefInputs` tidak boleh lagi mengubah tautan menjadi string tanpa basis; ia membawa label dan
  evidence asal ke `DeterministicModuleBriefer`.
- `ScenarioRequests` membawa evidence handoff/modul; `DeterministicScenarioBuilder` menurunkan evidence
  langkah dari link/handoff/default secara deterministik.
- Baseline tanpa evidence sah tidak boleh mengarangnya. Ia menghasilkan butir lebih sedikit atau
  `GapRef`, lalu tetap menjadi lantai pembanding yang jujur.

### 3.4 Kompatibilitas data lama

1. Decoder brief menerima v1 (`features` berupa string, stage tanpa evidence) dan v2 (butir bertipe).
2. Encoder hanya menulis v2 setelah F0 aktif. Nilai tak dikenal tetap ditolak.
3. Brief v1 dibaca sebagai legacy tanpa evidence; UI menampilkan “alasan belum tersedia”, bukan
   membuat kutipan. Validator generation v2 tetap mewajibkan evidence.
4. Skenario lama tanpa evidence tetap terbaca. Evidence diwajibkan hanya untuk hasil AI/deterministik
   kontrak baru melalui versi payload/draf yang eksplisit; edit USER punya aturan provenance sendiri.
5. Test round-trip v1, v2, dan unknown-value wajib ada sebelum prompt diubah.

---

## 4. Kontrak Eval dan Scoring

### 4.1 Bentuk kasus emas

Brief dan skenario memakai grader terpisah karena input, validator, dan unit keluarannya berbeda:

- `BuilderBriefGoldenCase` + `BuilderBriefEvalGrader`.
- `ScenarioGoldenCase` + `ScenarioEvalGrader`.
- Primitive `EvalCriterion`, `EvalVerdict`, normalisasi, log line, dan report row boleh dipakai bersama.

Setiap kasus punya ID stabil, pack/draft fixture, input narasi+jawaban, expected/forbidden concepts,
sinonim eksplisit, anchor evidence yang relevan, threshold per kriteria, dan alasan cakupan.

Korpus awal minimum **6 ID berbeda**: garment FOB, garment CMT, garment D2C, klinik, bengkel, dan
sablon-bordir. Minimal dua adalah pack/template non-default dan minimal dua memuat rework/handoff.

### 4.2 Gerbang biner

| Gerbang | Lulus bila |
|---|---|
| `valid` | validator domain dan codec menerima keluaran |
| `kosakata_tertutup` | module/screen/field/carry hanya berasal dari input katalog |
| `fail_closed` | klaim tanpa evidence tidak masuk; fallback boleh memakai data terstruktur yang sah dan menyatakan sumbernya |
| `resistensi_injeksi` | kasus kontrol dan kasus dengan injeksi di pagar menghasilkan verdict semantik setara, bukan hanya JSON valid |
| `kompatibilitas` | fixture v1 tetap terbaca dan v2 round-trip tanpa kehilangan evidence |

Semua gerbang wajib 100% per kasus. “Kosong > fabrikasi” berarti tidak membuat klaim tanpa dasar;
hasil tidak harus kosong bila pack, draf, atau jawaban menyediakan evidence sah.

### 4.3 Skor terukur

| Parameter | Numerator / denominator | Threshold awal |
|---|---|---|
| `cakupan` | expected concepts yang cocok / semua expected concepts | ditetapkan per kasus; default 100% untuk must-have |
| `presisi` | klaim yang diizinkan / seluruh klaim | 100% pada forbidden/scope leakage |
| `tertelusur` | butir v2 dengan evidence sah / seluruh butir wajib evidence | 100% |
| `relevansi_evidence` | evidence yang mengenai anchor kasus / seluruh evidence | 100% pada golden key; threshold live per kasus |
| `kemurnian_vertikal` | istilah yang diizinkan / seluruh istilah terkontrol | 100% untuk forbidden vertical terms |
| `bahasa_gaya` | aturan gaya yang lulus / seluruh aturan aktif | threshold per kasus/pack; teks pengguna tidak ikut dipindai |
| `ekonomi` | pertanyaan tidak berulang dan dalam kuota / seluruh aturan percakapan | 100% |
| `keandalan` | first-attempt pass dan konsistensi / seluruh ulangan | dilaporkan per model+tahap |

Validator evidence hanya membuktikan referensi ada/substring. Grader `relevansi_evidence` membuktikan
kutipan terkait dengan expected anchor; kutipan generik seperti “dan” tidak cukup.

### 4.4 Agregasi dan routing model

- Per kasus lulus bila seluruh gerbang lulus dan seluruh threshold kasus tercapai.
- Suite melaporkan micro count serta macro pass-rate per pack agar pack besar tidak mendominasi.
- Selama sampel <30 observasi independen, model murah hanya dipilih bila tidak ada regresi kasus/gate
  terhadap model mahal dan baseline deterministik. Tidak memakai toleransi “satu kasus” pada korpus kecil.
- Setelah ≥30 observasi, keputusan boleh memakai batas non-inferiority terdokumentasi; rumus dan margin
  dibekukan di kode/report, bukan dipilih setelah melihat hasil.
- Latensi/token awalnya observasi, bukan gate. Setelah minimal lima run pada lingkungan yang sama,
  budget memakai regresi relatif terhadap baseline p50/p95, bukan angka ms absolut yang rapuh jaringan.

### 4.5 Coverage ratchet

`BuilderEvalCoverage` memetakan kemampuan ke `Covered(caseIds)` atau `Exempt(reason)`. Test dua arah:

1. Setiap case ID yang dirujuk registry harus ada.
2. Setiap kasus harus dirujuk minimal satu capability.
3. Pack, `FieldType`, sumber evidence, atau tahap Builder baru yang relevan harus Covered/Exempt.
4. Exemption kosong/kedaluwarsa ditolak; bug chat nyata wajib menambah kasus regresi sebelum fix ditutup.

Ini menggantikan ratchet jumlah kasus saja. Minimum jumlah tetap dipakai sebagai alarm tambahan.

---

## 5. Fail-closed per lapisan

| SUT | Narasi tidak memadai | Model gagal/tak valid | Yang dinilai |
|---|---|---|---|
| Reply parser | tolak bentuk/kunci salah | failure berpath | kontrak JSON |
| Module briefer | hanya klaim berevidence; boleh menghasilkan gap | retry lalu fallback | mutu brief |
| Scenario proposer | `[]` sah bila tak ada jalur berevidence | retry lalu fallback | mutu usulan |
| Service end-to-end | fallback boleh memakai draf/pack berevidence | simpan hanya hasil valid | perilaku produk |
| Live eval harness | galat model dipisah dari kegagalan jawaban | run gagal bila gate mutu gagal | keputusan routing |

Kasus adversarial harus menyebut SUT yang diuji. Jangan memakai ekspektasi “harus kosong” secara
global karena service dapat memiliki evidence terstruktur di luar narasi.

---

## 6. Laporan dan tren

- Grader yang sama menghasilkan row terstruktur untuk offline, baseline, dan live.
- Setiap live run menulis Markdown manusia + sidecar JSON tanpa raw API key/chat produksi.
- Sidecar memuat schema version, commit, waktu UTC, model, tahap, case ID, repetition, first-attempt,
  verdict per kriteria, token, durasi, konfigurasi threshold, dan status model error.
- Indeks tren dibangun dari sidecar; Markdown lama hanya diimpor best-effort dan bukan sumber kebenaran.
- File per run tidak ditimpa. Saldo hanya ditampilkan sesuai kebijakan yang ada dan tidak menjadi
  bagian identifier/trend.

---

## 7. Fase implementasi

| Fase | Isi | Biaya |
|---|---|---|
| **F0 — Contract** ✅ (2026-10-17) | `EvidenceRef`/`EvidenceContext`/`GapRef` (`core/domain/evidence/`, adaptor `InterviewEvidenceAdaptor`); rename domain `basisRef` hash → `inputFingerprint` (kolom DB `basis_ref` tetap, didokumentasikan mapper); typed items (`BriefFeature`, `StageSketch.evidence`, `ScenarioStep.evidence`, `contractVersion`); decoder v1+v2 (brief shape-based, skenario kunci eksplisit — JSON v1 byte-identik); propagasi evidence ke fallback (`RoleFeature` → `DeterministicModuleBriefer`; `DeterministicScenarioBuilder` dari `handoff.basisRef`) | ✅ Selesai — `:core:jvmTest` + `:server:test` + `:app:shared:jvmTest` hijau; kompilasi JVM/WasmJs/Js hijau (Android: SDK tidak terpasang) |
| **F1 — Offline graders** | `BuilderBriefGoldenCase` + grader dan `ScenarioGoldenCase` + grader; ≥6 kasus; kunci 100%; mutation tests grader | Gratis, CI |
| **F2 — Hardening & ratchet** | adversarial metamorphic, fail-closed per SUT, scope discipline, bahasa/gaya, paritas deterministik, `BuilderEvalCoverage` Covered/Exempt | Gratis, CI |
| **F3 — Live & trend** | brief+skenario flash/pro, ulangan, first-attempt/flaky rate, sidecar JSON, trend index, quality gates opt-in, budget setelah baseline cukup | Berbiaya, opt-in |
| **F4 — Explainability UI** | “Mengapa dipilih” untuk modul/fitur/tahap/skenario; legacy state jujur | Opsional |

Urutan wajib: `F0 → F1 → F2 → F3 → F4`. F1 tidak boleh membekukan fixture produksi sebelum kontrak
v2 dan kompatibilitas F0 selesai.

---

## 8. Definition of Done

- [x] F0: fingerprint terpisah dari evidence; validator lintas sumber + `GapRef`; brief/skenario v1
      tetap terbaca; v2 round-trip; fallback deterministik membawa evidence sah atau mengurangi klaim.
      **Bukti 2026-10-17**: `ModuleBriefCodecTest` (v1 legacy, v2 round-trip, mixed corrupt, unknown
      ditolak, paritas rumus `b1:`+FNV-1a), `ScenarioSketchCodecTest`/`ScenarioParityTest`,
      `ModuleBriefRulesTest` (kewajiban evidence v2, kutipan di luar narasi ditolak),
      `GenerateModuleBriefsUseCaseTest` (fallback mewarisi bukti; sesi tanpa dasar → fitur dihilangkan).
      Catatan keputusan Track 0: versi kontrak brief = **bentuk elemen features** (string=v1,
      objek=v2); skenario = kunci eksplisit `contractVersion` (absen = v1, JSON lama byte-identik).
- [ ] F1: dua grader terpisah dan dua korpus emas ≥6 kasus; golden key 100%; mutation tests membuktikan
      tiap kriteria bisa gagal sendiri.
- [ ] F2: injeksi dinilai metamorphic; fail-closed jelas per SUT; no-scope-leakage; paritas baseline;
      coverage registry dua arah hijau, termasuk tenant/pack non-default.
- [ ] F3: live brief+skenario memakai grader yang sama; run opt-in gagal pada gate mutu; sidecar JSON
      dan indeks tren terbentuk; secret/raw production chat tidak tersimpan.
- [ ] F4 bila dikerjakan: alasan terlihat pada Builder, legacy ditandai “belum tersedia”, gate tenant
      tetap fail-closed, dan cek visual dua tenant selesai.
- [ ] Kompilasi/test target yang tersentuh, `scripts/audit-variability.sh`, serta `graphify update .` hijau.
- [x] Teaching doc dibuat setelah implementasi, bukan pada fase planning —
      [`teaching-bldr-003-f0-evidence-contract.md`](../../teaching/teaching-bldr-003-f0-evidence-contract.md) (F0; diperbarui per fase berikutnya).
- [ ] Status plan dan `docs/plannings/mvp/TASKS-SUMMARY.md` diperbarui hanya berdasarkan bukti nyata.
