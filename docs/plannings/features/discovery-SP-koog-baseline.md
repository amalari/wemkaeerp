# Discovery — Keluaran Koog & Baseline Evaluasi (SP C0)

**Agent:** C · **Tanggal:** 2026-10-05 · **Plan:** [PLAN-sp-C-koog.md](parallel3/PLAN-sp-C-koog.md) butir C0
**Basis kode:** `main` @ `d5c893e` (setelah merge B0: kontrak `ScreenProposal` + validator tunggal + codec draf).

> Dokumen keputusan, bukan tutorial. Semua angka dibaca dari kode, bukan dikira-kira. Tidak ada panggilan LLM selama penyusunan dokumen ini.

---

## 1. Bagaimana galat berpath dikembalikan ke model pada putaran koreksi

Ada **dua saluran** galat berpath, keduanya aktif dan saling melengkapi:

### 1.1 Di dalam satu run — alat `validate_draft(draft)`

Dipanggil model sendiri sebelum menjawab. Bentuk persis keluarannya (`KoogDiscoveryTools.reportOf`):

```json
{"valid": false, "issues": [{"path": "$.pack.modules[2].id", "message": "…"}, …]}
```

- Dokumen rusak **tidak pernah melempar**: `DiscoveryDraftDecodeException` ditangkap dan diubah menjadi satu isu berpath (`validationReport`); `IllegalArgumentException` lain menjadi isu berpath `$`.
- Path decode dibangun oleh parser produksi (`DiscoveryDraftCodec` + `ScreenProposalCodec` + `ProposalJsonReader`), contoh: `$.pack.slots[0].code`, `$.screens[0].proposal.entity.fields[3].type`, `$.screens[1].seed[0].jumlah`.
- Path validasi bisnis dibangun `DiscoveryDraftValidator` (`$.blueprint.modules[i].moduleCode`, `$.screens[i].{moduleId,widget,source}`) yang mendelegasikan isi proposal ke `ScreenProposalValidator` dengan awalan `$.screens[i].proposal…` (`.entity`, `.entity.fields[j].key`, `.view.columns[2]`, `.seed[0].status`, `.binding`).

### 1.2 Antar putaran — pesan pengguna baru

Loop koreksi berada **di luar** agent (`KoogDiscoveryAgent.generate`, `maxCorrectionRounds = 3`). Setiap putaran memulai percakapan Koog baru; `KoogDiscoveryPrompt.userMessage(request, feedback, previousAnswer, round)` menyusun:

```
Putaran koreksi ke-2. Draf sebelumnya ditolak validator:
- $.blueprint.modules[0].moduleCode: Modul 'tidak_ada_di_pack' tidak ada di pack klinik
- $.screens[0].proposal.entity.fields[3].type: Tipe field 'KARANGAN' bukan kosakata tertutup: TEXT, NUMBER, DATE, ENUM, BOOL

Jawaban sebelumnya (dipotong bila terlalu panjang):
{…jawaban utuh putaran sebelumnya, dipotong 6.000 karakter…}

Perbaiki HANYA bagian yang dilaporkan, pertahankan sisanya, lalu balas JSON utuh.
```

- Dekode gagal → satu isu dari `issueOf`: path `DiscoveryDraftDecodeException`, atau `$` untuk kegagalan lain.
- `previousAnswer` dipotong `PREVIOUS_ANSWER_CHAR_LIMIT = 6_000` karakter (C3 menambah cap jumlah isu & panjang pesan isu).
- Draf hanya diterima bila `DiscoveryDraftValidator.validate(draft)` kosong; jika tidak, semua isu jadi `feedback` putaran berikut. Setelah 3 putaran gagal → `IllegalStateException` (bukan draf diam-diam) → `fallback` deterministik bila dipasang.

---

## 2. Perkiraan token/langkah satu run

| Parameter | Nilai | Sumber |
|---|---|---|
| Putaran koreksi maksimum | 3 | `KoogDiscoveryAgent.DEFAULT_MAX_CORRECTION_ROUNDS` |
| Langkah per putaran (panggilan LLM + eksekusi alat) | 24 | `DEFAULT_MAX_TOOL_ITERATIONS` (KDoc: tiap panggilan alat = 2 langkah; run sehat terbukti memakai 5 langkah `platform_modules` + 2 `validate_draft` + jawaban) |
| Temperatur | 0.2 | konstanta pribadi |
| Jawaban sebelumnya di prompt koreksi | ≤ 6.000 karakter | `PREVIOUS_ANSWER_CHAR_LIMIT` |
| Panjang prompt sistem (baseline, sebelum C1) | ±2,9 ribu karakter | teks `KoogDiscoveryPrompt.system` |
| Panjang contoh dokumen di prompt (baseline) | ±2,7 ribu karakter | `exampleDraftJson()` — 2 layar tanpa `proposal` |
| Narasi prospek | bebas, biasanya < 1 ribu karakter | `userMessage` |

Perkiraan run sehat: **3–8 langkah LLM per putaran** (±4–10 ribu token input per putaran pada ±4 karakter/token; keluaran ±2–4 ribu token untuk dokumen JSON penuh). Kasus terburuk: 3 putaran × 24 langkah — tetap terbatas, itulah alasan loop koreksi berada di luar agent (biaya per putaran bisa dihitung).

Setelah C1 (contoh membawa `proposal`), panjang contoh naik ±2–3×; itu risiko yang dikelola di §4 dan **dikunci test** (`KoogDiscoveryPromptTest`: batas panjang prompt sistem & contoh).


---

## 3. Apa yang `validate_draft` periksa sekarang (setelah B0; diperluas B3–B4)

Dekode ketat (`DiscoveryDraftCodec` → `DomainPackCodec` + `ScreenProposalCodec` + `ViewProposalCodec`), lalu `DiscoveryDraftValidator`:

1. **Identitas pack** — pack bawaan (garment) wajib identik atau lewat jembatan `useShipped` (dijembatani server sebelum validasi); pack baru wajib lolos `DomainPackRegistry.violations` (prefiks `<pack.code>_`, id bersama identik). Pesan registry dipetakan ke path `$.pack.modules[i].id` / `$.pack.slots[i].code`.
2. **Blueprint ↔ pack** — `blueprint.modules[].moduleCode` wajib ada di `pack.modules[].id`.
3. **Layar ↔ pack** — `screens[].moduleId` wajib ada di pack; `widget` ∈ kosakata tertutup (`FORM, TABLE, KANBAN, DASHBOARD, CHECKLIST, PRINT, CUSTOM_SCREEN`).
4. **Proposal (jika ada)** — identitas sama dengan deskriptor layar (screenId/moduleId/widget); `source` wajib; lalu seluruh `ScreenProposalValidator`:
   - kehadiran entity menurut widget (KANBAN/TABLE/FORM/CHECKLIST wajib; DASHBOARD/CUSTOM_SCREEN ditolak; PRINT opsional);
   - aturan entitas (`ProposalEntityRules`): kunci regex `[a-z][a-z0-9_]{0,40}`, ≥1 field, ≤12 field, kunci unik, ENUM wajib options (≤8, tak kembar), statusField = field ENUM 2–8 pilihan, transitions menunjuk pilihan yang ada;
   - aturan tampilan (`ProposalViewRules`): varian `view` cocok dengan widget, semua rujukan field ada, kolom kanban = opsi statusField, kolom tabel ≥1 & unik, field wajib masuk form, status bermesin tidak boleh jadi sel teks, dasbor 1–8 ubin yang menunjuk modul pack;
   - seed (`checkSeed`): ≤8 baris, kunci = field entity, nilai bertipe sah (ENUM ∈ options, NUMBER angka, BOOL `ya`/`tidak`, DATE ISO), field wajib terisi tiap baris;
   - batas teks: rationale/title/label ≤200 karakter; `binding` API hanya untuk source PACK.

Yang **belum** diperiksa saat B0 (kemurnian vertikal lintas pack, konsistensi lintas-layar `entity.id` sama) sudah ditutup **B4** (`ProposalPurityRules`, `CrossScreenRules`) — validator menolak, dan grader C4 menilai kriteria yang sama dari sisi pengukuran.


---

## 4. Risiko memperluas keluaran dengan `proposal` — dan keputusan format di prompt

| Risiko | Penjelasan | Mitigasi yang dipilih |
|---|---|---|
| **Panjang keluaran membengkak** | `proposal` menambah ±1,5–3 ribu token per layar; 3 layar × 3 putaran bisa melipatgandakan biaya | Contoh hanya **2 layar**; seed contoh 2 baris; semua batas `ProposalLimits` (field 12, options 8, status 8, seed 8, teks 200, ubin 8) ditulis eksplisit di prompt; jawaban sebelumnya tetap dipotong 6.000 karakter; batas putaran & langkah tidak diubah |
| **Kesalahan JSON naik** | Struktur bersarang lebih dalam (`entity.fields[].options[]`, `view.tiles[]`) = lebih banyak tempat salah koma/tipe | Tidak memperbaiki JSON sendiri — parser produksi yang melaporkan berpath; `ViewProposalCodec` membaca `view` **menurut widget** sehingga varian yang tak cocok tidak mungkin lewat dokumen; contoh di prompt **dirakit dari tipe kontrak lalu di-encode codec** (bukan teks tempel) sehingga bentuk yang dipelajari model dijamin sah |
| **Kunci hilang / kunci karangan** | Model menulis `proposal` tanpa `rationale`, atau mengarang jenis `view` sendiri | Decode ketat menolak dengan path (tanpa fallback senyap — Kontrak 4 tenant-variability); prompt menuliskan daftar kunci wajib per widget dan melarang kunci baru |
| **Nilai tak sah** — tipe field, opsi status, rujukan field | Kosakata tertutup (`FieldType`, `CardStyle`) dan rujukan lintas-bagian (kolom ↔ field) mudah keliru | `screen_catalog()` (C2) menjawab kosakata + pemetaan peran→tampilan dari pack bawaan; validator menolak berpath; loop koreksi yang sudah ada yang mengembalikannya |
| **Provenance salah** | Model bisa menulis `source: PACK` padahal keluarannya AGENT | C3: server **membubuhkan** `ProposalSource.Agent(agentRef)` pada setiap layar ber-proposal setelah dekode — sumber tidak pernah dipercaya ke LLM |
| **Prompt bocor jawaban** | Contoh kaya bisa dicontek vertikal nyata | Contoh tetap berkode pack `contoh` dengan kosakata bisnis netral; test mengunci `pack.code == "contoh"` |
| **Prompt melebihi konteks/mahal** | Sistem + contoh + narasi + jawaban lama per putaran | Test C1 mengunci panjang prompt sistem & contoh di bawah batas tertulis |

### Keputusan format `proposal` di prompt (ringkas, ramah LLM)

- Satu layar = satu objek `{"screenId","moduleId","title","widget","rationale","entity","view","seed","source"}` — tanpa kunci lain.
- `entity` hanya untuk widget data; dua layar yang mengelola benda sama **memakai definisi entity yang sama persis** (ditulis ulang identik di masing-masing layar).
- `rationale` satu kalimat bahasa pemilik usaha, ≤200 karakter, pola "Dipilih karena …".
- `view` mengikuti widget: TABLE `{columns, inlineCreate, editableFields}`; KANBAN `{card, columnMeta, detailFormFields}` (kolom = opsi statusField); FORM `{fields, submitLabel}`; CHECKLIST `{labelField, doneField BOOL}`; DASHBOARD `{tiles}`; PRINT `{fields}`; CUSTOM_SCREEN `null`.
- `seed` ≤8 baris objek string (angka ditulis `"5"`, tanggal ISO, BOOL `ya`/`tidak`).
- `source` selalu `{"kind":"AGENT","agentRef":"<agentRef agent ini>"}` — nilai persisnya dicontek dari contoh di prompt.
- Pemilihan widget mengikuti watak kerja modul (antrean/alur → KANBAN, daftar/ledger → TABLE, pencatatan satu-per-satu → FORM, langkah bercentang → CHECKLIST, ringkasan angka → DASHBOARD, dokumen cetak → PRINT), bukan selera.

---

## 5. Keputusan untuk C1–C3 (rangkuman)

1. Contoh prompt diperluas dengan `proposal` **dirakit dari kode** (`ScreenProposal` → `DiscoveryDraftCodec`), 2 layar (TABLE + FORM) berbagi satu entity — test tetap mewajibannya lolos `DiscoveryDraftValidator`.
2. Versi prompt naik: `agentRef = koog/<model>/draft-v2` (draf lama `draft-v1` tetap terbaca — proposal bersifat tambatif).
3. Alat baru `screen_catalog()` untuk kosakata tertutup + petunjuk peran→tampilan; anggaran langkah di prompt disesuaikan (masing-masing alat informasi maksimal sekali).
4. Sumber proposal dibubuhkan server, bukan dipercaya ke model; galat feedback dipotong (jumlah & panjang) agar putaran koreksi tetap ringkas.
5. ~~Baseline deterministik belum menghasilkan layar (B3 belum merge)~~ — **B3 sudah merge saat kerja C**: `DeterministicScreenProposer` mengisi layar dari `SlotDefinition.defaultWidget`/`defaultStatuses`, sehingga grader C4 menilai kriteria isi layar secara **nyata** terhadap baseline (terbukti: 11/11 PASS dengan layar dinilai). Kriteria tetap dinilai bila layar ada; draf tanpa layar lulus kosong dengan keterangan.
