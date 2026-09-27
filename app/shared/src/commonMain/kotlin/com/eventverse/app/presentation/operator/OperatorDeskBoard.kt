package com.eventverse.app.presentation.operator

import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.OperatorDeskColumn
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.StageTransitionAudit
import com.eventverse.app.domain.sampling.arrivalBefore
import com.eventverse.app.domain.sampling.deskColumn
import com.eventverse.app.domain.sampling.handoffsFrom
import com.eventverse.app.domain.sampling.pendingRework
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Satu kali SPK keluar dari meja ini — baris kolom Selesai dan dialog Riwayat.
 *
 * Tiga titik waktu memisahkan dua angka yang sering tertukar: **tunggu** (tiba → mulai) adalah
 * antrean, **kerja** (mulai → selesai) adalah kecepatan operator. Menjumlahkannya jadi satu
 * membuat meja yang kebanjiran kiriman terlihat lambat padahal operatornya cepat.
 */
data class DeskHandoff(
    val order: SamplingOrder,
    val audit: StageTransitionAudit,
    val arrivedAt: Instant? = null
) {
    val waitMinutes: Long? get() = minutesBetween(arrivedAt, audit.workStartedAt)
    val workMinutes: Long? get() = minutesBetween(audit.workStartedAt, audit.at)
}

private fun minutesBetween(from: Instant?, to: Instant?): Long? =
    if (from == null || to == null || to < from) null else (to - from).inWholeMinutes

/** Isi tiga kolom satu meja operator; dirakit murni supaya bisa diuji tanpa merender apa pun. */
data class OperatorDeskBoard(
    val stage: SamplingPipelineStage,
    val queue: List<SamplingOrder>,
    val inProgress: List<SamplingOrder>,
    val doneToday: List<DeskHandoff>,
    /** Serah terima maju saja — dasar kolom Selesai dan hitungan "Riwayat (n)". */
    val history: List<DeskHandoff>,
    /** Semua kejadian di meja ini: serah terima, rework keluar, dan kartu yang dikembalikan. */
    val activity: List<DeskHandoff>
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
    val onDesk = orders.filter { it.isPhysicallyAt(stage) }
    val queue = onDesk
        .filter { it.deskColumn(stage) == OperatorDeskColumn.QUEUE }
        .sortedWith(compareBy<SamplingOrder> { it.pendingRework == null }.thenBy { it.arrivedAt(stage) })
    val inProgress = onDesk
        .filter { it.deskColumn(stage) == OperatorDeskColumn.IN_PROGRESS }
        .sortedBy { it.activeWork?.startedAt }
    val history = orders
        .flatMap { order -> order.handoffsFrom(stage).map { DeskHandoff(order, it, order.arrivalBefore(it)) } }
        .sortedByDescending { it.audit.at }
    val activity = orders
        .flatMap { order ->
            order.stageHistory.filter { it.fromStage == stage }.map { DeskHandoff(order, it, order.arrivalBefore(it)) }
        }
        .sortedByDescending { it.audit.at }
    return OperatorDeskBoard(
        stage = stage,
        queue = queue,
        inProgress = inProgress,
        doneToday = history.filter { it.audit.at.toLocalDateTime(timeZone).date == today },
        history = history,
        activity = activity
    )
}

/** Barangnya benar-benar ada di meja [stage]; SPK makloon vendor tidak pernah singgah di Linking. */
fun SamplingOrder.isPhysicallyAt(stage: SamplingPipelineStage): Boolean =
    !(stage == SamplingPipelineStage.LINKING_ASSEMBLY && finishingPath == FinishingPath.MAKLOON_VENDOR)

/** Jumlah kartu di kolom Antrian meja [stage] — sama persis dengan yang dirender papan. */
fun List<SamplingOrder>.queueCountAt(stage: SamplingPipelineStage): Int =
    count { it.isPhysicallyAt(stage) && it.deskColumn(stage) == OperatorDeskColumn.QUEUE }

/** Kapan SPK tiba di tahap ini (entri audit terakhir yang menuju ke sana), untuk urutan FIFO. */
private fun SamplingOrder.arrivedAt(stage: SamplingPipelineStage): Instant =
    stageHistory.lastOrNull { !it.isRelease && it.toStage == stage }?.at ?: updatedAt
