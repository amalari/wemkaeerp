package com.eventverse.app.routes

import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.brief.BriefCoverage
import com.eventverse.app.domain.discovery.brief.BriefRenderer
import com.eventverse.app.domain.discovery.brief.CaptureEntry
import com.eventverse.app.domain.discovery.brief.RequirementsBriefAssembler
import com.eventverse.app.domain.discovery.usecases.PriceDiscoveryDraftUseCase
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContext
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.pack.BriefCodec
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
 * `POST /api/builder/draft/brief` (PLAN-proto-C, C4; kontrak §3.3) — brief kebutuhan untuk developer.
 *
 * Body `{ "included": ["moduleId",…], "changes": [CaptureEntry,…] }` → `{ "markdown", "brief" }`.
 * **Tidak menulis apa pun**: log perubahan tidak disimpan di server (state prototype = memori sesi klien),
 * klien mengirimnya tiap kali meminta brief; POST dipakai hanya karena membawa body. Gerbang builder yang
 * sama dengan `GET /api/builder/draft` (identitas tak terhitung = 403); 404 tanpa draf; 400 untuk body/entri/modul
 * yang tak sah — **ditolak, tidak diabaikan diam-diam**. Cakupan & harga memakai mesin yang sama dengan panel harga.
 */
fun Route.builderBriefRoutes(drafts: DiscoveryDraftRepository, priceDraft: PriceDiscoveryDraftUseCase) {
    route("/api/builder/draft/brief") {
        post {
            call.gate() ?: return@post
            if (call.callerPrincipalOrNull == null) {
                call.respond(HttpStatusCode.Forbidden, "Identitas pemanggil tidak bisa dihitung.")
                return@post
            }
            val stored = drafts.findByTenant(call.tenantContext.tenantId)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Belum ada draf kerja.")
            val body = runCatching { JsonParser.parseObject(call.receiveText()) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Body harus JSON objek.")

            val allModules = stored.draft.pack.modules.map { it.id.value }.toSet()
            val included = body.stringArray("included").toSet().ifEmpty { allModules }
            val unknown = included - allModules
            if (unknown.isNotEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Modul tidak ada di draf: " + unknown.sorted().joinToString())
            }
            val changes = mutableListOf<CaptureEntry>()
            for ((i, raw) in body.objectArray("changes").withIndex()) {
                SpecOpCodec.decodeEntry(raw).fold(
                    onSuccess = { changes += it },
                    onFailure = { return@post call.respond(HttpStatusCode.BadRequest, "Perubahan #" + (i + 1) + " tidak sah: " + (it.message ?: "format salah")) }
                )
            }

            val pricing = priceDraft(stored.draft, Percentage(35.0), onlyModuleIds = included).getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, it.message ?: "Gagal menghitung estimasi")
            }
            val coverage = pricing.lines.map { BriefCoverage(it.moduleId, it.displayName, it.covered, it.monthlyIdr, it.gapLowIdr, it.gapHighIdr) }
            val brief = RequirementsBriefAssembler.assemble(stored.draft, included, changes, coverage)
            call.respondText(
                jsonObjectOf("markdown" to jsonOf(BriefRenderer.markdown(brief)), "brief" to BriefCodec.encode(brief)).encode(),
                ContentType.Application.Json
            )
        }
    }
}
