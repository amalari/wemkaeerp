# Teaching — Discovery E1–E3: Sesi Persisten & Buku Demand (Fase E)

> Plan: [`docs/plannings/PLAN-discovery-blueprint-prototype-studio.md`](../plannings/PLAN-discovery-blueprint-prototype-studio.md) §6 · Tanggal: 2026-09-30
> Kode: `core/.../domain/discovery/DiscoveryDemand.kt`, `server/.../routes/DiscoveryRoutes.kt`,
> `server/src/main/resources/db/migration/V80__discovery_demands.sql`,
> `app/shared/.../presentation/discovery/DiscoveryWizardScreen.kt` (+`DiscoveryWizardSteps.kt`),
> `app/shared/.../presentation/discovery/studio/DemandLedgerScreen.kt` (irisan kedua)

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

## Tantangan mandiri (sisa)

1. Turunkan ambang Rule of Three menjadi per-vertikal? Uji dulu dengan Uji Variabilitas — ambang
   adalah konsep platform atau data tenant?
2. Demand dari agent LLM vs deterministik: apakah `agent_ref` cukup untuk membandingkan kualitas
   keduanya, atau perlu skor cakupan per demand?
3. Hubungkan kandidat Rule of Three ke keputusan widget Studio (saat ini hanya melapor di layar).
4. Autosave narasi saat mengetik — resume sudah memulihkan teks, tapi tab yang tertutup sebelum
   "Susun Draf" masih kehilangan cerita.
