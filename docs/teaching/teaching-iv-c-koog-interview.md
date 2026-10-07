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
  sejak keputusan 2026-10-07 dinilai dari *kemampuan & asal modul*, bukan pack baku.
- **Tujuh kriteria**: `valid`, `divisi_masuk_akal`, `peran_ke_divisi`, `tautan_modul`, `asal_modul`,
  `kemurnian_vertikal`, `jumlah_giliran` (+ batas `maxDivisions` sebagai kasus negatif C6:
  cerita kecil → draf kecil).
- **Grader dites sendiri** (`InterviewEvalGraderTest`): sesi emas wajib lulus 100% (kalau tidak,
  penilainya yang rusak); setiap sesi rusak gagal di kriteria yang tepat.
- **Kalibrasi terbuka**: pencocokan `contains` longgar — salah positif mungkin; naikkan ke pencocokan
  kata bila eval live menunjukkan salah positif. Kriteria `berdasar_cerita` (C6) **menunggu kontrak
  `basisRef` dari B** (induk §6.1) — belum dinilai, jangan mengaku menilai.

## 4. Disiplin biaya eval live (C4) — jangan ringankan

1. Tiga gerbang: `INTERVIEW_LIVE_EVALS=1`, `DEEPSEEK_API_KEY`, `INTERVIEW_LIVE_EVALS_CONFIRM=yes`.
2. Estimasi dicetak dengan **margin ganda** (`EST_TOKENS_PER_ROUND = 12_000`, 2× SP) — estimasi SP
   dulu meleset ±4×.
3. Ulangan bawaan **1**, baru 3 (`INTERVIEW_LIVE_EVALS_REPEAT`).
4. Saldo API dicek sebelum/sesudah (`/user/balance`, best-effort, kunci tidak pernah dicetak).
5. Laporan `docs/plannings/eval-iv-<tanggal-UTC>.md`, **tidak pernah menimpa** (sufiks `-rondeN`).
6. Gagal **model** (penebak tidak sah) dan gagal **penilai** (sah tapi kunci tidak tercapai) dipisah.

## 5. Menyambungkan yang masih terbuka

| Terbuka | Pemilik | Titik sambung yang sudah disiapkan |
|---|---|---|
| Port `InterviewGuesser` di core | B (plan §6) | `AgentInterviewGuesser.guess(step, pack, draft, narrative)` sudah bertanda tangan sama; adapter = satu baris |
| `DeterministicInterviewGuesser` + baseline 100% | B1 | pasang ke `InterviewGuessFn` di `runInterviewFlow` — pelari tidak berubah |
| `basisRef` / `Basis` / F0-F2 di kontrak | B (induk §6.1) | prompt sudah mengajarkan F0→F2; grader sudah punya lubang `berdasar_cerita` |

## 6. Kontrak yang C butuhkan dari B (laporan, bukan suntingan)

1. Port `InterviewGuesser` (plan §6) supaya `AgentInterviewGuesser` resmi menempel di belakangnya.
2. Perluasan kontrak induk §6.1 (`basisRef`, `BusinessProfile`, `RequirementSpec`, langkah F0–F2)
   untuk kriteria `berdasar_cerita` dan skenario pengguna bingung/menolak saran.

## 7. Verifikasi (yang dijalankan di mesin pengajar)

```bash
DB_NAME=wemake_erp_scratch_c ./gradlew :server:test --tests '*Interview*' --tests '*KoogInterview*'
# eval live (manual, butuh kunci & konfirmasi):
INTERVIEW_LIVE_EVALS=1 DEEPSEEK_API_KEY=sk-... INTERVIEW_LIVE_EVALS_CONFIRM=yes \
  ./gradlew :server:test --tests '*KoogInterviewLiveEvalsTest'
```
