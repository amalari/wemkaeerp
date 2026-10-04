package com.eventverse.app.routes

import com.eventverse.app.domain.discovery.usecases.PriceDiscoveryDraftUseCase
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

private fun jsonOfNullable(v: Long?): JsonValue = v?.let { jsonOf(it) } ?: JsonValue.Null

/** Estimasi draf untuk klien (`GET /api/discovery/drafts/{id}/price`), termasuk rincian per modul. */
internal fun priceJson(p: PriceDiscoveryDraftUseCase.DraftPricing): JsonValue.Obj = jsonObjectOf(
    "packCode" to jsonOf(p.packCode.value),
    "coveredModuleIds" to jsonArrayOf(p.coveredModuleIds.map(::jsonOf)),
    "newModuleIds" to jsonArrayOf(p.newModuleIds.map(::jsonOf)),
    "customScreenCount" to jsonOf(p.customScreenCount),
    "subscriptionMonthlyIdr" to jsonOf(p.pricing.range.subscriptionMonthly.amount),
    "gapLowMonthlyIdr" to jsonOfNullable(p.pricing.range.gapLowMonthly?.amount),
    "gapHighMonthlyIdr" to jsonOfNullable(p.pricing.range.gapHighMonthly?.amount),
    "withheld" to jsonOf(!p.pricing.range.isPublishable),
    "unpriceableGapCount" to jsonOf(p.pricing.range.unpriceableGapCount),
    "lines" to jsonArrayOf(p.lines.map { l ->
        jsonObjectOf(
            "moduleId" to jsonOf(l.moduleId), "displayName" to jsonOf(l.displayName), "covered" to jsonOf(l.covered),
            "monthlyIdr" to jsonOfNullable(l.monthlyIdr),
            "gapLowIdr" to jsonOfNullable(l.gapLowIdr), "gapHighIdr" to jsonOfNullable(l.gapHighIdr)
        )
    })
)
