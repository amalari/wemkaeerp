# Teaching: Kartu SPK A6 dengan QR & Strip Urgensi Berbasis Slack

> Untuk junior developer yang mau memahami fitur ini dari nol. Konteksnya: setiap SPK sampling
> sekarang punya kartu A6 (105 × 148 mm) yang dicetak dan menempel di tiap section produksi —
> dengan QR menuju halaman telusur, ukuran client & hasil ukur tim sampling, kode warna benang,
> dan strip warna urgensi di tepi bawah.

## 1. Mulai dari mana? (Urutan menulis)

Fitur ini menyentuh 4 lapis. **Urutan penulisannya penting** karena setiap lapis hanya boleh
bergantung pada lapis di bawahnya:

```
1. core/.../sampling/SpkUrgency.kt          ← rumus murni, tanpa framework
2. core/.../traceability/print/
   ├── SpkCardContent.kt                    ← data apa yang tercetak
   └── SpkCardLayout.kt                     ← geometri Mm10, di mana ia tercetak
3. server/.../traceability/SpkCardBuilder.kt ← sampling order → content
   server/.../pdf/SpkCardPdfRenderer.kt     ← content+layout → byte PDF
   server/.../routes/TraceabilityPrintRoutes.kt
4. app/shared/.../TraceabilityApiClient.kt + 2 tombol Compose
```

Mulailah dari domain karena ia bisa dites tanpa PDF, tanpa server, tanpa UI. Kalau rumusnya sudah
dikunci test, semua lapis di atasnya tinggal "memasok data dan menggambar".

## 2. Bedah kode blok per blok

### Rumus slack (`assessUrgency`)

```kotlin
val daysLeft = deadline.toEpochDays() - today.toEpochDays()
val remainingMinutes = input.totalStdMinutes * profile.remainingFactor(input.stage)
val neededDays = ceil(remainingMinutes * input.qtyPcs / capacity.minutesPerDay)
val slack = daysLeft - neededDays
```

Mental model: **slack = sisa napas**. Bukan "berapa hari lagi deadline", tapi "setelah kerja
selesai, masih sisa berapa hari". Contoh yang melahirkan rumus ini:

- SPK A deadline besok, qty kecil → butuh 1 hari → slack 0 → **SEGERA**
- SPK B deadline lusa, qty besar → butuh 2 hari → slack 0 → **SEGERA juga!**

Deadline B lebih jauh tapi qty-nya makan lead time-nya. Sortir by slack ascending = urutan
prioritas antrean; itulah yang tercetak sebagai `PRIORITAS 2/14` di kartu.

### Kenapa `remainingFactor(stage)`?

444 menit standar adalah beban **dari awal sampai akhir**. SPK yang sudah di *Setrika Uap* tinggal
mengerjakan ~15% sisanya. Faktor per tahap (1,00 → 0,00) mengubah "total menit" menjadi "sisa
menit". Faktornya di belakang interface `StageWorkProfile` — begitu data nyata terkumpul dari
`StageTransitionAudit` (durasi kerja nyata per tahap), profil terukur tinggal menggantikan
`DefaultStageWorkProfile` di satu titik wiring, tanpa menyentuh rumus/kartu/test.

### Kenapa urgensi TIDAK disimpan ke database?

Slack itu *pembacaan momen* — angka yang benar hari Senin basi hari Kamis. `SpkCardBuilder`
memuat seluruh SPK aktif tenant (satu query), menilai semuanya in-memory, dan posisi SPK ini
menjadi peringkatnya. Cetak ulang = segar lagi. QR tetap pintu ke status live.

### Kenapa geometri di domain, bukan di renderer? (`SpkCardLayout`)

Pola yang sama dengan `TraceLabelSheetLayout`. Angka `Mm10` (1/10 mm, integer) dihitung di domain
supaya **bisa dites tanpa membuka PDF**: test memastikan semua rect di dalam kertas, grid tidak
menabrak strip, QR ≥ 25 mm, dan baris meluap menjadi ringkasan (`+N ukuran lainnya`) — bukan
terpotong diam-diam. Renderer pdfbox hanya "mengisi" rect yang sudah dijamin aman.

### Kenapa grid dua kolom?

Tabel ukuran sampling bisa 11+ baris (label bebas dari lembar CAM). Satu kolom × 11 baris × 4,5 mm
= 49,5 mm — masih muat, tapi tanpa ruang cadangan. Grid 2 kolom memotongnya jadi 6 baris sehingga
A6 punya ~40 mm napas. `chunked(2)` + `take(MAX * 2)` + hitung overflow adalah seluruh triknya.

### Strip urgensi

```kotlin
val (r, g, b) = stripColor(content.urgencyLevel)
cs.setNonStrokingColor(r, g, b)   // isi band
// ... teks putih bold di tengah, diposisikan lewat getStringWidth
```

Warna mengikuti bahasa sinyal produksi (merah/amber/hijau). Teks level (`URGENT · PRIORITAS 2/14
· DL 30-09 · SISA −1 HK`) selalu ikut tercetak supaya printer hitam-putih tetap menyampaikan
urgensinya — jangan pernah mengandalkan warna saja di kertas.

## 3. Technology & Approach ("The Why")

| Keputusan | Alternatif yang ditolak | Risiko kalau dipakai alternatif |
|---|---|---|
| Slack Time Remaining | Ambang hari tetap (≤3 hari = amber) | SPK besar yang harus mulai duluan justru tampak "hijau" |
| Hitung urgensi per klik, tidak disimpan | Simpan kolom `urgency` di DB | Angka basi + kolom yang terus menyesatkan |
| QR tier WORKSHEET yang sama dengan lembar kerja | Skema QR baru khusus kartu | Operator harus belajar cara memindai kedua |
| Geometri di domain (Mm10) | Angka hardcode di renderer pdfbox | Tidak bisa dites; layout pecah diam-diam saat diubah |
| `StageWorkProfile` interface | Map faktor ditanam di fungsi | Kalibrasi data nyata = refactor merata |
| Integer Mm10 | Float mm | Drift pembulatan JS/Wasm vs JVM → posisi geser mikro |

## 4. Jebakan Pemula (Common Pitfalls)

1. **`kotlin.math.ceil`** — di commonMain tidak ada import otomatis; `Math.ceil` (java) dilarang di
   common KMP.
2. **Extension property butuh import** — `isQtyRow` hidup di `SamplingSizeMatrix.kt`; dipakai dari
   package `infrastructure.traceability` wajib `import ...sampling.isQtyRow` walau package-nya
   "kelihatan" terbuka.
3. **`when` non-exhaustive di enum yang ditambah** — menambah `PaperSize.A6` memecah
   `InvoicePdfRenderer` yang mengekshaus `PaperSize`. Menambah nilai enum = grep semua `when`-nya.
4. **`setNonStrokingColor(FloatArray)` tidak ada** — pdfbox punya overload (Float,Float,Float);
   spread `*array` tidak match. Destructure dulu.
5. **Inkremental kompilasi bisa berbohong** — error "syntax" yang tak bisa dijelaskan setelah
   stash/pop: coba `--rerun-tasks --no-build-cache` sebelum mendiagnosis kode.
6. **Baris bebas = wajib cap.** Label ukuran dari lembar CAM bebas jumlahnya; layout yang tidak
   memasang cap akan meluap halaman pada data pertama yang "tidak wajar" — dan itu data yang
   justru nyata.

## 5. Verifikasi & Tantangan Mandiri

Yang sudah dijalankan:

- [x] `:core:jvmTest` — 12 test baru (rumus slack + geometri) hijau
- [x] `:server:test` — 4 test baru, termasuk **decode QR dari PDF ter-render 300 DPI** kembali ke
      URL scan yang benar (`SpkCardPdfRendererTest`)
- [x] Kompilasi JVM + WasmJS + JS + `:app:shared:jvmTest` hijau
      (Android target tidak bisa dijalankan di mesin ini — tidak ada SDK; source set-nya sama
      dengan commonMain yang sudah terkompilasi 3×)
- [x] Verifikasi visual: PNG hasil render dicek pikselnya — strip merah `≈ DC2626` di tepi bawah,
      QR hitam di kuadran kanan-atas, teks di tempatnya

Tantangan untukmu:

1. Tambahkan test: SPK dengan `deadlineDelivery` null tapi `deadlineFinishing` terisi — pastikan
   builder jatuh ke yang benar.
2. Implementasikan `MeasuredStageWorkProfile`: baca `StageTransitionAudit`, ambil **median** durasi
   `transisiBerikutnya.at − workStartedAt` per tahap, turunkan faktor, pakai default bila sampel
   < 5. Sambungkan di `ServerRouteWiring` — satu baris.
3. Cetak kartunya di printer kantor dan pindai QR-nya dengan HP dari ~30 cm. Tidak ada test yang
   bisa menggantikan langkah ini — sama seperti catatan di `TracePrintRendererTest`.


