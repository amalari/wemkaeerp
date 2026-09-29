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

import com.eventverse.app.domain.rbac.BusinessModules

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.rbac.BusinessModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Penjaga pendaftaran modul (TRD-FLOW-002 Fase 2, module-integration-rules §5). Modul operasional
 * yang didaftarkan setengah — tanpa spec, dengan port tak terdaftar, atau port yang tidak
 * menyambung — gagal di sini, bukan diam-diam hilang dari kanvas Factory Flow.
 */
class ModuleRegistrationConsistencyTest {

    @Test
    fun everyOperationalModule_hasExactlyOneCatalogSpec_andNoOtherKindDoes() {
        val specModules = OperationalModuleCatalog.all.map { it.module }
        assertEquals(BusinessModules.operational.toSet(), specModules.toSet(), "Spec katalog ≠ BusinessModules.operational")
        assertEquals(specModules.size, specModules.toSet().size, "Spec ganda di OperationalModuleCatalog.all")
    }

    @Test
    fun everyFoundationModule_hasFoundationSpec_andGovernanceHasNoSpecAtAll() {
        assertEquals(BusinessModules.foundation.toSet(), FoundationModuleCatalog.all.map { it.module }.toSet())
        val specced = OperationalModuleCatalog.all.map { it.module } + FoundationModuleCatalog.all.map { it.module }
        BusinessModules.governance.forEach { assertTrue(it !in specced, "Modul governance ${it.code} tidak boleh punya spec katalog") }
    }

    @Test
    fun everyPortType_isRegistered() {
        GarmentBlueprints.all.forEach { blueprint ->
            OperationalModuleCatalog.all.forEach { spec ->
                (spec.inputsFor(blueprint.parametersOf(spec.module.code)) + spec.outputsFor(blueprint.parametersOf(spec.module.code))).forEach { type ->
                    assertTrue(DomainPackRegistry.soleActivePack.isWired(type), "Port '$type' (${spec.module.code}) belum terdaftar di port wiring pack")
                }
            }
        }
    }

    @Test
    fun everyActiveModule_isFedAndFeedsSomething_exceptChainEnds() {
        GarmentBlueprints.all.forEach { blueprint ->
            val edges = CatalogPortWiring.edges(blueprint)
            val active = CatalogPortWiring.activeModules(blueprint).map { it.module }
            active.forEach { module ->
                val spec = OperationalModuleCatalog.specificationFor(module)
                if (spec.inputsFor(blueprint.parametersOf(spec.module.code)).isNotEmpty()) {
                    assertTrue(edges.any { it.to == module }, "${blueprint.code.value}: ${module.code} punya port masuk tapi tak ada yang menyuplai")
                }
                if (spec.outputsFor(blueprint.parametersOf(spec.module.code)).isNotEmpty() && module != GarmentModules.FULFILLMENT) {
                    assertTrue(edges.any { it.from == module }, "${blueprint.code.value}: port keluar ${module.code} tidak dikonsumsi siapa pun")
                }
            }
        }
    }

    /**
     * Paritas sementara (Strangler Fig): sambungan dari port harus sama persis dengan sambungan maju
     * yang digambar kanvas hari ini dari preset tulis tangan. Dihapus bersama PipelinePresetFactory.
     */
    @Test
    fun portWiring_shouldReproduceHandWrittenPresetEdges() {
        GarmentBlueprints.all.forEach { blueprint ->
            val nodes = PresetNodeSeeds.nodes(blueprint.code)
            val active = nodes.filterNot { it.isBypassed }
            val moduleOf = active.associate { it.id to it.module }
            val presetEdges = PipelineGraph.from(active).edges
                .filterNot { it.isFeedback }
                .mapNotNull { e -> moduleOf[e.fromNodeId]?.let { f -> moduleOf[e.toNodeId]?.let { t -> f to t } } }
                .toSet()
            val portEdges = CatalogPortWiring.edges(blueprint).map { it.from to it.to }.toSet()
            assertEquals(
                presetEdges.map { "${it.first.code}→${it.second.code}" }.sorted(),
                portEdges.map { "${it.first.code}→${it.second.code}" }.sorted(),
                "${blueprint.code.value}: sambungan port ≠ sambungan preset"
            )
            assertEquals(
                active.map { it.module }.toSet(),
                CatalogPortWiring.activeModules(blueprint).map { it.module }.toSet(),
                "${blueprint.code.value}: modul aktif Blueprint ≠ modul aktif di preset"
            )
        }
    }
}
