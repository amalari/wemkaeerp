package com.eventverse.app.domain.workqueue

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkCardTest {

    private val now = Clock.System.now()
    private val sampleSubject = WorkSubjectRef(
        kind = WorkSubjectKind.BULK_WORK_ORDER,
        subjectId = "wo-001",
        orderNumber = "PO-2026-088",
        articleName = "Cardigan Rajut"
    )

    @Test
    fun `bundle card without bundle number should throw exception`() {
        assertFailsWith<IllegalArgumentException> {
            WorkCard(
                id = WorkCardId("card-1"),
                tenantId = "tenant-1",
                subject = sampleSubject,
                stationCode = WorkStationCatalog.CUTTING.code,
                sizeLabel = "L",
                bundleNo = null, // Invalid for BUNDLE tracking
                queuedPcs = 20,
                wipPcs = 20,
                trackingUnit = WorkTrackingUnit.BUNDLE,
                createdAt = now
            )
        }
    }

    @Test
    fun `lot card with bundle number should throw exception`() {
        assertFailsWith<IllegalArgumentException> {
            WorkCard(
                id = WorkCardId("card-2"),
                tenantId = "tenant-1",
                subject = sampleSubject,
                stationCode = WorkStationCatalog.STEAM.code,
                sizeLabel = "L",
                bundleNo = 5, // Invalid for LOT_ACCUMULATION
                queuedPcs = 100,
                wipPcs = 100,
                trackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
                createdAt = now
            )
        }
    }

    @Test
    fun `wip exceeding queued pcs should throw exception`() {
        assertFailsWith<IllegalArgumentException> {
            WorkCard(
                id = WorkCardId("card-3"),
                tenantId = "tenant-1",
                subject = sampleSubject,
                stationCode = WorkStationCatalog.JAHIT_LURUS.code,
                sizeLabel = "L",
                bundleNo = 1,
                queuedPcs = 20,
                wipPcs = 25, // Exceeds queued
                trackingUnit = WorkTrackingUnit.BUNDLE,
                createdAt = now
            )
        }
    }

    @Test
    fun `record output reduces wip and marks completed when zero`() {
        val card = WorkCard(
            id = WorkCardId("card-4"),
            tenantId = "tenant-1",
            subject = sampleSubject,
            stationCode = WorkStationCatalog.JAHIT_LURUS.code,
            sizeLabel = "M",
            bundleNo = 1,
            queuedPcs = 20,
            wipPcs = 20,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            createdAt = now
        )

        val inProgress = card.recordOutput(10, now)
        assertEquals(10, inProgress.wipPcs)
        assertEquals(10, inProgress.completedPcs)
        assertEquals(WorkCardStatus.IN_PROGRESS, inProgress.status)

        val completed = inProgress.recordOutput(10, now)
        assertEquals(0, completed.wipPcs)
        assertEquals(20, completed.completedPcs)
        assertEquals(WorkCardStatus.COMPLETED, completed.status)
        assertTrue(completed.isFinished)
    }

    @Test
    fun `subcontracted card should preserve vendorRef across transfers`() {
        val card = WorkCard(
            id = WorkCardId("card-subcon"),
            tenantId = "tenant-1",
            subject = sampleSubject,
            stationCode = WorkStationCatalog.PASANG_KANCING.code,
            sizeLabel = "XL",
            bundleNo = 2,
            queuedPcs = 20,
            wipPcs = 20,
            trackingUnit = WorkTrackingUnit.BUNDLE,
            executionMode = WorkExecutionMode.SUBCONTRACTED,
            vendorRef = "vendor-kancing-berkah",
            createdAt = now
        )

        assertTrue(card.isSubcontracted)
        assertEquals("vendor-kancing-berkah", card.vendorRef)

        val updated = card.recordOutput(20, now)
        assertTrue(updated.isSubcontracted)
        assertEquals("vendor-kancing-berkah", updated.vendorRef)
    }
}
