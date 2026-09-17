# Teaching — Badge Hasil QC di Kartu Kanban Sampling (Kolom Read-Only)

> Konteks: Setelah refactor "Pipeline Kanban sebagai landing view + scope berakhir di turun mesin",
> kolom **"Di Meja Finishing & QC"** bersifat read-only (`showActions = false`). Masalahnya: kartu
> yang **gagal QC (Rework/Reject)** tampil **identik** dengan kartu yang belum pernah diperiksa.
> Task ini menambahkan badge informasi hasil QC terakhir pada kartu — tanpa menambah aksi apa pun.

## 1. Start dari mana? (Order of Operations)

Kalau menulis dari nol, urutannya:

1. **Pastikan datanya sudah ada di domain** — jangan pernah menambah UI untuk data yang belum ada.
   Di sini kita beruntung: `SamplingOrder.latestQcReport` (`SamplingOrder.kt:83`) sudah ada:
   ```kotlin
   val latestQcReport: QcInspectionReport? get() = qcInspections.lastOrNull()
   ```
   dan enum `QcInspectionResult` (`SamplingOrderValueObjects.kt:84`) sudah punya
   `PASSED / REWORK / REJECT`. **Nol perubahan domain, nol migrasi DB.**
2. **Cari titik render yang tepat** — bukan di header kolom, tapi di kartu, persis di cabang
   `else` dari `if (showActions)` di `SamplingPipelineKanbanBoard.kt` (fungsi kartu privat).
   Kenapa di sana? Karena itulah satu-satunya jalur yang dilalui kartu read-only.
3. **Pakai komponen katalog** — `ClayBadge` (sudah dipakai kartu lain di file yang sama) +
   token warna `WeMadeColors.Warning` / `WeMadeColors.Error` (sama dengan pola badge
   "Hasil QC Terakhir" di `SamplingDesktopWorkbench.kt:378-388`). Nol literal warna baru.
4. **Kompilasi multi-target** — `compileKotlinJvm` + `compileKotlinWasmJs` minimal, karena file ini
   common main dan dipakai Wasm.

## 2. Bedah Kode Blok per Blok

```kotlin
val qcResult = order.latestQcReport?.qcResult
if (qcResult == QcInspectionResult.REWORK || qcResult == QcInspectionResult.REJECT) {
    ClayBadge(
        text = if (qcResult == QcInspectionResult.REJECT) "QC: Rajut Ulang" else "QC: Perbaikan Ulang",
        tint = if (qcResult == QcInspectionResult.REJECT) WeMadeColors.Error else WeMadeColors.Warning,
        modifier = Modifier.fillMaxWidth()
    )
}
```

- **`order.latestQcReport?.qcResult`** — safe call + `null` berarti "belum pernah di-QC" → tanpa
  badge. Ingat: `qcInspections` adalah `List`, jadi `lastOrNull()` = inspeksi terbaru. Jika QC
  memeriksa 3 kali (REWORK → REWORK → PASSED), badge mengikuti hasil **terakhir**.
- **Kenapa `PASSED` tidak diberi badge?** Karena `completeQcInspection()` langsung memindahkan
  kartu `PASSED` di stage `FINISHING_QC` ke `IN_DELIVERY` (kolom "Tunggu ACC Buyer"). Kartu
  `PASSED` nyaris tidak pernah menginap di kolom ini — badge hijau hanya akan jadi noise.
  Prinsipnya: **badge exception, bukan badge status normal** (sinyal produksi harus kontras).
- **Kenapa teksnya pendek ("QC: Rajut Ulang") bukan `displayName` penuh?** `displayName` enum-nya
  panjang — `"Ditolak / Rajut Ulang (Reject)"`. Di kartu 300dp dengan outline clay 3dp + hard
  shadow 6dp, badge lebar-penuh dengan teks sepanjang itu berisiko wrap dua baris. Kontrak 12:
  clay memakan ruang — teks badge dipadatkan, makna tetap utuh.
- **`tint` membedakan state, bukan ketebalan/bentuk** (Kontrak 8): amber = perlu perbaikan di meja
  finishing, merah = gagal total harus rajut ulang. Bentuk badge-nya sama persis.

## 3. Technology & Approach ("The Why")

- **Kenapa tidak menambah field `qcStatus` baru di UiState?** Redundant — `UiState.orders` sudah
  membawa entity utuh; computed property di domain (`latestQcReport`) adalah *single source of
  truth*. Menyalinnya ke UiState = dua sumber kebenaran yang bisa saling basi.
- **Kenapa tidak menampilkan badge ini juga di kolom aksi (1–3)?** Karena QC hanya terjadi di
  ranah finishing (`FINISHING_QC`). Secara domain mustahil kartu di `NEW_INTAKE` punya laporan QC;
  kalau punya (data kotor), lebih baik tidak divisualisasikan di kolom yang salah.
- **Risiko kalau cara lain dipakai**: menaruh logika "ambil QC terakhir" di ViewModel berarti
  memindahkan pengetahuan domain ke presentation (anemic domain model). Menaruhnya di komponen
  design system (`ClayBadge` tahu soal `QcInspectionResult`) melanggar Kontrak 6 — komponen
  bersama harus buta domain. Posisi sekarang (komponen fitur membaca domain, menerjemahkan ke
  `String` + `Color` untuk `ClayBadge`) adalah titik tengah yang benar.

## 4. Jebakan Pemula (Common Pitfalls)

1. **Memberi badge di cabang `showActions = true` juga** — kartu `IN_DELIVERY` bisa saja punya
   riwayat QC lama `REWORK` dari revisi sebelumnya; badge merah di kolom ACC akan menyesatkan
   ("SPK ini bermasalah") padahal sudah lolos dan menunggu keputusan buyer.
2. **Memakai `qcInspections.firstOrNull()`** — badge akan selamanya menunjukkan hasil inspeksi
   pertama; kartu yang sudah diperbaiki tetap tampak "Rework".
3. **Nambah literal `Color(0xFFFF...)`** untuk amber/merah — melanggar Kontrak 1; tokennya sudah
   ada (`WeMadeColors.Warning` / `Error`), dipakai workbench dengan semantik identik.
4. **Mengubah `showActions` jadi semi-aktif "cuma untuk badge"** — jangan. Badge ini murni
   `showActions = false` + informasi; begitu kamu menambah satu tombol "kecil", batas ranah
   divisi yang barusan dipagari mulai bocor lagi.

## 5. Verifikasi & Tantangan Mandiri

- [x] `./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs` → BUILD SUCCESSFUL
- [ ] Visual: buat SPK lolos ke FINISHING_QC, submit QC report `REWORK` lewat layar QC Inspector,
      refresh `/sampling-order` → kartu di kolom ke-4 harus muncul badge kuning "QC: Perbaikan
      Ulang" di atas badge stage teal.
- [ ] Submit ulang QC dengan `PASSED` → kartu pindah ke kolom "Tunggu ACC Buyer" **tanpa** badge.
- [ ] Tantangan: badge yang sama layakkah ditampilkan di workbench mobile
      (`SamplingMobileWorkbench.kt`)? Cek — apakah di sana sudah ada "Hasil QC Terakhir"?
      Kalau belum, itu pemakaian ke-3 dari pola yang sama → Kontrak 4 (Rule of Three) mulai
      berbunyi: pertimbangkan `QcResultPill(qcResult: QcInspectionResult?)` di level fitur.

