# Teaching IV-C: Agent Koog Menebak Wawancara & Evaluasi Berstruktur

> Modul pelajaran dari [PLAN-iv-C-koog](../plannings/parallel4/PLAN-iv-C-koog.md) — jalur C gelombang
> `parallel4`. Pembaca target: engineer yang akan menyambung `DeterministicInterviewGuesser` (B1),
> merawat prompt konsultan, atau menjalankan eval live pertama.

## 1. Masalah yang diselesaikan

Alur wawancara discovery (G1 divisi → G2 peran → G3 modul → G4 sambungan → G5 ringkasan) punya dua
pembuat tebakan: deterministik (kamus pack, milik B) dan LLM (Koog, milik C). Keduanya hanya
**mengusulkan**; `InterviewValidator` yang menegakkan. Yang belum ada sebelum jalur C: cara
**mengukur** mutu tebakan LLM dengan biaya yang tidak mengejutkan, dan cara menahan LLM agar tidak
mengarang divisi/modul di luar katalog.

## 2. Arsitektur lima bagian (semua di `server/.../infrastructure/discovery/`)

```
KoogInterviewPrompt   ── persona konsultan + contoh dokumen (dirakit dari kode)
KoogInterviewTools    ── interview_state + interview_catalog (katalog + kosakata asal)
KoogInterviewBridge   ── SATU jembatan/dekoder untuk alat, pesan pengguna, dan jawaban akhir
KoogInterviewGuesser  ── AgentInterviewGuesser: loop koreksi berbatas + provenance server
(server/src/test) InterviewGoldenCases + InterviewEvalGrader + InterviewEvalFlow + InterviewLiveEvalReport
```

### 2.1 Jembatan tunggal (pelajaran `useShipped`, ditegakkan lagi di sini)

`KoogInterviewBridge.interviewStateJson(draft)` menghasilkan dokumen **terbungkus**
`{"interview":{...}}` — bentuk yang *persis* sama dengan yang diminta dari jawaban akhir model.
`decodeInterviewAnswer(answer, draft)` menerima bentuk itu, menjembatani singkatan
`{"interview":{"useCurrent":true}}` menjadi sesi berjalan, **menyuntikkan pack + blueprint dari draf**
(model tidak boleh menulisnya dari ingatan), lalu mendekode lewat `DiscoveryDraftCodec` produksi.

**Insiden kecil yang menunjukkan kenapa:** versi pertama jembatan mengembalikan dokumen *telanjang*
(`{...}` tanpa pembungkus `interview`) sementara dekoder menuntut terbungkus — tiga test langsung
merah ("jawaban tidak memuat dokumen 'interview'"). Setelah dibuat tunggal, alat dan jawaban akhir
tidak mungkin divergensi lagi. *Alat, contoh di prompt, dan jawaban akhir wajib satu bentuk.*

### 2.2 Provenance server, bukan model

`stampGuessProvenance()` membubuhkan `source=GUESS`, `confirmed=GUESSED`, dan confidence bawaan 70
(dipatok 0–100). Model yang menulis `"confirmed":"confirmed"` tidak mengubah apa pun. Confidence
> 100 **ditolak lebih awal** oleh konstruktor `RoleModuleLink` — jangan mencoba menjepitnya di lapisan
teks; galat berpath dari codec yang mengajari model mengoreksi.

### 2.3 Validasi setelah penggabungan

Usulan divalidasi **setelah digabung** ke sesi berjalan (`mergeStepGuesses` + `InterviewValidator`).
Alasannya: galat yang dilihat model identik dengan galat yang akan dilihat penyimpanan dokumen, jadi
koreksi diri menyasar bagian yang benar (mis. `$.interview.links[0].moduleId`).

## 3. Penilaian (C0) — yang bikin skornya bisa dipercaya

- **Kunci jawaban per langkah**: 11 kasus emas (`InterviewGoldenCases`), termasuk sablon/bordir yang
  sejak keputusan 2026-10-07 dinilai dari *kemampuan & asal modul*, bukan pack baku. Kunci mengikuti
  **narasi** — jangan mengharapkan modul yang tidak disebut cerita (pelajaran: kunci katering pernah
  mengharapkan modul "produksi" padahal pack hasil narasi hanya punya pesanan & laporan).
- **Tujuh kriteria**: `valid`, `divisi_masuk_akal`, `peran_ke_divisi`, `tautan_modul`, `asal_modul`,
  `kemurnian_vertikal`, `jumlah_giliran` (+ batas `maxDivisions` sebagai kasus negatif C6:
  cerita kecil → draf kecil).
- **Grader dites sendiri** (`InterviewEvalGraderTest`): sesi emas wajib lulus 100% (kalau tidak,
  penilainya yang rusak); setiap sesi rusak gagal di kriteria yang tepat.
- **Kalibrasi terbuka**: pencocokan `contains` longgar — salah positif mungkin; naikkan ke pencocokan
  kata bila eval live menunjukkan salah positif. Kriteria `berdasar_cerita` (C6) **sudah aktif** sejak kontrak
  `basisRef` B7 masuk (sembilan kriteria; lihat `InterviewEvalGrader`).

## 4. Disiplin biaya eval live (C4) — jangan ringankan

1. Tiga gerbang: `INTERVIEW_LIVE_EVALS=1`, `DEEPSEEK_API_KEY`, `INTERVIEW_LIVE_EVALS_CONFIRM=yes`.
2. Estimasi dicetak dengan **margin ganda** (`EST_TOKENS_PER_ROUND = 12_000`, 2× SP) — estimasi SP
   dulu meleset ±4×.
3. Ulangan bawaan **1**, baru 3 (`INTERVIEW_LIVE_EVALS_REPEAT`).
4. Saldo API dicek sebelum/sesudah (`/user/balance`, best-effort, kunci tidak pernah dicetak).
5. Laporan `docs/plannings/eval-iv-<tanggal-UTC>.md`, **tidak pernah menimpa** (sufiks `-rondeN`).
6. Gagal **model** (penebak tidak sah) dan gagal **penilai** (sah tapi kunci tidak tercapai) dipisah.

## 5. Menyambungkan yang masih terbuka

| Butir | Status | Catatan sambungan |
|---|---|---|
| Port `InterviewGuesser` di core (B) | ✅ merge di main | adapter `AgentInterviewGuesser.asInterviewGuesser()` + `InterviewStepGuesses.toGuesses()` (kunci sama dengan pelaksana B: `role:moduleId`, `from>to`) |
| `DeterministicInterviewGuesser` + baseline 100% (B1) | ✅ merge; baseline terpasang | `deterministicKamusSeam()` di pelari alur; `InterviewEvalsTest` wajibkan 100% di 11 kasus — pengguna kooperatif melengkapi kunci sebagai `ANSWER`, mutu tebakan diukur sebelum suplemen |
| `basisRef` / `Basis` / F0-F2 di kontrak (B7) | ✅ merge di main | C6 terpasang: `berdasar_cerita` dinilai, F0–F2 berfungsi, skenario bingung/menolak dites |
| C6 — persona konsultan penuh | ✅ selesai (cabang ini) | lihat bagian 5a di bawah |

## 5a. C6 — persona konsultan penuh (berdasar cerita, F0–F2, saran & penolakan)

C6 dikerjakan setelah B7 (basisRef + F0–F2 + profil/spek) merge ke main. Prinsipnya: **C tidak
mengarang aturan baru — ia menaati aturan yang validator B7 tegakkan, dan mengejarnya lewat prompt.**

Yang dipasang:

1. **Sesi dasar berdasar-cerita** (`AgentInterviewGuesser.generate`): draf tanpa wawancara memulai
   `version = BASED_ON_STORY` dengan narasi giliran; sesi tersimpan dihormati versinya (dokumen lama
   pra-B7 tetap v1 — basis opsional). Efeknya: validator menolak tebakan tanpa `basisRef`, dan pesan
   koreksi berpath (`$.interview.links[0].basisRef`) yang mengajari model.
2. **Butir konsultan di usulan**: `InterviewStepGuesses` kawa `profile` (F0/F1) dan `specs` (F2);
   `mergeStepGuesses` mengganti profil bila diusulkan dan menggabung spesifikasi per `areaKey`.
3. **Prompt**: aturan keras basis (narasi=kutipan persis, jawaban/saran_diterima=answerId), instruksi
   per langkah F0/F1/F2 yang eksplisit "isi profile/specs saja", dan **contoh dokumen ikut versi 2** —
   dibangun dari kode, dites wajib lolos validator penuh, jadi bentuk `basisRef` diajarkan lewat contoh
   yang dijamin sah, bukan tempelan teks.
4. **Pelari alur memakai fungsi giliran produksi** (`nextQuestion` + `answer` dari core): F0–F2,
   pelengkapan dasar `JAWABAN` pada suplemen pengguna, penyelesaian `GUESSED`, dan pelompatan langkah
   berperilaku identik dengan route — eval mengukur jalur yang benar-benar dipakai pengguna.
5. **Dua kriteria grader baru**: `berdasar_cerita` (versi 2, cerita tersimpan, semua butir berbasis sah;
   dinilai independen dari validator supaya penilaiannya bisa dipercaya sendirian) dan
   `tanpa_modul_tak_disebut` (kasus negatif — pengetahuan modul lazim hanya boleh jadi pertanyaan).
6. **Skenario dites tanpa LLM**: tebakan tanpa dasar → koreksi berkutipan; `SARAN_BELUM_DIJAWAB` →
   dikosongkan (pengguna menolak saran); F0 mengisi profil; F2 menggabung spesifikasi per area.

Pelajaran teknis: smart cast Kotlin menolak `narrative.contains(ref.quote)` setelah `isNullOrBlank()`
karena properti lintas modul — pola amannya `ref.quote?.let { it.isNotBlank() && narrative.contains(it) } != true`.

## 6. Kontrak yang C butuhkan dari B (laporan, bukan suntingan)

1. ~~Port `InterviewGuesser` (plan §6)~~ ✅ terpenuhi; adapter `asInterviewGuesser()` menempel.
2. ~~Perluasan kontrak induk §6.1 (`basisRef`, `BusinessProfile`, `RequirementSpec`, F0–F2)~~ ✅ B7.
3. Yang masih terbuka: keputusan **G3** (eval live) dan penggunaan `berdasar_cerita` pada keyakinan
   akhir — saran baru tetap masuk setelah diterima (`SARAN_DITERIMA`) sudah didukung kontrak, sisanya
   keputusan produk di route ringkasan.

## 7. Verifikasi (yang dijalankan di mesin pengajar)

```bash
DB_NAME=wemake_erp_scratch_c ./gradlew :server:test --tests '*Interview*' --tests '*KoogInterview*'
# eval live (manual, butuh kunci & konfirmasi):
INTERVIEW_LIVE_EVALS=1 DEEPSEEK_API_KEY=sk-... INTERVIEW_LIVE_EVALS_CONFIRM=yes \
  ./gradlew :server:test --tests '*KoogInterviewLiveEvalsTest'
```
