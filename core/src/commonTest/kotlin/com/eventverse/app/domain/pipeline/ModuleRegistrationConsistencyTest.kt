package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.contracts.PortDataTypeRegistry
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
        assertEquals(BusinessModule.operational.toSet(), specModules.toSet(), "Spec katalog ≠ BusinessModule.operational")
        assertEquals(specModules.size, specModules.toSet().size, "Spec ganda di OperationalModuleCatalog.all")
    }

    @Test
    fun everyFoundationModule_hasFoundationSpec_andGovernanceHasNoSpecAtAll() {
        assertEquals(BusinessModule.foundation.toSet(), FoundationModuleCatalog.all.map { it.module }.toSet())
        val specced = OperationalModuleCatalog.all.map { it.module } + FoundationModuleCatalog.all.map { it.module }
        BusinessModule.governance.forEach { assertTrue(it !in specced, "Modul governance ${it.code} tidak boleh punya spec katalog") }
    }

    @Test
    fun everyPortType_isRegistered() {
        GarmentBusinessPreset.entries.forEach { preset ->
            OperationalModuleCatalog.all.forEach { spec ->
                (spec.inputsFor(preset) + spec.outputsFor(preset)).forEach { type ->
                    assertTrue(PortDataTypeRegistry.isTyped(type), "Port '$type' (${spec.module.code}) belum terdaftar di PortDataTypeRegistry")
                }
            }
        }
    }

    @Test
    fun everyActiveModule_isFedAndFeedsSomething_exceptChainEnds() {
        GarmentBusinessPreset.entries.forEach { preset ->
            val edges = CatalogPortWiring.edges(preset)
            val active = CatalogPortWiring.activeModules(preset).map { it.module }
            active.forEach { module ->
                val spec = OperationalModuleCatalog.specificationFor(module)
                if (spec.inputsFor(preset).isNotEmpty()) {
                    assertTrue(edges.any { it.to == module }, "$preset: ${module.code} punya port masuk tapi tak ada yang menyuplai")
                }
                if (spec.outputsFor(preset).isNotEmpty() && module != BusinessModule.FULFILLMENT) {
                    assertTrue(edges.any { it.from == module }, "$preset: port keluar ${module.code} tidak dikonsumsi siapa pun")
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
        GarmentBusinessPreset.entries.forEach { preset ->
            val nodes = PipelinePresetFactory.createSnapshot(preset).nodes
            val active = nodes.filterNot { it.isBypassed }
            val moduleOf = active.associate { it.id to it.module }
            val presetEdges = PipelineGraph.from(active).edges
                .filterNot { it.isFeedback }
                .mapNotNull { e -> moduleOf[e.fromNodeId]?.let { f -> moduleOf[e.toNodeId]?.let { t -> f to t } } }
                .toSet()
            val portEdges = CatalogPortWiring.edges(preset).map { it.from to it.to }.toSet()
            assertEquals(
                presetEdges.map { "${it.first.code}→${it.second.code}" }.sorted(),
                portEdges.map { "${it.first.code}→${it.second.code}" }.sorted(),
                "$preset: sambungan port ≠ sambungan preset"
            )
            assertEquals(
                active.map { it.module }.toSet(),
                CatalogPortWiring.activeModules(preset).map { it.module }.toSet(),
                "$preset: supportedPresets katalog ≠ modul aktif di preset"
            )
        }
    }
}
