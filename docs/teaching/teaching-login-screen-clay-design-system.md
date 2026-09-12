# 🎓 Modul Pembelajaran: Transformasi Login Screen ke Claymorphism Design System & Eliminasi Persona Pengujian

> **Level Target**: Junior to Mid Compose Multiplatform Developer  
> **Topik Utama**: Claymorphism, Neo-Brutalism, Design Tokens, WeMade Colors, Compose Multiplatform UI  
> **Prasyarat**: Dasar Jetpack Compose / Compose Multiplatform, State Hosting, Pemahaman Token Design System  
> **Referensi File**: [`LoginScreen.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/auth/LoginScreen.kt), [`WeMadeTheme.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/theme/WeMadeTheme.kt)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Sebelum refaktorisasi ini:
1. **Kebocoran Persona Testing ke Layar Publik**: Di bawah kartu login terdapat daftar panjang persona pengujian RBAC (*Testing Persona*). Di lingkungan produksi atau demo rapi, tombol persona pengujian di halaman login publik membuat antarmuka terasa seperti *debug screen* alih-alih gerbang login enterprise profesional. Pengujian persona seharusnya diakses dari dalam dashboard/top bar (`PersonaSwitcherDropdown`) saat pengguna terautentikasi.
2. **Utang Teknis Desain (Bypass Theme & Literal Colors)**: File `LoginScreen.kt` sebelumnya tercatat dalam utang teknis `design-system-rules.md`. Kode menggunakan komponen mentah Material 3 (`Card`, `OutlinedButton`, `Button`, `FilledTonalButton`), `RoundedCornerShape(16.dp)`, `BorderStroke(1.dp, ...)`, serta warna *hardcoded* literal `Color(0xFFF1F5F9)`, `Color(0xFFF3E8FF)`, `Color(0xFF7E22CE)`, dan `Color(0xFFF8FAFC)`. Ini melanggar Kontrak 1 & 5 Design System WeMade ERP.

### Hasil Akhir yang Dicapai
- Bagian testing persona dieliminasi sepenuhnya dari layar login.
- Antarmuka login bertransformasi 100% mengikuti bahasa visual baku: **Claymorphism + Neo-Brutalism** (outline tebal 3dp `ClayBorder.Thick`, hard shadow solid `ClayOffset.Rest` tanpa blur, sudut membulat ramah 24dp `ClayShapes.Panel`, dan font Fredoka + Nunito).
- Nol literal warna baru (`Color(0xFF...)` dieliminasi total; warna brand pihak ketiga seperti Google diangkat ke `WeMadeColors`).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika seorang junior developer diminta mengonversi layar dari gaya standar/Material ke Clay Design System:

1. **Langkah 0: Audit Utang & Literal Warna**
   - Jalankan `grep -rn "Color(0xFF" <file>` untuk mencatat semua warna *hardcoded*.
   - Analisis apakah warna tersebut adalah warna brand eksternal, peran UI baru, atau sudah ada padanannya di `WeMadeColors`.
2. **Langkah 1: Angkat Token ke `WeMadeColors` (Single Source of Truth)**
   - Jika ada warna yang sah (misal palet vektor resmi Google: `GoogleBlue`, `GoogleGreen`, `GoogleYellow`, `GoogleRed`), daftarkan terlebih dahulu di `WeMadeTheme.kt`. Jangan biarkan ada deklarasi `Color(0xFF...)` di file layar fitur.
3. **Langkah 2: Bersihkan Elemen yang Tidak Lagi Diperlukan**
   - Hapus komponen atau blok yang diminta dieliminasi (seperti `PersonaLoginSection` dan pemanggilannya) beserta import-import yang tidak lagi terpakai.
4. **Langkah 3: Konversi Kontainer Terluar (Dari Luar ke Dalam)**
   - Latar belakang halaman diselaraskan ke `WeMadeColors.BackgroundWarm` (`#FDFAF7`).
   - Kartu utama `Card(...)` diganti menjadi `ClayCard(shape = ClayShapes.Panel, borderWidth = ClayBorder.Thick, offset = ClayOffset.Rest, containerColor = WeMadeColors.Surface)`.
5. **Langkah 4: Konversi Input & Kontrol Interaktif**
   - `OutlinedTextField` diganti dengan `ClayTextField` yang memiliki label mandiri, bayangan tactile saat fokus, dan `ClayTag` untuk trailing text.
   - Segmented tab bar diganti menggunakan `clayFlat` container dan item terpilih dengan `claySurface(pressed = true)`.
6. **Langkah 5: Konversi Seluruh Tombol Aksi ke `ClayButton`**
   - Tombol Google: `ClayButton(style = ClayButtonStyle.Secondary)`
   - Tombol Demo Owner: `ClayButton(style = ClayButtonStyle.Primary)`
   - Tombol Demo Superadmin: `ClayButton(style = ClayButtonStyle.Accent, leading = { IconZap(...) })`
7. **Langkah 6: Verifikasi Multiplatform & Visual**
   - Kompilasi target JVM dan WasmJS.
   - Jalankan automated tests dan inspeksi visual di browser.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pengangkatan Token Brand Resmi di `WeMadeTheme.kt`

```kotlin
// presentation/theme/WeMadeTheme.kt
object WeMadeColors {
    // ... token tema WeMade lainnya ...

    // ── Asset pihak ketiga ──────────────────────────────────────────────────────────────────
    val GoogleBlue = Color(0xFF4285F4)
    val GoogleGreen = Color(0xFF34A853)
    val GoogleYellow = Color(0xFFFBBC05)
    val GoogleRed = Color(0xFFEA4335)
}
```

**Mengapa blok ini ditulis begini?**
- Mematuhi **Kontrak 1**: *Satu-satunya tempat `Color(0xFF……)` boleh ditulis adalah `WeMadeTheme.kt`*.
- Menghindari duplikasi deklarasi warna hex di berbagai layar saat tombol SSO Google digunakan kembali di modul lain.

---

### Blok B: Kartu Utama Menggunakan `ClayCard` dengan Sudut Panel 24dp

```kotlin
// presentation/auth/LoginScreen.kt
ClayCard(
    modifier = Modifier.fillMaxWidth(),
    shape = ClayShapes.Panel,
    containerColor = WeMadeColors.Surface,
    outlineColor = WeMadeColors.Outline,
    offset = ClayOffset.Rest,
    borderWidth = ClayBorder.Thick,
    contentPadding = PaddingValues(ClaySpacing.Xxl)
) {
    // Isi formulir dan tombol autentikasi
}
```

**Mengapa blok ini ditulis begini?**
- `ClayCard` secara otomatis menerapkan chain modifier:
  1. `padding(end = offset, bottom = offset)` untuk mereservasi ruang hard shadow.
  2. `drawBehind` untuk menggambar bayangan solid (tanpa blur) berwarna `Outline` (`#1E293B`).
  3. `clip(shape)` memotong konten di sudut 24dp.
  4. `drawBehind` inner shade 10% di bagian bawah untuk menghasilkan efek tactile clay.
  5. `border(ClayBorder.Thick, Outline, shape)` memberikan outline neo-brutalis 3dp yang tegas.

---

### Blok C: Input Tenant Slug Menggunakan `ClayTextField` & `ClayTag`

```kotlin
@Composable
private fun TenantSlugInput(
    tenantSlug: String,
    onSlugChange: (String) -> Unit
) {
    ClayTextField(
        value = tenantSlug,
        onValueChange = onSlugChange,
        modifier = Modifier.fillMaxWidth(),
        label = "Subdomain / Kode Pabrik",
        placeholder = "contoh: wemade-demo",
        focusColor = WeMadeColors.Primary,
        trailingIcon = {
            ClayTag(
                text = ".wemade.id",
                tint = WeMadeColors.Primary
            )
        }
    )
}
```

**Mengapa blok ini ditulis begini?**
- `ClayTextField` menempatkan label di atas input secara independen (bukan floating notch Material yang memotong garis border tebal neo-brutalis).
- `trailingIcon` disematkan dengan `ClayTag` yang memiliki radius chip dan background tint senada dengan warna fokus `Primary`.

---

### Blok D: Tombol Aksi Cepat dengan `ClayButton`

```kotlin
// Quick Demo Login Button (Superadmin Apps / Platform Admin)
ClayButton(
    text = "Demo Mode: Masuk Cepat (Superadmin Apps)",
    onClick = onDemoSuperAdminLoginClick,
    enabled = !isLoading,
    style = ClayButtonStyle.Accent,
    fontSize = 12.sp,
    offset = ClayOffset.Small,
    contentPadding = PaddingValues(horizontal = ClaySpacing.Lg, vertical = 9.dp),
    modifier = Modifier.fillMaxWidth(),
    leading = {
        IconZap(modifier = Modifier.size(14.dp), color = Color.White)
    }
)
```

**Mengapa blok ini ditulis begini?**
- Menggantikan `FilledTonalButton` yang sebelumnya memakai warna ungu hardcoded.
- Menggunakan `ClayButtonStyle.Accent` (Safety Orange WeMade `#EA580C`) yang merupakan warna identitas keselamatan konveksi WeMade.
- Memakai Canvas vector icon `IconZap` bawaan design system yang bebas dari dependensi font ikon eksternal atau SVG parsing runtime.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan Terpilih | Alternatif yang Ada | Mengapa Memilih Pendekatan Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Claymorphism + Neo-Brutalism Custom Canvas** | Material 3 Default (`ElevatedCard`, `Button`, `shadow()`) | Memberikan identitas visual brand WeMade ERP yang khas, tactile, ramah pengguna, dan presisi di seluruh platform desktop/web/mobile. | Tampilan generic Material 3, bayangan selalu blur (tidak neo-brutalist), notch TextField sering merusak outline tebal. |
| **Pemusatan Warna di `WeMadeColors`** | Literal `Color(0xFF...)` tersebar di Composable | Single source of truth; mempermudah pengenalan Dark Theme global nantinya. | Terjadi fragmentasi warna, bug kontras, dan puluhan file harus diubah manual jika palet diupdate. |
| **Canvas Vector Icons (`ClayIcons.kt`)** | Icon Font / SVG XML per platform | Menggambar langsung ke Skia/Skiko canvas yang 100% konsisten di WebAssembly, Desktop, iOS, dan Android tanpa masalah font hilang/tofu. | Ikon pecah atau render kotak-kotak (tofu) di WebAssembly/WasmJs akibat font sistem yang berbeda. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Menggunakan `Modifier.shadow()` pada Kartu Clay**
   - *Kenapa bahaya*: `Modifier.shadow()` selalu merender gaussian blur elevation. Dalam Neo-Brutalism, bayangan adalah bidang solid tanpa blur sama sekali.
   - *Solusi elegan*: Gunakan `ClayCard` atau `Modifier.claySurface()` yang menggambar hard shadow via `drawBehind`.
2. **Jebakan 2: Meletakkan `clip()` Sebelum `drawBehind()` Bayangan**
   - *Kenapa bahaya*: `clip()` bertindak sebagai batas terluar. Jika bayangan digambar setelah clip, bayangan akan terpotong habis.
   - *Solusi elegan*: Bayangan harus digambar di luar bounds sebelum `clip()`, dengan `padding()` awal untuk mengalokasikan ruang pergeserannya.
3. **Jebakan 3: Meninggalkan Komponen Debug di Layar Publik**
   - *Kenapa bahaya*: Tombol testing persona yang terpampang di layar login publik membingungkan pengguna non-teknis dan membuka jalur akses simulasi langsung dari public internet.
   - *Solusi elegan*: Akses persona pengujian dipindahkan secara elegan ke dalam top bar navigasi internal (`PersonaSwitcherDropdown`) yang hanya aktif ketika sesi pengujian berlangsung.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Unit Test Autentikasi**:
   ```bash
   ./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.auth.*"
   ```
   *Memastikan logika tab switcher, update tenant slug, demo superadmin login, dan session cleanup tetap hijau (100% lulus).*
2. **Kompilasi Multiplatform (JVM & WasmJS)**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs
   ```
   *Memastikan resource font, Canvas modifier, dan layout kompatibel dengan engine Skiko WebAssembly.*
3. **Audit Kontrak Design System**:
   ```bash
   git diff -- 'app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/auth/LoginScreen.kt' | grep '^+' | grep 'Color(0xFF'
   ```
   *Harus menghasilkan nol baris (nol literal warna baru).*

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka `LoginScreen.kt` dan coba ubah `offset` pada `ClayButton` Google Sign-In saat state `isLoading == true` agar tombol terlihat "mengendap" (pressed) selama proses autentikasi berlangsung.
- [ ] **Tantangan 2**: Buat animasi transisi sederhana saat beralih antara tab `LoginTab.GOOGLE` dan `LoginTab.WHATSAPP` menggunakan `AnimatedContent` dari Compose Animation.
