package com.eventverse.app.domain.pack

import com.eventverse.app.domain.contracts.PortDataTypeRegistry
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.pipeline.PipelineStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Paritas Strangler Fig B0: pack garment identik dengan enum lama. Test ini **mengiterasi enum**,
 * jadi entri enum baru tanpa padanan di pack langsung merah.
 */
class GarmentDomainPackParityTest {

    private val pack = GarmentDomainPack.pack

    @Test
    fun everyPipelineStage_hasIdenticalPhase() {
        assertEquals(PipelineStage.entries.size, pack.phases.size)
        PipelineStage.entries.forEach { s ->
            val phase = assertNotNull(pack.phase(PhaseCode(s.name)), "Fase ${s.name} tidak ada di pack")
            assertEquals(s.stepOrder, phase.order)
            assertEquals(s.displayName, phase.displayName)
            assertEquals(s.subtitle, phase.subtitle)
            assertEquals(s.colorHex, phase.colorHex)
        }
        assertEquals(PipelineStage.entries.sortedBy { it.stepOrder }.map { it.name }, pack.orderedPhases.map { it.code.value })
    }

    @Test
    fun everyModuleArchetype_hasIdenticalSlot_inItsDefaultStage() {
        assertEquals(ModuleArchetype.entries.size, pack.slots.size)
        ModuleArchetype.entries.forEach { a ->
            val slot = assertNotNull(pack.slot(SlotCode(a.code)), "Slot ${a.code} tidak ada di pack")
            assertEquals(a.displayName, slot.displayName)
            assertEquals(PhaseCode(a.defaultStage.name), slot.phase)
            assertEquals(a.defaultExpectedInputType, slot.defaultInput.value)
            assertEquals(a.defaultProducedOutputType, slot.defaultOutput.value)
        }
    }

    @Test
    fun wiredPortTypes_equalPortRegistry_andVocabularyAddsOnlyArchetypeDefaults() {
        assertEquals(PortDataTypeRegistry.KNOWN_TYPED_LABELS, pack.wiredPortTypes.map { it.value }.toSet())
        val defaults = ModuleArchetype.entries.flatMap { listOf(it.defaultExpectedInputType, it.defaultProducedOutputType) }
        assertEquals(PortDataTypeRegistry.KNOWN_TYPED_LABELS + defaults, pack.portTypes.map { it.value }.toSet())
    }

    @Test
    fun registry_findsGarmentByCode_andUnknownIsNull() {
        assertSame(pack, DomainPackRegistry.find(DomainPackCode("garment")))
        assertEquals(null, DomainPackRegistry.find(DomainPackCode("elearning")), "belum didaftarkan — tidak boleh fallback ke garment")
    }
}
