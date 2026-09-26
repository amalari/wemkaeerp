# Teaching — Kolom R&D & Selesai di Kanban Sampling

## Apa yang berubah
- Kolom **Mesin Rajut** + **Penyelesaian Akhir** digabung jadi **4. R&D** (`RD_STAGES`: rajut → kemas).
- **Tunggu ACC Buyer** diganti nama jadi **5. Selesai** (IN_DELIVERY + ACC_APPROVED). Revisi tetap
  kembali ke Program CAM.
- Tombol "Turun Mesin Selesai" dihapus. Rajut dikerjakan tim sampling lewat **menu operator rajut**,
  jadi kolom R&D hanya dipakai untuk memantau.

## Step per step
- **Step 0 — domain** (`core/.../sampling/SamplingRdProgress.kt`): `rdProgress()` merakit jejak
  tahap wajib + proses sisipan dari alur SPK. `daysInCurrentStage()` membaca `stageHistory`.
  Ambang `RD_STALL_WARNING_DAYS = 3`. Diuji di `SamplingRdProgressTest`.
- **Step 1 — zona Kanban**: `SamplingStageZone.RND` dan `SELESAI` menggantikan tiga zona lama.
  Tahap di domain tidak disentuh, jadi audit, gerbang, dan Surat Jalan tetap sama.
- **Step 2 — kartu**: `SamplingRdProgressTrack` menampilkan titik per langkah (hijau = selesai,
  biru = aktif, oranye pudar = proses vendor), label "Sedang: …", dan tag "N hari" yang berubah
  kuning saat ≥3 hari.
- **Step 3 — kepala kolom**: `SamplingRdStageFilterRow` berisi chip hitungan per tahap. Klik untuk
  menyaring, klik lagi untuk melepas.

## Kenapa begini
Kolom menunjukkan *siapa yang memegang* SPK: di tahap R&D pemegangnya tim sampling. Posisi rinci
adalah *data*, jadi ditampilkan di kartu. Kalau diberi kolom sendiri, papan jadi sembilan kolom dan
tidak muat di layar 1280dp.

## Jebakan
- Order belum mencatat posisi di dalam proses sisipan, jadi proses sisipan tidak pernah ditandai
  "aktif". Status pastinya akan datang dari integrasi Work Queue (langkah berikutnya).
