package com.eventverse.app.domain.workqueue

import com.eventverse.app.domain.pipeline.DefectLiability
import kotlinx.datetime.Instant

/**
 * Agregat tiket tugas reparasi pakaian cacat dari gerbang pemeriksaan mutu (QC).
 *
 * Mengikuti siklus hidup:
 * [ReworkTicketStatus.REWORK_ISSUED] ➔ [ReworkTicketStatus.IN_REPAIR] ➔
 * [ReworkTicketStatus.READY_FOR_RE_CHECK] ➔ [ReworkTicketStatus.CLOSED] atau [ReworkTicketStatus.SCRAP].
 */
data class ReworkTicket(
    val id: ReworkTicketId,
    val cardId: WorkCardId,
    val tenantId: String,
    val subject: WorkSubjectRef,
    val defectCode: DefectCode,
    val defectDisplayName: String,
    val liability: DefectLiability,
    val qtyPcs: Int,
    val sizeLabel: String,
    val targetStationCode: WorkStationCode,
    val responsibleOperatorId: String? = null,
    val assignedRepairOperatorId: String? = null,
    val status: ReworkTicketStatus = ReworkTicketStatus.REWORK_ISSUED,
    val qcNotes: String = "",
    val repairNotes: String = "",
    val scrapReason: String? = null,
    val issuedAt: Instant,
    val inRepairAt: Instant? = null,
    val readyForRecheckAt: Instant? = null,
    val closedAt: Instant? = null
) {
    init {
        require(tenantId.isNotBlank()) { "tenantId cannot be blank" }
        require(defectDisplayName.isNotBlank()) { "defectDisplayName cannot be blank" }
        require(qtyPcs > 0) { "qtyPcs must be strictly positive, was $qtyPcs" }
        require(sizeLabel.isNotBlank()) { "sizeLabel cannot be blank" }
    }

    val isOpen: Boolean
        get() = status != ReworkTicketStatus.CLOSED && status != ReworkTicketStatus.SCRAP

    fun startRepair(repairOperatorId: String, now: Instant): ReworkTicket {
        require(status == ReworkTicketStatus.REWORK_ISSUED) {
            "Can only start repair from REWORK_ISSUED, current status: $status"
        }
        require(repairOperatorId.isNotBlank()) { "repairOperatorId cannot be blank" }
        return copy(
            status = ReworkTicketStatus.IN_REPAIR,
            assignedRepairOperatorId = repairOperatorId,
            inRepairAt = now
        )
    }

    fun markReadyForRecheck(notes: String = "", now: Instant): ReworkTicket {
        require(status == ReworkTicketStatus.IN_REPAIR) {
            "Can only mark ready for re-check from IN_REPAIR, current status: $status"
        }
        return copy(
            status = ReworkTicketStatus.READY_FOR_RE_CHECK,
            repairNotes = notes,
            readyForRecheckAt = now
        )
    }

    fun closeAsPassed(notes: String = "", now: Instant): ReworkTicket {
        require(status == ReworkTicketStatus.READY_FOR_RE_CHECK) {
            "Cannot close ticket directly from $status; must go through READY_FOR_RE_CHECK inspection"
        }
        return copy(
            status = ReworkTicketStatus.CLOSED,
            qcNotes = if (notes.isNotBlank()) notes else qcNotes,
            closedAt = now
        )
    }

    fun closeAsScrap(reason: String, now: Instant): ReworkTicket {
        require(reason.isNotBlank()) { "Scrapping a rework ticket requires an explicit non-blank reason" }
        require(isOpen) { "Cannot scrap an already resolved ticket (status: $status)" }
        return copy(
            status = ReworkTicketStatus.SCRAP,
            scrapReason = reason,
            closedAt = now
        )
    }
}
