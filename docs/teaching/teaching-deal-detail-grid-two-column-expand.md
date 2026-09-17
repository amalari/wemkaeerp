# 🎓 Modul Pembelajaran: Grid Dua Kolom Konsisten pada Kartu Desain Sampling (Deal Detail Dialog)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform Layout, Compose `Row`/`weight`, "Grid" manual tanpa `LazyVerticalGrid`, State Hoisting untuk accordion  
> **Prasyarat**: Paham dasar `Row`/`Column` Compose, konsep `Modifier.weight(1f)` vs `fillMaxWidth()`, dan membaca file bertingkat (dialog → tab → kartu)  
> **Referensi Task**: Permintaan user — *"di detail, ketika expand, width-nya tetap grid 2, jangan jadi full width"*

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Dialog detail Deal (`/crm-sales/deals` → klik deal → tab **Siklus Sampling**) menampilkan kartu
desain (`DSG-01`, `DSG-02`, …) dalam **grid dua kolom** bergaya accordion: kartu lipat ringkas,
kartu terbuka memunculkan slot foto mockup + form fee & catatan.

- **Masalah Nyata**: Logika layout lama membedakan nasib kartu berdasarkan state-nya:
  kartu **ter-expand** diberi `Modifier.fillMaxWidth()` di barisnya sendiri (satu baris penuh),
  sementara kartu **lipat** dipasangkan dua per baris. Efek sampingnya, setiap kali admin
  menekan chevron, kartu itu "melompat" dari kolom kiri ke posisi baris penuh —
  posisi kartu berubah, urutan visual melompat-lompat, dan grid kehilangan ritmenya.
- **Analogi Sederhana**: Bayangkan lemari arsip dengan dua kolom. Aturan lama: kalau satu map
  dibuka lebar, map itu boleh merebut seluruh rak. Aturan baru: setiap map **selalu** punya
  slot setengah rak — membuka map hanya membuatnya *lebih tinggi*, tidak pernah *lebih lebar*.
- **Hasil Akhir**: Grid dua kolom yang stabil. Expand/collapse hanya mengubah **tinggi** kartu,
  tidak pernah **lebar** maupun posisinya.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0: Temukan pemilik layout, bukan pemilik state.** Masalah "lebar" adalah masalah
   *layout*, jadi mulai dari `SamplingTabContent` (pemilik `Row` pengelompok kartu) — bukan dari
   `SamplingDesignCard` (pemilik isi kartu).
2. **Langkah 1: Pahami state yang sudah di-hoist.** `expandedOverride: Map<String, Boolean>`
   sudah diangkat ke `SamplingTabContent` — artinya layout *sudah tahu* kartu mana terbuka.
   Perbaikan cukup di satu tempat.
3. **Langkah 2: Pilih strategi pengelompokan.** `list.chunked(2)` menghasilkan pasangan kartu
   deterministik (baris 1: DSG-01+DSG-02, dst.) tanpa cursor manual.
4. **Langkah 3: Seragamkan modifier.** Setiap kartu — lipat maupun terbuka — mendapat
   `Modifier.weight(1f)`. Baris ganjil ditutup `Spacer(Modifier.weight(1f))` sebagai slot kosong.
5. **Langkah 4: Perbaiki toggle.** `onToggleExpanded` sekarang menulis `!isExpanded`
   (flip boolean) karena kartu tak lagi "pindah baris" saat toggle.
6. **Langkah 5: Kompilasi multi-target** (`compileKotlinJvm` + `compileKotlinWasmJs`) dan lihat
   hasilnya dengan mata di browser — bug layout tidak tertangkap compiler.


---

## 🔬 3. Bedah Kode Blok per Blok

### Blok A — Evolusi dari `chunked(2)` ke Masonry 2 Kolom Independen (file `DealDetailDialog.kt`)

Pada iterasi awal, kartu dipotong per pasang menggunakan `chunked(2)` dan diletakkan dalam satu `Row`:
```kotlin
// ⚠️ MASALAH "OMPONG":
state.samplingOrders.chunked(2).forEach { pair ->
    Row(verticalAlignment = Alignment.Top) {
        pair.forEach { order -> SamplingDesignCard(..., modifier = Modifier.weight(1f)) }
    }
}
```
**Mengapa ini menyebabkan tampilan "ompong"?**
Karena tinggi `Row` ditentukan oleh kartu yang **paling tinggi** di baris tersebut. Jika kartu kiri (`DSG-01`) di-expand (tinggi ~750dp) sementara kartu kanan (`DSG-02`) di-collapse (tinggi ~50dp), baris berikutnya (`DSG-03` & `DSG-04`) baru bisa dimulai di y = 750dp! Akibatnya, di bawah `DSG-02` tercipta **ruang kosong menganga sebesar ~700dp ("ompong")**.

**Solusi: Masonry 2 Kolom Independen**
Daftar dipisahkan menjadi dua aliran vertikal mandiri (`leftColumnOrders` dan `rightColumnOrders`):

```kotlin
// ✅ SOLUSI MASONRY (RAPAT TANPA OMPONG):
val leftColumnOrders = state.samplingOrders.filterIndexed { index, _ -> index % 2 == 0 }
val rightColumnOrders = state.samplingOrders.filterIndexed { index, _ -> index % 2 == 1 }

Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
    verticalAlignment = Alignment.Top
) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        leftColumnOrders.forEach { order ->
            SamplingDesignCard(order = order, modifier = Modifier.fillMaxWidth(), ...)
        }
    }
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        rightColumnOrders.forEach { order ->
            SamplingDesignCard(order = order, modifier = Modifier.fillMaxWidth(), ...)
        }
    }
}
```

**Mental model**: Sekarang, `DSG-04` tidak perlu menunggu `DSG-01` selesai. Karena `DSG-04` berada di kolom kanan, ia langsung menempel rapat persis di bawah `DSG-02`. Kolom kiri dan kolom kanan mengalir sendiri-sendiri secara natural seperti layout Pinterest, dengan lebar tetap 50% masing-masing (`weight(1f)`).

### Blok B — Kenapa dulu pakai `while` + cursor, dan kenapa sekarang tidak

Versi lama menelusuri daftar dengan cursor manual dan bercabang: kartu terbuka → `fillMaxWidth()`
sendirian; kartu lipat → cari tetangga lipat untuk dipasangkan. Logika cabang itu yang membuat
posisi kartu **tidak stabil**: kartu yang sama bisa berada di kolom kiri, kolom kanan, atau
baris penuh — tergantung state expand-nya. `chunked(2)` menghapus semua cabang: posisi kartu
**hanya** fungsi dari indeksnya di daftar (`index / 2` = baris, `index % 2` = kolom).

### Blok C — Isi kartu yang ter-expand tetap muat di setengah lebar

Konten terbuka adalah `Row { DesignMockupSlot(); Column(Modifier.weight(1f)) {...} }`.
`DesignMockupSlot` berukuran **tetap 150dp**, jadi di dalam dialog maks 1150dp (setengah kartu
≈ 500dp lebih) kolom kanan masih punya ratusan dp — tidak ada risiko teks pecah per huruf.
Jika kelak slot fotonya dibesarkan, ingat Kontrak 13: elemen yang boleh menyusut wajib
`weight(1f, fill = false)` + `maxLines` + `overflow`.


---

## 🏗️ 4. Teknologi & Pendekatan ("The Why")

| Keputusan | Alasan | Risiko jika salah jalan |
|---|---|---|
| `chunked(2)` + `Row` manual, bukan `LazyVerticalGrid` | Daftar desain kecil (belasan) dan kartu punya state lokal (`remember` per kartu: revision pill, file picker, autosave). `LazyVerticalGrid` membuang-rebuat composable saat scroll dan state lokal bisa hilang. | Grid lazy menyebabkan upload yang sedang berjalan / field autosave ter-reset di luar viewport. |
| `weight(1f)` untuk semua kartu | `weight` adalah satu-satunya cara di `Row` untuk membagi lebar sisa secara proporsional dan **memaksa** kontrak lebar 50/50. | `fillMaxWidth()` per kartu membuat kartu kedua terdorong keluar layar. |
| State expand tetap di-hoist (`expandedOverride`) | Layout butuh tahu state untuk merender `expanded = ...`; default mengikuti `order.isActiveDesign` supaya desain aktif terbuka otomatis. | State di dalam kartu = default "aktif = terbuka" tidak bisa diekspresikan oleh layout. |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Menukar `fillMaxWidth()` dengan `weight(1f)` di dalam `Row`.** `fillMaxWidth()` pada anak
   pertama memakan seluruh lebar baris sebelum anak kedua diukur → anak kedua terdorong keluar.
2. **Lupa `Spacer(Modifier.weight(1f))` untuk baris ganjil.** Tanpa filler, satu-satunya kartu
   di baris terakhir memanjang penuh (dialah satu-satunya pembobot) — grid
   "menyempit-melebar" antar baris.
3. **Menulis toggle sebagai literal `true`/`false` per cabang.** Versi lama punya tiga lambda
   berbeda (`to false`, `to true`, …). Dengan grid seragam, satu lambda flip
   (`!isExpanded`) cukup — lebih sedikit cabang, lebih sedikit bug.
4. **Mengubah ukuran slot mockup jadi `fillMaxWidth()` "biar mengisi".** Di setengah lebar itu
   akan menindih kolom form. Ukuran tetap (150dp) disengaja.

---

## ✅ 6. Verifikasi & Tantangan Mandiri

**Cara menguji:**
1. Jalankan web app, buka `http://localhost:3000/crm-sales/deals`, klik salah satu deal.
2. Di tab *Siklus Sampling*, tekan chevron expand pada kartu mana pun.
3. **Ekspektasi**: kartu terbuka tetap di posisi grid-nya (setengah lebar), hanya tumbuh ke
   bawah; kartu tetangganya tetap di sebelahnya, nempel ke atas.
4. Kompilasi: `./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs`.

**Tantangan mandiri:**
- [ ] Tambahkan animasi `animateContentSize()` pada `ClayCard` di `SamplingDesignCard` agar
      expand/collapse terasa halus — perhatikan interaksinya dengan `Alignment.Top`.
- [ ] Buat lebar kolom adaptif (gaya `GridCells.Adaptive(340.dp)`) memakai `BoxWithConstraints`
      untuk viewport sempit — kapan `chunked(2)` mulai menjadi pilihan yang salah?
- [ ] Tulis test Compose (JVM) yang memverifikasi dua kartu pada baris yang sama memiliki
      lebar identik saat salah satunya ter-expand.
