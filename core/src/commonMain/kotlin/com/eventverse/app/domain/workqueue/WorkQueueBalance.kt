package com.eventverse.app.domain.workqueue

/**
 * Akumulator rekonsiliasi keseimbangan kuantitas pesanan.
 *
 * Menjamin identitas fisik:
 * orderedPcs == wipPcs + inRepairPcs + scrapPcs + finishedPcs
 *
 * Perbedaan dihitung sebagai [reconciliationDeltaPcs] dan dilaporkan secara transparan,
 * bukan memicu fatal crash yang memblokir tampilan dasbor pabrik.
 */
data class WorkQueueBalance(
    val orderedPcs: Int,
    val wipPcs: Int,
    val inRepairPcs: Int,
    val scrapPcs: Int,
    val finishedPcs: Int,
    val reconciliationDeltaPcs: Int,
    val isBalanced: Boolean,
    val totalReworkTicketsCount: Int
) {
    companion object {
        fun calculate(
            orderedPcs: Int,
            cards: List<WorkCard>,
            openTickets: List<ReworkTicket>
        ): WorkQueueBalance {
            val totalOrdered = orderedPcs.coerceAtLeast(0)
            
            // inRepairPcs adalah akumulasi pcs pada tiket reparasi yang masih aktif (belum closed/scrap)
            val inRepair = openTickets.filter { it.isOpen }.sumOf { it.qtyPcs }
            
            // Total scrap dari kartu + tiket yang diputuskan scrap
            val scrapFromCards = cards.sumOf { it.scrapPcs }
            val scrapFromTickets = openTickets.filter { it.status == ReworkTicketStatus.SCRAP }.sumOf { it.qtyPcs }
            val totalScrap = scrapFromCards + scrapFromTickets

            // Pcs yang selesai di stasiun hilir (PACKAGING)
            val packagingCards = cards.filter { it.stationCode == WorkStationCatalog.PACKAGING.code }
            val finished = if (packagingCards.isNotEmpty()) {
                packagingCards.sumOf { it.completedPcs }
            } else {
                cards.filter { it.isFinished }.maxOfOrNull { it.completedPcs } ?: 0
            }

            // Sisa WIP di stasiun yang belum rampung (dikurangi yang sedang di repair/scrap/finished)
            val currentWip = (totalOrdered - inRepair - totalScrap - finished).coerceAtLeast(0)

            val accounted = currentWip + inRepair + totalScrap + finished
            val delta = totalOrdered - accounted

            return WorkQueueBalance(
                orderedPcs = totalOrdered,
                wipPcs = currentWip,
                inRepairPcs = inRepair,
                scrapPcs = totalScrap,
                finishedPcs = finished,
                reconciliationDeltaPcs = delta,
                isBalanced = delta == 0,
                totalReworkTicketsCount = openTickets.size
            )
        }
    }
}
