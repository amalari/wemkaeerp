package com.eventverse.app.routes

import com.eventverse.app.domain.crm.prefill.CrmAiSettingsRepository
import com.eventverse.app.domain.crm.prefill.LeadDraftExtractor
import com.eventverse.app.domain.crm.prefill.usecases.ExtractLeadDraftUseCase
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.crm.LeadDraftCodec
import com.eventverse.app.shared.json.JsonParser
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.slf4j.LoggerFactory

/**
 * Draf lead AI (TRD-HELP-002) — fitur di dalam modul CRM, gerbangnya modul CRM (termasuk entitlement).
 *
 * - `POST /api/tenant/crm/leads/draft`: CRM **OPERATE** (sama dengan membuat lead), diperiksa **sebelum** body
 *   dibaca; tenant belum opt-in → 409. Tidak menyimpan apa pun; lead disimpan user lewat `POST /crm/leads`.
 * - `GET  /api/tenant/crm/ai-settings`: CRM VIEW. `PUT`: CRM **MANAGE** (admin pabrik yang memutuskan data
 *   pelanggan boleh dikirim ke penyedia LLM).
 *
 * Isi teks tidak pernah dicatat di log — hanya panjangnya, agent, dan jumlah field.
 */
fun Route.crmLeadDraftRoutes(
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    customFieldRepository: CustomFieldDefinitionRepository,
    settingsRepository: CrmAiSettingsRepository,
    extractor: LeadDraftExtractor,
) {
    val log = LoggerFactory.getLogger("CrmLeadDraftRoutes")
    val extract = ExtractLeadDraftUseCase(extractor)

    suspend fun ApplicationCall.gate(required: AccessLevel): Pair<com.eventverse.app.domain.tenant.TenantContext, com.eventverse.app.domain.rbac.AccessDecision>? {
        val tenant = tenantContextOrNull ?: run { respond(HttpStatusCode.NotFound, "No tenant context found"); return null }
        callerPrincipalOrNull ?: run { respond(HttpStatusCode.Unauthorized, "Authentication required"); return null }
        val decision = moduleDecision(GarmentModules.CRM_SALES, tenant, roleRepository, moduleAssignmentRepository)
        return if (requireModuleAccess(GarmentModules.CRM_SALES, decision, required)) tenant to decision else null
    }

    post("/api/tenant/crm/leads/draft") {
        val (tenant, _) = call.gate(AccessLevel.OPERATE) ?: return@post
        if (!settingsRepository.isLeadDraftEnabled(tenant.tenantId)) {
            return@post call.respond(HttpStatusCode.Conflict, "Draf lead AI belum diaktifkan untuk pabrik ini")
        }
        val text = runCatching { JsonParser.parseObject(call.receiveText()).string("text") }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Body wajib berisi 'text'")
        val definitions = customFieldRepository.findActiveByResource(tenant.tenantId, OwnerResource.CRM_SALES)
        extract(text, definitions).fold(
            onSuccess = { draft ->
                log.info("crm/leads/draft tenant={} chars={} agent={} partial={} issues={}",
                    tenant.slug.value, text.length, draft.agentRef, draft.partial, draft.issues.size)
                call.respondText(LeadDraftCodec.encodeDraft(draft).encode(), ContentType.Application.Json)
            },
            onFailure = { e ->
                if (e is IllegalArgumentException) call.respond(HttpStatusCode.BadRequest, e.message ?: "Teks tidak valid") else throw e
            },
        )
    }

    get("/api/tenant/crm/ai-settings") {
        val (tenant, decision) = call.gate(AccessLevel.VIEW) ?: return@get
        val settings = LeadDraftCodec.AiSettings(
            leadDraftEnabled = settingsRepository.isLeadDraftEnabled(tenant.tenantId),
            canManage = decision.config.level.isAtLeast(AccessLevel.MANAGE),
        )
        call.respondText(LeadDraftCodec.encodeSettings(settings), ContentType.Application.Json)
    }

    put("/api/tenant/crm/ai-settings") {
        val (tenant, _) = call.gate(AccessLevel.MANAGE) ?: return@put
        val enabled = runCatching { JsonParser.parseObject(call.receiveText()).boolean("leadDraftEnabled") }.getOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Body wajib berisi 'leadDraftEnabled'")
        settingsRepository.setLeadDraftEnabled(tenant.tenantId, enabled, call.callerPrincipalOrNull?.userId)
        log.info("crm/ai-settings tenant={} leadDraftEnabled={} by={}", tenant.slug.value, enabled, call.callerPrincipalOrNull?.userId)
        call.respondText(LeadDraftCodec.encodeSettings(LeadDraftCodec.AiSettings(enabled, canManage = true)), ContentType.Application.Json)
    }
}

/**
 * Aksi "isi form lead dari chat" (TRD-HELP-002 Fase 5b) untuk AI helper. Ditawarkan **hanya** bila pemanggil boleh
 * membuat lead (CRM OPERATE, termasuk entitlement) **dan** tenant sudah opt-in — gerbang yang sama dengan
 * `POST /crm/leads/draft`, sehingga tombol di chat tidak pernah membuka jalan yang endpoint-nya akan menolak.
 */
fun leadPrefillActions(settingsRepository: CrmAiSettingsRepository): suspend (com.eventverse.app.domain.tenant.TenantContext, Map<com.eventverse.app.domain.pack.ModuleId, com.eventverse.app.domain.rbac.AccessDecision>) -> com.eventverse.app.domain.help.HelpActionResolver? =
    { tenant, decisions ->
        val crm = GarmentModules.CRM_SALES
        val canCreate = decisions[crm]?.config?.level?.isAtLeast(AccessLevel.OPERATE) == true
        if (canCreate && settingsRepository.isLeadDraftEnabled(tenant.tenantId)) {
            com.eventverse.app.domain.help.HelpActionResolver { q ->
                if (com.eventverse.app.domain.crm.prefill.LeadEntryIntent.matches(q)) com.eventverse.app.domain.help.HelpAction.PrefillLead(crm, q) else null
            }
        } else null
    }
