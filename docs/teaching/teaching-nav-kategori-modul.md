# 🎓 Modul Pembelajaran: Menyambung Modul Bisnis & Kategori ke Menu Navigasi

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform, Design System (Clay), RBAC-driven Navigation, Pure Function Testing, Rule of Three
> **Prasyarat**: Paham dasar Composable, `remember`, enum Kotlin, dan konsep `AccessLevel` di RBAC WeMade
> **Referensi Task**: Integrasi `BusinessModule` × `ModuleCategory` ke `ClayNavDrawer`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Sebelum task ini, drawer navigasi WeMade adalah **satu daftar panjang tanpa pengelompokan**: tiga
menu administrasi lalu sembilan modul pabrik berderet, semuanya memakai ikon yang sama
(`IconLayers`), di bawah satu label statis `"MODUL PABRIK"`.

### Masalah Nyata

Bayangkan kepala produksi membuka menu dan melihat dua belas baris identik. Untuk menemukan
"Jadwal Produksi (MRP)" dia harus **membaca** setiap baris, bukan **melihat**-nya. Ikon yang
seragam tidak memberi informasi apa pun — ia hanya mengisi ruang.

Lebih buruk lagi, pengelompokan bisnis yang **sudah ada di domain** (`BusinessModule.category`)
tidak terpakai sama sekali di UI. Data itu ada, benar, dan terawat — tapi mati di jalan.

### Analogi Sederhana

Bedanya seperti rak minimarket tanpa papan kategori versus dengan papan kategori. Barangnya sama
persis; yang berubah adalah berapa lama Anda mencari mi instan. Papan kategori bukan hiasan, ia
mengubah pencarian linear (baca satu per satu) menjadi pencarian dua tahap (cari papan → cari
barang di bawahnya).

### Hasil Akhir yang Diharapkan

```
SISTEM & STRUKTUR
  🧱 Bagan Organisasi
  🛡  Hak Akses (RBAC)
  ⚡ Alur Pabrik (Pipeline)
─────────────────────────
PENJUALAN & RELASI PELANGGAN
  🤝 Penjualan & Pelanggan     [Penuh]
  📐 Order Sampling            [Input]
─────────────────────────
GUDANG, BAHAN BAKU & LOGISTIK
  📦 Gudang & Bahan Baku       [Penuh]
  🚚 Packing & Pengiriman      [Input]
… dan seterusnya
```

Dengan tiga aturan perilaku:
1. Kategori yang **seluruh** modulnya tidak berizin → hilang total, header-nya ikut hilang.
2. Mode Audit membalik aturan itu: semua muncul, yang terkunci diberi gembok + badge.
3. Saat menyamar jadi jabatan lain, seksi `SISTEM & STRUKTUR` ikut hilang.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Ini bagian yang paling sering salah. Naluri pertama junior developer adalah **langsung membuka
`App.kt` dan menulis `when`**. Jangan. Urutannya begini:

### Langkah 0 — Inventarisasi dulu, jangan menulis apa pun

Sebelum satu baris pun diketik, jawab: **apakah data yang saya butuhkan sudah ada?**

```bash
rg -n "enum class ModuleCategory" -A 10 --glob "*.kt"
rg -n "iconKey" --glob "*.kt"
```

Hasilnya mengejutkan: `BusinessModule` **sudah punya** `category` *dan* `iconKey`. Dan mapper
`iconKey → ikon Canvas` **sudah ditulis**, tapi `private` di `ModuleCardView.kt`.

> **Mental model**: setiap kali Anda hendak menulis sebuah tabel pemetaan (`CRM_SALES → ikon
> jabat tangan`), curigai diri sendiri. Pemetaan seperti itu adalah *pengetahuan domain*, dan
> pengetahuan domain punya kecenderungan kuat untuk sudah ada di suatu tempat. Kalau Anda
> menulisnya untuk kedua kalinya, Anda baru saja membuat dua sumber kebenaran yang akan
> berselisih tiga bulan lagi.

### Langkah 1 — Angkat yang sudah ada, sebelum menambah yang baru

`ModuleIcon` dipindah dari `private` di `rbac/components/` ke publik di
`presentation/module/ModuleIcon.kt`. Ini **Aturan Tiga Kali** (Kontrak 4 design system) yang
bertindak sebagai rem: pemakaian kedua adalah peringatan, bukan izin untuk menyalin.

### Langkah 2 — Pisahkan keputusan dari rupa

Ini langkah paling penting dan paling sering dilewat. Tanya diri sendiri:

> "Kalau nanti ada bug 'kenapa menu HPP muncul untuk operator jahit', **di mana saya menulis
> test-nya?**"

Kalau jawabannya "tidak bisa, logikanya di dalam Composable", maka desainnya salah. Karena itu
lahir `NavMenu.kt`: fungsi murni tanpa satu pun tipe Compose.

### Langkah 3 — Baru sentuh design system

`ClayNavSection` ditambahkan ke `ClayNavDrawer`, dan parameter lama `sectionLabel` **dihapus**,
bukan dibiarkan hidup berdampingan.

### Langkah 4 — Terakhir, `App.kt`

Perannya menyusut jadi penerjemah: data → `ClayNavItem`. Tidak ada keputusan wewenang di sini.

### Langkah 5 — Jalankan, lihat dengan mata

Bukan opsional. Bagian §7 di bawah menunjukkan bug yang **lolos dari semua test dan semua
compiler** dan hanya ketahuan karena dilihat.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Fungsi Murni Penyusun Menu — `NavMenu.kt`

```kotlin
data class NavMenuEntry(
    val screen: AppNavScreen,
    val accessLevel: AccessLevel? = null,
    val badge: String? = null,
    val locked: Boolean = false
)

data class NavMenuSection(val title: String, val entries: List<NavMenuEntry>)

fun buildNavMenu(
    permissions: Map<BusinessModule, ModuleAccessConfig>,
    auditView: Boolean,
    isImpersonating: Boolean
): List<NavMenuSection>
```

**Mengapa blok ini ditulis begini?**

- **Tidak ada `@Composable`, tidak ada `Color`, tidak ada lambda `onClick`.** Akibatnya test-nya
  bisa dijalankan sebagai unit test biasa — tanpa Compose test runtime, tanpa emulator, dalam
  milidetik. Bandingkan dengan UI test yang butuh merender drawer, mencari node, dan sering
  flaky.
- **Parameternya tiga buah primitif keadaan**, bukan `RbacAccessPolicyRepository`. Test tidak
  perlu membangun repository, cukup kirim `Map`. Kalau fungsi ini menerima repository, test-nya
  akan butuh fake repository, dan kompleksitas itu menular.
- **`accessLevel` nullable** membedakan dua hal yang kelihatannya sama: layar administrasi yang
  *tidak dijaga RBAC sama sekali* versus modul dengan level tertentu. `null` di sini bermakna
  "pertanyaannya tidak berlaku", bukan "levelnya nol".

### Blok B: Kategori Dibaca, Bukan Didaftar

```kotlin
ModuleCategory.entries.forEach { category ->
    val entries = BusinessModule.entries
        .filter { it.category == category }
        .mapNotNull { module ->
            val screen = screensByModule[module] ?: return@mapNotNull null
            val access = permissions[module] ?: ModuleAccessConfig()
            if (!access.isAccessible && !auditView) return@mapNotNull null
            NavMenuEntry(
                screen = screen,
                accessLevel = access.level,
                badge = access.level.badgeLabel(),
                locked = !access.isAccessible
            )
        }

    if (entries.isNotEmpty()) {
        sections += NavMenuSection(title = category.displayName, entries = entries)
    }
}
```

**Mengapa blok ini ditulis begini?**

- **`BusinessModule.entries.filter { it.category == category }`**, bukan daftar literal
  `listOf(CRM_SALES, SAMPLING_ORDER)`. Ketika modul kesepuluh ditambahkan enam bulan lagi, ia
  muncul di menu **dengan sendirinya**. Daftar literal akan diam-diam menelannya, dan tidak ada
  compiler yang mengeluh — bug paling mahal adalah bug yang tidak bersuara.
- **`category.displayName`**, bukan string yang diketik ulang. Saat menyusun rencana, saya
  sempat menulis `"Gudang & Logistik"` padahal nilai aslinya `"Gudang, Bahan Baku & Logistik"`.
  Menyalin string selalu berakhir begini.
- **`if (entries.isNotEmpty())`** — ini yang membuat header tidak menggantung. Header tanpa isi
  lebih buruk daripada tidak ada header: ia menjanjikan sesuatu lalu tidak menepatinya.
- **`?: ModuleAccessConfig()`** — default-nya `AccessLevel.NONE`. Fail-closed: modul yang tidak
  tercantum di peta izin dianggap **tertutup**, bukan terbuka. Ini keputusan keamanan, bukan
  gaya penulisan.

### Blok C: Section Header di Design System

```kotlin
@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        modifier = Modifier.padding(start = ClaySpacing.Lg, bottom = ClaySpacing.Md),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = ClayLetterSpacing.Label,
        color = WeMadeColors.OnSurfaceMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
```

**Mengapa blok ini ditulis begini?**

- **`style = MaterialTheme.typography.labelSmall`**, bukan `fontSize = 10.sp`. Peran tipografi
  ikut ganti otomatis kalau font sistem diubah; angka telanjang tidak.
- **`color` diberikan di call site, bukan ditanam ke `TextStyle`** (Kontrak 9). `color` di dalam
  `TextStyle` selalu mengalahkan `LocalContentColor`, sehingga seluruh mekanisme pewarnaan
  kontekstual Compose mati diam-diam.
- **`ClayLetterSpacing.Label` token baru**, bukan `0.8.sp` di tempat. Kapital pendek butuh
  renggang; teks isi tidak. Memberinya nama peran mencegah orang memakainya di paragraf.
- **`maxLines = 2` + `overflow`** — `"GUDANG, BAHAN BAKU & LOGISTIK"` panjang, dan drawer hanya
  300dp.

### Blok D: Divider Hanya di Antara, Bukan di Awal

```kotlin
sections.forEachIndexed { index, section ->
    if (index > 0) {
        Spacer(modifier = Modifier.height(ClaySpacing.Lg))
        DrawerDivider()
        Spacer(modifier = Modifier.height(ClaySpacing.Lg))
    }
    DrawerSectionHeader(title = section.title)
    section.items.forEach { item ->
        DrawerRow(item = item)
        Spacer(modifier = Modifier.height(ClaySpacing.Xs))
    }
}
```

**Mengapa blok ini ditulis begini?**

- **`if (index > 0)`** bukan detail estetika. Karena seksi bisa tersaring habis, "seksi pertama"
  berubah-ubah tergantung persona. Kalau divider ditulis *sesudah* tiap seksi, persona yang
  seksi terakhirnya kosong akan memunculkan **garis menggantung di dasar drawer** — cacat yang
  hanya muncul untuk sebagian pengguna, dan karenanya paling lama tidak terdeteksi.

### Blok E: `App.kt` Menyusut Jadi Penerjemah

```kotlin
val menuSections = remember(effectivePermissions, auditView, isImpersonating) {
    buildNavMenu(effectivePermissions, auditView, isImpersonating)
}

val navSections = menuSections.map { section ->
    ClayNavSection(
        title = section.title,
        items = section.entries.map { entry ->
            val module = entry.screen.businessModule
            ClayNavItem(
                …
                icon = { tint ->
                    when {
                        entry.locked || !isAuthenticated ->
                            IconLock(modifier = Modifier.fillMaxSize(), color = tint)
                        module != null ->
                            ModuleIcon(module.iconKey, Modifier.fillMaxSize(), tint)
                        else -> AdminScreenIcon(entry.screen, tint)
                    }
                },
                badgeTint = entry.accessLevel?.tint() ?: WeMadeColors.OnSurfaceDisabled,
                enabled = !entry.locked
            )
        }
    )
}
```

**Mengapa blok ini ditulis begini?**

- **`remember(...)` hanya membungkus `buildNavMenu`, bukan seluruh pemetaan.** `navSections`
  sengaja **tidak** di-`remember`: isinya menangkap `currentScreen` dan lambda `openScreen`, dan
  me-`remember` sesuatu yang menangkap state yang berubah adalah cara klasik menghasilkan menu
  yang sorotan "terpilih"-nya nyangkut di layar lama.
- **`entry.locked || !isAuthenticated`** — dua alasan berbeda untuk gembok yang sama: tidak
  berwenang, dan belum login. Sengaja disatukan di lapisan rupa, sementara di lapisan keputusan
  keduanya tetap terpisah.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang dipilih | Alternatif yang ada | Mengapa kita memilih ini | Risiko alternatif |
|---|---|---|---|
| Fungsi murni `buildNavMenu` + data class | Logika langsung di `remember { }` dalam Composable | Bisa diuji sebagai unit test biasa, 8 test jalan dalam milidetik | Logika wewenang jadi tidak teruji; regresinya baru ketahuan di produksi oleh pengguna |
| `groupBy` dari `BusinessModule.category` | Daftar literal per kategori di `App.kt` | Modul baru otomatis masuk menu | Modul baru hilang diam-diam, tanpa error compiler |
| `module.iconKey` (data domain) | `when (screen) { CRM_SALES -> IconHandshake … }` di UI | Satu sumber kebenaran; tenant bisa mengubahnya | Dua tabel pemetaan yang akan berselisih |
| Hapus `sectionLabel` | Simpan sebagai overload backwards-compatible | Hanya ada satu call site; dua mekanisme header = utang baru | Developer berikutnya bingung yang mana yang "benar" |
| `ClayNavSection(title: String)` | `ClayNavSection(category: ModuleCategory)` | Design system buta domain (Kontrak 6) | `designsystem/` jadi bergantung ke `domain/`, arah dependensi terbalik |

### Catatan khusus: kenapa `data class` polos, bukan `sealed interface`?

Godaannya besar: `sealed interface NavEntry { data class Module(…); data class Admin(…) }`. Tapi
perbedaan keduanya hanyalah **`accessLevel` ada atau tidak**. Sealed hierarchy untuk satu field
nullable menghasilkan `when` bercabang di setiap pemakaian tanpa menambah keamanan tipe yang
berarti. Gunakan sealed interface ketika cabangnya punya **perilaku** berbeda, bukan sekadar
bentuk data berbeda.

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

### Jebakan 1: Menyalin `when` yang sudah ada karena "cuma sepuluh baris"

- *Kenapa bahaya*: Sebelum design system ada, repo ini punya **343 literal warna** dan **3
  implementasi badge** — semuanya lahir dari keputusan "cuma sepuluh baris" yang diulang.
  Ongkosnya tidak terasa saat menyalin, tapi terasa saat mengubah.
- *Solusi elegan kita*: Aturan Tiga Kali. Pemakaian kedua adalah **alarm**, bukan izin.

### Jebakan 2: Menaruh logika penyaringan di dalam Composable

- *Kenapa bahaya*: Logika itu jadi tidak bisa diuji tanpa merender. Ujungnya tidak ada yang
  menulis test-nya, dan aturan wewenang — bagian paling sensitif dari seluruh aplikasi — berjalan
  tanpa jaring pengaman.
- *Solusi elegan kita*: Fungsi murni terpisah; Composable hanya menerjemahkan hasilnya.

### Jebakan 3: Menulis nama kategori dengan tangan

- *Kenapa bahaya*: Terdengar sepele sampai Anda sadar `displayName` yang asli adalah `"Gudang,
  Bahan Baku & Logistik"` sementara yang Anda ketik `"Gudang & Logistik"`. Sekarang ada dua nama
  untuk satu hal, dan yang muncul di layar bukan yang dipakai di tempat lain.
- *Solusi elegan kita*: `category.displayName`, selalu.

### Jebakan 4: Menyembunyikan menu tanpa menyediakan jalan pulang

- *Kenapa bahaya*: Saat menyamar sebagai operator, seksi `SISTEM & STRUKTUR` hilang — termasuk
  layar RBAC. Kalau tombol "kembali ke diri sendiri" juga ada di menu, penguji **terkunci di
  dalam penyamarannya**.
- *Solusi elegan kita*: Persona switcher hidup di **top bar**, bukan di drawer. Menyembunyikan
  menu aman justru karena jalan keluarnya tidak lewat menu.

### Jebakan 5: Menganggap "kompilasi hijau" sama dengan "selesai"

- *Kenapa bahaya*: Lihat §7.
- *Solusi elegan kita*: Jalankan aplikasinya. Lihat dengan mata.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Delapan test di
[`NavMenuTest.kt`](../../app/shared/src/commonTest/kotlin/com/eventverse/app/presentation/navigation/NavMenuTest.kt),
semuanya tanpa Compose.

```kotlin
@Test
fun build_menu_when_category_has_no_accessible_module_should_hide_the_section() {
    val sections = buildNavMenu(
        permissions = grant(BusinessModule.INVENTORY to AccessLevel.VIEW),
        auditView = false,
        isImpersonating = false
    )

    val titles = sections.map { it.title }
    assertTrue(ModuleCategory.LOGISTICS.displayName in titles)
    assertFalse(ModuleCategory.SALES.displayName in titles)
    assertFalse(ModuleCategory.TECHNICAL.displayName in titles)
}
```

Yang membuat test ini bermakna: ia mengassert **yang tidak ada**, bukan hanya yang ada. Test yang
cuma memeriksa "menu gudang muncul" akan tetap hijau walau seluruh dua belas menu muncul.

Satu test khusus yang layak ditiru di tempat lain:

```kotlin
@Test
fun build_menu_should_cover_every_module_backed_screen_when_audit_view_enabled() {
    val listed = buildNavMenu(emptyMap(), auditView = true, isImpersonating = false)
        .filter { it.title != SYSTEM_SECTION_TITLE }
        .flatMap { it.entries }.map { it.screen }.toSet()

    val expected = AppNavScreen.entries.filter { it.businessModule != null }.toSet()
    assertEquals(expected, listed)
}
```

Ini **test kelengkapan**. Ia gagal kalau seseorang menambah modul baru tapi lupa memberinya
kategori yang benar. Test semacam ini murah ditulis dan mahal harganya — ia menangkap kelas bug
"lupa mendaftarkan" yang tidak akan pernah ditangkap test per-kasus.

Verifikasi lengkapnya:

```bash
./gradlew :app:shared:jvmTest :app:shared:testAndroidHostTest
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain
```

> ⚠️ **Catatan soal nama task**: modul ini memakai plugin **Android KMP**
> (`androidMultiplatformLibrary`), yang menamai task-nya berbeda dari plugin Android biasa.
> `testDebugUnitTest` **tidak ada**, dan `androidHostTest` juga bukan nama task (itu nama
> *source set*-nya). Yang benar: **`jvmTest`** dan **`testAndroidHostTest`**. Kalau ragu:
> `./gradlew :app:shared:tasks --all | grep -i test`.

---

## 👁 7. Bug yang Hanya Bisa Ditangkap dengan Mata

Sekalian menyentuh `App.kt`, `AuthGuardCard` dicicil dari daftar utang §8: `Card`/`Button`
Material mentah diganti `ClayCard`/`ClayButton`, dan dua literal amber diganti
`WeMadeColors.Warning`.

Kompilasi hijau di lima target. Semua test lolos. Lalu layarnya dibuka:

> **Tombolnya bertuliskan `Masuk ke Akun Sekarang ␣`** — panahnya jadi kotak tofu.

Penyebabnya: teks tombol mengandung `"→"` (U+2192). Selama tombolnya `Button` Material, ia
memakai Roboto bawaan sistem yang punya glyph itu. Begitu berpindah ke `ClayButton`, ia memakai
**Fredoka/Nunito yang kita bundel sendiri** — dan subset font yang kita bundel tidak memuat panah.

**Pelajaran yang bisa dibawa pulang**: mengganti komponen berarti mengganti font, dan mengganti
font berarti **mengganti himpunan glyph yang tersedia**. Karakter non-ASCII di teks UI adalah
titik rapuh yang tidak dijaga compiler maupun test. Solusi yang dipakai: buang panahnya.

Pemeriksaan lanjutan menemukan glyph serupa masih hidup di beberapa layar lain yang **belum**
dikonversi ke clay (`"← berlaku"`, `"✓"`, `"✕ Tutup Presentasi"`, `"→ Melapor ke Direksi"`).
Semuanya aman **selama** layar itu belum memakai komponen clay — jadi ini bukan bug hari ini,
melainkan **ranjau untuk konversi berikutnya**. Catat, jangan lupakan.

---

## 🏆 8. Tantangan Mandiri

- [ ] **Tantangan 1 — Urutan yang berarti.** Sekarang urutan kategori mengikuti deklarasi
      `ModuleCategory.entries`. Ubah agar kategori tempat pengguna paling sering bekerja naik ke
      atas, tanpa mengorbankan keterprediksian (menu yang berpindah-pindah membuat orang gagal
      membangun memori otot). Petunjuk: mungkin yang naik cukup **satu** seksi, bukan diurut ulang
      semuanya.

- [ ] **Tantangan 2 — Ikon untuk layar administrasi.** `AdminScreenIcon` masih berupa `when` atas
      `AppNavScreen`. Beri `AppNavScreen` sebuah `iconKey` seperti yang sudah dipunyai
      `BusinessModule`, lalu hapus `when` itu. Pertanyaan yang harus Anda jawab dulu: apakah
      `iconKey` milik `AppNavScreen` (rute) atau milik hal yang ditunjuknya?

- [ ] **Tantangan 3 — Jaring pengaman glyph.** Tulis test yang gagal kalau ada teks UI di
      `presentation/**` mengandung karakter di luar himpunan glyph font yang dibundel. Ini akan
      menangkap bug §7 secara otomatis. Petunjuk: mulai dari daftar-hitam kecil
      (`→ ← ✓ ✕ ⚠`) sebelum mencoba membaca tabel glyph font yang sesungguhnya — jaring yang
      kasar tapi ada jauh lebih berguna daripada jaring sempurna yang tidak pernah selesai.

---

## 📁 Berkas yang Disentuh

| Berkas | Perubahan |
|---|---|
| `presentation/navigation/NavMenu.kt` | **Baru** — fungsi murni `buildNavMenu` + model data |
| `presentation/module/ModuleIcon.kt` | **Baru** — mapper `iconKey` → ikon, diangkat dari `private` |
| `commonTest/.../navigation/NavMenuTest.kt` | **Baru** — 8 test |
| `presentation/designsystem/ClayNavDrawer.kt` | `ClayNavSection` + section header; `sectionLabel` dihapus |
| `presentation/designsystem/ClayIcons.kt` | `IconLock` masuk katalog |
| `presentation/designsystem/ClayTokens.kt` | Token `ClayLetterSpacing.Label` |
| `presentation/rbac/components/ModuleCardView.kt` | Duplikat mapper dihapus |
| `App.kt` | Menyusut jadi penerjemah; `LockIcon` privat dihapus; `AuthGuardCard` dicicil ke clay |
