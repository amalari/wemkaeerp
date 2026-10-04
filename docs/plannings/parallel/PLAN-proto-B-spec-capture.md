# PLAN — Agent B: Spec, Domain & Capture (Prototype Setara MVP)

**Agent:** B · **Tanggal:** 2026-10-04 · **Induk:** [PLAN-prototype-mvp-parallel-agents](../PLAN-prototype-mvp-parallel-agents.md)

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

## 2. Misi & Lingkupmu — Agent B (Spec, Domain & Capture)

**Misi:** model yang cukup untuk MVP (form, hapus, operasi spec) dan penangkap kebutuhan yang menghasilkan **brief yang benar-benar membantu developer**. Kamu menerbitkan **kontrak** yang membuka jalan A dan C — itu prioritas pertama.

**Kamu memiliki (boleh sunting):** `core/src/commonMain/kotlin/com/eventverse/app/domain/prototype/**`, `shared/pack/InteractiveScreenCodec.kt`, `shared/pack/DomainPackCodec.kt`, `domain/discovery/WidgetRegistry.kt`, `domain/pack/DomainPack.kt`, `domain/pack/GarmentScreenSuggestions.kt`, `domain/pack/GarmentExportSeed.kt`, `domain/discovery/brief/**` (baru), `domain/discovery/usecases/Capture*` (baru), tes terkait di `core/src/commonTest/**`.
**Dilarang:** `app/**`, `server/**`, `HandoffScaffoldGenerator*` (C), adaptor Koog (C6).

### Butir kerja
**B0 — TERBITKAN KONTRAK (Gelombang 0, tenggat ±1 hari, prioritas tertinggi)**
- Tulis **kode** untuk §3.1–§3.3b: `FieldSpec.required`, `FormConfig`, `ScreenSpec.form` (validasi konstruktor: `FORM` ⇔ `form != null`, field form ada di entitas, `entityId` wajib), `PrototypeAction.Delete`, `SpecOp` (+ `SpecOpApplier` **kerangka** yang sudah mengembalikan `Result.failure("belum didukung")` per jenis), `CaptureEntry`, `RequirementsBrief`/`BriefModule`/`BriefCoverage`, `BriefRenderer` kerangka, `SpecOpProposer` + `DeterministicSpecOpProposer` kerangka.
- Codec: `InteractiveScreenCodec` memuat `form`; `CaptureEntry`/`SpecOp` punya encode/decode JSON (A dan C butuh untuk body endpoint). **Kompatibel mundur** — spec lama tanpa `form`/`required` tetap terbaca.
- Fixture: contoh JSON `InteractiveScreen` + `SpecOp` + `CaptureEntry` di `core/src/commonTest/resources` atau konstanta test yang bisa dipakai A dan C.
- **AC:** test round-trip codec (termasuk spec lama), semua test `core` hijau, file ≤250 baris soft. Setelah koordinator memerge, **kabari A dan C** bahwa kontrak terbit.
- **Catatan ukuran:** `DomainPackCodec.kt` sudah ±308 baris (soft 250, hard 400). Pecah bagian `screenSuggestions` ke `ScreenSuggestionCodec.kt` bila akan bertambah — pemecahan mengikuti tanggung jawab, bukan baris.

**B1 — Reducer: `required`, `Delete`**
- `Create` menolak field `required` kosong (pesan: nama field berbahasa pengguna); `Delete` baris tak ada → galat siap-tampil; hapus tidak merusak baris lain.
- **AC:** test dengan fixture **non-garment**; `PrototypeStore` tetap immutable; pesan tidak memuat nama teknis.

**B2 — Blok Form di factory & registry**
- `InteractiveScreenFactory.form(...)`; `WidgetRegistry.interactiveFor` melayani `WidgetKind.FORM`; `FormHints` di data pack (daftar field wajib/urutan) — tambah ke `ScreenSuggestion` + codec **opsional** (kompatibel mundur). Isi hints Form untuk Stok Kain garment (contoh: Bahan, Stok, Kepemilikan wajib).
- Entitas form harus **sama** dengan entitas tabel sumbernya supaya baris baru tampil di tabel lewat `PrototypeSession` — nyatakan di KDoc bagaimana A menghubungkan form → layar sumber (mis. `targetModuleId`).
- **AC:** form garment interaktif; layar lama berbentuk tak cocok tetap `null` → statis; round-trip codec pack.

**B3 — `SpecOpApplier` penuh**
- Implementasikan lima operasi. Aturan: id entitas/field/opsi tak ada → pesan jelas; `AddEnumOption` menambahkan opsi ke `FieldSpec.options` **dan** `KanbanConfig.columns` bila field itu field kelompok kanban (posisi `after`); `RenameEnumOption` menulis ulang **seed**, `StateMachine`, dan `columns`; `AddTransition` hanya antar opsi yang ada; `AddField` menambah field ke entitas (+ ke `TableConfig.columns`/`FormConfig.fields` bila layar itu menampilkan semua field? — **putuskan dan dokumentasikan**, jangan menebak diam-diam); `RenameFieldLabel` hanya mengubah label tampil.
- Spec hasil harus lolos validasi konstruktor `PrototypeSpec` (jangan melonggarkan validasi demi operasi).
- **AC:** tiap operasi sah → spec baru yang valid; tiap operasi tak sah → `Result.failure` berpesan; **dua template** (garment + non-garment) diuji; batas 5 operasi per giliran ditegakkan di `applyAll`.

**B4 — Log tangkapan, `RequirementsBrief`, `BriefRenderer.markdown`**
- `RequirementsBrief` berisi: modul dipakai (layar, entitas, field, status/transisi), `changes` (urut waktu), `coverage` per modul (sudah-ada vs perlu-dibangun + harga dari `lines`), `customNeeds` (kebutuhan di luar blok → ditandai `CUSTOM_EXTENSION`).
- `BriefRenderer.markdown` **deterministik**; susunan ditujukan untuk developer yang tidak ikut sesi: ringkasan, modul & layar, perubahan klien, yang sudah ada vs perlu dibangun, harga, kebutuhan kustom.
- **AC:** **golden test** Markdown byte-per-byte (garment + non-garment); modul tanpa perubahan tetap tercetak; tidak ada istilah garment di renderer (isinya datang dari data).

**B5 — `DeterministicSpecOpProposer`** (G2)
- Ubah kalimat bahasa Indonesia → `SpecOp`: "tambah(kan) status X setelah Y", "ganti nama (status) X jadi Z", "tambah kolom/field X", "izinkan X ke Y" (transisi). Pencocokan **kata kunci**, tanpa LLM/kunci API. Kalimat yang tak dikenal → `Result.failure` dengan pesan yang menyebut contoh kalimat yang didukung — **bukan tebakan**.
- **AC:** set kalimat emas (garment, bordir, sablon, non-konveksi) → operasi yang diharapkan; kalimat ngawur/berbahaya ditolak; ≤ 5 operasi.

### Urutan & ketergantungan
`B0 (segera, merge cepat) → B1 → B2 → B3 → B4 → [G2] B5`. B tidak bergantung pada agent lain. **A dan C menunggu B0** — kabari begitu dimerge.

### Definition of Done — B
- [ ] AC B0–B5 terpenuhi; test domain murni tanpa dependensi eksternal; **tenant kedua** (non-garment) di setiap fitur
- [ ] Kompatibel mundur: draf/pack lama tetap terbaca; tak ada fallback senyap (tolak, bukan tebak)
- [ ] `wc -l` ≤ batas core (250/400); `DomainPackCodec.kt` tidak melewati 400
- [ ] `./gradlew :core:jvmTest` hijau; kompilasi 4 target hijau; `scripts/audit-variability.sh` 0 temuan baru; `graphify update .`
- [ ] Teaching doc `docs/teaching/teaching-proto-b-<slug>.md`; PR kecil, laporan sesuai format

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
