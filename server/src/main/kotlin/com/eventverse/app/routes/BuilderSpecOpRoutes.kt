package com.eventverse.app.routes

import com.eventverse.app.domain.prototype.DeterministicSpecOpProposer
import com.eventverse.app.domain.prototype.SpecOpProposer
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.pack.InteractiveScreenCodec
import com.eventverse.app.shared.pack.SpecOpCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * `POST /api/builder/draft/spec-ops` (PLAN-proto-C, C6; kontrak §3.3b) — mengusulkan operasi spec dari kalimat biasa.
 *
 * Body `{ "message", "screenId", "spec": <InteractiveScreen JSON> }` → `{ "ops": [...], "reply": "..." }`.
 * **Hanya usulan**: tidak menerapkan apa pun dan tidak menulis; klien menerapkannya lewat `SpecOpApplier` yang
 * memvalidasi ulang. Kalimat yang tak dikenal dijawab 200 dengan `ops` kosong dan `reply` berisi contoh kalimat —
 * itu jawaban sah, bukan galat. Gerbang builder (401/403 sebelum body dibaca). [proposer] adalah port: default
 * deterministik tanpa LLM; adaptor LLM (opsional) wajib tetap melewati validator yang sama.
 */
fun Route.builderSpecOpRoutes(proposer: SpecOpProposer = DeterministicSpecOpProposer()) {
    route("/api/builder/draft/spec-ops") {
        post {
            call.gate() ?: return@post
            if (call.callerPrincipalOrNull == null) {
                call.respond(HttpStatusCode.Forbidden, "Identitas pemanggil tidak bisa dihitung.")
                return@post
            }
            val body = runCatching { JsonParser.parseObject(call.receiveText()) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Body harus JSON objek.")
            val message = body.string("message")?.takeIf { it.isNotBlank() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Bidang 'message' wajib diisi.")
            val specJson = body.obj("spec") as? JsonValue.Obj
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Bidang 'spec' wajib diisi.")
            val screen = runCatching { InteractiveScreenCodec.decode(specJson) }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, "Spec tidak sah: " + (it.message ?: "format salah"))
            }

            val result = proposer.propose(message, screen)
            val ops = result.getOrNull().orEmpty()
            val reply = result.fold(
                onSuccess = { "Diusulkan " + it.size + " perubahan." },
                onFailure = { it.message ?: "Belum bisa memahami permintaan itu." }
            )
            call.respondText(
                jsonObjectOf("ops" to jsonArrayOf(ops.map(SpecOpCodec::encode)), "reply" to jsonOf(reply)).encode(),
                ContentType.Application.Json
            )
        }
    }
}
