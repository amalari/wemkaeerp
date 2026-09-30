package com.eventverse.app.routes

import com.eventverse.app.domain.help.DeterministicHelpAgent
import com.eventverse.app.domain.help.HelpActionResolver
import com.eventverse.app.domain.help.HelpAgent
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.domain.help.usecases.AskHelpCommand
import com.eventverse.app.domain.help.usecases.AskHelpUseCase
import com.eventverse.app.domain.pack.ShippedTutorialSource
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tutorial.LexicalTutorialMatcher
import com.eventverse.app.domain.tutorial.TutorialCatalog
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.help.HelpCodec
import com.eventverse.app.shared.json.JsonParser
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

/**
 * `POST /api/tenant/help/ask` — AI helper memilih tutorial untuk pertanyaan bebas (TRD-HELP-001 FR-6).
 *
 * Terbuka untuk setiap anggota tenant **dengan sengaja** (tercatat di `RouteGateLedger.openByDesign`): gerbangnya
 * bukan satu modul, melainkan per tutorial — `AskHelpUseCase` menyaring katalog dengan keputusan wewenang pemanggil
 * sebelum agent melihatnya. Tanpa wewenang apa pun, jawabannya 200 tanpa saran. Endpoint ini tidak mengubah data.
 */
fun Route.helpRoutes(
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    agent: HelpAgent = DeterministicHelpAgent(),
    /**
     * Aksi untuk pemanggil ini (TRD-HELP-002 Fase 5b), dihitung dari keputusan wewenang yang sama. `null` = hanya tutorial.
     * Dirakit di wiring supaya route ini tidak menyebut modul pack tertentu.
     */
    actionsFor: suspend (TenantContext, Map<ModuleId, AccessDecision>) -> HelpActionResolver? = { _, _ -> null },
) {
    val log = LoggerFactory.getLogger("HelpRoutes")
    val askHelp = AskHelpUseCase(TutorialCatalog(ShippedTutorialSource), LexicalTutorialMatcher(), agent)

    post("/api/tenant/help/ask") {
        val tenant = call.tenantContextOrNull ?: return@post call.respond(HttpStatusCode.NotFound, "No tenant context found")
        call.callerPrincipalOrNull ?: return@post call.respond(HttpStatusCode.Unauthorized, "Authentication required")
        val request = runCatching { JsonParser.parseObject(call.receiveText()) }.getOrNull()?.let(HelpCodec::decodeRequest)
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Body wajib berisi 'question'")

        val decisions = call.callerDecisions(tenant, roleRepository, moduleAssignmentRepository)
        val command = AskHelpCommand(
            question = request.question,
            currentModule = request.currentModule?.takeIf { tenant.pack.module(it) != null },
            pack = tenant.pack,
            decisions = decisions,
            actionResolver = actionsFor(tenant, decisions),
        )
        askHelp(command).fold(
            onSuccess = { result ->
                // Isi pertanyaan tidak dicatat — bisa memuat data pelanggan.
                log.info("help/ask tenant={} agent={} suggested={} alternatives={} action={}",
                    tenant.slug.value, result.agentRef, result.suggestion != null, result.alternatives.size, result.action?.let { it::class.simpleName })
                call.respondText(HelpCodec.encodeResult(result).encode(), ContentType.Application.Json)
            },
            onFailure = { e ->
                if (e is IllegalArgumentException) call.respond(HttpStatusCode.BadRequest, e.message ?: "Pertanyaan tidak valid")
                else throw e
            },
        )
    }
}
