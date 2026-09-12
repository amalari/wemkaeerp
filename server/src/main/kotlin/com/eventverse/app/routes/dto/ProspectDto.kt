package com.eventverse.app.routes.dto

import com.eventverse.app.domain.prospect.CoverageAnalysis
import com.eventverse.app.domain.prospect.CoverageDecision
import com.eventverse.app.domain.prospect.FlowTranslation
import com.eventverse.app.domain.prospect.ProspectLead
import com.eventverse.app.domain.prospect.ProspectPriceRange
import com.eventverse.app.domain.prospect.usecases.ProspectPricingResult
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * HTTP boundary mapping for the prospect flow.
 *
 * **The public and internal shapes are deliberately different functions, not one function with a
 * flag.** A boolean parameter deciding whether to include cost is one wrong argument away from
 * publishing our hourly rate to an unauthenticated endpoint; two functions cannot make that mistake.
 */
object ProspectDto {

    // ---- Requests in ---------------------------------------------------------

    data class AssessmentBody(
        val companyName: String,
        val narrative: String,
        val contactName: String?,
        val contactEmail: String?,
        val contactPhone: String?
    )

    fun readAssessment(rawBody: String): AssessmentBody? {
        val obj = JsonParser.parseObjectOrNull(rawBody) ?: return null
        val company = obj.string("companyName")?.takeIf { it.isNotBlank() } ?: return null
        val narrative = obj.string("narrative")?.takeIf { it.isNotBlank() } ?: return null
        return AssessmentBody(
            companyName = company,
            narrative = narrative,
            contactName = obj.string("contactName"),
            contactEmail = obj.string("contactEmail"),
            contactPhone = obj.string("contactPhone")
        )
    }

    data class RepriceBody(
        val expectedTenantCount: Int,
        val amortizationMonths: Int,
        val marginPercent: Double,
        val monthlyMaintenancePercent: Double,
        val monthlyInfraCostIdr: Long
    )

    fun readReprice(rawBody: String): RepriceBody {
        val obj = JsonParser.parseObjectOrNull(rawBody)
        return RepriceBody(
            expectedTenantCount = (obj?.int("expectedTenantCount") ?: 1).coerceAtLeast(1),
            amortizationMonths = (obj?.int("amortizationMonths") ?: 24).coerceAtLeast(1),
            marginPercent = obj?.double("marginPercent") ?: 0.0,
            monthlyMaintenancePercent = obj?.double("monthlyMaintenancePercent") ?: 0.0,
            monthlyInfraCostIdr = obj?.long("monthlyInfraCostIdr") ?: 0L
        )
    }

    // ---- Public response -----------------------------------------------------

    /**
     * What a prospect sees.
     *
     * Modules by name, a rounded range or an explicit "we will get back to you", and the questions
     * worth answering. No hours, no rates, no build cost, no margin — and a test scans for exactly
     * those strings.
     */
    fun publicAssessmentToJson(
        lead: ProspectLead,
        translation: FlowTranslation,
        coverage: CoverageAnalysis,
        range: ProspectPriceRange
    ): String = jsonObjectOf(
        "leadId" to jsonOf(lead.id.value),
        "detectedBusinessModel" to jsonOf(translation.detectedPreset?.displayName),
        "modules" to jsonArrayOf(
            coverage.decisions.map { decision ->
                jsonObjectOf(
                    "name" to jsonOf(
                        when (decision) {
                            is CoverageDecision.CoveredByCatalog -> decision.entry.displayName
                            is CoverageDecision.Gap -> decision.requirement.title
                        }
                    ),
                    "stage" to jsonOf(decision.requirement.archetype.displayName),
                    // "available" vs "needs building" is genuinely useful to a prospect and reveals
                    // nothing about our costs.
                    "status" to jsonOf(
                        when (decision) {
                            is CoverageDecision.CoveredByCatalog -> "TERSEDIA"
                            is CoverageDecision.Gap -> "PERLU DIBANGUN"
                        }
                    )
                )
            }
        ),
        "priceRange" to if (range.isPublishable) {
            jsonObjectOf(
                "available" to jsonOf(true),
                "monthlyLowIdr" to jsonOf(range.displayLow!!.amount),
                "monthlyHighIdr" to jsonOf(range.displayHigh!!.amount),
                "note" to jsonOf(
                    "Perkiraan awal. Angka final dikonfirmasi setelah kebutuhan ditinjau."
                )
            )
        } else {
            jsonObjectOf(
                "available" to jsonOf(false),
                // No partial total. Summing only the gaps we could price would understate the work.
                "reason" to jsonOf(
                    "Ada ${range.unpriceableGapCount} kebutuhan yang perlu kami pelajari dulu. " +
                        "Tim kami menghubungi Anda dalam 1×24 jam."
                )
            )
        },
        "openQuestions" to jsonArrayOf(translation.openQuestions.map { jsonOf(it) })
    ).encode()

    // ---- Internal responses --------------------------------------------------

    fun leadListToJson(leads: List<ProspectLead>): String = jsonObjectOf(
        "leads" to jsonArrayOf(
            leads.map { lead ->
                jsonObjectOf(
                    "id" to jsonOf(lead.id.value),
                    "companyName" to jsonOf(lead.companyName),
                    "status" to jsonOf(lead.status.code),
                    "source" to jsonOf(lead.source.code),
                    "contactEmail" to jsonOf(lead.contactEmail),
                    "convertedTenantId" to jsonOf(lead.convertedTenantId?.value)
                )
            }
        )
    ).encode()

    /**
     * The reviewer's view: everything the public response hides, plus why the model said it.
     */
    fun internalAssessmentToJson(
        lead: ProspectLead,
        translation: FlowTranslation,
        coverage: CoverageAnalysis,
        pricing: ProspectPricingResult
    ): String = jsonObjectOf(
        "lead" to jsonObjectOf(
            "id" to jsonOf(lead.id.value),
            "companyName" to jsonOf(lead.companyName),
            "status" to jsonOf(lead.status.code),
            "narrativeRaw" to jsonOf(lead.narrativeRaw)
        ),
        "translation" to jsonObjectOf(
            "id" to jsonOf(translation.id.value),
            "translatorRef" to jsonOf(translation.translatorRef),
            "detectedPreset" to jsonOf(translation.detectedPreset?.code),
            "needsHumanReview" to jsonOf(translation.needsHumanReview),
            "validationWarnings" to jsonArrayOf(translation.validationWarnings.map { jsonOf(it) }),
            "openQuestions" to jsonArrayOf(translation.openQuestions.map { jsonOf(it) }),
            "requirements" to jsonArrayOf(
                translation.requirements.map { requirement ->
                    jsonObjectOf(
                        "archetype" to jsonOf(requirement.archetype.code),
                        "title" to jsonOf(requirement.title),
                        // The fragment that justifies this requirement. A reviewer reads this first:
                        // an empty quote is the cheapest hallucination signal there is.
                        "sourceQuote" to jsonOf(requirement.sourceQuote),
                        "lacksEvidence" to jsonOf(requirement.lacksEvidence)
                    )
                }
            )
        ),
        "coverage" to jsonArrayOf(
            coverage.decisions.map { decision ->
                jsonObjectOf(
                    "title" to jsonOf(decision.requirement.title),
                    "kind" to jsonOf(decision.kind.code),
                    "moduleId" to jsonOf(
                        when (decision) {
                            is CoverageDecision.CoveredByCatalog -> decision.entry.moduleId
                            is CoverageDecision.Gap -> decision.proposedModuleId
                        }
                    )
                )
            }
        ),
        "pricing" to jsonObjectOf(
            "isPublishable" to jsonOf(pricing.range.isPublishable),
            "subscriptionMonthlyIdr" to jsonOf(pricing.range.subscriptionMonthly.amount),
            "gapLowMonthlyIdr" to (pricing.range.gapLowMonthly?.let { jsonOf(it.amount) } ?: JsonValue.Null),
            "gapHighMonthlyIdr" to (pricing.range.gapHighMonthly?.let { jsonOf(it.amount) } ?: JsonValue.Null),
            "displayLowIdr" to (pricing.range.displayLow?.let { jsonOf(it.amount) } ?: JsonValue.Null),
            "displayHighIdr" to (pricing.range.displayHigh?.let { jsonOf(it.amount) } ?: JsonValue.Null),
            "expectedTenantCount" to jsonOf(pricing.range.expectedTenantCount),
            "amortizationMonths" to jsonOf(pricing.range.amortizationMonths),
            "pricingModelVersion" to jsonOf(pricing.range.pricingModelVersion),
            "gaps" to jsonArrayOf(
                pricing.gapPricings.map { gapPricing ->
                    jsonObjectOf(
                        "title" to jsonOf(gapPricing.gap.requirement.title),
                        "monthlyLowIdr" to (gapPricing.lowMonthly?.let { jsonOf(it.amount) } ?: JsonValue.Null),
                        "monthlyHighIdr" to (gapPricing.highMonthly?.let { jsonOf(it.amount) } ?: JsonValue.Null),
                        // Why a gap could not be priced is the most actionable line on this screen:
                        // it says which past work is missing from the ledger.
                        "refusalReason" to jsonOf(gapPricing.refusalReason)
                    )
                }
            )
        )
    ).encode()
}
