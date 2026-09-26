package com.eventverse.app.domain.workqueue.usecases

import com.eventverse.app.domain.workqueue.ReworkTicketRepository
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkQueueBoard
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationSpec
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef

data class GetWorkQueueBoardQuery(
    val tenantId: String,
    val subjectId: String,
    val orderedPcs: Int,
    val customStations: List<WorkStationSpec> = emptyList()
)

/**
 * Menghitung dan merakit proyeksi papan kanban stasiun kerja serta keseimbangan stok.
 */
class GetWorkQueueBoardUseCase(
    private val cardRepository: WorkCardRepository,
    private val ticketRepository: ReworkTicketRepository
) {
    suspend operator fun invoke(query: GetWorkQueueBoardQuery): Result<WorkQueueBoard> = runCatching {
        val cards = cardRepository.findBySubject(query.tenantId, query.subjectId)
        val tickets = ticketRepository.findBySubject(query.tenantId, query.subjectId)
        val stations = WorkStationCatalog.line(query.customStations)

        val subjectRef = cards.firstOrNull()?.subject
            ?: tickets.firstOrNull()?.subject
            ?: WorkSubjectRef(
                kind = WorkSubjectKind.BULK_WORK_ORDER,
                subjectId = query.subjectId,
                orderNumber = query.subjectId,
                articleName = "Unknown Article"
            )

        WorkQueueBoard.build(
            subject = subjectRef,
            orderedPcs = query.orderedPcs,
            stations = stations,
            cards = cards,
            tickets = tickets
        )
    }
}
