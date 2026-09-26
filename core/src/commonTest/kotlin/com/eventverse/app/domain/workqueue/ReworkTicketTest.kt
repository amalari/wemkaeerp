package com.eventverse.app.domain.workqueue

import com.eventverse.app.domain.pipeline.DefectLiability
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReworkTicketTest {

    private val now = Clock.System.now()
    private val sampleSubject = WorkSubjectRef(
        kind = WorkSubjectKind.BULK_WORK_ORDER,
        subjectId = "wo-001",
        orderNumber = "PO-2026-088",
        articleName = "Cardigan Rajut"
    )

    private fun createSampleTicket(): ReworkTicket = ReworkTicket(
        id = ReworkTicketId("rw-001"),
        cardId = WorkCardId("card-001"),
        tenantId = "tenant-1",
        subject = sampleSubject,
        defectCode = DefectCode("jahitan_melintir"),
        defectDisplayName = "Jahitan Melintir",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        qtyPcs = 4,
        sizeLabel = "L",
        targetStationCode = WorkStationCatalog.JAHIT_LURUS.code,
        responsibleOperatorId = "op-budi",
        status = ReworkTicketStatus.REWORK_ISSUED,
        qcNotes = "Bongkar overdeck lengan kiri",
        issuedAt = now
    )

    @Test
    fun `close ticket directly from issued should throw exception`() {
        val ticket = createSampleTicket()
        assertFailsWith<IllegalArgumentException> {
            ticket.closeAsPassed("Langsung tutup tanpa periksa ulang", now)
        }
    }

    @Test
    fun `scrap without reason should throw exception`() {
        val ticket = createSampleTicket()
        assertFailsWith<IllegalArgumentException> {
            ticket.closeAsScrap("   ", now)
        }
    }

    @Test
    fun `full happy path issued to in repair to ready for recheck to closed`() {
        val ticket = createSampleTicket()
        assertEquals(ReworkTicketStatus.REWORK_ISSUED, ticket.status)
        assertTrue(ticket.isOpen)

        val inRepair = ticket.startRepair("op-revisi-01", now)
        assertEquals(ReworkTicketStatus.IN_REPAIR, inRepair.status)
        assertEquals("op-revisi-01", inRepair.assignedRepairOperatorId)
        assertTrue(inRepair.isOpen)

        val readyForRecheck = inRepair.markReadyForRecheck("Sudah dibongkar dan dijahit ulang", now)
        assertEquals(ReworkTicketStatus.READY_FOR_RE_CHECK, readyForRecheck.status)
        assertTrue(readyForRecheck.isOpen)

        val closed = readyForRecheck.closeAsPassed("Lolos QC periksa ulang", now)
        assertEquals(ReworkTicketStatus.CLOSED, closed.status)
        assertFalse(closed.isOpen)
    }

    @Test
    fun `scrapping ticket closes it with explicit scrap reason`() {
        val ticket = createSampleTicket()
        val inRepair = ticket.startRepair("op-01", now)
        val scrapped = inRepair.closeAsScrap("Kain robek saat dibongkar benang", now)

        assertEquals(ReworkTicketStatus.SCRAP, scrapped.status)
        assertEquals("Kain robek saat dibongkar benang", scrapped.scrapReason)
        assertFalse(scrapped.isOpen)
    }
}
