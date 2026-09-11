# Katalog Komponen & Token Clay

Semua ada di `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/designsystem/`.

**Cek katalog ini sebelum menulis `Card`, `Button`, `Box` bergaya, atau badge apa pun.**

---

## Pemilihan cepat

```
Kotak berisi konten, terangkat dari latar ........ ClayCard
Tombol berisi teks (+ ikon) ...................... ClayButton
Tombol ikon saja (✕, toolbar) .................... ClayIconButton
Area yang bisa diklik dengan isi bebas ........... ClayActionSurface
Pil status membulat penuh ........................ ClayBadge
Label persegi padat di sela teks ................. ClayTag
Permukaan clay kustom, butuh bayangan ............ Modifier.claySurface
Permukaan rata ber-outline, tanpa bayangan ....... Modifier.clayFlat
```

---

## `ClayCard`

```kotlin
@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    shape: Shape = ClayShapes.Card,
    containerColor: Color = WeMadeColors.Surface,
    outlineColor: Color = WeMadeColors.Outline,
    shadowColor: Color = outlineColor,
    offset: Dp = ClayOffset.Rest,
    borderWidth: Dp = ClayBorder.Thick,
    selected: Boolean = false,
    innerShade: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(ClaySpacing.Xl),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
)
```

Pengganti blok `Card(shape=…, colors=…, border=…, elevation=…)` yang dulu disalin di ~32 tempat.

**Perbedaan perilaku dari `Card` Material yang harus kamu tahu:**

1. **Ripple dimatikan.** Umpan baliknya adalah kartu bergerak masuk ke bayangannya. Ripple di atas
   itu membuat dua bahasa interaksi bertabrakan.
2. **`selected` dipetakan ke state tertekan.** Kartu terpilih menetap di posisi "masuk", jadi
   seleksi terbaca dari **bentuk**, bukan cuma warna outline.
3. **Kartu tidak memenuhi seluruh kotak yang dialokasikan.** `offset` di kanan-bawah disisihkan
   untuk bayangan. Ini disengaja — jangan "perbaiki" dengan `fillMaxSize()`.

**`shadowColor` terpisah dari `outlineColor` — kapan dipakai?**
Saat outline mengambil warna status tapi bayangannya harus tetap gelap. Tanpa ini, kartu bottleneck
merah terlihat melayang lebih tinggi dari tetangganya hanya karena statusnya merah:

```kotlin
ClayCard(
    outlineColor = Color(node.healthStatus.badgeColorHex),  // merah/amber/hijau
    shadowColor = WeMadeColors.Outline,                     // selalu gelap
)
```

---

## `ClayButton`

```kotlin
enum class ClayButtonStyle { Primary, Secondary, Accent, Danger, Ghost }

@Composable
fun ClayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ClayButtonStyle = ClayButtonStyle.Primary,
    enabled: Boolean = true,
    fontSize: TextUnit = 13.sp,
    offset: Dp = ClayOffset.Small,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    leading: (@Composable () -> Unit)? = null
)
```

| Style | Isian | Teks | Untuk |
|---|---|---|---|
| `Primary` | `Primary` biru | putih | Aksi utama; juga state "aktif" pada toggle |
| `Secondary` | `Surface` putih | `OnSurface` | Aksi netral, aksi sekunder |
| `Accent` | `Accent` oranye | putih | Aksi khas produksi garmen |
| `Danger` | `Error` merah | putih | Aksi destruktif |
| `Ghost` | transparan | `OnSurfaceMuted` | Aksi tersier |

**Padding default sengaja jauh lebih rapat dari referensi desain aslinya** (`1rem 2rem`). Layar
operasional menampung belasan aksi sekaligus; ukuran tombol landing page akan mendorong toolbar ke
bawah lipatan.

**Tombol nonaktif kehilangan bayangannya**, bukan cuma diredupkan. Tombol clay yang masih
"mengambang" tapi tidak bisa ditekan membaca sebagai bug, bukan sebagai disabled.

**Pola toggle:**

```kotlin
ClayButton(
    text = if (isOpen) "Tutup Panel" else "Buka Panel",
    onClick = onToggle,
    style = if (isOpen) ClayButtonStyle.Primary else ClayButtonStyle.Secondary
)
```

---

## `ClayIconButton`

```kotlin
@Composable
fun ClayIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    shape: Shape = CircleShape,
    containerColor: Color = WeMadeColors.SurfaceMuted,
    outlineColor: Color = WeMadeColors.Outline,
    offset: Dp = ClayOffset.Pressed,
    enabled: Boolean = true,
    content: @Composable () -> Unit
)
```

Untuk tombol ikon-saja: ✕ pada modal & drawer, aksi ikonik di toolbar.

Menggantikan `IconButton` Material, yang membawa ripple dan ukuran sentuh 48dp bawaan Material
tanpa outline apa pun. Umpan baliknya di sini sama seperti komponen clay lain: tombolnya masuk ke
dalam bayangannya sendiri.

Bentuk defaultnya lingkaran karena itu pemakaian paling umum. Untuk ikon di toolbar persegi,
berikan `shape = ClayShapes.Tile`.

---

## `ClayActionSurface`

```kotlin
@Composable
fun ClayActionSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = WeMadeColors.Surface,
    outlineColor: Color = WeMadeColors.Outline,
    offset: Dp = ClayOffset.Small,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
)
```

Untuk area klik yang isinya bukan sekadar teks — ikon + angka + panah, avatar + nama + badge, dsb.
Punya `selected` seperti `ClayCard`.

---

## `ClayBadge` & `ClayTag`

```kotlin
@Composable
fun ClayBadge(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    fontSize: TextUnit = 11.sp,
    containerColor: Color = tint.copy(alpha = 0.14f),
    leading: (@Composable () -> Unit)? = null
)

@Composable
fun ClayTag(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 10.sp,
    leading: (@Composable () -> Unit)? = null
)
```

Keduanya menggantikan **tiga implementasi badge duplikat** yang dulu berdiri sendiri di tiga package
(`HealthStatusPill`, `Badge`, `ModuleCategoryBadge`).

| | `ClayBadge` | `ClayTag` |
|---|---|---|
| Bentuk | Pil membulat penuh | Persegi bersudut (`ClayShapes.Chip`) |
| Border | 2dp | 1dp |
| Padding | 9×3 | 7×3 |
| Untuk | Status yang harus menonjol | Label padat, metadata, di sela teks |

**Warna diturunkan dari satu `tint`**: isian = tint sangat diredam, outline = tint pekat. Jadi badge
status produksi (hijau/amber/merah) tetap terbaca sebagai sinyal, bukan hanyut jadi dekorasi.

`containerColor` dioverride hanya kalau domain sudah menyediakan warna latarnya sendiri:

```kotlin
ClayBadge(
    text = status.label,
    tint = Color(status.badgeColorHex),
    containerColor = Color(status.bgTintHex),   // dari domain
    dot = true
)
```

**Keduanya `maxLines = 1, softWrap = false`** — badge tidak akan pernah memecah teks per huruf,
seberapa sempit pun ruangnya. Tapi ini jaring pengaman, bukan pengganti `weight` yang benar di
pemanggilnya; lihat Kontrak 13 di rules.

---

## `Modifier.claySurface` & `Modifier.clayFlat`

```kotlin
fun Modifier.claySurface(
    shape: Shape,
    background: Color,
    outline: Color,
    shadowColor: Color = outline,
    offset: Dp = ClayOffset.Rest,
    shadowX: Dp = offset,      // boleh negatif → bayangan ke kiri
    shadowY: Dp = offset,      // boleh negatif → bayangan ke atas
    pressed: Boolean = false,
    borderWidth: Dp = ClayBorder.Thick,
    innerShade: Boolean = true
): Modifier

fun Modifier.clayFlat(
    shape: Shape,
    background: Color,
    outline: Color,
    borderWidth: Dp = ClayBorder.Medium
): Modifier
```

Mekanika lengkap ada di [`clay-recipe.md`](clay-recipe.md).

Aturan praktis: **kalau elemennya berada di dalam kartu lain, hampir selalu `clayFlat`.**
Bayangan bertumpuk membuat hierarki kedalaman kacau.

---

## Token

### `ClayShapes` — dinamai per peran, bukan ukuran

| Token | Nilai | Untuk |
|---|---|---|
| `Panel` | 24dp | Modal, drawer, panel besar |
| `Card` | 20dp | Kartu entitas |
| `Button` | 16dp | Tombol, pil tahapan |
| `Tile` | 14dp | Tile ikon persegi |
| `Chip` | 12dp | Chip, segmented control, field, kotak dalam kartu |
| `Pill` | 50% | Badge membulat penuh |

### `ClayOffset` — pengganti konsep elevation

| Token | Nilai | Untuk |
|---|---|---|
| `Rest` | 6dp | Kartu entitas (objek utama) |
| `Small` | 4dp | Wadah, tombol, kartu dalam modal |
| `Pressed` | 2dp | Chip toggle; posisi otomatis saat `pressed = true` |
| `Flat` | 0dp | Tombol nonaktif |

Modal pakai `10.dp` eksplisit — lapisan tertinggi di layar.

### `ClayBorder`

| Token | Nilai | Untuk |
|---|---|---|
| `Thick` | 3dp | Kartu & panel (padanan `border: 3px` di referensi) |
| `Medium` | 2dp | Chip, badge, tombol, elemen kecil |
| `Hairline` | 1dp | Pemisah di dalam chip |

### `ClaySpacing`

`Xxs` 2 · `Xs` 4 · `Sm` 6 · `Md` 8 · `Lg` 12 · `Xl` 16 · `Xxl` 24

### `ClayMaterialShapes`

Diserahkan ke `MaterialTheme(shapes = …)`. Membuat komponen M3 bawaan ikut radius clay tanpa
disentuh satu per satu:

| Komponen M3 | Ambil dari | Default M3 | Jadi |
|---|---|---|---|
| `AlertDialog` | `extraLarge` | 28dp | 24dp |
| `Card` | `medium` | 12dp | 16dp |
| `Chip` / `FilterChip` | `small` | 8dp | 12dp |
| `OutlinedTextField` | `extraSmall` | 4dp | 8dp |

> Karena itu, **jangan override `shape` di `AlertDialog` atau `OutlinedTextField`** — biarkan
> mewarisi. Override justru memutusnya dari sistem.

---

## `WeMadeColors` — token warna

| Kelompok | Token |
|---|---|
| Brand | `Primary` `PrimaryDark` `PrimaryContainer` `Secondary` |
| Aksen | `Accent` `AccentDark` `AccentLight` |
| Permukaan | `Background` `BackgroundWarm` `Surface` `SurfaceMuted` |
| Teks | `OnSurface` `OnSurfaceMuted` |
| Garis | `Outline` `OutlineSoft` `OutlineInverse` `Border` `BorderFocus` |
| Status | `Success`/`SuccessBg` · `Warning`/`WarningBg` · `Error`/`ErrorBg` |
| Semantik khusus | `Info`/`InfoDark` (aliran otomatis) · `Defect` (cacat & rework) |
| Kategori | `Purple`/`PurpleBg` · `Teal`/`TealBg` |
| Mode gelap | `BackgroundDark` `SurfaceDark` `SurfaceDarkElevated` `SurfaceDarkSunken` `OnSurfaceInverse` `OnSurfaceMutedInverse` `BorderInverse` `PrimaryInverse` `ErrorBgDark` `ErrorBgDarkMuted` `DefectBgDark` |

### Beberapa pembedaan yang penting

- **`Primary` vs `Info`** — `Primary` berarti *aksi user*; `Info` berarti *aliran otomatis
  mesin-ke-mesin*. Di kanvas pipeline, keduanya muncul berdampingan dan tidak boleh tertukar.
- **`Error` vs `Defect`** — `Error` adalah kegagalan sistem (request gagal, validasi); `Defect`
  adalah cacat produk dan jalur rework mundur. Domain berbeda, warna berbeda.
- **`Outline` vs `Border`** — `Outline` (slate-800) untuk outline clay tebal; `Border` (slate-200)
  untuk pemisah halus dan garis nonaktif.
- **`Background` vs `BackgroundWarm`** — `BackgroundWarm` adalah latar utama layar (hangat, diambil
  dari referensi desain); `Background` untuk well/permukaan cekung di dalam kartu.

---

## Tipografi

`rememberClayTypography()` mengisi **seluruh 15 peran Material**:

- **Fredoka** → `display*`, `headline*`, `title*`
- **Nunito** → `body*`, `label*`

Font ada di `app/shared/src/commonMain/composeResources/font/`, instance statis per bobot
(Fredoka: Medium/SemiBold/Bold · Nunito: Regular/SemiBold/Bold).

> **Jangan tanam `color` ke dalam `TextStyle`** — itu mematikan `LocalContentColor` dan membuat teks
> di atas permukaan gelap tetap keluar berwarna slate.
