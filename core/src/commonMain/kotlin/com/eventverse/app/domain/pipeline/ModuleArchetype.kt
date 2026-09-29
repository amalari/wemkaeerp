package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentSlots
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.rbac.BusinessModule

/**
 * Slot kemampuan modul (Jalur B, B3). Dulu `enum class` konveksi; kini [SlotCode] dari Domain Pack,
 * sehingga pack lain (e-learning) punya slotnya sendiri. Nama `ModuleArchetype` dipertahankan
 * **sementara** agar tipe di ±60 file tetap terkompilasi; diganti menjadi `SlotCode` langsung di B3b.
 */
typealias ModuleArchetype = SlotCode

private val SlotCode.definition: SlotDefinition
    get() = requireNotNull(DomainPackRegistry.soleActivePack.slot(this)) {
        "Slot $value tidak ada di pack ${DomainPackRegistry.soleActivePack.code.value}"
    }

/** Kode tersimpan (`sewing`) — dulu `ModuleArchetype.code`. */
val SlotCode.code: String get() = value

val SlotCode.displayName: String get() = definition.displayName

val SlotCode.defaultExpectedInputType: String get() = definition.defaultInput.value

val SlotCode.defaultProducedOutputType: String get() = definition.defaultOutput.value

/** Kolom kanvas tempat modul ber-slot ini digambar (B1). */
val SlotCode.canvasPhase: PhaseDefinition
    get() = DomainPackRegistry.soleActivePack.phaseOfSlot(this)

/** Modul bawaan pewakil slot (ikon & cakupan akses node plugin kustom). */
val SlotCode.representativeModule: BusinessModule get() = GarmentSlots.representativeModule(this)
