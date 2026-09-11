# 🎓 Modul Pembelajaran: Implementasi Clay Design System pada Bagan Struktur Organisasi (Org Chart)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform, Design Systems, Claymorphism + Neo-Brutalism, Custom Modifiers, State-Driven UI  
> **Prasyarat**: Dasar Jetpack Compose / Compose Multiplatform (Modifiers, Recomposition, Lambdas, State), Prinsip Tokenisasi Desain  
> **Referensi Task**: Implementasi Clay Design System ke `http://localhost:3000/org-chart`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Seringkali saat sebuah aplikasi enterprise berkembang, developer menulis style visual secara "ad-hoc":
- Menaruh literal warna langsung di composable (`Color(0xFFF8FAFC)`, `Color(0xFF2563EB)`).
- Menaruh radius sembarangan (`RoundedCornerShape(8.dp)`, `RoundedCornerShape(16.dp)`).
- Menggunakan komponen bawaan Material 3 mentah (`Card`, `Button`, `ElevatedButton`) dengan `Modifier.shadow(elevation = 4.dp)` yang bayangannya blur dan kabur.

Akibatnya:
1. **Inkonsistensi Visual**: Satu tombol punya radius 8dp, tombol lain 12dp. Ada shadow dengan blur lembut di samping shadow keras.
2. **Kerapuhan Mode Gelap/Tema**: Ketika ingin mengubah tema brand atau mendukung tema kontras tinggi, developer harus menyisir ratusan file dan mengubah kode baris per baris.
3. **Penyimpangan Bahasa Visual Brand**: WeMade ERP memiliki signature visual yang khas: **Claymorphism + Neo-Brutalism** (outline tebal 3dp, hard shadow tanpa blur bergeser 4–6dp, sudut membulat 16–24dp, font ramah dan kokoh Fredoka + Nunito). Material default merusak identitas ini.

### Analogi Sederhana
Bayangkan sebuah pabrik garmen:
- Jika setiap penjahit membeli benang, kancing, dan resleting sendiri-sendiri di toko luar tanpa standar (literal ad-hoc), kemeja yang dihasilkan akan belang-belang.
- **Design System** adalah gudang bahan baku terpusat: penjahit hanya boleh mengambil kancing standar pabrik (`ClayShapes.Button`), benang standar pabrik (`WeMadeColors.Outline`), dan label standar (`ClayTag`). Hasil jahitan seragam, rapi, dan kokoh.

### Hasil Akhir yang Diharapkan
Seluruh elemen di layar Org Chart (`OrgChartScreen`, `OrgNodeCard`, `TShapeChartView`, panel drawer arsip, serta 4 dialog interaktif) tampil seragam dengan:
- Outline tegas 3dp (`ClayBorder.Thick`) berwarna solid slate-800 (`WeMadeColors.Outline`).
- Bayangan hard offset tanpa blur (`ClayOffset.Rest` / `ClayOffset.Small`).
- Zero literal warna di luar data tenant dinamis (`colorHex`).
- Zero Material `Card` dan `Button` mentah; semuanya memakai `ClayCard`, `ClayButton`, `ClayTag`, dan `Modifier.clayFlat`/`claySurface`.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta memodifikasi sebuah layar kompleks (2000+ baris) ke design system baru dari nol, berikut urutan kerja profesionalnya:

1. **Langkah 0: Audit Baseline & Pengukuran Awal**
   - Hitung berapa banyak "utang teknis styling": grep `Color(0xFF`, `Card(`, `Button(`, `RoundedCornerShape(`.
   - Ambil screenshot visual awal menggunakan browser subagent untuk dijadikan acuan regresi.
2. **Langkah 1: Konversi Daun Hierarki Terkecil (Leaf Components)**
   - Mulai dari komponen kartu atomik: `OrgNodeCard.kt`.
   - Mengapa? Karena komponen ini di-render berulang kali di dalam pohon chart. Jika kartu node sudah mengadopsi `ClayCard`, `ClayTag`, dan avatar `clayFlat`, perubahan langsung terasa di seluruh tree.
3. **Langkah 2: Konversi Komponen Diagram Pohon (Composite View)**
   - Konversi `TShapeChartView.kt`.
   - Ganti konektor garis kanvas/box menjadi neo-brutalist bar (ketebalan 3dp solid).
   - Ubah chip rekan kerja sejajar menjadi `ClayTag`.
4. **Langkah 3: Konversi Komponen Navigasi & Drawer Samping**
   - Konversi `ArchivedPanel` dan `OrgChartHeader`.
   - Terapkan `claySurface` pada sliding drawer dan `ClayButton` pada header action bar.
5. **Langkah 4: Konversi Formulir Utama & Kontainer Panel**
   - Konversi `EmployeeFormPanel` dan `ChartPreviewPanel`.
   - Bungkus form dalam `ClayCard`, bersihkan field input dari shape ad-hoc (biarkan mewarisi `ClayMaterialShapes`), dan gunakan `clayFlat` untuk selector dan chip slider.
6. **Langkah 5: Konversi Dialog & Alert**
   - Konversi semua `AlertDialog`: `CreateDepartmentDialog`, `AddTierDialog`, `DeleteConfirmationDialog`, `EmailConflictDialog`, `EditDepartmentDialog`, `EditTierDialog`.
   - Hapus parameter `shape` manual di `AlertDialog` agar otomatis mewarisi `ClayMaterialShapes.extraLarge` (24dp) dari tema.
   - Ganti button aksi dengan `ClayButton(style = ClayButtonStyle.Primary / Ghost / Danger)`.
7. **Langkah 6: Verifikasi Multiplatform (5 Target)**
   - Jalankan kompilasi: JVM, WasmJS, JS, Android, serta unit tests.
8. **Langkah 7: Verifikasi Visual Interaktif**
   - Jalankan browser subagent: klik kartu untuk melihat perubahan state form dan highlight border, buka drawer arsip, cek responsivitas.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Penggantian Kartu Node ke `ClayCard` (`OrgNodeCard.kt`)

Sebelum:
```kotlin
Card(
    modifier = modifier
        .width(220.dp)
        .clickable { onClick() },
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 6.dp else 2.dp),
    border = BorderStroke(if (isHighlighted) 2.dp else 1.dp, if (isHighlighted) Color(0xFF2563EB) else Color(0xFFE2E8F0))
) { ... }
```

Sesudah:
```kotlin
ClayCard(
    onClick = onClick,
    shape = ClayShapes.Card,
    offset = ClayOffset.Small,
    selected = isHighlighted,
    selectedOutline = WeMadeColors.Primary,
    selectedOffset = ClayOffset.Rest,
    width = 236.dp,
    modifier = modifier
) {
    Column(modifier = Modifier.padding(ClaySpacing.Lg)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // Avatar inisial dengan flat neo-brutalist circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clayFlat(
                        shape = CircleShape,
                        background = deptColor.copy(alpha = 0.15f),
                        outline = deptColor,
                        borderWidth = ClayBorder.Medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = deptColor
                )
            }
            ...
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- **Lebar naik dari 220.dp ke 236.dp**: Neo-brutalism memakai outline 3dp dan hard shadow offset. Di Compose, outline dan padding memakan ruang fisik komponen. Menambah lebar ke 236dp memastikan teks jabatan panjang dan badge tidak wrapping secara canggung.
- **`selected = isHighlighted`**: Diatur lewat properti semantik `ClayCard`. Saat terpilih, outline berubah menjadi `WeMadeColors.Primary` dan bayangan membesar ke `ClayOffset.Rest` (6dp), memberikan feedback visual yang sangat tegas tanpa animasi blur yang berat.

---

### Blok B: Neo-Brutalist Vertical Connector Bar (`TShapeChartView.kt`)

Sebelum:
```kotlin
Box(
    modifier = Modifier
        .width(2.dp)
        .height(32.dp)
        .background(Color(0xFFCBD5E1))
)
```

Sesudah:
```kotlin
Box(
    modifier = Modifier
        .width(ClayBorder.Thick) // 3.dp token
        .height(28.dp)
        .background(WeMadeColors.Outline) // Slate-800 solid
)
```

**Mengapa blok ini ditulis begini?**
- Di Neo-Brutalism, garis konektor diagram bukan garis tipis abu-abu pucat (1dp/2dp Slate-300). Garis konektor harus memiliki ketebalan dan bobot visual yang sama dengan outline kartu (`ClayBorder.Thick` = 3dp) dengan warna outline utama (`WeMadeColors.Outline`). Ini menciptakan ilusi struktur mekanis yang solid dan menyatu.

---

### Blok C: Dialog Peringatan Integritas (`DeleteConfirmationDialog`)

Sebelum:
```kotlin
AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(16.dp),
    containerColor = WeMadeColors.Surface,
    text = {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = if (isBlocked) Color(0xFFFEF2F2) else Color(0xFFFFFBEB)),
            border = BorderStroke(1.dp, if (isBlocked) Color(0xFFFCA5A5) else Color(0xFFFDE68A))
        ) { ... }
    },
    confirmButton = {
        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
            shape = RoundedCornerShape(8.dp)
        ) { Text("Ya, Arsipkan") }
    }
)
```

Sesudah:
```kotlin
AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = WeMadeColors.Surface,
    // TIDAK ada shape = RoundedCornerShape(16.dp) eksplisit!
    // Otomatis mewarisi ClayMaterialShapes.extraLarge (24.dp)
    text = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = if (isBlocked) WeMadeColors.ErrorBg else WeMadeColors.WarningBg,
                    outline = if (isBlocked) WeMadeColors.Error else WeMadeColors.Warning
                )
                .padding(ClaySpacing.Md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                ClayTag(
                    text = if (isBlocked) "ATURAN INTEGRITAS ERP" else "POLA ODOO ARCHIVE",
                    tint = if (isBlocked) WeMadeColors.Error else WeMadeColors.Warning,
                    fontSize = 9.sp
                )
                Text(
                    text = warningNote,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = if (isBlocked) WeMadeColors.Error else WeMadeColors.Warning
                )
            }
        }
    },
    confirmButton = {
        if (!isBlocked) {
            ClayButton(
                onClick = onConfirm,
                text = "Ya, Arsipkan",
                style = ClayButtonStyle.Danger
            )
        }
    },
    dismissButton = {
        ClayButton(
            onClick = onDismiss,
            text = if (isBlocked) "Tutup / Mengerti" else "Batal",
            style = ClayButtonStyle.Ghost
        )
    }
)
```

**Mengapa blok ini ditulis begini?**
- **Menghapus override `shape` pada `AlertDialog`**: Di `WeMadeTheme`, `ClayMaterialShapes` sudah mengkonfigurasi `extraLarge = RoundedCornerShape(24.dp)`. Dengan menghapus override lokal 16dp, seluruh dialog di aplikasi memiliki sudut 24dp yang konsisten secara otomatis.
- **`clayFlat` + `ClayTag`**: Kotak peringatan aturan bisnis tampil padat dengan border 2dp berwarna sinyal (`Error` merah / `Warning` oranye), bukan garis tipis 1dp yang tenggelam.
- **`ClayButton(style = ClayButtonStyle.Danger)`**: Tombol arsipkan menggunakan tombol standar clay dengan outline 3dp dan hard shadow yang menekan ke dalam saat di-klik.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Design Token (`WeMadeColors`, `ClayShapes`, `ClayBorder`)** | Literal hardcoded `Color(0xFF...)` dan `RoundedCornerShape(8.dp)` di file presentasi | Satu sumber kebenaran (single source of truth). Perubahan skala atau tema terjadi di satu tempat. | Inkonsistensi visual, audit styling melelahkan, dan kebocoran warna tidak terduga di ribuan baris kode. |
| **`ClayCard` & `Modifier.clayFlat`** | Material `Card` + `Modifier.shadow(elevation = 4.dp)` | Menghasilkan visual Neo-Brutalisme sejati (hard shadow offset tanpa blur gaussian, 3dp outline solid). | Bayangan Material selalu blur/lembut, bertolak belakang dengan bahasa desain WeMade ERP. |
| **Pewarisan Theme Shapes (`ClayMaterialShapes`)** | Menyetel `shape = RoundedCornerShape(...)` di setiap `AlertDialog` & `TextField` | Memanfaatkan arsitektur theming Compose bawaan (pengungkit termurah). Cukup atur sekali di level root `MaterialTheme`. | Developer lupa memberi shape di dialog baru sehingga dialog baru tampil kotak/beda radius. |
| **Browser Subagent Screenshot Verification** | Hanya mengandalkan compilation test (`jvmTest`, `compileKotlinJvm`) | Bug layout (teks terpotong, z-fighting shadow, overflow) tidak bisa ditangkap oleh compiler. | Tampilan rusak di mata pengguna akhir meskipun build berstatus SUCCESSFUL. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Menyetel `ClayShapes.Panel` ke parameter `topStart: Dp`**
   - *Kenapa salah*: `ClayShapes.Panel` bertipe `RoundedCornerShape`, bukan `Dp`. Memasukkannya ke parameter `topStart = ClayShapes.Panel` menyebabkan compile error.
   - *Solusi*: Gunakan `24.dp` atau buat token radius khusus jika membutuhkan sudut asimetris pada drawer sliding.

2. **Jebakan 2: Lupa bahwa Clay Card Memakan Ruang Fisik Lebih Lebar**
   - *Kenapa bahaya*: Dengan outline 3dp di kiri-kanan (+6dp) dan shadow offset 4–6dp, lebar kartu node yang awalnya 220dp akan mempersempit ruang teks di dalamnya.
   - *Solusi*: Naikkan lebar kartu ke 236dp agar badge status dan nama panjang tidak mengalami clipping atau wrapping jelek.

3. **Jebakan 3: Menggunakan `Modifier.border()` dan `Modifier.background()` manual**
   - *Kenapa bahaya*: Urutan modifier di Compose sangat sensitif. Jika `background` dipanggil sebelum `clip`, latar akan bocor keluar sudut rounded.
   - *Solusi*: Selalu gunakan helper `Modifier.clayFlat(...)` atau `Modifier.claySurface(...)` yang sudah menjamin urutan `clip -> background -> border` secara benar.

4. **Jebakan 4: Menambahkan Ternary `if (isPresentationMode)` Baru**
   - *Kenapa bahaya*: Ini adalah utang teknis yang mematikan LocalContentColor dan membebani recomposition.
   - *Solusi*: Gunakan token warna semantik (`WeMadeColors.Surface`, `WeMadeColors.OnSurface`, dll.) yang nanti akan otomatis dialihkan oleh `darkColorScheme`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### A. Verifikasi Statis (Grep Tokens)
Pastikan nol literal warna ad-hoc yang tersisa di folder presentasi:
```bash
# Harus 0 hasil (kecuali warna tenant dinamis deptColor.hex)
grep -rn "Color(0xFF" app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart | grep -v colorHex

# Harus 0 hasil untuk raw Card
grep -rn "Card(" app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart | grep -v ClayCard | grep -v OrgNodeCard
```

### B. Verifikasi Multi-Target KMP
Pastikan kode valid di semua target kompilasi:
```bash
./gradlew :app:shared:compileKotlinJvm \
          :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs \
          :app:shared:assembleAndroidMain \
          :app:shared:jvmTest
```

### C. Verifikasi Visual Nyata
1. Buka browser di `http://localhost:3000/org-chart`.
2. Amati outline 3dp hitam solid, offset shadow tanpa blur, dan badge `ClayTag`.
3. Klik salah satu node karyawan (misal: "Rian Firmansyah"):
   - Formulir di sebelah kiri harus berubah menjadi mode edit.
   - Kartu yang dipilih harus menampilkan border tebal warna primary dan offset shadow lebih menonjol.
4. Klik tombol "Lihat Arsip":
   - Drawer terarsip harus meluncur dari kanan dengan sudut membulat 24dp dan border tebal 3dp.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1 (Mikro-Interaksi Drag & Drop)**: Jika suatu saat kita ingin mendukung drag-and-drop antar node karyawan untuk re-assign atasan langsung, bagaimana kamu memanfaatkan `ClayOffset.Pressed` dan `ClayOffset.Rest` pada `ClayCard` saat kartu sedang diangkat (dragged)?
- [ ] **Tantangan 2 (Badge Tooltip Neo-Brutalist)**: Buat komponen `ClayTooltip` yang mewarisi styling `clayFlat(shape = ClayShapes.Chip)` untuk menampilkan penjelasan aturan wewenang ketika user mengarahkan mouse ke tombol tanda tanya di form wewenang!

---

## 🔬 8. Bedah Kasus Lanjutan: Mengatasi Tofu Icon, Shadow Bocor, & Input Clay

Dalam proses iterasi dan review visual langsung di browser WebAssembly, kita menemukan 4 pertanyaan/masalah krusial yang sangat penting dipahami setiap engineer:

### A. Mengapa Emoji Berubah Menjadi Kotak Kosong ("Tofu" `[]`) di Skiko/Wasm?
- **Penyebab**: Mesin render Skia/Skiko di Compose Wasm tidak memuat font emoji bawaan OS secara otomatis. Karakter unicode seperti ⚡, ✏️, 📦, ⚠️, 🔄, ✅, ✕ akan di-render sebagai glyph tak dikenal (tofu box).
- **Solusi Benar**: **Zero Emojis untuk UI Ikonografi**. Gunakan Pure Compose `Canvas` vector icon yang dihitung berdasarkan kepadatan layar (`ClayIcons.kt`):
  ```kotlin
  @Composable
  fun IconEdit(modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
      Canvas(modifier = modifier.size(16.dp)) {
          // Draw rotated rectangle and pencil tip polygon
      }
  }
  ```
  Keuntungannya: 100% immune terhadap ketiadaan font OS, ukuran vector tajam di resolusi apa pun, dan warnanya otomatis mewarisi `LocalContentColor`.

### B. Mengapa Tombol "Edit Divisi" & "Arsipkan" Berwarna Hitam Gelap?
- **Penyebab**: Tombol tersebut awalnya menggunakan `ClayButtonStyle.Ghost` dengan background transparan. Namun modifier dasar `claySurface` tetap menggambar bayangan hitam pekat di koordinat latar belakang (`drawOutline`). Karena container-nya transparan, bayangan hitam tebal di bawahnya terlihat tembus pandang langsung ke mata pengguna.
- **Solusi**:
  1. Pada `ClayButton.kt`, bila `style == ClayButtonStyle.Ghost`, nonaktifkan shadow (`effectiveOffset = ClayOffset.Flat`, `shadowColor = Color.Transparent`).
  2. Untuk aksi penting seperti "Edit Divisi" dan "Arsipkan", gunakan gaya `ClayButtonStyle.Secondary` dengan container putih bersih, border tegas 2dp, dan hard shadow tipis 2dp sehingga kontras dan ceria.

### C. Mengapa Kartu Fokus Tidak Boleh Menggunakan Container Semi-Transparan (Bug Shadow Tembus Pandang)?
- **Penyebab Kartu Berubah Menjadi Biru Blok Solid**:
  Saat pertama kali mencoba mewarnai kartu aktif mengikuti warna divisi, kita menyetel `containerColor = deptColor.copy(alpha = 0.08f)` dan `shadowColor = deptColor`.
  Namun di balik layar, modifier `claySurface` menggambar bayangan (*silhouette outline*) dengan `style = DrawStyle.Fill` berwarna `shadowColor` (biru pekat). Karena latar kartu ber-alpha 0.08f (92% transparan), bayangan biru pekat di bawahnya tembus pandang 100% ke permukaan kartu, membuat seluruh isi kartu tenggelam menjadi balok biru pekat tanpa avatar dan divider!
- **Solusi Benar (Neo-Brutalist Active Card)**:
  Kartu `ClayCard` **WAJIB selalu memiliki latar belakang putih solid (`WeMadeColors.Surface`)**. Aksen warna aktif divisi diberikan melalui **outline (border)** yang menebal menjadi 3.5dp dan berwarna divisi (`outlineColor = if (isHighlighted) deptColor else WeMadeColors.Outline`), serta badge status `ClayTag` di atasnya.
  Dengan begitu, kartu tetap terang, bersih, avatar terlihat jelas, teks mudah dibaca, dan status aktifnya tegas terpancar dari garis border berwarna divisi.

### D. Anatomi Gaya Input Clay (`ClayTextField`): Apa Bedanya dengan Input Biasa?
Input bawaan Material (`OutlinedTextField`) dirancang untuk estetika flat modern Google: garis border tipis 1dp, label mengambang memotong garis border (floating label notch), dan tanpa bayangan fisik.
Dalam estetika Claymorphism + Neo-Brutalism, input field harus memiliki rasa taktil (tactile feel):
1. **Label Luar Mandiri**: Label berada di atas kotak input (bukan notch yang memotong border).
2. **Outline Neo-Brutal**: Garis border tebal 2.5dp (`ClayBorder.Medium` atau `Thick`) warna solid slate.
3. **Elevasi Fisik & Shadow Taktil**: Menggunakan `ClayOffset.Pressed` (2dp) saat istirahat dan membesar/berubah warna saat user memfokuskan kursor.
4. **Leading Vector Icon**: Ikon kanvas terintegrasi rapi di sisi kiri input (`IconUser`, `IconMail`, `IconPhone`) untuk memperjelas konteks field.

