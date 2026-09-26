package com.eventverse.app.domain.workqueue.usecases

import com.eventverse.app.domain.workqueue.ReworkTicket
import com.eventverse.app.domain.workqueue.ReworkTicketId
import com.eventverse.app.domain.workqueue.ReworkTicketRepository
import com.eventverse.app.domain.workqueue.WorkCardRepository
import kotlinx.datetime.Instant

sealed interface ReworkAction {
    data class StartRepair(val repairOperatorId: String) : ReworkAction
    data class MarkReadyForRecheck(val repairNotes: String = "") : ReworkAction
    data class CloseAsPassed(val qcNotes: String = "") : ReworkAction
    data class CloseAsScrap(val scrapReason: String) : ReworkAction
}

data class AdvanceReworkTicketCommand(
    val ticketId: ReworkTicketId,
    val action: ReworkAction,
    val now: Instant
)

/**
 * Menggerakkan status tiket reparasi pada siklus hidup perbaikan cacat.
 * Menjamin sinkronisasi kuantitas kembali ke kartu antrean saat tiket diselesaikan (pass/scrap).
 */
class AdvanceReworkTicketUseCase(
    private val ticketRepository: ReworkTicketRepository,
    private val cardRepository: WorkCardRepository
) {
    suspend operator fun invoke(command: AdvanceReworkTicketCommand): Result<ReworkTicket> = runCatching {
        val ticket = ticketRepository.findById(command.ticketId)
            ?: error("ReworkTicket not found with id: ${command.ticketId.value}")

        val updatedTicket = when (val action = command.action) {
            is ReworkAction.StartRepair -> ticket.startRepair(action.repairOperatorId, command.now)
            is ReworkAction.MarkReadyForRecheck -> ticket.markReadyForRecheck(action.repairNotes, command.now)
            is ReworkAction.CloseAsPassed -> {
                val passedTicket = ticket.closeAsPassed(action.qcNotes, command.now)
                val card = cardRepository.findById(ticket.cardId)
                if (card != null) {
                    val updatedCard = card.markReworkResolved(ticket.qtyPcs)
                    cardRepository.save(updatedCard)
                }
                passedTicket
            }
            is ReworkAction.CloseAsScrap -> {
                val scrappedTicket = ticket.closeAsScrap(action.scrapReason, command.now)
                val card = cardRepository.findById(ticket.cardId)
                if (card != null) {
                    val updatedCard = card.copy(
                        reworkPcs = (card.reworkPcs - ticket.qtyPcs).coerceAtLeast(0),
                        scrapPcs = card.scrapPcs + ticket.qtyPcs
                    )
                    cardRepository.save(updatedCard)
                }
                scrappedTicket
            }
        }

        ticketRepository.save(updatedTicket)
        updatedTicket
    }
}
