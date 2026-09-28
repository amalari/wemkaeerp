# 🎓 Modul Pembelajaran: Papan Sampling dari Kerangka Tenant (TRD-FLOW-001 R3a)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: UI dari data (bukan enum), zona papan berbasis peran, fallback status-quo, verifikasi visual canvas Compose/Wasm
> **Prasyarat**: [Tahap 2e — peran, bukan nama](teaching-flow-001-tahap-2e-roles-over-names.md)
> **Referensi Task**: [TRD-FLOW-001](../trd/TRD-FLOW-001-industry-stage-templates.md) — R3a

---

## 💡 1. Konsep Dasar

Papan Kanban sampling dulu adalah `enum class SamplingStageZone` dengan enam kolom yang masing-masing
menyebut tahap rajut. Tenant bordir akan melihat kolom "Program CAM" yang tidak pernah terisi dan
kolom R&D berisi "Rajut, linking…" — padahal kerangkanya Digitizing → Hooping → Bordir.

**Analogi**: denah gedung parkir yang digambar dari daftar lantai gedung itu sendiri, bukan cetakan
denah gedung lain yang ditempel di pintu masuk.

Hasil akhir: papan dirakit dari `TenantStageFlow` yang dimuat dari server; untuk rajut hasilnya
**identik** dengan papan lama (dibuktikan test dan tangkapan layar).

---

## 🧭 2. "Start dari Mana?"

1. **Sumber data dulu**: `StageFlowApiClient` → `SamplingUiState.stageFlow` (default rajut).
2. **Fungsi murni penyusun kolom** `samplingStageZones(frame)` — bisa diuji tanpa Compose.
3. **Rantai tipe event** (`AdvanceStage`, `OpenStageAdvanceDialog`, rework) ke `StageCode`, lalu
   biarkan kompilator menunjuk setiap pemakai.
4. **Baru Composable**: papan, kartu, chip saring.
5. **Lihat dengan mata** — di dua lebar layar.

---

## 🧱 3. Bedah Kode

### Blok A — Kolom dari peran tahap

```kotlin
val prep  = frame.filter { it.kind == StageKind.WORK && !it.has(StageTrait.OPERATOR_DESK) }
val desks = frame.filter { it.has(StageTrait.OPERATOR_DESK) }
...
title = "${index + 1}. ${draft.title}"   // nomor dihitung, bukan ditulis
```

- "Program CAM" bukan konstanta lagi: ia adalah **tahap persiapan tunggal** kerangka rajut, dan
  judulnya diambil dari `displayName`-nya. Kerangka tanpa tahap persiapan kehilangan kolom itu dan
  nomornya merapat (test `frameWithoutPrepStage_shouldDropZoneAndRenumber`).

### Blok B — Kerangka efektif untuk SPK yang belum beku

```kotlin
fun SamplingOrder.effectiveFrame(tenantStages: List<StageDefinition>) = frozenStageFlow ?: tenantStages
```

SPK di SPK Masuk / Penentuan Alur **belum beku**, jadi `order.stageFrame` masih rajut default.
Tujuan seretnya harus mengikuti kerangka yang *akan* ia jalani — kerangka pabrik — kalau tidak,
SPK bordir akan menawarkan "Mulai CAM".

### Blok C — Fallback status-quo, bukan fallback senyap

```kotlin
// Gagal memuat kerangka → papan tetap rajut, persis seperti sebelum TRD-FLOW-001.
stageFlowSource.fetchTenantStages().onSuccess { stages -> _uiState.update { it.copy(stageFlow = stages) } }
```

Bedanya dengan fallback yang dilarang di repository: di sana fallback *mengubah data* (SPK bordir
jadi SPK baru); di sini fallback hanya menampilkan apa yang semua tenant lihat hari ini.

### Blok D — Label tenant boleh berisi angka

`"${stage.shortLabel} $count"` menghasilkan **"QC 2 0"** — tak terbaca. Bug ini tidak tertangkap
test mana pun; ia terlihat di tangkapan layar pertama. Perbaikannya satu pemisah: `"QC 2 · 0"`.

---

## ⚖️ 4. The "Why"

| Keputusan | Alternatif | Alasan |
|---|---|---|
| Zona = fungsi murni dari kerangka | Enum zona per template | Template baru tidak butuh kode; bisa diuji tanpa UI |
| Tabel token warna lama pindah ke test | Biarkan fungsi mati di `main` | Kode produksi tidak menyimpan fungsi yang hanya dipakai test |
| Label timeline = nama tahap | Field `timelineLabel` | Satu nama per tahap; tenant yang mengganti nama tidak melihat dua nama berbeda |
| Lembar CAM tetap dikunci ke kode CAM | Generalisasi sekarang | Lembar wajib per tahap = data Tahap 3; sekarang cukup tidak meledak untuk non-rajut |

---

## ⚠️ 5. Jebakan Pemula

1. **Canvas tidak punya DOM.** Compose/Wasm menggambar ke `<canvas>`: selector teks tidak ada, klik
   harus dengan koordinat dari tangkapan layar.
2. **Dev server mati diam-diam.** `wasmJsBrowserDevelopmentRun` lewat `nohup` langsung selesai;
   jalankan `--continuous` sebagai proses latar.
3. **Browser MCP terkunci sesi lain.** Pakai Chrome headless dengan profil sementara sendiri —
   jangan mematikan browser milik sesi lain.
4. **`git rm` mengubah index milik user.** Hapus file tanpa men-stage; commit adalah keputusan user.

---

## 🧪 6. Pembuktian

- `SamplingStageZonesTest`: kerangka rajut → enam judul, drop stage, isi R&D, dan `showActions`
  **identik** dengan enum lama; kerangka tanpa persiapan → kolom hilang & nomor merapat.
- `StageColorParityTest`: warna `colorHex` = token lama (tabelnya kini tinggal di test).
- `core` 910, `app:shared` 144 test lulus; perubahan test lama hanya fixture.
- V72/V73 teraplikasi di Postgres dev saat server dijalankan ulang; `GET /api/tenant/stage-flow` 200.
- **Visual** 1440px & 1280px (login superadmin demo): enam kolom, warna, tombol, jejak R&D sama;
  perubahan teks yang disengaja terlihat; chip "QC 2 · 0" terbaca.

---

## 🏆 7. Tantangan Mandiri

- [ ] `StageAdvanceDialog` & meja operator masih enum (R3b). Rancang meja operator yang daftarnya
      dari `stagesWith(OPERATOR_DESK)` kerangka pabrik.
- [ ] Buat tenant uji ber-kerangka bordir di DB dev dan lihat papannya — kolom apa yang muncul?
