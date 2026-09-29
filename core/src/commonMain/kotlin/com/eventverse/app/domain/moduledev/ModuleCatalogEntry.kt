package com.eventverse.app.domain.moduledev

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.ModuleIdCodec

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * A sellable module.
 *
 * The built-in catalogue lives in the [BusinessModule] enum, which cannot grow without a deploy
 * and therefore cannot describe a module commissioned by one factory. This entity is the same
 * catalogue as data, keyed by [moduleId] — the string already used by `CustomPipelineNode.moduleId`
 * and `granted_custom_module_ids`, so existing pipelines join to it without migration.
 */
data class ModuleCatalogEntry(
    val id: ModuleCatalogEntryId,
    val moduleId: String,
    val archetypeCode: String,
    val displayName: String,
    val description: String = "",
    val categoryCode: String? = null,
    val scopeCapabilityCode: String = "GLOBAL_ONLY",
    val stockOwnershipCode: String? = null,
    val costingBehaviorCode: String? = null,
    val acceptedInputTypes: List<String> = emptyList(),
    val producedOutputType: String? = null,
    val isCustomPlugin: Boolean = false,
    /**
     * The tenant a plugin was first commissioned for; null for core product.
     *
     * Not an exclusivity claim. Once built, a plugin can be offered to other factories — which is
     * what makes spreading its build cost across several tenants possible in the first place.
     */
    val originTenantId: TenantId? = null,
    val lifecycleStatus: ModuleLifecycleStatus = ModuleLifecycleStatus.PLANNED,
    val complexityTier: ComplexityTier? = null,
    /** Subscription price per tenant per month for running this module. */
    val baseMonthlyPriceIdr: MoneyIdr? = null,
    val releasedAt: Instant? = null
) {
    init {
        require(moduleId.isNotBlank()) { "moduleId cannot be blank" }
        require(moduleId.length <= 64) { "moduleId must be at most 64 characters" }
        require(displayName.isNotBlank()) { "displayName cannot be blank" }
        require(archetypeCode.isNotBlank()) { "archetypeCode cannot be blank" }
    }

    /** The built-in module this entry mirrors, or null when it is a tenant plugin. */
    val standardModule: BusinessModule?
        get() = ModuleIdCodec.standardOrNull(moduleId)

    /**
     * Whether this entry can appear on a bill.
     *
     * A module still in development has no price to charge, and a priced-but-unreleased module
     * on an invoice is a support conversation rather than revenue.
     */
    val isBillable: Boolean
        get() = lifecycleStatus.isBillable && baseMonthlyPriceIdr != null

    fun release(at: Instant): ModuleCatalogEntry =
        copy(lifecycleStatus = ModuleLifecycleStatus.RELEASED, releasedAt = releasedAt ?: at)

    fun deprecate(): ModuleCatalogEntry =
        copy(lifecycleStatus = ModuleLifecycleStatus.DEPRECATED)

    /**
     * Sets the catalogue list price.
     *
     * Changing it affects what NEW subscriptions are quoted. It must not change what existing
     * tenants are billed — that requires the price to have been locked per subscription when they
     * signed up, which is why billing cannot be built without such a record.
     */
    fun withListPrice(price: MoneyIdr): ModuleCatalogEntry = copy(baseMonthlyPriceIdr = price)
}
