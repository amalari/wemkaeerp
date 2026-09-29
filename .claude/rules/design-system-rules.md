# WeMade ERP — Aturan Standar Design System & UI Styling (Compose Multiplatform)

Dokumen ini adalah **aturan baku styling** yang wajib ditaati setiap kali membuat, memperluas, atau
merefaktor UI apa pun di `app/shared`. Statusnya sejajar dengan
[`module-integration-rules.md`](module-integration-rules.md): kalau modul mengatur *apa yang dikerjakan*,
dokumen ini mengatur *bagaimana rupanya*.

**Ruang lingkup**: seluruh `app/shared/src/commonMain/.../presentation/**`.

---

## 1. Paradigma: Satu Bahasa Visual, Satu Sumber Kebenaran

Bahasa visual WeMade ERP adalah **Claymorphism + Neo-Brutalism**:

| Ciri | Nilai | Token |
|---|---|---|
| Outline tebal gelap | 3dp | `ClayBorder.Thick` |
| Hard shadow (blur = 0) | geser 6dp | `ClayOffset.Rest` |
| Sudut membulat besar | 12–24dp per peran | `ClayShapes.*` |
| Inner bottom shade | hitam 10% di 12% bawah | otomatis di `claySurface` |
| Interaksi tekan | kartu masuk ke bayangannya | `pressed` di `claySurface` |
| Font | Fredoka (judul) + Nunito (isi) | `rememberClayTypography()` |

Paletnya **tetap palet brand WeMade** — biru `#2563EB` dan oranye `#EA580C`. Bahasa clay diambil
bentuknya, bukan warnanya.

> **Alasan yang tidak boleh dilanggar**: Factory Flow memakai hijau/amber/merah sebagai **sinyal
> produksi** (`HEALTHY` / `WARNING` / `BOTTLENECK` / `CRITICAL`). Palet dekoratif apa pun yang
> meredam kontras sinyal itu ditolak, sebagus apa pun tampilannya.

Sumber kebenaran tunggal:

```
app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/
├── theme/WeMadeTheme.kt          # Lapisan token: warna & theme entry point
└── designsystem/                 # Lapisan bentuk & komponen
    ├── ClayTokens.kt             # shapes, offsets, border widths, spacing
    ├── ClayModifier.kt           # claySurface() & clayFlat()
    ├── ClayCard.kt
    ├── ClayButton.kt
    ├── ClayBadge.kt              # ClayBadge + ClayTag
    └── ClayTypography.kt
```

---

## 2. Arsitektur Token Tiga Lapis

Setiap nilai visual wajib berada di salah satu lapisan berikut, dan **hanya boleh mengalir ke bawah**:

```
Lapis 1 — PRIMITIF      WeMadeColors.*            "warna apa"
              ↓          (Primary, Success, Outline, Border, …)
Lapis 2 — SEMANTIK      WeMadeLightColorScheme    "perannya apa di Material"
              ↓          ClayShapes / ClayOffset / ClayBorder / ClaySpacing
Lapis 3 — KOMPONEN      ClayCard / ClayButton /   "bagaimana perannya dirakit"
                        ClayBadge / ClayTag
```

### Kontrak 1 — Dilarang keras literal warna di dalam Composable fitur

```kotlin
// ❌ DITOLAK — literal warna di file layar/komponen fitur
Box(modifier = Modifier.background(Color(0xFFF1F5F9)))
Text(text = label, color = Color(0xFF64748B))
border = BorderStroke(1.dp, Color(0xFFE2E8F0))

// ✅ BENAR
Box(modifier = Modifier.background(WeMadeColors.SurfaceMuted))
Text(text = label, color = WeMadeColors.OnSurfaceMuted)
```

**Satu-satunya tempat `Color(0xFF……)` boleh ditulis adalah `WeMadeTheme.kt`.**

Dua pengecualian yang sah, keduanya **bukan** hardcode:

1. **Warna yang berasal dari domain**, di mana nilainya adalah data, bukan keputusan desain:
   ```kotlin
   Color(node.stage.colorHex)            // dari PipelineStage
   Color(node.healthStatus.badgeColorHex) // dari FlowHealthStatus
   Color(node.deptColorHex)               // dari Department
   ```
   Warna ini hidup di `core/.../domain/**` karena tenant bisa mengubahnya. Jangan menyalinnya
   ke `WeMadeColors`, dan jangan menggantinya dengan token.

2. **Turunan token** lewat `.copy(alpha = …)`:
   ```kotlin
   outline = WeMadeColors.Success.copy(alpha = 0.45f)   // ✅ boleh
   ```

### Kontrak 2 — Butuh warna baru? Tambahkan token, jangan tulis di tempat

Kalau sebuah warna belum ada tokennya, **berhenti**. Jangan menulis literalnya "sementara".
Tambahkan ke `WeMadeColors` dengan nama berbasis peran, lalu pakai tokennya.

Nama token berbasis **peran**, bukan rupa:

| ✅ Benar | ❌ Salah | Kenapa |
|---|---|---|
| `OnSurfaceMuted` | `Slate500` | Nama rupa mengunci kita pada satu palet; ganti palet = ganti semua nama |
| `Outline` | `DarkBorder` | Peran menjelaskan kapan dipakai |
| `SurfaceDark` | `Navy900` | |
| `WarningBg` | `Amber50` | |

### Kontrak 3 — `colorScheme` harus terisi penuh

`WeMadeLightColorScheme` wajib mengisi **seluruh** slot Material 3, bukan sebagian.

*Alasannya konkret*: slot yang tidak di-override tetap memakai **ungu default Material 3**, dan ungu
itu bocor diam-diam ke `AlertDialog`, `DropdownMenu`, dan `OutlinedTextField` yang tidak diberi
warna eksplisit. Kebocoran inilah yang dulu memaksa developer menulis warna manual di mana-mana —
lingkaran setan yang harus diputus dari akarnya.

Slot yang paling sering terlupakan: `surfaceVariant`, `onSurfaceVariant`, `outline`, `outlineVariant`,
`tertiary`, `surfaceContainer`/`surfaceContainerHigh`/`surfaceContainerHighest`, `inverseSurface`,
`scrim`.

---

## 3. Kontrak Komponen Bersama

### Kontrak 4 — Aturan Tiga Kali (Rule of Three)

> Kalau sebuah pola visual muncul **tiga kali atau lebih**, ia wajib diangkat menjadi komponen
> bersama di `presentation/designsystem/` sebelum pemakaian keempat ditulis.

Ini bukan aturan gaya, ini aturan pencegahan. Sebelum design system ada, kode ini punya:
- blok `Card(shape=…, colors=…, border=…, elevation=…)` identik di **~32 call site**
- **3 implementasi badge** yang nyaris sama di 3 package berbeda
- **343 literal `Color(0xFF……)`** yang mem-bypass theme

Dampaknya: satu perubahan desain = menyentuh puluhan file, dengan risiko terlewat di sebagian.

### Kontrak 5 — Pakai katalog yang sudah ada sebelum membuat baru

Sebelum menulis `Card`, `Button`, `Box` bergaya, atau badge apa pun, **cek katalognya dulu**:

| Kebutuhan | Pakai ini | Jangan |
|---|---|---|
| Kartu/panel dengan bayangan | `ClayCard` | `Card` Material |
| Tombol aksi | `ClayButton` | `Button` / `OutlinedButton` / `TextButton` |
| Toolbar dengan isi bebas | `ClayActionSurface` | `Row` + `clickable` manual |
| Pil status membulat | `ClayBadge` | `Box` + `clip` + `background` |
| Label persegi/tag padat | `ClayTag` | idem |
| Permukaan clay kustom | `Modifier.claySurface(…)` | `Modifier.shadow()` |
| Permukaan rata ber-outline | `Modifier.clayFlat(…)` | `.clip().background().border()` |

`Modifier.shadow()` **dilarang** di seluruh `presentation/**`: ia selalu menghasilkan bayangan
ber-blur mengikuti kurva elevation Material, yang berlawanan dengan bahasa visual kita.

### Kontrak 6 — Komponen bersama harus buta terhadap fitur

Komponen di `designsystem/` **tidak boleh** mengimpor apa pun dari `presentation/<fitur>/` atau dari
`domain/`. Dia menerima `String`, `Color`, dan lambda — bukan `PipelineNode` atau `FlowHealthStatus`.

```kotlin
// ❌ DITOLAK — design system tahu soal domain
@Composable fun ClayBadge(status: FlowHealthStatus) { … }

// ✅ BENAR — design system netral, fitur yang menerjemahkan
@Composable fun ClayBadge(text: String, tint: Color, dot: Boolean = false) { … }

// dan di lapisan fitur:
@Composable
fun HealthStatusPill(status: FlowHealthStatus) = ClayBadge(
    text = status.label,
    tint = Color(status.badgeColorHex),
    dot = true
)
```

Pembungkus tipis berbasis domain seperti `HealthStatusPill` **tetap di package fiturnya**.

### Kontrak 7 — Bentuk, ketebalan, dan spasi juga token

```kotlin
// ❌ DITOLAK
shape = RoundedCornerShape(12.dp)
border = BorderStroke(1.dp, …)
Arrangement.spacedBy(8.dp)

// ✅ BENAR
shape = ClayShapes.Chip
borderWidth = ClayBorder.Medium
Arrangement.spacedBy(ClaySpacing.Md)
```

Token bentuk dinamai per **peran** (`Panel` / `Card` / `Button` / `Tile` / `Chip` / `Pill`), bukan per
ukuran (`Large` / `Medium` / `Small`). Nama berbasis ukuran menggoda orang memakai `Medium` untuk dua
hal yang tidak berhubungan, lalu keduanya terikat selamanya.

### Kontrak 8 — Ketebalan outline konsisten, warnanya yang berbicara

State dibedakan lewat **warna outline**, bukan ketebalannya.

```kotlin
// ❌ DITOLAK — tiga ketebalan untuk tiga state; kartu terpilih jadi "menggemuk"
val border = when {
    isSelected   -> BorderStroke(2.dp, Primary)
    isBottleneck -> BorderStroke(1.5.dp, statusColor)
    else         -> BorderStroke(1.dp, Border)
}

// ✅ BENAR — satu ketebalan, warna yang membedakan
val outlineColor = when {
    isSelected   -> WeMadeColors.Primary
    isBottleneck -> Color(node.healthStatus.badgeColorHex)
    else         -> WeMadeColors.Outline
}
```

---

## 4. Kontrak Tipografi

### Kontrak 9 — Jangan tanam `color` ke dalam `TextStyle`

```kotlin
// ❌ DITOLAK — mematikan LocalContentColor; teks di atas kartu gelap tetap keluar slate
bodySmall = TextStyle(fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)

// ✅ BENAR — warna diserahkan ke pemanggil
bodySmall = TextStyle(fontSize = 12.sp)
```

`color` eksplisit di `TextStyle` selalu menang atas `LocalContentColor`, jadi menanamnya membuat
seluruh mekanisme pewarnaan kontekstual Compose tidak berfungsi.

### Kontrak 10 — Seluruh 15 peran Material wajib terdefinisi

Peran yang dilewat membuat komponen M3 bawaan jatuh ke default Roboto dan **tidak akan pernah**
ikut memakai Nunito, sebagus apa pun font-mu terpasang.

### Kontrak 11 — Font dibundel sebagai instance statis

Font ditaruh di `app/shared/src/commonMain/composeResources/font/`, nama huruf kecil + underscore.
**Gunakan instance statis per bobot, bukan variable font** — dukungan variable font belum seragam di
5 target KMP, dan gejalanya sulit didiagnosis (semua bobot ter-render sebagai Regular di sebagian
platform).

Package `Res` wajib dikunci di `app/shared/build.gradle.kts`:

```kotlin
compose.resources {
    publicResClass = true
    packageOfResClass = "com.eventverse.app.shared.resources"
    generateResClass = always
}
```

---

## 5. Kontrak Layout

### Kontrak 12 — Clay memakan ruang; sesuaikan densitasnya

Outline 3dp + hard shadow 6dp menambah **~18dp per kartu**. Setiap kali mengkonversi layar padat,
lebar kolom/kontainer wajib ditinjau ulang.

Contoh nyata: kolom swimlane Factory Flow dinaikkan **305dp → 324dp**.

Nunito juga punya **x-height lebih besar** dari Roboto/SF. Teks ≤10sp yang tadinya terbaca jadi
berdesakan. Naikkan tier ukuran **satu tingkat serentak** (9→10, 10→11, 11→12) — jangan berurutan,
karena substitusi bertahap akan menaikkan angka yang sama dua kali dan hierarki ukuran kolaps.

### Kontrak 13 — Elemen yang boleh mengalah wajib dinyatakan eksplisit

Di dalam `Row` dengan `SpaceBetween`, elemen yang boleh menyusut harus diberi
`Modifier.weight(1f, fill = false)` + `maxLines` + `overflow`. Tanpa itu, ia mengambil hampir seluruh
lebar dan menyisakan beberapa dp untuk tetangganya, yang lalu memecah teksnya **satu huruf per baris**.

```kotlin
Row(horizontalArrangement = Arrangement.SpaceBetween) {
    Row(modifier = Modifier.weight(1f, fill = false)) {      // boleh mengecil
        Text(labelPanjang, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(ClaySpacing.Sm))
    ClayBadge(text = status, tint = tint)                     // tidak boleh menyusut
}
```

### Kontrak 14 — Urutan modifier `claySurface` tidak boleh ditukar

Lihat [`.claude/skills/compose-design-system/references/clay-recipe.md`](../skills/compose-design-system/references/clay-recipe.md)
untuk penjelasan lengkapnya. Ringkasnya: `padding` (reservasi ruang bayangan) → `offset` → `drawBehind`
(bayangan) → `clip` → `background` → `drawBehind` (inner shade) → `border`.

Menaruh `drawBehind` bayangan **setelah** `clip` akan memotong habis bayangannya. Menghilangkan
`padding` akan membuat bayangan menimpa elemen tetangga.

---

## 6. Kontrak Mode Gelap

### Kontrak 15 — Mode gelap lewat theme, bukan lewat ternary

```kotlin
// ❌ DITOLAK — pola yang sedang kita bayar utangnya sekarang
color = if (isPresentationMode) Color(0xFF0F172A) else WeMadeColors.Surface
```

Utang teknis yang ada: "presentation mode" di Factory Flow masih berupa ~15 ternary
`if (isPresentationMode)` yang tersebar di 6 file. Token gelapnya sudah tersedia
(`WeMadeColors.SurfaceDark` / `SurfaceDarkElevated` / `BackgroundDark` / `OutlineInverse`).

**Jangan menambah ternary baru.** Fitur baru yang butuh mode gelap wajib menunggu atau ikut
mengerjakan `darkColorScheme` + `WeMadeTheme(darkTheme: Boolean)`.

---

## 7. Checklist Verifikasi Sebelum Merge (Definition of Done)

- [ ] Nol literal `Color(0xFF……)` baru di luar `WeMadeTheme.kt`
      (`grep -rn "Color(0xFF" <file-yang-disentuh>` — kecuali `*.colorHex` dari domain)
- [ ] Nol `RoundedCornerShape(N.dp)` telanjang; semua lewat `ClayShapes.*`
- [ ] Nol `Modifier.shadow()`; kedalaman lewat `claySurface(offset = …)`
- [ ] Nol `Card` / `Button` / `OutlinedButton` Material; pakai `ClayCard` / `ClayButton`
- [ ] Pola yang muncul ≥3 kali sudah diangkat ke `designsystem/`
- [ ] Komponen `designsystem/` tidak mengimpor `domain/` maupun package fitur
- [ ] Ketebalan outline konsisten; state dibedakan lewat warna
- [ ] Tidak ada `color` yang ditanam ke dalam `TextStyle`
- [ ] Densitas ditinjau: lebar kontainer dan tier ukuran font disesuaikan
- [ ] Elemen yang boleh mengalah punya `weight(1f, fill = false)` + `maxLines` + `overflow`
- [ ] Tidak ada ternary `isPresentationMode` baru
- [ ] **Kompilasi 5 target**, bukan satu:
      ```bash
      ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
                :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
                :app:shared:jvmTest
      ```
- [ ] **Dijalankan dan dilihat dengan mata**, bukan hanya dikompilasi — bug layout seperti teks
      pecah per huruf tidak akan tertangkap test mana pun
- [ ] **Belum login saat mengecek visual? Login dulu, jangan dilewati.** Kalau halaman yang dicek
      menampilkan "Akses Terbatas: Autentikasi Diperlukan", buka `http://localhost:3001/login` (repo B; repo A: `3000`), klik
      **"Demo Mode: Masuk Cepat (Superadmin Apps)"** (`superadmin_apps` / `PLATFORM_SUPERADMIN`),
      lalu kembali ke halaman tujuan dan lakukan pengecekannya. "Belum login" **bukan** alasan sah
      untuk melaporkan UI tanpa melihatnya.
- [ ] **Layar lain yang tidak dikonversi ikut diperiksa** jika `WeMadeTheme.kt` disentuh —
      `shapes` dan `colorScheme` berdampak ke seluruh aplikasi, jadi "pilot satu layar" tidak
      pernah benar-benar terisolasi
- [ ] Diuji di lebar sempit (~1280dp): reservasi ruang bayangan bekerja, kartu tidak saling timpa
- [ ] Perancah verifikasi sementara (preview main, dependency sementara) sudah dihapus
- [ ] Dokumentasi pengajaran dibuat di `docs/teaching/`

---

## 8. Utang Teknis Terdaftar (jangan ditambah, boleh dicicil)

| Utang | Lokasi | Ukuran |
|---|---|---|
| Literal warna belum disapu | `OrgChartScreen.kt` (108), `ModuleCardView.kt` (19), `AssignDepartmentModal.kt` (19), `ModuleMatrixCard.kt` (17), `RoleListSidebar.kt`, `OrgNodeCard.kt`, `TShapeChartView.kt`, `LoginScreen.kt` | ~290 literal |
| Layar belum dikonversi ke clay | Org Chart, RBAC, Login, top bar `App.kt:98-337` | 3 layar + shell |
| Mode gelap masih ternary | 6 file di `presentation/pipeline/` | ~15 ternary |
| Tidak ada adaptivitas window size | Seluruh `presentation/**` | greenfield |

Setiap kali menyentuh file di daftar ini untuk alasan apa pun, **cicil** bagiannya — jangan menambah
barisnya.
