package com.eventverse.app.routes

import com.eventverse.app.domain.process.TenantStagePhaseTagsRepository
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.process.StagePhaseTagsCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * Template tag fase pabrik — tag `[Sampling ×] [Produksi ×]` pada Cuci & Setrika yang diwarisi
 * setiap desain baru. Dipisah dari [tenantProcessRoutes] karena yang diatur di sini adalah tahap
 * wajib, bukan proses sisipan.
 *
 * Mengubah template tidak me-rute ulang kartu yang sudah berjalan: tag dibekukan ke SPK saat
 * masuk Program CAM.
 */
fun Route.tenantPhaseTagRoutes(repository: TenantStagePhaseTagsRepository) {
    route("/api/tenant/process-catalog/phase-tags") {

        get {
            val tenant = call.tenantContextOrNull ?: return@get call.respond(HttpStatusCode.NotFound, "No tenant context found")
            val tags = repository.findByTenantId(tenant.tenantId)
            call.respondText(
                jsonObjectOf("stagePhaseTags" to StagePhaseTagsCodec.encode(tags)).encode(),
                ContentType.Application.Json
            )
        }

        put {
            val tenant = call.tenantContextOrNull ?: return@put call.respond(HttpStatusCode.NotFound, "No tenant context found")
            val body = runCatching { JsonParser.parse(call.receiveText()) as? JsonValue.Obj }.getOrNull()
            val tags = StagePhaseTagsCodec.decode(body?.entries?.get("stagePhaseTags"))
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing stagePhaseTags")

            repository.save(tenant.tenantId, tags)
                .onSuccess { saved ->
                    call.respondText(
                        jsonObjectOf("stagePhaseTags" to StagePhaseTagsCodec.encode(saved)).encode(),
                        ContentType.Application.Json
                    )
                }
                .onFailure { call.respond(HttpStatusCode.InternalServerError, it.message ?: "Failed to save phase tags") }
        }
    }
}
