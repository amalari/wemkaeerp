package com.eventverse.app.shared.pipeline

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

import com.eventverse.app.domain.pack.ModuleIdCodec

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.pipeline.TenantModuleAvailability
import com.eventverse.app.domain.pipeline.TenantModuleCatalogSnapshot
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Wire format for the per-tenant module catalogue, shared by the Ktor server that produces
 * it and the Compose client that consumes it.
 */
object TenantModuleCatalogCodec {

    fun encode(
        entitlement: TenantModuleEntitlement,
        modules: List<TenantModuleAvailability>
    ): String = encodeValue(entitlement, modules).encode()

    /**
     * As [encode], but returns the structured value rather than text — for a caller (the
     * admin API) that needs to merge this catalogue into a larger response object without
     * an encode-then-reparse round trip.
     */
    fun encodeValue(
        entitlement: TenantModuleEntitlement,
        modules: List<TenantModuleAvailability>
    ): JsonValue.Obj = jsonObjectOf(
        "tier" to jsonOf(entitlement.tier.name),
        "maxActiveModules" to jsonOf(entitlement.maxActiveModules),
        "allowsCustomPlugins" to jsonOf(entitlement.allowsCustomPlugins),
        "activeModuleCount" to jsonOf(modules.count { it.isActive }),
        "modules" to jsonArrayOf(modules.map(::encodeModule))
    )

    fun decode(rawJson: String): TenantModuleCatalogSnapshot {
        val root = JsonParser.parseObject(rawJson)
        return TenantModuleCatalogSnapshot(
            tier = root.string("tier")
                ?.let { name -> SubscriptionTier.entries.firstOrNull { it.name == name } }
                ?: SubscriptionTier.PRO,
            maxActiveModules = root.int("maxActiveModules") ?: 0,
            allowsCustomPlugins = root.boolean("allowsCustomPlugins") ?: false,
            modules = root.objectArray("modules").mapNotNull(::decodeModule)
        )
    }

    private fun encodeModule(module: TenantModuleAvailability): JsonValue = jsonObjectOf(
        "moduleId" to jsonOf(module.moduleId),
        "displayName" to jsonOf(module.displayName),
        "tenantDisplayName" to jsonOf(module.tenantDisplayName),
        "archetype" to jsonOf(module.archetype.code),
        "isCustomPlugin" to jsonOf(module.isCustomPlugin),
        "isInstalled" to jsonOf(module.isInstalled),
        "isActive" to jsonOf(module.isActive),
        "isGrantedByPlan" to jsonOf(module.isGrantedByPlan),
        "isRecommendedForPreset" to jsonOf(module.isRecommendedForPreset),
        "nodeId" to jsonOf(module.nodeId)
    )

    private fun decodeModule(module: JsonValue.Obj): TenantModuleAvailability? {
        val moduleId = module.string("moduleId")?.takeIf { it.isNotBlank() } ?: return null
        val archetype = GarmentSlots.fromCode(module.string("archetype"))
            ?: GarmentSlots.forModuleCode(moduleId)
        return TenantModuleAvailability(
            moduleId = moduleId,
            displayName = module.string("displayName") ?: moduleId,
            tenantDisplayName = module.string("tenantDisplayName"),
            archetype = archetype,
            standardModule = ModuleIdCodec.standardOrNull(moduleId),
            isCustomPlugin = module.boolean("isCustomPlugin") ?: false,
            isInstalled = module.boolean("isInstalled") ?: false,
            isActive = module.boolean("isActive") ?: false,
            isGrantedByPlan = module.boolean("isGrantedByPlan") ?: false,
            isRecommendedForPreset = module.boolean("isRecommendedForPreset") ?: false,
            nodeId = module.string("nodeId")
        )
    }
}
