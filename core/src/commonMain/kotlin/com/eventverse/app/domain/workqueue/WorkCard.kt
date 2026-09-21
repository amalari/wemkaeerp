package com.eventverse.app.domain.workqueue

import kotlinx.datetime.Instant

/**
 * Agregat kartu antrean kerja di satu stasiun.
 *
 * Mengikuti invarian pelacakan fisik:
 * - Pada unit [WorkTrackingUnit.BUNDLE]: wajib memiliki [bundleNo] > 0.
 * - Pada unit [WorkTrackingUnit.LOT_ACCUMULATION]: [bundleNo] wajib null.
 */
data class WorkCard(
    val id: WorkCardId,
    val tenantId: String,
    val subject: WorkSubjectRef,
    val stationCode: WorkStationCode,
    val sizeLabel: String,
    val bundleNo: Int?,
    val queuedPcs: Int,
    val wipPcs: Int,
    val scrapPcs: Int = 0,
    val reworkPcs: Int = 0,
    val trackingUnit: WorkTrackingUnit,
    val status: WorkCardStatus = WorkCardStatus.QUEUED,
    val executionMode: WorkExecutionMode = WorkExecutionMode.IN_HOUSE,
    val vendorRef: String? = null,
    val createdAt: Instant,
    val completedAt: Instant? = null
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId cannot be blank" }
        require(sizeLabel.isNotBlank()) { "sizeLabel cannot be blank" }
        require(queuedPcs >= 0) { "queuedPcs cannot be negative, was $queuedPcs" }
        require(wipPcs >= 0) { "wipPcs cannot be negative, was $wipPcs" }
        require(scrapPcs >= 0) { "scrapPcs cannot be negative, was $scrapPcs" }
        require(reworkPcs >= 0) { "reworkPcs cannot be negative, was $reworkPcs" }

        when (trackingUnit) {
            WorkTrackingUnit.BUNDLE -> {
                require(bundleNo != null && bundleNo > 0) {
                    "Bundle tracking unit requires a positive bundleNo, got $bundleNo"
                }
            }
            WorkTrackingUnit.LOT_ACCUMULATION -> {
                require(bundleNo == null) {
                    "Lot accumulation unit cannot have bundleNo, got $bundleNo"
                }
            }
        }

        val nonWip = queuedPcs - wipPcs
        require(nonWip >= 0) { "wipPcs ($wipPcs) cannot exceed queuedPcs ($queuedPcs)" }
        require(scrapPcs <= queuedPcs) { "scrapPcs ($scrapPcs) cannot exceed queuedPcs ($queuedPcs)" }
        require(reworkPcs <= queuedPcs) { "reworkPcs ($reworkPcs) cannot exceed queuedPcs ($queuedPcs)" }
    }

    /** Pcs yang sudah selesai diproses (di luar barang rusak/scrap). */
    val completedPcs: Int get() = (queuedPcs - wipPcs - scrapPcs).coerceAtLeast(0)

    val isFinished: Boolean get() = status == WorkCardStatus.COMPLETED || status == WorkCardStatus.MERGED

    val isSubcontracted: Boolean get() = executionMode == WorkExecutionMode.SUBCONTRACTED

    /**
     * Merekam setoran output selesai pada kartu ini.
     */
    fun recordOutput(completedQty: Int, now: Instant): WorkCard {
        require(completedQty > 0) { "completedQty must be positive" }
        require(completedQty <= wipPcs) {
            "Cannot record output ($completedQty pcs) exceeding remaining WIP ($wipPcs pcs)"
        }
        val newWip = wipPcs - completedQty
        val newStatus = if (newWip == 0) WorkCardStatus.COMPLETED else WorkCardStatus.IN_PROGRESS
        return copy(
            wipPcs = newWip,
            status = newStatus,
            completedAt = if (newWip == 0) now else completedAt
        )
    }

    /**
     * Merekam afkir / scrap permanen.
     */
    fun recordScrap(scrapQty: Int): WorkCard {
        require(scrapQty > 0) { "scrapQty must be positive" }
        require(scrapQty <= wipPcs) {
            "Cannot scrap ($scrapQty pcs) exceeding remaining WIP ($wipPcs pcs)"
        }
        val newWip = wipPcs - scrapQty
        val newScrap = scrapPcs + scrapQty
        val newStatus = if (newWip == 0) WorkCardStatus.COMPLETED else WorkCardStatus.IN_PROGRESS
        return copy(
            wipPcs = newWip,
            scrapPcs = newScrap,
            status = newStatus
        )
    }

    /**
     * Mengunci kuantitas yang ditarik ke jalur reparasi (rework).
     */
    fun markReworkIssued(qty: Int): WorkCard {
        require(qty > 0) { "rework qty must be positive" }
        require(reworkPcs + qty <= queuedPcs) { "total rework cannot exceed queuedPcs" }
        return copy(reworkPcs = reworkPcs + qty)
    }

    /**
     * Mengembalikan kuantitas yang sudah selesai diperbaiki dan lolos QC re-check.
     */
    fun markReworkResolved(qty: Int): WorkCard {
        require(qty > 0) { "resolved qty must be positive" }
        require(qty <= reworkPcs) { "resolved qty ($qty) cannot exceed pending reworkPcs ($reworkPcs)" }
        return copy(reworkPcs = reworkPcs - qty)
    }

    /**
     * Menandai kartu telah dilebur di stasiun merge gate (misal washing).
     */
    fun markMerged(now: Instant): WorkCard {
        return copy(
            status = WorkCardStatus.MERGED,
            completedAt = now
        )
    }
}
