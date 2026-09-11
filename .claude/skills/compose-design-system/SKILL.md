---
name: compose-design-system
description: >
  Design system clay (Claymorphism + Neo-Brutalism) untuk WeMade ERP di Compose Multiplatform.
  Aktifkan skill ini saat membuat atau mengubah UI apa pun di app/shared: menambah warna atau token,
  membuat komponen bersama, mengkonversi layar ke bahasa visual clay, memperbaiki styling yang
  tidak konsisten, atau saat user menyebut warna/tema/font/kartu/tombol/badge/spacing/radius/bayangan.
  Juga aktif untuk pertanyaan "kenapa warnanya begini", "komponennya taruh di mana",
  "kok Modifier.shadow tidak boleh", atau review PR yang menyentuh presentation layer.
---

# Compose Design System — WeMade ERP (Clay)

Skill ini adalah **panduan kerja**. Aturan yang mengikat ada di
[`.claude/rules/design-system-rules.md`](../../rules/design-system-rules.md) — baca itu dulu kalau
belum pernah. Skill ini menjawab *"jadi saya harus ngapain"*.

---

## Gerbang Masuk: tentukan dulu kamu sedang mengerjakan apa

| Situasi | Lompat ke |
|---|---|
| Butuh warna yang belum ada | [§1 Menambah token warna](#1-menambah-token-warna) |
| Menulis UI baru di layar yang sudah ada | [§2 Menulis UI baru](#2-menulis-ui-baru) |
| Pola visual mulai berulang | [§3 Mengangkat komponen bersama](#3-mengangkat-komponen-bersama) |
| Mengkonversi layar lama ke clay | [§4 Playbook konversi layar](#4-playbook-konversi-layar) |
| Butuh permukaan clay yang tidak ada komponennya | `references/clay-recipe.md` |
| Mau tahu komponen apa saja yang tersedia | `references/component-catalog.md` |
| Review PR orang lain | [§5 Checklist review](#5-checklist-review) |

---

## 1. Menambah Token Warna

**Pertanyaan pertama, selalu**: apakah ini keputusan desain, atau data domain?

```
Warnanya ditentukan tenant / tersimpan di database / ada di enum domain?
  → JANGAN bikin token. Pakai Color(x.colorHex) langsung.
    Contoh: PipelineStage.colorHex, FlowHealthStatus.badgeColorHex, Department.colorHex

Warnanya keputusan desain kita?
  → Token wajib. Lanjut ke bawah.
```

### Langkahnya

1. **Cek dulu apakah sudah ada.** Buka `WeMadeColors` di
   [`presentation/theme/WeMadeTheme.kt`](../../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/theme/WeMadeTheme.kt).
   Sebagian besar kebutuhan sudah tertutup.

2. **Kalau benar belum ada, tambahkan di `WeMadeColors`** — dan **hanya** di situ.

3. **Namai berdasarkan peran, bukan rupa.**

   | ✅ | ❌ | |
   |---|---|---|
   | `SurfaceMuted` | `Slate100` | Nama rupa mengunci ke satu palet |
   | `Info` | `SkyBlue` | |
   | `Defect` | `Rose600` | |
   | `OnSurfaceInverse` | `LightGrayText` | |

4. **Kalau warnanya punya pasangan gelap**, tambahkan sekalian dengan akhiran `Inverse` atau `Dark`
   (`OnSurfaceInverse`, `BorderInverse`, `ErrorBgDark`). Ini jadi bahan baku `darkColorScheme` nanti.

5. **Kalau perannya juga ada di Material**, isi slotnya di `WeMadeLightColorScheme`. Slot yang
   dibiarkan kosong akan memakai ungu default M3 dan bocor ke `AlertDialog`/`DropdownMenu`/`TextField`.

### Yang TIDAK boleh

```kotlin
// ❌ "sementara nanti dirapikan" — tidak ada yang merapikan
Box(Modifier.background(Color(0xFFF1F5F9)))
```

Sebelum ada design system, kode ini punya **343 literal warna** yang semuanya bermula dari
"sementara". Package `pipeline/` sekarang **nol literal** — jaga angka itu.

---

## 2. Menulis UI Baru

Urutannya selalu: **cari komponen → cari token → baru menulis**.

### Alur keputusan

```
Butuh kotak berisi konten, terangkat dari latar?
  → ClayCard

Butuh sesuatu yang diklik?
  Isinya cuma teks (+ ikon)?     → ClayButton
  Ikon saja (tombol ✕, toolbar)? → ClayIconButton
  Isinya bebas (ikon+angka+dll)? → ClayActionSurface

Butuh label kecil berwarna?
  Membulat penuh, status?        → ClayBadge
  Persegi, padat, di sela teks?  → ClayTag

Butuh permukaan yang tidak cocok semuanya?
  Perlu bayangan?                → Modifier.claySurface(...)
  Menempel rata di latar?        → Modifier.clayFlat(...)
```

Detail parameter tiap komponen ada di `references/component-catalog.md`.

### Yang dilarang keras

```kotlin
Modifier.shadow(elevation = 4.dp)      // ❌ selalu blur, berlawanan dengan bahasa visual kita
Card(...) / Button(...) / OutlinedButton(...)  // ❌ Material mentah
RoundedCornerShape(12.dp)              // ❌ pakai ClayShapes.Chip
BorderStroke(1.dp, ...)                // ❌ pakai ClayBorder.*
.clip(s).background(c).border(w, o, s) // ❌ pakai Modifier.clayFlat(...)
```

### Contoh lengkap

```kotlin
ClayCard(
    modifier = modifier,
    containerColor = WeMadeColors.Surface,
    outlineColor = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
    selected = isSelected,                       // kartu menetap di posisi tertekan
    contentPadding = PaddingValues(ClaySpacing.Xl),
    onClick = onClick
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f, fill = false),   // ini yang boleh mengalah
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = WeMadeColors.OnSurface
        )
        Spacer(Modifier.width(ClaySpacing.Sm))
        ClayBadge(text = status.label, tint = Color(status.badgeColorHex), dot = true)
    }
}
```

---

## 3. Mengangkat Komponen Bersama

### Kapan wajib

**Aturan Tiga Kali**: pola yang muncul ≥3 kali wajib diangkat ke `designsystem/` sebelum
pemakaian keempat ditulis.

Cara cepat memeriksa sebelum menulis yang keempat:

```bash
# Berapa kali blok Card manual masih ada?
grep -rn "Card(" app/shared/src/commonMain/kotlin/com/eventverse/app/presentation --include=*.kt | grep -v ClayCard

# Berapa kali pola chip manual muncul?
grep -rn -A2 "clip(RoundedCornerShape" app/shared/src/commonMain/kotlin/com/eventverse/app/presentation --include=*.kt | grep -c background
```

### Cara mengangkatnya (tanpa bikin diff raksasa)

Ini pola dua langkah yang membuat PR tetap bisa di-review:

**Langkah 1 — buat komponen netral di `designsystem/`.**
Dia hanya boleh menerima tipe primitif dan `Color`. **Dilarang** mengimpor `domain/` atau package fitur.

```kotlin
// designsystem/ClayBadge.kt — buta terhadap domain
@Composable
fun ClayBadge(text: String, tint: Color, dot: Boolean = false, ...) { ... }
```

**Langkah 2 — ubah komponen lama jadi pembungkus tipis, jangan hapus namanya.**

```kotlin
// pipeline/components/PipelineNodeCard.kt — tetap di package fitur
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

**Kenapa tidak langsung hapus nama lamanya?** Karena memisahkan "mengubah implementasi" dari
"mengubah call site" membuat diff-nya kecil dan reviewer bisa menilai satu hal pada satu waktu.
Call site dibersihkan di PR terpisah, atau dibiarkan kalau pembungkusnya memang bermakna.

### Di mana meletakkannya

```
designsystem/          ← netral, reusable, buta domain
pipeline/components/   ← pembungkus berbasis domain, spesifik fitur
```

Kalau komponenmu butuh tahu soal `PipelineNode`, dia **bukan** penghuni `designsystem/`.

---

## 4. Playbook Konversi Layar

Untuk mengubah layar lama (Org Chart, RBAC, Login, top bar `App.kt`) ke bahasa clay.

Langkah lengkap dengan perintahnya ada di `references/screen-conversion-playbook.md`.
Ringkasnya:

1. **Ukur dulu** — hitung literal warna dan call site `Card`/`Button` di layar itu.
2. **Dari luar ke dalam** — layar → panel → kartu → chip. Jangan mulai dari chip: kamu tidak punya
   konteks visual untuk menilai ukurannya.
3. **Sapu token dengan `perl`**, satu pass serentak. Jangan berurutan.
4. **Tinjau densitas** — clay menambah ~18dp per kartu; lebar kontainer dan tier font wajib diperiksa.
5. **Kompilasi 5 target**, bukan satu.
6. **Jalankan dan lihat** — ini yang menemukan bug layout; tidak ada test yang menangkapnya.
7. **Periksa layar lain** kalau `WeMadeTheme.kt` ikut disentuh.

---

## 5. Checklist Review

Saat mereview PR yang menyentuh `presentation/**`:

```bash
# 1. Literal warna baru? (colorHex dari domain itu sah)
git diff --unified=0 origin/main -- 'app/shared/**/presentation/**' \
  | grep '^+' | grep 'Color(0xFF' | grep -v colorHex

# 2. Shape / border telanjang?
git diff --unified=0 origin/main -- 'app/shared/**/presentation/**' \
  | grep '^+' | grep -E 'RoundedCornerShape\([0-9]|BorderStroke\([0-9]'

# 3. Material mentah atau shadow?
#    Batas kata di depan penting: tanpa itu, KpiStatCard( dan PipelineNodeCard( ikut tertangkap.
git diff --unified=0 origin/main -- 'app/shared/**/presentation/**' \
  | grep '^+' \
  | grep -E '(Modifier\.shadow|(^\+|[^A-Za-z])(Card|Button|OutlinedButton|TextButton|IconButton|ElevatedCard|FilledTonalButton)\()'

# 4. Ternary mode gelap baru?
git diff --unified=0 origin/main -- 'app/shared/**/presentation/**' \
  | grep '^+' | grep 'isPresentationMode'

# 5. designsystem/ bocor ke domain?
grep -rn "import com.eventverse.app.domain" \
  app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/designsystem/
```

Kelima perintah itu idealnya **nol hasil**. Hasil dari #1 boleh ada kalau itu `colorHex` domain.

Lalu checklist manual:

- [ ] Pola berulang ≥3 kali sudah diangkat ke `designsystem/`
- [ ] Ketebalan outline konsisten; state dibedakan lewat warna, bukan ketebalan
- [ ] Tidak ada `color` yang ditanam ke `TextStyle`
- [ ] Elemen yang boleh mengalah punya `weight(1f, fill = false)` + `maxLines` + `overflow`
- [ ] Densitas ditinjau (lebar kontainer, tier ukuran font)
- [ ] Sudah dijalankan dan dilihat, bukan cuma dikompilasi
- [ ] Perancah verifikasi sementara sudah dihapus

---

## 6. Verifikasi

```bash
# Kompilasi 5 target — WasmJS sering gagal padahal JVM lolos, terutama soal resource font
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
          :app:shared:jvmTest
```

**Lalu jalankan dan lihat dengan mata.** Ini bukan opsional. Bug seperti badge yang memecah teks
menjadi satu huruf per baris (`N-o-r-m-a-l`) hanya muncul di layar penuh dengan data nyata —
tidak ada unit test yang akan menangkapnya.

Cara menjalankan layar yang ada di balik auth guard, dan cara membersihkannya lagi, ada di
`references/screen-conversion-playbook.md` bagian "Verifikasi Visual".

---

## 7. Pertanyaan yang Sering Muncul

**"Kenapa `Modifier.shadow()` tidak boleh?"**
Dia adalah implementasi kurva elevation Material — parameter `elevation` diterjemahkan jadi radius
blur. Tidak ada cara mematikan blur-nya. Bahasa visual kita butuh bidang solid tanpa blur, jadi
bayangannya digambar sendiri. Mekanismenya di `references/clay-recipe.md`.

**"Kartu saya bayangannya hilang."**
Hampir pasti `clip` diletakkan sebelum `drawBehind` bayangan. `clip` membuat `graphicsLayer` yang
memotong semua yang datang setelahnya. Ingat: **"`clip` adalah pintu; apa pun setelahnya ada di
dalam ruangan."**

**"Bayangan kartu saya menimpa kartu sebelahnya."**
`drawBehind` tidak dibatasi bounds. `claySurface` menyisihkan ruang lewat `padding` di langkah
pertama — kalau kamu menulis rantai modifier sendiri, kamu harus melakukannya juga.

**"Panel saya di tepi kanan, bayangannya jatuh ke luar layar."**
Pakai `shadowX` negatif: `claySurface(shadowX = -ClayOffset.Rest, shadowY = 0.dp)`. Reservasi
ruangnya otomatis pindah ke sisi `start`.

**"Layout saya jadi sesak setelah dikonversi."**
Benar, itu memang terjadi. Outline 3dp + bayangan 6dp = ~18dp per kartu. Dan Nunito punya x-height
lebih besar dari Roboto/SF, jadi teks ≤10sp terasa berdesakan. Naikkan lebar kontainer dan tier
ukuran font satu tingkat.

**"Boleh saya pakai palet pastel dari referensi desain aslinya?"**
Tidak. Factory Flow memakai hijau/amber/merah sebagai sinyal produksi. Pastel meredam kontras
sinyal itu, dan operator kehilangan kemampuan membedakan node sehat dari bottleneck sekilas.
Bahasa clay diambil **bentuknya**, bukan warnanya.

**"Saya butuh mode gelap untuk fitur baru saya."**
Jangan menambah ternary `if (isPresentationMode)` baru — sudah ada ~15 yang jadi utang teknis.
Token gelapnya sudah tersedia (`SurfaceDark`, `OnSurfaceInverse`, `BorderInverse`, dst).
Kerjakan `darkColorScheme` + `WeMadeTheme(darkTheme: Boolean)` sekalian, atau tunggu.

---

## Referensi

- `references/clay-recipe.md` — mekanika `claySurface`, urutan modifier, matematika animasi tekan
- `references/component-catalog.md` — daftar komponen, signature, kapan pakai yang mana
- `references/screen-conversion-playbook.md` — langkah konversi layar + verifikasi visual
- [`.claude/rules/design-system-rules.md`](../../rules/design-system-rules.md) — aturan mengikat & DoD
- [`docs/teaching/teaching-claymorphism-design-system.md`](../../../docs/teaching/teaching-claymorphism-design-system.md) — penjelasan mendalam
