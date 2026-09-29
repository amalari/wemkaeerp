package com.eventverse.app.presentation.pipeline

import com.eventverse.app.domain.pipeline.FactoryPipelineSnapshot
import com.eventverse.app.domain.pipeline.ModuleTelemetry
import com.eventverse.app.domain.pipeline.PipelineTelemetryOverlay
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.PipelinePresetFactory
import com.eventverse.app.domain.pipeline.PipelineSimulationScenario
import com.eventverse.app.domain.pipeline.TenantPipelineProjector
import com.eventverse.app.infrastructure.api.PipelineApiClient
import com.eventverse.app.infrastructure.api.PipelineRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the factory flow canvas.
 *
 * Renders the tenant's **persisted** topology: which modules exist, what this factory calls
 * them, and which are switched off all come from the server. [TenantPipelineProjector]
 * overlays that topology onto the preset template, which supplies only the operational
 * detail a graph cannot carry (WIP, cycle times, input ports).
 *
 * If the server cannot be reached the screen falls back to the preset template and says so,
 * rather than silently presenting template data as if it were the tenant's configuration.
 */
class FactoryFlowViewModel(
    private val apiClient: PipelineRemoteDataSource = PipelineApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(FactoryFlowUiState())
    val uiState: StateFlow<FactoryFlowUiState> = _uiState.asStateFlow()

    fun onEvent(event: FactoryFlowUiEvent) {
        when (event) {
            is FactoryFlowUiEvent.LoadTenantPipeline -> loadTenantPipeline(event.tenantSlug)
            is FactoryFlowUiEvent.Retry -> loadTenantPipeline(event.tenantSlug)

            is FactoryFlowUiEvent.ResetToPreset -> mutateTopology(
                tenantSlug = event.tenantSlug,
                successMessage = "Alur dipulihkan ke preset ${event.preset.shortBadge}."
            ) { apiClient.resetPipeline(event.tenantSlug, event.preset) }

            is FactoryFlowUiEvent.SetModuleActive -> mutateTopology(
                tenantSlug = event.tenantSlug,
                successMessage = if (event.isActive) {
                    "Modul diaktifkan untuk tenant ini."
                } else {
                    "Modul dinonaktifkan (bypass) untuk tenant ini."
                }
            ) { apiClient.setModuleActivation(event.tenantSlug, event.moduleId, event.isActive) }

            is FactoryFlowUiEvent.RenameModule -> {
                val trimmed = event.displayName.trim()
                if (trimmed.isBlank()) {
                    _uiState.update { it.copy(error = "Nama modul tidak boleh kosong.") }
                } else {
                    _uiState.update { it.copy(renamingNode = null) }
                    mutateTopology(
                        tenantSlug = event.tenantSlug,
                        successMessage = "Nama modul disimpan sebagai \"$trimmed\"."
                    ) { apiClient.renameModule(event.tenantSlug, event.nodeId, trimmed) }
                }
            }

            is FactoryFlowUiEvent.StartRenamingModule ->
                _uiState.update { it.copy(renamingNode = event.node) }

            is FactoryFlowUiEvent.ToggleModulePanel ->
                _uiState.update { it.copy(isModulePanelVisible = !it.isModulePanelVisible) }

            is FactoryFlowUiEvent.DismissStatusMessage ->
                _uiState.update { it.copy(statusMessage = null, error = null) }

            is FactoryFlowUiEvent.SelectPreset -> _uiState.update { current ->
                // Preview a preset locally without touching persisted tenant data.
                current.copy(
                    selectedPreset = event.preset,
                    pipeline = null,
                    isOfflineFallback = true,
                    snapshot = PipelineTelemetryOverlay.applyTo(
                        PipelinePresetFactory.createSnapshot(event.preset, current.activeScenario), emptyList(), current.activeScenario
                    ),
                    selectedNode = null,
                    inspectingInputNode = null
                )
            }

            is FactoryFlowUiEvent.SelectScenario -> _uiState.update { current ->
                current.copy(
                    activeScenario = event.scenario,
                    snapshot = current.copy(activeScenario = event.scenario).let { it.withTelemetry(it.reproject(event.scenario), it.telemetry) },
                    selectedNode = null,
                    inspectingInputNode = null
                )
            }

            is FactoryFlowUiEvent.SelectNode -> _uiState.update { it.copy(selectedNode = event.node) }

            is FactoryFlowUiEvent.InspectNodeInputs ->
                _uiState.update { it.copy(inspectingInputNode = event.node) }

            is FactoryFlowUiEvent.TogglePresentationMode ->
                _uiState.update { it.copy(isPresentationMode = !it.isPresentationMode) }

            is FactoryFlowUiEvent.FilterByStage ->
                _uiState.update { it.copy(selectedStageFilter = event.stage) }

            is FactoryFlowUiEvent.UpdateSearchQuery ->
                _uiState.update { it.copy(searchQuery = event.query) }

            is FactoryFlowUiEvent.ToggleSimulation ->
                _uiState.update { it.copy(isSimulatingRealtime = !it.isSimulatingRealtime) }

            is FactoryFlowUiEvent.ToggleHideBypassed ->
                _uiState.update { it.copy(hideBypassedNodes = !it.hideBypassedNodes) }

            is FactoryFlowUiEvent.ResetFilters ->
                _uiState.update { it.copy(selectedStageFilter = null, searchQuery = "") }
        }
    }

    private fun loadTenantPipeline(tenantSlug: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        scope.launch {
            apiClient.getPipeline(tenantSlug)
                .onSuccess { pipeline ->
                    applyPipeline(pipeline)
                    loadModuleCatalog(tenantSlug)
                    // Level 2 kanvas; gagal memuat → tetap kerangka rajut, tidak memblokir kanvas.
                    apiClient.getStageFlow().onSuccess { stages -> _uiState.update { it.copy(stageFlow = stages) } }
                    apiClient.getTelemetry(tenantSlug).onSuccess { readings ->
                        _uiState.update { it.copy(telemetry = readings, snapshot = it.withTelemetry(it.snapshot, readings)) }
                    }
                }
                .onFailure { cause -> fallBackToPreset(cause) }
        }
    }

    private suspend fun loadModuleCatalog(tenantSlug: String) {
        apiClient.getModuleCatalog(tenantSlug)
            .onSuccess { catalog -> _uiState.update { it.copy(moduleCatalog = catalog) } }
        // A missing catalogue only disables the module panel; the canvas itself stays usable,
        // so this is deliberately not surfaced as a blocking error.
    }

    /**
     * Runs a server-side topology change, then adopts the pipeline the server returns as the
     * new truth instead of guessing the outcome locally.
     */
    private fun mutateTopology(
        tenantSlug: String,
        successMessage: String,
        block: suspend () -> Result<CustomTenantPipeline>
    ) {
        _uiState.update { it.copy(isSaving = true, error = null, statusMessage = null) }

        scope.launch {
            block()
                .onSuccess { pipeline ->
                    applyPipeline(pipeline)
                    _uiState.update { it.copy(statusMessage = successMessage) }
                    loadModuleCatalog(tenantSlug)
                }
                .onFailure { cause ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = cause.message ?: "Perubahan alur gagal disimpan."
                        )
                    }
                }
        }
    }

    private fun applyPipeline(pipeline: CustomTenantPipeline) {
        _uiState.update { current ->
            current.copy(
                pipeline = pipeline,
                selectedPreset = pipeline.baseStarterPreset ?: current.selectedPreset,
                snapshot = current.withTelemetry(TenantPipelineProjector.project(pipeline, current.activeScenario), current.telemetry),
                isLoading = false,
                isSaving = false,
                isOfflineFallback = false,
                error = null,
                // A node held from a previous topology may no longer exist.
                selectedNode = null,
                inspectingInputNode = null
            )
        }
    }

    private fun fallBackToPreset(cause: Throwable) {
        _uiState.update { current ->
            current.copy(
                pipeline = null,
                snapshot = PipelineTelemetryOverlay.applyTo(
                    PipelinePresetFactory.createSnapshot(current.selectedPreset, current.activeScenario),
                    emptyList(),
                    current.activeScenario
                ),
                isLoading = false,
                isSaving = false,
                isOfflineFallback = true,
                error = cause.message ?: "Tidak dapat memuat alur tenant dari server."
            )
        }
    }

    /** Re-renders the current topology under a different simulation scenario. */
    private fun FactoryFlowUiState.reproject(
        scenario: PipelineSimulationScenario
    ) = pipeline
        ?.let { TenantPipelineProjector.project(it, scenario) }
        ?: PipelinePresetFactory.createSnapshot(selectedPreset, scenario)

    /**
     * Angka nyata hanya untuk alur tenant tersimpan pada skenario Normal. Pratinjau preset dan
     * simulasi cacat tetap angka contoh — dan karena itu seluruh node aktifnya bertag "estimasi".
     */
    private fun FactoryFlowUiState.withTelemetry(snapshot: FactoryPipelineSnapshot, readings: List<ModuleTelemetry>) =
        PipelineTelemetryOverlay.applyTo(
            snapshot,
            readings.takeIf { pipeline != null && activeScenario == PipelineSimulationScenario.NORMAL }.orEmpty(),
            activeScenario
        )
}
