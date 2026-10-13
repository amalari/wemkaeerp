package com.eventverse.app.presentation.pipeline

import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.pipeline.PipelinePresetFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PLAN A2: tenant baru mulai tanpa modul aktif — kanvas tidak boleh kosong, tombol "Aktivasi" harus terjangkau. */
class FactoryFlowBypassVisibilityTest {

    private fun stateWith(allBypassed: Boolean, preset: com.eventverse.app.domain.blueprint.Blueprint): FactoryFlowUiState {
        val snapshot = PipelinePresetFactory.createSnapshot(preset)
        val nodes = if (allBypassed) snapshot.nodes.map { it.copy(healthStatus = FlowHealthStatus.BYPASSED) } else snapshot.nodes
        return FactoryFlowUiState(selectedPreset = preset, snapshot = snapshot.copy(nodes = nodes))
    }

    @Test
    fun noActiveNode_showsBypassedNodesDespiteHideDefault() {
        GarmentBlueprints.all.forEach { preset ->
            val state = stateWith(allBypassed = true, preset = preset)
            assertTrue(state.hideBypassedNodes)
            assertFalse(state.hidesBypassedNodes, "preset ${preset.code}")
            assertEquals(state.snapshot.nodes.size, state.filteredNodes.size)
        }
    }

    @Test
    fun withActiveNodes_stillHidesBypassedNodes() {
        val state = stateWith(allBypassed = false, preset = GarmentBlueprints.CMT_MAKLOON)
        assertTrue(state.snapshot.nodes.any { it.isBypassed })
        assertTrue(state.hidesBypassedNodes)
        assertTrue(state.filteredNodes.none { it.isBypassed && !it.isNewFromCatalog })
    }
}
