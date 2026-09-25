package com.eventverse.app.domain.sampling

/**
 * Gerbang antar tahap: transisi CAM -> Mesin Rajut hanya sah setelah section wajib
 * lembar CAM (PROGRAM, INSTRUKSI PANAH, RUMUS POLA) memiliki minimal satu baris terisi.
 *
 * Dipisah dari [SamplingOrder] karena file entity sudah di ambang hard limit core; gerbang
 * adalah aturan murni yang hanya membaca isi agregatnya sendiri.
 */
internal fun SamplingOrder.requireStageGate(target: SamplingPipelineStage) {
    if (pipelineStage != SamplingPipelineStage.CAM_PROGRAMMING || target != SamplingPipelineStage.MACHINE_KNITTING) return
    val camInput = stageInputFor(SamplingPipelineStage.CAM_PROGRAMMING)
    val missing = StageSectionNames.CAM_REQUIRED.filter { name ->
        camInput?.section(name)?.hasFilledRow != true
    }
    require(missing.isEmpty()) {
        "Lembar Program CAM belum lengkap — isi dulu: ${missing.joinToString(", ")}"
    }
}
