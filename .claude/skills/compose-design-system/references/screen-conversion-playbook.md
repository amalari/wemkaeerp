# Playbook Konversi Layar ke Bahasa Clay

Untuk mengubah layar lama (Org Chart, RBAC, Login, top bar `App.kt`) ke design system clay.

Playbook ini ditulis dari pengalaman nyata mengkonversi Factory Flow — 11 file, ~290 baris berubah.
Urutan dan peringatannya berasal dari kesalahan yang benar-benar terjadi, bukan teori.

---

## Langkah 1 — Ukur dulu, jangan langsung mengetik

```bash
TARGET=app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart

echo "Literal warna:"; grep -rn "Color(0xFF" $TARGET | grep -v colorHex | wc -l
echo "Card Material:"; grep -rn "Card(" $TARGET | grep -v ClayCard | wc -l
echo "Button Material:"; grep -rnE "(^|[^y])(Button|OutlinedButton|TextButton)\(" $TARGET | wc -l
echo "Shape telanjang:"; grep -rn "RoundedCornerShape([0-9]" $TARGET | wc -l
echo "Lebar hardcoded:"; grep -rnE "\.width\([0-9]+\.dp\)" $TARGET
```

Angka terakhir yang paling penting: **lebar hardcoded adalah tempat konversi ini akan menyakitkan**,
karena clay menambah ~18dp per kartu.

---

## Langkah 2 — Konversi dari luar ke dalam

```
Layar (background, header)
  └─ Panel besar (Card → ClayCard, shape = Panel)
       └─ Kartu entitas (Card → ClayCard, shape = Card)
            └─ Chip & badge (Box+clip+bg → clayFlat / ClayTag / ClayBadge)
```

**Kenapa bukan sebaliknya?** Kalau mulai dari chip, kamu tidak punya konteks visual untuk menilai
apakah ukurannya pas. Chip yang terlihat benar sendirian sering terlalu besar di dalam kartu yang
sudah ber-outline 3dp.

### Pola penggantian

```kotlin
// ── Card Material ───────────────────────────────────────────────
// SEBELUM
Card(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
    border = BorderStroke(1.dp, WeMadeColors.Border),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) { … }
}

// SESUDAH — perhatikan padding pindah ke contentPadding
ClayCard(
    modifier = modifier,
    contentPadding = PaddingValues(16.dp)
) {
    Column(modifier = Modifier.fillMaxWidth()) { … }
}

// ── Chip manual ─────────────────────────────────────────────────
// SEBELUM
Modifier
    .clip(RoundedCornerShape(8.dp))
    .background(WeMadeColors.PrimaryContainer)
    .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))

// SESUDAH
Modifier.clayFlat(
    shape = ClayShapes.Chip,
    background = WeMadeColors.PrimaryContainer,
    outline = WeMadeColors.Primary.copy(alpha = 0.35f),
    borderWidth = ClayBorder.Medium
)

// ── Badge lokal ─────────────────────────────────────────────────
// SEBELUM
Box(
    modifier = Modifier
        .clip(RoundedCornerShape(4.dp))
        .background(color.copy(alpha = 0.12f))
        .padding(horizontal = 6.dp, vertical = 2.dp)
) {
    Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
}

// SESUDAH
ClayTag(text = label, tint = color, fontSize = 11.sp)

// ── Border bertingkat untuk state ───────────────────────────────
// SEBELUM — tiga ketebalan; kartu terpilih "menggemuk"
val border = when {
    isSelected   -> BorderStroke(2.dp, Primary)
    isBottleneck -> BorderStroke(1.5.dp, statusColor)
    else         -> BorderStroke(1.dp, Border)
}

// SESUDAH — satu ketebalan, warna yang membedakan
val outlineColor = when {
    isSelected   -> WeMadeColors.Primary
    isBottleneck -> Color(node.healthStatus.badgeColorHex)
    else         -> WeMadeColors.Outline
}
```

---

## Langkah 3 — Sapu warna ke token, satu pass serentak

```bash
cd app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart

perl -pi -e '
  s/Color\(0xFFF1F5F9\)/WeMadeColors.SurfaceMuted/g;
  s/Color\(0xFFF8FAFC\)/WeMadeColors.Background/g;
  s/Color\(0xFFE2E8F0\)/WeMadeColors.Border/g;
  s/Color\(0xFF64748B\)/WeMadeColors.OnSurfaceMuted/g;
  s/Color\(0xFF1E293B\)/WeMadeColors.OnSurface/g;
  s/Color\(0xFF2563EB\)/WeMadeColors.Primary/g;
  s/Color\(0xFFEA580C\)/WeMadeColors.Accent/g;
  s/Color\(0xFF16A34A\)/WeMadeColors.Success/g;
  s/Color\(0xFFDC2626\)/WeMadeColors.Error/g;
  s/Color\(0xFFD97706\)/WeMadeColors.Warning/g;
  s/Color\(0xFF0284C7\)/WeMadeColors.Info/g;
  s/Color\(0xFFE11D48\)/WeMadeColors.Defect/g;
' *.kt components/*.kt

# Verifikasi: harus nol (colorHex dari domain itu sah)
grep -rn "Color(0xFF" . | grep -v colorHex
```

> ⚠️ **Warna yang tidak ada di daftar itu belum punya token.** Jangan biarkan literalnya. Tambahkan
> token ke `WeMadeColors` (nama berbasis peran!), lalu tambahkan barisnya ke skrip di atas.

> ⚠️ **Perhatikan konteks sebelum mengganti.** `0xFF1E293B` bisa berarti `OnSurface` (warna teks)
> **atau** `SurfaceDarkElevated` (latar mode gelap). Periksa hasilnya sebelum commit; sapuan buta
> akan menukar makna keduanya.

---

## Langkah 4 — Naikkan tier ukuran font, serentak

Nunito punya **x-height lebih besar** dari Roboto/SF. Teks ≤10sp yang tadinya terbaca jadi
berdesakan.

```bash
# Serentak dalam satu pass, dengan callback /e
perl -pi -e 's/fontSize = (9|10|11)\.sp/"fontSize = " . ($1+1) . ".sp"/ge' *.kt components/*.kt
```

> ⚠️ **Jangan berurutan.** Kalau kamu jalankan `9→10` dulu lalu `10→11`, angka 9 naik dua kali jadi
> 11 dan hierarki ukuranmu kolaps. Substitusi serentak dengan `/e` menghindari ini.

---

## Langkah 5 — Tinjau densitas

Clay menambah **~18dp per kartu** (outline 3dp + bayangan 6dp, dua sisi). Setiap lebar hardcoded
dari Langkah 1 wajib ditinjau:

```kotlin
// Contoh nyata dari Factory Flow
modifier = Modifier.width(324.dp)   // dinaikkan dari 305.dp
contentPadding = PaddingValues(16.dp)  // dinaikkan dari 14.dp
```

Kandidat yang perlu diperiksa di layar yang belum dikonversi:

| Lokasi | Lebar sekarang |
|---|---|
| `OrgChartScreen.kt:112` panel form | 420dp |
| `OrgChartScreen.kt:224` panel arsip | 400dp |
| `DynamicRbacScreen.kt:190` sidebar | 320dp |
| `OrgNodeCard.kt:49` kartu node | 220dp ← paling sempit, paling berisiko |
| `TShapeChartView.kt` kartu | 220–240dp |

---

## Langkah 6 — Amankan elemen yang boleh mengalah

Di setiap `Row` dengan `SpaceBetween` yang berisi label panjang + badge:

```kotlin
Row(horizontalArrangement = Arrangement.SpaceBetween) {
    Row(modifier = Modifier.weight(1f, fill = false)) {      // boleh mengecil
        Text(labelPanjang, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(ClaySpacing.Sm))
    ClayBadge(text = status, tint = tint)                     // tidak boleh menyusut
}
```

**Tanpa ini**, `Row` kiri mengukur diri lebih dulu, mengambil hampir seluruh lebar, dan menyisakan
beberapa dp untuk badge — yang lalu memecah teksnya **satu huruf per baris**. Ini persis yang terjadi
pada label `"RANTAI PASOK & BAHAN BAKU"` di Factory Flow.

`weight(1f, fill = false)` artinya: *"kamu boleh mengecil kalau perlu, tapi jangan memaksa melebar."*

---

## Langkah 7 — Verifikasi

### 7a. Kompilasi 5 target

```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
          :app:shared:jvmTest
```

Kode bisa lolos JVM tapi gagal WasmJS — resource font terutama rawan.

### 7b. Verifikasi Visual (wajib, bukan opsional)

Bug layout tidak akan tertangkap test mana pun. Layar di balik auth guard bisa dilihat lewat entry
point sementara:

```kotlin
// app/desktopApp/src/main/kotlin/com/eventverse/app/ClayPreviewMain.kt
// TEMPORARY — hapus setelah review
package com.eventverse.app

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(size = DpSize(1600.dp, 1000.dp)),
        title = "Clay Preview",
        alwaysOnTop = true            // supaya bisa di-screenshot
    ) {
        WeMadeTheme {
            // Dikendalikan env var; mengklik butuh izin Accessibility yang mungkin tidak ada
            when (System.getenv("CLAY_SCREEN") ?: "flow") {
                "orgchart" -> OrgChartScreen()
                "rbac"     -> DynamicRbacScreen()
                else       -> FactoryFlowScreen()
            }
        }
    }
}
```

Lalu arahkan `mainClass` sementara di `app/desktopApp/build.gradle.kts`:

```kotlin
mainClass = "com.eventverse.app.ClayPreviewMainKt"
```

```bash
CLAY_SCREEN=orgchart ./gradlew :app:desktopApp:run
screencapture -x -o -D 2 /tmp/preview.png    # -D memilih display
```

> ⚠️ **Catatan lingkungan**: target desktop **tidak punya Ktor client engine**, jadi layar yang
> membuat API client akan melempar `ExceptionInInitializerError` sebelum apa pun ter-render. Untuk
> preview, tambahkan `implementation("io.ktor:ktor-client-cio:3.5.1")` sementara ke `desktopApp`.
> Ini bug yang sudah ada di target desktop, bukan akibat konversimu.

### 7c. Periksa layar lain kalau `WeMadeTheme.kt` disentuh

`shapes` dan `colorScheme` berdampak ke **seluruh aplikasi**. "Pilot satu layar" tidak pernah
benar-benar terisolasi. Buka layar lain dan pastikan `AlertDialog`, `FilterChip`, dan
`OutlinedTextField` masih terbaca dan tidak ada ungu Material yang bocor.

### 7d. Uji di lebar sempit

Kecilkan jendela sampai ~1280dp. Pastikan reservasi ruang bayangan bekerja dan kartu tetangga tidak
saling menimpa.

---

## Langkah 8 — Bersihkan perancah

```bash
rm app/desktopApp/src/main/kotlin/com/eventverse/app/ClayPreviewMain.kt
# kembalikan mainClass = "com.eventverse.app.MainKt"
# hapus dependency ktor-client-cio sementara

# Pastikan bersih
grep -rn "ClayPreview\|ktor-client-cio\|CLAY_SCREEN" --include="*.kt" --include="*.kts" . \
  | grep -v "/build/" | grep -v docs/
```

Perancah verifikasi yang tertinggal di repo adalah utang teknis yang membingungkan orang berikutnya.

---

## Langkah 9 — Dokumentasi

Panggil skill `teaching` → `docs/teaching/teaching-[slug].md`. Ini wajib otomatis per
`CLAUDE.md` §12 (atau `AGENTS.md` §11), tidak perlu menunggu diminta.

---

## Ringkasan Checklist

- [ ] Diukur dulu (literal warna, Card/Button, lebar hardcoded)
- [ ] Dikonversi dari luar ke dalam
- [ ] Warna disapu ke token; hasil `grep "Color(0xFF"` nol di luar `colorHex`
- [ ] Tier font dinaikkan serentak, bukan berurutan
- [ ] Lebar kontainer ditinjau ulang
- [ ] Elemen yang boleh mengalah punya `weight(1f, fill = false)` + `maxLines` + `overflow`
- [ ] Kompilasi 5 target hijau
- [ ] Dijalankan & dilihat dengan mata
- [ ] Layar lain diperiksa kalau theme disentuh
- [ ] Diuji di ~1280dp
- [ ] Perancah dihapus
- [ ] Dokumentasi teaching dibuat
