package com.eventverse.app.routes

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

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.crm.LeadScope
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessDecisionEngine
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.callerPrincipalOrNull
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * Authority guard for CRM Leads routes (`GarmentModules.CRM_SALES`), modeled on
 * [OrgChartAccessGuard.kt] — including its "already responded 403" convention: a caller
 * returns `false` and this file has already written the response, so a handler just
 * `return@get`/`return@post`s.
 *
 * ONE DELIBERATE DEVIATION from that precedent: [orgChartDecision] returns `null` ("cannot
 * be computed") and `requireOrgChartAccess` then ALLOWS, to preserve legacy route wiring that
 * predates the guard. CRM routes are brand new — there is no legacy behaviour to preserve —
 * so here `null` DENIES, and every repository this file needs is a non-nullable constructor
 * parameter of `crmRoutes(...)`. Do not copy the nullable-permissive fallback into a new
 * surface; it exists in the org-chart guard only as a migration compromise.
 */

internal suspend fun ApplicationCall.crmDecision(
    tenant: TenantContext,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository
): AccessDecision {
    val principal = callerPrincipalOrNull

    // No identity at all: treat as NONE, never as unrestricted. Denying by default is the
    // only safe reading of "we could not compute an authority decision" on a brand-new route.
    if (principal == null) {
        val none = ModuleAccessConfig()
        return AccessDecision(config = none, source = AccessSource.NONE, fromRole = none, fromDepartment = none)
    }

    val role = principal.customRoleId
        ?.let { runCatching { RoleId(it) }.getOrNull() }
        ?.let { roleRepository.findById(tenant.tenantId, it) }

    val persona = TestingPersona(
        userId = principal.userId.ifBlank { "unknown" },
        name = principal.email ?: principal.userId.ifBlank { "unknown" },
        tenantId = tenant.tenantId,
        tenantSlug = tenant.slug.value,
        departmentId = principal.departmentId,
        departmentName = "",
        roleId = role?.id,
        roleTitle = role?.name ?: "",
        // Owner pabrik (Role platform TENANT_ADMIN — akun seed `usr-owner-001`) melewati matriks
        // wewenang sama seperti superadmin: ia pemilik tenant. Tanpa bypass ini seluruh modul CRM
        // terkunci 403 justru untuk akun yang paling berhak, karena TENANT_ADMIN tidak punya
        // jabatan tenant (custom role) maupun penugasan departemen. Bypass tetap mensyaratkan
        // `role == null` agar bila owner diberi jabatan tenant, matriks jabatan itu yang berlaku.
        isOwnerOrSuperAdmin = (principal.isPlatformSuperadmin || principal.role == Role.TENANT_ADMIN) && role == null,
        isPlatformSuperAdmin = principal.isPlatformSuperadmin && role == null
    )

    val assignments = moduleAssignmentRepository.findAllByTenant(tenant.tenantId)
    return AccessDecisionEngine.explain(
        persona = persona,
        module = GarmentModules.CRM_SALES,
        role = role,
        assignments = assignments[GarmentModules.CRM_SALES].orEmpty()
    )
}

/**
 * Ensures the caller has at least [required] authority over CRM Leads.
 *
 * Unlike [OrgChartAccessGuard.requireOrgChartAccess], there is no "unknown -> allow"
 * fallback here: [decision] is always computed by [crmDecision], so a level below
 * [required] always means an actual 403, never a legacy compatibility bypass.
 */
internal suspend fun ApplicationCall.requireCrmAccess(decision: AccessDecision, required: AccessLevel): Boolean {
    if (decision.config.level.isAtLeast(required)) return true

    respond(
        HttpStatusCode.Forbidden,
        "Butuh wewenang ${required.displayName} atas modul " +
            "\"${GarmentModules.CRM_SALES.displayName}\"; wewenang Anda saat ini " +
            "${decision.config.level.displayName} (${decision.source.label})."
    )
    return false
}

/**
 * The set of employee ids whose leads this caller may read/write under `DataScope`, or
 * `null` for an unrestricted (`ALL_TENANT_DATA`) caller. Computed the SAME way as
 * `OrgChartDataReach` — checking `.level` is a precondition of the caller
 * ([requireCrmAccess] must have already passed) so this function never has to guard against
 * `ModuleAccessConfig`'s "scope defaults to ALL_TENANT_DATA even when level is NONE" trap
 * itself; it trusts that the level gate already ran.
 */
internal suspend fun ApplicationCall.crmOwnerReach(
    tenant: TenantContext,
    decision: AccessDecision,
    employeeRepository: EmployeeRepository
): Set<OrgNodeId>? {
    val scope = decision.config.sanitizeFor(GarmentModules.CRM_SALES).scope
    val principal = callerPrincipalOrNull

    val viewerDepartmentId = principal?.departmentId
    val viewerEmployeeId = principal?.email
        ?.let { email -> employeeRepository.findByEmail(tenant.tenantId, email) }
        ?.id

    val employees = employeeRepository.findAllByTenant(tenant.tenantId)
    return LeadScope.reachableOwnerIds(scope, employees, viewerEmployeeId, viewerDepartmentId)
}

/** Rejects with 403 when [ownerEmployeeId] falls outside [reach] — the write-side mirror of the read filter. */
internal suspend fun ApplicationCall.requireReachableOwner(reach: Set<OrgNodeId>?, ownerEmployeeId: OrgNodeId?): Boolean {
    val allowed = when {
        reach == null -> true
        ownerEmployeeId == null -> false
        else -> ownerEmployeeId in reach
    }
    if (allowed) return true

    respond(
        HttpStatusCode.Forbidden,
        "PIC yang dipilih berada di luar jangkauan data Anda untuk modul " +
            "\"${GarmentModules.CRM_SALES.displayName}\"."
    )
    return false
}
