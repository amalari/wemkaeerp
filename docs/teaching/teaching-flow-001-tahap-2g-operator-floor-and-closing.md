# 🎓 Modul Pembelajaran: Meja Operator dari Kerangka & Penutupan Tahap 2 (TRD-FLOW-001 R3b)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Ukuran keberhasilan migrasi yang benar, snapshot yang basi, Aturan Ratchet sebagai peluang
> **Prasyarat**: [Tahap 2f — papan sampling](teaching-flow-001-tahap-2f-sampling-board-from-frame.md)
> **Referensi Task**: [TRD-FLOW-001](../trd/TRD-FLOW-001-industry-stage-templates.md) — R3b

---

## 💡 1. Konsep Dasar

Meja operator adalah bagian paling "rajut" dari sistem: daftar meja adalah
`SamplingPipelineStage.entries.filter { it.isOperatorDesk }`, aksi "selesai" adalah tabel
`when` per nama tahap, dan label meja ditulis tangan. Setelah R3b, meja adalah `StageDefinition`
dari kerangka pabrik, aksinya diturunkan dari **peran** tahap, dan labelnya data tenant.

---

## 🧭 2. "Start dari Mana?" — Ukur yang Benar

**Jangan mengukur kemajuan dengan jumlah kata `SamplingPipelineStage`.** Sebagian besar sisanya
adalah konstanta jangkar (`NEW_INTAKE.toStageCode()`) yang sah di semua template. Yang berbahaya
hanya dua jenis:

1. **Pembacaan jembatan yang bisa melempar** — `.pipelineStage`, `.fromStage`, `.toStage`,
   `claim.stage`, `reworkTargets` (enum). Satu saja tersisa = layar meledak untuk SPK bordir.
2. **Enum dipakai sebagai kerangka** — `entries`, `RD_STAGES`, daftar tahap tulis tangan
   (`ADJUSTABLE_STAGES`). Tidak meledak, tapi diam-diam menampilkan tahap rajut ke tenant bordir.

R3b ditutup ketika pemindai jenis 1 kembali **kosong** di ketiga lapisan, dan pemindai jenis 2
hanya menyisakan definisi rujukan & pengecualian yang disengaja.

---

## 🧱 3. Bedah Kode

### Blok A — Aksi "selesai" dari peran

```kotlin
fun StageDefinition.finishAction(frame, route): DeskFinishAction? = when {
    code == KNITTING -> route.nextAfter(code)?.let(DeskFinishAction::Worksheet)     // lembar khas rajut
    code == frame.firstWorkWith(SEWING)?.code -> DeskFinishAction.Deposit
    code == frame.firstWorkWith(QUALITY_CONTROL)?.code -> DeskFinishAction.QcInspection
    code == frame.firstWorkWith(FULFILLMENT)?.code -> DeskFinishAction.Store
    has(OPERATOR_DESK) -> next()?.let(DeskFinishAction::Handoff)
    else -> null
}
```

Tabel lama `MACHINE_KNITTING → Worksheet(LINKING_ASSEMBLY)` menulis tujuan secara tetap; versi
baru menanyakan rute. Untuk rajut sama (Linking tidak pernah dilompati) — dibuktikan
`finishAction_onKnitFrame_shouldMatchLegacyTable`.

### Blok B — Validasi akses meja terhadap kerangka tenant

`OperatorDeskAccessTest` mengunci aturan "kode meja asing diabaikan". Aturan itu dipertahankan
dengan memvalidasi terhadap **meja kerangka** (parameter `desks`, default rajut), bukan terhadap
enum. Hanya tipe assertion yang berubah (`QC_FINISHING` → `QC_FINISHING.toStageCode()`).

### Blok C — Satu nama per tahap, dipilih dari bahasa lantai

Tiga label berbeda untuk tahap yang sama (`deskLabel` "Linking", chip "Linking", badge timeline
"Jahit"). Keputusan 2026-09-29: istilah lantai. Operator rajut menyebut mejanya "Linking";
label yang dipakai sistem harus bahasa orang yang memakainya.

---

## ⚠️ 4. Jebakan Pemula

1. **Snapshot basi — pelajaran yang sama dengan awal percakapan ini.** Baris `tenant_stage_flows`
   yang ter-provision saat server dijalankan ulang **membekukan** label lama; mengubah template
   sesudahnya tidak berpengaruh. Di dev baris itu dihapus agar provision ulang (belum pernah
   diedit, tanpa SPK beku). Di produksi, V72 belum jalan — tidak ada baris basi. Tapi pelajarannya
   umum: **template yang disalin ≠ template yang dirujuk**; begitu Tahap 3 memberi tenant editor,
   perubahan template bawaan tidak boleh diharapkan sampai ke tenant lama.
2. **Server tidak me-reload `core`.** Mode watch Ktor hanya memuat ulang kelas `server`; perubahan
   di `core` (template) butuh restart proses server.
3. **Login canvas yang rapuh.** Klik koordinat sebelum tombol ter-render hilang tanpa jejak.
   Skrip dibuat mengulang sampai URL meninggalkan `/login`, dan gagal keras bila tidak — jangan
   pernah menilai UI dari layar "Akses Terbatas".
4. **Ratchet sebagai peluang.** `DealDetailDialog.kt` tidak boleh bertambah; 16 import
   `domain.sampling.*` eksplisit diringkas menjadi satu wildcard → file **lebih pendek** 16 baris
   sekaligus memberi ruang untuk perubahan yang dibutuhkan.

---

## 🧪 5. Pembuktian

- Pemindai pembacaan-yang-bisa-melempar: **kosong** di `core`, `server`, `app/shared`.
- `OperatorNonKnitDeskTest` (5): aksi selesai per peran di kerangka bordir, paritas tabel lama
  untuk rajut, label meja rajut = istilah lantai, akses meja divalidasi terhadap kerangka tenant,
  rework hanya bila ada meja sebelumnya.
- `core` 910, `app:shared` 149 test lulus (hasil segar). Perubahan test lama R3b: 6 baris fixture
  + 2 assertion tipe.
- Visual (superadmin demo): lantai produksi — tab "Rajut (2) · Linking (2) · Cuci (2) · Setrika ·
  QC · Kemas" identik dengan sebelum TRD; Detail SPK — badge warna tahap, panel alur dari kerangka.
- **Belum dilihat mata**: timeline monitoring deal, antrian QC, dialog rework (terkompilasi & teruji).

---

## 🏆 6. Tantangan Mandiri

- [ ] `OperatorDeskAccessPicker` di layar RBAC masih memakai meja rajut default — alirkan kerangka
      pabrik ke layar RBAC.
- [ ] Tahap 3: longgarkan `FlowNodeRef.parse` dengan validasi terhadap kerangka tenant, lalu
      aktifkan template kedua. Apa test pertama yang harus kamu tulis?
