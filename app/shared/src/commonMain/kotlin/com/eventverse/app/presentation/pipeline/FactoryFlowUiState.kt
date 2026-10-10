package com.eventverse.app.presentation.pipeline

import com.eventverse.app.presentation.pack.ActiveTenantPack
import com.eventverse.app.domain.blueprint.Blueprint

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.FactoryPipelineSnapshot
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.domain.pipeline.PipelinePresetFactory
import com.eventverse.app.domain.pipeline.PipelineSimulationScenario
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pipeline.TenantModuleCatalogSnapshot
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.pipeline.ModuleTelemetry
import com.eventverse.app.domain.stageflow.StageDefinition

data class FactoryFlowUiState(
    val selectedPreset: Blueprint = GarmentBlueprints.DEFAULT,
    val snapshot: FactoryPipelineSnapshot = PipelinePresetFactory.createSnapshot(selectedPreset),
    /**
     * The tenant's persisted topology. Null while loading, or when the server is unreachable
     * and the screen is showing the preset template as a fallback.
     */
    val pipeline: CustomTenantPipeline? = null,
    val moduleCatalog: TenantModuleCatalogSnapshot = TenantModuleCatalogSnapshot.EMPTY,
    val isLoading: Boolean = true,
    /** Hasil pemuatan alur tenant; AccessDenied/Failed menggantikan kanvas dengan kartu (Q2). */
    val loadState: FactoryFlowLoadState = FactoryFlowLoadState.Loading,
    val isSaving: Boolean = false,
    val error: String? = null,
    /** Set when the topology shown is the preset template rather than persisted tenant data. */
    val isOfflineFallback: Boolean = false,
    val statusMessage: String? = null,
    val isModulePanelVisible: Boolean = false,
    val renamingNode: PipelineNode? = null,
    val selectedNode: PipelineNode? = null,
    val inspectingInputNode: PipelineNode? = null,
    val isPresentationMode: Boolean = false,
    val selectedStageFilter: PhaseDefinition? = null,
    /** Kosakata vertikal: kolom kanvas dibaca dari sini (Jalur B, B1). */
    val pack: DomainPack = ActiveTenantPack.current,
    val searchQuery: String = "",
    val isSimulatingRealtime: Boolean = true,
    val hideBypassedNodes: Boolean = true,
    val activeScenario: PipelineSimulationScenario = PipelineSimulationScenario.NORMAL,
    /** Kerangka tahap tenant untuk kanvas level 2 (rajut sampai berhasil dimuat). */
    val stageFlow: List<StageDefinition> = SamplingRoute.DEFAULT_STAGES,
    /** Telemetri nyata terakhir; kosong → node aktif ditandai "estimasi" (TRD-FLOW-002 Fase 5). */
    val telemetry: List<ModuleTelemetry> = emptyList()
) {
    val bypassedCount: Int get() = snapshot.nodes.count { it.isBypassed }

    /** True once persisted tenant data is on screen, as opposed to the preset template. */
    val isTenantDataLoaded: Boolean get() = pipeline != null && !isOfflineFallback

    val pipelineName: String? get() = pipeline?.pipelineName

    val customPluginCount: Int get() = snapshot.nodes.count { it.isCustomPlugin }

    val filteredNodes: List<PipelineNode>
        get() = snapshot.nodes
            .filter { node ->
                // Modul baru dari katalog selalu tampil, supaya tidak tersembunyi sebelum sempat diaktifkan.
                if (hideBypassedNodes) !node.isBypassed || node.isNewFromCatalog else true
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
    /** Loads the tenant's persisted pipeline and module catalogue from the server. */
    data class LoadTenantPipeline(val tenantSlug: String) : FactoryFlowUiEvent
    data class Retry(val tenantSlug: String) : FactoryFlowUiEvent

    /** Resets the tenant's topology back to a standard starter preset. */
    data class ResetToPreset(
        val tenantSlug: String,
        val preset: Blueprint
    ) : FactoryFlowUiEvent

    /** Switches one module on or off for this tenant. */
    data class SetModuleActive(
        val tenantSlug: String,
        val moduleId: String,
        val isActive: Boolean
    ) : FactoryFlowUiEvent

    /** Renames a module for this tenant only. */
    data class RenameModule(
        val tenantSlug: String,
        val nodeId: String,
        val displayName: String
    ) : FactoryFlowUiEvent

    data class StartRenamingModule(val node: PipelineNode?) : FactoryFlowUiEvent
    data object ToggleModulePanel : FactoryFlowUiEvent
    data object DismissStatusMessage : FactoryFlowUiEvent

    data class SelectPreset(val preset: Blueprint) : FactoryFlowUiEvent
    data class SelectScenario(val scenario: PipelineSimulationScenario) : FactoryFlowUiEvent
    data class SelectNode(val node: PipelineNode?) : FactoryFlowUiEvent
    data class InspectNodeInputs(val node: PipelineNode?) : FactoryFlowUiEvent
    data object TogglePresentationMode : FactoryFlowUiEvent
    data class FilterByStage(val stage: PhaseDefinition?) : FactoryFlowUiEvent
    data class UpdateSearchQuery(val query: String) : FactoryFlowUiEvent
    data object ToggleSimulation : FactoryFlowUiEvent
    data object ToggleHideBypassed : FactoryFlowUiEvent
    data object ResetFilters : FactoryFlowUiEvent
}
