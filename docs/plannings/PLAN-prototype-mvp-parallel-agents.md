# PLAN — Prototype Setara MVP & Brief untuk Tim (3 AI Agent Paralel)

**Status**: Draf 1 · **Tanggal**: 2026-10-04 · **Induk**: [PLAN-interactive-prototype-block-library](PLAN-interactive-prototype-block-library.md) (menggantikan urutan fasenya), [TRD-PLAT-003](../trd/TRD-PLAT-003-interactive-prototype.md)
**Pembaca**: tiga AI agent pelaksana (A, B, C) dan koordinator manusia. Setiap agent membaca §0–§4 dan bagian agent-nya sendiri.

---

## 0. Tujuan & Batas

**Posisi produk (diputuskan 2026-10-04):** ini **alat software house**, bukan pembuat aplikasi otomatis. Klien bercerita dan mencoba prototype; tim kita mendapat **brief** (apa yang harus dibangun, mana yang sudah ada di katalog, berapa biayanya) dan **titik awal kode** yang diteruskan tim. UI/UX akhir diubah tim, bukan generator.

**Target rencana ini — "prototype setara MVP":**
1. **Berfungsi**: tambah / ubah / hapus data lewat form; aturan alur ditegakkan reducer; (di modul pilot) data tersimpan per tenant dengan RBAC fail-closed.
2. **Mudah diteruskan**: komponen UI = komponen Clay bersama (satu tempat mengubah tampilan); spec = data di repo; kode hasil generate bukan kotak hitam dan **dimiliki tim setelah diterapkan**.
3. **Menangkap kebutuhan**: setiap perubahan klien ("tambah status Revisi") tercatat terstruktur dan masuk brief.

**Di luar lingkup (ditunda, jangan dikerjakan):** F4 serah-terima antarmodul otomatis, F6 versi beku/publish, F10 migrasi perubahan merusak, F9 migrasi massal modul live. Alasan: hanya perlu bila klien mengubah aplikasi live sendiri.

**Larangan lintas-agent (aturan repo):**
- Jalur B: fitur **konveksi** baru tidak ditulis pertama kali di repo ini (`CLAUDE.md` Status Repo #5). Karena itu modul pilot memakai **pack netral** (lihat §6), bukan modul garment baru.
- Kode mesin tidak boleh menyebut konsep satu industri. Isi garment hanya di pack (`domain/pack/Garment*`).

---

## 1. Baseline (terverifikasi 2026-10-04, commit `7cc47fa`)

| Sudah ada | Lokasi |
|---|---|
| Spec/Store/Reducer murni, blok Kanban/Tabel/Checklist/Dasbor, `PrototypeSession` | `core/.../domain/prototype/*`, `app/shared/.../presentation/discovery/Interactive*`, `PrototypeSession.kt` |
| Seed garment ekspor satu sumber | `core/.../domain/pack/GarmentExportSeed.kt` |
| Panel Paket & Harga + `/api/builder/draft/price` | `PrototypePricePanel.kt`, `BuilderPriceRoutes.kt`, `PriceDiscoveryDraftUseCase` (`onlyModuleIds`, `lines`) |
| Generator handoff (**tabel stub**, bukan dari spec) | `core/.../domain/discovery/HandoffScaffoldGenerator.kt` |
| Agent discovery (menghasilkan draf utuh, bukan operasi) | `DiscoveryAgent`, `DeterministicDiscoveryAgent`, Koog |

**Belum ada:** blok Form dan aksi hapus; komponen kanban generik di design system (drag ditulis 4×: CRM, Sampling, operator, prototype); operasi spec (`SpecOp`); handoff dari spec; ringkasan kebutuhan/brief.

**Utang yang harus diperhatikan:** `DiscoveryRoutes.kt` 410 baris (soft 300, hard 500) — jangan menambah; Android SDK tidak ada di mesin dev (hanya CI); 3 file test lama fragile terhadap cache kompilasi Kotlin (lihat §5.4).

---

## 2. Tiga Jalur Kerja (kepemilikan file eksklusif)

```
 A  UI & Design System   ── blok, kanban generik, form UI, chat-edit UI, tombol brief
 B  Spec, Domain & Capture ── model spec, aksi, SpecOp, brief (rendering Markdown), agent tool
 C  Persistensi & Handoff ── generator dari spec → migrasi/route/layar, modul pilot, endpoint brief
```

### 2.1 Tabel kepemilikan (satu pemilik per file; selain pemilik = **dilarang menyunting langsung**)

| Agent | Memiliki | Jangan sentuh |
|---|---|---|
| **A** | `app/shared/.../presentation/designsystem/**`, `presentation/discovery/Interactive*.kt`, `PrototypeRenderer.kt`, `PrototypeSession.kt`, `InteractiveBlock.kt`, `presentation/discovery/blocks/**` (baru), `presentation/builder/**`, `presentation/sampling/components/SamplingDragDropState.kt` & `SamplingPipelineKanbanBoard.kt` (hanya untuk paritas F1) | `core/**`, `server/**` |
| **B** | `core/.../domain/prototype/**`, `core/.../shared/pack/InteractiveScreenCodec.kt`, `core/.../domain/discovery/WidgetRegistry.kt`, `core/.../domain/discovery/usecases/Capture*` (baru), `core/.../domain/discovery/brief/**` (baru), tes di `core/src/commonTest/**/prototype/**` & `**/brief/**` | `app/**`, `server/**`, `HandoffScaffoldGenerator*` |
| **C** | `core/.../domain/discovery/HandoffScaffoldGenerator*` (+ file baru `handoff/**`), `server/**` (route baru: `BuilderBriefRoutes.kt`, `Discovery*`), `server/src/main/resources/db/migration/V90+`, pack netral & modul pilot (`core/.../domain/pack/GenericPilotPack*`), tes `core/**/discovery/Handoff*`, `server/src/test/**` | `app/**`, `domain/prototype/**` |

### 2.2 Berkas bersama (hotspot) — aturan khusus
| Berkas | Pemilik | Cara agent lain meminta perubahan |
|---|---|---|
| `PrototypeSpec.kt`, `EntitySpec.kt` | B | Ajukan lewat **Kontrak §3** (PR kecil ke cabang B); jangan edit |
| `DomainPackCodec.kt`, `DomainPack.kt`, `GarmentScreenSuggestions.kt` | B | idem; C boleh **menambah** file pack baru, bukan mengedit ini |
| `DiscoveryUiModel.kt` | A | B/C memberi bentuk JSON di Kontrak; A yang mem-parse |
| `DiscoverySummary.kt`, `DiscoveryPriceJson.kt` | C | A/B meminta field baru lewat Kontrak |
| `.claude/**`, `AGENTS.md`, `docs/plannings/PLAN-*.md` | koordinator | agent tidak menyunting |

---

## 3. Kontrak Antar-Agent (dibekukan di Gelombang 0 oleh B; perubahan = versi baru + kabar ke semua)

Semua agent mengembangkan terhadap kontrak ini dengan **fixture**, tidak menunggu implementasi agent lain.

### 3.1 Model (B menerbitkan; A dan C memakai)
```kotlin
// FieldSpec: tambah (default kompatibel mundur)
data class FieldSpec(key, label, type, options = emptyList(), val required: Boolean = false)

// Blok Form
data class FormConfig(val fields: List<String>, val submitLabel: String = "Simpan")
// ScreenSpec: tambah `val form: FormConfig? = null`; widget FORM <-> form != null; entityId wajib

// Aksi
PrototypeAction.Delete(entityId: String, rowId: String)     // tambahan; Create & SetField tetap
PrototypeReducer.reduce(...)  // `required` ditegakkan pada Create; pesan galat siap-tampil
```
Pesan galat reducer **berbahasa pengguna** (sudah begitu), tanpa nama teknis.

### 3.2 SpecOp (B menerbitkan; A memakai di UI chat; C hanya membaca log)
```kotlin
sealed interface SpecOp {
    data class AddEnumOption(entityId, field, option, after: String? = null) : SpecOp   // "tambah status/kolom"
    data class RenameEnumOption(entityId, field, from, to) : SpecOp                      // seed ikut ditulis ulang
    data class AddTransition(entityId, field, from, to) : SpecOp
    data class AddField(entityId, field: FieldSpec) : SpecOp
    data class RenameFieldLabel(entityId, key, label) : SpecOp
}
object SpecOpApplier { fun apply(screen: InteractiveScreen, op: SpecOp): Result<InteractiveScreen> }
// Gagal = Result.failure dengan pesan siap-tampil; operasi di luar kosakata tidak bisa dibentuk (sealed).
```
Satu giliran chat ≤ 5 operasi (konstanta di B). Operasi diterapkan **satu per satu**; yang gagal tidak membatalkan yang sudah sah, dan semuanya masuk log.

### 3.3 Log & Brief (B menerbitkan model + renderer; C menyajikan lewat HTTP; A menampilkan tombol)
```kotlin
data class CaptureEntry(val at: String, val op: SpecOp, val ok: Boolean, val message: String?)
data class RequirementsBrief(
  val packCode: String, val modules: List<BriefModule>,        // modul dipakai + layar + entitas + field + status/transisi
  val changes: List<CaptureEntry>,                              // permintaan perubahan klien (urut waktu)
  val coverage: List<BriefCoverage>,                            // per modul: covered/gap + harga (diisi dari PriceDiscoveryDraftUseCase.lines)
  val customNeeds: List<String>                                 // kebutuhan di luar blok, ditandai CUSTOM_EXTENSION
)
object BriefRenderer { fun markdown(brief: RequirementsBrief): String }   // deterministik
```
Log perubahan **tidak disimpan di server** (state prototype = memori sesi klien, keputusan awal). Klien mengirimkannya saat meminta brief — endpoint tak menulis apa pun:

Endpoint (C): `POST /api/builder/draft/brief`, body `{ "included": ["moduleId",…], "changes": [CaptureEntry,…] }` → `{ "markdown": "...", "brief": {...} }`. Gerbang builder, **semantik baca-saja** (POST hanya karena membawa body), 404 tanpa draf, 400 untuk modul asing, 403/401 seperti `GET /draft`. C merakit `coverage` dari `PriceDiscoveryDraftUseCase.lines` dan memanggil `BriefRenderer` milik B.

### 3.3b Pengusul operasi dari bahasa biasa (B menerbitkan port + implementasi deterministik; C memasang endpoint)
```kotlin
interface SpecOpProposer {                      // port domain murni
    suspend fun propose(message: String, screen: InteractiveScreen): Result<List<SpecOp>>   // ≤ 5, tervalidasi
}
class DeterministicSpecOpProposer : SpecOpProposer   // aturan kata kunci Indonesia; tanpa LLM/kunci API
```
Endpoint (C, G2): `POST /api/builder/draft/spec-ops` body `{ "message": "...", "screenId": "..." , "spec": {InteractiveScreen JSON} }` → `{ "ops": [...] , "reply": "..." }`. Tidak menerapkan apa pun; klien menerapkannya lewat `SpecOpApplier`. Adaptor LLM (Koog) = butir C6, opsional, di belakang port yang sama dan **wajib melewati validator yang sama**.

### 3.4 Handoff dari spec (C menerbitkan; B hanya menyediakan model)
```kotlin
HandoffScaffoldGenerator.generateFromSpec(
   spec: PrototypeSpec, module: ModuleDefinition, migrationVersion: Int
): Scaffold   // migrasi (tabel kolom nyata, bukan JSONB stub), RLS, grant, entri katalog,
              // repository + route fail-closed + test 403, potongan ModuleSchemaMap/registry
```
Deterministik (byte-per-byte identik untuk masukan sama); keluaran = **kandidat PR untuk ditinjau manusia**, tidak pernah menulis ke DB/pohon sumber.

### 3.5 JSON ke klien (C menyajikan; A mem-parse)
Field baru di `summaryObj` tak boleh mengubah field lama. Bentuk `form` mengikuti `ScreenSpec.form` di `InteractiveScreenCodec` (B).

---

## 4. Gelombang & Gerbang

```
G0  Kontrak (B, ±1 hari)      ┐  A: spike drag-extract (baca-saja) · C: Discovery Note + fixture pilot
G1  Paralel (A ∥ B ∥ C)       ┘  masing-masing di jalurnya terhadap kontrak
G2  Integrasi (urut)          ── B→C→A: chat-edit end-to-end, brief endpoint + tombol
G3  Pilot & pengerasan        ── bandingkan hasil, cek mata, dokumentasi
```

| Gerbang | Syarat lulus |
|---|---|
| **G0** | Kontrak §3 terbit sebagai kode (data class + codec + fixture JSON) dengan test; A dan C menandatangani "bisa dikerjakan terhadap ini" |
| **G1** | Tiap agent: DoD jalurnya (§5–7) hijau di worktree sendiri; tidak ada edit di luar kepemilikan |
| **G2** | Gabung berurutan B → C → A; setelah tiap gabung: `:core:jvmTest`, `:server:test`, kompilasi 4 target hijau |
| **G3** | Skenario §8 lulus dengan mata; teaching doc ada |

### 4.1 Perkiraan (kasar, belum dikalibrasi — untuk urutan, bukan janji)
| Jalur | G0 | G1 | G2 | G3 |
|---|---|---|---|---|
| A | spike 0,5 hr | 5–6 hr | 2 hr | 1–2 hr |
| B | **1 hr** | 5–6 hr | 2 hr | 1 hr |
| C | 1 hr (Discovery Note) | 6–7 hr | 2 hr | 2 hr |
Total jam dinding **±10–13 hari kerja** bila paralel; ±25–30 bila sekuensial. Risiko terbesar: C (F8-lite). Jika C molor, A dan B tetap menghasilkan prototype setara MVP + brief; yang tertunda hanya titik awal kode modul.

---

## 5. Aturan Kerja Bersama (semua agent)

### 5.1 Isolasi & cabang
- Setiap agent bekerja di **git worktree sendiri** (`isolation: worktree`), cabang `feat/proto-a-*`, `feat/proto-b-*`, `feat/proto-c-*` dari `main` terbaru. Satu PR kecil per butir kerja.
- **Rebase** ke `main` sebelum membuka PR; konflik = berhenti dan lapor koordinator, jangan menyelesaikan dengan menyunting berkas milik agent lain.
- Commit: pesan Indonesia ringkas, akhiri dengan `Co-Authored-By` sesuai pengaturan sesi. **Jangan commit/push ke `main`** langsung.

### 5.2 Standar repo (wajib; baca `.claude/CLAUDE.md` dan rules terkait)
- **Graphify dulu** untuk pencarian kode (`.claude/rules/graphify.md`); `graphify update .` setelah mengubah kode.
- DDD: domain murni tanpa framework; use case `[Verb][Noun]UseCase`; value object; tanpa `!!`.
- **Variabilitas**: konsep per tenant/industri = data; blok & tipe field = kode (tulis alasan di KDoc). Test wajib memakai **template non-default**; jalankan `scripts/audit-variability.sh` (target 0 temuan baru).
- **Ukuran file**: core ≤250/400, presentation ≤400/600, server ≤300/500, test ≤500/800. File yang sudah di atas batas tidak boleh bertambah (ratchet). Satu blok = satu file.
- **Design system**: nol literal `Color(0xFF…)`, pakai `Clay*`; komponen `designsystem/` buta domain.
- **Endpoint tulis fail-closed + test 403** (peran tak berwenang), baca-saja bila memungkinkan.
- Dokumentasi: **satu teaching doc per jalur** `docs/teaching/teaching-proto-{a|b|c}-<slug>.md` (hindari bentrok nama).

### 5.3 Verifikasi minimum per PR
```bash
./gradlew :core:jvmTest                       # + test baru
./gradlew :server:test --tests '<yang disentuh>'   # bila menyentuh server
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs :server:compileKotlin
scripts/audit-variability.sh
```
Android hanya di CI (SDK tak ada di mesin dev) — catat sebagai "belum diverifikasi lokal".

### 5.4 Jebakan lingkungan (sudah terjadi di sesi sebelumnya)
- **Cache inkremental Kotlin kadang rusak** ("Could not close incremental caches", error `Unresolved reference` di file yang tak disentuh). Ulangi perintah; bila berulang, hapus `build/` **di worktree sendiri** saja.
- **Cek mata hanya satu agent pada satu waktu** (port `3001`/`8081`, dan browser Playwright tunggal): **hanya A** menjalankan `dev.sh` dan browser; B dan C memverifikasi lewat test. Pada G2/G3 koordinator mengatur antrean.
- Server dev **tidak** mendaftarkan rute baru otomatis — restart `dev.sh` sebelum uji mata.
- Nomor migrasi: **hanya C** memakai `V90`+ (cek `ls server/src/main/resources/db/migration` saat mulai; jangan bentrok).
- Login uji: `/login` → "Owner wemade-demo" (halaman prototype), "Superadmin" untuk lainnya.

---

## 6. Jalur A — UI & Design System

**Misi:** blok-blok prototype setara MVP dan bisa diteruskan tim (satu tempat mengubah tampilan).

| # | Butir | Hasil | AC |
|---|---|---|---|
| A1 | **`ClayKanbanBoard` generik** di `designsystem/` (buta domain: kolom, kartu, `onMove`, `canMove`, slot kartu; drag + menu "Pindah ke…") | menggantikan `InteractiveKanban`; **paritas drag** pada `SamplingPipelineKanbanBoard` (hanya koordinator drag, kartu Sampling tetap) | perilaku drag Sampling identik (cek mata); salinan drag turun dari 4 → ≤3; `SamplingKanbanCard.kt` (511 baris) **tidak membesar** |
| A2 | **Blok Form** (`blocks/InteractiveForm.kt` + state) | tambah data dari `FormConfig`: validasi `required`/tipe, pesan galat, baris baru muncul di tabel/papan yang sama lewat sesi | form di Stok Kain menambah baris yang langsung muncul di tabel; nilai tak sah ditolak dengan pesan |
| A3 | **Hapus baris** (`PrototypeAction.Delete`) di tabel/kanban dengan konfirmasi | | baris hilang, dasbor ikut terhitung |
| A4 | **Panel Chat Edit** di `/builder/prototype` (G2) | kotak pesan → `POST /api/builder/draft/spec-ops` → operasi `SpecOp` → `SpecOpApplier` di klien → layar render ulang, riwayat + undo, harga ikut berubah | "tambah status Revisi setelah Dikerjakan" menambah kolom kanban dan tercatat |
| A5 | **Tombol "Ekspor brief"** (G2) | `POST /api/builder/draft/brief` dengan modul terpilih + log perubahan sesi; tampil Markdown + salin/unduh | brief memuat perubahan sesi; 404 tanpa draf ditangani |
| A6 | Perapian: lebar ~1280dp, tenant `bordir-uji` (non-rajut) | | cek mata terdokumentasi |

**Bergantung pada:** Kontrak §3.1 (A2, A3), §3.2 + §3.3b endpoint `spec-ops` (A4), §3.3 endpoint `brief` (A5). Mulai A1 segera (tak bergantung kontrak).
**Larangan:** menaruh logika aturan di Composable; literal warna; `Modifier.shadow()`.

---

## 7. Jalur B — Spec, Domain & Capture

**Misi:** model yang cukup untuk MVP (form, hapus, operasi spec) dan penangkap kebutuhan yang menghasilkan brief berguna bagi developer.

| # | Butir | Hasil | AC |
|---|---|---|---|
| B0 (G0) | **Terbitkan Kontrak §3.1–3.3** sebagai kode + codec + fixture | data class, `ScreenSpec.form`, `FieldSpec.required`, `PrototypeAction.Delete`, `SpecOp`, `CaptureEntry`, `RequirementsBrief` (kerangka) | test round-trip codec; A dan C menandatangani |
| B1 | Reducer: `Create` menegakkan `required`; `Delete`; test tenant kedua | | menolak kosong pada field wajib; hapus baris tak ada = galat siap-tampil |
| B2 | `InteractiveScreenFactory.form` + dukungan `WidgetRegistry.interactiveFor` untuk FORM; petunjuk `FormHints` di pack garment (field wajib Stok Kain) | | form garment interaktif; layar lama tetap statis bila bentuk tak cocok |
| B3 | **`SpecOpApplier`** + validator (fail-closed, batas 5 op/giliran), seed ditulis ulang pada rename | | tiap op: sah → spec baru; tak sah → pesan; tak ada fallback senyap; ≥ 2 template (garment + non-garment) |
| B4 | **Log tangkapan** (`CaptureEntry`) + **`RequirementsBrief` + `BriefRenderer.markdown`** (deterministik) | modul, entitas, field, status/transisi, perubahan, cakupan+harga (input dari `lines`), `customNeeds` | golden test Markdown byte-per-byte |
| B5 (G2) | **`SpecOpProposer`** (port) + `DeterministicSpecOpProposer` (kata kunci Indonesia: "tambah status X setelah Y", "ganti nama X jadi Z", "tambah kolom/field X") | | narasi emas (garment, bordir, sablon, non-konveksi) → operasi yang diharapkan; kalimat di luar kosakata → `Result.failure` dengan pesan jelas, bukan tebakan |

**Bergantung pada:** tidak ada (B0 membuka jalan A dan C). B4 memakai bentuk `lines` yang sudah ada.
**Larangan:** mengedit `app/**`/`server/**` (termasuk adaptor Koog — itu C6); menyebut istilah garment di mesin.

---

## 8. Jalur C — Persistensi & Handoff (F8-lite) + Pilot

**Misi:** spec yang disetujui menjadi **titik awal modul nyata** untuk tim (kandidat PR), dibuktikan pada satu modul pilot.

### 8.1 Modul pilot — **Permintaan Perubahan (Change Request)**, pack netral
Alasan memilih (koreksi dari usulan "Sampling"): Sampling tulis-tangan terlalu kaya (CAM, alur tahap, dialog detail) untuk dibandingkan 1:1. Pilot ini **netral industri** (tidak melanggar aturan Jalur B), kecil (kanban + tabel + form), dan **dogfood**: dipakai tim sendiri melacak permintaan customisasi klien — persis konsep software house. Entitas: `judul`, `peminta`, `prioritas` (ENUM), `status` (ENUM Baru→Ditinjau→Disetujui→Selesai, dengan transisi), `catatan`.
> **Keputusan terbuka (koordinator):** konfirmasi pilot ini, atau ganti dengan kekurangan nyata dari prospek pertama.

| # | Butir | Hasil | AC |
|---|---|---|---|
| C0 (G0) | **Discovery Note** pilot (skill `wemade-feature-discovery`) + fixture `EntitySpec/PrototypeSpec` pilot | file Note + fixture | jenis modul, uji variabilitas, I/O, governance, scope terisi |
| C1 | `generateFromSpec`: migrasi dari `EntitySpec` — kolom bertipe nyata (bukan JSONB stub), PK, FK tenant, indeks, `apply_tenant_rls_in`, grant `wemade_app`, entri katalog | | golden file; determinisme; tenant lain tak terlihat (RLS) |
| C2 | Keluaran kode: repository (Exposed), route CRUD **fail-closed** (`requireModuleAccess`), potongan `ModuleSchemaMap`/pendaftaran sesuai `module-integration-rules.md` §5.1 | | test 403 peran tak berwenang **ikut digenerate** |
| C3 | Terapkan keluaran ke pilot **di worktree sendiri** (tinjau manusia), jalankan terhadap Postgres uji | modul pilot hidup di dev | CRUD lewat API; RLS terbukti; Flyway bersih dari nol |
| C4 | `BuilderBriefRoutes.kt` — `POST /api/builder/draft/brief` (G2, kontrak §3.3) | | 403/401/404/400 dites; tidak menulis; tanpa menambah `DiscoveryRoutes.kt` |
| C6 (G2) | `BuilderSpecOpRoutes.kt` — `POST /api/builder/draft/spec-ops` (kontrak §3.3b) memakai `SpecOpProposer`; **opsional** adaptor Koog di `server/.../infrastructure/discovery/` dengan fallback ke deterministik | | 403/401 dites; keluaran LLM wajib lolos `SpecOpApplier` validator; tanpa kunci API = deterministik |
| C5 | Dokumentasi: **"Cara meneruskan hasil generate"** untuk tim (apa yang boleh diubah, apa yang sebaiknya lewat spec) | teaching doc | |

**Bergantung pada:** Kontrak §3.1 (model entitas) untuk C1; §3.3 (`RequirementsBrief`, `BriefRenderer`) untuk C4; §3.3b (`SpecOpProposer`) untuk C6.
**Larangan:** menjalankan migrasi ke DB bersama; menimpa pohon sumber otomatis; mengedit `domain/prototype/**`.

---

## 8b. Pembagian file rencana
Plan per agent (berdiri sendiri, untuk dirujuk agent): [A — UI](parallel/PLAN-proto-A-ui.md) · [B — Spec & Capture](parallel/PLAN-proto-B-spec-capture.md) · [C — Handoff & Pilot](parallel/PLAN-proto-C-handoff-pilot.md). Bila ada selisih, **plan induk ini yang berlaku** (kontrak §3).

## 9. Skenario Penerimaan Akhir (G3, dengan mata)
1. Garment ekspor: di Stok Kain, tambah baris lewat **Form**; tabel dan (bila relevan) dasbor ikut berubah; nilai tak sah ditolak.
2. Chat: "tambahkan status Revisi setelah Dikerjakan di papan sampling" → kolom baru muncul; "ganti nama status Selesai jadi Disetujui Buyer" → kartu ikut; permintaan di luar kosakata → pesan jelas.
3. Panel harga berubah mengikuti modul; **Ekspor brief** menampilkan modul, perubahan klien, cakupan+harga, dan `customNeeds`.
4. Pilot: spec Change Request → kandidat PR → diterapkan di worktree → CRUD API jalan, 403 untuk peran tak berwenang, data antar-tenant terisolasi.
5. Seorang developer **yang tidak mengikuti sesi ini** dapat memulai dari brief + kode pilot tanpa bertanya ulang (uji dokumen C5).

## 10. Risiko
| Risiko | Mitigasi |
|---|---|
| Kontrak G0 meleset, memaksa kerja ulang | G0 pendek; fixture dulu; perubahan kontrak = versi baru + kabar semua |
| Konflik file antar agent | Kepemilikan eksklusif §2; konflik = berhenti & lapor |
| C molor (F8-lite sulit ditebak) | A dan B mandiri; hasil minimum tetap prototype MVP + brief |
| Hasil generate buruk (lebih mahal daripada tulis tangan) | AC C3/C5; ukur: developer lebih cepat memulai dari hasil dibanding dari nol |
| Cek mata berebut port/browser | Hanya A; G2/G3 diantre koordinator |
| `HandoffScaffoldGenerator` salah menimpa sumber | Keluaran = kandidat PR; tidak pernah menulis otomatis |

## 11. Definition of Done (per jalur)
- [ ] AC semua butir jalur terpenuhi; tes domain (termasuk tenant non-default) hijau
- [ ] Tidak ada edit di luar kepemilikan; `wc -l` sesuai batas/ratchet
- [ ] Kompilasi JVM/WasmJS/JS/server hijau; Android = CI
- [ ] `scripts/audit-variability.sh` tanpa temuan baru; `graphify update .`
- [ ] Endpoint tulis (bila ada) fail-closed + test 403
- [ ] Teaching doc jalur; PR kecil, rebase bersih, dilaporkan ke koordinator
