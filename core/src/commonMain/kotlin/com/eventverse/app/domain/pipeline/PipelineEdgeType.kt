package com.eventverse.app.domain.pipeline

/**
 * Categorization of connections between nodes in the factory pipeline.
 *
 * Distinguishes normal forward handoffs from dynamic feedback/rework loops
 * caused by quality inspection failures or material defects.
 */
enum class PipelineEdgeType(
    val displayName: String,
    val isFeedback: Boolean,
    val colorHex: Long
) {
    /** Standard forward handoff from upstream to downstream stage. */
    FORWARD(
        displayName = "Alur Maju Normal",
        isFeedback = false,
        colorHex = 0xFF2563EB
    ),

    /**
     * Feedback loop triggered when QC fails due to raw material / fabric defect.
     * Routes backward to Supply Chain / Warehouse for vendor return & shortage re-order.
     */
    FEEDBACK_DEFECT(
        displayName = "Feedback: Cacat Kain -> Rantai Pasok",
        isFeedback = true,
        colorHex = 0xFFDC2626 // Crimson Red
    ),

    /**
     * Feedback loop triggered when QC fails due to workmanship / sewing defect.
     * Routes backward to Production Floor (Sewing Line) for alteration / rework.
     */
    FEEDBACK_REWORK(
        displayName = "Feedback: Cacat Jahit -> Rework Operator",
        isFeedback = true,
        colorHex = 0xFFEA580C // Orange
    ),

    /** Feedback loop triggered when dimensions or specs deviate from Tech Pack. */
    FEEDBACK_TECHPACK_REVISION(
        displayName = "Feedback: Deviasi Ukuran -> Revisi Tech Pack",
        isFeedback = true,
        colorHex = 0xFF7C3AED // Purple
    ),

    /** Generic conditional alternative path. */
    CONDITIONAL_BRANCH(
        displayName = "Cabang Bersyarat",
        isFeedback = true,
        colorHex = 0xFF64748B
    )
}
