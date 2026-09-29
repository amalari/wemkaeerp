package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.FlowPhase
import com.eventverse.app.domain.process.PhaseTaggableStage
import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.sampling.usecases.AdvanceSamplingStageCommand
import com.eventverse.app.domain.sampling.usecases.AdvanceSamplingStageUseCase
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.FlowLegDerivation
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.shared.process.StagePhaseTagsCodec
import com.eventverse.app.shared.sampling.SamplingOrderCodec
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tag `[Sampling ×] [Produksi ×]` pada Cuci & Setrika menentukan rute kartu sampling. */
class SamplingPhaseRouteTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val tenant = TenantId("demo-tenant")

    private val noSamplingWash = StagePhaseTags.DEFAULT.without(PhaseTaggableStage.WASHING, FlowPhase.SAMPLING)
    private val noSamplingWetWork = noSamplingWash.without(PhaseTaggableStage.PRESSING, FlowPhase.SAMPLING)

    private fun order(stage: SamplingPipelineStage, tags: StagePhaseTags? = null, qty: Int = 2) = SamplingOrder(
        id = SamplingOrderId("smp_route"),
        tenantId = tenant,
        spkNumber = SpkNumber("SPK-SMP-0300"),
        clientName = "BIANCA",
        styleName = "RIB CARDIGAN",
        status = SamplingStatus.IN_PROGRESS,
        stageCode = stage.toStageCode(),
        sampleQuantity = qty,
        stagePhaseTags = tags,
        createdAt = now,
        updatedAt = now
    )

    // --- StagePhaseTags ---

    @Test
    fun `default tags should apply every stage to both phases`() {
        PhaseTaggableStage.entries.forEach { stage ->
            FlowPhase.entries.forEach { assertTrue(StagePhaseTags.DEFAULT.appliesTo(stage, it)) }
        }
        assertTrue(StagePhaseTags.DEFAULT.skippedSamplingStages.isEmpty())
    }

    @Test
    fun `toggle twice should return to default`() {
        val back = StagePhaseTags.DEFAULT
            .toggled(PhaseTaggableStage.WASHING, FlowPhase.SAMPLING)
            .toggled(PhaseTaggableStage.WASHING, FlowPhase.SAMPLING)
        assertTrue(back.isDefault)
    }

    @Test
    fun `production tag removed should drop washing from active stations and nextAfter skips it`() {
        val tags = StagePhaseTags.DEFAULT.without(PhaseTaggableStage.WASHING, FlowPhase.PRODUCTION)
        val active = tags.productionActiveStations()

        assertFalse(WorkStationCatalog.WASHING.code in active)
        assertEquals(WorkStationCatalog.STEAM.code, WorkStationCatalog.nextAfter(WorkStationCatalog.PASANG_LABEL.code, active))
    }

    @Test
    fun `sampling tag removed should not affect production stations`() {
        assertTrue(WorkStationCatalog.WASHING.code in noSamplingWash.productionActiveStations())
    }

    // --- SamplingRoute ---

    @Test
    fun `route when cuci skipped should go from linking to setrika`() {
        val route = SamplingRoute.from(noSamplingWash)
        assertEquals(SamplingPipelineStage.SETRIKA_UAP, route.nextAfter(SamplingPipelineStage.LINKING_ASSEMBLY))
        assertFalse(SamplingPipelineStage.CUCI_SOFTENER in route)
    }

    @Test
    fun `route when cuci and setrika skipped should go from linking to qc`() {
        val route = SamplingRoute.from(noSamplingWetWork)
        assertEquals(SamplingPipelineStage.QC_FINISHING, route.nextAfter(SamplingPipelineStage.LINKING_ASSEMBLY))
        assertEquals(SamplingPipelineStage.entries.size - 2, route.stages.size)
    }

    // --- SamplingOrder ---

    @Test
    fun `advance to skipped stage should be rejected`() {
        assertFailsWith<IllegalArgumentException> {
            order(SamplingPipelineStage.LINKING_ASSEMBLY, noSamplingWash)
                .advancePipelineStage(SamplingPipelineStage.CUCI_SOFTENER, now)
        }
    }

    @Test
    fun `full linking deposit when cuci skipped should hand off to setrika`() {
        val deposit = FinishingDeposit(depositDate = LocalDate(2026, 9, 28), qtyPcs = 2, operatorName = "Rina")
        val done = order(SamplingPipelineStage.LINKING_ASSEMBLY, noSamplingWash).addFinishingDeposit(deposit, now)
        assertEquals(SamplingPipelineStage.SETRIKA_UAP, done.pipelineStage)
        assertNull(done.deskColumn(SamplingPipelineStage.CUCI_SOFTENER), "Kartu tidak boleh muncul di antrian Cuci")
    }

    @Test
    fun `full linking deposit when both wet stages skipped should hand off to qc`() {
        val deposit = FinishingDeposit(depositDate = LocalDate(2026, 9, 28), qtyPcs = 2, operatorName = "Rina")
        val done = order(SamplingPipelineStage.LINKING_ASSEMBLY, noSamplingWetWork).addFinishingDeposit(deposit, now)
        assertEquals(SamplingPipelineStage.QC_FINISHING, done.pipelineStage)
    }

    @Test
    fun `vendor return when cuci skipped should land on setrika`() {
        val returned = order(SamplingPipelineStage.LINKING_ASSEMBLY, noSamplingWash)
            .recordVendorReturn(LocalDate(2026, 9, 28), now)
        assertEquals(SamplingPipelineStage.SETRIKA_UAP, returned.pipelineStage)
    }

    @Test
    fun `rework targets should exclude skipped desks`() {
        val targets = order(SamplingPipelineStage.QC_FINISHING, noSamplingWash).reworkTargets
        assertFalse(SamplingPipelineStage.CUCI_SOFTENER in targets)
        assertTrue(SamplingPipelineStage.SETRIKA_UAP in targets)
        assertFailsWith<IllegalArgumentException> {
            order(SamplingPipelineStage.QC_FINISHING, noSamplingWash)
                .sendBackForRework(SamplingPipelineStage.CUCI_SOFTENER, "noda", com.eventverse.app.domain.pipeline.DefectLiability.FACTORY_WORKMANSHIP, "", "", now)
        }
    }

    @Test
    fun `rd progress should mark skipped stage`() {
        val steps = order(SamplingPipelineStage.MACHINE_KNITTING, noSamplingWash).rdProgress()
        assertEquals(RdStepState.SKIPPED, steps.first { it.stage == SamplingPipelineStage.CUCI_SOFTENER }.state)
    }

    @Test
    fun `phase tags when flow locked should be rejected`() {
        assertFailsWith<IllegalArgumentException> {
            order(SamplingPipelineStage.MACHINE_KNITTING).withPhaseTags(noSamplingWash, now)
        }
    }

    // --- Pembekuan saat masuk Program CAM ---

    @Test
    fun `advance into cam should freeze tenant template tags`() = runTest {
        val advanced = AdvanceSamplingStageUseCase(legsUseCase = null)(
            AdvanceSamplingStageCommand(
                order = order(SamplingPipelineStage.FLOW_REVIEW),
                target = SamplingPipelineStage.CAM_PROGRAMMING.toStageCode(),
                stages = SamplingRoute.DEFAULT_FRAME,
                processes = emptyList(),
                tenantPhaseTags = noSamplingWash,
                now = now
            )
        ).getOrThrow()

        assertEquals(noSamplingWash.normalized, advanced.stagePhaseTags)
        assertFalse(SamplingPipelineStage.CUCI_SOFTENER in advanced.samplingRoute)
    }

    @Test
    fun `advance into cam should keep design own tags over template`() = runTest {
        val own = StagePhaseTags.DEFAULT.without(PhaseTaggableStage.PRESSING, FlowPhase.SAMPLING)
        val advanced = AdvanceSamplingStageUseCase(legsUseCase = null)(
            AdvanceSamplingStageCommand(
                order = order(SamplingPipelineStage.FLOW_REVIEW, own),
                target = SamplingPipelineStage.CAM_PROGRAMMING.toStageCode(),
                stages = SamplingRoute.DEFAULT_FRAME,
                processes = emptyList(),
                tenantPhaseTags = noSamplingWash,
                now = now
            )
        ).getOrThrow()

        assertEquals(own.normalized, advanced.stagePhaseTags)
    }

    // --- Leg perpindahan barang ---

    @Test
    fun `resolve nodes when cuci skipped should drop stage but keep anchored process`() {
        val laundry = TenantOptionalProcess(
            processId = "proc-laundry",
            tenantId = tenant,
            code = "LAUNDRY",
            displayName = "Laundry",
            archetype = GarmentSlots.FINISHING,
            samplingAnchorAfter = SamplingPipelineStage.CUCI_SOFTENER.toStageCode()
        )
        val nodes = FlowLegDerivation.resolveNodes(
            SamplingPipelineStage.entries,
            listOf(laundry),
            skipped = setOf(SamplingPipelineStage.CUCI_SOFTENER)
        )

        assertFalse(FlowNodeRef.Stage(SamplingPipelineStage.CUCI_SOFTENER) in nodes)
        val linking = nodes.indexOf(FlowNodeRef.Stage(SamplingPipelineStage.LINKING_ASSEMBLY))
        assertEquals(FlowNodeRef.Process("LAUNDRY"), nodes[linking + 1])
    }

    // --- Codec ---

    @Test
    fun `codec should round trip tags and keep null distinct from default`() {
        assertEquals(noSamplingWetWork.normalized, StagePhaseTagsCodec.decode(StagePhaseTagsCodec.encode(noSamplingWetWork)))
        assertNull(StagePhaseTagsCodec.decode(StagePhaseTagsCodec.encode(null)))
        assertEquals(StagePhaseTags.DEFAULT, StagePhaseTagsCodec.decode(StagePhaseTagsCodec.encode(StagePhaseTags.DEFAULT)))
    }

    @Test
    fun `order codec should round trip stage phase tags`() {
        val original = order(SamplingPipelineStage.FLOW_REVIEW, noSamplingWash)
        val decoded = SamplingOrderCodec.decode(SamplingOrderCodec.encode(original))
        assertEquals(noSamplingWash.normalized, decoded.stagePhaseTags)
        assertNull(SamplingOrderCodec.decode(SamplingOrderCodec.encode(order(SamplingPipelineStage.FLOW_REVIEW))).stagePhaseTags)
    }
}
