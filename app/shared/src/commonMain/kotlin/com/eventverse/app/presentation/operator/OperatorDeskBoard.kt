package com.eventverse.app.presentation.operator

import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.OperatorDeskColumn
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.StageTransitionAudit
import com.eventverse.app.domain.sampling.deskColumn
import com.eventverse.app.domain.sampling.handoffsFrom
import com.eventverse.app.domain.sampling.pendingRework
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Satu kali SPK keluar dari meja ini — baris kolom Selesai dan dialog Riwayat. */
data class DeskHandoff(val order: SamplingOrder, val audit: StageTransitionAudit)

/** Isi tiga kolom satu meja operator; dirakit murni supaya bisa diuji tanpa merender apa pun. */
data class OperatorDeskBoard(
    val stage: SamplingPipelineStage,
    val queue: List<SamplingOrder>,
    val inProgress: List<SamplingOrder>,
    val doneToday: List<DeskHandoff>,
    val history: List<DeskHandoff>
)

/**
 * Menyusun papan meja [stage].
 *
 * - Antrian: kartu rework di puncak (barangnya sudah pernah lewat sini dan ditunggu tahap
 *   berikutnya), sisanya yang paling lama menunggu dulu.
 * - SPK makloon yang dirakit di vendor tidak muncul di meja Linking — barangnya tidak ada di
 *   lantai pabrik; ia kembali ke lantai lewat "vendor kembali" langsung ke Cuci.
 */
fun buildOperatorDeskBoard(
    orders: List<SamplingOrder>,
    stage: SamplingPipelineStage,
    today: LocalDate,
    timeZone: TimeZone
): OperatorDeskBoard {
    val onDesk = orders.filter { order ->
        !(stage == SamplingPipelineStage.LINKING_ASSEMBLY && order.finishingPath == FinishingPath.MAKLOON_VENDOR)
    }
    val queue = onDesk
        .filter { it.deskColumn(stage) == OperatorDeskColumn.QUEUE }
        .sortedWith(compareBy<SamplingOrder> { it.pendingRework == null }.thenBy { it.arrivedAt(stage) })
    val inProgress = onDesk
        .filter { it.deskColumn(stage) == OperatorDeskColumn.IN_PROGRESS }
        .sortedBy { it.activeWork?.startedAt }
    val history = orders
        .flatMap { order -> order.handoffsFrom(stage).map { DeskHandoff(order, it) } }
        .sortedByDescending { it.audit.at }
    return OperatorDeskBoard(
        stage = stage,
        queue = queue,
        inProgress = inProgress,
        doneToday = history.filter { it.audit.at.toLocalDateTime(timeZone).date == today },
        history = history
    )
}

/** Kapan SPK tiba di tahap ini (entri audit terakhir yang menuju ke sana), untuk urutan FIFO. */
private fun SamplingOrder.arrivedAt(stage: SamplingPipelineStage): Instant =
    stageHistory.lastOrNull { it.toStage == stage }?.at ?: updatedAt
