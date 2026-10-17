# Teaching: Tutorial Source Registry per Pack (TRD-TUT-001 FR-3, FR-4, FR-5)

## Overview

This document explains the implementation of per-pack tutorial sources in WeMade ERP, enabling different packs (including tenant packs) to provide their own tutorials.

## What Was Implemented

### FR-3: TutorialSourceRegistry

**Problem**: Previously, `ShippedTutorialSource` was hardcoded to only support the `garment` pack. Adding tutorial support for new packs required modifying the core code.

**Solution**: Created a registry pattern:

```kotlin
// core/.../domain/pack/TutorialSourceRegistry.kt
interface PackTutorialSource {
    fun tutorialsFor(pack: DomainPack): List<ModuleTutorial>
    fun anchorsFor(pack: DomainPack): Set<TutorialAnchorId>
}

object TutorialSourceRegistry {
    private val sources = mutableMapOf<DomainPackCode, PackTutorialSource>()

    fun forPack(pack: DomainPack): PackTutorialSource = 
        sources[pack.code] ?: EmptyPackTutorialSource

    fun register(code: DomainPackCode, source: PackTutorialSource) {
        sources[code] = source
    }
}

object EmptyPackTutorialSource : PackTutorialSource {
    override fun tutorialsFor(pack: DomainPack) = emptyList()
    override fun anchorsFor(pack: DomainPack) = emptySet()
}
```

**Key Points**:
- Registry is data-driven, not code-driven (tenant-variability compliant)
- Unknown packs get `EmptyPackTutorialSource` (safe fallback)
- Both shipped (garment) and tenant packs can register their tutorials

### FR-4: Layanan Pack Tutorial

**Problem**: Tenant packs (J3) need to provide their own tutorials.

**Solution**: Added `tutorialSource` field to `TenantPackContributions`:

```kotlin
// server/.../tenant/TenantPackContributions.kt
class Contribution(
    // ... other fields ...
    val tutorialSource: PackTutorialSource? = null
)
```

Created `LayananTutorials.kt` for the layanan pack:

```kotlin
// core/.../domain/pack/tenant/layanan/LayananTutorials.kt
object LayananTutorials {
    val CHANGE_REQUEST_LIST = TutorialAnchorId("layanan_change_request.list")
    val CHANGE_REQUEST_ADD = TutorialAnchorId("layanan_change_request.add")

    val all: List<ModuleTutorial> = listOf(
        ModuleTutorial(
            id = TutorialId("layanan_change_request_basics"),
            scope = TutorialScope.Module(LayananPilotPack.CHANGE_REQUEST),
            title = "Mengelola permintaan perubahan",
            // ... steps with anchors ...
        )
    )
}

object LayananTutorialSource : PackTutorialSource {
    override fun tutorialsFor(pack: DomainPack) = 
        if (pack.code == LayananPilotPack.CODE) LayananTutorials.all else emptyList()

    override fun anchorsFor(pack: DomainPack) = 
        if (pack.code == LayananPilotPack.CODE) LayananTutorials.anchors else emptySet()

    init {
        // Register to registry on class load
        TutorialSourceRegistry.register(LayananPilotPack.CODE, this)
    }
}
```

### FR-5: Per-Pack Exempt Testing

**Problem**: Need to ensure every pack with a registered source meets coverage requirements.

**Solution**: Extended `TutorialCoverageTest` with per-pack exempt maps:

```kotlin
// core/.../domain/tutorial/TutorialCoverageTest.kt
/** Pack yang punya PackTutorialSource terdaftar. */
private val packsWithSource: Map<DomainPackCode, Map<ModuleId, String>> = mapOf(
    GarmentDomainPack.CODE to garmentExempt,
)

/** Pack yang tidak punya PackTutorialSource — kontrak tidak berlaku untuk ini. */
private val packsWithoutSource: Set<DomainPackCode> = setOf(
    DomainPackCode("elearning"),
)

@Test
fun layananPack_hasTutorialForItsModule() {
    // Trigger init of LayananTutorialSource
    LayananTutorialSource.hashCode()
    
    val layananPack = LayananPilotPack.pack
    val layananTutorials = TutorialCatalog().forPack(layananPack)
    val layananExempt: Map<ModuleId, String> = emptyMap()

    // Verify no violations
    val problems = TutorialCoverage.violations(layananPack, layananTutorials, layananExempt)
    assertTrue(problems.isEmpty())
}
```

## Files Changed

| File | Change |
|------|--------|
| `core/.../domain/pack/TutorialSourceRegistry.kt` | **NEW**: Registry + interfaces |
| `core/.../domain/pack/ShippedTutorialSource.kt` | **MODIFIED**: Register to registry |
| `core/.../domain/tutorial/TutorialCatalog.kt` | **MODIFIED**: Use registry |
| `core/.../domain/pack/tenant/layanan/LayananTutorials.kt` | **NEW**: Layanan pack tutorials |
| `server/.../tenant/TenantPackContributions.kt` | **MODIFIED**: Add tutorialSource field |
| `core/.../domain/tutorial/TutorialCoverageTest.kt` | **MODIFIED**: Per-pack exempt tests |
| `core/.../domain/tutorial/TutorialSourceRegistryTest.kt` | **NEW**: Registry tests |

## Testing

Run the tests:

```bash
./gradlew :core:jvmTest --tests "*Tutorial*" --no-configuration-cache
```

Results: **31 tests passed**

Key tests:
- `TutorialSourceRegistryTest` - Verifies registry works for garment, layanan, and unknown packs
- `TutorialCoverageTest` - Verifies per-pack exempt maps work correctly

## Key Takeaways

1. **Registry Pattern**: Use registries instead of hardcoded `when` statements for extensible features
2. **Data-Driven**: Pack-specific data goes in registries, not in domain code
3. **Safe Fallback**: Always provide fallback for unknown cases (`EmptyPackTutorialSource`)
4. **Testing**: Each pack with a source must be tested with its own exempt map
5. **Tenant Isolation**: Tenant pack tutorials are only visible to owning tenants (through pack resolution)

## Common Pitfalls Avoided

- ❌ NOT using `when(packCode)` in domain code (violates tenant-variability)
- ❌ NOT silent fallback that changes stored data
- ❌ NOT adding new literal colors outside WeMadeTheme.kt
- ✅ File size kept within limits (soft 250 / hard 400 for core)
