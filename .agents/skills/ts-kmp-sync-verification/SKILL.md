---
name: ts-kmp-sync-verification
description: Verify and synchronize Kotlin Multiplatform (KMP) domain state, repositories, use cases, view models, and runtime bridges whenever TypeScript/Three.js frontend code is modified in the EventVerse / AchmadPorto hybrid architecture.
license: Apache-2.0
metadata:
  author: Antigravity DeepMind Team
  version: "2.0.0"
---

# TypeScript ↔ Kotlin Multiplatform (KMP) Sync Verification

Use this skill whenever TypeScript (`src/**`) code is modified, added, or deleted in the hybrid **Three.js + Kotlin Multiplatform** architecture.

This skill ensures that whenever frontend contracts, 3D world entities, player states, interactive items, or bridge interfaces change on the TypeScript side, the **Kotlin Multiplatform backend/domain layer (`core/`, `app/shared/`, `app/webApp/`)** is verified and updated to maintain 100% data consistency and prevent state drift.

---

## 🏛️ Hybrid Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                   TypeScript (Frontend — DDD Vertical Slices)          │
│  src/features/maps/*, src/features/player/*, src/features/portfolio/*  │
│  - 60 FPS Real-time Rendering (Three.js WebGL)                         │
│  - Spatial Collision & Pathfinding (src/core/physics)                  │
│  - User Input & Camera Rig (src/features/player)                       │
│  - Dedicated Modals, Audio Synthesizers & CSS per Feature Slice        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                         [ src/core/bridge/KmpBridge.ts ]
                         [ Global window.eventVerseKmp  ]
                                    │
┌───────────────────────────────────┴────────────────────────────────────┐
│                  Kotlin Multiplatform (Domain & State Engine)          │
│  app/webApp (jsMain) -> PortfolioJsRuntimeBridge.kt                    │
│  app/shared          -> PortfolioViewModel, Repositories               │
│  core                -> Entities, Value Objects, UseCases              │
│  - Single Authoritative Source of Truth for Portfolio & Game State     │
│  - Multiplatform Portable Business Logic                               │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Verification Triggers

Trigger this review checklist whenever changes occur in:

1. **`src/features/maps/**`** (`outside/`, `farmhouse/`, `shared/`):
   - Adding, renaming, moving, or removing landmarks, buildings, flora, or props (e.g. Farmhouse, Mailbox, Well, Shipping Bin, Boulders, Trees, Bed, Bookshelf).
   - Changing coordinates of interactive objects or zone boundaries in `constants.ts` or map files.
2. **`src/features/player/**`**:
   - Adding or modifying movement states (e.g. `IDLE`, `WALKING`, `RUNNING`, `EXHAUSTED`, `WATERING`, `HARVESTING`).
   - Changing player telemetry synchronization rate, transform schema, or stamina logic.
3. **`src/core/bridge/KmpBridge.ts`**:
   - Modifying interop functions, state interfaces, subscriber callbacks, or global `window.eventVerseKmp` method signatures.
4. **`src/features/portfolio/**`, `src/features/calendar/**`, `src/features/dialogue/**`**:
   - Adding new interactive dialogs, season selectors, inventory items, or project detail modals.

---

## 📋 Comprehensive 5-Dimension Sync Checklist

Whenever TypeScript files are modified, run through these 5 dimensions:

### 1. World Entities & Landmark Consistency
When a 3D world object is added, modified, or removed:

- [ ] **Check `core/src/commonMain/kotlin/com/eventverse/app/domain/world/WorldEntities.kt`**:
  - Does `BuildingType` or `PropType` enum need a new or removed entry?
  - Does `FarmBuilding` or `WorldProp` model match the properties used in TS?
- [ ] **Check `app/shared/src/commonMain/kotlin/com/eventverse/app/data/InMemoryFarmWorldRepository.kt`**:
  - Is the default world object list (`buildings`, `worldProps`) matching the 3D scene?
  - (e.g., if a prop is added or removed in TS `src/features/maps/outside/FarmEnvironment.ts` or `src/features/maps/farmhouse/FarmhouseInterior.ts`, update the Kotlin repository).
- [ ] **Check `core/src/commonMain/kotlin/com/eventverse/app/domain/interaction/usecase/InteractWithObjectUseCase.kt`**:
  - Does the interaction handler have matching dialogue responses, audio SFX keys, and action payloads?

### 2. Player & Movement State Consistency
When character movement, inputs, or animations change in `src/features/player/`:

- [ ] **Check `core/src/commonMain/kotlin/com/eventverse/app/domain/player/PlayerEntities.kt`**:
  - Is `MovementState` enum matching all animation states passed from TS (`IDLE`, `WALKING`, `RUNNING`, etc.)?
  - Does `Vector3D` or `PlayerCharacter` contain all necessary transform fields?
- [ ] **Check `app/shared/src/commonMain/kotlin/com/eventverse/app/data/InMemoryPlayerRepository.kt`**:
  - Does the in-memory player state store the correct initial coordinate and rotation?
- [ ] **Check `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/portfolio/PortfolioViewModel.kt`**:
  - Does `PortfolioUiEvent.UpdatePlayerMovement` correctly process the state?

### 3. Portfolio, Projects & Skill Crops Consistency
When project showcases, career data, or skill models change in `src/features/portfolio/domain/`:

- [ ] **Check `core/src/commonMain/kotlin/com/eventverse/app/domain/portfolio/PortfolioEntities.kt`**:
  - Ensure `FarmerProfile`, `ProjectShowcase`, `SkillCrop`, and `FarmSeason` models match.
- [ ] **Check `app/shared/src/commonMain/kotlin/com/eventverse/app/data/InMemoryPortfolioRepository.kt`**:
  - Ensure project IDs, skill IDs, tags, links, and descriptions match.

### 4. Bridge & Serialization Schema Parity
When interop methods in `src/core/bridge/KmpBridge.ts` change:

- [ ] **Check `app/webApp/src/jsMain/kotlin/com/eventverse/app/PortfolioJsRuntimeBridge.kt`**:
  - Are all exposed bridge methods present? (`updatePlayerTransform`, `interactWithWorldObject`, `selectCrop`, `visitBuilding`, `changeSeason`, `dismissDialogue`, `subscribe`).
  - Does `serializeState(state: PortfolioUiState): dynamic` serialize all fields expected by `KmpPortfolioState` in TypeScript?

### 5. Automated Build & Test Guardrails
Run both verification test suites:

```bash
# 1. Typecheck TypeScript & build Vite bundle
npx tsc --noEmit
npm run build

# 2. Run Kotlin Multiplatform unit & platform tests
./gradlew check
```
