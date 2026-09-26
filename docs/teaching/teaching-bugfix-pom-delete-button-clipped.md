# 🎓 Modul Pembelajaran: Bugfix — Tombol Hapus (X) Baris Size Chart/POM Tidak Terlihat

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform Layout, `horizontalScroll` + `fillMaxWidth`, fixed-width row math, clipping
> **Prasyarat**: Paham dasar Compose `Row`/`Box`/`Modifier`, konsep constraint (min/max width), dan design system Clay repo ini
> **Referensi Task**: Bugfix laporan QA — "di Deals bagian Size Chart / POM, X di tiap row-nya ga keliatan"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah Nyata**: Di dialog Deal Detail → kartu desain sampling → tabel *Size Chart / POM*, setiap baris POM (Lebar Dada, Panjang Baju, dst.) seharusnya punya tombol hapus (ikon X) di ujung kanan. Di lapangan, X itu tidak terlihat — yang muncul hanya serpihan kecil seperti "›" di tepi kanan panel.

**Analogi Sederhana**: Bayangkan Anda menata deretan kotak di atas meja yang panjangnya pas-pasan. Kotak terakhir sedikit lebih panjang dari meja, dan meja berada di dalam ruangan berdinding (viewport). Yang terlihat dari luar hanyalah 2–3 mm ujung kotak terakhir — orang mengira itu ornamen, padahal itu pintu.

**Mental model inti**: `Modifier.fillMaxWidth().horizontalScroll(state)` **bukan** "biarkan konten melebar sesukanya". Artinya:

1. `fillMaxWidth` → *viewport* Box dikunci selebar parent-nya.
2. `horizontalScroll` → *child* diukur dengan lebar tak terbatas (unbounded), jadi konten boleh lebih lebar dari viewport.
3. Sisa konten di luar viewport **terpotong (clipped)**, dan hanya bisa dilihat dengan scroll.

Konsekuensinya: kalau total lebar konten hanya meluber 5–20dp, elemen paling kanan (di kasus ini tombol hapus 24dp) terpotong sebagian atau seluruhnya, dan karena rentang scroll-nya cuma segitu, pengguna tidak sadar ada konten tersembunyi. **Bug "elemen hilang" klasik akibat aritmetika lebar baris yang fixed.**

---

## 🧭 2. "Start dari Mana?" — Alur Diagnosis (Order of Operations)

1. **Langkah 0: Reproduksi visual dulu.** Screenshot menunjukkan simbol "›" di ujung kanan setiap baris data, tapi tidak di baris header. Petunjuk emas: elemen yang hanya ada di baris data = tombol hapus. Berarti yang terlihat itu *serpihan* tombolnya, bukan ikon lain.
2. **Langkah 1: Temukan komponennya.** Grep string UI ("Tambah Ukuran", "Bagian / POM") → `SamplingSizeChartTable` di `presentation/deal/components/DealDetailDialog.kt`.
3. **Langkah 2: Hitung aritmetika lebarnya** (jangan menebak!):
   - Baris header: padding `Sm`(6)×2 + label `115dp` + 7 kolom × `52dp` + spacer `28dp` = **519dp**
   - Baris data: padding `Sm`(6)×2 + label `115dp` + 7 sel × (`48dp`+4 padding) + tombol `24dp` = **515dp**
   - Viewport: kolom kanan dialog (lebar dialog − 200dp kolom foto − gap) ≈ 510–515dp → **konten meluber beberapa dp**.
4. **Langkah 3: Perbaiki di sumber angkanya**, bukan menambal simptom.
5. **Langkah 4: Verifikasi mata + kompilasi 5 target.**

---

## 🔍 3. Bedah Kode Blok per Blok

### Blok A — Kontainer scrollable (biarkan tetap)

```kotlin
Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
    Column { /* header row + data rows */ }
}
```

Pola ini **sah dan benar** — tabel memang harus bisa scroll kalau layar sempit. Yang salah bukan kontainernya, melainkan angka-angka di dalamnya yang membuat konten *pas-pasan meluber* di lebar normal. Scroll seharusnya "safety net", bukan "tempat menyembunyikan tombol".

### Blok B — Baris header vs baris data (harus sinkron per kolom)

Sebelum fix, ada dua ketidakselarasan:

| Elemen | Header | Data | Masalah |
|---|---|---|---|
| Label POM | 115dp | 115dp | ✓ sama, tapi terlalu lebar untuk teks 11sp |
| Kolom ukuran | 52dp | 48dp + 4 padding = 52dp | ✓ total sama |
| Ruang hapus | Spacer 28dp | Box 24dp | 4dp tidak sinkron (kosong kecil di header) |

Perbaikannya mengecilkan semua angka agar total konten (~472dp) muat nyaman di viewport terkecil yang wajar:

```kotlin
// header: label 100dp, kolom 48dp, spacer 24dp
// data:   label 100dp, sel 44dp + 4 padding = 48dp, tombol 24dp
```

**Aturan yang harus dipegang**: setiap kolom di header dan di baris data harus punya *total lebar yang identik*, dan spacer ruang hapus harus persis selebar tombolnya. Selisih beberapa dp inilah yang dulu membuat baris "bergoyang" secara halus dan tombol kegeser.

### Blok C — Tombol hapus yang terlihat

```kotlin
// ❌ sebelum: ikon 12dp warna muted → bahkan saat terlihat pun nyaris tak kasat
IconClose(modifier = Modifier.size(12.dp), color = WeMadeColors.OnSurfaceMuted)

// ✅ sesudah: 14dp dengan warna OnSurface — tetap hit area 24dp
IconClose(modifier = Modifier.size(14.dp), color = WeMadeColors.OnSurface)
```

Catatan design system: state dibedakan lewat **warna**, bukan ketebalan/ukuran. Hit area tetap 24dp (standar sentuh minimum), hanya gambarnya yang diperjelas.

---

## 🏗️ 4. Technology & Approach ("The Why")

---

## ✅ 6. Verifikasi

- Kompilasi hijau: `:app:shared:compileKotlinJvm`, `compileKotlinWasmJs`, `compileKotlinJs`, `jvmTest` — semuanya `BUILD SUCCESSFUL`.
- Target Android (`compileAndroidMain`) saat ini **sudah gagal sebelum perubahan ini** karena `MockupCropDialog.kt` memakai `org.jetbrains.skia.*` di commonMain yang tidak resolve di Android — utang terpisah yang tidak disentuh bugfix ini.
- Verifikasi visual (wajib, aturan design system §7): buka `localhost:3000/crm-sales/deals` → buka deal → kartu desain → pastikan X sekarang utuh di ujung kanan tiap baris POM dan bisa diklik (baris terhapus).

## 🏆 7. Tantangan Mandiri

- [ ] Ekstrak lebar kolom tabel (`100.dp`, `48.dp`, `24.dp`) menjadi konstanta bernama di atas `SamplingSizeChartTable` agar header dan data tidak bisa tidak sinkron lagi.
- [ ] Tambahkan `Modifier.widthIn(min = ...)` pada panel Size Chart dan uji di lebar sempit (~1100dp): apakah scroll muncul dan X tetap bisa dicapai dengan scroll?
- [ ] Periksa tabel lain di file yang sama (`SamplingQuantityTable`): apakah punya pola rawan yang sama (konten fixed-width di dalam `horizontalScroll`)?


- **Kenapa tidak memakai `weight(1f)` untuk kolom?** Karena header dan baris data adalah `Row` terpisah di dalam `Column` yang di-scroll horizontal. `weight` hanya berlaku dalam satu `Row` — dia tidak bisa menyinkronkan lebar kolom *antar* Row. Untuk tabel matriks seperti ini, lebar fixed per kolom + perhitungan manual adalah pendekatan yang jujur; yang wajib adalah menjaga sinkronnya lewat angka yang sama (idealnya diekstrak ke konstanta bila tabelnya makin kompleks).
- **Kenapa tidak menghapus `horizontalScroll`?** Karena dialog bisa dibuka di lebar sempit (aturan design system: uji di ~1280dp dan lebih sempit). Scroll tetap dibutuhkan sebagai fallback; perbaikannya adalah memastikan pada lebar normal *tidak ada* yang terpotong.
- **Kenapa X-nya tidak diberi warna `Error`?** Merah di bahasa visual Factory Flow adalah sinyal produksi (`CRITICAL`/`BOTTLENECK`). Tombol hapus bukan alarm — memakai `OnSurface` cukup dan tidak mencemari semantik warna.

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **"Elemen hilang" dicari di logika, padahal di layout.** Gejala X tidak muncul terlihat seperti bug state (`readOnly` salah, event tidak terkirim). Padahal tombolnya dirender — hanya saja di luar viewport. Selalu cek dulu: *apakah elemennya ada di tree tapi terpotong?*
2. **Mengubah satu angka tanpa menghitung total.** Menyempitkan kolom label tanpa menghitung 7 kolom × sel + tombol hanya menggeser titik potongnya. Hitung total baris header **dan** baris data, keduanya harus ≤ viewport.
3. **Membiarkan header dan data tidak sinkron.** Spacer 28dp vs tombol 24dp = 4dp hantu yang membuat kolom tidak lurus secara piksel.
4. **Lupa bahwa `fillMaxWidth` + `horizontalScroll` menghasilkan clipping, bukan wrapping.** Di HTML/CSS, `overflow-x: auto` + flex-wrap bisa membungkus; di Compose `Row` tidak pernah wrap — konten lebar hanya terpotong.