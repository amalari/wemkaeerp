# Teaching: Membuat Stasiun QC Benar-Benar Mobile-First

> Dokumentasi mentoring untuk perubahan layout responsif di `presentation/qc/`.
> Konteks: layar `/quality-control` rusak saat dibuka di lebar telepon (390dp).

---

## Step 0 — Mulai dari Mana: Lihat Dulu, Baru Baca Kode

Godaan pertama saat mendengar "tampilan mobile rusak" adalah langsung membuka file layar dan
menebak. Jangan. Buka aplikasinya di lebar telepon dan **lihat**, karena gejala visual
menunjukkan file mana yang perlu dibaca — dan bug layout Compose hampir tidak pernah terlihat
dari membaca kode saja.

```bash
# dev server sudah jalan lewat :app:webApp:wasmJsBrowserDevelopmentRun --continuous
# resize ke 390x844 (iPhone), lalu screenshot
```

Yang terlihat di screenshot awal ada empat hal, dan keempatnya penyakit yang berbeda:

| Gejala | Dugaan penyebab |
|---|---|
| Judul terpotong `KONTROL KUALITAS (QU…` | teks terlalu panjang dipaksa sebaris dengan badge |
| Antrean terpotong di tengah kartu ke-2 | tinggi antrean dipatok `heightIn(max = 280.dp)` |
| **Kotak abu-abu kosong** di lembar ukur | inilah yang paling mencurigakan |
| Bilah submit berdesakan di sisa layar | dua panel berbagi 844dp tinggi |

Kotak abu-abu kosong itu kunci. Kotak kosong di Compose hampir selalu berarti sebuah child
mendapat **lebar nol** — bukan berarti datanya kosong.

---

## Step 1 — Menemukan Akar: Aritmetika Lebar, Bukan Selera Desain

Buka `QcInspectionSections.kt`, `QcPomInputRow`. Susunannya satu `Row` berisi empat anak:

```kotlin
Column(modifier = Modifier.width(POM_LABEL_WIDTH))   // 230dp — TETAP
ClayTextField(modifier = Modifier.width(96.dp))      //  96dp — TETAP
ClayTextField(modifier = Modifier.weight(1f))        // sisa  — kolom catatan
QcDeviationBadge(...)                                // ~90dp — intrinsik
```

Sekarang hitung di lebar telepon:

```
390dp  lebar layar
-32dp  padding layar (ClaySpacing.Xl × 2)
-24dp  padding dalam blok (ClaySpacing.Lg × 2)
-24dp  tiga jarak antar anak (ClaySpacing.Md × 3)
──────
310dp  tersedia untuk empat anak

310 - 230 (label) - 96 (cm) - 90 (badge) = -106dp
```

**Sisanya negatif.** `weight(1f)` membagi sisa ruang; kalau sisanya nol atau kurang, ia
mendapat nol. Itulah kotak abu-abu kosong di screenshot: kolom catatan yang secara harfiah
tidak punya lebar.

### Mental model yang perlu dibawa pulang

> `Modifier.weight()` bukan "ambil ruang yang pantas", melainkan "bagi **sisa** ruang setelah
> semua anak berukuran tetap dilayani". Setiap `.width(N.dp)` di dalam `Row` adalah utang yang
> ditagih lebih dulu; `weight` adalah kreditor terakhir dalam antrean.

Ini juga alasan kenapa Kontrak 13 di
[`design-system-rules.md`](../../.claude/rules/design-system-rules.md) mewajibkan
`weight(1f, fill = false)` + `maxLines` + `overflow` pada elemen yang boleh mengalah: masalahnya
selalu tentang **siapa yang mengalah lebih dulu**.

### Kenapa lebar tetap 230dp itu tetap benar (di desktop)

Jangan buru-buru menghapusnya. Komentar di kodenya sudah menjelaskan alasannya, dan alasannya
valid: sembilan sampai sebelas kotak angka yang diisi berurutan jauh lebih cepat dibaca kalau
semuanya sejajar di satu garis vertikal. Label sepanjang apa pun tidak boleh menggeser kolom cm.

Jadi ini **bukan** kasus "kode lama salah". Ini kasus satu susunan yang benar untuk satu lebar
dan mustahil untuk lebar lain. Solusinya dua susunan, bukan satu susunan kompromi yang
buruk di kedua lebar.

---

## Step 2 — Keputusan Arsitektur: Dua Panel Sempit vs Satu Panel Penuh

Kode lama sudah punya cabang compact:

```kotlin
if (isCompact) {
    Column {
        queuePane(Modifier.fillMaxWidth().heightIn(max = 280.dp))  // antrean jadi pita
        inspectionPane(Modifier.fillMaxWidth().weight(1f))
    }
}
```

Niatnya baik — tapi hasilnya dua panel yang sama-sama tidak cukup: antrean 280dp memotong kartu
kedua di tengah (terbaca sebagai render rusak, bukan sebagai "gulir untuk lanjut"), dan lembar
ukur berisi 11 titik ukur hanya kebagian ~400dp sisa tinggi.

### Pertanyaan yang benar bukan "bagaimana memuatnya", tapi "siapa yang memakai ini"

QC adalah satu-satunya modul yang dipakai **sambil berdiri di meja ukur dengan telepon di satu
tangan dan meteran di tangan lain**. Alurnya berurutan, bukan paralel:

1. pilih SPK (sekali, di awal)
2. ukur 11 titik (menit-menit berikutnya)
3. submit, lanjut pcs berikutnya

Antrean hanya berguna di langkah 1. Menahannya tetap terlihat selama langkah 2 adalah memberi
280dp tinggi layar kepada informasi yang tidak dipakai. Karena itu pilihannya:

> **Satu panel penuh pada satu waktu**, dengan bilah kembali — bukan dua panel yang dikecilkan.

```kotlin
var showDetailOnCompact by remember { mutableStateOf(false) }
val isViewingDetail = isCompact && showDetailOnCompact && selectedItem != null
```

### Kenapa bukan `ModalBottomSheet` seperti CRM?

CRM Leads memakai `LeadsMobileFeedLayout` + `ModalBottomSheet`, dan itu tepat **di sana**:
inspektor lead membuka satu kartu, membaca, menutup. Interaksinya pendek.

Di QC interaksinya panjang dan penuh input teks. Bottom sheet + keyboard virtual + bilah submit
yang menempel di bawah adalah tiga hal yang berebut tinggi layar yang sama, dan di Compose
Multiplatform perilaku `ModalBottomSheet` terhadap IME belum seragam di lima target. Full-screen
swap tidak punya masalah itu sama sekali.

**Pelajaran umum**: pola mobile yang dipakai satu modul tidak otomatis benar untuk modul lain.
Yang harus konsisten adalah *token dan komponennya*, bukan *pilihan navigasinya*.

---

## Step 3 — Membedah Perubahan, Blok per Blok

### 3a. `QcPomInputRow` → dua susunan

Yang dilakukan bukan mengganti susunan lama, melainkan menambah cabang dan **berbagi
perhitungan**:

```kotlin
val blockModifier = Modifier
    .fillMaxWidth()
    .clayFlat(shape = ClayShapes.Tile, background = …, outline = outline)
    .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md)

if (isCompact) {
    QcPomInputBlockCompact(modifier = blockModifier, …)
    return
}
Row(modifier = blockModifier, …) { … }
```

Perhatikan apa yang **tidak** diduplikasi: `outline`, `deviation`, `isWithinTolerance`,
`hasNote` semua dihitung sekali di atas percabangan, lalu diteruskan. Kalau logika toleransi
disalin ke dalam kedua cabang, cepat atau lambat satu cabang akan memakai ±1.0 cm dan satunya
±1.5 cm, dan tidak ada yang menyadarinya sampai ada klaim dari buyer.

> **Aturan praktis**: saat memecah satu komponen jadi dua susunan, garis pisahnya ditarik
> **setelah** semua turunan state dihitung. Percabangan boleh menyentuh tata letak, tidak boleh
> menyentuh arti.

Susunan compact-nya dua baris — **baris keterangan di atas, baris isian di bawah**:

```
[ Panjang Baju (Body Length)      ] [ Sesuai +0.0 cm ]   ← yang DIBACA
  Target 60.0 cm • ±1.0 cm
[  cm  ] [ Catatan temuan ....................... ]      ← yang DIISI
```

Pemisahan ini bukan sekadar merapatkan baris. Memisahkan "yang dibaca" dari "yang diisi"
memberi jempol **satu garis mendatar** berisi seluruh kotak yang perlu disentuh, dan memberi
mata satu garis di atasnya berisi seluruh yang perlu dibaca — alih-alih keduanya berselang-seling
naik-turun. Efek sampingnya tiap blok jadi lebih pendek, dan tiga titik ukur muat dalam satu
layar alih-alih dua.

Badge status naik ke **kanan atas, sebaris dengan nama titiknya**, karena badge itu
*menerangkan* titik ini — ia bukan langkah tersendiri yang pantas menyela antara nama dan kotak
isiannya.

Kotak cm tetap `POM_ACTUAL_WIDTH` (96dp) yang sama dengan desktop, jadi kolom angkanya **masih
sejajar dari atas ke bawah** — properti yang jadi alasan lebar tetap itu ada sejak awal tetap
terjaga. Konstanta 96dp yang tadinya literal di satu tempat sekarang jadi `private val` yang
dipakai kedua cabang, supaya keduanya tidak bisa melenceng.

Placeholder catatan juga dipendekkan dari `"Catatan titik ini — kosongkan bila tidak ada
temuan"` menjadi `"Catatan temuan"`. Placeholder yang terpotong tidak menjelaskan apa pun; ia
hanya terlihat seperti teks rusak.

### 3b. `ClayFlowRow` — mengangkat pola, bukan menyalinnya

Tiga tempat butuh baris badge yang membungkus. `Row` biasa **tidak membungkus** — ia memotong
anak terakhir di luar layar tanpa peringatan apa pun (tidak ada error, tidak ada warning; badge
itu sekadar tidak ada).

Sesuai [Aturan Tiga Kali](../../.claude/rules/design-system-rules.md#kontrak-4--aturan-tiga-kali-rule-of-three),
pemakaian ketiga memicu kewajiban mengangkatnya:

```kotlin
// designsystem/ClayFlowRow.kt
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClayFlowRow(
    modifier: Modifier = Modifier,
    spacing: Dp = ClaySpacing.Sm,
    content: @Composable FlowRowScope.() -> Unit
) { FlowRow(modifier, Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(spacing), content = content) }
```

Manfaat kedua yang sering terlewat: anotasi opt-in `ExperimentalLayoutApi` sekarang terkurung di
**satu** berkas. Saat API itu naik jadi stabil, yang perlu diubah satu baris, bukan sepuluh.

> Komponen design system **buta terhadap domain** (Kontrak 6): `ClayFlowRow` menerima `Dp` dan
> lambda, tidak tahu apa-apa soal QC.

### 3c. Header diekstrak ke berkasnya sendiri

`QcWorkspaceHeader` pindah dari `QcInspectorWorkspaceScreen.kt` ke
`components/QcWorkspaceHeader.kt`. Alasannya bukan jumlah baris, melainkan **tanggung jawab**
(lihat [`file-size-rules.md` §4](../../.claude/rules/file-size-rules.md)): begitu header punya
dua susunan sendiri, berkas layar berhenti "hanya merakit" dan mulai ikut merender. Itu garis
pisah yang jujur.

Di dalamnya, dua penyesuaian mobile yang layak dicatat:

```kotlin
val title = if (isCompact) "KONTROL KUALITAS" else "KONTROL KUALITAS (QUALITY CONTROL)"
```

Judul **dipendekkan**, bukan dielipsis. `KONTROL KUALITAS (QU…` terbaca sebagai teks rusak;
`KONTROL KUALITAS` terbaca sebagai judul. Ellipsis adalah jaring pengaman untuk data yang tidak
kita kendalikan (nama klien, nama style) — bukan untuk teks statis yang kita tulis sendiri.

```kotlin
ClayButton(
    modifier = Modifier.weight(1f),
    contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Lg),
    …
)
```

Tab meja (`QC Rajut` / `QC Finishing`) dibuat masing-masing separuh lebar dengan padding
vertikal lebih tebal. Ini bukan estetika: target sentuh di lantai produksi ditekan dengan ibu
jari, sering bersarung tangan katun.

### 3d. Bilah submit

Masalahnya sama persis dengan Step 1, satu tingkat di atasnya: kalimat status
`weight(1f, fill = false)` bersebelahan dengan badge yang tidak boleh menyusut. Di telepon
kalimat itu tersisa beberapa dp dan pecah per suku kata — padahal justru kalimat inilah yang
menjelaskan **kenapa tombol submit masih mati** ("Terisi 0 dari 11 titik ukur").

Solusinya bertumpuk di compact. Yang penting dari cara ini: `statusText`, `statusColor`, dan
`statusBadges` diangkat jadi `val` di atas percabangan, jadi kedua susunan menampilkan kalimat
yang identik. Sekali lagi — **percabangan menyentuh tata letak, tidak menyentuh arti**.

### 3e. Bilah kembali

```kotlin
// Canvas ikon tidak punya ukuran intrinsik: tanpa .size() ia menciut jadi titik.
leading = { IconArrowBack(Modifier.size(16.dp), color = WeMadeColors.OnSurface) }
```

Jebakan yang sempat kena di iterasi pertama: `IconArrowBack` adalah `Canvas(modifier)` yang
menggambar relatif terhadap `size.width`/`size.height`. `Canvas` tanpa ukuran eksplisit di dalam
`Row` akan menyusut sampai nyaris nol, dan panah itu ter-render sebagai **titik kecil**. Dua
call site lain di `invoicing/` sudah memakai `.size(13.dp)`; di sini dipakai 16dp karena ini
target sentuh, bukan hiasan toolbar.

> Ini gejala yang sama dengan kotak abu-abu di Step 1 — anak dengan lebar nol. Biasakan
> mengenalinya: **kalau sesuatu "hilang" atau "menciut" di Compose, curigai ukuran sebelum
> mencurigai data.**

---

## Step 4 — Yang Ikut Rapi Tanpa Diminta

Beberapa hal yang terjadi sebagai akibat, bukan sebagai pekerjaan tambahan:

- **Padding layar** `ClaySpacing.Xl` (16dp × 2 = 32dp) jadi `ClaySpacing.Md` di compact. 32dp
  dari 390dp itu 8% lebar layar yang diberikan ke ruang kosong; di 1440dp itu 2%.
- **Sorotan kartu antrean dimatikan di compact** (`selectedOrderId = if (isCompact) null else …`).
  Di desktop sorotan itu mencerminkan isi panel kanan. Di telepon tidak ada panel kanan yang
  dicerminkan, jadi sorotan hanya bikin bingung — kartu tampak "aktif" padahal layarnya sudah
  pindah.
- **Ganti meja mereset detail** (`showDetailOnCompact = false`). Tanpa ini, menekan
  `QC Finishing` saat lembar rajut terbuka akan menampilkan lembar kosong tanpa penjelasan.
- **Kembali otomatis setelah pcs terakhir**: `isViewingDetail` bergantung pada
  `selectedItem != null`. Saat SPK selesai dan keluar dari antrean, layar kembali ke antrean
  dengan sendirinya — tanpa satu baris kode pun yang khusus menangani itu. Ini keuntungan
  menurunkan state dari data, bukan menyimpannya terpisah.

---

## Step 5 — Verifikasi

Sesuai Definition of Done di
[`design-system-rules.md` §7](../../.claude/rules/design-system-rules.md):

```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:jvmTest
```

Dan — yang tidak bisa digantikan test apa pun — **dijalankan dan dilihat dengan mata** di
390×844 dan 1440×900. Bug seperti kotak abu-abu kosong dan panah yang menciut jadi titik tidak
akan pernah tertangkap unit test; keduanya hanya terlihat.

Catatan kondisi repo saat perubahan ini dibuat:

- Target `assembleAndroidMain` gagal karena kesalahan **yang sudah ada sebelumnya dan tidak
  berhubungan**: `presentation/deal/components/MockupCropDialog.kt` memakai API skia
  (`toComposeImageBitmap`, `org.jetbrains.skia.*`) dari `commonMain`, yang tidak tersedia di
  source set Android. Itu utang terpisah.

### Checklist yang dilalui

- [x] Nol literal `Color(0xFF……)` baru
- [x] Nol `RoundedCornerShape(N.dp)` telanjang — semua lewat `ClayShapes.*`
- [x] Nol `Modifier.shadow()`
- [x] Pola yang muncul ≥3 kali diangkat ke `designsystem/` (`ClayFlowRow`)
- [x] Komponen `designsystem/` tidak mengimpor domain maupun package fitur
- [x] Tidak ada ternary `isPresentationMode` baru
- [x] Seluruh berkas yang disentuh di bawah soft limit 400 baris lapisan `presentation/`
- [x] Dilihat dengan mata di lebar telepon **dan** desktop

---

## Ringkasan: Tiga Hal untuk Dibawa ke Task Berikutnya

1. **Kotak kosong = lebar nol, bukan data kosong.** Setiap kali melihat area kosong atau elemen
   menciut di Compose, jumlahkan dulu lebar tetap anak-anak `Row`-nya. `weight` adalah kreditor
   terakhir.

2. **Dua susunan lebih jujur daripada satu kompromi.** Kalau satu susunan benar di desktop dan
   mustahil di telepon, jangan cari nilai tengah yang buruk di keduanya — cabangkan tata
   letaknya, dan tarik garis pisahnya *setelah* semua state dihitung supaya artinya tidak ikut
   bercabang.

3. **Mobile-first artinya menanyakan alur kerjanya, bukan mengecilkan panelnya.** Yang mengubah
   layar ini bukan penyesuaian dp, melainkan satu pengamatan: di QC, antrean dan lembar ukur
   dipakai **berurutan**, tidak bersamaan. Begitu itu disadari, satu panel penuh jadi jawaban
   yang jelas.
