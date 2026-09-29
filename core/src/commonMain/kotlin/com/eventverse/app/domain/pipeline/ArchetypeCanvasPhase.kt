package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.SlotCode

/**
 * Kolom kanvas tempat modul ber-archetype ini digambar — dibaca dari Domain Pack (B1), bukan dari
 * enum. Tetap berbasis `ModuleArchetype` sampai B3 menggantinya dengan `SlotCode`.
 */
val ModuleArchetype.canvasPhase: PhaseDefinition
    get() = DomainPackRegistry.soleActivePack.phaseOfSlot(SlotCode(code))
