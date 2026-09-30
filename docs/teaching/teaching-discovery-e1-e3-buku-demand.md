# Teaching — Discovery E1–E3: Sesi Persisten & Buku Demand (Fase E)

> Plan: [`docs/plannings/PLAN-discovery-blueprint-prototype-studio.md`](../plannings/PLAN-discovery-blueprint-prototype-studio.md) §6 · Tanggal: 2026-09-30
> Kode: `core/.../domain/discovery/DiscoveryDemand.kt`, `server/.../routes/DiscoveryRoutes.kt`,
> `server/src/main/resources/db/migration/V80__discovery_demands.sql`,
> `app/shared/.../presentation/discovery/DiscoveryWizardScreen.kt` (+`DiscoveryWizardSteps.kt`),
> `app/shared/.../presentation/discovery/studio/DemandLedgerScreen.kt` (irisan kedua),
> `app/shared/.../presentation/discovery/studio/DemandSignals.kt` (irisan ketiga)

## Apa yang diselesaikan

Tiga irisan Fase E (operasi produk):

1. **E1 — Sesi interview persisten.** Wizard dulu mulai dari kosong setiap kunjungan; draf lama
   menumpuk tanpa pintu kembali. Kini `DiscoveryWizardScreen` memuat `GET /api/discovery/drafts`
   (endpointnya sudah lama ada — yang tidak ada adalah *pemakainya*) dan menawarkan
   "Lanjutkan sesi sebelumnya" untuk draf berstatus `DRAFT`. Ringkasan dari endpoint list adalah
   **ringkasan penuh**, jadi resume langsung melompat ke langkah 2 tanpa fetch kedua.
2. **E2 — Buku demand.** Temuan yang menyeramkan saat audit: **narasi prospek tidak tersimpan di
   mana pun** — dokumen draf hanya pack + blueprint + screens, tabel V78 tidak punya kolom narasi.
   V80 menambah `ops.discovery_demands`: narasi verbatim + istilah yang terwakili/tidak.
3. **E3 — Gerbang Rule of Three.** `DemandLedger.candidates`: istilah yang belum punya modul
   dikelompokkan lintas demand; ≥ 3 demand **berbeda** → kandidat modul/widget, lengkap dengan
   kutipan narasi asli supaya reviewer membaca konteks, bukan kata kaos.

## Enam keputusan, dan kenapa

| # | Keputusan | Alasan |
|---|---|---|
| 1 | Narasi disimpan di buku demand, **bukan** di dokumen `DiscoveryDraft` | Dokumen draf = kontrak yang dibekukan & divalidasi ketat. Narasi adalah sinyal produk, bukan bagian kontrak; menaruhnya di sana berarti mengubah schema dokumen lama demi telemetri |
| 2 | Gagal simpan demand menggagalkan `POST /drafts` | Setelah keputusan 1, narasi hanya hidup di satu tempat. Best-effort di sini = demand hilang tanpa jejak — persis penyakit "fallback senyap" yang Kontrak 4 tenant-variability larang |
| 3 | Pencocokan istilah = substring sederhana terhadap kosakata pack | Sasarannya menyaring kata yang sudah terjawab, bukan memahami bahasa. False positive tidak fatal: kandidat selalu dibawa kutipan narasi asli |
| 4 | Kandidat dihitung **saat dibaca**, bukan disimpan | Ambang Rule of Three bisa berubah tanpa migrasi; yang disimpan hanya fakta per demand |
| 5 | `GET /api/discovery/demands` superadmin saja + test 403 | Buku demand = sinyal produk lintas-prospek, milik platform. Bocor ke prospek = bocornya peta jalan kompetitor |
| 6 | Demand insert-saja (tanpa update) | Demand adalah catatan historis; merevisi narasi prospek = memalsukan sinyal |

## Detail teknis yang mudah salah

- **Regex Unicode di KMP.** `\p{L}` di `Regex` melempar di JS/WasmJS tanpa flag `u`. Ganti dengan
  pemetaan karakter (`if (it.isLetterOrDigit()) it else ' '` lalu `split(' ')`) — pola yang sama
  dipakai `DeterministicDiscoveryAgent.packCode`. Berpindah pola = menemukan bug yang kompilasi
  tidak tangkap di JVM.
- **`Num` bukan `Str`.** Asersi test pertama membaca `demandCount` lewat `obj.string(...)` —
  `null` diam-diam karena nilainya `Num`. Accessor yang benar `obj.int(key)`. JSON helper ini
  tidak melempar untuk tipe yang salah; test yang gagal karena `expected 3 but was null` hampir
  selalu ini.
- **Ratchet `Application.kt`.** Menambah parameter inject + baris call site membuat file 698→701.
  Kontrak 2 melarang. Ditambal dengan memindahkan default `PostgresPrototypePatternRepository()`
  ke `DiscoveryRouteFactory` (−1) dan memadatkan dua `val` 2-baris jadi 1 baris (−1). Hasil: 698,
  dan sekalian **utang Fase C cicil** — sebelumnya test Studio menulis ke DB pengembang karena
  repository pattern dibuat langsung di `Application.kt`.
- **Pemecahan file, bukan pemotongan.** Wizard melewati soft limit 400 karena resume card ditambah.
  Yang dipindah bukan baris acak: langkah-langkah (`StepNarrative`, `StepBuild`, `EstimasiPrice`,
  `StepActions`) pindah utuh ke `DiscoveryWizardSteps.kt` — shell hanya merakit, langkah yang
  merender. `private` → `internal` karena lintas-file dalam modul yang sama.

## Verifikasi

- `DemandLedgerTest` (3, `commonTest`): kata yang sudah jadi modul tidak kembali sebagai sinyal;
  kandidat menuntut 3 demand **berbeda** (dobel di satu demand dihitung sekali); urutan + aman
  untuk input kosong.
- `DiscoveryApiTest::narasi tercatat di buku demand dan kandidat rule of three terbaca superadmin
  saja`: 3 draf dengan istilah "gigi" yang belum punya modul → 403 untuk pengguna biasa, 200 untuk
  superadmin, kandidat `gigi` demandCount=3, narasi verbatim + matchedModules tercatat.
- `OpsSchemaBoundaryTest` +1 baris: `discovery_demands` wajib tinggal di schema `ops`.

## Irisan kedua (2026-09-30 sore): layar Buku Demand & pemulihan narasi

Dua tantangan mandiri di bawah (dulu #1 dan #2) dikerjakan di hari yang sama:

1. **`DemandLedgerScreen`** (`/discovery/demands`, drawer Studio). Struktur: kandidat Rule of Three
   di atas (kartu outline `Primary`, badge jumlah demand, kutipan narasi italic), lalu semua demand
   (narasi, `ClayTag` istilah tak terwakili, footer agent + tanggal). Pelajaran gerbang: **server**
   tetap satu-satunya penjaga (403); UI hanya *menjelaskan* — baris drawer disembunyikan untuk
   non-superadmin agar tidak menabrak layar yang pasti ditolak, dan karena gerbangnya sudah pasti,
   layar tidak buang request: `LaunchedEffect(isSuperadmin)` berhenti sebelum fetch.
2. **Pemulihan narasi.** `DiscoveryDemandRepository.findByDraftId(draftId)` → route menambahkan
   `narrative` ke ringkasan draf (`summaryWithNarrative`, dipakai kelima call site ringkasan) →
   wizard mengisi ulang textarea saat resume/"Ubah Narasi". Draf lahir sebelum V80 → `null` →
   mulai kosong, tanpa crash. Draf pra-V80 tidak pernah punya demand, jadi `findByDraftId` null
   untuk mereka adalah **fakta, bukan kegagalan** — inilah kenapa `?` (bukan `!!`) adalah
   pemodelan yang benar.

Verifikasi tambahan: browser superadmin (1440 & 1280 — kandidat, demand, tag muat), owner demo
(kartu penjelasan amber, tanpa kebocoran data, tanpa baris drawer), AuthGuard saat logout;
asersi test baru: `GET /drafts/{id}` mengembalikan `narrative` verbatim. Suite `--rerun`:
1487 test, 0 gagal; `audit-variability.sh` 0 temuan.

## Irisan ketiga (2026-09-30): gerbang di titik keputusan widget

Tantangan #3 daftar sisa dikerjakan: kandidat Rule of Three kini tampil **di Studio Pola**,
tepat setelah kartu berisi pilihan widget — bukan hanya di layar Buku Demand. Pelajarannya:

1. **Titik keputusan menentukan tempat sinyal.** `GET /demands` sudah ada, kandidatnya sudah
   dihitung — yang kurang bukan data, melainkan *jarak*: superadmin memutuskan pola/kind di
   Studio tanpa pernah melewati layar lain. `WidgetDemandGateCard` menutup jarak itu.
2. **Informatif, bukan toggle.** Godaan berikutnya adalah "klik kandidat → widget baru dibuat".
   Salah: kosakata `WidgetKind` tertutup karena renderer harus bisa menggambar setiap kind di
   semua vertikal (Uji Variabilitas) — menambah kind = mengubah kode, dan kandidat ("gigi")
   tidak memberi tahu kind apa. Kartu memberi **bukti** untuk keputusan kode, bukan penggantinya.
3. **Angkat sejak pemakaian kedua.** Parsing kandidat + kartunya langsung dipindah ke
   `DemandSignals.kt` (`internal`, satu package) yang dipakai Buku Demand dan kartu gerbang —
   menyalin markup identik ke layar kedua adalah ulang penyakit Aturan Tiga Kali yang sama.
   `DemandSignalsTest` (3) mengunci parsingnya: fail-loud untuk respons bukan objek, fallback
   ambang ke `DemandLedger.RULE_OF_THREE`, urutan kandidat dari server tidak diacak ulang.

**Pelajaran proses**: menyambung composable dengan `insert_line` di atas 6000 karakter memecah
file bertahap dan sekali membuat kurung penutup bergeser — kompilasi menangkapnya, tapi tiga
putaran perbaikan bisa dihindari bila file baru ditulis utuh sejak awal, bukan dirakit dari
tiga sisipan.

## Irisan keempat (2026-09-30): autosave narasi & antrean kandidat terbatas

Dua sisa Fase E ditutup sekaligus:

1. **Autosave di tepi klien, bukan endpoint baru.** Cerita yang belum dikirim tidak punya rumah di
   server (draf baru ada setelah `POST /drafts`), dan menambah endpoint "narasi sementara" berarti
   menambah state server untuk data yang pemiliknya belum punya akun pun. `PlatformLocalStorage`
   (sudah multiplatform: localStorage / NSUserDefaults / in-memory) cukup: disimpan per ketikan,
   dipulihkan saat layar dibuka. Kebijakan pembersihannya yang penting: simpanan dihapus saat draf
   **berhasil dibuat** — narasi kini hidup di buku demand, dan dua salinan hidup adalah resep data
   bertentangan. Saat resume sesi lama, autosave ikut isi textarea (sesi yang dilanjutkan kini
   pemilik ceritanya). Kejujuran platform: di Desktop/JVM penyimpanannya in-memory, jadi autosave
   di sana hanya melindungi ganti layar — itu kemampuan platformnya, ditulis di KDoc.
2. **Antrean kandidat dibatasi puncaknya.** Kartu gerbang di tengah perancang menampilkan 3
   kandidat teratas; sisanya satu baris rujukan "…dan N kandidat lagi — antrean penuhnya di Buku
   Demand". Layar penuh tetap jadi tugas Buku Demand; perancang tidak ikut menumpuk.

Verifikasi dengan mata (superadmin, :3001): narasi+hint diketik → localStorage terisi per ketikan →
reload → teks kembali utuh di textarea → "Susun Draf Sistem" → draf terbentuk, kedua kunci lokal
`null`. Kartu gerbang tetap normal (1 kandidat, di bawah batas). Compilasi JVM/WasmJs/JS hijau,
`jvmTest` hijau; Android tetap gagal pra-ada (SDK location), bukan karena perubahan ini.

## Tantangan mandiri (sisa)

1. Turunkan ambang Rule of Three menjadi per-vertikal? Uji dulu dengan Uji Variabilitas — ambang
   adalah konsep platform atau data tenant?
2. Demand dari agent LLM vs deterministik: apakah `agent_ref` cukup untuk membandingkan kualitas
   keduanya, atau perlu skor cakupan per demand?
