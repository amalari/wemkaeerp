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

import com.eventverse.app.domain.rbac.BusinessModule

/**
 * One row of the module catalogue as it applies to a specific tenant: what the module is,
 * whether the plan grants it, and whether this tenant currently runs it.
 *
 * Drives both the tenant-facing module picker and the superadmin provisioning view, so
 * neither has to recompute plan rules itself.
 */
data class TenantModuleAvailability(
    val moduleId: String,
    val displayName: String,
    /** The tenant's own name for the module, when it differs from the catalogue default. */
    val tenantDisplayName: String?,
    val archetype: ModuleArchetype,
    val standardModule: BusinessModule?,
    val isCustomPlugin: Boolean,
    /** Present in the tenant's pipeline graph. */
    val isInstalled: Boolean,
    /** Installed and switched on (not bypassed). */
    val isActive: Boolean,
    /** Allowed by the subscription plan. */
    val isGrantedByPlan: Boolean,
    /** Recommended as a starter for the tenant's business model. */
    val isRecommendedForPreset: Boolean,
    val nodeId: String? = null
) {
    /** Can be switched on right now without a plan upgrade. */
    val canBeActivated: Boolean get() = isGrantedByPlan && !isActive

    /** Blocked purely by the subscription plan — the case worth upselling. */
    val requiresPlanUpgrade: Boolean get() = !isGrantedByPlan
}
