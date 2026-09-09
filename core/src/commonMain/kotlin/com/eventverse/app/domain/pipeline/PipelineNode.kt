package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.BusinessModule

/**
 * An individual node in the factory operational pipeline.
 * Represents a business module in action with upstream/downstream contracts and live metrics.
 */
data class PipelineNode(
    val id: String,
    val module: BusinessModule,
    val stage: PipelineStage,
    val stepNumber: Int,
    val title: String,
    val description: String,
    val assignedDepartment: String,
    val deptColorHex: Long,
    val inputContract: String,
    val outputContract: String,
    val wipPieces: Int,
    val cycleTimeHours: Double,
    val healthStatus: FlowHealthStatus,
    val healthMessage: String,
    val downstreamModuleCodes: List<String> = emptyList()
) {
    val isBypassed: Boolean get() = healthStatus == FlowHealthStatus.BYPASSED
    val isBottleneck: Boolean get() = healthStatus == FlowHealthStatus.BOTTLENECK || healthStatus == FlowHealthStatus.CRITICAL
}
