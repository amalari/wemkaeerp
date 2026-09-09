package com.eventverse.app.domain.pipeline

/**
 * High-level executive snapshot of the factory operational pipeline.
 * Aggregates live KPI metrics across all stages for executive presentations.
 */
data class FactoryPipelineSnapshot(
    val preset: GarmentBusinessPreset,
    val nodes: List<PipelineNode>,
    val overallHealthScore: Int,
    val totalWipPieces: Int,
    val activeBottlenecks: Int,
    val avgLeadTimeDays: Double,
    val activeModulesCount: Int,
    val bypassedModulesCount: Int
) {
    val isHealthy: Boolean get() = activeBottlenecks == 0
}
