package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.StageTrait
import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import com.eventverse.app.shared.sampling.StageWorkInputCodec
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.*

/**
 * Bukti TRD-FLOW-001 R2: SPK di kerangka **bordir** berjalan melewati aturan domain tanpa sekali
 * pun menyentuh jalur enum rajut. Setiap pembacaan `pipelineStage` di jalur ini akan melempar
 * `IllegalStateException`, jadi test ini gagal bila ada aturan yang masih diam-diam memakai enum.
 */
class NonKnitStageFlowWalkTest {

    private val now = Instant.parse("2026-09-29T00:00:00Z")
    private val tenant = TenantId("ten-bordir")

    private fun code(value: String) = StageCode(value)

    private fun stage(c: String, kind: StageKind, archetype: ModuleArchetype, vararg traits: StageTrait) =
        StageDefinition(code(c), c.lowercase(), kind, archetype, traits.toSet())

    private val embroidery = TenantStageFlow(
        tenant, IndustryTemplateCode.KNIT_SWEATER,
        listOf(
            stage("NEW_INTAKE", StageKind.ENTRY_ANCHOR, ModuleArchetype.ORDER_INGESTION),
            stage("FLOW_REVIEW", StageKind.ENTRY_ANCHOR, ModuleArchetype.PRODUCT_ENGINEERING),
            stage("DIGITIZING", StageKind.WORK, ModuleArchetype.PRODUCT_ENGINEERING),
            stage("HOOPING", StageKind.WORK, ModuleArchetype.CUTTING, StageTrait.OPERATOR_DESK),
            stage("MACHINE_EMBROIDERY", StageKind.WORK, ModuleArchetype.SEWING, StageTrait.OPERATOR_DESK, StageTrait.FINISHING_FLOOR),
            stage("THREAD_TRIMMING", StageKind.WORK, ModuleArchetype.FINISHING, StageTrait.OPERATOR_DESK, StageTrait.FINISHING_FLOOR),
            stage("BORDIR_QC", StageKind.WORK, ModuleArchetype.QUALITY_CONTROL, StageTrait.OPERATOR_DESK, StageTrait.FINISHING_FLOOR),
            stage("PACKING", StageKind.WORK, ModuleArchetype.FULFILLMENT, StageTrait.OPERATOR_DESK, StageTrait.FINISHING_FLOOR),
            stage("STORAGE_HOLDING", StageKind.EXIT_ANCHOR, ModuleArchetype.FULFILLMENT),
            stage("IN_DELIVERY", StageKind.EXIT_ANCHOR, ModuleArchetype.FULFILLMENT),
            stage("ACC_APPROVED", StageKind.EXIT_ANCHOR, ModuleArchetype.FULFILLMENT)
        )
    )

    private fun orderAt(stageCode: String) = SamplingOrder(
        id = SamplingOrderId("smp_bordir_001"),
        tenantId = tenant,
        spkNumber = SpkNumber("SPK-BRD-0001"),
        clientName = "Buyer Bordir",
        styleName = "Logo Dada",
        sampleQuantity = 2,
        stageCode = code(stageCode),
        createdAt = now,
        updatedAt = now
    ).freezeStageFlow(embroidery)

    @Test
    fun advance_shouldWalkEmbroideryFrameAndAudit() {
        val atHooping = orderAt("DIGITIZING").advancePipelineStage(code("HOOPING"), now)

        assertEquals(code("HOOPING"), atHooping.stageCode)
        assertEquals(code("DIGITIZING"), atHooping.stageHistory.last().fromCode)
        assertEquals("hooping", atHooping.currentStage.displayName)
    }

    @Test
    fun operatorDesk_claimReleaseAndReworkTargets_shouldUseTraits() {
        val claimed = orderAt("THREAD_TRIMMING").startStageWork("Sari", "sari@x", now)
        assertEquals(OperatorDeskColumn.IN_PROGRESS, claimed.deskColumn(code("THREAD_TRIMMING")))
        assertNull(claimed.releaseStageWork(now).currentWork)

        assertEquals(listOf(code("HOOPING"), code("MACHINE_EMBROIDERY")), claimed.reworkTargetCodes)
        val sentBack = claimed.sendBackForRework(code("HOOPING"), "Benang lepas", DefectLiability.FACTORY_WORKMANSHIP, "qc@x", "QC", now)
        assertEquals(code("HOOPING"), sentBack.stageCode)
        assertNotNull(sentBack.pendingRework)
    }

    @Test
    fun digitizingDesk_isNotOperatorDesk_shouldRejectClaim() {
        assertFailsWith<IllegalArgumentException> { orderAt("DIGITIZING").startStageWork("Sari", "sari@x", now) }
    }

    @Test
    fun makloonAndDeposit_shouldLandOnSewingRoleThenNextOnRoute() {
        val withVendor = orderAt("HOOPING").assignMakloonVendor(MakloonVendorInfo(vendorName = "CV Bordir"), now)
        assertEquals(code("MACHINE_EMBROIDERY"), withVendor.stageCode)

        val returned = withVendor.recordVendorReturn(LocalDate(2026, 9, 30), now)
        assertEquals(code("THREAD_TRIMMING"), returned.stageCode)
    }

    @Test
    fun qcPass_shouldMoveToPackingRole() {
        val report = QcInspectionReport(
            kind = QcInspectionKind.FINISHING,
            qcResult = QcInspectionResult.PASSED,
            inspectorName = "Rina",
            inspectedAt = now
        )
        assertEquals(code("PACKING"), orderAt("BORDIR_QC").completeQcInspection(report, now).stageCode)
    }

    @Test
    fun rdProgress_shouldListOperatorDesksOfFrame() {
        val steps = orderAt("MACHINE_EMBROIDERY").rdProgress(emptyList())

        assertEquals(listOf("HOOPING", "MACHINE_EMBROIDERY", "THREAD_TRIMMING", "BORDIR_QC", "PACKING"), steps.map { it.stageCode?.value })
        assertEquals(listOf(RdStepState.DONE, RdStepState.ACTIVE), steps.take(2).map { it.state })
    }
    @Test
    fun codec_roundTrip_shouldKeepNonKnitStageAndHistory() {
        val worked = orderAt("DIGITIZING").advancePipelineStage(code("HOOPING"), now).startStageWork("Sari", "sari@x", now)

        val decoded = SamplingOrderCodec.decode(SamplingOrderCodec.encode(worked))

        assertEquals(code("HOOPING"), decoded.stageCode)
        assertEquals(code("DIGITIZING"), decoded.stageHistory.last().fromCode)
        assertEquals(code("HOOPING"), decoded.activeWork?.stageCode)
        val history = StageWorkInputCodec.decodeHistory(StageWorkInputCodec.encodeHistory(worked.stageHistory), now)
        assertEquals(worked.stageHistory.map { it.toCode }, history.map { it.toCode })
    }

    @Test
    fun resolveStoredStage_shouldAcceptOnlyCodesInFrameAndKeepLegacyAlias() {
        assertEquals(code("HOOPING"), resolveStoredStageCode("HOOPING", embroidery.stages))
        // Kode rajut tidak ada di kerangka bordir → pemanggil memakai fallback lamanya.
        assertNull(resolveStoredStageCode("MACHINE_KNITTING", embroidery.stages))
        assertNull(resolveStoredStageCode("garbage", embroidery.stages))
        assertEquals(code("CUCI_SOFTENER"), resolveStoredStageCode("FINISHING_QC", SamplingRoute.DEFAULT_STAGES))
    }
}
