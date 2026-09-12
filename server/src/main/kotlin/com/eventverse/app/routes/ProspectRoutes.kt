package com.eventverse.app.routes

import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.prospect.FlowTranslationId
import com.eventverse.app.domain.prospect.FlowTranslationRepository
import com.eventverse.app.domain.prospect.LeadSource
import com.eventverse.app.domain.prospect.LeadStatus
import com.eventverse.app.domain.prospect.ProspectLead
import com.eventverse.app.domain.prospect.ProspectLeadId
import com.eventverse.app.domain.prospect.ProspectLeadRepository
import com.eventverse.app.domain.prospect.ProspectPriceEstimateId
import com.eventverse.app.domain.prospect.ProspectPriceEstimateRepository
import com.eventverse.app.domain.prospect.StoredPriceEstimate
import com.eventverse.app.domain.prospect.usecases.AnalyzeCoverageUseCase
import com.eventverse.app.domain.prospect.usecases.PriceProspectFlowUseCase
import com.eventverse.app.domain.prospect.usecases.SubmitProspectLeadUseCase
import com.eventverse.app.domain.prospect.usecases.TranslateProspectFlowUseCase
import com.eventverse.app.routes.dto.ProspectDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

/**
 * HTTP surface for the prospect flow.
 *
 * Two tiers, split the same way the schema is:
 *
 *  - `/api/public/prospect-assessment` — unauthenticated. Returns modules, a rounded range or an
 *    explicit refusal, and open questions. Never a cost figure.
 *  - `/api/admin/prospects` — superadmin only, guarded by `TenantResolutionPlugin`'s
 *    `platformRoutePrefixes`. Returns everything, including why each gap could not be priced.
 *
 * The public endpoint currently runs on a keyword translator, so it costs nothing to call. **That
 * changes the moment a real model is wired in**, and a per-IP rate limit and daily cap must land
 * before it does — an unauthenticated route that invokes a paid model is a tap anyone can open.
 * A narrative length cap is already enforced in [SubmitProspectLeadUseCase], in the domain rather
 * than only at this edge, so a second caller cannot bypass it.
 */
fun Route.prospectRoutes(
    leadRepository: ProspectLeadRepository,
    translationRepository: FlowTranslationRepository,
    priceEstimateRepository: ProspectPriceEstimateRepository,
    submitLeadUseCase: SubmitProspectLeadUseCase,
    translateUseCase: TranslateProspectFlowUseCase,
    analyzeCoverageUseCase: AnalyzeCoverageUseCase,
    priceUseCase: PriceProspectFlowUseCase,
    defaultMarginPercent: Percentage
) {

    // =========================================================================
    // Public
    // =========================================================================
    post("/api/public/prospect-assessment") {
        val body = ProspectDto.readAssessment(call.receiveText())
        if (body == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                "Body harus berisi companyName dan narrative."
            )
            return@post
        }

        val now = Clock.System.now()
        val leadId = ProspectLeadId("lead-${now.toEpochMilliseconds()}")

        val lead = submitLeadUseCase(
            id = leadId,
            companyName = body.companyName,
            narrativeRaw = body.narrative,
            contactName = body.contactName,
            contactEmail = body.contactEmail,
            contactPhone = body.contactPhone,
            source = LeadSource.LANDING_PAGE,
            submittedAt = now
        ).getOrElse {
            call.respondFailure(HttpStatusCode.BadRequest, it, "Data tidak valid")
            return@post
        }

        val translation = translateUseCase(
            translationId = FlowTranslationId("tr-${now.toEpochMilliseconds()}"),
            lead = lead,
            translatedAt = now
        ).getOrElse {
            // The lead is already saved, so the narrative is not lost even when the translator
            // cannot make anything of it — sales can still follow up by hand.
            call.respondFailure(HttpStatusCode.UnprocessableEntity, it, "Narasi belum bisa diterjemahkan")
            return@post
        }

        val coverage = analyzeCoverageUseCase(translation).getOrElse {
            call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal memetakan modul")
            return@post
        }

        val pricing = priceUseCase(coverage, defaultMarginPercent).getOrElse {
            call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal menghitung perkiraan")
            return@post
        }

        priceEstimateRepository.save(
            StoredPriceEstimate(
                id = ProspectPriceEstimateId("pe-${now.toEpochMilliseconds()}"),
                leadId = lead.id,
                translationId = translation.id,
                range = pricing.range,
                marginPercent = defaultMarginPercent.value,
                computedAt = now
            )
        )

        call.respondJson(
            ProspectDto.publicAssessmentToJson(lead, translation, coverage, pricing.range),
            HttpStatusCode.Created
        )
    }

    // =========================================================================
    // Platform admin
    // =========================================================================
    route("/api/admin/prospects") {

        get {
            val statusFilter = call.request.queryParameters["status"]?.let { LeadStatus.fromCode(it) }
            runCatching {
                if (statusFilter != null) {
                    leadRepository.findByStatus(statusFilter)
                } else {
                    leadRepository.findRecent()
                }
            }
                .onSuccess { call.respondJson(ProspectDto.leadListToJson(it)) }
                .onFailure { call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal memuat prospek") }
        }

        get("/{leadId}") {
            val lead = call.requireLead(leadRepository) ?: return@get
            val translation = translationRepository.findByLead(lead.id).firstOrNull()
            if (translation == null) {
                call.respond(HttpStatusCode.NotFound, "Prospek ${lead.id.value} belum diterjemahkan.")
                return@get
            }

            val coverage = analyzeCoverageUseCase(translation).getOrElse {
                call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal memetakan modul")
                return@get
            }
            val pricing = priceUseCase(coverage, defaultMarginPercent).getOrElse {
                call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal menghitung perkiraan")
                return@get
            }

            call.respondJson(
                ProspectDto.internalAssessmentToJson(lead, translation, coverage, pricing)
            )
        }

        /**
         * Recomputes the range with a reviewer's own assumptions.
         *
         * The one that matters is `expectedTenantCount`: deciding a module belongs in the core
         * product rather than to this customer alone moves the price several times over, and that
         * is a judgement call a person makes, not a default.
         */
        post("/{leadId}/price") {
            val lead = call.requireLead(leadRepository) ?: return@post
            val translation = translationRepository.findByLead(lead.id).firstOrNull()
            if (translation == null) {
                call.respond(HttpStatusCode.NotFound, "Prospek ${lead.id.value} belum diterjemahkan.")
                return@post
            }

            val body = ProspectDto.readReprice(call.receiveText())
            val coverage = analyzeCoverageUseCase(translation).getOrElse {
                call.respondFailure(HttpStatusCode.InternalServerError, it, "Gagal memetakan modul")
                return@post
            }

            val pricing = priceUseCase(
                coverage = coverage,
                marginPercent = Percentage(body.marginPercent),
                monthlyMaintenancePercent = Percentage(body.monthlyMaintenancePercent),
                monthlyInfraCost = MoneyIdr(body.monthlyInfraCostIdr),
                expectedTenantCount = body.expectedTenantCount,
                amortizationMonths = body.amortizationMonths
            ).getOrElse {
                call.respondFailure(HttpStatusCode.BadRequest, it, "Gagal menghitung ulang")
                return@post
            }

            val now = Clock.System.now()
            priceEstimateRepository.save(
                StoredPriceEstimate(
                    id = ProspectPriceEstimateId("pe-${now.toEpochMilliseconds()}"),
                    leadId = lead.id,
                    translationId = translation.id,
                    range = pricing.range,
                    marginPercent = body.marginPercent,
                    computedAt = now
                )
            )

            call.respondJson(
                ProspectDto.internalAssessmentToJson(lead, translation, coverage, pricing)
            )
        }
    }
}

private suspend fun ApplicationCall.requireLead(
    repository: ProspectLeadRepository
): ProspectLead? {
    val raw = parameters["leadId"].orEmpty()
    val lead = runCatching { repository.findById(ProspectLeadId(raw)) }.getOrNull()
    if (lead == null) {
        respond(HttpStatusCode.NotFound, "Prospek $raw tidak ditemukan.")
    }
    return lead
}

private suspend fun ApplicationCall.respondJson(
    json: String,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondText(json, ContentType.Application.Json, status)
}

private suspend fun ApplicationCall.respondFailure(
    status: HttpStatusCode,
    cause: Throwable,
    fallbackMessage: String
) {
    respond(status, cause.message ?: fallbackMessage)
}
