package com.eventverse.app.presentation.pipeline

import com.eventverse.app.domain.pipeline.FactoryPipelineSnapshot
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.domain.pipeline.PipelinePresetFactory
import com.eventverse.app.domain.pipeline.PipelineStage

data class FactoryFlowUiState(
    val selectedPreset: GarmentBusinessPreset = GarmentBusinessPreset.DEFAULT,
    val snapshot: FactoryPipelineSnapshot = PipelinePresetFactory.createSnapshot(selectedPreset),
    val selectedNode: PipelineNode? = null,
    val inspectingInputNode: PipelineNode? = null,
    val isPresentationMode: Boolean = false,
    val selectedStageFilter: PipelineStage? = null,
    val searchQuery: String = "",
    val isSimulatingRealtime: Boolean = true,
    val hideBypassedNodes: Boolean = true,
    val activeScenario: com.eventverse.app.domain.pipeline.PipelineSimulationScenario = com.eventverse.app.domain.pipeline.PipelineSimulationScenario.NORMAL
) {
    val bypassedCount: Int get() = snapshot.nodes.count { it.isBypassed }

    val filteredNodes: List<PipelineNode>
        get() = snapshot.nodes
            .filter { node ->
                if (hideBypassedNodes) !node.isBypassed else true
            }
            .filter { node ->
                selectedStageFilter == null || node.stage == selectedStageFilter
            }
            .filter { node ->
                if (searchQuery.isBlank()) true
                else {
                    node.title.contains(searchQuery, ignoreCase = true) ||
                    node.assignedDepartment.contains(searchQuery, ignoreCase = true) ||
                    node.inputContract.contains(searchQuery, ignoreCase = true) ||
                    node.outputContract.contains(searchQuery, ignoreCase = true)
                }
            }
}

sealed interface FactoryFlowUiEvent {
    data class SelectPreset(val preset: GarmentBusinessPreset) : FactoryFlowUiEvent
    data class SelectScenario(val scenario: com.eventverse.app.domain.pipeline.PipelineSimulationScenario) : FactoryFlowUiEvent
    data class SelectNode(val node: PipelineNode?) : FactoryFlowUiEvent
    data class InspectNodeInputs(val node: PipelineNode?) : FactoryFlowUiEvent
    data object TogglePresentationMode : FactoryFlowUiEvent
    data class FilterByStage(val stage: PipelineStage?) : FactoryFlowUiEvent
    data class UpdateSearchQuery(val query: String) : FactoryFlowUiEvent
    data object ToggleSimulation : FactoryFlowUiEvent
    data object ToggleHideBypassed : FactoryFlowUiEvent
    data object ResetFilters : FactoryFlowUiEvent
}

