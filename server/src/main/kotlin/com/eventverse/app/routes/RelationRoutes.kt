package com.eventverse.app.routes

import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.relation.RelationTargetRegistry
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Batas opsi (TRD-FIELD-001 NFR): lookup mengetik → banyak query kecil, jadi dipatok 20. */
internal const val RELATION_OPTION_LIMIT = 20

/**
 * Route pencarian opsi rujukan tipe field `RELATION` (C7, TRD-FIELD-001 FR-4) — fail-closed.
 *
 * `GET /api/tenant/relation-options?module={targetModuleCode}&entity={entityId}&q={kueri}`
 *
 * **Urutan gerbang tidak boleh diubah** (pola `SpecRoutesWriter.authorized`):
 * 1. konteks tenant (404 bila tidak ada);
 * 2-4. gerbang target bersama [authorizeRelationTarget] (modul dikenal+operasional 403, VIEW atas modul target 403
 *    **sebelum** `entity`/`q` dibaca (Kontrak 7), modul ada di pack tenant 404, jangkauan data modul target);
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

        // 2-4 + 5b. Gerbang target bersama (dikenal+operasional 403, VIEW modul TARGET 403, ada di pack 404,
        //    jangkauan data modul target) — satu fungsi dengan guard tulis CRM (TRD-FIELD-004 FR-3.4).
        val access = call.authorizeRelationTarget(
            tenant, call.request.queryParameters["module"], roleRepository, moduleAssignmentRepository, employeeRepository, "relation-options"
        ) ?: return@get

        // 5. Baru sekarang parameter dibaca.
        val entity = call.request.queryParameters["entity"]
        if (entity.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Query 'entity' wajib diisi")
            return@get
        }
        val query = call.request.queryParameters["q"].orEmpty().trim()

        val options = registry.sourceFor(access.module.value)
            ?.options(tenant.tenantId, entity, access.reachableOwnerIds, query, RELATION_OPTION_LIMIT)
            .orEmpty()

        call.respondText(
            text = jsonArrayOf(options.map { jsonObjectOf("id" to jsonOf(it.id), "label" to jsonOf(it.label)) }).encode(),
            contentType = ContentType.Application.Json
        )
    }
}
