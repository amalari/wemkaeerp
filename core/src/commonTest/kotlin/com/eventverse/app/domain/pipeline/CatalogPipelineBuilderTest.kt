package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.rbac.BusinessModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Kanvas dari katalog (TRD-FLOW-002 bagian A). */
class CatalogPipelineBuilderTest {

    /** Strangler Fig: tiga preset bawaan harus identik dengan seed tulis tangan — node per node. */
    @Test
    fun builtPresets_shouldEqualHandWrittenSeedsExactly() {
        GarmentBlueprints.all.forEach { blueprint ->
            PipelineSimulationScenario.entries.forEach { scenario ->
                assertEquals(
                    PresetNodeSeeds.nodes(blueprint.code, scenario),
                    CatalogPipelineBuilder.build(blueprint, scenario),
                    "${blueprint.code.value}/$scenario berbeda dari seed"
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
        val blueprint = GarmentBlueprints.FOB_FULL_PACKAGE
        val seedsWithoutQc = PresetNodeSeeds.nodes(blueprint.code).filterNot { it.module == GarmentModules.QUALITY_CONTROL }

        val nodes = CatalogPipelineBuilder.build(blueprint, seeds = seedsWithoutQc)
        val qc = nodes.single { it.module == GarmentModules.QUALITY_CONTROL }

        assertEquals("fob-quality-control", qc.id)
        assertEquals(listOf(GarmentModules.FULFILLMENT.code), qc.downstreamModuleCodes)
        assertTrue(GarmentModules.QUALITY_CONTROL.code in nodes.single { it.module == GarmentModules.OPERATOR_EXEC }.downstreamModuleCodes)
        // Rujukan tech pack tergambar sebagai port masuk otomatis.
        assertTrue(qc.inputs.any { it.sourceModuleCode == GarmentModules.TECH_PACK_BOM.code && it.isAutomated })
        val edges = PipelineGraph.from(nodes).edges.filterNot { it.isFeedback }.map { it.fromNodeId to it.toNodeId }
        assertTrue(("fob-operator-exec" to qc.id) in edges || edges.any { it.second == qc.id }, "QC harus tersambung di kanvas")
    }

    @Test
    fun catalogOrder_shouldDriveStepNumbers() {
        GarmentBlueprints.all.forEach { blueprint ->
            assertEquals(
                OperationalModuleCatalog.all.map { it.module },
                CatalogPipelineBuilder.build(blueprint).sortedBy { it.stepNumber }.map { it.module }
            )
        }
    }

    /**
     * Blueprint baru (buatan tenant/AI) tanpa seed: node disintesis dengan awalan id dari kodenya, modul
     * aktif/bypass dari Blueprint, dan sambungan dari port — tanpa satu pun kode yang menyebut preset.
     * Contoh: makloon yang juga menerima kain titipan di gudang (Gudang aktif, Tech Pack tetap bypass).
     */
    @Test
    fun blueprintWithoutSeeds_shouldBuildFromBlueprintAlone() {
        val base = GarmentBlueprints.CMT_MAKLOON
        val blueprint = base.copy(
            code = BlueprintCode("makloon_titipan_gudang"),
            modules = base.modules.map { if (it.moduleCode == GarmentModules.INVENTORY.code) it.copy(active = true) else it }
        )

        val nodes = CatalogPipelineBuilder.build(blueprint)

        val inventory = nodes.single { it.module == GarmentModules.INVENTORY }
        assertEquals("makloon-titipan-gudang-inventory", inventory.id)
        assertTrue(!inventory.isBypassed)
        assertTrue(nodes.single { it.module == GarmentModules.TECH_PACK_BOM }.isBypassed)
        assertEquals(listOf(GarmentModules.PRODUCTION_MRP.code), inventory.downstreamModuleCodes,
            "HPP CMT = SERVICE_FEE_ONLY → tidak membaca stok; hanya MRP yang menerima kain")
    }
}
