package com.eventverse.app.domain.moduledev

import kotlinx.datetime.Instant

/**
 * One attempt at building or customising a module: what was asked for, what it was estimated at,
 * and what it actually took.
 *
 * The fields are grouped into the blocks described in the migration, and the grouping is load
 * bearing rather than cosmetic:
 *
 *  - **Retrieval** ([requirementText], [embedding]) — how comparable past work is found.
 *  - **Features** ([features], [sizePoints]) — frozen at [estimatedAt], never corrected.
 *  - **Normalisers** ([clarityScore], [builderExperience]) — separate how big the work was from
 *    the conditions it happened under.
 *  - **Estimate** ([estimatedHours], [estimatedHoursP90]) — written once, never overwritten.
 *  - **Actuals** ([actualHours]) — filled on completion.
 *
 * The estimate is never overwritten with actuals because the gap between them is the only thing
 * a finished build has to teach. Overwriting produces a tidy table that has forgotten every
 * lesson in it.
 */
data class ModuleBuildRecord(
    val id: ModuleBuildId,
    val catalogEntryId: ModuleCatalogEntryId,
    val buildType: BuildType,
    val archetypeCode: String,
    val requirementText: String,
    val status: BuildStatus = BuildStatus.ESTIMATING,
    val customizationRequestId: CustomizationRequestId? = null,
    val versionLabel: String? = null,
    val requirementSource: RequirementSource = RequirementSource.TENANT_REQUEST,

    // ---- Retrieval -----------------------------------------------------------
    val embedding: EmbeddingVector? = null,

    // ---- Features (frozen at estimatedAt) ------------------------------------
    val features: BuildFeatureVector = BuildFeatureVector.EMPTY,
    val sizePoints: SizePoints = SizePoints(0),
    val sizePointsWeightsVersion: String? = null,

    // ---- Normalisers ---------------------------------------------------------
    val clarityScore: Int? = null,
    val builderExperience: BuilderExperienceLevel? = null,
    val wasRushed: Boolean = false,
    val hadParallelWork: Boolean = false,

    // ---- Estimate (write-once) -----------------------------------------------
    val estimatedHours: WorkHours? = null,
    val estimatedHoursP90: WorkHours? = null,
    val estimatedBy: EstimatorKind? = null,
    val estimatorRef: String? = null,
    val estimateConfidence: EstimateConfidence? = null,
    val retrievedNeighborIds: List<String> = emptyList(),
    val nearestNeighborSimilarity: Double? = null,
    val openQuestions: List<String> = emptyList(),
    val estimatedAt: Instant? = null,

    // ---- Actuals -------------------------------------------------------------
    val actualHours: WorkHours? = null,
    val reworkHours: WorkHours = WorkHours.ZERO,
    val revisionRoundCount: Int = 0,
    val postReleaseDefectCount: Int = 0,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    /** Delivery duration promised to the client. Never an effort measure — see [WorkHours]. */
    val leadTimeDays: Int? = null,
    val discoveredScopeDelta: String? = null,
    val effortSource: EffortSource = EffortSource.LOGGED,
    val blendedHourlyRateIdr: MoneyIdr? = null,
    val totalBuildCostIdr: MoneyIdr? = null,
    val gitRef: String? = null,
    val retrospectiveNotes: String? = null
) {
    init {
        require(requirementText.isNotBlank()) {
            "requirementText cannot be blank: it is the richest context any estimator gets"
        }
        require(archetypeCode.isNotBlank()) { "archetypeCode cannot be blank" }
        require(clarityScore == null || clarityScore in 1..5) {
            "clarityScore must be between 1 and 5, was $clarityScore"
        }
        require(revisionRoundCount >= 0) { "revisionRoundCount cannot be negative" }
        require(postReleaseDefectCount >= 0) { "postReleaseDefectCount cannot be negative" }
        require(leadTimeDays == null || leadTimeDays >= 0) { "leadTimeDays cannot be negative" }
        require(nearestNeighborSimilarity == null || nearestNeighborSimilarity in -1.0..1.0) {
            "nearestNeighborSimilarity must be a cosine value in [-1, 1]"
        }
    }

    val isEstimated: Boolean get() = estimatedHours != null && estimatedAt != null

    /**
     * Hours per size point — what a neighbour actually lends to a new estimate.
     *
     * Null unless the row is training grade: reconstructed hours were recalled rather than
     * recorded, and imported rows have no hours at all. Letting either through would have the
     * model learn from our own guesses and call it evidence.
     */
    val productivity: Double?
        get() {
            val hours = actualHours ?: return null
            if (!effortSource.isTrainingGrade) return null
            if (sizePoints.isZero) return null
            return hours.hours / sizePoints.value
        }

    /** Signed percentage: positive means the work overran the estimate. */
    val estimateVariancePercent: Double?
        get() {
            val estimate = estimatedHours ?: return null
            val actual = actualHours ?: return null
            if (estimate.hours == 0.0) return null
            return (actual.hours - estimate.hours) / estimate.hours * 100.0
        }

    /**
     * Freezes the estimate onto the record.
     *
     * Refuses to run twice. Re-estimating in place would erase the original prediction, and the
     * distance between the first prediction and reality is the entire point of keeping this row.
     * A genuinely new estimate is a new build record.
     */
    fun withEstimate(
        estimatedHours: WorkHours,
        estimatedHoursP90: WorkHours,
        estimatedBy: EstimatorKind,
        estimatorRef: String,
        confidence: EstimateConfidence,
        estimatedAt: Instant,
        retrievedNeighborIds: List<String> = emptyList(),
        nearestNeighborSimilarity: Double? = null,
        openQuestions: List<String> = emptyList()
    ): ModuleBuildRecord {
        check(!isEstimated) {
            "Build ${id.value} is already estimated; estimates are frozen once written"
        }
        require(estimatedHoursP90.hours >= estimatedHours.hours) {
            "p90 (${estimatedHoursP90.hours}) cannot be below p50 (${estimatedHours.hours})"
        }
        return copy(
            estimatedHours = estimatedHours,
            estimatedHoursP90 = estimatedHoursP90,
            estimatedBy = estimatedBy,
            estimatorRef = estimatorRef,
            estimateConfidence = confidence,
            estimatedAt = estimatedAt,
            retrievedNeighborIds = retrievedNeighborIds,
            nearestNeighborSimilarity = nearestNeighborSimilarity,
            openQuestions = openQuestions
        )
    }

    /** Scores the frozen feature vector. Kept separate so the weights version is recorded with it. */
    fun withSizing(weights: SizingWeights): ModuleBuildRecord = copy(
        sizePoints = weights.scoreOf(features),
        sizePointsWeightsVersion = weights.version
    )

    fun markInProgress(startedAt: Instant): ModuleBuildRecord =
        copy(status = BuildStatus.IN_PROGRESS, startedAt = this.startedAt ?: startedAt)

    /**
     * Closes the build with what it actually took.
     *
     * [totalCost] and [blendedRate] are passed in already computed from the effort entries and
     * stored here deliberately: correcting an effort row years later must not silently restate
     * what a delivered build cost, because a price was quoted from that figure.
     */
    fun completeWith(
        actualHours: WorkHours,
        blendedRate: MoneyIdr,
        totalCost: MoneyIdr,
        completedAt: Instant,
        effortSource: EffortSource = EffortSource.LOGGED,
        reworkHours: WorkHours = WorkHours.ZERO,
        revisionRoundCount: Int = 0,
        leadTimeDays: Int? = null,
        discoveredScopeDelta: String? = null,
        retrospectiveNotes: String? = null,
        gitRef: String? = null
    ): ModuleBuildRecord = copy(
        status = BuildStatus.DELIVERED,
        actualHours = actualHours,
        blendedHourlyRateIdr = blendedRate,
        totalBuildCostIdr = totalCost,
        completedAt = completedAt,
        effortSource = effortSource,
        reworkHours = reworkHours,
        revisionRoundCount = revisionRoundCount,
        leadTimeDays = leadTimeDays,
        discoveredScopeDelta = discoveredScopeDelta,
        retrospectiveNotes = retrospectiveNotes,
        gitRef = gitRef ?: this.gitRef
    )

    fun cancel(): ModuleBuildRecord = copy(status = BuildStatus.CANCELLED)
}

/**
 * Hours spent by one role in one phase, at the rate in force at the time.
 *
 * The rate is stored per entry rather than looked up, so raising rates next year cannot
 * retroactively change what past work cost.
 */
data class ModuleBuildEffortEntry(
    val id: EffortEntryId,
    val buildRecordId: ModuleBuildId,
    val role: EffortRole,
    val phase: BuildPhase,
    val hours: WorkHours,
    val hourlyRateIdr: MoneyIdr,
    val performedBy: String? = null,
    val note: String? = null,
    val loggedAt: Instant? = null
) {
    init {
        require(hours.hours > 0.0) { "An effort entry must record more than zero hours" }
    }

    val cost: MoneyIdr get() = hours.costAt(hourlyRateIdr)
}

/**
 * Totals over a build's effort entries.
 *
 * The blended rate is weighted by hours rather than averaged across roles: four QA hours at a
 * lower rate and forty backend hours at a higher one do not contribute equally, and a plain mean
 * would understate the cost of backend-heavy work.
 */
data class EffortRollup(
    val totalHours: WorkHours,
    val totalCost: MoneyIdr,
    val blendedHourlyRate: MoneyIdr
) {
    companion object {
        fun of(entries: List<ModuleBuildEffortEntry>): EffortRollup {
            if (entries.isEmpty()) {
                return EffortRollup(WorkHours.ZERO, MoneyIdr.ZERO, MoneyIdr.ZERO)
            }
            val hours = WorkHours.sum(entries.map { it.hours })
            val cost = MoneyIdr.sum(entries.map { it.cost })
            val blended = if (hours.hours > 0.0) {
                MoneyIdr((cost.amount / hours.hours).toLong())
            } else {
                MoneyIdr.ZERO
            }
            return EffortRollup(hours, cost, blended)
        }
    }
}
