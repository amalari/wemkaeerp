package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.stageflow.StageCode

/**
 * Gerbang antar tahap: transisi CAM -> Mesin Rajut hanya sah setelah section wajib
 * lembar CAM (PROGRAM, INSTRUKSI PANAH, RUMUS POLA) memiliki minimal satu baris terisi.
 *
 * Dipisah dari [SamplingOrder] karena file entity sudah di ambang hard limit core; gerbang
 * adalah aturan murni yang hanya membaca isi agregatnya sendiri.
 */
internal fun SamplingOrder.requireStageGate(target: StageCode) {
    // Lembar CAM khas rajut; lembar wajib per tahap untuk kerangka lain menjadi data di Tahap 3.
    if (stageCode != CAM || target != KNITTING) return
    val camInput = stageInputFor(SamplingPipelineStage.CAM_PROGRAMMING)
    val missing = StageSectionNames.CAM_REQUIRED.filter { name ->
        camInput?.section(name)?.hasFilledRow != true
    }
    require(missing.isEmpty()) {
        "Lembar Program CAM belum lengkap — isi dulu: ${missing.joinToString(", ")}"
    }
}

private val CAM = SamplingPipelineStage.CAM_PROGRAMMING.toStageCode()
private val KNITTING = SamplingPipelineStage.MACHINE_KNITTING.toStageCode()
