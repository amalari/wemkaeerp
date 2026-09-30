# Teaching — Discovery A8: agent Koog (LLM) di belakang kontrak domain

> Plan: [`docs/plannings/PLAN-discovery-blueprint-prototype-studio.md`](../plannings/PLAN-discovery-blueprint-prototype-studio.md) §2 A8 ·
> Status: selesai 2026-09-30 · Pendahulu: `teaching-discovery-a1-a7-a9-vertical-slice.md`

## Apa yang dibangun

Generator draf discovery kini punya **dua implementasi** di belakang kontrak domain yang sama
(`DiscoveryAgent`): `DeterministicDiscoveryAgent` (kata kunci, basis evals) dan `KoogDiscoveryAgent`
(LLM, plan §2 A8, keputusan D4). Route, use case, dan penyimpanan tidak tahu bedanya — itulah gunanya
kontrak.

```
POST /api/discovery/drafts  {narrative}
  → DiscoveryAgents.fromEnv()        kill-switch: koog | deterministik
  → KoogDiscoveryAgent
       ├─ AIAgent(singleRunStrategy) + tool: platform_modules(), validate_draft(draft)
       ├─ dekode lewat DiscoveryDraftCodec      (parser produksi, bukan parser sendiri)
       ├─ DiscoveryDraftValidator               galat berpath → putaran koreksi (maks. 3)
       └─ fallback deterministik (opsional)     kalau LLM gagal total
  → ops.discovery_drafts  (DRAFT, pemilik = caller)
```

## Keputusan penting & alasannya

1. **Dependensi hanya di `server`.** `core` tetap murni tanpa framework (DDD §2); Koog masuk sebagai
   `implementation` di `server` saja. `koog:1.3.0` **tidak** menarik `kotlinx-datetime`, jadi versi
   `0.6.2` repo ini tidak tersentuh — inilah yang dulu dikhawatirkan plan (“cek kotlinx-datetime 0.6.2
   vs Koog”). Klien DeepSeek dipublikasikan terpisah (`1.3.0-beta`, lapisan tipis di atas
   `openai-client-base:1.3.0`).
2. **Loop koreksi di luar agent, bukan di dalam graf.** Setiap putaran = satu percakapan bersih yang
   memuat galat berpath dari putaran sebelumnya. Konsekuensinya: jumlah panggilan LLM terbatas dan
   terhitung (maks. 3), dan jawaban LLM bisa diganti skrip di test tanpa menyentuh graf Koog.
3. **Jembatan pack bawaan (`useShipped`).** Validator menuntut dokumen berkode `garment` **identik**
   dengan pack yang dikirim platform. Model tidak mungkin menulis ulang dokumen sebesar itu dari
   ingatan, jadi prompt mengizinkan singkatan `"pack": {"useShipped": "garment"}` dan
   `applyShippedPackBridge` menukarnya dengan dokumen asli **sebelum** validator melihatnya. Ini
   pemetaan ke dokumen yang sudah ada — bukan pelonggaran aturan. Kode tak dikenal ditolak berpath
   (`$.pack.useShipped`), bukan diabaikan.
4. **Alat dibuat dengan deskriptor eksplisit, bukan introspeksi kelas.** Introspeksi Koog bersandar
   pada kotlinx-serialization (`@Serializable` + compiler plugin), sedangkan repo ini sengaja belum
   memakai plugin itu (lihat catatan di `ProspectCodec`/`ModuleDevCodec`). Dua alat ini cukup
   mendeklarasikan `ToolDescriptor` tangan + `decodeArgs` sendiri, sehingga dependensi Koog tidak
   menular ke konvensi repo. Karena dibuat tangan, deskriptornya **dites lewat penerjemah provider**
   (`OpenAICompatibleToolDescriptorSchemaGenerator`) — kalau skemanya rusak, model tidak bisa
   memanggil alat itu sama sekali.
5. **`validate_draft` mengembalikan laporan, bukan melempar.** Model perlu melihat galat berpath di
   dalam percakapan; lemparan hanya akan membunuh satu putaran.
6. **Kill-switch jujur.** `DISCOVERY_AGENT=koog` tanpa kunci API → deterministik **+ peringatan**
   (server tidak boleh gagal start). `DISCOVERY_AGENT_FALLBACK=off` mematikan jalur cadangan supaya
   kegagalan LLM terlihat sebagai kegagalan, bukan draf kata kunci yang menyamar.
7. **Id model diambil dari API, bukan dari katalog klien.** `GET https://api.deepseek.com/models`
   (2026-09-30) menyebut `deepseek-flash`; katalog Koog menyebut `deepseek-v4-flash`. Resolusi memakai
   **definisi klien** (kemampuan tools/JSON schema) dengan **id yang diminta**, dan id tak dikenal
   diteruskan apa adanya + peringatan.

## Bukti

| Bukti | Cara menjalankan | Hasil |
|---|---|---|
| 18 test tanpa jaringan | `./gradlew :server:test --tests 'com.eventverse.app.infrastructure.discovery.*'` | 18 tes, 0 gagal (1 dilewati: evals hidup) |
| Evals LLM hidup | `DISCOVERY_LIVE_EVALS=1 DEEPSEEK_API_KEY=sk-… ./gradlew :server:test --tests '*KoogDiscoveryLiveEvalsTest'` | **4/4 PASS** — `evals | koog/deepseek-flash/draft-v1 | klinik | PASS | …` |
| RLS + schema boundary | `set -a; . ./.env; set +a; ./gradlew :server:test --tests '*TenantRlsIsolation*' --tests '*Schema*'` | 10 tes, 0 gagal (pool `wemade_app` aktif) |
| Discovery API (A1–A7) | `--tests 'com.eventverse.app.Discovery*'` | 7 tes, 0 gagal |
| Audit variabilitas | `bash scripts/audit-variability.sh` | 0 temuan |

Skor LLM hidup pada narasi emas: klinik, bengkel, katering, dan **garment CMT** (lewat jembatan
`useShipped`). Garment lolos justru karena jembatan itu — tanpa jembatan, model gagal tiga putaran.

> **Catatan lingkungan**: `TenantRlsIsolationTest` butuh role `wemade_app` benar-benar bisa login.
> Di dev DB ini ia masih `NOLOGIN` (utang A0), sehingga suite diam-diam jatuh ke pool owner dan test
> gagal dengan `expected:<wemade_app> but was:<postgres>`. Diperbaiki dengan:
> `docker exec wemade-postgres psql -U postgres -d wemade_erp -c "ALTER ROLE wemade_app LOGIN PASSWORD '<DB_APP_PASSWORD>'"`.

## Pelajaran saat implementasi (semuanya ditemukan evals hidup, bukan unit test)

1. **Batas langkah 12 terlalu ketat.** Evals garment gagal dengan `Agent couldn't finish in given
   number of steps (12)` — bukan draf yang salah, melainkan agent kehabisan langkah. Satu pemanggilan
   alat memakan **dua** langkah (minta → eksekusi), jadi `platform_modules` + dua `validate_draft` +
   jawaban = 5 pemanggilan = 10 langkah. Dinaikkan ke 24 **dan** prompt diberi aturan hemat langkah.
   Pelajaran: kegagalan karena batas iterasi menyesatkan; pesannya tidak menyebut galat dokumen.
2. **Sinonim modul ≠ ejaan modul.** Grader awal menuntut sufiks persis dari agent kata kunci
   (`klinik_antrean`, `klinik_tagihan`); model menamai `klinik_pendaftaran`, `klinik_kasir` — sama
   benarnya. Grader hidup kini menguji **kemampuan** (`Set<String>` sinonim per kemampuan), bukan
   ejaan. Pelajaran: grader yang menguji ejaan akan menghukum model yang lebih baik.
3. **Katalog model pustaka tertinggal dari API.** `deepseek-v4-flash` (katalog Koog) bukan id yang
   dikenal `GET /models`; yang benar `deepseek-flash`. Karena itu default diambil dari verifikasi
   langsung dan id tak dikenal tidak ditolak.

## Utang & langkah berikutnya

- **A4 belum ada** (`ModuleDefinition.actions`/`vocabulary`): layar `/m/{code}` masih memakai istilah
  generik; prompt belum bisa meminta label aksi per vertikal.
- **`agentRef` belum tersimpan**: `StoredDiscoveryDraft` (V78) belum punya kolom jejak model, jadi
  `koog/…` vs `deterministic/…` baru terlihat di log server. Butuh migrasi kolom kecil saat jejak
  audit model diminta (pola `translatorRef` di domain prospek).
- **Fase C/D/E** (renderer + Studio, wizard + PDF, operasi produk) tetap seperti rencana.
- **`AccessSnapshotB6Test` gagal 690 → 725 di DB dev ini** — **bukan** akibat A8 (dibuktikan: gagal
  sama persis dengan perubahan A8 di-`git stash`). Penyebabnya drift data dev (`custom_roles`/
  `module_catalog_entries` vs snapshot yang di-commit), perlu ditelusuri terpisah.
- Katalog model DeepSeek di Koog perlu ditinjau saat upgrade; `DEFAULT_MODEL_ID` di
  `DiscoveryAgents` adalah satu-satunya tempat yang harus ikut berubah.
