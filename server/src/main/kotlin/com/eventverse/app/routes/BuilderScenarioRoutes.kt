package com.eventverse.app.routes

import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.domain.discovery.scenario.ScenarioIssue
import com.eventverse.app.domain.discovery.scenario.ScenarioService
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.builder.BuilderRunRegistry
import com.eventverse.app.plugins.tenantContext
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.discovery.ScenarioSketchCodec
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
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("scenario")

/**
 * `POST /api/builder/draft/scenarios/propose` dan `.../confirm` (TRD-BLDR-002 §4). Dipasang di dalam `route("/api/builder")`.
 * Skenario tidak punya GET sendiri: ikut dokumen draf (`GET /api/builder/draft`).
 *
 * - Gerbang Builder sama dengan rute lain (`MANAGE_BUILDER`, fail-closed 403); [service] `null` (flag mati) → 404, SETELAH gerbang.
 * - 409 bila ada run chat aktif untuk tenant. 400 body rusak / `id` kosong.
 * - confirm: galat validasi → 422 `{"error","issues":[{path,message}]}`; `IllegalArgumentException` lain = id tak ada → 404.
 *   ASUMSI (belum final dengan Track A): galat validasi = [ScenarioValidationException] (didefinisikan di sini),
 *   [DiscoveryDraftDecodeException], atau `IllegalArgumentException` yang pesannya memuat path (`steps[n]`, `.carries[`,
 *   `.reworkTo`, `.screenId`) — lihat [issuesOf].
 * - Log terstruktur tanpa isi narasi/PII.
 */
fun Route.builderScenarioRoutes(service: ScenarioService?, registry: BuilderRunRegistry) {
    post("/draft/scenarios/propose") {
        val tenantId = guard(service, registry) ?: return@post
        val svc = service ?: return@post
        val raw = call.receiveText()
        if (raw.isNotBlank() && runCatching { JsonParser.parse(raw) as? JsonValue.Obj }.getOrNull() == null) {
            call.respond(HttpStatusCode.BadRequest, "Body wajib objek JSON {}")
            return@post
        }
        svc.propose(tenantId).fold(
            onSuccess = { list ->
                val sources = list.map { it.source.name }.distinct().joinToString("|").ifEmpty { "-" }
                log.info("scenario_propose tenant={} source={} count={} rejected={}", tenantId.value, sources, list.size, 0)
                call.respondText(jsonObjectOf("scenarios" to ScenarioSketchCodec.encode(list)).encode(), ContentType.Application.Json)
            },
            onFailure = { e ->
                log.warn("scenario_propose tenant={} source=- count=0 rejected=1 gagal={}", tenantId.value, e::class.simpleName)
                call.respond(HttpStatusCode.InternalServerError, "Gagal mengusulkan skenario: ${e.message}")
            }
        )
    }

    post("/draft/scenarios/confirm") {
        val tenantId = guard(service, registry) ?: return@post
        val svc = service ?: return@post
        val id = runCatching { (JsonParser.parse(call.receiveText()) as? JsonValue.Obj)?.string("id")?.takeIf { it.isNotBlank() } }.getOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, "Body wajib {\"id\":\"<id skenario>\"}")
            return@post
        }
        svc.confirm(tenantId, ScenarioId(id)).fold(
            onSuccess = { sc ->
                log.info("scenario_confirm tenant={} id={} ok=true", tenantId.value, id)
                val encoded = ScenarioSketchCodec.encode(listOf(sc)).items.first()
                call.respondText(jsonObjectOf("scenario" to encoded).encode(), ContentType.Application.Json)
            },
            onFailure = { e ->
                log.info("scenario_confirm tenant={} id={} ok=false", tenantId.value, id)
                val issues = issuesOf(e)
                when {
                    issues != null -> call.respondText(
                        jsonObjectOf(
                            "error" to jsonOf("Skenario tidak lagi sah"),
                            "issues" to jsonArrayOf(issues.map { jsonObjectOf("path" to jsonOf(it.path), "message" to jsonOf(it.message)) })
                        ).encode(), ContentType.Application.Json, HttpStatusCode.UnprocessableEntity
                    )
                    e is IllegalArgumentException -> call.respond(HttpStatusCode.NotFound, e.message ?: "Skenario tidak ditemukan")
                    else -> call.respond(HttpStatusCode.InternalServerError, "Gagal mengonfirmasi skenario: ${e.message}")
                }
            }
        )
    }
}

/** Galat validasi berpath dari service skenario (dipetakan 422). Asumsi sementara sampai tipe final Track A. */
class ScenarioValidationException(val issues: List<ScenarioIssue>) :
    IllegalArgumentException(issues.joinToString("; ") { "${it.path}: ${it.message}" })

private val PATH_HINT = Regex("""steps\[\d+]|\.carries\[|\.reworkTo|\.screenId""")

/** `null` = bukan galat validasi (id tak ada / galat lain). */
internal fun issuesOf(e: Throwable): List<ScenarioIssue>? = when {
    e is ScenarioValidationException -> e.issues
    e is DiscoveryDraftDecodeException -> listOf(ScenarioIssue(e.path, e.message ?: "tidak sah"))
    e is IllegalArgumentException && PATH_HINT.containsMatchIn(e.message.orEmpty()) -> listOf(ScenarioIssue("", e.message.orEmpty()))
    else -> null
}

/** Gerbang + flag + 409; mengembalikan tenant bila lolos, selain itu sudah menjawab. */
private suspend fun RoutingContext.guard(service: ScenarioService?, registry: BuilderRunRegistry): TenantId? {
    call.gate() ?: return null
    if (service == null) {
        call.respond(HttpStatusCode.NotFound, "Fitur skenario dimatikan")
        return null
    }
    val tenantId = call.tenantContext.tenantId
    if (registry.hasActiveRun(tenantId)) {
        call.respond(HttpStatusCode.Conflict, "Masih ada proses yang berjalan untuk project ini; tunggu selesai dulu")
        return null
    }
    return tenantId
}
