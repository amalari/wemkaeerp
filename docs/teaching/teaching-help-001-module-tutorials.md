# 🎓 Modul Pembelajaran: Tutorial Modul dengan Coach Mark (TRD-HELP-001 Fase 1)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Katalog berbasis data per Domain Pack, RBAC fail-closed di sisi baca, koordinat layout Compose, overlay spotlight
> **Prasyarat**: `ModuleId` dan `DomainPack` (TRD-PLAT-001), `AccessDecision`, dasar `Modifier` Compose
> **Referensi Task**: [`docs/trd/TRD-HELP-001-module-tutorials.md`](../trd/TRD-HELP-001-module-tutorials.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: Staf baru membuka CRM dan tidak tahu harus mulai dari mana. Dokumentasi di luar aplikasi (PDF, video) cepat basi dan tidak menunjuk tombol yang sebenarnya.
- **Analogi**: Coach mark itu seperti pemandu museum yang menyorotkan senter ke satu lukisan, sementara ruangan lain diredupkan. Senternya tidak menggambar ulang lukisan. Ia hanya perlu tahu **di mana** lukisan itu tergantung.
- **Hasil akhir**:
  - Tombol ❓ di top bar membuka daftar panduan yang sudah disaring hak akses.
  - Memilih panduan akan meredupkan layar, menyorot elemen asli, dan menampilkan kartu langkah dengan tombol Kembali/Lanjut/Selesai.
  - Kalau langkahnya ada di modul lain, aplikasi pindah layar dulu.
  - Fase berikutnya, AI chat helper cukup memanggil `TutorialController.start(...)`.

---

## 🧭 2. "Start dari Mana?"

1. **Langkah 0: Uji Variabilitas dulu, bukan UI.**
   Pertanyaannya: apakah isi tutorial berbeda per industri? Ya, karena tutorial CRM konveksi tidak ada artinya di pack e-learning. Jadi isinya menjadi **data per pack**, bukan `when(module)` di layar.
2. **Langkah 1: Value object.**
   `TutorialId`, `TutorialAnchorId`, `SurfaceCode` divalidasi di konstruktor. Satu-satunya enum, `CalloutPlacement`, adalah konsep teknis UI, dan alasannya ditulis di KDoc.
3. **Langkah 2: Entity dan scope.**
   `ModuleTutorial` punya `scope: TutorialScope`. Scope ini sealed dengan dua jenis:
   - `Module`: gerbangnya RBAC modul.
   - `Surface`: gerbangnya izin layar, misalnya Builder dengan `MANAGE_BUILDER`.
4. **Langkah 3: Kontrak sumber.**
   `TutorialSource` di mesin, implementasinya `ShippedTutorialSource` di paket `pack`. Mesin tidak pernah menyebut garment.
5. **Langkah 4: Aturan baca.**
   `TutorialAccess.accessible(...)` bersifat fail-closed: tanpa keputusan RBAC berarti tersembunyi.
6. **Langkah 5: Server.**
   Belum ada di Fase 1, karena tutorial dikirim bersama rilis. Baru dibutuhkan di Fase 2 (`/help/ask`).
7. **Langkah 6: UI.**
   Urutannya: anchor, lalu controller, lalu overlay, lalu layer, lalu dipasang di `App.kt`.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Scope menentukan gerbang

```kotlin
is TutorialScope.Module -> decisions[s.moduleId]?.config?.level?.isAtLeast(requiredLevel) == true
is TutorialScope.Surface -> s.code in allowedSurfaces
```

- `== true` sengaja ditulis begitu. `null` (tidak ada keputusan) jatuh ke `false`. Inilah arti fail-closed di sisi baca.
- Kenapa perlu menyembunyikan tutorial? Karena tutorial untuk modul yang tidak di-entitle tetap membocorkan bahwa modul itu ada. Nanti AI juga hanya diberi daftar yang sudah disaring.

### Blok B: Katalog = platform + pack, dan invarian yang bisa dites

```kotlin
fun forPack(pack: DomainPack) = PlatformTutorials.all.filter { it.appliesTo(pack) } + source.tutorialsFor(pack.code)
fun violations(pack: DomainPack): List<String>   // id ganda, modul/layar tak ada di pack, anchor tak terdaftar
```

- Tutorial platform (`org_chart`, `dynamic_rbac`) hanya ikut bila pack itu memang memakai modulnya. Test e-learning membuktikan hanya `platform_org_chart_basics` yang lolos.
- `violations` membuat anchor yatim gagal di CI, bukan ditemukan user.

### Blok C: Anchor, satu modifier yang melaporkan posisinya

```kotlin
fun Modifier.tutorialAnchor(id: TutorialAnchorId): Modifier = composed {
    val registry = LocalTutorialAnchors.current ?: return@composed Modifier
    ...
    Modifier.bringIntoViewRequester(requester)
        .onGloballyPositioned { if (it.isAttached) registry.update(id, it.boundsInWindow()) }
}
```

- Layar fitur tidak tahu ada tutorial. Ia hanya menempelkan penanda. Di preview atau test, `LocalTutorialAnchors` bernilai `null`, jadi modifier ini tidak melakukan apa pun.
- Posisi disimpan dalam **koordinat window**. Overlay lalu mengurangi posisinya sendiri (`translate(-origin)`), sehingga benar di mana pun overlay dipasang.
- Registry memakai `mutableStateMapOf`. Kalau layout bergeser, sorotan ikut bergeser.

### Blok D: Controller murni, orkestrasi di layer

- `TutorialController` hanya mengurus urutan langkah (next/back/dismiss, `start` dijepit ke rentang yang valid). Karena tanpa Compose, ia bisa diuji di `commonTest`.
- `TutorialLayer` menjalankan efek:
  1. Navigasi ke `step.screen`.
  2. Tunggu anchor maksimal 1,5 detik (`snapshotFlow { … }.first { it != null }` di dalam `withTimeoutOrNull`).
  3. `bringIntoView`.
  4. Tampilkan overlay.

  Kalau anchor tidak muncul, callout tampil di tengah. Tutorial tidak pernah macet.
- Saat wewenang berubah (ganti persona) dan tutorial tidak lagi boleh dibaca, tutorial otomatis berhenti.

### Blok E: Spotlight scrim (design system)

```kotlin
val path = Path().apply { fillType = PathFillType.EvenOdd; addRect(full); addRoundRect(hole) }
```

- Dengan EvenOdd, persegi penuh dikurangi persegi berlubang, sehingga terbentuk lubang.
- Komponennya buta domain: hanya menerima `Rect?` dan `Color` (design-system Kontrak 6). Warna diambil dari token `WeMadeColors.Scrim` dan `Accent`.

---

## ⚖️ 4. The "Why"

| Pendekatan | Alternatif | Kenapa ini | Risiko alternatif |
|---|---|---|---|
| Anchor = konstanta + test invarian | Mencari elemen lewat teks tombol | Tahan terhadap pergantian label dan bahasa | Tutorial diam-diam rusak begitu label berubah |
| Data per pack (`ShippedTutorialSource`) | Field tutorial di `ModuleDefinition` | Modul bersama wajib identik lintas pack (`DomainPack.kt:50-57`) | Pack e-learning ditolak saat mendaftar |
| Tidak disimpan di codec pack | Menambah field ke `DomainPackCodec` | Codec ketat, versi pack terkunci; tutorial mengikuti UI rilis | Tutorial lama terkunci di pack versi lama padahal UI sudah berubah |
| Scrim menelan klik | Klik tembus ke lubang | Sederhana dan pasti di 5 target | Klik tembus berperilaku beda di Wasm/Android dan bisa membuka dialog di tengah langkah |

---

## ⚠️ 5. Jebakan Pemula

1. **Menulis `Modifier.tutorialAnchor(TutorialAnchorId("crm.x"))` langsung di layar.**
   Test katalog tidak bisa melihat string itu. Selalu pakai `GarmentTutorialAnchors.*`.
2. **Memakai `boundsInRoot` lalu memasang overlay di tempat lain.**
   Koordinatnya meleset sejauh offset top bar. Pakai koordinat window dan kurangi origin overlay.
3. **Menyorot elemen di dalam `Dialog`.**
   Dialog berada di window lain, jadi koordinatnya tidak cocok. Pakai langkah `CENTER` tanpa anchor (lihat langkah 3 "Isi data pembeli").
4. **Menambah tutorial tanpa `requiredLevel` yang tepat.**
   Tutorial "tambah lead" dengan level VIEW akan mengajari orang melakukan aksi yang tombolnya tidak mereka punya.
5. **Isi tutorial tidak dicocokkan dengan layar nyata.**
   Ini terjadi di task ini: draf RBAC menyebut "daftar kiri", padahal layarnya memakai tab Per Modul/Divisi/Jabatan. Ketahuan hanya karena dicek dengan mata.

---

## 🧪 6. Membuktikan Kodenya Bekerja

| Test | Jumlah | Isi |
|---|---|---|
| `core/.../tutorial/TutorialCatalogTest` | 4 | Katalog garment tanpa pelanggaran; pack e-learning (tenant kedua) hanya mendapat tutorial `org_chart`; anchor atau modul fiktif dilaporkan |
| `core/.../tutorial/TutorialAccessTest` | 5 | Tanpa keputusan berarti kosong; VIEW hanya melihat `crm_find_lead`; OPERATE melihat ketiga tutorial CRM; NONE tersembunyi; `Surface` butuh izin |
| `app/shared/.../tutorial/TutorialControllerTest` | 5 | Urutan langkah; callout dijepit di dalam layar; tanpa target berarti di tengah |

Cek visual di `wemade-demo` (superadmin, 1280×800):
- Daftar panduan terbelah jadi "Untuk layar ini" dan "Panduan lain".
- Keempat langkah CRM tersorot tepat.
- Tutorial RBAC yang dimulai dari CRM berpindah ke `/rbac` dengan callout di tengah.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Tambah anchor + tutorial untuk Sampling (`SamplingWorkspaceScreen`). Pastikan `TutorialCatalogTest` merah dulu, lalu hijau setelah anchor didaftarkan.
- [ ] **Tantangan 2**: Callout `TOP` untuk elemen di dekat tepi atas saat ini hanya dijepit ke tepi. Buat `calloutPosition` membalik ke `BOTTOM` bila tidak muat, lalu tulis test-nya.
- [ ] **Tantangan 3**: Tambah `Surface(builder)` dan kirim `allowedSurfaces` dari izin `MANAGE_BUILDER` (persiapan Fase 6).

---

# Bagian 2 — Fase 2: Pencocok Pertanyaan & `POST /api/tenant/help/ask`

## 💡 Masalahnya

User bertanya "ada buyer baru chat WA, dicatat di mana?". Aplikasi harus menjawab dengan tutorial yang benar, dan **tidak boleh** menyarankan tutorial untuk modul yang tidak boleh dibuka user itu. Kalau pertanyaannya "gimana cara atur hak akses?" datang dari operator QC, jawaban yang benar adalah "tidak ada panduan untuk Anda". Menyebut bahwa tutorial RBAC ada pun sudah termasuk bocor.

## 🧭 Urutan penulisan

1. **Port dulu**: `TutorialMatcher` dan `HelpAgent`. Keduanya antarmuka, supaya Fase 3 (LLM) cukup mengganti implementasi.
2. **Implementasi deterministik**: `LexicalTutorialMatcher` dan `DeterministicHelpAgent`. Keduanya berjalan tanpa jaringan, jadi bisa dites dan selalu tersedia sebagai fallback.
3. **Use case** `AskHelpUseCase`: validasi, lalu **saring wewenang**, lalu cocokkan, lalu agent, lalu validasi jawaban agent.
4. **Codec bersama** `HelpCodec` di `core/shared`, dipakai server dan klien supaya format kabelnya tidak bisa berbeda.
5. **Route**, lalu **daftarkan di `RouteGateLedger.openByDesign`**, lalu klien.

## 🧱 Blok penting

### Urutan di use case adalah fitur keamanan

```kotlin
val visible = TutorialAccess.accessible(catalog.forPack(command.pack), command.decisions, command.allowedSurfaces)
val candidates = matcher.rank(question, visible, command.currentModule)
```

Penyaringan dilakukan **sebelum** pencocokan, bukan sesudahnya. Di Fase 3 kandidat dikirim ke LLM. Kalau penyaringan terjadi sesudah LLM menjawab, LLM sudah sempat membaca isi tutorial terlarang dan bisa mengutipnya di teks jawaban.

### Jawaban agent harus berpijak pada kandidat

```kotlin
val answer = agent.answer(helpQuestion).getOrNull()?.takeIf { it.isGroundedIn(candidates) }
    ?: fallback.answer(helpQuestion).getOrThrow()
```

Kalau agent menyebut `tutorialId` di luar kandidat, atau `stepIndex` di luar rentang, **seluruh** jawabannya dibuang dan diganti jawaban deterministik. Jangan hanya membuang id-nya: teks jawabannya kemungkinan menjelaskan tutorial fiktif itu.

### Kenapa route ini tidak memakai `requireModuleAccess`

Gerbangnya per tutorial, bukan per modul. Route mengambil `callerDecisions(...)`, yaitu jalur yang **sama** dengan gerbang modul dan menu `me/access`, lalu menyerahkannya ke use case. Karena itu `RouteGateTest` harus diberi tahu lewat `RouteGateLedger.openByDesign`, disertai komentar alasannya. Tanpa entri itu, test ratchet akan merah, dan memang begitu seharusnya.

### Pencocok leksikal

- Bobot: contoh pertanyaan dan kata kunci 3, judul 2, ringkasan dan judul langkah 1.
- Bonus modul aktif +1 **hanya bila sudah cocok**. Tanpa syarat ini, pertanyaan "resep nasi goreng" di layar CRM akan dijawab tutorial CRM.
- Pemotong akhiran ringan (`-nya`, `-kan`, `-lah`) cukup untuk "leadnya" dan "tambahkan". Ini bukan stemmer lengkap, dan sengaja dibuat begitu.

## ⚠️ Jebakan

1. **Mencatat isi pertanyaan di log.** Pertanyaan bisa memuat nama dan nomor WA pelanggan. Log hanya mencatat `agentRef`, ada tidaknya saran, dan jumlah alternatif.
2. **Menganggap `currentModule` dari klien bisa dipercaya.** Route membuang modul yang tidak ada di pack tenant. Modul itu hanya dipakai untuk bonus peringkat, tidak pernah untuk akses.
3. **Test hanya dengan owner.** Owner melewati matriks, jadi test semacam itu tidak membuktikan penyaringan. `HelpApiTest` memakai peran QC tanpa akses CRM.

## 🧪 Bukti

| Test | Jumlah | Isi |
|---|---|---|
| `LexicalTutorialMatcherTest` | 4 | 5 pertanyaan sehari-hari memetakan ke tutorial yang tepat; pertanyaan tak berhubungan kosong; `stepIndex` menunjuk langkah "Cari"; bonus tidak menciptakan kecocokan |
| `AskHelpUseCaseTest` | 8 | Agent tidak pernah melihat tutorial di luar wewenang; id halusinasi, langkah di luar rentang, dan agent gagal semuanya jatuh ke fallback; pack e-learning tidak mendapat tutorial CRM |
| `HelpCodecTest` | 3 | Round-trip; modul atau id yang tidak valid dilewati, bukan ditebak |
| `HelpApiTest` | 4 | Sales mendapat tutorial CRM; QC tidak pernah mendapat tutorial CRM; owner mendapat RBAC; 401 dan 400 |
| `RouteGateTest` | 1 | Probe sudah mencakup `POST /api/tenant/help/ask` |

## 🏆 Tantangan

- [ ] Tambah sinonim ("bikin" = "buat", "customer" = "pelanggan") sebagai data di tutorial, bukan kamus di mesin. Kenapa di tutorial?
- [ ] Tulis test yang gagal bila seseorang memindahkan `TutorialAccess.accessible` ke **setelah** `matcher.rank`.

---

# Bagian 3 — Fase 3: Jawaban LLM dengan Koog + DeepSeek

## 💡 Mental model: LLM itu *pemilih*, bukan *pencari*

LLM tidak diberi seluruh katalog, dan tidak diberi alat untuk mencari. LLM hanya menerima **maksimal 5 kandidat** yang sudah lolos wewenang dan sudah diperingkat pencocok leksikal. Tugasnya tinggal dua:

1. Merangkai jawaban manusiawi **dari isi kandidat**.
2. Memilih satu `tutorialId` + `stepIndex`.

Analogi: pustakawan (pencocok) mengambil 5 buku dari rak yang boleh kamu baca, lalu pemandu (LLM) membacakan bagian yang relevan. Pemandu tidak pernah masuk gudang.

## 🧭 Urutan penulisan

1. `KoogHelpPrompt`: sistem prompt, lalu pesan pengguna berisi kandidat (id, judul, ringkasan, langkah bernomor).
2. `KoogHelpAgent`: satu run Koog **tanpa alat**, parse JSON, validasi, maksimal 2 putaran (1 koreksi).
3. `HelpAgents.fromEnv()`: kill-switch `HELP_AGENT=koog|deterministic`.
4. Pasang di `ServerRouteWiring`, lalu test dengan `ScriptedPromptExecutor` (dipakai ulang dari discovery), lalu live evals opt-in.

## 🧱 Blok penting

### Tanpa kandidat, LLM tidak dipanggil

```kotlin
if (question.candidates.isEmpty()) return@runCatching HelpAnswer(DeterministicHelpAgent.NO_MATCH, null, null, agentRef)
```

Tidak ada yang bisa dijawab dengan jujur, jadi tidak perlu ada biaya. Ini juga menutup celah halusinasi terbesar: LLM yang dipaksa menjawab tanpa bahan akan mengarang menu.

### Koreksi satu kali, lalu serahkan ke use case

Jawaban yang tidak sah (bukan JSON, id di luar kandidat, langkah di luar rentang) dikirim balik sekali sebagai umpan balik. Kalau masih gagal, agent mengembalikan `Result` gagal. **Agent tidak memasang fallback sendiri**: `AskHelpUseCase` sudah punya aturan "berpijak pada kandidat" dan fallback deterministik. Satu aturan cukup ditulis di satu tempat.

### Prompt injection ringan

- Pertanyaan dibungkus `<pertanyaan>…</pertanyaan>`.
- `<` dari pengguna diganti `‹`, sehingga user tidak bisa menutup tag lalu menulis "abaikan aturan".
- Sistem prompt menyatakan isi tag adalah data, bukan instruksi.

Ini bukan pertahanan sempurna. Pertahanan sebenarnya ada di arsitektur: LLM hanya melihat kandidat yang boleh dilihat, dan id jawabannya divalidasi ulang.

## ⚖️ Kenapa tanpa alat (tool)?

| Pendekatan | Kelebihan | Kenapa tidak dipilih |
|---|---|---|
| Alat `search_tutorials` untuk LLM | LLM bisa mencari ulang | Lebih banyak langkah, jadi latensi dan biaya naik. Alat juga bisa lupa disaring wewenangnya |
| **Kandidat di prompt (dipilih)** | 1 panggilan, biaya bisa dihitung, wewenang disaring sebelum LLM | Recall bergantung pada pencocok leksikal, dan itu dimitigasi dengan `sampleQuestions`/`keywords` di data |

## ⚠️ Jebakan

1. **Menyalakan `HELP_AGENT=koog` di `.env` dev lalu menjalankan `:server:test`.** `HelpApiTest` mengharapkan `deterministic/help-v1`. Biarkan `.env` dev tetap `deterministic`, dan pakai live evals untuk mengukur LLM.
2. **Menilai eval hanya dari tutorial yang dipilih.** Tanpa cek `agentRef`, fallback deterministik bisa "lulus" menggantikan LLM yang gagal. Eval di sini memeriksa keduanya.

## 🧪 Bukti

| Test | Jumlah | Isi |
|---|---|---|
| `KoogHelpAgentTest` | 6 | JSON dalam pagar kode diterima; id halusinasi mendapat umpan balik lalu dikoreksi; dua putaran buruk jatuh ke deterministik lewat use case; tanpa kandidat berarti 0 panggilan; prompt tidak memuat kandidat di luar yang diberikan dan tag penutup dari user dinetralkan; seleksi env |
| `KoogHelpLiveEvalsTest` (opt-in, 2026-09-30) | 3 | 3/3 PASS dengan `koog/deepseek-flash/help-v1`, termasuk pertanyaan RBAC yang dijawab dengan langkah nyata ("+ Tambahkan Akses") |

## 🏆 Tantangan

- [ ] Tambah kasus eval "resep nasi goreng" yang harus menghasilkan `suggestion = null` **dan** 0 panggilan LLM.
- [ ] Ukur latensi p95 live evals dan bandingkan dengan target NFR (< 6 detik).

---

# Bagian 4 — Fase 4: Panel Chat & "Mulai tutorial"

## 💡 Masalahnya

Jawaban AI yang hanya berupa teks masih membuat user mencari tombolnya sendiri. Fase ini menutup lingkarannya: satu klik "Mulai tutorial" di gelembung chat langsung menjalankan coach mark **di langkah yang dipilih AI**, termasuk berpindah layar bila perlu.

## 🧭 Urutan penulisan

1. **Kontrak klien**: `HelpGateway` (antarmuka) di atas `HelpApiClient`, supaya ViewModel bisa diuji dengan gateway palsu.
2. **MVI**: `HelpChatUiState`, `HelpChatUiEvent`, `HelpChatUiEffect`.
3. **`HelpChatViewModel`**: kirim, simpan riwayat sesi, dan pancarkan efek `StartTutorial`.
4. **UI**: `HelpMessageBubble`, lalu `HelpChatPanel`, lalu shell `HelpSheet` dengan tab Panduan / Tanya AI.
5. **Sambungan** di `TutorialLayer`: efek diterjemahkan ke `TutorialController.start(tutorial, step)`.

## 🧱 Blok penting

### ViewModel chat tidak tahu apa-apa soal coach mark

```kotlin
is HelpChatUiEvent.StartSuggestion -> _effects.trySend(HelpChatUiEffect.StartTutorial(event.suggestion))
```

Efek (sekali jalan) dipakai, bukan state, karena "mulai tutorial" adalah perintah, bukan kondisi layar. `TutorialLayer` yang menerjemahkannya:

```kotlin
latestAccessible.firstOrNull { it.id == effect.suggestion.tutorialId }?.let { state.controller.start(it, effect.suggestion.stepIndex) }
```

Id dicari di katalog klien yang **sudah disaring wewenang yang sama**. Id yang tidak dikenal (klien lebih lama dari server) diabaikan, bukan ditebak.

### Gagal kirim tidak menggandakan riwayat

Saat gagal, pesan pengguna dihapus dari riwayat dan dikembalikan ke kotak input. Tanpa ini, tombol Kirim ulang akan menampilkan pertanyaan yang sama dua kali.

### Riwayat bertahan selama sesi

`HelpChatViewModel` dibuat sekali di `rememberTutorialUiState()`, jadi menutup lalu membuka jendela Bantuan tidak menghapus percakapan. Riwayat persisten sengaja di luar cakupan (Non-Goal).

## 🔎 Temuan dari cek visual (dan kenapa cek mata itu wajib)

Pertanyaan "ada buyer baru chat WA, dicatat di mana?" menampilkan alternatif **"Menyusun struktur organisasi"**. Penyebabnya: satu kata "baru" cocok dengan contoh pertanyaan "cara bikin divisi baru". Semua test hijau, tapi hasilnya jelas derau bagi user.

Perbaikannya ada di `LexicalTutorialMatcher`: kandidat dengan skor kurang dari **separuh skor teratas** dibuang. Perbaikan ini dikunci test `weakSingleWordMatches_areNotOfferedAsAlternatives`.

## ⚠️ Jebakan

1. **Mengumpulkan efek dengan lambda yang menangkap `accessible` lama.** Gunakan `rememberUpdatedState`, karena `LaunchedEffect(state)` hidup lebih lama dari satu rekomposisi.
2. **Menjalankan dev server di port yang sudah dipakai sesi lain.** Pengecekan "server siap" bisa lolos karena mengenai server **orang lain** dengan kode lama. Repo ini punya `WEMADE_WEB_PORT` / `WEMADE_API_PORT` dan `PORT` untuk menjalankan instance kedua (misalnya 3011 → 8091).

## 🧪 Bukti

| Bukti | Isi |
|---|---|
| `HelpChatViewModelTest` (5, fixture modul e-learning) | Kirim menyertakan modul aktif; alternatif dibatasi 2; draf kosong tidak dikirim; gagal mengembalikan draf tanpa menggandakan riwayat; saran menjadi efek; draf dibatasi 500 karakter |
| Cek visual `wemade-demo` dengan `HELP_AGENT=koog` (2026-09-30) | Tanya "ada buyer baru chat WA, dicatat di mana?" → DeepSeek menjawab dari isi tutorial → "Mulai tutorial: Mencatat lead baru" → coach mark langsung di langkah 2/4 menyorot "+ Tambah Lead" |

## 🏆 Tantangan

- [ ] Tekan Enter untuk mengirim (`KeyboardActions`). Pastikan jalan di Wasm dan Desktop.
- [ ] Tampilkan "Tutorial ini tidak tersedia di versi aplikasi Anda" bila id saran tidak ditemukan di katalog klien.
