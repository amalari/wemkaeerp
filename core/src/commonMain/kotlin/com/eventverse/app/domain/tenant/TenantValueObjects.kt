package com.eventverse.app.domain.tenant

import kotlin.jvm.JvmInline

@JvmInline
value class TenantId(val value: String) {
    init {
        require(value.isNotBlank()) { "TenantId cannot be blank" }
        require(value.length in 3..64) { "TenantId must be between 3 and 64 characters" }
    }
}

@JvmInline
value class TenantSlug(val value: String) {
    init {
        require(value.isNotBlank()) { "TenantSlug cannot be blank" }
        require(value.length in 3..30) { "TenantSlug must be between 3 and 30 characters" }
        require(SLUG_REGEX.matches(value)) { 
            "TenantSlug must consist of lowercase alphanumeric characters and hyphens, and cannot start or end with a hyphen: $value" 
        }
        require(!FORBIDDEN_SLUGS.contains(value)) {
            "TenantSlug '$value' is a reserved system keyword and cannot be used"
        }
    }

    companion object {
        private val SLUG_REGEX = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        val FORBIDDEN_SLUGS = setOf(
            "admin", "api", "app", "auth", "billing", "dashboard", 
            "mail", "portal", "root", "superadmin", "system", "wemade", "www"
        )
    }
}

@JvmInline
value class TenantName(val value: String) {
    init {
        require(value.isNotBlank()) { "TenantName cannot be blank" }
        require(value.length in 2..100) { "TenantName must be between 2 and 100 characters" }
    }
}

enum class TenantStatus {
    TRIAL,
    ACTIVE,
    DUE,
    PAST_DUE,
    SUSPENDED,
    ARCHIVED;

    val isAccessible: Boolean
        get() = this == TRIAL || this == ACTIVE || this == DUE || this == PAST_DUE
}

enum class SubscriptionTier(
    val maxActiveMachines: Int,
    val maxManagementUsers: Int,
    val allowUnlimitedOperators: Boolean,
    val allowAdvancedGantt: Boolean,
    val allowQrDefectTracking: Boolean,
    val storageLimitMb: Long,
    /**
     * How many operational modules a tenant on this plan may keep active in its pipeline.
     * This is what makes the plan actually gate the module catalogue instead of only
     * counting machines.
     */
    val maxActivePipelineModules: Int,
    /** Whether the plan may install custom / third-party plugin modules. */
    val allowCustomPluginModules: Boolean
) {
    STARTER(
        maxActiveMachines = 5,
        maxManagementUsers = 2,
        allowUnlimitedOperators = false,
        allowAdvancedGantt = false,
        allowQrDefectTracking = false,
        storageLimitMb = 2048, // 2 GB
        maxActivePipelineModules = 5,
        allowCustomPluginModules = false
    ),
    PRO(
        maxActiveMachines = 15, // Optimal for 10-machine convection setup
        maxManagementUsers = 5,
        allowUnlimitedOperators = true,
        allowAdvancedGantt = true,
        allowQrDefectTracking = true,
        storageLimitMb = 15360, // 15 GB
        maxActivePipelineModules = 9, // every built-in module
        allowCustomPluginModules = false
    ),
    ENTERPRISE(
        maxActiveMachines = Int.MAX_VALUE,
        maxManagementUsers = Int.MAX_VALUE,
        allowUnlimitedOperators = true,
        allowAdvancedGantt = true,
        allowQrDefectTracking = true,
        storageLimitMb = 102400, // 100 GB
        maxActivePipelineModules = Int.MAX_VALUE,
        allowCustomPluginModules = true
    );
}
