package com.eventverse.app.infrastructure.tables

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

import org.jetbrains.exposed.sql.Table

/**
 * Per-tenant module grants that the subscription tier alone cannot express — most
 * importantly which custom plugin modules a specific factory is licensed to run.
 */
object TenantModuleEntitlementsTable : Table("tenant_module_entitlements") {
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)

    /**
     * JSON array of [com.eventverse.app.domain.rbac.BusinessModule] names, or `null` to mean
     * "whatever the plan tier grants by default".
     */
    val grantedModules = jsonbText("granted_modules").nullable()

    /** JSON array of custom plugin module ids provisioned to this tenant. */
    val grantedCustomModuleIds = jsonbText("granted_custom_module_ids").default("[]")

    override val primaryKey = PrimaryKey(tenantId)
}
