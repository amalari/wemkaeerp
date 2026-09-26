package com.eventverse.app.presentation.sampling.components

import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.StageSectionNames

/** Konfigurasi section input untuk satu transisi tahap. */
data class StageSectionSpec(val sectionName: String, val hint: String)

/**
 * Lembar Program CAM — dipakai dua momen: saat SPK Baru maju ke tahap Program CAM
 * (persiapan tim sampling) dan sebagai gerbang saat Program CAM masuk Mesin Rajut.
 */
val CAM_SECTION_SPECS = listOf(
    StageSectionSpec(StageSectionNames.PROGRAM, "mis. DEPAN : BIAN-D"),
    StageSectionSpec(StageSectionNames.FEEDER_INSTRUCTIONS, "mis. 1 RIB STRIPE 1 PLAY ( HITAM )"),
    StageSectionSpec(StageSectionNames.TENSELITY, "mis. 1 BS POLY"),
    StageSectionSpec(StageSectionNames.PATTERN_FORMULAS, "mis. P BADAN : 2.94 K")
)

/** Lembar hasil rajut — dipakai transisi Mesin Rajut -> Finishing. */
val KNITTING_SECTION_SPECS = listOf(
    StageSectionSpec(StageSectionNames.PANEL_WEIGHTS, "mis. DEPAN : 117 GR"),
    StageSectionSpec(StageSectionNames.PANEL_MINUTES, "mis. DEPAN : 37 MENIT"),
    StageSectionSpec(StageSectionNames.SIZE_CHART, "mis. P BADAN : 55 CM"),
    StageSectionSpec(StageSectionNames.TENSELITY, "mis. 1 BS POLY")
)

/** Section + hint per transisi — sumber kebenaran satu-satunya untuk dialog tahap. */
fun stageSectionsFor(targetStage: SamplingPipelineStage): List<StageSectionSpec> =
    when (targetStage) {
        SamplingPipelineStage.CAM_PROGRAMMING, SamplingPipelineStage.MACHINE_KNITTING -> CAM_SECTION_SPECS
        SamplingPipelineStage.LINKING_ASSEMBLY -> KNITTING_SECTION_SPECS
        else -> emptyList()
    }
