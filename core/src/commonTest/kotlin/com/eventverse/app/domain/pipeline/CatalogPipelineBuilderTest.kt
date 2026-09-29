package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.BusinessModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Kanvas dari katalog (TRD-FLOW-002 bagian A). */
class CatalogPipelineBuilderTest {

    /** Strangler Fig: tiga preset bawaan harus identik dengan seed tulis tangan — node per node. */
    @Test
    fun builtPresets_shouldEqualHandWrittenSeedsExactly() {
        GarmentBusinessPreset.entries.forEach { preset ->
            PipelineSimulationScenario.entries.forEach { scenario ->
                assertEquals(
                    PresetNodeSeeds.nodes(preset, scenario),
                    CatalogPipelineBuilder.build(preset, scenario),
                    "$preset/$scenario berbeda dari seed"
                )
            }
        }
    }

    /**
     * Modul yang didaftarkan di katalog tanpa seed tampilan tetap muncul — disintesis dan
     * disambung dari portnya. Disimulasikan dengan menghapus seed QC.
     */
    @Test
    fun catalogModuleWithoutSeed_shouldAppearAndBeWiredFromPorts() {
        val preset = GarmentBusinessPreset.FOB_FULL_PACKAGE
        val seedsWithoutQc = PresetNodeSeeds.nodes(preset).filterNot { it.module == BusinessModule.QUALITY_CONTROL }

        val nodes = CatalogPipelineBuilder.build(preset, seeds = seedsWithoutQc)
        val qc = nodes.single { it.module == BusinessModule.QUALITY_CONTROL }

        assertEquals("fob-quality-control", qc.id)
        assertEquals(listOf(BusinessModule.FULFILLMENT.code), qc.downstreamModuleCodes)
        assertTrue(BusinessModule.QUALITY_CONTROL.code in nodes.single { it.module == BusinessModule.OPERATOR_EXEC }.downstreamModuleCodes)
        // Rujukan tech pack tergambar sebagai port masuk otomatis.
        assertTrue(qc.inputs.any { it.sourceModuleCode == BusinessModule.TECH_PACK_BOM.code && it.isAutomated })
        val edges = PipelineGraph.from(nodes).edges.filterNot { it.isFeedback }.map { it.fromNodeId to it.toNodeId }
        assertTrue(("fob-operator-exec" to qc.id) in edges || edges.any { it.second == qc.id }, "QC harus tersambung di kanvas")
    }

    @Test
    fun catalogOrder_shouldDriveStepNumbers() {
        GarmentBusinessPreset.entries.forEach { preset ->
            assertEquals(
                OperationalModuleCatalog.all.map { it.module },
                CatalogPipelineBuilder.build(preset).sortedBy { it.stepNumber }.map { it.module }
            )
        }
    }
}
