package com.eventverse.app.routes

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDemandRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.InterviewStepFiller
import com.eventverse.app.domain.discovery.usecases.InterviewDraftUseCases
import com.eventverse.app.domain.discovery.usecases.UpdateDiscoveryDraftUseCase
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.discovery.InterviewSessionCodec
import com.eventverse.app.shared.json.JsonParser
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * `POST /api/discovery/drafts/{id}/interview` (PLAN-iv-B B4) — file sendiri supaya `DiscoveryRoutes.kt` tidak
 * bertambah. **Fail-closed, pemilik draf saja**: tanpa login 401, bukan pemilik 403 (superadmin pun tidak menjawab
 * atas nama prospek), draf terkunci 409. Body: `{"action": "start" | "answer" | "accept_all", ...}`; `start` boleh membawa `mode: "konsultan"` (buka dengan fase F0–F2); `answer`
 * membawa `questionId`, `outcome` (`confirmed|changed|skipped`), `text?`, dan `session?` (hasil suntingan klien).
 * Balasan = ringkasan draf yang sama dengan `GET`, sudah memuat `interview` dan `nextQuestion`.
 */
fun Route.discoveryInterviewRoutes(
    repository: DiscoveryDraftRepository,
    demands: DiscoveryDemandRepository,
    /** Pengisi tebakan langkah (agent AI); null = tebakan deterministik saja. */
    filler: InterviewStepFiller? = null,
    /** Perencana alur penuh (model besar, sekali di awal); null = tanpa rencana. */
    planner: com.eventverse.app.domain.discovery.interview.InterviewPlanner? = null
) {
    val interviews = InterviewDraftUseCases(repository, filler, planner)

    route("/api/discovery/drafts") {
        post("/{id}/interview") {
            val principal = call.callerPrincipalOrNull ?: return@post unauthorized()
            val id = DiscoveryDraftId(call.parameters["id"].orEmpty())
            val stored = repository.findById(id) ?: return@post notFound("Draf tidak ditemukan")
            if (stored.ownerUserId.value != principal.userId) return@post forbidden()
            val body = runCatching { JsonParser.parseObject(call.receiveText()) }.getOrNull()
                ?: return@post badRequest("Body harus JSON objek")
            val caller = UserId(principal.userId)
            val narrative = demands.findByDraftId(id)?.narrative.orEmpty()

            val result = when (val action = body.string("action")) {
                "start" -> interviews.start(id, caller, narrative, consultant = body.string("mode") == "konsultan")
                "accept_all" -> interviews.acceptAll(id, caller, narrative)
                "answer" -> {
                    val outcome = Confirmation.fromCode(body.string("outcome").orEmpty())
                        ?.takeIf { it != Confirmation.GUESSED }
                        ?: return@post badRequest("Field 'outcome' wajib: confirmed, changed, atau skipped")
                    val questionId = body.string("questionId")?.takeIf { it.isNotBlank() }
                        ?: return@post badRequest("Field 'questionId' wajib diisi")
                    val revised = try {
                        body.obj("session")?.let { InterviewSessionCodec.decode(it, "$.session") }
                    } catch (e: DiscoveryDraftDecodeException) {
                        return@post badRequest("Sesi wawancara tidak sah (${e.path}): ${e.message}")
                    }
                    interviews.answer(id, caller, questionId, outcome, body.string("text"), revised, narrative)
                }
                else -> return@post badRequest("Field 'action' wajib: start, answer, atau accept_all (dapat '$action')")
            }
            result.onSuccess { call.respondText(summaryObj(it, narrative.ifBlank { null }).encode(), ContentType.Application.Json) }
                .onFailure {
                    val status = when (it) {
                        is UpdateDiscoveryDraftUseCase.LockedException -> HttpStatusCode.Conflict
                        is UpdateDiscoveryDraftUseCase.NotOwnerException -> HttpStatusCode.Forbidden
                        else -> HttpStatusCode.BadRequest
                    }
                    call.respondText(it.message ?: "Gagal memproses wawancara", ContentType.Text.Plain, status)
                }
        }
    }
}
