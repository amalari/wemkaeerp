# 🎓 Modul Pembelajaran: Zero-Emoji Policy & Standar Canvas Vector Icons di Compose Multiplatform (Wasm)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform, Skiko/Wasm Rendering, Font Glyphs & Tofu Glitch, Canvas Vector Icons, Design System Governance  
> **Prasyarat**: Dasar Compose Multiplatform, Canvas drawing, pemahaman teks & font font-family  
> **Referensi File**: 
> - [`.agents/rules/design-system-rules.md`](file:///Volumes/amalari/Projects/wemade/.agents/rules/design-system-rules.md)
> - [`ClayIcons.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/designsystem/ClayIcons.kt)
> - [`CrmKanbanCard.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/CrmKanbanCard.kt)
> - [`LeadActivitiesDialog.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/LeadActivitiesDialog.kt)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata (Mengapa Ikon Sering Tiba-Tiba Jadi Kotak `▯`?)
Di web dan desktop browser, developer sering tergoda mengambil "jalan pintas" ketika butuh ikon kecil:
```kotlin
// ❌ JALAN PINTAS BERBAHAYA
Text(text = "💬 Aktivitas Sales")
Text(text = "📝 Belum Ada Data")
ClayTag(text = "📱 08123456789")
ClayBadge(text = "New Lead ▾")
```
Di Android atau iOS native, sistem operasi memiliki font emoji global (`Apple Color Emoji` atau `Noto Color Emoji`) yang di-inject otomatis oleh OS. Namun, ketika aplikasi di-compile ke **WebAssembly (WasmJs)** dengan Compose Multiplatform, apa yang terjadi di balik layar?

### Mental Model & Analogi Sederhana
Bayangkan Anda sedang mencetak buku stensil. Font yang dibundel aplikasi kita (Fredoka untuk judul dan Nunito untuk teks isi) adalah satu set cetakan huruf alfabet Latin dan angka (A-Z, 0-9, tanda baca dasar). 

Ketika printer diminta mencetak karakter Unicode `💬` (U+1F4AC) atau `📝` (U+1F4DD), printer mencari cetakan stensil tersebut di laci font Nunito/Fredoka. Karena tidak ada, printer mencetak simbol darurat: **`.notdef` glyph — sebuah kotak kosong persegi panjang `▯` (dikenal di dunia tipografi sebagai *Tofu*)**.

> **Compose Multiplatform Wasm TIDAK me-render HTML DOM (`<span>` atau `<div>`)!**  
> Compose Wasm merender seluruh UI ke dalam satu elemen `<canvas>` menggunakan engine grafis **Skia (Skiko)**. Skia hanya merender apa yang ada di dalam font file `.ttf`/`.woff2` yang kita muat ke memori. Skia di canvas browser **tidak meminjam font emoji sistem operasi**.

### Hasil Akhir yang Diharapkan
Seluruh ikon antarmuka harus berupa **vektor matematis resolusi independen** yang digambar langsung oleh engine Skia (melalui `Canvas { ... }`), sehingga 100% konsisten, tidak pernah pecah, tidak tergantung font OS, dan anti-tofu di semua platform (Android, iOS, Desktop, Wasm, JS).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda ingin menambahkan atau menggunakan ikon di WeMade ERP, ikuti urutan berikut:

1. **Langkah 0: Buka Katalog Ikon Bersama (`ClayIcons.kt`)**
   - Periksa apakah ikon yang Anda butuhkan sudah ada di `app/shared/.../presentation/designsystem/ClayIcons.kt`.
   - Di sana sudah tersedia puluhan ikon: `IconSearch`, `IconEdit`, `IconUser`, `IconPhone`, `IconMail`, `IconChevronDown`, `IconChat`, `IconNote`, `IconCheckCircle`, dll.

2. **Langkah 1: Jika Belum Ada, Gambar Vektor di `ClayIcons.kt`**
   - Buat fungsi `@Composable fun IconNama(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurface)` menggunakan Skia `Canvas`.
   - Gunakan koordinat relatif persentase ukuran (`w * 0.2f`, `h * 0.5f`) agar ikon responsif diatur ukurannya via `Modifier.size(...)`.

3. **Langkah 2: Integrasikan ke Komponen UI Lewat Slot Resmi**
   - Jangan satukan ikon ke dalam string label.
   - Manfaatkan slot `leading` atau `trailing`:
     - `ClayBadge(text = "...", leading = { Icon... }, trailing = { Icon... })`
     - `ClayTag(text = "...", leading = { Icon... })`
     - `ClayButton(text = "...", leading = { Icon... })`
   - Atau susun di dalam `Row(verticalAlignment = CenterVertically) { Icon...(); Text(...) }`.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Mari kita bedah bagaimana kita memperbaiki kotak `▯` pada modal aktivitas sales dan kartu kanban:

### Blok A: Membuat Ikon Vektor Berbasis Canvas (`IconChat` & `IconNote`)

```kotlin
// ClayIcons.kt
@Composable
fun IconChat(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurface) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8f * density

        // 1. Gambar balon obrolan dengan ekor sudut kiri bawah
        val path = Path().apply {
            moveTo(w * 0.20f, h * 0.16f)
            lineTo(w * 0.80f, h * 0.16f)
            quadraticTo(w * 0.90f, h * 0.16f, w * 0.90f, h * 0.28f)
            lineTo(w * 0.90f, h * 0.60f)
            quadraticTo(w * 0.90f, h * 0.72f, w * 0.80f, h * 0.72f)
            lineTo(w * 0.48f, h * 0.72f)
            lineTo(w * 0.25f, h * 0.88f) // Ujung ekor chat
            lineTo(w * 0.28f, h * 0.72f)
            lineTo(w * 0.20f, h * 0.72f)
            quadraticTo(w * 0.10f, h * 0.72f, w * 0.10f, h * 0.60f)
            lineTo(w * 0.10f, h * 0.28f)
            quadraticTo(w * 0.10f, h * 0.16f, w * 0.20f, h * 0.16f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 2. Gambar tiga titik pesan di tengah balon
        val dotY = h * 0.44f
        val dotR = w * 0.045f
        drawCircle(color = color, radius = dotR, center = Offset(w * 0.32f, dotY))
        drawCircle(color = color, radius = dotR, center = Offset(w * 0.50f, dotY))
        drawCircle(color = color, radius = dotR, center = Offset(w * 0.68f, dotY))
    }
}
```

**Mengapa ditulis begini?**
- `density`: Memastikan ketebalan garis (`stroke`) proporsional terhadap DPI layar perangkat.
- `StrokeCap.Round` & `StrokeJoin.Round`: Menjaga ujung garis dan sudut path tetap melengkung lembut sesuai estetika Claymorphism (tanpa sudut runcing kasar).
- Ukuran relatif (`w`, `h`): Ikon otomatis menyesuaikan apakah pemanggil memberikan `Modifier.size(11.dp)` untuk tombol kecil atau `Modifier.size(32.dp)` untuk header modal.

---

### Blok B: Memasang Ikon pada Kartu Kanban (`CrmKanbanCard.kt`)

Sebelumnya (Rusak di Browser Wasm):
```kotlin
// ❌ Menggunakan emoji string
Text(text = "💬", fontSize = 11.sp)
ClayTag(text = "📱 ${whatsapp.normalizedNumber}")
ClayTag(text = "✉️ ${lead.email}")
ClayBadge(text = "${lead.stage.displayName} ▾")
```

Sesudahnya (Vektor Murni & Robust):
```kotlin
// ✅ 1. Ikon Chat di Footer Kartu
Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    IconChat(modifier = Modifier.size(11.dp), color = WeMadeColors.OnSurfaceMuted)
    Text(
        text = "${lead.activityCount} Aktivitas",
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = WeMadeColors.OnSurface
    )
}

// ✅ 2. Ikon Telepon & Email via slot `leading` pada ClayTag
ClayTag(
    text = whatsapp.normalizedNumber,
    tint = WeMadeColors.Success,
    fontSize = 9.sp,
    leading = { IconPhone(Modifier.size(10.dp), color = WeMadeColors.Success) }
)

// ✅ 3. Dropdown Caret via slot `trailing` pada ClayBadge
ClayBadge(
    text = lead.stage.displayName,
    tint = lead.stage.tint(),
    fontSize = 10.sp,
    trailing = if (canWrite) {
        { IconChevronDown(Modifier.size(9.dp), color = lead.stage.tint()) }
    } else null
)
```

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Canvas Vector Icons (`ClayIcons.kt`)** | Unicode Emojis (`💬`, `📝`, dll.) | 100% terjamin dirender oleh Skia di Web Wasm, Android, iOS, & Desktop tanpa bergantung font OS. | Tofu glitch (`▯`), warna emoji berantakan antar-OS, ukuran tidak bisa diwarnai via `tint`. |
| **Canvas Vector Icons** | Asset Image PNG/WebP | Zero network payload, resolusi vektor tajam tak terbatas di layar Retina/4K, bisa ganti warna dinamis (`tint`). | Membengkakkan ukuran bundle APK/Wasm, gambar buram saat di-zoom, sulit mengubah warna per-state. |
| **Slot `leading`/`trailing` di Komponen** | Menggabungkan String (`"Icon " + text`) | Pemisahan tanggung jawab visual (Separation of Concerns). Layout text dan layout ikon punya baseline dan padding independen. | Font-scaling OS membuat teks bergeser aneh, line-height rusak, ikon tidak bisa diwarnai berbeda dari warna teks. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan "Di Android / Mac Saya Muncul Kok!"**:
   - *Kenapa bahaya*: Di Mac lokal saat preview JVM/Desktop, Java AWT kadang bisa meminjam glyph dari font fallback sistem. Begitu di-compile ke Web Wasm dan dibuka di Chrome/Edge pengguna lain, muncul kotak `▯`.
   - *Solusi kita*: Patuhi **Aturan 11 di `AGENTS.md` & Bagian 7 di `design-system-rules.md`**: **Nol literal emoji / unicode glyph di UI string**.

2. **Jebakan Menanam Padding di dalam Path Canvas**:
   - *Kenapa bahaya*: Jika Anda menggambar dengan koordinat statis `lineTo(20f, 40f)`, ikon akan gepeng atau terpotong saat dipasangi `Modifier.size(16.dp)`.
   - *Solusi kita*: Selalu kalikan dengan `w` (`size.width`) dan `h` (`size.height`).

3. **Jebakan Lupa Mengatur `density` pada Garis**:
   - *Kenapa bahaya*: Menulis `val stroke = 2f` tanpa mengalikan `density` membuat garis terlihat tipis seperti rambut di layar HP beresolusi tinggi (xxhdpi).
   - *Solusi kita*: Selalu tulis `val stroke = 1.8f * density`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Kompilasi Multi-Target**:
   Pastikan kode tidak memanggil library platform spesifik:
   ```bash
   ./gradlew :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJvm
   ```
2. **Visual Inspection di Browser Wasm**:
   - Jalankan `./dev.sh`.
   - Buka `http://localhost:3000/crm-sales`.
   - Periksa:
     - Header dialog "Aktivitas Sales" menampilkan balon chat biru yang rapi.
     - State kosong menampilkan dokumen bergaris (bukan kotak `▯`).
     - Tag nomor WhatsApp dan Email di kartu menampilkan telepon hijau dan amplop biru mini.
     - Dropdown badge stage menampilkan segitiga panah bawah (`IconChevronDown`).

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka `ClayIcons.kt` dan buat ikon baru `IconFilter` (ikon corong filter penyaring data) yang bisa menerima `color: Color`.
- [ ] **Tantangan 2**: Pasang `IconFilter` tersebut pada tombol filter di toolbar tabel menggunakan slot `leading`.
- [ ] **Tantangan 3**: Cek apakah ada file lain di modul Anda yang masih memakai emoji teks, lalu gantikan dengan fungsi dari `ClayIcons.kt`.
