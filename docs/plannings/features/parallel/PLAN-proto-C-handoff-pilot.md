# PLAN — Agent C: Persistensi & Handoff F8-lite + Pilot (Prototype Setara MVP)

**Agent:** C · **Tanggal:** 2026-10-04 · **Induk:** [PLAN-prototype-mvp-parallel-agents](../PLAN-prototype-mvp-parallel-agents.md)

> File ini berdiri sendiri untuk satu agent. Bila selisih dengan plan induk, **plan induk (kontrak §3) yang berlaku**.

---

## 0. Konteks (baca dulu)

**Produk:** alat **software house** — klien bercerita dan mencoba prototype interaktif di `/builder/prototype`; tim kita mendapat **brief** (apa yang dibangun, mana yang sudah ada di katalog, berapa biayanya) dan **titik awal kode** yang diteruskan tim. Bukan pembuat aplikasi otomatis. UI/UX akhir diubah tim.

**Target:** prototype **setara MVP** — (1) berfungsi: tambah/ubah/hapus data, aturan alur ditegakkan, (2) mudah diteruskan: komponen Clay bersama + spec sebagai data, (3) menangkap kebutuhan klien terstruktur.

**Di luar lingkup (jangan dikerjakan):** serah-terima antarmodul otomatis (F4), versi beku/publish (F6), migrasi perubahan merusak (F10), migrasi massal modul live (F9).

**Dokumen induk (sumber kebenaran bila ada selisih):** [`../PLAN-prototype-mvp-parallel-agents.md`](../PLAN-prototype-mvp-parallel-agents.md). Latar: [`../../trd/TRD-PLAT-003-interactive-prototype.md`](../../trd/TRD-PLAT-003-interactive-prototype.md) dan teaching doc `docs/teaching/teaching-builder-*.md` (kanban, tabel, dasbor-checklist, seed, price-panel — **baca yang relevan sebelum mengubah kodenya**).

**Tiga jalur paralel — satu pemilik per file:**
| Agent | Jalur | Memiliki |
|---|---|---|
| **A** | UI & design system | `app/shared/**` (designsystem, `presentation/discovery/**`, `presentation/builder/**`, drag Sampling untuk paritas) |
| **B** | Spec, domain & capture | `core/.../domain/prototype/**`, `InteractiveScreenCodec`, `WidgetRegistry`, `DomainPack*`, `GarmentScreenSuggestions`, `domain/discovery/brief/**` |
| **C** | Persistensi & handoff | `HandoffScaffoldGenerator*`, `server/**`, migrasi `V90+`, pack netral + modul pilot |

Menyunting berkas milik agent lain **dilarang**. Butuh perubahan di sana → ajukan lewat kontrak (§3) ke koordinator; konflik → berhenti dan lapor.

## 1. Baseline (terverifikasi di commit `7cc47fa`, 2026-10-04)
- `core/.../domain/prototype/`: `EntitySpec`/`FieldSpec`(TEXT, NUMBER, DATE, ENUM, BOOL)/`StateMachine`, `PrototypeSpec`/`ScreenSpec` (konfigurasi `kanban`/`table`/`checklist`/`dashboard`, `entityId` nullable hanya untuk dasbor), `PrototypeStore`, `PrototypeReducer` (`SetField`, `Create`, `moveCard`), `InteractiveScreenFactory` (adaptor baris lama → spec), `TableView`, `DashboardSpec` (`CountSpec`, `DashboardEvaluator`), hints pack (`KanbanHints`, `TableHints`, `DashboardHints`).
- UI: `InteractiveKanban(State)`, `InteractiveTable(State)`, `InteractiveChecklist(State)`, `InteractiveDashboard(State)`, `InteractiveBlock` (dispatcher atas `sealed interface PlayableState`), `PrototypeSession` (sesi bersama lintas layar), `PrototypeRenderer`, `PrototypePricePanel`.
- Data: `GarmentExportSeed` (satu sumber PO/pembeli/artikel garment ekspor), `GarmentScreenSuggestions`.
- Server: `/api/builder/draft` (bootstrap), `/api/builder/draft/price?modules=` (baca-saja), `DiscoverySummary.kt`, `DiscoveryPriceJson.kt`.
- Handoff: `HandoffScaffoldGenerator` menghasilkan **tabel stub** (`payload JSONB`), bukan dari spec.
- **Utang:** drag kanban ditulis **4×** (CRM, Sampling, operator, prototype); `DiscoveryRoutes.kt` 410 baris (jangan menambah); Android SDK tidak ada di mesin dev (hanya CI).

## 2. Misi & Lingkupmu — Agent C (Persistensi & Handoff F8-lite + Pilot)

**Misi:** spec yang disetujui menjadi **titik awal modul nyata** untuk tim (kandidat PR yang ditinjau manusia), dibuktikan pada **satu modul pilot**; plus endpoint brief dan pengusul operasi. **Risiko terbesar dan paling sulit ditebak** — kerjakan bertahap dan lapor lebih awal bila meleset.

**Kamu memiliki (boleh sunting):** `core/.../domain/discovery/HandoffScaffoldGenerator*` + `domain/discovery/handoff/**` (baru), `server/src/main/**` (route baru `BuilderBriefRoutes.kt`, `BuilderSpecOpRoutes.kt`, `DiscoveryRouteFactory.kt`, `DiscoverySummary.kt`, `DiscoveryPriceJson.kt`, adaptor di `infrastructure/discovery/`), `server/src/main/resources/db/migration/V90+`, **pack netral + modul pilot** (`core/.../domain/pack/GenericPilotPack*.kt` baru + satu entri di `DomainPackRegistry`), tes: `core/**/discovery/Handoff*`, `server/src/test/**`.
**Dilarang:** `app/**`, `domain/prototype/**` (B), `GarmentScreenSuggestions`/`DomainPackCodec` (B), `DiscoveryRoutes.kt` (jangan menambah barisnya — 410, soft 300; pecah bila harus menyentuh).

### Modul pilot — "Permintaan Perubahan" (Change Request), pack netral
Alasan: Sampling tulis-tangan terlalu kaya untuk dibandingkan 1:1; fitur **konveksi baru tidak boleh ditulis pertama kali di repo Jalur B** (`CLAUDE.md` Status Repo #5) — pilot ini netral industri, kecil (kanban + tabel + form), dan **dipakai tim sendiri** melacak permintaan customisasi klien.
Entitas `change_request`: `judul` (TEXT, wajib), `peminta` (TEXT), `prioritas` (ENUM: Rendah/Sedang/Tinggi), `status` (ENUM: Baru → Ditinjau → Disetujui → Selesai; transisi linear + mundur satu langkah), `catatan` (TEXT).
> **Keputusan terbuka (koordinator):** konfirmasi pilot ini atau ganti dengan kekurangan nyata dari prospek. Jangan mulai C1 sebelum dikonfirmasi; C0 boleh jalan.

### Butir kerja
**C0 — Discovery Note + fixture pilot** (Gelombang 0)
- Jalankan skill `wemade-feature-discovery` untuk pilot → Discovery Note (jenis modul: operasional atau fitur? — putuskan lewat tangga keputusan; uji variabilitas; I/O port; governance: gate, level akses per operasi, `ScopeCapability`, peran yang **ditolak** untuk test 403). Simpan di `docs/plannings/discovery-PROTO-pilot-change-request.md`.
- Fixture `EntitySpec`/`PrototypeSpec` pilot sebagai konstanta test (kerjakan terhadap Kontrak §3.1; selaraskan nama tipe setelah B0 terbit).
- **AC:** Note lengkap (8 bagian template); pilihan `ModuleKind` beralasan; daftar peran-ditolak jelas.

**C1 — `generateFromSpec`: migrasi dari `EntitySpec`**
- Dari `PrototypeSpec` + `ModuleDefinition` hasilkan migrasi Flyway: `CREATE SCHEMA <kode modul>`, tabel dengan **kolom bertipe nyata** (TEXT→`TEXT`/`VARCHAR`, NUMBER→`NUMERIC`, DATE→`DATE`, ENUM→`VARCHAR` + `CHECK` dari opsi, BOOL→`BOOLEAN`), `id` PK, `tenant_id` FK, `required`→`NOT NULL`, indeks, `apply_tenant_rls_in('<schema>','<tabel>')`, grant + `ALTER DEFAULT PRIVILEGES` untuk `wemade_app`, entri `module_catalog_entries` (kunci **code**), backfill entitlement/`custom_roles` (kunci **NAME** enum) — pola **V27/V64/V76/V77** (`module-integration-rules.md` §5.1). **Jangan tertukar NAME vs code.**
- Pertahankan jalur lama (`generate(pack, version)` stub) agar test lama tetap hijau; tambahkan `generateFromSpec`.
- **AC:** golden file; **deterministik** (masukan sama → byte-per-byte sama); test: migrasi bersih di Postgres uji dari nol; tenant lain tak melihat baris (RLS) — pola test RLS yang sudah ada.

**C2 — Keluaran kode: repository + route fail-closed + test 403**
- Hasilkan (sebagai file kandidat): repository Exposed (`Table("<kode>.<nama>")`, daftarkan di `ModuleSchemaMap`), route CRUD memakai `requireModuleAccess`/`moduleDecision` **fail-closed** (`mayEditWithoutDecision` — lihat `TenantStageFlowRoutes.kt` sebagai contoh), pendaftaran di `ServerRouteWiring`, dan **test 403 untuk peran tak berwenang yang ikut digenerate**.
- Ikuti anatomi pendaftaran `module-integration-rules.md` §5 sesuai jenis modul hasil C0 (`BusinessModule`, `OperationalModuleCatalog`/`PortDataTypeRegistry` bila operasional, `CustomRole.createFactoryPresets`, dst. — **kandidat yang ditinjau manusia**, bukan penulisan otomatis ke pohon sumber).
- **AC:** keluaran menyertakan semua titik pendaftaran §5 untuk jenis modulnya (daftar periksa di KDoc); test 403 hasil generate lulus saat diterapkan.

**C3 — Terapkan ke pilot di worktree & uji**
- Terapkan keluaran C1–C2 secara manual (tinjauan manusia) di **worktreemu**, jalankan terhadap Postgres uji, CRUD lewat API. Catat **semua yang harus kamu ubah manual** setelah generate — itu ukuran kualitas generator dan bahan C5.
- **AC:** CRUD lewat API jalan; RLS terbukti; Flyway bersih dari nol; peran tak berwenang 403; jumlah edit manual dilaporkan.

**C4 — `POST /api/builder/draft/brief`** (G2; bergantung B4)
- `BuilderBriefRoutes.kt` per Kontrak §3.3. Pola: `BuilderPriceRoutes.kt` (gerbang `gate()`, hanya membaca draf yang ada, tidak menulis). `coverage` dari `PriceDiscoveryDraftUseCase.lines` (`onlyModuleIds = included`); panggil `BriefRenderer` milik B.
- **AC (server test, tanpa Postgres seperti `BuilderDraftBootstrapTest`):** 403 peran tak berwenang tanpa efek samping; 401 tanpa kredensial; 404 tanpa draf; 400 modul asing; **jalur sukses** dengan katalog in-memory bila memungkinkan (injeksi lewat parameter, bukan Postgres).

**C5 — "Cara meneruskan hasil generate"** (dokumen untuk tim)
- Teaching/how-to: apa yang dihasilkan generator, apa yang **boleh langsung diubah** tim (UI/UX, aturan khusus), apa yang sebaiknya lewat spec, dan cara menjalankan ulang generator tanpa menimpa suntingan. Sertakan daftar edit manual dari C3.
- **AC:** developer yang tidak ikut sesi bisa memulai dari dokumen + brief + kode pilot tanpa bertanya.

**C6 — `POST /api/builder/draft/spec-ops`** (G2; bergantung B5)
- `BuilderSpecOpRoutes.kt` per Kontrak §3.3b memakai `SpecOpProposer` (B). **Opsional:** adaptor LLM (Koog) di `server/.../infrastructure/discovery/` di belakang port yang sama, **fallback ke `DeterministicSpecOpProposer`** tanpa kunci API; **keluaran LLM wajib lolos validator `SpecOpApplier`** (jangan percaya LLM; pola `DiscoveryDraftValidator`).
- **AC:** 403/401 dites; tanpa kunci API = deterministik; keluaran LLM yang tak sah ditolak; tidak menerapkan apa pun ke state.

### Urutan & ketergantungan
`C0 (G0) → [setelah B0 + konfirmasi pilot] C1 → C2 → C3 → C5`, dan `[G2] C4 (butuh B4) → C6 (butuh B5)`. C1–C3 tak bergantung A. **Jangan** mengejar C6-LLM sebelum C1–C3 selesai.

### Definition of Done — C
- [ ] AC C0–C6 terpenuhi; golden test deterministik; tes tenant kedua
- [ ] Endpoint baru: fail-closed + test 403/401/404; **tidak menulis** (brief & spec-ops baca-saja); `DiscoveryRoutes.kt` tidak bertambah
- [ ] Migrasi hanya `V90+`, tidak ada migrasi ke DB bersama; Flyway bersih dari nol (Postgres uji)
- [ ] `wc -l` sesuai batas (server 300/500); ratchet bila menyentuh file besar
- [ ] `./gradlew :core:jvmTest :server:test` (yang disentuh) hijau; kompilasi 4 target hijau; `scripts/audit-variability.sh` 0 temuan baru; `graphify update .`
- [ ] Discovery Note + teaching doc `docs/teaching/teaching-proto-c-<slug>.md` + dokumen C5; PR kecil, laporan sesuai format

## 3. Kontrak antar-agent (dibekukan di Gelombang 0 oleh B; perubahan = versi baru + kabar semua)

Kembangkan terhadap kontrak ini dengan **fixture**; jangan menunggu implementasi agent lain. Bentuk di bawah adalah niat; **B0 menerbitkannya sebagai kode** dan itu yang berlaku begitu dimerge.

**3.1 Model (B).** `FieldSpec` + `val required: Boolean = false`. `FormConfig(fields: List<String>, submitLabel = "Simpan")`; `ScreenSpec.form: FormConfig?` (widget `FORM` ⇔ `form != null`, `entityId` wajib). `PrototypeAction.Delete(entityId, rowId)`. `PrototypeReducer.reduce`: `Create` menegakkan `required`; pesan galat berbahasa pengguna.

**3.2 SpecOp (B).**
```kotlin
sealed interface SpecOp {
    data class AddEnumOption(val entityId: String, val field: String, val option: String, val after: String? = null) : SpecOp
    data class RenameEnumOption(val entityId: String, val field: String, val from: String, val to: String) : SpecOp
    data class AddTransition(val entityId: String, val field: String, val from: String, val to: String) : SpecOp
    data class AddField(val entityId: String, val field: FieldSpec) : SpecOp
    data class RenameFieldLabel(val entityId: String, val key: String, val label: String) : SpecOp
}
object SpecOpApplier { fun apply(screen: InteractiveScreen, op: SpecOp): Result<InteractiveScreen> }
```
Gagal = `Result.failure` berpesan siap-tampil; operasi di luar kosakata tak bisa dibentuk (sealed). ≤ 5 operasi per giliran; tiap operasi diterapkan satu per satu (yang gagal tak membatalkan yang sah). Rename status menulis ulang seed.

**3.3 Brief (B model + renderer; C endpoint; A tombol).**
```kotlin
data class CaptureEntry(val at: String, val op: SpecOp, val ok: Boolean, val message: String?)
data class RequirementsBrief(val packCode: String, val modules: List<BriefModule>, val changes: List<CaptureEntry>,
                             val coverage: List<BriefCoverage>, val customNeeds: List<String>)
object BriefRenderer { fun markdown(brief: RequirementsBrief): String }   // deterministik
```
Log perubahan **tidak disimpan di server** (state prototype = memori sesi klien). Endpoint **(C):** `POST /api/builder/draft/brief`, body `{ "included": ["moduleId"], "changes": [CaptureEntry] }` → `{ "markdown": "...", "brief": {...} }`; gerbang builder, tidak menulis apa pun, 404 tanpa draf, 400 modul asing, 403/401 seperti `GET /draft`. `coverage` dirakit dari `PriceDiscoveryDraftUseCase.lines`.

**3.3b Pengusul operasi (B port + deterministik; C endpoint).**
```kotlin
interface SpecOpProposer { suspend fun propose(message: String, screen: InteractiveScreen): Result<List<SpecOp>> }
class DeterministicSpecOpProposer : SpecOpProposer   // kata kunci Indonesia, tanpa LLM
```
Endpoint **(C, G2):** `POST /api/builder/draft/spec-ops`, body `{ "message", "screenId", "spec": <InteractiveScreen JSON> }` → `{ "ops": [...], "reply": "..." }`. Tidak menerapkan apa pun; klien menerapkan lewat `SpecOpApplier`. Adaptor LLM (Koog) = butir C6, di belakang port yang sama dan wajib melewati validator yang sama.

**3.4 Handoff (C).** `HandoffScaffoldGenerator.generateFromSpec(spec: PrototypeSpec, module: ModuleDefinition, migrationVersion: Int): Scaffold` — migrasi dengan kolom nyata, RLS, grant, entri katalog, repository + route fail-closed + test 403. Deterministik byte-per-byte. Keluaran = **kandidat PR untuk ditinjau manusia**, tak pernah menulis ke DB/pohon sumber.

**3.5 JSON ke klien (C menyajikan, A mem-parse).** Field baru di `summaryObj` tidak boleh mengubah field lama. Bentuk `form` mengikuti `InteractiveScreenCodec` (B).

## 4. Aturan kerja (semua agent)

**Cabang & PR.** Worktree sendiri (`isolation: worktree`), cabang `feat/proto-<huruf-agent>-<slug>` dari `main` terbaru. **Jangan commit/push ke `main`.** Satu PR kecil per butir. Rebase ke `main` sebelum PR; konflik = berhenti dan lapor. Commit berbahasa Indonesia ringkas, akhiri `Co-Authored-By` sesuai pengaturan sesi.

**Standar repo** (baca `.claude/CLAUDE.md` + rules):
- **Graphify dulu** untuk pencarian kode (`.claude/rules/graphify.md`); jalankan `graphify update .` setelah mengubah kode.
- DDD: domain murni tanpa framework; use case `[Verb][Noun]UseCase`; value object; tanpa `!!`; `Result<T>`.
- **Variabilitas**: beda per tenant/industri/admin → **data**; blok & tipe field → kode (alasan di KDoc). Test wajib memakai **template non-default** (fixture non-garment). `scripts/audit-variability.sh` harus 0 temuan baru.
- **Ukuran file**: core ≤250/400, presentation ≤400/600, server ≤300/500, test ≤500/800 (soft/hard). File yang sudah di atas batas **tidak boleh bertambah** (ratchet — catat `wc -l` sebelum/sesudah). Satu blok = satu file; dilarang `…Part2/Extra/Helpers` tanpa tema.
- **Design system**: nol literal `Color(0xFF…)` di luar `WeMadeTheme.kt`; pakai `ClayCard/ClayButton/ClayBadge/ClayTag/claySurface/clayFlat` + token `ClayShapes/ClayBorder/ClaySpacing`; dilarang `Modifier.shadow()` dan `Card`/`Button` Material mentah; komponen `designsystem/` **buta domain**.
- **Endpoint tulis fail-closed** + test peran tak berwenang (403). Utamakan baca-saja.
- **Dokumentasi:** satu teaching doc untuk jalurmu, `docs/teaching/teaching-proto-<huruf>-<slug>.md` (skill `teaching`).

**Verifikasi minimum per PR:**
```bash
./gradlew :core:jvmTest
./gradlew :server:test --tests '<yang-disentuh>'          # bila menyentuh server
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs :server:compileKotlin
./gradlew :app:shared:jvmTest                              # bila menyentuh app
scripts/audit-variability.sh
```
Android hanya di CI — tulis "belum diverifikasi lokal" di laporan.

**Jebakan lingkungan (sudah terjadi):**
- Cache inkremental Kotlin kadang rusak ("Could not close incremental caches", `Unresolved reference` di file yang tak disentuh). Ulangi perintah; bila berulang, hapus `build/` **di worktreemu saja**.
- **Cek mata hanya Agent A** pada satu waktu (port `3001`/`8081` dan browser Playwright tunggal). Agent lain memverifikasi lewat test. G2/G3 diantre koordinator.
- Server dev tidak mendaftarkan rute baru otomatis: restart `./dev.sh` sebelum uji mata.
- Login uji: `/login` → tombol "Owner wemade-demo" (halaman prototype) atau "Superadmin".
- Nomor migrasi: **hanya C** memakai `V90+` (cek `ls server/src/main/resources/db/migration`).
- Jangan menjalankan migrasi ke DB bersama.

## Format laporan ke koordinator (setiap PR / akhir gelombang)
1. Butir yang selesai + tautan PR/cabang.
2. Hasil perintah verifikasi (ringkas, sertakan kegagalan apa adanya).
3. `wc -l` sebelum → sesudah untuk file di atas soft limit.
4. Yang **belum** diverifikasi (mis. Android, lebar ~1280dp) dan temuan/keputusan terbuka.
5. Perubahan kontrak yang kamu butuhkan (jika ada) — jangan menyunting berkas milik agent lain.
