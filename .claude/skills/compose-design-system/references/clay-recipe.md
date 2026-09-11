# Resep Clay — Mekanika `claySurface`

Referensi teknis untuk `Modifier.claySurface()` di
[`presentation/designsystem/ClayModifier.kt`](../../../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/designsystem/ClayModifier.kt).

Baca ini kalau kamu perlu membuat permukaan clay yang tidak tertutup oleh `ClayCard`/`ClayButton`,
atau kalau bayanganmu berperilaku aneh.

---

## Asal-usul: aturan CSS yang kita terjemahkan

```css
.clay-card {
  background: #fff;
  border: 3px solid #2d3748;               /* outline tebal gelap */
  box-shadow: 6px 6px 0 #2d3748,           /* hard shadow, blur = 0 */
              inset 0 -4px 0 rgba(0,0,0,.10); /* inner bottom shade */
  border-radius: 24px;
}
.clay-card:hover {
  box-shadow: 4px 4px 0 #2d3748;
  transform: translate(2px, 2px);
}
```

Empat hal yang harus direplikasi, dan tidak satu pun punya padanan langsung di Compose:

| Properti CSS | Kenapa tidak bisa langsung | Cara kita |
|---|---|---|
| `box-shadow: … 0` (blur 0) | `Modifier.shadow()` selalu blur | Gambar siluet sendiri via `drawOutline` |
| `inset box-shadow` | Tidak ada padanannya sama sekali | `drawBehind` + `Brush.verticalGradient` |
| `transform: translate` saat tekan | — | `Modifier.offset` (bukan `padding`) |
| Bayangan boleh keluar batas | `drawBehind` memang boleh, tapi berbahaya | Reservasi ruang lewat `padding` |

---

## Kenapa `Modifier.shadow()` tidak bisa dipakai

`Modifier.shadow(elevation)` adalah implementasi **kurva elevation Material Design**. Parameter
`elevation` yang kamu berikan diterjemahkan menjadi radius blur dan penyebaran bayangan. **Tidak ada
parameter untuk mematikan blur-nya** — itu bukan kelalaian API, itu memang desainnya.

Filosofinya berlawanan dengan yang kita butuhkan:

| | Material | Clay / Neo-Brutalism |
|---|---|---|
| Bayangan | Kabur, menyebar | Bidang solid, tepi tajam |
| Makin tinggi | Makin kabur | Makin jauh bergeser |
| Metafora | Kertas melayang | Stiker tebal terangkat |

---

## Urutan modifier — dan kenapa tidak boleh ditukar

```kotlin
this
    .padding(start/end/top/bottom = …)   // 1. Reservasi ruang bayangan
    .offset(x = shadowX - gapX, …)       // 2. Geser kartu saat ditekan
    .drawBehind { /* hard shadow */ }    // 3. Bayangan
    .clip(shape)                         // 4. Mulai memotong
    .background(background)              // 5a. Cat latar
    .drawBehind { /* inner shade */ }    // 5b. Gelapkan dasar
    .border(borderWidth, outline, shape) // 6. Outline paling atas
```

### 1. `padding` harus paling atas

`drawBehind` **tidak dibatasi oleh bounds elemen** — dia boleh menggambar keluar batas. Kedengarannya
enak, sebenarnya berbahaya: tanpa reservasi, bayangan 6dp akan menimpa kartu tetangga di dalam
`Row`/`Column` yang rapat.

> **Mental model**: `padding` di sini bukan jarak estetis, tapi **reservasi ruang layout**. Kartunya
> sengaja dibuat lebih kecil dari kotak yang dialokasikan; sisanya tempat bayangan duduk.

Bug ini **tidak muncul di preview komponen tunggal**. Hanya muncul di layar penuh.

### 2. `offset`, bukan `padding`, untuk menggeser

`Modifier.offset()` hanya mengubah **penempatan**, tidak mengubah **pengukuran**. Ukuran yang
dilaporkan ke parent tetap sama → nol reflow saat animasi.

Kalau pakai `padding` yang dianimasikan, seluruh layout diukur ulang 60×/detik dan elemen tetangga
ikut bergoyang.

### 3. Bayangan harus SEBELUM `clip`

```kotlin
.drawBehind {
    val silhouette = shape.createOutline(size, layoutDirection, this)
    translate(left = gapX.toPx(), top = gapY.toPx()) {
        drawOutline(outline = silhouette, color = shadowColor)
    }
}
```

Idenya sederhana: **ambil siluet bentuk yang sama, geser, cat solid.**

`Modifier.clip()` sebenarnya `graphicsLayer(clip = true)`. Semua yang datang **setelahnya** berada
di dalam layer itu dan akan terpotong. Bayangan berada di luar bentuk kartu, jadi kalau ditaruh
setelah `clip`, dia terpotong 100% dan hilang total.

> **Hafalkan**: *"`clip` adalah pintu; apa pun setelahnya ada di dalam ruangan."*

### 5b. Inner shade di antara `background` dan konten

```kotlin
.background(background)
.drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            0.88f to Color.Transparent,
            1.0f  to Color.Black.copy(alpha = 0.10f)
        )
    )
}
```

`drawBehind` menggambar **di belakang apa pun yang datang setelahnya dalam rantai**. Karena
diletakkan setelah `.background()` tapi sebelum konten:

```
background dicat → gradient gelap dicat di atasnya → teks/isi dicat paling atas
```

Hasilnya dasar kartu menggelap **tanpa menggelapkan teksnya**.

### 6. `border` paling akhir

Supaya outline duduk di atas background *dan* konten. Kalau `border` ditaruh sebelum `background`,
background menutupi separuh ketebalannya dan outline 3dp terlihat seperti 1.5dp.

---

## Matematika animasi tekan

Ini yang paling sering disalahpahami. Baca CSS-nya lagi:

```css
.clay-card       { box-shadow: 6px 6px 0; }
.clay-card:hover { box-shadow: 4px 4px 0; transform: translate(2px, 2px); }
```

Kelihatannya "bayangannya mengecil". Sebenarnya: **posisi absolut bayangan tidak bergerak sama
sekali** — kartunya yang maju masuk ke dalam bayangannya.

| State | Posisi kartu | Jarak bayangan (relatif kartu) | Posisi absolut bayangan |
|---|---|---|---|
| Diam | 0 | 6 | 0 + 6 = **6** |
| Ditekan | 4 | 2 | 4 + 2 = **6** |

Sama-sama 6. Itulah kenapa rasanya seperti **menekan tombol fisik**, bukan seperti bayangan yang
menyusut.

Kode:

```kotlin
val gapX by animateDpAsState(targetValue = if (pressed) pressedX else shadowX)
// ...
.offset(x = shadowX - gapX, y = shadowY - gapY)  // 6-6=0 diam; 6-2=4 saat ditekan
.drawBehind { translate(gapX, gapY) { ... } }     // relatif terhadap kartu
```

---

## Bayangan berarah (`shadowX` / `shadowY` negatif)

Panel yang menempel di tepi layar tidak boleh membuang bayangannya ke luar viewport. Drawer inspector
di Factory Flow menempel di kanan, jadi bayangannya diarahkan ke kiri:

```kotlin
.claySurface(
    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
    background = WeMadeColors.Surface,
    outline = WeMadeColors.Outline,
    shadowX = -ClayOffset.Rest,   // ke KIRI
    shadowY = 0.dp,               // tanpa komponen vertikal
    innerShade = false            // panel setinggi layar: gradient di dasar jadi noda
)
```

Reservasi ruangnya otomatis pindah ke sisi yang benar:

```kotlin
.padding(
    start  = if (shadowX < 0.dp) -shadowX else 0.dp,
    end    = if (shadowX > 0.dp)  shadowX else 0.dp,
    top    = if (shadowY < 0.dp) -shadowY else 0.dp,
    bottom = if (shadowY > 0.dp)  shadowY else 0.dp
)
```

Dan jarak tekannya mempertahankan tanda masing-masing sumbu, jadi arahnya tetap konsisten.

---

## Kapan pakai `clayFlat` saja

`Modifier.clayFlat(shape, background, outline, borderWidth)` = outline + latar, **tanpa bayangan**.

Pakai ini untuk elemen yang menempel rata di latar dan bukan objek yang "terangkat":

- chip & tag
- tile ikon
- header kolom / swimlane
- kotak IN/OUT di dalam kartu
- baris dalam daftar

Aturan praktis: **kalau elemennya berada di dalam kartu lain, hampir selalu `clayFlat`.** Bayangan
bertumpuk membuat hierarki kedalaman jadi kacau.

---

## Panduan memilih `offset`

| Nilai | Kapan |
|---|---|
| `ClayOffset.Rest` (6dp) | Kartu entitas — objek utama yang bisa disentuh |
| `ClayOffset.Small` (4dp) | Wadah & elemen sekunder (kolom swimlane, tombol, kartu di dalam modal) |
| `ClayOffset.Pressed` (2dp) | Chip yang bisa di-toggle; juga posisi otomatis saat `pressed = true` |
| `10.dp` (eksplisit) | Modal — lapisan tertinggi di layar |
| `ClayOffset.Flat` (0dp) | Tombol nonaktif — hilangkan bayangannya, jangan cuma diredupkan |

> Wadah harus punya bayangan **lebih tipis** dari isinya, supaya isinya tetap jadi lapisan yang
> paling menonjol. Kolom swimlane pakai `Small`, kartu node di dalamnya pakai `Rest`.

---

## Jebakan yang sudah pernah kena

| Gejala | Penyebab | Perbaikan |
|---|---|---|
| Bayangan tidak muncul sama sekali | `clip` sebelum `drawBehind` | Tukar urutannya |
| Bayangan menimpa elemen sebelah | `padding` reservasi hilang | Kembalikan langkah 1 |
| Layout bergoyang saat ditekan | `padding` dianimasikan, bukan `offset` | Pakai `offset` |
| Outline terlihat separuh tebal | `border` sebelum `background` | Pindahkan `border` ke akhir |
| Bayangan hilang di tepi kanan layar | `shadowX` positif pada panel tepi | Pakai `shadowX` negatif |
| Kedalaman terasa kacau | Wadah dan isi sama-sama `Rest` | Wadah turunkan ke `Small` |
| Gradient terlihat seperti noda | `innerShade` aktif di panel tinggi | Set `innerShade = false` |
