# PLAN — Agent C: Jalur ke Server & Modul Pilot

**Agent:** C · **Tanggal:** 2026-10-04 · **Induk:** [PLAN-prototype-data-port-rich-blocks](../PLAN-prototype-data-port-rich-blocks.md)

> File ini berdiri sendiri untuk satu agent. Bila selisih dengan plan induk, **plan induk (kontrak §3, kebijakan §2.1) yang berlaku**.

---

## Misi & Lingkupmu — Agent C (Jalur ke Server & Modul Pilot)

**Misi:** membuat blok yang sama berjalan terhadap **API sungguhan** untuk satu modul pilot ("Permintaan Perubahan"): klien port API, pengikatan data di JSON draf, pack pilot yang punya layar kanban kaya, dan cara mengaktifkan tenant pilot di dev — dengan bukti bahwa data **bertahan setelah muat ulang**.

**Kamu memiliki:** `app/shared/.../infrastructure/api/**`, `core/.../domain/pack/LayananPilotPack.kt`, `server/**`, skrip/dokumen dev seed, tes `server/src/test/**`.
**Dilarang:** `presentation/**` (A), `core/.../domain/prototype/**` dan data pack garment (B).

### Butir kerja
**C0 — Discovery aktivasi pilot (G0, ±1 hari; skill `wemade-feature-discovery`).** Cari tahu dan **tulis keputusan** di `docs/plannings/discovery-DP-pilot-activation.md`: bagaimana pack **data** `layanan` menjadi dikenal runtime sebuah tenant (`DomainPackRegistry.register`, tabel `domain_packs`, `DomainPackRepository`/`PostgresDomainPackRepository`, `Tenant.domainPack`, `EnsureTenantWorkingDraftUseCase`), dan bagaimana draf kerja tenant mendapat layar dari `screenSuggestions` pack itu. Pilihan: (a) skrip dev seed yang menyimpan pack + membuat tenant + pemilik; (b) menambah ke daftar `shipped` (mengubah platform — **eskalasi ke koordinator**, jangan putuskan sendiri). Tulis juga bagaimana login uji ke tenant itu dilakukan (superadmin "act-as" atau pemilik seed).
- **AC:** keputusan + langkah reproduksi yang bisa dijalankan terhadap **database uji**; daftar risiko (mis. pack kode vs data, prefiks `layanan_`).

**C1 — `ApiBlockDataPort`** (`infrastructure/api/ApiBlockDataPort.kt`) + **fabrik port** untuk `PrototypeSession`. Kontrak HTTP §3.6 (route yang sudah digenerate): `GET`/`POST {values}`/`PUT {values}`/`DELETE`. Pemetaan: 400 → `Validation(isi respons)`, 401/403 → `Forbidden`, 404 → `NotFound`, selain itu atau jaringan → `Unavailable`. Pakai pola klien yang ada (`BuilderApiClient.call`, header auth).
- **AC:** tes `jvmTest` dengan engine HTTP palsu: tiap status → `PortError` yang benar; parse `{id, values}`; `create` mengembalikan id server; kegagalan jaringan → `Unavailable` (tidak melempar mentah).

**C2 — Pack pilot dengan layar kanban kaya.** Tambahkan `screenSuggestions` ke `LayananPilotPack`: layar KANBAN (`card`: judul `TITLE`, prioritas `BADGE`, peminta `TEXT`, target `DATE`, mendesak `FLAG`; `columnMeta`: warna per status + `wipLimit` untuk "Ditinjau"; `detailForm`: seluruh field), `dataBinding = Api("/api/tenant/modules/layanan_change_request/change_requests")`. Memakai tipe kontrak B (tunggu B0).
- **AC:** pack valid (`DomainPack.init`); `WidgetRegistry.interactiveFor` menghasilkan `InteractiveScreen` ber-`binding` Api; seed kosong/diabaikan untuk binding Api.

**C3 — JSON draf.** `DiscoverySummary.kt`: `screens[].interactive.binding` = `{"type":"api","basePath":"…"}` untuk binding Api; tanpa kunci untuk memori. Kunci lama tak berubah; tes round-trip.

**C4 — Aktivasi dev (sesuai keputusan C0).** Skrip/dokumen yang membuat tenant pilot di **database uji** (dan langkah login). Jangan menulis data demo lewat migrasi. Skrip idempoten dan **menolak berjalan terhadap DB yang namanya tak berisi `scratch`** kecuali flag eksplisit.
- **AC:** dari DB uji kosong → tenant pilot bisa dibuka di `/builder/prototype` dan menampilkan kanban ber-binding Api (dicek A pada G2).

**C5 — Penguatan server.** Tes yang belum ada: **atomik multi-field** pada `PUT` (field kedua tak sah → field pertama tidak tersimpan); urutan `GET` stabil; peran VIEW/OPERATE/MANAGE pada tiap verb (403); isolasi tenant lewat API tetap hijau. Jangan menambah baris `DiscoveryRoutes.kt` (di atas batas lunak).

**C6 — Verifikasi G3 (bersama A).** Skenario §8 poin 3 dan 4: data bertahan setelah muat ulang, VIEW tak bisa mengubah (UI menampilkan pesan), server mati → "Gagal terhubung…" dan rollback pada pindah kartu. Dokumentasikan langkah di teaching doc.

### Urutan & ketergantungan
`C0 (G0) → [setelah B0] C1 → C2 → C3 → C4 → C5 → [G2/G3] C6`. C1 dapat dikerjakan segera setelah B0 (hanya butuh `BlockDataPort`). **Keputusan C0 bisa memblokir C4** — lapor lebih awal bila perlu keputusan koordinator.

### Definition of Done — C
- [ ] AC C0–C6; tes kegagalan HTTP; test 403 per verb; isolasi tenant
- [ ] Skrip dev tidak menyentuh database dev; tidak ada migrasi data demo
- [ ] Migrasi (bila ada) hanya `V91+`; Flyway bersih dari nol di DB uji
- [ ] `wc -l` sesuai batas server; `DiscoveryRoutes.kt` tidak bertambah
- [ ] Teaching doc `docs/teaching/teaching-dp-c-<slug>.md` + dokumen aktivasi pilot

---

## Rujukan bersama (salinan dari plan induk)

## 0. Tujuan, Keputusan, Batas

**Keputusan yang sudah diambil** (percakapan 2026-10-04):
1. Targetnya **jalur ke MVP**, bukan hanya demo yang lebih cantik. Karena itu blok dibangun di atas **port data** sejak awal.
2. Blok yang diperkaya: **tabel dinamis dengan form inline** (contoh: CRM) dan **kanban dengan kartu berisi elemen bertipe + form detail + state berpetadata**.
3. Hanya **satu modul pilot** yang dihubungkan ke API sungguhan: "Permintaan Perubahan" (`layanan_change_request`). Garment tetap berjalan di memori (demo).
4. Modul **live** (CRM, Sampling, operator) **tidak diubah**; semua ini untuk prototype dan modul hasil generate.

**Mengapa port data sekarang** (alasan dalam sesi): blok saat ini memegang store memori dan memanggil `PrototypeReducer` secara **sinkron**. Penyimpanan sungguhan itu **asinkron, bisa gagal, dan id dibuat server**. Menulis tabel inline/kanban kaya dengan asumsi sinkron berarti menulis ulang logika "tambah/ubah/pindah" saat menuju MVP. Port yang sejak awal asinkron dan bisa gagal membuat blok yang sama berjalan di memori (demo) dan di API (MVP).

**Di luar lingkup (jangan dikerjakan):** panel detail per baris selain form kartu kanban; layar ganda per modul (satu usulan layar per modul tetap berlaku); tata letak komposit (beberapa blok dalam satu halaman); onboarding tenant otomatis; impor data; penagihan; concurrency control (ETag/versi baris); Android lokal (hanya CI); adaptor LLM (Koog) untuk spec-ops.

## 1. Baseline (terverifikasi, `main` @ `595cf80`)
- **Spec & reducer murni (`core/.../domain/prototype/`)**: `PrototypeSpec`/`ScreenSpec` (`KanbanConfig(groupField, columns, titleField, detailFields)`, `TableConfig(columns, statusField)`, `FormConfig`, `ChecklistConfig`, `DashboardConfig`), `PrototypeReducer` (`SetField`, `Create`, `Delete`; `required` ditegakkan; transisi status), `InteractiveScreen(spec, seed)`, `InteractiveScreenFactory`, hints pack (`KanbanHints`, `TableHints`, `DashboardHints`), `SpecOp` (5 jenis) + `SpecOpApplier` + `DeterministicSpecOpProposer`.
- **UI (`app/shared/.../presentation/discovery/`)**: `InteractiveKanban(+State)`, `InteractiveTable(+State)`, `InteractiveForm(+State)`, `InteractiveChecklist`, `InteractiveDashboard`, `PrototypeSession` (sesi bersama; `sealed interface PlayableState`), `PrototypeRenderer`, `PrototypeChatEditPanel`; `designsystem/ClayKanbanBoard` (+`ClayKanbanDragState`) generik, adaptif tinggi terbatas/tak terbatas.
- **Server**: modul pilot `layanan_change_request` hasil generator (V90): CRUD `/api/tenant/modules/layanan_change_request/change_requests` (gerbang RBAC fail-closed, RLS, validasi memakai reducer yang sama), `/api/builder/draft/{price,brief,spec-ops}`, `DiscoverySummary.kt` (JSON draf ke klien).
- **Batas yang ada**: satu usulan layar per modul (`DomainPack.kt`); layar draf tenant `default-<modul>`; `DomainPackRegistry` memuat pack **data** dari tabel `domain_packs` (pack `layanan` adalah kode, belum terdaftar di runtime tenant mana pun); semua blok membaca/menulis **store memori** (hilang saat halaman ditutup); tidak ada klien HTTP untuk modul pilot.
- **Utang**: `DomainPackCodec.kt` 328 baris (soft 250, hard 400); `PrototypeRenderer.kt` dan `ClayKanbanBoard.kt` 341 (soft 400); drag kanban masih ada di CRM/Sampling/operator (tak diubah di sini).

## 2. Arsitektur Target
```
Pack/Spec ──► InteractiveScreen(spec, seed, binding)
                    │
          PrototypeSession  ── memilih port menurut binding
                    │
       ┌────────────┴────────────┐
 InMemoryBlockDataPort     ApiBlockDataPort            ◄── BlockDataPort (core, suspend, Result<PortError>)
 (reducer, seed; demo)     (HTTP ke /api/tenant/modules/…; MVP)
                    │
       BlockDataController (per blok; state Compose: Loading|Idle|Saving|Error; optimistik terkontrol)
                    │
   Blok: Tabel (inline create/edit) · Kanban (kartu bertipe, form detail, state berpetadata) · Form · Checklist · Dasbor
```

### 2.1 Kebijakan perilaku (dibekukan, supaya agent tidak berbeda tafsir)
| Aksi | Kebijakan | Alasan |
|---|---|---|
| Pindah kartu / ubah status / centang | **Optimistik**: UI berubah dulu; bila gagal **rollback** + pesan | respons terasa instan; gagal jarang |
| Tambah baris | **Pesimistik**: tunggu server (id dibuat server); baris isian tetap terbuka bila gagal | id nyata; validasi server menang |
| Ubah sel / simpan form detail | **Pesimistik** dengan indikator "menyimpan…" | hindari status setengah |
| Hapus | **Pesimistik** + konfirmasi (sudah ada) | tak bisa dibatalkan |
| Validasi | **Dua lapis**: reducer di klien untuk pesan instan; server tetap penentu (reducer yang sama) | satu sumber aturan |
| Konflik/versi baris | **Tidak ditangani** (terakhir menang); dicatat sebagai utang | di luar lingkup |
| Pesan galat | selalu berbahasa pengguna; 403 = "Anda tidak berwenang…"; jaringan = "Gagal terhubung…" | |

## 3. Kontrak Antar-Agent (dibekukan di Gelombang 0 oleh B; berlaku sebagai **kode** begitu dimerge)

### 3.1 Port data (B menerbitkan, core)
```kotlin
sealed interface PortError {
    data class Validation(val message: String) : PortError      // 400/422 atau reducer menolak
    data class Forbidden(val message: String) : PortError       // 401/403
    data class NotFound(val message: String) : PortError        // 404
    data class Unavailable(val message: String) : PortError     // jaringan/5xx
}
class PortException(val error: PortError) : Exception(/* pesan siap-tampil */)

interface BlockDataPort {
    suspend fun load(): Result<List<PrototypeRow>>
    suspend fun create(values: Map<String, String>): Result<PrototypeRow>        // id diberikan implementasi
    suspend fun update(rowId: String, changes: Map<String, String>): Result<PrototypeRow>  // beberapa field sekaligus, berurutan
    suspend fun delete(rowId: String): Result<Unit>
}
// Satu port = satu entitas (satu tabel). Kegagalan = Result.failure(PortException).
class InMemoryBlockDataPort(spec: PrototypeSpec, entityId: String, seed: List<PrototypeRow>) : BlockDataPort
```
`update` menerapkan perubahan **atomik** terhadap aturan: bila satu field melanggar, tidak ada yang berubah.

### 3.2 Pengikatan data (B menerbitkan)
```kotlin
sealed interface DataBinding {
    data object Memory : DataBinding                                   // seed di InteractiveScreen (demo)
    data class Api(val basePath: String) : DataBinding                 // mis. "/api/tenant/modules/layanan_change_request/change_requests"
}
// InteractiveScreen(spec, seed, binding: DataBinding = DataBinding.Memory)  — kompatibel mundur
```
Codec: `binding` opsional di JSON; tanpa kunci = `Memory`.

### 3.3 Perluasan spec (B menerbitkan; semua opsional, kompatibel mundur)
```kotlin
enum class CardStyle { TITLE, TEXT, BADGE, DATE, NUMBER, FLAG }               // kosakata tertutup (kode; alasan di KDoc)
data class CardElement(val field: String, val style: CardStyle = CardStyle.TEXT)
data class ColumnMeta(val tintHex: Long? = null, val wipLimit: Int? = null)    // warna = DATA tenant (pengecualian design-system Kontrak 1)

KanbanConfig  + val card: List<CardElement> = emptyList()                      // kosong = perilaku lama (titleField + detailFields)
              + val columnMeta: Map<String, ColumnMeta> = emptyMap()
              + val detailForm: FormConfig? = null                              // form saat kartu diketuk

TableConfig   + val inlineCreate: Boolean = false                               // baris isian + tombol Tambah
              + val editableFields: List<String> = emptyList()                  // sel yang bisa diubah (ketuk)
```
Aturan validasi konstruktor: `card`/`detailForm.fields`/`editableFields` wajib field entitas; `wipLimit > 0`; `statusField` dan `groupField` tidak boleh ada di `editableFields` bila ada mesin status yang melarang (status diubah lewat menu status/drag, bukan teks bebas).

### 3.4 Petunjuk pack (B)
`TableHints` + `fields: List<FieldHint(key, type, required, options)>`, `inlineCreate`, `editableFields`; `KanbanHints` + `card`, `columnMeta`, `detailForm`. `ScreenSuggestion` + `dataBinding: DataBinding = DataBinding.Memory` (pilot memakai `Api(basePath)`); `WidgetRegistry.interactiveFor` meneruskannya ke `InteractiveScreen.binding`. Tanpa petunjuk, perilaku lama. Pack garment diisi oleh B; pack pilot oleh C (lihat plan masing-masing).

### 3.5 Operasi spec baru (B menerbitkan; A memakai di chat)
`SpecOp.ShowFieldOnCard(entityId, field, style)` dan `SpecOp.SetFieldRequired(entityId, field, required)`; proposer: "tampilkan Prioritas di kartu", "wajibkan Target selesai". Total 7 jenis; batas 5 operasi per giliran tetap.

### 3.6 Klien API (C menerbitkan, app/infrastructure)
`class ApiBlockDataPort(basePath, http): BlockDataPort` — kontrak HTTP yang **sudah ada** di route yang digenerate: `GET basePath` → `[ {id, values} ]`; `POST basePath {values}` → 201 `{id, values}`; `PUT basePath/{id} {values}` → 200 `{id, values}`; `DELETE basePath/{id}`. Pemetaan status → `PortError`: 400 → Validation(isi respons), 401/403 → Forbidden, 404 → NotFound, selain itu/jaringan → Unavailable.

### 3.7 JSON draf ke klien (C menyajikan, A mem-parse)
`summaryObj` → `screens[].interactive.binding` = `{"type":"api","basePath":"…"}` atau tidak ada (= memori). Kunci lama tidak berubah.

## 4. Kepemilikan File (satu pemilik per file)

| Agent | Jalur | Memiliki |
|---|---|---|
| **A** | UI & state blok | `app/shared/.../presentation/designsystem/**`, `presentation/discovery/**` (kecuali parse JSON binding → `DiscoveryUiModel.kt` milik A juga), `presentation/builder/**`, tes `app/shared/src/jvmTest/**` |
| **B** | Kontrak, spec, port memori, data pack, ops | `core/.../domain/prototype/**`, `core/.../shared/pack/{InteractiveScreenCodec,DomainPackCodec,ScreenSuggestionCodec(baru)}.kt`, `core/.../domain/pack/{DomainPack,GarmentScreenSuggestions,GarmentExportSeed}.kt`, `core/.../domain/discovery/WidgetRegistry.kt`, tes `core/src/commonTest/**/prototype/**` |
| **C** | Jalur ke server & pilot | `app/shared/.../infrastructure/api/**` (klien HTTP), `core/.../domain/pack/LayananPilotPack.kt`, `server/**`, skrip/dok dev seed, tes `server/src/test/**` |

**Hotspot & aturan**: `PrototypeSession.kt` dan `InteractiveBlock.kt` = **A**; `InteractiveScreen.kt`/`PrototypeSpec.kt` = **B**; `DiscoverySummary.kt` = **C**; `DiscoveryUiModel.kt` = **A**. Agent lain **tidak menyunting**; mengajukan perubahan lewat kontrak (§3) ke koordinator. `.claude/**`, `AGENTS.md`, dan `docs/plannings/PLAN-*.md` = koordinator.

## 5. Gelombang & Gerbang
```
G0  Kontrak (B, ±1 hari)         ── B menerbitkan §3.1–§3.5 sebagai kode+tes → dimerge ke main
                                    A: spike (baca-saja)  ·  C: discovery cara pack `layanan` termuat di runtime tenant
G1  Paralel (A ∥ B ∥ C)          ── terhadap kontrak yang sudah di main
G2  Integrasi (koordinator)      ── merge B → C → A; satu tenant pilot hidup; cek mata (hanya A memegang browser)
G3  Bukti & dokumentasi          ── data bertahan setelah muat ulang; cek mata garment (memori) + pilot (API)
```
| Gerbang | Syarat |
|---|---|
| **G0** | §3.1–§3.5 sebagai kode: port + `InMemoryBlockDataPort`, `DataBinding`, perluasan spec + codec kompatibel mundur, `SpecOp` baru; tes bulat; A dan C menandatangani "bisa dikerjakan terhadap ini" |
| **G1** | DoD tiap jalur hijau di worktree sendiri |
| **G2** | `:core:jvmTest`, `:app:shared:jvmTest`, kompilasi 3 target klien + server hijau pada hasil gabungan; `RouteGateTest` hijau |
| **G3** | Skenario §8 lulus dengan mata |

### 5.1 Perkiraan (kasar, belum dikalibrasi; untuk urutan)
| Jalur | G0 | G1 | G2 | G3 |
|---|---|---|---|---|
| B | 1 hr | 4–5 hr | 1 hr | 0,5 hr |
| A | spike 0,5 hr | 6–7 hr | 2 hr | 1 hr |
| C | discovery 1 hr | 4–5 hr | 1 hr | 1 hr |
Total paralel ±10–12 hari kerja; sekuensial ±22–25.
**Risiko terbesar: A** (Compose: input di dalam tabel yang digulir ke samping, fokus keyboard, dialog di dalam wadah yang bisa digulir). **Risiko tak terduga: C** (memuat pack `layanan` di runtime tenant; kemungkinan butuh keputusan koordinator).

## 6. Aturan Kerja Bersama (semua agent)
**Cabang & isolasi (WAJIB — pelajaran sesi lalu).** Setiap agent bekerja di **`git worktree` sendiri**, **bukan** di folder utama: `git worktree add ../wemkaeerp-wt-<huruf> -b feat/dp-<huruf>-<slug> main`. Sesi lalu agent A/B menyunting folder utama bersamaan sehingga kompilasi agent lain pecah dan pekerjaan sempat tak punya cabang. **Jangan commit/push ke `main`.** Satu PR kecil per butir; rebase ke `main` sebelum PR; konflik = berhenti dan lapor. Commit berbahasa Indonesia ringkas, akhiri `Co-Authored-By` sesuai pengaturan sesi.

**Standar repo** (baca `.claude/CLAUDE.md` + rules): Graphify dulu bila tersedia; DDD (domain murni, tanpa `!!`, `Result<T>`); **variabilitas** (beda per tenant/industri → data; tipe field/gaya kartu → kode dengan alasan di KDoc; tes **tenant non-default**); `scripts/audit-variability.sh` 0 temuan baru; **ukuran file**: core 250/400, presentation 400/600, server 300/500, test 500/800 (file di atas batas keras tidak boleh bertambah — `DomainPackCodec.kt` 328 → pecah `ScreenSuggestionCodec.kt`, jangan menambah); **design system**: nol literal `Color(0xFF…)` kecuali warna dari data domain (`ColumnMeta.tintHex`), pakai `Clay*` dan token, komponen `designsystem/` buta domain; endpoint tulis fail-closed + test 403; **satu teaching doc per jalur** `docs/teaching/teaching-dp-<huruf>-<slug>.md`.

**Verifikasi minimum per PR**
```bash
./gradlew :core:jvmTest
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs :app:shared:jvmTest
./gradlew :server:compileKotlin
DB_NAME=<db-uji> ./gradlew :server:test --tests '<yang-disentuh>'      # bila menyentuh server
scripts/audit-variability.sh
```
**DB**: uji berbasis database **hanya** ke database uji bernama berisi `scratch` (buat: `docker exec wemade-postgres psql -U postgres -c "CREATE DATABASE wemake_dp_<huruf>_scratch"`; hapus setelah selesai). `DatabaseFactory.init()` menjalankan Flyway — **jangan pernah** tanpa `DB_NAME` yang menunjuk DB uji. `.env` tidak ikut worktree dan **dilarang disalin**; rujuk DB lewat variabel lingkungan; `EnvLoader` memprioritaskan env sistem di atas `.env`. Nomor migrasi: hanya C, `V91+`. Android hanya lewat CI — tulis "belum diverifikasi lokal".

**Cek mata hanya A** (port 3001/8081 dan satu browser Playwright); agent lain memverifikasi lewat test. **Server dev tidak mendaftarkan rute baru otomatis — mulai ulang.** Login uji: `/login` → "Owner wemade-demo" (garment) atau "Superadmin".

**Jebakan yang sudah terjadi** (baca sebelum mulai): cache inkremental Kotlin kadang rusak ("Could not close incremental caches", `Unresolved reference` di file tak tersentuh) → ulangi, bila berulang hapus `build/` **di worktreemu saja**; **test yang lulus tidak berarti UI benar** — komponen layout generik wajib dicoba di wadah bertinggi terbatas **dan** tak terbatas (kanban pernah runtuh jadi tinggi nol); **kunci `remember`** harus identitas stabil (id draf/layar), bukan data yang berubah karena aksi pengguna (sesi pernah dibuat ulang tiap edit dan menghapus log/undo); karakter di luar ASCII (`→`) bisa jadi kotak di font Nunito; **fallback tak boleh mengarang fakta** (brief lokal pernah memalsukan cakupan); selalu **kompilasi keluaran generator**, bukan hanya menguji teksnya; uji mutasi (ubah satu ekspektasi, harus gagal) untuk test yang dijaga env.

## 7. Risiko
| Risiko | Mitigasi |
|---|---|
| Kontrak G0 meleset → kerja ulang | G0 pendek; A/C mulai dengan fixture/fake; perubahan kontrak = versi baru + kabar semua |
| Input inline di tabel yang bisa digulir sulit (fokus, scroll) | A mulai dari spike; fallback: edit di baris via dialog kecil |
| Pack `layanan` tak termuat di runtime tenant | C0 (discovery) di G0; keputusan koordinator bila perlu perubahan loader |
| Optimistik + rollback menimbulkan status ganda | kebijakan §2.1 dibekukan; tes kegagalan port wajib (port palsu yang gagal) |
| Agent menyunting file agent lain | §4 + worktree wajib; konflik = berhenti & lapor |
| Seed `layanan` memengaruhi DB dev | seed hanya lewat skrip eksplisit, DB uji dulu; tidak ada migrasi data demo |

## 8. Skenario Penerimaan (G3, dengan mata)
1. **Garment/CRM (memori):** tabel "Daftar PO" — klik **+ Tambah**, isi baris (input sesuai tipe), Simpan → baris muncul; field wajib kosong → pesan; ketuk sel "Produk" → ubah; status lewat lencana; hapus. Kolom baru dari chat ("tambah kolom Prioritas") → kolom + input ikut muncul.
2. **Garment/Sampling (memori):** kartu kanban menampilkan lencana/tanggal/angka sesuai `card`; ketuk kartu → **form detail** berisi field spec, simpan; kolom berwarna dari data; batas WIP menandai kolom penuh; transisi terlarang ditolak.
3. **Pilot (API):** tenant pilot membuka kanban "Permintaan Perubahan" — data dimuat dari server; tambah/pindah/ubah/hapus tersimpan; **muat ulang halaman → data tetap ada**; peran VIEW tidak bisa mengubah (UI menampilkan pesan, bukan crash); matikan server → pesan "Gagal terhubung…" dan **rollback** pada pindah kartu.
4. **Isolasi:** tenant lain tidak melihat baris pilot (sudah dibuktikan di server; di sini lewat UI/API).
5. Brief tetap benar (perubahan sesi tercatat) dan harga tetap hidup.

## 9. Definition of Done (per jalur)
- [ ] AC semua butir jalur; tes domain murni + **tenant non-default**; tes kegagalan port
- [ ] Tidak ada edit di luar kepemilikan; `wc -l` sesuai batas/ratchet
- [ ] Kompilasi JVM/WasmJS/JS/server hijau; Android = CI
- [ ] `scripts/audit-variability.sh` 0 temuan baru; `graphify update .` bila tersedia
- [ ] Endpoint tulis (bila ada) fail-closed + test 403
- [ ] Teaching doc jalur; PR kecil, rebase bersih, laporan ke koordinator (format di file jalur)

## Format laporan ke koordinator (setiap PR / akhir gelombang)
1. Butir selesai + cabang/PR.
2. Hasil perintah verifikasi (ringkas; sertakan kegagalan apa adanya).
3. `wc -l` sebelum → sesudah untuk file di atas batas lunak.
4. Yang **belum** diverifikasi (Android, lebar ~1280dp, dsb.) + temuan/keputusan terbuka.
5. Perubahan kontrak yang kamu butuhkan — jangan menyunting berkas milik agent lain.
