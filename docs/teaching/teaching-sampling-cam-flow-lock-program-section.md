# Teaching — Kunci Alur & Section Program di Tahap Program CAM

## Konteks
Sebelumnya lembar Program CAM diisi di `StageAdvanceDialog` *saat* SPK maju dari Penentuan Alur ke
Program CAM, dan alur proses tetap bisa diubah walau SPK sudah di CAM. Sekarang:

1. **Masuk Program CAM tidak butuh lembar** — "Alur Siap -> Mulai CAM" langsung memajukan tahap.
2. **Di tahap Program CAM ke atas, alur dikunci** (read-only).
3. **Section Program dibuka di dialog Detail SPK** untuk tim sampling; tombol
   "Simpan Program -> Masuk Mesin Rajut" menyimpan lembar sebagai input tahap CAM lalu maju.

## Step per step
- **Step 0 — domain**: `requiresStageWorksheet()` di `StageWorkInput.kt` tidak lagi memuat
  `CAM_PROGRAMMING`. Gerbang CAM -> Mesin Rajut (`requireStageGate`) tidak berubah: tetap menuntut
  `CAM_REQUIRED` terisi pada input tahap **CAM**.
- **Step 1 — event**: `ConfirmStageAdvance` mendapat `inputStage` (default = `targetStage`).
  Lembar Program disimpan di tahap CAM walau target transisinya Mesin Rajut — persis yang dicek
  gerbang domain. Sebelumnya lembar dikirim atas nama tahap tujuan.
- **Step 2 — mode kunci panel**: `ProcessFlowAdjusterPanel(isLocked)` meneruskan flag ke
  `PlacedProcessChip` (tanpa drag & tombol hapus), `ProcessFlowGap` (tanpa tombol +, konektor
  pengiriman tetap tampil & bisa diklik), menyembunyikan palet dan tombol Reset, serta menampilkan
  badge "Alur Dikunci".
- **Step 3 — dialog**: `SamplingSpkDetailDialog` menghitung `isFlowLocked` dari urutan tahap dan
  merender `CamProgramSection` (memakai ulang `DynamicSectionTable`, prefill dari input CAM
  tersimpan).
- **Step 4 — ViewModel**: `advanceStage` ikut menyegarkan `spkDetailTarget`, jadi setelah "Mulai
  CAM" dialog tetap terbuka dan langsung berganti ke mode kunci + section Program.
  `confirmStageAdvance` menutup dialog setelah berhasil.
- **Step 5 — routing Kanban**: drag/tombol CAM -> Mesin Rajut membuka dialog Detail SPK, bukan
  dialog tahap terpisah.

## Kenapa begini
- Mengunci alur di UI mencerminkan keputusan bisnis: setelah CAM mulai, mengubah urutan proses
  mengacaukan program mesin yang sedang dikerjakan.
- `inputStage` memperbaiki ketidakcocokan diam-diam antara tahap tempat lembar disimpan dan tahap
  yang dicek gerbang domain.

## Jebakan
- Kuncinya baru di UI. Server belum menolak perubahan alur kustom untuk SPK yang sudah lewat CAM —
  kalau perlu ditegakkan, tambahkan penjaga domain di use case alur per-desain.
