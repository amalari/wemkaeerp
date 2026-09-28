package com.eventverse.app.presentation.operator

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.sampling.knitDefinition
import com.eventverse.app.domain.sampling.resolveAccessibleOperatorDesks
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.StageTrait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Meja operator di kerangka **bordir** (TRD-FLOW-001 R3b): aksi selesai, label, dan akses meja
 * diturunkan dari peran/trait tahap — bukan dari nama tahap rajut.
 */
class OperatorNonKnitDeskTest {

    private fun stage(c: String, kind: StageKind, archetype: ModuleArchetype, short: String, vararg t: StageTrait) =
        StageDefinition(StageCode(c), c.lowercase(), kind, archetype, t.toSet(), shortLabel = short)

    private val hooping = stage("HOOPING", StageKind.WORK, ModuleArchetype.CUTTING, "Hoop", StageTrait.OPERATOR_DESK)
    private val embroidery = stage("MACHINE_EMBROIDERY", StageKind.WORK, ModuleArchetype.SEWING, "Bordir", StageTrait.OPERATOR_DESK)
    private val trimming = stage("THREAD_TRIMMING", StageKind.WORK, ModuleArchetype.FINISHING, "Trim", StageTrait.OPERATOR_DESK)
    private val qc = stage("BORDIR_QC", StageKind.WORK, ModuleArchetype.QUALITY_CONTROL, "QC", StageTrait.OPERATOR_DESK)
    private val packing = stage("PACKING", StageKind.WORK, ModuleArchetype.FULFILLMENT, "Kemas", StageTrait.OPERATOR_DESK)
    private val frame = listOf(
        stage("NEW_INTAKE", StageKind.ENTRY_ANCHOR, ModuleArchetype.ORDER_INGESTION, "Draft"),
        stage("DIGITIZING", StageKind.WORK, ModuleArchetype.PRODUCT_ENGINEERING, "Digit"),
        hooping, embroidery, trimming, qc, packing,
        stage("STORAGE_HOLDING", StageKind.EXIT_ANCHOR, ModuleArchetype.FULFILLMENT, "Disimpan"),
        stage("ACC_APPROVED", StageKind.EXIT_ANCHOR, ModuleArchetype.FULFILLMENT, "Selesai")
    )
    private val route = SamplingRoute(frame = frame.map { it.code })

    @Test
    fun finishAction_shouldFollowRolesOnEmbroideryFrame() {
        assertEquals(DeskFinishAction.Handoff(embroidery), hooping.finishAction(frame, route))
        assertEquals(DeskFinishAction.Deposit, embroidery.finishAction(frame, route))
        assertEquals(DeskFinishAction.Handoff(qc), trimming.finishAction(frame, route))
        assertEquals(DeskFinishAction.QcInspection, qc.finishAction(frame, route))
        assertEquals(DeskFinishAction.Store, packing.finishAction(frame, route))
    }

    @Test
    fun finishAction_onKnitFrame_shouldMatchLegacyTable() {
        val knit = SamplingRoute.DEFAULT_STAGES
        fun of(s: SamplingPipelineStage) = s.knitDefinition().finishAction(knit)
        assertIs<DeskFinishAction.Worksheet>(of(SamplingPipelineStage.MACHINE_KNITTING))
        assertEquals(StageCode("LINKING_ASSEMBLY"), (of(SamplingPipelineStage.MACHINE_KNITTING) as DeskFinishAction.Worksheet).target)
        assertEquals(DeskFinishAction.Deposit, of(SamplingPipelineStage.LINKING_ASSEMBLY))
        assertEquals(StageCode("SETRIKA_UAP"), (of(SamplingPipelineStage.CUCI_SOFTENER) as DeskFinishAction.Handoff).target.code)
        assertEquals(DeskFinishAction.QcInspection, of(SamplingPipelineStage.QC_FINISHING))
        assertEquals(DeskFinishAction.Store, of(SamplingPipelineStage.PENGEMASAN))
    }

    @Test
    fun knitDeskLabels_shouldStayFloorTerms() {
        assertEquals(
            listOf("Rajut", "Linking", "Cuci", "Setrika", "QC", "Kemas"),
            SamplingRoute.DEFAULT_STAGES.filter { it.has(StageTrait.OPERATOR_DESK) }.map { it.deskLabel }
        )
    }

    @Test
    fun deskAccess_shouldValidateAgainstTenantDesks() {
        val desks = frame.filter { it.has(StageTrait.OPERATOR_DESK) }
        val dept = ModuleAccessConfig(AccessLevel.OPERATE, allowedDesks = setOf("MACHINE_EMBROIDERY", "MACHINE_KNITTING"))

        assertEquals(setOf(StageCode("MACHINE_EMBROIDERY")), resolveAccessibleOperatorDesks(false, dept, desks))
    }

    @Test
    fun rework_shouldBeOfferedOnlyWhenAnEarlierDeskExists() {
        assertFalse(hooping.hasEarlierDesk(frame))
        assertTrue(qc.hasEarlierDesk(frame))
    }
}
