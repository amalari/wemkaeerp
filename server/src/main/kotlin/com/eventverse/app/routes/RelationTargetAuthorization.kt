package com.eventverse.app.routes

import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.resolveModule
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModules
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.rbac.isOperational
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.callerPrincipalOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("RelationTargetAuthorization")

/** Hasil gerbang target rujukan: modul target yang sah + jangkauan data pemanggil atasnya (`null` = seluruh tenant). */
internal class RelationTargetAccess(val module: ModuleId, val reachableOwnerIds: Set<OrgNodeId>?)

/**
 * Gerbang **satu-satunya** untuk merujuk record modul target (TRD-FIELD-004 FR-3.1/3.4), dipakai route opsi
 * (`RelationRoutes`) dan guard tulis CRM (`rejectMissingRelationTargets`) — bukan salinan ketiga:
 *
 * 1. modul target dikenal proses **dan** operasional (403 bila tidak — governance/foundation bukan target, R1);
 * 2. `requireModuleAccess(target, VIEW)` atas modul **target**, bukan modul pemegang (403) — tanpa ini jalur
 *    rujukan jadi celah baca/oracle lintas modul;
 * 3. modul target ada di pack tenant lewat `resolveModule` — modul sendiri pack atau modul bersama R1 (404);
 * 4. jangkauan `DataScope` pemanggil atas modul target dihitung SEKALI dan diteruskan ke pemeriksaan/pencarian.
 *
 * Menjawab sendiri (403/404) dan mengembalikan `null` bila ditolak; pemanggil cukup `?: return`. [surface] hanya
 * label log (mis. `relation-options`). Dipanggil SEBELUM parameter lain pemanggil dibaca (Kontrak 7).
 */
internal suspend fun ApplicationCall.authorizeRelationTarget(
    tenant: TenantContext,
    targetModuleCode: String?,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    employeeRepository: EmployeeRepository,
    surface: String
): RelationTargetAccess? {
    val target = BusinessModules.fromCode(targetModuleCode)
    if (target == null || !target.isOperational) {
        log.warn("{} ditolak 403: pemanggil '{}' modul target '{}' tak dikenal/bukan operasional", surface, callerId(), targetModuleCode ?: "-")
        respond(HttpStatusCode.Forbidden, "Modul target tidak dikenal atau tidak dapat dirujuk sebagai rujukan.")
        return null
    }
    val decision = moduleDecision(target, tenant, roleRepository, moduleAssignmentRepository)
    if (!requireModuleAccess(target, decision, AccessLevel.VIEW)) {
        log.warn("{} ditolak 403: pemanggil '{}' tanpa VIEW atas modul target '{}'", surface, callerId(), target.value)
        return null
    }
    if (tenant.pack.resolveModule(target) == null) {
        respond(HttpStatusCode.NotFound, "Modul tidak tersedia untuk tenant ini.")
        return null
    }
    return RelationTargetAccess(target, callerOwnerReach(tenant, decision.config.sanitizeFor(target).scope, employeeRepository))
}

private fun ApplicationCall.callerId(): String = callerPrincipalOrNull?.userId ?: "anon"
