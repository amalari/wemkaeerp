package com.eventverse.app.presentation.pipeline

import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.domain.pipeline.PipelinePresetFactory
import com.eventverse.app.domain.pipeline.PipelineStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FactoryFlowViewModel(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(FactoryFlowUiState())
    val uiState: StateFlow<FactoryFlowUiState> = _uiState.asStateFlow()

    fun onEvent(event: FactoryFlowUiEvent) {
        when (event) {
            is FactoryFlowUiEvent.SelectPreset -> {
                _uiState.update { current ->
                    val newSnapshot = PipelinePresetFactory.createSnapshot(event.preset, current.activeScenario)
                    current.copy(
                        selectedPreset = event.preset,
                        snapshot = newSnapshot,
                        selectedNode = null, // reset selection when preset changes
                        inspectingInputNode = null
                    )
                }
            }
            is FactoryFlowUiEvent.SelectScenario -> {
                _uiState.update { current ->
                    val newSnapshot = PipelinePresetFactory.createSnapshot(current.selectedPreset, event.scenario)
                    current.copy(
                        activeScenario = event.scenario,
                        snapshot = newSnapshot,
                        selectedNode = null,
                        inspectingInputNode = null
                    )
                }
            }
            is FactoryFlowUiEvent.SelectNode -> {
                _uiState.update { it.copy(selectedNode = event.node) }
            }
            is FactoryFlowUiEvent.InspectNodeInputs -> {
                _uiState.update { it.copy(inspectingInputNode = event.node) }
            }
            is FactoryFlowUiEvent.TogglePresentationMode -> {
                _uiState.update { it.copy(isPresentationMode = !it.isPresentationMode) }
            }
            is FactoryFlowUiEvent.FilterByStage -> {
                _uiState.update { it.copy(selectedStageFilter = event.stage) }
            }
            is FactoryFlowUiEvent.UpdateSearchQuery -> {
                _uiState.update { it.copy(searchQuery = event.query) }
            }
            is FactoryFlowUiEvent.ToggleSimulation -> {
                _uiState.update { it.copy(isSimulatingRealtime = !it.isSimulatingRealtime) }
            }
            is FactoryFlowUiEvent.ToggleHideBypassed -> {
                _uiState.update { it.copy(hideBypassedNodes = !it.hideBypassedNodes) }
            }
            is FactoryFlowUiEvent.ResetFilters -> {

                _uiState.update {
                    it.copy(
                        selectedStageFilter = null,
                        searchQuery = ""
                    )
                }
            }
        }
    }
}
