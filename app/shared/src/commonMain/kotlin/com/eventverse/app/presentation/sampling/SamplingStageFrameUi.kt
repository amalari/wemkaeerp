package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.sampling.samplingRoute
import com.eventverse.app.domain.sampling.stageFrame
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind

/**
 * Kerangka yang berlaku bagi [order] di layar (TRD-FLOW-001 FR-5b):
 * - sudah beku → kerangka bekunya;
 * - belum beku & masih di tahap masuk → kerangka pabrik saat ini, karena itulah yang akan ia jalani;
 * - belum beku tapi sudah di lantai → SPK lama sebelum V73, dibuat di atas kerangka rajut; ia
 *   tetap di kerangka itu supaya perubahan kerangka pabrik tidak membuat kartunya kehilangan kolom.
 */
fun SamplingOrder.effectiveFrame(tenantStages: List<StageDefinition>): List<StageDefinition> {
    frozenStageFlow?.let { return it }
    val atEntry = tenantStages.firstOrNull { it.code == stageCode }?.kind == StageKind.ENTRY_ANCHOR
    return if (atEntry) tenantStages else stageFrame
}

/** Nama tahap untuk pesan & label; kode apa adanya bila tahap tidak ada di [frame]. */
fun List<StageDefinition>.nameOf(code: StageCode): String = firstOrNull { it.code == code }?.displayName ?: code.value

/** Tahap kerja pertama — tujuan langsung dari SPK Masuk bila alurnya tidak perlu ditinjau (rajut: Program CAM). */
fun List<StageDefinition>.firstWorkStage(): StageDefinition? = firstOrNull { it.kind == StageKind.WORK }

/** Rute SPK di atas kerangka efektifnya — pengganti `order.samplingRoute` sebelum SPK beku. */
fun SamplingOrder.routeOn(frame: List<StageDefinition>): SamplingRoute =
    samplingRoute.copy(frame = frame.map { it.code })
