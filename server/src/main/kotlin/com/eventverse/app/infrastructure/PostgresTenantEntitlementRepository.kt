package com.eventverse.app.infrastructure

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

import com.eventverse.app.domain.pipeline.TenantEntitlementGrants
import com.eventverse.app.domain.pipeline.TenantEntitlementRepository
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.TenantModuleEntitlementsTable
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonOf
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * PostgreSQL implementation of [TenantEntitlementRepository].
 */
class PostgresTenantEntitlementRepository : TenantEntitlementRepository {

    override suspend fun findByTenantId(tenantId: TenantId): TenantEntitlementGrants? =
        DatabaseFactory.dbQuery(tenantId) {
            TenantModuleEntitlementsTable.selectAll()
                .where { TenantModuleEntitlementsTable.tenantId eq tenantId.value }
                .map { toGrants(it) }
                .singleOrNull()
        }

    override suspend fun save(
        tenantId: TenantId,
        grants: TenantEntitlementGrants
    ): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            val grantedModulesJson = grants.grantedModules?.let { modules ->
                jsonArrayOf(modules.map { jsonOf(it.name) }).encode()
            }
            val customIdsJson = jsonArrayOf(
                grants.grantedCustomModuleIds.map { jsonOf(it) }
            ).encode()

            val updatedRows = TenantModuleEntitlementsTable.update(
                { TenantModuleEntitlementsTable.tenantId eq tenantId.value }
            ) {
                it[grantedModules] = grantedModulesJson
                it[grantedCustomModuleIds] = customIdsJson
            }

            // Decide insert-vs-update from the update result, so two concurrent grants
            // cannot both observe "absent" and both attempt an insert.
            if (updatedRows == 0) {
                TenantModuleEntitlementsTable.insert {
                    it[TenantModuleEntitlementsTable.tenantId] = tenantId.value
                    it[grantedModules] = grantedModulesJson
                    it[grantedCustomModuleIds] = customIdsJson
                }
            }
        }
    }

    private fun toGrants(row: ResultRow): TenantEntitlementGrants = TenantEntitlementGrants(
        grantedModules = row[TenantModuleEntitlementsTable.grantedModules]
            ?.let { decodeModules(it) },
        grantedCustomModuleIds = decodeStrings(
            row[TenantModuleEntitlementsTable.grantedCustomModuleIds]
        )
    )

    private fun decodeModules(json: String): Set<BusinessModule> =
        decodeStrings(json).mapNotNull { name -> ModuleIdCodec.fromStoredName(name, "tenant_module_entitlements.granted_modules") }.toSet()

    private fun decodeStrings(json: String): Set<String> =
        runCatching { JsonParser.parseArray(json) }
            .getOrDefault(emptyList())
            .filterIsInstance<JsonValue.Str>()
            .map { it.value }
            .toSet()
}
