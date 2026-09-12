package com.eventverse.app.domain.moduledev

/**
 * The countable, knowable-in-advance description of a piece of work.
 *
 * This is the contract between whoever breaks down a requirement and everything that later
 * estimates it, so new predictors are added here and nowhere else.
 *
 * **Every field must be knowable BEFORE the work starts.** Outcomes — rework hours, revision
 * rounds, post-release defects — are deliberately absent. In historical data a revision count
 * looks like the strongest predictor there is, but a new request does not carry one, so a model
 * trained on it scores beautifully offline and is useless in practice.
 *
 * A vector is frozen when the estimate is written and is never corrected afterwards, even once
 * the work proves it wrong. Having mis-scoped something is precisely the lesson the row exists
 * to teach; the correction belongs in `discoveredScopeDelta` on the build record.
 */
data class BuildFeatureVector(
    val entityCount: Int = 0,
    val useCaseCount: Int = 0,
    val screenCount: Int = 0,
    val apiEndpointCount: Int = 0,
    val dbTableCount: Int = 0,
    val reportCount: Int = 0,
    val integrationCount: Int = 0,
    /** How many of the KMP targets this work must run on. At least one. */
    val targetPlatformCount: Int = 1,
    /** Neighbouring modules the change reaches into; ripple is expensive. */
    val affectedExistingModuleCount: Int = 0,
    val requiresCustomFormula: Boolean = false,
    val requiresExternalIntegration: Boolean = false,
    val requiresRealtime: Boolean = false,
    val requiresOfflineSync: Boolean = false,
    val requiresFileUpload: Boolean = false,
    val requiresNewDesignComponent: Boolean = false
) {
    init {
        require(entityCount >= 0) { "entityCount cannot be negative" }
        require(useCaseCount >= 0) { "useCaseCount cannot be negative" }
        require(screenCount >= 0) { "screenCount cannot be negative" }
        require(apiEndpointCount >= 0) { "apiEndpointCount cannot be negative" }
        require(dbTableCount >= 0) { "dbTableCount cannot be negative" }
        require(reportCount >= 0) { "reportCount cannot be negative" }
        require(integrationCount >= 0) { "integrationCount cannot be negative" }
        require(targetPlatformCount >= 1) { "targetPlatformCount must be at least 1" }
        require(affectedExistingModuleCount >= 0) {
            "affectedExistingModuleCount cannot be negative"
        }
    }

    /** KMP targets beyond the first; the first one is the work, the rest are the tax. */
    val extraPlatformCount: Int get() = targetPlatformCount - 1

    /**
     * Feature key -> multiplicity, in the vocabulary [SizingWeights] uses.
     *
     * Boolean flags contribute 1 when set and are absent otherwise, so a weight applies once.
     */
    fun toWeightableCounts(): Map<String, Int> = buildMap {
        putIfPositive(KEY_ENTITY, entityCount)
        putIfPositive(KEY_USE_CASE, useCaseCount)
        putIfPositive(KEY_SCREEN, screenCount)
        putIfPositive(KEY_API_ENDPOINT, apiEndpointCount)
        putIfPositive(KEY_DB_TABLE, dbTableCount)
        putIfPositive(KEY_REPORT, reportCount)
        putIfPositive(KEY_INTEGRATION, integrationCount)
        putIfPositive(KEY_PLATFORM_EXTRA, extraPlatformCount)
        putIfPositive(KEY_AFFECTED_MODULE, affectedExistingModuleCount)
        if (requiresCustomFormula) put(KEY_CUSTOM_FORMULA, 1)
        if (requiresExternalIntegration) put(KEY_EXTERNAL_INTEGRATION, 1)
        if (requiresRealtime) put(KEY_REALTIME, 1)
        if (requiresOfflineSync) put(KEY_OFFLINE_SYNC, 1)
        if (requiresFileUpload) put(KEY_FILE_UPLOAD, 1)
        if (requiresNewDesignComponent) put(KEY_NEW_DESIGN_COMPONENT, 1)
    }

    private fun MutableMap<String, Int>.putIfPositive(key: String, count: Int) {
        if (count > 0) put(key, count)
    }

    companion object {
        const val KEY_ENTITY = "entity_count"
        const val KEY_USE_CASE = "use_case_count"
        const val KEY_SCREEN = "screen_count"
        const val KEY_API_ENDPOINT = "api_endpoint_count"
        const val KEY_DB_TABLE = "db_table_count"
        const val KEY_REPORT = "report_count"
        const val KEY_INTEGRATION = "integration_count"
        const val KEY_PLATFORM_EXTRA = "target_platform_extra"
        const val KEY_AFFECTED_MODULE = "affected_existing_module_count"
        const val KEY_CUSTOM_FORMULA = "requires_custom_formula"
        const val KEY_EXTERNAL_INTEGRATION = "requires_external_integration"
        const val KEY_REALTIME = "requires_realtime"
        const val KEY_OFFLINE_SYNC = "requires_offline_sync"
        const val KEY_FILE_UPLOAD = "requires_file_upload"
        const val KEY_NEW_DESIGN_COMPONENT = "requires_new_design_component"

        val EMPTY = BuildFeatureVector()
    }
}
