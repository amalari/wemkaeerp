package com.eventverse.app.routes.dto

import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.pipeline.TenantEntitlementGrants
import com.eventverse.app.domain.pipeline.TenantModuleAvailability
import com.eventverse.app.domain.pipeline.TenantModuleEntitlement
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.pipeline.TenantEntitlementGrantsCodec
import com.eventverse.app.shared.pipeline.TenantModuleCatalogCodec

/**
 * HTTP boundary mapping for the platform-administration API, under `/api/admin`.
 */
object AdminDto {

    /** Reads `{"tier":"ENTERPRISE"}`. */
    fun readTierCode(rawBody: String): String? =
        JsonParser.parseObjectOrNull(rawBody)?.string("tier")?.takeIf { it.isNotBlank() }

    /** Reads a `TenantEntitlementGrantsCodec` document from the PUT entitlement body. */
    fun readEntitlementGrants(rawBody: String): TenantEntitlementGrants? =
        JsonParser.parseObjectOrNull(rawBody)?.let(TenantEntitlementGrantsCodec::decode)

    /**
     * Full admin view of one tenant: identity, current plan, this tenant's own grants on
     * top of that plan, and the resolved module catalogue — the same shape the tenant-facing
     * `GET /api/tenant/pipeline/modules` returns, so an admin sees exactly what the factory
     * itself would see.
     */
    fun tenantAdminViewToJson(
        tenant: Tenant,
        grants: TenantEntitlementGrants,
        entitlement: TenantModuleEntitlement,
        modules: List<TenantModuleAvailability>
    ): String {
        val catalog = TenantModuleCatalogCodec.encodeValue(entitlement, modules)
        val identity = jsonObjectOf(
            "tenantId" to jsonOf(tenant.id.value),
            "slug" to jsonOf(tenant.slug.value),
            "name" to jsonOf(tenant.name.value),
            "status" to jsonOf(tenant.status.name),
            "businessPreset" to jsonOf(tenant.businessPreset.code.value)
        )
        val grantsValue = TenantEntitlementGrantsCodec.encode(grants)

        return JsonValue.Obj(
            linkedMapOf<String, JsonValue>().apply {
                putAll(identity.entries)
                putAll(grantsValue.entries)
                putAll(catalog.entries)
            }
        ).encode()
    }

    fun auditLogToJson(entries: List<AuditLogEntry>): String = jsonArrayOf(
        entries.map { entry ->
            jsonObjectOf(
                "id" to jsonOf(entry.id),
                "actorUserId" to jsonOf(entry.actorUserId),
                "actorRole" to jsonOf(entry.actorRole.name),
                "action" to jsonOf(entry.action.code),
                "summary" to jsonOf(entry.summary),
                "occurredAt" to jsonOf(entry.occurredAt.toString())
            )
        }
    ).encode()
}
