# Teaching — Discovery C1: verifikasi mata pada funnel wizard (dan enam bug yang hanya terlihat di peramban)

> Plan: [`PLAN-discovery-blueprint-prototype-studio.md`](../plannings/PLAN-discovery-blueprint-prototype-studio.md) §3–§5 (Fase C/D) ·
> Status: selesai 2026-09-30 · Pendahulu: [`teaching-discovery-a1-a7-a9-vertical-slice.md`](teaching-discovery-a1-a7-a9-vertical-slice.md),
> [`teaching-discovery-a8-koog-agent.md`](teaching-discovery-a8-koog-agent.md)

Dokumen ini bukan tentang fitur baru. Ia tentang **satu langkah yang paling sering dilewati**: membuka
aplikasinya dan melihatnya dengan mata. Seluruh kode di bawah ini **sudah hijau kompilasi** dan
**sudah lolos test** sebelum sesi ini — dan enam cacatnya baru ketahuan setelah funnel dijalankan di
peramban, dari narasi sampai tombol "Bangun Sistem Ini".

---

## Step 0 — Kenapa test tidak bisa menggantikan mata di sini

Aplikasi di `app/shared` menggambar ke **canvas WebGL** (Compose Wasm). Artinya:

- `assert` di `commonTest` menguji **fungsi murni** (validator, kodec, `WidgetRegistry`), bukan tata letak.
- Bug tata letak Compose tidak memecahkan kompilasi: `Modifier` yang salah urutan atau `when` yang
  kehilangan satu cabang tetap menghasilkan program yang berjalan — hanya salah tampil.
- Bug jenis ini **tidak punya jalur render di test**: tidak ada hierarki Compose yang diperiksa, tidak
  ada screenshot diff. Satu-satunya orakel yang kita punya adalah mata.

Checklist design-system rules §7 menuntut "dijalankan dan dilihat dengan mata" bukan tanpa alasan —
di sesi ini kalimat itu terbukti secara literal.

## Step 1 — Menyiapkan lingkungan (tiga proses)

```bash
docker start wemade-postgres                     # 5432
cd /Volumes/amalari/Projects/wemkaeerp
(nohup ./gradlew :server:run        > /tmp/wemade-server.log 2>&1 &)   # API :8081
(WEMADE_WEB_PORT=3001 nohup ./gradlew :app:webApp:wasmJsBrowserDevelopmentRun \
   > /tmp/wemade-web.log 2>&1 &)                                        # UI :3001
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/health   # 200
lsof -nP -iTCP:3001 -sTCP:LISTEN                                        # node … LISTEN
```

Dua catatan yang menghemat waktu:

- Repo B memakai port **3001 (web) / 8081 (API)** supaya bisa berdampingan dengan repo A
  (`app/webApp/webpack.config.d/devServer.js` + `app/webApp/build.gradle.kts`). Jalankan web dengan
  `WEMADE_WEB_PORT=3001` — kalau lupa, proxy `/api` di dev server akan menunjuk server yang salah.
- **Dev server tidak ikut menkompilasi ulang Kotlin.** `wasmJsBrowserDevelopmentRun` mengompilasi sekali
  lalu menyajikan hasilnya; mengubah `.kt` mengharuskan task itu dihidupkan ulang. Jadi siklusnya:
  ubah kode → `./gradlew :app:shared:compileKotlinWasmJs` → `pkill -f webpack` → jalankan lagi.


## Step 2 — Klik di kanvas Compose: pakai `getByRole`, jangan koordinat

Karena UI-nya canvas, `page.click('button:has-text(...)')` **tidak menemukan apa pun**. Dua cara yang
bekerja, dan bedanya penting:

| Cara | Perilaku | Pakai untuk |
|---|---|---|
| `page.mouse.click(x, y)` | menebak posisi dari screenshot | sekali pakai, cepat rusak saat tata letak bergeser |
| `page.getByRole('button', { name: 'Lihat Estimasi' }).click()` | memakai kotak semantik Compose | **selalu** — stabil terhadap pergeseran |

Kotak semantik (`boundingBox()`) **tidak** sama dengan posisi visual: kotak itu mengecualikan ruang
bayangan yang disisihkan `claySurface` (6dp) dan tinggi tombolnya sendiri. Di sesi ini klik koordinat
pada `y=1211` meleset, sedangkan tombolnya ada di `y=1154..1194` menurut kotak semantik — dan
`getByRole(...).click()` langsung benar.

> **Jebakan yang benar-benar memakan waktu**: `browser_resize` mengubah viewport, tetapi pemetaan
> koordinat klik bisa tetap memakai ukuran lama. Setelah resize, tata letak terlihat benar sementara
> klik mendarat di tempat lain. Kalau tombol "tidak bereaksi", pindah ke `getByRole` **dulu** sebelum
> menuduh kodenya rusak.

## Step 3 — Enam temuan, satu per satu

### Temuan 1 — `when` yang kehilangan satu cabang (langkah Narasi tidak pernah tampil)

```kotlin
// ❌ SEBELUM — `else` dipakai untuk pesan "terima kasih"
when (step) {
    2 -> …   // Draf
    3 -> …   // Estimasi
    4 -> …   // Bangun
    else -> ClayCard(…) { Text("Terima kasih! Tim kami akan menghubungi Anda…") }
}
```

`step` diinisialisasi `1`, jadi **langkah pertama langsung menampilkan pesan "terima kasih"** dan form
narasi tidak bisa dijangkau dari mana pun. Kompilator diam karena `when` di posisi *statement* tidak
wajib ekshaustif, dan `else` menelan nilai tak terduga.

```kotlin
// ✅ SESUDAH — cabang eksplisit, `else` dihapus
when (step) {
    1 -> StepNarrative(…, onSubmit = {
        client.createDraft(narrative.trim(), industryHint.trim().ifBlank { null })
            .mapCatching { raw -> DiscoveryDraftUi.fromJson(raw as? JsonValue.Obj
                ?: throw IllegalStateException("Respons draf tidak dikenali")) }
            .onSuccess { d -> draft = d; draftId = d.id; step = 2 }
    })
    2 -> …   3 -> …   4 -> …
    5 -> ClayCard(…) { Text("Terima kasih! …") }   // hanya step 5 = terkirim
}
```

**Pelajaran**: `else` pada `when` berbasis **state machine** adalah cara paling halus menyembunyikan
cabang yang hilang. Kalau cabangnya punya arti bisnis, tulis eksplisit — biarkan state tak dikenal
menghasilkan *tidak ada apa-apa*, bukan pesan yang menipu.

### Temuan 2 — `fillMaxWidth(fraction)` di dalam `Row(horizontalScroll)`

Kolom peta modul semula `Modifier.fillMaxWidth(0.3f)`. Di dalam `Row` yang menggulir mendatar, lebar
maksimum adalah **tak hingga**; `fillMaxWidth` mengabaikan constraint tak hingga, sehingga kolom
menyusut ke lebar intrinsiknya dan teksnya pecah **satu huruf per baris** ("P e l a n g g a n").

Perbaikannya bukan menebak angka di layar, melainkan menambah **token**:

```kotlin
// designsystem/ClayTokens.kt
object ClayPaneWidth {
    val List: Dp = 380.dp
    /** Kanvas yang menggulir mendatar: wajib lebar tetap, bukan fillMaxWidth(fraction). */
    val Board: Dp = 260.dp
}

// ModuleMapPane.kt
Column(modifier = Modifier.width(ClayPaneWidth.Board).background(…))
```

Sekalian dengan itu, teks yang boleh mengalah diberi `maxLines` + `overflow = TextOverflow.Ellipsis`
(Kontrak 13 design system) — tanpa itu, ellipsis tidak pernah muncul dan teksnya terpotong diam-diam.

### Temuan 3 — `ClayCard` dengan `containerColor` transparan menampakkan bayangan (kartu jadi navy)

Ini temuan yang paling tidak terduga, dan penyebabnya menarik:

```kotlin
// ❌ Kartu modul non-aktif "diredupkan" dengan alpha
ClayCard(containerColor = WeMadeColors.SurfaceMuted.copy(alpha = 0.12f), …)
```

`claySurface` menggambar **hard shadow sebagai bidang solid tepat di belakang kartu** (offset 6dp,
`shadowColor = outlineColor` = `#1E293B`). Isi kartu yang transparan **tidak menutupi** bidang itu, jadi
yang terlihat adalah navy gelap menembus kartu — teks kelabu di atasnya jadi tidak terbaca. Yang tampak
seperti "kartu dinonaktifkan" justru kebalikan dari maksudnya.

```kotlin
// ✅ Peredupan lewat token opaque + outline lebih lembut; state dibedakan warna, bukan alpha
containerColor = if (module.active) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
outlineColor   = if (module.active) WeMadeColors.Primary else WeMadeColors.OutlineSoft
```

Jebakan ini sekarang tertulis di KDoc `ClayCard` supaya orang berikutnya tidak mengulanginya. **Aturan
umumnya**: elemen clay yang punya bayangan wajib berlatar opaque; `.copy(alpha = …)` untuk peredupan
hanya aman pada elemen **tanpa** bayangan (mis. latar kolom papan, `ClayBadge` di dalam kartu opaque).

### Temuan 4 — State kosong yang tidak dijelaskan

Draf dari agent **deterministik** tidak mengusulkan `screens` (deskriptor layar hanya dibuat agent LLM,
`KoogDiscoveryPrompt`). Akibatnya judul "Pratinjau Layar" menggantung tanpa isi — prospek mengira
sesuatu rusak. Perbaikannya satu kartu penjelasan di `PrototypeRenderer` yang menyebut penyebab dan
jalan keluarnya (`DISCOVERY_AGENT=koog` + `DEEPSEEK_API_KEY`).

**Pelajaran**: jalur non-default (kill-switch mati) juga layar yang harus punya copy. Kalau sebuah mode
dijanjikan "berfungsi tanpa LLM", maka **tampilannya** juga harus jujur soal apa yang belum bisa ia
tampilkan.

### Temuan 5 — Angka yang benar tapi menyesatkan ("Rp 0 – Rp 0")

`gapLow/gapHigh` = jumlah harga modul yang **belum ada** (`PriceProspectFlowUseCase`). Untuk pack
konveksi yang seluruh modulnya sudah dirilis, hasilnya memang nol — bukan estimasi gagal. Tapi ditulis
"Biaya pembangunan sekali: Rp 0 – Rp 0", prospek membacanya sebagai angka rusak. Copy-nya diganti
menjadi kalimat yang menyebut **keadaannya** ("Semua modul pada alur ini sudah tersedia — tidak ada
biaya pembangunan tambahan"), dengan angka langganan tetap ditampilkan.

### Temuan 6 — Langkah terakhir tanpa jalan kembali, lalu CTA ganda

Langkah 4 tidak punya tombol kembali padahal 2 dan 3 punya; prospek terjebak dan harus memuat ulang
halaman. Setelah ditambah, muncul masalah kedua: CTA oranye di dalam kartu **dan** tombol di baris aksi
— dua tombol untuk satu aksi. Penyelesaiannya memperluas `StepActions` alih-alih menggandakan tombol:

```kotlin
StepActions(
    onBack = onBack, onBackLabel = "Kembali ke Estimasi",
    onNext = onSubmit, onNextLabel = if (busy) "Mengirim…" else "Kunci & Bangun Sistem Ini",
    nextEnabled = !busy, nextStyle = ClayButtonStyle.Accent   // CTA tetap oranye, satu saja
)
```

**Pelajaran**: saat menambah tombol, periksa dulu apakah **satu komponen bersama** bisa menampung
perbedaan itu (Rule of Three). Menambah parameter gaya pada `StepActions` lebih murah daripada
memelihara dua jalur tombol yang harus ikut berubah bersama.

## Step 4 — Verifikasi akhir: bukti yang harus dikumpulkan

Funnel dijalankan utuh sebagai superadmin (`superadmin_apps` / `PLATFORM_SUPERADMIN`, tenant
`wemade-demo`) dengan narasi konveksi:

| Titik | Bukti |
|---|---|
| Narasi → draf | `POST /api/discovery/drafts` → **201 Created** |
| Estimasi | `GET /api/discovery/drafts/{id}/price?marginPercent=35.0` → **200 OK** |
| Kunci | `POST /api/discovery/drafts/{id}/lock` → **200 OK** |
| Bangun | `POST /api/discovery/drafts/{id}/submit` → **201 Created** |
| Layar | langkah 2 = 7 modul aktif + peta modul + alur data; langkah 3 = copy baru; langkah 5 = terima kasih |

Kompilasi: `:app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs
:app:shared:jvmTest` → **BUILD SUCCESSFUL**; `:server:test --tests '*Discovery*'` → hijau.

> **Catatan lingkungan**: `:app:shared:assembleAndroidMain` **tidak bisa** dijalankan di mesin ini —
> `/Volumes/amalari/Android/sdk` kosong (tanpa `platforms/android-36`, lisensi belum diterima). Ini
> keterbatasan lingkungan, bukan regresi: jangan melaporkannya sebagai "5 target hijau".

## Step 5 — Audit sebelum merge

```bash
grep -c 'Color(0xFF'   …/presentation/discovery/*.kt        # 0 di keempat file
grep -n 'Modifier.shadow\|RoundedCornerShape([0-9]' …       # tidak ada
wc -l                  …/presentation/discovery/*.kt        # 81 / 312 / 127 / 192 (soft 400)
```

Pola baru yang diangkat/ditambahkan: `ClayPaneWidth.Board` (token, bukan angka di layar) dan catatan
jebakan `ClayCard` (dokumen kontrak, bukan komentar lokal).

## Adendum — tiga call site lain dengan akar yang sama (2026-09-30)

Menelusuri akar Temuan 3 dengan `grep 'containerColor' … | grep 'copy(alpha'` menemukan tiga call site
`ClayCard` lain yang memakai isi transparan di atas bayangan hard. Ketiganya diperbaiki dengan pola yang
sama: **isi opaque + outline berperan**.

| Berkas | Sebelum | Sesudah | Verifikasi mata |
|---|---|---|---|
| `fulfillment/FulfillmentWorkspaceScreen.kt:96` (banner error) | `Error.copy(alpha = 0.08f)` | `ErrorBg` + outline `Error` | ✅ dipicu error server nyata |
| `crm/components/LeadInspectorInvoiceTab.kt:215` (banner info) | `Info.copy(alpha = 0.08f)`, outline `Info.copy(alpha = 0.3f)` | `InfoBg` + outline `Info` | ✅ tab Invoice prospek |
| `sampling/components/CreateSamplingOrderDialog.kt:88,109` (tile ukuran terpilih) | `Primary.copy(alpha = 0.12f)` | `PrimaryContainer` + outline `Primary` | ❌ **komponen yatim** — lihat di bawah |

Token baru: `WeMadeColors.InfoBg` (Sky-50). Keluarga `*Bg` tadinya lengkap kecuali `Info` — itulah
sebabnya orang menulis `Info.copy(alpha = …)` sebagai gantinya. Menambah token lebih murah daripada
memerangi kebiasaan.

`PrimaryContainer` bukan token baru: ia sudah dipakai sebagai isi kartu "terpilih/aktif" di
`PersonaSwitcherDropdown`, `ModuleCardView`, dan `AiQuickEstimatorPane`, jadi tile di dialog sampling
kembali sekeluarga.

### Cara memicu state error agar bannernya bisa dilihat

Banner error hanya muncul bila `state.error != null`, sedangkan validasi klien menahan pengiriman lebih
dulu. Resep yang bekerja di Fulfillment — pakai ulang untuk layar lain yang berpola sama:

1. Isi kode wadah dengan **sampah** (`W1SK-KARUNG-PALSU-999`) → `Cari`.
2. Isi berat (`8,35`), unggah foto timbangan (berkas apa pun), isi nama petugas.
3. `Ajukan Antar` → server menolak (`Kode wadah tidak dikenali`) → banner tampil.

Lalu lihat bannernya dengan mata: merah pucat, outline merah pekat, teks terbaca, bayangan hard merah ke
kanan-bawah. Tanpa langkah ini, satu-satunya "bukti" adalah asumsi bahwa token opaque berperilaku seperti
di layar lain.

### Catatan penting: dialog Sampling ternyata kode mati

`CreateSamplingOrderDialog` **tidak punya satu pun call site** di seluruh repo (`.kt`, `iosApp`, `docs`) —
komponen yatim. Artinya perbaikannya benar, tetapi **tidak bisa diverifikasi mata**, dan itu harus
dikatakan apa adanya alih-alih mengklaim "sudah dicek". Dua pilihan lanjutan, keduanya di luar task ini:

- **Pakai**: sambungkan ke alur pembuatan order Sampling (tombol tambah di `SamplingWorkspaceScreen`),
  lalu verifikasi matanya saat itu.
- **Buang**: hapus berkasnya; bila nanti dibutuhkan, pola tile "terpilih" sudah ada di katalog — lebih
  baik ditulis sebagai komponen bersama (`ClayChoiceTile`) daripada menghidupkan kembali kode mati.

Komponen yatim bukan sekadar beban baris: ia **melewati pengecekan mata selamanya**, karena tidak ada
layar yang perlu dilewati untuk menyadari ia rusak. Itulah kenapa ia layak diselesaikan.


## 🏆 Tantangan mandiri

1. **Uji lebar sempit.** Kecilkan jendela dan buka langkah 2. Peta modul menggulir mendatar: pastikan
   bayangan kartu tidak terpotong di tepi kanvas dan kolom tidak saling timpa.
2. **Nyalakan agent LLM.** Jalankan server dengan `DISCOVERY_AGENT=koog DEEPSEEK_API_KEY=…`, ulangi
   narasi yang sama, lalu bandingkan langkah 2: kini `screens` tidak kosong dan `PrototypeRenderer`
   menggambar FORM/TABLE/KANBAN. Buktikan dengan mata bahwa empty-state baru **hilang** — bukan sekadar
   percaya pada `if`.
3. **Cari `else` yang mencurigakan.** `grep -rn "else ->" app/shared/src/commonMain/…/presentation/`
   lalu tanyakan pada tiap hasil: apakah `else` ini menangani nilai ke-`n` yang sah, atau menyembunyikan
   cabang yang seharusnya eksplisit?

## Utang & langkah berikutnya

- **Posisi langkah tidak ada di rute.** Memuat ulang di langkah 3 melempar pengguna kembali ke langkah 1
  dan `draftId` hilang; `step` masih `remember` lokal, belum bisa dipulihkan (mis. `?draft=<id>`).
- **Belum ada test layar.** Funnel ini sepenuhnya bergantung pada verifikasi mata; `commonTest` di paket
  `presentation/discovery/` kosong. Kandidat termurah: pindahkan transisi langkah ke fungsi murni
  (`stepAfter(step, event)`) lalu uji di sana — Temuan 1 sebenarnya bisa ditangkap test seperti itu.
- **Jejak agent belum terlihat di UI** (`agentRef`, utang A8): prospek tidak tahu drafnya disusun LLM
  atau kata kunci, padahal langkah 2 menampilkan "0 layar pratinjau" justru karena itu.
- **`ClayPaneWidth.Board` vs `324.dp` di `PipelineFlowCanvas.kt:298`** — swimlane Factory Flow masih
  memakai angka telanjang; saat file itu disentuh, pindahkan ke token yang sama.
- **`CreateSamplingOrderDialog` (177 baris) masih komponen yatim** — diperbaiki di sesi ini tapi tak bisa
  dilihat mata. Putuskan: sambungkan ke alur pembuatan order Sampling, atau hapus dan tulis ulang pola
  tile "terpilih" sebagai `ClayChoiceTile` di `designsystem/`.




---

## Adendum 2 — RLS yang ternyata tidak pernah aktif, batas schema, dan `CUSTOM_SCREEN` (2026-09-30)

Tiga item audit rencana (`docs/plannings/PLAN-discovery-blueprint-prototype-studio.md`) ditutup di
sesi ini. Yang pertama paling layak diajarkan: **bukan bug kode, tapi bug bacaan.**

### A0 — `DB_APP_USER` sudah "diisi", tapi tidak pernah dibaca

Gejala: server dev selalu mencetak 'DB_APP_USER is not set', padahal `.env`, `.env.example`, dan
`docker-compose.yml` sudah memuatnya sejak lama. Artinya penegakan RLS di mesin dev **tidak pernah
hidup** — dan seluruh suite "RLS hijau" lulus lewat peran owner (superuser), yang memang melewati RLS.

Akar masalahnya satu baris:

```kotlin
// DatabaseFactory.kt — sebelum
val appUser = System.getenv("DB_APP_USER")
```

`EnvLoader` (pembaca `.env` + `System.getenv`) **sudah ada** di repo ini… dan hanya dipakai
`GoogleAuthService`. Dua pembaca paling berkonsekuensi — `DatabaseFactory` dan kill-switch
`DiscoveryAgents.fromEnv()` — menembusnya dengan `System.getenv`. Nilainya ada di berkas, tapi tidak
ada yang membacanya dari berkas.

**Perangkap kedua, yang lebih halus:** guard test-nya sendiri memakai pembaca yang sama.

```kotlin
// TenantRlsIsolationTest — sebelum
val appUser = System.getenv("DB_APP_USER") ?: return   // ← diam-diam skip
```

Test itu ditulis justru untuk membuktikan "pool tenant benar-benar dipakai", tapi ia `return` lebih
awal selama nilainya hanya ada di `.env`. **Test hijau, assertion tidak pernah jalan** — kelas
kesalahan yang sama dengan pelajaran TRD-FLOW-001: fallback senyap yang menyembunyikan keadaan.

Perbaikan (satu arah, tiga tempat): `DatabaseFactory` (URL/DB/user + `DB_APP_USER`/`DB_APP_PASSWORD`)
dan `DiscoveryAgents.fromEnv()` membaca lewat `EnvLoader`; guard test memakai `EnvLoader` juga. Lalu
satu tambahan kecil — alasannya positif, bukan hanya negatifnya:

```kotlin
println("[DatabaseFactory] tenant-scoped pool active as '$appUser'; RLS enforced by PostgreSQL.")
```

*'Tidak ada peringatan' tidak bisa dibedakan dari 'peringatan tidak tercetak'* saat memverifikasi.
Baris positif membuat verifikasinya punya bukti, bukan kesan.

Bukti yang dikumpulkan (bukan hanya kompilasi):

| Bukti | Caranya | Hasil |
|---|---|---|
| Server menghormati `.env` tanpa `export` | `:server:run` dari shell bersih | `tenant-scoped pool active as 'wemade_app'`, **0 baris** 'is not set' |
| RLS benar-benar ditegakkan | `pg_stat_activity` | **10 koneksi `wemade_app`** + 11 `postgres` (pool owner/platform) |
| Sisa aplikasi tidak rusak oleh peran baru | `:server:test --no-daemon` penuh | 61 suite, **275 test, 0 gagal, 0 error**, 1 skip (evals LLM opt-in) |
| Assertion pool tenant benar-benar jalan | XML `TenantRlsIsolationTest` | `tests="4" skipped="0"` (sebelumnya skip diam-diam) |
| Funnel utuh di bawah RLS | wizard lengkap di peramban | draf `LOCKED` + lead prospek lahir |

Pelajaran yang bisa dipakai ulang: **konfigurasi yang sudah ditulis bukan konfigurasi yang terbaca.**
Saat menemukan nilai yang "seharusnya berlaku", cari **pembacanya** dulu (`grep -rn System.getenv`),
jangan nilainya. Dan test yang di-gate env harus memakai pembaca yang sama dengan kode produksi —
kalau tidak, ia bukan test, ia dekorasi.



### A5 — dua tabel yang lolos dari batas schema

`OpsSchemaBoundaryTest` menjaga daftar tabel berbiaya agar tinggal di schema `ops`. Dua tabel funnel
discovery belum terdaftar: `discovery_drafts` dan `prototype_patterns` — keduanya bisa "pindah" ke
`public` tanpa ada test yang gagal. Sekarang terdaftar, sekaligus tervalidasi di DB: keduanya
`ops.*`, dan `wemade_app` **tidak** punya satu pun grant di sana (batasnya nyata).

### `CUSTOM_SCREEN` — kind yang terdaftar tapi tidak pernah digambar

`WidgetKind.CUSTOM_SCREEN` ada di kosakata v1 (prompt LLM pun menyebutnya), tapi:

- `WidgetRegistry.sampleRowsFor` mengembalikan `emptyList()` untuk kind ini;
- `PrototypeRenderer.WidgetBody` tidak punya cabangnya → jatuh ke `else` (tabel) → kartu hanya punya
  judul dan badge, tanpa isi;
- test-nya justru **mengecualikan** kind ini dari assertion "sample non-kosong"
  (`entries.filter { it != CUSTOM_SCREEN }`) — jadi kartu kosong itu tidak pernah tertangkap.

Perbaikan mengikuti aturan yang sama seperti sampel kind lain: **penanda struktur, bukan data.**
Layar rancangan bebas tidak punya kolom baku, jadi yang dinyatakan adalah *blok apa yang ada dan
selebar apa*:

```kotlin
WidgetKind.CUSTOM_SCREEN -> listOf(
    mapOf("Blok" to "Ringkasan ${module.displayName}", "Lebar" to "penuh"),
    mapOf("Blok" to "Daftar ${module.displayName}", "Lebar" to "separuh"),
    mapOf("Blok" to "Panel aksi", "Lebar" to "separuh")
)
```

Aturan pemasangan (blok "penuh" berdiri sendiri, "separuh" dipasangkan dengan tetangganya) tinggal di
**renderer**, bukan di data — supaya sample tetap sekadar pernyataan struktur. Test-nya kini
mengiterasi **seluruh** kind (tanpa pengecualian) dan menegaskan urutan lebarnya
(`listOf("penuh", "separuh", "separuh")`).

Hasil di mata: kartu "Papan Informasi Ruang Tunggu" (draf LLM) sekarang menampilkan badge
**● Layar Kustom** (violet, `WeMadeColors.Purple`) plus kerangka satu blok penuh lalu dua blok separuh
— `tmp/.playwright-mcp/43-pratinjau-layar-1.png`.

### Kalimat yang menyalahkan prospek

Karena `CUSTOM_SCREEN` dulu satu-satunya jalan ke `rows.isEmpty()`, copy empty state-nya berbunyi
*"Pratinjau layar kustom — menyusul setelah pola Studio dipilih."* Sekarang jalan ke sana tinggal dua:
modul tidak ada di pack, atau kode widget di luar kosakata v1. Kalimatnya diganti agar menyebut
**penyebab sebenarnya** ("Layar ini belum bisa dipratinjau: \"$widget\" tidak punya contoh tata letak,
atau modulnya tidak ada di pak ini.").

Aturan kecil yang layak diingat: copy empty state menyebut keadaan sistem, bukan mengalihkan
tanggung jawab ke pengguna.

### Gate nama perusahaan — fallback diam yang menjadi data

Sesi sebelumnya memperbaiki CTA ganda di langkah 4. Saat memverifikasi submit, DB menunjukkan lead
bernama **"Prospek Baru"** — karena klien mengirim `companyName.ifBlank { "Prospek Baru" }` sementara
endpoint memang menolak nama kosong. Placeholder itu berakhir sebagai **judul lead di ledger tim**.

Ini persis "fallback senyap = data berubah" (`tenant-variability-rules.md` Kontrak 4). Perbaikan:
`ifBlank` dibuang, tombol dimatikan saat nama kosong, dan alasannya ditulis di UI
("Nama perusahaan wajib diisi — ia menjadi judul lead di ledger tim kami.").

Bukti perilakunya (DOM Compose tidak mengekspos `disabled`, jadi dicek dua cara):

| Cek | Hasil |
|---|---|
| Tampilan saat kosong | CTA oranye pudar tanpa bayangan + hint tampil — `47-step4-nama-kosong.png` |
| Klik **paksa** pada CTA itu | tetap di langkah 4; `company_name='Prospek Baru' and created_at > now() - interval '3 minutes'` → **0** |
| Setelah nama diisi | hint hilang, CTA kembali pekat — `48-step4-nama-terisi.png` |
| Submit | `lead-1790748819947 \| Klinik Gigi Senyum Sehat \| TRANSLATED \| 06:13:39` — nama asli, bukan placeholder |

Catatan teknik: `page.getByRole('button').isDisabled()` **tidak** bisa dipakai untuk Compose Wasm —
element-nya `<div role="button">` tanpa `aria-disabled`. Verifikasi harus lewat mata + efek di data.

### Berkas yang disentuh sesi ini

```
server/src/main/.../infrastructure/DatabaseFactory.kt           (178)  EnvLoader + baris log positif
server/src/main/.../infrastructure/discovery/DiscoveryAgents.kt  ( 95)  kill-switch lewat EnvLoader
server/src/test/.../infrastructure/TenantRlsIsolationTest.kt     (104)  guard pakai EnvLoader
server/src/test/.../infrastructure/OpsSchemaBoundaryTest.kt      (100)  + discovery_drafts, prototype_patterns
core/src/commonMain/.../domain/discovery/WidgetRegistry.kt       ( 56)  sample CUSTOM_SCREEN
core/src/commonTest/.../domain/discovery/WidgetRegistryTest.kt    ( 99)  tanpa pengecualian + urutan lebar
app/shared/.../presentation/discovery/PrototypeRenderer.kt       (243)  cabang CUSTOM_SCREEN + copy jujur
app/shared/.../presentation/discovery/DiscoveryWizardScreen.kt   (324)  gate nama perusahaan
```

Cek sebelum merge yang dijalankan: 4 target `:app:shared` + `:jvmTest` + `:core:jvmTest` +
`:server:test --no-daemon` (275 test) hijau; 0 literal `Color(0xFF` baru; semua berkas jauh di bawah
ambang hard lapisannya; `scripts/audit-variability.sh` → 0 temuan.

### Masih terbuka setelah sesi ini

- `:app:shared:assembleAndroidMain` belum bisa dijalankan di mesin ini (Android SDK tidak ada di
  `/Volumes/amalari/Android/sdk`); empat target lain hijau.
- **A4** — `ModuleDefinition` masih tanpa `actions`/`vocabulary`; `GenericModuleRoute` masih menulis
  "di pabrik ini".
- **B4** — kandidat hasil scaffold belum pernah dibuktikan lolos
  `ModuleSchemaOwnershipTest`/`TenantRlsIsolationTest`/`RouteGateTest`.
- **C** — layar pola Studio (`PrototypePattern`) belum punya UI; **D** — PDF blueprint ber-watermark;
  **E** — sesi wawancara persisten, demand ledger, gate Aturan Tiga Kali.
- Utang lama: posisi langkah tidak ada di rute (`?draft=<id>`); jalur LLM kini **bisa** dinyalakan
  lewat `.env` berkat A0, tapi jejak agent (`agentRef`) belum tampil di UI.


---

## Adendum 3 — A4: kosakata pack, dan kata "pabrik" yang bersembunyi di tempat yang tidak terduga (2026-09-30)

A4 meminta label aksi & istilah menjadi **data pack**, supaya layar `/m/{code}` milik tenant klinik
tidak berbicara "SPK"/"pabrik". Yang ditemukan saat mengerjakannya justru tiga hal yang berbeda dari
dugaan.

### Keputusan desain: aksi & istilah tinggal di **pack**, bukan di `ModuleDefinition`

Rencana menulis `ModuleDefinition.actions` + `vocabulary`. Itu akan **ditolak** oleh aturan identitas
global yang sudah ada:

```kotlin
// DomainPackRegistry.violations — modul bersama wajib identik lintas pack
val existing = others.firstNotNullOfOrNull { it.module(m.id) }
if (existing != null && existing != m) out += "Modul … sudah dipakai pack lain dengan definisi berbeda"
```

`DomainPackApiTest` sudah membuktikan susunan itu nyata: pack klinik memakai `org_chart` —
**modul platform yang sama** dengan pack garment. Kalau label aksi & istilah menempel di
`ModuleDefinition`, satu-satunya cara pack klinik lolos adalah memakai kata garment. Persis kebalikan
dari tujuan A4.

Jadi keduanya naik satu tingkat, ke `DomainPack`:

```kotlin
val actions: List<ModuleAction> = ModuleActionCode.neutral,      // urutan = urutan tombol
val vocabulary: Map<VocabularyKey, String> = emptyMap(),         // kosong = kata netral platform

fun actionLabel(code: ModuleActionCode): String = actions.firstOrNull { it.code == code }?.label ?: code.neutralLabel
fun term(key: VocabularyKey): String = vocabulary[key] ?: key.neutral
```

Yang tetap **enum** adalah perannya, bukan katanya (`VocabularyKey`, `ModuleActionCode`) — ia menjawab
"kata ini untuk benda apa", dan `ModuleActionCode` juga membawa wewenang minimum
(`ADD/EDIT → OPERATE`, `APPROVE/DELETE → MANAGE`). Yang jadi data pack cuma **nilainya**.

Test kuncinya sengaja dibuat untuk menjelaskan keputusan ini, bukan cuma menguji perilaku:

```kotlin
// PackVocabularyTest
val mixed = klinik.copy(modules = listOf(definisiOrgChartMilikGarment) + klinik.modules)
assertEquals(emptyList(), DomainPackRegistry.violations(mixed))     // kata berbeda, modul sama → sah

val renamed = shared.first().copy(displayName = "Pegawai Klinik")
assertTrue(DomainPackRegistry.violations(...).isNotEmpty())          // definisi berbeda → ditolak
```

### "Kata netral" harus benar-benar netral — dan itu harus dites

`actions`/`vocabulary` yang tidak diisi berarti chrome memakai kata platform: `"perusahaan"`,
`"dokumen"`, `"Tambah"`. Itu **bukan** fallback senyap ke kosakata vertikal: nilainya bukan data
vertikal mana pun, dan test menguncinya (`VocabularyKey.entries.map { it.neutral }` +
`ModuleActionCode.entries.map { it.neutralLabel }` diuji terhadap daftar kata terlarang: pabrik, spk,
kain, jahit, konveksi, garmen, makloon, bordir). Tanpa test ini, "kata netral" akan perlahan berubah
jadi kata konveksi lagi — persis yang terjadi pada `DataScope` di bawah.

### Kata "pabrik" ternyata bersembunyi di *domain*, bukan di layar

Setelah layar klinik bersih dari enam literal garment (tombol, judul kartu, banner, kartu modul tak
dikenal, kartu belum berlangganan), scan `ariaSnapshot` masih menemukan **satu** "pabrik":

```
Cakupan data: Pengguna dapat melihat seluruh data di seluruh divisi pabrik.
```

Kalimat itu tidak ditulis di Composable mana pun — ia `DataScope.ALL_TENANT_DATA.description`
(`core/.../domain/rbac/AccessLevel.kt`), dan chrome menampilkannya apa adanya. Pelajarannya:
**mencari kosakata vertikal harus sampai ke lapisan data domain**, bukan berhenti di `presentation/`.

### Bukti (test + route + mata)

| Lapis | Bukti | Hasil |
|---|---|---|
| Domain | `PackVocabularyTest` (9 test) + baris emas kata di `GarmentModulesParityTest` | `core:jvmTest` **1017 test, 0 gagal** |
| Agen deterministik | `DeterministicDiscoveryAgentTest` — narasi klinik → `WORKPLACE=klinik`, `DOCUMENT=Kunjungan`, `ADD="Tambah Kunjungan"`; vertikal tak dikenal → netral | hijau |
| Route/DB | `DomainPackApiTest` — pack klinik (`actions`+`vocabulary`) PUT → LOCK → `GET /api/tenant/pack` → `assertEquals(KLINIK, pack)` | hijau |
| Prompt LLM | `KoogDiscoveryPrompt` aturan 9 + `KoogDiscoveryPromptTest` | hijau |
| Suite penuh | `:server:test --no-daemon` | **61 suite, 275 test, 0 gagal, 0 error**, 1 skip (evals opt-in) |
| Mata | tenant `klinik-uji`, pack v2 (LOCKED) ditulis lewat API admin | `/m/klinik_antrean`, `/m/org_chart`, `/m/klinik_kasir`: **0× "pabrik", 0× "SPK", 7× "Kunjungan"** — `49-a4-klinik-antrean.png` |
| Regresi garment | tenant `wemade-demo`, `/m/inventory` | tombol `Tambah Pesanan / Input Progres / Setujui SPK / Hapus Data`, `Daftar Dokumen`, contoh kain tetap tampil |

Cara memverifikasi ini di peramban **tanpa menebak dari screenshot**: Compose Wasm tidak mengisi
`innerText`, tapi Playwright bisa membacanya lewat a11y tree —

```js
const snap = await page.locator('body').ariaSnapshot();
const pabrik = (snap.toLowerCase().match(/pabrik/g) || []).length;   // 0
```

### Jebakan peramban yang menghabiskan waktu sesi ini (lagi)

- **Field login menyambung teks, bukan mengganti.** `Meta+A` + `Delete` tidak dihormati Compose Wasm,
  jadi slug menjadi `klinik-ujiklinik-uji` → 404 → aplikasi jatuh ke sesi lama, dan yang terlihat di
  layar adalah **persona yang salah** (`achmad_owner`), bukan pesan error. Yang bekerja: klik field →
  40× `Backspace` → `keyboard.type`. Saat persona hasil login tidak sesuai harapan, cek URL demo yang
  benar-benar dipanggil di log server (`auth/demo?tenantSlug=…`) sebelum menuduh kode.
- Sesi lama masih hidup saat membuka `/login`, sehingga form tidak dirender (yang tampil panel "Sesi
  Aktif"). Logout dulu — kalau tidak, `getByRole('textbox')` timeout 30 detik.

### Sisa yang **belum** dibereskan (jangan dibaca sebagai selesai)

- Layar khusus garment (`/org-chart`, RBAC, pipeline) masih menulis "pabrik" sebagai literal
  (`OrgChartScreen` "…sesuai kebutuhan pabrik", `ModuleCardView`/`AssignModuleModal`
  "🌐 Seluruh Pabrik (Shared)"). Itu utang "layar belum dikonversi", bukan bagian A4 — tapi kini kata
  platform di `AccessLevel.kt` sudah netral, jadi ketidakcocokan itu **terlihat**, bukan tersembunyi.
- `ModuleWorkspaceScreen.kt` kini **429 baris** (soft 400 untuk `presentation/**`, hard 600): masih
  punya satu nama jujur (layar + komponen privatnya), tapi kandidat pemecahan berikutnya jelas —
  `AccessProvenanceCard`, `AccessBanner`, `ActionToolbar`, `SampleRecords` → `workspace/components/`.
- `scripts/audit-variability.sh` melaporkan **2 temuan baru** (`VocabularyKey`, `ModuleActionCode`).
  Keduanya lolos Uji Variabilitas dengan alasan tertulis di KDoc-nya: slot/istemanya konsep platform
  (chrome tidak tumbuh per tenant), yang menjadi data pack adalah nilainya. Skrip ini melapor, tidak
  memblokir.
- Contoh baris untuk modul non-garment belum ada (`ModuleSampleRows` masih khusus garment). Sekarang
  minimal jujur ("Belum ada contoh Kunjungan untuk modul ini."), tapi idealnya sampel ikut jadi data
  pack — persis janji Fase C ("sampel data berupa data").

Karena itu kata-kata platform (`AccessLevel`, `DataScope`, `ScopeCapability`) dinetralkan dan ditambah
penjaga regresi `platformAccessWording_isVerticalNeutral`.

Konsekuensi jujur: **satu kalimat pada layar garment ikut berubah** ("…seluruh divisi pabrik." →
"…seluruh divisi perusahaan."). Kata "SPK"/"Tambah Pesanan"/"Daftar Dokumen" pada garment **tidak**
berubah, karena kini datang dari pack-nya sendiri.

