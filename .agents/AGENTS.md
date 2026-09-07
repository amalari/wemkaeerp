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
EventVerse / AchmadPorto/
├── core/                        # PURE KOTLIN DOMAIN LAYER (Zero external dependencies)
│   └── src/commonMain/kotlin/com/eventverse/app/
│       ├── domain/player/       # Player Entities, Enums, Value Objects & UseCases
│       ├── domain/portfolio/    # Portfolio, Projects, Skills & UseCases
│       ├── domain/interaction/  # Dialogue, Interaction Entities & UseCases
│       └── domain/world/        # Farm Buildings, Props, Shipping & UseCases
│
├── app/
│   ├── shared/                  # Shared UI + application layer (Compose UI & Repositories)
│   ├── androidApp/              # Android entry point saja
│   ├── iosApp/                  # iOS entry point saja
│   ├── desktopApp/              # Desktop entry point saja
│   └── webApp/                  # Kotlin/JS Web bridge entry point (PortfolioJsRuntimeBridge.kt)
│
├── src/                         # TYPESCRIPT & THREE.JS 3D PRESENTATION LAYER (DDD / Vertical Slices)
│   ├── core/                    # Shared Kernel (engine, physics, audio, bridge, constants, styles)
│   ├── features/                # Bounded Contexts / Domain Feature Slices
│   │   ├── maps/                # Spatial Maps: outside/, farmhouse/ (inside), shared/
│   │   ├── player/              # 3D Farmer rig, CharacterController, Joystick, PlayerSfx
│   │   ├── portfolio/           # Rucksack Bag modal, project showcase data, MenuSfx, CSS
│   │   ├── calendar/            # Calendar modal, festival data, calendar.css
│   │   ├── television/          # CRT TV modal, broadcast data, tv.css
│   │   ├── diary/               # Save Diary modal, slot logic, diary.css
│   │   ├── dialogue/            # Dialogue window, Avatar portraits, DialogueSfx, CSS
│   │   └── hud/                 # Top status bar, real-time clock, toast notifications
│   ├── main.ts                  # Clean Composition Root & Scene Orchestrator
│   └── style.css                # Master CSS barrel importing all domain stylesheets
│
└── server/                      # Ktor server (REST/GraphQL)
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

### 8. File Organization (Domain & Vertical Slice Colocation)

- Satu file = satu konsep utama
- Boleh ada file gabungan untuk value objects kecil: `EventValueObjects.kt`
- Kelompokkan berdasarkan **fitur/domain/peta**, bukan berdasarkan layer teknis di level file (Dilarang memisahkan folder `css/`, `audio/`, `ui/`, `world/` secara horizontal)

#### A. Kotlin Domain & Feature Organization:
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

#### B. TypeScript / Three.js Frontend Feature Organization:
```
src/features/maps/farmhouse/
├── FarmhouseInterior.ts        # 3D Low-poly room diorama meshes & colliders
└── audio/
    └── FarmhouseSfx.ts         # Clock ticking, door creak, room synthesizers

src/features/portfolio/
├── domain/
│   ├── portfolio.ts            # Project showcase item data
│   └── profile.ts              # Farmer attributes & timeline data
├── presentation/
│   ├── RucksackMenu.ts         # 2D Rucksack Bag modal controller
│   └── rucksack.css            # Scoped rucksack stylesheet
└── audio/
    └── MenuSfx.ts              # Bag open/close & item equip synthesizers
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

### 11. Post-Task Teaching Documentation (Wajib)

Setiap kali menyelesaikan pengerjaan sebuah task, issue, atau modul:
- **Wajib men-generate modul dokumentasi pembelajaran (teaching)** menggunakan skill `teaching` ke dalam direktori `docs/teaching/teaching-[task/issue-id]-[slug].md`.
- Konten ditulis dengan gaya **Senior Lead Developer membimbing Junior Developer**:
  1. **Start dari mana?**: Urutan menulis (order of operations) dari nol.
  2. **Bedah kode blok per blok**: Penjelasan baris per baris dan mental model di baliknya.
  3. **Technology & Approach ("The Why")**: Mengapa teknologi ini yang dipilih dan risiko jika menggunakan cara lain.
  4. **Jebakan Pemula (Common Pitfalls)**: Kesalahan fatal yang dihindari.
  5. **Verifikasi & Tantangan Mandiri**: Cara menguji kebenaran kodenya.

---

## Anti-Patterns yang Dilarang

- **Horizontal Technical Layer Slicing di Frontend** — Mengumpulkan semua audio di `audio/`, semua CSS di `styles/`, semua modal di `ui/`, atau semua 3D di `world/`. Selalu gunakan Vertical Slices di `src/features/`!
- **Anemic Domain Model** — Entity hanya data tanpa behavior, logika tersebar di service
- **God UseCase / God Orchestrator** — Satu use case / satu file `main.ts` menangani seluruh operasi tanpa delegasi modul
- **Repository sebagai DAO generik** — Hindari findAll(), deleteById() tanpa konteks domain
- **Domain bergantung pada framework** — Tidak ada import Ktor/Android/Compose/Three.js di pure Kotlin domain
- **Business logic di ViewModel atau Composable**
- **String primitives untuk domain concepts** — Gunakan Value Objects
