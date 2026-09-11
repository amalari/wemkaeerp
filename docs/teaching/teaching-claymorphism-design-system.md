# 🎓 Modul Pembelajaran: Membangun Design System Claymorphism + Neo-Brutalism di Compose Multiplatform

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform, Custom `Modifier`, Design Tokens, `MaterialTheme` Overriding, Compose Resources (Font Bundling), CSS-to-Compose Translation
> **Prasyarat**: Paham dasar Composable, `Modifier` chain, dan Material 3 (`MaterialTheme`, `ColorScheme`, `Shapes`)
> **Referensi Task**: Restyle visual Factory Flow — pilot design system clay

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Dari mana ini bermula

User menunjukkan satu halaman demo dan bilang *"saya suka design ini, bisa ga diterapkan?"*. Tugas pertama kita **bukan menulis kode** — tapi **membedah apa sebenarnya design itu**, karena "suka" adalah perasaan, sementara yang bisa kita implementasikan adalah *aturan*.

Kalau kita cuma melihat screenshot lalu menebak-nebak, hasilnya akan "mirip-mirip tapi kok beda rasanya". Jadi kita unduh CSS-nya dan baca aturannya langsung:

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
  transform: translate(2px, 2px);          /* efek "ketekan" */
}
```

Ternyata namanya punya dua bapak:

| Ciri | Aliran | Fungsinya di mata |
|---|---|---|
| Outline tebal gelap 3px | **Neo-Brutalism** | Bikin objek terasa "dipotong tegas", tidak lembek |
| `box-shadow` tanpa blur | **Neo-Brutalism** | Bayangan jadi bidang solid, bukan kabut |
| Radius 24px yang besar | **Claymorphism** | Bikin objek terasa empuk seperti tanah liat |
| `inset` gelap di dasar | **Claymorphism** | Memberi ilusi objek punya ketebalan/volume |
| Font membulat (Fredoka) | **Claymorphism** | Menyatukan rasa; tanpa ini terasa dingin |

### Analogi Sederhana

Bayangkan **stiker tebal** yang ditempel di atas kertas, tapi tidak menempel rata — dia terangkat sedikit sehingga menghasilkan bayangan yang **tajam dan solid**, bukan bayangan kabur seperti kapas.

Kalau kamu **tekan** stiker itu, dia turun masuk ke bayangannya sendiri. Itulah seluruh bahasa interaksi yang kita bangun.

Bandingkan dengan Material Design yang kita pakai sebelumnya: Material itu seperti **kertas melayang** — bayangannya kabur (blur), menyebar ke segala arah, dan makin tinggi elevation makin kabur. Filosofinya berlawanan 180°.

### Masalah Nyata: kenapa tidak boleh asal tempel?

Sebelum ini, **seluruh design system WeMade hanya 1 file 113 baris** (`WeMadeTheme.kt`), dan file itu cuma mengontrol sekitar 40% dari yang tampil di layar. Sisanya?

- **343 literal `Color(0xFF……)`** ditulis langsung di dalam Composable, mem-bypass theme
- Blok `Card(shape=…, colors=…, border=…, elevation=…)` yang identik **disalin ulang di ~32 tempat**
- **3 implementasi badge yang nyaris sama persis**, berdiri sendiri di 3 package berbeda
- `MaterialTheme` tidak diberi `shapes` sama sekali, dan `colorScheme`-nya cuma mengisi 11 dari ~30 slot

Kalau kita restyle dengan cara "cari-ganti warna satu per satu", kita akan mengulang persis masalah yang sama: perubahan berikutnya akan sama menyakitkannya. **Restyle adalah kesempatan untuk membangun fondasi, bukan cuma mengecat ulang.**

### Hasil Akhir yang Diharapkan

Satu package `designsystem/` yang menjadi sumber kebenaran tunggal, dipakai oleh layar Factory Flow, dan siap dipakai layar lain tanpa menulis ulang apa pun.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Ini bagian yang paling sering membingungkan: *"file mana dulu yang saya buat?"*

Aturan umumnya: **bangun dari yang paling tidak punya ketergantungan, naik ke yang paling banyak bergantung.** Kalau kamu mulai dari layar, kamu akan terus bolak-balik memperbaiki fondasi sambil layarnya setengah jadi.

### Langkah 0 — Bedah dulu, jangan langsung ngoding

Unduh CSS aslinya. Catat angkanya. **Jangan menebak dari screenshot.** Perbedaan `border: 2px` vs `3px` kelihatan sepele di gambar tapi sangat terasa di layar penuh.

```bash
curl -sL "https://situs-referensi/halaman" -o page.html
grep -o '/_next/static/chunks/[a-f0-9]*\.css' page.html | sort -u   # temukan stylesheet
```

### Langkah 1 — Token (`ClayTokens.kt`)

Ini lapisan **paling dasar**: tidak import apa pun dari project, cuma angka dan bentuk.

Kenapa duluan? Karena semua langkah berikutnya akan memakainya. Kalau kamu menulis `24.dp` langsung di `ClayCard`, lalu `24.dp` lagi di `ClayButton`, lalu `24.dp` lagi di layar — kamu sudah mengulangi kesalahan 343 literal warna itu, hanya dengan angka.

### Langkah 2 — Modifier inti (`ClayModifier.kt`)

Ini **jantung** seluruh pekerjaan. Semua komponen lain hanya pembungkusnya.

Kenapa didahulukan sebelum komponen? Karena kalau `Modifier.claySurface()` sudah benar, `ClayCard`, `ClayButton`, dan `ClayBadge` tinggal jadi 20 baris masing-masing. Kalau kamu mulai dari `ClayCard`, logika gambar bayangan akan tertanam di dalamnya dan harus disalin ke tombol.

### Langkah 3 — Komponen (`ClayCard`, `ClayButton`, `ClayBadge`)

Baru sekarang boleh membuat Composable. Masing-masing hanya membungkus `claySurface` dengan default yang masuk akal untuk perannya.

### Langkah 4 — Font & Tipografi (`ClayTypography.kt`)

Butuh langkah build (unduh TTF + konfigurasi Gradle), jadi ditaruh di sini supaya tidak memblokir langkah 1–3 yang murni Kotlin.

### Langkah 5 — Sambungkan ke `MaterialTheme` (`WeMadeTheme.kt`)

Di sinilah kita "menyuntikkan" design system ke seluruh aplikasi lewat satu titik.

### Langkah 6 — Baru konversi layar

Dan urutannya **dari luar ke dalam**: layar → panel besar → kartu → chip kecil. Kenapa? Karena kalau kamu mulai dari chip, kamu tidak punya konteks visual untuk menilai apakah ukurannya pas.

### Langkah 7 — Jalankan & lihat dengan mata sendiri

Ini bukan langkah opsional. Lihat bagian 6.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Token — memberi nama pada angka

[`designsystem/ClayTokens.kt`](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/designsystem/ClayTokens.kt)

```kotlin
object ClayShapes {
    val Panel  = RoundedCornerShape(24.dp)  // modal, drawer
    val Card   = RoundedCornerShape(20.dp)  // kartu entitas
    val Button = RoundedCornerShape(16.dp)
    val Tile   = RoundedCornerShape(14.dp)
    val Chip   = RoundedCornerShape(12.dp)
    val Pill   = RoundedCornerShape(percent = 50)
}

object ClayOffset {
    val Rest    : Dp = 6.dp   // posisi diam
    val Small   : Dp = 4.dp   // elemen sekunder
    val Pressed : Dp = 2.dp   // saat ditekan
    val Flat    : Dp = 0.dp
}
```

**Mengapa blok ini ditulis begini?**

1. **Dinamai per *peran*, bukan per *ukuran*.** Perhatikan namanya `Panel`/`Card`/`Chip`, bukan `Large`/`Medium`/`Small`. Ini disengaja. Kalau suatu hari kita putuskan kartu harus 18dp bukan 20dp, kita ubah satu baris dan *semua kartu* ikut — tanpa risiko ikut mengubah chip yang kebetulan ukurannya sama. Nama berbasis ukuran akan menggoda orang memakai `Medium` untuk dua hal yang tidak berhubungan.

2. **`ClayOffset` menggantikan konsep `elevation`.** Ini pergeseran mental yang penting. Di Material, "seberapa tinggi" diungkapkan lewat *seberapa kabur* bayangannya. Di neo-brutalism tidak ada blur sama sekali — yang membedakan dekat/jauh adalah *seberapa besar pergeseran* bidang bayangannya.

---

### Blok B: Hard Shadow — kenapa harus digambar manual

[`designsystem/ClayModifier.kt`](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/designsystem/ClayModifier.kt)

Ini pertanyaan yang paling sering muncul: *"kan Compose punya `Modifier.shadow()`, kenapa tidak pakai itu saja?"*

Jawabannya: **`Modifier.shadow()` secara desain tidak bisa menghasilkan bayangan tanpa blur.** Dia adalah implementasi kurva elevation Material — parameter `elevation` yang kamu berikan diterjemahkan menjadi radius blur dan penyebaran. Tidak ada cara mematikan blur-nya. Jadi kita harus menggambar bidangnya sendiri:

```kotlin
.drawBehind {
    val silhouette = shape.createOutline(size, layoutDirection, this)
    translate(left = gapX.toPx(), top = gapY.toPx()) {
        drawOutline(outline = silhouette, color = shadowColor)
    }
}
```

Idenya sederhana sekali begitu dilihat: **ambil siluet bentuk yang sama, geser, cat solid.** Itu saja. `shape.createOutline(...)` memberi kita bentuk persis kartunya (termasuk sudut membulatnya), lalu `translate { }` menggesernya.

**Sekarang bagian yang paling penting: urutan modifier tidak boleh ditukar.**

```kotlin
this
    // 1. Sisihkan ruang untuk bayangan
    .padding(end = ..., bottom = ...)
    // 2. Geser kartunya saat ditekan
    .offset(x = shadowX - gapX, y = shadowY - gapY)
    // 3. Gambar hard shadow
    .drawBehind { /* siluet digeser */ }
    // 4. Mulai sini semua dipotong mengikuti bentuk
    .clip(shape)
    .background(background)
    // 5. Inner bottom shade
    .drawBehind { /* gradient gelap di dasar */ }
    // 6. Outline paling akhir
    .border(borderWidth, outline, shape)
```

Mari bedah kenapa tiap posisi itu wajib:

**Kenapa `padding` harus paling atas (langkah 1)?**
`drawBehind` **tidak dibatasi oleh bounds elemen** — dia boleh menggambar keluar batas. Enak? Tidak, berbahaya. Tanpa `padding`, bayangan 6dp itu akan menimpa kartu tetangga di dalam `Row`/`Column` yang rapat. Jadi kita "memesan" ruang 6dp di kanan dan bawah sebagai tempat bayangannya duduk.

> 💡 **Mental model**: `padding` di sini bukan untuk estetika jarak, tapi untuk **reservasi ruang layout**. Kartunya sengaja dibuat lebih kecil dari kotak yang dialokasikan, sisanya dipakai bayangan.

**Kenapa `drawBehind` bayangan harus SEBELUM `clip` (langkah 3 sebelum 4)?**
`Modifier.clip()` itu sebenarnya `graphicsLayer(clip = true)`. Segala sesuatu **setelahnya** dalam rantai berada *di dalam* layer tersebut dan akan terpotong. Kalau kamu taruh `drawBehind` bayangan setelah `clip`, bayangannya akan terpotong habis karena dia berada di luar bentuk kartu. Hasilnya: bayangan hilang total dan kamu akan bingung lama.

**Kenapa inner shade ada di antara `background` dan konten (langkah 5)?**
Ini trik urutan yang halus. `Modifier.drawBehind` menggambar **di belakang apa pun yang datang setelahnya dalam rantai**. Karena dia diletakkan *setelah* `.background()` tapi *sebelum* konten, hasilnya: background dicat dulu → gradient gelap dicat di atasnya → baru teks/isi kartu dicat paling atas. Jadi dasar kartunya menggelap **tanpa menggelapkan teksnya**.

**Kenapa `border` paling akhir (langkah 6)?**
Supaya outline duduk di atas background *dan* di atas konten. Kalau `border` ditaruh sebelum `background`, background akan menutupi separuh ketebalan garisnya dan outline 3dp akan terlihat seperti 1.5dp.

---

### Blok C: Animasi tekan — yang bergerak itu kartunya, bukan bayangannya

Ini bagian yang paling mudah salah paham. Lihat CSS aslinya lagi:

```css
.clay-card         { box-shadow: 6px 6px 0; }
.clay-card:hover   { box-shadow: 4px 4px 0; transform: translate(2px, 2px); }
```

Kalau dibaca cepat, kelihatannya "bayangannya mengecil". Padahal yang benar: **posisi absolut bayangan tidak bergerak sama sekali** — kartunya yang maju masuk ke dalam bayangannya.

Buktikan dengan aritmatika sederhana:

| State | Posisi kartu | Jarak bayangan (relatif kartu) | Posisi absolut bayangan |
|---|---|---|---|
| Diam | 0 | 6 | 0 + 6 = **6** |
| Ditekan | 4 | 2 | 4 + 2 = **6** |

Sama-sama 6. Bayangannya diam, kartunya yang bergerak 4px. Itulah kenapa efeknya terasa seperti *menekan tombol fisik*, bukan seperti *kartu yang bayangannya menyusut*.

Kode kita mereplikasi persis:

```kotlin
val gapX by animateDpAsState(targetValue = if (pressed) pressedX else restingX)
// ...
.offset(x = shadowX - gapX, y = shadowY - gapY)   // 6-6=0 saat diam, 6-2=4 saat ditekan
.drawBehind { translate(gapX, gapY) { ... } }      // digeser relatif terhadap kartu
```

> ⚠️ **Kenapa `offset()` dan bukan `padding()` untuk menggeser?**
> `Modifier.offset()` hanya mengubah **penempatan**, tidak mengubah **pengukuran**. Artinya ukuran yang dilaporkan ke parent tetap sama, jadi tidak ada reflow layout saat animasi berjalan. Kalau pakai `padding` yang dianimasikan, seluruh layout akan diukur ulang 60× per detik — boros dan bikin elemen tetangga bergoyang.

**Bonus: bayangan berarah.** Drawer di layar ini menempel di tepi kanan. Kalau bayangannya mengarah ke kanan seperti biasa, dia jatuh ke luar viewport dan panel kehilangan kedalamannya. Jadi `claySurface` menerima `shadowX` yang **boleh negatif**:

```kotlin
.claySurface(
    shadowX = -ClayOffset.Rest,  // bayangan ke KIRI
    shadowY = 0.dp,
)
```

Dan `padding` reservasinya otomatis pindah ke sisi `start`:

```kotlin
.padding(
    start = if (shadowX < 0.dp) -shadowX else 0.dp,
    end   = if (shadowX > 0.dp)  shadowX else 0.dp,
    // ...
)
```

---

### Blok D: `MaterialTheme.shapes` — pengungkit termurah dalam pekerjaan ini

[`theme/WeMadeTheme.kt`](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/theme/WeMadeTheme.kt)

```kotlin
val ClayMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun WeMadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WeMadeLightColorScheme,
        typography  = rememberClayTypography(),
        shapes      = ClayMaterialShapes,   // ← satu baris ini
        content     = content
    )
}
```

**Mengapa blok ini ditulis begini?**

Ini pelajaran terpenting soal *leverage* dalam pekerjaan ini. Di aplikasi ini ada **7 `AlertDialog`, 6 `FilterChip`, dan 16 `OutlinedTextField`** yang tidak diberi `shape` eksplisit. Semua komponen M3 bawaan, kalau tidak diberi `shape`, akan mengambilnya dari `MaterialTheme.shapes` lewat token peran masing-masing:

| Komponen M3 | Mengambil dari | Default Material | Jadi (clay) |
|---|---|---|---|
| `AlertDialog` | `shapes.extraLarge` | 28dp | 24dp |
| `Chip` | `shapes.small` | 8dp | 12dp |
| `OutlinedTextField` | `shapes.extraSmall` | 4dp | 8dp |
| `Card` | `shapes.medium` | 12dp | 16dp |

**Satu baris mengubah 29 komponen tanpa menyentuh satu pun file-nya.** Bandingkan dengan alternatifnya: membuka 29 call site dan menambahkan `shape = ...` di masing-masing.

Setelah itu kita juga mengisi **seluruh slot `colorScheme`** yang tadinya bolong:

```kotlin
surfaceVariant  = Color(0xFFF1F5F9),
outline         = WeMadeColors.Outline,
tertiary        = WeMadeColors.Accent,
// ... dst
```

**Kenapa ini penting?** Sebelumnya hanya 11 slot yang di-override. Slot sisanya — `surfaceVariant`, `outline`, `tertiary`, `surfaceContainer*` — **masih memakai ungu default Material 3**. Ungu itu diam-diam bocor ke `AlertDialog`, `DropdownMenu`, dan `OutlinedTextField` yang tidak diberi warna eksplisit. Itu sebabnya kode lama terpaksa menulis warna manual di mana-mana: theme-nya bocor, jadi orang menghindarinya. Kita perbaiki akarnya.

---

### Blok E: Membundel font di Compose Multiplatform

Fredoka + Nunito adalah **variable font** di repo Google Fonts, dan dukungan variable font di Compose belum seragam di 5 target. Jadi kita ambil **instance statis** per bobot lewat Google Fonts CSS API:

```bash
# Perhatikan: TANPA -H "User-Agent" apa pun.
# Kalau User-Agent-nya IE lama → dapat EOT (tidak bisa dipakai Compose).
# Kalau User-Agent-nya Chrome modern → dapat WOFF2 (juga tidak bisa).
# UA default curl → TrueType. Ini yang kita mau.
curl -s 'https://fonts.googleapis.com/css2?family=Nunito:wght@700' \
  | grep -o 'https://fonts.gstatic.com[^)]*'
```

Taruh hasilnya di `app/shared/src/commonMain/composeResources/font/` dengan nama huruf kecil + underscore (syarat Compose Resources), lalu **kunci nama package hasil generate**:

```kotlin
// app/shared/build.gradle.kts
compose.resources {
    publicResClass = true
    packageOfResClass = "com.eventverse.app.shared.resources"
    generateResClass = always
}
```

**Mengapa blok ini ditulis begini?** Tanpa `packageOfResClass`, nama package `Res` diturunkan otomatis dari Gradle group + nama modul. Artinya import-mu ikut berubah kalau suatu hari nama modul diubah. Mengunci namanya adalah 4 baris yang menghemat debugging di masa depan.

Lalu keluarganya dirakit dalam Composable (karena `Font()` dari Compose Resources adalah `@Composable`):

```kotlin
@Composable
fun rememberFredokaFamily(): FontFamily = FontFamily(
    Font(Res.font.fredoka_medium,   FontWeight.Medium),
    Font(Res.font.fredoka_semibold, FontWeight.SemiBold),
    Font(Res.font.fredoka_bold,     FontWeight.Bold)
)
```

Satu perbaikan diam-diam yang penting: tipografi lama **menanam `color` ke dalam `TextStyle`**:

```kotlin
// ❌ LAMA — mematikan LocalContentColor
bodySmall = TextStyle(fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
```

Ini membuat teks di atas kartu gelap tetap keluar berwarna slate, karena `color` eksplisit di `TextStyle` selalu menang atas `LocalContentColor`. Sekarang warnanya diserahkan ke pemanggil.

---

### Blok F: Menyatukan 3 badge duplikat

Sebelumnya ada tiga implementasi yang nyaris identik: `HealthStatusPill`, `Badge`, dan `ModuleCategoryBadge` — tiga package berbeda, tiga radius berbeda (6dp / 4dp / 8dp), tiga padding berbeda.

Sekarang satu `ClayBadge` menerima **satu `tint`** dan menurunkan sisanya:

```kotlin
.clayFlat(
    shape = ClayShapes.Pill,
    background = containerColor,            // default: tint.copy(alpha = 0.14f)
    outline = tint.copy(alpha = 0.55f),
    borderWidth = ClayBorder.Medium
)
```

Dan pemanggil lama tinggal jadi pembungkus tipis:

```kotlin
@Composable
fun HealthStatusPill(status: FlowHealthStatus, modifier: Modifier = Modifier) {
    ClayBadge(
        text = status.label,
        tint = Color(status.badgeColorHex),
        containerColor = Color(status.bgTintHex),
        dot = true,
        modifier = modifier
    )
}
```

**Mengapa tidak langsung hapus `HealthStatusPill` saja?** Karena dia dipakai di beberapa tempat, dan mempertahankan nama lama sebagai pembungkus membuat diff-nya **kecil dan mudah di-review**. Refactor yang baik memisahkan "mengubah implementasi" dari "mengubah call site".

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pilihan Kita | Alternatif | Mengapa Kita Memilih Ini | Risiko Alternatif |
|---|---|---|---|
| **Gambar hard shadow via `drawBehind` + `drawOutline`** | `Modifier.shadow(elevation)` | Satu-satunya cara mendapat bayangan tanpa blur; `shadow()` tidak punya opsi mematikan blur | Bayangan kabur → bukan neo-brutalism, cuma Material dengan sudut besar |
| **Reservasi ruang via `padding`** | Biarkan `drawBehind` melebihi bounds | `drawBehind` tidak di-clip, jadi tanpa reservasi bayangan menimpa kartu tetangga | Kartu saling tumpang tindih di `Row`/`Column` rapat — terlihat seperti bug rendering |
| **`offset()` untuk animasi tekan** | `padding()` yang dianimasikan | `offset` hanya memindahkan penempatan, tidak mengukur ulang → nol reflow | Layout diukur ulang 60×/detik; elemen tetangga bergoyang saat tombol ditekan |
| **Palet brand WeMade + bentuk clay** | Palet pastel demo apa adanya | Factory Flow memakai hijau/amber/merah sebagai **sinyal produksi**; pastel peach/mint akan meredam sinyal itu | Operator tidak bisa membedakan node `HEALTHY` dari `BOTTLENECK` sekilas — konsekuensi operasional nyata, bukan sekadar selera |
| **`MaterialTheme(shapes = …)`** | Tambah `shape =` di tiap call site | 1 baris vs 29 call site; komponen M3 baru otomatis ikut | Komponen yang ditambahkan developer lain besok akan kembali memakai radius Material |
| **Instance font statis** | Variable font (`Fredoka[wdth,wght].ttf`) | Dukungan variable font belum seragam di 5 target KMP | Bobot font di-render salah (semua jadi Regular) di sebagian platform, sulit didiagnosis |
| **Package `designsystem/` terpisah** | Taruh helper di `theme/` | `theme/` untuk token; `designsystem/` untuk komponen. Memisahkan "apa nilainya" dari "bagaimana bentuknya" | `theme/` membengkak jadi keranjang serbaguna — masalah yang persis sedang kita perbaiki |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

### Jebakan 1: Menukar urutan `clip` dan `drawBehind`

```kotlin
// ❌ SALAH — bayangan hilang total
.clip(shape)
.drawBehind { /* bayangan */ }

// ✅ BENAR
.drawBehind { /* bayangan */ }
.clip(shape)
```

*Kenapa bahaya*: `clip` membuat `graphicsLayer` yang memotong semua yang datang **setelahnya**. Bayangan berada di luar bentuk kartu, jadi terpotong 100%. Kamu akan melihat kartu tanpa bayangan dan mengira kodenya tidak jalan.

*Cara menghindar*: hafalkan kalimat ini — **"`clip` adalah pintu; apa pun setelahnya ada di dalam ruangan."**

---

### Jebakan 2: Lupa memesan ruang untuk bayangan

*Kenapa bahaya*: Di layar sepi kelihatan baik-baik saja. Begitu masuk `Row` yang rapat, bayangan kartu A menimpa kartu B. Bug ini **tidak muncul di preview komponen tunggal** — hanya muncul di layar penuh. Inilah kenapa langkah "jalankan dan lihat" tidak bisa dilewat.

---

### Jebakan 3: Mengira design system = ganti warna

*Kenapa bahaya*: Kalau kita cuma mengubah `WeMadeColors`, hasilnya adalah tampilan campur aduk — `AlertDialog` masih ungu Material, chip masih slate-100 hardcoded, kartu masih radius 12dp. Setengah jadi lebih buruk daripada tidak berubah sama sekali, karena terlihat seperti bug.

*Solusi elegan kita*: perbaiki **mekanismenya** (shapes, colorScheme lengkap, komponen bersama), bukan nilainya saja.

---

### Jebakan 4: Melupakan bahwa clay memakan ruang

Ini yang benar-benar kita temui. Outline 3dp + bayangan 6dp menambah **~18dp per kartu**. Di kanvas Factory Flow yang kolomnya 305dp, ini langsung terasa sesak.

```kotlin
// Dinaikkan dari 305dp
modifier = Modifier.width(324.dp)
```

Plus: Nunito punya **x-height lebih besar** dari Roboto/SF. Teks 10sp yang tadinya terbaca jadi terasa berdesakan. Jadi seluruh tier ukuran dinaikkan satu tingkat **serentak** (9→10, 10→11, 11→12):

```bash
# Serentak dalam satu pass — jangan berurutan!
perl -pi -e 's/fontSize = (9|10|11)\.sp/"fontSize = " . ($1+1) . ".sp"/ge' File.kt
```

> ⚠️ Kalau kamu jalankan 9→10 dulu, lalu 10→11, angka 9 akan naik dua kali jadi 11 dan hierarki ukuranmu kolaps. Substitusi serentak dengan callback `/e` menghindari ini.

---

### Jebakan 5: Badge yang memecah teks per huruf

Bug nyata yang kita temukan **hanya setelah melihat layarnya**:

Label tahap `"RANTAI PASOK & BAHAN BAKU"` sangat panjang. Di dalam `Row` dengan `SpaceBetween`, `Row` kiri mengukur diri lebih dulu dan mengambil hampir seluruh lebar, menyisakan ~20dp untuk pil status. Pil itu lalu memecah teks `"Normal"` menjadi **satu huruf per baris**: N-o-r-m-a-l.

Perbaikannya dua lapis:

```kotlin
Row(
    // Label panjang harus menyerah duluan
    modifier = Modifier.weight(1f, fill = false),
) {
    Text(
        text = stageName,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
Spacer(modifier = Modifier.width(6.dp))
HealthStatusPill(status = node.healthStatus)   // tidak menyusut
```

Dan di `ClayBadge` sendiri, sebagai jaring pengaman untuk semua pemakaian di masa depan:

```kotlin
Text(text = text, maxLines = 1, softWrap = false)
```

*Pelajaran*: `weight(1f, fill = false)` artinya **"kamu boleh mengecil kalau perlu, tapi jangan memaksa melebar"**. Ini yang dibutuhkan untuk elemen yang boleh mengalah.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Perubahan visual **tidak bisa dibuktikan oleh unit test**. Tidak ada assertion yang berguna untuk "apakah bayangannya terlihat benar". Jadi strateginya berlapis:

### Lapis 1 — Kompilasi semua target (bukan cuma satu)

KMP punya jebakan khusus: kode bisa lolos di JVM tapi gagal di WasmJS. Resource font terutama rawan.

```bash
./gradlew :app:shared:compileKotlinJvm \
          :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs \
          :app:shared:assembleAndroidMain \
          :app:shared:jvmTest
```

### Lapis 2 — Jalankan dan LIHAT dengan mata sendiri

Ini yang menemukan bug badge di atas. Tidak ada test yang akan menangkapnya.

Kendalanya: Factory Flow ada di balik auth guard. Solusinya — buat entry point **sementara** yang merender layarnya langsung:

```kotlin
// TEMPORARY — hapus setelah review
fun main() = application {
    Window(onCloseRequest = ::exitApplication, alwaysOnTop = true) {
        WeMadeTheme {
            // Dikendalikan env var karena kita tidak bisa klik tanpa izin Accessibility
            var screen by remember { mutableStateOf(System.getenv("CLAY_SCREEN") ?: "flow") }
            when (screen) {
                "orgchart" -> OrgChartScreen()
                "rbac"     -> DynamicRbacScreen()
                else       -> FactoryFlowScreen()
            }
        }
    }
}
```

> 🧹 **Wajib**: hapus file ini dan kembalikan `mainClass` di `build.gradle.kts` setelah selesai. Perancah verifikasi yang tertinggal di repo adalah utang teknis yang membingungkan orang berikutnya.

### Lapis 3 — Cek regresi di layar yang TIDAK dikonversi

Ini yang paling sering dilupakan. Kita cuma mengkonversi Factory Flow — tapi `MaterialTheme(shapes = …)` dan `colorScheme` yang baru **juga mengubah Org Chart dan RBAC**. "Pilot satu layar" tidak pernah benar-benar terisolasi kalau kamu menyentuh theme.

Jadi kedua layar itu wajib dibuka dan diperiksa: `AlertDialog`, `FilterChip`, `OutlinedTextField` masih terbaca? Tidak ada ungu Material yang bocor?

### Lapis 4 — Uji di lebar sempit

Kecilkan jendela sampai ~1280dp. Pastikan `padding` reservasi bayangan benar-benar bekerja dan kartu tetangga tidak saling menimpa.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1 — Dark mode sungguhan.**
  Saat ini "presentation mode" diimplementasikan sebagai ~15 ternary `if (isPresentationMode)` yang tersebar di 6 file. Ubah menjadi `darkColorScheme` sungguhan + `WeMadeTheme(darkTheme: Boolean)`, lalu hapus semua ternary itu. Petunjuk: warna gelapnya sudah ada di `WeMadeColors.SurfaceDark`/`BackgroundDark`/`OutlineInverse`.

- [ ] **Tantangan 2 — `CompositionLocal` untuk token clay.**
  Sekarang `ClayOffset.Rest` adalah konstanta global. Bagaimana kalau satu layar ingin bayangan yang lebih tipis untuk semua kartunya sekaligus? Buat `LocalClayOffset` dan `ProvideClayDensity { }`. Pikirkan: kapan `CompositionLocal` lebih baik daripada parameter, dan kapan justru bikin kode susah dilacak?

- [ ] **Tantangan 3 — Sapu literal warna yang tersisa.**
  Masih ada ~290 literal `Color(0xFF……)` di layar lain. `OrgChartScreen.kt` sendiri punya 108. Konversikan satu file dan ukur: berapa baris yang hilang? Berapa yang justru butuh token baru yang belum ada?

- [ ] **Tantangan 4 — Screenshot test.**
  Cari tahu bagaimana `ComposeUiTest` + screenshot testing bisa menangkap regresi seperti bug "N-o-r-m-a-l" secara otomatis. Apa yang perlu di-render, dan bagaimana menangani perbedaan rendering antar platform?

---

## 📎 Ringkasan File

**Baru:**
- `presentation/designsystem/ClayTokens.kt` — bentuk, offset bayangan, ketebalan outline, spasi
- `presentation/designsystem/ClayModifier.kt` — `Modifier.claySurface()` & `Modifier.clayFlat()`
- `presentation/designsystem/ClayCard.kt` — pengganti ~32 blok `Card` duplikat
- `presentation/designsystem/ClayButton.kt` — `ClayButton` + `ClayActionSurface`
- `presentation/designsystem/ClayBadge.kt` — `ClayBadge` + `ClayTag`, penyatu 3 badge duplikat
- `presentation/designsystem/ClayTypography.kt` — Fredoka + Nunito, 15 peran M3 terisi
- `composeResources/font/` — 6 file TTF (516KB)

**Diubah:**
- `presentation/theme/WeMadeTheme.kt` — token clay, `shapes`, `colorScheme` lengkap
- `app/shared/build.gradle.kts` — blok `compose.resources`
- 11 file di `presentation/pipeline/`
