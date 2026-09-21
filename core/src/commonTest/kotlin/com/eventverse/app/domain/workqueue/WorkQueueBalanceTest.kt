package com.eventverse.app.domain.workqueue

import com.eventverse.app.domain.pipeline.DefectLiability
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkQueueBalanceTest {

    private val now = Clock.System.now()
    private val sampleSubject = WorkSubjectRef(
        kind = WorkSubjectKind.BULK_WORK_ORDER,
        subjectId = "wo-001",
        orderNumber = "PO-2026-088",
        articleName = "Cardigan Rajut"
    )

    @Test
    fun `ordered pcs should always equal wip plus in repair plus scrap plus finished`() {
        // Skenario dari PDF: PO total 200 pcs
        // Status: 194 Ready to Pack (finished/WIP) | 4 In Repair | 2 Scrap
        val card = WorkCard(
            id = WorkCardId("card-qc"),
            tenantId = "tenant-1",
            subject = sampleSubject,
            stationCode = WorkStationCatalog.PACKAGING.code,
            sizeLabel = "L",
            bundleNo = null,
            queuedPcs = 200,
            wipPcs = 200,
            scrapPcs = 0,
            reworkPcs = 0,
            trackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
            status = WorkCardStatus.IN_PROGRESS,
            createdAt = now
        )
            .recordOutput(194, now) // 194 selesai lolos ke packaging, sisa WIP = 6
            .markReworkIssued(4)    // 4 pcs masuk jalur reparasi
            .recordScrap(2)         // 2 pcs afkir / scrap total, sisa WIP = 4

        val ticket = ReworkTicket(
            id = ReworkTicketId("rw-1"),
            cardId = card.id,
            tenantId = "tenant-1",
            subject = sampleSubject,
            defectCode = DefectCode("jahitan_melintir"),
            defectDisplayName = "Jahitan Melintir",
            liability = DefectLiability.FACTORY_WORKMANSHIP,
            qtyPcs = 4,
            sizeLabel = "L",
            targetStationCode = WorkStationCatalog.JAHIT_LURUS.code,
            status = ReworkTicketStatus.REWORK_ISSUED,
            issuedAt = now
        )

        val balance = WorkQueueBalance.calculate(
            orderedPcs = 200,
            cards = listOf(card),
            openTickets = listOf(ticket)
        )

        assertEquals(200, balance.orderedPcs)
        assertEquals(194, balance.finishedPcs)
        assertEquals(4, balance.inRepairPcs)
        assertEquals(2, balance.scrapPcs)
        assertEquals(0, balance.reconciliationDeltaPcs)
        assertTrue(balance.isBalanced)
    }

    @Test
    fun `closing rework ticket as pass returns pieces to ready pool`() {
        val card = WorkCard(
            id = WorkCardId("card-pack"),
            tenantId = "tenant-1",
            subject = sampleSubject,
            stationCode = WorkStationCatalog.PACKAGING.code,
            sizeLabel = "L",
            bundleNo = null,
            queuedPcs = 100,
            wipPcs = 100,
            scrapPcs = 0,
            reworkPcs = 0,
            trackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
            status = WorkCardStatus.IN_PROGRESS,
            createdAt = now
        )
            .recordOutput(96, now) // 96 selesai, sisa WIP = 4
            .markReworkIssued(4)   // 4 pcs masuk tiket reparasi

        val ticket = ReworkTicket(
            id = ReworkTicketId("rw-2"),
            cardId = card.id,
            tenantId = "tenant-1",
            subject = sampleSubject,
            defectCode = DefectCode("kancing_copot"),
            defectDisplayName = "Kancing Copot",
            liability = DefectLiability.FACTORY_WORKMANSHIP,
            qtyPcs = 4,
            sizeLabel = "L",
            targetStationCode = WorkStationCatalog.PASANG_KANCING.code,
            status = ReworkTicketStatus.REWORK_ISSUED,
            issuedAt = now
        )

        val initialBalance = WorkQueueBalance.calculate(100, listOf(card), listOf(ticket))
        assertEquals(4, initialBalance.inRepairPcs)
        assertEquals(96, initialBalance.finishedPcs)
        assertTrue(initialBalance.isBalanced)

        // Ticket diperbaiki dan lolos periksa ulang
        val inRepair = ticket.startRepair("op-kancing", now)
        val ready = inRepair.markReadyForRecheck("Kancing dipasang ulang kuat", now)
        val closedTicket = ready.closeAsPassed("Lolos QC", now)
        val updatedCard = card.markReworkResolved(4).recordOutput(4, now)

        val settledBalance = WorkQueueBalance.calculate(100, listOf(updatedCard), listOf(closedTicket))
        assertEquals(0, settledBalance.inRepairPcs)
        assertEquals(100, settledBalance.finishedPcs)
        assertEquals(0, settledBalance.reconciliationDeltaPcs)
        assertTrue(settledBalance.isBalanced)
    }
}
