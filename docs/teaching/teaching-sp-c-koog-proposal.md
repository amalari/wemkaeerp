# 🎓 Modul Pembelajaran: Agent Koog Menghasilkan `ScreenProposal` — LLM di Tali Kekang (SP-C0 s/d C5)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: contoh prompt dari kode (bukan teks tempel), provenance dibubuhkan server, galat berpath sebagai siklus koreksi, umpan balik berbatas, katalog kosakata untuk LLM, grader eval yang dites sendiri, eval live opt-in dengan gerbang biaya
> **Prasyarat**: paham kontrak `ScreenProposal` + validator tunggal (lihat `teaching-sp-b-screen-proposal-contract.md`), paham pola `ScriptedPromptExecutor` untuk test tanpa jaringan
> **Referensi Task**: `docs/plannings/parallel3/PLAN-sp-C-koog.md` (induk: `PLAN-screen-proposal-contract-koog.md`) — butir C0–C5

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Setelah B0 menyiapkan kontrak `ScreenProposal`, tugas jalur C adalah membuat agent Koog (LLM) **mengisi proposal itu** di dalam dokumen draf — tanpa kehilangan kendali. LLM punya tiga kecenderungan berbahaya di sini:

1. **Mengarang bentuk** — kunci `view` yang tidak ada, tipe field "KARANGAN", `source` yang bohong.
2. **Membengkak** — proposal kaya berarti keluaran JSON 2–3× lebih panjang; 3 putaran koreksi melipatgandakan biaya.
3. **Menurun kualitasnya diam-diam** — kalau validator menolak dengan pesan kabur, model tidak bisa memperbaiki, lalu orang me-longgar-kan validator. Lingkaran setan.

**Analogi.** LLM itu penerjamah lepas yang rajin tapi kadang mengarang istilah. Kita tidak memercayainya: kita beri **contoh dokumen yang dijamin sah** (dicetak dari formulir resmi), **kamus istilah** yang boleh dipakai, **pemeriksa** yang menunjuk baris yang salah, dan **stempel pengenal** yang tebakannya tidak berpengaruh karena dibubuhkan di loket.

**Hasil akhir (arsitektur C):**
```
narasi ─► prompt (contoh dari kode + aturan 1–14) ─► Koog (3 alat) ─► decode ketat
                                                        │              ├─ stamp sumber AGENT (server!)
                                                        │              └─ validator berpath
                                              galat berpath (≤12, terpotong)   │ lolos
                                                        ▼                      ▼
                                              putaran berikut (maks 3)   draf sah → prototype
                                                        │ gagal total
                                                        ▼
                                             fallback deterministik (B3: kini berlayar)
```

---

## 🧭 2. Keputusan per Butir (dan Alasannya)

1. **C0 — tulis dulu, kode belakangan.** `docs/plannings/discovery-SP-koog-baseline.md` mencatat bentuk persis galat berpath, angka batas (3 putaran × 24 langkah, jawaban lama ≤6.000 karakter), dan risiko `proposal`. Mendokumentasikan sebelum mengubah = keputusan punya jejak, bukan legenda lisan.
2. **C1 — contoh dari kode.** `exampleDraft(agentRef)` merakit `ScreenProposal` sungguhan (TABLE + FORM berbagi satu entity) lalu meng-encode-nya `DiscoveryDraftCodec`. Konsekuensinya ganda: contoh **mustahil melenceng dari kontrak** (test mewajibkannya lolos validator penuh), dan contoh **tidak membocorkan jawaban** (pack `contoh`, kosakata netral).
3. **C1 — versi naik: `koog/<model>/draft-v2`.** Bentuk keluaran berubah → versi berubah. Draf lama tetap terbaca karena `proposal` bersifat tambatif.
4. **C2 — `screen_catalog()`.** LLM tidak boleh menebak kosakata. Alat ini menjawab jenis widget + bentuk `view`-nya, tipe field, gaya kartu, dan **angka batas yang diambil dari `ProposalLimits`** — satu sumber kebenaran dengan validator, jadi prompt tidak mungkin bergeser dari aturan produksi. Petunjuk peran→tampilan diambil dari `pack.screenSuggestions` bawaan dan **diberi label petunjuk, bukan aturan**.
5. **C3 — sumber dibubuhkan server.** Lihat blok kode di §3. Model yang menulis `"kind":"PACK"` atau lupa `source` tidak mengubah apa pun.
6. **C3 — umpan balik berbatas.** 12 galat teratas, tiap pesan ≤240 karakter, sisanya dirangkum "(+N galat lain)". Galat ke-13 biasanya akibat berantai dari yang pertama; umpan balik yang membengkak justru membingungkan model.
7. **C4 — grader dites sendiri.** Enam kriteria §6 jadi `DiscoveryEvalGrader`; tiap kriteria punya kasus buatan tangan yang membuktikan ia **menuduh yang salah dan meloloskan yang benar**. Baseline deterministik wajib 100% — setelah B3 merge, layarnya dinilai nyata (11/11 PASS).
8. **C5 — eval live tiga gerbang.** `DISCOVERY_LIVE_EVALS=1` + kunci API + **`DISCOVERY_LIVE_EVALS_CONFIRM=yes`** (konfirmasi biaya — perkiraan kasus × ulangan × putaran dicetak di pesan penolakan). Laporan ditulis `docs/plannings/eval-SP-koog-<tanggal>.md`; keputusan G3 dibiarkan kosong untuk koordinator.

---

## 🧱 3. Bedah Blok Kode

### Blok A — provenance dibubuhkan server, bukan dipercaya ke model
```kotlin
internal fun stampAgentProvenance(draft: DiscoveryDraft, agentRef: String): DiscoveryDraft =
    if (draft.screens.none { it.proposal != null }) draft
    else draft.copy(screens = draft.screens.map { s ->
        if (s.proposal == null) s else s.copy(source = ProposalSource.Agent(agentRef))
    })
```
Dipanggil **sebelum** validasi: model tidak membuang satu putaran koreksi hanya untuk menyalin `source` yang sebenarnya diketahui server, dan jejak audit selalu menyebut `agentRef` yang benar. Layar tanpa proposal (draf gaya lama) dibiarkan apa adanya — kompatibel mundur.

### Blok B — contoh prompt yang tidak bisa basi
```kotlin
fun exampleDraftJson(agentRef: String = EXAMPLE_AGENT_REF): String =
    DiscoveryDraftCodec.encodeToString(exampleDraft(agentRef))
```
`exampleDraft()` memakai tipe kontrak (`EntityProposal`, `ViewProposal.Table`, …) — bukan string JSON tempelan. Kalau kontrak berubah, kode ini **gagal kompilasi** atau **gagal test**, bukan diam-diam mengajari model bentuk yang salah.

### Blok C — batas yang dibaca dari sumber kebenaran
```kotlin
"limits" to jsonObjectOf(
    "fields" to jsonOf(ProposalLimits.FIELDS),
    "seedRows" to jsonOf(ProposalLimits.SEED_ROWS), …
)
```
Katalog alat tidak menuliskan "12" sendiri; ia membaca `ProposalLimits`. Angka berubah di validator → katalog ikut tanpa disentuh.

### Blok D — kriteria yang jujur ketika kosong
```kotlin
if (screens.isEmpty()) return listOf(
    CriterionResult("jenis_tampilan", true, "tidak ada layar"), …
)
```
Kriteria isi layar hanya dinilai **bila layar ada**. Menuntut layar dari draf yang memang belum punya = grader yang rusak (plan §6: "kalau deterministik gagal, penilainya yang rusak"). Begitu B3 merge, kriteria yang sama menilai layar sungguhan **tanpa satu baris grader berubah** — itulah gunanya mendesain kriteria berdasar "apa yang ada", bukan "apa yang diharapkan".

### Blok E — gerbang biaya
```kotlin
assumeTrue(
    "Konfirmasi biaya belum diberikan. " + DiscoveryLiveEvalReport.costEstimate(...) +
        ". Set DISCOVERY_LIVE_EVALS_CONFIRM=yes untuk menjalankan.",
    System.getenv("DISCOVERY_LIVE_EVALS_CONFIRM") == "yes"
)
```
Perkiraan biaya dicetak **bahkan saat menolak jalan** — pelari melihat angkanya, lalu memutuskan. Token nyata dibaca dari `Message.Assistant.metaInfo.totalTokensCount`; provider yang tidak menyebutnya dilaporkan `"-"`, bukan 0 palsu.

---

## 🪤 4. Jebakan yang Benar-Benar Terjadi (dan Pelajarannya)

| Jebakan | Gejala | Pelajaran |
|---|---|---|
| `JsonValue.Obj.obj()` itu **nullable** | kompilasi test gagal 15 titik | Pola repo: `requireNotNull(root.obj("blueprint"))` — ikuti, jangan pakai `!!` |
| Cap panjang baris kena contoh dokumen | test "umpan balik terpotong" gagal padahal capnya bekerja | Contoh JSON memang satu baris panjang; cap ditujukan ke **baris galat** (`- $.…`) saja — tulis asersi sesuai yang diukur |
| Kata "servis" di narasi jasa/IT | baseline menghasilkan pack `bengkel`, bukan `kustom` | Kata kunci deterministik membaca narasi apa adanya; narasi kasus emas harus sadar-kata-kunci (uji dulu, baru janji angka) |
| `main` bergerak saat kita bekerja | B3+B4+B5 merge di tengah jalan; test yang berasumsi "baseline belum berlayar" retak | Sinkron ulang cepat: stash → `merge --ff-only` → pop; test yang premisnya soal kondisi dunia luar wajib ditinjau ulang, bukan dipaksakan |
| Asersi kesetaraan draf vs JSON deterministik | gagal karena `source` dibubuhkan `Agent` | Itu **perilaku yang diinginkan**; ekspektasi test diubah lewat fungsi yang sama dengan produksi (`stampAgentProvenance`), bukan dengan mematikan stamping |
| Token LLM dicari-cari di API | ternyata ada di `ResponseMetaInfo` (javap jar) | Sebelum menulis "tidak tersedia", periksa metadata yang sudah diberikan pustaka |

---

## ✅ 5. Verifikasi (Definition of Done — C)

- [x] C0 dokumen baseline tanpa panggilan LLM; C1 contoh ber-proposal lolos validator + versi `draft-v2`; C2 alat `screen_catalog` deterministik + galat proposal berpath; C3 loop koreksi berbatas + stamping + fallback (4 skenario LLM palsu berskrip); C4 grader teruji + 11 kasus emas (baseline 100%); C5 eval live 3 gerbang + laporan.
- [x] Tak ada panggilan LLM di test otomatis (`ScriptedPromptExecutor`; live evals `assumeTrue`).
- [x] `DiscoveryRoutes.kt` tidak bertambah satu baris pun; file server terpanjang milik C = 290 (batas lunak 300).
- [x] `audit-variability.sh`: 0 temuan.
- [x] Semua test C hijau di atas main terbaru (B0+B3+B4+B5) — termasuk integrasi G2: baseline deterministik kini **berlayar** dan lolos kriteria isi layar secara nyata.

**Yang tersisa (bukan milik C):** jalankan eval live dengan kunci sungguhan (`DISCOVERY_LIVE_EVALS=1 DEEPSEEK_API_KEY=… DISCOVERY_LIVE_EVALS_CONFIRM=yes ./gradlew :server:test --tests '*KoogDiscoveryLiveEvalsTest'`) lalu isi rekomendasi G3 di laporan; cek mata jalur A (prototype menampilkan `rationale`/`source`).
