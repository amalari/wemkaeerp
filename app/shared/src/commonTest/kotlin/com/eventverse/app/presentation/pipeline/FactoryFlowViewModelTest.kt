package com.eventverse.app.presentation.pipeline

import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.PipelineStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FactoryFlowViewModelTest {

    @Test
    fun initialState_shouldHaveDefaultFobPresetAndFullNodes() {
        val viewModel = FactoryFlowViewModel()
        val state = viewModel.uiState.value

        assertEquals(GarmentBusinessPreset.FOB_FULL_PACKAGE, state.selectedPreset)
        assertEquals(9, state.snapshot.nodes.size)
        assertEquals(9, state.filteredNodes.size)
        assertNull(state.selectedNode)
        assertFalse(state.isPresentationMode)
        assertTrue(state.isSimulatingRealtime)
    }

    @Test
    fun selectPreset_shouldSwitchSnapshotAndResetSelectedNode() {
        val viewModel = FactoryFlowViewModel()

        // Select first node
        val initialFirstNode = viewModel.uiState.value.filteredNodes.first()
        viewModel.onEvent(FactoryFlowUiEvent.SelectNode(initialFirstNode))
        assertEquals(initialFirstNode.id, viewModel.uiState.value.selectedNode?.id)

        // Now switch preset to CMT
        viewModel.onEvent(FactoryFlowUiEvent.SelectPreset(GarmentBusinessPreset.CMT_MAKLOON))
        val updatedState = viewModel.uiState.value

        assertEquals(GarmentBusinessPreset.CMT_MAKLOON, updatedState.selectedPreset)
        assertEquals(2, updatedState.snapshot.bypassedModulesCount)
        assertNull(updatedState.selectedNode) // must be reset to prevent stale inspection
    }

    @Test
    fun togglePresentationMode_shouldToggleFlag() {
        val viewModel = FactoryFlowViewModel()
        assertFalse(viewModel.uiState.value.isPresentationMode)

        viewModel.onEvent(FactoryFlowUiEvent.TogglePresentationMode)
        assertTrue(viewModel.uiState.value.isPresentationMode)

        viewModel.onEvent(FactoryFlowUiEvent.TogglePresentationMode)
        assertFalse(viewModel.uiState.value.isPresentationMode)
    }

    @Test
    fun filterByStage_shouldFilterFilteredNodes() {
        val viewModel = FactoryFlowViewModel()

        // Filter to COMMERCIAL stage (CRM + Sampling = 2 nodes)
        viewModel.onEvent(FactoryFlowUiEvent.FilterByStage(PipelineStage.COMMERCIAL))
        val state = viewModel.uiState.value

        assertEquals(PipelineStage.COMMERCIAL, state.selectedStageFilter)
        assertEquals(2, state.filteredNodes.size)
        assertTrue(state.filteredNodes.all { it.stage == PipelineStage.COMMERCIAL })

        // Clear filter
        viewModel.onEvent(FactoryFlowUiEvent.FilterByStage(null))
        assertEquals(9, viewModel.uiState.value.filteredNodes.size)
    }

    @Test
    fun updateSearchQuery_shouldFilterNodesByQuery() {
        val viewModel = FactoryFlowViewModel()

        viewModel.onEvent(FactoryFlowUiEvent.UpdateSearchQuery("kain"))
        val state = viewModel.uiState.value

        assertTrue(state.filteredNodes.isNotEmpty())
        assertTrue(state.filteredNodes.any { it.title.contains("Bahan Baku", ignoreCase = true) || it.inputContract.contains("kain", ignoreCase = true) })

        // Reset
        viewModel.onEvent(FactoryFlowUiEvent.ResetFilters)
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(9, viewModel.uiState.value.filteredNodes.size)
    }

    @Test
    fun toggleHideBypassed_shouldFilterOutBypassedNodesInCmt() {
        val viewModel = FactoryFlowViewModel()
        viewModel.onEvent(FactoryFlowUiEvent.SelectPreset(GarmentBusinessPreset.CMT_MAKLOON))

        // Initial default has hideBypassedNodes = true
        val initialCmtState = viewModel.uiState.value
        assertTrue(initialCmtState.hideBypassedNodes)
        assertEquals(2, initialCmtState.bypassedCount)
        assertEquals(7, initialCmtState.filteredNodes.size)
        assertTrue(initialCmtState.filteredNodes.none { it.isBypassed })

        // Toggle to show bypassed nodes
        viewModel.onEvent(FactoryFlowUiEvent.ToggleHideBypassed)
        val showAllState = viewModel.uiState.value
        assertFalse(showAllState.hideBypassedNodes)
        assertEquals(9, showAllState.filteredNodes.size)
        assertEquals(2, showAllState.filteredNodes.count { it.isBypassed })

        // Toggle back to hide
        viewModel.onEvent(FactoryFlowUiEvent.ToggleHideBypassed)
        val hiddenAgainState = viewModel.uiState.value
        assertTrue(hiddenAgainState.hideBypassedNodes)
        assertEquals(7, hiddenAgainState.filteredNodes.size)
    }
}

