package com.eventverse.app.routes

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
): AccessDecision {
    val principal = callerPrincipalOrNull
    if (principal == null) {
        val none = ModuleAccessConfig()
        return AccessDecision(config = none, source = AccessSource.NONE, fromRole = none, fromDepartment = none)
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

    return AccessDecisionEngine.explain(
        persona = persona,
        module = module,
        role = role,
        assignments = moduleAssignmentRepository.findAllByTenant(tenant.tenantId)[module].orEmpty()
    )
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
