package com.eventverse.app.presentation.qc

import com.eventverse.app.domain.sampling.QcInspectionKind
import com.eventverse.app.domain.sampling.QcInspectionReport
import com.eventverse.app.domain.sampling.QcInspectionResult
import com.eventverse.app.domain.sampling.QcInspectorContribution
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.calculateTotalSampleQuantity
import com.eventverse.app.domain.sampling.isCompleteFor
import com.eventverse.app.domain.sampling.latestReportForPiece
import com.eventverse.app.domain.sampling.nextPieceNoFor
import com.eventverse.app.domain.sampling.passedPieceCountFor
import com.eventverse.app.domain.sampling.tallyBy
import com.eventverse.app.domain.sampling.toStageCode
import kotlin.time.Duration
import kotlinx.datetime.Instant

/** Laci antrean di sidebar. Urutannya sengaja sama dengan urutan kerja di meja inspeksi. */
enum class QcQueueBucket(val label: String) {
    WAITING("Menunggu QC"),
    REWORK("Perlu Rework"),
    SETTLED("Sudah Diputuskan")
}

/**
 * Satu baris antrean, sudah dihitung sampai siap dirender. Semua turunan dikerjakan di sini
 * supaya Composable tidak menghitung apa pun saat digambar ulang.
 */
data class QcQueueItem(
    val order: SamplingOrder,
    val kind: QcInspectionKind,
    val bucket: QcQueueBucket,
    val waitingSince: Instant,
    val waitingFor: Duration,
    val inspectionRound: Int,
    val latestReport: QcInspectionReport?,
    /** Total pcs yang harus diperiksa pada SPK ini. */
    val targetQty: Int,
    /** Pcs yang sudah **lolos** pada jenis QC ini. */
    val inspectedQty: Int,
    /** Nomor pcs yang akan dikerjakan berikutnya. */
    val nextPieceNo: Int,
    /** Lembar sebelumnya untuk pcs itu — ada berarti ini pemeriksaan ulang setelah perbaikan. */
    val previousReportForNextPiece: QcInspectionReport?,
    val isFullyInspected: Boolean,
    val contributions: List<QcInspectorContribution>,
    val designCode: String? = null,
    val designNumber: Int = 1,
    val totalDealDesigns: Int = 1
) {
    val spk: String get() = order.spkNumber.value
    val isFirstInspection: Boolean get() = inspectionRound == 0
    val remainingQty: Int get() = (targetQty - inspectedQty).coerceAtLeast(0)
    val isRecheck: Boolean get() = previousReportForNextPiece != null

    /**
     * Satu satuan saja, dieja penuh: "4 hari" / "22 jam" / "35 mnt".
     *
     * Bentuk dua satuan bersingkatan ("4h 18j") terbaca ambigu di badge sempit — "h" sama-sama
     * bisa berarti hari atau *hour*, dan antrean jadi tampak terurut salah. Ketelitian menit
     * tidak dipakai untuk apa pun di sini; yang dinilai inspektor cuma "ini sudah lama atau belum".
     */
    val waitingLabel: String
        get() {
            val totalMinutes = waitingFor.inWholeMinutes.coerceAtLeast(0)
            val days = totalMinutes / (60 * 24)
            val hours = totalMinutes / 60
            return when {
                days > 0 -> "$days hari"
                hours > 0 -> "$hours jam"
                else -> "$totalMinutes mnt"
            }
        }
}

/**
 * Ambang "sudah kelamaan menganggur di meja QC". Bukan SLA kontraktual — sekadar penanda visual
 * agar sampel yang terlupakan naik sendiri ke perhatian inspektor.
 */
private const val STALE_QUEUE_HOURS = 8

val QcQueueItem.isStale: Boolean
    get() = bucket != QcQueueBucket.SETTLED && waitingFor.inWholeHours >= STALE_QUEUE_HOURS

/**
 * Susun antrean untuk satu meja QC.
 *
 * Kedua meja memandang SPK yang sama dari titik berbeda, jadi kelayakan masuk antrean pun beda:
 * meja rajut menunggu panel turun mesin, meja finishing menunggu baju jadi disetor.
 *
 * Urutannya: yang butuh keputusan dulu (rework, lalu menunggu) dengan yang paling lama
 * menganggur di atas — antrean QC adalah FIFO, bukan urutan penyimpanan.
 */
fun buildQcQueue(
    orders: List<SamplingOrder>,
    now: Instant,
    kind: QcInspectionKind
): List<QcQueueItem> =
    orders.asSequence()
        .filter { order -> order.isEligibleFor(kind) }
        .map { order -> order.toQueueItem(now, kind, orders) }
        .sortedWith(
            compareBy<QcQueueItem> { it.bucket.ordinal }
                .thenByDescending { it.waitingFor }
        )
        .toList()

private fun SamplingOrder.isEligibleFor(kind: QcInspectionKind): Boolean {
    val hasInspectionOfKind = qcInspections.any { it.kind == kind }
    return when (kind) {
        // Panel rajut baru bisa diperiksa setelah turun mesin, dan tetap terbuka setelah
        // dirakit supaya berita acaranya masih bisa dibaca dari meja rajut.
        QcInspectionKind.KNITTING ->
            pipelineStage.order >= SamplingPipelineStage.MACHINE_KNITTING.order || hasInspectionOfKind

        QcInspectionKind.FINISHING ->
            pipelineStage.order >= SamplingPipelineStage.QC_FINISHING.order ||
                totalFinishedDepositedQty > 0 ||
                hasInspectionOfKind
    }
}

private fun SamplingOrder.toQueueItem(
    now: Instant,
    kind: QcInspectionKind,
    allOrders: List<SamplingOrder>
): QcQueueItem {
    val ofKind = qcInspections.filter { it.kind == kind }
    val latest = ofKind.lastOrNull()
    val targetQty = calculateTotalSampleQuantity(sizeMatrix, fallback = sampleQuantity)
        .takeIf { it > 0 } ?: sampleQuantity
    val inspectedQty = qcInspections.passedPieceCountFor(kind, targetQty)
    val nextPieceNo = qcInspections.nextPieceNoFor(kind, targetQty)

    val bucket = when {
        // Selesai hanya kalau seluruh pcs lolos — bukan sekadar seluruh pcs pernah disentuh.
        qcInspections.isCompleteFor(kind, targetQty) -> QcQueueBucket.SETTLED
        qcInspections.latestReportForPiece(kind, nextPieceNo)?.qcResult == QcInspectionResult.REWORK ->
            QcQueueBucket.REWORK
        else -> QcQueueBucket.WAITING
    }

    // Jam mulai menunggu = sejak barang siap diperiksa, bukan sejak SPK dibuat. Setelah rework,
    // hitungannya dimulai ulang dari inspeksi terakhir supaya tidak terlihat menunggu berhari-hari
    // padahal baru saja dikembalikan ke lantai produksi.
    val waitingSince = latest?.inspectedAt
        ?: stageHistory.lastOrNull { it.toCode == kind.entryStage.toStageCode() }?.at
        ?: finishingDeposits.mapNotNull { it.createdAt }.maxOrNull()
        ?: updatedAt

    val designInfo = com.eventverse.app.presentation.sampling.resolveDesignInfo(this, allOrders)

    return QcQueueItem(
        order = this,
        kind = kind,
        bucket = bucket,
        waitingSince = waitingSince,
        waitingFor = (now - waitingSince).let { if (it.isNegative()) Duration.ZERO else it },
        inspectionRound = ofKind.size,
        latestReport = latest,
        targetQty = targetQty,
        inspectedQty = inspectedQty,
        nextPieceNo = nextPieceNo,
        previousReportForNextPiece = qcInspections.latestReportForPiece(kind, nextPieceNo)
            ?.takeIf { it.qcResult != QcInspectionResult.PASSED },
        isFullyInspected = qcInspections.isCompleteFor(kind, targetQty),
        contributions = qcInspections.tallyBy(kind),
        designCode = designInfo?.code,
        designNumber = designInfo?.designNumber ?: 1,
        totalDealDesigns = designInfo?.totalDesigns ?: 1
    )
}

/** Tahap yang membuat SPK layak masuk meja ini — dipakai menghitung sejak kapan ia menunggu. */
private val QcInspectionKind.entryStage: SamplingPipelineStage
    get() = when (this) {
        QcInspectionKind.KNITTING -> SamplingPipelineStage.MACHINE_KNITTING
        QcInspectionKind.FINISHING -> SamplingPipelineStage.QC_FINISHING
    }
