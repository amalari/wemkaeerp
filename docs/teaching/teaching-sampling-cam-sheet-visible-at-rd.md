# Teaching — Menampilkan Lembar Program CAM di Tahap R&D (Detail SPK)

> Kasus: operator di kolom **R&D (Rajut Turun Mesin)** tidak bisa melihat data step sebelumnya —
> program CAM, instruksi panah, tenselity, dan catatan rumus pola — padahal datanya tersimpan.
> Perbaikan: lembar Program CAM kini tetap terbuka saat SPK berada di lantai R&D.

## 1. Start dari Mana? (Order of Operations)

Kalau kamu membangun ulang alur ini dari nol, urutannya:

1. **Pahami model datanya dulu.** Semua lembar kerja SPK disimpan sebagai
   `List<StageInputSection>` yang digantungkan ke satu tahap pemilik — lembar Program CAM
   disimpan di tahap `CAM_PROGRAMMING` walau SPK sudah pindah ke `MACHINE_KNITTING`
   (lihat `ConfirmStageAdvance.inputStage`). Jadi data **tidak pernah hilang** — yang salah
   hanyalah *visibilitas* UI-nya.
2. **Cari saklar visibilitasnya.** Di `SamplingSpkDetailDialog.kt`, satu variabel
   `isCamSectionVisible` menentukan apakah `CamProgramTabbedSection` dirender.
3. **Perbaiki nilai awalnya** dengan menambah kondisi tahap R&D (`isRdStage`).
4. **Audit efek samping** — efek samping apa yang terikat pada variabel yang sama? Di sini ada
   `LaunchedEffect` auto-scroll; itu harus dijaga agar tidak melompat saat dialog dibuka.
5. **Rebuild bundle dev wasm** (`:app:webApp:wasmJsDevelopmentExecutableCompileSync`) lalu
   verifikasi dengan mata di browser.

## 2. Bedah Kode Blok per Blok

### Sebelumnya (bug)

```kotlin
var isCamSectionVisible by remember(order.id, initialShowCamSection, isCamStage) {
    mutableStateOf(initialShowCamSection || isCamStage)
}
```

Di tahap R&D: `isCamStage = false`, dan `initialShowCamSection` (dari `focusCam`) juga `false`
karena `focusCam = true` hanya dikirim saat kartu **masuk** ke kolom Program CAM
(`SamplingWorkspaceScreen.kt`). Akibatnya section CAM tidak dirender sama sekali — operator R&D
hanya melihat nama bagian + kode program di `RdResultSection` (baris "TEST / Test").

**Bukti bahwa ini wiring setengah jadi, bukan desain:** `onSectionsChange` di dialog sudah
menulis `if (isCamStage || isRdStage) onDraftChange(it)` — autosave lembar CAM di tahap R&D
sudah diantisipasi, tapi section-nya tidak pernah tampil sehingga cabang `isRdStage` mati.

### Sesudah (fix)

```kotlin
// Lembar Program CAM tetap terbuka di lantai R&D (rajut s/d kemas)...
var isCamSectionVisible by remember(order.id, initialShowCamSection, isCamStage, isRdStage) {
    mutableStateOf(initialShowCamSection || isCamStage || isRdStage)
}
```

Mental model: **satu lembar teknis per SPK**. CAM mengisinya, R&D melanjutkan isinya
(gramasi, waktu, ukuran jadi disimpan di lembar yang sama lewat `serializeCamSections`).
Karena parser (`parseCamSections`) dan serializer round-trip dengan aman, dua editor
(`CamProgramTabbedSection` dan `RdResultSection`) bisa berbagi state `camSections` tanpa saling
menghapus data — masing-masing mem-preserve field milik pihak lain.

### Efek samping yang dijaga

```kotlin
LaunchedEffect(isCamSectionVisible) {
    if (isCamSectionVisible && !isCamStage && !isRdStage) { ... scrollTo(max) }
}
```

Auto-scroll ke lembar CAM hanya relevan di gerbang awal (saat section dibuka lewat aksi
"Mulai CAM"). Di tahap CAM/R&D section sudah tampil sejak awal — tanpa guard `!isRdStage`,
dialog R&D akan melompat ke dasar dokumen setiap kali dibuka.

## 3. Technology & Approach ("The Why")

- **Kenapa tidak membuat UI referensi read-only baru?** Karena lembar CAM yang sama sudah
  punya autosave, validasi, dan serializer. Menggunakan ulang satu sumber kebenaran lebih
  murah daripada membuat mode tampil kedua yang cepat geser sinkronisasi.
- **Kenapa data tetap disimpan di tahap CAM?** Agar riwayat lembar teknis stabil: tahap SPK
  berpindah mengikuti lantai produksi, tapi lembar CAM adalah dokumen teknis yang hidup dari
  CAM s/d kemas. Memindahkan penyimpanannya akan memecah audit dan draft autosave.
- **Risiko kalau cara lain dipakai:** menyalin `sections` ke tahap R&D saat pindah tahap
  membuat dua salinan yang bisa divergen (edit CAM setelah pindah tidak terlihat di R&D).

## 4. Jebakan Pemula (Common Pitfalls)

1. **Mengira data hilang padahal UI-nya yang disembunyikan.** Selalu telusuri dari sumber
   data (`order.stageInputFor(CAM_PROGRAMMING)`) sebelum menambah "fitur simpan ulang".
2. **Lupa `remember` key.** Menambah kondisi ke nilai awal tanpa menambah `isRdStage` sebagai
   key `remember` membuat state basi saat recomposition dengan tahap berbeda.
3. **Auto-scroll yang ikut aktif.** `LaunchedEffect(true)` pada nilai yang sudah `true` sejak
   komposisi pertama tetap jalan sekali — guard eksplisit per tahap itu wajib.
4. **Dev server wasm tidak otomatis rebuild.** Webpack dev server hanya menyajikan output
   `build/wasm/packages/...`; kalau continuous build mati, jalankan manual:
   `./gradlew :app:webApp:wasmJsDevelopmentExecutableCompileSync`.

## 5. Verifikasi & Tantangan Mandiri

- `./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs :app:shared:jvmTest`
  harus hijau (target Android dilewati di mesin tanpa Android SDK).
- Dengan mata: buka kartu SPK di kolom R&D → dialog harus menampilkan section
  **PROGRAM CAM** (tab per bagian, kode program, instruksi panah, tenselity, catatan rumus pola)
  **di atas** section **HASIL R&D**; posisi scroll tetap di atas saat dialog dibuka.
- Tantangan: edit instruksi panah dari dialog R&D, tunggu indikator "Tersimpan", lalu buka
  SPK yang sama dari kolom Program CAM (buat SPK baru jika perlu) — isinya harus sama, karena
  keduanya menulis ke lembar CAM yang sama.
