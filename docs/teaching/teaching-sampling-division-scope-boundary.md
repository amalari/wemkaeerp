# Teaching: Batas Ranah Divisi Sampling — Berhenti di Turun Mesin

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Bounded Context antar-modul ERP, Progressive Disclosure, Kolom Agregat Kanban, Refactor di Working Tree yang Dipakai Bersama
> **Prasyarat**: Memahami modul Sampling (`SAMPLING_ORDER`), pola MVI, dan pipeline 7 tahap

---

## 1. Masalah: Satu Modul Mengerjakan Semua

Planning internal (`planning-sampling-finishing-qc-admin-control-tower.md`) sudah mendefinisikan
empat modul dengan batas tanggung jawab yang jelas:

| Modul | Tanggung jawab | Garis finis |
|---|---|---|
| `SAMPLING_ORDER` | CAM, feeder, tenselity, rajut, gramasi, timing | 🏁 **Turun mesin** |
| `OPERATOR_EXEC` | Linking, kancing, steam + setoran harian | 🏁 Baju jadi |
| `QUALITY_CONTROL` | Ukur POM fisik vs size chart, cek cacat | 🏃 QC approved |
| Admin Tower (CRM) | Vendor makloon, resi, ACC buyer | 🏁 Golden sample |

Tapi UI modul Sampling menampilkan & menggerakkan **seluruh 7 tahap**: kanban 7 kolom, tombol
`Setor Finishing`, `QC Inspeksi`, `ACC PRODUKSI` semua tersedia di satu layar. Padahal layar
Operator Finishing (`FinishingOperatorWorkspaceScreen`) dan QC Inspector
(`QcInspectorWorkspaceScreen`) sudah punya antrean + form masing-masing (disaring via
`pipeline_stage IN ('LINKING_ASSEMBLY','FINISHING_QC')`).

**Pelajaran**: pipeline 7 tahap itu lifecycle **order**, bukan daftar tugas **satu divisi**.
Jangan di-collapse di domain — `FinishingOperatorWorkspaceScreen` & `QcInspectorWorkspaceScreen`
menyaring SPK justru berdasarkan tahap-tahap itu. Yang perlu diubah hanyalah **cakupan UI modul
Sampling**.

## 2. Solusi: Tiga Kolom Milik Sampling + Dua Kolom Agregat

`SamplingPipelineKanbanBoard` kini menyusun board dari kelompok, bukan `entries.forEach`:

1. **3 kolom ranah sampling** (`NEW_INTAKE`, `CAM_PROGRAMMING`, `MACHINE_KNITTING`) — aksi penuh.
2. **Kolom agregat "Di Meja Finishing & QC"** (`LINKING_ASSEMBLY` + `FINISHING_QC`) — kartu
   read-only (`showActions = false`), posisi tahap ditampilkan sebagai badge berwarna
   `samplingStageTint()`.
3. **Kolom agregat "Tunggu ACC Buyer"** (`IN_DELIVERY` + `ACC_APPROVED`) — aksi ACC/Revisi tetap
   aktif karena itu tugas admin, bukan operator sampling.

Di Workbench (desktop & mobile), header berubah dari "semua tombol selalu tampil" menjadi
`when (order.pipelineStage)` dengan aksi maju yang jelas:
`Mulai Program CAM` → `Masuk Mesin Rajut` → `Selesai Turun Mesin -> Serah ke Finishing`;
linking/finishing/QC hanya badge read-only. Tombol `+ Setor`/`QC` **dihapus** dari modul ini —
kemampuannya hidup di modul divisi masing-masing.

## 3. Teknik Penting

- **Parameter `showActions` pada kartu** — satu komponen kartu melayani dua mode (aksi vs
  pantau) tanpa duplikasi. Ini stateless flag, bukan logika tersembunyi.
- **Event divisi lain tetap hidup** — `SamplingUiEvent.AddFinishingDeposit` dan
  `SubmitQcInspection` JANGAN dihapus: layar Operator Finishing & QC Inspector memakai ulang
  `SamplingViewModel`. Yang aman dihapus hanya event pembuka dialog
  (`OpenFinishingDialog`/`OpenQcDialog`) beserta state-nya, karena hanya modul sampling yang
  memakai. **Selalu grep pemakaian lintas-modul sebelum membuang event.**
- **Uji eksplisit: kompilasi 3 target** (Jvm/WasmJs/Js) setelah refactor lintas-file.

## 4. Jebakan Pemula

| Jebakan | Kenapa berbahaya |
|---|---|
| Menghapus tahap dari enum `SamplingPipelineStage` | Mematahkan saringan antrean modul Finishing & QC yang membaca tahap tersebut dari DB |
| Menghapus event `AddFinishingDeposit`/`SubmitQcInspection` | Layar divisi lain memakai ulang `SamplingViewModel` — fitur mati diam-diam |
| Menampilkan aksi divisi lain "biar praktis" | Tanggung jawab kabur; operator sampling mengira dia harus menyetor finishing |
| Edit bareng working tree yang dipakai sesi lain | Perubahan bisa ter-revert oleh `git restore` dari proses lain — komit cepat, kerja per-branch |

## 5. Verifikasi & Tantangan Mandiri

1. `./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs`
2. Buka `/sampling-order`: board 5 kolom (3 sampling + 2 agregat); kartu di "Di Meja Finishing &
   QC" tanpa tombol; klik kartu → Workbench menampilkan badge tahap + aksi yang sesuai tahap.
3. Pastikan layar `Operator Finishing` dan `QC Inspector` masih berfungsi (mereka memakai
   `SamplingViewModel` yang sama).
4. **Tantangan**: pindahkan aksi vendor makloon dari kartu kanban ke tab "Monitoring Vendor"
   secara penuh, lalu buktikan tidak ada alur vendor yang hilang.

---

## Next Related Tasks

- Mode "Isian Sampling Belum Lengkap (n)" di Workbench untuk SPK tahap 1–3.
- Auto-select `spkSelectorOrders.firstOrNull()` sebagai fallback `selectedOrder`.
- Pertimbangkan memindahkan aksi ACC buyer ke layar Admin Tower terpisah bila modulnya dibuat.
