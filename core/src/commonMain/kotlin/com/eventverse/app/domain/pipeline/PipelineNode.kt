package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.PhaseDefinition

import com.eventverse.app.domain.rbac.BusinessModule

/**
 * An individual node in the factory operational pipeline.
 * Represents a business module in action with upstream/downstream contracts and live metrics.
 */
data class PipelineNode(
    val id: String,
    val module: BusinessModule,
    val stage: PhaseDefinition,
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
    val downstreamModuleCodes: List<String> = emptyList(),
    val inputs: List<PipelineInputPort> = emptyList(),
    /**
     * Feedback & rework routes triggered when an exception or defect occurs (e.g. QC failure).
     * These are resolved into [PipelineEdge]s of kind [PipelineEdgeType.FEEDBACK_DEFECT] or
     * [PipelineEdgeType.FEEDBACK_REWORK] and never influence column layering.
     */
    val feedbackRoutes: List<PipelineFeedbackRoute> = emptyList(),
    /** Active feedback notification or badge message for dynamic simulation. */
    val activeFeedbackBadge: String? = null,
    /**
     * Branch paths this module can also take besides its normal forward hand-off.
     */
    val conditionalPaths: List<PipelineConditionalPath> = emptyList(),
    /**
     * Set when this node came from a tenant/third-party plugin rather than a built-in
     * [BusinessModule]. [module] then holds the representative module for its capability
     * slot, which is what drives icons and access scoping.
     */
    val customModuleCode: String? = null,
    /**
     * Tenant-specific calculation overrides projected from the persisted graph
     * (sewing tariff per minute, secret margin, …).
     */
    val formulaParameters: Map<String, String> = emptyMap(),
    /**
     * Modul yang disisipkan otomatis dari katalog dan belum pernah diaktifkan tenant. Kanvas tetap
     * menampilkannya (dengan badge) walau node bypass disembunyikan — kalau tidak, modul baru tak terlihat.
     */
    val isNewFromCatalog: Boolean = false,
    /** Angka WIP/cycle time masih seed, bukan telemetri nyata — kartu menampilkan tag "estimasi". */
    val isTelemetryEstimate: Boolean = false
) {
    val isCustomPlugin: Boolean get() = customModuleCode != null
    val isBypassed: Boolean get() = healthStatus == FlowHealthStatus.BYPASSED
    val isBottleneck: Boolean get() = healthStatus == FlowHealthStatus.BOTTLENECK || healthStatus == FlowHealthStatus.CRITICAL
    val hasActiveFeedback: Boolean get() = activeFeedbackBadge != null || feedbackRoutes.any { it.isActive }

    val manualInputs: List<PipelineInputPort> get() = inputs.filter { it.isManual }
    val automatedInputs: List<PipelineInputPort> get() = inputs.filter { it.isAutomated }
    val manualInputCount: Int get() = manualInputs.size
    val automatedInputCount: Int get() = automatedInputs.size
}

/**
 * A conditional branch out of a module, referenced by module code (resolved the same way as
 * [PipelineNode.downstreamModuleCodes]) — e.g. "if QC rejects a piece, it goes back to
 * production scheduling for rework" rather than the module's normal forward hand-off.
 */
data class PipelineConditionalPath(
    val targetModuleCode: String,
    val label: String
)
