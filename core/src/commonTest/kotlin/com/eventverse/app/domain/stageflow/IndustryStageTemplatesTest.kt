package com.eventverse.app.domain.stageflow

import com.eventverse.app.domain.process.PhaseTaggableStage
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.parseLegacyStageCodeOrNull
import com.eventverse.app.domain.sampling.toSamplingStageOrNull
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.usecases.GetTenantStageFlowUseCase
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.stageflow.TenantStageFlowCodec
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Paritas `KNIT_SWEATER` ↔ enum [SamplingPipelineStage]. Selama enum masih ada (TRD-FLOW-001
 * Tahap 1–2), setiap perbedaan di sini berarti perilaku berubah diam-diam untuk semua tenant.
 */
class IndustryStageTemplatesTest {

    private val tenantId = TenantId("ten-stage-flow")
    private val knit = IndustryStageTemplates.instantiate(tenantId, IndustryTemplateCode.KNIT_SWEATER)

    @Test
    fun knitTemplate_codesAndOrder_shouldMatchLegacyEnum() {
        assertEquals(SamplingPipelineStage.entries.map { it.name }, knit.stages.map { it.code.value })
        assertEquals(SamplingPipelineStage.entries.map { it.displayName }, knit.stages.map { it.displayName })
    }

    @Test
    fun knitTemplate_next_shouldMatchLegacyNextStage() {
        SamplingPipelineStage.entries.forEach { legacy ->
            assertEquals(legacy.nextStage?.name, knit.next(legacy.toStageCode())?.code?.value, "next of $legacy")
        }
    }

    @Test
    fun knitTemplate_traits_shouldMatchLegacyProperties() {
        SamplingPipelineStage.entries.forEach { legacy ->
            val stage = assertNotNull(knit.find(legacy.toStageCode()))
            assertEquals(legacy.isOnFinishingFloor, stage.has(StageTrait.FINISHING_FLOOR), "finishing floor $legacy")
            assertEquals(legacy.isWetOrPressWork, stage.has(StageTrait.WET_OR_PRESS), "wet/press $legacy")
            assertEquals(
                PhaseTaggableStage.forSamplingStage(legacy) != null,
                stage.has(StageTrait.PHASE_TAGGABLE),
                "phase taggable $legacy"
            )
        }
    }

    @Test
    fun bridge_legacyAlias_shouldResolveThroughEnumParser() {
        assertEquals(SamplingPipelineStage.CUCI_SOFTENER, StageCode("FINISHING_QC").toSamplingStageOrNull())
        assertNull(StageCode("MACHINE_EMBROIDERY").toSamplingStageOrNull())
    }

    @Test
    fun legacyParser_shouldKeepEnumRules() {
        assertEquals(StageCode("CUCI_SOFTENER"), parseLegacyStageCodeOrNull("FINISHING_QC"))
        assertEquals(StageCode("SETRIKA_UAP"), parseLegacyStageCodeOrNull("SETRIKA_UAP"))
        assertNull(parseLegacyStageCodeOrNull("MACHINE_EMBROIDERY"))
        assertNull(parseLegacyStageCodeOrNull(null))
    }

    @Test
    fun flow_whenWorkStageBeforeEntryAnchor_shouldBeRejected() {
        val shuffled = listOf(knit.stages[2], knit.stages[0]) + knit.stages.drop(3)
        assertFailsWith<IllegalArgumentException> { knit.copy(stages = shuffled) }
    }

    @Test
    fun flow_whenDuplicateCode_shouldBeRejected() {
        assertFailsWith<IllegalArgumentException> { knit.copy(stages = knit.stages + knit.stages.last()) }
    }

    @Test
    fun stageCode_whenContainsSeparatorOrLowercase_shouldBeRejected() {
        assertFailsWith<IllegalArgumentException> { StageCode("STAGE:X") }
        assertFailsWith<IllegalArgumentException> { StageCode("bordir") }
    }

    @Test
    fun codec_roundTrip_shouldPreserveFlow() {
        assertEquals(knit, TenantStageFlowCodec.decode(tenantId, TenantStageFlowCodec.encode(knit)))
    }

    @Test
    fun getUseCase_whenTenantHasNoFlow_shouldProvisionKnitOnce() = runTest {
        val repository = FakeTenantStageFlowRepository()
        val get = GetTenantStageFlowUseCase(repository)

        val first = get(tenantId).getOrThrow()
        val second = get(tenantId).getOrThrow()

        assertEquals(knit, first)
        assertEquals(first, second)
        assertEquals(1, repository.saveCount)
    }
}

class FakeTenantStageFlowRepository : TenantStageFlowRepository {
    private val flows = mutableMapOf<TenantId, TenantStageFlow>()
    var saveCount = 0
        private set

    override suspend fun findByTenantId(tenantId: TenantId): TenantStageFlow? = flows[tenantId]

    override suspend fun save(flow: TenantStageFlow): Result<TenantStageFlow> {
        saveCount++
        flows[flow.tenantId] = flow
        return Result.success(flow)
    }
}
