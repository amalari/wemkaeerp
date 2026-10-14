package com.eventverse.app.routes

import com.eventverse.app.domain.builder.BriefStatus
import com.eventverse.app.domain.builder.ModuleBriefOutcome
import com.eventverse.app.domain.builder.ModuleBriefService
import com.eventverse.app.infrastructure.builder.BuilderRunRegistry
import com.eventverse.app.plugins.tenantContext
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("module_brief")

/**
 * `POST /api/builder/chat/briefs` (TRD-BLDR-001 §4). Dipasang di dalam `route("/api/builder")`.
 *
 * Body `{"module": "<id>"?}` (kosong = semua modul). Respons `200 {"results":[{moduleId,status,source?}]}`.
 * - Gerbang Builder sama dengan rute chat lain (`MANAGE_BUILDER`, fail-closed 403).
 * - [service] `null` = fitur dimatikan (flag `BUILDER_MODULE_BRIEFS_ENABLED`) → 404, bukan 500.
 * - 400: body bukan objek JSON, atau service menolak modul tak dikenal (`IllegalArgumentException`).
 * - 409: masih ada run chat aktif untuk tenant (brief ditulis ke utas yang sama; tidak boleh bertabrakan).
 *
 * Rute hanya bergantung pada [ModuleBriefService]; isi brief dan idempotensi (FR-7) urusan service.
 */
fun Route.builderModuleBriefRoutes(service: ModuleBriefService?, registry: BuilderRunRegistry) {
    post("/chat/briefs") {
        call.gate() ?: return@post
        if (service == null) {
            call.respond(HttpStatusCode.NotFound, "Fitur brief modul dimatikan")
            return@post
        }
        val raw = call.receiveText()
        val body = if (raw.isBlank()) JsonValue.Obj(emptyMap())
        else runCatching { JsonParser.parse(raw) as? JsonValue.Obj }.getOrNull()
        if (body == null) {
            call.respond(HttpStatusCode.BadRequest, "Body wajib objek JSON {\"module\":\"<kode modul>\"?}")
            return@post
        }
        val module = body.string("module")?.takeIf { it.isNotBlank() }
        val tenantId = call.tenantContext.tenantId
        if (registry.hasActiveRun(tenantId)) {
            call.respond(HttpStatusCode.Conflict, "Masih ada proses yang berjalan untuk project ini; tunggu selesai dulu")
            return@post
        }
        service.generate(tenantId, module).fold(
            onSuccess = { outcomes ->
                outcomes.forEach {
                    log.info("module_brief tenant={} module={} status={} source={}", tenantId.value, it.moduleId, it.status, it.source)
                }
                call.respondText(
                    jsonObjectOf("results" to jsonArrayOf(outcomes.map(::outcomeJson))).encode(),
                    ContentType.Application.Json
                )
            },
            onFailure = { e ->
                if (e is IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, e.message ?: "Modul tidak dikenal")
                } else {
                    log.warn("module_brief tenant={} module={} gagal: {}", tenantId.value, module, e.message)
                    call.respond(HttpStatusCode.InternalServerError, "Gagal membuat brief modul: ${e.message}")
                }
            }
        )
    }
}

private fun outcomeJson(o: ModuleBriefOutcome): JsonValue = jsonObjectOf(
    "moduleId" to jsonOf(o.moduleId),
    "status" to jsonOf(
        when (o.status) {
            BriefStatus.CREATED -> "created"
            BriefStatus.FRESH -> "fresh"
            BriefStatus.SKIPPED -> "skipped"
        }
    ),
    "source" to (o.source?.let { jsonOf(it.name) } ?: JsonValue.Null)
)
