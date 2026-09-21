package com.eventverse.app.domain.workqueue

/**
 * Satu kolom stasiun kerja pada papan kanban antrean.
 */
data class WorkQueueColumn(
    val station: WorkStationSpec,
    val cards: List<WorkCard>,
    val totalQueuedPcs: Int,
    val totalWipPcs: Int,
    val totalCompletedPcs: Int,
    val activeTickets: List<ReworkTicket>
) {
    val isClear: Boolean get() = totalWipPcs == 0 && activeTickets.isEmpty()
}

/**
 * Proyeksi murni papan kanban stasiun kerja untuk satu pesanan/subject.
 */
data class WorkQueueBoard(
    val subject: WorkSubjectRef,
    val columns: List<WorkQueueColumn>,
    val balance: WorkQueueBalance
) {
    fun columnFor(stationCode: WorkStationCode): WorkQueueColumn? =
        columns.firstOrNull { it.station.code == stationCode }

    fun activeCardsFor(stationCode: WorkStationCode): List<WorkCard> =
        columnFor(stationCode)?.cards?.filter { !it.isFinished } ?: emptyList()

    companion object {
        fun build(
            subject: WorkSubjectRef,
            orderedPcs: Int,
            stations: List<WorkStationSpec>,
            cards: List<WorkCard>,
            tickets: List<ReworkTicket>
        ): WorkQueueBoard {
            val cardsByStation = cards.groupBy { it.stationCode }
            val ticketsByStation = tickets.filter { it.isOpen }.groupBy { it.targetStationCode }

            val columns = stations.map { station ->
                val stationCards = cardsByStation[station.code] ?: emptyList()
                val stationTickets = ticketsByStation[station.code] ?: emptyList()

                WorkQueueColumn(
                    station = station,
                    cards = stationCards,
                    totalQueuedPcs = stationCards.sumOf { it.queuedPcs },
                    totalWipPcs = stationCards.sumOf { it.wipPcs },
                    totalCompletedPcs = stationCards.sumOf { it.completedPcs },
                    activeTickets = stationTickets
                )
            }

            val balance = WorkQueueBalance.calculate(orderedPcs, cards, tickets)

            return WorkQueueBoard(
                subject = subject,
                columns = columns,
                balance = balance
            )
        }
    }
}
