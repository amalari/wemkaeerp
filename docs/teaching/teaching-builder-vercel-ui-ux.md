# 🎓 Modul Pembelajaran: Redesain UI/UX WeMake Builder Konsol Bergaya Vercel Dashboard

> **Level Target**: Junior to Mid Frontend / Compose Multiplatform Developer  
> **Topik Utama**: UI/UX Design, Vercel Dashboard Aesthetics, Claymorphism + Neo-Brutalism, Compose Multiplatform Decomposition  
> **Prasyarat**: Dasar Compose Multiplatform, token design system, hirarki tata letak UI  
> **Referensi Task**: Redesain UI/UX Builder Overview & Shell

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Sebelum pembaruan ini, halaman `localhost:3001/builder` (WeMake Builder Console) terlihat sangat kosong dan kaku:
1. Sidebar hanya berupa daftar teks polos tanpa ikon, tanpa pembeda visual aktif yang jelas, dan tanpa status identitas platform.
2. Di area konten utama, hanya ada judul "Overview" dan 2 kartu kecil terisolasi di pojok kiri atas. 85% layar berupa hamparan putih kosong yang membosankan.
3. Tidak ada hierarki visual, telemetri, atau pintu masuk cepat (quick actions) untuk menjelajah kapabilitas alur pabrik.

### Analogi Sederhana: Vercel Project Dashboard
Saat developer membuka proyek di **Vercel**, yang mereka temukan bukan sekadar teks "Proyek Anda Aktif". Mereka disambut oleh:
- **Hero Deployment Showcase**: Kartu rilis utama dengan visual preview mini, domain aktif, dan status deployment real-time.
- **KPI Telemetry Grid**: Kartu metrik ringkas (kecepatan, utilisasi modul, status kesehatan alur).
- **Capabilities Hub**: Pintu cepat menuju fungsi utama (AI Architect, visual canvas stasiun, skema port, live prototype).
- **Recent Deployment Activity**: Catatan riwayat build & deploy yang rapi.

Kita mengadopsi mental model ini ke dalam bahasa visual **WeMade Claymorphism + Neo-Brutalism**: outline tegas 3dp, hard shadow tanpa blur, font ramah berbobot, dan palet brand WeMade (Trust Blue `#2563EB` & Garment Safety Orange `#EA580C`).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun ulang antarmuka dashboard kompleks seperti ini, jangan langsung mengetik semua dalam satu file:

1. **Langkah 1: Identifikasi Data & Model Tampilan (`BuilderOverviewUi`)**
   - Periksa API respons `GET /api/builder/overview`. Ekstrak tidak hanya nama tenant, tapi juga deployment aktif, domain pack, dan riwayat deployment.
2. **Langkah 2: Rancang Hero Showcase Card (`BuilderOverviewHeroCard.kt`)**
   - Buat representasi visual mini dari sistem produksi (mini canvas pipeline: Procurement → Cutting → Sewing → QC).
   - Tampilkan metadata deployment aktif secara proporsional di samping kanvas.
3. **Langkah 3: Bangun Grid Metrik & Capability Cards (`BuilderOverviewGridCards.kt`)**
   - Kelompokkan kartu 4-kolom untuk metrik telemetri utama.
   - Sediakan kartu aksi cepat interaktif yang memicu navigasi tab (`onNavigate("chat")`, `onNavigate("modules")`, dll).
4. **Langkah 4: Orkestrasi di Layar Utama (`BuilderOverviewPane.kt`)**
   - Tangani siklus data: state loading, error, dan render banner header Vercel-style.
5. **Langkah 5: Tingkatkan Sidebar Navigasi (`BuilderShell.kt`)**
   - Tambahkan ikon native vektor Canvas Skiko untuk setiap menu.
   - Berikan active pill indicator beraksen dan status profil di bagian bawah.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Hero Production Showcase (`BuilderOverviewHeroCard.kt`)
```kotlin
@Composable
internal fun BuilderProductionDeploymentHero(
    ui: BuilderOverviewUi,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(modifier = modifier.fillMaxWidth(), shape = ClayShapes.Card) {
        // Baris Header: Indikator pulsasi hijau + Rev & Deployment number
        ...
        // Dua Kolom: Kiri Kanvas Miniatur, Kanan Metadata & Tombol Aksi
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xl)) {
            Box(modifier = Modifier.weight(1.1f)) {
                // Mini browser window dengan traffic light dots & mini flow nodes
            }
            Column(modifier = Modifier.weight(0.9f)) {
                // Info domain, pack, status, dan tombol aksi
            }
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
- Memadukan mock visual dan informasi teknis dalam satu kartu menghilangkan kesan kosong pada layar desktop lebar.
- Kolom kiri memberikan bukti visual nyata bahwa alur produksi sedang berjalan (`READY · LIVE`).

### Blok B: Interkoneksi Navigasi (`onNavigate`)
```kotlin
when (selected) {
    "overview" -> BuilderOverviewPane(onNavigate = { selected = it })
    "chat" -> BuilderChatPane()
    "modules" -> BuilderModulesPane()
    ...
}
```
**Mengapa blok ini ditulis begini?**
- Memungkinkan kartu aksi di Overview langsung memindahkan tab di `BuilderShell` tanpa mengotori domain state atau URL routing global.

---

## ⚠️ 4. Jebakan Umum yang Dihindari

1. **Jebakan God File (> 600 baris)**:
   - File presentation di `app/shared/**` memiliki batas lunak 400 baris dan batas keras 600 baris (Rule §14).
   - Memecah kartu menjadi `BuilderOverviewHeroCard.kt` (240 baris) dan `BuilderOverviewGridCards.kt` (327 baris) menjaga codebase tetap bersih dan modular.
2. **Jebakan Nilai Warna Mentah (Raw Color Literal)**:
   - Semua warna wajib bersumber dari `WeMadeColors.*` (Rule §13). Tidak ada `Color(0xFF...)` ad-hoc.
3. **Jebakan Operator `!!`**:
   - Menghindari operator `overview!!` dengan safe guard `val ui = overview ?: return`.

---

## 🎯 5. Kesimpulan & Verifikasi
Buka `http://localhost:3001/builder` di browser:
- Halaman kini memiliki identitas visual modern ala Vercel yang dipadukan dengan Claymorphism.
- Seluruh metrik, diagram alur mini, kartu aksi interaktif, dan navigasi sidebar dengan ikon telah aktif dan terkompilasi sukses di target JVM dan WebAssembly (WasmJs).
