package com.eventverse.app.routes

import com.eventverse.app.domain.crm.LeadScope
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.pack.resolveModule
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModules
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.rbac.isOperational
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.relation.RelationTargetRegistry
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.slf4j.LoggerFactory

/** Batas opsi (TRD-FIELD-001 NFR): lookup mengetik → banyak query kecil, jadi dipatok 20. */
internal const val RELATION_OPTION_LIMIT = 20

private val log = LoggerFactory.getLogger("RelationRoutes")

/**
 * Route pencarian opsi rujukan tipe field `RELATION` (C7, TRD-FIELD-001 FR-4) — fail-closed.
 *
 * `GET /api/tenant/relation-options?module={targetModuleCode}&entity={entityId}&q={kueri}`
 *
 * **Urutan gerbang tidak boleh diubah** (pola `SpecRoutesWriter.authorized`):
 * 1. konteks tenant (404 bila tidak ada);
 * 2. modul target dikenal proses **dan** operasional (403 bila tidak — governance/foundation bukan target, R1);
 * 3. `requireModuleAccess(targetModule, VIEW)` (403) — **sebelum** `entity`/`q` dibaca (Kontrak 7);
 * 4. modul target ada di pack tenant lewat `resolveModule` — modul sendiri pack atau modul bersama R1 (404);
 * 5. **baru** `entity`/`q` dibaca dan query dijalankan (`LIMIT` [RELATION_OPTION_LIMIT]).
 *
 * Gate di **modul target** (bukan modul pemegang) adalah inti keputusan #2 — tanpa itu, route ini jadi celah
 * baca lintas modul. Respons `[{"id","label"}]`, label v1 = field teks pertama baris target, fallback id.
 */
fun Route.relationRoutes(
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    employeeRepository: EmployeeRepository,
    registry: RelationTargetRegistry
) {
    get("/api/tenant/relation-options") {
        val tenant = call.tenantContextOrNull ?: run {
            call.respond(HttpStatusCode.NotFound, "No tenant context found")
            return@get
        }

        // 2. Modul target dikenal proses ini? Modul tak dikenal = keputusan RBAC tak bisa dihitung = 403 (fail-closed).
        val target = BusinessModules.fromCode(call.request.queryParameters["module"])
        if (target == null || !target.isOperational) {
            call.rejectRelation(HttpStatusCode.Forbidden, "Modul target tidak dikenal atau tidak dapat dirujuk sebagai rujukan.")
            return@get
        }

        // 3. RBAC DULU — tanpa wewenang VIEW atas modul TARGET = 403 sebelum parameter apa pun dibaca.
        val decision = call.moduleDecision(target, tenant, roleRepository, moduleAssignmentRepository)
        if (!call.requireModuleAccess(target, decision, AccessLevel.VIEW)) {
            log.warn(
                "relation-options ditolak 403: pemanggil '{}' tanpa VIEW atas modul target '{}'",
                call.callerPrincipalOrNull?.userId ?: "anon", target.value
            )
            return@get
        }

        // 4. Yang lolos RBAC tetap harus berada di tenant yang packnya memuat modul target (sendiri atau dirujuk R1).
        if (tenant.pack.resolveModule(target) == null) {
            call.respond(HttpStatusCode.NotFound, "Modul tidak tersedia untuk tenant ini.")
            return@get
        }

        // 5. Baru sekarang parameter dibaca.
        val entity = call.request.queryParameters["entity"]
        if (entity.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Query 'entity' wajib diisi")
            return@get
        }
        val query = call.request.queryParameters["q"].orEmpty().trim()

        // 5b. Jangkauan data pemanggil atas modul TARGET (bukan modul pemegang) — tanpa ini,
        //     pengguna dengan VIEW tapi OWN_DATA_ONLY/SUBORDINATE_DATA atas modul hierarkis (CRM)
        //     akan membaca seluruh record tenant. `null` (ALL_TENANT_DATA) = tanpa predicate.
        val scope = decision.config.sanitizeFor(target).scope
        val reachableOwnerIds = if (scope == DataScope.ALL_TENANT_DATA) {
            null
        } else {
            call.relationOwnerReach(tenant, scope, employeeRepository)
        }

        val options = registry.sourceFor(target.value)
            ?.options(tenant.tenantId, entity, reachableOwnerIds, query, RELATION_OPTION_LIMIT)
            .orEmpty()

        call.respondText(
            text = jsonArrayOf(options.map { jsonObjectOf("id" to jsonOf(it.id), "label" to jsonOf(it.label)) }).encode(),
            contentType = ContentType.Application.Json
        )
    }
}

/** 403 + WARN tanpa membocorkan isi tenant lain — hanya modul target yang disebut. */
private suspend fun ApplicationCall.rejectRelation(status: HttpStatusCode, message: String) {
    log.warn(
        "relation-options ditolak {}: pemanggil '{}' modul target '{}'",
        status.value, callerPrincipalOrNull?.userId ?: "anon", request.queryParameters["module"] ?: "-"
    )
    respond(status, message)
}

/**
 * Jangkauan data pemanggil atas modul target (pola `crmOwnerReach`): `null` = `ALL_TENANT_DATA`,
 * selain itu himpunan pemilik yang boleh dibaca. Dipakai supaya route opsi menghormati `DataScope`
 * modul **target**, bukan hanya status login pemanggil.
 */
private suspend fun ApplicationCall.relationOwnerReach(
    tenant: TenantContext,
    scope: DataScope,
    employeeRepository: EmployeeRepository
): Set<OrgNodeId>? {
    val principal = callerPrincipalOrNull
    val viewerEmployeeId = principal?.email
        ?.let { email -> employeeRepository.findByEmail(tenant.tenantId, email) }
        ?.id
    val employees = employeeRepository.findAllByTenant(tenant.tenantId)
    return LeadScope.reachableOwnerIds(scope, employees, viewerEmployeeId, principal?.departmentId)
}
