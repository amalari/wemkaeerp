# 🎓 Modul Pembelajaran: Drawer Navigasi Kiri ala Google Cloud Console (Bahasa Visual Clay)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform, Design System Layering, Overlay & Hit-Testing, AnimatedVisibility, Neo-Brutalist Hard Shadow
> **Prasyarat**: Dasar Composable & `Modifier` chaining, paham `remember`/`mutableStateOf`, pernah membaca `.claude/rules/design-system-rules.md`
> **Referensi Task**: Permintaan user — "Bagan Organisasi, Hak Akses dan Alur Pabrik saya ingin buat jadi menu di Kiri, dibuat seperti Google Cloud menu-nya, buat dengan bentuk claymorph"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Sebelum task ini, tiga tujuan navigasi utama (`Bagan Organisasi`, `Hak Akses (RBAC)`, `Alur Pabrik`) duduk sebagai `FilterChip` berjejer di **pojok kanan top bar**, berbagi ruang dengan company switcher, kartu profil, dan tombol logout.

Kenapa itu bermasalah, bukan sekadar "kurang cantik":

1. **Top bar punya anggaran lebar yang tetap, tapi jumlah modul tidak.** WeMade ERP sudah punya 9 modul tenant di RBAC. Begitu navigasi modul ikut masuk ke bar yang sama, chip akan saling mendorong dan pecah di layar 1280dp.
2. **Dua kelas kontrol tercampur.** Chip navigasi (pindah halaman) berdempetan dengan kontrol identitas (siapa saya, tenant mana, keluar). Mata pengguna harus menyaring dua hal berbeda dari satu baris yang sama.
3. **Tidak ada tempat untuk tumbuh.** Tidak ada slot untuk section header, tidak ada tempat untuk ikon modul, tidak ada ruang untuk item yang terkunci.

### Analogi Sederhana

Bayangkan **lobi hotel**. Top bar itu meja resepsionis: tempat kamu menunjukkan kartu identitas, memilih cabang hotel, dan check-out. Sedangkan daftar lantai & fasilitas seharusnya ada di **papan direktori di dinding**, bukan ditempel di meja resepsionis sampai mejanya tidak muat buku tamu.

Google Cloud Console memakai model yang persis ini: header untuk identitas & project, lalu **panel produk** yang dibuka dari tombol hamburger, melayang di atas konten, dan menutup sendiri setelah kamu memilih tujuan.

### Hasil Akhir yang Diharapkan

- Tombol hamburger `☰` di ujung kiri top bar.
- Panel setinggi layar meluncur dari kiri, menimpa konten dengan lapisan peredup (*scrim*).
- Header panel: tombol `✕` + judul + subtitle (persis `✕ Google Cloud`).
- Divider, section label `MODUL PABRIK`, lalu baris menu berbentuk **pil**.
- Tombol aksi ter-*pin* di dasar panel (`Login Akun` / `Logout`).
- Panel menutup lewat tiga jalan: tombol `✕`, klik scrim, atau setelah memilih item.
- Semuanya dalam bahasa clay: outline tebal 3dp, hard shadow tanpa blur.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Ini bagian yang paling sering salah urutannya. Insting junior biasanya: *"langsung buka `App.kt`, bikin `Column` di kiri, isi tiga tombol, beres."* Hasilnya jalan, tapi melahirkan literal warna baru dan komponen yang tidak bisa dipakai ulang.

Urutan yang benar berjalan **dari lapisan paling dasar ke atas**:

### Langkah 0: Tentukan dulu — ini keputusan desain atau data domain?

Sebelum mengetik apa pun, tanyakan: warna peredup di belakang panel itu datang dari mana? Apakah tenant bisa mengubahnya (seperti `PipelineStage.colorHex`)? **Tidak.** Itu keputusan desain kita.

Artinya: dia **wajib jadi token**, bukan literal di tempat pemakaian. Kalau kamu melewatkan langkah ini, kamu akan menulis `Color(0xFF0F172A).copy(alpha = 0.45f)` di tengah komponen — dan itulah satu batu bata pertama dari 343 literal warna yang dulu harus disapu habis-habisan.

### Langkah 1: Tambahkan token di `WeMadeTheme.kt`

Satu-satunya file yang boleh memuat `Color(0xFF……)`. Namai berdasarkan **peran** (`Scrim`), bukan rupa (`DarkSlate`).

### Langkah 2: Bangun komponen di `designsystem/` — buta terhadap domain

`ClayNavDrawer` tidak boleh tahu apa itu `AppNavScreen`, apa itu `Role`, atau apa itu tenant. Dia hanya menerima `String`, `Color`, dan lambda. Kalau komponenmu butuh meng-`import` sesuatu dari `domain/` atau dari package fitur, dia **bukan** penghuni `designsystem/`.

Kenapa ini dikerjakan **sebelum** menyentuh `App.kt`? Karena begitu kamu mulai dari `App.kt`, godaan untuk memasukkan `AppNavScreen` langsung ke dalam komponen jadi sangat besar — dan komponen itu mati untuk pemakaian kedua.

### Langkah 3: Rangkai di lapisan fitur (`App.kt`)

Di sinilah `AppNavScreen` diterjemahkan menjadi `List<ClayNavItem>`, dan di sinilah aturan "item terkunci saat belum login" hidup. Lapisan fitur yang tahu domain; design system tetap netral.

### Langkah 4: Cicil utang teknis di file yang kamu sentuh

`design-system-rules.md` §8 mendaftar top bar `App.kt` sebagai utang teknis. Aturannya: **setiap kali menyentuh file di daftar itu untuk alasan apa pun, cicil bagiannya.** Karena kita sudah membongkar baris top bar, sekalian ubah `OutlinedButton` logout → `ClayActionSurface` dan kapsul profil → `Modifier.clayFlat`.

### Langkah 5: Kompilasi 5 target, lalu **jalankan dan lihat dengan mata**

Bukan satu target. WasmJS sering gagal padahal JVM lolos. Dan bug layout tidak akan tertangkap test mana pun.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Token Scrim — satu nilai, dua konsumen

```kotlin
// presentation/theme/WeMadeTheme.kt
object WeMadeColors {
    /**
     * Lapisan peredup di balik panel melayang (drawer navigasi, modal). Selalu dipakai dengan
     * `.copy(alpha = …)` di call site — pekat penuh hanya berguna untuk `colorScheme.scrim`.
     */
    val Scrim = Color(0xFF0F172A)
}

val WeMadeLightColorScheme = lightColorScheme(
    // …
    scrim = WeMadeColors.Scrim   // sebelumnya: scrim = Color(0xFF0F172A)
)
```

**Mengapa blok ini ditulis begini?**

- **Nilainya sudah ada, tapi belum punya nama.** Sebelum task ini, `0xFF0F172A` ditulis langsung di slot `scrim` milik `colorScheme`. Nilainya benar, tapi tidak bisa dipanggil dari komponen mana pun. Memberi nama = membuatnya bisa dipakai ulang.
- **Alpha ditentukan di call site, bukan di token.** Satu warna peredup bisa dipakai dengan kepekatan berbeda (drawer 45%, modal konfirmasi mungkin 60%). Menanam alpha ke dalam token akan memaksa kita membuat `Scrim45`, `Scrim60`, `Scrim80` — penamaan berbasis rupa yang persis dilarang.
- **`.copy(alpha = …)` adalah pengecualian yang sah** dari larangan literal warna. Yang dilarang adalah mengarang warna baru di tempat, bukan menurunkan varian dari token.

---

### Blok B: Struktur overlay — dan kenapa `Box` sebesar layar tidak memblokir klik

```kotlin
@Composable
fun ClayNavDrawer(open: Boolean, onDismiss: () -> Unit, /* … */) {
    Box(modifier = modifier.fillMaxSize()) {

        // 1. Lapisan peredup
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WeMadeColors.Scrim.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,     // scrim tidak boleh ber-ripple
                        onClick = onDismiss
                    )
            )
        }

        // 2. Panelnya
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.TopStart),
            enter = slideInHorizontally(tween(200)) { -it },
            exit = slideOutHorizontally(tween(180)) { -it }
        ) { /* isi panel */ }
    }
}
```

**Mengapa blok ini ditulis begini?**

- **Ini pertanyaan yang paling sering ditanyakan**: *"`Box` terluar itu `fillMaxSize()` dan dipasang di atas seluruh konten aplikasi. Kenapa tombol-tombol di bawahnya masih bisa diklik saat drawer tertutup?"*

  Jawabannya: **Compose hanya merutekan sentuhan ke node yang memasang `pointerInput`.** `Box` kosong tanpa `clickable`/`pointerInput` itu transparan total terhadap hit-testing — dia menggambar (tidak menggambar apa-apa) tapi tidak menangkap apa pun. Ini beda tajam dengan HTML, di mana `<div>` menutupi layar akan mencegat klik kecuali kamu menulis `pointer-events: none`.

- **Saat `visible = false`, `AnimatedVisibility` tidak menyisakan node sama sekali.** Jadi scrim yang `clickable` itu benar-benar hilang, bukan sekadar transparan. Kalau kamu menggantinya dengan `alpha = if (open) 0.45f else 0f`, scrim-nya tetap ada dan **seluruh aplikasimu jadi tidak bisa diklik**. Ini jebakan yang mahal karena gejalanya membingungkan: UI terlihat normal, tapi mati total.

- **`slideInHorizontally { -it }`** — lambda-nya menerima lebar elemen dan mengembalikan offset awal. `-it` artinya "mulai dari satu lebar penuh di sebelah kiri", yaitu tepat di luar layar. Ini lebih benar daripada menebak `(-300).dp`, karena kalau suatu saat `DrawerWidth` berubah, animasinya ikut menyesuaikan sendiri.

- **Scrim dan panel punya durasi berbeda** (160ms fade vs 200ms slide). Disengaja: peredup muncul sedikit lebih cepat supaya mata sudah "menerima" bahwa konteks berubah sebelum panelnya tiba.

---

### Blok C: Panel clay — `RectangleShape`, bayangan satu arah, dan inner shade yang dimatikan

```kotlin
Column(
    modifier = Modifier
        .width(DrawerWidth)          // 300.dp
        .fillMaxHeight()
        .claySurface(
            shape = RectangleShape,
            background = WeMadeColors.Surface,
            outline = WeMadeColors.Outline,
            shadowX = ClayOffset.Rest,   // 6dp ke kanan
            shadowY = 0.dp,              // tidak ada geser vertikal
            borderWidth = ClayBorder.Thick,
            innerShade = false
        )
        .padding(vertical = ClaySpacing.Xl, horizontal = ClaySpacing.Lg)
)
```

**Mengapa blok ini ditulis begini?**

- **`shadowY = 0.dp`, bukan default diagonal.** Kartu clay biasa punya bayangan diagonal (6dp kanan + 6dp bawah). Tapi panel ini setinggi layar penuh — bayangan ke bawah akan jatuh di luar viewport dan tidak pernah terlihat, sementara reservasi ruangnya tetap memakan 6dp di dasar panel. Bayangan yang benar untuk panel tepi adalah **satu arah, tegak lurus tepinya**: ke kanan, menuju konten yang ditimpanya.

- **`innerShade = false`.** Inner shade adalah gradasi hitam 10% di 12% bagian bawah kartu — sumber kesan "empuk" khas clay. Pada kartu setinggi 120dp, itu pita halus. Pada panel setinggi 900dp, 12% berarti **pita gelap setinggi 108dp** di dasar panel, yang terbaca sebagai bug rendering, bukan sebagai tekstur.

- **`RectangleShape`, bukan `ClayShapes.Panel`.** Sudut membulat 24dp pada elemen yang menempel di tepi layar menghasilkan celah aneh di pojok kiri-atas dan kiri-bawah, tempat latar belakang mengintip. GCP juga memakai persegi penuh untuk alasan yang sama.

- **Urutan modifier di dalam `claySurface` tidak boleh ditukar** — dan ini pelajaran yang paling menyakitkan kalau dilanggar:

  ```
  padding (reservasi ruang bayangan) → offset → drawBehind (bayangan)
    → clip → background → drawBehind (inner shade) → border
  ```

  **Mnemonic yang perlu kamu hafal: "`clip` itu pintu; apa pun setelahnya ada di dalam ruangan."** `clip` membuat `graphicsLayer` yang memotong semua yang datang sesudahnya. Taruh `drawBehind` bayangan setelah `clip`, dan bayanganmu terpotong habis sampai tidak ada sisa satu piksel pun — lalu kamu akan menghabiskan setengah jam mengira warnanya yang salah.

---

### Blok D: Baris menu — dibedakan oleh warna, bukan ketebalan

```kotlin
@Composable
private fun DrawerRow(item: ClayNavItem) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val highlighted = item.selected || isPressed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Pill,
                background = if (highlighted) WeMadeColors.PrimaryContainer else Color.Transparent,
                outline    = if (highlighted) WeMadeColors.Primary else Color.Transparent,
                borderWidth = ClayBorder.Medium          // ← KONSTAN
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = item.onClick)
            .padding(horizontal = ClaySpacing.Lg, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (item.selected) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
        Box(modifier = Modifier.size(20.dp)) { item.icon(tint) }
        Text(
            text = item.label,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // …
        )
    }
}
```

**Mengapa blok ini ditulis begini?**

- **`borderWidth` konstan, warnanya yang berubah.** Ini Kontrak 8 di `design-system-rules.md`. Kalau state dibedakan lewat ketebalan (`1.dp` normal → `2.dp` terpilih), baris yang terpilih **berubah ukuran** dan seluruh daftar bergeser satu piksel setiap kali kamu berpindah menu. Dengan outline transparan pada state normal, ruang yang dipesan sudah sama sejak awal — yang berganti hanya warnanya.

- **`ClayShapes.Pill` = `RoundedCornerShape(percent = 50)`.** Persentase, bukan dp. Artinya radius selalu setengah dari sisi terpendek, jadi ujung barisnya membulat sempurna berapa pun tinggi barisnya — persis pil navigasi GCP.

- **`indication = null` mematikan ripple.** Bahasa umpan balik clay adalah permukaan yang bergerak masuk ke bayangannya. Menumpuk ripple Material di atasnya membuat dua bahasa interaksi bertabrakan dalam satu sentuhan.

- **`weight(1f, fill = false)` + `maxLines` + `overflow`** pada teks — ini Kontrak 13. Di dalam `Row`, elemen yang boleh mengalah **wajib dinyatakan eksplisit**. Tanpa itu, label panjang seperti "Alur Pabrik (Pipeline)" akan mengambil hampir seluruh lebar dan menyisakan beberapa dp untuk tetangganya, yang lalu memecah teksnya **satu huruf per baris**. Bug ini tidak akan pernah tertangkap unit test — hanya mata.

---

### Blok E: Perakitan di lapisan fitur — dan satu jebakan `remember`

```kotlin
// App.kt
var drawerOpen by remember { mutableStateOf(false) }

// Memilih item menutup drawer-nya, seperti panel produk Google Cloud Console.
val openScreen: (AppNavScreen) -> Unit = { target ->
    navigateTo(target)
    drawerOpen = false
}

val navItems = remember(currentScreen, isAuthenticated) {
    listOf(
        ClayNavItem(
            key = AppNavScreen.ORG_CHART.route,
            label = AppNavScreen.ORG_CHART.title,
            selected = currentScreen == AppNavScreen.ORG_CHART,
            onClick = { openScreen(AppNavScreen.ORG_CHART) },
            icon = { tint ->
                if (!isAuthenticated) LockIcon(Modifier.fillMaxSize(), color = tint)
                else IconLayers(Modifier.fillMaxSize(), color = tint)
            }
        ),
        // … RBAC & Factory Flow
    )
}
```

**Mengapa blok ini ditulis begini?**

- **Kunci `remember(currentScreen, isAuthenticated)` bukan hiasan.** `ClayNavItem` menyimpan `selected` sebagai nilai **beku**, bukan lambda. Kalau daftarnya di-`remember` tanpa kunci, highlight-nya akan macet selamanya di menu pertama yang kamu buka. Aturan praktisnya: *setiap state yang ikut dibekukan ke dalam objek hasil `remember` harus muncul sebagai kunci.*

- **`icon` bertipe `@Composable (tint: Color) -> Unit`, bukan sebuah enum ikon.** Inilah yang menjaga `ClayNavDrawer` tetap buta domain. Keputusan "kalau belum login, tampilkan gembok" adalah aturan **fitur**, bukan aturan design system — jadi dia hidup di `App.kt`, bukan di dalam komponen.

- **`openScreen` menggabungkan dua efek dalam satu tempat.** Navigasi dan penutupan drawer selalu terjadi bersamaan; memisahkannya ke dua call site berarti cepat atau lambat ada satu item yang lupa menutup drawer-nya.

- **Soal kekhawatiran yang wajar**: *"`openScreen` dibuat ulang setiap rekomposisi, tapi dia ikut terbekukan di dalam `remember`. Bukankah itu stale closure?"* Tidak, karena `openScreen` hanya menyentuh `currentScreen` dan `drawerOpen` — keduanya properti terdelegasi dari `MutableState` yang **stabil dan di-`remember`**. Membacanya di dalam lambda selalu mengambil nilai terbaru saat lambda dipanggil, bukan nilai saat lambda dibuat.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pilihan Kita | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Drawer overlay + scrim** | Rail permanen yang mendorong konten | Diminta eksplisit (paritas GCP); konten memakai lebar penuh — penting untuk kanvas Factory Flow yang lebar & Org Chart horizontal | Rail permanen 240dp memotong kanvas pipeline; swimlane jadi terpotong di layar 1280dp |
| **`ClayNavDrawer` buatan sendiri** | `ModalNavigationDrawer` Material 3 | M3 memaksa `DrawerDefaults` — elevation ber-blur, radius Material, dan bentuk `RoundedCornerShape` bawaan yang berlawanan dengan bahasa neo-brutalist | Harus melawan default-nya di 6 tempat berbeda; hasilnya tetap bayangan blur yang tidak bisa dimatikan |
| **`claySurface` + `drawBehind`** | `Modifier.shadow(elevation)` | `shadow()` menerjemahkan elevation jadi radius blur — tidak ada cara mematikan blur-nya | Panel jadi terlihat Material, bukan clay; inkonsisten dengan seluruh aplikasi |
| **`AnimatedVisibility`** | `if (open) { … }` polos | Exit animation mustahil tanpa `AnimatedVisibility` — node sudah dilepas sebelum sempat beranimasi | Drawer "lompat" hilang, terasa murah & membingungkan |
| **Token `WeMadeColors.Scrim`** | `Color.Black.copy(alpha = .45f)` | Hitam murni terlalu keras di atas palet slate; scrim kita bernada biru-slate sehingga menyatu dengan `Outline` | Peredup terasa "berlubang"; dan literal baru = batu bata pertama utang warna berikutnya |
| **Tombol pinned `Login`/`Logout` di dasar panel** | Item nav ke-4 "Login Akun" | Sejajar dengan GCP (`View all products` ter-pin di dasar); aksi akun bukan tujuan navigasi, jadi tidak pantas sederet dengan modul | Mencampur "pindah halaman" dengan "ubah sesi" dalam satu daftar — kelas kontrol yang berbeda |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Menyembunyikan scrim dengan `alpha = 0f`, bukan melepasnya**
   - *Kenapa bahaya*: Node-nya masih ada, `clickable`-nya masih terpasang, dan **seluruh aplikasi jadi tidak bisa diklik** meski terlihat normal. Gejalanya tidak mengarah ke penyebabnya sama sekali.
   - *Solusi elegan kita*: `AnimatedVisibility(visible = open)` — saat false, tidak ada node sama sekali.

2. **Jebakan 2: Menaruh `drawBehind` bayangan setelah `clip`**
   - *Kenapa bahaya*: Bayangannya terpotong habis. Kamu akan menyangka warnanya salah, offset-nya kurang, atau `drawBehind`-nya tidak jalan — padahal urutannya yang keliru.
   - *Solusi elegan kita*: Pakai `Modifier.claySurface(…)` yang urutannya sudah benar, jangan merakit rantai sendiri. Kalau terpaksa merakit: **`clip` itu pintu.**

3. **Jebakan 3: `remember` tanpa kunci pada daftar yang memuat state beku**
   - *Kenapa bahaya*: Highlight menu macet di posisi pertama; terlihat seperti "bug navigasi" padahal navigasinya benar, yang basi cuma daftarnya.
   - *Solusi elegan kita*: `remember(currentScreen, isAuthenticated) { … }` — setiap state yang ikut dibekukan wajib jadi kunci.

4. **Jebakan 4: Membedakan state terpilih lewat ketebalan outline**
   - *Kenapa bahaya*: Baris terpilih "menggemuk" dan mendorong tetangganya; daftar bergetar setiap kali berpindah menu.
   - *Solusi elegan kita*: Ketebalan konstan (`ClayBorder.Medium`), outline transparan saat normal, berwarna saat terpilih.

5. **Jebakan 5: Menulis `Color(0xFF0F172A)` "sementara, nanti dirapikan"**
   - *Kenapa bahaya*: Tidak ada yang pernah merapikan. Package `presentation/` dulu punya 343 literal warna yang semuanya bermula dari kalimat itu.
   - *Solusi elegan kita*: Berhenti, tambahkan token di `WeMadeTheme.kt`, baru lanjut. Butuh 30 detik.

6. **Jebakan 6: Lupa `weight(1f, fill = false)` pada teks yang boleh mengalah**
   - *Kenapa bahaya*: Label panjang memakan seluruh lebar, tetangganya kebagian beberapa dp, dan teksnya pecah **satu huruf per baris** (`A-l-u-r`). Tidak ada test yang menangkapnya.
   - *Solusi elegan kita*: `weight(1f, fill = false)` + `maxLines = 1` + `overflow = Ellipsis`, lalu **jalankan dan lihat**.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Untuk kerja UI, "membuktikan" punya dua lapis, dan **keduanya wajib**.

### Lapis 1 — Kompilasi 5 target, bukan satu

```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
          :app:shared:jvmTest
```

WasmJS rutin gagal padahal JVM lolos — paling sering soal resource font dan API yang tidak tersedia di target Wasm. Kalau kamu hanya menguji JVM, kamu baru menguji 1 dari 5 platform.

### Lapis 2 — Jalankan, gerakkan, dan lihat hasilnya

Ini yang tidak bisa digantikan test apa pun. Cara kami memverifikasi task ini tanpa perlu menatap layar manual:

```bash
# 1. Segarkan bundle yang disajikan dev server
./gradlew :app:webApp:wasmJsDevelopmentExecutableCompileSync

# 2. Chrome headless dengan WebGL software (Skiko butuh WebGL; tanpa flag ini
#    Compose/Wasm gagal dengan "Cannot read properties of undefined (reading 'getParameter')")
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless=new --use-gl=angle --use-angle=swiftshader --enable-unsafe-swiftshader \
  --remote-debugging-port=9333 --window-size=1600,1000 http://localhost:3000
```

Lalu kendalikan lewat Chrome DevTools Protocol (Node 22+ sudah punya `WebSocket` global, tidak perlu Puppeteer):

```js
await send("Input.dispatchMouseEvent", { type: "mousePressed",  x: 37, y: 38, button: "left", clickCount: 1 });
await send("Input.dispatchMouseEvent", { type: "mouseReleased", x: 37, y: 38, button: "left", clickCount: 1 });
await sleep(900);
const { data } = await send("Page.captureScreenshot", { format: "png" });
```

**Skenario yang wajib dibuktikan** — dan semuanya sudah lulus pada task ini:

| Skenario | Yang dibuktikan |
|---|---|
| Drawer tertutup, klik tombol di konten | `Box` overlay tidak menelan sentuhan |
| Hamburger → panel muncul | Trigger & animasi masuk |
| Klik scrim | Dismiss lewat area peredup |
| Klik `✕` | Dismiss lewat tombol |
| Pilih item | Rute berpindah **dan** drawer menutup |
| Buka lagi | Baris yang aktif ter-highlight pil biru |
| Belum login vs sudah login | Ikon gembok ↔ ikon modul; tombol `Login Akun` ↔ `Logout` |

### Lapis 3 — Grep kepatuhan design system

```bash
git diff --unified=0 -- 'app/shared/*/presentation/*' | grep '^+' | grep 'Color(0xFF' | grep -v colorHex
git diff --unified=0 -- 'app/shared/*/presentation/*' | grep '^+' | grep -E 'RoundedCornerShape\([0-9]|BorderStroke\([0-9]'
grep -rn "import com.eventverse.app.domain" app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/designsystem/
```

Ketiganya idealnya **nol hasil**. Satu-satunya hasil yang sah pada task ini adalah `val Scrim = Color(0xFF0F172A)` — karena dia berada di `WeMadeTheme.kt`, satu-satunya rumah yang sah untuk literal warna.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1 — Tutup dengan tombol Escape.** Saat ini drawer hanya menutup lewat `✕`, scrim, atau pemilihan item. Tambahkan dukungan tombol `Esc` memakai `Modifier.onPreviewKeyEvent`. Pikirkan: kenapa harus `onPreviewKeyEvent` dan bukan `onKeyEvent`? Dan bagaimana memastikan panel punya fokus saat dibuka?

- [ ] **Tantangan 2 — Item bersarang ala GCP.** GCP punya chevron `›` pada item yang bisa dimekarkan (IAM & Admin, APIs & Services). Perluas `ClayNavItem` agar mendukung `children: List<ClayNavItem>` dan render chevron yang berputar 90° saat terbuka. Syaratnya: `ClayNavDrawer` **tetap tidak boleh** meng-`import` apa pun dari `domain/` maupun package fitur. Gunakan `animateFloatAsState` + `Modifier.rotate` untuk chevron-nya.

- [ ] **Tantangan 3 — Adaptif terhadap lebar jendela.** Pada layar ≥1600dp, konsol modern biasanya menampilkan rail permanen alih-alih drawer overlay. Rancang API-nya sehingga satu sumber `List<ClayNavItem>` yang sama bisa dirender sebagai drawer **atau** rail, tanpa menduplikasi logikanya. Petunjuk: mulai dari memisahkan "isi daftar" dari "wadahnya" — dan baca `kotlin-ui-adaptive-resources` sebelum menulis baris pertama.

- [ ] **Tantangan 4 — Bayar sisa utang top bar.** `AuthGuardCard` di `App.kt` masih memakai `Card` + `Button` Material mentah dan dua literal warna amber. Konversikan ke `ClayCard` + `ClayButton`, dan warnanya jadikan token berbasis peran (petunjuk: perannya adalah *peringatan*, bukan *amber*). Jalankan grep di Lapis 3 untuk membuktikan hasilnya bersih.

---

## 📚 Bacaan Lanjutan

- [`.claude/rules/design-system-rules.md`](../../.claude/rules/design-system-rules.md) — aturan mengikat & Definition of Done
- [`.claude/skills/compose-design-system/references/clay-recipe.md`](../../.claude/skills/compose-design-system/references/clay-recipe.md) — mekanika `claySurface` & urutan modifier
- [`docs/teaching/teaching-claymorphism-design-system.md`](teaching-claymorphism-design-system.md) — fondasi bahasa visual clay
