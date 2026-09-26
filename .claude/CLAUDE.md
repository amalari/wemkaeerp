# EventVerse — Project Rules (Domain-Driven Design)

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
