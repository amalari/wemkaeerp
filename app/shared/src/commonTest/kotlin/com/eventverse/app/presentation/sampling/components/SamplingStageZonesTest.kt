package com.eventverse.app.presentation.sampling.components

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.StageTrait
import kotlin.test.Test
import kotlin.test.assertEquals

/** Kolom papan dari kerangka tahap (TRD-FLOW-001 R3a). Rajut wajib identik dengan enum zona lama. */
class SamplingStageZonesTest {

    @Test
    fun knitFrame_shouldReproduceLegacyZones() {
        val zones = samplingStageZones(SamplingRoute.DEFAULT_STAGES)

        assertEquals(
            listOf("1. SPK Masuk", "2. Penentuan Alur", "3. Program CAM", "4. R&D", "5. Penyimpanan", "6. Selesai"),
            zones.map { it.title }
        )
        assertEquals(
            listOf("NEW_INTAKE", "FLOW_REVIEW", "CAM_PROGRAMMING", "MACHINE_KNITTING", "STORAGE_HOLDING", "IN_DELIVERY"),
            zones.map { it.dropStage.value }
        )
        assertEquals(
            listOf("MACHINE_KNITTING", "LINKING_ASSEMBLY", "CUCI_SOFTENER", "SETRIKA_UAP", "QC_FINISHING", "PENGEMASAN"),
            zones.single { it.kind == SamplingZoneKind.RND }.stages.map { it.value }
        )
        assertEquals(listOf("IN_DELIVERY", "ACC_APPROVED"), zones.last().stages.map { it.value })
        assertEquals(listOf(true, true, true, false, true, true), zones.map { it.showActions })
    }

    @Test
    fun frameWithoutPrepStage_shouldDropZoneAndRenumber() {
        fun stage(c: String, kind: StageKind, vararg t: StageTrait) =
            StageDefinition(StageCode(c), c, kind, GarmentSlots.CUSTOM_EXTENSION, t.toSet(), shortLabel = c.take(4))
        val frame = listOf(
            stage("NEW_INTAKE", StageKind.ENTRY_ANCHOR),
            stage("FLOW_REVIEW", StageKind.ENTRY_ANCHOR),
            stage("HOOPING", StageKind.WORK, StageTrait.OPERATOR_DESK),
            stage("MACHINE_EMBROIDERY", StageKind.WORK, StageTrait.OPERATOR_DESK),
            stage("STORAGE_HOLDING", StageKind.EXIT_ANCHOR),
            stage("IN_DELIVERY", StageKind.EXIT_ANCHOR),
            stage("ACC_APPROVED", StageKind.EXIT_ANCHOR)
        )

        val zones = samplingStageZones(frame)

        assertEquals(listOf("1. SPK Masuk", "2. Penentuan Alur", "3. R&D", "4. Penyimpanan", "5. Selesai"), zones.map { it.title })
        assertEquals("HOOP & MACH", zones[2].subtitle)
        assertEquals(StageCode("HOOPING"), zones[2].dropStage)
    }
}
