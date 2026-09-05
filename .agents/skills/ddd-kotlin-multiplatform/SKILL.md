---
name: ddd-kotlin-multiplatform
description: >
  Apply Domain-Driven Design (DDD) and Vertical Slice modular architecture patterns to the EventVerse / AchmadPorto
  hybrid Kotlin Multiplatform & TypeScript/Three.js project. Activate this skill when creating new features, modules,
  domains, maps, use cases, repositories, entities, 3D world components, UI modals, audio synthesizers, or when refactoring.
---

# DDD & Vertical Slice Modular Architecture Skill — EventVerse / AchmadPorto

## Overview

This skill guides the implementation of **Domain-Driven Design (DDD) and Vertical Slice Architecture** across both the **Kotlin Multiplatform (KMP)** backend/authoritative engine and the **TypeScript / Three.js** 3D interactive presentation layer.

---

## When This Skill Activates

Trigger this skill when the user:
- Asks to create a new **feature**, **domain**, **map chunk**, **entity**, **modal**, or **use case**
- Mentions words like: `domain`, `repository`, `use case`, `entity`, `value object`, `aggregate`, `DDD`, `modularization`, `maps`, `feature slice`
- Wants to **refactor** or restructure code following clean architecture / vertical slices
- Asks how to organize code by feature/domain (rather than by technical layers like `css/`, `audio/`, `ui/`, `world/`)
- Wants to scaffold a new **bounded context** in Kotlin or TypeScript

---

## 🏛️ Architecture Philosophy: Vertical Slices over Horizontal Layers

### ❌ Anti-Pattern: Horizontal Slicing (Layer-Based)
```
src/
├── audio/            # All synthesizers mixed together
├── styles/           # All CSS files mixed together
├── ui/               # All modals mixed together
├── world/            # All 3D meshes mixed together
└── data/             # All static data mixed together
```
*Why this fails:* Modifying 1 feature requires touching 5+ distant directories, leading to high coupling, difficult code-splitting, and spaghetti `main.ts` orchestrators.

### ✅ Best Practice: Vertical Slice Architecture (DDD Bounded Contexts)
```
src/
├── core/             # Shared Kernel & Infrastructure (Engine, Physics, AudioContext, Bridge, Constants, Global CSS)
└── features/         # Bounded Contexts / Domain Slices
    ├── maps/         # Spatial Map Domains (outside, farmhouse interior, shared props/transitions)
    ├── player/       # Character 3D rig, Controller, Joystick, PlayerSfx
    ├── portfolio/    # Rucksack Modal, project showcase data, MenuSfx, rucksack.css
    ├── calendar/     # Calendar Modal, festival dates data, calendar.css
    ├── television/   # Retro CRT TV Modal, broadcast channels, tv.css
    ├── diary/        # Save Diary Modal, slot logic, diary.css
    ├── dialogue/     # Retro Dialogue window, Avatar portraits, DialogueSfx, dialogue.css
    └── hud/          # Top status bar, real-time clock, bag button, toast notifications
```
*Why this wins:* High cohesion, complete colocation, seamless lazy-loading, and 1-to-1 parity with Kotlin domain contexts.

---

## 🗺️ TypeScript / Three.js DDD Feature Scaffolding

When creating or extending a feature in `src/features/`, encapsulate all related parts inside that feature's directory:

### 1. Scaffolding an Interactive Modal Feature (e.g. `features/shop/`)
```
src/features/shop/
├── domain/
│   └── shopItems.ts            # Domain models, prices, catalog data
├── presentation/
│   ├── ShopModal.ts            # UI modal controller & DOM lifecycle
│   └── shop.css                # Scoped retro stylesheet
└── audio/
    └── ShopSfx.ts              # Cash register, item purchase synthesizers (imports AudioContextManager)
```

### 2. Scaffolding a 3D Spatial Map Chunk (e.g. `features/maps/barn/`)
```
src/features/maps/barn/
├── BarnInterior.ts             # 3D Low-poly room meshes, lighting, colliders (implements ColliderProvider)
├── BarnExterior.ts             # 3D Exterior building model
├── audio/
│   └── BarnSfx.ts              # Animal sounds, barn door creak synthesizers
└── index.ts                    # Dynamic lazy loader export
```

### 3. Shared Kernel (`src/core/`)
Only generic, cross-cutting engine infrastructure belongs in `src/core/`:
- `core/engine/` -> `Engine.ts`, `TimeManager.ts`, `SaveStateManager.ts`
- `core/physics/` -> `CollisionSystem.ts`, `Pathfinder.ts`
- `core/audio/` -> `AudioContextManager.ts`, `SoundEngine.ts`, `sfx.ts`
- `core/bridge/` -> `KmpBridge.ts` (StateFlow interop)
- `core/constants/` -> `constants.ts` (World scales, boundaries, interaction radii)
- `core/styles/` -> `variables.css`, `base.css`, `responsive.css`

---

## 🌾 Kotlin Multiplatform (KMP) DDD Scaffolding

### Step 1 — Domain Layer (`core/src/commonMain/kotlin/com/eventverse/app/domain/{name}/`)

**Entity:**
```kotlin
package com.eventverse.app.domain.{name}

data class {Name}(
    val id: {Name}Id,
    val title: {Name}Title,
    val status: {Name}Status,
) {
    fun update(): {Name} = copy(...)
}
```

**Value Objects:**
```kotlin
package com.eventverse.app.domain.{name}

import kotlin.jvm.JvmInline

@JvmInline
value class {Name}Id(val value: String) {
    init { require(value.isNotBlank()) { "Id cannot be blank" } }
}
```

**Repository Interface:**
```kotlin
package com.eventverse.app.domain.{name}

interface {Name}Repository {
    suspend fun findById(id: {Name}Id): {Name}?
    suspend fun save(entity: {Name})
}
```

### Step 2 — Application Layer (`app/shared/src/commonMain/kotlin/com/eventverse/app/`)

**Use Case:**
```kotlin
package com.eventverse.app.domain.{name}.usecase

class {Verb}{Name}UseCase(
    private val repository: {Name}Repository,
) {
    suspend operator fun invoke(command: {Verb}{Name}Command): Result<Unit> = runCatching {
        val entity = repository.findById(command.id) ?: error("Not found")
        repository.save(entity)
    }
}
```

### Step 3 — Presentation Layer (`app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/`)

**ViewModel & MVI:**
```kotlin
class {Name}ViewModel(
    private val getUseCase: Get{Name}UseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow({Name}UiState())
    val uiState: StateFlow<{Name}UiState> = _uiState.asStateFlow()
}
```

---

## 📋 Full-Stack DDD Checklist

Before committing changes:
- [ ] TypeScript files are grouped by **Feature / Map / Domain**, NOT by technical layer (`css`, `audio`, `ui`).
- [ ] 3D scenes, UI modals, audio synthesizers, domain data, and CSS for a feature live together in `src/features/{featureName}/`.
- [ ] CSS files are imported via `src/style.css` following the feature hierarchy.
- [ ] `src/main.ts` remains a lean **Composition Root** (~400 lines max) delegating to feature controllers.
- [ ] Domain types in `core/` (Kotlin) have zero imports from `app/` or framework libraries.
- [ ] TypeScript contracts in `src/core/bridge/KmpBridge.ts` match Kotlin bridge in `app/webApp/src/jsMain/kotlin/.../PortfolioJsRuntimeBridge.kt`.
- [ ] `npx tsc --noEmit` and `npm run build` pass without errors.
- [ ] `./gradlew check` passes with all tests green.
