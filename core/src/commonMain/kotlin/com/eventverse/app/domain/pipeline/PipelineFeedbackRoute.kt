package com.eventverse.app.domain.pipeline

/**
 * Declares a conditional feedback route from a node back to an upstream stage.
 * Used when an exception (such as a QC failure, fabric defect, or measurement error)
 * requires sending work or data backward through the factory pipeline.
 */
data class PipelineFeedbackRoute(
    val id: String,
    val targetModuleCode: String,
    val targetModuleName: String,
    val targetPortId: String? = null,
    val edgeType: PipelineEdgeType = PipelineEdgeType.FEEDBACK_DEFECT,
    val triggerReason: String,
    val actionContract: String,
    val isActive: Boolean = true
)
