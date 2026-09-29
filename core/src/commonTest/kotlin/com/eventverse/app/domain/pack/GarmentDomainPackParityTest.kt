package com.eventverse.app.domain.pack

import com.eventverse.app.domain.contracts.PortDataTypeRegistry
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.pipeline.canvasPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Paritas pack garment. Sejak B1 enum `PipelineStage` sudah dihapus; nilai-nilainya dibekukan di
 * [LEGACY_PIPELINE_STAGES] dan [LEGACY_DEFAULT_STAGE] — **salinan persis** enum & `defaultStage` terakhir
 * (commit ec74e26). Mengubah fase konveksi berarti mengubah tabel ini dengan sengaja, bukan diam-diam.
 */
class GarmentDomainPackParityTest {

    private val pack = GarmentDomainPack.pack

    private data class LegacyStage(val name: String, val order: Int, val displayName: String, val subtitle: String, val colorHex: Long)

    private val LEGACY_PIPELINE_STAGES = listOf(
        LegacyStage("COMMERCIAL", 1, "1. Komersial & Sampling", "Negosiasi Order & Prototipe Sample", 0xFF2563EB),
        LegacyStage("ENGINEERING", 2, "2. Spesifikasi & HPP", "Tech Pack, BOM & Kalkulasi Biaya", 0xFF7C3AED),
        LegacyStage("SUPPLY_CHAIN", 3, "3. Rantai Pasok & Bahan Baku", "Penerimaan Kain Rol & Aksesoris", 0xFF0D9488),
        LegacyStage("MANUFACTURING", 4, "4. Lantai Produksi", "Jadwal Mesin, Potong & Jahit", 0xFFEA580C),
        LegacyStage("ASSURANCE_DELIVERY", 5, "5. Mutu & Pengiriman", "Inspeksi QC, Packing & Surat Jalan", 0xFF16A34A)
    )

    private val LEGACY_DEFAULT_STAGE = mapOf(
        ModuleArchetype.ORDER_INGESTION to "COMMERCIAL",
        ModuleArchetype.PRODUCT_ENGINEERING to "ENGINEERING",
        ModuleArchetype.COSTING_HPP to "ENGINEERING",
        ModuleArchetype.RAW_MATERIAL to "SUPPLY_CHAIN",
        ModuleArchetype.CUTTING to "MANUFACTURING",
        ModuleArchetype.SEWING to "MANUFACTURING",
        ModuleArchetype.FINISHING to "MANUFACTURING",
        ModuleArchetype.CUSTOM_EXTENSION to "MANUFACTURING",
        ModuleArchetype.QUALITY_CONTROL to "ASSURANCE_DELIVERY",
        ModuleArchetype.FULFILLMENT to "ASSURANCE_DELIVERY"
    )

    @Test
    fun phases_equalLegacyPipelineStageExactly() {
        assertEquals(
            LEGACY_PIPELINE_STAGES,
            pack.orderedPhases.map { LegacyStage(it.code.value, it.order, it.displayName, it.subtitle, it.colorHex) }
        )
    }

    @Test
    fun everyArchetype_isDrawnInItsLegacyStage() {
        // Iterasi enum ModuleArchetype: slot baru tanpa fase = merah.
        assertEquals(ModuleArchetype.entries.toSet(), LEGACY_DEFAULT_STAGE.keys, "archetype baru: tentukan fasenya di GarmentPhases & tabel ini")
        ModuleArchetype.entries.forEach { a ->
            assertEquals(LEGACY_DEFAULT_STAGE.getValue(a), a.canvasPhase.code.value, "fase kanvas ${a.code}")
        }
    }

    @Test
    fun everyModuleArchetype_hasIdenticalSlot() {
        assertEquals(ModuleArchetype.entries.size, pack.slots.size)
        ModuleArchetype.entries.forEach { a ->
            val slot = assertNotNull(pack.slot(SlotCode(a.code)), "Slot ${a.code} tidak ada di pack")
            assertEquals(a.displayName, slot.displayName)
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
        assertSame(pack, DomainPackRegistry.soleActivePack)
        assertEquals(null, DomainPackRegistry.find(DomainPackCode("elearning")), "belum didaftarkan — tidak boleh fallback ke garment")
    }
}
