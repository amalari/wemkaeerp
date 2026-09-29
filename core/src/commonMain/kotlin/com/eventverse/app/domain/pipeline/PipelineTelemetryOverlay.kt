package com.eventverse.app.domain.pipeline

/**
 * Menimpa angka seed node kanvas dengan telemetri nyata (TRD-FLOW-002 Fase 5). Node aktif yang
 * modulnya tidak punya bacaan ditandai [PipelineNode.isTelemetryEstimate] — angka seed boleh
 * tampil, tapi tidak boleh terlihat nyata. Node bypass tidak disentuh.
 */
object PipelineTelemetryOverlay {

    /** Snapshot dengan node ditimpa telemetri; KPI agregat dihitung ulang dari angka baru. */
    fun applyTo(
        snapshot: FactoryPipelineSnapshot,
        readings: List<ModuleTelemetry>,
        scenario: PipelineSimulationScenario
    ): FactoryPipelineSnapshot =
        PipelinePresetFactory.snapshotOf(snapshot.preset, apply(snapshot.nodes, readings), scenario)

    fun apply(nodes: List<PipelineNode>, readings: List<ModuleTelemetry>): List<PipelineNode> {
        val byModule = readings.associateBy { it.module }
        return nodes.map { node ->
            val reading = byModule[node.module]?.takeUnless { node.isCustomPlugin }
            when {
                node.isBypassed -> node
                reading == null -> node.copy(isTelemetryEstimate = true)
                else -> node.copy(
                    wipPieces = reading.wipPieces,
                    cycleTimeHours = reading.cycleTimeHours,
                    healthStatus = reading.healthStatus,
                    isTelemetryEstimate = false
                )
            }
        }
    }
}
