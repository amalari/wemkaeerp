# 🎓 Modul Pembelajaran: Idempotensi Transisi Tahap Deal & Relokasi Toaster Dialog Deal

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design (idempotensi use case), stale state klien vs sumber kebenaran server, Compose Multiplatform overlay/toaster, Design System Clay
> **Prasyarat**: Pahami struktur lapisan DDD (`core` → `server` → `app/shared`), dasar Compose (`Box`, `Column`, `AnimatedVisibility`), dan alur event MVI (`UiState`/`UiEvent`)
> **Referensi Task**: Bugfix — HTTP 400 "Tidak bisa memindahkan deal dari PO Diterima ke PO Diterima" saat menerbitkan SPK Sampling + toaster inline yang menggeser layout

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah Nyata**: Saat admin menerbitkan SPK Sampling dari dialog deal, sistem otomatis menggeser deal ke `PO_RECEIVED`. Keberhasilan SPK muncul hijau, tapi langsung diikuti toast merah "Gagal mengubah tahap deal (HTTP 400): Tidak bisa memindahkan deal dari PO Diterima ke PO Diterima". User bingung: SPK-nya jadi atau tidak?
- **Akar Masalah**: *Stale state*. Klien mengecek `deal.stage == OPEN` dari salinan lokal sebelum meminta server menggeser stage. Salinan itu bisa kedaluwarsa (deal sudah digeser ke `PO_RECEIVED` oleh sesi/aksi sebelumnya), sehingga server menerima permintaan `PO_RECEIVED → PO_RECEIVED` dan domain menolaknya (`canTransitionTo = this != target`).
- **Analogi Sederhana**: Kamu menyuruh satpam "pastikan lampu ruangan nyala", padahal lampunya sudah nyala. Satpam yang kaku menjawab "saya tidak bisa menyalakan lampu yang sudah nyala" — padahal hasil akhirnya persis seperti permintaanmu.
- **Hasil Akhir**: Permintaan "pastikan deal berada di tahap X" bersifat **idempoten** — dipanggil sekali atau lima kali, hasilnya sama: sukses. Ditambah toaster mengambang yang tidak lagi menggeser layout konten dialog.

## 🧭 2. "Start dari Mana?" — Order of Operations

1. **Langkah 0: Reproduksi & telusuri rantai pemanggilan.** Mulai dari pesan error (`Gagal mengubah tahap deal (HTTP 400)`) di `DealApiClient.requireBody(...)` → naik ke `DealViewModel.changeStage(...)` → pemicunya `createSamplingSpk()` yang memanggil `changeStage(PO_RECEIVED)` setelah SPK sukses.
2. **Langkah 1: Putuskan lapisan perbaikan.** Domain invariant `canTransitionTo` melarang transisi illegal — itu benar dan tidak boleh dilemahkan. Yang salah adalah **menafsirkan** "pastikan berada di tahap X" sebagai "transisi ke tahap X". Perbaikannya di **use case** (`UpdateDealStageUseCase`), bukan di route, bukan pula di ViewModel.
3. **Langkah 2: Buat use case idempoten.** Jika `existing.stage == newStage`, kembalikan `existing` apa adanya (no-op sukses, tanpa tulis ulang DB).
4. **Langkah 3: Rapikan sisi presentasi.** Toaster sukses/error yang tadinya teks inline di bawah konten (menggeser layout) diangkat menjadi overlay mengambang `DealStatusToast`.
5. **Langkah 4: Verifikasi multi-target** — JVM (core, shared, server), WasmJS, JS, dan `jvmTest`.

## 🔍 3. Bedah Kode Blok per Blok

### A. `core/.../usecases/UpdateDealStageUseCase.kt` — no-op yang menyelamatkan

```kotlin
// Idempoten: permintaan ke tahap yang SAMA dianggap sukses tanpa menulis ulang.
if (existing.stage == newStage) return@runCatching existing
val transitioned = existing.transitionTo(newStage, Clock.System.now()).getOrThrow()
dealRepository.save(transitioned).getOrThrow()
```

- **Mental model**: use case semula bermakna "*pindahkan* deal ke tahap X" (gagal jika sudah di X). Sekarang: "*pastikan* deal berada di tahap X" (sukses jika sudah di X). Kalimat kedua inilah yang dimaksud semua pemanggil otomatis.
- **Kenapa no-op, bukan membolehkan `canTransitionTo(this, this)`?** Invariant domain itu dipakai untuk menangkap bug nyata (tombol UI yang salah kirim stage). Melemahkannya = mematikan alarm. No-op di use case mempersempit makna tanpa menyentuh invariant.
- **Kenapa tidak `save(existing)`?** Menulis ulang data sama memicu `updatedAt` baru & write sia-sia. No-op murni lebih murah dan bebas efek samping.

### B. `presentation/deal/components/DealStatusToast.kt` — toaster mengambang baru

- **Struktur**: `Box(contentAlignment = TopCenter)` + `AnimatedVisibility(fade)` + `Column` berisi hingga dua pil (`DealToastPill`) — pil sukses (`SuccessBg`/`Success`) dan error (`ErrorBg`/`Error`) bisa tampil berdampingan, persis skenario di screenshot.
- **Auto-dismiss**: `LaunchedEffect(statusMessage, error) { delay(4_000); ... }`; klik pada pil menutup lebih cepat. Event `DismissStatusMessage`/`DismissError` **sudah ada** di `DealUiEvent` — tinggal dipakai, tidak menambah state baru.
- **Kepatuhan design system**: nol literal warna (token `WeMadeColors`), bentuk lewat `ClayShapes.Card` + `clayFlat`, warna diteruskan ke `Text` (tidak ditanam di `TextStyle` — Kontrak 9), `maxLines` + `Ellipsis` + `widthIn` (Kontrak 13).
- **Kenapa file terpisah?** `DealDetailDialog.kt` (2408 baris) jauh di atas hard limit 600 dan tunduk **aturan ratchet**: setiap perubahan wajib membuatnya tidak lebih panjang. Komponen baru hidup di file sendiri; dialog hanya menambah satu panggilan.

### C. `DealDetailDialog.kt` — dari teks inline ke overlay


## ⚙️ 4. Technology & Approach — "The Why"

- **Kenapa perbaiki di use case (domain), bukan di klien?** Klien hanya satu dari banyak pemanggil; apa pun yang membuat state klien stale (tab lain, sesi lain, refresh tertunda) memicu bug yang sama. Idempotensi di server menyembuhkan **semua** klien sekaligus.
- **Kenapa bukan try-catch pesan error di klien?** String matching ("dari PO Diterima ke PO Diterima") rapuh terhadap perubahan copywriting dan menutupi kegagalan asli. No-op eksplisit jujur secara semantik.
- **Kenapa `AnimatedVisibility`, bukan `if (visible)`?** Transisi fade mencegah toast "berkedip"; selama exit animation pil masih tergambar tapi tidak memaksa layout (ia di dalam `Box` overlay).
- **Trade-off yang diakui**: jendela stale state singkat tetap ada (klien mengirim stage berdasar salinan lama). Untuk aksi otomatis seperti ini no-op server cukup; untuk stage sensitif bisnis (mis. `WON`), jangan andalkan state lokal — ambil stage terkini dulu.

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Melemahkan invariant domain demi memadamkan error.** `canTransitionTo = this != target` itu alarm; membiarkan same-stage di fungsi itu membuat semua penjaga lain tuli.
2. **Menyembuhkan gejala di UI saat penyakitnya di kontrak API.** Kalau diperbaiki hanya di `DealViewModel`, layar/sesi lain tetap 400.
3. **Menanam warna di `TextStyle`** saat membuat toast — mematikan `LocalContentColor` (Kontrak 9).
4. **Melanggar ratchet file besar.** Menambah 10 baris ke file 2400 baris tetap pelanggaran; angkat komponen baru ke file sendiri.
5. **Lupa auto-dismiss** — toast yang tidak pernah hilang menumpuk dan menutupi konten.

## ✅ 6. Verifikasi & Cara Menguji

- Kompilasi hijau: `:core:compileKotlinJvm`, `:app:shared:compileKotlinJvm`, `:server:compileKotlin`, `:app:shared:compileKotlinWasmJs`, `:app:shared:compileKotlinJs`, `:app:shared:jvmTest`.
- **Catatan**: `:app:shared:compileAndroidMain` gagal **sebelum** perubahan ini (utang pre-existing: `MockupCropDialog.kt` memakai referensi Skia langsung di `commonMain` yang tidak resolve di target Android) — file itu tidak tersentuh task ini dan layak jadi task terpisah.
- Uji manual: buka deal → Tab Sampling → terbitkan SPK dua kali berturut-turut. Kedua kali harus: toast hijau di tengah-atas, hilang sendiri ± 4 detik, tanpa toast merah "PO Diterima ke PO Diterima", layout tab tidak terdorong.
- Uji toleransi: selagi dialog terbuka, geser stage deal dari sesi lain, lalu terbitkan SPK — tidak boleh 400.

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Unit test `UpdateDealStageUseCase`: same-stage → sukses tanpa memanggil `repository.save`; transisi ilegal → tetap gagal (fake repository cukup — domain murni).
- [ ] **Tantangan 2**: Pola toast kini ada di ≥3 layar (RBAC `ToastAlertBanner`, OrgChart `ToastBanner`, Deal `DealStatusToast`). Sesuai Aturan Tiga Kali, angkat pil toast ke `presentation/designsystem/` sebagai komponen netral (`text: String`, `tint: Color`, `background: Color`), lalu migrasikan ketiganya.
- [ ] **Tantangan 3**: Selidiki kenapa `deal.stage` di klien bisa stale — apakah aksi lintas modul (PO upload, penerbitan SPK massal) mengubah stage tanpa menyegarkan state deal di dialog? Usulkan satu titik refresh setelah aksi tersebut.

- Sebelumnya di dasar `DealDetailContent`: dua blok `state.statusMessage?.let { Text(...) }` / `state.error?.let { Text(...) }`. Keduanya jadi anak `Column` — **ikut mengambil ruang layout**, mendorong konten tab, dan muncul "di situ" (tempat yang dikeluhkan user).
- Sesudahnya: `Column` dibungkus `Box(Modifier.fillMaxSize())`, lalu `DealStatusToast(...)` dipanggil dengan `Modifier.align(Alignment.TopCenter)` — digambar **di atas** konten tanpa memengaruhi ukuran apa pun.
- Bonus ratchet: total baris berkurang 2410 → 2408 (blok inline 8 baris dibongkar, panggilan overlay + pembungkus Box lebih hemat).

