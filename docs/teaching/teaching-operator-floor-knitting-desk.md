# Teaching — Meja Rajut di Modul Lantai Produksi

## Latar
Tim sampling ikut bekerja sebagai operator rajut, dan menu yang dipakai harus modul operator yang
sama. Ternyata modul `OPERATOR_EXEC` (Lantai Produksi) baru punya meja Finishing, dan API Work
Queue (`WorkCard`) belum punya layar sama sekali.

## Yang dibuat
- `presentation/operator/OperatorFloorWorkspaceScreen.kt`: shell modul dengan pilihan meja
  `Rajut | Finishing & Linking`. Kedua meja berbagi satu `SamplingViewModel`, jadi SPK yang
  diserahkan dari Rajut langsung muncul di antrean Finishing.
- `presentation/operator/KnittingOperatorDesk.kt`: antrean SPK di tahap `MACHINE_KNITTING`.
  Tiap kartu menampilkan badge **SAMPLE**, jumlah pcs, dan Program CAM read-only (instruksi mesin).
  Tombol "Turun Mesin -> Serah ke Linking" membuka `StageAdvanceDialog`, yaitu lembar
  gramasi/waktu/size chart/tenselity yang sama dengan yang dipakai Kanban.
- `ModuleWorkspaceScreen` mengarahkan `OPERATOR_EXEC` ke shell baru.

## Kenapa tidak langsung pakai `WorkCard`
SPK sample dan meja Finishing masih bekerja langsung pada `SamplingOrder`. Memasang `WorkCard`
hanya untuk rajut akan membuat satu SPK punya dua sumber status. Migrasi ke Work Queue sebaiknya
dikerjakan untuk seluruh meja sekaligus, ketika layar antrean untuk bulk production dibangun.

## Jebakan
- Meja Rajut baru berisi SPK sample. Bulk work order belum masuk, karena belum ada jalur UI dari
  Work Queue.
