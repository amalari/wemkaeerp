package com.eventverse.app.domain.workqueue.usecases

import com.eventverse.app.domain.workqueue.DefectCode
import com.eventverse.app.domain.workqueue.ReworkTicket
import com.eventverse.app.domain.workqueue.ReworkTicketId
import com.eventverse.app.domain.workqueue.ReworkTicketRepository
import com.eventverse.app.domain.workqueue.ReworkTicketStatus
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkDefectCatalog
import com.eventverse.app.domain.workqueue.WorkDefectSpec
import kotlinx.datetime.Instant

data class IssueReworkTicketCommand(
    val cardId: WorkCardId,
    val defectCode: DefectCode,
    val qtyPcs: Int,
    val responsibleOperatorId: String? = null,
    val qcNotes: String = "",
    val now: Instant,
    val customDefects: List<WorkDefectSpec> = emptyList()
)

/**
 * Menerbitkan tiket reparasi atas temuan cacat di meja inspeksi QC.
 * 1 tiket mewakili 1 masalah cacat yang sama per batch, dialirkan ke stasiun perbaikan yang sesuai.
 */
class IssueReworkTicketUseCase(
    private val cardRepository: WorkCardRepository,
    private val ticketRepository: ReworkTicketRepository
) {
    suspend operator fun invoke(command: IssueReworkTicketCommand): Result<ReworkTicket> = runCatching {
        val card = cardRepository.findById(command.cardId)
            ?: error("WorkCard not found with id: ${command.cardId.value}")

        val defectSpec = WorkDefectCatalog.resolve(command.defectCode, command.customDefects)
            ?: error("Unrecognized DefectCode: ${command.defectCode.value}")

        val updatedCard = card.markReworkIssued(command.qtyPcs)

        val ticketId = ReworkTicketId("rw-${card.subject.subjectId}-${command.defectCode.value}-${command.now.toEpochMilliseconds()}")
        val ticket = ReworkTicket(
            id = ticketId,
            cardId = card.id,
            tenantId = card.tenantId,
            subject = card.subject,
            defectCode = command.defectCode,
            defectDisplayName = defectSpec.displayName,
            liability = defectSpec.liability,
            qtyPcs = command.qtyPcs,
            sizeLabel = card.sizeLabel,
            targetStationCode = defectSpec.targetStationCode,
            responsibleOperatorId = command.responsibleOperatorId,
            status = ReworkTicketStatus.REWORK_ISSUED,
            qcNotes = command.qcNotes,
            issuedAt = command.now
        )

        cardRepository.save(updatedCard)
        ticketRepository.save(ticket)

        ticket
    }
}
