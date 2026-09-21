package com.eventverse.app.domain.workqueue

import kotlinx.datetime.Instant

/**
 * Catatan setoran hasil pengerjaan operator di stasiun kerja (Append-Only Event).
 *
 * Menjadi satu-satunya sumber kebenaran output fisik dan perhitungan upah borongan.
 * Merekam [tariffSnapshotIdr] pada saat transaksi agar perubahan tarif masa depan tidak
 * merusak riwayat upah masa lalu.
 */
data class WorkDeposit(
    val id: WorkDepositId,
    val cardId: WorkCardId,
    val operatorId: String,
    val operatorName: String,
    val qtyPcs: Int,
    val tariffSnapshotIdr: Long = 0L,
    val isReworkDeposit: Boolean = false,
    val notes: String = "",
    val verifiedPhotoKey: String? = null,
    val submittedAt: Instant
) {
    init {
        require(operatorId.isNotBlank()) { "operatorId cannot be blank" }
        require(operatorName.isNotBlank()) { "operatorName cannot be blank" }
        require(qtyPcs > 0) { "qtyPcs must be strictly positive, was $qtyPcs" }
        require(tariffSnapshotIdr >= 0L) { "tariffSnapshotIdr cannot be negative" }
    }

    /**
     * Total upah yang berhak diterima operator atas setoran ini.
     * Jika setoran ini adalah perbaikan cacat ([isReworkDeposit] == true), upah = 0 agar tidak terjadi klaim ganda.
     */
    val earnedPayIdr: Long
        get() = if (isReworkDeposit) 0L else qtyPcs.toLong() * tariffSnapshotIdr
}

data class WorkStationProgress(
    val targetPcs: Int,
    val depositedPcs: Int,
    val reworkPcs: Int,
    val regularPcs: Int,
    val remainingPcs: Int,
    val isComplete: Boolean,
    val overDepositedPcs: Int,
    val totalEarnedPayIdr: Long
)

/**
 * Akumulasi murni atas daftar setoran terhadap target kuantitas.
 */
fun List<WorkDeposit>.progressAgainst(targetPcs: Int): WorkStationProgress {
    val target = targetPcs.coerceAtLeast(0)
    val totalPcs = sumOf { it.qtyPcs }
    val reworkPcs = filter { it.isReworkDeposit }.sumOf { it.qtyPcs }
    val regularPcs = totalPcs - reworkPcs
    val remaining = (target - regularPcs).coerceAtLeast(0)
    val overDeposited = (regularPcs - target).coerceAtLeast(0)
    val totalPay = sumOf { it.earnedPayIdr }

    return WorkStationProgress(
        targetPcs = target,
        depositedPcs = totalPcs,
        reworkPcs = reworkPcs,
        regularPcs = regularPcs,
        remainingPcs = remaining,
        isComplete = regularPcs >= target && target > 0,
        overDepositedPcs = overDeposited,
        totalEarnedPayIdr = totalPay
    )
}
