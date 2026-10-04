# PLAN — Agent C: Agent Koog & Evaluasi

**Agent:** C · **Tanggal:** 2026-10-04 · **Induk:** [PLAN-screen-proposal-contract-koog](../PLAN-screen-proposal-contract-koog.md)

> Berdiri sendiri untuk satu agent. Bila selisih dengan plan induk, **plan induk (kontrak §2) yang berlaku**.

---

## Misi & Lingkupmu — Agent C (Agent Koog & Evaluasi)

**Misi:** membuat agent Koog **menghasilkan `ScreenProposal` yang valid** sebagai bagian dari dokumen draf, mengukurnya dengan **eval berstruktur** terhadap baseline deterministik, dan memberi koordinator dasar untuk **memutuskan** apakah Koog menjadi default.

**Kamu memiliki:** `server/.../infrastructure/discovery/**` (`KoogDiscoveryPrompt`, `KoogDiscoveryTools`, `KoogDiscoveryAgent`, `DiscoveryAgents`), `DiscoveryEvalsTest*` dan berkas eval baru di `server/src/test/**`, skrip eval live, dokumen.
**Dilarang:** `core/**` (kontrak/validator = B; minta lewat kontrak), `app/**` (A), `DiscoveryRoutes.kt` (jangan tambah baris).

### Butir kerja
**C0 — Discovery keluaran Koog (G0, ±1 hari, tanpa memanggil LLM).** Baca `KoogDiscovery*` dan tes yang ada; **tulis di `docs/plannings/discovery-SP-koog-baseline.md`**: (1) bagaimana galat berpath dikembalikan ke model pada putaran koreksi (bentuk persisnya); (2) berapa token/langkah satu run kira-kira (dari batas yang ada); (3) apa yang `validate_draft` periksa sekarang; (4) daftar risiko memperluas keluaran dengan `proposal` (panjang, kesalahan JSON, kunci hilang). Rancang format `proposal` **dalam prompt** agar ringkas.
- **AC:** dokumen dengan keputusan dan angka batas; tidak ada panggilan LLM.

**C1 — Prompt: contoh dari kode.** Setelah B0: perluas `exampleDraft()` agar layar contoh memuat `proposal` (dirakit dari tipe kontrak lalu di-encode `DiscoveryDraftCodec` — **bukan teks ditempel**); tambahkan aturan prompt untuk `proposal` (jenis tampilan menurut sifat kerja modul, status bermakna, field bertipe, batas ukuran, istilah pemilik usaha, **tanpa istilah konveksi** untuk vertikal lain). Pertahankan pola: contoh itu sendiri **lolos validator** (tes `KoogDiscoveryPromptTest`). Naikkan versi: `agentRef = koog/<model>/draft-v2`.
- **AC:** tes contoh lolos `DiscoveryDraftValidator` + `ScreenProposalValidator`; contoh berkode pack `contoh` (tidak membocorkan jawaban); panjang prompt dicatat dan dibatasi.

**C2 — Alat.** Perluas `validate_draft` agar mengembalikan galat proposal berpath (`$.screens[i].proposal.entity.fields[j]…`) dalam format yang sama dengan galat lain; tambahkan alat `screen_catalog()` yang menjawab kosakata tertutup (jenis tampilan, tipe field, gaya kartu) dan **pemetaan peran → tampilan** dari pack bawaan sebagai petunjuk (bukan aturan keras). Jaga batas `maxToolIterations`.
- **AC:** tes alat dengan masukan sah/tak sah (tanpa LLM); jawaban alat deterministik.

**C3 — Loop koreksi & cadangan.** Pastikan putaran koreksi mengembalikan galat proposal secara ringkas (potong bila panjang); kegagalan setelah batas → `fallback` deterministik (yang kini menghasilkan layar, dari B3) — **tidak pernah** menyimpan draf bertanda "Agent" yang proposalnya rusak. Catat `source` per layar dan `agentRef`/versi prompt di draf.
- **AC:** tes dengan **LLM palsu berskrip**: (a) langsung sah; (b) rusak lalu sah setelah koreksi; (c) tak pernah sah → fallback deterministik, `source = DETERMINISTIC`; (d) keluaran berisi jenis tampilan/field tak sah → ditolak berpath, bukan diterima.

**C4 — Set eval ≥ 10 kasus.** Perluas `DiscoveryEvalsTest`: garment ×2, klinik, bengkel, katering, sablon/bordir, retail, jasa/IT, sekolah, logistik. Penilai otomatis per kriteria §6 (valid, cakupan modul, jenis tampilan ∈ himpunan per peran, status bermakna, field memadai, kemurnian vertikal). Penilai ditulis sebagai kode terpisah yang **dites sendiri** (kasus lulus/gagal buatan tangan), supaya skor bisa dipercaya. Deterministik (B3) harus **100%** pada kriteria otomatis.
- **AC:** baseline deterministik 100%; penilai teruji; format log `evals | <kasus> | PASS|FAIL | <detail>` dipertahankan; tes **tidak memanggil LLM**.

**C5 — Eval live (opt-in) & laporan G3.** Skrip yang menjalankan Koog terhadap set emas (≥ 3 ulangan per kasus), **mencetak perkiraan biaya (kasus × ulangan × putaran) dan meminta konfirmasi sebelum berjalan**, lalu menulis laporan: skor per kasus dan per kriteria, variasi antar-ulangan, putaran koreksi, token/waktu, perbandingan dengan baseline. Kunci API hanya dari env; tidak pernah dicetak.
- **AC:** skrip berjalan hanya dengan kunci dan konfirmasi; laporan tertulis di `docs/plannings/eval-SP-koog-<tanggal>.md`; **rekomendasi** (bukan keputusan): Koog default / hanya bila deterministik gagal / belum layak.

### Urutan & ketergantungan
`C0 (G0) → [setelah B0] C1 → C2 → C3 → C4 → [G2/G3] C5`. C4 dapat mulai dengan penilai dan baseline segera setelah B3.

### Definition of Done — C
- [ ] AC C0–C5; **tak ada panggilan LLM di tes otomatis**; LLM palsu berskrip untuk jalur gagal
- [ ] Kunci API tidak masuk repo/log; skrip live meminta konfirmasi biaya
- [ ] `wc -l` server sesuai batas; `DiscoveryRoutes.kt` tidak bertambah
- [ ] `./gradlew :server:test` (yang disentuh) hijau dengan DB uji; audit variabilitas 0 temuan baru
- [ ] Teaching doc `docs/teaching/teaching-sp-c-<slug>.md` + dokumen discovery dan laporan eval

---

## Rujukan bersama (salinan dari plan induk)

## 0. Masalah, Keputusan, Batas

**Masalah.** Keputusan "modul ini ditampilkan sebagai kanban/tabel/…, dengan field, status, dan kartu begini" kini punya dua keadaan yang tidak setara:
- **Garment**: ditulis manusia di pack (`GarmentScreenSuggestions`) — kaya (field bertipe, transisi, kartu), tetapi tidak menurun ke bisnis lain.
- **Bisnis baru dari narasi klien**: agent hanya mengeluarkan **kode jenis tampilan** per layar (`PrototypeScreen(screenId, moduleId, title, widget)`); validator hanya memeriksa `moduleId` dan jenis ada di daftar tertutup. Isi layar (kolom, status, kartu, form) **tidak ada** → `WidgetRegistry` menampilkan penanda generik ("contoh 1"); agent deterministik **tidak mengusulkan layar sama sekali**.

**Keputusan** (percakapan 2026-10-04): rencana berjalan dari **dua arah dengan satu kontrak di tengah**:
```
Arah 1: contoh sekarang (garment)  ──┐
                                     ├──►  KONTRAK  ScreenProposal  ──►  validator tunggal  ──►  prototype / handoff
Arah 2: agent Koog dari narasi      ──┘
```
1. **Kontrak dulu dari contoh yang ada**, baru Koog mengikutinya. Koog tidak dibangun lebih dulu.
2. **Satu validator** untuk semua pembuat (manusia, deterministik, LLM) — pola yang sudah dipakai untuk draf.
3. **Agent LLM tidak dipercaya**: keluarannya hanya usulan; valid atau ditolak dengan galat berpath dan dikoreksi (loop koreksi yang sudah ada).
4. **Selalu ada cadangan deterministik** yang kini juga menghasilkan layar (pemetaan peran → tampilan sebagai **data pack**), sehingga demo dan eval tak bergantung pada kunci API.

**Di luar lingkup:** relaksasi "satu usulan layar per modul" di pack (draf **sudah** boleh banyak layar per modul; lihat §1); layout komposit; pembuatan modul live otomatis; fine-tuning model; penagihan biaya LLM.

## 1. Baseline (terbaca dari kode, `main` @ `595cf80`)
- **Agent Koog** (`server/.../infrastructure/discovery/`): `KoogDiscoveryAgent` — satu run per percakapan (`singleRunStrategy`) dengan dua alat (`platform_modules`, `validate_draft`); **loop koreksi di luar agent** (`maxCorrectionRounds`; tiap putaran percakapan baru berisi galat berpath sebelumnya); dekode lewat `DiscoveryDraftCodec`; **jembatan pack bawaan** (`{"pack":{"useShipped":"garment"}}`); `fallback` ke agent deterministik; `agentRef = koog/<model>/draft-v1` tercatat di draf. Dikonfigurasi `DISCOVERY_AGENT`, kunci API, `DISCOVERY_AGENT_MODEL`, `DISCOVERY_AGENT_FALLBACK`.
- **Prompt** (`KoogDiscoveryPrompt`): contoh dokumen **dirakit dari kode** (`exampleDraft()` → `DiscoveryDraftCodec`) dan dites lolos validator — pola yang harus dilanjutkan. Prompt meminta "1–3 `screens` untuk modul utama"; contoh berisi dua layar untuk satu modul (tabel + form) → **draf tampaknya sudah boleh banyak layar per modul** (disimpulkan dari validator dan contoh prompt; **belum diuji end-to-end di renderer** — jadikan satu uji di G2).
- **Keluaran layar saat ini**: `{"screens":[{"screenId","moduleId","title","widget"}]}`; validator (`DiscoveryDraftValidator`) memeriksa hanya `moduleId` ada di pack dan `widget` ∈ `WidgetKind`.
- **Eval** (`DiscoveryEvalsTest`): empat narasi emas (klinik, bengkel, katering, garment-cmt), dinilai oleh **validator** dan **cakupan modul**; deterministik harus 4/4 (baseline); skor LLM dicatat `evals | <kasus> | PASS|FAIL | <detail>`.
- **Agent deterministik** (`DeterministicDiscoveryAgent`): menghasilkan pack+blueprint dari kata kunci (kemampuan: pesanan, antrean, stok, tagihan, laporan), **tanpa layar**.
- **Sisi tampilan**: `WidgetRegistry.interactiveFor/sampleRowsFor/screenFor`, `InteractiveScreenFactory` (baris contoh + hints → `InteractiveScreen`), `InteractiveScreenCodec`; pack garment memberi hints (kanban/tabel/dasbor) dan seed sebagai data.

## 2. Kontrak

### 2.1 `ScreenProposal` (diterbitkan B; payload yang dihasilkan oleh pembuat mana pun)
Bentuk **ringkas** (bukan `InteractiveScreen` penuh) supaya ramah LLM dan murah token; di-*konversi* ke `InteractiveScreen` oleh kode, bukan oleh LLM.
```kotlin
data class ScreenProposal(
    val screenId: String, val moduleId: ModuleId, val title: String,
    val widget: WidgetKind,
    val rationale: String,                     // ≤ 200 karakter, bahasa pemilik usaha: "Dipilih karena …"
    val entity: EntityProposal? ,              // wajib untuk widget data (KANBAN, TABLE, FORM, CHECKLIST); null untuk DASHBOARD/PRINT/CUSTOM_SCREEN
    val view: ViewProposal,                    // konfigurasi per widget (lihat 2.2)
    val seed: List<Map<String, String>> = emptyList(),   // ≤ 8 baris, divalidasi terhadap skema entitas
    val binding: DataBinding = DataBinding.Memory
)
data class EntityProposal(val id: String, val label: String, val fields: List<FieldProposal>, val statusField: String? = null, val transitions: Map<String, List<String>> = emptyMap())
data class FieldProposal(val key: String, val label: String, val type: FieldType, val required: Boolean = false, val options: List<String> = emptyList())
sealed interface ViewProposal { Kanban(card, columnMeta, detailForm) · Table(columns, inlineCreate, editableFields) · Form(fields) · Checklist(labelField, doneField) · Dashboard(tiles) · Print(fields) · None }
enum class ProposalSource { PACK, DETERMINISTIC, AGENT }     // provenance; AGENT membawa agentRef
fun ScreenProposal.toInteractiveScreen(): Result<InteractiveScreen>   // memakai konstruktor PrototypeSpec (validasi) — tidak pernah melempar mentah
```
`ViewProposal` memakai tipe dari rencana data-port (`CardElement`, `ColumnMeta`, `FormConfig`, `TableConfig`).

### 2.2 Aturan validator (satu sumber, `ScreenProposalValidator`, diterbitkan B)
Setiap pelanggaran = `ProposalIssue(path, message)` berbahasa jelas untuk dibaca LLM maupun manusia (pola `DiscoveryValidationIssue`):
- **Kosakata tertutup**: `widget`, `FieldType`, `CardStyle` ∈ daftar; di luar itu ditolak.
- **Koherensi**: `statusField` adalah field ENUM; kolom kanban = opsi status; `transitions` hanya antar opsi yang ada; `card`/`detailForm`/`columns` merujuk field yang ada; `required` field ada di form bila ada.
- **Batas** (anti-bengkak, anti-penyalahgunaan): field ≤ 12, opsi per ENUM ≤ 8, status ≤ 8, seed ≤ 8 baris, teks ≤ 200 karakter, kunci `[a-z][a-z0-9_]{0,40}`.
- **Seed** cocok skema (opsi enum, angka, tanggal ISO bila bertipe DATE, wajib terisi).
- **Kemurnian vertikal**: untuk pack non-garment, tak boleh memuat istilah konveksi daftar-hitam (pola `INDUSTRY_TERMS` di `DeterministicDiscoveryAgent`) — keluaran ditolak, bukan disaring.
- **Konsistensi lintas-layar**: `moduleId` ada di pack; `screenId` unik; banyak layar per modul boleh (draf), `entity.id` yang sama harus berdefinisi identik.

### 2.3 Pemetaan peran → tampilan (data pack, B menerbitkan)
`SlotDefinition.defaultWidget: WidgetKind?` + `defaultStatuses: List<String>` (opsional, kompatibel mundur). Dipakai oleh **`DeterministicScreenProposer`** untuk modul tanpa usulan eksplisit. Garment memberi nilai untuk slot-slotnya (cermin dari `GarmentScreenSuggestions`); pack hasil agent deterministik memberi nilai dari kemampuan bawaannya (pesanan→TABLE, antrean→KANBAN, stok→TABLE, tagihan→TABLE, laporan→DASHBOARD).

### 2.4 Pembuat usulan (port)
```kotlin
interface ScreenProposer { suspend fun propose(pack: DomainPack, module: ModuleDefinition, narrative: String?): Result<List<ScreenProposal>> }
PackScreenProposer          // mock/acuan: dari GarmentScreenSuggestions (ditulis manusia) → source = PACK
DeterministicScreenProposer // pemetaan peran → tampilan (2.3) → source = DETERMINISTIC
AgentScreenProposer (Koog)  // dalam dokumen draf; source = AGENT(agentRef)
```
**Koog tidak memanggil `ScreenProposer` per modul**: ia menghasilkan `screens` sebagai bagian dari dokumen draf yang sama (satu run, biaya terkendali); tetapi bentuk dan validatornya **identik**.

### 2.5 Dokumen draf (B + C)
`DiscoveryDraft.screens: List<PrototypeScreen>` diperluas: `PrototypeScreen` + `proposal: ScreenProposal?` (opsional; kompatibel mundur — draf lama hanya punya kode jenis tetap terbaca) dan `source`. `DiscoveryDraftCodec` meng-encode/decode kunci baru; `DiscoveryDraftValidator` memanggil `ScreenProposalValidator` dengan path `$.screens[i].proposal…`.

## 3. Arsitektur Target
```
narasi ──► Koog (prompt memuat contoh DARI KODE) ──► draf {pack, blueprint, screens[+proposal]}
                                                              │
                       DiscoveryDraftValidator + ScreenProposalValidator (satu aturan)
                          │ galat berpath → putaran koreksi (sudah ada)          │ lolos
                          ▼                                                      ▼
                  gagal / cadangan DeterministicScreenProposer         proposal.toInteractiveScreen() → prototype
                                                                       (+ rationale & source tampil ke prospek)
```

## 4. Kepemilikan File (satu pemilik per file)
| Agent | Jalur | Memiliki |
|---|---|---|
| **A** | Tampilan proposal | `app/shared/.../presentation/**` (`discovery/**`, `builder/**`, wizard), `DiscoveryUiModel.kt`, tes `app/shared/src/jvmTest/**` |
| **B** | Kontrak, validator, pembuat acuan & deterministik | `core/.../domain/discovery/proposal/**` (baru), `core/.../domain/discovery/{DiscoveryDraft,DiscoveryDraftValidator,DeterministicDiscoveryAgent,WidgetRegistry}.kt`, `core/.../shared/discovery/DiscoveryDraftCodec.kt`, `core/.../domain/pack/{GarmentScreenSuggestions,DomainPack,GarmentSlots}.kt` (kolom `defaultWidget`), `ScreenSuggestionCodec`, tes core terkait |
| **C** | Agent Koog & evaluasi | `server/.../infrastructure/discovery/**` (prompt, alat, agent), `server/src/test/**/DiscoveryEvalsTest*` + berkas eval baru, skrip eval live, dokumen |
**Hotspot**: `DiscoveryDraftCodec.kt` & `DiscoveryDraftValidator.kt` = **B** (C meminta lewat kontrak); `KoogDiscoveryPrompt.kt` = **C**; `DiscoveryRoutes.kt` — **jangan tambah baris** (di atas batas lunak). `.claude/**`, `AGENTS.md`, `docs/plannings/PLAN-*.md` = koordinator.

## 5. Gelombang & Gerbang
```
G0  Kontrak (B, ±1,5 hari)   ── ScreenProposal + validator + konversi + codec draf, dimerge ke main
                               C: discovery eval & baca-tulis keluaran Koog; A: spike tampilan rationale/source
G1  Paralel (A ∥ B ∥ C)      ── B: pembuat acuan+deterministik; C: prompt+alat+eval; A: UI
G2  Integrasi                ── merge B → C → A; eval deterministik 100%; cek mata
G3  Eval live (opt-in)       ── Koog dengan kunci API: skor dicatat, dibandingkan baseline; keputusan rilis
```
| Gerbang | Syarat |
|---|---|
| **G0** | §2.1–§2.2, §2.5 sebagai kode+tes; **pack garment diekspresikan sebagai `ScreenProposal` acuan lolos validator**; draf lama tetap terbaca; A dan C menandatangani |
| **G1** | DoD jalur hijau di worktree sendiri |
| **G2** | `:core:jvmTest`, `:server:test` (eval deterministik), kompilasi 3 target klien; prototype menampilkan layar dari proposal deterministik untuk pack non-garment |
| **G3** | Skor Koog dilaporkan per kasus dan per kriteria; **keputusan eksplisit** koordinator apakah Koog dipakai sebagai default |

### 5.1 Perkiraan (kasar, belum dikalibrasi)
B ±6–7 hari, C ±5–6, A ±4–5 (paralel ±8–10 hari; sekuensial ±16–18). **Risiko terbesar: C** — kualitas desain entitas oleh LLM tak terjamin dan tidak bisa ditentukan sebelum G3. Itu sebabnya G3 berupa keputusan, bukan janji.

## 6. Evaluasi (inti arah 2)
Kasus emas diperluas dari 4 menjadi **≥ 10** (garment ×2, klinik, bengkel, katering, sablon/bordir, retail, jasa/IT, sekolah, logistik), masing-masing dengan **kriteria berstruktur** (bukan kecocokan teks):
| Kriteria | Cara menilai |
|---|---|
| Valid | lolos `DiscoveryDraftValidator` + `ScreenProposalValidator` (otomatis) |
| Cakupan modul | sufiks modul yang diharapkan ada (sudah ada) |
| **Jenis tampilan masuk akal** | tiap modul inti memakai jenis dari himpunan yang diizinkan per peran (mis. antrean ∈ {KANBAN, TABLE}); bukan satu jawaban tunggal |
| **Status bermakna** | 2–8 status, berurutan, ada awal dan akhir; transisi acyclic-ish atau bermakna; kata dari narasi |
| **Field memadai** | ada judul-ish, ≥ 1 field bertipe non-teks bila narasi menyebut tanggal/angka/prioritas |
| **Kemurnian vertikal** | tak ada istilah konveksi di pack non-garment |
| **Biaya & stabilitas** | jumlah putaran koreksi, token, waktu; dijalankan ≥ 3× per kasus untuk mengukur variasi |
Baseline: **deterministik harus lulus 100%** kriteria otomatis (kalau tidak, penilainya yang rusak). Skor LLM dicatat dengan format log yang ada.

## 7. Aturan Kerja Bersama (semua agent)
**Cabang & isolasi (WAJIB):** `git worktree add ../wemkaeerp-wt-<huruf> -b feat/sp-<huruf>-<slug> main` — **bukan** folder utama (sesi lalu agent menyunting folder yang sama dan kompilasi saling pecah). Jangan commit/push ke `main`; PR kecil; rebase sebelum PR; konflik = berhenti dan lapor. Commit Indonesia ringkas + `Co-Authored-By`.
**Standar repo:** Graphify bila tersedia; DDD, tanpa `!!`; variabilitas (beda per industri → data; **tes tenant/pack non-garment wajib**); `scripts/audit-variability.sh` 0 temuan baru; ukuran file core 250/400, presentation 400/600, server 300/500, test 500/800 (`DiscoveryRoutes.kt` jangan bertambah; `DomainPackCodec.kt` 328 → jangan bertambah); design system (nol literal warna, komponen `designsystem/` buta domain); teaching doc per jalur `docs/teaching/teaching-sp-<huruf>-<slug>.md`.
**Verifikasi:** `./gradlew :core:jvmTest` · kompilasi `:app:shared` JVM/WasmJS/JS + `:server:compileKotlin` · `:app:shared:jvmTest` · server: `DB_NAME=<db-uji> ./gradlew :server:test --tests '<yang disentuh>'`.
**DB:** hanya database uji bernama berisi `scratch`; `DatabaseFactory.init()` menjalankan Flyway — jangan tanpa `DB_NAME`. `.env` tidak ikut worktree dan **dilarang disalin**; `EnvLoader` memprioritaskan env sistem.
**LLM:** **tes otomatis tidak boleh memanggil LLM sungguhan**; kunci API tidak pernah masuk repo/log; eval live hanya lewat skrip opt-in dengan batas biaya (jumlah kasus × ulangan × putaran) yang dicetak sebelum berjalan.
**Cek mata hanya A** (satu browser, port 3001/8081). **Jebakan yang sudah terjadi:** cache inkremental Kotlin rusak → ulangi, hapus `build/` di worktree sendiri; test hijau ≠ UI benar (uji wadah tinggi terbatas dan tak terbatas); kunci `remember` harus identitas stabil; glyph non-ASCII di font Nunito; fallback tak boleh mengarang fakta; kompilasi keluaran, bukan hanya menguji teksnya; uji mutasi untuk test yang dijaga env.

## 8. Risiko
| Risiko | Mitigasi |
|---|---|
| LLM merancang entitas buruk/inkonsisten | validator ketat + loop koreksi; eval ≥ 10 kasus × 3 ulangan; cadangan deterministik; G3 = keputusan, bukan janji |
| Kontrak meleset → kerja ulang | G0 pendek; fixture dulu; perubahan kontrak = versi baru |
| Biaya/latensi LLM membengkak | batas langkah dan putaran (sudah ada); `proposal` ringkas, bukan `InteractiveScreen` penuh; seed ≤ 8 baris |
| Kebocoran istilah garment ke bisnis lain | validator kemurnian vertikal + kriteria eval |
| Agent saling menyunting | §4 + worktree wajib |
| Prompt injection lewat narasi klien | keluaran hanya data tervalidasi; tidak ada eksekusi; batas panjang; narasi tak pernah dieksekusi atau dipakai sebagai kode/SQL |
| Menunggu data-port | rencana ini dimulai setelah G2 data-port; sebelum itu hanya discovery C dan spike A |

## 9. Skenario Penerimaan (G2/G3)
1. **Acuan (mock):** pack garment diekspresikan sebagai `ScreenProposal`; `toInteractiveScreen()` menghasilkan layar yang **setara** dengan yang kini dibangun dari `GarmentScreenSuggestions` (test paritas).
2. **Deterministik:** narasi klinik/bengkel/katering → draf **dengan layar** (antrean kanban, pesanan tabel, laporan dasbor) berfield dan status bermakna; prototype tampil kaya, bukan "contoh 1".
3. **Koog (G3, opt-in):** narasi baru → draf dengan proposal lolos validator (setelah ≤ N putaran); skor eval per kriteria; perbandingan dengan baseline.
4. **Gagal aman:** LLM mengeluarkan jenis tampilan/field tak sah → galat berpath, dikoreksi atau jatuh ke deterministik; tidak ada layar rusak di prototype.
5. **Jejak:** setiap layar menampilkan alasan (`rationale`) dan sumber (Pack / Deterministik / Agent + model).

## 10. Definition of Done (per jalur)
- [ ] AC jalur; tes domain murni + **pack non-garment**; tes kegagalan (validator menolak, LLM palsu mengeluarkan sampah)
- [ ] Tidak ada edit di luar kepemilikan; `wc -l` sesuai batas/ratchet
- [ ] Kompilasi hijau; audit variabilitas 0 temuan baru; tak ada panggilan LLM di tes otomatis
- [ ] Teaching doc jalur; PR kecil; laporan ke koordinator

## Format laporan ke koordinator (setiap PR / akhir gelombang)
1. Butir selesai + cabang/PR. 2. Hasil perintah verifikasi (sertakan kegagalan apa adanya). 3. `wc -l` sebelum → sesudah untuk file di atas batas lunak. 4. Yang belum diverifikasi + temuan/keputusan terbuka. 5. Perubahan kontrak yang kamu butuhkan — jangan menyunting berkas milik agent lain.
