---
name: ddd-kotlin-multiplatform
description: >
  Apply Domain-Driven Design (DDD) patterns to the EventVerse Kotlin Multiplatform project.
  Activate this skill when creating new features, modules, domains, use cases, repositories,
  entities, value objects, domain events, or when refactoring existing code to follow DDD.
  Also trigger for questions about architecture, module dependencies, layer separation,
  or when the user asks to scaffold a new domain feature.
---

# DDD Kotlin Multiplatform Skill — EventVerse

## Overview

This skill guides implementation of Domain-Driven Design patterns in the EventVerse KMP project.
The project targets Android, iOS, Web (WasmJS), Desktop (JVM), and Server (Ktor).

---

## When This Skill Activates

Trigger this skill when the user:
- Asks to create a new **feature**, **domain**, **entity**, or **use case**
- Mentions words like: `domain`, `repository`, `use case`, `entity`, `value object`, `aggregate`, `DDD`
- Wants to **refactor** or restructure code following clean architecture
- Asks how to organize code by feature/domain
- Wants to scaffold a new **bounded context**

---

## Scaffolding a New Domain Feature

When the user asks to create a new feature (e.g., "create the Event feature"), follow this scaffold:

### Step 1 — Domain Layer (in `core/src/commonMain/kotlin/com/eventverse/app/`)

Create the following files:

**`feature/{name}/domain/{Name}.kt`** — Entity
```kotlin
package com.eventverse.app.feature.{name}.domain

import com.eventverse.app.feature.{name}.domain.{Name}Id
import com.eventverse.app.feature.{name}.domain.{Name}Status

data class {Name}(
    val id: {Name}Id,
    // ... other value objects
    val status: {Name}Status,
) {
    // Domain behavior methods here
}
```

**`feature/{name}/domain/{Name}ValueObjects.kt`** — Value Objects
```kotlin
package com.eventverse.app.feature.{name}.domain

import kotlin.jvm.JvmInline

@JvmInline
value class {Name}Id(val value: String)

// Add other value objects specific to this feature
```

**`feature/{name}/domain/{Name}Repository.kt`** — Repository Interface
```kotlin
package com.eventverse.app.feature.{name}.domain

interface {Name}Repository {
    suspend fun findById(id: {Name}Id): {Name}?
    suspend fun save(entity: {Name})
}
```

**`feature/{name}/domain/{Name}DomainEvents.kt`** — Domain Events
```kotlin
package com.eventverse.app.feature.{name}.domain

import kotlinx.datetime.Instant

// Use sealed interface, not sealed class
sealed interface {Name}DomainEvent

data class {Name}Created(
    val id: {Name}Id,
    val occurredAt: Instant,
) : {Name}DomainEvent
```

### Step 2 — Application Layer (in `app/shared/src/commonMain/`)

**`feature/{name}/application/{Verb}{Name}UseCase.kt`** — Use Case
```kotlin
package com.eventverse.app.feature.{name}.application

class {Verb}{Name}UseCase(
    private val repository: {Name}Repository,
) {
    suspend operator fun invoke(command: {Verb}{Name}Command): Result<Unit> = runCatching {
        // 1. Load aggregate
        // 2. Execute domain logic
        // 3. Save & publish events
    }
}

data class {Verb}{Name}Command(
    // command fields
)
```

### Step 3 — Presentation Layer (in `app/shared/src/commonMain/`)

**`feature/{name}/presentation/{Name}ListViewModel.kt`**
```kotlin
package com.eventverse.app.feature.{name}.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class {Name}ListViewModel(
    private val getUseCase: Get{Name}ListUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow({Name}ListUiState())
    val uiState: StateFlow<{Name}ListUiState> = _uiState.asStateFlow()

    fun onEvent(event: {Name}ListUiEvent) {
        when (event) {
            is {Name}ListUiEvent.Load -> load()
        }
    }

    private fun load() { /* ... */ }
}
```

**`feature/{name}/presentation/{Name}ListUiModel.kt`**
```kotlin
package com.eventverse.app.feature.{name}.presentation

data class {Name}ListUiState(
    val items: List<{Name}UiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface {Name}ListUiEvent {
    data object Load : {Name}ListUiEvent
}

data class {Name}UiModel(
    val id: String,
    // UI-friendly fields (no domain types)
)
```

---

## Dependency Rules Checklist

Before writing any code, verify:
- [ ] Domain types in `core/` have zero imports from `app/`, server, or framework libraries
- [ ] Use Cases only import from `domain/` interfaces
- [ ] ViewModel only imports from `application/` use cases and UI models
- [ ] Infrastructure implementations import from `domain/` interfaces only (no leaking domain logic)

---

## Kotlin KMP-Specific Rules

1. **Date/Time**: Always use `kotlinx.datetime.Instant` and `kotlinx.datetime.Clock`
2. **Coroutines**: All async operations use `suspend fun` with `kotlinx.coroutines`
3. **Platform expect/actual**: Place `expect` declarations in `commonMain`, implementations in platform-specific source sets
4. **No java.* imports in commonMain** except `@JvmInline` for value classes

---

## Common Patterns

### Result Handling in Use Cases
```kotlin
suspend operator fun invoke(command: Command): Result<Output> = runCatching {
    // throws are caught automatically
    val entity = repository.findById(command.id) ?: error("Not found: ${command.id}")
    // ... logic
    Output(...)
}
```

### Mapping Domain → UiModel
```kotlin
fun Event.toUiModel(): EventUiModel = EventUiModel(
    id = id.value,
    title = title.value,
    statusLabel = status.displayName(),
)
```

### Expect/Actual for Platform Differences
```kotlin
// commonMain
expect fun generateId(): String

// androidMain / jvmMain
actual fun generateId(): String = java.util.UUID.randomUUID().toString()

// wasmJsMain / jsMain
actual fun generateId(): String = js("crypto.randomUUID()") as String
```

---

## Anti-Pattern Examples to Avoid

```kotlin
// ❌ Anemic domain model
data class Event(var title: String, var status: String)
class EventService { fun publish(event: Event) { event.status = "published" } }

// ✅ Rich domain model
data class Event(val title: EventTitle, val status: EventStatus) {
    fun publish(): Event = copy(status = EventStatus.PUBLISHED)
}
```

```kotlin
// ❌ String primitive obsession
data class CreateEventCommand(val eventId: String, val creatorId: String)

// ✅ Typed with Value Objects
data class CreateEventCommand(val eventId: EventId, val creatorId: UserId)
```
