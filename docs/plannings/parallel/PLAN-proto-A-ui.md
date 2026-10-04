# PLAN — Agent A: UI & Design System (Prototype Setara MVP)

**Agent:** A · **Tanggal:** 2026-10-04 · **Induk:** [PLAN-prototype-mvp-parallel-agents](../PLAN-prototype-mvp-parallel-agents.md)

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

## 2. Misi & Lingkupmu — Agent A (UI & Design System)

**Misi:** blok prototype setara MVP dan **mudah diteruskan tim**: satu tempat mengubah tampilan tiap jenis blok, form untuk menambah data, chat edit, dan tombol brief.

**Kamu memiliki (boleh sunting):** `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/designsystem/**`, `presentation/discovery/Interactive*.kt`, `PrototypeSession.kt`, `PrototypeRenderer.kt`, `InteractiveBlock.kt`, `DiscoveryUiModel.kt`, `DraftPriceUi.kt`, `PrototypePricePanel.kt`, `presentation/discovery/blocks/**` (baru), `presentation/builder/**`, dan — **hanya untuk paritas A1** — `presentation/sampling/components/SamplingDragDropState.kt` & `SamplingPipelineKanbanBoard.kt`.
**Dilarang:** `core/**`, `server/**`, `presentation/crm/**`, `SamplingKanbanCard.kt` dan komponen Sampling lain.

### Butir kerja
**A1 — `ClayKanbanBoard` generik di `designsystem/`** (tak bergantung kontrak — mulai segera)
- Buta domain: terima daftar kolom (`id`, `title`, `count`), kartu (generik `<T>` dengan slot `@Composable`), `onMove(cardId, toColumnId)`, `canMove(cardId, toColumnId): Boolean`, tint/sorotan kolom tujuan. Drag (koordinat root, `graphicsLayer`, `zIndex` kolom pemegang kartu) + **menu "Pindah ke…"** sebagai jalur tanpa drag. Pelajari pola yang sudah terbukti di `InteractiveKanbanState`/`InteractiveKanban` dan `CrmDragDropState` (teaching: `teaching-crm-kanban-drag-drop-jira-experience.md`).
- Pakai ulang visual `ClayKanbanColumn` bila cocok (Aturan Tiga Kali); jangan membuat gaya ke-5.
- Ganti `InteractiveKanban` agar memakai komponen ini. **Paritas:** adopsi **hanya koordinator drag** di `SamplingPipelineKanbanBoard` (kartu Sampling tetap utuh); perilaku drag Sampling harus identik (cek mata).
- **AC:** salinan logika drag turun dari 4 → ≤3; `SamplingKanbanCard.kt` (511 baris) tidak membesar; `SamplingPipelineKanbanBoard.kt` (332) tidak membesar; cek mata: drag sah, drag terlarang ditolak dengan pesan, menu "Pindah ke…" bekerja, lebar ~1280dp.
- **Waspada:** kolom kosong harus bisa menjadi target drop (mis. "Lini 3"); gestur drag di dalam `horizontalScroll`.

**A2 — Blok Form** (bergantung Kontrak §3.1 — kerjakan dengan fixture sampai B0 dimerge)
- `presentation/discovery/blocks/InteractiveForm.kt` + `InteractiveFormState.kt`; tambahkan `InteractiveFormState` ke `sealed interface PlayableState` dan cabang di `InteractiveBlock` (kompilator akan memaksa `when` lengkap).
- Render field dari `FormConfig` memakai `ClayTextField`/`ClayChoiceChip`/`ClayCheckbox` sesuai `FieldType`; submit → `PrototypeAction.Create` lewat `PrototypeReducer`; id baris unik; tampilkan pesan galat reducer apa adanya (sudah berbahasa pengguna).
- Baris baru **langsung muncul** di tabel/papan layar sumber lewat `PrototypeSession` (state hidup bersama).
- **AC:** form Stok Kain menambah baris yang tampil di tabel; field `required` kosong ditolak dengan pesan; nilai tak sah (angka/pilihan) ditolak; tidak ada logika aturan di Composable.

**A3 — Hapus baris** (Kontrak §3.1 `PrototypeAction.Delete`)
- Tabel: aksi hapus per baris + konfirmasi (komponen Clay). Kanban: aksi pada kartu (di menu kartu). Dasbor ikut terhitung ulang (otomatis lewat sesi).
- **AC:** hapus kartu SPK menurunkan "SPK sampling berjalan"; konfirmasi mencegah hapus tak sengaja.

**A4 — Panel Chat Edit** (G2; bergantung §3.2 + §3.3b endpoint `spec-ops` dan butir C6)
- Kotak pesan di `/builder/prototype`: kirim ke `POST /api/builder/draft/spec-ops` → terima `ops` → terapkan satu per satu dengan `SpecOpApplier` pada layar yang dipilih → **ganti layar di `PrototypeSession`** (state blok dibuat ulang dari spec baru; pertahankan baris yang ada) → render ulang.
- Riwayat pesan + daftar operasi (sah/gagal + pesan), **undo/redo** (tumpukan spec sesi), dan **log `CaptureEntry`** yang disimpan di memori sesi untuk A5.
- Harga ikut berubah bila modul/layar berubah (panel harga sudah reaktif terhadap modul terpilih).
- **AC:** "tambahkan status Revisi setelah Dikerjakan" → kolom baru tampil di papan Sampling dan tercatat; kalimat di luar kosakata → pesan jelas tanpa merusak layar; undo mengembalikan.

**A5 — Tombol "Ekspor brief"** (G2; bergantung endpoint `brief`)
- `POST /api/builder/draft/brief` dengan `included` (modul terpilih) + `changes` (log sesi); tampilkan Markdown (komponen Clay), tombol salin/unduh; tangani 404/400/403.
- **AC:** brief memuat perubahan sesi; error ditampilkan jelas; tidak ada literal warna.

**A6 — Perapian & verifikasi akhir**
- Lebar sempit ~1280dp; tenant uji non-rajut `bordir-uji` (jalankan ke layar, bukan hanya test); lompatan layout setelah drag; fokus keyboard form.

### Urutan & ketergantungan
`A1 (segera) → A2/A3 (setelah B0 dimerge ke main; sebelumnya dengan fixture) → [G2] A4, A5 → A6`. Rebase ke `main` setelah B0 dan setelah C mem-merge endpoint.

### Definition of Done — A
- [ ] AC A1–A6 terpenuhi, **dilihat dengan mata** (login → `/builder/prototype`; hanya kamu yang memegang browser)
- [ ] Nol literal warna baru; tak ada `Modifier.shadow()`; komponen `designsystem/` buta domain
- [ ] `wc -l` file yang disentuh sesuai batas/ratchet (khususnya Sampling & `PrototypeRenderer.kt`)
- [ ] Kompilasi JVM/WasmJS/JS hijau + `:app:shared:jvmTest`; Android = CI
- [ ] `graphify update .`, `scripts/audit-variability.sh` 0 temuan baru
- [ ] Teaching doc `docs/teaching/teaching-proto-a-<slug>.md`; PR kecil, rebase bersih, laporan sesuai format

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
