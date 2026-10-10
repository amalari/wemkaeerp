<!-- FILE HASIL GENERATE oleh scripts/sync-agent-config.sh — JANGAN disunting langsung.
     Sunting .claude/CLAUDE.md atau .claude/rules/*.md, lalu jalankan skrip itu. -->

# WeMade Flow Platform (Jalur B) — Project Rules (Domain-Driven Design)

## Status Repo: Jalur B — Platform Alur General (BACA DULU)

Repo ini adalah **fork** dari `wemade-erp` (ERP konveksi) pada tag `fork-point/general-2026-09`.
Tujuannya berbeda: menjadi **platform alur lintas industri**. Konveksi hanyalah **Domain Pack pertama**.
Rencana lengkap: [`docs/plannings/PLAN-dual-track-garment-and-general-platform.md`](../docs/plannings/PLAN-dual-track-garment-and-general-platform.md) §4.

Konsekuensinya, aturan di bawah dibaca dengan penyesuaian ini:

1. Enum konveksi `PipelineStage`, `ModuleArchetype`, tipe port, `GarmentBusinessPreset`, dan
   `BusinessModule` **sedang dimigrasi** menjadi data per Domain Pack (`PhaseCode`, `SlotCode`,
   `PortType`, Blueprint, `ModuleId`). Menyentuh atau menggantinya **bukan** pelanggaran; menambah
   entri enum konveksi baru **adalah** pelanggaran — tambahkan ke pack.
2. Migrasi wajib Strangler Fig (`tenant-variability-rules.md` Kontrak 8), satu enum per tahap, urutan
   B0 → B6. Setiap tahap lulus **test paritas**: tenant konveksi berperilaku identik dengan `wemade-erp`.
3. **B5 (test gerbang keamanan) wajib hijau sebelum B6 (`BusinessModule`) dimulai.** Modul data yang
   terdaftar tanpa gerbang RBAC harus menggagalkan test.
4. Contoh "konveksi" di rules (FOB/CMT/D2C, rajut, bordir) berlaku untuk **pack `garment`**, bukan untuk
   platform. Kode mesin (`domain/pipeline` mesin kanvas, RBAC, entitlement) tidak boleh menyebut konsep
   satu industri.
5. Aliran kode **satu arah**: perbaikan mesin dari `wemade-erp` di-cherry-pick ke sini (remote
   `upstream-garment`) dan dicatat di `docs/plannings/sync-log.md`. Repo ini **tidak pernah** di-merge
   balik ke `wemade-erp`. Fitur konveksi baru tidak ditulis pertama kali di sini.

---

## Stack Overview

- **Kotlin Multiplatform (KMP)** — Android, iOS, Web (WasmJS + JS), Desktop (JVM), Server (Ktor)
- **Compose Multiplatform** — Shared UI di `app/shared`
- **Base package**: `com.eventverse.app`

---

## DDD Architecture Rules

### 1. Module Structure

Gunakan struktur modular berikut:

```
EventVerse/
├── core/                        # Shared primitives & types (no dependency ke app)
│   └── src/commonMain/kotlin/com/eventverse/app/
│       ├── domain/              # Domain types murni (Entity, ValueObject, DomainEvent)
│       └── shared/              # Shared utilities (Result, Either, extensions)
│
├── app/
│   ├── shared/                  # Shared UI + application layer (Compose UI)
│   │   └── src/commonMain/kotlin/com/eventverse/app/
│   │       ├── presentation/    # ViewModel, UiState, UiEvent
│   │       ├── navigation/      # Screen routing
│   │       └── di/              # Dependency injection setup
│   │
│   ├── androidApp/              # Android entry point saja
│   ├── iosApp/                  # iOS entry point saja
│   ├── desktopApp/              # Desktop entry point saja
│   └── webApp/                  # Web entry point saja
│
└── server/                      # Ktor server (REST/GraphQL)
    └── src/main/kotlin/com/eventverse/app/
        ├── domain/              # Business logic (Use Cases, Repositories interfaces)
        ├── application/         # Application services
        ├── infrastructure/      # DB, external APIs, implementations
        └── api/                 # Route handlers, request/response DTOs
```

---

### 2. Layer Dependencies (Dependency Rule)

```
Presentation → Application → Domain ← Infrastructure
```

- **Domain layer** tidak boleh bergantung pada layer mana pun
- **Application layer** hanya boleh bergantung pada Domain
- **Infrastructure** mengimplementasikan interface yang didefinisikan di Domain
- **Presentation** hanya boleh bergantung pada Application (via ViewModel/UseCase)

---

### 3. Domain Layer Rules

#### Entity
- Memiliki identitas unik (`id`)
- Immutable by default; mutasi melalui fungsi domain yang menghasilkan salinan baru
- Contoh: `Event`, `User`, `Ticket`

```kotlin
// ✅ BENAR
data class Event(
    val id: EventId,
    val title: EventTitle,
    val status: EventStatus,
) {
    fun publish(): Event = copy(status = EventStatus.PUBLISHED)
}

// ❌ SALAH — mutasi langsung
class Event {
    var status: String = "draft"
}
```

#### Value Object
- Tidak memiliki identitas; didefinisikan oleh nilainya
- Selalu `@JvmInline value class` atau `data class` yang tidak mutable
- Validasi dilakukan di konstruktor/factory

```kotlin
@JvmInline
value class EventTitle(val value: String) {
    init {
        require(value.isNotBlank()) { "EventTitle cannot be blank" }
        require(value.length <= 200) { "EventTitle too long" }
    }
}
```

#### Domain Event
- Nama dalam past tense: `EventPublished`, `TicketReserved`
- Extend `DomainEvent` base sealed interface

```kotlin
sealed interface DomainEvent
data class EventPublished(val eventId: EventId, val occurredAt: Instant) : DomainEvent
```

#### Repository Interface
- Didefinisikan di Domain, diimplementasikan di Infrastructure
- Hanya berisi operasi domain yang relevan (bukan CRUD generik)

```kotlin
interface EventRepository {
    suspend fun findById(id: EventId): Event?
    suspend fun save(event: Event)
    suspend fun findAllPublished(): List<Event>
}
```

---

### 4. Application Layer Rules (Use Cases)

- Satu Use Case = satu operasi bisnis
- Nama: `[Verb][Noun]UseCase` — `PublishEventUseCase`, `ReserveTicketUseCase`
- Menerima `Command` atau `Query` object, mengembalikan `Result<T>`
- Tidak mengandung logika domain

```kotlin
class PublishEventUseCase(
    private val eventRepository: EventRepository,
    private val eventBus: EventBus,
) {
    suspend operator fun invoke(command: PublishEventCommand): Result<Unit> = runCatching {
        val event = eventRepository.findById(command.eventId)
            ?: error("Event not found")
        val publishedEvent = event.publish()
        eventRepository.save(publishedEvent)
        eventBus.publish(EventPublished(publishedEvent.id, Clock.System.now()))
    }
}
```

---

### 5. Presentation Layer Rules (KMP/Compose)

- Gunakan **MVI pattern**: `UiState`, `UiEvent`, `UiEffect`
- ViewModel di `app/shared` menggunakan `CommonViewModel` base class
- Tidak ada logika bisnis di Composable atau ViewModel

```kotlin
data class EventListUiState(
    val events: List<EventUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface EventListUiEvent {
    data object LoadEvents : EventListUiEvent
    data class SelectEvent(val id: String) : EventListUiEvent
}
```

---

### 6. Naming Conventions

| Artefak | Konvensi | Contoh |
|---|---|---|
| Entity | Noun tunggal | `Event`, `User`, `Ticket` |
| Value Object | Descriptive Noun | `EventTitle`, `TicketPrice` |
| Use Case | Verb + Noun + UseCase | `PublishEventUseCase` |
| Repository | Noun + Repository | `EventRepository` |
| ViewModel | Screen + ViewModel | `EventListViewModel` |
| UiState | Screen + UiState | `EventListUiState` |
| DTO (API) | Noun + Request/Response | `CreateEventRequest`, `EventResponse` |
| Domain Event | Past tense Noun | `EventPublished`, `TicketReserved` |

---

### 7. General Kotlin Rules

- Gunakan **`suspend fun`** untuk semua operasi async — tidak ada RxJava atau LiveData
- Gunakan **`Result<T>`** sebagai return type untuk operasi yang bisa gagal di use cases
- Gunakan **`sealed interface`** untuk sealed hierarchies (bukan `sealed class`)
- Prefer **`value class`** untuk domain primitives (EventId, UserId, dll.)
- **Tidak ada `!!` operator** — gunakan safe call + Elvis atau `requireNotNull` dengan pesan jelas
- Gunakan **`kotlinx.datetime`** untuk semua date/time — bukan `java.util.Date`

---

### 8. File Organization

- Satu file = satu konsep utama
- Boleh ada file gabungan untuk value objects kecil: `EventValueObjects.kt`
- Kelompokkan berdasarkan **fitur/domain**, bukan berdasarkan layer di level file

```
feature/event/
├── domain/
│   ├── Event.kt
│   ├── EventValueObjects.kt   # EventId, EventTitle, EventStatus
│   ├── EventRepository.kt
│   └── EventDomainEvents.kt
├── application/
│   ├── PublishEventUseCase.kt
│   └── GetEventListUseCase.kt
└── presentation/
    ├── EventListViewModel.kt
    ├── EventListScreen.kt
    └── EventListUiModel.kt
```

---

### 9. Testing Rules

- Domain layer: **Unit test murni**, tidak boleh ada dependency eksternal
- Application layer: **Unit test dengan mock** repository dan services
- Presentation: **Unit test ViewModel** dengan fake use cases
- Infrastructure: **Integration test** dengan DB/API nyata
- Naming test: `[what]_[condition]_[expected]`

```kotlin
@Test
fun `publish event when already published should throw exception`() { ... }
```

---

### 10. Dependency Injection

- Gunakan **manual DI** atau **Koin** (multiplatform-compatible)
- Module DI diorganisir per domain feature
- Tidak ada `ServiceLocator` pattern di luar DI graph

---

### 11. Modul Operasional (Composable "Lego/Puzzle" Architecture)

WeMade ERP **tidak dibatasi** oleh enum model bisnis yang kaku (FOB/CMT/Brand D2C).
Preset itu hanya starter template — setiap tenant bebas menyusun, menukar, atau
menghibridkan node modul dalam alur pipeline-nya sendiri (`CustomTenantPipeline`).

Sebelum membuat, memperluas, atau merefaktor modul operasional apa pun (Procurement,
Sampling, Cutting SPK, Sewing Kanban, QC Inspection, Costing Engine, dll.), baca dan
patuhi **[`.claude/rules/module-integration-rules.md`](.claude/rules/module-integration-rules.md)**
secara penuh. Ringkasan kontrak wajibnya:

1. Deklarasikan `ModuleArchetype` yang tepat (slot kemampuan modul dapat saling ditukar).
2. Nyatakan tipe data Input/Output Port agar kompatibel disambung modul lain.
3. Jangan campur `StockOwnershipSemantics` (`OWNED_RAW_MATERIAL` vs
   `CONSIGNED_CLIENT_MATERIAL` vs `INTERNAL_FINISHED_GOODS`).
4. Pisahkan rumus `CostingBehavior` dari core engine (parameter dinamis per tenant,
   bukan hardcode).
5. Sediakan jalur `DefectLiability` & rework loop untuk modul lantai produksi.
6. Sediakan telemetri (`wipPieces`, `cycleTimeHours`, `healthStatus`) agar node bisa
   dipantau di kanvas.
7. Isolasi konfigurasi pipeline per `TenantId` — modifikasi satu tenant tidak boleh
   berdampak ke tenant lain.
8. Deklarasikan `ScopeCapability` (`GLOBAL_ONLY` vs `HIERARCHICAL`) untuk kapabilitas
   jangkauan data modul.

Jalankan checklist Definition of Done di file rules tersebut sebelum menganggap modul selesai.

---

### 12. Dokumentasi Wajib Pasca-Fitur (Teaching Skill)

**Setiap kali sebuah task, issue, modul, atau fitur baru selesai diimplementasikan**
(termasuk perubahan signifikan pada fitur yang sudah ada), panggil skill `teaching`
untuk menghasilkan dokumentasi mentoring teknis di `docs/teaching/teaching-[slug].md`,
lalu tautkan file tersebut di respons akhir ke user. Ini berlaku otomatis — tidak perlu
menunggu user memintanya secara eksplisit.

Skill dokumentasi lain yang tersedia dan boleh dipakai sesuai konteks (tidak wajib
otomatis seperti `teaching`):
- `task-resolution-doc` — ringkasan penyelesaian task terhubung ke GitHub Issue, di
  `docs/tasks/`. Pakai saat task punya issue GitHub yang jelas.
- `trd-generator` — Technical Requirements Document 5-bagian untuk fitur/servis baru
  yang cukup besar, di `docs/trd/`. Pakai di awal perencanaan fitur besar, bukan pasca-implementasi.
- `task-to-github-projects` — mengonversi rencana/breakdown task menjadi GitHub
  Issues & Project items via `gh` CLI.

---

### 13. Design System & UI Styling (Clay)

Bahasa visual WeMade ERP adalah **Claymorphism + Neo-Brutalism**: outline tebal 3dp, hard
shadow tanpa blur, sudut membulat besar, font Fredoka + Nunito — dengan **palet brand WeMade**
(biru `#2563EB`, oranye `#EA580C`), bukan palet pastel.

Sebelum menulis atau mengubah UI apa pun di `app/shared/**/presentation/`, baca dan patuhi
**[`.claude/rules/design-system-rules.md`](.claude/rules/design-system-rules.md)** secara penuh,
dan gunakan skill **`compose-design-system`** sebagai panduan kerjanya.

Ringkasan kontrak wajibnya:

1. **Nol literal `Color(0xFF……)` di luar `WeMadeTheme.kt`.** Satu-satunya pengecualian adalah
   warna yang berasal dari domain (`node.stage.colorHex`, `status.badgeColorHex`,
   `department.colorHex`) karena itu data tenant, bukan keputusan desain.
2. **Butuh warna baru? Tambahkan token di `WeMadeColors`**, dinamai per *peran*
   (`SurfaceMuted`, `Info`, `Defect`) bukan per *rupa* (`Slate100`, `SkyBlue`, `Rose600`).
3. **`colorScheme` wajib terisi penuh.** Slot yang dilewat memakai ungu default M3 dan bocor ke
   `AlertDialog`/`DropdownMenu`/`OutlinedTextField`.
4. **Aturan Tiga Kali** — pola visual yang muncul ≥3 kali wajib diangkat ke
   `presentation/designsystem/` sebelum pemakaian keempat ditulis.
5. **Pakai katalog yang ada**: `ClayCard`, `ClayButton`, `ClayActionSurface`, `ClayBadge`,
   `ClayTag`, `Modifier.claySurface`, `Modifier.clayFlat`. Dilarang `Card`/`Button` Material
   mentah dan `Modifier.shadow()`.
6. **Komponen `designsystem/` buta terhadap domain** — menerima `String`/`Color`/lambda, bukan
   `PipelineNode`. Pembungkus berbasis domain tetap di package fiturnya.
7. **Bentuk, ketebalan, dan spasi juga token** (`ClayShapes`, `ClayBorder`, `ClaySpacing`).
   Ketebalan outline konsisten; state dibedakan lewat *warna*, bukan ketebalan.
8. **Jangan tanam `color` ke dalam `TextStyle`** — itu mematikan `LocalContentColor`.
9. **Clay memakan ruang** (~18dp/kartu) dan Nunito ber-x-height besar; tinjau lebar kontainer
   dan tier ukuran font setiap kali mengkonversi layar padat.
10. **Mode gelap lewat theme, bukan ternary.** Jangan menambah `if (isPresentationMode)` baru.

Jalankan checklist Definition of Done di file rules tersebut sebelum menganggap UI selesai —
termasuk **menjalankan aplikasinya dan melihat dengan mata**, karena bug layout tidak tertangkap
test mana pun.
Kalau aplikasi meminta login saat pengecekan visual, **login dulu sebagai superadmin** lewat tombol
"Demo Mode: Masuk Cepat (Superadmin Apps)" di `/login`, lalu lanjutkan pengecekannya — jangan dilewati.

---

### 14. Batas Ukuran File (File Size & Decomposition)

Panjang file adalah *proxy* termurah untuk tiga penyakit nyata: pelanggaran Single Responsibility,
pola yang disalin alih-alih diangkat jadi komponen bersama, dan file yang tidak lagi bisa direview
sekali duduk. Karena itu ada ambangnya, **per lapisan** — satu angka global tidak masuk akal karena
Compose secara struktural lebih panjang dari domain murni.

| Lingkup | Soft (peringatan) | Hard (tolak merge) |
|---|---|---|
| `core/**` (domain murni) | **250** | **400** |
| `app/shared/**/presentation/**` | **400** | **600** |
| `server/src/main/**` | **300** | **500** |
| `**/commonTest/**`, `**/jvmTest/**` | **500** | **800** |
| ragu / tidak terdaftar | **400** | **600** |

Kontrak wajibnya:

1. **Hard limit berlaku ke file setelah diubah, bukan ke diff-nya.** Menambah 10 baris ke file 700
   baris tetap pelanggaran.
2. **Aturan Ratchet** — file yang sudah di atas hard limit sebelum aturan ini ada tidak wajib
   dinormalkan dalam satu PR, tapi **setiap perubahan padanya wajib membuatnya tidak lebih panjang**.
   Catat `wc -l` sebelum dan sesudah.
3. **Memecah file mengikuti batas tanggung jawab, bukan batas baris.** Dilarang
   `…Part2.kt` / `…Extra.kt` / `…Helpers.kt` tanpa tema.
4. **Pengecualian hanya untuk data terurut, bukan logika** — katalog ikon, seed preset, codec
   eksplisit, kode ter-generate. Wajib dideklarasikan di baris pertama file:
   ```kotlin
   // FILE-SIZE-EXEMPT: katalog aset — data terurut, bukan logika. Lihat .claude/rules/file-size-rules.md §3
   ```
   Screen/Dialog/ViewModel/Route **tidak pernah** memenuhi syarat pengecualian — panjangnya selalu
   gejala desain, bukan gejala data.
5. **Sebelum memecah UI, cek dulu apakah bagian yang berulang seharusnya naik ke
   `presentation/designsystem/`** (Aturan Tiga Kali). Sering kali separuh panjang file itu adalah
   styling yang disalin, bukan fitur.

Baca **[`.claude/rules/file-size-rules.md`](.claude/rules/file-size-rules.md)** secara penuh untuk
pola pemecahan per jenis file, daftar pengecualian, skrip audit, dan tabel utang teknis (21 file
yang saat ini melanggar). Jalankan checklist Definition of Done di file tersebut sebelum menganggap
pemecahan selesai — termasuk kompilasi 5 target dan **melihat UI-nya dengan mata**, karena memecah
Compose mudah menggeser `Modifier` chain tanpa memecahkan kompilasi.

Audit cepat file yang disentuh:

```bash
git diff --name-only --diff-filter=ACM main...HEAD -- '*.kt' \
  | xargs wc -l 2>/dev/null | sort -rn | head -20
```

---

### 15. Alur Wajib Fitur & Modul Baru (Discovery → Workflow)

Sebelum menulis kode fitur atau modul apa pun, jalankan berurutan:

1. **Skill `wemade-feature-discovery`** — kebutuhan bisnis, **fitur serupa sudah ada?**
   (`scripts/find-similar-feature.sh <kata>`), jenis (modul operasional / governance / foundation /
   fitur dalam modul), Uji Variabilitas, core & titik extend, input/output + posisi di kanvas
   Factory Flow, governance. Hasil: Discovery Note.
2. **Skill `wemade-feature-workflow`** — gerbang ukuran/TRD → domain (tenant kedua) → pendaftaran →
   persistensi → API fail-closed → UI dari data → verifikasi → teaching doc.

Baca dan patuhi **[`.claude/rules/tenant-variability-rules.md`](.claude/rules/tenant-variability-rules.md)**
(kode vs data) dan **[`module-integration-rules.md` §5](.claude/rules/module-integration-rules.md)**
(anatomi pendaftaran per jenis modul). Ringkasan kontraknya:

1. Uji Variabilitas sebelum `enum class`/`when` domain — beda per tenant/industri/admin → **data**.
2. Tangga keputusan: Modul → Tahap → Proses opsional → Stasiun → Konfigurasi. Proses **bukan** modul.
3. Aturan domain memakai **peran** (`ModuleArchetype`, `StageTrait`), bukan kode khas satu industri.
4. Kunci tersimpan = value object string; parser tunggal; **tidak** fallback senyap.
5. Template disalin ke tenant; dokumen **membeku** saat mulai dikerjakan.
6. Test wajib memakai **template non-default**; cek visual di tenant uji non-rajut (`bordir-uji`).
7. Endpoint tulis **fail-closed**; test wajib mencakup peran tak berwenang (403).
8. Konsep yang terlanjur enum dimigrasi dengan Strangler Fig + test paritas.

Sebelum merge: `scripts/audit-variability.sh` (melapor, tidak memblokir).

**Konfigurasi AI lintas tool**: `.claude/` adalah satu-satunya sumber kebenaran. Setelah mengubah
`CLAUDE.md`, rules, atau skill `wemade-*`, jalankan `scripts/sync-agent-config.sh` agar Cline
(`.clinerules`, `.cline/skills`) dan Gemini/Antigravity (`AGENTS.md`, `GEMINI.md`, `.agents/`) ikut
terbarui. `AGENTS.md` adalah **file hasil generate** — jangan disunting langsung.

---

### 16. Graphify Dulu, Baru Cara Lain (Pencarian Kode)

Repo ini punya knowledge graph di `graphify-out/` dan MCP server `graphify` (lihat `.mcp.json`).
**Untuk setiap pertanyaan soal kode atau arsitektur, konsultasikan graphify lebih dulu** — sebelum
`grep`, `find`, `Glob`, `Grep`, membaca banyak file, atau menjalankan `scripts/find-similar-feature.sh`.

1. Mulai dari tool MCP `query_graph` (CLI: `graphify query "<pertanyaan>"`). Relasi antar konsep:
   `shortest_path` / `graphify path "<A>" "<B>"`. Satu konsep: `get_node` / `graphify explain "<X>"`.
2. Kalau `graphify-out/wiki/index.md` ada, telusuri wiki itu sebelum membuka file mentah.
3. `GRAPH_REPORT.md` hanya untuk tinjauan arsitektur luas, atau saat query/path/explain belum cukup.
4. Boleh beralih ke grep/glob/baca file **hanya setelah** graphify dicoba dan hasilnya kurang — atau
   untuk string literal persis yang memang bukan simpul graph (pesan error, nilai konfigurasi).
   Sebutkan singkat di respons bahwa graphify sudah dicoba.
5. Setelah mengubah file kode, jalankan `graphify update .` (AST-only, tanpa biaya API) agar graph
   tidak basi. Jangan percaya graph untuk kode yang baru saja diubah sebelum di-update.
6. Ini melengkapi Discovery (§15): langkah "fitur serupa sudah ada?" dimulai dari graphify, lalu
   `scripts/find-similar-feature.sh` sebagai penguat.

---

### 17. Komponen Input Bersama & Tipe Field (Pendaftaran Wajib)

Saat membuat komponen input bersama atau tipe field baru (date picker, mata uang, pilihan ganda, unggah file, …), baca dan
patuhi **[`.claude/rules/field-component-rules.md`](.claude/rules/field-component-rules.md)**. Ringkasan kontraknya:

1. **Komponen common tidak berdiri sendiri** — wajib didaftarkan ke kosakata tipe field **dalam PR yang sama**
   (domain, codec, usulan layar, generator SQL, katalog agent `screen_catalog`, dan UI).
2. Ada **dua kosakata** yang terpisah: enum prototype (`domain/prototype/EntitySpec.kt`) dan sealed interface CRM
   (`domain/customfield/FieldType.kt`). Menambah ke satu tidak otomatis menambah ke yang lain.
3. **Varian ≠ tipe baru**: beda format/tampilan (mata uang) = parameter pada tipe yang ada.
4. Satu pintu kontrol input (`FieldInput`); dilarang `else ->` pada `when (FieldType)`; codec menolak nilai tak dikenal,
   **tidak** jatuh ke `TEXT`.
5. Komponen yang belum ada **tidak dipalsukan** jadi `TEXT`: tolak, pakai `CUSTOM_SCREEN`, atau ajukan lewat antrean build.

Celah dan prioritas saat ini: `docs/plannings/PLAN-field-component-gaps.md`.

---

## Anti-Patterns yang Dilarang

- Anemic Domain Model — Entity hanya data, logika di service
- God UseCase — satu use case menangani banyak operasi
- Repository sebagai DAO generik — hindari findAll(), deleteById() tanpa konteks domain
- Domain bergantung pada framework — tidak ada Ktor/Android/Compose import di domain
- Business logic di ViewModel atau Composable
- String primitives untuk domain concepts — gunakan Value Objects
- **Literal warna/radius/border di dalam Composable fitur** — gunakan token (lihat §13)
- **Menyalin blok styling** alih-alih mengangkatnya jadi komponen bersama
- **`Modifier.shadow()` di `presentation/`** — bayangannya selalu blur, berlawanan dengan bahasa visual
- **God File** — satu file melewati hard limit lapisannya (lihat §14) tanpa alasan pengecualian yang sah
- **Memecah file per baris, bukan per tanggung jawab** — `FooScreenPart2.kt`, `FooExtra.kt`, `FooHelpers.kt` tanpa tema

---

# WeMade ERP — Aturan Standar Design System & UI Styling (Compose Multiplatform)

Dokumen ini adalah **aturan baku styling** yang wajib ditaati setiap kali membuat, memperluas, atau
merefaktor UI apa pun di `app/shared`. Statusnya sejajar dengan
[`module-integration-rules.md`](module-integration-rules.md): kalau modul mengatur *apa yang dikerjakan*,
dokumen ini mengatur *bagaimana rupanya*.

**Ruang lingkup**: seluruh `app/shared/src/commonMain/.../presentation/**`.

---

## 1. Paradigma: Satu Bahasa Visual, Satu Sumber Kebenaran

Bahasa visual WeMade ERP adalah **Claymorphism + Neo-Brutalism**:

| Ciri | Nilai | Token |
|---|---|---|
| Outline tebal gelap | 3dp | `ClayBorder.Thick` |
| Hard shadow (blur = 0) | geser 6dp | `ClayOffset.Rest` |
| Sudut membulat besar | 12–24dp per peran | `ClayShapes.*` |
| Inner bottom shade | hitam 10% di 12% bawah | otomatis di `claySurface` |
| Interaksi tekan | kartu masuk ke bayangannya | `pressed` di `claySurface` |
| Font | Fredoka (judul) + Nunito (isi) | `rememberClayTypography()` |

Paletnya **tetap palet brand WeMade** — biru `#2563EB` dan oranye `#EA580C`. Bahasa clay diambil
bentuknya, bukan warnanya.

> **Alasan yang tidak boleh dilanggar**: Factory Flow memakai hijau/amber/merah sebagai **sinyal
> produksi** (`HEALTHY` / `WARNING` / `BOTTLENECK` / `CRITICAL`). Palet dekoratif apa pun yang
> meredam kontras sinyal itu ditolak, sebagus apa pun tampilannya.

Sumber kebenaran tunggal:

```
app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/
├── theme/WeMadeTheme.kt          # Lapisan token: warna & theme entry point
└── designsystem/                 # Lapisan bentuk & komponen
    ├── ClayTokens.kt             # shapes, offsets, border widths, spacing
    ├── ClayModifier.kt           # claySurface() & clayFlat()
    ├── ClayCard.kt
    ├── ClayButton.kt
    ├── ClayBadge.kt              # ClayBadge + ClayTag
    └── ClayTypography.kt
```

---

## 2. Arsitektur Token Tiga Lapis

Setiap nilai visual wajib berada di salah satu lapisan berikut, dan **hanya boleh mengalir ke bawah**:

```
Lapis 1 — PRIMITIF      WeMadeColors.*            "warna apa"
              ↓          (Primary, Success, Outline, Border, …)
Lapis 2 — SEMANTIK      WeMadeLightColorScheme    "perannya apa di Material"
              ↓          ClayShapes / ClayOffset / ClayBorder / ClaySpacing
Lapis 3 — KOMPONEN      ClayCard / ClayButton /   "bagaimana perannya dirakit"
                        ClayBadge / ClayTag
```

### Kontrak 1 — Dilarang keras literal warna di dalam Composable fitur

```kotlin
// ❌ DITOLAK — literal warna di file layar/komponen fitur
Box(modifier = Modifier.background(Color(0xFFF1F5F9)))
Text(text = label, color = Color(0xFF64748B))
border = BorderStroke(1.dp, Color(0xFFE2E8F0))

// ✅ BENAR
Box(modifier = Modifier.background(WeMadeColors.SurfaceMuted))
Text(text = label, color = WeMadeColors.OnSurfaceMuted)
```

**Satu-satunya tempat `Color(0xFF……)` boleh ditulis adalah `WeMadeTheme.kt`.**

Dua pengecualian yang sah, keduanya **bukan** hardcode:

1. **Warna yang berasal dari domain**, di mana nilainya adalah data, bukan keputusan desain:
   ```kotlin
   Color(node.stage.colorHex)            // dari PipelineStage
   Color(node.healthStatus.badgeColorHex) // dari FlowHealthStatus
   Color(node.deptColorHex)               // dari Department
   ```
   Warna ini hidup di `core/.../domain/**` karena tenant bisa mengubahnya. Jangan menyalinnya
   ke `WeMadeColors`, dan jangan menggantinya dengan token.

2. **Turunan token** lewat `.copy(alpha = …)`:
   ```kotlin
   outline = WeMadeColors.Success.copy(alpha = 0.45f)   // ✅ boleh
   ```

### Kontrak 2 — Butuh warna baru? Tambahkan token, jangan tulis di tempat

Kalau sebuah warna belum ada tokennya, **berhenti**. Jangan menulis literalnya "sementara".
Tambahkan ke `WeMadeColors` dengan nama berbasis peran, lalu pakai tokennya.

Nama token berbasis **peran**, bukan rupa:

| ✅ Benar | ❌ Salah | Kenapa |
|---|---|---|
| `OnSurfaceMuted` | `Slate500` | Nama rupa mengunci kita pada satu palet; ganti palet = ganti semua nama |
| `Outline` | `DarkBorder` | Peran menjelaskan kapan dipakai |
| `SurfaceDark` | `Navy900` | |
| `WarningBg` | `Amber50` | |

### Kontrak 3 — `colorScheme` harus terisi penuh

`WeMadeLightColorScheme` wajib mengisi **seluruh** slot Material 3, bukan sebagian.

*Alasannya konkret*: slot yang tidak di-override tetap memakai **ungu default Material 3**, dan ungu
itu bocor diam-diam ke `AlertDialog`, `DropdownMenu`, dan `OutlinedTextField` yang tidak diberi
warna eksplisit. Kebocoran inilah yang dulu memaksa developer menulis warna manual di mana-mana —
lingkaran setan yang harus diputus dari akarnya.

Slot yang paling sering terlupakan: `surfaceVariant`, `onSurfaceVariant`, `outline`, `outlineVariant`,
`tertiary`, `surfaceContainer`/`surfaceContainerHigh`/`surfaceContainerHighest`, `inverseSurface`,
`scrim`.

---

## 3. Kontrak Komponen Bersama

### Kontrak 4 — Aturan Tiga Kali (Rule of Three)

> Kalau sebuah pola visual muncul **tiga kali atau lebih**, ia wajib diangkat menjadi komponen
> bersama di `presentation/designsystem/` sebelum pemakaian keempat ditulis.

Ini bukan aturan gaya, ini aturan pencegahan. Sebelum design system ada, kode ini punya:
- blok `Card(shape=…, colors=…, border=…, elevation=…)` identik di **~32 call site**
- **3 implementasi badge** yang nyaris sama di 3 package berbeda
- **343 literal `Color(0xFF……)`** yang mem-bypass theme

Dampaknya: satu perubahan desain = menyentuh puluhan file, dengan risiko terlewat di sebagian.

### Kontrak 5 — Pakai katalog yang sudah ada sebelum membuat baru

Sebelum menulis `Card`, `Button`, `Box` bergaya, atau badge apa pun, **cek katalognya dulu**:

| Kebutuhan | Pakai ini | Jangan |
|---|---|---|
| Kartu/panel dengan bayangan | `ClayCard` | `Card` Material |
| Tombol aksi | `ClayButton` | `Button` / `OutlinedButton` / `TextButton` |
| Toolbar dengan isi bebas | `ClayActionSurface` | `Row` + `clickable` manual |
| Pil status membulat | `ClayBadge` | `Box` + `clip` + `background` |
| Label persegi/tag padat | `ClayTag` | idem |
| Permukaan clay kustom | `Modifier.claySurface(…)` | `Modifier.shadow()` |
| Permukaan rata ber-outline | `Modifier.clayFlat(…)` | `.clip().background().border()` |

`Modifier.shadow()` **dilarang** di seluruh `presentation/**`: ia selalu menghasilkan bayangan
ber-blur mengikuti kurva elevation Material, yang berlawanan dengan bahasa visual kita.

### Kontrak 6 — Komponen bersama harus buta terhadap fitur

Komponen di `designsystem/` **tidak boleh** mengimpor apa pun dari `presentation/<fitur>/` atau dari
`domain/`. Dia menerima `String`, `Color`, dan lambda — bukan `PipelineNode` atau `FlowHealthStatus`.

```kotlin
// ❌ DITOLAK — design system tahu soal domain
@Composable fun ClayBadge(status: FlowHealthStatus) { … }

// ✅ BENAR — design system netral, fitur yang menerjemahkan
@Composable fun ClayBadge(text: String, tint: Color, dot: Boolean = false) { … }

// dan di lapisan fitur:
@Composable
fun HealthStatusPill(status: FlowHealthStatus) = ClayBadge(
    text = status.label,
    tint = Color(status.badgeColorHex),
    dot = true
)
```

Pembungkus tipis berbasis domain seperti `HealthStatusPill` **tetap di package fiturnya**.

### Kontrak 7 — Bentuk, ketebalan, dan spasi juga token

```kotlin
// ❌ DITOLAK
shape = RoundedCornerShape(12.dp)
border = BorderStroke(1.dp, …)
Arrangement.spacedBy(8.dp)

// ✅ BENAR
shape = ClayShapes.Chip
borderWidth = ClayBorder.Medium
Arrangement.spacedBy(ClaySpacing.Md)
```

Token bentuk dinamai per **peran** (`Panel` / `Card` / `Button` / `Tile` / `Chip` / `Pill`), bukan per
ukuran (`Large` / `Medium` / `Small`). Nama berbasis ukuran menggoda orang memakai `Medium` untuk dua
hal yang tidak berhubungan, lalu keduanya terikat selamanya.

### Kontrak 8 — Ketebalan outline konsisten, warnanya yang berbicara

State dibedakan lewat **warna outline**, bukan ketebalannya.

```kotlin
// ❌ DITOLAK — tiga ketebalan untuk tiga state; kartu terpilih jadi "menggemuk"
val border = when {
    isSelected   -> BorderStroke(2.dp, Primary)
    isBottleneck -> BorderStroke(1.5.dp, statusColor)
    else         -> BorderStroke(1.dp, Border)
}

// ✅ BENAR — satu ketebalan, warna yang membedakan
val outlineColor = when {
    isSelected   -> WeMadeColors.Primary
    isBottleneck -> Color(node.healthStatus.badgeColorHex)
    else         -> WeMadeColors.Outline
}
```

---

## 4. Kontrak Tipografi

### Kontrak 9 — Jangan tanam `color` ke dalam `TextStyle`

```kotlin
// ❌ DITOLAK — mematikan LocalContentColor; teks di atas kartu gelap tetap keluar slate
bodySmall = TextStyle(fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)

// ✅ BENAR — warna diserahkan ke pemanggil
bodySmall = TextStyle(fontSize = 12.sp)
```

`color` eksplisit di `TextStyle` selalu menang atas `LocalContentColor`, jadi menanamnya membuat
seluruh mekanisme pewarnaan kontekstual Compose tidak berfungsi.

### Kontrak 10 — Seluruh 15 peran Material wajib terdefinisi

Peran yang dilewat membuat komponen M3 bawaan jatuh ke default Roboto dan **tidak akan pernah**
ikut memakai Nunito, sebagus apa pun font-mu terpasang.

### Kontrak 11 — Font dibundel sebagai instance statis

Font ditaruh di `app/shared/src/commonMain/composeResources/font/`, nama huruf kecil + underscore.
**Gunakan instance statis per bobot, bukan variable font** — dukungan variable font belum seragam di
5 target KMP, dan gejalanya sulit didiagnosis (semua bobot ter-render sebagai Regular di sebagian
platform).

Package `Res` wajib dikunci di `app/shared/build.gradle.kts`:

```kotlin
compose.resources {
    publicResClass = true
    packageOfResClass = "com.eventverse.app.shared.resources"
    generateResClass = always
}
```

---

## 5. Kontrak Layout

### Kontrak 12 — Clay memakan ruang; sesuaikan densitasnya

Outline 3dp + hard shadow 6dp menambah **~18dp per kartu**. Setiap kali mengkonversi layar padat,
lebar kolom/kontainer wajib ditinjau ulang.

Contoh nyata: kolom swimlane Factory Flow dinaikkan **305dp → 324dp**.

Nunito juga punya **x-height lebih besar** dari Roboto/SF. Teks ≤10sp yang tadinya terbaca jadi
berdesakan. Naikkan tier ukuran **satu tingkat serentak** (9→10, 10→11, 11→12) — jangan berurutan,
karena substitusi bertahap akan menaikkan angka yang sama dua kali dan hierarki ukuran kolaps.

### Kontrak 13 — Elemen yang boleh mengalah wajib dinyatakan eksplisit

Di dalam `Row` dengan `SpaceBetween`, elemen yang boleh menyusut harus diberi
`Modifier.weight(1f, fill = false)` + `maxLines` + `overflow`. Tanpa itu, ia mengambil hampir seluruh
lebar dan menyisakan beberapa dp untuk tetangganya, yang lalu memecah teksnya **satu huruf per baris**.

```kotlin
Row(horizontalArrangement = Arrangement.SpaceBetween) {
    Row(modifier = Modifier.weight(1f, fill = false)) {      // boleh mengecil
        Text(labelPanjang, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(ClaySpacing.Sm))
    ClayBadge(text = status, tint = tint)                     // tidak boleh menyusut
}
```

### Kontrak 14 — Urutan modifier `claySurface` tidak boleh ditukar

Lihat [`.claude/skills/compose-design-system/references/clay-recipe.md`](../skills/compose-design-system/references/clay-recipe.md)
untuk penjelasan lengkapnya. Ringkasnya: `padding` (reservasi ruang bayangan) → `offset` → `drawBehind`
(bayangan) → `clip` → `background` → `drawBehind` (inner shade) → `border`.

Menaruh `drawBehind` bayangan **setelah** `clip` akan memotong habis bayangannya. Menghilangkan
`padding` akan membuat bayangan menimpa elemen tetangga.

---

## 6. Kontrak Mode Gelap

### Kontrak 15 — Mode gelap lewat theme, bukan lewat ternary

```kotlin
// ❌ DITOLAK — pola yang sedang kita bayar utangnya sekarang
color = if (isPresentationMode) Color(0xFF0F172A) else WeMadeColors.Surface
```

Utang teknis yang ada: "presentation mode" di Factory Flow masih berupa ~15 ternary
`if (isPresentationMode)` yang tersebar di 6 file. Token gelapnya sudah tersedia
(`WeMadeColors.SurfaceDark` / `SurfaceDarkElevated` / `BackgroundDark` / `OutlineInverse`).

**Jangan menambah ternary baru.** Fitur baru yang butuh mode gelap wajib menunggu atau ikut
mengerjakan `darkColorScheme` + `WeMadeTheme(darkTheme: Boolean)`.

---

## 7. Checklist Verifikasi Sebelum Merge (Definition of Done)

- [ ] Nol literal `Color(0xFF……)` baru di luar `WeMadeTheme.kt`
      (`grep -rn "Color(0xFF" <file-yang-disentuh>` — kecuali `*.colorHex` dari domain)
- [ ] Nol `RoundedCornerShape(N.dp)` telanjang; semua lewat `ClayShapes.*`
- [ ] Nol `Modifier.shadow()`; kedalaman lewat `claySurface(offset = …)`
- [ ] Nol `Card` / `Button` / `OutlinedButton` Material; pakai `ClayCard` / `ClayButton`
- [ ] Pola yang muncul ≥3 kali sudah diangkat ke `designsystem/`
- [ ] Komponen `designsystem/` tidak mengimpor `domain/` maupun package fitur
- [ ] Ketebalan outline konsisten; state dibedakan lewat warna
- [ ] Tidak ada `color` yang ditanam ke dalam `TextStyle`
- [ ] Densitas ditinjau: lebar kontainer dan tier ukuran font disesuaikan
- [ ] Elemen yang boleh mengalah punya `weight(1f, fill = false)` + `maxLines` + `overflow`
- [ ] Tidak ada ternary `isPresentationMode` baru
- [ ] **Kompilasi 5 target**, bukan satu:
      ```bash
      ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
                :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
                :app:shared:jvmTest
      ```
- [ ] **Dijalankan dan dilihat dengan mata**, bukan hanya dikompilasi — bug layout seperti teks
      pecah per huruf tidak akan tertangkap test mana pun
- [ ] **Belum login saat mengecek visual? Login dulu, jangan dilewati.** Kalau halaman yang dicek
      menampilkan "Akses Terbatas: Autentikasi Diperlukan", buka `http://localhost:3001/login` (repo B; repo A: `3000`), klik
      **"Demo Mode: Masuk Cepat (Superadmin Apps)"** (`superadmin_apps` / `PLATFORM_SUPERADMIN`),
      lalu kembali ke halaman tujuan dan lakukan pengecekannya. "Belum login" **bukan** alasan sah
      untuk melaporkan UI tanpa melihatnya.
- [ ] **Layar lain yang tidak dikonversi ikut diperiksa** jika `WeMadeTheme.kt` disentuh —
      `shapes` dan `colorScheme` berdampak ke seluruh aplikasi, jadi "pilot satu layar" tidak
      pernah benar-benar terisolasi
- [ ] Diuji di lebar sempit (~1280dp): reservasi ruang bayangan bekerja, kartu tidak saling timpa
- [ ] Perancah verifikasi sementara (preview main, dependency sementara) sudah dihapus
- [ ] Dokumentasi pengajaran dibuat di `docs/teaching/`

---

## 8. Utang Teknis Terdaftar (jangan ditambah, boleh dicicil)

| Utang | Lokasi | Ukuran |
|---|---|---|
| Literal warna belum disapu | `OrgChartScreen.kt` (108), `ModuleCardView.kt` (19), `AssignDepartmentModal.kt` (19), `ModuleMatrixCard.kt` (17), `RoleListSidebar.kt`, `OrgNodeCard.kt`, `TShapeChartView.kt`, `LoginScreen.kt` | ~290 literal |
| Layar belum dikonversi ke clay | Org Chart, RBAC, Login, top bar `App.kt:98-337` | 3 layar + shell |
| Mode gelap masih ternary | 6 file di `presentation/pipeline/` | ~15 ternary |
| Tidak ada adaptivitas window size | Seluruh `presentation/**` | greenfield |

Setiap kali menyentuh file di daftar ini untuk alasan apa pun, **cicil** bagiannya — jangan menambah
barisnya.

---

# WeMade ERP — Aturan Pendaftaran Komponen Input Bersama & Tipe Field

Status sejajar dengan [`design-system-rules.md`](design-system-rules.md) (bagaimana rupanya),
[`module-integration-rules.md`](module-integration-rules.md) (apa yang dikerjakan), dan
[`tenant-variability-rules.md`](tenant-variability-rules.md) (kode vs data). Dokumen ini menjawab satu pertanyaan:
**saat sebuah komponen input atau tipe field bersama dibuat, ke mana ia wajib didaftarkan supaya seluruh sistem —
renderer, validator, generator kode, dan agent AI — mengenalnya?**

Lahir dari temuan 2026-10-08: tipe `DATE` hanya dirender sebagai kolom teks (`TTTT-BB-HH`), tidak ada `DatePicker`
di seluruh `app/shared`, dan kosakata tipe field tersebar di dua tempat yang tidak saling mengenal. Komponen yang
"ada di UI tapi tidak terdaftar di kosakata" tidak bisa dipilih agent, tidak punya tipe kolom SQL, dan tidak
divalidasi — ia hanya terlihat ada.

---

## 1. Dua Kosakata Tipe Field (jangan tertukar)

| Kosakata | Lokasi | Dipakai untuk | Bentuk |
|---|---|---|---|
| **Prototype / Builder** | `core/.../domain/prototype/EntitySpec.kt` `enum class FieldType` (`TEXT, NUMBER, DATE, ENUM, BOOL`) | Layar prototype, draf pack hasil agent, generator kode modul (SQL, route) | enum tertutup |
| **CRM custom field** | `core/.../domain/customfield/FieldType.kt` `sealed interface FieldType` (Text, LongText, Number, SingleSelect, DateField, Checkbox, UserRef, …) | Kolom kustom per tenant di modul CRM | sealed hierarchy ber-parameter |

Keduanya sengaja terpisah hari ini. Menambah tipe ke salah satunya **tidak otomatis** menambah ke yang lain, dan
itu keputusan yang harus ditulis, bukan terjadi karena lupa (lihat §6).

---

## 2. Aturan Wajib

### Kontrak 1 — Komponen common tidak boleh berdiri sendiri
Komponen input bersama baru (date picker, input mata uang, pilihan ganda, unggah file, …) **wajib didaftarkan ke
kosakata tipe field yang relevan dalam PR yang sama**. "Komponennya dulu, daftarnya nanti" ditolak. Komponen yang
tidak terdaftar adalah kode mati bagi agent dan generator.

### Kontrak 2 — Tipe baru atau varian? (tangga keputusan)
1. **Hanya beda tampilan atau format** → *bukan tipe baru*. Jadikan parameter pada tipe yang ada. Contoh yang sudah
   dipakai: mata uang = `Number(format = Currency)`, bukan tipe `Currency` (alasan di KDoc `customfield/FieldType.kt`:
   penyimpanan, filter, urutan, dan koersi identik).
2. **Beda cara menyimpan, memvalidasi, atau mengurutkan** → tipe baru.
3. **Beda hanya di kontrol UI untuk tipe yang sama** (mis. `DATE` dari kolom teks menjadi date picker) → **tidak
   mengubah kosakata**; ganti kontrolnya di komponen bersama (`FieldInput`) dan tulis di Discovery Note.

### Kontrak 3 — Satu pintu komponen input
Kontrol input untuk tipe field hidup di **satu tempat** per kosakata: untuk prototype
`presentation/discovery/fields/FieldInput.kt` (dipakai Form Blok, Form Inline Tabel, Dialog Detail Kanban); komponen
dasarnya di `presentation/designsystem/`. Dilarang menulis kontrol input tipe field di layar fitur — Aturan Tiga Kali
(`design-system-rules.md` Kontrak 4). Komponen `designsystem/` buta domain (menerima `String`/`Color`/lambda).

### Kontrak 4 — Titik pendaftaran tipe baru (kosakata prototype)
Cari semua penyebutnya sebelum menulis kode — daftar di bawah dari `grep` 2026-10-08, **jalankan ulang, jangan
mengandalkan daftar ini**:

```bash
grep -rln "FieldType" --include='*.kt' core/src/commonMain server/src/main app/shared/src/commonMain
```

| Lapisan | Titik | Yang dipastikan |
|---|---|---|
| Domain | `EntitySpec.kt` (enum, validasi `FieldSpec`), `PrototypeSpec.kt`, `InteractiveScreenFactory.kt`, `ChangeWidgetOp.kt`, `SpecOpApplier.kt`, `DeterministicSpecOpProposer.kt` | Nilai tipe baru valid di spec, dapat diubah lewat `SpecOp`, dan reducer menegakkan validasinya |
| Usulan layar | `proposal/ProposalEntityRules.kt`, `ProposalEdit.kt`, `ProposalViewRules.kt`, `DeterministicScreenProposer.kt`, `DeterministicScreenRoles.kt`, `PackSuggestionMapping.kt` | Validator usulan mengenal tipe; usulan deterministik tidak menghasilkan tipe yang tak didukung renderer |
| Codec | `shared/pack/InteractiveScreenCodec.kt`, `SpecOpCodec.kt`, `ScreenSuggestionCodec.kt`, `shared/discovery/ScreenProposalCodec.kt` | Dokumen berisi tipe baru terbaca; **nilai tak dikenal ditolak** (Kontrak 4 variability), bukan jatuh ke `TEXT` |
| Generator kode | `handoff/SpecColumns.kt` (tipe kolom SQL), `SpecPostgresWriter.kt`, `SpecRoutesWriter.kt` | Tipe punya pemetaan SQL, tulis, dan baca |
| Agent AI | `infrastructure/discovery/KoogDiscoveryTools.kt` (`screen_catalog`), `KoogDiscoveryPrompt.kt`, `infrastructure/builder/KoogModuleEditor.kt` | Katalog yang dibaca model memuat tipe baru dan aturan pemakaiannya; prompt tidak menyebut daftar tipe yang basi |
| UI | `presentation/discovery/fields/FieldInput.kt`, `TableCell.kt`, `InlineRowEditor.kt`, `KanbanDetailDialog.kt`, `InteractiveFormState.kt`, `InteractiveTableState.kt` | Setiap konteks (form, sel tabel, kartu kanban) tahu menggambar dan menyunting tipe baru |

### Kontrak 5 — Titik pendaftaran tipe baru (CRM custom field)
`customfield/FieldType.kt`, `CustomFieldValidation.kt`, `CustomAttributesCodec.kt`, `FieldTypeConversion.kt`
(aturan konversi antar tipe), `AddCustomFieldDefinitionUseCase.kt`, `PostgresCustomFieldDefinitionRepository.kt`
(kolom `field_type`), serta UI `AddCustomFieldDialog.kt` dan `LeadCustomField*.kt`. Tipe baru yang butuh integritas
referensial memakai `isReferential`; **tidak** ada migrasi skema untuk tipe tanpa penyimpanan baru.

### Kontrak 6 — Kompilator menjaga, bukan ingatan
`when (fieldType)` pada kosakata mana pun **dilarang memakai `else`**. Cabang yang dipaksa kompilator itulah daftar
titik pendaftaran yang tidak bisa terlewat. Tipe baru yang membuat kompilasi gagal di sebuah `when` berarti titik itu
memang harus disentuh — jangan "menenangkan" kompilator dengan `else`.

### Kontrak 7 — Tipe baru wajib punya tes paritas
- Tes yang **mengiterasi `FieldType.entries`** (atau varian sealed-nya) dan memastikan tiap tipe punya: pemetaan SQL,
  kontrol input, entri katalog agent, serta round-trip codec. Entri baru tanpa padanan → tes gagal.
- Tes tenant/kontekst kedua: tipe baru diuji di minimal **dua** konteks (mis. form dan sel tabel) dan pada pack
  non-default.

### Kontrak 8 — Komponen yang belum ada tidak boleh dipalsukan
Bila kebutuhan di luar kosakata (mis. unggah file belum didukung), **jangan** memetakannya diam-diam ke `TEXT`.
Pilihannya: (a) tolak dengan pesan yang jelas, (b) pakai `CUSTOM_SCREEN` sebagai sketsa kerangka, atau (c) ajukan
sebagai permintaan pembuatan komponen lewat antrean build (TRD-PLAT-006/007). Memetakan ke `TEXT` mengubah data
tanpa jejak.

**Pengecualian sah: `CUSTOM_SCREEN` sebagai kerangka (C10).** Layar yang butuh komponen belum ada boleh diusulkan
sebagai `ViewProposal.Skeleton` berisi `SkeletonBlock` (label singkat, lebar `FULL`/`HALF`, hint dari daftar tertutup
`TABLE`/`FORM`/`METRIC_CARDS`/`ACTIONS`). Ini cara sah **menolak tanpa memalsukan**: sketsa non-interaktif untuk
dinilai, bukan data yang tersimpan sebagai tipe lain. Batasnya: kerangka **bukan tipe field** dan tidak
menggantikan entri di kosakata mana pun; field tetap wajib bertipe yang terdaftar (tak dikenal ditolak). Hint/lebar
tak sah ditolak oleh codec (bukan fallback senyap), dan kerangka hanya berlaku untuk `CUSTOM_SCREEN`.

---

## 3. Alur Kerja Saat Menambah Komponen/Tipe

1. **Discovery** (`wemade-feature-discovery`): komponen ini tipe baru, varian (Kontrak 2), atau hanya kontrol baru?
   Siapa yang memakainya (prototype, CRM, keduanya)? Tulis di Discovery Note.
2. **Kosakata dulu**: tambahkan tipe/parameter + validasi di domain, biarkan kompilator menunjukkan titik yang
   terlewat (Kontrak 6).
3. **Satu pintu UI**: kontrolnya di komponen bersama + dasarnya di `designsystem/` (Kontrak 3), ikuti
   `design-system-rules.md` (token, Clay, tanpa literal warna).
4. **Agent**: perbarui `screen_catalog` dan aturan prompt; jalankan evaluasi agent deterministik (tanpa LLM berbayar).
5. **Generator**: pemetaan SQL + route; coba hasil scaffold pada modul uji.
6. **Tes paritas** (Kontrak 7) dan cek visual di dua konteks.
7. **Dokumentasi**: teaching doc (CLAUDE.md §12); bila aturan berubah → `scripts/sync-agent-config.sh`.

---

## 4. Anti-Pola yang Ditolak

- Komponen input baru ditulis langsung di layar fitur tanpa lewat komponen bersama.
- Tipe baru ditambahkan di UI saja (agent dan generator tidak tahu).
- `else ->` pada `when (FieldType)`.
- Nilai tipe tak dikenal dibaca sebagai `TEXT`.
- Menambah tipe untuk sekadar perbedaan format (mata uang, persen, telepon) — jadikan parameter.
- Mengubah salah satu kosakata dan mengira yang lain ikut berubah tanpa menuliskan keputusannya.

---

## 5. Checklist Verifikasi Sebelum Merge (Definition of Done)

- [ ] Discovery Note menyatakan tipe baru / varian / kontrol baru, dan kosakata mana yang terdampak
- [ ] `grep -rln "FieldType"` dijalankan ulang; setiap penyebut ditinjau
- [ ] Tidak ada `else ->` baru pada `when (FieldType)`
- [ ] Kontrol input hanya di komponen bersama; tidak ada literal warna/bentuk (design-system-rules)
- [ ] Codec menolak nilai tak dikenal; tidak ada fallback senyap ke `TEXT`
- [ ] Katalog agent (`screen_catalog`) dan aturan prompt memuat tipe baru
- [ ] Generator kode: pemetaan SQL, tulis, dan baca; scaffold diuji pada modul uji
- [ ] Tes paritas yang mengiterasi tipe; tes di ≥ 2 konteks dan pack non-default
- [ ] Kompilasi 5 target; cek visual (login superadmin demo, lalu tenant uji non-garment)
- [ ] Teaching doc dibuat; `scripts/sync-agent-config.sh` dijalankan bila aturan berubah

---

## 6. Keputusan Terbuka (belum diputuskan)

- **Menyatukan dua kosakata** (prototype vs CRM)? Keduanya bergerak terpisah dan mulai menyimpang (CRM punya
  `LongText`, `UserRef`, `DateField(withTime)`; prototype hanya 5 tipe). Menyatukan menyederhanakan agent dan
  generator tetapi menyentuh CRM yang sudah berjalan. Sampai ada keputusan, aturan §2 berlaku untuk **masing-masing**.
- Tes paritas Kontrak 7 belum ada untuk kedua kosakata; membuatnya adalah bagian pekerjaan pertama
  (`PLAN-field-component-gaps.md`).

---

# WeMade ERP — Aturan Standar Batas Ukuran File (File Size & Decomposition)

Dokumen ini adalah **aturan baku ukuran file** yang wajib ditaati setiap kali membuat atau
mengubah file Kotlin di repo ini. Statusnya sejajar dengan
[`module-integration-rules.md`](module-integration-rules.md) dan
[`design-system-rules.md`](design-system-rules.md): kalau yang pertama mengatur *apa yang
dikerjakan* dan yang kedua *bagaimana rupanya*, dokumen ini mengatur **seberapa besar satu file
boleh tumbuh sebelum ia berhenti bisa dibaca**.

**Ruang lingkup**: seluruh `*.kt` di `core/`, `app/`, dan `server/`.

---

## 1. Paradigma: Batas Baris Adalah Alarm, Bukan Target

Batas ini **bukan** soal estetika atau menghitung baris demi menghitung baris. Panjang file adalah
*proxy* paling murah untuk tiga penyakit yang sebenarnya:

1. **File melanggar Single Responsibility** — satu file mengerjakan lima hal, jadi tidak ada nama
   yang jujur untuk isinya.
2. **Pola visual/logika disalin, bukan diangkat** — gejala yang sama dengan pelanggaran
   [Aturan Tiga Kali](design-system-rules.md#kontrak-4--aturan-tiga-kali-rule-of-three).
3. **File tidak lagi bisa direview** — satu file yang melebihi batas tidak bisa dibaca sekali duduk
   maupun dinilai utuh dalam satu review, sehingga bug lolos di bagian yang tidak sempat dibaca.

> **Konsekuensinya**: melewati batas **tidak** boleh diselesaikan dengan memotong file di tengah
> secara sembarang (`FooScreenPart2.kt`). Memecah file wajib mengikuti **batas tanggung jawab**,
> bukan batas baris. Kalau tidak ada garis pisah yang jujur, itu tandanya masalahnya bukan panjang
> file — melainkan desainnya.

---

## 2. Ambang Baris per Lapisan

Satu angka global tidak masuk akal: Compose secara struktural lebih panjang dari domain murni
(median `presentation/` di repo ini 3,4× median `core/`). Karena itu ambangnya per lapisan.

| Lingkup | Soft (peringatan) | Hard (tolak merge) | Alasan ambang |
|---|---|---|---|
| `core/**` (domain murni) | **250** | **400** | p90 lapisan ini 196 baris. Entity/VO >250 hampir pasti God Entity |
| `app/shared/**/presentation/**` | **400** | **600** | median 203; Compose butuh ruang, tapi 600 adalah batas satu kali duduk |
| `server/src/main/**` | **300** | **500** | p90 lapisan ini 374; routes & repository Postgres |
| `**/commonTest/**`, `**/jvmTest/**` | **500** | **800** | test memang repetitif; memecahnya merugikan keterbacaan kasus |

**Kalau ragu atau lingkupnya tidak terdaftar: soft 400 / hard 600.**

Cara membacanya:

- **Di bawah soft** — tidak perlu berpikir, lanjut.
- **Melewati soft** — boleh lanjut, tapi wajib berhenti sebentar dan bertanya: *apakah file ini
  masih punya satu nama yang jujur?* Kalau jawabannya tidak, pecah sekarang selagi murah.
- **Melewati hard** — **berhenti**. Dilarang menambah baris ke file itu tanpa memecahnya lebih
  dulu, kecuali masuk pengecualian §3.

### Kontrak 1 — Hard limit berlaku ke *file setelah diubah*, bukan ke diff-nya

Menambah 10 baris ke file 700 baris tetap pelanggaran. Aturan ini tentang hasil akhir, bukan
ukuran perubahan.

### Kontrak 2 — Aturan Ratchet: file yang sudah melanggar tidak boleh membesar

Untuk file yang **sudah** di atas hard limit sebelum aturan ini ada (lihat §5), berlaku aturan
searah: **setiap perubahan pada file itu wajib membuatnya lebih pendek, atau minimal tidak lebih
panjang.** Tidak ada kewajiban menormalkannya dalam satu PR — tapi tidak boleh bertambah.

```
# sebelum menyentuh file yang sudah besar
wc -l <file>          # catat angkanya
# ... kerjakan perubahan ...
wc -l <file>          # wajib ≤ angka sebelumnya
```

---

## 3. Pengecualian yang Sah (dan Hanya Ini)

Ambang baris **tidak berlaku** untuk file yang isinya **data terurut, bukan logika bercabang** —
karena memecahnya tidak menambah keterbacaan sedikit pun, hanya menyebarkan satu tabel ke lima
tempat.

Pengecualian wajib **dideklarasikan eksplisit** dengan komentar di baris pertama file:

```kotlin
// FILE-SIZE-EXEMPT: katalog aset — data terurut, bukan logika. Lihat .claude/rules/file-size-rules.md §3
```

Yang memenuhi syarat:

| Kategori | Contoh di repo ini | Kenapa sah |
|---|---|---|
| Katalog ikon / vector path | `ClayIcons.kt` (1354), `PipelineIcons.kt` (679) | Deretan `Path` deklaratif; nol percabangan |
| Seed / preset template | `PipelinePresetFactory.kt` (1224) | Tabel data onboarding per archetype |
| Codec / mapper eksplisit | `SamplingOrderCodec.kt` (510) | Satu baris per field, lurus, tanpa logika |
| Kode ter-generate | — | Bukan kita yang menulis |

Yang **tidak** memenuhi syarat, betapa pun besarnya:

- Screen / Dialog / Pane Compose — panjangnya selalu gejala styling yang disalin atau komponen yang
  belum diangkat, bukan gejala data.
- ViewModel — panjangnya selalu gejala terlalu banyak tanggung jawab dalam satu state holder.
- Route handler & repository — pecah per agregat/resource.

---

## 4. Pola Pemecahan yang Disarankan

Jangan mengarang struktur baru; ikuti pola yang sudah dipakai repo ini.

### Compose Screen / Dialog yang membengkak

```
presentation/deal/components/
├── DealDetailDialog.kt          # hanya shell: state hoisting, scaffold, wiring event
├── DealDetailHeader.kt          # satu section = satu file
├── DealDetailSpecForm.kt
├── DealDetailTimelinePane.kt
└── DealDetailUiModel.kt         # mapping domain → UI model
```

Aturannya: **file shell hanya merakit, section yang merender.** Kalau setelah dipecah shell-nya
masih >400 baris, berarti dialog itu sebenarnya beberapa layar yang dipaksa jadi satu.

Sebelum memecah, cek dulu apakah bagian yang berulang seharusnya naik ke
`presentation/designsystem/` — sering kali separuh panjangnya adalah styling yang melanggar
[Kontrak 4 design system](design-system-rules.md#kontrak-4--aturan-tiga-kali-rule-of-three).

### ViewModel yang membengkak

Pindahkan logika ke Use Case di `core/` (memang tempatnya menurut
[CLAUDE.md §4](../CLAUDE.md)), lalu pisahkan per sumbu:

```
presentation/orgchart/
├── OrgChartViewModel.kt         # state holder + dispatch event
├── OrgChartUiState.kt           # state & event model
└── OrgChartLayoutCalculator.kt  # perhitungan murni, bisa diuji tanpa ViewModel
```

### Route / Repository server yang membengkak

Pecah per agregat, bukan per HTTP method:

```
routes/
├── CostingRoutes.kt             # composisi: route("/costing") { … }
├── CostingEstimateRoutes.kt
└── CostingRateCardRoutes.kt
```

### Domain file yang membengkak

Satu file = satu konsep, sesuai [CLAUDE.md §8](../CLAUDE.md). Value object kecil boleh digabung
(`EventValueObjects.kt`), tapi begitu file itu >250 baris, kelompokkan per sub-konsep.

---

## 5. Checklist Verifikasi Sebelum Merge (Definition of Done)

- [ ] Tidak ada file yang **melewati hard limit** lapisannya tanpa komentar
      `FILE-SIZE-EXEMPT` yang beralasan menurut §3:
      ```bash
      # semua file Kotlin yang disentuh, diurutkan dari terpanjang
      git diff --name-only --diff-filter=ACM main...HEAD -- '*.kt' \
        | xargs wc -l 2>/dev/null | sort -rn | head -20
      ```
- [ ] Untuk file yang **sudah** di atas hard limit (§5 tabel utang): jumlah barisnya **tidak
      bertambah** (Kontrak 2 / Ratchet)
- [ ] File yang melewati soft limit sudah ditinjau: masih punya satu nama yang jujur
- [ ] Pemecahan mengikuti **batas tanggung jawab**, bukan potongan baris —
      tidak ada `…Part2.kt` / `…Extra.kt` / `…Helpers.kt` tanpa tema
- [ ] Kalau yang dipecah adalah UI: bagian yang berulang sudah diperiksa apakah seharusnya naik ke
      `presentation/designsystem/` alih-alih hanya dipindah file
- [ ] Setelah pemecahan, **kompilasi 5 target** (bukan satu) masih hijau:
      ```bash
      ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
                :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
                :app:shared:jvmTest
      ```
- [ ] Kalau yang dipecah adalah UI: **dijalankan dan dilihat dengan mata** — pemecahan Compose
      mudah menggeser `Modifier` chain dan merusak layout tanpa memecahkan kompilasi

Skrip audit seluruh repo (untuk mengukur kemajuan, bukan gate per-PR):

```bash
find . -name "*.kt" -not -path "*/build/*" -not -path "*/bin/*" \
  | xargs wc -l | grep -v total | awk '$1>600' | sort -rn
```

---

## 6. Utang Teknis Terdaftar (jangan ditambah, boleh dicicil)

Kondisi awal saat aturan ini ditetapkan (2026-09-18): **954 file Kotlin, median 101 baris,
p90 353 baris**. Yang di atas hard limit: **21 file** — 4 di antaranya sah sebagai pengecualian §3,
menyisakan **17 file** untuk dicicil.

| File | Baris | Status |
|---|---|---|
| `presentation/deal/components/DealDetailDialog.kt` | 3006 | **Prioritas 1.** Juga terdaftar di utang design system |
| `presentation/orgchart/OrgChartScreen.kt` | 2420 | **Prioritas 2.** Juga 108 literal warna belum disapu |
| `presentation/designsystem/ClayIcons.kt` | 1354 | Pengecualian §3 — tandai `FILE-SIZE-EXEMPT` |
| `core/domain/pipeline/PipelinePresetFactory.kt` | 1224 | Pengecualian §3 — tandai `FILE-SIZE-EXEMPT` |
| `presentation/orgchart/OrgChartViewModel.kt` | 1196 | Pecah: logika layout → kalkulator murni |
| `presentation/rbac/components/AssignDepartmentModal.kt` | 1057 | Juga 19 literal warna |
| `presentation/invoicing/template/TemplateCanvas.kt` | 983 | |
| `presentation/invoicing/template/DesignerPropertyInspector.kt` | 892 | |
| `presentation/deal/components/DealsPane.kt` | 861 | |
| `presentation/costing/CostingWorkspaceScreen.kt` | 825 | |
| `presentation/pipeline/components/NodeInputInspectorModal.kt` | 809 | |
| `server/routes/CostingRoutes.kt` | 754 | Pecah per agregat |
| `presentation/crm/components/LeadInspectorPane.kt` | 733 | |
| `presentation/auth/LoginScreen.kt` | 716 | Juga terdaftar di utang design system |
| `server/infrastructure/PostgresSamplingOrderRepository.kt` | 698 | |
| `server/Application.kt` | 697 | Pecah: konfigurasi plugin → file terpisah |
| `presentation/pipeline/components/PipelineIcons.kt` | 679 | Pengecualian §3 — tandai `FILE-SIZE-EXEMPT` |
| `server/routes/DealRoutes.kt` | 677 | Pecah per agregat |
| `presentation/navigation/PersonaSwitcherDropdown.kt` | 676 | |
| `server/infrastructure/PostgresModuleDevRepositories.kt` | 628 | Nama jamak = tanda sudah waktunya dipecah |
| `presentation/deal/components/ContactsPane.kt` | 625 | |

Setiap kali menyentuh file di daftar ini untuk alasan apa pun, **cicil** bagiannya (Kontrak 2) —
jangan menambah barisnya.

---

## 7. Catatan Penegakan Otomatis

Saat ini **belum ada gate otomatis**: Detekt belum terpasang di Gradle (hook
`.claude/hooks/validate-detekt.sh` ada tapi belum ada plugin maupun `detekt.yml`), jadi aturan ini
ditegakkan lewat review dan skrip di §5.

Kalau nanti Detekt dipasang, ambang di §2 dipetakan ke:

```yaml
complexity:
  LongMethod:
    threshold: 60
style:
  MaxLineLength:
    maxLineLength: 120
# batas panjang FILE tidak punya rule bawaan detekt —
# gunakan custom rule atau skrip CI di §5
```

---

# WeMade ERP — Aturan Graphify (Graphify First)

Repo ini memiliki knowledge graph Graphify di `graphify-out/` (termasuk `graphify-out/graph.json`, nodes, god nodes, relasi antar-file, dan komunitas).

> **Status Cline**: MCP server `graphify` sudah terpasang di `.cline/mcp_settings.json` dan
> melayani graph repo ini (±15 ribu node, 57 ribu edge). Tool-nya tersedia langsung di setiap
> sesi Cline — tidak perlu install apa pun.

---

## MANDATORY DIRECTIVE: Selalu Cek Graphify Terlebih Dahulu Sebelum Cara Lain

Ketika mencari kode, memahami arsitektur, melacak relasi/dependensi, atau menemukan letak implementasi:
**WAJIB SELALU memeriksa Graphify terlebih dahulu sebelum menggunakan cara pencarian lain (seperti `grep_search`, `search_codebase`, ripgrep/`rg`, `find`, `list_dir`, glob, atau penelusuran file mentah).**

### 1. Urutan Pencarian Wajib (Graphify First)
Bila `graphify-out/graph.json` tersedia:
1. **Via MCP (tersedia di Cline — selalu langkah pertama)**:
   - `query_graph(query="...")` — pertanyaan konsep, alur kerja, atau pencarian fitur.
   - `get_node(name="...")` — simbol/kelas/fungsi tertentu.
   - `shortest_path(source="...", target="...")` atau `get_neighbors(node_id="...")` — relasi & dependensi.
   - `graph_stats` / `god_nodes` — gambaran struktur sebelum menyelam lebih dalam.
2. **Via CLI / Shell** (bila MCP tool belum memangkas cukup):
   - `graphify query "<pertanyaan>"` — subgraph terfokus (jauh lebih hemat token dan akurat dibanding grep mentah).
   - `graphify path "<A>" "<B>"` — relasi antar dua konsep/komponen.
   - `graphify explain "<konsep>"` — penjelasan konsep terfokus.
3. **Navigasi Luas**:
   - Bila `graphify-out/wiki/index.md` ada, telusuri wiki tersebut daripada membaca file mentah satu per satu.
   - Baca `graphify-out/GRAPH_REPORT.md` hanya untuk konteks arsitektur global bila query/path/explain belum cukup.

### 2. Larangan (Anti-Pattern)
- **DILARANG** memanggil `grep_search`, `search_codebase`, `find`, `list_dir`, atau scanning direktori mentah sebagai langkah pertama saat knowledge graph tersedia.
- **DILARANG** membaca berkas kode sembarangan sebelum mengidentifikasi titik masuk lewat Graphify.
- **DILARANG** menyimpulkan "Graphify tidak punya informasinya" tanpa pernah memanggil tool Graphify di sesi tersebut.

### 3. Kapan Boleh Menggunakan Cara Lain (Fallback)
Pencarian konvensional (`grep_search`, `rg`, `find`, pembacaan file langsung) HANYA diperbolehkan jika:
1. Graphify sudah dicek dan hasilnya tidak mencukupi atau konsep belum terindeks — sebutkan query Graphify yang sudah dicoba, ATAU
2. Mencari string literal spesifik (misal: config key exact, regex token, pesan error exact, typo text) yang bukan merupakan simbol AST.

### 4. Pemeliharaan Graph
- Setelah memodifikasi atau menambah file kode, jalankan `graphify update .` untuk menjaga graph tetap mutakhir (hanya AST, cepat, tanpa biaya API).

---

# WeMade ERP — Aturan Standar Pembuatan & Integrasi Modul (Composable & Puzzling Architecture)

Dokumen ini adalah **aturan baku arsitektur** yang wajib ditaati setiap kali membuat, memperluas, atau merefaktorisasi modul fungsional di WeMade ERP (misal: *Procurement*, *Sampling*, *Cutting SPK*, *Sewing Kanban*, *QC Inspection*, *Consignment Receiving*, *B2C Marketplace Sync*, *Custom Costing Engine*, *Invoicing*).

---

## 1. Paradigma Sistem: Composable "Lego / Puzzle" Architecture

Sistem WeMade ERP **TIDAK TERBATAS** pada enum model bisnis yang kaku. Kita menganut arsitektur **Composable Enterprise ERP**:

1. **Preset (FOB, CMT, Brand D2C) adalah Starter Templates**:
   - FOB, CMT, dan D2C **BUKAN batasan mati (hardcoded lock)**.
   - Preset hanyalah **blueprint awal (starter template)** siap pakai saat onboarding agar tenant baru tidak perlu menyusun dari kanvas kosong.
2. **Kebebasan Merangkai Alur (Puzzling / Directed Acyclic Graph)**:
   - Setiap tenant memiliki kebebasan 100% untuk menambah node, menghapus node, menukar urutan proses, atau membuat alur hibrida (misal: *Makloon Sablon & Jahit*, *FOB Custom Seragam*, atau *Distro D2C dengan Sub-kon Jahit Luar*).
3. **Konsep "Padanan Modul" (Module Archetype / Capability Slot)**:
   - Modul yang dibuat developer diklasifikasikan ke dalam **Archetype** (slot kemampuan fungsional).
   - **Contoh Nyata**: Modul HPP Tenant A (*Full BOM Costing*) dan Modul HPP Tenant B (*Tarif Menit SAM Makloon*) adalah **SEPADAN** karena sama-sama mengisi slot archetype `COSTING_HPP`. Keduanya bisa saling ditukar (*interchangeable*) tanpa merusak modul sebelum (*Tech Pack*) dan modul sesudahnya (*Alokasi Mesin/SPK*).

---

## 2. Klasifikasi Slot Kemampuan (Module Archetype Registry)

Setiap modul baru **WAJIB** menyatakan padanannya pada salah satu `ModuleArchetype`:

| Archetype Code | Nama Padanan Slot | Ekspektasi Data Masuk (Input Port) | Ekspektasi Data Keluar (Output Port) | Contoh Variasi Implementasi |
|---|---|---|---|---|
| `ORDER_INGESTION` | Penerimaan Pesanan / Sales | `CommercialInquiry` | `ProductionOrderDraft` | RFQ B2B Ekspor, SPK Makloon Buyer, Order Marketplace Shopee/TikTok |
| `RAW_MATERIAL` | Bahan Baku & Gudang | `MaterialRequisition` | `VerifiedMaterialStock` | Pembelian Kain Supplier (FOB), Penerimaan Kain Titipan Konsinyasi (CMT), Gudang Kain Multi-Roll |
| `COSTING_HPP` | Mesin Hitung Biaya & HPP | `TechPackAndYieldData` | `CostingCalculationResult` | HPP Rumus Penuh (Kain+Jahit+Margin), HPP Borongan SAM per Menit, HPP Valuasi Retail + Fee Marketplace |
| `CUTTING` | Pemotongan Pola (Spreading) | `CuttingOrderWithFabric` | `CutPiecesBundle` | Meja Potong Manual, Laser Cutting Otomatis, Makloon Jasa Potong Saja |
| `SEWING` | Penjahitan & Perakitan | `CutPiecesBundle` | `AssembledGarmentBundle` | Lini Jahit Ban Berjalan, Stasiun Borongan Perorangan, Alur Jahit Makloon Luar (Subcontract) |
| `FINISHING` | Cuci, Gosok & Trimming | `AssembledGarmentBundle` | `FinishedGarmentUnit` | Setrika Uap Konveksi, Garment Washing/Dyeing, Labeling & Hangtag |
| `QUALITY_CONTROL` | Pengawasan Mutu & AQL | `FinishedGarmentUnit` | `InspectedAndGradedUnit` | QC 100% End-line, Sampling AQL 2.5 Ekspor, QC Verifikasi Cacat Bahan Buyer |
| `FULFILLMENT` | Packing, Surat Jalan & Kirim | `InspectedAndGradedUnit` | `DispatchedShipmentManifest` | Packing Karton Ekspor B2B, Retur Kain Sisa Makloon, Pick-Pack-Scan Barcode E-commerce |
| `CUSTOM_EXTENSION`| Plugin / Ekstensi Bebas | `AnyOperationalPayload` | `AnyOperationalPayload` | Sablon Manual, Bordir Komputer, Laundry Kimia, Approval Khusus Direksi |

---

## 3. Tujuh Kontrak Baku Pembuatan Modul (The 7 Modular Contracts)

Setiap kali developer membuat class modul operasional di WeMade ERP, kontrak berikut wajib dipenuhi:

### Kontrak 1: Deklarasi Archetype & Template Rekomendasi
- Modul wajib mendefinisikan `archetype: ModuleArchetype`.
- Modul mencantumkan `recommendedStarterPresets`: di template starter mana modul ini sebaiknya aktif secara default (misal: modul pengadaan kain aktif di FOB & D2C, tapi di-bypass di template starter CMT).

### Kontrak 2: Kompatibilitas Port Puzzle (Input & Output Port Typing)
- Modul adalah "blok puzzle" independen.
- Port Input harus mendeklarasikan tipe data yang diterima (`acceptedInputDataTypes`).
- Port Output harus mendeklarasikan tipe data yang dipancarkan (`producedOutputDataType`).
- Dua modul bisa disambungkan dalam alur custom tenant **jika dan hanya jika** tipe data output Modul A kompatibel dengan tipe data input Modul B.

### Kontrak 3: Semantik Kepemilikan Stok (`StockOwnershipSemantics`)
Modul gudang dan bahan baku dilarang mencampuradukkan status kepemilikan:
- `OWNED_RAW_MATERIAL`: Bahan baku dibeli dan dimiliki pabrik (masuk neraca aset keuangan).
- `CONSIGNED_CLIENT_MATERIAL`: Kain milik buyer/klien yang dititipkan (nilai Rp 0 di neraca pabrik; wajib rekonsiliasi sisa kain perca/waste).
- `INTERNAL_FINISHED_GOODS`: Stok baju jadi milik brand internal per SKU x Warna x Ukuran.

### Kontrak 4: Strategi Kalkulasi Biaya Fleksibel (`CostingBehavior`)
- Modul yang menempati slot `COSTING_HPP` harus memisahkan logika rumus dari core engine:
  - Gunakan **Strategy Pattern** atau parameter dinamis (`customFormulaParameters`).
  - Tenant bebas mengatur koefisien (misal: margin laba, tarif SAM per detik, biaya jarum/benang).
  - Pada model jasa makloon murni, harga kain titipan tidak boleh dijumlahkan ke total tagihan invoice klien.

### Kontrak 5: Penanganan Cacat & Alur Mundur (`DefectLiability & Rework Loop`)
Setiap modul lantai produksi wajib memiliki jalur penanganan jika terjadi cacat/reject:
- Bedakan tanggung jawab:
  - `FACTORY_WORKMANSHIP`: Kesalahan operator pabrik -> biaya pengerjaan ulang (*rework*) ditanggung pabrik.
  - `CLIENT_SUPPLIED_DEFECT`: Cacat serat kain bawaan buyer -> disisihkan dan dicatat berita acara agar tidak merugikan penjahit.
  - `SUPPLIER_VENDOR_DEFECT`: Cacat dari pabrik tekstil rekanan -> retur nota debit ke supplier.

### Kontrak 6: Telemetri Pemantauan Alur (`Operational Telemetry`)
Agar node modul dapat berkedip hijau/kuning/merah di visualisasi kanvas:
- Modul wajib menyediakan: `wipPieces` (antrean potong/baju yang sedang tertahan), `cycleTimeHours` (kecepatan kerja), dan `healthStatus` (`HEALTHY`, `WARNING`, `BOTTLENECK`, `CRITICAL`).

### Kontrak 7: Isolasi Multi-Tenant & Konfigurasi Pipeline
- Data konfigurasi alur kustom disimpan pada entitas `CustomTenantPipeline` berdasar `TenantId`.
- Alur pipeline diisolasi per tenant sehingga modifikasi node oleh satu pabrik tidak berdampak ke tenant lain.

### Kontrak 8: Aturan Baku Kapabilitas Jangkauan Data Modul (`ScopeCapability` & `DataScope`)
Setiap modul baru **WAJIB** mendeklarasikan salah satu dari dua kapabilitas jangkauan data (`ScopeCapability`):

#### 1. `ScopeCapability.GLOBAL_ONLY` (Kolektif / Shared Factory)
- **Karakteristik**: Digunakan untuk modul yang datanya merupakan aset bersama pabrik dan tidak logis dipartisi per individu pembuat.
- **Daftar Modul Contoh**: Bahan Baku Gudang (`INVENTORY`), Spesifikasi BOM (`TECH_PACK_BOM`), Kalkulasi HPP (`COSTING_HPP`), Jadwal Mesin (`PRODUCTION_MRP`), Inspeksi QC (`QUALITY_CONTROL`), Packing & Surat Jalan (`FULFILLMENT`).
- **Aturan Opsi Wewenang**:
  - Pilihan scope **otomatis terkunci 100% pada `DataScope.ALL_TENANT_DATA`** ("Seluruh Data Pabrik").
  - Opsi *Data Sendiri* (`OWN_DATA_ONLY`) dan *Data Bawahan* (`SUBORDINATE_DATA`) **TIDAK TERSEDIA** (disembunyikan dari UI modal wewenang) untuk mencegah kesalahan staf yang mengira sistem rusak saat data kain/mesin kosong.
  - Tampilan UI: Menampilkan badge informatif tunggal `🌐 Seluruh Pabrik (Shared)`.
- **Proteksi Domain**: Otomatis dilindungi oleh method `ModuleAccessConfig.sanitizeFor(module)` yang memaksa scope kembali ke `ALL_TENANT_DATA` jika ada request payload ilegal.

#### 2. `ScopeCapability.HIERARCHICAL` (Hirarkis / Per-Karyawan & Tim)
- **Karakteristik**: Digunakan untuk modul transaksional di mana dokumen dimiliki oleh staf pembuat (*maker*) dan diawasi oleh kepala divisinya (*checker/leader*).
- **Daftar Modul Contoh**: Pelanggan & Prospek Sales (`CRM_SALES`), Order Sampling Desain (`SAMPLING_ORDER`), Catatan Kerja Operator (`OPERATOR_EXEC`).
- **Aturan Opsi Wewenang**:
  - Tersedia **3 pilihan lengkap** yang dapat dipilih oleh admin pabrik:
    1. `DataScope.OWN_DATA_ONLY` ("Data Sendiri"): Pengguna hanya melihat dokumen miliknya sendiri.
    2. `DataScope.SUBORDINATE_DATA` ("Data Bawahan"): Pengguna melihat data miliknya dan seluruh staf bawahannya di divisi yang sama.
    3. `DataScope.ALL_TENANT_DATA` ("Semua Data"): Pengguna melihat seluruh data lintas cabang/pabrik.
  - Tampilan UI: Menampilkan segment selector 3 tab `[Data Sendiri | Data Bawahan | Semua Data]`.

---

## 4. Checklist Verifikasi Developer Sebelum Merge (Definition of Done)

- [ ] Apakah modul telah menetapkan `ModuleArchetype` yang tepat sehingga sepadan dengan modul sejenis?
- [ ] Apakah tipe data Input Port dan Output Port telah terdokumentasi dan kompatibel?
- [ ] Apakah modul mendukung parameter kustom per tenant tanpa perlu mengubah kode `core`?
- [ ] Jika memproses kain titipan, apakah status persediaan ditandai `CONSIGNED_CLIENT_MATERIAL` (Rp 0 di neraca)?
- [ ] **Apakah `scopeCapability` modul sudah ditentukan dengan benar?**
  - Jika data kolektif pabrik (kain/HPP/mesin) ➔ set `ScopeCapability.GLOBAL_ONLY` (hanya 1 opsi: Semua Data).
  - Jika data transaksi perorangan/sales/operator ➔ set `ScopeCapability.HIERARCHICAL` (tersedia 3 opsi: Sendiri / Bawahan / Semua Data).
- [ ] Apakah modul telah diuji berjalan pada alur bawaan (FOB/CMT/D2C) maupun alur custom hasil utak-atik (*puzzled*)?
- [ ] Apakah modul menyertakan dokumentasi pengajaran (*teaching*) di `docs/teaching/`?

---

## 5. Anatomi Pendaftaran (apa yang wajib disentuh per jenis)

Hasil scan kode 2026-09-29. Tentukan dulu **jenisnya** (skill `wemade-feature-discovery`), lalu ikuti
baris yang sesuai. "Otomatis" = tidak perlu disentuh; ikut dari pendaftaran.

### 5.1 Modul OPERASIONAL (tampil sebagai node kanvas Factory Flow)

1. `core/.../domain/rbac/BusinessModule.kt` — entri baru (`code`, `category`, `scopeCapability`, `iconKey`; `kind` default OPERATIONAL).
2. `core/.../domain/pipeline/OperationalModuleContract.kt` — cabang `ModuleArchetype.forModule` (dipaksa kompilator); slot baru → entri `ModuleArchetype` + `representativeModule`/`defaultStage`.
3. `core/.../domain/pipeline/OperationalModuleCatalog.kt` — objek spec + **masukkan ke `all` di posisi yang benar** (posisi = urutan kanvas & sisipan reconciler). Port = `upstreamPrerequisites` / `downstreamHandoffs`.
4. `core/.../domain/pack/` — daftarkan tipe port baru di **kosakata pack**: `DomainPack.portTypes`, dan `wiredPortTypes` bila port itu menyambung antarmodul (pack garment: `GarmentPortTypes`). Port yang dipakai spec katalog wajib ada di sana (dikunci `CatalogPortVocabularyTest`). *`PortDataTypeRegistry` sudah dihapus; port kini data pack.*
5. Migrasi Flyway pola **V27/V64**: backfill entitlement (kunci **NAME** enum), baris `module_catalog_entries` (kunci **code**), backfill `custom_roles` per peran sistem.
   **Tabel modul di schema bernama kode modulnya** (`CREATE SCHEMA <kode>`, `Table("<kode>.<nama>")`, grant + `ALTER DEFAULT PRIVILEGES` untuk `wemade_app`, RLS lewat `apply_tenant_rls_in('<kode>', '<tabel>')`). Daftarkan di `ModuleSchemaMap` (pola V76, B8).
6. `core/.../domain/rbac/CustomRole.kt` `createFactoryPresets` — akses per peran preset (Owner otomatis).
7. `AppNavScreen.kt` + cabang `App.kt` (dipaksa kompilator) + `ModuleWorkspaceScreen` (`sampleRowsFor` dipaksa kompilator) + `ModuleIcon`.
8. Server: route + `requireModuleAccess`/`moduleDecision`, didaftarkan di `ServerRouteWiring`.
9. **Otomatis**: seksi menu, matriks RBAC, dialog entitlement superadmin, kuota paket, reconciler pipeline tenant.

### 5.2 Modul GOVERNANCE (layar tata kelola; tidak pernah di kanvas)

1. Entri `BusinessModule` dengan `kind = GOVERNANCE`, `category = GOVERNANCE`.
2. Cabang `null` di `ModuleArchetype.forModule`; **jangan** masuk `OperationalModuleCatalog`.
3. `AppNavScreen` + cabang `App.kt` lewat `GovernanceModuleGate`; `sampleRowsFor`; ikon.
4. Migrasi pola **V18/V19**: entitlement, katalog `archetype_code = 'governance'` harga 0, backfill peran + anti-lockout Owner.
5. **Otomatis**: tidak dihitung kuota, tidak di kanvas.

### 5.3 Modul FOUNDATION (data induk/referensi; tidak di kanvas)

1. Entri `BusinessModule` dengan `kind = FOUNDATION`; cabang `null` di `forModule`.
2. `FoundationModuleCatalog` (sediakan `providedReferenceTypes`).
3. `AppNavScreen` + `App.kt` + `ModuleWorkspaceScreen`; migrasi pola **V27** (`archetype_code = 'foundation'`); preset peran; guard server.

### 5.4 Fitur di dalam modul (washing, storage, traceability, surat jalan, …)

Fitur **tidak** membuat `BusinessModule` baru (lihat `tenant-variability-rules.md` Kontrak 2). Ia
mewarisi RBAC, entitlement, dan katalog dari **modul induk**.

1. Core: paket domain + repository + migrasi tabel **di schema modul induk** (`<kode induk>.<tabel>`, daftarkan di `ModuleSchemaMap`); bila tahap → `IndustryStageTemplates`/`TenantStageFlow`, bila proses → `TenantProcessCatalog`, bila stasiun → `WorkStationCatalog`.
2. Server: route didaftarkan di `ServerRouteWiring` dan **wajib** memakai gate modul induk (`requireModuleAccess(modulInduk, …)`). Menulis: fail-closed.
3. Klien: di-host di workspace modul induk, atau `AppNavScreen` dengan `businessModule = modulInduk` — dan cabang `App.kt` **wajib** memeriksa `accessDecisions`, bukan hanya status login.
4. Kanvas: daftarkan di registry fitur modul (TRD-FLOW-002, Fase 4) agar tampil di level 2 di bawah node induk.

### 5.5 Kontrak Input/Output

- Port keluar modul A **harus sama** dengan port masuk modul B yang disambung; tipe port wajib terdaftar di kosakata pack (`DomainPack.portTypes`; yang menyambung juga di `wiredPortTypes`).
- Kanvas menyambung node **dari port**, bukan dari daftar tulis tangan (target TRD-FLOW-002). Port yang tidak menyambung = node yatim di kanvas.
- Kunci: entitlement, `custom_roles`, `department_module_assignments` memakai **NAME** enum (`QUALITY_CONTROL`); node pipeline & `module_catalog_entries` memakai **code** (`quality_control`). Jangan tertukar.

### 5.6 Kepemilikan & promosi modul (TRD-PLAT-004, diputuskan 2026-10-08)

- **Jalur kepemilikan**: mesin platform → pack bawaan → pack data bersama → khusus tenant. Ketergantungan hanya
  ke atas; kode mesin tidak boleh menyebut kode khusus tenant — dijaga `TenantCodeBoundaryTest` (memblokir); kode J3 hanya disebut dari paketnya dan registri `TenantPackContributions` (TRD-PLAT-004 Track B).
- **Promosi = salin, bukan ganti nama.** Modul khusus tenant yang ternyata berguna lintas tenant dipromosikan
  dengan **menyalin** ke pack baru (kode dan prefiks baru). Nama schema DB sama dengan kode modul, jadi ganti
  nama = migrasi tabel. Field `derivedFrom` dibuat saat promosi pertama terjadi, bukan sebelumnya.
- **Kode khusus tenant tetap di pohon sumber dan binary yang sama** (P3) selama pagar impor dan pagar migrasi
  hijau. Tinjau ulang bila ada tenant yang menuntut kode tertutup (kontrak, NDA).
- **Modul khusus tenant**: migrasinya tidak boleh mereferensikan schema modul lain, kecuali `public.tenants`,
  `public.users`, schema sendiri, dan schema yang dirujuk lewat `moduleReferences` (P4) — dijaga `J3MigrationFenceTest` (memblokir; TRD-PLAT-004 Track C).
  B8 (FK/JOIN lintas schema) tetap berlaku untuk modul garment.
- **Pack berpemilik** hanya bisa dipasang pada tenant pemiliknya (TRD-PLAT-005). Handoff identik oleh tenant lain
  melepasnya menjadi bersama dan tercatat audit.

---

# WeMade ERP — Aturan Variabilitas Tenant (Kode vs Data)

Status sejajar dengan [`module-integration-rules.md`](module-integration-rules.md),
[`design-system-rules.md`](design-system-rules.md), dan [`file-size-rules.md`](file-size-rules.md).
Aturan ini menjawab satu pertanyaan: **sebuah konsep boleh tinggal di kode (enum/`when`), atau wajib
menjadi data per tenant?**

Lahir dari TRD-FLOW-001: kerangka tahap sampling ditulis sebagai enum rajut, lalu harus dibongkar
lewat ±70 file dan 7 PR ketika tenant bordir/potong-jahit/sablon masuk. Semua itu bisa dihindari
dengan satu pertanyaan di hari pertama.

---

## Kontrak 1 — Uji Variabilitas sebelum `enum class` / `when`

Sebelum menulis `enum class` atau `when` untuk **konsep domain**, jawab:

1. Apakah nilainya bisa **berbeda antar tenant**?
2. Apakah bisa **berbeda antar industri** (rajut, potong-jahit, bordir, sablon)?
3. Apakah admin pabrik **mungkin ingin mengubahnya** (nama, urutan, menambah)?

Satu jawaban "ya" → konsep itu **data**: template bawaan + salinan per tenant.
Enum hanya untuk konsep yang dimiliki **sistem**: status teknis, jenis leg transfer, peran platform,
`StageKind`, `StageTrait`.

```kotlin
// ❌ Kerangka tahap sebagai enum — tenant bordir dipaksa lewat "Rajut Turun Mesin"
enum class SamplingPipelineStage { NEW_INTAKE, CAM_PROGRAMMING, MACHINE_KNITTING, … }

// ✅ Kerangka tahap sebagai data per tenant
data class TenantStageFlow(val tenantId: TenantId, val template: IndustryTemplateCode, val stages: List<StageDefinition>)
```

## Kontrak 2 — Tangga keputusan: taruh di anak tangga yang tepat

| Anak tangga | Artinya | Contoh | Tempat |
|---|---|---|---|
| Modul | Dijual, di-RBAC, dihitung kuota | QC, Fulfillment | `BusinessModule` + `OperationalModuleCatalog` |
| Tahap | Urutan kerja satu dokumen | Digitizing, Hooping | `TenantStageFlow` / `IndustryStageTemplates` |
| Proses opsional | Sisipan per desain | Bordir di SPK rajut | `TenantProcessCatalog` |
| Stasiun | Meja di lini produksi massal | Obras, Steam | `WorkStationCatalog` |
| Konfigurasi | Pilihan per tenant | Tag fase Cuci/Setrika | tabel JSONB per tenant (pola V71) |

Proses **bukan** modul (keputusan 2026-09-28): menjadikan setiap proses modul memecah kuota & RBAC.

## Kontrak 3 — Peran, bukan nama

Aturan domain mencari **peran** (`ModuleArchetype`, `StageTrait`), bukan kode khas satu industri.

```kotlin
// ❌ hanya benar untuk rajut
if (stageCode == LINKING_ASSEMBLY) …
// ✅ benar untuk semua template
if (stageCode == firstStageWith(ModuleArchetype.SEWING)?.code) …
```

Peran boleh **tidak ada** (sablon tidak punya `SEWING`). Kode yang mencari peran wajib aman bila
hasilnya `null` — itu bug nyata yang ditemukan di TRD-FLOW-001 Tahap 3a.

## Kontrak 4 — Kunci tersimpan = value object string, parser tunggal, tolak bukan fallback

Kolom DB dan key JSON memakai value object (`StageCode`), bukan `enum.name`. Satu parser, dan nilai
tak dikenal **ditolak** (atau dicocokkan ke kerangka dokumen), **tidak** jatuh diam-diam ke default.

> Fallback senyap = data berubah. Contoh nyata: SPK bordir dibaca ulang sebagai `NEW_INTAKE`.

## Kontrak 5 — Template disalin, dokumen membeku

- Template bawaan (`IndustryStageTemplates`) → **disalin** ke tenant saat pertama dibutuhkan.
- Dokumen (SPK) **membekukan** salinan saat mulai dikerjakan (`frozenStageFlow`, V73).
- Konsekuensinya wajib ditulis di KDoc: mengubah template bawaan **tidak** sampai ke tenant lama;
  mengubah kerangka tenant **tidak** mengubah dokumen yang sudah beku.

## Kontrak 6 — Tenant kedua wajib di test

Setiap fitur yang membaca konsep variabel punya test dengan **template non-default** (fixture
bordir/sablon), dan bila menyentuh UI, dicek mata di tenant uji non-rajut (`bordir-uji`).
Test yang hanya memakai data rajut tidak membuktikan apa pun tentang tenant lain.

## Kontrak 7 — Menulis wajib fail-closed

Endpoint mutasi menolak bila keputusan RBAC tidak bisa dihitung (`mayEditWithoutDecision` di
`TenantStageFlowRoutes.kt`). Test wajib mencakup **peran yang tidak berwenang** (harus 403), bukan
hanya pengguna yang berwenang.

## Kontrak 8 — Konsep yang terlanjur enum: Strangler Fig

1. Bangun struktur data baru di samping enum; template bawaan **identik** dengan enum.
2. Test paritas yang **mengiterasi enum** (entri baru tanpa padanan → test gagal).
3. Jembatan (`toStageCode()`, konstruktor sekunder), pindahkan pembaca satu paket per PR.
4. Kriteria selesai: pemindai "pembacaan jembatan yang bisa melempar" kosong di semua lapisan.
5. Baru setelah itu aktifkan variasi kedua (template lain).

---

## Checklist Definition of Done

- [ ] Setiap `enum class` domain baru lolos Uji Variabilitas (tulis alasannya di KDoc)
- [ ] Tidak ada `when (enumDomain)` baru di `presentation/**` untuk konsep variabel
- [ ] Aturan domain memakai peran/trait, aman bila peran tidak ada
- [ ] Kunci tersimpan berupa value object; parser tidak fallback senyap
- [ ] Template vs salinan vs beku dijelaskan di KDoc
- [ ] Ada test dengan template non-default; cek visual di tenant uji non-rajut
- [ ] Endpoint tulis fail-closed + test peran tidak berwenang
- [ ] `scripts/audit-variability.sh` tidak menambah temuan baru

## Pelajaran TRD-FLOW-001 (bacaan)

`docs/teaching/teaching-flow-001-*.md` (Tahap 1 s.d. 3c) dan
[`docs/trd/TRD-FLOW-001-industry-stage-templates.md`](../../docs/trd/TRD-FLOW-001-industry-stage-templates.md).
