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

## Anti-Patterns yang Dilarang

- Anemic Domain Model — Entity hanya data, logika di service
- God UseCase — satu use case menangani banyak operasi
- Repository sebagai DAO generik — hindari findAll(), deleteById() tanpa konteks domain
- Domain bergantung pada framework — tidak ada Ktor/Android/Compose import di domain
- Business logic di ViewModel atau Composable
- String primitives untuk domain concepts — gunakan Value Objects
