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

import com.eventverse.app.domain.pipeline.TenantEntitlementGrants
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Wire format for [TenantEntitlementGrants] — the platform-side "which modules can this
 * tenant run" decision, submitted by a superadmin and echoed back in admin responses.
 */
object TenantEntitlementGrantsCodec {

    private const val KEY_GRANTED_MODULES = "grantedModules"
    private const val KEY_GRANTED_CUSTOM_MODULE_IDS = "grantedCustomModuleIds"

    fun encode(grants: TenantEntitlementGrants): JsonValue.Obj = jsonObjectOf(
        KEY_GRANTED_MODULES to (grants.grantedModules
            ?.let { modules -> jsonArrayOf(modules.map { jsonOf(it.name) }) }
            ?: JsonValue.Null),
        KEY_GRANTED_CUSTOM_MODULE_IDS to jsonArrayOf(
            grants.grantedCustomModuleIds.map { jsonOf(it) }
        )
    )

    /**
     * Decodes a grants document. A missing or `null` [KEY_GRANTED_MODULES] means "every
     * built-in module the plan tier grants by default" — the normal case — rather than
     * "no modules", which an absent key would mean under most JSON conventions.
     */
    fun decode(root: JsonValue.Obj): TenantEntitlementGrants = TenantEntitlementGrants(
        grantedModules = if (root[KEY_GRANTED_MODULES] is JsonValue.Arr) {
            root.stringArray(KEY_GRANTED_MODULES)
                .mapNotNull { name -> ModuleIdCodec.fromStoredName(name, "entitlement.grantedModules") }
                .toSet()
        } else {
            null
        },
        grantedCustomModuleIds = root.stringArray(KEY_GRANTED_CUSTOM_MODULE_IDS).toSet()
    )

    fun decode(rawJson: String): TenantEntitlementGrants = decode(JsonParser.parseObject(rawJson))
}
