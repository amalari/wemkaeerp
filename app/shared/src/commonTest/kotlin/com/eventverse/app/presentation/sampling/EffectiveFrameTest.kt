package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.sampling.SpkNumber
import com.eventverse.app.domain.sampling.freezeStageFlow
import com.eventverse.app.domain.stageflow.IndustryStageTemplates
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Kerangka efektif SPK di layar (TRD-FLOW-001 FR-5b), termasuk SPK lama yang belum pernah beku. */
class EffectiveFrameTest {

    private val now = Instant.parse("2026-09-29T00:00:00Z")
    private val tenant = TenantId("ten-eff")
    private val embroidery = IndustryStageTemplates.stagesOf(IndustryTemplateCode.EMBROIDERY)

    private fun order(stage: String) = SamplingOrder(
        id = SamplingOrderId("smp_eff"), tenantId = tenant, spkNumber = SpkNumber("SPK-EFF-1"),
        clientName = "B", styleName = "S", stageCode = StageCode(stage), createdAt = now, updatedAt = now
    )

    @Test
    fun unfrozenAtEntry_shouldFollowTenantFrame() {
        assertEquals(embroidery, order("FLOW_REVIEW").effectiveFrame(embroidery))
    }

    @Test
    fun unfrozenOnFloor_shouldKeepKnitFrameItWasCreatedOn() {
        assertEquals(SamplingRoute.DEFAULT_STAGES, order("MACHINE_KNITTING").effectiveFrame(embroidery))
    }

    @Test
    fun frozen_shouldAlwaysUseItsOwnFrame() {
        val frozen = order("HOOPING").freezeStageFlow(IndustryStageTemplates.instantiate(tenant, IndustryTemplateCode.EMBROIDERY))
        assertEquals(embroidery, frozen.effectiveFrame(SamplingRoute.DEFAULT_STAGES))
    }
}
