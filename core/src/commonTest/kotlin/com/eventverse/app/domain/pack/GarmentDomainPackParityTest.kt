package com.eventverse.app.domain.pack

import com.eventverse.app.domain.pipeline.defaultProducedOutputType

import com.eventverse.app.domain.pipeline.defaultExpectedInputType

import com.eventverse.app.domain.pipeline.displayName

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.pipeline.canvasPhase
import com.eventverse.app.domain.pipeline.defaultExpectedInputType
import com.eventverse.app.domain.pipeline.defaultProducedOutputType
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
        GarmentSlots.ORDER_INGESTION to "COMMERCIAL",
        GarmentSlots.PRODUCT_ENGINEERING to "ENGINEERING",
        GarmentSlots.COSTING_HPP to "ENGINEERING",
        GarmentSlots.RAW_MATERIAL to "SUPPLY_CHAIN",
        GarmentSlots.CUTTING to "MANUFACTURING",
        GarmentSlots.SEWING to "MANUFACTURING",
        GarmentSlots.FINISHING to "MANUFACTURING",
        GarmentSlots.CUSTOM_EXTENSION to "MANUFACTURING",
        GarmentSlots.QUALITY_CONTROL to "ASSURANCE_DELIVERY",
        GarmentSlots.FULFILLMENT to "ASSURANCE_DELIVERY"
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
        // Iterasi GarmentSlots.all: slot baru tanpa fase = merah.
        assertEquals(GarmentSlots.all.toSet(), LEGACY_DEFAULT_STAGE.keys, "archetype baru: tentukan fasenya di GarmentPhases & tabel ini")
        GarmentSlots.all.forEach { a ->
            assertEquals(LEGACY_DEFAULT_STAGE.getValue(a), a.canvasPhase.code.value, "fase kanvas ${a.code}")
        }
    }

    private data class LegacySlot(val code: String, val displayName: String, val input: String, val output: String, val representative: BusinessModule)

    /** Salinan persis `enum class ModuleArchetype` terakhir (commit 171038e), urutan deklarasi dipertahankan. */
    private val LEGACY_ARCHETYPES = listOf(
        LegacySlot("order_ingestion", "Penerimaan Pesanan / PO / Sales Ingestion", "CommercialInquiry", "ProductionOrderDraft", BusinessModule.CRM_SALES),
        LegacySlot("raw_material", "Bahan Baku & Persediaan Gudang", "MaterialRequisition", "VerifiedMaterialStock", BusinessModule.INVENTORY),
        LegacySlot("product_engineering", "Rekayasa Produk: Tech Pack, BOM & Yield", "ApprovedSampleSpecification", "TechPackAndYieldData", BusinessModule.TECH_PACK_BOM),
        LegacySlot("costing_hpp", "Perhitungan Biaya & HPP (Costing Engine)", "TechPackAndYieldData", "CostingCalculationResult", BusinessModule.COSTING_HPP),
        LegacySlot("cutting", "Pemotongan Pola Kain (Spreading & Cutting)", "CuttingOrderWithFabric", "CutPiecesBundle", BusinessModule.PRODUCTION_MRP),
        LegacySlot("sewing", "Penjahitan & Perakitan (Sewing Line)", "CutPiecesBundle", "AssembledGarmentBundle", BusinessModule.OPERATOR_EXEC),
        LegacySlot("finishing", "Finishing, Cuci, Setrika & Trimming", "AssembledGarmentBundle", "FinishedGarmentUnit", BusinessModule.OPERATOR_EXEC),
        LegacySlot("quality_control", "Pengawasan Mutu, Grading & Inspeksi", "FinishedGarmentUnit", "InspectedAndGradedUnit", BusinessModule.QUALITY_CONTROL),
        LegacySlot("fulfillment", "Pengemasan, Surat Jalan & Ekspedisi", "InspectedAndGradedUnit", "DispatchedShipmentManifest", BusinessModule.FULFILLMENT),
        LegacySlot("custom_extension", "Modul Khusus Tambahan (Custom Plugin / Extension)", "AnyOperationalPayload", "AnyOperationalPayload", BusinessModule.PRODUCTION_MRP)
    )

    @Test
    fun slots_equalLegacyArchetypeEnumExactly_inDeclarationOrder() {
        assertEquals(LEGACY_ARCHETYPES.map { it.code }, GarmentSlots.all.map { it.value })
        assertEquals(
            LEGACY_ARCHETYPES,
            GarmentSlots.all.map { s ->
                val def = assertNotNull(pack.slot(s))
                LegacySlot(s.value, def.displayName, def.defaultInput.value, def.defaultOutput.value, GarmentSlots.representativeModule(s))
            }
        )
    }

    @Test
    fun legacyCodeLookup_isCaseInsensitive_andLegacyNameRoundTrips() {
        assertEquals(GarmentSlots.SEWING, GarmentSlots.fromCode("SEWING"))
        assertEquals(null, GarmentSlots.fromCode("bordir"))
        GarmentSlots.all.forEach { assertEquals(it, GarmentSlots.fromLegacyName(GarmentSlots.legacyNameOf(it))) }
        assertEquals("QUALITY_CONTROL", GarmentSlots.legacyNameOf(GarmentSlots.QUALITY_CONTROL))
    }

    /** Salinan persis `PortDataTypeRegistry.KNOWN_TYPED_LABELS` terakhir (commit 1982a3b). */
    private val LEGACY_WIRED_PORTS = setOf(
        "ProductionOrderDraft", "ApprovedSampleSpecification", "TechPackAndYieldData", "MaterialRequisition",
        "VerifiedMaterialStock", "CostingCalculationResult", "CutPiecesBundle", "AssembledGarmentBundle",
        "InspectedAndGradedUnit", "DispatchedShipmentManifest", "IssuedInvoiceDocument"
    )

    @Test
    fun wiredPortTypes_equalLegacyRegistry_andVocabularyAddsOnlyArchetypeDefaults() {
        assertEquals(LEGACY_WIRED_PORTS, pack.wiredPortTypes.map { it.value }.toSet())
        val defaults = GarmentSlots.all.flatMap { listOf(it.defaultExpectedInputType, it.defaultProducedOutputType) }
        assertEquals(LEGACY_WIRED_PORTS + defaults, pack.portTypes.map { it.value }.toSet())
    }

    @Test
    fun registry_findsGarmentByCode_andUnknownIsNull() {
        assertSame(pack, DomainPackRegistry.find(DomainPackCode("garment")))
        assertSame(pack, DomainPackRegistry.soleActivePack)
        assertEquals(null, DomainPackRegistry.find(DomainPackCode("elearning")), "belum didaftarkan — tidak boleh fallback ke garment")
    }
}
