package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.sampling.samplingRoute
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind

/**
 * Kerangka yang berlaku bagi [order] di layar: kerangka beku SPK bila sudah beku, kalau belum
 * kerangka pabrik saat ini — SPK di SPK Masuk / Penentuan Alur memang belum membeku, dan
 * kolom tujuannya harus mengikuti kerangka yang akan ia jalani (TRD-FLOW-001 FR-5b).
 */
fun SamplingOrder.effectiveFrame(tenantStages: List<StageDefinition>): List<StageDefinition> =
    frozenStageFlow ?: tenantStages

/** Nama tahap untuk pesan & label; kode apa adanya bila tahap tidak ada di [frame]. */
fun List<StageDefinition>.nameOf(code: StageCode): String = firstOrNull { it.code == code }?.displayName ?: code.value

/** Tahap kerja pertama — tujuan langsung dari SPK Masuk bila alurnya tidak perlu ditinjau (rajut: Program CAM). */
fun List<StageDefinition>.firstWorkStage(): StageDefinition? = firstOrNull { it.kind == StageKind.WORK }

/** Rute SPK di atas kerangka efektifnya — pengganti `order.samplingRoute` sebelum SPK beku. */
fun SamplingOrder.routeOn(frame: List<StageDefinition>): SamplingRoute =
    samplingRoute.copy(frame = frame.map { it.code })
