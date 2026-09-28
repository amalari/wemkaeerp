package com.eventverse.app.routes

import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.parseLegacyStageCodeOrNull
import com.eventverse.app.domain.sampling.usecases.ReleaseSamplingStageWorkCommand
import com.eventverse.app.domain.sampling.usecases.ReleaseSamplingStageWorkUseCase
import com.eventverse.app.domain.sampling.usecases.SendBackSamplingReworkCommand
import com.eventverse.app.domain.sampling.usecases.SendBackSamplingReworkUseCase
import com.eventverse.app.domain.sampling.usecases.StartSamplingStageWorkCommand
import com.eventverse.app.domain.sampling.usecases.StartSamplingStageWorkUseCase
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock

/**
 * Meja operator lantai produksi sampling: ambil SPK (Antrian → Sedang Dikerjakan), kembalikan
 * ke antrian, dan kirim balik rework ke meja penyebab cacat.
 *
 * Dipisah dari [samplingRoutes] per sub-agregat "pekerjaan di meja" — file induknya sudah di
 * atas soft limit server. Identitas aktor dibaca dari JWT, bukan dari body.
 */
fun Route.samplingStageWorkRoutes(repository: SamplingOrderRepository) {
    val startWork = StartSamplingStageWorkUseCase(repository)
    val releaseWork = ReleaseSamplingStageWorkUseCase(repository)
    val sendBackRework = SendBackSamplingReworkUseCase(repository)

    route("/api/tenant/sampling/orders/{id}") {

        // POST …/{id}/work/start  body: { operatorName }
        post("/work/start") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val json = JsonParser.parse(call.receiveText()) as? JsonValue.Obj
            val caller = call.callerPrincipalOrNull
            val operatorName = json?.string("operatorName")?.takeIf { it.isNotBlank() }
                ?: caller?.email
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing operatorName")
            call.respondOrder(
                startWork(
                    StartSamplingStageWorkCommand(
                        orderId = SamplingOrderId(id),
                        operatorName = operatorName,
                        actorEmail = caller?.email ?: "unknown",
                        now = Clock.System.now()
                    )
                )
            )
        }

        // POST …/{id}/work/release
        post("/work/release") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val actorEmail = call.callerPrincipalOrNull?.email ?: "unknown"
            call.respondOrder(releaseWork(ReleaseSamplingStageWorkCommand(SamplingOrderId(id), actorEmail, Clock.System.now())))
        }

        // POST …/{id}/rework  body: { targetStage, reason, liability }
        post("/rework") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing ID")
            val json = JsonParser.parse(call.receiveText()) as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")
            val target = parseLegacyStageCodeOrNull(json.string("targetStage")) ?: StageCode.parseOrNull(json.string("targetStage"))
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid targetStage")
            val liability = DefectLiability.entries.firstOrNull { it.name == json.string("liability") }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid liability")
            val caller = call.callerPrincipalOrNull
            call.respondOrder(
                sendBackRework(
                    SendBackSamplingReworkCommand(
                        orderId = SamplingOrderId(id),
                        target = target,
                        reason = json.string("reason") ?: "",
                        liability = liability,
                        actorEmail = caller?.email ?: "unknown",
                        actorRole = caller?.role?.name ?: "UNKNOWN",
                        now = Clock.System.now()
                    )
                )
            )
        }
    }
}

/** Pelanggaran aturan domain (`require`) → 422 + pesannya; SPK tak ditemukan (`error`) → 404. */
private suspend fun ApplicationCall.respondOrder(result: Result<SamplingOrder>) {
    result
        .onSuccess { respondText(SamplingOrderCodec.encode(it).encode(), ContentType.Application.Json) }
        .onFailure { error ->
            val status = if (error is IllegalArgumentException) HttpStatusCode.UnprocessableEntity else HttpStatusCode.NotFound
            respond(status, error.message ?: "Unknown error")
        }
}
