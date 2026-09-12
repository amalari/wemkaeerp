package com.eventverse.app.routes.dto

import com.eventverse.app.domain.moduledev.BuildFeatureVector
import com.eventverse.app.domain.moduledev.BuildPhase
import com.eventverse.app.domain.moduledev.BuildType
import com.eventverse.app.domain.moduledev.EffortRole
import com.eventverse.app.domain.moduledev.EstimationOutcome
import com.eventverse.app.domain.moduledev.ModuleBuildRecord
import com.eventverse.app.domain.moduledev.ModuleCatalogEntry
import com.eventverse.app.domain.moduledev.ModuleCustomizationRequest
import com.eventverse.app.domain.moduledev.ModulePricingQuote
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.domain.moduledev.usecases.ModuleBuildEstimation
import com.eventverse.app.domain.moduledev.usecases.TenantBillingPreview
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * HTTP boundary mapping for the module development ledger.
 *
 * Written against the project's hand-rolled JSON helpers, like every other DTO here — there is no
 * kotlinx-serialization dependency in this build.
 *
 * **Cost figures never appear in a tenant-facing payload.** [customizationRequestToJson] and
 * [billingPreviewToJson] expose prices only; hours, rates and build cost stay in the admin
 * responses. The database enforces the same split by keeping them on tables with no `tenant_id`.
 */
object ModuleDevDto {

    // ---- Requests in ---------------------------------------------------------

    data class CreateBuildBody(
        val buildId: String,
        val catalogEntryId: String,
        val buildType: BuildType,
        val requirementText: String,
        val features: BuildFeatureVector,
        val clarityScore: Int?,
        val customizationRequestId: String?
    )

    fun readCreateBuild(rawBody: String): CreateBuildBody? {
        val obj = JsonParser.parseObjectOrNull(rawBody) ?: return null
        val buildId = obj.string("buildId")?.takeIf { it.isNotBlank() } ?: return null
        val catalogEntryId = obj.string("catalogEntryId")?.takeIf { it.isNotBlank() } ?: return null
        val requirementText = obj.string("requirementText")?.takeIf { it.isNotBlank() } ?: return null
        val buildType = obj.string("buildType")?.let { BuildType.fromCode(it) }
            ?: BuildType.CUSTOMIZATION

        return CreateBuildBody(
            buildId = buildId,
            catalogEntryId = catalogEntryId,
            buildType = buildType,
            requirementText = requirementText,
            features = readFeatures(obj.obj("features")),
            clarityScore = obj.int("clarityScore"),
            customizationRequestId = obj.string("customizationRequestId")
        )
    }

    private fun readFeatures(obj: JsonValue.Obj?): BuildFeatureVector {
        if (obj == null) return BuildFeatureVector.EMPTY
        return BuildFeatureVector(
            entityCount = obj.int("entityCount") ?: 0,
            useCaseCount = obj.int("useCaseCount") ?: 0,
            screenCount = obj.int("screenCount") ?: 0,
            apiEndpointCount = obj.int("apiEndpointCount") ?: 0,
            dbTableCount = obj.int("dbTableCount") ?: 0,
            reportCount = obj.int("reportCount") ?: 0,
            integrationCount = obj.int("integrationCount") ?: 0,
            targetPlatformCount = (obj.int("targetPlatformCount") ?: 1).coerceAtLeast(1),
            affectedExistingModuleCount = obj.int("affectedExistingModuleCount") ?: 0,
            requiresCustomFormula = obj.boolean("requiresCustomFormula") ?: false,
            requiresExternalIntegration = obj.boolean("requiresExternalIntegration") ?: false,
            requiresRealtime = obj.boolean("requiresRealtime") ?: false,
            requiresOfflineSync = obj.boolean("requiresOfflineSync") ?: false,
            requiresFileUpload = obj.boolean("requiresFileUpload") ?: false,
            requiresNewDesignComponent = obj.boolean("requiresNewDesignComponent") ?: false
        )
    }

    data class EffortBody(
        val entryId: String,
        val role: EffortRole,
        val phase: BuildPhase,
        /** Hours. The API has no day-based field, by design. */
        val hours: Double,
        val hourlyRateIdr: Long,
        val performedBy: String?,
        val note: String?
    )

    fun readEffort(rawBody: String): EffortBody? {
        val obj = JsonParser.parseObjectOrNull(rawBody) ?: return null
        val entryId = obj.string("entryId")?.takeIf { it.isNotBlank() } ?: return null
        val role = obj.string("role")?.let { EffortRole.fromCode(it) } ?: return null
        val phase = obj.string("phase")?.let { BuildPhase.fromCode(it) } ?: return null
        val hours = obj.double("hours") ?: return null
        val rate = obj.long("hourlyRateIdr") ?: return null
        if (hours <= 0.0 || rate < 0) return null

        return EffortBody(entryId, role, phase, hours, rate, obj.string("performedBy"), obj.string("note"))
    }

    data class CompleteBody(
        val revisionRoundCount: Int,
        val leadTimeDays: Int?,
        val discoveredScopeDelta: String?,
        val retrospectiveNotes: String?,
        val gitRef: String?
    )

    fun readComplete(rawBody: String): CompleteBody {
        val obj = JsonParser.parseObjectOrNull(rawBody)
        return CompleteBody(
            revisionRoundCount = obj?.int("revisionRoundCount") ?: 0,
            leadTimeDays = obj?.int("leadTimeDays"),
            discoveredScopeDelta = obj?.string("discoveredScopeDelta"),
            retrospectiveNotes = obj?.string("retrospectiveNotes"),
            gitRef = obj?.string("gitRef")
        )
    }

    data class QuoteBody(
        val quoteId: String,
        val buildId: String,
        val expectedTenantCount: Int,
        val amortizationMonths: Int,
        val marginPercent: Percentage,
        val monthlyMaintenancePercent: Percentage,
        val monthlyInfraCost: MoneyIdr,
        val discountPercent: Percentage,
        val tenantId: String?,
        val blendedHourlyRateIdr: MoneyIdr?
    )

    fun readQuote(rawBody: String): QuoteBody? {
        val obj = JsonParser.parseObjectOrNull(rawBody) ?: return null
        val quoteId = obj.string("quoteId")?.takeIf { it.isNotBlank() } ?: return null
        val buildId = obj.string("buildId")?.takeIf { it.isNotBlank() } ?: return null

        return runCatching {
            QuoteBody(
                quoteId = quoteId,
                buildId = buildId,
                expectedTenantCount = obj.int("expectedTenantCount") ?: 1,
                amortizationMonths = obj.int("amortizationMonths") ?: 24,
                marginPercent = Percentage(obj.double("marginPercent") ?: 0.0),
                monthlyMaintenancePercent = Percentage(obj.double("monthlyMaintenancePercent") ?: 0.0),
                monthlyInfraCost = MoneyIdr(obj.long("monthlyInfraCostIdr") ?: 0L),
                discountPercent = Percentage(obj.double("discountPercent") ?: 0.0),
                tenantId = obj.string("tenantId"),
                blendedHourlyRateIdr = obj.long("blendedHourlyRateIdr")?.let { MoneyIdr(it) }
            )
        }.getOrNull()
    }

    data class CustomizationRequestBody(
        val requestId: String,
        val title: String,
        val descriptionRaw: String,
        val catalogEntryId: String?
    )

    fun readCustomizationRequest(rawBody: String): CustomizationRequestBody? {
        val obj = JsonParser.parseObjectOrNull(rawBody) ?: return null
        val requestId = obj.string("requestId")?.takeIf { it.isNotBlank() } ?: return null
        val title = obj.string("title")?.takeIf { it.isNotBlank() } ?: return null
        val description = obj.string("descriptionRaw")?.takeIf { it.isNotBlank() } ?: return null
        return CustomizationRequestBody(requestId, title, description, obj.string("catalogEntryId"))
    }

    // ---- Responses out -------------------------------------------------------

    fun catalogToJson(entries: List<ModuleCatalogEntry>): String = jsonObjectOf(
        "modules" to jsonArrayOf(entries.map { entry ->
            jsonObjectOf(
                "id" to jsonOf(entry.id.value),
                "moduleId" to jsonOf(entry.moduleId),
                "archetypeCode" to jsonOf(entry.archetypeCode),
                "displayName" to jsonOf(entry.displayName),
                "lifecycleStatus" to jsonOf(entry.lifecycleStatus.code),
                "complexityTier" to jsonOf(entry.complexityTier?.code),
                "isCustomPlugin" to jsonOf(entry.isCustomPlugin),
                "baseMonthlyPriceIdr" to (entry.baseMonthlyPriceIdr?.let { jsonOf(it.amount) }
                    ?: JsonValue.Null),
                "isBillable" to jsonOf(entry.isBillable)
            )
        })
    ).encode()

    /**
     * The admin view of an estimate.
     *
     * A refusal is reported as `estimated: false` with a reason, never as zero hours — the whole
     * point of the confidence gate is that "we do not know" must not be mistakable for a low
     * number.
     */
    fun estimationToJson(estimation: ModuleBuildEstimation): String {
        val record = estimation.record
        val outcomeJson = when (val outcome = estimation.outcome) {
            is EstimationOutcome.Estimated -> jsonObjectOf(
                "estimated" to jsonOf(true),
                "p50Hours" to jsonOf(outcome.p50Hours.hours),
                "p90Hours" to jsonOf(outcome.p90Hours.hours),
                "sizePoints" to jsonOf(outcome.sizePoints.value),
                "medianProductivity" to jsonOf(outcome.medianProductivity),
                "clarityMultiplier" to jsonOf(outcome.clarityMultiplier),
                "confidence" to jsonOf(outcome.confidence.code),
                "nearestSimilarity" to jsonOf(outcome.nearestSimilarity),
                "neighborIds" to jsonArrayOf(outcome.neighborIds.map { jsonOf(it) })
            )
            is EstimationOutcome.InsufficientEvidence -> jsonObjectOf(
                "estimated" to jsonOf(false),
                "reason" to jsonOf(outcome.reason),
                "nearestSimilarity" to (outcome.nearestSimilarity?.let { jsonOf(it) }
                    ?: JsonValue.Null),
                "usableNeighborCount" to jsonOf(outcome.usableNeighborCount),
                "requiresHumanEstimate" to jsonOf(true)
            )
        }

        return jsonObjectOf(
            "build" to JsonParser.parseObject(buildToJson(record)),
            "estimate" to outcomeJson
        ).encode()
    }

    fun buildToJson(record: ModuleBuildRecord): String = jsonObjectOf(
        "id" to jsonOf(record.id.value),
        "catalogEntryId" to jsonOf(record.catalogEntryId.value),
        "buildType" to jsonOf(record.buildType.code),
        "status" to jsonOf(record.status.code),
        "archetypeCode" to jsonOf(record.archetypeCode),
        "requirementText" to jsonOf(record.requirementText),
        "sizePoints" to jsonOf(record.sizePoints.value),
        "sizePointsWeightsVersion" to jsonOf(record.sizePointsWeightsVersion),
        "clarityScore" to (record.clarityScore?.let { jsonOf(it) } ?: JsonValue.Null),
        "estimatedHours" to (record.estimatedHours?.let { jsonOf(it.hours) } ?: JsonValue.Null),
        "estimatedHoursP90" to (record.estimatedHoursP90?.let { jsonOf(it.hours) } ?: JsonValue.Null),
        "estimatedBy" to jsonOf(record.estimatedBy?.code),
        "estimatorRef" to jsonOf(record.estimatorRef),
        "estimateConfidence" to jsonOf(record.estimateConfidence?.code),
        "retrievedNeighborIds" to jsonArrayOf(record.retrievedNeighborIds.map { jsonOf(it) }),
        "actualHours" to (record.actualHours?.let { jsonOf(it.hours) } ?: JsonValue.Null),
        "reworkHours" to jsonOf(record.reworkHours.hours),
        "revisionRoundCount" to jsonOf(record.revisionRoundCount),
        "leadTimeDays" to (record.leadTimeDays?.let { jsonOf(it) } ?: JsonValue.Null),
        "effortSource" to jsonOf(record.effortSource.code),
        "totalBuildCostIdr" to (record.totalBuildCostIdr?.let { jsonOf(it.amount) } ?: JsonValue.Null),
        "blendedHourlyRateIdr" to (record.blendedHourlyRateIdr?.let { jsonOf(it.amount) } ?: JsonValue.Null),
        "estimateVariancePercent" to (record.estimateVariancePercent?.let { jsonOf(it) } ?: JsonValue.Null),
        "discoveredScopeDelta" to jsonOf(record.discoveredScopeDelta)
    ).encode()

    fun quoteToJson(quote: ModulePricingQuote): String = jsonObjectOf(
        "id" to jsonOf(quote.id.value),
        "catalogEntryId" to jsonOf(quote.catalogEntryId.value),
        "buildRecordId" to jsonOf(quote.buildRecordId?.value),
        "tenantId" to jsonOf(quote.tenantId?.value),
        "status" to jsonOf(quote.status.code),
        "monthlyPriceIdr" to jsonOf(quote.result.monthlyPrice.amount),
        "oneTimeFeeIdr" to jsonOf(quote.result.oneTimeFee.amount),
        "pricingModelVersion" to jsonOf(quote.inputs.pricingModelVersion),
        "inputs" to jsonObjectOf(
            "basisHours" to jsonOf(quote.inputs.basisHours.hours),
            "buildCostIdr" to jsonOf(quote.inputs.buildCost.amount),
            "expectedTenantCount" to jsonOf(quote.inputs.expectedTenantCount),
            "amortizationMonths" to jsonOf(quote.inputs.amortizationMonths),
            "marginPercent" to jsonOf(quote.inputs.marginPercent.value),
            "monthlyMaintenancePercent" to jsonOf(quote.inputs.monthlyMaintenancePercent.value),
            "monthlyInfraCostIdr" to jsonOf(quote.inputs.monthlyInfraCost.amount),
            "discountPercent" to jsonOf(quote.inputs.discountPercent.value)
        ),
        "breakdown" to JsonValue.Obj(
            quote.result.breakdown.mapValues { (_, value) -> jsonOf(value) }
        )
    ).encode()

    /** Tenant-facing: price and status only, never our cost. */
    fun customizationRequestToJson(requests: List<ModuleCustomizationRequest>): String =
        jsonObjectOf(
            "requests" to jsonArrayOf(requests.map { request ->
                jsonObjectOf(
                    "id" to jsonOf(request.id.value),
                    "title" to jsonOf(request.title),
                    "descriptionRaw" to jsonOf(request.descriptionRaw),
                    "catalogEntryId" to jsonOf(request.catalogEntryId?.value),
                    "status" to jsonOf(request.status.code),
                    "activeQuoteId" to jsonOf(request.activeQuoteId?.value),
                    "rejectionReason" to jsonOf(request.rejectionReason)
                )
            })
        ).encode()

    /** Tenant-facing: what they pay and for which modules. No hours, no rates, no build cost. */
    fun billingPreviewToJson(preview: TenantBillingPreview): String = jsonObjectOf(
        "tenantId" to jsonOf(preview.tenantId.value),
        "subscriptionTotalIdr" to jsonOf(preview.subscriptionTotal.amount),
        "customizationTotalIdr" to jsonOf(preview.customizationTotal.amount),
        "monthlyTotalIdr" to jsonOf(preview.monthlyTotal.amount),
        "lines" to jsonArrayOf(preview.lines.map { line ->
            jsonObjectOf(
                "moduleId" to jsonOf(line.moduleId),
                "displayName" to jsonOf(line.displayName),
                "monthlyPriceIdr" to jsonOf(line.monthlyPrice.amount),
                "kind" to jsonOf(line.kind.name)
            )
        })
    ).encode()
}
