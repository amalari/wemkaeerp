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
