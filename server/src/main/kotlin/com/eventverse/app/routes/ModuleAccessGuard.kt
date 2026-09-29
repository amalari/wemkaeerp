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

import com.eventverse.app.domain.rbac.BusinessModules

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessDecisionEngine
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.grantedModulesOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/**
 * Gerbang wewenang generik untuk modul `GLOBAL_ONLY` baru.
 *
 * [CrmAccessGuard], [OrgChartAccessGuard], dan [FactoryFlowAccessGuard] adalah tiga salinan
 * dari perhitungan persona yang sama, masing-masing terkunci pada satu modul. Modul keempat
 * tidak menambah salinan keempat (Aturan Tiga Kali) — ia memakai versi ini, yang modulnya
 * dijadikan parameter. Ketiga guard lama belum dipindahkan ke sini; itu refactor tersendiri.
 *
 * Konvensinya sama dengan CRM: tanpa identitas berarti `NONE` (tolak), bukan "boleh semua".
 */
internal suspend fun ApplicationCall.moduleDecision(
    module: BusinessModule,
    tenant: TenantContext,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository
): AccessDecision = callerDecisions(tenant, roleRepository, moduleAssignmentRepository, listOf(module)).getValue(module)

/**
 * Keputusan wewenang pemanggil untuk [modules] — satu kali membangun persona & jabatan, dipakai gerbang (satu modul)
 * maupun `GET /api/tenant/me/access` (semua modul). Satu jalur perhitungan: menu klien dan gerbang server mustahil
 * berbeda pendapat.
 */
internal suspend fun ApplicationCall.callerDecisions(
    tenant: TenantContext,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    modules: List<BusinessModule> = BusinessModules.entries
): Map<BusinessModule, AccessDecision> {
    val principal = callerPrincipalOrNull
    if (principal == null) {
        val none = ModuleAccessConfig()
        return modules.associateWith { AccessDecision(config = none, source = AccessSource.NONE, fromRole = none, fromDepartment = none) }
    }

    val role = principal.customRoleId
        ?.let { runCatching { RoleId(it) }.getOrNull() }
        ?.let { roleRepository.findById(tenant.tenantId, it) }

    val persona = com.eventverse.app.domain.rbac.TestingPersona(
        userId = principal.userId.ifBlank { "unknown" },
        name = principal.email ?: principal.userId.ifBlank { "unknown" },
        tenantId = tenant.tenantId,
        tenantSlug = tenant.slug.value,
        departmentId = principal.departmentId,
        departmentName = "",
        roleId = role?.id,
        roleTitle = role?.name ?: "",
        // Owner pabrik tanpa jabatan tenant melewati matriks — alasan lengkapnya di CrmAccessGuard.
        isOwnerOrSuperAdmin = (principal.isPlatformSuperadmin || principal.role == Role.TENANT_ADMIN) && role == null,
        isPlatformSuperAdmin = principal.isPlatformSuperadmin && role == null
    )

    // Query penugasan dilewati bila hasilnya pasti tidak dipakai (keputusan identik, satu query lebih sedikit):
    // - owner/superadmin selalu MANAGE (penugasan hanya mengisi penjelasan `fromDepartment`, tak dibaca server);
    // - tanpa divisi, `resolveDepartmentAccess` tidak pernah mencocokkan penugasan apa pun.
    val assignments = if (persona.isOwnerOrSuperAdmin || persona.departmentId == null) emptyMap()
        else moduleAssignmentRepository.findAllByTenant(tenant.tenantId)

    return modules.associateWith { module ->
        AccessDecisionEngine.explain(
            persona = persona,
            module = module,
            role = role,
            assignments = assignments[module].orEmpty(),
            grantedModules = grantedModulesOrNull
        )
    }
}

/** `false` berarti 403 sudah dikirim; handler cukup `return@…`. */
internal suspend fun ApplicationCall.requireModuleAccess(
    module: BusinessModule,
    decision: AccessDecision,
    required: AccessLevel
): Boolean {
    if (decision.config.level.isAtLeast(required)) return true

    respond(
        HttpStatusCode.Forbidden,
        "Butuh wewenang ${required.displayName} atas modul \"${module.displayName}\"; " +
            "wewenang Anda saat ini ${decision.config.level.displayName} (${decision.source.label})."
    )
    return false
}
