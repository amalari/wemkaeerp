package com.eventverse.app.routes

import com.eventverse.app.domain.builder.BuilderChatRepository
import com.eventverse.app.domain.builder.SendBuilderMessageUseCase
import com.eventverse.app.infrastructure.builder.BuilderRunRegistry
import com.eventverse.app.plugins.tenantContext
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.routing.intercept
import io.ktor.server.application.call
import io.ktor.server.routing.route
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.ktor.util.AttributeKey
import com.eventverse.app.infrastructure.builder.BuilderRun

/**
 * Chat Builder asinkron (PLAN-builder-interview-chat §3.3). Dipasang di dalam `route("/api/builder")`.
 *
 * - `POST /chat/runs` `{text, module?}` → 202 `{runId}`; pesan pengguna disimpan dulu, agent bekerja di latar.
 * - `GET /runs/{runId}/events` → SSE progres (`status`, `message`, `error`, `done`, …), `Last-Event-ID` didukung.
 *
 * Riwayat di DB tetap sumber kebenaran: klien memuat ulang riwayat setelah `done`/`error`, jadi SSE yang putus tidak
 * menghilangkan data. Gerbang sama dengan seluruh Builder (`MANAGE_BUILDER`, fail-closed); run tenant lain = 404.
 */
fun Route.builderChatStreamRoutes(
    send: SendBuilderMessageUseCase,
    chats: BuilderChatRepository,
    registry: BuilderRunRegistry
) {
    post("/chat/runs") {
        call.gate() ?: return@post
        val body = runCatching { JsonParser.parse(call.receiveText()) as? JsonValue.Obj }.getOrNull()
        val text = body?.string("text")?.trim()
        if (text.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Body wajib {\"text\":\"…\"}")
            return@post
        }
        val moduleId = body.string("module")?.takeIf { it.isNotBlank() }
        val tenantId = call.tenantContext.tenantId
        val run = try {
            registry.start(tenantId) { run ->
                send(tenantId, text, moduleId) { phase -> run.emit("status", "phase" to jsonOf(phase)) }.getOrThrow()
                val last = chats.messages(chats.conversationFor(tenantId).id).lastOrNull()
                // Pesan QUESTION = agent bertanya dulu; klien menampilkannya dan menunggu jawaban pengguna.
                val type = if (last?.kind == com.eventverse.app.domain.builder.ChatMessageKind.QUESTION) "question" else "message"
                run.emit(type, "messageId" to jsonOf(last?.id?.value), "moduleId" to jsonOf(moduleId))
            }
        } catch (e: BuilderRunRegistry.RunAlreadyActiveException) {
            call.respond(HttpStatusCode.Conflict, e.message ?: "Masih ada proses yang berjalan")
            return@post
        }
        call.respondText(jsonObjectOf("runId" to jsonOf(run.id)).encode(), ContentType.Application.Json, HttpStatusCode.Accepted)
    }

    // Gerbang dijalankan SEBELUM aliran dimulai: respons 403/404 baru bisa dikirim selagi header belum terkirim.
    route("/runs/{runId}/events") {
        intercept(ApplicationCallPipeline.Call) {
            if (call.gate() == null) return@intercept finish()
            val run = registry.find(call.parameters["runId"].orEmpty(), call.tenantContext.tenantId)
            if (run == null) {
                call.respond(HttpStatusCode.NotFound, "Run tidak ditemukan")
                return@intercept finish()
            }
            call.attributes.put(RunKey, run)
        }
        sse {
            val run = call.attributes[RunKey]
            val after = call.request.header("Last-Event-ID")?.toLongOrNull() ?: 0L
            run.stream(after).collect { send(ServerSentEvent(data = it.data, event = it.type, id = it.id.toString())) }
        }
    }
}

private val RunKey = AttributeKey<BuilderRun>("builder.run")
